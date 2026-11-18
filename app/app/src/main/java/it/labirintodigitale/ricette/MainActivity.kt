package it.labirintodigitale.ricette

import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.labirintodigitale.ricette.ui.FavoriteDetailScreen
import it.labirintodigitale.ricette.ui.FavoritesScreen
import it.labirintodigitale.ricette.ui.MainViewModel
import it.labirintodigitale.ricette.ui.Mode
import it.labirintodigitale.ricette.ui.RecipeScreen
import it.labirintodigitale.ricette.ui.SearchScreen
import it.labirintodigitale.ricette.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { RicetteTheme { App(vm) } }
    }

    override fun onStart() {
        super.onStart()
        if (vm.state.value.mode == Mode.OFFLINE) vm.refresh()
    }
}

private sealed interface Screen {
    data object Favorites : Screen
    data class Detail(val id: String) : Screen
    data object Search : Screen
    data class Recipe(val url: String) : Screen
    data object Settings : Screen
}

private fun Screen.key(): String = when (this) {
    Screen.Favorites -> "favorites"
    is Screen.Detail -> "fav:$id"
    Screen.Search -> "search"
    is Screen.Recipe -> "recipe:" + Uri.encode(url)
    Screen.Settings -> "settings"
}

private fun screenOf(key: String): Screen = when {
    key.startsWith("fav:") -> Screen.Detail(key.removePrefix("fav:"))
    key.startsWith("recipe:") -> Screen.Recipe(Uri.decode(key.removePrefix("recipe:")))
    key == "search" -> Screen.Search
    key == "settings" -> Screen.Settings
    else -> Screen.Favorites
}

@Composable
private fun App(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var stack by rememberSaveable { mutableStateOf(listOf("favorites")) }
    val screen = screenOf(stack.last())
    fun push(s: Screen) { stack = stack + s.key() }
    fun pop() { if (stack.size > 1) stack = stack.dropLast(1) }

    BackHandler(enabled = stack.size > 1) { pop() }

    if (state.mode == Mode.SETUP && screen != Screen.Settings) {
        SettingsScreen(vm, firstRun = true, onDone = {}, onBack = null)
        return
    }

    when (screen) {
        Screen.Favorites -> FavoritesScreen(
            state = state,
            vm = vm,
            onOpen = { push(Screen.Detail(it.id)) },
            onSearch = { push(Screen.Search) },
            onSettings = { push(Screen.Settings) },
        )
        is Screen.Detail -> {
            val fav = state.favorites.find { it.id == screen.id }
            if (fav == null) LaunchedEffect(screen) { pop() }
            else FavoriteDetailScreen(fav = fav, state = state, vm = vm, onBack = ::pop)
        }
        Screen.Search -> SearchScreen(
            vm = vm,
            onOpen = { push(Screen.Recipe(it.url)) },
            onBack = ::pop,
        )
        is Screen.Recipe -> {
            val result = vm.chosenResult(screen.url)
            if (result == null) LaunchedEffect(screen) { pop() }
            else RecipeScreen(
                result = result,
                state = state,
                vm = vm,
                onBack = ::pop,
                // After saving, go back to search; the new favorite is on the list.
                onSaved = {},
            )
        }
        Screen.Settings -> SettingsScreen(vm, firstRun = false, onDone = ::pop, onBack = ::pop)
    }
}

@Composable
private fun RicetteTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFFFFB74D))
        else -> lightColorScheme(primary = Color(0xFFE65100))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
