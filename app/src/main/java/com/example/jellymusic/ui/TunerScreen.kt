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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.jellymusic.util.MusicTheory
import com.example.jellymusic.util.PitchDetector
import kotlinx.coroutines.isActive
import kotlin.math.ln
import kotlin.math.roundToInt

@Composable
fun TunerScreen(state: AppState) {
    val context = LocalContext.current
    var running by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("-") }
    var cents by remember { mutableStateOf(0) }
    var frequency by remember { mutableStateOf(0f) }
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
        val buffer = ShortArray(2048)
        while (isActive && running) {
            val read = record.read(buffer, 0, buffer.size)
            if (read > 0) {
                val pitch = PitchDetector.detect(buffer, sampleRate)
                if (pitch > 0f) {
                    frequency = pitch
                    val midi = 69.0 + 12.0 * (ln(pitch / 440f) / ln(2.0))
                    val rounded = midi.roundToInt()
                    note = MusicTheory.noteName(rounded)
                    cents = ((midi - rounded) * 100).roundToInt()
                }
            }
        }
        runCatching {
            record.stop()
            record.release()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TopBar(stringResource(R.string.tuner), onBack = { state.back() })
        Text(note, fontSize = 72.sp, style = MaterialTheme.typography.displayMedium)
        Text(
            (if (cents > 0) "+" else "") + cents + " " + stringResource(R.string.cents),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            if (frequency > 0f) String.format(java.util.Locale.US, "%.1f Hz", frequency) else "-",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
