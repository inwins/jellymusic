package com.example.jellymusic.ui

import androidx.activity.compose.BackHandler
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.example.jellymusic.AppState
import com.example.jellymusic.R
import com.example.jellymusic.jellyfin.LyricLine
import com.example.jellymusic.player.MusicPlayer
import com.example.jellymusic.player.PlayMode
import kotlinx.coroutines.launch
import com.example.jellymusic.util.formatDuration

@Composable
fun MiniPlayerBar(state: AppState) {
    val player = state.player
    val current = player.current ?: return
    val fraction = if (player.durationMs > 0) {
        (player.positionMs.toFloat() / player.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    Surface(tonalElevation = 4.dp) {
        Column {
            LinearProgressIndicator(
                progress = fraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { state.navigate(Screen.NowPlaying) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(state.artworkUrl(current, 160), 44.dp, 6.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        current.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        current.primaryArtist.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { player.toggle() }) {
                    Icon(
                        if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.play_pause),
                    )
                }
                IconButton(onClick = { player.next() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.next))
                }
            }
        }
    }
}

@Composable
fun NowPlayingScreen(state: AppState) {
    val player = state.player
    val current = player.current ?: return

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var lyricsFullScreen by rememberSaveable { mutableStateOf(false) }
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    val sliderPosition = dragPosition ?: player.positionMs.toFloat()
    val duration = player.durationMs.coerceAtLeast(1L)
    val downloaded = state.downloads.isDownloaded(current.id)
    val downloadProgress = state.downloads.progress[current.id]
    val favorite = state.favorites.isFavorite(current)
    val context = LocalContext.current

    val lyrics by produceState<List<LyricLine>?>(null, current.id) {
        value = try {
            state.api?.getLyrics(current.id) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (lyricsFullScreen) {
            FullScreenLyrics(
                title = current.name,
                lyrics = lyrics,
                positionMs = player.positionMs,
                onClose = { lyricsFullScreen = false },
                onSeek = { player.seekTo(it) },
                modifier = Modifier.fillMaxSize(),
            )
            return@Column
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { state.back() }) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.collapse))
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { state.favorites.toggle(current) }) {
                Icon(
                    if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = stringResource(
                        if (favorite) R.string.unfavorite else R.string.favorite,
                    ),
                    tint = if (favorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            IconButton(onClick = { state.addToPlaylistItem = current }) {
                Icon(
                    Icons.Default.PlaylistAdd,
                    contentDescription = stringResource(R.string.add_to_playlist),
                )
            }
            IconButton(
                onClick = {
                    when {
                        downloadProgress != null -> state.downloads.cancel(current)
                        downloaded -> state.downloads.delete(current)
                        state.downloads.errors[current.id] != null ->
                            state.api?.let { state.downloads.retry(it, current) }
                        else -> state.api?.let { state.downloads.download(it, current) }
                    }
                },
            ) {
                when {
                    downloadProgress != null -> CircularProgressIndicator(
                        progress = if (downloadProgress < 0f) 0f else downloadProgress,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                    )

                    downloaded -> Icon(
                        Icons.Default.DownloadDone,
                        contentDescription = stringResource(R.string.delete_download),
                        tint = MaterialTheme.colorScheme.primary,
                    )

                    else -> Icon(Icons.Default.Download, contentDescription = stringResource(R.string.download))
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            val side = minOf(maxWidth, maxHeight, 320.dp)
            Artwork(state.artworkUrl(current, 800), side, 16.dp)
        }
        Spacer(Modifier.height(12.dp))

        Text(
            current.name,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            listOfNotNull(current.primaryArtist, current.album).joinToString(" · "),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(12.dp))
        Slider(
            value = sliderPosition.coerceIn(0f, duration.toFloat()),
            onValueChange = { dragPosition = it },
            valueRange = 0f..duration.toFloat(),
            onValueChangeFinished = {
                player.seekTo((dragPosition ?: 0f).toLong())
                dragPosition = null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                formatDuration(sliderPosition.toLong()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                formatDuration(duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    player.cyclePlayMode()
                    val label = when (player.playMode) {
                        PlayMode.SEQUENTIAL -> R.string.play_mode_sequential
                        PlayMode.LIST_LOOP -> R.string.play_mode_list_loop
                        PlayMode.SINGLE_LOOP -> R.string.play_mode_single_loop
                        PlayMode.SHUFFLE -> R.string.play_mode_shuffle
                    }
                    Toast.makeText(context, context.getString(label), Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(48.dp),
            ) {
                val icon = when (player.playMode) {
                    PlayMode.SEQUENTIAL -> Icons.Default.PlaylistPlay
                    PlayMode.LIST_LOOP -> Icons.Default.Repeat
                    PlayMode.SINGLE_LOOP -> Icons.Default.RepeatOne
                    PlayMode.SHUFFLE -> Icons.Default.Shuffle
                }
                val label = when (player.playMode) {
                    PlayMode.SEQUENTIAL -> R.string.play_mode_sequential
                    PlayMode.LIST_LOOP -> R.string.play_mode_list_loop
                    PlayMode.SINGLE_LOOP -> R.string.play_mode_single_loop
                    PlayMode.SHUFFLE -> R.string.play_mode_shuffle
                }
                Icon(
                    icon,
                    contentDescription = stringResource(label),
                    tint = if (player.playMode == PlayMode.SEQUENTIAL) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            IconButton(onClick = { player.previous() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.previous), modifier = Modifier.size(40.dp))
            }
            IconButton(
                onClick = { player.toggle() },
                modifier = Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            ) {
                Icon(
                    if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.play_pause),
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            IconButton(onClick = { player.next() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.next), modifier = Modifier.size(40.dp))
            }
        }

        Spacer(Modifier.height(8.dp))
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.lyrics)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.queue)) })
        }

        if (tab == 0) {
            LyricsView(
                lyrics = lyrics,
                positionMs = player.positionMs,
                fullScreen = false,
                onExpand = { lyricsFullScreen = true },
                onSeekLine = {},
                modifier = Modifier.weight(1.5f),
            )
        } else {
            QueueList(
                player = player,
                modifier = Modifier.weight(1.5f),
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun QueueList(
    player: MusicPlayer,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    var targetIndex by remember { mutableStateOf<Int?>(null) }

    LazyColumn(state = listState, modifier = modifier) {
        itemsIndexed(player.queue) { index, item ->
            val isCurrent = index == player.playIndex
            val dragging = draggingIndex == index
            Row(
                Modifier
                    .fillMaxWidth()
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationY = if (dragging) dragDelta else 0f }
                    .background(
                        if (dragging) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            Color.Transparent
                        },
                    )
                    .clickable { player.jumpTo(index) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = if (isCurrent) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        item.primaryArtist.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    item.durationMs?.let { formatDuration(it) }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = stringResource(R.string.drag_to_reorder),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .pointerInput(index, player.queue.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingIndex = index
                                    dragDelta = 0f
                                    targetIndex = index
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragDelta += amount.y
                                    val info = listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.index == index }
                                    if (info != null) {
                                        val viewportStart = listState.layoutInfo.viewportStartOffset
                                        val viewportEnd = listState.layoutInfo.viewportEndOffset
                                        val itemTop = info.offset + dragDelta
                                        val itemBottom = itemTop + info.size
                                        if (itemTop < viewportStart + 64) {
                                            scope.launch { listState.scrollBy(-24f) }
                                        } else if (itemBottom > viewportEnd - 64) {
                                            scope.launch { listState.scrollBy(24f) }
                                        }
                                        val center = info.offset + info.size / 2f + dragDelta
                                        val target = listState.layoutInfo.visibleItemsInfo
                                            .firstOrNull {
                                                center >= it.offset &&
                                                    center <= it.offset + it.size
                                            }
                                            ?.index
                                        if (target != null) targetIndex = target
                                    }
                                },
                                onDragEnd = {
                                    val from = draggingIndex
                                    val to = targetIndex
                                    draggingIndex = null
                                    dragDelta = 0f
                                    targetIndex = null
                                    if (from != null && to != null && from != to) {
                                        player.moveQueueItem(from, to)
                                    }
                                },
                                onDragCancel = {
                                    draggingIndex = null
                                    dragDelta = 0f
                                    targetIndex = null
                                },
                            )
                        },
                )
                IconButton(onClick = { player.removeFromQueue(index) }) {
                    Icon(
                        Icons.Default.RemoveCircleOutline,
                        contentDescription = stringResource(R.string.remove_from_queue),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FullScreenLyrics(
    title: String,
    lyrics: List<LyricLine>?,
    positionMs: Long,
    onClose: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onClose() }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.exit_fullscreen_lyrics))
            }
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(48.dp))
        }
        LyricsView(
            lyrics = lyrics,
            positionMs = positionMs,
            fullScreen = true,
            onExpand = {},
            onSeekLine = onSeek,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LyricsView(
    lyrics: List<LyricLine>?,
    positionMs: Long,
    fullScreen: Boolean,
    onExpand: () -> Unit,
    onSeekLine: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (lyrics == null) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        return
    }
    if (lyrics.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.no_lyrics),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    val synced = lyrics.any { it.startMs != null }
    val currentIndex = remember(lyrics, positionMs, synced) {
        if (!synced) -1 else {
            var index = -1
            lyrics.forEachIndexed { i, line ->
                val start = line.startMs ?: return@forEachIndexed
                if (start <= positionMs) index = i
            }
            index
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    val currentSize = if (fullScreen) 30.sp else 24.sp
    val otherSize = if (fullScreen) 22.sp else 18.sp

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .let { if (fullScreen) it else it.clickable(onClick = onExpand) },
        verticalArrangement = Arrangement.spacedBy(if (fullScreen) 22.dp else 12.dp),
        contentPadding = PaddingValues(vertical = if (fullScreen) 140.dp else 16.dp),
    ) {
        itemsIndexed(lyrics) { index, line ->
            val isCurrent = index == currentIndex
            Text(
                line.text,
                fontSize = if (isCurrent) currentSize else otherSize,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .let { base ->
                        if (fullScreen && line.startMs != null) {
                            base.clickable { line.startMs?.let(onSeekLine) }
                        } else {
                            base
                        }
                    }
                    .padding(vertical = 2.dp),
            )
        }
    }
}