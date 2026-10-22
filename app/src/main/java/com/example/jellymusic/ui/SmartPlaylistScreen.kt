package com.example.jellymusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import com.example.jellymusic.AppState
import com.example.jellymusic.R
import com.example.jellymusic.jellyfin.BaseItem

@Composable
fun SmartPlaylistScreen(state: AppState) {
    var preset by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<BaseItem>?>(null) }
    val presets = listOf(
        R.string.recent_played,
        R.string.most_played,
        R.string.favorite_songs,
        R.string.high_rated,
    )

    LaunchedEffect(preset) {
        items = null
        if (state.isOffline) {
            val downloaded = state.downloads.downloadedItems()
            items = when (preset) {
                2 -> downloaded.filter { state.favorites.isFavorite(it) }
                3 -> downloaded.filter { state.ratings.rating(it.id) >= 4 }
                else -> downloaded
            }
            return@LaunchedEffect
        }
        items = try {
            when (preset) {
                0 -> state.requireApi().getRecentlyPlayed(100)
                1 -> state.requireApi().getMostPlayed(100)
                2 -> state.requireApi().getFavorites()
                else -> {
                    val all = mutableListOf<BaseItem>()
                    var start = 0
                    while (all.size < 1000) {
                        val page = state.requireApi().getAllSongs(start, 200)
                        all.addAll(page)
                        if (page.size < 200) break
                        start += page.size
                    }
                    all.filter { state.ratings.rating(it.id) >= 4 }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.smart_playlists), onBack = { state.back() })
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(presets.size) { index ->
                FilterChip(
                    selected = preset == index,
                    onClick = { preset = index },
                    label = { Text(stringResource(presets[index])) },
                )
            }
        }

        when {
            items == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            items.isNullOrEmpty() -> EmptyHint(stringResource(R.string.search_no_results))

            else -> {
                val list = items.orEmpty()
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Text(
                            stringResource(presets[preset]) + " · " + list.size,
                            style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    itemsIndexed(list) { index, item ->
                        val id = item.id
                        TrackRow(
                            item = item,
                            index = index + 1,
                            artworkUrl = state.artworkUrl(item, 120),
                            onClick = { state.player.play(list, index) },
                            showArtwork = true,
                            isCurrent = state.player.current?.id == id,
                            favorite = state.favorites.isFavorite(item),
                            onToggleFavorite = { state.favorites.toggle(item) },
                            downloaded = state.downloads.downloadedIds[id] == true,
                            downloading = state.downloads.progress[id],
                            downloadError = state.downloads.errors[id] != null,
                            cached = state.streamCache.isCached(id),
                            onDownload = {
                                state.api?.let { api -> state.downloads.download(api, item) }
                            },
                            onLongClick = { state.trackMenu = item },
                        )
                    }
                }
            }
        }
    }
}
