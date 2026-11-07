package io.github.inwins.jellymusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import io.github.inwins.jellymusic.AppState
import io.github.inwins.jellymusic.R
import io.github.inwins.jellymusic.jellyfin.BaseItem
import io.github.inwins.jellymusic.util.MusicTheory
import io.github.inwins.jellymusic.util.TonePlayer

private data class TheoryLesson(
    val id: String,
    val titleRes: Int,
    val bodyRes: Int,
    val example: (TonePlayer) -> Unit,
)

private data class ExampleSong(val title: String, val artist: String)

private val EXAMPLES = mapOf(
    "major" to listOf(
        ExampleSong("Let It Be", "The Beatles"),
        ExampleSong("Free Fallin'", "Tom Petty"),
        ExampleSong("Born to Run", "Bruce Springsteen"),
    ),
    "minor" to listOf(
        ExampleSong("Stairway to Heaven", "Led Zeppelin"),
        ExampleSong("Hello", "Adele"),
        ExampleSong("Losing My Religion", "R.E.M."),
    ),
    "interval" to listOf(
        ExampleSong("Somewhere Over the Rainbow", "Judy Garland"),
        ExampleSong("Amazing Grace", "John Newton"),
        ExampleSong("Maria", "West Side Story"),
    ),
    "triad" to listOf(
        ExampleSong("Let It Be", "The Beatles"),
        ExampleSong("House of the Rising Sun", "The Animals"),
    ),
    "key" to listOf(
        ExampleSong("Autumn Leaves", "Cannonball Adderley"),
        ExampleSong("Fly Me to the Moon", "Frank Sinatra"),
    ),
    "progression" to listOf(
        ExampleSong("Let It Be", "The Beatles"),
        ExampleSong("Someone Like You", "Adele"),
        ExampleSong("With or Without You", "U2"),
        ExampleSong("Stand By Me", "Ben E. King"),
    ),
    "penta" to listOf(
        ExampleSong("茉莉花", "中国民歌"),
        ExampleSong("沧海一声笑", "黄霑"),
        ExampleSong("Amazing Grace", "John Newton"),
        ExampleSong("My Girl", "The Temptations"),
    ),
)

private fun scaleNotes(root: Int, scaleName: String): List<Int> {
    val intervals = MusicTheory.SCALES.firstOrNull { it.name == scaleName }?.intervals
        ?: intArrayOf(0, 2, 4, 5, 7, 9, 11)
    val base = 60 + root
    val up = intervals.map { base + it }
    return up + up.reversed().drop(1)
}

@Composable
fun TheoryScreen(state: AppState) {
    val tonePlayer = remember { TonePlayer() }
    DisposableEffect(Unit) { onDispose { tonePlayer.stop() } }
    val lessons = remember {
        listOf(
            TheoryLesson(
                "notes", R.string.theory_notes_title, R.string.theory_notes_body,
            ) { player -> player.play((0..12).map { 60 + it }, 220) },
            TheoryLesson(
                "major", R.string.theory_major_title, R.string.theory_major_body,
            ) { player -> player.play(scaleNotes(0, "大调"), 260) },
            TheoryLesson(
                "minor", R.string.theory_minor_title, R.string.theory_minor_body,
            ) { player -> player.play(scaleNotes(9, "自然小调"), 260) },
            TheoryLesson(
                "interval", R.string.theory_interval_title, R.string.theory_interval_body,
            ) { player -> player.play(listOf(60, 67), 700) },
            TheoryLesson(
                "triad", R.string.theory_triad_title, R.string.theory_triad_body,
            ) { player ->
                player.playChords(listOf(listOf(60, 64, 67), listOf(57, 60, 64)), 800)
            },
            TheoryLesson(
                "key", R.string.theory_key_title, R.string.theory_key_body,
            ) { player ->
                player.play(scaleNotes(0, "大调") + scaleNotes(7, "大调"), 220)
            },
            TheoryLesson(
                "progression", R.string.theory_progression_title, R.string.theory_progression_body,
            ) { player ->
                player.playChords(
                    listOf(
                        listOf(60, 64, 67),
                        listOf(55, 59, 62),
                        listOf(57, 60, 64),
                        listOf(53, 57, 60),
                    ),
                    700,
                )
            },
            TheoryLesson(
                "penta", R.string.theory_penta_title, R.string.theory_penta_body,
            ) { player -> player.play(scaleNotes(0, "大调五声"), 260) },
        )
    }
    var expanded by remember { mutableStateOf<String?>(null) }
    val resolved = remember { mutableStateMapOf<String, BaseItem?>() }
    LaunchedEffect(expanded) {
        val lessonId = expanded ?: return@LaunchedEffect
        val api = state.api ?: return@LaunchedEffect
        EXAMPLES[lessonId].orEmpty().forEach { example ->
            val key = lessonId + "|" + example.title
            if (!resolved.containsKey(key)) {
                val result = runCatching {
                    api.searchSongs(example.title + " " + example.artist, 1).firstOrNull()
                }.getOrNull()
                resolved[key] = result?.takeIf {
                    it.name.contains(example.title.take(4), ignoreCase = true)
                }
            }
        }
    }
    val done = lessons.count { state.theory.isCompleted(it.id) }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.theory_basics), onBack = { state.back() })
        Text(
            stringResource(R.string.theory_progress, done, lessons.size),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LinearProgressIndicator(
            progress = if (lessons.isEmpty()) 0f else done.toFloat() / lessons.size,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(lessons, key = { it.id }) { lesson ->
                Card {
                    Column {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expanded = if (expanded == lesson.id) null else lesson.id
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(lesson.titleRes),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            if (state.theory.isCompleted(lesson.id)) {
                                Text(
                                    stringResource(R.string.theory_done),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Icon(
                                if (expanded == lesson.id) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (expanded == lesson.id) {
                            Text(
                                stringResource(lesson.bodyRes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            val examples = EXAMPLES[lesson.id].orEmpty()
                            if (examples.isNotEmpty()) {
                                Text(
                                    stringResource(R.string.theory_examples),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                )
                                examples.forEach { example ->
                                    val found = resolved[lesson.id + "|" + example.title]
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = found != null) {
                                                found?.let { state.player.play(listOf(it), 0) }
                                            }
                                            .padding(horizontal = 16.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = if (found != null) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                            },
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                example.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            Text(
                                                example.artist,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Text(
                                            stringResource(
                                                if (found != null) {
                                                    R.string.theory_play
                                                } else {
                                                    R.string.theory_not_found
                                                },
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(onClick = { lesson.example(tonePlayer) }) {
                                    Text(stringResource(R.string.theory_play))
                                }
                                TextButton(onClick = { state.theory.toggle(lesson.id) }) {
                                    Text(
                                        stringResource(
                                            if (state.theory.isCompleted(lesson.id)) {
                                                R.string.theory_done
                                            } else {
                                                R.string.theory_mark_done
                                            },
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
