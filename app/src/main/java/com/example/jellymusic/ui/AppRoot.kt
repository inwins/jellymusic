package com.example.jellymusic.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.jellymusic.AppState

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
        }
    }
    GlobalDialogs(state)
}