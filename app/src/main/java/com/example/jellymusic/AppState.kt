package com.example.jellymusic

import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.example.jellymusic.data.AppSettings
import com.example.jellymusic.data.AuthHeaders
import com.example.jellymusic.data.AutoDownloadScheduler
import com.example.jellymusic.data.DiskCache
import com.example.jellymusic.data.DownloadStore
import com.example.jellymusic.data.FavoriteStore
import com.example.jellymusic.data.LocalPlaylistStore
import com.example.jellymusic.data.SearchHistoryStore
import com.example.jellymusic.data.SessionStore
import com.example.jellymusic.data.StreamCache
import com.example.jellymusic.jellyfin.BaseItem
import com.example.jellymusic.jellyfin.JellyfinApi
import com.example.jellymusic.player.LyricsOverlay
import com.example.jellymusic.player.MusicPlayer
import com.example.jellymusic.ui.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppState(private val context: Context) {
    private val session = SessionStore(context)
    val settings = AppSettings(context)
    private val diskCache = DiskCache(context)
    private val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        ?: "jellymusic-android"

    val streamCache = StreamCache(context, settings)
    val player = MusicPlayer(context, streamCache, settings)
    val lyricsOverlay = LyricsOverlay(context)
    val downloads = DownloadStore(context, settings)
    val favorites = FavoriteStore(context)
    val localPlaylists = LocalPlaylistStore(context)
    val searchHistory = SearchHistoryStore(context)

    val isLoggedIn = mutableStateOf(session.hasSession)
    val isOnline = mutableStateOf(true)
    val userName = mutableStateOf(session.userName)
    val sessionExpired = mutableStateOf(false)
    val refreshSignal = mutableStateOf(0)

    val backStack = mutableStateListOf<Screen>(Screen.Home)

    var api: JellyfinApi? = null
        private set

    var trackMenu by mutableStateOf<BaseItem?>(null)
    var addToPlaylistItem by mutableStateOf<BaseItem?>(null)
    val syncing = mutableStateOf(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val wasOffline = !isOnline.value
            isOnline.value = true
            lastServerCheckAt = 0
            if (wasOffline) {
                scope.launch {
                    syncOfflineChanges()
                    refreshSignal.value += 1
                }
            }
        }

        override fun onLost(network: Network) {
            isOnline.value = connectivityManager?.activeNetwork != null
        }
    }

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null

    init {
        player.downloadStore = downloads
        runCatching { connectivityManager?.registerDefaultNetworkCallback(networkCallback) }
        bindPlaybackService()
        if (session.hasSession) {
            restoreSession()
        }
    }

    private fun bindPlaybackService() {
        if (mediaControllerFuture != null) return
        runCatching {
            val token = SessionToken(
                context,
                ComponentName(context, com.example.jellymusic.player.PlaybackService::class.java),
            )
            val future = MediaController.Builder(context, token).buildAsync()
            future.addListener(
                { runCatching { future.get() } },
                MoreExecutors.directExecutor(),
            )
            mediaControllerFuture = future
        }
    }

    private fun releasePlaybackService() {
        mediaControllerFuture?.let { future ->
            runCatching { MediaController.releaseFuture(future) }
        }
        mediaControllerFuture = null
    }

    private fun restoreSession() {
        val server = session.serverUrl ?: return
        val a = JellyfinApi(context, server, deviceId, Build.MODEL)
        a.accessToken = session.token
        a.userId = session.userId
        setupApi(a)
        api = a
        player.api = a
        downloads.resumePending(a)
        AutoDownloadScheduler.schedule(context)
        player.restoreState()
        scope.launch { syncOfflineChanges() }
    }

    suspend fun login(serverInput: String, username: String, password: String): Result<Unit> {
        return try {
            val server = serverInput.trim().trimEnd('/')
            val a = JellyfinApi(context, server, deviceId, Build.MODEL)
            val auth = a.authenticate(username.trim(), password)
            setupApi(a)
            session.serverUrl = server
            session.token = auth.accessToken
            session.userId = auth.userId
            session.userName = auth.displayName
            api = a
            player.api = a
            downloads.resumePending(a)
            AutoDownloadScheduler.schedule(context)
            userName.value = auth.displayName
            sessionExpired.value = false
            isLoggedIn.value = true
            bindPlaybackService()
            backStack.clear()
            backStack.add(Screen.Home)
            scope.launch { syncOfflineChanges() }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        sessionExpired.value = false
        lyricsOverlay.hide()
        releasePlaybackService()
        player.stop()
        session.clear()
        diskCache.clear()
        AuthHeaders.clear()
        streamCache.setAccessToken(null)
        api = null
        player.api = null
        userName.value = null
        isLoggedIn.value = false
        backStack.clear()
        backStack.add(Screen.Home)
    }

    val serverUrl: String?
        get() = api?.baseUrl ?: session.serverUrl

    private var lastServerCheckAt = 0L

    val isOffline: Boolean
        get() = settings.manualOffline || !isOnline.value

    suspend fun checkServer(force: Boolean = false): Boolean {
        if (settings.manualOffline) return false
        val now = System.currentTimeMillis()
        if (!force && now - lastServerCheckAt < 30_000L) return isOnline.value
        lastServerCheckAt = now
        val current = api
        val online = current != null && current.ping()
        isOnline.value = online
        return online
    }

    suspend fun syncOfflineChanges(): Boolean {
        val apiClient = api ?: return false
        if (settings.manualOffline) return false
        if (syncing.value) return false
        if (!checkServer()) return false
        syncing.value = true
        val ok = try {
            val favoritesOk = favorites.sync(apiClient)
            val playlistsOk = localPlaylists.sync(apiClient)
            if (favoritesOk || playlistsOk) {
                apiClient.cache?.clear()
            }
            favoritesOk && playlistsOk
        } catch (_: Exception) {
            false
        } finally {
            syncing.value = false
        }
        return ok
    }

    private fun setupApi(apiClient: JellyfinApi) {
        apiClient.cache = diskCache
        apiClient.onUnauthorized = {
            scope.launch { onSessionExpired() }
        }
        streamCache.serverHost = runCatching {
            Uri.parse(apiClient.baseUrl).let { "${it.host}:${it.port}" }
        }.getOrNull()
        AuthHeaders.authorization = apiClient.currentAuthorization()
        AuthHeaders.host = runCatching { Uri.parse(apiClient.baseUrl).host }.getOrNull()
        streamCache.setAccessToken(apiClient.currentAuthorization())
    }

    private fun onSessionExpired() {
        if (!isLoggedIn.value) return
        logout()
        sessionExpired.value = true
    }

    fun toggleDesktopLyrics() {
        lyricsOverlay.toggle(player, settings) { api }
    }

    fun requireApi(): JellyfinApi =
        api ?: throw IllegalStateException("Not logged in")

    fun navigate(screen: Screen) {
        backStack.add(screen)
    }

    fun back() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    fun artworkUrl(item: BaseItem, size: Int): String? {
        val api = api ?: return null
        val imageId = when {
            item.hasImage -> item.id
            item.albumId != null -> item.albumId
            else -> null
        } ?: return null
        return api.imageUrl(imageId, size)
    }
}