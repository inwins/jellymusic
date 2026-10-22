package com.example.jellymusic.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sqrt

data class AudioFeatures(
    val bpm: Float,
    val keyIndex: Int,
    val minor: Boolean,
    val energy: Float,
    val loudnessDb: Float,
    val brightness: Float,
    val instrumentalness: Float,
    val beatStrength: Float,
    val chroma: List<Float>,
    val analyzedAt: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("bpm", bpm.toDouble())
        .put("keyIndex", keyIndex)
        .put("minor", minor)
        .put("energy", energy.toDouble())
        .put("loudnessDb", loudnessDb.toDouble())
        .put("brightness", brightness.toDouble())
        .put("instrumentalness", instrumentalness.toDouble())
        .put("beatStrength", beatStrength.toDouble())
        .put("chroma", JSONArray(chroma))
        .put("analyzedAt", analyzedAt)

    companion object {
        fun fromJson(json: JSONObject): AudioFeatures {
            val chromaArray = json.optJSONArray("chroma")
            val chroma = if (chromaArray != null) {
                (0 until chromaArray.length()).map { chromaArray.optDouble(it).toFloat() }
            } else {
                emptyList()
            }
            return AudioFeatures(
                bpm = json.optDouble("bpm").toFloat(),
                keyIndex = json.optInt("keyIndex"),
                minor = json.optBoolean("minor"),
                energy = json.optDouble("energy").toFloat(),
                loudnessDb = json.optDouble("loudnessDb").toFloat(),
                brightness = json.optDouble("brightness").toFloat(),
                instrumentalness = json.optDouble("instrumentalness").toFloat(),
                beatStrength = json.optDouble("beatStrength").toFloat(),
                chroma = chroma,
                analyzedAt = json.optLong("analyzedAt"),
            )
        }

        val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

        fun keyName(features: AudioFeatures): String {
            val note = NOTE_NAMES[features.keyIndex.mod(12)]
            return if (features.minor) "$note 小调" else "$note 大调"
        }

        fun vector(features: AudioFeatures): FloatArray {
            val vector = FloatArray(5 + 12)
            vector[0] = features.bpm / 180f
            vector[1] = features.energy
            vector[2] = features.brightness
            vector[3] = features.instrumentalness
            vector[4] = features.beatStrength
            for (i in 0 until 12) {
                vector[5 + i] = features.chroma.getOrElse(i) { 0f } * 0.5f
            }
            return vector
        }

        fun cosine(a: FloatArray, b: FloatArray): Float {
            var dot = 0f
            var na = 0f
            var nb = 0f
            for (i in a.indices) {
                dot += a[i] * b[i]
                na += a[i] * a[i]
                nb += b[i] * b[i]
            }
            if (na <= 0f || nb <= 0f) return 0f
            return (dot / (sqrt(na) * sqrt(nb))).coerceIn(0f, 1f)
        }
    }
}
