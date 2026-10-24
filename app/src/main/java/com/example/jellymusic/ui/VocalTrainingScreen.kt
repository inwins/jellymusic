package com.example.jellymusic.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.jellymusic.AppState
import com.example.jellymusic.R
import com.example.jellymusic.util.MicPitch
import com.example.jellymusic.util.MusicTheory
import com.example.jellymusic.util.TonePlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun VocalTrainingScreen(state: AppState) {
    var mode by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.vocal_training), onBack = { state.back() })
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val labels = listOf(
                R.string.range_test,
                R.string.sustain,
                R.string.call_response,
                R.string.warmup,
                R.string.rhythm_tap,
            )
            itemsIndexed(labels) { index, label ->
                FilterChip(
                    selected = mode == index,
                    onClick = { mode = index },
                    label = { Text(stringResource(label)) },
                )
            }
        }
        when (mode) {
            0 -> RangeTestContent()
            1 -> SustainContent()
            2 -> CallResponseContent()
            3 -> WarmupContent()
            else -> RhythmTapContent()
        }
    }
}

@Composable
private fun micStarter(onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) onGranted() }
    return {
        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            onGranted()
        } else {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

private fun midiOf(hz: Float): Float =
    69f + 12f * (ln(hz / 440f) / ln(2f))

@Composable
private fun RangeTestContent() {
    var running by remember { mutableStateOf(false) }
    var current by remember { mutableIntStateOf(0) }
    var low by remember { mutableIntStateOf(0) }
    var high by remember { mutableIntStateOf(0) }
    val requestMic = micStarter { running = true }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            MicPitch.loop { pitch ->
                if (pitch > 0f) {
                    val midi = midiOf(pitch).roundToInt()
                    current = midi
                    if (low == 0 || midi < low) low = midi
                    if (high == 0 || midi > high) high = midi
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.current_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            if (current > 0) MusicTheory.midiName(current) else "-",
            fontSize = 64.sp,
        )
        Text(
            stringResource(R.string.your_range) + ": " +
                if (low > 0) MusicTheory.midiName(low) + " – " + MusicTheory.midiName(high) else "-",
            style = MaterialTheme.typography.titleMedium,
        )
        if (low > 0 && high > 0) {
            Text(
                stringResource(R.string.voice_type) + ": " + voiceType(low, high),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(
            Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { if (running) running = false else requestMic() }) {
                Text(stringResource(if (running) R.string.stop else R.string.start))
            }
            TextButton(onClick = {
                running = false
                current = 0
                low = 0
                high = 0
            }) {
                Text(stringResource(R.string.restart))
            }
        }
    }
}

@Composable
private fun SustainContent() {
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val samples = remember { mutableStateListOf<Float>() }
    val requestMic = micStarter { running = true }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        samples.clear()
        result = null
        withContext(Dispatchers.IO) {
            MicPitch.loop { pitch ->
                if (pitch > 0f) samples.add(pitch)
            }
        }
    }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        delay(10_000)
        running = false
    }
    LaunchedEffect(running, samples.size) {
        if (!running && samples.isNotEmpty() && result == null) {
            val average = samples.average().toFloat()
            val midi = midiOf(average)
            val stable = samples.count { abs(midiOf(it) - midi) <= 0.5f }
            val stability = (stable * 100 / samples.size).coerceIn(0, 100)
            val seconds = samples.size * 0.093f
            result = String.format(java.util.Locale.US, "%.1f", seconds) + "s · " +
                "稳定度 " + stability + "%"
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.hold_note), style = MaterialTheme.typography.titleMedium)
        Text(
            result ?: if (running) "..." else "-",
            fontSize = 40.sp,
            modifier = Modifier.padding(vertical = 24.dp),
        )
        Button(onClick = { if (running) running = false else requestMic() }) {
            Text(stringResource(if (running) R.string.stop else R.string.start))
        }
    }
}

