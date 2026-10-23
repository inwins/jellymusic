package com.example.jellymusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jellymusic.AppState
import com.example.jellymusic.R
import com.example.jellymusic.jellyfin.BaseItem

private const val SEARCH_PAGE_SIZE = 200
private const val SEARCH_MAX_ITEMS = 2000

@Composable
fun SearchScreen(state: AppState) {
    var query by rememberSaveable { mutableStateOf(state.searchRequest.orEmpty()) }
    var kind by rememberSaveable { mutableIntStateOf(0) }
    var songs by remember { mutableStateOf<List<BaseItem>>(emptyList()) }
    var albums by remember { mutableStateOf<List<BaseItem>>(emptyList()) }
    var artists by remember { mutableStateOf<List<BaseItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.searchRequest) {
        state.searchRequest?.let {
            query = it
            state.searchRequest = null
        }
    }

    val kinds = listOf(
        "Audio" to stringResource(R.string.search_songs),
        "MusicAlbum" to stringResource(R.string.search_albums),
        "MusicArtist" to stringResource(R.string.search_artists),
    )

    LaunchedEffect(kind, retry) {
        val alreadyLoaded = when (kind) {
            0 -> songs.isNotEmpty()
            1 -> albums.isNotEmpty()
            else -> artists.isNotEmpty()
        }
        if (alreadyLoaded && retry == 0) return@LaunchedEffect
        loading = true
        error = null
        try {
            if (state.isOffline) {
                when (kind) {
                    0 -> songs = state.offlineSongs()
                    1 -> albums = state.offlineAlbums()
                    else -> artists = state.offlineArtists()
                }
                return@LaunchedEffect
            }
            when (kind) {
                0 -> {
                    val all = mutableListOf<BaseItem>()
                    var start = 0
                    while (all.size < SEARCH_MAX_ITEMS) {
                        val page = state.requireApi().getAllSongs(start, SEARCH_PAGE_SIZE)
                        all.addAll(page)
                        if (page.size < SEARCH_PAGE_SIZE) break
                        start += page.size
                    }
                    songs = (all + state.localMusic.songs).distinctBy { it.id }
                }

                1 -> albums = state.requireApi().getAlbums(0, 500)
                else -> artists = state.requireApi().getArtists(0, 500)
            }
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    val source = when (kind) {
        0 -> songs
        1 -> albums
        else -> artists
    }
    val needle = query.trim()
    val filtered = remember(source, needle, kind) {
        if (needle.isEmpty()) {
            source
        } else {
            source.filter { item ->
                when (kind) {
                    0 -> item.name.contains(needle, true) ||
                        item.trackArtist?.contains(needle, true) == true ||
                        item.album?.contains(needle, true) == true ||
                        item.albumArtist?.contains(needle, true) == true

                    1 -> item.name.contains(needle, true) ||
                        item.albumArtist?.contains(needle, true) == true

                    else -> item.name.contains(needle, true)
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.search), onBack = { state.back() })

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = stringResource(R.string.clear_search),
                        )
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { if (query.isNotBlank()) state.searchHistory.add(query) },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            kinds.forEachIndexed { index, (_, label) ->
                FilterChip(
                    selected = kind == index,
                    onClick = { kind = index },
                    label = { Text(label) },
                )
            }
        }

        when {
            loading && source.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            error != null && source.isEmpty() -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(error ?: "", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { retry++ }) {
                    Text(stringResource(R.string.retry))
                }
            }

            else -> {
                val history = state.searchHistory.history
                LazyColumn(Modifier.fillMaxSize()) {
                    if (needle.isBlank() && history.isNotEmpty()) {
                        item {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 8.dp, top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.search_history),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { state.searchHistory.clear() }) {
                                    Text(stringResource(R.string.clear_history))
                                }
                            }
                            LazyRow(
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 16.dp,
                                ),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(history) { value ->
                                    SuggestionChip(
                                        onClick = { query = value },
                                        label = { Text(value) },
                                    )
                                }
                            }
                        }
                    }

                    if (filtered.isEmpty()) {
                        item {
                            EmptyHint(
                                stringResource(
                                    if (needle.isBlank()) {
                                        R.string.search_no_results
                                    } else {
                                        R.string.search_no_results
                                    },
                                ),
                            )
                        }
                    } else if (kind == 0) {
                        itemsIndexed(filtered) { index, item ->
                            val id = item.id
                            val isLocal = item.localUri != null
                            TrackRow(
                                item = item,
                                index = index + 1,
                                artworkUrl = state.artworkUrl(item, 120),
                                onClick = {
                                    if (needle.isNotBlank()) state.searchHistory.add(needle)
                                    state.player.play(filtered, index)
                                },
                                showArtwork = true,
                                isCurrent = state.player.current?.id == id,
                                favorite = !isLocal && state.favorites.isFavorite(item),
                                onToggleFavorite = if (isLocal) {
                                    null
                                } else {
                                    ({ state.favorites.toggle(item) })
                                },
                                downloaded = !isLocal && state.downloads.downloadedIds[id] == true,
                                downloading = if (isLocal) null else state.downloads.progress[id],
                                downloadError = !isLocal &&
                                    state.downloads.errors[id] != null,
                                cached = !isLocal && state.streamCache.isCached(id),
                                onDownload = if (isLocal) {
                                    null
                                } else {
                                    ({ state.api?.let { api -> state.downloads.download(api, item) } })
                                },
                                onLongClick = { state.trackMenu = item },
                            )
                        }
                    } else {
                        itemsIndexed(filtered) { _, item ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (needle.isNotBlank()) state.searchHistory.add(needle)
                                        if (kind == 1) {
                                            if (state.isOffline) {
                                                state.navigate(Screen.OfflineAlbum(item.id, item.name))
                                            } else {
                                                state.navigate(Screen.Album(item.id, item.name))
                                            }
                                        } else {
                                            if (state.isOffline) {
                                                state.navigate(Screen.OfflineArtist(item.name))
                                            } else {
                                                state.navigate(Screen.Artist(item.id, item.name))
                                            }
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Artwork(state.artworkUrl(item, 160), 48.dp, 8.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        if (kind == 1) {
                                            item.albumArtist.orEmpty()
                                        } else {
                                            stringResource(R.string.artist)
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
