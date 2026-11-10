package io.github.inwins.jellymusic

import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import io.github.inwins.jellymusic.data.AppSettings
import io.github.inwins.jellymusic.data.AudioFeaturesStore
import io.github.inwins.jellymusic.data.RatingStore
import io.github.inwins.jellymusic.data.TheoryStore
import io.github.inwins.jellymusic.data.AuthHeaders
import io.github.inwins.jellymusic.data.AutoDownloadScheduler
import io.github.inwins.jellymusic.data.DislikeStore
import io.github.inwins.jellymusic.data.DiskCache
import io.github.inwins.jellymusic.data.DownloadStore
import io.github.inwins.jellymusic.data.FavoriteStore
import io.github.inwins.jellymusic.data.LocalMusicStore
import io.github.inwins.jellymusic.data.PlaybackReportStore
import io.github.inwins.jellymusic.data.LocalPlaylistStore
import io.github.inwins.jellymusic.data.NetworkGate
import io.github.inwins.jellymusic.data.SearchHistoryStore
import io.github.inwins.jellymusic.data.SelectionStore
import io.github.inwins.jellymusic.data.SessionStore
import io.github.inwins.jellymusic.data.StreamCache
import io.github.inwins.jellymusic.jellyfin.BaseItem
import io.github.inwins.jellymusic.jellyfin.JellyfinApi
import io.github.inwins.jellymusic.player.LyricsOverlay
import io.github.inwins.jellymusic.player.MusicPlayer
import io.github.inwins.jellymusic.ui.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import okhttp3.Request
import okhttp3.OkHttpClient
import kotlinx.coroutines.withContext

class AppState(private val context: Context) {
    private val session = SessionStore(context)
    val settings = AppSettings(context)
    private val diskCache = DiskCache(context)
    private val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        ?: "jellymusic-android"

    val streamCache = StreamCache(context, settings)
    val audioFeatures = AudioFeaturesStore(context)
    val player = MusicPlayer(context, streamCache, settings, audioFeatures)
    val visualizer = io.github.inwins.jellymusic.player.VisualizerController(context)
    val lyricsOverlay = LyricsOverlay(context)
    val downloads = DownloadStore(context, settings)
    val favorites = FavoriteStore(context)
    val ratings = RatingStore(context)
    val dislikes = DislikeStore(context)
    val theory = TheoryStore(context)
    val playbackReports = PlaybackReportStore(context)
    val localMusic = LocalMusicStore(context)
    val localPlaylists = LocalPlaylistStore(context)
    val searchHistory = SearchHistoryStore(context)
    val selection = SelectionStore()

    val isLoggedIn = mutableStateOf(session.hasSession)
    var homeTab by mutableIntStateOf(0)
    val isOnline = mutableStateOf(true)
    val hasNetwork = mutableStateOf(true)
    val isWifi = mutableStateOf(false)
    val connectionIsLocal = mutableStateOf(false)
    val userName = mutableStateOf(session.userName)
    val localServerUrlState = mutableStateOf(session.localUrl)
    val sessionExpired = mutableStateOf(false)
    val serverName = mutableStateOf<String?>(null)
    val serverVersion = mutableStateOf<String?>(null)
    val serverOs = mutableStateOf<String?>(null)
    val refreshSignal = mutableStateOf(0)

    val backStack = mutableStateListOf<Screen>(Screen.Home)

    var api: JellyfinApi? = null
        private set

