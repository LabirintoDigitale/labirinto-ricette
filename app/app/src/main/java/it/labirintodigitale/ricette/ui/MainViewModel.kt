package it.labirintodigitale.ricette.ui

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.labirintodigitale.ricette.R
import it.labirintodigitale.ricette.data.Favorite
import it.labirintodigitale.ricette.data.HaException
import it.labirintodigitale.ricette.data.RecipeRepository
import it.labirintodigitale.ricette.data.SearchResult
import it.labirintodigitale.ricette.data.TodoList
import it.labirintodigitale.ricette.data.Settings
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Mode { CONNECTING, ONLINE, OFFLINE, SETUP }

data class UiState(
    val favorites: List<Favorite> = emptyList(),
    val mode: Mode = Mode.CONNECTING,
    val lastSync: Long? = null,
    val syncing: Boolean = false,
    val offlineReason: String? = null,
    val message: String? = null,
) {
    val canEdit: Boolean get() = mode == Mode.ONLINE
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = RecipeRepository(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private var syncJob: Job? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (_state.value.mode == Mode.OFFLINE) refresh()
        }
    }

    init {
        viewModelScope.launch {
            val cached = repo.cached()
            val last = repo.lastSync()
            if (!repo.settings.configured) {
                _state.update { it.copy(favorites = cached, lastSync = last, mode = Mode.SETUP) }
            } else {
                _state.update { it.copy(favorites = cached, lastSync = last) }
                refresh()
            }
        }
        runCatching {
            app.getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(networkCallback)
        }
    }

    override fun onCleared() {
        runCatching {
            getApplication<Application>().getSystemService(ConnectivityManager::class.java)
                .unregisterNetworkCallback(networkCallback)
        }
    }

    fun refresh() {
        if (!repo.settings.configured || syncJob?.isActive == true) return
        syncJob = viewModelScope.launch {
            _state.update { it.copy(syncing = true) }
            try {
                val favs = repo.sync()
                _state.update {
                    it.copy(favorites = favs, mode = Mode.ONLINE, lastSync = repo.lastSync(), offlineReason = null)
                }
            } catch (e: Exception) {
                goOffline(e)
            } finally {
                _state.update { it.copy(syncing = false) }
            }
        }
    }

    private suspend fun reloadFavorites() {
        _state.update { it.copy(favorites = repo.cached(), lastSync = repo.lastSync()) }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    // ---------- settings / login ----------

    suspend fun saveToken(new: Settings): String? {
        try {
            repo.test(new)
        } catch (e: Exception) {
            return describe(e)
        }
        applySettings(new) { repo.settings = new }
        return null
    }

    sealed interface LoginOutcome {
        data object Done : LoginOutcome
        data class NeedsCode(val module: String?) : LoginOutcome
        data class Error(val message: String) : LoginOutcome
    }

    private var pendingLogin: RecipeRepository.PendingLogin? = null

    suspend fun login(url: String, altUrl: String, username: String, password: String): LoginOutcome =
        loginStep { repo.login(Settings(url, altUrl), username, password) }

    suspend fun loginCode(code: String): LoginOutcome {
        val pending = pendingLogin ?: return LoginOutcome.Error(str(R.string.err_login_restart))
        return loginStep { repo.loginCode(pending, code) }
    }

    private suspend fun loginStep(block: suspend () -> RecipeRepository.LoginResult): LoginOutcome {
        val result = try {
            block()
        } catch (e: Exception) {
            return LoginOutcome.Error(describe(e))
        }
        return when (result) {
            is RecipeRepository.LoginResult.Ok -> {
                pendingLogin = null
                applySettings(result.settings) { repo.saveLogin(result) }
                LoginOutcome.Done
            }
            is RecipeRepository.LoginResult.NeedsCode -> {
                pendingLogin = result.pending
                LoginOutcome.NeedsCode(result.module)
            }
            is RecipeRepository.LoginResult.Failed -> {
                pendingLogin = result.pending
                LoginOutcome.Error(loginError(result.error))
            }
        }
    }

    private suspend fun applySettings(new: Settings, save: () -> Unit) {
        val old = repo.settings
        if (old.url != new.url.trim() || (old.username != new.username.trim() && old.username.isNotEmpty())) {
            repo.clearCache()
            _state.update { it.copy(favorites = emptyList(), lastSync = null) }
        }
        save()
        _state.update { it.copy(mode = Mode.CONNECTING) }
        syncJob?.cancel()
        refresh()
    }

    private fun loginError(code: String): String = when (code) {
        "invalid_auth" -> str(R.string.err_invalid_auth)
        "invalid_code" -> str(R.string.err_invalid_code)
        "banned" -> str(R.string.err_banned)
        else -> str(R.string.err_login_failed) + " ($code)"
    }

    // ---------- search and favorites (online) ----------

    data class SearchUi(
        val ingredients: List<String> = emptyList(),
        val results: List<SearchResult> = emptyList(),
        val searching: Boolean = false,
        val error: String? = null,
        val done: Boolean = false,
    )

    private val _search = MutableStateFlow(SearchUi())
    val searchState: StateFlow<SearchUi> = _search

    /** Chosen search results, kept so the recipe screen can look one up by url. */
    private val chosen = HashMap<String, SearchResult>()
    fun remember(result: SearchResult) { chosen[result.url] = result }
    fun chosenResult(url: String): SearchResult? = chosen[url]

    fun setIngredients(list: List<String>) = _search.update { it.copy(ingredients = list) }

    fun runSearch() {
        val ingredients = _search.value.ingredients
        if (ingredients.isEmpty() || _search.value.searching) return
        viewModelScope.launch {
            _search.update { it.copy(searching = true, error = null) }
            val result = search(ingredients)
            _search.update {
                result.fold(
                    onSuccess = { list -> it.copy(results = list, searching = false, done = true) },
                    onFailure = { e -> it.copy(searching = false, done = true, error = e.message) },
                )
            }
        }
    }

    private suspend fun search(ingredients: List<String>): Result<List<SearchResult>> =
        runCatching { repo.search(ingredients) }.recoverCatching { throw asMessage(it) }

    suspend fun loadRecipe(url: String): Result<it.labirintodigitale.ricette.data.Recipe> =
        runCatching { repo.recipe(url) }.recoverCatching { throw asMessage(it) }

    suspend fun isFavorite(url: String): Boolean = runCatching { repo.isFavorite(url) }.getOrDefault(false)

    fun addFavorite(result: SearchResult, onBusy: (Boolean) -> Unit, onDone: (Favorite) -> Unit) = online(onBusy) {
        val fav = repo.addFavorite(result)
        reloadFavorites()
        onDone(fav)
    }

    fun saveNote(fav: Favorite, note: String, onBusy: (Boolean) -> Unit) = online(onBusy) {
        repo.setNote(fav, note)
        reloadFavorites()
        _state.update { it.copy(message = str(R.string.notes_saved)) }
    }

    fun removeFavorite(fav: Favorite, onDone: () -> Unit) = online {
        repo.removeFavorite(fav.id)
        _state.update { s -> s.copy(favorites = s.favorites.filterNot { it.id == fav.id }) }
        onDone()
    }

    // ---------- shopping list ----------

    suspend fun todoLists(): Result<List<TodoList>> =
        runCatching { repo.todoLists() }.recoverCatching { throw asMessage(it) }

    private val lastListKey = "last_todo_list"
    private val prefs = app.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE)
    fun lastList(): String? = prefs.getString(lastListKey, null)

    /** Add [items] to a to-do list; shows a confirmation message. */
    fun addToList(list: TodoList, items: List<String>, note: String, onBusy: (Boolean) -> Unit, onDone: () -> Unit) =
        online(onBusy) {
            val added = repo.todoAdd(list.entityId, items, note)
            prefs.edit().putString(lastListKey, list.entityId).apply()
            _state.update { it.copy(message = getApplication<Application>().getString(R.string.added_to_list, added, list.name)) }
            onDone()
        }

    private fun online(onBusy: (Boolean) -> Unit = {}, block: suspend () -> Unit) {
        if (!_state.value.canEdit) {
            _state.update { it.copy(message = str(R.string.read_only_offline)) }
            return
        }
        viewModelScope.launch {
            onBusy(true)
            try {
                block()
            } catch (e: HaException) {
                _state.update { it.copy(message = describe(e)) }
            } catch (e: Exception) {
                goOffline(e)
                _state.update { it.copy(message = str(R.string.save_failed_offline)) }
            } finally {
                onBusy(false)
            }
        }
    }

    private fun goOffline(e: Exception) {
        _state.update { it.copy(mode = Mode.OFFLINE, offlineReason = describe(e)) }
    }

    /** Wrap an exception as one carrying a user-facing message. */
    private fun asMessage(e: Throwable): Exception = Exception(describe(e as? Exception ?: Exception(e)))

    private fun describe(e: Exception): String = when {
        e is HaException && e.code == "auth_invalid" -> str(R.string.err_token)
        e is HaException && e.code == "banned" -> str(R.string.err_banned)
        e is HaException && e.code == "not_configured" -> str(R.string.err_integration_missing)
        e is HaException -> e.message ?: e.code
        e is java.net.UnknownHostException -> str(R.string.err_no_host)
        e is java.io.IOException -> str(R.string.err_unreachable)
        else -> e.message ?: e.toString()
    }

    private fun str(id: Int) = getApplication<Application>().getString(id)
}
