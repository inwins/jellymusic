package com.example.jellymusic.ui

import androidx.activity.compose.BackHandler
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.core.content.ContextCompat
import com.example.jellymusic.util.LyricsPoster
import com.example.jellymusic.util.MusicTheory
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.example.jellymusic.AppState
import com.example.jellymusic.R
import com.example.jellymusic.data.AudioFeatures
import com.example.jellymusic.jellyfin.LyricLine
import com.example.jellymusic.player.MusicPlayer
import com.example.jellymusic.player.Metronome
import com.example.jellymusic.player.PlayMode
import kotlin.math.roundToInt
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.jellymusic.util.formatDuration

@Composable
fun MiniPlayerBar(state: AppState) {
    val player = state.player
    val current = player.current ?: return
    val fraction = if (player.durationMs > 0) {
        (player.positionMs.toFloat() / player.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    Surface(
        tonalElevation = 6.dp,
        shadowElevation = 2.dp,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column {
            LinearProgressIndicator(
                progress = fraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { state.navigate(Screen.NowPlaying) }
                    .pointerInput(Unit) {
                        var total = 0f
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                total += dragAmount
                            },
                            onDragEnd = {
                                if (total < -60f) state.navigate(Screen.NowPlaying)
                                total = 0f
                            },
                            onDragCancel = { total = 0f },
                        )
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(state.artworkUrl(current, 160), 44.dp, 8.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        current.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        current.trackArtist.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { player.toggle() }) {
                    Icon(
                        if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.play_pause),
                    )
                }
                IconButton(onClick = { player.next() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.next))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(state: AppState) {
    val player = state.player
    val current = player.current ?: return

    var lyricsFullScreen by rememberSaveable { mutableStateOf(false) }
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    var showQueue by remember { mutableStateOf(false) }
    var queueQuery by remember { mutableStateOf("") }
    var metronomeOn by remember { mutableStateOf(false) }
    val metronome = remember { Metronome() }
    DisposableEffect(Unit) {
        onDispose { metronome.stop() }
    }
    var moreMenuOpen by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showAbDialog by remember { mutableStateOf(false) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }

    val lyrics by produceState<List<LyricLine>?>(null, current.id) {
        value = try {
            state.api?.getLyrics(current.id) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    val sliderPosition = dragPosition ?: player.positionMs.toFloat()
    val duration = player.durationMs.coerceAtLeast(1L)
    val downloaded = state.downloads.isDownloaded(current.id)
    val downloadProgress = state.downloads.progress[current.id]
    val downloadError = state.downloads.errors[current.id] != null
    val favorite = state.favorites.isFavorite(current)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        pageCount = { if (state.settings.visualizerEnabled) 3 else 2 },
    )
    val artUrl = state.artworkUrl(current, 800)
    val backgroundUrl = state.settings.customBackgroundUri ?: artUrl
    val lyricsOffset = state.settings.lyricsOffsetFor(current.id)
    val paletteAccent by produceState<Color?>(initialValue = null, artUrl) {
        val url = artUrl ?: return@produceState
        value = withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val request = coil.request.ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .build()
                val result = coil.Coil.imageLoader(context).execute(request)
                val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                bitmap?.let { bmp ->
                    Palette.from(bmp).generate().vibrantSwatch?.rgb?.let { Color(it) }
                }
            }.getOrNull()
        }
    }
    val keyAccent = state.audioFeatures.get(current.id)?.let {
        Color.hsv(MusicTheory.camelotHue(it.keyIndex, it.minor), 0.55f, 0.95f)
    }
    val accent = paletteAccent ?: keyAccent ?: MaterialTheme.colorScheme.primary
    val recordPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            state.settings.visualizerEnabled = true
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.visualizer_permission),
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    LaunchedEffect(
        state.settings.visualizerEnabled,
        player.isPlaying,
        player.current?.id,
    ) {
        if (state.settings.visualizerEnabled && player.isPlaying) {
            state.visualizer.start(player.audioSessionId)
        } else {
            state.visualizer.stop()
        }
    }
    DisposableEffect(Unit) {
        onDispose { state.visualizer.stop() }
    }
    val downloadLabel = when {
        downloadProgress != null -> R.string.cancel_download
        downloaded -> R.string.delete_download
        downloadError -> R.string.retry_download
        else -> R.string.download
    }

    Box(Modifier.fillMaxSize()) {
        if (backgroundUrl != null) {
            AsyncImage(
                model = backgroundUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(60.dp)
                    .alpha(0.30f),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.45f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.78f),
                            MaterialTheme.colorScheme.background,
                        ),
                    ),
                ),
        )

        if (lyricsFullScreen) {
            FullScreenLyrics(
                title = current.name,
                lyrics = lyrics,
                positionMs = player.positionMs,
                offsetMs = lyricsOffset,
                fontScale = state.settings.lyricsFontScale,
                onOffsetChange = { state.settings.setLyricsOffset(current.id, it) },
                onFontScaleChange = { state.settings.lyricsFontScale = it },
                onClose = { lyricsFullScreen = false },
                onSeek = { player.seekTo(it) },
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp),
            )
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    IconButton(
                        onClick = { state.back() },
                        modifier = Modifier.align(Alignment.CenterStart),
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.collapse),
                        )
                    }
                    Text(
                        stringResource(R.string.now_playing),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    Box(Modifier.align(Alignment.CenterEnd)) {
                        IconButton(onClick = { moreMenuOpen = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.more),
                            )
                        }
                        DropdownMenu(
                            expanded = moreMenuOpen,
                            onDismissRequest = { moreMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(downloadLabel)) },
                                leadingIcon = {
                                    if (downloadProgress != null) {
                                        CircularProgressIndicator(
                                            progress = if (downloadProgress < 0f) 0f else downloadProgress,
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        Icon(
                                            if (downloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                                            contentDescription = null,
                                        )
                                    }
                                },
                                onClick = {
                                    moreMenuOpen = false
                                    when {
                                        downloadProgress != null -> state.downloads.cancel(current)
                                        downloaded -> state.downloads.delete(current)
                                        downloadError -> state.api?.let { state.downloads.retry(it, current) }
                                        else -> state.api?.let { state.downloads.download(it, current) }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.add_to_playlist)) },
                                leadingIcon = {
                                    Icon(Icons.Default.PlaylistAdd, contentDescription = null)
                                },
                                onClick = {
                                    moreMenuOpen = false
                                    state.addToPlaylistItem = current
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sleep_timer)) },
                                onClick = {
                                    moreMenuOpen = false
                                    showSleepDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.speed) + ": " +
                                            stringResource(
                                                R.string.speed_value,
                                                formatSpeed(player.playbackSpeed),
                                            ),
                                    )
                                },
                                onClick = {
                                    moreMenuOpen = false
                                    showSpeedDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ab_repeat)) },
                                trailingIcon = {
                                    if (player.repeatA != null) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                },
                                onClick = {
                                    moreMenuOpen = false
                                    showAbDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.visualizer_style)) },
                                onClick = {
                                    moreMenuOpen = false
                                    state.settings.visualizerStyle =
                                        (state.settings.visualizerStyle + 1) % 3
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.skip_intro)) },
                                onClick = {
                                    moreMenuOpen = false
                                    val intro = state.audioFeatures.get(current.id)?.introEndMs
                                    if (intro != null && intro > 0L) {
                                        player.seekTo(intro)
                                        if (!player.isPlaying) player.toggle()
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.lyrics_poster)) },
                                onClick = {
                                    moreMenuOpen = false
                                    scope.launch {
                                        val line = currentLyricLine(lyrics, player.positionMs)
                                        val file = LyricsPoster.generate(
                                            context = context,
                                            coverUrl = artUrl,
                                            title = current.name,
                                            artist = current.trackArtist.orEmpty(),
                                            lyric = line,
                                            accent = accent.toArgb(),
                                        )
                                        if (file != null) LyricsPoster.share(context, file)
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.metronome)) },
                                trailingIcon = {
                                    if (metronomeOn) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                },
                                onClick = {
                                    moreMenuOpen = false
                                    metronomeOn = !metronomeOn
                                    if (metronomeOn) {
                                        val bpm = state.audioFeatures.get(current.id)?.bpm
                                            ?.roundToInt()
                                            ?.takeIf { it > 0 } ?: 120
                                        metronome.start(bpm)
                                    } else {
                                        metronome.stop()
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.highlight)) },
                                onClick = {
                                    moreMenuOpen = false
                                    val start = state.audioFeatures.get(current.id)?.highlightStartMs
                                    if (start != null && start > 0L) {
                                        player.seekTo(start)
                                        if (!player.isPlaying) player.toggle()
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.instant_mix)) },
                                onClick = {
                                    moreMenuOpen = false
                                    state.api?.let { api ->
                                        scope.launch {
                                            val mix = runCatching {
                                                api.getInstantMix(current.id)
                                            }.getOrDefault(emptyList())
                                            if (mix.isNotEmpty()) {
                                                state.player.play(mix, 0)
                                            }
                                        }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.audio_features)) },
                                onClick = {
                                    moreMenuOpen = false
                                    state.navigate(Screen.AudioFeatures(current))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.visualizer)) },
                                trailingIcon = {
                                    if (state.settings.visualizerEnabled) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                },
                                onClick = {
                                    moreMenuOpen = false
                                    if (state.settings.visualizerEnabled) {
                                        state.settings.visualizerEnabled = false
                                        state.visualizer.stop()
                                    } else if (
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO,
                                        ) == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        state.settings.visualizerEnabled = true
                                    } else {
                                        recordPermission.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.desktop_lyrics)) },
                                trailingIcon = {
                                    if (state.lyricsOverlay.enabled) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                },
                                onClick = {
                                    moreMenuOpen = false
                                    if (state.lyricsOverlay.enabled) {
                                        state.lyricsOverlay.hide()
                                    } else if (Settings.canDrawOverlays(context)) {
                                        state.toggleDesktopLyrics()
                                    } else {
                                        runCatching {
                                            context.startActivity(
                                                Intent(
                                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                    Uri.parse("package:${context.packageName}"),
                                                ),
                                            )
                                        }
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.desktop_lyrics_permission),
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                },
                            )
                        }
                    }
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 44.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            current.name,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        val artist = current.trackArtist?.takeIf { it.isNotBlank() }
                        val album = current.album?.takeIf { it.isNotBlank() }
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (artist != null) {
                                Text(
                                    artist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .clickable {
                                            state.searchRequest = artist
                                            state.navigate(Screen.Search)
                                        },
                                )
                            }
                            if (artist != null && album != null) {
                                Text(
                                    " · ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (album != null) {
                                Text(
                                    album,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .clickable {
                                            state.searchRequest = album
                                            state.navigate(Screen.Search)
                                        },
                                )
                            }
                        }
                        state.audioFeatures.get(current.id)?.let { f ->
                            Text(
                                "${f.bpm.roundToInt()} BPM · ${AudioFeatures.keyName(f)} · " +
                                    stringResource(R.string.energy_label) +
                                    " ${(f.energy * 100).roundToInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    Box(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .clip(CircleShape)
                            .combinedClickable(
                                onClick = { state.favorites.toggle(current) },
                                onLongClick = { state.addToPlaylistItem = current },
                            )
                            .padding(12.dp),
                    ) {
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
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) { page ->
                    when (page) {
                        0 -> CoverPage(
                            artUrl = artUrl,
                            bands = state.visualizer.bands.value,
                            accent = accent,
                            showRing = state.settings.visualizerEnabled &&
                                state.settings.visualizerStyle != 0,
                            showBars = state.settings.visualizerEnabled &&
                                state.settings.visualizerStyle != 1,
                            gesturesEnabled = state.settings.gesturesEnabled,
                            circle = state.settings.coverShape == 1,
                            isPlaying = player.isPlaying,
                            onTogglePlay = { player.toggle() },
                            onNext = { player.next() },
                            onPrevious = { player.previous() },
                        ) {
                            scope.launch { pagerState.animateScrollToPage(1) }
                        }

                        2 -> VisualizerPage(
                            bands = state.visualizer.bands.value,
                            accent = accent,
                        )

                        else -> Column(Modifier.fillMaxSize()) {
                            val currentChord = state.audioFeatures.get(current.id)?.chords
                                ?.lastOrNull { it.startMs <= player.positionMs }
                                ?.name
                            if (currentChord != null) {
                                Text(
                                    MusicTheory.transposeChord(
                                        currentChord,
                                        state.settings.pitchSemitones,
                                    ),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = accent,
                                    modifier = Modifier.padding(start = 16.dp),
                                )
                            }
                            LyricsView(
                            lyrics = lyrics,
                            positionMs = player.positionMs,
                            offsetMs = lyricsOffset,
                            fontScale = state.settings.lyricsFontScale,
                            fullScreen = false,
                            onExpand = { lyricsFullScreen = true },
                            onSeekLine = {},
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            )
                        }
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    repeat(pagerState.pageCount) { index ->
                        val selected = pagerState.currentPage == index
                        Box(
                            Modifier
                                .size(32.dp)
                                .clickable {
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(if (selected) 8.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                        },
                                    ),
                            )
                        }
                    }
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                ) {
                    val bpm = state.audioFeatures.get(current.id)?.bpm
                        ?.takeIf { it > 0f } ?: 120f
                    val beatMs = 60_000f / bpm
                    val beatIndex = ((player.positionMs / beatMs).toInt()) % 4
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(4) { index ->
                            Box(
                                Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(if (index == beatIndex) 7.dp else 5.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (index == beatIndex) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                        },
                                    ),
                            )
                        }
                    }
                    Slider(
                        value = sliderPosition.coerceIn(0f, duration.toFloat()),
                        onValueChange = { dragPosition = it },
                        valueRange = 0f..duration.toFloat(),
                        onValueChangeFinished = {
                            player.seekTo((dragPosition ?: 0f).toLong())
                            dragPosition = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            formatDuration(sliderPosition.toLong()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            formatDuration(duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    val sleepEnd = player.sleepTimerEndsAt
                    val sleepRemaining = sleepEnd?.let { (it - nowMs).coerceAtLeast(0L) }
                    val pointA = player.repeatA
                    val pointB = player.repeatB
                    if (sleepRemaining != null || player.sleepAtEndOfTrack ||
                        player.playbackSpeed != 1f || (pointA != null && pointB != null)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (sleepRemaining != null && sleepRemaining > 0L) {
                                Text(
                                    stringResource(R.string.sleep_remaining, formatDuration(sleepRemaining)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else if (player.sleepAtEndOfTrack) {
                                Text(
                                    stringResource(R.string.sleep_end_of_track_active),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (player.playbackSpeed != 1f) {
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    stringResource(R.string.speed_value, formatSpeed(player.playbackSpeed)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (pointA != null && pointB != null) {
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "A-B ${formatDuration(pointA)}–${formatDuration(pointB)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val modeIcon = when (player.playMode) {
                            PlayMode.SEQUENTIAL -> Icons.Default.PlaylistPlay
                            PlayMode.LIST_LOOP -> Icons.Default.Repeat
                            PlayMode.SINGLE_LOOP -> Icons.Default.RepeatOne
                            PlayMode.SHUFFLE -> Icons.Default.Shuffle
                        }
                        val modeLabel = when (player.playMode) {
                            PlayMode.SEQUENTIAL -> R.string.play_mode_sequential
                            PlayMode.LIST_LOOP -> R.string.play_mode_list_loop
                            PlayMode.SINGLE_LOOP -> R.string.play_mode_single_loop
                            PlayMode.SHUFFLE -> R.string.play_mode_shuffle
                        }
                        IconButton(
                            onClick = {
                                player.cyclePlayMode()
                                Toast.makeText(
                                    context,
                                    context.getString(modeLabel),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                modeIcon,
                                contentDescription = stringResource(modeLabel),
                                tint = if (player.playMode == PlayMode.SEQUENTIAL) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        IconButton(onClick = { player.previous() }, modifier = Modifier.size(52.dp)) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = stringResource(R.string.previous),
                                modifier = Modifier.size(36.dp),
                            )
                        }
                        IconButton(
                            onClick = { player.toggle() },
                            modifier = Modifier
                                .size(64.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                        ) {
                            Icon(
                                if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.play_pause),
                                modifier = Modifier.size(38.dp),
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        IconButton(onClick = { player.next() }, modifier = Modifier.size(52.dp)) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = stringResource(R.string.next),
                                modifier = Modifier.size(36.dp),
                            )
                        }
                        IconButton(onClick = { showQueue = true }, modifier = Modifier.size(44.dp)) {
                            Icon(
                                Icons.Default.QueueMusic,
                                contentDescription = stringResource(R.string.queue),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }

    if (showQueue) {
        ModalBottomSheet(
            onDismissRequest = { showQueue = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                Modifier
                    .fillMaxHeight(0.72f)
                    .padding(horizontal = 8.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.queue),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp),
                    )
                    TextButton(onClick = { player.shuffleUpcoming() }) {
                        Text(stringResource(R.string.shuffle_remaining))
                    }
                    TextButton(onClick = {
                        val playlist = state.localPlaylists.playlists
                            .firstOrNull { it.name == "播放队列" }
                            ?: state.localPlaylists.create("播放队列")
                        player.queue.forEach { state.localPlaylists.addItem(playlist.id, it) }
                        Toast.makeText(
                            context,
                            context.getString(R.string.sync_done),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }) {
                        Text(stringResource(R.string.save_as_playlist))
                    }
                }
                OutlinedTextField(
                    value = queueQuery,
                    onValueChange = { queueQuery = it },
                    placeholder = { Text(stringResource(R.string.search_queue)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
                QueueList(
                    player = player,
                    query = queueQuery,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (showSleepDialog) {
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            title = { Text(stringResource(R.string.sleep_timer)) },
            text = {
                Column {
                    TextButton(onClick = {
                        player.clearSleepTimer()
                        showSleepDialog = false
                    }) {
                        Text(stringResource(R.string.sleep_off))
                    }
                    listOf(15, 30, 60, 90).forEach { minutes ->
                        TextButton(onClick = {
                            player.setSleepTimerMinutes(minutes)
                            showSleepDialog = false
                        }) {
                            Text(stringResource(R.string.sleep_minutes, minutes))
                        }
                    }
                    TextButton(onClick = {
                        player.setSleepAtEndOfTrack()
                        showSleepDialog = false
                    }) {
                        Text(stringResource(R.string.sleep_end_of_track))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSleepDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text(stringResource(R.string.speed)) },
            text = {
                Column {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { value ->
                        TextButton(onClick = {
                            player.changePlaybackSpeed(value)
                        }) {
                            Text(
                                stringResource(R.string.speed_value, formatSpeed(value)),
                                color = if (player.playbackSpeed == value) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.pitch) + ": " +
                            if (state.settings.pitchSemitones > 0) {
                                "+${state.settings.pitchSemitones}"
                            } else {
                                state.settings.pitchSemitones.toString()
                            } + " st",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.Center) {
                        listOf(-5, -2, -1, 0, 1, 2, 5).forEach { semitones ->
                            TextButton(onClick = { player.changePitchSemitones(semitones) }) {
                                Text(if (semitones > 0) "+$semitones" else "$semitones")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showAbDialog) {
        AlertDialog(
            onDismissRequest = { showAbDialog = false },
            title = { Text(stringResource(R.string.ab_repeat)) },
            text = {
                Column {
                    TextButton(onClick = {
                        player.setRepeatPointA()
                        showAbDialog = false
                    }) {
                        Text(stringResource(R.string.ab_set_a))
                    }
                    TextButton(onClick = {
                        player.setRepeatPointB()
                        showAbDialog = false
                    }) {
                        Text(stringResource(R.string.ab_set_b))
                    }
                    TextButton(onClick = {
                        player.clearRepeatPoints()
                        showAbDialog = false
                    }) {
                        Text(stringResource(R.string.ab_clear))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun CoverPage(
    artUrl: String?,
    bands: FloatArray,
    accent: Color,
    showRing: Boolean,
    showBars: Boolean,
    gesturesEnabled: Boolean,
    circle: Boolean,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShowLyrics: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val side = minOf(maxWidth * 0.82f, maxHeight * 0.9f, 360.dp)
            val rotation = remember { Animatable(0f) }
            LaunchedEffect(isPlaying, circle) {
                if (circle && isPlaying) {
                    while (true) {
                        rotation.animateTo(
                            rotation.value + 360f,
                            tween(24_000, easing = LinearEasing),
                        )
                    }
                }
            }
            Box(
                Modifier.size(side * 1.5f),
                contentAlignment = Alignment.Center,
            ) {
                if (showRing) {
                    SpectrumRing(
                        bands = bands,
                        accent = accent,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    Modifier
                        .size(side)
                        .graphicsLayer { rotationZ = if (circle) rotation.value else 0f }
                        .shadow(18.dp, if (circle) CircleShape else RoundedCornerShape(20.dp))
                        .clip(if (circle) CircleShape else RoundedCornerShape(20.dp))
                        .pointerInput(gesturesEnabled) {
                            if (!gesturesEnabled) return@pointerInput
                            detectTapGestures(
                                onTap = { onShowLyrics() },
                                onDoubleTap = { onTogglePlay() },
                            )
                        }
                        .pointerInput(gesturesEnabled) {
                            if (!gesturesEnabled) return@pointerInput
                            var total = 0f
                            detectVerticalDragGestures(
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    total += dragAmount
                                },
                                onDragEnd = {
                                    if (total < -80f) {
                                        onNext()
                                    } else if (total > 80f) {
                                        onPrevious()
                                    }
                                    total = 0f
                                },
                                onDragCancel = { total = 0f },
                            )
                        },
                ) {
                    Artwork(artUrl, side, if (circle) side / 2 else 20.dp)
                }
            }
        }
        if (showBars) {
            SpectrumBars(
                bands = bands,
                accent = accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(48.dp),
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun VisualizerPage(bands: FloatArray, accent: Color) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SpectrumRing(
            bands = bands,
            accent = accent,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )
        Spacer(Modifier.height(24.dp))
        SpectrumBars(
            bands = bands,
            accent = accent,
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp),
        )
    }
}

@Composable
private fun SpectrumRing(
    bands: FloatArray,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val count = bands.size
        if (count == 0) return@Canvas
        val center = Offset(size.width / 2f, size.height / 2f)
        val inner = size.minDimension / 3f
        val maxLength = inner * 0.5f
        for (i in 0 until count) {
            val angle = (i.toFloat() / count) * 2f * PI.toFloat() - (PI / 2).toFloat()
            val magnitude = bands.getOrElse(i) { 0f }.coerceIn(0f, 1f)
            val length = 3f + magnitude * maxLength
            val startX = center.x + cos(angle) * inner
            val startY = center.y + sin(angle) * inner
            val endX = center.x + cos(angle) * (inner + length)
            val endY = center.y + sin(angle) * (inner + length)
            drawLine(
                color = accent.copy(alpha = 0.25f + 0.75f * magnitude),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = 4f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun SpectrumBars(
    bands: FloatArray,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        for (i in bands.indices) {
            val magnitude = bands[i].coerceIn(0f, 1f)
            Box(
                Modifier
                    .weight(1f)
                    .height((magnitude * 46).dp + 2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent.copy(alpha = 0.3f + 0.7f * magnitude)),
            )
        }
    }
}

@Composable
private fun QueueList(
    player: MusicPlayer,
    modifier: Modifier = Modifier,
    query: String = "",
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    var targetIndex by remember { mutableStateOf<Int?>(null) }
    var didScroll by remember { mutableStateOf(false) }

    if (query.isNotBlank()) {
        val filtered = player.queue.withIndex().filter { entry ->
            entry.value.name.contains(query, true) ||
                entry.value.trackArtist?.contains(query, true) == true
        }
        LazyColumn(state = listState, modifier = modifier) {
            itemsIndexed(filtered) { _, entry ->
                val item = entry.value
                val isCurrent = entry.index == player.playIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { player.jumpTo(entry.index) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
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
                    Text(
                        item.durationMs?.let { formatDuration(it) }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        return
    }

    LaunchedEffect(player.current?.id, player.queue.size) {
        if (!didScroll && player.queue.isNotEmpty()) {
            val index = player.queue.indexOfFirst { it.id == player.current?.id }
            if (index >= 0) {
                listState.animateScrollToItem(index)
                didScroll = true
            }
        }
    }

    LazyColumn(state = listState, modifier = modifier) {
        itemsIndexed(player.queue) { index, item ->
            val isCurrent = index == player.playIndex
            val dragging = draggingIndex == index
            Row(
                Modifier
                    .fillMaxWidth()
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationY = if (dragging) dragDelta else 0f }
                    .background(
                        if (dragging) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            Color.Transparent
                        },
                    )
                    .clickable { player.jumpTo(index) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = if (isCurrent) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
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
                Spacer(Modifier.width(8.dp))
                Text(
                    item.durationMs?.let { formatDuration(it) }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = stringResource(R.string.drag_to_reorder),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .pointerInput(index, player.queue.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingIndex = index
                                    dragDelta = 0f
                                    targetIndex = index
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragDelta += amount.y
                                    val info = listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.index == index }
                                    if (info != null) {
                                        val viewportStart = listState.layoutInfo.viewportStartOffset
                                        val viewportEnd = listState.layoutInfo.viewportEndOffset
                                        val itemTop = info.offset + dragDelta
                                        val itemBottom = itemTop + info.size
                                        if (itemTop < viewportStart + 64) {
                                            scope.launch { listState.scrollBy(-24f) }
                                        } else if (itemBottom > viewportEnd - 64) {
                                            scope.launch { listState.scrollBy(24f) }
                                        }
                                        val center = info.offset + info.size / 2f + dragDelta
                                        val target = listState.layoutInfo.visibleItemsInfo
                                            .firstOrNull {
                                                center >= it.offset &&
                                                    center <= it.offset + it.size
                                            }
                                            ?.index
                                        if (target != null) targetIndex = target
                                    }
                                },
                                onDragEnd = {
                                    val from = draggingIndex
                                    val to = targetIndex
                                    draggingIndex = null
                                    dragDelta = 0f
                                    targetIndex = null
                                    if (from != null && to != null && from != to) {
                                        player.moveQueueItem(from, to)
                                    }
                                },
                                onDragCancel = {
                                    draggingIndex = null
                                    dragDelta = 0f
                                    targetIndex = null
                                },
                            )
                        },
                )
                IconButton(onClick = { player.removeFromQueue(index) }) {
                    Icon(
                        Icons.Default.RemoveCircleOutline,
                        contentDescription = stringResource(R.string.remove_from_queue),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FullScreenLyrics(
    title: String,
    lyrics: List<LyricLine>?,
    positionMs: Long,
    offsetMs: Long,
    fontScale: Float,
    onOffsetChange: (Long) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onClose: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onClose() }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.exit_fullscreen_lyrics))
            }
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(48.dp))
        }
        LyricsView(
            lyrics = lyrics,
            positionMs = positionMs,
            offsetMs = offsetMs,
            fontScale = fontScale,
            fullScreen = true,
            onExpand = {},
            onSeekLine = onSeek,
            modifier = Modifier.weight(1f),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onFontScaleChange(fontScale - 0.1f) }) {
                Text("A-")
            }
            TextButton(onClick = { onFontScaleChange(fontScale + 0.1f) }) {
                Text("A+")
            }
            TextButton(onClick = { onOffsetChange(offsetMs - 500) }) {
                Text("-0.5s")
            }
            TextButton(onClick = { onOffsetChange(offsetMs + 500) }) {
                Text("+0.5s")
            }
        }
        Text(
            stringResource(R.string.lyrics_offset_value, formatLyricsOffset(offsetMs)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LyricsView(
    lyrics: List<LyricLine>?,
    positionMs: Long,
    offsetMs: Long,
    fontScale: Float,
    fullScreen: Boolean,
    onExpand: () -> Unit,
    onSeekLine: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (lyrics == null) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        return
    }
    if (lyrics.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.no_lyrics),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    val synced = lyrics.any { it.startMs != null }
    val adjustedPosition = positionMs + offsetMs
    val currentIndex = remember(lyrics, adjustedPosition, synced) {
        if (!synced) -1 else {
            var index = -1
            lyrics.forEachIndexed { i, line ->
                val start = line.startMs ?: return@forEachIndexed
                if (start <= adjustedPosition) index = i
            }
            index
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    val currentSize = ((if (fullScreen) 30f else 24f) * fontScale).sp
    val otherSize = ((if (fullScreen) 22f else 18f) * fontScale).sp

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .let { if (fullScreen) it else it.clickable(onClick = onExpand) },
        verticalArrangement = Arrangement.spacedBy(if (fullScreen) 22.dp else 12.dp),
        contentPadding = PaddingValues(vertical = if (fullScreen) 140.dp else 16.dp),
    ) {
        itemsIndexed(lyrics) { index, line ->
            val isCurrent = index == currentIndex
            Text(
                line.text,
                fontSize = if (isCurrent) currentSize else otherSize,
                lineHeight = (if (isCurrent) currentSize else otherSize) * 1.35f,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .let { base ->
                        if (fullScreen && line.startMs != null) {
                            base.clickable { line.startMs?.let(onSeekLine) }
                        } else {
                            base
                        }
                    }
                    .padding(vertical = 2.dp),
            )
        }
    }
}

private fun formatSpeed(speed: Float): String =
    if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()

private fun formatLyricsOffset(offsetMs: Long): String =
    "%+.1f".format(offsetMs / 1000.0)

private fun currentLyricLine(lyrics: List<LyricLine>?, positionMs: Long): String {
    if (lyrics.isNullOrEmpty()) return ""
    val synced = lyrics.filter { it.startMs != null }
    if (synced.isEmpty()) return lyrics.first().text
    var current = synced.first()
    for (line in synced) {
        if ((line.startMs ?: 0L) <= positionMs) current = line else break
    }
    return current.text
}
