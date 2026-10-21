package com.example.jellymusic.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("jellymusic_settings", Context.MODE_PRIVATE)

    var downloadTreeUri: String?
        get() = prefs.getString(KEY_DOWNLOAD_TREE, null)
        set(value) {
            prefs.edit().putString(KEY_DOWNLOAD_TREE, value).apply()
        }

    var downloadDirLabel: String?
        get() = prefs.getString(KEY_DOWNLOAD_LABEL, null)
        set(value) {
            prefs.edit().putString(KEY_DOWNLOAD_LABEL, value).apply()
        }

    var cacheLimitMb: Int
        get() = prefs.getInt(KEY_CACHE_LIMIT_MB, DEFAULT_CACHE_LIMIT_MB)
        set(value) {
            prefs.edit().putInt(KEY_CACHE_LIMIT_MB, value).apply()
        }

    var streamCacheEnabled: Boolean
        get() = prefs.getBoolean(KEY_STREAM_CACHE_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_STREAM_CACHE_ENABLED, value).apply()
        }

    private var themeModeState by mutableStateOf(loadThemeMode())

    var themeMode: ThemeMode
        get() = themeModeState
        set(value) {
            themeModeState = value
            prefs.edit().putString(KEY_THEME_MODE, value.name).apply()
        }

    private var dynamicColorState by mutableStateOf(prefs.getBoolean(KEY_DYNAMIC_COLOR, false))

    var dynamicColor: Boolean
        get() = dynamicColorState
        set(value) {
            dynamicColorState = value
            prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply()
        }

    private var amoledState by mutableStateOf(prefs.getBoolean(KEY_AMOLED_DARK, false))

    var amoledDark: Boolean
        get() = amoledState
        set(value) {
            amoledState = value
            prefs.edit().putBoolean(KEY_AMOLED_DARK, value).apply()
        }

    private var fadeEnabledState by mutableStateOf(prefs.getBoolean(KEY_FADE_ENABLED, true))

    var fadeEnabled: Boolean
        get() = fadeEnabledState
        set(value) {
            fadeEnabledState = value
            prefs.edit().putBoolean(KEY_FADE_ENABLED, value).apply()
        }

    private var fadeMsState by mutableStateOf(prefs.getInt(KEY_FADE_MS, DEFAULT_FADE_MS))

    var fadeMs: Int
        get() = fadeMsState
        set(value) {
            fadeMsState = value
            prefs.edit().putInt(KEY_FADE_MS, value).apply()
        }

    private var rememberPositionState by mutableStateOf(prefs.getBoolean(KEY_REMEMBER_POSITION, true))

    var rememberPosition: Boolean
        get() = rememberPositionState
        set(value) {
            rememberPositionState = value
            prefs.edit().putBoolean(KEY_REMEMBER_POSITION, value).apply()
        }

    private var playbackSpeedState by mutableStateOf(prefs.getFloat(KEY_PLAYBACK_SPEED, 1f))

    var playbackSpeed: Float
        get() = playbackSpeedState
        set(value) {
            playbackSpeedState = value
            prefs.edit().putFloat(KEY_PLAYBACK_SPEED, value).apply()
        }

    private fun loadThemeMode(): ThemeMode = runCatching {
        ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)!!)
    }.getOrDefault(ThemeMode.SYSTEM)

    private companion object {
        const val KEY_DOWNLOAD_TREE = "download_tree_uri"
        const val KEY_DOWNLOAD_LABEL = "download_dir_label"
        const val KEY_CACHE_LIMIT_MB = "stream_cache_limit_mb"
        const val KEY_STREAM_CACHE_ENABLED = "stream_cache_enabled"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_AMOLED_DARK = "amoled_dark"
        const val KEY_FADE_ENABLED = "fade_enabled"
        const val KEY_FADE_MS = "fade_ms"
        const val KEY_REMEMBER_POSITION = "remember_position"
        const val KEY_PLAYBACK_SPEED = "playback_speed"
        const val DEFAULT_CACHE_LIMIT_MB = 512
        const val DEFAULT_FADE_MS = 300
    }
}