package com.example.jellymusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.jellymusic.AppState
import com.example.jellymusic.R
import com.example.jellymusic.jellyfin.BaseItem
import kotlinx.coroutines.launch

@Composable
fun AllSongsScreen(state: AppState) {
    if (state.isOffline) {
        Column(Modifier.fillMaxSize()) {
            TopBar(stringResource(R.string.all_songs), onBack = { state.back() })
            EmptyHint(stringResource(R.string.offline_hint))
        }
        return
    }

    val items = remember { mutableStateListOf<BaseItem>() }
    var loading by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    var startIndex by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    suspend fun loadNext() {
        if (loading || endReached) return
        loading = true
        try {
            val page = state.requireApi().getAllSongs(startIndex, PAGE_SIZE)
            if (page.isEmpty()) {
                endReached = true
            } else {
                items.addAll(page)
                startIndex += page.size
                if (page.size < PAGE_SIZE) endReached = true
            }
            error = null
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        loadNext()
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= items.size - 5
        }
    }
    LaunchedEffect(shouldLoadMore, items.size) {
        if (shouldLoadMore && items.isNotEmpty()) {
            loadNext()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.all_songs), onBack = { state.back() })

        when {
            items.isEmpty() && loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            items.isEmpty() && error != null -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(error ?: "", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = {
                    error = null
                    scope.launch { loadNext() }
                }) {
                    Text(stringResource(R.string.retry))
                }
            }

            items.isEmpty() -> EmptyHint(stringResource(R.string.search_no_results))

            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                item {
                    DetailHeader(
                        state = state,
                        title = stringResource(R.string.all_songs),
                        subtitle = stringResource(R.string.track_count, items.size),
                        coverItem = items.firstOrNull(),
                        onPlay = { state.player.play(items.toList(), 0) },
                        onShuffle = { state.player.playShuffled(items.toList()) },
                    )
                }
                itemsIndexed(items) { index, item ->
                    val id = item.id
                    TrackRow(
                        item = item,
                        index = index + 1,
                        artworkUrl = state.artworkUrl(item, 120),
                        onClick = { state.player.play(items, index) },
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
                if (loading) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
                if (error != null) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            TextButton(onClick = {
                                error = null
                                scope.launch { loadNext() }
                            }) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val PAGE_SIZE = 100