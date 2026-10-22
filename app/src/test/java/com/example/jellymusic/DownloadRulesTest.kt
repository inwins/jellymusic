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
    private fun item(id: String = "abc", container: String? = null) = BaseItem(
        id = id,
        name = "Song",
        type = "Audio",
        isFolder = false,
        container = container,
    )

    @Test
    fun usesAllowedContainerAsExtension() {
        assertEquals("abc.flac", downloadFileName(item(container = "flac")))
        assertEquals("abc.mp3", downloadFileName(item(container = "MP3")))
    }

    @Test
    fun stripsContainerParameters() {
        assertEquals("abc.mp3", downloadFileName(item(container = "mp3,128")))
        assertEquals("abc.flac", downloadFileName(item(container = "flac 24bit")))
    }

    @Test
    fun fallsBackToAudioForUnknownOrMissingContainer() {
        assertEquals("abc.audio", downloadFileName(item(container = "zip")))
        assertEquals("abc.audio", downloadFileName(item(container = null)))
        assertEquals("abc.audio", downloadFileName(item(container = "")))
    }

    @Test
    fun preservesItemId() {
        assertEquals("0f9a-1.mp3", downloadFileName(item(id = "0f9a-1", container = "mp3")))
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
