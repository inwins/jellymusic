package com.example.jellymusic

import com.example.jellymusic.util.formatDuration
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
    fun clampsNegativeValues() {
        assertEquals("0:00", formatDuration(-1_000L))
    }
}
