package io.github.inwins.jellymusic.util

import io.github.inwins.jellymusic.jellyfin.LyricLine

private val LRC_TIME_TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

/**
 * Parses a local .lrc file into lyric lines. Supports multiple time tags per
 * line and one/two/three digit fractions. Lines without a time tag are kept as
 * unsynced lyrics, except metadata tags such as [ti:...] or [ar:...].
 */
fun parseLrc(content: String): List<LyricLine> {
    val result = mutableListOf<LyricLine>()
    content.lineSequence().forEach { raw ->
        val line = raw.trim()
        if (line.isEmpty()) return@forEach
        val tags = LRC_TIME_TAG.findAll(line).toList()
        if (tags.isEmpty()) {
            if (!line.startsWith("[")) {
                result.add(LyricLine(line, null))
            }
            return@forEach
        }
        val text = line.substring(tags.last().range.last + 1).trim()
        tags.forEach { tag ->
            val minutes = tag.groupValues[1].toLongOrNull() ?: 0L
            val seconds = tag.groupValues[2].toLongOrNull() ?: 0L
            val fraction = tag.groupValues[3]
            val millis = when {
                fraction.isNullOrEmpty() -> 0L
                fraction.length == 1 -> fraction.toLong() * 100L
                fraction.length == 2 -> fraction.toLong() * 10L
                else -> fraction.take(3).toLong()
            }
            result.add(LyricLine(text, minutes * 60_000L + seconds * 1_000L + millis))
        }
    }
    return result.sortedBy { it.startMs ?: Long.MAX_VALUE }
}
