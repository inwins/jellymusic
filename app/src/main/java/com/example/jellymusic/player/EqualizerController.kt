package com.example.jellymusic.player

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.jellymusic.data.AppSettings

class EqualizerController(
    private val player: ExoPlayer,
    private val settings: AppSettings,
) : Player.Listener {
    private var effect: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudness: LoudnessEnhancer? = null

    var bassAvailable by mutableStateOf(false)
        private set
    var virtualAvailable by mutableStateOf(false)
        private set
    var loudnessAvailable by mutableStateOf(false)
        private set

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
            runCatching {
                val bass = BassBoost(0, sessionId)
                bassBoost = bass
                bassAvailable = true
                applyBass()
            }.onFailure { bassAvailable = false }
            runCatching {
                val virtual = Virtualizer(0, sessionId)
                virtualizer = virtual
                virtualAvailable = true
                applyVirtualizer()
            }.onFailure { virtualAvailable = false }
            runCatching {
                val enhancer = LoudnessEnhancer(sessionId)
                loudness = enhancer
                loudnessAvailable = true
                applyLoudness()
            }.onFailure { loudnessAvailable = false }
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

    private fun applyBass() {
        runCatching {
            bassBoost?.let {
                it.setStrength(settings.bassBoostStrength.toShort())
                it.enabled = settings.bassBoostEnabled
            }
        }
    }

    private fun applyVirtualizer() {
        runCatching {
            virtualizer?.let {
                it.setStrength(settings.virtualizerStrength.toShort())
                it.enabled = settings.virtualizerEnabled
            }
        }
    }

    private fun applyLoudness() {
        runCatching {
            loudness?.let {
                it.setTargetGain(settings.loudnessBoostMb)
                it.enabled = settings.loudnessBoostEnabled
            }
        }
    }

    fun setBassBoost(enabled: Boolean) {
        settings.bassBoostEnabled = enabled
        applyBass()
    }

    fun setBassStrength(value: Int) {
        settings.bassBoostStrength = value
        applyBass()
    }

    fun setVirtualizer(enabled: Boolean) {
        settings.virtualizerEnabled = enabled
        applyVirtualizer()
    }

    fun setVirtualizerStrength(value: Int) {
        settings.virtualizerStrength = value
        applyVirtualizer()
    }

    fun setLoudnessBoost(enabled: Boolean) {
        settings.loudnessBoostEnabled = enabled
        applyLoudness()
    }

    fun setLoudnessGain(value: Int) {
        settings.loudnessBoostMb = value
        applyLoudness()
    }

    private fun releaseEffect() {
        runCatching { effect?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { loudness?.release() }
        effect = null
        bassBoost = null
        virtualizer = null
        loudness = null
        available = false
        bassAvailable = false
        virtualAvailable = false
        loudnessAvailable = false
    }

    fun release() {
        player.removeListener(this)
        releaseEffect()
    }
}
