package io.github.inwins.jellymusic

import io.github.inwins.jellymusic.util.MusicTheory
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
    fun detectsMajorScale() {
        val chroma = List(12) { pc ->
            if (pc in setOf(0, 2, 4, 5, 7, 9, 11)) 1f else 0.02f
        }
        val match = MusicTheory.detectScale(chroma)
        assertEquals(0, match?.tonic)
        assertEquals("大调", match?.scale?.name)
    }

    @Test
    fun detectsPentatonicScale() {
        val chroma = List(12) { pc ->
            if (pc in setOf(9, 0, 2, 4, 7)) 1f else 0f
        }
        val match = MusicTheory.detectScale(chroma)
        assertEquals(setOf("A", "C", "D", "E", "G"), match?.notes?.toSet())
    }

    @Test
    fun detectsTriads() {
        val cMajor = List(12) { if (it in setOf(0, 4, 7)) 1f else 0f }
        val aMinor = List(12) { if (it in setOf(9, 0, 4)) 1f else 0f }
        assertEquals(0 to false, MusicTheory.chordForChroma(cMajor))
        assertEquals(9 to true, MusicTheory.chordForChroma(aMinor))
        assertEquals("C", MusicTheory.chordName(0, false))
        assertEquals("Am", MusicTheory.chordName(9, true))
    }

    @Test
    fun jianpuMapping() {
        assertEquals("1", MusicTheory.toJianpu(60, 0))
        assertEquals("2", MusicTheory.toJianpu(62, 0))
        assertEquals("1'", MusicTheory.toJianpu(72, 0))
        assertEquals("1,", MusicTheory.toJianpu(48, 0))
        assertEquals("b3", MusicTheory.toJianpu(72, 9))
        assertEquals("5", MusicTheory.toJianpu(76, 9))
    }

    @Test
    fun midiNaming() {
        assertEquals("C4", MusicTheory.midiName(60))
        assertEquals(69, MusicTheory.hzToMidi(440f))
    }

    @Test
    fun harmonicScorePrefersCompatibleKeys() {
        val same = MusicTheory.harmonicScore(0, false, 120f, 0, false, 122f)
        val far = MusicTheory.harmonicScore(0, false, 120f, 6, false, 170f)
        assertTrue(same > far)
    }
}
