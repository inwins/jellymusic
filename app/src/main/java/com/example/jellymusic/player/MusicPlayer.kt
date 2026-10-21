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
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.example.jellymusic.data.AppSettings
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

enum class PlayMode {
    SEQUENTIAL,
    LIST_LOOP,
    SINGLE_LOOP,
    SHUFFLE,
}

@androidx.annotation.OptIn(UnstableApi::class)
class MusicPlayer(
    context: Context,
    private val streamCache: StreamCache,
    private val settings: AppSettings,
) {
    var api: JellyfinApi? = null
    var downloadStore: DownloadStore? = null

    private val appContext = context.applicationContext

    private companion object {
        const val KEY_STATE = "playback_state"
        const val KEY_RESUME = "resume_positions"
        const val MAX_RESUME_ENTRIES = 500
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
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(15_000, 60_000, 2_500, 5_000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build(),
        )
        .setPauseAtEndOfMediaItems(false)
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
    var playMode by mutableStateOf(PlayMode.SEQUENTIAL)
        private set
    var playbackSpeed by mutableStateOf(settings.playbackSpeed)
        private set
    var sleepTimerEndsAt by mutableStateOf<Long?>(null)
        private set
    var sleepAtEndOfTrack by mutableStateOf(false)
        private set

    private val resumePositions = mutableMapOf<String, Long>()
    private var fadeJob: Job? = null
    private var bufferingSince = 0L
    private var stallStage = 0

    private val prefs = appContext.getSharedPreferences("jellymusic_playback", Context.MODE_PRIVATE)
    private var tickCount = 0
    private var restoring = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticker: Job? = null
    private var stopping = false
    private var lastRecordedId: String? = null

    init {
        exo.setPlaybackParameters(PlaybackParameters(playbackSpeed))
        loadResumePositions()

        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing) {
                    saveResumePosition()
                }
                saveState()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState != Player.STATE_BUFFERING) {
                    bufferingSince = 0L
                    stallStage = 0
                }
                if (playbackState == Player.STATE_ENDED) {
                    val item = current
                    if (!stopping && item != null && item.id != lastRecordedId) {
                        recordPlay(item, isFullyListened())
                        lastRecordedId = item.id
                    }
                    if (sleepAtEndOfTrack) {
                        sleepAtEndOfTrack = false
                        exo.volume = 1f
                        exo.pause()
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val previous = current
                if (!stopping && previous != null && previous.id != lastRecordedId) {
                    recordPlay(previous, isFullyListened())
                    lastRecordedId = previous.id
                }
                if (!stopping && previous != null) {
                    saveResumePosition(
                        item = previous,
                        position = maxOf(positionMs, exo.currentPosition),
                    )
                }
                updateCurrent()
                if (sleepAtEndOfTrack) {
                    sleepAtEndOfTrack = false
                    fadeTo(0f, settings.fadeMs.toLong()) {
                        exo.pause()
                        exo.volume = 1f
                    }
                }
                saveState()
            }
        })
    }

    fun playShuffled(items: List<BaseItem>) {
        if (items.isEmpty()) return
        stopping = false
        lastRecordedId = null
        playMode = PlayMode.SHUFFLE
        applyPlayMode(playMode)
        queue = items
        exo.setMediaItems(items.map(::toMediaItem))
        exo.prepare()
        exo.volume = 0f
        exo.play()
        startPlaybackService()
        fadeTo(1f, settings.fadeMs.toLong())
        updateCurrent()
        saveState()
    }

    fun play(items: List<BaseItem>, startIndex: Int) {
        stopping = false
        lastRecordedId = null
        queue = items
        val startItem = items.getOrNull(startIndex)
        val startPosition = if (settings.rememberPosition && startItem != null) {
            resumePositionFor(startItem)
        } else {
            0L
        }
        exo.setMediaItems(items.map(::toMediaItem), startIndex, startPosition)
        exo.prepare()
        exo.volume = 0f
        exo.play()
        startPlaybackService()
        fadeTo(1f, settings.fadeMs.toLong())
        updateCurrent()
        positionMs = startPosition
        saveState()
    }

    fun toggle() {
        if (exo.playWhenReady) {
            fadeTo(0f, settings.fadeMs.toLong()) {
                exo.pause()
                exo.volume = 1f
            }
        } else {
            exo.volume = 0f
            exo.play()
            startPlaybackService()
            fadeTo(1f, settings.fadeMs.toLong())
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
        val item = queue.getOrNull(index)
        val position = if (settings.rememberPosition && item != null) {
            resumePositionFor(item)
        } else {
            0L
        }
        exo.seekTo(index, position)
        if (!exo.isPlaying) {
            exo.volume = 0f
            exo.play()
            fadeTo(1f, settings.fadeMs.toLong())
        }
    }

    fun stop() {
        saveResumePosition()
        stopping = true
        fadeJob?.cancel()
        fadeJob = null
        sleepTimerEndsAt = null
        sleepAtEndOfTrack = false
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

    fun changePlaybackSpeed(speed: Float) {
        playbackSpeed = speed
        settings.playbackSpeed = speed
        exo.setPlaybackParameters(PlaybackParameters(speed))
        saveState()
    }

    fun setSleepTimerMinutes(minutes: Int?) {
        sleepAtEndOfTrack = false
        sleepTimerEndsAt = minutes?.let { System.currentTimeMillis() + it * 60_000L }
    }

    fun setSleepAtEndOfTrack() {
        sleepTimerEndsAt = null
        sleepAtEndOfTrack = true
    }

    fun clearSleepTimer() {
        sleepTimerEndsAt = null
        sleepAtEndOfTrack = false
    }

    private fun fadeTo(target: Float, durationMs: Long, onEnd: (() -> Unit)? = null) {
        fadeJob?.cancel()
        val start = exo.volume
        if (!settings.fadeEnabled || durationMs <= 0L) {
            exo.volume = target
            onEnd?.invoke()
            return
        }
        fadeJob = scope.launch {
            val steps = (durationMs / 30L).coerceAtLeast(1L).toInt()
            for (i in 1..steps) {
                val value = start + (target - start) * i / steps
                exo.volume = value.coerceIn(0f, 1f)
                delay(durationMs / steps)
            }
            exo.volume = target.coerceIn(0f, 1f)
            onEnd?.invoke()
        }
    }

    private fun resumePositionFor(item: BaseItem): Long {
        val saved = resumePositions[item.id] ?: return 0L
        val duration = item.durationMs ?: 0L
        if (saved < 5_000L) return 0L
        if (duration > 0L && saved >= (duration * 0.95).toLong()) return 0L
        return saved
    }

    private fun saveResumePosition(
        item: BaseItem? = current,
        position: Long = exo.currentPosition,
    ) {
        val baseItem = item ?: return
        val duration = if (durationMs > 0L) durationMs else (baseItem.durationMs ?: 0L)
        if (position < 5_000L) {
            resumePositions.remove(baseItem.id)
            saveResumePositions()
            return
        }
        if (duration > 0L && position >= (duration * 0.95).toLong()) {
            resumePositions.remove(baseItem.id)
            saveResumePositions()
            return
        }
        resumePositions[baseItem.id] = position
        while (resumePositions.size > MAX_RESUME_ENTRIES) {
            val first = resumePositions.keys.firstOrNull() ?: break
            resumePositions.remove(first)
        }
        saveResumePositions()
    }

    private fun saveResumePositions() {
        runCatching {
            val json = JSONObject()
            resumePositions.forEach { (id, position) -> json.put(id, position) }
            prefs.edit().putString(KEY_RESUME, json.toString()).apply()
        }
    }

    private fun loadResumePositions() {
        val raw = prefs.getString(KEY_RESUME, null) ?: return
        runCatching {
            val json = JSONObject(raw)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                resumePositions[key] = json.optLong(key)
            }
        }
    }

    fun selectPlayMode(mode: PlayMode) {
        playMode = mode
        applyPlayMode(mode)
        saveState()
    }

    fun cyclePlayMode() {
        selectPlayMode(
            when (playMode) {
                PlayMode.SEQUENTIAL -> PlayMode.LIST_LOOP
                PlayMode.LIST_LOOP -> PlayMode.SINGLE_LOOP
                PlayMode.SINGLE_LOOP -> PlayMode.SHUFFLE
                PlayMode.SHUFFLE -> PlayMode.SEQUENTIAL
            },
        )
    }

    private fun applyPlayMode(mode: PlayMode) {
        when (mode) {
            PlayMode.SEQUENTIAL -> {
                exo.shuffleModeEnabled = false
                exo.repeatMode = Player.REPEAT_MODE_OFF
            }

            PlayMode.LIST_LOOP -> {
                exo.shuffleModeEnabled = false
                exo.repeatMode = Player.REPEAT_MODE_ALL
            }

            PlayMode.SINGLE_LOOP -> {
                exo.shuffleModeEnabled = false
                exo.repeatMode = Player.REPEAT_MODE_ONE
            }

            PlayMode.SHUFFLE -> {
                exo.shuffleModeEnabled = true
                exo.repeatMode = Player.REPEAT_MODE_ALL
            }
        }
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
            val legacyRepeat = root.optInt("repeat", Player.REPEAT_MODE_OFF)
            playMode = runCatching { PlayMode.valueOf(root.optString("mode", "")) }.getOrNull()
                ?: when {
                    root.optBoolean("shuffle", false) -> PlayMode.SHUFFLE
                    legacyRepeat == Player.REPEAT_MODE_ONE -> PlayMode.SINGLE_LOOP
                    legacyRepeat == Player.REPEAT_MODE_ALL -> PlayMode.LIST_LOOP
                    else -> PlayMode.SEQUENTIAL
                }

            queue = items
            exo.setMediaItems(items.map(::toMediaItem), index, position)
            applyPlayMode(playMode)
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
                .put("mode", playMode.name)
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
                    if (tickCount % 20 == 0) {
                        saveState()
                        saveResumePosition()
                    }
                }
                val now = System.currentTimeMillis()
                val sleepEnd = sleepTimerEndsAt
                if (sleepEnd != null && now >= sleepEnd) {
                    sleepTimerEndsAt = null
                    fadeTo(0f, settings.fadeMs.toLong()) {
                        exo.pause()
                        exo.volume = 1f
                    }
                }
                if (exo.playWhenReady &&
                    exo.playbackState == Player.STATE_BUFFERING &&
                    exo.mediaItemCount > 0
                ) {
                    if (bufferingSince == 0L) bufferingSince = now
                    val stalledFor = now - bufferingSince
                    if (stalledFor > 30_000L && stallStage == 0) {
                        stallStage = 1
                        exo.seekTo(exo.currentPosition)
                    } else if (stalledFor > 60_000L && stallStage == 1) {
                        stallStage = 2
                        if (exo.hasNextMediaItem()) {
                            exo.seekToNextMediaItem()
                        } else {
                            exo.pause()
                        }
                    }
                } else if (!isBuffering) {
                    bufferingSince = 0L
                    stallStage = 0
                }
                delay(250)
            }
        }
    }

    private fun toMediaItem(item: BaseItem): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(item.name)
            .setArtist(item.trackArtist)
            .setAlbumArtist(item.albumArtist)
            .setAlbumTitle(item.album)

        val artworkId = if (item.hasImage) item.id else item.albumId
        if (artworkId != null) {
            api?.let { metadataBuilder.setArtworkUri(Uri.parse(it.imageUrl(artworkId, 600))) }
        }

        val localUri = downloadStore?.uriFor(item.id)
        val streamingUrl = api?.streamUrl(item)
        if (localUri != null) {
            return MediaItem.Builder()
                .setMediaId(item.id)
                .setUri(localUri)
                .setMediaMetadata(metadataBuilder.build())
                .build()
        }
        if (streamingUrl != null) {
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