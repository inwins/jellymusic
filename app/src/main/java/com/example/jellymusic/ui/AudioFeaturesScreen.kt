package com.example.jellymusic.ui

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.example.jellymusic.data.NoteEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jellymusic.AppState
import com.example.jellymusic.R
import com.example.jellymusic.data.AudioFeatures
import com.example.jellymusic.jellyfin.BaseItem
import com.example.jellymusic.util.TonePlayer
import com.example.jellymusic.util.MusicTheory
import kotlin.math.roundToInt

@Composable
fun AudioFeaturesScreen(state: AppState, screen: Screen.AudioFeatures) {
    val item = screen.item
    val features = state.audioFeatures.get(item.id)
    val analyzing = state.audioFeatures.analyzingId.value == item.id
    val error = state.audioFeatures.error.value
    val uri: Uri? = remember(item.id) {
        item.localUri?.let { Uri.parse(it) } ?: state.downloads.uriFor(item.id)
    }
    val tonePlayer = remember { TonePlayer() }
    DisposableEffect(Unit) {
        onDispose { tonePlayer.stop() }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(item.name, onBack = { state.back() })

        when {
            features != null -> {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        FeatureRow(stringResource(R.string.bpm), "${features.bpm.roundToInt()} BPM")
                        FeatureRow(stringResource(R.string.key_label), AudioFeatures.keyName(features))
                        FeatureRow(
                            stringResource(R.string.camelot_label),
                            MusicTheory.camelot(features.keyIndex, features.minor),
                        )
                        val (relKey, relMinor) =
                            MusicTheory.relative(features.keyIndex, features.minor)
                        FeatureRow(
                            stringResource(R.string.relative_key),
                            MusicTheory.keyName(relKey, relMinor),
                        )
                        FeatureRow(
                            stringResource(R.string.diatonic_chords),
                            MusicTheory.diatonicChords(features.keyIndex, features.minor)
                                .joinToString(" · "),
                        )
                        if (features.timeSignature.isNotBlank()) {
                            FeatureRow(
                                stringResource(R.string.time_signature),
                                features.timeSignature,
                            )
                        }
                        FeatureRow(
                            stringResource(R.string.key_confidence),
                            "${(features.keyConfidence * 100).roundToInt()}%",
                        )
                        if (features.chords.isNotEmpty()) {
                            FeatureRow(
                                stringResource(R.string.chord_progression),
                                features.chords.map { it.name }.take(16).joinToString(" · "),
                            )
                        }
                        if (features.scaleName.isNotBlank()) {
                            FeatureRow(
                                stringResource(R.string.scale_label),
                                MusicTheory.noteName(features.scaleTonic) + " " +
                                    features.scaleName,
                            )
                            FeatureRow(
                                stringResource(R.string.scale_notes),
                                MusicTheory.SCALES
                                    .firstOrNull { it.name == features.scaleName }
                                    ?.intervals
                                    ?.joinToString(" ") {
                                        MusicTheory.noteName(features.scaleTonic + it)
                                    }
                                    .orEmpty(),
                            )
                            FeatureRow(
                                stringResource(R.string.scale_confidence),
                                "${(features.scaleConfidence * 100).roundToInt()}%",
                            )
                            TextButton(onClick = {
                                val base = 60 + (((features.scaleTonic - 60) % 12) + 12) % 12
                                val intervals = MusicTheory.SCALES
                                    .firstOrNull { it.name == features.scaleName }
                                    ?.intervals
                                    ?: intArrayOf(0, 2, 4, 5, 7, 9, 11)
                                val ascending = intervals.map { base + it }
                                tonePlayer.play(ascending + ascending.reversed().drop(1))
                            }) {
                                Text(stringResource(R.string.preview_scale))
                            }
                        }
                        FeatureRow(
                            stringResource(R.string.energy_label),
                            "${(features.energy * 100).roundToInt()}%",
                        )
                        FeatureRow(
                            stringResource(R.string.loudness_label),
                            "${features.loudnessDb.roundToInt()} dB",
                        )
                        FeatureRow(
                            stringResource(R.string.brightness_label),
                            "${(features.brightness * 100).roundToInt()}%",
                        )
                        FeatureRow(
                            stringResource(R.string.vocal_label),
                            "${((1f - features.instrumentalness) * 100).roundToInt()}%",
                        )
                        FeatureRow(
                            stringResource(R.string.accompaniment_label),
                            "${(features.instrumentalness * 100).roundToInt()}%",
                        )
                        FeatureRow(
                            stringResource(R.string.beat_label),
                            "${(features.beatStrength * 100).roundToInt()}%",
                        )
                        Text(
                            stringResource(R.string.estimate_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        ChromaBars(
                            chroma = features.chroma,
                            keyIndex = features.keyIndex,
                            minor = features.minor,
                        )
                        if (uri != null) {
                            TextButton(onClick = { state.audioFeatures.analyze(item, uri) }) {
                                Text(stringResource(R.string.reanalyze))
                            }
                        }
                    }

                    item { SectionTitle(stringResource(R.string.melody_label)) }
                    if (features.notes.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.reanalyze_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    } else {
                        item {
                            FeatureRow(
                                stringResource(R.string.pitch_range),
                                MusicTheory.midiName(features.pitchMin) + " – " +
                                    MusicTheory.midiName(features.pitchMax),
                            )
                            FeatureRow(
                                stringResource(R.string.note_count),
                                features.notes.size.toString(),
                            )
                            FeatureRow(
                                stringResource(R.string.melody_confidence),
                                "${(features.melodyConfidence * 100).roundToInt()}%",
                            )
                            PianoRoll(
                                notes = features.notes,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .height(120.dp),
                            )
                            TextButton(onClick = {
                                tonePlayer.play(features.notes.take(32).map { it.midi })
                            }) {
                                Text(stringResource(R.string.preview_melody))
                            }
                            Text(
                                stringResource(R.string.jianpu_label) + ": " +
                                    features.notes.take(48).joinToString(" ") {
                                        MusicTheory.toJianpu(it.midi, features.scaleTonic)
                                    },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }

                    val harmonic = state.audioFeatures.harmonicallySimilar(item)
                        .filter { state.localMusic.isAvailable(it.first) }
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SectionTitle(stringResource(R.string.harmonic_match))
                            Spacer(Modifier.weight(1f))
                            if (harmonic.isNotEmpty()) {
                                TextButton(onClick = {
                                    val order = state.audioFeatures.harmonicOrder(item)
                                    if (order.isNotEmpty()) state.player.play(order, 0)
                                }) {
                                    Text(stringResource(R.string.generate_harmonic_mix))
                                }
                            }
                        }
                    }
                    if (harmonic.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.no_audio_features),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    } else {
                        itemsIndexed(harmonic) { index, entry ->
                            HarmonicRow(
                                state = state,
                                candidate = entry.first,
                                score = entry.second,
                                list = harmonic.map { it.first },
                                index = index,
                            )
                        }
                    }

                    val similar = state.audioFeatures.similar(item)
                        .filter { state.localMusic.isAvailable(it) }
                    item { SectionTitle(stringResource(R.string.similar_by_sound)) }
                    if (similar.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.no_audio_features),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    } else {
                        itemsIndexed(similar) { index, candidate ->
                            val isLocal = candidate.localUri != null
                            TrackRow(
                                item = candidate,
                                index = index + 1,
                                artworkUrl = state.artworkUrl(candidate, 120),
                                onClick = { state.player.play(similar, index) },
                                showArtwork = true,
                                isCurrent = state.player.current?.id == candidate.id,
                                favorite = !isLocal && state.favorites.isFavorite(candidate),
                                onToggleFavorite = if (isLocal) {
                                    null
                                } else {
                                    ({ state.favorites.toggle(candidate) })
                                },
                                downloaded = !isLocal &&
                                    state.downloads.downloadedIds[candidate.id] == true,
                                downloading = if (isLocal) {
                                    null
                                } else {
                                    state.downloads.progress[candidate.id]
                                },
                                downloadError = !isLocal &&
                                    state.downloads.errors[candidate.id] != null,
                                cached = !isLocal && state.streamCache.isCached(candidate.id),
                                onDownload = if (isLocal) {
                                    null
                                } else {
                                    ({
                                        state.api?.let { api ->
                                            state.downloads.download(api, candidate)
                                        }
                                    })
                                },
                                onLongClick = { state.trackMenu = candidate },
                            )
                        }
                    }
                }
            }

            analyzing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.size(12.dp))
                    Text(
                        stringResource(R.string.analyzing),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (uri == null) {
                    Text(
                        stringResource(R.string.need_download_first),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Button(onClick = { state.audioFeatures.analyze(item, uri) }) {
                        Text(stringResource(R.string.analyze))
                    }
                }
                if (error != null) {
                    Spacer(Modifier.size(12.dp))
                    Text(
                        stringResource(R.string.analysis_failed),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun HarmonicRow(
    state: AppState,
    candidate: BaseItem,
    score: Float,
    list: List<BaseItem>,
    index: Int,
) {
    val isLocal = candidate.localUri != null
    TrackRow(
        item = candidate,
        index = index + 1,
        artworkUrl = state.artworkUrl(candidate, 120),
        onClick = { state.player.play(list, index) },
        showArtwork = true,
        trackLabel = "${(score * 100).roundToInt()}%",
        isCurrent = state.player.current?.id == candidate.id,
        favorite = !isLocal && state.favorites.isFavorite(candidate),
        onToggleFavorite = if (isLocal) null else ({ state.favorites.toggle(candidate) }),
        downloaded = !isLocal && state.downloads.downloadedIds[candidate.id] == true,
        downloading = if (isLocal) null else state.downloads.progress[candidate.id],
        downloadError = !isLocal && state.downloads.errors[candidate.id] != null,
        cached = !isLocal && state.streamCache.isCached(candidate.id),
        onDownload = if (isLocal) {
            null
        } else {
            ({ state.api?.let { api -> state.downloads.download(api, candidate) } })
        },
        onLongClick = { state.trackMenu = candidate },
    )
}

@Composable
private fun ChromaBars(chroma: List<Float>, keyIndex: Int, minor: Boolean) {
    val max = chroma.maxOrNull()?.takeIf { it > 0f } ?: 1f
    val tonic = ((keyIndex % 12) + 12) % 12
    val diatonic = if (minor) {
        setOf(0, 2, 3, 5, 7, 8, 10)
    } else {
        setOf(0, 2, 4, 5, 7, 9, 11)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(84.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        for (pc in 0 until 12) {
            val value = (chroma.getOrElse(pc) { 0f } / max).coerceIn(0f, 1f)
            val color = when {
                pc == tonic -> MaterialTheme.colorScheme.primary
                ((pc - tonic + 12) % 12) in diatonic ->
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
            }
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((56f * value).dp + 2.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    MusicTheory.noteName(pc),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = if (pc == tonic) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.6f),
        )
    }
}

@Composable
private fun PianoRoll(notes: List<NoteEvent>, modifier: Modifier = Modifier) {
    if (notes.isEmpty()) return
    val minMidi = notes.minOf { it.midi }
    val maxMidi = notes.maxOf { it.midi }
    val span = (maxMidi - minMidi).coerceAtLeast(1)
    val total = notes.maxOf { it.startMs + it.durationMs }.coerceAtLeast(1L)
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val barHeight = 7f
        val usable = (size.height - barHeight).coerceAtLeast(1f)
        notes.forEach { note ->
            val x = (note.startMs.toFloat() / total) * size.width
            val width = (note.durationMs.toFloat() / total) * size.width
            val y = ((maxMidi - note.midi).toFloat() / span) * usable
            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(width.coerceAtLeast(3f), barHeight),
                cornerRadius = CornerRadius(2f, 2f),
            )
        }
    }
}
