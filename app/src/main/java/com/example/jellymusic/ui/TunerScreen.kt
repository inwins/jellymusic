package com.example.jellymusic.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.jellymusic.AppState
import com.example.jellymusic.R
import com.example.jellymusic.util.Instruments
import com.example.jellymusic.util.MusicTheory
import com.example.jellymusic.util.PitchDetector
import com.example.jellymusic.util.TonePlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

@Composable
fun TunerScreen(state: AppState) {
    val context = LocalContext.current
    var running by remember { mutableStateOf(false) }
    var instrumentIndex by remember { mutableIntStateOf(-1) }
    var note by remember { mutableStateOf("-") }
    var cents by remember { mutableStateOf(0) }
    var frequency by remember { mutableStateOf(0f) }
    var detectedMidi by remember { mutableStateOf(0f) }
    val tonePlayer = remember { TonePlayer() }
    DisposableEffect(Unit) { onDispose { tonePlayer.stop() } }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) running = true }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val sampleRate = 22_050
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(4096)
        val record = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer,
            )
        }.getOrNull() ?: return@LaunchedEffect
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return@LaunchedEffect
        }
        record.startRecording()
        withContext(Dispatchers.IO) {
            val buffer = ShortArray(2048)
            while (isActive && running) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val pitch = PitchDetector.detect(buffer, sampleRate)
                    if (pitch > 0f) {
                        frequency = pitch
                        val midi = 69.0 + 12.0 * (ln(pitch / 440f) / ln(2.0))
                        detectedMidi = midi.toFloat()
                        val rounded = midi.roundToInt()
                        note = MusicTheory.noteName(rounded)
                        cents = ((midi - rounded) * 100).roundToInt()
                    }
                }
            }
        }
        runCatching {
            record.stop()
            record.release()
        }
    }

    val tuning = Instruments.ALL.getOrNull(instrumentIndex)
    val nearestString = tuning?.strings?.minByOrNull {
        abs(it.midi - detectedMidi)
    }
    val stringCents = nearestString?.let { (detectedMidi - it.midi) * 100 }?.roundToInt()

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TopBar(stringResource(R.string.tuner), onBack = { state.back() })

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = instrumentIndex == -1,
                    onClick = { instrumentIndex = -1 },
                    label = { Text("半音") },
                )
            }
            items(Instruments.ALL.size) { index ->
                FilterChip(
                    selected = instrumentIndex == index,
                    onClick = { instrumentIndex = index },
                    label = { Text(Instruments.ALL[index].name) },
                )
            }
        }

        if (nearestString != null) {
            Text(
                nearestString.label,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                (if ((stringCents ?: 0) > 0) "+" else "") + stringCents + " " +
                    stringResource(R.string.cents),
                style = MaterialTheme.typography.titleMedium,
                color = if (abs(stringCents ?: 0) <= 5) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        } else {
            Text(note, fontSize = 64.sp, fontWeight = FontWeight.Bold)
            Text(
                (if (cents > 0) "+" else "") + cents + " " + stringResource(R.string.cents),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            if (frequency > 0f) String.format(java.util.Locale.US, "%.1f Hz", frequency) else "-",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (tuning != null) {
            LazyRow(
                Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(tuning.strings) { string ->
                    val isNearest = nearestString?.label == string.label
                    TextButton(onClick = { tonePlayer.play(listOf(string.midi), 900) }) {
                        Text(
                            string.label,
                            color = if (isNearest) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                if (running) {
                    running = false
                } else if (
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    running = true
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        ) {
            Text(stringResource(if (running) R.string.stop else R.string.start))
        }
    }
}
