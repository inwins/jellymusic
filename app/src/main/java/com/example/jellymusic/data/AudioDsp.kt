package com.example.jellymusic.data

import com.example.jellymusic.util.Fft
import com.example.jellymusic.util.MusicTheory
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Lightweight on-device audio feature extraction. Pure Kotlin so it can be unit tested
 * with synthetic signals; [AudioAnalyzer] supplies the decoded mono PCM.
 */
object AudioDsp {
    const val FRAME = 2048
    const val HOP = 1024
    private const val MEL_FILTERS = 26
    private const val MFCC_COEFFS = 13

    private val MAJOR = floatArrayOf(
        6.35f, 2.23f, 3.48f, 2.33f, 4.38f, 4.09f,
        2.52f, 5.19f, 2.39f, 3.66f, 2.29f, 2.88f,
    )
    private val MINOR = floatArrayOf(
        6.33f, 2.68f, 3.52f, 5.38f, 2.60f, 3.53f,
        2.54f, 4.75f, 3.98f, 2.69f, 3.34f, 3.17f,
    )

    fun empty(now: Long = System.currentTimeMillis()): AudioFeatures = AudioFeatures(
        bpm = 0f,
        keyIndex = 0,
        minor = false,
        energy = 0f,
        loudnessDb = -80f,
        brightness = 0f,
        instrumentalness = 0f,
        beatStrength = 0f,
        chroma = List(12) { 0f },
        analyzedAt = now,
    )

