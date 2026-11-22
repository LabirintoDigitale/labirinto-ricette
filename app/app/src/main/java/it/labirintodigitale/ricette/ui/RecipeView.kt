package it.labirintodigitale.ricette.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import it.labirintodigitale.ricette.R
import it.labirintodigitale.ricette.data.Recipe
import it.labirintodigitale.ricette.data.RecipeRepository
import it.labirintodigitale.ricette.data.localImageName
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/** Coil model for a recipe image URL: the local file if downloaded, else the remote URL. */
fun imageModel(repo: RecipeRepository, url: String): Any? {
    if (url.isBlank()) return null
    localImageName(url)?.let { name -> return repo.imageFile(name).takeIf { it.isFile } }
    return if (url.startsWith("http")) url else null
}

/**
 * The recipe body: photo, times, ingredients (tappable to tick off), a button to send the
 * ones you still need to a shopping list, and numbered steps.
 * Used both for a saved favorite and for a recipe opened from search.
 */
@Composable
fun RecipeBody(recipe: Recipe, vm: MainViewModel, accent: Color, modifier: Modifier = Modifier) {
    val repo = vm.repo
    val checked = remember { mutableStateMapOf<Int, Boolean>() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val cover = imageModel(repo, recipe.image)
        if (cover != null) {
            AsyncImage(
                model = cover,
                contentDescription = recipe.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent),
            )
        }

        if (recipe.description.isNotBlank()) {
            Text(recipe.description, style = MaterialTheme.typography.bodyMedium)
        }

        val meta = buildList {
            recipe.yield_.takeIf { it.isNotBlank() }?.let { add(stringResource(R.string.servings, it)) }
            recipe.times["prep"]?.let { add(stringResource(R.string.time_prep, it)) }
            recipe.times["cook"]?.let { add(stringResource(R.string.time_cook, it)) }
            recipe.times["total"]?.let { add(stringResource(R.string.time_total, it)) }
        }
        if (meta.isNotEmpty()) {
            Text(meta.joinToString("  ·  "), style = MaterialTheme.typography.labelLarge, color = accent)
        }

        if (recipe.ingredients.isNotEmpty()) {
            Text(stringResource(R.string.ingredients), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.ingredients_tick_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column {
                recipe.ingredients.forEachIndexed { i, ingredient ->
                    val on = checked[i] == true
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { checked[i] = !on }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (on) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                            null,
                            tint = if (on) accent else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            ingredient,
                            modifier = Modifier.padding(start = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (on) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            val missing = recipe.ingredients.filterIndexed { i, _ -> checked[i] != true }
            ShoppingListButton(vm = vm, title = recipe.title, missing = missing, accent = accent)
        }

        if (recipe.steps.isNotEmpty()) {
            Text(stringResource(R.string.steps), style = MaterialTheme.typography.titleMedium)
            var number = 0
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                recipe.steps.forEach { step ->
                    if (step.heading != null) {
                        Text(step.heading, style = MaterialTheme.typography.titleSmall, color = accent)
                    }
                    if (!step.text.isNullOrBlank()) {
                        number++
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(accent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("$number", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            }
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(step.text, style = MaterialTheme.typography.bodyLarge)
                                step.images.forEach { img ->
                                    imageModel(repo, img)?.let { model ->
                                        AsyncImage(
                                            model = model,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .padding(top = 8.dp)
                                                .fillMaxWidth()
                                                .aspectRatio(1.6f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(accent.copy(alpha = 0.2f)),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * "Add to shopping list": lets you pick a to-do list and sends the ingredients
 * you did not tick off (the ones you still need). Online only.
 */
@Composable
private fun ShoppingListButton(vm: MainViewModel, title: String, missing: List<String>, accent: Color) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    var lists by remember { mutableStateOf<List<it.labirintodigitale.ricette.data.TodoList>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = {
            picking = true
            error = null
            lists = null
            scope.launch {
                vm.todoLists().fold(
                    onSuccess = { lists = it },
                    onFailure = { error = it.message },
                )
            }
        },
        enabled = state.canEdit && missing.isNotEmpty() && !busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.AddShoppingCart, null, Modifier.size(18.dp))
        Text(
            "  " + stringResource(R.string.add_to_list, missing.size),
            style = MaterialTheme.typography.labelLarge,
        )
    }

    if (picking) {
        AlertDialog(
            onDismissRequest = { if (!busy) picking = false },
            title = { Text(stringResource(R.string.choose_list)) },
            text = {
                when {
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                    lists == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("  " + stringResource(R.string.connecting))
                    }
                    lists!!.isEmpty() -> Text(stringResource(R.string.no_lists))
                    else -> Column {
                        val last = vm.lastList()
                        lists!!.sortedByDescending { it.entityId == last }.forEach { list ->
                            TextButton(
                                onClick = {
                                    vm.addToList(
                                        list, missing, title,
                                        onBusy = { busy = it },
                                        onDone = { picking = false },
                                    )
                                },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    list.name + if (list.entityId == last) "  ·  " + stringResource(R.string.last_used) else "",
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { picking = false }, enabled = !busy) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
