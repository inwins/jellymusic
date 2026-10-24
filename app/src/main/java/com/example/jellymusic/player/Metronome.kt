package com.example.jellymusic.player

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/** Loop-based metronome with an accent on the first beat of each bar. */
class Metronome {
    private var track: AudioTrack? = null

    fun start(bpm: Int, beatsPerBar: Int = 4, subdivision: Int = 1) {
        stop()
        val safeBpm = bpm.coerceIn(30, 240)
        val sub = subdivision.coerceIn(1, 4)
        val sampleRate = 22_050
        val samplesPerBeat = sampleRate * 60 / safeBpm
        val samplesPerTick = samplesPerBeat / sub
        val ticksPerBar = beatsPerBar * sub
        val total = samplesPerTick * ticksPerBar
        val buffer = ShortArray(total)
        val clickLength = (sampleRate * 0.045).toInt()
        for (tick in 0 until ticksPerBar) {
            val start = tick * samplesPerTick
            val isBeat = tick % sub == 0
            val beat = tick / sub
            val frequency = when {
                isBeat && beat == 0 -> 1_600.0
                isBeat -> 1_000.0
                else -> 800.0
            }
            val volume = when {
                isBeat && beat == 0 -> 0.45
                isBeat -> 0.32
                else -> 0.12
            }
            for (i in 0 until clickLength) {
                val envelope = 1.0 - i.toDouble() / clickLength
                val value = sin(2.0 * PI * frequency * i / sampleRate) * envelope * volume
                buffer[start + i] = (value * 32767).toInt().toShort()
            }
        }
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
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        }.getOrNull() ?: return
        runCatching {
            output.write(buffer, 0, buffer.size)
            output.setLoopPoints(0, buffer.size, -1)
            output.play()
        }
        track = output
    }

    fun stop() {
        runCatching {
            track?.stop()
            track?.release()
        }
        track = null
    }
}
