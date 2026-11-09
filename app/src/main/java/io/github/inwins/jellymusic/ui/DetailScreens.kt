package io.github.inwins.jellymusic.ui

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.inwins.jellymusic.AppState
import io.github.inwins.jellymusic.R
import io.github.inwins.jellymusic.jellyfin.BaseItem
import kotlinx.coroutines.launch

@Composable
fun AlbumScreen(state: AppState, screen: Screen.Album) {
    val listState = rememberLazyListState()
    val albumItem = remember(screen.id) {
        BaseItem(id = screen.id, name = screen.name, type = "MusicAlbum", isFolder = true)
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(screen.name, onBack = { state.back() }) {
            val favorite = state.favorites.isFavorite(albumItem)
            IconButton(onClick = { state.favorites.toggle(albumItem) }) {
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
        }
        Loaded(key = "album-${screen.id}", load = {
            state.requireApi().getAlbumTracks(screen.id)
        }) { loaded ->
            val tracks = loaded.sortedWith(
                compareBy<BaseItem>(
                    { it.discNumber ?: 1 },
                    { it.trackNumber ?: 0 },
                    { it.name },
                ),
            )
            RegisterSelectableItems(tracks)
            val multiDisc = tracks.any { (it.discNumber ?: 1) > 1 }
            var didScroll by remember { mutableStateOf(false) }
            LaunchedEffect(tracks.size, state.player.current?.id) {
                if (!didScroll && tracks.isNotEmpty()) {
                    val index = tracks.indexOfFirst { it.id == state.player.current?.id }
                    if (index >= 0) {
                        listState.animateScrollToItem(index + 1)
                        didScroll = true
                    }
                }
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                item {
                    DetailHeader(
                        state = state,
                        title = screen.name,
                        subtitle = tracks.firstOrNull()?.albumArtist,
                        coverItem = tracks.firstOrNull(),
                        onPlay = { state.player.play(tracks, 0) },
                        onShuffle = { state.player.playShuffled(tracks) },
                        onDownloadAll = {
                            state.api?.let { api -> state.downloads.downloadAll(api, tracks) }
                        },
                    )
                }
                itemsIndexed(tracks) { index, track ->
                    val id = track.id
                    TrackRow(
                        item = track,
                        index = index + 1,
                        artworkUrl = state.artworkUrl(track, 120),
                        trackLabel = if (multiDisc) {
                            "${track.discNumber ?: 1}-${
                                (track.trackNumber ?: (index + 1)).toString().padStart(2, '0')
                            }"
                        } else {
                            track.trackNumber?.toString()
                        },
                        onClick = { state.player.play(tracks, index) },
                        isCurrent = state.player.current?.id == id,
                        favorite = state.favorites.isFavorite(track),
                        onToggleFavorite = { state.favorites.toggle(track) },
                        downloaded = state.downloads.downloadedIds[id] == true,
                        downloading = state.downloads.progress[id],
                        downloadError = state.downloads.errors[id] != null,
                        cached = state.streamCache.isCached(id),
                        onDownload = {
                            state.api?.let { api -> state.downloads.download(api, track) }
                        },
                        onLongClick = { state.trackMenu = track },
                    )
                }
            }
        }
    }
}

private data class ArtistContent(
    val albums: List<BaseItem>,
    val songs: List<BaseItem>,
    val similarArtists: List<BaseItem> = emptyList(),
)

