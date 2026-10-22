package com.example.jellymusic.data

import com.example.jellymusic.jellyfin.BaseItem

internal val ALLOWED_DOWNLOAD_EXTENSIONS = setOf(
    "mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "wma", "ape", "alac", "m4b",
)

internal const val STALE_PART_AGE_MS = 5 * 60 * 1000L

internal fun downloadFileName(item: BaseItem): String {
    val ext = item.container
        ?.substringBefore(',')
        ?.substringBefore(' ')
        ?.lowercase()
        ?.takeIf { it in ALLOWED_DOWNLOAD_EXTENSIONS }
        ?: "audio"
    return "${item.id}.$ext"
}

internal fun isStalePartFile(lastModified: Long, now: Long = System.currentTimeMillis()): Boolean =
    now - lastModified > STALE_PART_AGE_MS
