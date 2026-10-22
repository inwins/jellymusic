package com.example.jellymusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.jellymusic.AppState
import com.example.jellymusic.R
import com.example.jellymusic.util.MusicTheory
import kotlin.math.roundToInt

@Composable
fun FeatureLibraryScreen(state: AppState) {
    LaunchedEffect(Unit) { state.audioFeatures.reload() }
    var bpmFilter by remember { mutableIntStateOf(0) }
    var keyFilter by remember { mutableStateOf<String?>(null) }
    val entries = state.audioFeatures.all()
    val keys = remember(entries.size) {
        entries.map { MusicTheory.camelot(it.second.keyIndex, it.second.minor) }
            .distinct()
            .sortedBy { it.dropLast(1).toIntOrNull() ?: 0 }
    }
    val filtered = entries.filter { (_, features) ->
        val bpmOk = when (bpmFilter) {
            1 -> features.bpm < 90f
            2 -> features.bpm in 90f..120f
            3 -> features.bpm in 120f..150f
            4 -> features.bpm > 150f
            else -> true
        }
        val keyOk = keyFilter == null ||
            MusicTheory.camelot(features.keyIndex, features.minor) == keyFilter
        bpmOk && keyOk
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.feature_library), onBack = { state.back() })

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = bpmFilter == 0,
                onClick = { bpmFilter = 0 },
                label = { Text(stringResource(R.string.filter_all)) },
            )
            listOf("<90", "90-120", "120-150", ">150").forEachIndexed { index, label ->
                FilterChip(
                    selected = bpmFilter == index + 1,
                    onClick = { bpmFilter = index + 1 },
                    label = { Text(label) },
                )
            }
        }
        if (keys.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = keyFilter == null,
                        onClick = { keyFilter = null },
                        label = { Text(stringResource(R.string.filter_all)) },
                    )
                }
                items(keys) { key ->
                    FilterChip(
                        selected = keyFilter == key,
                        onClick = { keyFilter = if (keyFilter == key) null else key },
                        label = { Text(key) },
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            EmptyHint(stringResource(R.string.no_audio_features))
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(filtered) { index, entry ->
                    val item = entry.first
                    val features = entry.second
                    TrackRow(
                        item = item,
                        index = index + 1,
                        artworkUrl = state.artworkUrl(item, 120),
                        onClick = { state.player.play(filtered.map { it.first }, index) },
                        trackLabel = "${features.bpm.roundToInt()} · " +
                            MusicTheory.camelot(features.keyIndex, features.minor),
                        isCurrent = state.player.current?.id == item.id,
                        onLongClick = { state.trackMenu = item },
                    )
                }
            }
        }
    }
}
