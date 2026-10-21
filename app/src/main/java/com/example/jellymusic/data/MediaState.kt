package com.example.jellymusic.data

enum class MediaState {
    ONLINE,
    CACHED,
    DOWNLOADED,
}

fun mediaStateOf(downloaded: Boolean, cached: Boolean): MediaState = when {
    downloaded -> MediaState.DOWNLOADED
    cached -> MediaState.CACHED
    else -> MediaState.ONLINE
}
