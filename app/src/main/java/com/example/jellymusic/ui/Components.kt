package com.example.jellymusic.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.jellymusic.R
import com.example.jellymusic.jellyfin.BaseItem
import com.example.jellymusic.util.formatDuration

sealed class LoadResult<out T> {
    data class Success<T>(val data: T) : LoadResult<T>()
    data class Error(val e: Exception) : LoadResult<Nothing>()
}

@Composable
fun <T> Loaded(
    key: Any,
    load: suspend () -> T,
    content: @Composable (T) -> Unit,
) {
    var refresh by remember { mutableIntStateOf(0) }
    val result by produceState<LoadResult<T>?>(null, key, refresh) {
        value = try {
            LoadResult.Success(load())
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
    when (val r = result) {
        null -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        is LoadResult.Error -> Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.loading_failed), color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(4.dp))
            Text(
                r.e.message ?: stringResource(R.string.unknown_error),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { refresh++ }) {
                Text(stringResource(R.string.retry))
            }
        }

        is LoadResult.Success -> content(r.data)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
fun FillArtwork(url: String?, modifier: Modifier = Modifier, cornerRadius: Dp = 14.dp) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxSize(0.35f),
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
fun Artwork(url: String?, size: Dp, cornerRadius: Dp = 8.dp) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.size(size * 0.4f),
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    item: BaseItem,
    index: Int,
    artworkUrl: String?,
    onClick: () -> Unit,
    trackLabel: String? = null,
    showArtwork: Boolean = false,
    isCurrent: Boolean = false,
    favorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    downloaded: Boolean = false,
    cached: Boolean = false,
    downloading: Float? = null,
    downloadError: Boolean = false,
    onDownload: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    selectable: Boolean = false,
    selected: Boolean = false,
    onSelect: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selectable) onSelect?.invoke() else onClick() },
                onLongClick = onLongClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectable) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onSelect?.invoke() },
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        if (showArtwork) {
            Artwork(artworkUrl, 44.dp)
            Spacer(Modifier.width(12.dp))
        } else {
            Box(
                Modifier.width(if (trackLabel != null) 36.dp else 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    trackLabel ?: index.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                item.trackArtist.orEmpty(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onToggleFavorite != null) {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = stringResource(
                        if (favorite) R.string.unfavorite else R.string.favorite,
                    ),
                    tint = if (favorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (onDownload != null) {
            IconButton(onClick = onDownload) {
                when {
                    downloading != null -> CircularProgressIndicator(
                        progress = if (downloading < 0f) 0f else downloading,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )

                    downloaded -> Icon(
                        Icons.Default.DownloadDone,
                        contentDescription = stringResource(R.string.downloaded),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )

                    cached && !downloadError -> Icon(
                        Icons.Default.CloudDone,
                        contentDescription = stringResource(R.string.cached_tap_to_download),
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp),
                    )

                    else -> Icon(
                        Icons.Default.Download,
                        contentDescription = stringResource(
                            if (downloadError) R.string.download_error else R.string.download,
                        ),
                        tint = if (downloadError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.RemoveCircleOutline,
                    contentDescription = stringResource(R.string.remove_from_playlist),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Text(
            item.durationMs?.let { formatDuration(it) }.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun CardItem(
    item: BaseItem,
    artworkUrl: String?,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        FillArtwork(artworkUrl, Modifier.fillMaxWidth(), cornerRadius = 14.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            item.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (subtitle != null) {
            Text(
                subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun PlayIconButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
    ) {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = stringResource(R.string.play),
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(36.dp),
        )
    }
}
private val indexKeyCache = HashMap<String, Char>()

fun alphabetIndexKey(name: String): Char {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return '#'
    indexKeyCache[trimmed]?.let { return it }
    val first = trimmed.first()
    val key = if (first in 'A'..'Z' || first in 'a'..'z') {
        first.uppercaseChar()
    } else {
        runCatching {
            val transliterator = android.icu.text.Transliterator
                .getInstance("Han-Latin; Latin-ASCII")
            transliterator.transliterate(first.toString())
                .firstOrNull { it in 'A'..'Z' || it in 'a'..'z' }
                ?.uppercaseChar()
        }.getOrNull() ?: '#'
    }
    if (indexKeyCache.size > 4000) indexKeyCache.clear()
    indexKeyCache[trimmed] = key
    return key
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(java.util.Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(java.util.Locale.US, "%.1f MB", mb)
    return String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0)
}
