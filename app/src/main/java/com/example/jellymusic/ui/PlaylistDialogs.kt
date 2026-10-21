package com.example.jellymusic.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.QueueMusic
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jellymusic.AppState
import com.example.jellymusic.R

@Composable
fun GlobalDialogs(state: AppState) {
    TrackActionsDialog(state)
    AddToPlaylistDialog(state)
}

@Composable
private fun TrackActionsDialog(state: AppState) {
    val item = state.trackMenu ?: return
    val favorite = state.favorites.isFavorite(item)
    val downloaded = state.downloads.isDownloaded(item.id)
    val downloading = state.downloads.progress[item.id] != null
    val downloadError = state.downloads.errors[item.id] != null
    val cached = state.streamCache.isCached(item.id)

    AlertDialog(
        onDismissRequest = { state.trackMenu = null },
        title = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                Text(
                    when {
                        downloaded -> stringResource(R.string.downloaded)
                        cached -> stringResource(R.string.cached)
                        else -> stringResource(R.string.online)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ActionRow(
                    icon = {
                        Icon(
                            if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                        )
                    },
                    text = stringResource(if (favorite) R.string.unfavorite else R.string.favorite),
                ) {
                    state.favorites.toggle(item)
                    state.trackMenu = null
                }
                ActionRow(
                    icon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) },
                    text = stringResource(R.string.add_to_playlist),
                ) {
                    state.addToPlaylistItem = item
                    state.trackMenu = null
                }
                ActionRow(
                    icon = { Icon(Icons.Default.QueuePlayNext, contentDescription = null) },
                    text = stringResource(R.string.play_next),
                ) {
                    state.player.playNext(item)
                    state.trackMenu = null
                }
                ActionRow(
                    icon = { Icon(Icons.Default.QueueMusic, contentDescription = null) },
                    text = stringResource(R.string.add_to_queue),
                ) {
                    state.player.addToQueue(item)
                    state.trackMenu = null
                }
                ActionRow(
                    icon = {
                        Icon(
                            if (downloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                            contentDescription = null,
                        )
                    },
                    text = stringResource(
                        when {
                            downloading -> R.string.cancel_download
                            downloaded -> R.string.delete_download
                            downloadError -> R.string.retry_download
                            else -> R.string.download
                        },
                    ),
                ) {
                    when {
                        downloading -> state.downloads.cancel(item)
                        downloaded -> state.downloads.delete(item)
                        downloadError -> state.api?.let { state.downloads.retry(it, item) }
                        else -> state.api?.let { state.downloads.download(it, item) }
                    }
                    state.trackMenu = null
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { state.trackMenu = null }) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun AddToPlaylistDialog(state: AppState) {
    val item = state.addToPlaylistItem ?: return
    val context = LocalContext.current
    var showCreate by remember { mutableStateOf(false) }
    val playlists = state.localPlaylists.playlists

    AlertDialog(
        onDismissRequest = { state.addToPlaylistItem = null },
        title = { Text(stringResource(R.string.add_to_playlist_title)) },
        text = {
            Column {
                if (playlists.isEmpty()) {
                    Text(
                        stringResource(R.string.no_local_playlists),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    playlists.forEach { playlist ->
                        ActionRow(
                            icon = { Icon(Icons.Default.QueueMusic, contentDescription = null) },
                            text = playlist.name,
                        ) {
                            state.localPlaylists.addItem(playlist.id, item)
                            state.addToPlaylistItem = null
                            Toast.makeText(
                                context,
                                context.getString(R.string.added_to_playlist),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
                ActionRow(
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = stringResource(R.string.new_playlist),
                ) {
                    showCreate = true
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { state.addToPlaylistItem = null }) {
                Text(stringResource(R.string.cancel))
            }
        },
    )

    if (showCreate) {
        CreatePlaylistDialog(
            onCreate = { name ->
                val playlist = state.localPlaylists.create(name)
                state.localPlaylists.addItem(playlist.id, item)
                showCreate = false
                state.addToPlaylistItem = null
                Toast.makeText(
                    context,
                    context.getString(R.string.added_to_playlist),
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onDismiss = { showCreate = false },
        )
    }
}

@Composable
fun CreatePlaylistDialog(
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_playlist)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.playlist_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name.trim()) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun ActionRow(
    icon: @Composable () -> Unit,
    text: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}