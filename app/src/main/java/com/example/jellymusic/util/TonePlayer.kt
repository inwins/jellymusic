package com.example.jellymusic.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Simple sine synthesiser to audition recognised melodies / scales. */
class TonePlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var track: AudioTrack? = null

    fun play(notes: List<Int>, noteMs: Int = 240) {
        stop()
        if (notes.isEmpty()) return
        job = scope.launch {
            val sampleRate = 22_050
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
            runCatching { output.play() }
            val gap = ShortArray(sampleRate * 20 / 1000)
            for (midi in notes) {
                val frequency = 440.0 * 2.0.pow((midi - 69) / 12.0)
                val count = sampleRate * noteMs / 1000
                val buffer = ShortArray(count)
                for (i in 0 until count) {
                    val attack = min(1.0, i / (sampleRate * 0.01))
                    val release = min(1.0, (count - i) / (sampleRate * 0.04))
                    val envelope = attack * release
                    val value = sin(2.0 * PI * frequency * i / sampleRate) * 0.35 * envelope
                    buffer[i] = (value * 32767).toInt().toShort()
                }
                runCatching {
                    output.write(buffer, 0, buffer.size)
                    output.write(gap, 0, gap.size)
                }
            }
            runCatching {
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
}
