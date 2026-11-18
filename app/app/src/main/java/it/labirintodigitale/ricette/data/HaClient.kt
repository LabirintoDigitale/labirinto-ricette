package it.labirintodigitale.ricette.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Error reported by Home Assistant (`code` is HA's error code, e.g. `auth_invalid`). */
class HaException(val code: String, message: String) : Exception(message)

/** Talks to Home Assistant: the websocket API for the cards, HTTP for the images. */
class HaClient {

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    /** Open an authenticated websocket, run [block], then close it. */
    suspend fun <T> session(baseUrl: String, token: String, block: suspend (Session) -> T): T {
        val session = Session(token)
        val request = Request.Builder().url(websocketUrl(baseUrl)).build()
        val socket = http.newWebSocket(request, session.listener)
        session.socket = socket
        try {
            withTimeout(15_000) { session.authenticated.await() }
            return block(session)
        } catch (e: TimeoutCancellationException) {
            // Not a cancellation of the caller: report it as a network error.
            throw IOException("Timeout", e)
        } finally {
            socket.close(1000, null)
        }
    }

    /**
     * Download a file served by Home Assistant to [target]. Returns false if it is gone (404).
     * [path] is an absolute path on the server; [token] is sent only when not null.
     */
    suspend fun download(baseUrl: String, path: String, token: String?, target: File): Boolean =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(normalizeUrl(baseUrl) + path)
            if (token != null) builder.header("Authorization", "Bearer $token")
            val request = builder.build()
            http.newCall(request).execute().use { response ->
                when {
                    response.code == 404 -> false
                    !response.isSuccessful -> throw IOException("HTTP ${response.code}")
                    else -> {
                        val tmp = File(target.parentFile, target.name + ".part")
                        tmp.outputStream().use { out -> response.body!!.byteStream().copyTo(out) }
                        if (!tmp.renameTo(target)) throw IOException("Cannot save $file")
                        true
                    }
                }
            }
        }

    // ---------- login with username and password (Home Assistant auth API) ----------

    /** Result of one step of the login form. */
    sealed interface LoginStep {
        /** Logged in: [code] is exchanged for tokens with [exchangeCode]. */
        data class Done(val code: String) : LoginStep

        /** Two-factor authentication: send the code with [submitLogin]. */
        data class NeedsCode(val flowId: String, val module: String?) : LoginStep

        /** The form was rejected; [error] is HA's code, e.g. `invalid_auth`, `invalid_code`. */
        data class Failed(val flowId: String?, val error: String) : LoginStep
    }

    data class Tokens(val access: String, val expiresAt: Long, val refresh: String?)

    /** Start the login flow and send username and password. */
    suspend fun login(baseUrl: String, username: String, password: String): LoginStep {
        val start = postJson(
            baseUrl, "/auth/login_flow",
            JSONObject()
                .put("client_id", CLIENT_ID)
                .put("handler", org.json.JSONArray().put("homeassistant").put(JSONObject.NULL))
                .put("redirect_uri", REDIRECT_URI),
        )
        val flowId = start.optString("flow_id").ifEmpty { throw HaException("login", "Login not available") }
        return submitLogin(
            baseUrl, flowId,
            JSONObject().put("username", username).put("password", password),
        )
    }

    /** Send a step of the login form ([data] without client_id). */
    suspend fun submitLogin(baseUrl: String, flowId: String, data: JSONObject): LoginStep {
        val res = postJson(baseUrl, "/auth/login_flow/$flowId", data.put("client_id", CLIENT_ID))
        return when (res.optString("type")) {
            "create_entry" -> LoginStep.Done(res.getString("result"))
            "form" -> {
                val error = res.optJSONObject("errors")?.let { e ->
                    e.keys().asSequence().firstOrNull()?.let { e.optString(it) }
                }
                when {
                    error != null -> LoginStep.Failed(flowId.takeIf { res.optString("step_id") == "mfa" }, error)
                    res.optString("step_id") == "mfa" -> LoginStep.NeedsCode(
                        flowId, res.optJSONObject("description_placeholders")?.optString("mfa_module_name"),
                    )
                    else -> LoginStep.Failed(null, "unknown")
                }
            }
            "abort" -> LoginStep.Failed(null, res.optString("reason", "abort"))
            else -> LoginStep.Failed(null, res.optString("message", "unknown"))
        }
    }

    suspend fun exchangeCode(baseUrl: String, code: String): Tokens = token(
        baseUrl,
        FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("client_id", CLIENT_ID)
            .build(),
    )

    /** New access token from the refresh token. Throws HaException("invalid_grant") if it was revoked. */
    suspend fun refresh(baseUrl: String, refreshToken: String): Tokens = token(
        baseUrl,
        FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", CLIENT_ID)
            .build(),
    )

    private suspend fun token(baseUrl: String, body: FormBody): Tokens = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("${normalizeUrl(baseUrl)}/auth/token").post(body).build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (!response.isSuccessful) {
                val code = json?.optString("error")?.ifEmpty { null }
                if (code != null) throw HaException(code, json?.optString("error_description")?.ifEmpty { null } ?: code)
                throw IOException("HTTP ${response.code}")
            }
            json ?: throw IOException("Invalid response")
            Tokens(
                access = json.getString("access_token"),
                expiresAt = System.currentTimeMillis() + json.optLong("expires_in", 1800) * 1000,
                refresh = json.optString("refresh_token").ifEmpty { null },
            )
        }
    }

    private suspend fun postJson(baseUrl: String, path: String, body: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(normalizeUrl(baseUrl) + path)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrNull()
                when {
                    json != null -> json
                    // Too many failed logins: HA bans the address for a while.
                    response.code == 403 || response.code == 429 -> throw HaException("banned", "HTTP ${response.code}")
                    else -> throw IOException("HTTP ${response.code}")
                }
            }
        }

    class Session internal constructor(private val token: String) {
        internal lateinit var socket: WebSocket
        internal val authenticated = CompletableDeferred<Unit>()
        private val pending = ConcurrentHashMap<Int, CompletableDeferred<Any?>>()
        private val nextId = AtomicInteger(1)

        /** Send a command and return its `result` (JSONObject, JSONArray or null). */
        suspend fun call(type: String, params: JSONObject = JSONObject(), timeoutMs: Long = 30_000): Any? {
            val id = nextId.getAndIncrement()
            val deferred = CompletableDeferred<Any?>()
            pending[id] = deferred
            val msg = JSONObject(params.toString()).put("id", id).put("type", type)
            if (!socket.send(msg.toString())) {
                pending.remove(id)
                throw IOException("Connection closed")
            }
            try {
                return withTimeout(timeoutMs) { deferred.await() }
            } finally {
                pending.remove(id)
            }
        }

        internal val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val parsed = runCatching { JSONObject(text) }.getOrNull()
                if (parsed != null) {
                    handle(parsed)
                } else {
                    // Coalesced messages arrive as an array.
                    runCatching { JSONArray(text) }.getOrNull()?.let { arr ->
                        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let(::handle)
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                failAll(if (t is IOException) t else IOException(t.message, t))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                failAll(IOException("Connection closed"))
            }
        }

        private fun handle(msg: JSONObject) {
            when (msg.optString("type")) {
                "auth_required" -> socket.send(
                    JSONObject().put("type", "auth").put("access_token", token).toString()
                )
                "auth_ok" -> authenticated.complete(Unit)
                "auth_invalid" -> authenticated.completeExceptionally(
                    HaException("auth_invalid", msg.optString("message", "Invalid token"))
                )
                "result" -> {
                    val deferred = pending[msg.optInt("id")] ?: return
                    if (msg.optBoolean("success")) {
                        deferred.complete(if (msg.isNull("result")) null else msg.get("result"))
                    } else {
                        val err = msg.optJSONObject("error")
                        deferred.completeExceptionally(
                            HaException(
                                err?.optString("code") ?: "unknown_error",
                                err?.optString("message") ?: "Unknown error",
                            )
                        )
                    }
                }
            }
        }

        private fun failAll(error: IOException) {
            authenticated.completeExceptionally(error)
            pending.values.forEach { it.completeExceptionally(error) }
        }
    }

    companion object {
        /**
         * Identifies the app to Home Assistant (shown in Profile → Security → Refresh tokens).
         * The redirect URI is on the same host, so HA accepts it without fetching anything.
         */
        const val CLIENT_ID = "https://github.com/LabirintoDigitale/BCC/"
        const val REDIRECT_URI = "https://github.com/LabirintoDigitale/BCC/app"

        /** `ha.local:8123/` -> `http://ha.local:8123` */
        fun normalizeUrl(url: String): String {
            var u = url.trim().trimEnd('/')
            if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) u = "http://$u"
            return u
        }

        fun websocketUrl(baseUrl: String): String {
            val u = normalizeUrl(baseUrl)
            val ws = if (u.startsWith("https://", true)) "wss://" + u.substring(8) else "ws://" + u.substring(7)
            return "$ws/api/websocket"
        }
    }
}
