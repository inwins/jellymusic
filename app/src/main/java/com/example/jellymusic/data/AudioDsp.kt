package com.example.jellymusic.data

import com.example.jellymusic.util.Fft
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Lightweight on-device audio feature extraction. Pure Kotlin so it can be unit tested
 * with synthetic signals; [AudioAnalyzer] supplies the decoded mono PCM.
 */
object AudioDsp {
    const val FRAME = 2048
    const val HOP = 1024

    private val MAJOR = floatArrayOf(
        6.35f, 2.23f, 3.48f, 2.33f, 4.38f, 4.09f,
        2.52f, 5.19f, 2.39f, 3.66f, 2.29f, 2.88f,
    )
    private val MINOR = floatArrayOf(
        6.33f, 2.68f, 3.52f, 5.38f, 2.60f, 3.53f,
        2.54f, 4.75f, 3.98f, 2.69f, 3.34f, 3.17f,
    )

    fun empty(now: Long = System.currentTimeMillis()): AudioFeatures = AudioFeatures(
        bpm = 0f,
        keyIndex = 0,
        minor = false,
        energy = 0f,
        loudnessDb = -80f,
        brightness = 0f,
        instrumentalness = 0f,
        beatStrength = 0f,
        chroma = List(12) { 0f },
        analyzedAt = now,
    )

    fun analyze(samples: FloatArray, sampleRate: Int): AudioFeatures {
        if (sampleRate <= 0 || samples.size < FRAME) return empty()
        val bins = FRAME / 2
        val window = FloatArray(FRAME) {
            (0.5 - 0.5 * cos(2.0 * PI * it / (FRAME - 1))).toFloat()
        }
        val re = FloatArray(FRAME)
        val im = FloatArray(FRAME)
        val prevMag = FloatArray(bins)
        val flux = ArrayList<Float>()
        val chroma = FloatArray(12)
        val freqs = FloatArray(bins) { (it + 1) * sampleRate.toFloat() / FRAME }

        var centroidSum = 0f
        var flatnessSum = 0f
        var bandSum = 0f
        var magSumAll = 0f
        var rmsSum = 0f
        var frames = 0
        var pos = 0

        while (pos + FRAME <= samples.size) {
            for (i in 0 until FRAME) {
                re[i] = samples[pos + i] * window[i]
                im[i] = 0f
            }
            Fft.transform(re, im)

            var magSum = 0f
            var centroid = 0f
            var logSum = 0f
            var frameFlux = 0f
            for (k in 0 until bins) {
                val mag = sqrt(re[k] * re[k] + im[k] * im[k])
                magSum += mag
                centroid += freqs[k] * mag
                logSum += ln(mag + 1e-6f)
                frameFlux += max(0f, mag - prevMag[k])
                prevMag[k] = mag
                val f = freqs[k]
                if (f in 300f..3400f) bandSum += mag
                if (f in 55f..2000f) {
                    val midi = 69.0 + 12.0 * (ln(f / 440f) / ln(2.0))
                    val pc = ((midi.roundToInt() % 12) + 12) % 12
                    chroma[pc] += mag
                }
            }
            var rms = 0f
            for (i in 0 until FRAME) rms += samples[pos + i] * samples[pos + i]
            rms = sqrt(rms / FRAME)
            rmsSum += rms
            magSumAll += magSum
            centroidSum += if (magSum > 0f) centroid / magSum else 0f
            val arith = magSum / bins
            val geo = exp(logSum / bins)
            flatnessSum += if (arith > 0f) (geo / arith).coerceIn(0f, 1f) else 0f
            flux.add(frameFlux)
            frames++
            pos += HOP
        }
        if (frames == 0) return empty()

        val rmsAvg = rmsSum / frames
        val loudnessDb = if (rmsAvg > 0f) (20f * log10(rmsAvg)) else -80f
        val energy = ((loudnessDb + 50f) / 50f).coerceIn(0f, 1f)
        val brightness = ((centroidSum / frames) / (sampleRate / 2f)).coerceIn(0f, 1f)
        val flatness = (flatnessSum / frames).coerceIn(0f, 1f)
        val bandRatio = if (magSumAll > 0f) (bandSum / magSumAll).coerceIn(0f, 1f) else 0f
        val vocalness = (bandRatio * (0.5f + 0.5f * (1f - flatness))).coerceIn(0f, 1f)
        val instrumentalness = (1f - vocalness).coerceIn(0f, 1f)

        val (bpm, beatStrength) = estimateTempo(flux, sampleRate)
        val (keyIndex, minor) = estimateKey(chroma)

        val chromaSum = chroma.sum()
        val chromaNorm = if (chromaSum > 0f) {
            chroma.map { it / chromaSum }
        } else {
            List(12) { 0f }
        }

        return AudioFeatures(
            bpm = bpm,
            keyIndex = keyIndex,
            minor = minor,
            energy = energy,
            loudnessDb = loudnessDb,
            brightness = brightness,
            instrumentalness = instrumentalness,
            beatStrength = beatStrength,
            chroma = chromaNorm,
            analyzedAt = System.currentTimeMillis(),
        )
    }

    private fun estimateTempo(flux: List<Float>, sampleRate: Int): Pair<Float, Float> {
        if (flux.size < 16) return 0f to 0f
        val mean = flux.average().toFloat()
        val env = FloatArray(flux.size) { flux[it] - mean }
        val frameRate = sampleRate.toFloat() / HOP
        val minLag = (60f * frameRate / 180f).roundToInt().coerceAtLeast(1)
        val maxLag = (60f * frameRate / 60f).roundToInt().coerceAtMost(env.size - 1)
        var bestLag = 0
        var best = 0f
        for (lag in minLag..maxLag) {
            var sum = 0f
            for (i in 0 until env.size - lag) sum += env[i] * env[i + lag]
            if (sum > best) {
                best = sum
                bestLag = lag
            }
        }
        if (bestLag == 0) return 0f to 0f
        var total = 0f
        for (v in env) total += v * v
        val strength = if (total > 0f) (best / total).coerceIn(0f, 1f) else 0f
        var bpm = 60f * frameRate / bestLag
        while (bpm < 70f) bpm *= 2f
        while (bpm > 180f) bpm /= 2f
        return bpm to strength
    }

    private fun estimateKey(chroma: FloatArray): Pair<Int, Boolean> {
        val sum = chroma.sum()
        if (sum <= 0f) return 0 to false
        val norm = FloatArray(12) { chroma[it] / sum }
        var bestScore = -Float.MAX_VALUE
        var bestKey = 0
        var bestMinor = false
        for (rot in 0 until 12) {
            var major = 0f
            var minor = 0f
            for (i in 0 until 12) {
                val c = norm[(i + rot) % 12]
                major += c * MAJOR[i]
                minor += c * MINOR[i]
            }
            if (major > bestScore) {
                bestScore = major
                bestKey = rot
                bestMinor = false
            }
            if (minor > bestScore) {
                bestScore = minor
                bestKey = rot
                bestMinor = true
            }
        }
        return bestKey to bestMinor
    }
}
