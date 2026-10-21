package com.example.jellymusic.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.example.jellymusic.data.DownloadStore
import com.example.jellymusic.data.StreamCache
import com.example.jellymusic.data.baseItemFromJson
import com.example.jellymusic.data.toJson
import com.example.jellymusic.jellyfin.BaseItem
import com.example.jellymusic.jellyfin.JellyfinApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@androidx.annotation.OptIn(UnstableApi::class)
class MusicPlayer(context: Context, private val streamCache: StreamCache) {
    var api: JellyfinApi? = null
    var downloadStore: DownloadStore? = null

    private val appContext = context.applicationContext

    private companion object {
        const val KEY_STATE = "playback_state"
    }

    private val defaultMediaSourceFactory =
        DefaultMediaSourceFactory(DefaultDataSource.Factory(context))
    private val cachedMediaSourceFactory =
        DefaultMediaSourceFactory(streamCache.cacheDataSourceFactory)

    private val routingMediaSourceFactory = object : MediaSource.Factory {
        override fun setDrmSessionManagerProvider(
            drmSessionManagerProvider: DrmSessionManagerProvider,
        ): MediaSource.Factory = this

        override fun setLoadErrorHandlingPolicy(
            loadErrorHandlingPolicy: LoadErrorHandlingPolicy,
        ): MediaSource.Factory = this

        override fun getSupportedTypes(): IntArray = defaultMediaSourceFactory.supportedTypes

        override fun createMediaSource(mediaItem: MediaItem): MediaSource {
            val scheme = mediaItem.localConfiguration?.uri?.scheme
            val factory = if (scheme == "file" || scheme == "content") {
                defaultMediaSourceFactory
            } else {
                cachedMediaSourceFactory
            }
            return factory.createMediaSource(mediaItem)
        }
    }

    val exo: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(routingMediaSourceFactory)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    var queue by mutableStateOf<List<BaseItem>>(emptyList())
        private set
    var current by mutableStateOf<BaseItem?>(null)
        private set
    var playIndex by mutableIntStateOf(-1)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isBuffering by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var shuffleEnabled by mutableStateOf(false)
        private set
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set

    private val prefs = appContext.getSharedPreferences("jellymusic_playback", Context.MODE_PRIVATE)
    private var tickCount = 0
    private var restoring = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticker: Job? = null
    private var stopping = false
    private var lastRecordedId: String? = null

