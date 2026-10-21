package com.example.jellymusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jellymusic.AppState
import com.example.jellymusic.R
import com.example.jellymusic.jellyfin.BaseItem

@Composable
fun AlbumScreen(state: AppState, screen: Screen.Album) {
    Column(Modifier.fillMaxSize()) {
        TopBar(screen.name, onBack = { state.back() })
        Loaded(key = "album-${screen.id}", load = {
            state.requireApi().getAlbumTracks(screen.id)
        }) { tracks ->
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    DetailHeader(
                        state = state,
                        title = screen.name,
                        subtitle = tracks.firstOrNull()?.albumArtist,
                        coverItem = tracks.firstOrNull(),
                        onPlay = { state.player.play(tracks, 0) },
                        onDownloadAll = {
                            state.api?.let { api ->
                                tracks.forEach { state.downloads.download(api, it) }
                            }
                        },
                    )
                }
                itemsIndexed(tracks) { index, track ->
                    val id = track.id
                    TrackRow(
                        item = track,
                        index = index + 1,
                        artworkUrl = state.artworkUrl(track, 120),
                        onClick = { state.player.play(tracks, index) },
                        isCurrent = state.player.current?.id == id,
                        favorite = state.favorites.isFavorite(track),
                        onToggleFavorite = { state.favorites.toggle(track) },
                        downloaded = state.downloads.downloadedIds[id] == true,
                        downloading = state.downloads.progress[id],
                        downloadError = state.downloads.errors[id] != null,
                        onDownload = {
                            state.api?.let { api -> state.downloads.download(api, track) }
                        },
                        onLongClick = { state.trackMenu = track },
                    )
                }
            }
        }
    }
}

@Composable
fun ArtistScreen(state: AppState, screen: Screen.Artist) {
    Column(Modifier.fillMaxSize()) {
        TopBar(screen.name, onBack = { state.back() })
        Loaded(key = "artist-${screen.id}", load = {
            state.requireApi().getArtistAlbums(screen.id)
        }) { albums ->
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(albums, key = { it.id }) { album ->
                    CardItem(
                        item = album,
                        artworkUrl = state.artworkUrl(album, 400),
                        subtitle = album.albumArtist,
                        onClick = { state.navigate(Screen.Album(album.id, album.name)) },
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistScreen(state: AppState, screen: Screen.Playlist) {
    Column(Modifier.fillMaxSize()) {
        TopBar(screen.name, onBack = { state.back() })
        Loaded(key = "playlist-${screen.id}", load = {
            state.requireApi().getPlaylistTracks(screen.id)
        }) { tracks ->
            if (tracks.isEmpty()) {
                EmptyHint(stringResource(R.string.playlist_empty))
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        DetailHeader(
                            state = state,
                            title = screen.name,
                            subtitle = stringResource(R.string.track_count, tracks.size),
                            coverItem = tracks.firstOrNull(),
                            onPlay = { state.player.play(tracks, 0) },
                            onDownloadAll = {
                                state.api?.let { api ->
                                    tracks.forEach { state.downloads.download(api, it) }
                                }
                            },
                        )
                    }
                    itemsIndexed(tracks) { index, track ->
                        val id = track.id
                        TrackRow(
                            item = track,
                            index = index + 1,
                            artworkUrl = state.artworkUrl(track, 120),
                            onClick = { state.player.play(tracks, index) },
                            isCurrent = state.player.current?.id == id,
                            favorite = state.favorites.isFavorite(track),
                            onToggleFavorite = { state.favorites.toggle(track) },
                            downloaded = state.downloads.downloadedIds[id] == true,
                            downloading = state.downloads.progress[id],
                        downloadError = state.downloads.errors[id] != null,
                            onDownload = {
                                state.api?.let { api -> state.downloads.download(api, track) }
                            },
                            onLongClick = { state.trackMenu = track },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LocalPlaylistScreen(state: AppState, screen: Screen.LocalPlaylist) {
    val playlist = state.localPlaylists.get(screen.id)
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf("") }

    if (playlist == null) {
        Column(Modifier.fillMaxSize()) {
            TopBar(stringResource(R.string.tab_playlists), onBack = { state.back() })
            EmptyHint(stringResource(R.string.no_playlists))
        }
        return
    }

    val items = playlist.items.toList()
    Column(Modifier.fillMaxSize()) {
        TopBar(playlist.name, onBack = { state.back() })
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = {
                renameValue = playlist.name
                showRename = true
            }) {
                Text(stringResource(R.string.rename))
            }
            TextButton(onClick = { showDelete = true }) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        }

        if (items.isEmpty()) {
            EmptyHint(stringResource(R.string.playlist_empty))
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    DetailHeader(
                        state = state,
                        title = playlist.name,
                        subtitle = stringResource(R.string.track_count, items.size),
                        coverItem = items.firstOrNull(),
                        onPlay = { state.player.play(items, 0) },
                    )
                }
                if (playlist.pendingSync) {
                    item {
                        Text(
                            stringResource(R.string.pending_sync),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
                itemsIndexed(items) { index, track ->
                    val id = track.id
                    TrackRow(
                        item = track,
                        index = index + 1,
                        artworkUrl = state.artworkUrl(track, 120),
                        onClick = { state.player.play(items, index) },
                        isCurrent = state.player.current?.id == id,
                        favorite = state.favorites.isFavorite(track),
                        onToggleFavorite = { state.favorites.toggle(track) },
                        downloaded = state.downloads.downloadedIds[id] == true,
                        downloading = state.downloads.progress[id],
                        downloadError = state.downloads.errors[id] != null,
                        onRemove = { state.localPlaylists.removeItem(playlist.id, id) },
                        onLongClick = { state.trackMenu = track },
                    )
                }
            }
        }
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text(stringResource(R.string.rename)) },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    label = { Text(stringResource(R.string.playlist_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameValue.isNotBlank()) {
                            state.localPlaylists.rename(playlist.id, renameValue.trim())
                        }
                        showRename = false
                    },
                    enabled = renameValue.isNotBlank(),
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.delete_playlist_title)) },
            text = { Text(stringResource(R.string.delete_playlist_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.localPlaylists.delete(playlist.id)
                        showDelete = false
                        state.back()
                    },
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
fun DetailHeader(
    state: AppState,
    title: String,
    subtitle: String?,
    coverItem: BaseItem?,
    onPlay: () -> Unit,
    onDownloadAll: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(coverItem?.let { state.artworkUrl(it, 280) }, 140.dp, 12.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayIconButton(onClick = onPlay)
                if (onDownloadAll != null) {
                    Spacer(Modifier.width(12.dp))
                    TextButton(onClick = onDownloadAll) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.download_all))
                    }
                }
            }
        }
    }
}