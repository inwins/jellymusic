package io.github.inwins.jellymusic.data

import io.github.inwins.jellymusic.jellyfin.BaseItem

internal val ALLOWED_DOWNLOAD_EXTENSIONS = setOf(
    "mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "wma", "ape", "alac", "m4b",
)

internal const val STALE_PART_AGE_MS = 5 * 60 * 1000L

private const val MAX_READABLE_BASE_LENGTH = 60

private val INVALID_FILE_CHARS = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]")
private val WHITESPACE = Regex("\\s+")

internal fun sanitizeFileNamePart(raw: String): String =
    raw.replace(INVALID_FILE_CHARS, "_").replace(WHITESPACE, " ").trim()

internal fun downloadFileName(item: BaseItem): String {
    val ext = item.container
        ?.substringBefore(',')
        ?.substringBefore(' ')
        ?.lowercase()
        ?.takeIf { it in ALLOWED_DOWNLOAD_EXTENSIONS }
        ?: "audio"
    val artist = item.primaryArtist?.let { sanitizeFileNamePart(it) }?.takeIf { it.isNotEmpty() }
    val title = sanitizeFileNamePart(item.name).ifEmpty { "Track" }
    val base = buildString {
        if (artist != null) append(artist).append(" - ")
        append(title)
    }
        .take(MAX_READABLE_BASE_LENGTH)
        .trim()
        .trimEnd('.', ' ')
        .ifBlank { "Track" }
    return "$base [${item.id}].$ext"
}

internal fun legacyDownloadFileName(item: BaseItem): String {
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
