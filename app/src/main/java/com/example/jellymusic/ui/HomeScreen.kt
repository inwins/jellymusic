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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.jellymusic.AppState
import com.example.jellymusic.R
import com.example.jellymusic.data.LocalPlaylist
import com.example.jellymusic.jellyfin.BaseItem

private data class HomeData(
    val latest: List<BaseItem>,
    val favorites: List<BaseItem>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: AppState) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
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
                    onClick = { tab = index },
                    text = { Text(title) },
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
    var online by remember { mutableStateOf<Boolean?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(refresh, state.refreshSignal.value) {
        online = null
        val reachable = state.checkServer(force = refresh > 0)
        online = reachable
        if (reachable) {
            state.syncOfflineChanges()
        }
    }

    when (online) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        false -> OfflineHome(state, onRetry = { refresh++ })

        true -> Loaded(key = "home-$refresh", load = {
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
                                    item.primaryArtist.orEmpty(),
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
                        onDownload = {
                            state.api?.let { api -> state.downloads.download(api, item) }
                        },
                        onLongClick = { state.trackMenu = item },
                    )
                }
            }
        }
    }
}

@Composable
fun OfflineHome(state: AppState, onRetry: () -> Unit) {
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
            Text(stringResource(R.string.cannot_reach_server), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.no_downloaded_songs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
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
                onDownload = { state.downloads.delete(item) },
                onLongClick = { state.trackMenu = item },
            )
        }
    }
}

@Composable
fun AlbumsTab(state: AppState) {
    Loaded(key = "albums", load = { state.requireApi().getAlbums() }) { albums ->
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

@Composable
fun ArtistsTab(state: AppState) {
    Loaded(key = "artists", load = { state.requireApi().getArtists() }) { artists ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(artists, key = { it.id }) { artist ->
                CardItem(
                    item = artist,
                    artworkUrl = state.artworkUrl(artist, 400),
                    subtitle = stringResource(R.string.artist),
                    onClick = { state.navigate(Screen.Artist(artist.id, artist.name)) },
                )
            }
        }
    }
}

@Composable
fun PlaylistsTab(state: AppState) {
    var serverPlaylists by remember { mutableStateOf<List<BaseItem>?>(null) }
    var showCreate by remember { mutableStateOf(false) }

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
    val downloadedMap = state.downloads.downloadedIds
    val items = remember(downloadedMap.toMap()) { state.downloads.downloadedItems() }

    if (items.isEmpty()) {
        EmptyHint(stringResource(R.string.no_downloads_hint))
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            SectionTitle(stringResource(R.string.downloaded_count, items.size))
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
                onDownload = { state.downloads.delete(item) },
                onLongClick = { state.trackMenu = item },
            )
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