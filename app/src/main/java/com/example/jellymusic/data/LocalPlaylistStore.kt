package com.example.jellymusic.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.jellymusic.jellyfin.BaseItem
import com.example.jellymusic.jellyfin.JellyfinApi
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class LocalPlaylist(
    val id: String,
    name: String,
    serverId: String? = null,
    val items: MutableList<BaseItem> = mutableStateListOf(),
    syncedItemIds: Set<String> = emptySet(),
) {
    var name by mutableStateOf(name)
    var serverId by mutableStateOf(serverId)
    var syncedItemIds by mutableStateOf(syncedItemIds)
    var removedItemIds by mutableStateOf<Set<String>>(emptySet())
    var needsRename by mutableStateOf(false)

    val pendingSync: Boolean
        get() = serverId == null ||
            needsRename ||
            removedItemIds.isNotEmpty() ||
            items.any { it.id !in syncedItemIds }
}

class LocalPlaylistStore(context: Context) {
    private val prefs = context.getSharedPreferences("jellymusic_playlists", Context.MODE_PRIVATE)

    val playlists = mutableStateListOf<LocalPlaylist>()
    private val deletions = mutableStateListOf<String>()

    var lastError by mutableStateOf<String?>(null)
        private set

    init {
        load()
    }

    fun get(id: String): LocalPlaylist? = playlists.firstOrNull { it.id == id }

    fun create(name: String): LocalPlaylist {
        val playlist = LocalPlaylist(id = UUID.randomUUID().toString(), name = name)
        playlists.add(playlist)
        save()
        return playlist
    }

    fun rename(id: String, name: String) {
        val playlist = get(id) ?: return
        if (playlist.name == name) return
        playlist.name = name
        playlist.needsRename = true
        save()
    }

    fun delete(id: String) {
        val playlist = get(id) ?: return
        playlist.serverId?.let { deletions.add(it) }
        playlists.remove(playlist)
        save()
    }

    fun addItem(playlistId: String, item: BaseItem): Boolean {
        val playlist = get(playlistId) ?: return false
        if (playlist.items.any { it.id == item.id }) return false
        playlist.items.add(item)
        save()
        return true
    }

    fun removeItem(playlistId: String, itemId: String) {
        val playlist = get(playlistId) ?: return
        playlist.items.removeAll { it.id == itemId }
        if (itemId in playlist.syncedItemIds) {
            playlist.removedItemIds = playlist.removedItemIds + itemId
        }
        save()
    }

    fun pendingCount(): Int = playlists.count { it.pendingSync } + deletions.size

    suspend fun sync(api: JellyfinApi): Boolean {
        lastError = null
        for (serverId in deletions.toList()) {
            try {
                api.deleteItem(serverId)
                deletions.remove(serverId)
            } catch (e: Exception) {
                lastError = e.message
                save()
                return false
            }
        }

        for (playlist in playlists.toList()) {
            try {
                val serverId = playlist.serverId
                if (serverId == null) {
                    if (playlist.items.isEmpty()) {
                        playlist.needsRename = false
                        continue
                    }
                    val createdId = api.createPlaylist(playlist.name, playlist.items.map { it.id })
                    playlist.serverId = createdId
                    playlist.syncedItemIds = playlist.items.map { it.id }.toSet()
                    playlist.removedItemIds = emptySet()
                    playlist.needsRename = false
                } else {
                    if (playlist.needsRename) {
                        api.updatePlaylistName(serverId, playlist.name)
                        playlist.needsRename = false
                    }
                    if (playlist.removedItemIds.isNotEmpty()) {
                        val serverItems = api.getPlaylistTracks(serverId, fresh = true)
                        val entryIds = serverItems
                            .filter { it.id in playlist.removedItemIds }
                            .mapNotNull { it.playlistItemId }
                        if (entryIds.isNotEmpty()) {
                            api.removeFromPlaylist(serverId, entryIds)
                        }
                        playlist.syncedItemIds = playlist.syncedItemIds - playlist.removedItemIds
                        playlist.removedItemIds = emptySet()
                    }
                    val toAdd = playlist.items
                        .map { it.id }
                        .filter { it !in playlist.syncedItemIds && !it.startsWith("local:") }
                    if (toAdd.isNotEmpty()) {
                        api.addToPlaylist(serverId, toAdd)
                        playlist.syncedItemIds = playlist.syncedItemIds + toAdd
                    }
                }
            } catch (e: Exception) {
                lastError = e.message
                save()
                return false
            }
        }
        save()
        return true
    }

    private fun save() {
        try {
            val playlistsJson = JSONArray()
            playlists.forEach { playlist ->
                val itemsJson = JSONArray()
                playlist.items.forEach { itemsJson.put(it.toJson()) }
                playlistsJson.put(
                    JSONObject()
                        .put("id", playlist.id)
                        .put("name", playlist.name)
                        .put("serverId", playlist.serverId)
                        .put("needsRename", playlist.needsRename)
                        .put("syncedItemIds", JSONArray(playlist.syncedItemIds.toList()))
                        .put("removedItemIds", JSONArray(playlist.removedItemIds.toList()))
                        .put("items", itemsJson),
                )
            }
            val root = JSONObject()
                .put("playlists", playlistsJson)
                .put("deletions", JSONArray(deletions.toList()))
            prefs.edit().putString(KEY, root.toString()).apply()
        } catch (_: Exception) {
        }
    }

    private fun load() {
        val raw = prefs.getString(KEY, null) ?: return
        try {
            if (raw.trimStart().startsWith("[")) {
                // Legacy format: a bare array of playlists.
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    playlists.add(playlistFrom(array.getJSONObject(i), legacy = true))
                }
                return
            }
            val root = JSONObject(raw)
            root.optJSONArray("playlists")?.let { array ->
                for (i in 0 until array.length()) {
                    playlists.add(playlistFrom(array.getJSONObject(i), legacy = false))
                }
            }
            root.optJSONArray("deletions")?.let { array ->
                for (i in 0 until array.length()) {
                    deletions.add(array.getString(i))
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun playlistFrom(json: JSONObject, legacy: Boolean): LocalPlaylist {
        val itemsJson = json.optJSONArray("items")
        val items = mutableListOf<BaseItem>()
        if (itemsJson != null) {
            for (j in 0 until itemsJson.length()) {
                items.add(baseItemFromJson(itemsJson.getJSONObject(j)))
            }
        }
        val syncedIds: Set<String> = if (legacy) {
            val syncedCount = json.optInt("syncedCount", 0)
            items.take(syncedCount).map { it.id }.toSet()
        } else {
            json.optJSONArray("syncedItemIds")?.let { array ->
                (0 until array.length()).map { array.getString(it) }.toSet()
            } ?: emptySet()
        }
        val removedIds: Set<String> = json.optJSONArray("removedItemIds")?.let { array ->
            (0 until array.length()).map { array.getString(it) }.toSet()
        } ?: emptySet()
        return LocalPlaylist(
            id = json.getString("id"),
            name = json.optString("name"),
            serverId = json.optString("serverId").takeIf { it.isNotBlank() },
            items = items,
            syncedItemIds = syncedIds,
        ).apply {
            removedItemIds = removedIds
            needsRename = json.optBoolean("needsRename", false)
        }
    }

    private companion object {
        const val KEY = "playlists"
    }
}