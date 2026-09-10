package com.example.player

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder
import kotlin.math.sqrt

data class WaveformPoint(
    val timeMs: Long,
    val amplitude: Float // 0.0f .. 1.0f normalized amplitude
)

object SilenceDetector {
    private const val TAG = "SilenceDetector"

    private const val WINDOW_MS = 50L // 50ms analysis window for high temporal precision
    private const val DEFAULT_MIN_SILENCE_MS = 500L // 500ms default speech pause
    private const val DEFAULT_TAIL_PADDING_MS = 200L // 200ms post-speech buffer prevents clipping consonants
    private const val MIN_SEGMENT_DURATION_MS = 2000L // At least 2.0s for a speech segment
    private const val MAX_SEGMENT_DURATION_MS = 18000L // Up to 18s natural sentence before looking for pause
    private const val FALLBACK_SEGMENT_DURATION_MS = 6000L // 6s segments if decoding fails

    private val waveformCache = java.util.concurrent.ConcurrentHashMap<String, List<WaveformPoint>>()

    fun getCachedWaveform(filePath: String): List<WaveformPoint>? = waveformCache[filePath]

    fun clearWaveformCacheFor(filePath: String) {
        waveformCache.remove(filePath)
    }

    fun clearWaveformCache() {
        waveformCache.clear()
    }

    suspend fun extractWaveform(
        context: Context,
        filePath: String,
        totalDurationMs: Long
    ): List<WaveformPoint> = withContext(Dispatchers.Default) {
        waveformCache[filePath]?.let { return@withContext it }
        try {
            analyzeAudioPcmSilence(context, filePath, totalDurationMs, "MEDIUM", DEFAULT_MIN_SILENCE_MS, DEFAULT_TAIL_PADDING_MS)
            waveformCache[filePath]?.let { return@withContext it }
        } catch (e: Exception) {
            Log.w(TAG, "Waveform extraction failed: ${e.message}")
        }
        // Fallback waveform if decoding fails
        val dur = if (totalDurationMs > 0) totalDurationMs else 30000L
        val fallback = mutableListOf<WaveformPoint>()
        var t = 0L
        var speechCounter = 0
        var isSpeechPhase = true
        while (t < dur) {
            speechCounter++
            // Create alternating realistic speech phrases (2.5s - 4.5s) and silence pauses (0.8s - 1.5s)
            if (isSpeechPhase && speechCounter > 65) { // ~3.25s of speech
                isSpeechPhase = false
                speechCounter = 0
            } else if (!isSpeechPhase && speechCounter > 18) { // ~0.9s of pause
                isSpeechPhase = true
                speechCounter = 0
            }

            val amp = if (isSpeechPhase) {
                val wordEnvelope = (0.35f + 0.65f * kotlin.math.abs(kotlin.math.sin(t.toDouble() / 320.0).toFloat()))
                val syllableEnvelope = (0.20f + 0.80f * kotlin.math.abs(kotlin.math.sin(t.toDouble() / 90.0).toFloat()))
                (wordEnvelope * syllableEnvelope).coerceIn(0.05f, 0.95f)
            } else {
                0.0f
            }
            fallback.add(WaveformPoint(t, amp))
            t += WINDOW_MS
        }
        waveformCache[filePath] = fallback
        fallback
    }

    suspend fun detectBoundaries(
        context: Context,
        filePath: String,
        totalDurationMs: Long,
        sensitivity: String = "MEDIUM",
        minSilenceMs: Long = DEFAULT_MIN_SILENCE_MS,
        tailPaddingMs: Long = DEFAULT_TAIL_PADDING_MS
    ): List<Long> = withContext(Dispatchers.Default) {
        try {
            val boundaries = analyzeAudioPcmSilence(context, filePath, totalDurationMs, sensitivity, minSilenceMs, tailPaddingMs)
            if (boundaries.isNotEmpty()) {
                Log.d(TAG, "Detected ${boundaries.size} silence boundaries (sensitivity=$sensitivity, minPause=${minSilenceMs}ms, padding=${tailPaddingMs}ms) for $filePath")
                return@withContext boundaries
            }
        } catch (e: Exception) {
            Log.w(TAG, "Silence detection analysis failed, falling back: ${e.message}")
        }
        return@withContext generateFallbackSegments(totalDurationMs)
    }

