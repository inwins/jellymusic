package com.example.jellymusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import android.widget.Toast
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jellymusic.AppState
import com.example.jellymusic.R
import com.example.jellymusic.data.LocalPlaylist
import com.example.jellymusic.jellyfin.BaseItem
import kotlinx.coroutines.launch

private data class HomeData(
    val latest: List<BaseItem>,
    val favorites: List<BaseItem>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: AppState) {
    val tab = state.homeTab
    val tabs = listOf(
        stringResource(R.string.tab_home),
        stringResource(R.string.tab_albums),
        stringResource(R.string.tab_artists),
        stringResource(R.string.tab_playlists),
        stringResource(R.string.tab_downloads),
    )

    Column(Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.app_name)) },
            actions = {
                IconButton(onClick = { state.navigate(Screen.Folders) }) {
                    Icon(Icons.Default.Folder, contentDescription = stringResource(R.string.folders))
                }
                IconButton(onClick = { state.navigate(Screen.Search) }) {
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                }
                IconButton(onClick = { state.navigate(Screen.Settings) }) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )
        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = tab == index,
                    onClick = { state.homeTab = index },
                    text = { Text(title) },
                )
            }
        }
        if (state.isOffline) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    stringResource(R.string.offline_banner),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
        when (tab) {
            0 -> HomeTab(state)
            1 -> AlbumsTab(state)
            2 -> ArtistsTab(state)
            3 -> PlaylistsTab(state)
            4 -> DownloadsTab(state)
        }
    }
}

