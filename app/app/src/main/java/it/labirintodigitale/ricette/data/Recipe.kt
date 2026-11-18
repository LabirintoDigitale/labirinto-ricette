package it.labirintodigitale.ricette.data

import org.json.JSONArray
import org.json.JSONObject

/** One step of a recipe: either a section heading, or text with optional images. */
data class Step(val heading: String?, val text: String?, val images: List<String>) {
    companion object {
        fun fromJson(o: JSONObject): Step {
            val imgs = o.optJSONArray("images")?.let { a ->
                (0 until a.length()).mapNotNull { a.optString(it).ifEmpty { null } }
            } ?: emptyList()
            return Step(
                heading = o.optStringOrNull("heading"),
                text = o.optStringOrNull("text"),
                images = imgs,
            )
        }
    }
}

/** A recipe as returned by the integration (`labirinto_ricette/recipe` or inside a favorite). */
data class Recipe(
    val title: String,
    val description: String,
    val image: String,
    val yield_: String,
    val times: Map<String, Int>,
    val ingredients: List<String>,
    val steps: List<Step>,
) {
    companion object {
        fun fromJson(o: JSONObject): Recipe {
            val times = o.optJSONObject("times")?.let { t ->
                t.keys().asSequence().associateWith { t.optInt(it) }.filterValues { it > 0 }
            } ?: emptyMap()
            return Recipe(
                title = o.optString("title"),
                description = o.optString("description"),
                image = o.optString("image"),
                yield_ = o.optString("yield"),
                times = times,
                ingredients = o.optJSONArray("ingredients").toStringList(),
                steps = o.optJSONArray("steps")?.let { a ->
                    (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(Step::fromJson) }
                } ?: emptyList(),
            )
        }
    }
}

/** Short entry shown in the favorites grid (`favorites/list`). */
data class FavoriteSummary(
    val id: String,
    val url: String,
    val title: String,
    val site: String,
    val image: String,
    val savedAt: String,
) {
    companion object {
        fun fromJson(o: JSONObject) = FavoriteSummary(
            id = o.getString("id"),
            url = o.optString("url"),
            title = o.optString("title"),
            site = o.optString("site"),
            image = o.optString("image"),
            savedAt = o.optString("saved_at"),
        )
    }
}

/** Full saved recipe (`favorites/get`): the snapshot kept for offline reading. */
data class Favorite(
    val id: String,
    val url: String,
    val site: String,
    val title: String,
    val savedAt: String,
    val files: List<String>,
    val recipe: Recipe,
    val raw: JSONObject,
) {
    companion object {
        fun fromJson(o: JSONObject) = Favorite(
            id = o.getString("id"),
            url = o.optString("url"),
            site = o.optString("site"),
            title = o.optString("title"),
            savedAt = o.optString("saved_at"),
            files = o.optJSONArray("files").toStringList(),
            recipe = Recipe.fromJson(o.getJSONObject("recipe")),
            raw = o,
        )
    }
}

internal fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

internal fun JSONArray?.toStringList(): List<String> =
    this?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).ifEmpty { null } } } ?: emptyList()

/** File name of a local image URL ("/api/labirinto_ricette/img/ab12.jpg" -> "ab12.jpg"), else null. */
fun localImageName(url: String): String? =
    if (url.startsWith(IMG_PATH)) url.substringAfterLast('/').takeIf { it.isNotEmpty() } else null

const val IMG_PATH = "/api/labirinto_ricette/img/"
