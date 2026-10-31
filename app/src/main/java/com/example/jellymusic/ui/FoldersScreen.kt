package com.example.jellymusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import android.widget.Toast
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jellymusic.AppState
import com.example.jellymusic.R
import com.example.jellymusic.jellyfin.BaseItem

@Composable
fun FoldersScreen(state: AppState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun downloadFolder(folderId: String) {
        scope.launch {
            val count = state.queueFolderDownload(folderId)
            Toast.makeText(
                context,
                context.getString(R.string.download_folder_queued, count),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(
            stringResource(R.string.folders),
            onBack = { state.back() },
            actions = {
                IconButton(onClick = { state.selection.begin() }) {
                    Icon(
                        Icons.Default.Checklist,
                        contentDescription = stringResource(R.string.select),
                    )
                }
            },
        )

        if (state.isOffline) {
            EmptyHint(stringResource(R.string.offline_hint))
            return@Column
        }

        Loaded(key = "folders", load = { state.requireApi().getUserViews() }) { views ->
            val musicViews = views.filter { it.collectionType == "music" }.ifEmpty { views }
            RegisterSelectableItems(musicViews)
            if (musicViews.isEmpty()) {
                EmptyHint(stringResource(R.string.folder_empty))
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(musicViews, key = { it.id }) { view ->
                        FolderRow(
                            state = state,
                            item = view,
                            onClick = { state.navigate(Screen.Folder(view.id, view.name)) },
                            onDownload = { downloadFolder(view.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FolderScreen(state: AppState, screen: Screen.Folder) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun downloadFolder(folderId: String) {
        scope.launch {
            val count = state.queueFolderDownload(folderId)
            Toast.makeText(
                context,
                context.getString(R.string.download_folder_queued, count),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(
            screen.name,
            onBack = { state.back() },
            actions = {
                IconButton(onClick = { state.selection.begin() }) {
                    Icon(
                        Icons.Default.Checklist,
                        contentDescription = stringResource(R.string.select),
                    )
                }
                IconButton(onClick = { downloadFolder(screen.id) }) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = stringResource(R.string.download_folder),
                    )
                }
            },
        )

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
                RegisterSelectableItems(children)
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
                            onDownload = {
                                if (item.type == "Audio") {
                                    state.api?.let { state.downloads.download(it, item) }
                                } else {
                                    downloadFolder(item.id)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(
    state: AppState,
    item: BaseItem,
    showArtwork: Boolean = false,
    onClick: () -> Unit,
    onDownload: (() -> Unit)? = null,
) {
    val selection = LocalSelection.current
    val inSelection = selection?.selecting == true
    val selected = selection?.selected?.containsKey(item.id) == true
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (inSelection) selection?.toggle(item) else onClick()
                },
                onLongClick = {
                    if (inSelection) {
                        selection?.toggle(item)
                    } else {
                        selection?.let {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            it.begin(item)
                        }
                    }
                },
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (inSelection) {
            Checkbox(
                checked = selected,
                onCheckedChange = { selection?.toggle(item) },
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
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
        if (onDownload != null && !inSelection) {
            IconButton(onClick = onDownload) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = stringResource(R.string.download),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (!inSelection) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}