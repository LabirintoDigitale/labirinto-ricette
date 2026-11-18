package it.labirintodigitale.ricette.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import it.labirintodigitale.ricette.R
import it.labirintodigitale.ricette.data.Recipe
import it.labirintodigitale.ricette.data.SearchResult

/** A recipe opened from search: fetched online, with a heart to save it as favorite. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeScreen(
    result: SearchResult,
    state: UiState,
    vm: MainViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(state, vm, snackbar)
    val context = LocalContext.current
    val accent = siteColor(result.site)

    var recipe by remember { mutableStateOf<Recipe?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(result.url) {
        saved = vm.isFavorite(result.url)
        vm.loadRecipe(result.url).fold(
            onSuccess = { recipe = it },
            onFailure = { error = it.message },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(recipe?.title?.ifBlank { result.title } ?: result.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (result.url.startsWith("http")) {
                        IconButton(onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, result.url.toUri())) }
                        }) { Icon(Icons.Filled.OpenInBrowser, stringResource(R.string.open_on_site)) }
                    }
                    if (saving) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(
                            enabled = state.canEdit && !saved,
                            onClick = {
                                vm.addFavorite(result, onBusy = { saving = it }) {
                                    saved = true
                                    onSaved()
                                }
                            },
                        ) {
                            Icon(
                                if (saved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                stringResource(R.string.save_favorite),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            StatusBanner(state, onRetry = vm::refresh)
            when {
                recipe != null -> RecipeBody(recipe!!, vm.repo, accent, Modifier.padding(16.dp))
                error != null -> Box(Modifier.fillMaxSize().padding(32.dp), Alignment.TopCenter) {
                    Text(error!!, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
                }
                else -> Box(Modifier.fillMaxSize().padding(48.dp), Alignment.TopCenter) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
