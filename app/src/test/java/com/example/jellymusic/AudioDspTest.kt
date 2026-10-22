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
        assertEquals("4/4", features.timeSignature)
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

    @Test
    fun detectsSimpleMelody() {
        val samples = FloatArray(3 * sampleRate)
        val notes = listOf(440.0, 523.25, 659.25) // A4, C5, E5
        for (n in notes.indices) {
            val start = n * sampleRate
            for (i in 0 until sampleRate) {
                val t = i.toDouble() / sampleRate
                val envelope = if (i < 200) i / 200.0 else if (i > sampleRate - 400) (sampleRate - i) / 400.0 else 1.0
                val value = sin(2.0 * PI * notes[n] * t) * 0.6 +
                    sin(2.0 * PI * notes[n] * 2 * t) * 0.2
                samples[start + i] = (value * envelope).toFloat()
            }
        }
        val features = AudioDsp.analyze(samples, sampleRate)
        assertTrue("notes=${features.notes.map { it.midi }}", features.notes.isNotEmpty())
        assertTrue(features.pitchMax - features.pitchMin >= 5)
        assertTrue(features.pitchHistogram.sum() > 0f)
    }

    @Test
    fun mfccDiscriminatesTimbre() {
        val tone = FloatArray(sampleRate * 2) { sin(2.0 * PI * 440.0 * it / sampleRate).toFloat() }
        val rich = FloatArray(sampleRate * 2) {
            (sin(2.0 * PI * 440.0 * it / sampleRate) * 0.6 +
                sin(2.0 * PI * 1320.0 * it / sampleRate) * 0.4).toFloat()
        }
        var seed = 12345L
        val noise = FloatArray(sampleRate * 2) {
            seed = (seed * 1103515245 + 12345) and 0x7fffffff
            ((seed % 1000) / 1000f - 0.5f)
        }
        val fa = AudioDsp.analyze(tone, sampleRate)
        val fb = AudioDsp.analyze(rich, sampleRate)
        val fn = AudioDsp.analyze(noise, sampleRate)
        assertEquals(13, fa.mfcc.size)
        val similar = AudioFeatures.cosine(AudioFeatures.vector(fa), AudioFeatures.vector(fb))
        val different = AudioFeatures.cosine(AudioFeatures.vector(fa), AudioFeatures.vector(fn))
        assertTrue("similar=$similar different=$different", similar > different)
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
