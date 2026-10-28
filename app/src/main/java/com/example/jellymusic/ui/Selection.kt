package com.example.jellymusic.ui

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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.jellymusic.AppState
import com.example.jellymusic.R
import com.example.jellymusic.data.SelectionStore
import com.example.jellymusic.jellyfin.BaseItem

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
    val allFavorite = items.isNotEmpty() && items.all { state.favorites.isFavorite(it) }
    val canAddToPlaylist = items.any { it.localUri == null }
    val api = state.api
    val downloaded = items.filter { state.downloads.isDownloaded(it.id) }

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
                        items.forEach { state.player.addToQueue(it) }
                        selection.clear()
                    },
                    enabled = items.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.add_to_queue))
                }
                TextButton(
                    onClick = {
                        state.addToPlaylistBatch = items
                        selection.clear()
                    },
                    enabled = canAddToPlaylist,
                ) {
                    Text(stringResource(R.string.add_to_playlist))
                }
                TextButton(
                    onClick = {
                        api?.let { server ->
                            state.downloads.downloadAll(
                                server,
                                items.filter { it.localUri == null },
                            )
                        }
                        selection.clear()
                    },
                    enabled = api != null && items.any { it.localUri == null },
                ) {
                    Text(stringResource(R.string.download))
                }
                TextButton(
                    onClick = {
                        items.forEach { item ->
                            if (state.favorites.isFavorite(item) == allFavorite) {
                                state.favorites.toggle(item)
                            }
                        }
                        selection.clear()
                    },
                    enabled = items.isNotEmpty(),
                ) {
                    Text(
                        stringResource(
                            if (allFavorite) R.string.unfavorite else R.string.favorite,
                        ),
                    )
                }
                if (downloaded.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            downloaded.forEach { state.downloads.delete(it) }
                            selection.clear()
                        },
                    ) {
                        Text(stringResource(R.string.delete_download))
                    }
                }
            }
        }
    }
}
