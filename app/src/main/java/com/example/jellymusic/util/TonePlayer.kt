package com.example.jellymusic.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Additive piano-ish synthesiser used to audition recognised melodies / scales.
 * Each note is a sum of slightly inharmonic partials with a hammer transient and an
 * exponential decay; notes are mixed onto a shared timeline so they ring naturally.
 */
class TonePlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var track: AudioTrack? = null

    fun play(notes: List<Int>, noteMs: Int = 320) =
        playEvents(notes.map { listOf(it) }, noteMs)

    fun playChords(chords: List<List<Int>>, noteMs: Int = 700) =
        playEvents(chords, noteMs)

    private fun playEvents(events: List<List<Int>>, noteMs: Int) {
        stop()
        if (events.isEmpty()) return
        job = scope.launch {
            val sampleRate = 22_050
            val buffer = render(events, sampleRate, noteMs)
            if (buffer.isEmpty()) return@launch
            val minBuffer = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(8192)
            val output = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(minBuffer)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            }.getOrNull() ?: return@launch
            track = output
            runCatching {
                output.play()
                output.write(buffer, 0, buffer.size)
                output.stop()
                output.release()
            }
            track = null
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        runCatching {
            track?.pause()
            track?.flush()
            track?.release()
        }
        track = null
    }

    private fun render(events: List<List<Int>>, sampleRate: Int, noteMs: Int): ShortArray {
        val tailMs = 1_400
        val totalSamples = sampleRate * (events.size * noteMs + tailMs) / 1000
        if (totalSamples <= 0) return ShortArray(0)
        val mix = FloatArray(totalSamples)
        val random = Random(7L)
        events.forEachIndexed { index, midis ->
            val offset = index * sampleRate * noteMs / 1000
            midis.forEach { midi ->
                val frequency = 440.0 * 2.0.pow((midi - 69) / 12.0)
                val note = pianoNote(frequency, sampleRate, tailMs + noteMs, random)
                for (j in note.indices) {
                    val target = offset + j
                    if (target >= totalSamples) break
                    mix[target] += note[j] / midis.size
                }
            }
        }
        var peak = 0f
        for (value in mix) peak = maxOf(peak, abs(value))
        val gain = if (peak > 0f) 0.85f / peak else 1f
        return ShortArray(totalSamples) { i ->
            (mix[i] * gain * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    private fun pianoNote(
        frequency: Double,
        sampleRate: Int,
        durationMs: Int,
        random: Random,
    ): FloatArray {
        val count = sampleRate * durationMs / 1000
        val out = FloatArray(count)
        val nyquist = sampleRate / 2.0
        val partials = ArrayList<Pair<Double, Double>>()
        var harmonic = 1
        while (harmonic <= 28) {
            // slight inharmonicity, stronger for higher partials
            val partial = frequency * harmonic * sqrt(1.0 + 0.0004 * harmonic * harmonic)
            if (partial >= nyquist) break
            val amplitude = 1.0 / harmonic.toDouble().pow(1.35)
            partials.add(partial to amplitude)
            harmonic++
        }
        if (partials.isEmpty()) partials.add(frequency to 1.0)
        // lower notes sustain longer, higher notes decay faster
        val decayRate = 1.1 + frequency / 900.0
        val attackSamples = (sampleRate * 0.005).toInt().coerceAtLeast(1)
        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            val attack = if (i < attackSamples) i.toDouble() / attackSamples else 1.0
            val decay = exp(-decayRate * t)
            var sample = 0.0
            for ((partial, amplitude) in partials) {
                sample += amplitude * sin(2.0 * PI * partial * t)
            }
            // hammer transient
            if (i < attackSamples) {
                sample += (random.nextDouble() - 0.5) * 0.4 * (1.0 - i.toDouble() / attackSamples)
            }
            out[i] = (sample * attack * decay).toFloat()
        }
        return out
    }
}
