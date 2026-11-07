package io.github.inwins.jellymusic.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.inwins.jellymusic.AppState
import io.github.inwins.jellymusic.R
import io.github.inwins.jellymusic.util.ChordData
import io.github.inwins.jellymusic.util.ChordShape
import io.github.inwins.jellymusic.util.Instruments
import io.github.inwins.jellymusic.util.MusicTheory
import io.github.inwins.jellymusic.util.TonePlayer
import kotlin.math.roundToInt

private val PIANO_CHORDS = listOf("C", "G", "Am", "F", "Dm", "Em")

@Composable
fun ChordLibraryScreen(state: AppState) {
    var instrument by remember { mutableIntStateOf(0) }
    var selected by remember { mutableIntStateOf(0) }
    val tonePlayer = remember { TonePlayer() }
    DisposableEffect(Unit) { onDispose { tonePlayer.stop() } }

    val shapes = when (instrument) {
        0 -> ChordData.GUITAR
        1 -> ChordData.UKULELE
        else -> emptyList()
    }
    val chordName = if (instrument == 2) {
        PIANO_CHORDS[selected.coerceIn(0, PIANO_CHORDS.lastIndex)]
    } else {
        shapes.getOrNull(selected.coerceIn(0, (shapes.size - 1).coerceAtLeast(0)))?.name ?: "C"
    }
    val chordNotes = remember(chordName, instrument) {
        if (instrument == 2) pianoChordNotes(chordName) else guitarChordNotes(chordName, instrument)
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.chord_library), onBack = { state.back() })
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("吉他", "尤克里里", "钢琴").forEachIndexed { index, label ->
                FilterChip(
                    selected = instrument == index,
                    onClick = { instrument = index; selected = 0 },
                    label = { Text(label) },
                )
            }
        }
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val names = if (instrument == 2) PIANO_CHORDS else shapes.map { it.name }
            items(names.size) { index ->
                FilterChip(
                    selected = selected == index,
                    onClick = { selected = index },
                    label = { Text(names[index]) },
                )
            }
        }
        if (instrument != 2) {
            shapes.getOrNull(selected)?.let { shape ->
                Fretboard(shape, Modifier.fillMaxWidth())
            }
        } else {
            PianoKeys(
                highlighted = chordNotes.map { it % 12 }.toSet(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(12.dp),
            )
        }
        Button(
            onClick = { tonePlayer.playChords(listOf(chordNotes), 1_200) },
            modifier = Modifier.padding(16.dp),
        ) {
            Text(stringResource(R.string.play) + " " + chordName)
        }
    }
}

@Composable
fun ScaleVisualizerScreen(state: AppState) {
    var root by remember { mutableIntStateOf(0) }
    var scaleIndex by remember { mutableIntStateOf(0) }
    var guitar by remember { mutableIntStateOf(1) }
    val tonePlayer = remember { TonePlayer() }
    DisposableEffect(Unit) { onDispose { tonePlayer.stop() } }
    val intervals = MusicTheory.SCALES[scaleIndex].intervals
    val scalePcs = intervals.map { (root + it) % 12 }.toSet()

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.scale_visualizer), onBack = { state.back() })
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
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = guitar == 1,
                onClick = { guitar = 1 },
                label = { Text("吉他") },
            )
            FilterChip(
                selected = guitar == 0,
                onClick = { guitar = 0 },
                label = { Text("钢琴") },
            )
        }
        if (guitar == 1) {
            ScaleFretboard(scalePcs, Modifier.fillMaxWidth())
        } else {
            PianoKeys(
                highlighted = scalePcs,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .padding(12.dp),
            )
        }
        Button(
            onClick = {
                val base = 60 + root
                val up = intervals.map { base + it }
                tonePlayer.play(up + up.reversed().drop(1), 260)
            },
            modifier = Modifier.padding(16.dp),
        ) {
            Text(stringResource(R.string.play))
        }
    }
}

