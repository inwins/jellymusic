package com.example.jellymusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.jellymusic.AppState
import com.example.jellymusic.R
import kotlin.math.roundToInt

@Composable
fun RunningRadioScreen(state: AppState) {
    var target by remember { mutableIntStateOf(140) }
    val tracks = remember(target, state.audioFeatures.features.size) {
        state.audioFeatures.runningRadio(target)
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.running_radio), onBack = { state.back() })
        Text(
            stringResource(R.string.target_bpm),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(100, 120, 140, 160, 180).forEach { bpm ->
                FilterChip(
                    selected = target == bpm,
                    onClick = { target = bpm },
                    label = { Text(bpm.toString()) },
                )
            }
        }
        if (tracks.isEmpty()) {
            EmptyHint(stringResource(R.string.no_audio_features))
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(tracks) { index, item ->
                    val id = item.id
                    val bpm = state.audioFeatures.get(id)?.bpm ?: 0f
                    TrackRow(
                        item = item,
                        index = index + 1,
                        artworkUrl = state.artworkUrl(item, 120),
                        onClick = { state.player.play(tracks, index) },
                        showArtwork = false,
                        isCurrent = state.player.current?.id == id,
                        trackLabel = "${bpm.roundToInt()}",
                        favorite = state.favorites.isFavorite(item),
                        onToggleFavorite = { state.favorites.toggle(item) },
                        onLongClick = { state.trackMenu = item },
                    )
                }
            }
        }
    }
}
