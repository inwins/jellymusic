package com.example.jellymusic

import com.example.jellymusic.data.baseItemFromJson
import com.example.jellymusic.data.toJson
import com.example.jellymusic.jellyfin.BaseItem
import org.junit.Assert.assertEquals
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
