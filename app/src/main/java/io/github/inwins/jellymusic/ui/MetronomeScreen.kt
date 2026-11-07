package io.github.inwins.jellymusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.inwins.jellymusic.AppState
import io.github.inwins.jellymusic.R
import io.github.inwins.jellymusic.player.Metronome
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun MetronomeScreen(state: AppState) {
    var bpm by remember { mutableIntStateOf(90) }
    var beats by remember { mutableIntStateOf(4) }
    var subdivision by remember { mutableIntStateOf(1) }
    var running by remember { mutableStateOf(false) }
    var trainer by remember { mutableStateOf(false) }
    var lastTap by remember { mutableLongStateOf(0L) }
    var tapCount by remember { mutableIntStateOf(0) }
    val metronome = remember { Metronome() }
    DisposableEffect(Unit) { onDispose { metronome.stop() } }

    LaunchedEffect(running, bpm, beats, subdivision) {
        if (running) metronome.start(bpm, beats, subdivision) else metronome.stop()
    }
    LaunchedEffect(running, trainer) {
        if (!running || !trainer) return@LaunchedEffect
        while (isActive) {
            delay(((60_000f / bpm) * beats * 4).toLong())
            if (bpm < 200) bpm += 5
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopBar(stringResource(R.string.metronome), onBack = { state.back() })
        Text("$bpm BPM", fontSize = 48.sp, style = MaterialTheme.typography.displaySmall)
        Slider(
            value = bpm.toFloat(),
            onValueChange = { bpm = it.toInt() },
            valueRange = 40f..200f,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = beats == 4,
                onClick = { beats = 4 },
                label = { Text("4/4") },
            )
            FilterChip(
                selected = beats == 3,
                onClick = { beats = 3 },
                label = { Text("3/4") },
            )
        }
        Row(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(1 to "1/4", 2 to "1/8", 4 to "1/16").forEach { (value, label) ->
                FilterChip(
                    selected = subdivision == value,
                    onClick = { subdivision = value },
                    label = { Text(label) },
                )
            }
        }
        Row(
            Modifier.padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = { running = !running }) {
                Text(stringResource(if (running) R.string.stop else R.string.start))
            }
            TextButton(onClick = {
                val now = System.currentTimeMillis()
                if (lastTap > 0 && now - lastTap < 2000) {
                    val gap = now - lastTap
                    tapCount++
                    bpm = ((60_000.0 / gap).toInt()).coerceIn(40, 200)
                } else {
                    tapCount = 0
                }
                lastTap = now
            }) {
                Text(stringResource(R.string.tap_tempo))
            }
        }
        Row(
            Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.tempo_trainer))
            Switch(checked = trainer, onCheckedChange = { trainer = it })
        }
    }
}
