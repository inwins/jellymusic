package io.github.inwins.jellymusic.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray

/** Tracks completed music-theory lessons. */
class TheoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("jellymusic_theory", Context.MODE_PRIVATE)
    val completed = mutableStateListOf<String>()

    init {
        load()
    }

    fun isCompleted(id: String): Boolean = completed.contains(id)

    fun toggle(id: String) {
        if (completed.contains(id)) completed.remove(id) else completed.add(id)
        save()
    }

    private fun save() {
        runCatching { prefs.edit().putString(KEY, JSONArray(completed).toString()).apply() }
    }

    private fun load() {
        val raw = prefs.getString(KEY, null) ?: return
        runCatching {
            val array = JSONArray(raw)
            completed.clear()
            for (i in 0 until array.length()) completed.add(array.getString(i))
        }
    }

    private companion object {
        const val KEY = "completed"
    }
}
