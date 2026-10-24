package com.example.jellymusic.ui

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jellymusic.AppState
import com.example.jellymusic.R
import com.example.jellymusic.player.DrumMachine

@Composable
fun DrumMachineScreen(state: AppState) {
    var bpm by remember { mutableIntStateOf(100) }
    var beats by remember { mutableIntStateOf(4) }
    var running by remember { mutableStateOf(false) }
    val drum = remember { DrumMachine() }
    DisposableEffect(Unit) { onDispose { drum.stop() } }
    LaunchedEffect(running, bpm, beats) {
        if (running) drum.start(bpm, beats) else drum.stop()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopBar(stringResource(R.string.drum_machine), onBack = { state.back() })
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
                label = { Text("4/4 · 底鼓 1/3，军鼓 2/4") },
            )
            FilterChip(
                selected = beats == 3,
                onClick = { beats = 3 },
                label = { Text("3/4") },
            )
        }
        Button(
            onClick = { running = !running },
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(stringResource(if (running) R.string.stop else R.string.start))
        }
    }
}