    var trackMenu by mutableStateOf<BaseItem?>(null)
    var addToPlaylistItem by mutableStateOf<BaseItem?>(null)
    var addToPlaylistBatch by mutableStateOf<List<BaseItem>>(emptyList())
    var searchRequest by mutableStateOf<String?>(null)
    var localEditItem by mutableStateOf<BaseItem?>(null)
    var audioInfoItem by mutableStateOf<BaseItem?>(null)
    val syncing = mutableStateOf(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var flushingReports = false

    init {
        player.onPlaybackStart = { item ->
            if (settings.reportPlayback && item.localUri == null) {
                playbackReports.enqueue("start", item.id, player.positionMs)
                flushPlaybackReports()
            }
        }
        player.onPlaybackProgress = { item, position ->
            if (settings.reportPlayback && item.localUri == null) {
                playbackReports.enqueue("progress", item.id, position)
                flushPlaybackReports()
            }
        }
        player.onPlaybackStop = { item, fully ->
            if (settings.reportPlayback && item.localUri == null) {
                playbackReports.enqueue("stop", item.id, player.positionMs)
                if (fully) playbackReports.enqueue("played", item.id, 0)
                flushPlaybackReports()
            }
        }
    }

    fun flushPlaybackReports() {
        if (flushingReports) return
        if (NetworkGate.offline) return
        val client = api ?: return
        if (!isOnline.value) return
        if (playbackReports.size() == 0) return
        flushingReports = true
        scope.launch {
            val reports = playbackReports.peekAll()
            val remaining = mutableListOf<io.github.inwins.jellymusic.data.PlaybackReport>()
            for (report in reports) {
                val ok = runCatching {
                    when (report.type) {
                        "start" -> client.reportPlaybackStart(report.itemId, report.positionMs)
                        "progress" -> client.reportPlaybackProgress(
                            report.itemId, report.positionMs, !player.isPlaying,
                        )
                        "stop" -> client.reportPlaybackStopped(report.itemId, report.positionMs)
                        "played" -> client.markPlayed(report.itemId)
                    }
                }.isSuccess
                if (!ok) remaining.add(report)
            }
            playbackReports.replace(remaining)
            flushingReports = false
        }
    }

    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    @Volatile private var refreshingConnection = false
    @Volatile private var lastConnectionProbeAt = 0L

    private val probeClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(1500, TimeUnit.MILLISECONDS)
            .readTimeout(1500, TimeUnit.MILLISECONDS)
            .callTimeout(2500, TimeUnit.MILLISECONDS)
            .build()
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val wasOffline = !isOnline.value
            isOnline.value = true
            hasNetwork.value = true
            updateNetworkType()
            lastServerCheckAt = 0
            if (wasOffline) {
                scope.launch {
                    flushPlaybackReports()
                    syncOfflineChanges()
                    refreshSignal.value += 1
                }
            }
            refreshConnection()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) {
            hasNetwork.value = true
            updateNetworkType()
            if (isLoggedIn.value) refreshConnection()
        }