@Composable
private fun Fretboard(shape: ChordShape, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        modifier
            .height(240.dp)
            .padding(16.dp),
    ) {
        val count = shape.frets.size
        val left = size.width * 0.08f
        val right = size.width * 0.92f
        val top = size.height * 0.18f
        val bottom = size.height * 0.95f
        val stringGap = if (count > 1) (right - left) / (count - 1) else 0f
        val fretGap = (bottom - top) / 5f
        for (i in 0 until count) {
            val x = left + i * stringGap
            drawLine(line, Offset(x, top), Offset(x, bottom), 2f)
            val fret = shape.frets[i]
            when {
                fret == -1 -> {
                    drawLine(line, Offset(x - 8f, top - 26f), Offset(x + 8f, top - 10f), 3f)
                    drawLine(line, Offset(x + 8f, top - 26f), Offset(x - 8f, top - 10f), 3f)
                }
                fret == 0 -> drawCircle(primary, 7f, Offset(x, top - 18f), style = Stroke(3f))
            }
        }
        for (f in 0..5) {
            val y = top + f * fretGap
            drawLine(line, Offset(left, y), Offset(right, y), if (f == 0) 6f else 2f)
        }
        shape.frets.forEachIndexed { i, fret ->
            if (fret > 0) {
                val x = left + i * stringGap
                val y = top + (fret - 0.5f) * fretGap
                drawCircle(primary, 13f, Offset(x, y))
            }
        }
    }
}

@Composable
private fun ScaleFretboard(scalePcs: Set<Int>, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.onSurfaceVariant
    val strings = Instruments.GUITAR.strings
    Canvas(
        modifier
            .height(260.dp)
            .padding(16.dp),
    ) {
        val count = strings.size
        val left = size.width * 0.06f
        val right = size.width * 0.96f
        val top = size.height * 0.08f
        val bottom = size.height * 0.95f
        val stringGap = (right - left) / (count - 1)
        val fretGap = (right - left) / 12f
        for (i in 0 until count) {
            val y = top + (bottom - top) * i / (count - 1)
            drawLine(line, Offset(left, y), Offset(right, y), 2f)
        }
        for (f in 0..12) {
            val x = left + f * fretGap
            drawLine(line, Offset(x, top), Offset(x, bottom), if (f == 0) 6f else 2f)
        }
        for (i in 0 until count) {
            val open = strings[i].midi
            val y = top + (bottom - top) * i / (count - 1)
            for (f in 0..12) {
                if (((open + f) % 12) in scalePcs) {
                    val x = left + f * fretGap
                    drawCircle(primary, 9f, Offset(x, y))
                }
            }
        }
    }
}

@Composable
private fun PianoKeys(highlighted: Set<Int>, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val white = Color.White
    val black = Color(0xFF202020)
    val line = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        val whitePcs = listOf(0, 2, 4, 5, 7, 9, 11)
        val whiteCount = 14
        val w = size.width / whiteCount
        for (i in 0 until whiteCount) {
            val pc = whitePcs[i % 7]
            val rectSize = Size(w, size.height)
            drawRect(if (pc in highlighted) primary else white, Offset(i * w, 0f), rectSize)
            drawRect(line, Offset(i * w, 0f), rectSize, style = Stroke(1.5f))
        }
        val blackOffsets = listOf(0 to 1, 1 to 3, 3 to 6, 4 to 8, 5 to 10)
        for (octave in 0..1) {
            for ((whiteIndex, pc) in blackOffsets) {
                val x = (octave * 7 + whiteIndex + 1) * w
                val rectSize = Size(w * 0.6f, size.height * 0.62f)
                drawRect(
                    if (pc in highlighted) primary else black,
                    Offset(x - rectSize.width / 2f, 0f),
                    rectSize,
                )
            }
        }
    }
}

private fun guitarChordNotes(name: String, instrument: Int): List<Int> {
    val shape = when (instrument) {
        0 -> ChordData.GUITAR.firstOrNull { it.name == name }
        1 -> ChordData.UKULELE.firstOrNull { it.name == name }
        else -> null
    } ?: return pianoChordNotes(name)
    val tuning = if (instrument == 0) Instruments.GUITAR.strings else Instruments.UKULELE.strings
    return shape.frets.mapIndexedNotNull { index, fret ->
        if (fret < 0) null else tuning[index].midi + fret
    }
}

private fun pianoChordNotes(name: String): List<Int> {
    val minor = name.endsWith("m")
    val rootName = if (minor) name.dropLast(1) else name
    val pc = MusicTheory.NOTE_NAMES.indexOf(rootName).let { if (it < 0) 0 else it }
    val base = 60 + pc
    val third = if (minor) 3 else 4
    return listOf(base, base + third, base + 7)
}
