package io.github.inwins.jellymusic.util

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/** Streams microphone pitch estimates (Hz) until the calling coroutine is cancelled. */
object MicPitch {
    suspend fun loop(sampleRate: Int = 22_050, onPitch: (Float) -> Unit) {
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
        }.getOrNull() ?: return
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return
        }
        record.startRecording()
        try {
            val buffer = ShortArray(2048)
            while (currentCoroutineContext().isActive) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    onPitch(PitchDetector.detect(buffer, sampleRate))
                }
            }
        } finally {
            runCatching {
                record.stop()
                record.release()
            }
        }
    }
}
