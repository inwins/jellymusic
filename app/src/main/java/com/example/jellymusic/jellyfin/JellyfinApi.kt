package com.example.jellymusic.jellyfin

import android.content.Context
import android.util.Log
import com.example.jellymusic.R
import com.example.jellymusic.data.DiskCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class AuthResult(
    val accessToken: String,
    val userId: String,
    val displayName: String,
)

class JellyfinApi(
    private val context: Context,
    val baseUrl: String,
    private val deviceId: String,
    private val deviceName: String,
) {
    var accessToken: String? = null
    var userId: String? = null
    var cache: DiskCache? = null
    var onUnauthorized: (() -> Unit)? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val TAG = "JellyfinApi"

    private fun authHeader(): String =
        "MediaBrowser Client=\"Jellyfin Music\", Device=\"$deviceName\", DeviceId=\"$deviceId\", Version=\"1.0.0\""

    fun currentAuthorization(): String {
        val base = authHeader()
        return if (accessToken == null) base else "$base, Token=\"$accessToken\""
    }

    private fun Request.Builder.auth(): Request.Builder {
        header("Authorization", currentAuthorization())
        return this
    }

    private fun buildUrl(path: String, params: Map<String, String>): String {
        if (params.isEmpty()) return "$baseUrl$path"
        val query = params.entries.joinToString("&") { (k, v) ->
            "$k=${URLEncoder.encode(v, "UTF-8")}"
        }
        return "$baseUrl$path?$query"
    }

    private fun execute(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            Log.d(TAG, "${request.method} ${request.url} -> ${response.code}")
            if (!response.isSuccessful) {
                Log.w(TAG, "HTTP ${response.code}: $body")
                if (response.code == 401 && accessToken != null) {
                    onUnauthorized?.invoke()
                }
                throw IOException(describeError(response.code, body))
            }
            return JSONObject(body)
        }
    }

    private fun describeError(code: Int, body: String): String = when (code) {
        401 -> context.getString(R.string.error_auth)
        403 -> context.getString(R.string.error_forbidden)
        404 -> context.getString(R.string.error_not_found)
        else -> context.getString(R.string.error_http, code, body)
    }

    suspend fun authenticate(username: String, password: String): AuthResult =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "authenticate baseUrl=$baseUrl user=[$username] pwLen=${password.length}")
            val payload = JSONObject()
                .put("Username", username)
                .put("Pw", password)
            val request = Request.Builder()
                .url("$baseUrl/Users/AuthenticateByName")
                .auth()
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()
            val json = execute(request)
            val token = json.getString("AccessToken")
            val user = json.getJSONObject("User")
            accessToken = token
            userId = user.getString("Id")
            AuthResult(accessToken = token, userId = userId!!, displayName = user.getString("Name"))
        }

    suspend fun getLatest(limit: Int = 30): List<BaseItem> = items(
        "/Users/$userId/Items",
        mapOf(
            "Recursive" to "true",
            "IncludeItemTypes" to "Audio",
            "SortBy" to "DateCreated",
            "SortOrder" to "Descending",
            "Limit" to limit.toString(),
        ),
    )

    suspend fun getFavorites(): List<BaseItem> = items(
        "/Users/$userId/Items",
        mapOf(
            "Recursive" to "true",
            "IncludeItemTypes" to "Audio",
            "Filters" to "IsFavorite",
            "SortBy" to "SortName",
        ),
    )

    suspend fun getAlbums(
        startIndex: Int = 0,
        limit: Int = 100,
        sortBy: String = "SortName",
        sortOrder: String = "Ascending",
    ): List<BaseItem> = items(
        "/Users/$userId/Items",
        mapOf(
            "Recursive" to "true",
            "IncludeItemTypes" to "MusicAlbum",
            "SortBy" to sortBy,
            "SortOrder" to sortOrder,
            "StartIndex" to startIndex.toString(),
            "Limit" to limit.toString(),
        ),
    )

    suspend fun getArtists(
        startIndex: Int = 0,
        limit: Int = 100,
        sortBy: String = "SortName",
        sortOrder: String = "Ascending",
    ): List<BaseItem> = items(
        "/Artists",
        mapOf(
            "UserId" to (userId ?: ""),
            "Recursive" to "true",
            "SortBy" to sortBy,
            "SortOrder" to sortOrder,
            "StartIndex" to startIndex.toString(),
            "Limit" to limit.toString(),
        ),
    )

    suspend fun getArtistAlbums(artistId: String): List<BaseItem> = items(
        "/Users/$userId/Items",
        mapOf(
            "ArtistIds" to artistId,
            "Recursive" to "true",
            "IncludeItemTypes" to "MusicAlbum",
            "SortBy" to "SortName",
            "SortOrder" to "Ascending",
        ),
    )

    suspend fun getPlaylists(): List<BaseItem> = items(
        "/Users/$userId/Items",
        mapOf(
            "Recursive" to "true",
            "IncludeItemTypes" to "Playlist",
            "SortBy" to "SortName",
            "SortOrder" to "Ascending",
        ),
    )

    suspend fun getAlbumTracks(albumId: String): List<BaseItem> = items(
        "/Users/$userId/Items",
        mapOf(
            "ParentId" to albumId,
            "SortBy" to "SortName",
            "SortOrder" to "Ascending",
        ),
    )

    suspend fun getPlaylistTracks(
        playlistId: String,
        fresh: Boolean = false,
    ): List<BaseItem> = items(
        "/Users/$userId/Items",
        mapOf(
            "ParentId" to playlistId,
            "SortBy" to "SortName",
            "SortOrder" to "Ascending",
        ),
        ttlMs = if (fresh) 0L else ITEMS_TTL_MS,
    )

    suspend fun searchSongs(query: String, limit: Int = 50): List<BaseItem> =
        search(query, "Audio", limit)

    suspend fun search(
        query: String,
        includeItemTypes: String,
        limit: Int = 50,
    ): List<BaseItem> = items(
        "/Users/$userId/Items",
        mapOf(
            "SearchTerm" to query,
            "Recursive" to "true",
            "IncludeItemTypes" to includeItemTypes,
            "Limit" to limit.toString(),
            "SortBy" to "SortName",
            "SortOrder" to "Ascending",
        ),
    )

    suspend fun searchArtists(query: String, limit: Int = 50): List<BaseItem> = items(
        "/Artists",
        mapOf(
            "SearchTerm" to query,
            "UserId" to (userId ?: ""),
            "Recursive" to "true",
            "Limit" to limit.toString(),
        ),
    )

    suspend fun getUserViews(): List<BaseItem> = items(
        "/Users/$userId/Views",
        emptyMap(),
    )

    suspend fun getFolderChildren(parentId: String): List<BaseItem> = items(
        "/Users/$userId/Items",
        mapOf(
            "ParentId" to parentId,
            "SortBy" to "SortName",
            "SortOrder" to "Ascending",
        ),
    )

    suspend fun getGenres(): List<BaseItem> = items(
        "/MusicGenres",
        mapOf(
            "UserId" to (userId ?: ""),
            "SortBy" to "SortName",
            "SortOrder" to "Ascending",
        ),
    )

    suspend fun getGenreSongs(genreId: String, limit: Int = 500): List<BaseItem> = items(
        "/Users/$userId/Items",
        mapOf(
            "GenreIds" to genreId,
            "Recursive" to "true",
            "IncludeItemTypes" to "Audio",
            "SortBy" to "SortName",
            "SortOrder" to "Ascending",
            "Limit" to limit.toString(),
        ),
    )

    suspend fun getAllSongs(startIndex: Int, limit: Int): List<BaseItem> = items(
        "/Users/$userId/Items",
        mapOf(
            "Recursive" to "true",
            "IncludeItemTypes" to "Audio",
            "SortBy" to "SortName",
            "SortOrder" to "Ascending",
            "StartIndex" to startIndex.toString(),
            "Limit" to limit.toString(),
        ),
    )

    private suspend fun items(
        path: String,
        params: Map<String, String>,
        ttlMs: Long = ITEMS_TTL_MS,
    ): List<BaseItem> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(buildUrl(path, params))
                .auth()
                .get()
                .build()
            val json = cachedJson(request, ttlMs)
            val arr = json.optJSONArray("Items") ?: JSONArray()
            (0 until arr.length()).map { parseBaseItem(arr.getJSONObject(it)) }
        }

    private fun cachedJson(request: Request, ttlMs: Long): JSONObject {
        val key = request.url.toString()
        cache?.get(key, ttlMs)?.let { cached ->
            runCatching { JSONObject(cached) }.getOrNull()?.let { return it }
        }
        return try {
            val json = execute(request)
            cache?.put(key, json.toString())
            json
        } catch (e: IOException) {
            val stale = cache?.getAny(key)
            if (stale != null) {
                runCatching { JSONObject(stale) }.getOrNull()?.let { return it }
            }
            throw e
        }
    }

    fun streamUrl(item: BaseItem): String {
        return if (item.container != null) {
            "$baseUrl/Audio/${item.id}/stream?static=true&container=${item.container}"
        } else {
            "$baseUrl/Audio/${item.id}/stream"
        }
    }

    fun imageUrl(itemId: String, size: Int): String =
        "$baseUrl/Items/$itemId/Images/Primary?maxHeight=$size&maxWidth=$size&quality=90"

    suspend fun ping(timeoutMs: Long = 3000L): Boolean = withContext(Dispatchers.IO) {
        try {
            val shortClient = client.newBuilder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build()
            val request = Request.Builder()
                .url("$baseUrl/System/Info/Public")
                .get()
                .build()
            shortClient.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getLyrics(itemId: String): List<LyricLine> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/Audio/$itemId/Lyrics"
        cache?.get(url, LYRICS_TTL_MS)?.let { return@withContext parseLyrics(it) }
        try {
            val request = Request.Builder()
                .url(url)
                .auth()
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    cache?.put(url, "{\"Lyrics\":[]}")
                    return@withContext emptyList()
                }
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string().orEmpty()
                cache?.put(url, body)
                return@withContext parseLyrics(body)
            }
        } catch (e: Exception) {
            val stale = cache?.getAny(url)
            if (stale != null) parseLyrics(stale) else emptyList()
        }
    }

    private fun parseLyrics(body: String): List<LyricLine> {
        if (body.isBlank()) return emptyList()
        return try {
            val json = JSONObject(body)
            val array = json.optJSONArray("Lyrics") ?: return emptyList()
            (0 until array.length()).mapNotNull { index ->
                val line = array.optJSONObject(index) ?: return@mapNotNull null
                val text = line.optString("Text")
                if (text.isBlank()) return@mapNotNull null
                val startTicks = if (line.has("Start") && !line.isNull("Start")) {
                    line.optLong("Start")
                } else {
                    null
                }
                LyricLine(text = text, startMs = startTicks?.let { it / 10_000 })
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun downloadAudio(
        item: BaseItem,
        output: java.io.OutputStream,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(streamUrl(item))
            .auth()
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(context.getString(R.string.download_failed_http, response.code))
            }
            val body = response.body ?: throw IOException(context.getString(R.string.download_failed_empty))
            val total = body.contentLength().takeIf { it > 0 }
            body.byteStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                var downloaded = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    onProgress(downloaded, total)
                }
                output.flush()
            }
        }
    }

    suspend fun setFavorite(itemId: String, favorite: Boolean) = withContext(Dispatchers.IO) {
        val request = if (favorite) {
            Request.Builder()
                .url("$baseUrl/Users/$userId/FavoriteItems/$itemId")
                .auth()
                .post(ByteArray(0).toRequestBody())
                .build()
        } else {
            Request.Builder()
                .url("$baseUrl/Users/$userId/FavoriteItems/$itemId")
                .auth()
                .delete()
                .build()
        }
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(describeError(response.code, response.body?.string().orEmpty()))
            }
        }
    }

    suspend fun createPlaylist(name: String, itemIds: List<String>): String =
        withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("Name", name)
                .put("Ids", JSONArray(itemIds))
                .put("MediaType", "Audio")
                .put("UserId", userId)
            val request = Request.Builder()
                .url("$baseUrl/Playlists")
                .auth()
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()
            execute(request).getString("Id")
        }

    suspend fun addToPlaylist(playlistId: String, itemIds: List<String>) =
        withContext(Dispatchers.IO) {
            val ids = itemIds.joinToString(",") { URLEncoder.encode(it, "UTF-8") }
            val request = Request.Builder()
                .url("$baseUrl/Playlists/$playlistId/Items?Ids=$ids&UserId=$userId")
                .auth()
                .post(ByteArray(0).toRequestBody())
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException(describeError(response.code, response.body?.string().orEmpty()))
                }
            }
        }

    suspend fun updatePlaylistName(playlistId: String, name: String) =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().put("Name", name)
            val request = Request.Builder()
                .url("$baseUrl/Playlists/$playlistId")
                .auth()
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException(describeError(response.code, response.body?.string().orEmpty()))
                }
            }
        }

    suspend fun removeFromPlaylist(playlistId: String, entryIds: List<String>) =
        withContext(Dispatchers.IO) {
            val ids = entryIds.joinToString(",") { URLEncoder.encode(it, "UTF-8") }
            val request = Request.Builder()
                .url("$baseUrl/Playlists/$playlistId/Items?EntryIds=$ids")
                .auth()
                .delete()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException(describeError(response.code, response.body?.string().orEmpty()))
                }
            }
        }

    suspend fun deleteItem(itemId: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/Items/$itemId")
            .auth()
            .delete()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(describeError(response.code, response.body?.string().orEmpty()))
            }
        }
    }

    private companion object {
        const val ITEMS_TTL_MS = 10 * 60 * 1000L
        const val LYRICS_TTL_MS = 24 * 60 * 60 * 1000L
    }
}
