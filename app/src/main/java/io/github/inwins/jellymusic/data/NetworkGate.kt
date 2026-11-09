package io.github.inwins.jellymusic.data

/**
 * Process-wide offline flag checked by the API client, image loader and
 * background prefetching. Set by [io.github.inwins.jellymusic.AppState].
 */
object NetworkGate {
    @Volatile
    var offline: Boolean = false
}
