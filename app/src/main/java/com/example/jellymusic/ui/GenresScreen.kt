package com.example.jellymusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jellymusic.AppState
import com.example.jellymusic.R

@Composable
fun GenresScreen(state: AppState) {
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.genres), onBack = { state.back() })
        if (state.isOffline) {
            EmptyHint(stringResource(R.string.offline_hint))
            return@Column
        }

        Loaded(key = "genres", load = { state.requireApi().getGenres() }) { genres ->
            if (genres.isEmpty()) {
                EmptyHint(stringResource(R.string.search_no_results))
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(genres, key = { it.id }) { genre ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    state.navigate(Screen.GenreSongs(genre.id, genre.name))
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Artwork(state.artworkUrl(genre, 160), 48.dp, 8.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                genre.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
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

@Composable
fun GenreSongsScreen(state: AppState, screen: Screen.GenreSongs) {
    Column(Modifier.fillMaxSize()) {
        TopBar(screen.name, onBack = { state.back() })
        if (state.isOffline) {
            EmptyHint(stringResource(R.string.offline_hint))
            return@Column
        }

        Loaded(
            key = "genre-${screen.id}",
            load = { state.requireApi().getGenreSongs(screen.id) },
        ) { songs ->
            if (songs.isEmpty()) {
                EmptyHint(stringResource(R.string.search_no_results))
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        DetailHeader(
                            state = state,
                            title = screen.name,
                            subtitle = stringResource(R.string.track_count, songs.size),
                            coverItem = songs.firstOrNull(),
                            onPlay = { state.player.play(songs, 0) },
                            onShuffle = { state.player.playShuffled(songs) },
                        )
                    }
                    itemsIndexed(songs) { index, item ->
                        val id = item.id
                        TrackRow(
                            item = item,
                            index = index + 1,
                            artworkUrl = state.artworkUrl(item, 120),
                            onClick = { state.player.play(songs, index) },
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