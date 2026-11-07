package io.github.inwins.jellymusic.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.inwins.jellymusic.AppState
import io.github.inwins.jellymusic.R
import io.github.inwins.jellymusic.jellyfin.BaseItem
import kotlinx.coroutines.delay

@Composable
fun FavoritesScreen(state: AppState) {
    var items by remember { mutableStateOf<List<BaseItem>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }
    var didScroll by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(retry) {
        items = null
        error = null
        if (state.isOffline) {
            items = state.downloads.downloadedItems()
                .filter { state.favorites.isFavorite(it) }
            return@LaunchedEffect
        }
        delay(50)
        items = try {
            val server = state.requireApi().getFavorites()
            state.favorites.mergeFavorites(server)
        } catch (e: Exception) {
            error = e.message
            state.favorites.mergeFavorites(emptyList())
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.favorite_songs), onBack = { state.back() })

        when {
            items == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            items.isNullOrEmpty() -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.search_no_results),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> {
                val list = items.orEmpty()
                RegisterSelectableItems(list)
                LaunchedEffect(list.size, state.player.current?.id) {
                    if (!didScroll && list.isNotEmpty()) {
                        val index = list.indexOfFirst { it.id == state.player.current?.id }
                        if (index >= 0) {
                            listState.animateScrollToItem(index + 1)
                            didScroll = true
                        }
                    }
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    item {
                        SectionTitle(
                            stringResource(R.string.favorite_songs) + " " + list.size,
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
                    if (error != null) {
                        item {
                            TextButton(onClick = { retry++ }) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
            }
        }
    }
}
