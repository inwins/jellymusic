package io.github.inwins.jellymusic

import io.github.inwins.jellymusic.util.parseLrc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LrcTest {
    @Test
    fun parsesSyncedLinesAndMetadata() {
        val lines = parseLrc("[ti:Song]\n[ar:Artist]\n[00:01.50]Hello\n[00:03.25]World")
        assertEquals(2, lines.size)
        assertEquals("Hello", lines[0].text)
        assertEquals(1500L, lines[0].startMs)
        assertEquals("World", lines[1].text)
        assertEquals(3250L, lines[1].startMs)
    }

    @Test
    fun supportsMultipleTagsAndFractions() {
        val lines = parseLrc("[00:01.5][00:02.05]Chorus\n[01:00]End")
        assertEquals(3, lines.size)
        assertEquals(1500L, lines[0].startMs)
        assertEquals(2050L, lines[1].startMs)
        assertEquals(60000L, lines[2].startMs)
    }

    @Test
    fun keepsPlainLyricsAsUnsynced() {
        val lines = parseLrc("plain line\nanother line")
        assertEquals(2, lines.size)
        assertNull(lines[0].startMs)
    }
}
