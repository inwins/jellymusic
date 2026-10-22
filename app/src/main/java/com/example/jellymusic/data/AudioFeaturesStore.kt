package com.example.jellymusic.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import com.example.jellymusic.jellyfin.BaseItem
import com.example.jellymusic.util.MusicTheory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

class AudioFeaturesStore(context: Context) {
    private val analyzer = AudioAnalyzer(context)
    private val prefs = context.getSharedPreferences("jellymusic_audio_features", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val features = mutableStateMapOf<String, AudioFeatures>()
    private val items = mutableStateMapOf<String, BaseItem>()
    val analyzingId = mutableStateOf<String?>(null)
    val error = mutableStateOf<String?>(null)

    init {
        load()
    }

    fun get(itemId: String): AudioFeatures? = features[itemId]

    suspend fun analyzeAndStore(item: BaseItem, uri: Uri): Boolean {
        val result = runCatching { analyzer.analyze(uri) }.getOrNull() ?: return false
        if (result.bpm <= 0f) return false
        features[item.id] = result
        items[item.id] = item
        save()
        return true
    }

    fun all(): List<Pair<BaseItem, AudioFeatures>> =
        items.mapNotNull { (id, item) -> features[id]?.let { item to it } }

    fun reload() {
        features.clear()
        items.clear()
        load()
    }

    fun item(itemId: String): BaseItem? = items[itemId]

    fun analyzedItems(): List<BaseItem> = items.values.toList()

    fun analyze(item: BaseItem, uri: Uri) {
        if (analyzingId.value != null) return
        analyzingId.value = item.id
        error.value = null
        scope.launch {
            val result = runCatching { analyzer.analyze(uri) }
            result.onSuccess { features ->
                if (features != null && features.bpm > 0f) {
                    this@AudioFeaturesStore.features[item.id] = features
                    items[item.id] = item
                    save()
                } else {
                    error.value = "analysis_failed"
                }
            }.onFailure { e ->
                error.value = e.message
            }
            analyzingId.value = null
        }
    }

    fun remove(itemId: String) {
        features.remove(itemId)
        items.remove(itemId)
        save()
    }

    fun harmonicallySimilar(item: BaseItem, limit: Int = 20): List<Pair<BaseItem, Float>> {
        val seed = features[item.id] ?: return emptyList()
        return features.entries
            .asSequence()
            .filter { it.key != item.id }
            .mapNotNull { entry ->
                val other = items[entry.key] ?: return@mapNotNull null
                val score = MusicTheory.harmonicScore(
                    seed.keyIndex, seed.minor, seed.bpm,
                    entry.value.keyIndex, entry.value.minor, entry.value.bpm,
                )
                other to score
            }
            .sortedByDescending { it.second }
            .take(limit)
            .toList()
    }

    /** Greedy harmonic-mixing order: each next track is the most compatible with the previous. */
    fun harmonicOrder(item: BaseItem, limit: Int = 30): List<BaseItem> {
        val seed = features[item.id] ?: return listOf(item)
        val pool = features.entries
            .asSequence()
            .filter { it.key != item.id }
            .mapNotNull { entry -> items[entry.key]?.let { it to entry.value } }
            .toMutableList()
        val ordered = mutableListOf(item)
        var current = seed
        while (pool.isNotEmpty() && ordered.size < limit) {
            val best = pool.maxByOrNull {
                MusicTheory.harmonicScore(
                    current.keyIndex, current.minor, current.bpm,
                    it.second.keyIndex, it.second.minor, it.second.bpm,
                )
            } ?: break
            pool.remove(best)
            ordered.add(best.first)
            current = best.second
        }
        return ordered
    }

    fun similar(item: BaseItem, limit: Int = 20): List<BaseItem> {
        val seed = features[item.id] ?: return emptyList()
        val seedVector = AudioFeatures.vector(seed)
        return features.entries
            .asSequence()
            .filter { it.key != item.id }
            .mapNotNull { entry ->
                val other = items[entry.key] ?: return@mapNotNull null
                val score = AudioFeatures.cosine(seedVector, AudioFeatures.vector(entry.value))
                other to score
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
            .toList()
    }

    private fun save() {
        runCatching {
            val root = JSONObject()
            features.forEach { (id, value) ->
                val item = items[id] ?: return@forEach
                root.put(
                    id,
                    JSONObject()
                        .put("item", item.toJson())
                        .put("features", value.toJson()),
                )
            }
            prefs.edit().putString(KEY, root.toString()).apply()
        }
    }

    private fun load() {
        val raw = prefs.getString(KEY, null) ?: return
        runCatching {
            val root = JSONObject(raw)
            val keys = root.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val entry = root.optJSONObject(id) ?: continue
                val itemJson = entry.optJSONObject("item") ?: continue
                val featureJson = entry.optJSONObject("features") ?: continue
                val item = baseItemFromJson(itemJson)
                items[id] = item
                features[id] = AudioFeatures.fromJson(featureJson)
            }
        }
    }

    private companion object {
        const val KEY = "features"
    }
}
