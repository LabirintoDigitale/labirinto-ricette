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

/** Coil model for a recipe image URL: the local file if downloaded, else the remote URL. */
fun imageModel(repo: RecipeRepository, url: String): Any? {
    if (url.isBlank()) return null
    localImageName(url)?.let { name -> return repo.imageFile(name).takeIf { it.isFile } }
    return if (url.startsWith("http")) url else null
}

/**
 * The recipe body: photo, times, ingredients (tappable to tick off) and numbered steps.
 * Used both for a saved favorite and for a recipe opened from search.
 */
@Composable
fun RecipeBody(recipe: Recipe, repo: RecipeRepository, accent: Color, modifier: Modifier = Modifier) {
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
