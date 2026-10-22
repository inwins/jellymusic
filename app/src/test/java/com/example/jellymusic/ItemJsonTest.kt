package com.example.jellymusic

import com.example.jellymusic.data.baseItemFromJson
import com.example.jellymusic.data.toJson
import com.example.jellymusic.jellyfin.BaseItem
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ItemJsonTest {
    @Test
    fun roundTripsAllFields() {
        val item = BaseItem(
            id = "item-1",
            name = "Song",
            type = "Audio",
            isFolder = false,
            album = "Album",
            albumArtist = "Album Artist",
            artists = listOf("Artist A", "Artist B"),
            albumId = "album-1",
            parentId = "parent-1",
            runTimeTicks = 123_456_789L,
            trackNumber = 3,
            discNumber = 2,
            container = "flac",
            hasImage = true,
            hasLyrics = true,
            isFavorite = true,
            playlistItemId = null,
            collectionType = null,
            childCount = 12,
        )

        val restored = baseItemFromJson(item.toJson())
        assertEquals(item, restored)
    }

    @Test
    fun roundTripsLocalUri() {
        val item = BaseItem(
            id = "local:abc",
            name = "Local song",
            type = "Audio",
            isFolder = false,
            localUri = "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/abc.mp3",
        )
        assertEquals(item, baseItemFromJson(item.toJson()))
    }

    @Test
    fun missingOptionalFieldsUseDefaults() {
        val restored = baseItemFromJson(JSONObject().put("id", "x").put("name", "N"))

        assertEquals("x", restored.id)
        assertEquals("N", restored.name)
        assertEquals("Audio", restored.type)
        assertEquals(false, restored.isFolder)
        assertEquals(emptyList<String>(), restored.artists)
        assertNull(restored.album)
        assertNull(restored.container)
        assertNull(restored.runTimeTicks)
        assertNull(restored.trackNumber)
        assertNull(restored.childCount)
    }

    @Test
    fun dropsNonPositiveNumericFields() {
        val restored = baseItemFromJson(
            JSONObject()
                .put("id", "x")
                .put("name", "N")
                .put("runTimeTicks", 0)
                .put("trackNumber", 0)
                .put("discNumber", -1)
                .put("childCount", -1),
        )

        assertNull(restored.runTimeTicks)
        assertNull(restored.trackNumber)
        assertNull(restored.discNumber)
        assertNull(restored.childCount)
    }

    @Test
    fun roundTripsEmptyOptionalFields() {
        val item = BaseItem(id = "1", name = "Song", type = "Audio", isFolder = false)
        assertEquals(item, baseItemFromJson(item.toJson()))
    }

    @Test
    fun trackArtistFallsBackToAlbumArtist() {
        val item = BaseItem(
            id = "1",
            name = "Song",
            type = "Audio",
            isFolder = false,
            albumArtist = "Album Artist",
        )
        assertEquals("Album Artist", item.trackArtist)
    }

    @Test
    fun trackArtistJoinsArtists() {
        val item = BaseItem(
            id = "1",
            name = "Song",
            type = "Audio",
            isFolder = false,
            albumArtist = "Album Artist",
            artists = listOf("A", "B"),
        )
        assertEquals("A, B", item.trackArtist)
    }
}
