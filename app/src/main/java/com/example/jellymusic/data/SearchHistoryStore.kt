package com.example.jellymusic.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray

class SearchHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("jellymusic_search", Context.MODE_PRIVATE)

    val history = mutableStateListOf<String>()

    init {
        load()
    }

    fun add(query: String) {
        val value = query.trim()
        if (value.isEmpty()) return
        history.remove(value)
        history.add(0, value)
        while (history.size > MAX_ENTRIES) {
            history.removeAt(history.lastIndex)
        }
        save()
    }

    fun clear() {
        history.clear()
        save()
    }

    private fun save() {
        runCatching {
            prefs.edit().putString(KEY, JSONArray(history).toString()).apply()
        }
    }

    private fun load() {
        val raw = prefs.getString(KEY, null) ?: return
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                history.add(array.getString(i))
            }
        }
    }

    private companion object {
        const val KEY = "history"
        const val MAX_ENTRIES = 12
    }
}
