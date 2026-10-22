package com.example.jellymusic.data

import android.net.Uri

internal fun streamCacheKey(uri: Uri): String {
    val segments = uri.pathSegments
    val audioIndex = segments.indexOf("Audio")
    val id = if (audioIndex >= 0) segments.getOrNull(audioIndex + 1) else null
    val host = "${uri.host}:${uri.port}"
    return if (id != null) "$host/$id" else uri.toString()
}