    fun analyze(samples: FloatArray, sampleRate: Int): AudioFeatures {
        if (sampleRate <= 0 || samples.size < FRAME) return empty()
        val bins = FRAME / 2
        val window = FloatArray(FRAME) {
            (0.5 - 0.5 * cos(2.0 * PI * it / (FRAME - 1))).toFloat()
        }
        val re = FloatArray(FRAME)
        val im = FloatArray(FRAME)
        val prevMag = FloatArray(bins)
        val mag = FloatArray(bins)
        val flux = ArrayList<Float>()
        val frameRms = ArrayList<Float>()
        val framePitch = ArrayList<Float>()
        val frameSalience = ArrayList<Float>()
        val frameChroma = ArrayList<FloatArray>()
        val chroma = FloatArray(12)
        val freqs = FloatArray(bins) { (it + 1) * sampleRate.toFloat() / FRAME }
        val melFilters = melFilterbank(sampleRate, MEL_FILTERS, bins, freqs)
        val mfccSum = DoubleArray(MFCC_COEFFS)

        var centroidSum = 0f
        var flatnessSum = 0f
        var bandSum = 0f
        var magSumAll = 0f
        var rmsSum = 0f
        var frames = 0
        var pos = 0

        while (pos + FRAME <= samples.size) {
            for (i in 0 until FRAME) {
                re[i] = samples[pos + i] * window[i]
                im[i] = 0f
            }
            Fft.transform(re, im)

            var magSum = 0f
            var centroid = 0f
            var logSum = 0f
            var frameFlux = 0f
            val frameChromaVec = FloatArray(12)
            for (k in 0 until bins) {
                val value = sqrt(re[k] * re[k] + im[k] * im[k])
                mag[k] = value
                magSum += value
                centroid += freqs[k] * value
                logSum += ln(value + 1e-6f)
                frameFlux += max(0f, value - prevMag[k])
                prevMag[k] = value
                val f = freqs[k]
                if (f in 300f..3400f) bandSum += value
                if (f in 55f..2000f) {
                    val midi = 69.0 + 12.0 * (ln(f / 440f) / ln(2.0))
                    val pc = ((midi.roundToInt() % 12) + 12) % 12
                    chroma[pc] += value
                    frameChromaVec[pc] += value
                }
            }
            for (c in 0 until MFCC_COEFFS) {
                var sum = 0.0
                for (f in 0 until MEL_FILTERS) {
                    sum += melLogEnergy(melFilters[f], mag, f) *
                        kotlin.math.cos(PI * c * (f + 0.5) / MEL_FILTERS)
                }
                mfccSum[c] += sum / MEL_FILTERS
            }
            var rms = 0f
            for (i in 0 until FRAME) rms += samples[pos + i] * samples[pos + i]
            rms = sqrt(rms / FRAME)
            rmsSum += rms
            frameRms.add(rms)
            frameChroma.add(frameChromaVec)
            val (pitchHz, salience) = estimatePitch(mag, freqs)
            framePitch.add(pitchHz)
            frameSalience.add(salience)
            magSumAll += magSum
            centroidSum += if (magSum > 0f) centroid / magSum else 0f
            val arith = magSum / bins
            val geo = exp(logSum / bins)
            flatnessSum += if (arith > 0f) (geo / arith).coerceIn(0f, 1f) else 0f
            flux.add(frameFlux)
            frames++
            pos += HOP
        }
        if (frames == 0) return empty()

        val rmsAvg = rmsSum / frames
        val loudnessDb = if (rmsAvg > 0f) (20f * log10(rmsAvg)) else -80f
        val energy = ((loudnessDb + 50f) / 50f).coerceIn(0f, 1f)
        val brightness = ((centroidSum / frames) / (sampleRate / 2f)).coerceIn(0f, 1f)
        val flatness = (flatnessSum / frames).coerceIn(0f, 1f)
        val bandRatio = if (magSumAll > 0f) (bandSum / magSumAll).coerceIn(0f, 1f) else 0f
        val vocalness = (bandRatio * (0.5f + 0.5f * (1f - flatness))).coerceIn(0f, 1f)
        val instrumentalness = (1f - vocalness).coerceIn(0f, 1f)

        val (bpm, beatStrength) = estimateTempo(flux, sampleRate)
        val (keyIndex, minor, keyConfidence) = estimateKey(chroma)

        val chromaSum = chroma.sum()
        val chromaNorm = if (chromaSum > 0f) {
            chroma.map { it / chromaSum }
        } else {
            List(12) { 0f }
        }

        val scaleMatch = MusicTheory.detectScale(chromaNorm)
        val noteEvents = buildNotes(framePitch, frameSalience, frameRms, sampleRate, bpm, scaleMatch)
        val histogram = FloatArray(12)
        noteEvents.forEach { histogram[((it.midi % 12) + 12) % 12] += it.durationMs.toFloat() }
        val histogramSum = histogram.sum()
        val pitchHistogram = if (histogramSum > 0f) {
            histogram.map { it / histogramSum }
        } else {
            List(12) { 0f }
        }

        return AudioFeatures(
            bpm = bpm,
            keyIndex = keyIndex,
            minor = minor,
            energy = energy,
            loudnessDb = loudnessDb,
            brightness = brightness,
            instrumentalness = instrumentalness,
            beatStrength = beatStrength,
            chroma = chromaNorm,
            analyzedAt = System.currentTimeMillis(),
            scaleTonic = scaleMatch?.tonic ?: 0,
            scaleName = scaleMatch?.scale?.name ?: "",
            scaleConfidence = scaleMatch?.confidence ?: 0f,
            notes = noteEvents,
            pitchMin = noteEvents.minOfOrNull { it.midi } ?: 0,
            pitchMax = noteEvents.maxOfOrNull { it.midi } ?: 0,
            pitchHistogram = pitchHistogram,
            chords = buildChords(frameChroma, bpm, sampleRate),
            timeSignature = estimateTimeSignature(flux, bpm, sampleRate),
            melodyConfidence = run {
                val voiced = frameSalience.filterIndexed { index, _ ->
                    framePitch.getOrElse(index) { 0f } > 0f
                }
                if (voiced.isEmpty()) 0f else voiced.average().toFloat().coerceIn(0f, 1f)
            },
            keyConfidence = run {
                val intervals = if (minor) {
                    intArrayOf(0, 2, 3, 5, 7, 8, 10)
                } else {
                    intArrayOf(0, 2, 4, 5, 7, 9, 11)
                }
                var inKey = 0f
                for (i in intervals) inKey += chromaNorm[(keyIndex + i) % 12]
                inKey.coerceIn(0f, 1f)
            },
            mfcc = if (frames > 0) {
                mfccSum.map { (it / frames).toFloat() }
            } else {
                emptyList()
            },
        )
    }

    private fun melLogEnergy(filter: FloatArray, mag: FloatArray, index: Int): Double {
        var energy = 0.0
        for (k in filter.indices) {
            if (filter[k] != 0f) energy += filter[k] * mag[k]
        }
        if (energy <= 0.0) return -23.0
        return ln(energy).coerceAtLeast(-23.0)
    }

