package com.example.jellymusic.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jellymusic.AppState
import com.example.jellymusic.R
import com.example.jellymusic.jellyfin.BaseItem
import kotlinx.coroutines.launch

private enum class AllSongsSort(
    val sortBy: String,
    val defaultOrder: String,
    val labelRes: Int,
) {
    NAME("SortName", "Ascending", R.string.sort_name),
    ARTIST("Artist", "Ascending", R.string.sort_artist),
    RECENT("DateCreated", "Descending", R.string.sort_recent),
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AllSongsScreen(state: AppState) {
    val items = remember { mutableStateListOf<BaseItem>() }
    var loading by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    var startIndex by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var sort by remember {
        mutableStateOf(
            runCatching { AllSongsSort.valueOf(state.settings.allSongsSort) }
                .getOrDefault(AllSongsSort.NAME),
        )
    }
    var descending by remember { mutableStateOf(state.settings.allSongsDescending) }
    var favoritesOnly by remember { mutableStateOf(state.settings.allSongsFavoritesOnly) }
    var minRating by remember { mutableIntStateOf(0) }
    var sortMenu by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var didScroll by remember { mutableStateOf(false) }
    val selected = remember { mutableStateMapOf<String, BaseItem>() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val sortOrder = if (descending) {
        if (sort.defaultOrder == "Ascending") "Descending" else "Ascending"
    } else {
        sort.defaultOrder
    }

    suspend fun loadNext() {
        if (loading || endReached) return
        loading = true
        try {
            val page = state.requireApi().getAllSongs(
                startIndex = startIndex,
                limit = PAGE_SIZE,
                sortBy = sort.sortBy,
                sortOrder = sortOrder,
            )
            if (page.isEmpty()) {
                endReached = true
            } else {
                items.addAll(page)
                startIndex += page.size
                if (page.size < PAGE_SIZE) endReached = true
            }
            error = null
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    LaunchedEffect(sort, descending, favoritesOnly) {
        state.settings.allSongsSort = sort.name
        state.settings.allSongsDescending = descending
        state.settings.allSongsFavoritesOnly = favoritesOnly
        items.clear()
        startIndex = 0
        endReached = false
        error = null
        didScroll = false
        when {
            state.isOffline -> {
                items.addAll(sortClient(state.downloads.downloadedItems(), sort, descending))
                endReached = true
            }

            favoritesOnly -> {
                val favorites = runCatching { state.requireApi().getFavorites() }
                    .getOrDefault(emptyList())
                items.addAll(sortClient(favorites, sort, descending))
                endReached = true
            }

            else -> loadNext()
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 6
        }
    }
    LaunchedEffect(shouldLoadMore, items.size) {
        if (shouldLoadMore && items.isNotEmpty() && !endReached && !favoritesOnly && !state.isOffline) {
            loadNext()
        }
    }

    val visible = remember(items.toList(), minRating) {
        if (minRating == 0) {
            items.toList()
        } else {
            items.filter { state.ratings.rating(it.id) >= minRating }
        }
    }
    val sections = remember(visible, sort) {
        val indexed = visible.withIndex().toList()
        if (sort == AllSongsSort.RECENT) {
            listOf<Pair<Char?, List<IndexedValue<BaseItem>>>>(null to indexed)
        } else {
            indexed
                .groupBy { entry ->
                    alphabetIndexKey(
                        if (sort == AllSongsSort.ARTIST) {
                            entry.value.trackArtist ?: entry.value.name
                        } else {
                            entry.value.name
                        },
                    )
                }
                .toSortedMap()
                .map { it.key to it.value }
        }
    }
    val sectionPositions = remember(sections) {
        var position = 1
        val map = LinkedHashMap<Char, Int>()
        sections.forEach { (key, entries) ->
            if (key != null) {
                map[key] = position
                position++
            }
            position += entries.size
        }
        map
    }

    LaunchedEffect(items.size, state.player.current?.id, sections) {
        if (!didScroll && items.isNotEmpty()) {
            val id = state.player.current?.id ?: return@LaunchedEffect
            var position = 1
            for ((key, entries) in sections) {
                if (key != null) position++
                val offset = entries.indexOfFirst { it.value.id == id }
                if (offset >= 0) {
                    listState.animateScrollToItem(position + offset)
                    didScroll = true
                    return@LaunchedEffect
                }
                position += entries.size
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            stringResource(R.string.all_songs),
            onBack = {
                if (selecting) {
                    selecting = false
                    selected.clear()
                } else {
                    state.back()
                }
            },
            actions = {
                IconButton(onClick = {
                    if (selecting) {
                        selecting = false
                        selected.clear()
                    } else {
                        selecting = true
                    }
                }) {
                    Icon(
                        if (selecting) Icons.Default.Close else Icons.Default.Checklist,
                        contentDescription = stringResource(R.string.select),
                    )
                }
            },
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                TextButton(onClick = { sortMenu = true }) {
                    Text(stringResource(R.string.sort) + ": " + stringResource(sort.labelRes))
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    AllSongsSort.values().forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(option.labelRes)) },
                            onClick = {
                                sort = option
                                descending = false
                                sortMenu = false
                            },
                        )
                    }
                }
            }
            IconButton(onClick = { descending = !descending }) {
                Icon(
                    if (descending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = stringResource(R.string.sort),
                    modifier = Modifier.size(18.dp),
                )
            }
            FilterChip(
                selected = favoritesOnly,
                onClick = { favoritesOnly = !favoritesOnly },
                label = { Text(stringResource(R.string.favorite)) },
            )
            FilterChip(
                selected = minRating > 0,
                onClick = {
                    minRating = when (minRating) {
                        0 -> 4
                        4 -> 5
                        else -> 0
                    }
                },
                label = {
                    Text(
                        if (minRating > 0) {
                            stringResource(R.string.rating_at_least, minRating)
                        } else {
                            stringResource(R.string.rating_filter)
                        },
                    )
                },
            )
            Spacer(Modifier.weight(1f))
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }

        Box(Modifier.weight(1f)) {
            when {
                visible.isEmpty() && loading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                visible.isEmpty() && error != null -> Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = {
                        error = null
                        scope.launch { loadNext() }
                    }) {
                        Text(stringResource(R.string.retry))
                    }
                }

                visible.isEmpty() -> EmptyHint(stringResource(R.string.search_no_results))

                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    item {
                        DetailHeader(
                            state = state,
                            title = stringResource(R.string.all_songs),
                            subtitle = stringResource(R.string.track_count, visible.size),
                            coverItem = visible.firstOrNull(),
                            onPlay = { state.player.play(visible, 0) },
                            onShuffle = { state.player.playShuffled(visible) },
                        )
                    }
                    sections.forEach { (key, entries) ->
                        if (key != null) {
                            item(key = "header-$key") {
                                LetterHeader(key)
                            }
                        }
                        itemsIndexed(entries, key = { _, entry -> entry.value.id }) { _, entry ->
                            val item = entry.value
                            val id = item.id
                            TrackRow(
                                item = item,
                                index = entry.index + 1,
                                artworkUrl = state.artworkUrl(item, 120),
                                onClick = { state.player.play(visible, entry.index) },
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
                                selectable = selecting,
                                selected = selected.containsKey(id),
                                onSelect = {
                                    if (selected.containsKey(id)) {
                                        selected.remove(id)
                                    } else {
                                        selected[id] = item
                                    }
                                },
                            )
                        }
                    }
                    if (loading) {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }

            if (sectionPositions.size > 3) {
                Column(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(end = 2.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    sectionPositions.forEach { (key, position) ->
                        Text(
                            key.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable {
                                    scope.launch { listState.scrollToItem(position) }
                                }
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }

        if (selecting) {
            SelectionBar(
                allItems = visible,
                selected = selected,
                onSelectAll = { visible.forEach { selected[it.id] = it } },
                onQueue = { selected.values.forEach { state.player.addToQueue(it) } },
                onPlaylist = { state.addToPlaylistBatch = selected.values.toList() },
                onDownload = {
                    state.api?.let { api ->
                        selected.values.forEach { state.downloads.download(api, it) }
                    }
                },
            )
        }
    }
}

@Composable
private fun LetterHeader(key: Char) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            key.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

private fun sortClient(
    list: List<BaseItem>,
    sort: AllSongsSort,
    descending: Boolean,
): List<BaseItem> {
    if (sort == AllSongsSort.RECENT) return list
    val collator = java.text.Collator.getInstance(java.util.Locale.CHINA)
    val sorted = list.sortedWith(
        compareBy(collator) {
            if (sort == AllSongsSort.ARTIST) it.trackArtist ?: it.name else it.name
        },
    )
    return if (descending) sorted.reversed() else sorted
}

@Composable
private fun SelectionBar(
    allItems: List<BaseItem>,
    selected: Map<String, BaseItem>,
    onSelectAll: () -> Unit,
    onQueue: () -> Unit,
    onPlaylist: () -> Unit,
    onDownload: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onSelectAll) {
                Text(stringResource(R.string.select_all))
            }
            TextButton(onClick = onQueue, enabled = selected.isNotEmpty()) {
                Text(stringResource(R.string.add_to_queue))
            }
            TextButton(onClick = onPlaylist, enabled = selected.isNotEmpty()) {
                Text(stringResource(R.string.add_to_playlist))
            }
            TextButton(onClick = onDownload, enabled = selected.isNotEmpty()) {
                Text(stringResource(R.string.download))
            }
        }
    }
}

private const val PAGE_SIZE = 100
