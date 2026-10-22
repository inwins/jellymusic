package com.example.jellymusic.ui

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.jellymusic.AppState
import com.example.jellymusic.R

@Composable
fun AppRoot(state: AppState) {
    if (!state.isLoggedIn.value) {
        LoginScreen(state)
    } else {
        MainScaffold(state)
    }
}

@Composable
fun MainScaffold(state: AppState) {
    BackHandler(enabled = state.backStack.size > 1) {
        state.back()
    }

    val current = state.backStack.last()
    val player = state.player
    val showMiniPlayer = current != Screen.NowPlaying && player.current != null

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            Box(Modifier.weight(1f)) {
                when (current) {
                    Screen.Home -> HomeScreen(state)
                    is Screen.Album -> AlbumScreen(state, current)
                    is Screen.Artist -> ArtistScreen(state, current)
                    is Screen.Playlist -> PlaylistScreen(state, current)
                    is Screen.LocalPlaylist -> LocalPlaylistScreen(state, current)
                    Screen.NowPlaying -> NowPlayingScreen(state)
                    Screen.Search -> SearchScreen(state)
                    Screen.AllSongs -> AllSongsScreen(state)
                    Screen.Favorites -> FavoritesScreen(state)
                    Screen.FeatureLibrary -> FeatureLibraryScreen(state)
                    Screen.SmartPlaylists -> SmartPlaylistScreen(state)
                    Screen.ListeningStats -> ListeningStatsScreen(state)
                    is Screen.AudioFeatures -> AudioFeaturesScreen(state, current)
                    Screen.Genres -> GenresScreen(state)
                    Screen.Folders -> FoldersScreen(state)
                    is Screen.Folder -> FolderScreen(state, current)
                    is Screen.GenreSongs -> GenreSongsScreen(state, current)
                    Screen.Settings -> SettingsScreen(state)
                    Screen.Changelog -> ChangelogScreen(state)
                }
            }
            if (showMiniPlayer) {
                MiniPlayerBar(state)
            }
            if (current == Screen.Home) {
                HomeBottomBar(state)
            }
        }
    }
    GlobalDialogs(state)
}

@Composable
private fun HomeBottomBar(state: AppState) {
    val items = listOf(
        Triple(Icons.Default.Home, R.string.tab_home, 0),
        Triple(Icons.Default.Album, R.string.tab_albums, 1),
        Triple(Icons.Default.Person, R.string.tab_artists, 2),
        Triple(Icons.Default.PlaylistPlay, R.string.tab_playlists, 3),
        Triple(Icons.Default.Download, R.string.tab_downloads, 4),
    )
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        items.forEach { (icon, label, index) ->
            NavigationBarItem(
                selected = state.homeTab == index,
                onClick = { state.homeTab = index },
                icon = { Icon(icon, contentDescription = stringResource(label)) },
                label = { Text(stringResource(label)) },
            )
        }
    }
}