    init {
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                saveState()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_ENDED) {
                    val item = current
                    if (!stopping && item != null && item.id != lastRecordedId) {
                        recordPlay(item, isFullyListened())
                        lastRecordedId = item.id
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val previous = current
                if (!stopping && previous != null && previous.id != lastRecordedId) {
                    recordPlay(previous, isFullyListened())
                    lastRecordedId = previous.id
                }
                updateCurrent()
                saveState()
            }
        })
    }

    fun play(items: List<BaseItem>, startIndex: Int) {
        stopping = false
        lastRecordedId = null
        queue = items
        exo.setMediaItems(items.map(::toMediaItem), startIndex, 0L)
        exo.prepare()
        exo.play()
        startPlaybackService()
        updateCurrent()
        saveState()
    }

    fun toggle() {
        if (exo.isPlaying) {
            exo.pause()
        } else {
            exo.play()
            startPlaybackService()
        }
    }

    fun next() {
        exo.seekToNextMediaItem()
    }

    fun previous() {
        if (exo.currentPosition > 3000) exo.seekTo(0) else exo.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        exo.seekTo(positionMs)
    }

    fun jumpTo(index: Int) {
        exo.seekTo(index, 0L)
        if (!exo.isPlaying) exo.play()
    }

    fun stop() {
        stopping = true
        ticker?.cancel()
        ticker = null
        exo.stop()
        exo.clearMediaItems()
        stopPlaybackService()
        queue = emptyList()
        current = null
        playIndex = -1
        positionMs = 0
        durationMs = 0
        isPlaying = false
        isBuffering = false
        lastRecordedId = null
        stopping = false
        clearSavedState()
    }

    private fun startPlaybackService() {
        runCatching {
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, PlaybackService::class.java),
            )
        }
    }

    private fun stopPlaybackService() {
        runCatching {
            appContext.stopService(Intent(appContext, PlaybackService::class.java))
        }
    }

    private fun updateCurrent() {
        val index = exo.currentMediaItemIndex
        playIndex = index
        current = queue.getOrNull(index)
        durationMs = knownDuration()
        startTicker()
    }

    private fun knownDuration(): Long {
        val reported = exo.duration
        return if (reported > 0L) reported else (current?.durationMs ?: 0L)
    }

    private fun isFullyListened(): Boolean {
        val duration = if (durationMs > 0L) durationMs else knownDuration()
        if (duration <= 0L) return false
        val position = maxOf(positionMs, exo.currentPosition)
        return position >= (duration * 0.95).toLong()
    }

    private fun recordPlay(item: BaseItem, fullyListened: Boolean) {
        if (downloadStore?.isDownloaded(item.id) == true) return
        streamCache.recordPlay(item, fullyListened)
    }

    fun toggleShuffle() {
        shuffleEnabled = !shuffleEnabled
        exo.shuffleModeEnabled = shuffleEnabled
        saveState()
    }

    fun cycleRepeat() {
        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        exo.repeatMode = repeatMode
        saveState()
    }

    fun playNext(item: BaseItem) {
        if (queue.isEmpty()) {
            play(listOf(item), 0)
            return
        }
        val index = (exo.currentMediaItemIndex + 1).coerceIn(0, exo.mediaItemCount)
        exo.addMediaItem(index, toMediaItem(item))
        queue = queue.toMutableList().also { it.add(index, item) }
        playIndex = exo.currentMediaItemIndex
        saveState()
    }

    fun addToQueue(item: BaseItem) {
        if (queue.isEmpty()) {
            play(listOf(item), 0)
            return
        }
        exo.addMediaItem(toMediaItem(item))
        queue = queue + item
        saveState()
    }

    fun moveQueueItem(from: Int, to: Int) {
        if (from == to || from !in queue.indices || to !in queue.indices) return
        exo.moveMediaItem(from, to)
        val mutable = queue.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        queue = mutable
        playIndex = exo.currentMediaItemIndex
        current = queue.getOrNull(playIndex)
        saveState()
    }

    fun removeFromQueue(index: Int) {
        if (index !in queue.indices) return
        exo.removeMediaItem(index)
        queue = queue.toMutableList().also { it.removeAt(index) }
        playIndex = exo.currentMediaItemIndex
        current = queue.getOrNull(playIndex)
        saveState()
    }

    fun restoreState() {
        val raw = prefs.getString(KEY_STATE, null) ?: return
        restoring = true
        try {
            val root = JSONObject(raw)
            val array = root.optJSONArray("queue") ?: return
            val items = (0 until array.length()).map { baseItemFromJson(array.getJSONObject(it)) }
            if (items.isEmpty()) return
            val index = root.optInt("index", 0).coerceIn(0, items.lastIndex)
            val position = root.optLong("position", 0L).coerceAtLeast(0L)
            shuffleEnabled = root.optBoolean("shuffle", false)
            repeatMode = root.optInt("repeat", Player.REPEAT_MODE_OFF)

            queue = items
            exo.setMediaItems(items.map(::toMediaItem), index, position)
            exo.shuffleModeEnabled = shuffleEnabled
            exo.repeatMode = repeatMode
            exo.playWhenReady = false
            exo.prepare()
            updateCurrent()
            positionMs = position
        } catch (_: Exception) {
        } finally {
            restoring = false
        }
    }

    private fun saveState() {
        if (restoring) return
        try {
            val items = queue
            if (items.isEmpty()) return
            val array = JSONArray()
            items.forEach { array.put(it.toJson()) }
            val root = JSONObject()
                .put("queue", array)
                .put("index", playIndex.coerceAtLeast(0))
                .put("position", exo.currentPosition.coerceAtLeast(0L))
                .put("shuffle", shuffleEnabled)
                .put("repeat", repeatMode)
            prefs.edit().putString(KEY_STATE, root.toString()).apply()
        } catch (_: Exception) {
        }
    }

    private fun clearSavedState() {
        prefs.edit().remove(KEY_STATE).apply()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                positionMs = exo.currentPosition.coerceAtLeast(0L)
                durationMs = knownDuration()
                if (exo.isPlaying) {
                    tickCount++
                    if (tickCount % 20 == 0) saveState()
                }
                delay(250)
            }
        }
    }

    private fun toMediaItem(item: BaseItem): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(item.name)
            .setArtist(item.primaryArtist)
            .setAlbumTitle(item.album)
        val localUri = downloadStore?.uriFor(item.id)
        val streamingUrl = api?.streamUrl(item)
        if (localUri != null) {
            if (item.hasImage) {
                api?.let { a ->
                    metadataBuilder.setArtworkUri(Uri.parse(a.imageUrl(item.id, 600)))
                }
            }
            return MediaItem.Builder()
                .setMediaId(item.id)
                .setUri(localUri)
                .setMediaMetadata(metadataBuilder.build())
                .build()
        }
        if (streamingUrl != null) {
            val a = api
            if (a != null && item.hasImage) {
                metadataBuilder.setArtworkUri(Uri.parse(a.imageUrl(item.id, 600)))
            }
            return MediaItem.Builder()
                .setMediaId(item.id)
                .setUri(streamingUrl)
                .setMediaMetadata(metadataBuilder.build())
                .build()
        }
        return MediaItem.Builder()
            .setMediaId(item.id)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }
}