@Composable
private fun CallResponseContent() {
    var stage by remember { mutableIntStateOf(0) } // 0 idle, 1 playing, 2 recording, 3 done
    var target by remember { mutableStateOf<List<Int>>(emptyList()) }
    var detected by remember { mutableStateOf<List<Int>>(emptyList()) }
    var score by remember { mutableIntStateOf(0) }
    val samples = remember { mutableStateListOf<Float>() }
    val recording = remember { mutableStateOf(false) }
    val tonePlayer = remember { TonePlayer() }
    val scope = rememberCoroutineScope()
    val requestMic = micStarter { }
    DisposableEffect(Unit) { onDispose { tonePlayer.stop() } }

    LaunchedEffect(recording.value) {
        if (!recording.value) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            MicPitch.loop { pitch -> if (pitch > 0f) samples.add(pitch) }
        }
    }

    fun start() {
        target = List(4) { (60..72).random() }
        detected = emptyList()
        score = 0
        samples.clear()
        stage = 1
        scope.launch {
            tonePlayer.play(target, 420)
            delay(4 * 420L + 700)
            samples.clear()
            recording.value = true
            stage = 2
            delay(5_000)
            recording.value = false
            delay(250)
            detected = segmentNotes(samples)
            score = detected.zip(target).count { (d, t) ->
                d > 0 && (abs(d - t) % 12 == 0)
            }
            stage = 3
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.listen_repeat), style = MaterialTheme.typography.titleMedium)
        if (target.isNotEmpty()) {
            Text(
                stringResource(R.string.target_notes) + ": " +
                    target.joinToString(" ") { MusicTheory.noteName(it) },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        if (detected.isNotEmpty()) {
            Text(
                stringResource(R.string.detected_notes) + ": " +
                    detected.joinToString(" ") {
                        if (it > 0) MusicTheory.noteName(it) else "-"
                    },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(R.string.matches, score, target.size),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (stage == 1 || stage == 2) {
            Text(
                if (stage == 1) "…" else stringResource(R.string.current_note) + ": …",
                fontSize = 32.sp,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
        Button(
            onClick = {
                if (stage == 0 || stage == 3) {
                    requestMic()
                    start()
                }
            },
            enabled = stage == 0 || stage == 3,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(stringResource(if (stage == 3) R.string.restart else R.string.start))
        }
    }
}

private fun segmentNotes(samples: List<Float>): List<Int> {
    if (samples.isEmpty()) return emptyList()
    val result = mutableListOf<Int>()
    var current = 0
    var count = 0
    for (sample in samples) {
        val midi = midiOf(sample).roundToInt()
        if (current == 0) {
            current = midi
            count = 1
        } else if (abs(midi - current) <= 1) {
            count++
        } else {
            if (count >= 3) result.add(current)
            current = midi
            count = 1
        }
        if (result.size >= 4) break
    }
    if (count >= 3 && result.size < 4) result.add(current)
    return result.take(4)
}

@Composable
private fun WarmupContent() {
    val tonePlayer = remember { TonePlayer() }
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    var step by remember { mutableIntStateOf(-1) }
    val exercises = listOf(
        "唇颤音 · C4 持续 5 秒",
        "五声音阶 · 上下行",
        "三和弦琶音 · C-E-G-C",
        "八度跳进 · C4-C5",
    )
    DisposableEffect(Unit) {
        onDispose {
            job?.cancel()
            tonePlayer.stop()
        }
    }

    fun start() {
        job?.cancel()
        job = scope.launch {
            step = 0
            tonePlayer.play(listOf(60), 5_000)
            delay(5_300)
            if (!isActive) return@launch
            step = 1
            val penta = listOf(60, 62, 64, 67, 69, 67, 64, 62, 60)
            tonePlayer.play(penta, 300)
            delay(300L * penta.size + 700)
            if (!isActive) return@launch
            step = 2
            val arpeggio = listOf(60, 64, 67, 72, 67, 64, 60)
            tonePlayer.play(arpeggio, 300)
            delay(300L * arpeggio.size + 700)
            if (!isActive) return@launch
            step = 3
            val octaves = listOf(60, 72, 60, 72, 60, 72, 60)
            tonePlayer.play(octaves, 260)
            delay(260L * octaves.size + 700)
            step = -1
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.warmup), style = MaterialTheme.typography.titleMedium)
                exercises.forEachIndexed { index, label ->
                    Text(
                        (index + 1).toString() + ". " + label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (step == index) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
        Row(
            Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { start() }) {
                Text(stringResource(R.string.start))
            }
            TextButton(onClick = {
                job?.cancel()
                tonePlayer.stop()
                step = -1
            }) {
                Text(stringResource(R.string.stop))
            }
        }
    }
}

@Composable
private fun RhythmTapContent() {
    val taps = remember { mutableStateListOf<Long>() }
    val context = LocalContext.current
    var metronomeOn by remember { mutableStateOf(false) }
    val metronome = remember { com.example.jellymusic.player.Metronome() }
    DisposableEffect(Unit) { onDispose { metronome.stop() } }
    LaunchedEffect(metronomeOn) {
        if (metronomeOn) metronome.start(90) else metronome.stop()
    }
    val intervals = remember(taps.size) {
        if (taps.size < 2) emptyList() else taps.zipWithNext { a, b -> b - a }
    }
    val average = if (intervals.isEmpty()) 0.0 else intervals.average()
    val deviation = if (intervals.size < 2) 0.0 else {
        sqrt(intervals.sumOf { (it - average) * (it - average) } / intervals.size)
    }
    val consistency = if (average > 0) {
        (100 - deviation / average * 100).coerceIn(0.0, 100.0).toInt()
    } else {
        0
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.tap_along), style = MaterialTheme.typography.titleMedium)
        Text(
            if (average > 0) {
                "${(60_000 / average).roundToInt()} BPM · " +
                    stringResource(R.string.tap_consistency, consistency)
            } else {
                "-"
            },
            fontSize = 28.sp,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        Button(
            onClick = { taps.add(System.currentTimeMillis()) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("TAP")
        }
        Row(
            Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = { taps.clear() }) {
                Text(stringResource(R.string.restart))
            }
            TextButton(onClick = { metronomeOn = !metronomeOn }) {
                Text(
                    stringResource(
                        if (metronomeOn) R.string.stop else R.string.metronome,
                    ),
                )
            }
        }
    }
}

private fun voiceType(low: Int, high: Int): String {
    val center = (low + high) / 2
    return when {
        center < 48 -> "男低音 Bass"
        center < 55 -> "男中音 Baritone"
        center < 60 -> "男高音 Tenor"
        center < 65 -> "女低音 Alto"
        center < 70 -> "女中音 Mezzo-soprano"
        else -> "女高音 Soprano"
    }
}