@Composable
fun HomeTab(state: AppState) {
    var refresh by remember { mutableIntStateOf(0) }

    if (state.isOffline) {
        OfflineHome(state, onRetry = { refresh++ })
        return
    }

    // Render cached content immediately; check connectivity in the background.
    LaunchedEffect(refresh, state.refreshSignal.value) {
        state.checkServer(force = refresh > 0)
        state.syncOfflineChanges()
    }

    Loaded(key = "home-$refresh", load = {
            val api = state.requireApi()
            HomeData(api.getLatest(30), api.getFavorites())
        }) { data ->
            val favorites = state.favorites.mergeFavorites(data.favorites)
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionTitle(stringResource(R.string.recently_added))
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { state.navigate(Screen.AllSongs) }) {
                            Text(stringResource(R.string.all_songs))
                        }
                        TextButton(onClick = { state.navigate(Screen.Genres) }) {
                            Text(stringResource(R.string.genres))
                        }
                    }
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(data.latest) { item ->
                            Column(
                                Modifier
                                    .width(140.dp)
                                    .clickable {
                                        state.player.play(data.latest, data.latest.indexOf(item))
                                    },
                            ) {
                                Artwork(state.artworkUrl(item, 400), 140.dp)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    item.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    item.trackArtist.orEmpty(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                item {
                    SectionTitle(stringResource(R.string.favorite_songs))
                }
                itemsIndexed(favorites) { index, item ->
                    val id = item.id
                    TrackRow(
                        item = item,
                        index = index + 1,
                        artworkUrl = state.artworkUrl(item, 120),
                        onClick = { state.player.play(favorites, index) },
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

@Composable
fun OfflineHome(state: AppState, onRetry: () -> Unit) {
    val manualOffline = state.settings.manualOffline
    val downloadedMap = state.downloads.downloadedIds
    val items = remember(downloadedMap.toMap()) { state.downloads.downloadedItems() }

    if (items.isEmpty()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (manualOffline) {
                Text(
                    stringResource(R.string.no_downloaded_songs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    stringResource(R.string.cannot_reach_server),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.no_downloaded_songs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            if (manualOffline) {
                SectionTitle(stringResource(R.string.downloaded_count, items.size))
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.offline_showing_downloads),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
            }
        }
        itemsIndexed(items) { index, item ->
            val id = item.id
            TrackRow(
                item = item,
                index = index + 1,
                artworkUrl = state.artworkUrl(item, 120),
                onClick = { state.player.play(items, index) },
                showArtwork = true,
                isCurrent = state.player.current?.id == id,
                favorite = state.favorites.isFavorite(item),
                onToggleFavorite = { state.favorites.toggle(item) },
                downloaded = true,
                downloading = state.downloads.progress[id],
                downloadError = state.downloads.errors[id] != null,
                cached = state.streamCache.isCached(id),
                onDownload = { state.downloads.delete(item) },
                onLongClick = { state.trackMenu = item },
            )
        }
    }
}

private enum class AlbumSort(
    val sortBy: String,
    val sortOrder: String,
    val labelRes: Int,
) {
    NAME("SortName", "Ascending", R.string.sort_name),
    RECENT("DateCreated", "Descending", R.string.sort_recent),
    YEAR("ProductionYear", "Descending", R.string.sort_year),
}

@Composable
fun AlbumsTab(state: AppState) {
    if (state.isOffline) {
        EmptyHint(stringResource(R.string.offline_hint))
        return
    }

    val items = remember { mutableStateListOf<BaseItem>() }
    var sort by remember { mutableStateOf(AlbumSort.NAME) }
    var startIndex by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sortMenu by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    suspend fun loadNext() {
        if (loading || endReached) return
        loading = true
        try {
            val page = state.requireApi().getAlbums(
                startIndex = startIndex,
                limit = LIST_PAGE_SIZE,
                sortBy = sort.sortBy,
                sortOrder = sort.sortOrder,
            )
            if (page.isEmpty()) {
                endReached = true
            } else {
                items.addAll(page)
                startIndex += page.size
                if (page.size < LIST_PAGE_SIZE) endReached = true
            }
            error = null
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    LaunchedEffect(sort) {
        items.clear()
        startIndex = 0
        endReached = false
        error = null
        loadNext()
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= items.size - 6
        }
    }
    LaunchedEffect(shouldLoadMore, items.size) {
        if (shouldLoadMore && items.isNotEmpty()) {
            loadNext()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                TextButton(onClick = { sortMenu = true }) {
                    Text(
                        stringResource(R.string.sort) + ": " + stringResource(sort.labelRes),
                    )
                }
                DropdownMenu(
                    expanded = sortMenu,
                    onDismissRequest = { sortMenu = false },
                ) {
                    AlbumSort.values().forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(option.labelRes)) },
                            onClick = {
                                sort = option
                                sortMenu = false
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            }
        }

        if (items.isEmpty() && error != null) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(error ?: "", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { scope.launch { loadNext() } }) {
                    Text(stringResource(R.string.retry))
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.id }) { album ->
                    CardItem(
                        item = album,
                        artworkUrl = state.artworkUrl(album, 400),
                        subtitle = album.albumArtist,
                        onClick = { state.navigate(Screen.Album(album.id, album.name)) },
                    )
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
                if (error != null) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            TextButton(onClick = { scope.launch { loadNext() } }) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistsTab(state: AppState) {
    if (state.isOffline) {
        EmptyHint(stringResource(R.string.offline_hint))
        return
    }

    val items = remember { mutableStateListOf<BaseItem>() }
    var startIndex by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    suspend fun loadNext() {
        if (loading || endReached) return
        loading = true
        try {
            val page = state.requireApi().getArtists(
                startIndex = startIndex,
                limit = LIST_PAGE_SIZE,
            )
            if (page.isEmpty()) {
                endReached = true
            } else {
                items.addAll(page)
                startIndex += page.size
                if (page.size < LIST_PAGE_SIZE) endReached = true
            }
            error = null
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        loadNext()
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= items.size - 6
        }
    }
    LaunchedEffect(shouldLoadMore, items.size) {
        if (shouldLoadMore && items.isNotEmpty()) {
            loadNext()
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (loading) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
        if (items.isEmpty() && error != null) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(error ?: "", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { scope.launch { loadNext() } }) {
                    Text(stringResource(R.string.retry))
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.id }) { artist ->
                    CardItem(
                        item = artist,
                        artworkUrl = state.artworkUrl(artist, 400),
                        subtitle = stringResource(R.string.artist),
                        onClick = { state.navigate(Screen.Artist(artist.id, artist.name)) },
                    )
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
                if (error != null) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            TextButton(onClick = { scope.launch { loadNext() } }) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistsTab(state: AppState) {
    var serverPlaylists by remember { mutableStateOf<List<BaseItem>?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        serverPlaylists = try {
            state.requireApi().getPlaylists()
        } catch (_: Exception) {
            emptyList()
        }
    }

    val locals = state.localPlaylists.playlists

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.local_playlists),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        scope.launch {
                            val ok = state.syncOfflineChanges()
                            Toast.makeText(
                                context,
                                if (ok) {
                                    context.getString(R.string.sync_done)
                                } else {
                                    state.localPlaylists.lastError
                                        ?: context.getString(R.string.sync_failed)
                                },
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                    enabled = !state.syncing.value,
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.sync_now),
                    )
                }
                TextButton(onClick = { showCreate = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.new_playlist))
                }
            }
        }
        if (locals.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.no_local_playlists),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        } else {
            items(locals, key = { "local-${it.id}" }) { playlist ->
                LocalPlaylistRow(
                    state = state,
                    playlist = playlist,
                    onClick = { state.navigate(Screen.LocalPlaylist(playlist.id)) },
                )
            }
        }

        item { SectionTitle(stringResource(R.string.tab_playlists)) }

        when (val server = serverPlaylists) {
            null -> item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }

            else -> if (server.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_playlists),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            } else {
                items(server, key = { "server-${it.id}" }) { playlist ->
                    ServerPlaylistRow(
                        state = state,
                        playlist = playlist,
                        onClick = { state.navigate(Screen.Playlist(playlist.id, playlist.name)) },
                    )
                }
            }
        }
    }

    if (showCreate) {
        CreatePlaylistDialog(
            onCreate = { name ->
                val playlist = state.localPlaylists.create(name)
                showCreate = false
                state.navigate(Screen.LocalPlaylist(playlist.id))
            },
            onDismiss = { showCreate = false },
        )
    }
}

@Composable
private fun LocalPlaylistRow(
    state: AppState,
    playlist: LocalPlaylist,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            playlist.items.firstOrNull()?.let { state.artworkUrl(it, 160) },
            56.dp,
            8.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                playlist.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (playlist.pendingSync) {
                Text(
                    stringResource(R.string.pending_sync),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            stringResource(R.string.track_count, playlist.items.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ServerPlaylistRow(
    state: AppState,
    playlist: BaseItem,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(state.artworkUrl(playlist, 160), 56.dp, 8.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            playlist.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        playlist.childCount?.let { count ->
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.track_count, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
fun DownloadsTab(state: AppState) {
    var showCached by rememberSaveable { mutableStateOf(false) }
    val downloadedMap = state.downloads.downloadedIds
    val revision = state.streamCache.revision.value
    val downloaded = remember(downloadedMap.toMap()) { state.downloads.downloadedItems() }
    val cached = remember(revision, downloadedMap.toMap()) {
        state.streamCache.cachedItems().filter { !state.downloads.isDownloaded(it.id) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = !showCached,
                onClick = { showCached = false },
                label = {
                    Text(
                        stringResource(R.string.downloaded_songs) + " (${downloaded.size})",
                    )
                },
            )
            FilterChip(
                selected = showCached,
                onClick = { showCached = true },
                label = {
                    Text(
                        stringResource(R.string.cached_songs) + " (${cached.size})",
                    )
                },
            )
        }

        val items = if (showCached) cached else downloaded
        if (items.isEmpty()) {
            EmptyHint(
                stringResource(
                    if (showCached) R.string.no_cached_songs else R.string.no_downloads_hint,
                ),
            )
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionTitle(
                        if (showCached) {
                            stringResource(R.string.cached_songs) + " " + items.size
                        } else {
                            stringResource(R.string.downloaded_count, items.size)
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    if (showCached) {
                        TextButton(
                            onClick = {
                                state.api?.let { api ->
                                    cached.forEach { state.downloads.download(api, it) }
                                }
                            },
                        ) {
                            Text(stringResource(R.string.download_all_cached))
                        }
                    }
                }
            }
            itemsIndexed(items) { index, item ->
                val id = item.id
                TrackRow(
                    item = item,
                    index = index + 1,
                    artworkUrl = state.artworkUrl(item, 120),
                    onClick = { state.player.play(items, index) },
                    showArtwork = true,
                    isCurrent = state.player.current?.id == id,
                    favorite = state.favorites.isFavorite(item),
                    onToggleFavorite = { state.favorites.toggle(item) },
                    downloaded = !showCached,
                    cached = showCached,
                    downloading = state.downloads.progress[id],
                    downloadError = state.downloads.errors[id] != null,
                    onDownload = {
                        if (showCached) {
                            state.api?.let { api -> state.downloads.download(api, item) }
                        } else {
                            state.downloads.delete(item)
                        }
                    },
                    onLongClick = { state.trackMenu = item },
                )
            }
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val LIST_PAGE_SIZE = 100
