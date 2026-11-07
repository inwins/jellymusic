package io.github.inwins.jellymusic

import io.github.inwins.jellymusic.data.MediaState
import io.github.inwins.jellymusic.data.mediaStateOf
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaStateTest {
    @Test
    fun downloadedTakesPrecedence() {
        assertEquals(MediaState.DOWNLOADED, mediaStateOf(downloaded = true, cached = true))
        assertEquals(MediaState.DOWNLOADED, mediaStateOf(downloaded = true, cached = false))
    }

    @Test
    fun cachedState() {
        assertEquals(MediaState.CACHED, mediaStateOf(downloaded = false, cached = true))
    }

    @Test
    fun onlineState() {
        assertEquals(MediaState.ONLINE, mediaStateOf(downloaded = false, cached = false))
    }
}
