package com.example.jellymusic.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.jellymusic.AppState
import com.example.jellymusic.R
import com.example.jellymusic.data.AudioFeatures
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

    Column(Modifier.fillMaxSize()) {
        TopBar(item.name, onBack = { state.back() })

        when {
            features != null -> {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        FeatureRow(stringResource(R.string.bpm), "${features.bpm.roundToInt()} BPM")
                        FeatureRow(stringResource(R.string.key_label), AudioFeatures.keyName(features))
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
                        if (uri != null) {
                            TextButton(onClick = { state.audioFeatures.analyze(item, uri) }) {
                                Text(stringResource(R.string.reanalyze))
                            }
                        }
                    }

                    val similar = state.audioFeatures.similar(item)
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
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
