package com.example.jellymusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jellymusic.AppState
import com.example.jellymusic.R
import com.example.jellymusic.jellyfin.BaseItem
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(state: AppState) {
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableIntStateOf(0) }
    var results by remember { mutableStateOf<List<BaseItem>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    val kinds = listOf(
        "Audio" to stringResource(R.string.search_songs),
        "MusicAlbum" to stringResource(R.string.search_albums),
        "MusicArtist" to stringResource(R.string.search_artists),
    )

    LaunchedEffect(query, kind, retry) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            results = null
            loading = false
            error = null
            return@LaunchedEffect
        }
        loading = true
        error = null
        delay(350)
        try {
            val api = state.requireApi()
            results = when (kind) {
                0 -> api.searchSongs(trimmed)
                1 -> api.search(trimmed, "MusicAlbum")
                else -> api.searchArtists(trimmed)
            }
        } catch (e: Exception) {
            results = emptyList()
            error = e.message
        } finally {
            loading = false
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
                onSearch = { state.searchHistory.add(query) },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .focusRequester(focusRequester),
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
            query.isBlank() -> {
                val history = state.searchHistory.history
                if (history.isEmpty()) {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.search_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
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
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(history) { value ->
                                SuggestionChip(
                                    onClick = { query = value },
                                    label = { Text(value) },
                                )
                            }
                        }
                    }
                }
            }

            loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            error != null -> Column(
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

            results.isNullOrEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.search_no_results),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> {
                val items = results ?: emptyList()
                LazyColumn(Modifier.fillMaxSize()) {
                    if (kind == 0) {
                        itemsIndexed(items) { index, item ->
                            val id = item.id
                            TrackRow(
                                item = item,
                                index = index + 1,
                                artworkUrl = state.artworkUrl(item, 120),
                                onClick = {
                                    state.searchHistory.add(query)
                                    state.player.play(items, index)
                                },
                                showArtwork = true,
                                isCurrent = state.player.current?.id == id,
                                favorite = state.favorites.isFavorite(item),
                                onToggleFavorite = { state.favorites.toggle(item) },
                                downloaded = state.downloads.downloadedIds[id] == true,
                                downloading = state.downloads.progress[id],
                                downloadError = state.downloads.errors[id] != null,
                                onDownload = {
                                    state.api?.let { api -> state.downloads.download(api, item) }
                                },
                                onLongClick = { state.trackMenu = item },
                            )
                        }
                    } else {
                        itemsIndexed(items) { _, item ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        state.searchHistory.add(query)
                                        if (kind == 1) {
                                            state.navigate(Screen.Album(item.id, item.name))
                                        } else {
                                            state.navigate(Screen.Artist(item.id, item.name))
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
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}