package com.example.jellymusic

import com.example.jellymusic.data.STALE_PART_AGE_MS
import com.example.jellymusic.data.downloadFileName
import com.example.jellymusic.data.isStalePartFile
import com.example.jellymusic.jellyfin.BaseItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRulesTest {
    private fun item(
        id: String = "abc",
        container: String? = null,
        name: String = "Song",
        albumArtist: String? = null,
    ) = BaseItem(
        id = id,
        name = name,
        type = "Audio",
        isFolder = false,
        container = container,
        albumArtist = albumArtist,
    )

    @Test
    fun usesAllowedContainerAsExtension() {
        assertEquals("Song [abc].flac", downloadFileName(item(container = "flac")))
        assertEquals("Song [abc].mp3", downloadFileName(item(container = "MP3")))
    }

    @Test
    fun stripsContainerParameters() {
        assertEquals("Song [abc].mp3", downloadFileName(item(container = "mp3,128")))
        assertEquals("Song [abc].flac", downloadFileName(item(container = "flac 24bit")))
    }

    @Test
    fun fallsBackToAudioForUnknownOrMissingContainer() {
        assertEquals("Song [abc].audio", downloadFileName(item(container = "zip")))
        assertEquals("Song [abc].audio", downloadFileName(item(container = null)))
        assertEquals("Song [abc].audio", downloadFileName(item(container = "")))
    }

    @Test
    fun preservesItemId() {
        assertEquals("Song [0f9a-1].mp3", downloadFileName(item(id = "0f9a-1", container = "mp3")))
    }

    @Test
    fun includesArtistAndSanitizesInvalidCharacters() {
        assertEquals(
            "AC_DC - A_B_ C_ [abc].mp3",
            downloadFileName(
                item(name = "A/B: C?", albumArtist = "AC/DC", container = "mp3"),
            ),
        )
    }

    @Test
    fun truncatesLongNames() {
        val long = "长".repeat(120)
        val name = downloadFileName(item(name = long, albumArtist = long, container = "mp3"))
        val base = name.substringBefore(" [abc].mp3")
        assertTrue(base.length <= 60)
        assertTrue(name.endsWith(" [abc].mp3"))
    }

    @Test
    fun partFileOlderThanAgeIsStale() {
        val now = 10_000_000L
        assertTrue(isStalePartFile(now - STALE_PART_AGE_MS - 1, now))
    }

    @Test
    fun recentPartFileIsNotStale() {
        val now = 10_000_000L
        assertFalse(isStalePartFile(now - 1_000L, now))
        assertFalse(isStalePartFile(now - STALE_PART_AGE_MS, now))
    }
}