    private fun melFilterbank(
        sampleRate: Int,
        filters: Int,
        bins: Int,
        freqs: FloatArray,
    ): Array<FloatArray> {
        fun hzToMel(hz: Double) = 2595.0 * kotlin.math.log10(1.0 + hz / 700.0)
        fun melToHz(mel: Double) = 700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0)
        val minMel = hzToMel(80.0)
        val maxMel = hzToMel((sampleRate / 2).toDouble() * 0.95)
        val points = DoubleArray(filters + 2) { melToHz(minMel + (maxMel - minMel) * it / (filters + 1)) }
        return Array(filters) { f ->
            val filter = FloatArray(bins)
            val left = points[f]
            val center = points[f + 1]
            val right = points[f + 2]
            for (k in 0 until bins) {
                val f0 = freqs[k].toDouble()
                filter[k] = when {
                    f0 < left || f0 > right -> 0f
                    f0 <= center -> ((f0 - left) / (center - left)).toFloat()
                    else -> ((right - f0) / (right - center)).toFloat()
                }
            }
            filter
        }
    }

    private fun buildChords(
        chromaFrames: List<FloatArray>,
        bpm: Float,
        sampleRate: Int,
    ): List<ChordSegment> {
        if (chromaFrames.isEmpty() || bpm <= 0f) return emptyList()
        val frameMs = HOP * 1000L / sampleRate
        val beatMs = (60_000f / bpm).toLong().coerceAtLeast(200L)
        val framesPerBeat = (beatMs / frameMs).coerceAtLeast(1L).toInt()
        val raw = ArrayList<Pair<Long, Pair<Int, Boolean>?>>()
        var i = 0
        while (i < chromaFrames.size) {
            val end = (i + framesPerBeat).coerceAtMost(chromaFrames.size)
            val sum = FloatArray(12)
            for (j in i until end) {
                for (p in 0 until 12) sum[p] += chromaFrames[j][p]
            }
            raw.add(i.toLong() * frameMs to MusicTheory.chordForChroma(sum.toList()))
            i = end
        }
        if (raw.isEmpty()) return emptyList()
        val smoothed = raw.mapIndexed { index, pair ->
            val counts = HashMap<Pair<Int, Boolean>?, Int>()
            for (w in (index - 1).coerceAtLeast(0)..(index + 1).coerceAtMost(raw.size - 1)) {
                val key = raw[w].second
                counts[key] = (counts[key] ?: 0) + 1
            }
            pair.first to counts.maxByOrNull { it.value }!!.key
        }
        val out = ArrayList<ChordSegment>()
        var last: Pair<Int, Boolean>? = null
        var lastStart = 0L
        for ((start, chord) in smoothed) {
            if (chord != last) {
                if (last != null) {
                    out.add(ChordSegment(lastStart, MusicTheory.chordName(last.first, last.second)))
                }
                last = chord
                lastStart = start
            }
        }
        if (last != null) {
            out.add(ChordSegment(lastStart, MusicTheory.chordName(last.first, last.second)))
        }
        return out.take(96)
    }

    private fun estimateTimeSignature(flux: List<Float>, bpm: Float, sampleRate: Int): String {
        if (bpm <= 0f || flux.size < 32) return ""
        val mean = flux.average().toFloat()
        val env = FloatArray(flux.size) { flux[it] - mean }
        val frameRate = sampleRate.toFloat() / HOP
        val beatFrames = (60f * frameRate / bpm).roundToInt().coerceAtLeast(1)
        fun correlation(lag: Int): Float {
            if (lag <= 0 || lag >= env.size) return 0f
            var sum = 0f
            for (i in 0 until env.size - lag) sum += env[i] * env[i + lag]
            return sum
        }
        return if (correlation(beatFrames * 4) >= correlation(beatFrames * 3)) "4/4" else "3/4"
    }

    private fun estimatePitch(mag: FloatArray, freqs: FloatArray): Pair<Float, Float> {
        var minBin = 1
        while (minBin < freqs.size && freqs[minBin] < 120f) minBin++
        var maxBin = minBin
        while (maxBin < freqs.size && freqs[maxBin] <= 1000f) maxBin++
        if (minBin >= maxBin) return 0f to 0f
        var mean = 0f
        for (k in minBin until maxBin) mean += mag[k]
        mean /= (maxBin - minBin)
        var bestBin = -1
        var best = 0f
        for (k in minBin until maxBin) {
            // harmonic summation (robust, avoids the underflow of a product)
            var score = mag[k]
            var harmonic = 2
            while (harmonic <= 4) {
                val index = k * harmonic
                if (index >= mag.size) break
                score += mag[index] / harmonic
                harmonic++
            }
            if (score > best) {
                best = score
                bestBin = k
            }
        }
        if (bestBin <= 0 || best <= 0f) return 0f to 0f
        val salience = (mag[bestBin] / (mean + 1e-6f)).coerceIn(0f, 40f) / 40f
        return freqs[bestBin] to salience
    }

    private fun buildNotes(
        pitches: List<Float>,
        salience: List<Float>,
        rms: List<Float>,
        sampleRate: Int,
        bpm: Float,
        scale: MusicTheory.ScaleMatch?,
    ): List<NoteEvent> {
        if (pitches.isEmpty()) return emptyList()
        val frameMs = HOP * 1000L / sampleRate
        val average = rms.average().toFloat()
        val silence = average * 0.30f

        // median filter over 5 frames to reject octave/noise outliers
        val filtered = FloatArray(pitches.size)
        for (i in pitches.indices) {
            val window = ArrayList<Float>()
            for (j in (i - 2).coerceAtLeast(0)..(i + 2).coerceAtMost(pitches.size - 1)) {
                if (pitches[j] > 0f) window.add(pitches[j])
            }
            filtered[i] = if (window.isEmpty()) 0f else window.sorted()[window.size / 2]
        }

        val scalePcs = scale?.let { match ->
            match.scale.intervals.map { (match.tonic + it) % 12 }.toSet()
        }
        data class Frame(val index: Int, val midi: Int)
        val frames = ArrayList<Frame>()
        for (i in filtered.indices) {
            val hz = filtered[i]
            if (hz <= 0f || rms[i] < silence || salience[i] < 0.3f) continue
            var midi = MusicTheory.hzToMidi(hz)
            if (midi < 0) continue
            if (scalePcs != null) midi = snapToScale(midi, scalePcs)
            frames.add(Frame(i, midi))
        }
        if (frames.isEmpty()) return emptyList()

        val grid = if (bpm > 0f) (60_000f / bpm) / 2f else 250f
        val minDuration = maxOf(90L, (grid * 0.5f).toLong())

        val notes = ArrayList<NoteEvent>()
        var startIndex = frames[0].index
        var lastIndex = frames[0].index
        var sum = frames[0].midi.toFloat()
        var count = 1
        var current = frames[0].midi
        fun close() {
            val duration = (lastIndex - startIndex + 1) * frameMs
            if (duration >= minDuration) {
                val quantized = (Math.round(duration.toFloat() / grid) * grid).toLong()
                    .coerceAtLeast(minDuration)
                notes.add(NoteEvent(startIndex * frameMs, quantized, (sum / count).toInt()))
            }
        }
        for (f in frames.drop(1)) {
            if (kotlin.math.abs(f.midi - current) <= 1) {
                lastIndex = f.index
                sum += f.midi
                count++
                current = (sum / count).toInt()
            } else {
                close()
                startIndex = f.index
                lastIndex = f.index
                sum = f.midi.toFloat()
                count = 1
                current = f.midi
            }
        }
        close()
        return notes.take(400)
    }

    private fun snapToScale(midi: Int, pcs: Set<Int>): Int {
        val pc = ((midi % 12) + 12) % 12
        if (pc in pcs) return midi
        for (delta in 1..2) {
            if (((pc - delta) % 12 + 12) % 12 in pcs) return midi - delta
            if (((pc + delta) % 12) % 12 in pcs) return midi + delta
        }
        return midi
    }

    private fun estimateTempo(flux: List<Float>, sampleRate: Int): Pair<Float, Float> {
        if (flux.size < 16) return 0f to 0f
        val mean = flux.average().toFloat()
        val env = FloatArray(flux.size) { flux[it] - mean }
        val frameRate = sampleRate.toFloat() / HOP
        val minLag = (60f * frameRate / 180f).roundToInt().coerceAtLeast(1)
        val maxLag = (60f * frameRate / 60f).roundToInt().coerceAtMost(env.size - 1)
        var bestLag = 0
        var best = 0f
        for (lag in minLag..maxLag) {
            var sum = 0f
            for (i in 0 until env.size - lag) sum += env[i] * env[i + lag]
            if (sum > best) {
                best = sum
                bestLag = lag
            }
        }
        if (bestLag == 0) return 0f to 0f
        var total = 0f
        for (v in env) total += v * v
        val strength = if (total > 0f) (best / total).coerceIn(0f, 1f) else 0f
        var bpm = 60f * frameRate / bestLag
        while (bpm < 70f) bpm *= 2f
        while (bpm > 180f) bpm /= 2f
        return bpm to strength
    }

    private fun estimateKey(chroma: FloatArray): Triple<Int, Boolean, Float> {
        val sum = chroma.sum()
        if (sum <= 0f) return Triple(0, false, 0f)
        val norm = FloatArray(12) { chroma[it] / sum }
        var bestScore = -Float.MAX_VALUE
        var secondScore = -Float.MAX_VALUE
        var bestKey = 0
        var bestMinor = false
        for (rot in 0 until 12) {
            var major = 0f
            var minor = 0f
            for (i in 0 until 12) {
                val c = norm[(i + rot) % 12]
                major += c * MAJOR[i]
                minor += c * MINOR[i]
            }
            if (major > bestScore) {
                secondScore = bestScore
                bestScore = major
                bestKey = rot
                bestMinor = false
            } else if (major > secondScore) {
                secondScore = major
            }
            if (minor > bestScore) {
                secondScore = bestScore
                bestScore = minor
                bestKey = rot
                bestMinor = true
            } else if (minor > secondScore) {
                secondScore = minor
            }
        }
        val confidence = if (bestScore > 0f) {
            ((bestScore - secondScore.coerceAtLeast(0f)) / bestScore).coerceIn(0f, 1f)
        } else {
            0f
        }
        return Triple(bestKey, bestMinor, confidence)
    }
}
