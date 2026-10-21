package com.example.jellymusic.player

import android.media.audiofx.Equalizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class EqualizerController(private val player: ExoPlayer) : Player.Listener {
    private var effect: Equalizer? = null

    var available by mutableStateOf(false)
        private set
    var enabled by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    var minLevel by mutableIntStateOf(-1500)
        private set
    var maxLevel by mutableIntStateOf(1500)
        private set
    var bandCount by mutableIntStateOf(0)
        private set

    val bandLevels = mutableStateListOf<Int>()
    val centerFreqs = mutableStateListOf<Int>()
    val presetNames = mutableStateListOf<String>()
    var currentPreset by mutableIntStateOf(-1)
        private set

    init {
        player.addListener(this)
        val session = player.audioSessionId
        if (session != C.AUDIO_SESSION_ID_UNSET) {
            attach(session)
        }
    }

    override fun onAudioSessionIdChanged(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        attach(audioSessionId)
    }

    private fun attach(sessionId: Int) {
        releaseEffect()
        runCatching {
            val equalizer = Equalizer(0, sessionId)
            val range = equalizer.bandLevelRange
            minLevel = range[0].toInt()
            maxLevel = range[1].toInt()
            bandCount = equalizer.numberOfBands.toInt()
            bandLevels.clear()
            centerFreqs.clear()
            for (band in 0 until bandCount) {
                bandLevels.add(equalizer.getBandLevel(band.toShort()).toInt())
                centerFreqs.add(equalizer.getCenterFreq(band.toShort()) / 1000)
            }
            presetNames.clear()
            for (index in 0 until equalizer.numberOfPresets.toInt()) {
                presetNames.add(equalizer.getPresetName(index.toShort()))
            }
            currentPreset = equalizer.currentPreset.toInt()
            equalizer.enabled = enabled
            effect = equalizer
            available = true
            error = null
        }.onFailure {
            available = false
            error = it.message
        }
    }

    fun changeEnabled(value: Boolean) {
        enabled = value
        runCatching { effect?.enabled = value }
    }

    fun setBandLevel(band: Int, level: Int) {
        if (band !in bandLevels.indices) return
        val clamped = level.coerceIn(minLevel, maxLevel)
        runCatching { effect?.setBandLevel(band.toShort(), clamped.toShort()) }
        bandLevels[band] = clamped
        currentPreset = -1
    }

    fun applyPreset(index: Int) {
        val equalizer = effect ?: return
        if (index !in 0 until presetNames.size) return
        runCatching {
            equalizer.usePreset(index.toShort())
            for (band in 0 until bandCount) {
                bandLevels[band] = equalizer.getBandLevel(band.toShort()).toInt()
            }
            currentPreset = equalizer.currentPreset.toInt()
            if (!enabled) {
                enabled = true
                equalizer.enabled = true
            }
        }
    }

    private fun releaseEffect() {
        runCatching { effect?.release() }
        effect = null
        available = false
    }

    fun release() {
        player.removeListener(this)
        releaseEffect()
    }
}
