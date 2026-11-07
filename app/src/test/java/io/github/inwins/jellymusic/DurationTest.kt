package io.github.inwins.jellymusic

import io.github.inwins.jellymusic.util.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Test

class DurationTest {
    @Test
    fun formatsMinutes() {
        assertEquals("4:31", formatDuration(271_000L))
    }

    @Test
    fun formatsHours() {
        assertEquals("1:00:00", formatDuration(3_600_000L))
    }

    @Test
    fun formatsZero() {
        assertEquals("0:00", formatDuration(0L))
    }

    @Test
    fun truncatesPartialSeconds() {
        assertEquals("0:59", formatDuration(59_999L))
        assertEquals("1:00", formatDuration(60_000L))
        assertEquals("59:59", formatDuration(3_599_000L))
    }

    @Test
    fun formatsMultiHour() {
        assertEquals("10:00:00", formatDuration(36_000_000L))
    }

    @Test
    fun clampsNegativeValues() {
        assertEquals("0:00", formatDuration(-1_000L))
    }
}
