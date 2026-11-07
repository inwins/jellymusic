package io.github.inwins.jellymusic.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.inwins.jellymusic.AppState
import io.github.inwins.jellymusic.R

@Composable
fun OfflineAlbumScreen(state: AppState, screen: Screen.OfflineAlbum) {
    val tracks = remember(screen.key, state.downloads.downloadedIds.size, state.localMusic.songs.size) {
        state.offlineAlbumTracks(screen.key)
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(screen.name, onBack = { state.back() })
        if (tracks.isEmpty()) {
            EmptyHint(stringResource(R.string.no_downloaded_songs))
            return@Column
        }
        RegisterSelectableItems(tracks)
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                DetailHeader(
                    state = state,
                    title = screen.name,
                    subtitle = stringResource(R.string.track_count, tracks.size),
                    coverItem = tracks.firstOrNull(),
                    onPlay = { state.player.play(tracks, 0) },
                    onShuffle = { state.player.playShuffled(tracks) },
                )
            }
            itemsIndexed(tracks) { index, track ->
                val id = track.id
                TrackRow(
                    item = track,
                    index = index + 1,
                    artworkUrl = state.artworkUrl(track, 120),
                    onClick = { state.player.play(tracks, index) },
                    showArtwork = true,
                    isCurrent = state.player.current?.id == id,
                    favorite = state.favorites.isFavorite(track),
                    onToggleFavorite = { state.favorites.toggle(track) },
                    downloaded = state.downloads.downloadedIds[id] == true,
                    cached = state.streamCache.isCached(id),
                    onLongClick = { state.trackMenu = track },
                )
            }
        }
    }
}

@Composable
fun OfflineArtistScreen(state: AppState, screen: Screen.OfflineArtist) {
    val tracks = remember(screen.name, state.downloads.downloadedIds.size, state.localMusic.songs.size) {
        state.offlineArtistTracks(screen.name)
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(screen.name, onBack = { state.back() })
        if (tracks.isEmpty()) {
            EmptyHint(stringResource(R.string.no_downloaded_songs))
            return@Column
        }
        RegisterSelectableItems(tracks)
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                DetailHeader(
                    state = state,
                    title = screen.name,
                    subtitle = stringResource(R.string.track_count, tracks.size),
                    coverItem = tracks.firstOrNull(),
                    onPlay = { state.player.play(tracks, 0) },
                    onShuffle = { state.player.playShuffled(tracks) },
                )
            }
            itemsIndexed(tracks) { index, track ->
                val id = track.id
                TrackRow(
                    item = track,
                    index = index + 1,
                    artworkUrl = state.artworkUrl(track, 120),
                    onClick = { state.player.play(tracks, index) },
                    showArtwork = true,
                    isCurrent = state.player.current?.id == id,
                    favorite = state.favorites.isFavorite(track),
                    onToggleFavorite = { state.favorites.toggle(track) },
                    downloaded = state.downloads.downloadedIds[id] == true,
                    cached = state.streamCache.isCached(id),
                    onLongClick = { state.trackMenu = track },
                )
            }
        }
    }
}
