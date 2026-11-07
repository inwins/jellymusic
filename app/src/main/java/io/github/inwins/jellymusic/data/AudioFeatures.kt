package io.github.inwins.jellymusic.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sqrt

data class NoteEvent(
    val startMs: Long,
    val durationMs: Long,
    val midi: Int,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("startMs", startMs)
        .put("durationMs", durationMs)
        .put("midi", midi)

    companion object {
        fun fromJson(json: JSONObject): NoteEvent = NoteEvent(
            startMs = json.optLong("startMs"),
            durationMs = json.optLong("durationMs"),
            midi = json.optInt("midi"),
        )
    }
}

data class ChordSegment(
    val startMs: Long,
    val name: String,
) {
    fun toJson(): JSONObject = JSONObject().put("startMs", startMs).put("name", name)

    companion object {
        fun fromJson(json: JSONObject): ChordSegment = ChordSegment(
            startMs = json.optLong("startMs"),
            name = json.optString("name"),
        )
    }
}

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
    val scaleTonic: Int = 0,
    val scaleName: String = "",
    val scaleConfidence: Float = 0f,
    val notes: List<NoteEvent> = emptyList(),
    val pitchMin: Int = 0,
    val pitchMax: Int = 0,
    val pitchHistogram: List<Float> = emptyList(),
    val chords: List<ChordSegment> = emptyList(),
    val timeSignature: String = "",
    val keyConfidence: Float = 0f,
    val mfcc: List<Float> = emptyList(),
    val melodyConfidence: Float = 0f,
    val highlightStartMs: Long = 0L,
    val introEndMs: Long = 0L,
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
        .put("scaleTonic", scaleTonic)
        .put("scaleName", scaleName)
        .put("scaleConfidence", scaleConfidence.toDouble())
        .put("pitchMin", pitchMin)
        .put("pitchMax", pitchMax)
        .put("pitchHistogram", JSONArray(pitchHistogram))
        .put("notes", JSONArray().apply { notes.forEach { put(it.toJson()) } })
        .put("chords", JSONArray().apply { chords.forEach { put(it.toJson()) } })
        .put("timeSignature", timeSignature)
        .put("keyConfidence", keyConfidence.toDouble())
        .put("mfcc", JSONArray(mfcc))
        .put("melodyConfidence", melodyConfidence.toDouble())
        .put("highlightStartMs", highlightStartMs)
        .put("introEndMs", introEndMs)

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
                scaleTonic = json.optInt("scaleTonic"),
                scaleName = json.optString("scaleName"),
                scaleConfidence = json.optDouble("scaleConfidence").toFloat(),
                notes = json.optJSONArray("notes")?.let { array ->
                    (0 until array.length()).map { NoteEvent.fromJson(array.getJSONObject(it)) }
                } ?: emptyList(),
                pitchMin = json.optInt("pitchMin"),
                pitchMax = json.optInt("pitchMax"),
                pitchHistogram = json.optJSONArray("pitchHistogram")?.let { array ->
                    (0 until array.length()).map { array.optDouble(it).toFloat() }
                } ?: emptyList(),
                chords = json.optJSONArray("chords")?.let { array ->
                    (0 until array.length()).map { ChordSegment.fromJson(array.getJSONObject(it)) }
                } ?: emptyList(),
                timeSignature = json.optString("timeSignature"),
                keyConfidence = json.optDouble("keyConfidence").toFloat(),
                mfcc = json.optJSONArray("mfcc")?.let { array ->
                    (0 until array.length()).map { array.optDouble(it).toFloat() }
                } ?: emptyList(),
                melodyConfidence = json.optDouble("melodyConfidence").toFloat(),
                highlightStartMs = json.optLong("highlightStartMs"),
                introEndMs = json.optLong("introEndMs"),
            )
        }

        val NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

        fun keyName(features: AudioFeatures): String {
            val note = NOTE_NAMES[features.keyIndex.mod(12)]
            return if (features.minor) "$note 小调" else "$note 大调"
        }

        fun vector(features: AudioFeatures): FloatArray {
            val vector = FloatArray(5 + 12 + 13)
            vector[0] = features.bpm / 180f
            vector[1] = features.energy
            vector[2] = features.brightness
            vector[3] = features.instrumentalness
            vector[4] = features.beatStrength
            for (i in 0 until 12) {
                vector[5 + i] = features.chroma.getOrElse(i) { 0f } * 0.5f
            }
            for (i in 0 until 13) {
                vector[17 + i] = (features.mfcc.getOrElse(i) { 0f }) * 0.05f
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
