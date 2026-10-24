package com.example.jellymusic.player

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import java.util.Random

/** Simple looping drum machine: kick, snare and hi-hat. */
class DrumMachine {
    private var track: AudioTrack? = null
    private val random = Random(3L)

    fun start(bpm: Int, beatsPerBar: Int = 4) {
        stop()
        val safeBpm = bpm.coerceIn(40, 220)
        val sampleRate = 22_050
        val samplesPerBeat = sampleRate * 60 / safeBpm
        val total = samplesPerBeat * beatsPerBar
        val buffer = FloatArray(total)

        fun addKick(at: Int) {
            val length = (sampleRate * 0.16).toInt()
            for (i in 0 until length) {
                if (at + i >= total) break
                val t = i.toDouble() / sampleRate
                val env = exp(-18.0 * t)
                buffer[at + i] += (sin(2.0 * PI * 55.0 * t) * env * 0.9).toFloat()
            }
        }

        fun addSnare(at: Int) {
            val length = (sampleRate * 0.14).toInt()
            for (i in 0 until length) {
                if (at + i >= total) break
                val t = i.toDouble() / sampleRate
                val env = exp(-22.0 * t)
                val noise = random.nextDouble() * 2.0 - 1.0
                buffer[at + i] += ((noise * 0.5 + sin(2.0 * PI * 180.0 * t) * 0.3) * env * 0.6).toFloat()
            }
        }

        fun addHat(at: Int, strong: Boolean) {
            val length = (sampleRate * 0.05).toInt()
            for (i in 0 until length) {
                if (at + i >= total) break
                val t = i.toDouble() / sampleRate
                val env = exp(-70.0 * t)
                val noise = random.nextDouble() * 2.0 - 1.0
                buffer[at + i] += (noise * env * (if (strong) 0.22 else 0.12)).toFloat()
            }
        }

        for (beat in 0 until beatsPerBar) {
            val at = beat * samplesPerBeat
            if (beatsPerBar == 4) {
                if (beat == 0 || beat == 2) addKick(at)
                if (beat == 1 || beat == 3) addSnare(at)
            } else {
                if (beat == 0) addKick(at)
                if (beat == 1) addSnare(at)
                if (beat == 2) addKick(at)
            }
            addHat(at, beat == 0)
            addHat(at + samplesPerBeat / 2, false)
        }

        val pcm = ShortArray(total) { i ->
            (buffer[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
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
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        }.getOrNull() ?: return
        runCatching {
            output.write(pcm, 0, pcm.size)
            output.setLoopPoints(0, pcm.size, -1)
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
