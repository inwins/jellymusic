package io.github.inwins.jellymusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.inwins.jellymusic.AppState
import io.github.inwins.jellymusic.R
import io.github.inwins.jellymusic.util.MusicTheory
import io.github.inwins.jellymusic.util.TonePlayer

private val PROGRESSIONS = listOf(
    "I–V–vi–IV" to intArrayOf(0, 4, 5, 3),
    "ii–V–I" to intArrayOf(1, 4, 0),
    "vi–IV–I–V" to intArrayOf(5, 3, 0, 4),
    "卡农" to intArrayOf(0, 4, 5, 2, 3, 0, 3, 4),
)

@Composable
fun PracticeScreen(state: AppState) {
    var mode by remember { mutableIntStateOf(0) }
    var root by remember { mutableIntStateOf(0) }
    var scaleIndex by remember { mutableIntStateOf(0) }
    var progressionIndex by remember { mutableIntStateOf(0) }
    var tempo by remember { mutableIntStateOf(90) }
    var arpeggio by remember { androidx.compose.runtime.mutableStateOf(false) }
    var randomDegrees by remember { androidx.compose.runtime.mutableStateOf<IntArray?>(null) }
    val tonePlayer = remember { TonePlayer() }
    DisposableEffect(Unit) { onDispose { tonePlayer.stop() } }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.practice), onBack = { state.back() })

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = mode == 0,
                onClick = { mode = 0 },
                label = { Text(stringResource(R.string.scale_practice)) },
            )
            FilterChip(
                selected = mode == 1,
                onClick = { mode = 1 },
                label = { Text(stringResource(R.string.chord_practice)) },
            )
        }

        Text(
            stringResource(R.string.root_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items((0 until 12).toList()) { pc ->
                FilterChip(
                    selected = root == pc,
                    onClick = { root = pc },
                    label = { Text(MusicTheory.noteName(pc)) },
                )
            }
        }

        if (mode == 0) {
            Text(
                stringResource(R.string.scale_practice),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(MusicTheory.SCALES.indices.toList()) { index ->
                    FilterChip(
                        selected = scaleIndex == index,
                        onClick = { scaleIndex = index },
                        label = { Text(MusicTheory.SCALES[index].name) },
                    )
                }
            }
        } else {
            Text(
                stringResource(R.string.progression),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(PROGRESSIONS.indices.toList()) { index ->
                    FilterChip(
                        selected = progressionIndex == index,
                        onClick = {
                            progressionIndex = index
                            randomDegrees = null
                        },
                        label = { Text(PROGRESSIONS[index].first) },
                    )
                }
            }
        }

        if (mode == 1) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = arpeggio,
                    onClick = { arpeggio = !arpeggio },
                    label = { Text(stringResource(R.string.arpeggio)) },
                )
                androidx.compose.material3.TextButton(onClick = {
                    randomDegrees = IntArray(4) { listOf(0, 1, 2, 3, 4, 5).random() }
                }) {
                    Text(stringResource(R.string.random_progression))
                }
            }
        }
        Text(
            stringResource(R.string.tempo) + ": $tempo",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Slider(
            value = tempo.toFloat(),
            onValueChange = { tempo = it.toInt() },
            valueRange = 50f..180f,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        val spacingMs = (60_000f / tempo).toInt().coerceAtLeast(120)
        Button(
            onClick = {
                if (mode == 0) {
                    val base = 60 + root
                    val intervals = MusicTheory.SCALES[scaleIndex].intervals
                    val ascending = intervals.map { base + it }
                    tonePlayer.play(ascending + ascending.reversed().drop(1), spacingMs)
                } else {
                    val base = 60 + root
                    val intervals = MusicTheory.SCALES[scaleIndex].intervals
                    val n = intervals.size
                    fun note(index: Int): Int = base + intervals[index % n] + (index / n) * 12
                    val degrees = randomDegrees ?: PROGRESSIONS[progressionIndex].second
                    val chords = degrees.map { degree ->
                        listOf(note(degree), note(degree + 2), note(degree + 4))
                    }
                    if (arpeggio) {
                        tonePlayer.play(chords.flatten(), (spacingMs / 2).coerceAtLeast(100))
                    } else {
                        tonePlayer.playChords(chords, spacingMs)
                    }
                }
            },
            modifier = Modifier.padding(16.dp),
        ) {
            Text(stringResource(R.string.play))
        }
    }
}
