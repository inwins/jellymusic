package com.example.jellymusic.data

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import com.example.jellymusic.jellyfin.BaseItem
import com.example.jellymusic.jellyfin.JellyfinApi
import org.json.JSONArray
import org.json.JSONObject

private data class PendingFavorite(
    val itemId: String,
    val favorite: Boolean,
)

class FavoriteStore(context: Context) {
    private val prefs = context.getSharedPreferences("jellymusic_favorites", Context.MODE_PRIVATE)
    private val lock = Any()

    val overrides = mutableStateMapOf<String, Boolean>()
    val localItems = mutableStateMapOf<String, BaseItem>()
    private val pending = mutableListOf<PendingFavorite>()

    init {
        load()
    }

    fun isFavorite(item: BaseItem): Boolean = overrides[item.id] ?: item.isFavorite

    fun toggle(item: BaseItem): Boolean {
        val value = !isFavorite(item)
        overrides[item.id] = value
        if (value) {
            localItems[item.id] = item
        } else {
            localItems.remove(item.id)
        }
        synchronized(lock) {
            pending.removeAll { it.itemId == item.id }
            pending.add(PendingFavorite(item.id, value))
        }
        save()
        return value
    }

    fun pendingCount(): Int = synchronized(lock) { pending.size }

    fun mergeFavorites(serverItems: List<BaseItem>): List<BaseItem> {
        val merged = LinkedHashMap<String, BaseItem>()
        for (item in serverItems) {
            val favorite = overrides[item.id] ?: true
            if (favorite) merged[item.id] = item
        }
        for ((id, item) in localItems) {
            if (overrides[id] == true && !merged.containsKey(id)) {
                merged[id] = item
            }
        }
        return merged.values.toList()
    }

    suspend fun sync(api: JellyfinApi): Boolean {
        val ops = synchronized(lock) { pending.toList() }
        if (ops.isEmpty()) return true
        for (op in ops) {
            try {
                api.setFavorite(op.itemId, op.favorite)
                synchronized(lock) {
                    pending.removeAll { it.itemId == op.itemId && it.favorite == op.favorite }
                }
            } catch (_: Exception) {
                save()
                return false
            }
        }
        save()
        return true
    }

    private fun save() {
        try {
            val overridesJson = JSONObject()
            overrides.forEach { (id, value) -> overridesJson.put(id, value) }
            val itemsJson = JSONArray()
            localItems.values.forEach { itemsJson.put(it.toJson()) }
            val pendingJson = JSONArray()
            synchronized(lock) {
                pending.forEach { op ->
                    pendingJson.put(JSONObject().put("id", op.itemId).put("favorite", op.favorite))
                }
            }
            val root = JSONObject()
                .put("overrides", overridesJson)
                .put("items", itemsJson)
                .put("pending", pendingJson)
            prefs.edit().putString(KEY, root.toString()).apply()
        } catch (_: Exception) {
        }
    }

    private fun load() {
        val raw = prefs.getString(KEY, null) ?: return
        try {
            val root = JSONObject(raw)
            root.optJSONObject("overrides")?.let { overridesJson ->
                val keys = overridesJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    overrides[key] = overridesJson.optBoolean(key)
                }
            }
            root.optJSONArray("items")?.let { itemsJson ->
                for (i in 0 until itemsJson.length()) {
                    val item = baseItemFromJson(itemsJson.getJSONObject(i))
                    localItems[item.id] = item
                }
            }
            root.optJSONArray("pending")?.let { pendingJson ->
                synchronized(lock) {
                    pending.clear()
                    for (i in 0 until pendingJson.length()) {
                        val json = pendingJson.getJSONObject(i)
                        pending.add(
                            PendingFavorite(
                                itemId = json.getString("id"),
                                favorite = json.optBoolean("favorite"),
                            ),
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val KEY = "state"
    }
}