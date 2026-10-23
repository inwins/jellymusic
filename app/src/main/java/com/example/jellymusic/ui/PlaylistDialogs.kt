package com.example.jellymusic.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
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
    LocalEditDialog(state)
    AudioInfoDialog(state)
}

private data class AudioInfo(
    val codec: String,
    val bitrate: String,
    val sampleRate: String,
    val duration: String,
    val size: String,
    val local: Boolean,
)

@Composable
private fun AudioInfoDialog(state: AppState) {
    val item = state.audioInfoItem ?: return
    val context = LocalContext.current
    val info by androidx.compose.runtime.produceState<AudioInfo?>(initialValue = null, item.id) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val uri = item.localUri?.let { android.net.Uri.parse(it) }
                ?: state.downloads.uriFor(item.id)
            loadAudioInfo(context, item, uri)
        }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { state.audioInfoItem = null },
        title = { Text(stringResource(R.string.audio_info)) },
        text = {
            Column {
                Text(item.name, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.size(8.dp))
                val current = info
                if (current == null) {
                    Text(stringResource(R.string.analyzing))
                } else {
                    InfoText("Codec", current.codec)
                    InfoText(stringResource(R.string.bitrate), current.bitrate)
                    InfoText(stringResource(R.string.sample_rate), current.sampleRate)
                    InfoText(stringResource(R.string.duration_label), current.duration)
                    InfoText(stringResource(R.string.file_size), current.size)
                    if (!current.local) {
                        Text(
                            stringResource(R.string.audio_features) + ": " +
                                stringResource(R.string.online),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { state.audioInfoItem = null }) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun InfoText(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

private fun loadAudioInfo(
    context: android.content.Context,
    item: com.example.jellymusic.jellyfin.BaseItem,
    uri: android.net.Uri?,
): AudioInfo {
    var codec = item.container.orEmpty()
    var bitrate = "-"
    var sampleRate = "-"
    var duration = item.durationMs?.let { com.example.jellymusic.util.formatDuration(it) } ?: "-"
    var size = "-"
    var local = false
    if (uri != null) {
        local = true
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                ?.let { codec = it.substringAfter('/').uppercase() }
            retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toLongOrNull()
                ?.let { bitrate = "${it / 1000} kbps" }
            retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                ?.toIntOrNull()
                ?.let { sampleRate = "$it Hz" }
            retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.let { duration = com.example.jellymusic.util.formatDuration(it) }
        } catch (_: Exception) {
        } finally {
            runCatching { retriever.release() }
        }
        val length = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull() ?: -1L
        if (length > 0) size = com.example.jellymusic.ui.formatBytes(length)
    }
    return AudioInfo(codec.ifBlank { "-" }, bitrate, sampleRate, duration, size, local)
}

@Composable
private fun LocalEditDialog(state: AppState) {
    val item = state.localEditItem ?: return
    val isLocal = item.localUri != null
    var title by remember(item.id) { mutableStateOf(item.name) }
    var artist by remember(item.id) { mutableStateOf(item.trackArtist.orEmpty()) }
    var album by remember(item.id) { mutableStateOf(item.album.orEmpty()) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val coverLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (isLocal) {
            state.localMusic.setCover(item.id, uri)
        } else {
            scope.launch {
                val ok = runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null) state.api?.uploadItemImage(item.id, bytes)
                }.isSuccess
                Toast.makeText(
                    context,
                    context.getString(if (ok) R.string.sync_done else R.string.sync_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    AlertDialog(
        onDismissRequest = { state.localEditItem = null },
        title = { Text(stringResource(R.string.edit_info)) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.title_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text(stringResource(R.string.artist_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text(stringResource(R.string.album_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { coverLauncher.launch(arrayOf("image/*")) }) {
                    Text(stringResource(R.string.change_cover))
                }
                if (!isLocal) {
                    Text(
                        stringResource(R.string.sync_to_server),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (isLocal) {
                    state.localMusic.setOverride(item.id, title, artist, album)
                } else {
                    scope.launch {
                        val ok = runCatching {
                            state.api?.updateItemMetadata(
                                itemId = item.id,
                                name = title.ifBlank { item.name },
                                artists = if (artist.isBlank()) {
                                    item.artists
                                } else {
                                    listOf(artist)
                                },
                                album = album.ifBlank { null },
                            )
                        }.isSuccess
                        Toast.makeText(
                            context,
                            context.getString(if (ok) R.string.sync_done else R.string.sync_failed),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                state.localEditItem = null
            }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = { state.localEditItem = null }) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun TrackActionsDialog(state: AppState) {
    val item = state.trackMenu ?: return
    val isLocal = item.localUri != null
    val favorite = state.favorites.isFavorite(item)
    val downloaded = state.downloads.isDownloaded(item.id)
    val downloading = state.downloads.progress[item.id] != null
    val downloadError = state.downloads.errors[item.id] != null
    val cached = state.streamCache.isCached(item.id)
    val rating = state.ratings.rating(item.id)
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { state.trackMenu = null },
        title = { Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    (1..5).forEach { star ->
                        IconButton(
                            onClick = {
                                state.ratings.set(
                                    item.id,
                                    if (rating == star) 0 else star,
                                )
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                if (star <= rating) {
                                    Icons.Default.Star
                                } else {
                                    Icons.Default.StarBorder
                                },
                                contentDescription = stringResource(R.string.rating),
                                tint = if (star <= rating) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                Text(
                    when {
                        downloaded -> stringResource(R.string.downloaded)
                        cached -> stringResource(R.string.cached)
                        else -> stringResource(R.string.online)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!isLocal) {
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
                    icon = { Icon(Icons.Default.Radio, contentDescription = null) },
                    text = stringResource(R.string.instant_mix),
                ) {
                    val api = state.api
                    if (api != null) {
                        scope.launch {
                            val mix = runCatching { api.getInstantMix(item.id) }
                                .getOrDefault(emptyList())
                            if (mix.isNotEmpty()) state.player.play(mix, 0)
                        }
                    }
                    state.trackMenu = null
                }
                ActionRow(
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    text = stringResource(R.string.edit_info),
                ) {
                    state.localEditItem = item
                    state.trackMenu = null
                }
                if (!isLocal) {
                    ActionRow(
                        icon = { Icon(Icons.Default.DoneAll, contentDescription = null) },
                        text = stringResource(R.string.mark_played),
                    ) {
                        scope.launch { runCatching { state.api?.markPlayed(item.id) } }
                        state.trackMenu = null
                    }
                }
                ActionRow(
                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    text = stringResource(R.string.audio_info),
                ) {
                    state.audioInfoItem = item
                    state.trackMenu = null
                }
                ActionRow(
                    icon = { Icon(Icons.Default.GraphicEq, contentDescription = null) },
                    text = stringResource(R.string.audio_features),
                ) {
                    state.navigate(Screen.AudioFeatures(item))
                    state.trackMenu = null
                }
                if (!isLocal) {
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
    val batch = state.addToPlaylistBatch
    val items = if (batch.isNotEmpty()) batch else listOfNotNull(state.addToPlaylistItem)
    if (items.isEmpty()) return
    val context = LocalContext.current
    var showCreate by remember { mutableStateOf(false) }
    val playlists = state.localPlaylists.playlists
    fun close() {
        state.addToPlaylistItem = null
        state.addToPlaylistBatch = emptyList()
    }

    AlertDialog(
        onDismissRequest = { close() },
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
                            items.forEach { state.localPlaylists.addItem(playlist.id, it) }
                            close()
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
            TextButton(onClick = { close() }) {
                Text(stringResource(R.string.cancel))
            }
        },
    )

    if (showCreate) {
        CreatePlaylistDialog(
            onCreate = { name ->
                val playlist = state.localPlaylists.create(name)
                items.forEach { state.localPlaylists.addItem(playlist.id, it) }
                showCreate = false
                close()
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