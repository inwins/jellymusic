package com.example.jellymusic.data

import android.content.Context

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

    private companion object {
        const val KEY_DOWNLOAD_TREE = "download_tree_uri"
        const val KEY_DOWNLOAD_LABEL = "download_dir_label"
        const val KEY_CACHE_LIMIT_MB = "stream_cache_limit_mb"
        const val KEY_STREAM_CACHE_ENABLED = "stream_cache_enabled"
        const val DEFAULT_CACHE_LIMIT_MB = 512
    }
}