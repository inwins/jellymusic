package com.example.jellymusic

import com.example.jellymusic.util.MusicTheory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicTheoryTest {
    @Test
    fun camelotWheel() {
        assertEquals("8B", MusicTheory.camelot(0, minor = false))
        assertEquals("8A", MusicTheory.camelot(9, minor = true))
        assertEquals("3A", MusicTheory.camelot(10, minor = true))
        assertEquals("2B", MusicTheory.camelot(6, minor = false))
        assertEquals("7B", MusicTheory.camelot(5, minor = false))
    }

    @Test
    fun camelotHueFollowsWheel() {
        assertEquals(210f, MusicTheory.camelotHue(0, minor = false), 0.001f)
        assertEquals(60f, MusicTheory.camelotHue(10, minor = true), 0.001f)
    }

    @Test
    fun relativeKeys() {
        assertEquals(9 to true, MusicTheory.relative(0, minor = false))
        assertEquals(0 to false, MusicTheory.relative(9, minor = true))
    }

    @Test
    fun keyCompatibility() {
        assertEquals(1f, MusicTheory.keyCompatibility(0, false, 0, false), 0.001f)
        assertEquals(0.9f, MusicTheory.keyCompatibility(0, false, 7, false), 0.001f)
        assertEquals(0.85f, MusicTheory.keyCompatibility(0, false, 9, true), 0.001f)
        assertEquals(0.2f, MusicTheory.keyCompatibility(0, false, 6, false), 0.001f)
    }

    @Test
    fun tempoTreatsDoubleTimeAsCompatible() {
        assertEquals(1f, MusicTheory.tempoCompatibility(120f, 120f), 0.001f)
        assertEquals(1f, MusicTheory.tempoCompatibility(120f, 60f), 0.001f)
        assertTrue(MusicTheory.tempoCompatibility(120f, 128f) < 0.3f)
    }

    @Test
    fun diatonicChords() {
        assertEquals(
            listOf("C", "Dm", "Em", "F", "G", "Am", "Bdim"),
            MusicTheory.diatonicChords(0, minor = false),
        )
        assertEquals(
            listOf("Am", "Bdim", "C", "Dm", "Em", "F", "G"),
            MusicTheory.diatonicChords(9, minor = true),
        )
    }

    @Test
    fun harmonicScorePrefersCompatibleKeys() {
        val same = MusicTheory.harmonicScore(0, false, 120f, 0, false, 122f)
        val far = MusicTheory.harmonicScore(0, false, 120f, 6, false, 170f)
        assertTrue(same > far)
    }
}
