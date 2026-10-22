package com.example.jellymusic.jellyfin

import org.json.JSONObject

data class BaseItem(
    val id: String,
    val name: String,
    val type: String,
    val isFolder: Boolean,
    val album: String? = null,
    val albumArtist: String? = null,
    val artists: List<String> = emptyList(),
    val albumId: String? = null,
    val parentId: String? = null,
    val runTimeTicks: Long? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val container: String? = null,
    val hasImage: Boolean = false,
    val hasLyrics: Boolean = false,
    val isFavorite: Boolean = false,
    val collectionType: String? = null,
    val playlistItemId: String? = null,
    val childCount: Int? = null,
    val localUri: String? = null,
) {
    val durationMs: Long? get() = runTimeTicks?.div(10_000)
    val primaryArtist: String? get() = albumArtist ?: artists.firstOrNull()
    val trackArtist: String? get() = artists.joinToString(", ").ifBlank { albumArtist }
}

fun parseBaseItem(json: JSONObject): BaseItem {
    val imageTags = json.optJSONObject("ImageTags")
    return BaseItem(
        id = json.getString("Id"),
        name = json.getString("Name"),
        type = json.getString("Type"),
        isFolder = json.optBoolean("IsFolder", false),
        album = json.optString("Album").takeIf { it.isNotBlank() },
        albumArtist = json.optString("AlbumArtist").takeIf { it.isNotBlank() },
        artists = json.optJSONArray("Artists")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        } ?: emptyList(),
        albumId = json.optString("AlbumId").takeIf { it.isNotBlank() },
        parentId = json.optString("ParentId").takeIf { it.isNotBlank() },
        runTimeTicks = json.optLong("RunTimeTicks").takeIf { it > 0 },
        trackNumber = json.optInt("IndexNumber").takeIf { it > 0 },
        discNumber = json.optInt("ParentIndexNumber").takeIf { it > 0 },
        container = json.optString("Container").takeIf { it.isNotBlank() },
        hasImage = imageTags?.has("Primary") == true,
        hasLyrics = json.optBoolean("HasLyrics", false),
        isFavorite = json.optJSONObject("UserData")?.optBoolean("IsFavorite") ?: false,
        collectionType = json.optString("CollectionType").takeIf { it.isNotBlank() },
        playlistItemId = json.optString("PlaylistItemId").takeIf { it.isNotBlank() },
        childCount = json.optInt("ChildCount").takeIf { it >= 0 },
    )
}

data class LyricLine(
    val text: String,
    val startMs: Long?,
)