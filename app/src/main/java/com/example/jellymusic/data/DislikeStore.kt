package com.example.jellymusic.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray

/** Tracks "not interested" songs so they are excluded from recommendations. */
class DislikeStore(context: Context) {
    private val prefs = context.getSharedPreferences("jellymusic_dislikes", Context.MODE_PRIVATE)
    val ids = mutableStateListOf<String>()

    init {
        load()
    }

    fun isDisliked(itemId: String): Boolean = ids.contains(itemId)

    fun dislike(itemId: String) {
        if (!ids.contains(itemId)) {
            ids.add(itemId)
            save()
        }
    }

    fun undislike(itemId: String) {
        ids.remove(itemId)
        save()
    }

    private fun save() {
        runCatching {
            prefs.edit().putString(KEY, JSONArray(ids).toString()).apply()
        }
    }

    private fun load() {
        val raw = prefs.getString(KEY, null) ?: return
        runCatching {
            val array = JSONArray(raw)
            ids.clear()
            for (i in 0 until array.length()) ids.add(array.getString(i))
        }
    }

    private companion object {
        const val KEY = "ids"
    }
}
