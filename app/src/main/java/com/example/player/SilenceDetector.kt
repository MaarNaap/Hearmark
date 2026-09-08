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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

object SilenceDetector {
    private const val TAG = "SilenceDetector"

    private const val WINDOW_MS = 100L // 100ms analysis window
    private const val MIN_SILENCE_DURATION_MS = 300L // At least 300ms of silence for a pause boundary
    private const val MIN_SEGMENT_DURATION_MS = 2500L // At least 2.5s for a meaningful speech segment
    private const val MAX_SEGMENT_DURATION_MS = 9000L // At most 9s before forcing a cut at the quietest point
    private const val FALLBACK_SEGMENT_DURATION_MS = 5000L // 5s segments if decoding fails

    suspend fun detectBoundaries(
        context: Context,
        filePath: String,
        totalDurationMs: Long
    ): List<Long> = withContext(Dispatchers.Default) {
        try {
            val boundaries = analyzeAudioPcmSilence(context, filePath, totalDurationMs)
            if (boundaries.isNotEmpty()) {
                Log.d(TAG, "Detected ${boundaries.size} silence boundaries for $filePath")
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
        return list
    }

    private fun analyzeAudioPcmSilence(
        context: Context,
        filePath: String,
        totalDurationMs: Long
    ): List<Long> {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            if (filePath.startsWith("content://")) {
                extractor.setDataSource(context, Uri.parse(filePath), null)
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
                audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else {
                2
            }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            val windows = mutableListOf<AudioWindow>()
            var sawInputEOS = false
            var sawOutputEOS = false

            val samplesPerWindow = ((sampleRate.toLong() * channelCount * WINDOW_MS) / 1000L).toInt().coerceAtLeast(100)
            var currentWindowSumSquares = 0.0
            var currentWindowSampleCount = 0
            var currentWindowStartMs = 0L

            val maxDurationLimitMs = if (totalDurationMs > 0) totalDurationMs else 3600000L // 1 hour max
            val startTimeWall = System.currentTimeMillis()

            while (!sawOutputEOS && (System.currentTimeMillis() - startTimeWall) < 15000L) {
                // Feed input
                if (!sawInputEOS) {
                    val inputIndex = codec.dequeueInputBuffer(4000L)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
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
                val outputIndex = codec.dequeueOutputBuffer(info, 4000L)
                if (outputIndex >= 0) {
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true
                    }

                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && info.size > 0) {
                        outputBuffer.position(info.offset)
                        outputBuffer.limit(info.offset + info.size)
                        outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

                        val shortBuffer = outputBuffer.asShortBuffer()
                        val numShorts = shortBuffer.remaining()

                        var i = 0
                        while (i < numShorts) {
                            val sample = shortBuffer.get()
                            currentWindowSumSquares += (sample.toDouble() * sample.toDouble())
                            currentWindowSampleCount++

                            if (currentWindowSampleCount >= samplesPerWindow) {
                                val rms = sqrt(currentWindowSumSquares / currentWindowSampleCount)
                                windows.add(AudioWindow(timeMs = currentWindowStartMs, rms = rms))
                                currentWindowStartMs += WINDOW_MS
                                currentWindowSumSquares = 0.0
                                currentWindowSampleCount = 0

                                if (currentWindowStartMs > maxDurationLimitMs) {
                                    sawOutputEOS = true
                                    break
                                }
                            }
                            i++
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                }
            }

            if (currentWindowSampleCount > 0) {
                val rms = sqrt(currentWindowSumSquares / currentWindowSampleCount)
                windows.add(AudioWindow(timeMs = currentWindowStartMs, rms = rms))
            }

            return extractBoundariesFromWindows(windows, totalDurationMs)
        } finally {
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
        totalDurationMs: Long
    ): List<Long> {
        if (windows.isEmpty()) return generateFallbackSegments(totalDurationMs)

        val peakRms = windows.maxOfOrNull { it.rms } ?: 1.0
        if (peakRms < 10.0) {
            // Audio is nearly silent throughout
            return generateFallbackSegments(totalDurationMs)
        }

        // Adaptive silence threshold: 7% of peak RMS or baseline 250
        val silenceThreshold = maxOf(250.0, peakRms * 0.07)
        val minSilenceWindows = (MIN_SILENCE_DURATION_MS / WINDOW_MS).toInt().coerceAtLeast(2)

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

        val effectiveDuration = if (totalDurationMs > 0) totalDurationMs else (windows.last().timeMs + WINDOW_MS)
        val boundaries = mutableListOf<Long>()
        var lastBoundary = 0L

        for (span in silenceSpans) {
            val candidateCut = (span.startMs + span.endMs) / 2L
            val segmentLength = candidateCut - lastBoundary

            if (segmentLength >= MIN_SEGMENT_DURATION_MS) {
                // If segment exceeds MAX_SEGMENT_DURATION_MS, we could have inserted an intermediate cut
                if (segmentLength > MAX_SEGMENT_DURATION_MS) {
                    // Find intermediate minimum RMS between lastBoundary + 3000 and candidateCut - 2000
                    val subStart = lastBoundary + 3000L
                    val subEnd = candidateCut - 2000L
                    val intermediate = windows.filter { it.timeMs in subStart..subEnd }.minByOrNull { it.rms }
                    if (intermediate != null) {
                        boundaries.add(intermediate.timeMs)
                        lastBoundary = intermediate.timeMs
                    }
                }
                boundaries.add(candidateCut)
                lastBoundary = candidateCut
            }
        }

        // Check tail segment
        if (effectiveDuration - lastBoundary > MIN_SEGMENT_DURATION_MS) {
            boundaries.add(effectiveDuration)
        } else if (boundaries.isNotEmpty()) {
            boundaries[boundaries.lastIndex] = effectiveDuration
        } else {
            boundaries.add(effectiveDuration)
        }

        return boundaries.distinct().sorted()
    }

    data class AudioWindow(val timeMs: Long, val rms: Double)
}
