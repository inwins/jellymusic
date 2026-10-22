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

    private var dynamicColorState by mutableStateOf(prefs.getBoolean(KEY_DYNAMIC_COLOR, true))

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

    private var crossfadeState by mutableStateOf(prefs.getInt(KEY_CROSSFADE_MS, 0))

    var crossfadeMs: Int
        get() = crossfadeState
        set(value) {
            crossfadeState = value
            prefs.edit().putInt(KEY_CROSSFADE_MS, value).apply()
        }

    private var prefetchState by mutableStateOf(prefs.getBoolean(KEY_PREFETCH, true))

    var prefetchNext: Boolean
        get() = prefetchState
        set(value) {
            prefetchState = value
            prefs.edit().putBoolean(KEY_PREFETCH, value).apply()
        }

    private var skipSilenceState by mutableStateOf(prefs.getBoolean(KEY_SKIP_SILENCE, false))

    var skipSilence: Boolean
        get() = skipSilenceState
        set(value) {
            skipSilenceState = value
            prefs.edit().putBoolean(KEY_SKIP_SILENCE, value).apply()
        }

    private var normalizeState by mutableStateOf(prefs.getBoolean(KEY_NORMALIZE, false))

    var normalizeVolume: Boolean
        get() = normalizeState
        set(value) {
            normalizeState = value
            prefs.edit().putBoolean(KEY_NORMALIZE, value).apply()
        }

    private var reportPlaybackState by mutableStateOf(prefs.getBoolean(KEY_REPORT_PLAYBACK, true))

    var reportPlayback: Boolean
        get() = reportPlaybackState
        set(value) {
            reportPlaybackState = value
            prefs.edit().putBoolean(KEY_REPORT_PLAYBACK, value).apply()
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

    fun lyricsOffsetFor(itemId: String): Long {
        val raw = prefs.getString(KEY_LYRICS_OFFSETS, null) ?: return lyricsOffsetMs
        return runCatching { org.json.JSONObject(raw).optLong(itemId, lyricsOffsetMs) }
            .getOrDefault(lyricsOffsetMs)
    }

    fun setLyricsOffset(itemId: String, offsetMs: Long) {
        val raw = prefs.getString(KEY_LYRICS_OFFSETS, null)
        val json = runCatching {
            if (raw.isNullOrBlank()) org.json.JSONObject() else org.json.JSONObject(raw)
        }.getOrDefault(org.json.JSONObject())
        json.put(itemId, offsetMs)
        prefs.edit().putString(KEY_LYRICS_OFFSETS, json.toString()).apply()
    }

    var albumSort: String
        get() = prefs.getString(KEY_ALBUM_SORT, "NAME") ?: "NAME"
        set(value) = prefs.edit().putString(KEY_ALBUM_SORT, value).apply()

    var albumDescending: Boolean
        get() = prefs.getBoolean(KEY_ALBUM_DESC, false)
        set(value) = prefs.edit().putBoolean(KEY_ALBUM_DESC, value).apply()

    var albumsFavoritesOnly: Boolean
        get() = prefs.getBoolean(KEY_ALBUM_FAV, false)
        set(value) = prefs.edit().putBoolean(KEY_ALBUM_FAV, value).apply()

    var artistSort: String
        get() = prefs.getString(KEY_ARTIST_SORT, "NAME") ?: "NAME"
        set(value) = prefs.edit().putString(KEY_ARTIST_SORT, value).apply()

    var artistDescending: Boolean
        get() = prefs.getBoolean(KEY_ARTIST_DESC, false)
        set(value) = prefs.edit().putBoolean(KEY_ARTIST_DESC, value).apply()

    var artistsFavoritesOnly: Boolean
        get() = prefs.getBoolean(KEY_ARTIST_FAV, false)
        set(value) = prefs.edit().putBoolean(KEY_ARTIST_FAV, value).apply()

    var visualizerStyle: Int
        get() = prefs.getInt(KEY_VISUALIZER_STYLE, 2)
        set(value) = prefs.edit().putInt(KEY_VISUALIZER_STYLE, value).apply()

    private fun loadThemeMode(): ThemeMode = runCatching {
        ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)!!)
    }.getOrDefault(ThemeMode.SYSTEM)

    private var autoFavoritesState by mutableStateOf(prefs.getBoolean(KEY_AUTO_FAVORITES, false))

    var autoDownloadFavorites: Boolean
        get() = autoFavoritesState
        set(value) {
            autoFavoritesState = value
            prefs.edit().putBoolean(KEY_AUTO_FAVORITES, value).apply()
        }

    private var autoPlaylistsState by mutableStateOf(prefs.getBoolean(KEY_AUTO_PLAYLISTS, false))

    var autoDownloadPlaylists: Boolean
        get() = autoPlaylistsState
        set(value) {
            autoPlaylistsState = value
            prefs.edit().putBoolean(KEY_AUTO_PLAYLISTS, value).apply()
        }

    private var autoLatestState by mutableStateOf(prefs.getBoolean(KEY_AUTO_LATEST, false))

    var autoDownloadLatest: Boolean
        get() = autoLatestState
        set(value) {
            autoLatestState = value
            prefs.edit().putBoolean(KEY_AUTO_LATEST, value).apply()
        }

    private var autoWifiOnlyState by mutableStateOf(prefs.getBoolean(KEY_AUTO_WIFI_ONLY, true))

    var autoDownloadWifiOnly: Boolean
        get() = autoWifiOnlyState
        set(value) {
            autoWifiOnlyState = value
            prefs.edit().putBoolean(KEY_AUTO_WIFI_ONLY, value).apply()
        }

    private var autoMaxMbState by mutableStateOf(prefs.getInt(KEY_AUTO_MAX_MB, 2048))

    var autoDownloadMaxMb: Int
        get() = autoMaxMbState
        set(value) {
            autoMaxMbState = value
            prefs.edit().putInt(KEY_AUTO_MAX_MB, value).apply()
        }

    private var visualizerState by mutableStateOf(prefs.getBoolean(KEY_VISUALIZER, false))

    var visualizerEnabled: Boolean
        get() = visualizerState
        set(value) {
            visualizerState = value
            prefs.edit().putBoolean(KEY_VISUALIZER, value).apply()
        }

    private var manualOfflineState by mutableStateOf(prefs.getBoolean(KEY_MANUAL_OFFLINE, false))

    var manualOffline: Boolean
        get() = manualOfflineState
        set(value) {
            manualOfflineState = value
            prefs.edit().putBoolean(KEY_MANUAL_OFFLINE, value).apply()
        }

    private var lyricsFontScaleState by mutableStateOf(prefs.getFloat(KEY_LYRICS_FONT_SCALE, 1f))

    var lyricsFontScale: Float
        get() = lyricsFontScaleState
        set(value) {
            val clamped = value.coerceIn(0.7f, 1.7f)
            lyricsFontScaleState = clamped
            prefs.edit().putFloat(KEY_LYRICS_FONT_SCALE, clamped).apply()
        }

    private var lyricsOffsetState by mutableStateOf(prefs.getLong(KEY_LYRICS_OFFSET_MS, 0L))

    var lyricsOffsetMs: Long
        get() = lyricsOffsetState
        set(value) {
            val clamped = value.coerceIn(-10_000L, 10_000L)
            lyricsOffsetState = clamped
            prefs.edit().putLong(KEY_LYRICS_OFFSET_MS, clamped).apply()
        }

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
        const val KEY_AUTO_FAVORITES = "auto_download_favorites"
        const val KEY_AUTO_PLAYLISTS = "auto_download_playlists"
        const val KEY_AUTO_LATEST = "auto_download_latest"
        const val KEY_AUTO_WIFI_ONLY = "auto_download_wifi_only"
        const val KEY_AUTO_MAX_MB = "auto_download_max_mb"
        const val KEY_MANUAL_OFFLINE = "manual_offline"
        const val KEY_CROSSFADE_MS = "crossfade_ms"
        const val KEY_NORMALIZE = "normalize_volume"
        const val KEY_SKIP_SILENCE = "skip_silence"
        const val KEY_PREFETCH = "prefetch_next"
        const val KEY_REPORT_PLAYBACK = "report_playback"
        const val KEY_LYRICS_OFFSETS = "lyrics_offsets"
        const val KEY_ALBUM_SORT = "album_sort"
        const val KEY_ALBUM_DESC = "album_desc"
        const val KEY_ALBUM_FAV = "album_fav_only"
        const val KEY_ARTIST_SORT = "artist_sort"
        const val KEY_ARTIST_DESC = "artist_desc"
        const val KEY_ARTIST_FAV = "artist_fav_only"
        const val KEY_VISUALIZER_STYLE = "visualizer_style"
        const val KEY_VISUALIZER = "visualizer_enabled"
        const val KEY_LYRICS_FONT_SCALE = "lyrics_font_scale"
        const val KEY_LYRICS_OFFSET_MS = "lyrics_offset_ms"
        const val DEFAULT_CACHE_LIMIT_MB = 512
        const val DEFAULT_FADE_MS = 300
    }
}