        override fun onLost(network: Network) {
            hasNetwork.value = connectivityManager?.activeNetwork != null
            isOnline.value = hasNetwork.value
            updateNetworkType()
        }
    }

    private fun updateNetworkType() {
        isWifi.value = runCatching {
            val network = connectivityManager?.activeNetwork ?: return@runCatching false
            connectivityManager?.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }.getOrDefault(false)
    }

    private fun probe(baseUrl: String): Boolean {
        val request = Request.Builder()
            .url("$baseUrl/System/Info/Public")
            .get()
            .build()
        return runCatching {
            probeClient.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    val localServerUrl: String?
        get() = localServerUrlState.value

    fun setLocalServerUrl(value: String?) {
        session.updateLocalUrl(value?.trim()?.trimEnd('/')?.ifBlank { null })
        localServerUrlState.value = session.localUrl
        refreshConnection(force = true)
    }

    fun setPreferLocalAddress(value: Boolean) {
        settings.preferLocalAddress = value
        refreshConnection(force = true)
    }

    /**
     * Picks between the primary (remote) address and the optional LAN address.
     * Probes both, preferring the LAN address on Wi-Fi when enabled.
     */
    fun refreshConnection(force: Boolean = false) {
        if (!isLoggedIn.value) return
        if (NetworkGate.offline) return
        val now = System.currentTimeMillis()
        if (refreshingConnection || (!force && now - lastConnectionProbeAt < 3000L)) return
        val primary = session.serverUrl?.trimEnd('/') ?: return
        val local = session.localUrl?.trimEnd('/')?.takeIf { it.isNotBlank() }
        val preferLocal = settings.preferLocalAddress
        val onWifi = isWifi.value
        refreshingConnection = true
        lastConnectionProbeAt = now
        scope.launch(Dispatchers.IO) {
            try {
                if (local == null && api?.baseUrl?.trimEnd('/') == primary) {
                    withContext(Dispatchers.Main) { connectionIsLocal.value = false }
                    return@launch
                }
                val candidates = LinkedHashSet<String>()
                if (local != null && preferLocal && onWifi) candidates.add(local)
                candidates.add(primary)
                if (local != null) candidates.add(local)
                val chosen = candidates.firstOrNull { probe(it) } ?: primary
                val usingLocal = local != null && chosen == local
                if (api?.baseUrl?.trimEnd('/') == chosen) {
                    withContext(Dispatchers.Main) {
                        connectionIsLocal.value = usingLocal
                    }
                    return@launch
                }
                val client = JellyfinApi(context, chosen, deviceId, Build.MODEL)
                client.accessToken = session.token
                client.userId = session.userId
                setupApi(client)
                withContext(Dispatchers.Main) {
                    api = client
                    player.api = client
                    downloads.resumePending(client)
                    connectionIsLocal.value = usingLocal
                }
                refreshServerInfo()
            } finally {
                refreshingConnection = false
            }
        }
    }

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null

    init {
        hasNetwork.value = connectivityManager?.activeNetwork != null
        NetworkGate.offline = settings.manualOffline || !hasNetwork.value
        scope.launch {
            snapshotFlow { settings.manualOffline || !hasNetwork.value }
                .collect { NetworkGate.offline = it }
        }
        player.downloadStore = downloads
        updateNetworkType()
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
                ComponentName(context, io.github.inwins.jellymusic.player.PlaybackService::class.java),
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
        localServerUrlState.value = session.localUrl
        downloads.resumePending(a)
        AutoDownloadScheduler.schedule(context)
        player.restoreState()
        refreshServerInfo()
        refreshConnection(force = true)
        scope.launch { syncOfflineChanges() }
    }

    suspend fun login(serverInput: String, username: String, password: String): Result<Unit> {
        return try {
            val server = serverInput.trim().trimEnd('/')
            val a = JellyfinApi(context, server, deviceId, Build.MODEL)
            val auth = a.authenticate(username.trim(), password)
            setupApi(a)
            val savedLocal = session.servers()
                .firstOrNull { it.url == server }
                ?.localUrl
                ?.ifBlank { null }
            session.serverUrl = server
            session.token = auth.accessToken
            session.userId = auth.userId
            session.userName = auth.displayName
            session.localUrl = savedLocal
            localServerUrlState.value = session.localUrl
            api = a
            player.api = a
            downloads.resumePending(a)
            AutoDownloadScheduler.schedule(context)
            userName.value = auth.displayName
            sessionExpired.value = false
            isLoggedIn.value = true
            session.rememberCurrent()
            bindPlaybackService()
            backStack.clear()
            backStack.add(Screen.Home)
            refreshServerInfo()
            scope.launch { syncOfflineChanges() }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private var quickApi: io.github.inwins.jellymusic.jellyfin.JellyfinApi? = null

    suspend fun startQuickConnect(serverInput: String): Result<io.github.inwins.jellymusic.jellyfin.QuickConnectState> {
        return try {
            val server = serverInput.trim().trimEnd('/')
            val client = JellyfinApi(context, server, deviceId, Build.MODEL)
            quickApi = client
            Result.success(client.quickConnectInitiate())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pollQuickConnect(secret: String): io.github.inwins.jellymusic.jellyfin.AuthResult? =
        quickApi?.quickConnectAuthenticate(secret)

    fun completeQuickConnect(auth: io.github.inwins.jellymusic.jellyfin.AuthResult) {
        val client = quickApi ?: return
        val server = client.baseUrl
        setupApi(client)
        session.serverUrl = server
        session.token = auth.accessToken
        session.userId = auth.userId
        session.userName = auth.displayName
        api = client
        player.api = client
        downloads.resumePending(client)
        AutoDownloadScheduler.schedule(context)
        userName.value = auth.displayName
        sessionExpired.value = false
        isLoggedIn.value = true
        session.rememberCurrent()
        bindPlaybackService()
        backStack.clear()
        backStack.add(Screen.Home)
        refreshServerInfo()
        scope.launch { syncOfflineChanges() }
        quickApi = null
    }

    fun savedServers(): List<io.github.inwins.jellymusic.data.ServerInfo> = session.servers()

    fun switchServer(server: io.github.inwins.jellymusic.data.ServerInfo) {
        val client = JellyfinApi(context, server.url, deviceId, Build.MODEL)
        client.accessToken = server.token
        client.userId = server.userId
        setupApi(client)
        session.setActive(server)
        localServerUrlState.value = session.localUrl
        api = client
        player.api = client
        downloads.resumePending(client)
        AutoDownloadScheduler.schedule(context)
        userName.value = server.userName
        sessionExpired.value = false
        isLoggedIn.value = true
        backStack.clear()
        backStack.add(Screen.Home)
        refreshServerInfo()
        refreshConnection(force = true)
        scope.launch { syncOfflineChanges() }
    }

    fun logout() {
        sessionExpired.value = false
        lyricsOverlay.hide()
        releasePlaybackService()
        player.stop()
        session.clear()
        localServerUrlState.value = null
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

    fun setManualOffline(enabled: Boolean) {
        settings.manualOffline = enabled
        NetworkGate.offline = enabled || !hasNetwork.value
        if (!enabled) {
            scope.launch {
                checkServer(force = true)
                refreshConnection(force = true)
                if (isOnline.value) {
                    runCatching { syncOfflineChanges() }
                }
                refreshServerInfo()
                refreshSignal.value += 1
            }
        }
    }

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
        if (NetworkGate.offline) return false
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

    private fun refreshServerInfo() {
        if (NetworkGate.offline) return
        val client = api ?: return
        scope.launch {
            runCatching { client.getServerInfo() }.onSuccess { info ->
                serverName.value = info.name
                serverVersion.value = info.version
                serverOs.value = info.os
            }
        }
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
        selection.clear()
        backStack.add(screen)
    }

    fun back() {
        selection.clear()
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    suspend fun queueFolderDownload(folderId: String): Int {
        val api = api ?: return 0
        val audio = mutableListOf<BaseItem>()
        val queue = ArrayDeque<String>()
        queue.add(folderId)
        val visited = mutableSetOf<String>()
        while (queue.isNotEmpty() && audio.size < 5000) {
            val id = queue.removeFirst()
            if (!visited.add(id)) continue
            val children = runCatching { api.getFolderChildren(id) }.getOrDefault(emptyList())
            for (child in children) {
                if (child.type == "Audio") {
                    audio.add(child)
                } else if (child.isFolder) {
                    queue.add(child.id)
                }
            }
        }
        downloads.downloadAll(api, audio)
        return audio.size
    }

    fun offlineSongs(): List<BaseItem> =
        (downloads.downloadedItems() + localMusic.songs).distinctBy { it.id }

    fun offlineAlbums(): List<BaseItem> {
        return offlineSongs()
            .groupBy { it.albumId ?: it.album ?: "未知专辑" }
            .map { (key, list) ->
                BaseItem(
                    id = key,
                    name = list.firstOrNull()?.album ?: "未知专辑",
                    type = "MusicAlbum",
                    isFolder = true,
                    albumArtist = list.firstOrNull()?.albumArtist,
                    hasImage = list.any { it.hasImage },
                    childCount = list.size,
                )
            }
            .sortedBy { it.name }
    }

    fun offlineArtists(): List<BaseItem> {
        return offlineSongs()
            .groupBy { it.trackArtist ?: "未知艺术家" }
            .map { (name, list) ->
                BaseItem(
                    id = name,
                    name = name,
                    type = "MusicArtist",
                    isFolder = true,
                    hasImage = false,
                    childCount = list.size,
                )
            }
            .sortedBy { it.name }
    }

    fun offlineAlbumTracks(key: String): List<BaseItem> =
        offlineSongs()
            .filter { (it.albumId ?: it.album ?: "未知专辑") == key }
            .sortedWith(compareBy({ it.discNumber ?: 1 }, { it.trackNumber ?: 0 }, { it.name }))

    fun offlineArtistTracks(name: String): List<BaseItem> =
        offlineSongs()
            .filter { (it.trackArtist ?: "未知艺术家") == name }
            .sortedWith(compareBy({ it.album ?: "" }, { it.discNumber ?: 1 }, { it.trackNumber ?: 0 }, { it.name }))

    fun artworkUrl(item: BaseItem, size: Int): String? {
        if (item.localUri != null) {
            localMusic.artFile(item.id)?.let { return Uri.fromFile(it).toString() }
        }
        val api = api ?: return null
        val imageId = when {
            item.hasImage -> item.id
            item.albumId != null -> item.albumId
            else -> null
        } ?: return null
        return api.imageUrl(imageId, size)
    }
}