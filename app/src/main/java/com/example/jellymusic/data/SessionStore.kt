package com.example.jellymusic.data

import android.content.Context

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("jellymusic_session", Context.MODE_PRIVATE)

    var serverUrl: String?
        get() = prefs.getString(KEY_SERVER, null)
        set(value) {
            prefs.edit().putString(KEY_SERVER, value).apply()
        }

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) {
            prefs.edit().putString(KEY_TOKEN, value).apply()
        }

    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) {
            prefs.edit().putString(KEY_USER_ID, value).apply()
        }

    var userName: String?
        get() = prefs.getString(KEY_USER_NAME, null)
        set(value) {
            prefs.edit().putString(KEY_USER_NAME, value).apply()
        }

    val hasSession: Boolean
        get() = !serverUrl.isNullOrBlank() && !token.isNullOrBlank()

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_SERVER = "server_url"
        const val KEY_TOKEN = "access_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_USER_NAME = "user_name"
    }
}