package io.github.inwins.jellymusic

import io.github.inwins.jellymusic.data.AuthHeaders
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthHeadersTest {
    @After
    fun tearDown() {
        AuthHeaders.clear()
    }

    @Test
    fun storesAndClearsHeaders() {
        AuthHeaders.authorization = "MediaBrowser Token=abc"
        AuthHeaders.host = "http://host:8096"

        assertEquals("MediaBrowser Token=abc", AuthHeaders.authorization)
        assertEquals("http://host:8096", AuthHeaders.host)

        AuthHeaders.clear()
        assertNull(AuthHeaders.authorization)
        assertNull(AuthHeaders.host)
    }
}
