package com.example.jellymusic.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class PlaybackReport(
    val id: Long,
    val type: String,
    val itemId: String,
    val positionMs: Long,
)

/** Queues Jellyfin playback reports so offline listens can be sent later. */
class PlaybackReportStore(context: Context) {
    private val prefs = context.getSharedPreferences("jellymusic_reports", Context.MODE_PRIVATE)
    private val lock = Any()
    private var sequence = 0L

    fun enqueue(type: String, itemId: String, positionMs: Long) {
        synchronized(lock) {
            val list = load().toMutableList()
            list.add(PlaybackReport(System.currentTimeMillis() * 1000 + sequence++, type, itemId, positionMs))
            save(list)
        }
    }

    fun peekAll(): List<PlaybackReport> = synchronized(lock) { load() }

    fun replace(list: List<PlaybackReport>) = synchronized(lock) { save(list) }

    fun size(): Int = synchronized(lock) { load().size }

    private fun load(): List<PlaybackReport> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val json = array.getJSONObject(index)
                PlaybackReport(
                    id = json.optLong("id"),
                    type = json.optString("type"),
                    itemId = json.optString("itemId"),
                    positionMs = json.optLong("positionMs"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun save(list: List<PlaybackReport>) {
        runCatching {
            val array = JSONArray()
            list.forEach { report ->
                array.put(
                    JSONObject()
                        .put("id", report.id)
                        .put("type", report.type)
                        .put("itemId", report.itemId)
                        .put("positionMs", report.positionMs),
                )
            }
            prefs.edit().putString(KEY, array.toString()).apply()
        }
    }

    private companion object {
        const val KEY = "pending"
    }
}
