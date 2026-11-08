package io.github.inwins.jellymusic.ui

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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.inwins.jellymusic.AppState
import io.github.inwins.jellymusic.R

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
    BackHandler(enabled = state.selection.selecting) {
        state.selection.clear()
    }

    val current = state.backStack.last()
    val saveableStateHolder = rememberSaveableStateHolder()
    LaunchedEffect(current) { state.selection.clear() }
    LaunchedEffect(state.homeTab) { state.selection.clear() }
    val player = state.player
    val showMiniPlayer = current != Screen.NowPlaying && player.current != null

    Surface(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalSelection provides state.selection) {
        Column(
            Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            Box(Modifier.weight(1f)) {
                saveableStateHolder.SaveableStateProvider(screenStateKey(current)) {
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
                    Screen.RunningRadio -> RunningRadioScreen(state)
                    Screen.Daily -> DailyScreen(state)
                    is Screen.OfflineAlbum -> OfflineAlbumScreen(state, current)
                    is Screen.OfflineArtist -> OfflineArtistScreen(state, current)
                    Screen.Practice -> PracticeScreen(state)
                    Screen.Theory -> TheoryScreen(state)
                    Screen.VocalTraining -> VocalTrainingScreen(state)
                    Screen.EarTraining -> EarTrainingScreen(state)
                    Screen.Tuner -> TunerScreen(state)
                    Screen.ChordLibrary -> ChordLibraryScreen(state)
                    Screen.ScaleVisualizer -> ScaleVisualizerScreen(state)
                    Screen.DrumMachine -> DrumMachineScreen(state)
                    Screen.Metronome -> MetronomeScreen(state)
                    is Screen.AudioFeatures -> AudioFeaturesScreen(state, current)
                    Screen.Genres -> GenresScreen(state)
                    Screen.Folders -> FoldersScreen(state)
                    is Screen.Folder -> FolderScreen(state, current)
                    is Screen.GenreSongs -> GenreSongsScreen(state, current)
                    Screen.Settings -> SettingsScreen(state)
                    Screen.Changelog -> ChangelogScreen(state)
                }
                }
            }
            if (state.selection.selecting) {
                SelectionBar(state)
            }
            if (showMiniPlayer) {
                MiniPlayerBar(state)
            }
            if (current == Screen.Home) {
                HomeBottomBar(state)
            }
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
private fun screenStateKey(screen: Screen): String = when (screen) {
    Screen.Home -> "home"
    is Screen.Album -> "album:${screen.id}"
    is Screen.Artist -> "artist:${screen.id}"
    is Screen.Playlist -> "playlist:${screen.id}"
    is Screen.LocalPlaylist -> "local-playlist:${screen.id}"
    Screen.Search -> "search"
    Screen.AllSongs -> "all-songs"
    Screen.Favorites -> "favorites"
    Screen.FeatureLibrary -> "feature-library"
    Screen.SmartPlaylists -> "smart-playlists"
    Screen.ListeningStats -> "listening-stats"
    Screen.RunningRadio -> "running-radio"
    Screen.Daily -> "daily"
    is Screen.OfflineAlbum -> "offline-album:${screen.key}"
    is Screen.OfflineArtist -> "offline-artist:${screen.name}"
    Screen.Practice -> "practice"
    Screen.Theory -> "theory"
    Screen.VocalTraining -> "vocal-training"
    Screen.EarTraining -> "ear-training"
    Screen.Tuner -> "tuner"
    Screen.ChordLibrary -> "chord-library"
    Screen.ScaleVisualizer -> "scale-visualizer"
    Screen.DrumMachine -> "drum-machine"
    Screen.Metronome -> "metronome"
    is Screen.AudioFeatures -> "audio-features:${screen.item.id}"
    Screen.Genres -> "genres"
    Screen.Folders -> "folders"
    is Screen.Folder -> "folder:${screen.id}"
    is Screen.GenreSongs -> "genre-songs:${screen.id}"
    Screen.Settings -> "settings"
    Screen.Changelog -> "changelog"
    Screen.NowPlaying -> "now-playing"
}
