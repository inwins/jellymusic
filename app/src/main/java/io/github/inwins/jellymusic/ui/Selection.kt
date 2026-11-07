package io.github.inwins.jellymusic.ui

import android.widget.Toast

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.inwins.jellymusic.AppState
import io.github.inwins.jellymusic.R
import io.github.inwins.jellymusic.data.SelectionStore
import io.github.inwins.jellymusic.jellyfin.BaseItem
import kotlinx.coroutines.launch

val LocalSelection = staticCompositionLocalOf<SelectionStore?> { null }

@Composable
fun RegisterSelectableItems(items: List<BaseItem>) {
    val selection = LocalSelection.current ?: return
    LaunchedEffect(items) { selection.setAllItems(items) }
}

@Composable
fun SelectionBar(state: AppState) {
    val selection = state.selection
    if (!selection.selecting) return
    val items = selection.selected.values.toList()
    val audioItems = items.filter { !it.isFolder }
    val folderItems = items.filter { it.isFolder }
    val downloadableAudio = audioItems.filter { it.localUri == null }
    val allFavorite = audioItems.isNotEmpty() && audioItems.all { state.favorites.isFavorite(it) }
    val canAddToPlaylist = audioItems.any { it.localUri == null }
    val api = state.api
    val downloaded = audioItems.filter { state.downloads.isDownloaded(it.id) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    Surface(tonalElevation = 8.dp, shadowElevation = 8.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selection.clear() }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                }
                Text(
                    stringResource(R.string.selected_count, items.size),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { selection.selectAll() },
                    enabled = selection.allItems.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.select_all))
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(
                    onClick = {
                        audioItems.forEach { state.player.addToQueue(it) }
                        selection.clear()
                    },
                    enabled = audioItems.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.add_to_queue))
                }
                TextButton(
                    onClick = {
                        state.addToPlaylistBatch = audioItems
                        selection.clear()
                    },
                    enabled = canAddToPlaylist,
                ) {
                    Text(stringResource(R.string.add_to_playlist))
                }
                TextButton(
                    onClick = {
                        val server = api
                        if (server != null) {
                            if (downloadableAudio.isNotEmpty()) {
                                state.downloads.downloadAll(server, downloadableAudio)
                            }
                            if (folderItems.isNotEmpty()) {
                                scope.launch {
                                    var count = downloadableAudio.size
                                    folderItems.forEach { folder ->
                                        count += state.queueFolderDownload(folder.id)
                                    }
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.download_folder_queued, count),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        }
                        selection.clear()
                    },
                    enabled = api != null &&
                        (downloadableAudio.isNotEmpty() || folderItems.isNotEmpty()),
                ) {
                    Text(stringResource(R.string.download))
                }
                TextButton(
                    onClick = {
                        audioItems.forEach { item ->
                            if (state.favorites.isFavorite(item) == allFavorite) {
                                state.favorites.toggle(item)
                            }
                        }
                        selection.clear()
                    },
                    enabled = audioItems.isNotEmpty(),
                ) {
                    Text(
                        stringResource(
                            if (allFavorite) R.string.unfavorite else R.string.favorite,
                        ),
                    )
                }
                if (downloaded.isNotEmpty()) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(R.string.delete_download))
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        DeleteDownloadsDialog(
            count = downloaded.size,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                downloaded.forEach { state.downloads.delete(it) }
                confirmDelete = false
                selection.clear()
            },
        )
    }
}
