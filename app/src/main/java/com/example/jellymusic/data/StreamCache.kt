package com.example.jellymusic.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.example.jellymusic.jellyfin.BaseItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class StreamCacheEntry(
    val key: String,
    val item: BaseItem,
    var playCount: Int,
    var lastPlayedAt: Long,
    val cachedAt: Long,
)

@androidx.annotation.OptIn(UnstableApi::class)
class StreamCache(context: Context, private val settings: AppSettings) {
    private val cacheDir = File(context.cacheDir, "stream_cache")
    private val indexFile = File(context.filesDir, "stream_cache_index.json")
    private val databaseProvider = StandaloneDatabaseProvider(context)

    val simpleCache: SimpleCache = SimpleCache(cacheDir, NoOpCacheEvictor(), databaseProvider)

    private val entries = ConcurrentHashMap<String, StreamCacheEntry>()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var serverHost: String? = null

    private val upstreamFactory = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(30_000)

    val cacheDataSourceFactory: DataSource.Factory = CacheDataSource.Factory()
        .setCache(simpleCache)
        .setUpstreamDataSourceFactory(upstreamFactory)
        .setCacheKeyFactory(CacheKeyFactory { dataSpec -> keyFromUri(dataSpec.uri) })
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    fun setAccessToken(authorization: String?) {
        val headers = if (authorization.isNullOrBlank()) {
            emptyMap()
        } else {
            mapOf("Authorization" to authorization)
        }
        runCatching { upstreamFactory.setDefaultRequestProperties(headers) }
    }

    val revision = mutableStateOf(0)

    fun cachedItems(): List<BaseItem> =
        entries.values.map { it.item }.sortedBy { it.name }

    var enabled: Boolean
        get() = settings.streamCacheEnabled
        set(value) {
            settings.streamCacheEnabled = value
        }

    var limitMb: Int
        get() = settings.cacheLimitMb
        set(value) {
            settings.cacheLimitMb = value
            ioScope.launch { evictIfNeeded(null) }
        }

    init {
        loadIndex()
        ioScope.launch {
            pruneOrphans()
            evictIfNeeded(null)
        }
    }

    private fun pruneOrphans() {
        val keys = runCatching { simpleCache.keys }.getOrDefault(emptySet())
        for (key in keys) {
            if (!entries.containsKey(key)) {
                runCatching { simpleCache.removeResource(key) }
            }
        }
    }

    fun keyFromUri(uri: Uri): String = streamCacheKey(uri)

    private fun keyFor(itemId: String): String? {
        val host = serverHost ?: return null
        return "$host/$itemId"
    }

    fun playCount(itemId: String): Int {
        val key = keyFor(itemId) ?: return 0
        return entries[key]?.playCount ?: 0
    }

    fun prefetch(url: String) {
        if (url.isBlank()) return
        ioScope.launch {
            runCatching {
                val uri = Uri.parse(url)
                val key = keyFromUri(uri)
                if (simpleCache.getCachedBytes(key, 0, Long.MAX_VALUE) > 0L) return@launch
                val source = cacheDataSourceFactory.createDataSource()
                source.open(DataSpec(uri))
                val buffer = ByteArray(64 * 1024)
                while (source.read(buffer, 0, buffer.size) != C.RESULT_END_OF_INPUT) {
                    if (!isActive) break
                }
                source.close()
            }
        }
    }

    fun isCached(itemId: String): Boolean {
        val key = keyFor(itemId) ?: return false
        return runCatching {
            simpleCache.getCachedBytes(key, 0, Long.MAX_VALUE) > 0L
        }.getOrDefault(false)
    }

    fun sizeBytes(): Long = runCatching { simpleCache.cacheSpace }.getOrDefault(0L)

    fun count(): Int = entries.size

