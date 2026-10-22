package com.example.jellymusic.data

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONObject

/** Local 0-5 star ratings (Jellyfin's song API has no numeric rating endpoint). */
class RatingStore(context: Context) {
    private val prefs = context.getSharedPreferences("jellymusic_ratings", Context.MODE_PRIVATE)
    val ratings = mutableStateMapOf<String, Int>()

    init {
        load()
    }

    fun rating(itemId: String): Int = ratings[itemId] ?: 0

    fun set(itemId: String, stars: Int) {
        if (stars <= 0) ratings.remove(itemId) else ratings[itemId] = stars.coerceIn(1, 5)
        save()
    }

    private fun save() {
        runCatching {
            val json = JSONObject()
            ratings.forEach { (id, value) -> json.put(id, value) }
            prefs.edit().putString(KEY, json.toString()).apply()
        }
    }

    private fun load() {
        val raw = prefs.getString(KEY, null) ?: return
        runCatching {
            val json = JSONObject(raw)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                ratings[key] = json.optInt(key)
            }
        }
    }

    private companion object {
        const val KEY = "ratings"
    }
}
