package io.github.inwins.jellymusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.inwins.jellymusic.AppState
import io.github.inwins.jellymusic.R
import io.github.inwins.jellymusic.jellyfin.BaseItem

@Composable
fun ListeningStatsScreen(state: AppState) {
    var mostPlayed by remember { mutableStateOf<List<BaseItem>?>(null) }
    var recent by remember { mutableStateOf<List<BaseItem>>(emptyList()) }
    var libraryCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        if (state.isOffline) return@LaunchedEffect
        mostPlayed = runCatching { state.requireApi().getMostPlayed(200) }
            .getOrDefault(emptyList())
        recent = runCatching { state.requireApi().getRecentlyPlayed(50) }
            .getOrDefault(emptyList())
        libraryCount = runCatching { state.requireApi().getAudioCount() }.getOrDefault(0)
    }

    val played = mostPlayed.orEmpty()
    val topArtists = remember(played) {
        played.groupBy { it.trackArtist ?: "未知" }
            .map { it.key to it.value.sumOf { item -> item.playCount ?: 1 } }
            .sortedByDescending { it.second }
            .take(10)
    }
    val topAlbums = remember(played) {
        played.groupBy { it.album ?: "未知" }
            .map { it.key to it.value.sumOf { item -> item.playCount ?: 1 } }
            .sortedByDescending { it.second }
            .take(10)
    }
    val totalPlays = played.sumOf { it.playCount ?: 1 }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.listening_stats), onBack = { state.back() })
        if (mostPlayed == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                StatRow(stringResource(R.string.library_songs), libraryCount.toString())
                StatRow(stringResource(R.string.total_plays), totalPlays.toString())
                StatRow("本地音乐", state.localMusic.songs.size.toString())
                StatRow("已分析", state.audioFeatures.all().size.toString())
            }
            item { SectionTitle(stringResource(R.string.top_artists)) }
            itemsIndexed(topArtists) { index, entry ->
                CountRow(index + 1, entry.first, entry.second)
            }
            item { SectionTitle(stringResource(R.string.top_albums)) }
            itemsIndexed(topAlbums) { index, entry ->
                CountRow(index + 1, entry.first, entry.second)
            }
            item { SectionTitle(stringResource(R.string.recent_played)) }
            itemsIndexed(recent) { index, item ->
                val id = item.id
                TrackRow(
                    item = item,
                    index = index + 1,
                    artworkUrl = state.artworkUrl(item, 120),
                    onClick = { state.player.play(recent, index) },
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

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun CountRow(rank: Int, name: String, count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            rank.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            count.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
