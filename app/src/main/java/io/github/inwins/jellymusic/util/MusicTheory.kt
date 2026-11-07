package io.github.inwins.jellymusic.util

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


    data class Scale(val name: String, val intervals: IntArray)

    val SCALES = listOf(
        Scale("大调", intArrayOf(0, 2, 4, 5, 7, 9, 11)),
        Scale("自然小调", intArrayOf(0, 2, 3, 5, 7, 8, 10)),
        Scale("和声小调", intArrayOf(0, 2, 3, 5, 7, 8, 11)),
        Scale("旋律小调", intArrayOf(0, 2, 3, 5, 7, 9, 11)),
        Scale("多利亚", intArrayOf(0, 2, 3, 5, 7, 9, 10)),
        Scale("弗里吉亚", intArrayOf(0, 1, 3, 5, 7, 8, 10)),
        Scale("利底亚", intArrayOf(0, 2, 4, 6, 7, 9, 11)),
        Scale("混合利底亚", intArrayOf(0, 2, 4, 5, 7, 9, 10)),
        Scale("洛克里亚", intArrayOf(0, 1, 3, 5, 6, 8, 10)),
        Scale("大调五声", intArrayOf(0, 2, 4, 7, 9)),
        Scale("小调五声", intArrayOf(0, 3, 5, 7, 10)),
        Scale("布鲁斯", intArrayOf(0, 3, 5, 6, 7, 10)),
    )

    data class ScaleMatch(
        val tonic: Int,
        val scale: Scale,
        val confidence: Float,
    ) {
        val notes: List<String> get() = scale.intervals.map { noteName(tonic + it) }
    }

    /** Best matching scale across all 12 rotations of every [SCALES] template. */
    fun detectScale(chroma: List<Float>): ScaleMatch? {
        if (chroma.size < 12) return null
        val total = chroma.sum()
        if (total <= 0f) return null
        val normalized = FloatArray(12) { chroma[it] / total }
        var best: ScaleMatch? = null
        var bestScore = -1f
        for (scale in SCALES) {
            for (tonic in 0 until 12) {
                var inScale = 0f
                for (interval in scale.intervals) inScale += normalized[(tonic + interval) % 12]
                val outOfScale = (1f - inScale).coerceAtLeast(0f)
                // Favour templates that cover the most energy and penalise stray notes.
                val score = inScale - 0.5f * outOfScale
                val current = best
                val better = score > bestScore + 1e-4f ||
                    (kotlin.math.abs(score - bestScore) <= 1e-4f &&
                        current != null && scale.intervals.size < current.scale.intervals.size)
                if (current == null || better) {
                    bestScore = score
                    best = ScaleMatch(tonic, scale, inScale.coerceIn(0f, 1f))
                }
            }
        }
        return best
    }


    private val MAJOR_TRIAD = intArrayOf(0, 4, 7)
    private val MINOR_TRIAD = intArrayOf(0, 3, 7)

    /** Best-matching major/minor triad for a chroma vector, or null when too flat. */
    fun chordForChroma(chroma: List<Float>): Pair<Int, Boolean>? {
        if (chroma.size < 12) return null
        val total = chroma.sum()
        if (total <= 0f) return null
        val normalized = FloatArray(12) { chroma[it] / total }
        var bestRoot = -1
        var bestMinor = false
        var bestScore = 0f
        for (root in 0 until 12) {
            for (minor in listOf(false, true)) {
                val triad = if (minor) MINOR_TRIAD else MAJOR_TRIAD
                var inTriad = 0f
                for (interval in triad) inTriad += normalized[(root + interval) % 12]
                val outOfTriad = (1f - inTriad).coerceAtLeast(0f)
                val score = inTriad - 0.6f * outOfTriad
                if (score > bestScore) {
                    bestScore = score
                    bestRoot = root
                    bestMinor = minor
                }
            }
        }
        return if (bestRoot >= 0) bestRoot to bestMinor else null
    }

    fun transposeChord(name: String, semitones: Int): String {
        if (semitones == 0 || name.isBlank()) return name
        val rootLength = when {
            name.length >= 2 && name[1] == '#' -> 2
            else -> 1
        }
        val rootName = name.take(rootLength)
        val suffix = name.drop(rootLength)
        val index = NOTE_NAMES.indexOf(rootName)
        if (index < 0) return name
        val pc = ((index + semitones) % 12 + 12) % 12
        return NOTE_NAMES[pc] + suffix
    }

    /** Suggests a semitone shift so a melody range sits in a comfortable vocal range. */
    fun suggestTranspose(pitchMin: Int, pitchMax: Int): Int {
        if (pitchMin <= 0 || pitchMax <= 0 || pitchMax < pitchMin) return 0
        val targetCenter = 65 // F4
        val center = (pitchMin + pitchMax) / 2
        var shift = targetCenter - center
        // keep the result inside a broad singable range
        if (pitchMin + shift < 48) shift = 48 - pitchMin
        if (pitchMax + shift > 84) shift = 84 - pitchMax
        return shift.coerceIn(-12, 12)
    }

    fun chordName(root: Int, minor: Boolean): String =
        noteName(root) + if (minor) "m" else ""

    fun midiName(midi: Int): String =
        noteName(midi % 12) + (midi / 12 - 1)

    fun hzToMidi(hz: Float): Int {
        if (hz <= 0f) return -1
        return (69.0 + 12.0 * (kotlin.math.ln(hz / 440f) / kotlin.math.ln(2.0))).toInt()
    }

    private val MAJOR_DEGREE = mapOf(0 to "1", 2 to "2", 4 to "3", 5 to "4", 7 to "5", 9 to "6", 11 to "7")

    /** Numbered notation (简谱) degree for a pitch class relative to the tonic. */
    fun jianpuDegree(semitoneFromTonic: Int): String {
        val s = ((semitoneFromTonic % 12) + 12) % 12
        MAJOR_DEGREE[s]?.let { return it }
        if (s == 3 || s == 8 || s == 10) {
            MAJOR_DEGREE[(s + 1) % 12]?.let { return "b$it" }
        }
        if (s == 1 || s == 6) {
            MAJOR_DEGREE[(s - 1 + 12) % 12]?.let { return "#$it" }
        }
        MAJOR_DEGREE[(s + 1) % 12]?.let { return "b$it" }
        MAJOR_DEGREE[(s - 1 + 12) % 12]?.let { return "#$it" }
        return "?"
    }

    fun toJianpu(midi: Int, tonic: Int): String {
        val semitone = ((midi - tonic) % 12 + 12) % 12
        val degree = jianpuDegree(semitone)
        val baseTonic = 60 + (((tonic - 60) % 12) + 12) % 12
        val diff = midi - baseTonic
        val octave = if (diff >= 0) diff / 12 else -((-diff + 11) / 12)
        val marks = when {
            octave > 0 -> "'".repeat(octave)
            octave < 0 -> ",".repeat(-octave)
            else -> ""
        }
        return degree + marks
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
