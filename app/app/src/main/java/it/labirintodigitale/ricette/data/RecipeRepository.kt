package it.labirintodigitale.ricette.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Connection settings. Either [refreshToken] (login with username and password; the
 * password itself is never stored) or [token] (long-lived access token) is set.
 */
data class Settings(
    val url: String,
    val altUrl: String,
    val token: String = "",
    val refreshToken: String = "",
    val username: String = "",
) {
    val configured: Boolean get() = url.isNotBlank() && (token.isNotBlank() || refreshToken.isNotBlank())

    /** Addresses to try, in order (e.g. LAN first, then Nabu Casa / external). */
    val urls: List<String> get() = listOf(url, altUrl).filter { it.isNotBlank() }.distinct()
}

/** One recipe found by a search (`labirinto_ricette.search`). */
data class SearchResult(val title: String, val url: String, val site: String, val image: String) {
    companion object {
        fun fromJson(o: JSONObject) = SearchResult(
            title = o.optString("title"),
            url = o.optString("url"),
            site = o.optString("site"),
            image = o.optString("image"),
        )
    }
}

/** A to-do list the ingredients can be added to (`todo/lists`). */
data class TodoList(val entityId: String, val name: String) {
    companion object {
        fun fromJson(o: JSONObject) = TodoList(o.optString("entity_id"), o.optString("name"))
    }
}

/**
 * Favorites from Home Assistant with a local copy (SQLite + image files), so the
 * saved recipes open even without a connection. Online it can also search sites,
 * open a recipe and save or remove favorites.
 */
class RecipeRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val db = RecipeDb(context)
    private val client = HaClient()
    private val imageDir = File(context.filesDir, "images").apply { mkdirs() }

    @Volatile private var workingUrl: String? = null

    var settings: Settings
        get() = Settings(
            prefs.getString("url", "") ?: "",
            prefs.getString("alt_url", "") ?: "",
            prefs.getString("token", "") ?: "",
            prefs.getString("refresh_token", "") ?: "",
            prefs.getString("username", "") ?: "",
        )
        set(value) {
            prefs.edit()
                .putString("url", value.url.trim())
                .putString("alt_url", value.altUrl.trim())
                .putString("token", value.token.trim())
                .putString("refresh_token", value.refreshToken)
                .putString("username", value.username.trim())
                .remove("access_token")
                .remove("access_expires")
                .apply()
            workingUrl = null
        }

    fun imageFile(name: String): File = File(imageDir, name)

    fun hasImage(name: String?): Boolean = name != null && imageFile(name).isFile

    /** Local file for an image URL from a recipe, if it was downloaded; else null. */
    fun localImage(url: String): File? = localImageName(url)?.let { imageFile(it) }?.takeIf { it.isFile }

    suspend fun cached(): List<Favorite> = withContext(Dispatchers.IO) { db.load() }

    suspend fun lastSync(): Long? = withContext(Dispatchers.IO) { db.lastSync() }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        db.clear()
        imageDir.listFiles()?.forEach { it.delete() }
    }

    // ---------- login with username and password ----------

    sealed interface LoginResult {
        data class Ok(val settings: Settings, val tokens: HaClient.Tokens) : LoginResult
        data class NeedsCode(val pending: PendingLogin, val module: String?) : LoginResult
        data class Failed(val error: String, val pending: PendingLogin?) : LoginResult
    }

    data class PendingLogin(val settings: Settings, val baseUrl: String, val flowId: String)

    suspend fun login(s: Settings, username: String, password: String): LoginResult {
        var last: Exception? = null
        for (url in s.urls) {
            val step = try {
                client.login(url, username.trim(), password)
            } catch (e: HaException) {
                return LoginResult.Failed(e.code, null)
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                last = e
                continue
            }
            return finishStep(s.copy(username = username.trim()), url, step)
        }
        throw last ?: IllegalStateException("No URL")
    }

    suspend fun loginCode(pending: PendingLogin, code: String): LoginResult {
        val step = client.submitLogin(pending.baseUrl, pending.flowId, JSONObject().put("code", code.trim()))
        return finishStep(pending.settings, pending.baseUrl, step)
    }

    private suspend fun finishStep(s: Settings, url: String, step: HaClient.LoginStep): LoginResult =
        when (step) {
            is HaClient.LoginStep.NeedsCode ->
                LoginResult.NeedsCode(PendingLogin(s, url, step.flowId), step.module)
            is HaClient.LoginStep.Failed ->
                LoginResult.Failed(step.error, step.flowId?.let { PendingLogin(s, url, it) })
            is HaClient.LoginStep.Done -> {
                val tokens = client.exchangeCode(url, step.code)
                // Check that the integration is installed before keeping the login.
                client.session(url, tokens.access) { it.call("labirinto_ricette/favorites/list") }
                LoginResult.Ok(s.copy(token = "", refreshToken = tokens.refresh.orEmpty()), tokens)
            }
        }

    fun saveLogin(result: LoginResult.Ok) {
        settings = result.settings
        storeAccess(result.tokens)
    }

    private suspend fun accessToken(url: String): String {
        val s = settings
        if (s.token.isNotBlank()) return s.token
        val cached = prefs.getString("access_token", null)
        if (cached != null && prefs.getLong("access_expires", 0) > System.currentTimeMillis() + 60_000) {
            return cached
        }
        val tokens = try {
            client.refresh(url, s.refreshToken)
        } catch (e: HaException) {
            throw HaException("auth_invalid", e.message ?: e.code)
        }
        storeAccess(tokens)
        return tokens.access
    }

    private fun storeAccess(tokens: HaClient.Tokens) {
        prefs.edit()
            .putString("access_token", tokens.access)
            .putLong("access_expires", tokens.expiresAt)
            .apply()
    }

    // ---------- favorites ----------

    /** Check that [s] reaches Home Assistant and the integration is installed. */
    suspend fun test(s: Settings) {
        var last: Exception? = null
        for (url in s.urls) {
            try {
                client.session(url, s.token) { it.call("labirinto_ricette/favorites/list") }
                return
            } catch (e: HaException) {
                throw e
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("No URL")
    }

    /**
     * Download the favorites and their images, then update the local copy.
     * Throws if Home Assistant cannot be reached: the old copy is left untouched.
     */
    suspend fun sync(): List<Favorite> = withContext(Dispatchers.IO) {
        val (url, token, list) = withServer { it.call("labirinto_ricette/favorites/list") }
        val arr = (list as? JSONObject)?.optJSONArray("favorites") ?: JSONArray()
        val summaries = (0 until arr.length()).mapNotNull {
            arr.optJSONObject(it)?.let { o -> runCatching { FavoriteSummary.fromJson(o) }.getOrNull() }
        }
        val serverIds = summaries.map { it.id }.toSet()
        val known = db.ids()

        // The recipe is a fixed snapshot, but notes change: update them from the list
        // (sent by integration 0.12.1+; older versions have no "note" field).
        val notes = (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.takeIf { it.has("note") && it.has("id") }?.let { it.getString("id") to it.optString("note") }
        }.toMap()
        if (notes.isNotEmpty()) {
            for (local in db.load()) {
                val note = notes[local.id] ?: continue
                if (note != local.note) db.upsert(local.withNote(note))
            }
        }

        // Saved recipes are immutable snapshots: fetch only the ones not downloaded yet.
        for (summary in summaries) {
            if (summary.id in known) continue
            val full = client.session(url, token) { it.call("labirinto_ricette/favorites/get", JSONObject().put("fav_id", summary.id)) }
            val fav = Favorite.fromJson(full as JSONObject)
            downloadImages(url, fav)
            db.upsert(fav)
        }
        db.keepOnly(serverIds)
        db.markSynced(System.currentTimeMillis())
        pruneImages()
        db.load()
    }

    /** Search recipes by ingredients (online only). */
    suspend fun search(ingredients: List<String>, maxPerSite: Int = 8): List<SearchResult> =
        withContext(Dispatchers.IO) {
            val params = JSONObject()
                .put("domain", "labirinto_ricette")
                .put("service", "search")
                .put("service_data", JSONObject().put("ingredients", JSONArray(ingredients)).put("max_per_site", maxPerSite))
                .put("return_response", true)
            val (_, _, res) = withServer { it.call("call_service", params, timeoutMs = 90_000) }
            val response = (res as? JSONObject)?.optJSONObject("response") ?: JSONObject()
            val arr = response.optJSONArray("results") ?: JSONArray()
            (0 until arr.length()).mapNotNull {
                arr.optJSONObject(it)?.let { o -> runCatching { SearchResult.fromJson(o) }.getOrNull() }
            }
        }

    /** Full recipe from a URL on a configured site (online only). */
    suspend fun recipe(url: String): Recipe = withContext(Dispatchers.IO) {
        val (_, _, res) = withServer { it.call("labirinto_ricette/recipe", JSONObject().put("url", url), timeoutMs = 60_000) }
        Recipe.fromJson(res as JSONObject)
    }

    /** Save a recipe as favorite; its text and images are stored on the server and locally. */
    suspend fun addFavorite(result: SearchResult): Favorite = withContext(Dispatchers.IO) {
        val (url, token, res) = withServer {
            it.call(
                "labirinto_ricette/favorites/add",
                JSONObject().put("url", result.url).put("site", result.site)
                    .put("title", result.title).put("image", result.image),
                timeoutMs = 120_000,
            )
        }
        val id = (res as JSONObject).getString("id")
        val full = client.session(url, token) { it.call("labirinto_ricette/favorites/get", JSONObject().put("fav_id", id)) }
        val fav = Favorite.fromJson(full as JSONObject)
        downloadImages(url, fav)
        db.upsert(fav)
        pruneImages()
        fav
    }

    /** Save the personal notes of a favorite on the server and in the local copy. */
    suspend fun setNote(fav: Favorite, note: String) = withContext(Dispatchers.IO) {
        withServer {
            it.call("labirinto_ricette/favorites/note", JSONObject().put("fav_id", fav.id).put("note", note))
        }
        db.upsert(fav.withNote(note.trim()))
    }

    suspend fun removeFavorite(id: String) = withContext(Dispatchers.IO) {
        withServer { it.call("labirinto_ricette/favorites/remove", JSONObject().put("fav_id", id)) }
        db.delete(id)
        pruneImages()
    }

    /** True if [url] is already a saved favorite. */
    suspend fun isFavorite(url: String): Boolean = withContext(Dispatchers.IO) {
        db.load().any { it.url == url }
    }

    private suspend fun downloadImages(url: String, fav: Favorite) {
        for (name in fav.files) {
            if (!hasImage(name) && SAFE_NAME.matches(name)) {
                runCatching { client.download(url, IMG_PATH + name, null, imageFile(name)) }
            }
        }
    }

    private fun pruneImages() {
        val wanted = db.load().flatMap { it.files }.toSet()
        imageDir.listFiles()?.forEach { if (it.name !in wanted) it.delete() }
    }

    /** To-do lists that accept new items (online only). */
    suspend fun todoLists(): List<TodoList> = withContext(Dispatchers.IO) {
        val (_, _, res) = withServer { it.call("labirinto_ricette/todo/lists") }
        val arr = (res as? JSONObject)?.optJSONArray("lists") ?: JSONArray()
        (0 until arr.length()).mapNotNull {
            arr.optJSONObject(it)?.let { o -> runCatching { TodoList.fromJson(o) }.getOrNull() }
        }
    }

    /** Add [items] to a to-do list, with [note] as each item's note where supported. */
    suspend fun todoAdd(entityId: String, items: List<String>, note: String): Int =
        withContext(Dispatchers.IO) {
            val params = JSONObject()
                .put("entity_id", entityId)
                .put("items", JSONArray(items))
                .put("note", note)
            val (_, _, res) = withServer { it.call("labirinto_ricette/todo/add", params, timeoutMs = 60_000) }
            (res as? JSONObject)?.optInt("added") ?: 0
        }

    /** Run [block] on the first base URL that answers; returns that URL and the token used too. */
    private suspend fun <T> withServer(block: suspend (HaClient.Session) -> T): Triple<String, String, T> {
        val s = settings
        if (!s.configured) throw IllegalStateException("Not configured")
        val urls = (listOfNotNull(workingUrl) + s.urls).distinct()
        var last: Exception? = null
        for (url in urls) {
            try {
                val token = accessToken(url)
                val result = client.session(url, token, block)
                workingUrl = url
                return Triple(url, token, result)
            } catch (e: HaException) {
                workingUrl = url
                throw e
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                last = e
            }
        }
        throw last ?: IllegalStateException("No URL")
    }

    private companion object {
        val SAFE_NAME = Regex("^[A-Za-z0-9_.-]+$")
    }
}
