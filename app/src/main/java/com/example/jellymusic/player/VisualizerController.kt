package com.example.jellymusic.player

import android.content.Context
import android.media.audiofx.Visualizer
import androidx.compose.runtime.mutableStateOf
import kotlin.math.sqrt

/**
 * Real-time spectrum capture attached to the player's audio session.
 * Requires RECORD_AUDIO; callers request it before enabling.
 */
class VisualizerController(@Suppress("UNUSED_PARAMETER") context: Context) {
    val bands = mutableStateOf(FloatArray(BANDS))
    val enabled = mutableStateOf(false)
    val failed = mutableStateOf(false)

    private var visualizer: Visualizer? = null
    private var smoothed = FloatArray(BANDS)

    fun start(sessionId: Int) {
        if (visualizer != null && enabled.value) return
        stop()
        if (sessionId == 0) return
        runCatching {
            val instance = Visualizer(sessionId)
            instance.captureSize = FFT_SIZE
            instance.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int,
                    ) = Unit

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int,
                    ) {
                        if (fft == null) return
                        bands.value = toBands(fft)
                    }
                },
                Visualizer.getMaxCaptureRate(),
                false,
                true,
            )
            instance.enabled = true
            visualizer = instance
            enabled.value = true
            failed.value = false
        }.onFailure {
            failed.value = true
            enabled.value = false
        }
    }

    fun stop() {
        runCatching {
            visualizer?.enabled = false
            visualizer?.release()
        }
        visualizer = null
        enabled.value = false
        smoothed = FloatArray(BANDS)
        bands.value = FloatArray(BANDS)
    }

    private fun toBands(fft: ByteArray): FloatArray {
        val binCount = fft.size / 2
        if (binCount < 2) return FloatArray(BANDS)
        val out = FloatArray(BANDS)
        for (b in 0 until BANDS) {
            val start = Math.pow(binCount.toDouble(), b.toDouble() / BANDS)
                .toInt().coerceIn(1, binCount - 1)
            val end = Math.pow(binCount.toDouble(), (b + 1).toDouble() / BANDS)
                .toInt().coerceIn(start + 1, binCount)
            var sum = 0f
            for (k in start until end) {
                val re = fft[2 * k].toInt()
                val im = fft[2 * k + 1].toInt()
                sum += sqrt((re * re + im * im).toDouble()).toFloat()
            }
            val value = (sum / (end - start)) / 128f
            out[b] = value.coerceIn(0f, 1f)
        }
        // attack fast, release slow
        for (i in 0 until BANDS) {
            smoothed[i] = maxOf(out[i], smoothed[i] * 0.75f)
        }
        return smoothed.copyOf()
    }

    private companion object {
        const val BANDS = 48
        const val FFT_SIZE = 1024
    }
}
