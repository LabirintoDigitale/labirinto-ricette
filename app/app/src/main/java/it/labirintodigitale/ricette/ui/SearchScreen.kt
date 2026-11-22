package it.labirintodigitale.ricette.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import it.labirintodigitale.ricette.R
import it.labirintodigitale.ricette.data.SearchResult

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(vm: MainViewModel, onOpen: (SearchResult) -> Unit, onBack: () -> Unit) {
    val search by vm.searchState.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }

    fun addIngredient() {
        val parts = input.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isNotEmpty()) {
            vm.setIngredients((search.ingredients + parts).distinct())
            input = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_recipes)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.search_intro), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.ingredients_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    trailingIcon = {
                        if (input.isNotBlank()) {
                            AssistChip(onClick = ::addIngredient, label = { Text(stringResource(R.string.add)) })
                        }
                    },
                )
                if (search.ingredients.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        search.ingredients.forEach { ing ->
                            InputChip(
                                selected = false,
                                onClick = { vm.setIngredients(search.ingredients - ing) },
                                label = { Text(ing) },
                                trailingIcon = { Icon(Icons.Filled.Close, null, Modifier.size(16.dp)) },
                            )
                        }
                    }
                }
                Button(
                    onClick = { addIngredient(); vm.runSearch() },
                    enabled = !search.searching && (search.ingredients.isNotEmpty() || input.isNotBlank()),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (search.searching) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(stringResource(R.string.search_button))
                }
                search.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }

            when {
                search.results.isNotEmpty() -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(170.dp),
                    contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(search.results, key = { it.url }) { result ->
                        ResultTile(result, onClick = { vm.remember(result); onOpen(result) })
                    }
                }
                search.done && !search.searching -> Box(Modifier.fillMaxSize().padding(32.dp), Alignment.TopCenter) {
                    Text(stringResource(R.string.no_results), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ResultTile(result: SearchResult, onClick: () -> Unit) {
    val color = siteColor(result.site)
    Box(
        Modifier
            .aspectRatio(0.82f)
            .clip(RoundedCornerShape(14.dp))
            .background(color)
            .clickable(onClick = onClick)
    ) {
        if (result.image.startsWith("http")) {
            AsyncImage(
                model = result.image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                .padding(10.dp)
        ) {
            Column {
                Text(
                    result.title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (result.site.isNotBlank()) {
                    Text(result.site, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
