package com.example.jellymusic.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
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
import com.example.jellymusic.jellyfin.BaseItem

@Composable
fun FoldersScreen(state: AppState) {
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.folders), onBack = { state.back() })

        if (state.isOffline) {
            EmptyHint(stringResource(R.string.offline_hint))
            return@Column
        }

        Loaded(key = "folders", load = { state.requireApi().getUserViews() }) { views ->
            val musicViews = views.filter { it.collectionType == "music" }.ifEmpty { views }
            if (musicViews.isEmpty()) {
                EmptyHint(stringResource(R.string.folder_empty))
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(musicViews, key = { it.id }) { view ->
                        FolderRow(
                            state = state,
                            item = view,
                            onClick = { state.navigate(Screen.Folder(view.id, view.name)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FolderScreen(state: AppState, screen: Screen.Folder) {
    Column(Modifier.fillMaxSize()) {
        TopBar(screen.name, onBack = { state.back() })

        if (state.isOffline) {
            EmptyHint(stringResource(R.string.offline_hint))
            return@Column
        }

        Loaded(
            key = "folder-${screen.id}",
            load = { state.requireApi().getFolderChildren(screen.id) },
        ) { children ->
            if (children.isEmpty()) {
                EmptyHint(stringResource(R.string.folder_empty))
            } else {
                val audio = children.filter { it.type == "Audio" }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(children, key = { it.id }) { item ->
                        FolderRow(
                            state = state,
                            item = item,
                            showArtwork = item.type == "Audio",
                            onClick = {
                                if (item.type == "Audio") {
                                    val index = audio.indexOfFirst { it.id == item.id }
                                    if (index >= 0) {
                                        state.player.play(audio, index)
                                    }
                                } else {
                                    state.navigate(Screen.Folder(item.id, item.name))
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderRow(
    state: AppState,
    item: BaseItem,
    showArtwork: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showArtwork) {
            Artwork(state.artworkUrl(item, 160), 48.dp, 8.dp)
        } else {
            Box(
                Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (item.isFolder) Icons.Default.Folder else Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            item.name,
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