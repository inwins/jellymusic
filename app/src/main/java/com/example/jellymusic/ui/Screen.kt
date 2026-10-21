package com.example.jellymusic.ui

sealed class Screen {
    data object Home : Screen()
    data class Album(val id: String, val name: String) : Screen()
    data class Artist(val id: String, val name: String) : Screen()
    data class Playlist(val id: String, val name: String) : Screen()
    data class LocalPlaylist(val id: String) : Screen()
    data object NowPlaying : Screen()
    data object Search : Screen()
    data object AllSongs : Screen()
    data object Genres : Screen()
    data class GenreSongs(val id: String, val name: String) : Screen()
    data object Settings : Screen()
    data object Changelog : Screen()
}