@Composable
fun ArtistScreen(state: AppState, screen: Screen.Artist) {
    val listState = rememberLazyListState()
    val artistItem = remember(screen.id) {
        BaseItem(id = screen.id, name = screen.name, type = "MusicArtist", isFolder = true)
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(screen.name, onBack = { state.back() }) {
            val favorite = state.favorites.isFavorite(artistItem)
            IconButton(onClick = { state.favorites.toggle(artistItem) }) {
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
        }
        Loaded(key = "artist-${screen.id}", load = {
            if (state.isOffline) {
                ArtistContent(
                    albums = emptyList(),
                    songs = state.offlineArtistTracks(screen.name),
                    similarArtists = emptyList(),
                )
            } else {
                val api = state.requireApi()
                val allSongs = mutableListOf<BaseItem>()
                var startIndex = 0
                while (allSongs.size < 5000) {
                    val page = api.getArtistSongs(screen.id, startIndex, 200)
                    allSongs.addAll(page)
                    if (page.size < 200) break
                    startIndex += page.size
                }
                ArtistContent(
                    albums = api.getArtistAlbums(screen.id),
                    similarArtists = runCatching {
                        api.getSimilarArtists(screen.id, 20)
                    }.getOrDefault(emptyList()),
                    songs = allSongs.sortedWith(
                        compareBy(
                            { it.album ?: "" },
                            { it.discNumber ?: 1 },
                            { it.trackNumber ?: 0 },
                            { it.name },
                        ),
                    ),
                )
            }
        }) { content ->
            val albums = content.albums
            val songs = content.songs
            val similarArtists = content.similarArtists
            var showAllSongs by remember(screen.id) { mutableStateOf(false) }
            var sortByAlbum by remember(screen.id) { mutableStateOf(true) }
            val orderedSongs = remember(songs, sortByAlbum) {
                if (sortByAlbum) {
                    songs
                } else {
                    val collator = java.text.Collator.getInstance(java.util.Locale.CHINA)
                    songs.sortedWith(compareBy(collator) { it.name })
                }
            }
            val visibleSongs = remember(orderedSongs, showAllSongs) {
                if (showAllSongs) orderedSongs else orderedSongs.take(50)
            }
            RegisterSelectableItems(visibleSongs)
            val songGroups = remember(visibleSongs, sortByAlbum) {
                if (sortByAlbum) {
                    visibleSongs.withIndex().groupBy { it.value.album ?: "" }
                } else {
                    emptyMap()
                }
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (similarArtists.isNotEmpty()) {
                    item {
                        Column {
                            SectionTitle(stringResource(R.string.related_artists))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                similarArtists.forEach { artist ->
                                    item(key = "similar-${artist.id}") {
                                        Column(
                                            Modifier
                                                .width(96.dp)
                                                .clickable {
                                                    state.navigate(
                                                        Screen.Artist(artist.id, artist.name),
                                                    )
                                                },
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            FillArtwork(
                                                state.artworkUrl(artist, 300),
                                                Modifier.size(96.dp),
                                                cornerRadius = 48.dp,
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                artist.name,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (songs.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SectionTitle(stringResource(R.string.songs) + " · " + songs.size)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { sortByAlbum = !sortByAlbum }) {
                                Text(
                                    stringResource(
                                        if (sortByAlbum) R.string.tab_albums else R.string.sort_name,
                                    ),
                                )
                            }
                            if (showAllSongs) {
                                TextButton(onClick = { showAllSongs = false }) {
                                    Text(stringResource(R.string.collapse))
                                }
                            }
                        }
                    }
                    if (sortByAlbum) {
                        songGroups.forEach { (album, entries) ->
                            if (album.isNotBlank()) {
                                item(key = "album-$album") { AlbumHeader(album) }
                            }
                            itemsIndexed(entries, key = { _, entry -> entry.value.id }) { _, entry ->
                                val song = entry.value
                                TrackRow(
                                    item = song,
                                    index = entry.index + 1,
                                    artworkUrl = state.artworkUrl(song, 120),
                                    onClick = { state.player.play(visibleSongs, entry.index) },
                                    showArtwork = true,
                                    isCurrent = state.player.current?.id == song.id,
                                    favorite = state.favorites.isFavorite(song),
                                    onToggleFavorite = { state.favorites.toggle(song) },
                                    downloaded = state.downloads.downloadedIds[song.id] == true,
                                    downloading = state.downloads.progress[song.id],
                                    downloadError = state.downloads.errors[song.id] != null,
                                    cached = state.streamCache.isCached(song.id),
                                    onDownload = {
                                        state.api?.let { api ->
                                            state.downloads.download(api, song)
                                        }
                                    },
                                    onLongClick = { state.trackMenu = song },
                                )
                            }
                        }
                    } else {
                        itemsIndexed(visibleSongs, key = { _, song -> song.id }) { index, song ->
                            TrackRow(
                                item = song,
                                index = index + 1,
                                artworkUrl = state.artworkUrl(song, 120),
                                onClick = { state.player.play(visibleSongs, index) },
                                showArtwork = true,
                                isCurrent = state.player.current?.id == song.id,
                                favorite = state.favorites.isFavorite(song),
                                onToggleFavorite = { state.favorites.toggle(song) },
                                downloaded = state.downloads.downloadedIds[song.id] == true,
                                downloading = state.downloads.progress[song.id],
                                downloadError = state.downloads.errors[song.id] != null,
                                cached = state.streamCache.isCached(song.id),
                                onDownload = {
                                    state.api?.let { api ->
                                        state.downloads.download(api, song)
                                    }
                                },
                                onLongClick = { state.trackMenu = song },
                            )
                        }
                    }
                    if (orderedSongs.size > 50) {
                        item {
                            TextButton(onClick = { showAllSongs = !showAllSongs }) {
                                if (showAllSongs) {
                                    Text(stringResource(R.string.collapse))
                                } else {
                                    Text(stringResource(R.string.show_all_count, orderedSongs.size))
                                }
                            }
                        }
                    }
                }
                if (albums.isNotEmpty()) {
                    item {
                        SectionTitle(stringResource(R.string.tab_albums))
                    }
                    albums.chunked(2).forEach { row ->
                        item {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                row.forEach { album ->
                                    Box(Modifier.weight(1f)) {
                                        CardItem(
                                            item = album,
                                            artworkUrl = state.artworkUrl(album, 400),
                                            subtitle = album.albumArtist,
                                            onClick = {
                                                state.navigate(
                                                    Screen.Album(album.id, album.name),
                                                )
                                            },
                                        )
                                    }
                                }
                                if (row.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistScreen(state: AppState, screen: Screen.Playlist) {
    Column(Modifier.fillMaxSize()) {
        TopBar(screen.name, onBack = { state.back() })
        if (state.isOffline) {
            EmptyHint(stringResource(R.string.offline_hint))
            return@Column
        }
        Loaded(key = "playlist-${screen.id}", load = {
            state.requireApi().getPlaylistTracks(screen.id)
        }) { tracks ->
            if (tracks.isEmpty()) {
                EmptyHint(stringResource(R.string.playlist_empty))
            } else {
                RegisterSelectableItems(tracks)
                val listState = rememberLazyListState()
                var didScroll by remember { mutableStateOf(false) }
                LaunchedEffect(tracks.size, state.player.current?.id) {
                    if (!didScroll && tracks.isNotEmpty()) {
                        val index = tracks.indexOfFirst { it.id == state.player.current?.id }
                        if (index >= 0) {
                            listState.animateScrollToItem(index + 1)
                            didScroll = true
                        }
                    }
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    item {
                        DetailHeader(
                            state = state,
                            title = screen.name,
                            subtitle = stringResource(R.string.track_count, tracks.size),
                            coverItem = tracks.firstOrNull(),
                            onPlay = { state.player.play(tracks, 0) },
                            onShuffle = { state.player.playShuffled(tracks) },
                            onDownloadAll = {
                                state.api?.let { api ->
                                    state.downloads.downloadAll(api, tracks)
                                }
                            },
                        )
                    }
                    itemsIndexed(tracks) { index, track ->
                        val id = track.id
                        TrackRow(
                            item = track,
                            index = index + 1,
                            artworkUrl = state.artworkUrl(track, 120),
                            onClick = { state.player.play(tracks, index) },
                            isCurrent = state.player.current?.id == id,
                            favorite = state.favorites.isFavorite(track),
                            onToggleFavorite = { state.favorites.toggle(track) },
                            downloaded = state.downloads.downloadedIds[id] == true,
                            downloading = state.downloads.progress[id],
                        downloadError = state.downloads.errors[id] != null,
                        cached = state.streamCache.isCached(id),
                            onDownload = {
                                state.api?.let { api -> state.downloads.download(api, track) }
                            },
                            onLongClick = { state.trackMenu = track },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LocalPlaylistScreen(state: AppState, screen: Screen.LocalPlaylist) {
    val playlist = state.localPlaylists.get(screen.id)
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val current = state.localPlaylists.get(screen.id) ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    current.items.forEach {
                        writer.write(it.name + " - " + it.trackArtist.orEmpty() + "\n")
                    }
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val lines = runCatching {
                    context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.readLines()
                        .orEmpty()
                }.getOrDefault(emptyList())
                val api = state.api ?: return@launch
                var added = 0
                for (line in lines) {
                    val query = line.substringBefore(" - ").trim()
                    if (query.isBlank()) continue
                    val match = runCatching { api.searchSongs(query, 1).firstOrNull() }
                        .getOrNull() ?: continue
                    if (state.localPlaylists.addItem(screen.id, match)) added++
                }
                Toast.makeText(context, "导入 $added 首", Toast.LENGTH_SHORT).show()
            }
        }
    }
    var renameValue by remember { mutableStateOf("") }

    if (playlist == null) {
        Column(Modifier.fillMaxSize()) {
            TopBar(stringResource(R.string.tab_playlists), onBack = { state.back() })
            EmptyHint(stringResource(R.string.no_playlists))
        }
        return
    }

    val items = playlist.items.toList()
    RegisterSelectableItems(items)
    Column(Modifier.fillMaxSize()) {
        TopBar(playlist.name, onBack = { state.back() })
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            if (playlist.pendingSync) {
                TextButton(
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
                    Text(stringResource(R.string.sync_now))
                }
            }
            TextButton(onClick = {
                renameValue = playlist.name
                showRename = true
            }) {
                Text(stringResource(R.string.rename))
            }
            TextButton(onClick = { exportLauncher.launch(playlist.name + ".txt") }) {
                Text(stringResource(R.string.playlist_export))
            }
            TextButton(onClick = { importLauncher.launch(arrayOf("text/plain")) }) {
                Text(stringResource(R.string.playlist_import))
            }
            TextButton(onClick = { state.localPlaylists.dedupe(screen.id) }) {
                Text(stringResource(R.string.playlist_dedupe))
            }
            TextButton(onClick = { showDelete = true }) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        }

        if (items.isEmpty()) {
            EmptyHint(stringResource(R.string.playlist_empty))
        } else {
            val listState = rememberLazyListState()
            var didScroll by remember { mutableStateOf(false) }
            LaunchedEffect(items.size, state.player.current?.id) {
                if (!didScroll && items.isNotEmpty()) {
                    val index = items.indexOfFirst { it.id == state.player.current?.id }
                    if (index >= 0) {
                        listState.animateScrollToItem(index + 1)
                        didScroll = true
                    }
                }
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                item {
                    DetailHeader(
                        state = state,
                        title = playlist.name,
                        subtitle = stringResource(R.string.track_count, items.size),
                        coverItem = items.firstOrNull(),
                        onPlay = { state.player.play(items, 0) },
                        onShuffle = { state.player.playShuffled(items) },
                    )
                }
                if (playlist.pendingSync) {
                    item {
                        Text(
                            stringResource(R.string.pending_sync),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
                itemsIndexed(items) { index, track ->
                    val id = track.id
                    TrackRow(
                        item = track,
                        index = index + 1,
                        artworkUrl = state.artworkUrl(track, 120),
                        onClick = { state.player.play(items, index) },
                        isCurrent = state.player.current?.id == id,
                        favorite = state.favorites.isFavorite(track),
                        onToggleFavorite = { state.favorites.toggle(track) },
                        downloaded = state.downloads.downloadedIds[id] == true,
                        downloading = state.downloads.progress[id],
                        downloadError = state.downloads.errors[id] != null,
                        cached = state.streamCache.isCached(id),
                        onRemove = { state.localPlaylists.removeItem(playlist.id, id) },
                        onLongClick = { state.trackMenu = track },
                    )
                }
            }
        }
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text(stringResource(R.string.rename)) },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    label = { Text(stringResource(R.string.playlist_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameValue.isNotBlank()) {
                            state.localPlaylists.rename(playlist.id, renameValue.trim())
                        }
                        showRename = false
                    },
                    enabled = renameValue.isNotBlank(),
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.delete_playlist_title)) },
            text = { Text(stringResource(R.string.delete_playlist_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.localPlaylists.delete(playlist.id)
                        showDelete = false
                        state.back()
                    },
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
fun DetailHeader(
    state: AppState,
    title: String,
    subtitle: String?,
    coverItem: BaseItem?,
    onPlay: () -> Unit,
    onShuffle: (() -> Unit)? = null,
    onDownloadAll: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(coverItem?.let { state.artworkUrl(it, 280) }, 140.dp, 12.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayIconButton(onClick = onPlay)
                if (onShuffle != null) {
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onShuffle) {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = stringResource(R.string.shuffle),
                        )
                    }
                }
                if (onDownloadAll != null) {
                    Spacer(Modifier.width(12.dp))
                    TextButton(onClick = onDownloadAll) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.download_all))
                    }
                }
            }
        }
    }
}
@Composable
private fun AlbumHeader(title: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}
