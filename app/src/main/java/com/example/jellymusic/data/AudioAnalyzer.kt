package com.example.jellymusic.data

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/** Decodes an audio URI to mono PCM using MediaCodec, then hands it to [AudioDsp]. */
class AudioAnalyzer(private val context: Context) {

    suspend fun analyze(uri: Uri, maxSeconds: Int = 90): AudioFeatures? =
        withContext(Dispatchers.Default) {
            val decoded = decodeMono(uri, maxSeconds) ?: return@withContext null
            AudioDsp.analyze(decoded.first, decoded.second)
        }

    private fun decodeMono(uri: Uri, maxSeconds: Int): Pair<FloatArray, Int>? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                val mime = candidate.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = candidate
                    break
                }
            }
            val selected = format ?: return null
            if (trackIndex < 0) return null
            extractor.selectTrack(trackIndex)

            val mime = selected.getString(MediaFormat.KEY_MIME) ?: return null
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(selected, null, null, 0)
            codec.start()

            var sampleRate = selected.intValue(MediaFormat.KEY_SAMPLE_RATE, 44_100)
            var channels = selected.intValue(MediaFormat.KEY_CHANNEL_COUNT, 2)

            var data = FloatArray(1 shl 16)
            var length = 0
            fun push(value: Float) {
                if (length == data.size) data = data.copyOf(data.size * 2)
                data[length++] = value
            }

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var startedAt = System.currentTimeMillis()
            var maxSamples = maxSeconds * sampleRate

            while (!outputDone) {
                if (System.currentTimeMillis() - startedAt > DECODE_TIMEOUT_MS) break
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex) ?: continue
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = codec.outputFormat
                        sampleRate = newFormat.intValue(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                        channels = newFormat.intValue(MediaFormat.KEY_CHANNEL_COUNT, channels)
                        maxSamples = maxSeconds * sampleRate
                    }

                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                    else -> if (outIndex >= 0) {
                        if (info.size > 0) {
                            val buffer = codec.getOutputBuffer(outIndex)
                            if (buffer != null) {
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                val shorts = ShortArray(info.size / 2)
                                buffer.asShortBuffer().get(shorts)
                                var i = 0
                                val ch = channels.coerceAtLeast(1)
                                while (i + ch <= shorts.size) {
                                    var sum = 0f
                                    for (c in 0 until ch) sum += shorts[i + c] / 32768f
                                    push(sum / ch)
                                    i += ch
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (length >= maxSamples) outputDone = true
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            if (length < AudioDsp.FRAME) return null

            val targetRate = TARGET_SAMPLE_RATE
            val step = max(1, (sampleRate.toFloat() / targetRate).roundToInt())
            val outRate = sampleRate / step
            val mono = FloatArray(length / step)
            var sum = 0f
            var count = 0
            var index = 0
            for (i in 0 until length) {
                sum += data[i]
                count++
                if (count == step) {
                    if (index < mono.size) mono[index++] = sum / count
                    sum = 0f
                    count = 0
                }
            }
            return mono to outRate
        } catch (_: Exception) {
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun MediaFormat.intValue(key: String, fallback: Int): Int =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(fallback) else fallback

    private companion object {
        const val TARGET_SAMPLE_RATE = 11_025
        const val DECODE_TIMEOUT_MS = 45_000L
    }
}
