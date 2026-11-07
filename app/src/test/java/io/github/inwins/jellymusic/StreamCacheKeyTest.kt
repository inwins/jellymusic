package io.github.inwins.jellymusic

import android.net.Uri
import io.github.inwins.jellymusic.data.streamCacheKey
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreamCacheKeyTest {
    @Test
    fun keysAudioStreamsByHostAndItemId() {
        val uri = Uri.parse("http://192.168.0.101:8096/Audio/item123/stream?static=true")
        assertEquals("192.168.0.101:8096/item123", streamCacheKey(uri))
    }

    @Test
    fun ignoresQueryParameters() {
        val a = Uri.parse("http://host:8096/Audio/id/stream?a=1")
        val b = Uri.parse("http://host:8096/Audio/id/stream?b=2")
        assertEquals(streamCacheKey(a), streamCacheKey(b))
    }

    @Test
    fun fallsBackToFullUriForNonAudioPaths() {
        val uri = Uri.parse("http://host:8096/Items/abc/Images/Primary")
        assertEquals(uri.toString(), streamCacheKey(uri))
    }

    @Test
    fun fallsBackWhenAudioPathHasNoId() {
        val uri = Uri.parse("http://host:8096/Audio")
        assertEquals(uri.toString(), streamCacheKey(uri))
    }
}
