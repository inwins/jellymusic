package io.github.inwins.jellymusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.inwins.jellymusic.AppState
import io.github.inwins.jellymusic.R
import io.github.inwins.jellymusic.util.TonePlayer

private val INTERVALS = listOf(
    2 to "M2", 3 to "m3", 4 to "M3", 5 to "P4",
    7 to "P5", 9 to "M6", 11 to "M7", 12 to "P8",
)

private val CHORDS = listOf(
    "Major" to intArrayOf(0, 4, 7),
    "Minor" to intArrayOf(0, 3, 7),
    "Dim" to intArrayOf(0, 3, 6),
    "Aug" to intArrayOf(0, 4, 8),
)

@Composable
fun EarTrainingScreen(state: AppState) {
    var mode by remember { mutableIntStateOf(0) }
    var correct by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<String?>(null) }
    var score by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    val tonePlayer = remember { TonePlayer() }
    DisposableEffect(Unit) { onDispose { tonePlayer.stop() } }

    fun playQuestion(answer: String) {
        if (mode == 0) {
            val semitones = INTERVALS.firstOrNull { it.second == answer }?.first ?: 4
            tonePlayer.play(listOf(60, 60 + semitones), 600)
        } else {
            val triad = CHORDS.firstOrNull { it.first == answer }?.second ?: intArrayOf(0, 4, 7)
            tonePlayer.playChords(listOf(triad.map { 60 + it }), 900)
        }
    }

    fun newQuestion() {
        picked = null
        correct = if (mode == 0) {
            INTERVALS.random().second
        } else {
            CHORDS.random().first
        }
        correct?.let { playQuestion(it) }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.ear_training), onBack = { state.back() })
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = mode == 0,
                onClick = { mode = 0; correct = null; picked = null },
                label = { Text(stringResource(R.string.interval)) },
            )
            FilterChip(
                selected = mode == 1,
                onClick = { mode = 1; correct = null; picked = null },
                label = { Text(stringResource(R.string.chord)) },
            )
        }
        Text(
            stringResource(R.string.score) + ": $score / $total",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { newQuestion() }) {
                Text(if (correct == null) stringResource(R.string.start) else stringResource(R.string.next_question))
            }
            if (correct != null) {
                Button(onClick = { correct?.let { playQuestion(it) } }) {
                    Text(stringResource(R.string.play))
                }
            }
        }
        val options = if (mode == 0) INTERVALS.map { it.second } else CHORDS.map { it.first }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(options) { option ->
                val isCorrect = option == correct
                val isPicked = option == picked
                Card(
                    Modifier.fillMaxWidth(),
                ) {
                    Button(
                        onClick = {
                            if (correct == null) return@Button
                            picked = option
                            total++
                            if (isCorrect) score++
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                    ) {
                        Text(
                            when {
                                isPicked && isCorrect -> option + " ✓"
                                isPicked -> option + " ✗"
                                picked != null && isCorrect -> option + " ✓"
                                else -> option
                            },
                        )
                    }
                }
            }
        }
    }
}
