package com.example.jellymusic.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import java.util.Calendar

@Composable
fun DailyScreen(state: AppState) {
    val daySeed = remember {
        val calendar = Calendar.getInstance()
        calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
    }
    val favoriteIds = remember(state.favorites.overrides.size, state.favorites.localItems.size) {
        state.favorites.overrides.keys.toSet() + state.favorites.localItems.keys
    }
    val tracks = remember(daySeed, state.audioFeatures.features.size, state.dislikes.ids.size) {
        state.audioFeatures.dailyMix(daySeed, favoriteIds, state.dislikes.ids.toSet())
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.daily_mix), onBack = { state.back() }) {
            if (tracks.isNotEmpty()) {
                TextButton(onClick = { state.player.playShuffled(tracks) }) {
                    Text(stringResource(R.string.heart_mode))
                }
            }
        }
        if (tracks.isEmpty()) {
            EmptyHint(stringResource(R.string.no_audio_features))
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.daily_mix) + " · " + tracks.size,
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            itemsIndexed(tracks) { index, item ->
                val id = item.id
                TrackRow(
                    item = item,
                    index = index + 1,
                    artworkUrl = state.artworkUrl(item, 120),
                    onClick = { state.player.play(tracks, index) },
                    showArtwork = true,
                    isCurrent = state.player.current?.id == id,
                    favorite = state.favorites.isFavorite(item),
                    onToggleFavorite = { state.favorites.toggle(item) },
                    onLongClick = { state.trackMenu = item },
                )
            }
        }
    }
}
