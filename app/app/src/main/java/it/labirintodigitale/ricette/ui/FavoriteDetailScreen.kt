package it.labirintodigitale.ricette.ui

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import it.labirintodigitale.ricette.R
import it.labirintodigitale.ricette.data.Favorite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteDetailScreen(
    fav: Favorite,
    state: UiState,
    vm: MainViewModel,
    onBack: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(state, vm, snackbar)
    val context = LocalContext.current
    var confirmRemove by remember { mutableStateOf(false) }
    val accent = siteColor(fav.site)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fav.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (fav.url.startsWith("http")) {
                        IconButton(onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, fav.url.toUri()))
                            }
                        }) { Icon(Icons.Filled.OpenInBrowser, stringResource(R.string.open_on_site)) }
                    }
                    if (state.canEdit) {
                        IconButton(onClick = { confirmRemove = true }) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.remove_favorite))
                        }
                    } else {
                        Icon(Icons.Filled.Favorite, null, Modifier.padding(12.dp))
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
            RecipeBody(fav.recipe, vm.repo, accent, Modifier.padding(16.dp))
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.remove_title)) },
            text = { Text(stringResource(R.string.remove_text, fav.title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    vm.removeFavorite(fav, onDone = onBack)
                }) { Text(stringResource(R.string.remove_favorite)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
