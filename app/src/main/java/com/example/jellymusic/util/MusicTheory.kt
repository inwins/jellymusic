package com.example.jellymusic.util

import kotlin.math.abs

/**
 * Music-theory helpers used for key naming, the Camelot wheel and harmonic mixing.
 * Pure Kotlin so it can be unit tested.
 */
object MusicTheory {
    val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    // Camelot number for each pitch class, major ("B") and minor ("A") wheels.
    private val MAJOR_WHEEL = intArrayOf(8, 3, 10, 5, 12, 7, 2, 9, 4, 11, 6, 1)
    private val MINOR_WHEEL = intArrayOf(5, 12, 7, 2, 9, 4, 11, 6, 1, 8, 3, 10)

    /** Hue (0..360) derived from the Camelot number, for key-coloured visuals. */
    fun camelotHue(keyIndex: Int, minor: Boolean): Float {
        val number = camelot(keyIndex, minor).dropLast(1).toIntOrNull() ?: 1
        return ((number - 1) % 12) * 30f
    }

    fun noteName(pitchClass: Int): String = NOTE_NAMES[((pitchClass % 12) + 12) % 12]

    fun keyName(keyIndex: Int, minor: Boolean): String =
        noteName(keyIndex) + if (minor) " 小调" else " 大调"

    fun camelot(keyIndex: Int, minor: Boolean): String {
        val pc = ((keyIndex % 12) + 12) % 12
        val number = if (minor) MINOR_WHEEL[pc] else MAJOR_WHEEL[pc]
        return "$number" + if (minor) "A" else "B"
    }

    fun relative(keyIndex: Int, minor: Boolean): Pair<Int, Boolean> =
        if (minor) {
            (keyIndex + 3) % 12 to false
        } else {
            (keyIndex + 9) % 12 to true
        }

    fun parallel(keyIndex: Int, minor: Boolean): Pair<Int, Boolean> = keyIndex % 12 to !minor

    /** 0 (identical) .. 6 (opposite side of the wheel). */
    fun camelotDistance(a: String, b: String): Int {
        val na = a.dropLast(1).toIntOrNull() ?: return 6
        val nb = b.dropLast(1).toIntOrNull() ?: return 6
        val raw = abs(na - nb)
        return minOf(raw, 12 - raw)
    }

    fun keyCompatibility(aKey: Int, aMinor: Boolean, bKey: Int, bMinor: Boolean): Float {
        val ca = camelot(aKey, aMinor)
        val cb = camelot(bKey, bMinor)
        if (ca == cb) return 1f
        val la = ca.last()
        val lb = cb.last()
        val diff = camelotDistance(ca, cb)
        return when {
            diff == 0 && la != lb -> 0.85f
            diff == 1 && la == lb -> 0.9f
            diff == 1 && la != lb -> 0.7f
            diff == 2 && la == lb -> 0.55f
            diff == 2 && la != lb -> 0.5f
            diff == 3 -> 0.35f
            else -> 0.2f
        }
    }

    /** Tempo compatibility treating half/double time as equivalent. */
    fun tempoCompatibility(aBpm: Float, bBpm: Float): Float {
        if (aBpm <= 0f || bBpm <= 0f) return 0.5f
        var ratio = aBpm / bBpm
        while (ratio < 1f) ratio *= 2f
        while (ratio >= 2f) ratio /= 2f
        val relative = abs(ratio - 1f)
        return (1f - relative / 0.08f).coerceIn(0f, 1f)
    }

    fun harmonicScore(
        aKey: Int,
        aMinor: Boolean,
        aBpm: Float,
        bKey: Int,
        bMinor: Boolean,
        bBpm: Float,
    ): Float = 0.65f * keyCompatibility(aKey, aMinor, bKey, bMinor) +
        0.35f * tempoCompatibility(aBpm, bBpm)

    fun diatonicChords(keyIndex: Int, minor: Boolean): List<String> {
        val degrees: IntArray
        val suffixes: List<String>
        if (minor) {
            degrees = intArrayOf(0, 2, 3, 5, 7, 8, 10)
            suffixes = listOf("m", "dim", "", "m", "m", "", "")
        } else {
            degrees = intArrayOf(0, 2, 4, 5, 7, 9, 11)
            suffixes = listOf("", "m", "m", "", "", "m", "dim")
        }
        return degrees.mapIndexed { index, degree ->
            noteName(keyIndex + degree) + suffixes[index]
        }
    }
}
