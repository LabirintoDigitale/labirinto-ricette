package it.labirintodigitale.ricette.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

/**
 * Local copy of the saved recipes, so favorites open without a connection.
 *
 * Each favorite is stored as the JSON object returned by Home Assistant
 * (`favorites/get`), with its image URLs already pointing at local files.
 */
class RecipeDb(context: Context) : SQLiteOpenHelper(context, "ricette.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE favorites (id TEXT PRIMARY KEY, saved_at TEXT, json TEXT NOT NULL)")
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun load(): List<Favorite> {
        val out = ArrayList<Favorite>()
        readableDatabase.rawQuery("SELECT json FROM favorites ORDER BY saved_at DESC", null).use { c ->
            while (c.moveToNext()) {
                runCatching { Favorite.fromJson(JSONObject(c.getString(0))) }.onSuccess(out::add)
            }
        }
        return out
    }

    fun ids(): Set<String> {
        val out = HashSet<String>()
        readableDatabase.rawQuery("SELECT id FROM favorites", null).use { c ->
            while (c.moveToNext()) out.add(c.getString(0))
        }
        return out
    }

    fun upsert(fav: Favorite) {
        writableDatabase.insertWithOnConflict("favorites", null, values(fav), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun delete(id: String) {
        writableDatabase.delete("favorites", "id = ?", arrayOf(id))
    }

    /** Remove local favorites whose id is not in [keep] (deleted on the server). */
    fun keepOnly(keep: Set<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            readableDatabase.rawQuery("SELECT id FROM favorites", null).use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    if (id !in keep) db.delete("favorites", "id = ?", arrayOf(id))
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun markSynced(syncedAt: Long) {
        writableDatabase.insertWithOnConflict(
            "meta", null,
            ContentValues().apply { put("key", "last_sync"); put("value", syncedAt.toString()) },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun lastSync(): Long? =
        readableDatabase.rawQuery("SELECT value FROM meta WHERE key = 'last_sync'", null).use { c ->
            if (c.moveToFirst()) c.getString(0)?.toLongOrNull() else null
        }

    fun clear() {
        writableDatabase.delete("favorites", null, null)
        writableDatabase.delete("meta", null, null)
    }

    private fun values(fav: Favorite) = ContentValues().apply {
        put("id", fav.id)
        put("saved_at", fav.savedAt)
        put("json", fav.raw.toString())
    }
}