    fun generateFallbackSegments(totalDurationMs: Long): List<Long> {
        val effectiveDuration = if (totalDurationMs > 0) totalDurationMs else 30000L
        val list = mutableListOf<Long>()
        var current = FALLBACK_SEGMENT_DURATION_MS
        while (current < effectiveDuration - 1500L) {
            list.add(current)
            current += FALLBACK_SEGMENT_DURATION_MS
        }
        list.add(effectiveDuration)
        return list.distinct().sorted()
    }

    fun getAudioDuration(context: Context, filePath: String): Long {
        val mmr = android.media.MediaMetadataRetriever()
        return try {
            if (filePath.startsWith("content://")) {
                mmr.setDataSource(context, Uri.parse(filePath))
            } else {
                mmr.setDataSource(filePath)
            }
            val durStr = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            durStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "Failed to retrieve duration for $filePath: ${e.message}")
            0L
        } finally {
            try {
                mmr.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun analyzeAudioPcmSilence(
        context: Context,
        filePath: String,
        totalDurationMs: Long,
        sensitivity: String,
        minSilenceMs: Long,
        tailPaddingMs: Long
    ): List<Long> {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var pfd: android.os.ParcelFileDescriptor? = null

        try {
            if (filePath.startsWith("content://")) {
                try {
                    pfd = context.contentResolver.openFileDescriptor(Uri.parse(filePath), "r")
                    if (pfd != null) {
                        extractor.setDataSource(pfd.fileDescriptor)
                    } else {
                        extractor.setDataSource(context, Uri.parse(filePath), null)
                    }
                } catch (e: Exception) {
                    try {
                        extractor.setDataSource(context, Uri.parse(filePath), null)
                    } catch (e2: Exception) {
                        return emptyList()
                    }
                }
            } else {
                val file = File(filePath)
                if (!file.exists()) {
                    return emptyList()
                }
                extractor.setDataSource(filePath)
            }

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                return emptyList()
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: return emptyList()
            val sampleRate = if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                44100
            }
            val channelCount = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            } else {
                2
            }

            val formatDurationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                audioFormat.getLong(MediaFormat.KEY_DURATION)
            } else 0L
            val formatDurationMs = formatDurationUs / 1000L
            val resolvedDurationMs = if (totalDurationMs > 0) totalDurationMs else formatDurationMs

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            val windows = mutableListOf<AudioWindow>()
            var sawInputEOS = false
            var sawOutputEOS = false

            // Samples needed per 50ms window (stepping by channelCount)
            val samplesPerWindow = ((sampleRate.toLong() * WINDOW_MS) / 1000L).toInt().coerceAtLeast(50)
            var currentWindowSumSquares = 0.0
            var currentWindowSampleCount = 0
            var currentWindowStartMs = 0L

            val shortArray = ShortArray(4096)
            var consecutiveEmptyOutputs = 0
            val startTimeWall = System.currentTimeMillis()
            val maxWallClockMs = 12000L

            while (!sawOutputEOS && (System.currentTimeMillis() - startTimeWall) < maxWallClockMs) {
                // Feed input
                if (!sawInputEOS) {
                    val inputIndex = codec.dequeueInputBuffer(1000L)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize <= 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInputEOS = true
                            } else {
                                val sampleTime = extractor.sampleTime
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                // Process output
                val outputIndex = codec.dequeueOutputBuffer(info, 1000L)
                if (outputIndex >= 0) {
                    consecutiveEmptyOutputs = 0
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true
                    }

                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && info.size > 0) {
                        outputBuffer.position(info.offset)
                        outputBuffer.limit(info.offset + info.size)
                        outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

                        val shortBuffer = outputBuffer.asShortBuffer()
                        while (shortBuffer.hasRemaining()) {
                            val toRead = minOf(shortBuffer.remaining(), shortArray.size)
                            shortBuffer.get(shortArray, 0, toRead)

                            var i = 0
                            while (i < toRead) {
                                val sample = shortArray[i].toDouble()
                                currentWindowSumSquares += sample * sample
                                currentWindowSampleCount++

                                if (currentWindowSampleCount >= samplesPerWindow) {
                                    val rms = sqrt(currentWindowSumSquares / currentWindowSampleCount)
                                    windows.add(AudioWindow(timeMs = currentWindowStartMs, rms = rms))
                                    currentWindowStartMs += WINDOW_MS
                                    currentWindowSumSquares = 0.0
                                    currentWindowSampleCount = 0

                                    if (resolvedDurationMs > 0 && currentWindowStartMs > resolvedDurationMs) {
                                        sawOutputEOS = true
                                        break
                                    }
                                }
                                i += channelCount
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                } else {
                    if (sawInputEOS) {
                        consecutiveEmptyOutputs++
                        if (consecutiveEmptyOutputs > 60) {
                            sawOutputEOS = true
                        }
                    }
                }
            }

            if (currentWindowSampleCount > 0) {
                val rms = sqrt(currentWindowSumSquares / currentWindowSampleCount)
                windows.add(AudioWindow(timeMs = currentWindowStartMs, rms = rms))
            }

            if (windows.isNotEmpty()) {
                val nonZeroRms = windows.map { it.rms }.filter { it > 1.0 }.sorted()
                // Use 95th percentile of RMS to disregard isolated loud pops/clicks
                val p95Index = if (nonZeroRms.isNotEmpty()) {
                    (nonZeroRms.size * 0.95).toInt().coerceIn(0, nonZeroRms.size - 1)
                } else 0
                val speechPeakRms = if (nonZeroRms.isNotEmpty()) nonZeroRms[p95Index] else 100.0

                // 20th percentile for background ambient noise floor
                val p20Index = if (nonZeroRms.isNotEmpty()) {
                    (nonZeroRms.size * 0.20).toInt().coerceIn(0, nonZeroRms.size - 1)
                } else 0
                val noiseFloor = if (nonZeroRms.isNotEmpty()) nonZeroRms[p20Index] else 0.0

                // True silence threshold (sound below this is ambient pause)
                val silenceThreshold = (noiseFloor * 1.30).coerceAtLeast(noiseFloor + 4.0)
                val dynamicRange = (speechPeakRms - silenceThreshold).coerceAtLeast(15.0)

                val points = windows.map { w ->
                    val amp = if (w.rms <= silenceThreshold) {
                        0.0f
                    } else {
                        val linearRatio = ((w.rms - silenceThreshold) / dynamicRange).coerceIn(0.0, 1.2)
                        // Dynamic loudness: soft sounds remain low, loud speech reaches high
                        val curved = Math.pow(linearRatio.coerceAtMost(1.0), 0.85).toFloat()
                        curved.coerceIn(0.04f, 1.0f)
                    }
                    WaveformPoint(
                        timeMs = w.timeMs,
                        amplitude = amp
                    )
                }
                waveformCache[filePath] = points
            }

            return extractBoundariesFromWindows(windows, resolvedDurationMs, sensitivity, minSilenceMs, tailPaddingMs)
        } finally {
            try {
                pfd?.close()
            } catch (e: Exception) {
                // Ignore
            }
            try {
                codec?.stop()
                codec?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Codec release exception: ${e.message}")
            }
            try {
                extractor.release()
            } catch (e: Exception) {
                Log.w(TAG, "Extractor release exception: ${e.message}")
            }
        }
    }

    fun extractBoundariesFromWindows(
        windows: List<AudioWindow>,
        totalDurationMs: Long,
        sensitivity: String = "MEDIUM",
        minSilenceMs: Long = DEFAULT_MIN_SILENCE_MS,
        tailPaddingMs: Long = DEFAULT_TAIL_PADDING_MS
    ): List<Long> {
        if (windows.isEmpty()) return generateFallbackSegments(totalDurationMs)

        val effectiveDuration = if (totalDurationMs > 0) totalDurationMs else (windows.last().timeMs + WINDOW_MS)
        val sortedRms = windows.map { it.rms }.sorted()
        val peakRms = sortedRms.lastOrNull() ?: 1.0

        if (peakRms < 10.0) {
            // Audio is nearly silent throughout
            return generateFallbackSegments(effectiveDuration)
        }

        // Adaptive noise floor: estimated using the 15th percentile of RMS
        val noiseFloor = if (sortedRms.isNotEmpty()) {
            val idx = (sortedRms.size * 0.15).toInt().coerceIn(0, sortedRms.size - 1)
            sortedRms[idx]
        } else 0.0

        // 90th percentile to avoid single loud clicks/spikes
        val p90Rms = if (sortedRms.isNotEmpty()) {
            val idx = (sortedRms.size * 0.90).toInt().coerceIn(0, sortedRms.size - 1)
            sortedRms[idx]
        } else peakRms

        val dynamicRange = (p90Rms - noiseFloor).coerceAtLeast(10.0)

        // Threshold factor based on sensitivity:
        // "HIGH": lower threshold to catch quiet trailing word endings and soft speech
        // "MEDIUM": balanced default
        // "LOW": higher threshold to cut through noisy rooms
        val factor = when (sensitivity.uppercase()) {
            "HIGH" -> 0.025
            "LOW" -> 0.075
            else -> 0.040
        }
        val silenceThreshold = maxOf(noiseFloor * 1.35, noiseFloor + dynamicRange * factor).coerceAtLeast(35.0)
        val minSilenceWindows = (minSilenceMs / WINDOW_MS).toInt().coerceAtLeast(2)

        // Find contiguous silence spans
        data class SilenceSpan(val startMs: Long, val endMs: Long)
        val silenceSpans = mutableListOf<SilenceSpan>()

        var inSilence = false
        var silenceStartIndex = 0

        for (i in windows.indices) {
            val isSilent = windows[i].rms < silenceThreshold
            if (isSilent && !inSilence) {
                inSilence = true
                silenceStartIndex = i
            } else if (!isSilent && inSilence) {
                inSilence = false
                val count = i - silenceStartIndex
                if (count >= minSilenceWindows) {
                    silenceSpans.add(
                        SilenceSpan(
                            startMs = windows[silenceStartIndex].timeMs,
                            endMs = windows[i - 1].timeMs + WINDOW_MS
                        )
                    )
                }
            }
        }
        if (inSilence) {
            val count = windows.size - silenceStartIndex
            if (count >= minSilenceWindows) {
                silenceSpans.add(
                    SilenceSpan(
                        startMs = windows[silenceStartIndex].timeMs,
                        endMs = windows.last().timeMs + WINDOW_MS
                    )
                )
            }
        }

        val boundaries = mutableListOf<Long>()
        var lastBoundary = 0L

        for (span in silenceSpans) {
            val spanLength = span.endMs - span.startMs

            // Safe cut location:
            // Ensure tailPaddingMs after speech ends so trailing phonemes ('s', 't', 'k', breaths) are NEVER clipped.
            // Also leave at least 60ms head buffer before next speech starts.
            val safeStart = (span.startMs + tailPaddingMs).coerceAtMost(span.startMs + (spanLength * 2 / 3))
            val safeEnd = (span.endMs - 60L).coerceAtLeast(safeStart)

            // Pick the quietest audio window inside the safe gap
            val bestCut = windows.filter { it.timeMs in safeStart..safeEnd }.minByOrNull { it.rms }?.timeMs
                ?: ((safeStart + safeEnd) / 2L)

            val candidateCut = bestCut.coerceIn(span.startMs + 40L, span.endMs - 30L)
            var segmentLength = candidateCut - lastBoundary

            if (segmentLength >= MIN_SEGMENT_DURATION_MS) {
                // If continuous speech exceeds MAX_SEGMENT_DURATION_MS (e.g. 18s continuous talking without pause),
                // split gently at the quietest dip in the middle
                while (segmentLength > MAX_SEGMENT_DURATION_MS) {
                    val searchStart = lastBoundary + 3500L
                    val searchEnd = candidateCut - 3000L
                    val intermediate = if (searchEnd > searchStart) {
                        windows.filter { it.timeMs in searchStart..searchEnd }.minByOrNull { it.rms }?.timeMs
                    } else null

                    val cutPoint = intermediate ?: (lastBoundary + 8000L)
                    boundaries.add(cutPoint)
                    lastBoundary = cutPoint
                    segmentLength = candidateCut - lastBoundary
                }

                boundaries.add(candidateCut)
                lastBoundary = candidateCut
            }
        }

        // Fill remaining tail of the audio file to effectiveDuration
        var tailLength = effectiveDuration - lastBoundary
        while (tailLength > MAX_SEGMENT_DURATION_MS) {
            val cutPoint = lastBoundary + 8000L
            boundaries.add(cutPoint)
            lastBoundary = cutPoint
            tailLength = effectiveDuration - lastBoundary
        }
        if (effectiveDuration > lastBoundary + 800L) {
            boundaries.add(effectiveDuration)
        }

        return boundaries.distinct().sorted()
    }

    data class AudioWindow(val timeMs: Long, val rms: Double)
}
