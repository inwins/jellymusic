package com.example.jellymusic.util

import kotlin.math.sqrt

/** Simple autocorrelation pitch detector for the tuner. */
object PitchDetector {
    fun detect(samples: ShortArray, sampleRate: Int): Float {
        if (samples.size < 1024) return 0f
        var rms = 0.0
        for (sample in samples) rms += sample.toDouble() * sample
        rms = sqrt(rms / samples.size)
        if (rms < 600.0) return 0f

        val minLag = (sampleRate / 1500.0).toInt().coerceAtLeast(2)
        val maxLag = (sampleRate / 60.0).toInt().coerceAtMost(samples.size / 2)
        var bestLag = 0
        var bestCorrelation = 0.0
        for (lag in minLag..maxLag) {
            var correlation = 0.0
            for (i in 0 until samples.size - lag) {
                correlation += samples[i].toDouble() * samples[i + lag]
            }
            if (correlation > bestCorrelation) {
                bestCorrelation = correlation
                bestLag = lag
            }
        }
        if (bestLag <= 0) return 0f
        return sampleRate.toFloat() / bestLag
    }
}
