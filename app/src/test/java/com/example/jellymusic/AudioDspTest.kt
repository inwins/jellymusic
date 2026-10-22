package com.example.jellymusic

import com.example.jellymusic.data.AudioDsp
import com.example.jellymusic.data.AudioFeatures
import com.example.jellymusic.util.Fft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class AudioDspTest {
    private val sampleRate = 11_025

    @Test
    fun fftFindsSineBin() {
        val n = 2048
        val freq = 440.0
        val re = FloatArray(n) { sin(2.0 * PI * freq * it / sampleRate).toFloat() }
        val im = FloatArray(n)
        Fft.transform(re, im)

        var peak = 0
        var peakMag = 0f
        for (k in 1 until n / 2) {
            val mag = sqrt(re[k] * re[k] + im[k] * im[k])
            if (mag > peakMag) {
                peakMag = mag
                peak = k
            }
        }
        val expected = (freq * n / sampleRate).toInt()
        assertTrue("peak=$peak expected~$expected", peak in (expected - 1)..(expected + 1))
    }

    @Test
    fun detectsTempoOfClickTrack() {
        val seconds = 12
        val samples = FloatArray(seconds * sampleRate)
        val interval = sampleRate / 2
        var pos = 0
        while (pos < samples.size) {
            for (i in 0 until 200) {
                if (pos + i < samples.size) {
                    val decay = 1f - i / 200f
                    samples[pos + i] = (sin(2.0 * PI * 1500 * i / sampleRate) * decay).toFloat()
                }
            }
            pos += interval
        }
        val features = AudioDsp.analyze(samples, sampleRate)
        assertTrue("bpm=${features.bpm}", features.bpm in 100f..150f)
        assertTrue(features.beatStrength > 0.05f)
    }

    @Test
    fun chromaPeaksAtConcertA() {
        val samples = FloatArray(4 * sampleRate) {
            sin(2.0 * PI * 440.0 * it / sampleRate).toFloat()
        }
        val features = AudioDsp.analyze(samples, sampleRate)
        val peak = features.chroma.indices.maxByOrNull { features.chroma[it] }
        assertEquals(9, peak)
    }

    @Test
    fun silenceHasNoTempo() {
        val features = AudioDsp.analyze(FloatArray(sampleRate * 3), sampleRate)
        assertEquals(0f, features.bpm, 0.01f)
        assertEquals(0f, features.energy, 0.001f)
    }

    @Test
    fun cosineSimilarity() {
        val a = AudioFeatures.vector(features(bpm = 120f))
        assertEquals(1f, AudioFeatures.cosine(a, a), 0.0001f)
        assertEquals(0f, AudioFeatures.cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 0.0001f)
    }

    @Test
    fun featuresJsonRoundTrip() {
        val original = features(bpm = 128f)
        assertEquals(original, AudioFeatures.fromJson(original.toJson()))
    }

    private fun features(bpm: Float) = AudioFeatures(
        bpm = bpm,
        keyIndex = 9,
        minor = true,
        energy = 0.5f,
        loudnessDb = -20f,
        brightness = 0.3f,
        instrumentalness = 0.4f,
        beatStrength = 0.6f,
        chroma = List(12) { if (it == 9) 1f else 0f },
        analyzedAt = 123L,
    )
}
