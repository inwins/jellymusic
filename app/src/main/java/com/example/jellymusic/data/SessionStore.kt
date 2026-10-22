package com.example.jellymusic.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ServerInfo(
    val url: String,
    val token: String,
    val userId: String,
    val userName: String,
)

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("jellymusic_session", Context.MODE_PRIVATE)

    var serverUrl: String?
        get() = prefs.getString(KEY_SERVER, null)
        set(value) {
            prefs.edit().putString(KEY_SERVER, value).apply()
        }

    var token: String?
        get() = SecretBox.decrypt(prefs.getString(KEY_TOKEN, null))
        set(value) {
            prefs.edit().putString(KEY_TOKEN, value?.let { SecretBox.encrypt(it) }).apply()
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

    fun servers(): List<ServerInfo> {
        val raw = prefs.getString(KEY_SERVERS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val json = array.getJSONObject(index)
                ServerInfo(
                    url = json.optString("url"),
                    token = SecretBox.decrypt(json.optString("token")).orEmpty(),
                    userId = json.optString("userId"),
                    userName = json.optString("userName"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun rememberCurrent() {
        val url = serverUrl ?: return
        val currentToken = token ?: return
        val list = servers().filter { it.url != url }.toMutableList()
        list.add(0, ServerInfo(url, currentToken, userId.orEmpty(), userName.orEmpty()))
        saveServers(list)
    }

    fun setActive(server: ServerInfo) {
        serverUrl = server.url
        token = server.token
        userId = server.userId
        userName = server.userName
        rememberCurrent()
    }

    private fun saveServers(list: List<ServerInfo>) {
        val array = JSONArray()
        list.forEach { server ->
            array.put(
                JSONObject()
                    .put("url", server.url)
                    .put("token", SecretBox.encrypt(server.token))
                    .put("userId", server.userId)
                    .put("userName", server.userName),
            )
        }
        prefs.edit().putString(KEY_SERVERS, array.toString()).apply()
    }

    fun clear() {
        val keep = servers()
        prefs.edit().clear().apply()
        saveServers(keep)
    }

    private companion object {
        const val KEY_SERVER = "server_url"
        const val KEY_TOKEN = "access_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_USER_NAME = "user_name"
        const val KEY_SERVERS = "servers"
    }
}