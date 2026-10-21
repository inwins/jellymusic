package com.example.jellymusic.data

import com.example.jellymusic.jellyfin.BaseItem
import org.json.JSONArray
import org.json.JSONObject

fun BaseItem.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("type", type)
    .put("album", album)
    .put("albumArtist", albumArtist)
    .put("albumId", albumId)
    .put("parentId", parentId)
    .put("runTimeTicks", runTimeTicks)
    .put("trackNumber", trackNumber)
    .put("discNumber", discNumber)
    .put("container", container)
    .put("hasImage", hasImage)
    .put("hasLyrics", hasLyrics)
    .put("isFavorite", isFavorite)
    .put("playlistItemId", playlistItemId)
    .put("collectionType", collectionType)
    .put("childCount", childCount)
    .put("artists", JSONArray(artists))

fun baseItemFromJson(json: JSONObject): BaseItem {
    val artists = json.optJSONArray("artists")?.let { array ->
        (0 until array.length()).map { array.getString(it) }
    } ?: emptyList()
    return BaseItem(
        id = json.getString("id"),
        name = json.getString("name"),
        type = json.optString("type", "Audio"),
        isFolder = false,
        album = json.optString("album").takeIf { it.isNotBlank() },
        albumArtist = json.optString("albumArtist").takeIf { it.isNotBlank() },
        artists = artists,
        albumId = json.optString("albumId").takeIf { it.isNotBlank() },
        parentId = json.optString("parentId").takeIf { it.isNotBlank() },
        runTimeTicks = json.optLong("runTimeTicks").takeIf { it > 0 },
        trackNumber = json.optInt("trackNumber").takeIf { it > 0 },
        discNumber = json.optInt("discNumber").takeIf { it > 0 },
        container = json.optString("container").takeIf { it.isNotBlank() },
        hasImage = json.optBoolean("hasImage", false),
        hasLyrics = json.optBoolean("hasLyrics", false),
        isFavorite = json.optBoolean("isFavorite", false),
        playlistItemId = json.optString("playlistItemId").takeIf { it.isNotBlank() },
        collectionType = json.optString("collectionType").takeIf { it.isNotBlank() },
        childCount = json.optInt("childCount", -1).takeIf { it >= 0 },
    )
}