    fun recordPlay(item: BaseItem, fullyListened: Boolean) {
        if (!settings.streamCacheEnabled) return
        val key = keyFor(item.id) ?: return
        if (!fullyListened) {
            // Keep a previously completed copy; only discard never-finished partial data.
            if (!entries.containsKey(key)) {
                ioScope.launch { runCatching { simpleCache.removeResource(key) } }
            }
            return
        }

        val now = System.currentTimeMillis()
        val entry = entries[key]
        if (entry != null) {
            entry.playCount += 1
            entry.lastPlayedAt = now
        } else {
            entries[key] = StreamCacheEntry(
                key = key,
                item = item,
                playCount = 1,
                lastPlayedAt = now,
                cachedAt = now,
            )
        }
        saveIndex()
        revision.value += 1
        ioScope.launch { evictIfNeeded(exceptKey = key) }
    }

    fun evictIfNeeded(exceptKey: String?) {
        val limitMbValue = settings.cacheLimitMb
        if (limitMbValue <= 0) return
        val limitBytes = limitMbValue.toLong() * 1024L * 1024L
        if (sizeBytes() <= limitBytes) return

        val ordered = entries.values
            .filter { it.key != exceptKey }
            .sortedWith(compareBy<StreamCacheEntry> { it.playCount }.thenBy { it.lastPlayedAt })

        for (entry in ordered) {
            if (sizeBytes() <= limitBytes) break
            val before = sizeBytes()
            runCatching { simpleCache.removeResource(entry.key) }
            entries.remove(entry.key)
            if (sizeBytes() >= before) break
        }
        saveIndex()
        revision.value += 1
    }

    fun clear() {
        entries.clear()
        saveIndex()
        revision.value += 1
        ioScope.launch {
            runCatching {
                simpleCache.keys.toList().forEach { key ->
                    runCatching { simpleCache.removeResource(key) }
                }
            }
        }
    }

    private fun loadIndex() {
        if (!indexFile.exists()) return
        try {
            val array = JSONArray(indexFile.readText())
            for (i in 0 until array.length()) {
                val json = array.getJSONObject(i)
                val key = json.getString("key")
                val cachedBytes = runCatching {
                    simpleCache.getCachedBytes(key, 0, Long.MAX_VALUE)
                }.getOrDefault(0L)
                if (cachedBytes <= 0L) continue
                entries[key] = StreamCacheEntry(
                    key = key,
                    item = itemFromJson(json),
                    playCount = json.optInt("playCount", 1),
                    lastPlayedAt = json.optLong("lastPlayedAt", 0L),
                    cachedAt = json.optLong("cachedAt", 0L),
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun saveIndex() {
        try {
            val array = JSONArray()
            entries.values.forEach { entry ->
                array.put(
                    itemToJson(entry.item)
                        .put("key", entry.key)
                        .put("playCount", entry.playCount)
                        .put("lastPlayedAt", entry.lastPlayedAt)
                        .put("cachedAt", entry.cachedAt),
                )
            }
            indexFile.writeText(array.toString())
        } catch (_: Exception) {
        }
    }

    private fun itemToJson(item: BaseItem): JSONObject = JSONObject()
        .put("id", item.id)
        .put("name", item.name)
        .put("type", item.type)
        .put("album", item.album)
        .put("albumArtist", item.albumArtist)
        .put("albumId", item.albumId)
        .put("runTimeTicks", item.runTimeTicks)
        .put("container", item.container)
        .put("hasImage", item.hasImage)
        .put("artists", JSONArray(item.artists))

    private fun itemFromJson(json: JSONObject): BaseItem {
        val artists = json.optJSONArray("artists")?.let { array ->
            (0 until array.length()).map { array.getString(it) }
        } ?: emptyList()
        return BaseItem(
            id = json.getString("id"),
            name = json.getString("name"),
            type = json.optString("type", "Audio"),
            isFolder = false,
            album = json.optString("album").takeIf { it.isNotBlank() },
            albumArtist = json.optString("albumArtist").takeIf { it.isNotBlank() },
            artists = artists,
            albumId = json.optString("albumId").takeIf { it.isNotBlank() },
            runTimeTicks = json.optLong("runTimeTicks").takeIf { it > 0 },
            container = json.optString("container").takeIf { it.isNotBlank() },
            hasImage = json.optBoolean("hasImage", false),
        )
    }
}