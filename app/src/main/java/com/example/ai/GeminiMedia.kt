package com.example.ai

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.os.Build
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

object GeminiMedia {
    private const val TAG = "GeminiService"

    fun getMediaDurationMs(file: File): Long {
        var retriever: MediaMetadataRetriever? = null
        return try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            time?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            try {
                Log.w(TAG, "Failed to retrieve media duration for ${file.name}: ${e.message}")
            } catch (_: Throwable) {}
            0L
        } finally {
            try { retriever?.release() } catch (ignored: Exception) {}
        }
    }

    fun extractAudioFromVideoIfPossible(videoFile: File): File {
        val ext = videoFile.extension.lowercase()
        val isVideo = ext in listOf("mp4", "mkv", "mov", "webm", "3gp", "avi", "m4v")
        if (!isVideo || videoFile.length() < 64 * 1024L) return videoFile

        val cacheDir = videoFile.parentFile ?: File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        val audioOutM4a = File(cacheDir, "extracted_${videoFile.nameWithoutExtension}_${videoFile.lastModified()}.m4a")
        if (audioOutM4a.exists() && audioOutM4a.length() > 1024) {
            Log.d(TAG, "Reusing already extracted audio: ${audioOutM4a.absolutePath}")
            return audioOutM4a
        }
        val audioOutWebm = File(cacheDir, "extracted_${videoFile.nameWithoutExtension}_${videoFile.lastModified()}.webm")
        if (audioOutWebm.exists() && audioOutWebm.length() > 1024) {
            Log.d(TAG, "Reusing already extracted webm audio: ${audioOutWebm.absolutePath}")
            return audioOutWebm
        }

        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var targetOutFile: File = audioOutM4a
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(videoFile.absolutePath)
            var audioTrackIdx = -1
            var format: MediaFormat? = null
            var audioMime = ""

            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIdx = i
                    format = f
                    audioMime = mime
                    break
                }
            }

            if (audioTrackIdx != -1 && format != null) {
                extractor.selectTrack(audioTrackIdx)
                val useWebm = audioMime.contains("opus", ignoreCase = true) || audioMime.contains("vorbis", ignoreCase = true)
                targetOutFile = if (useWebm) audioOutWebm else audioOutM4a
                val outputFormat = if (useWebm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
                } else {
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                }

                muxer = MediaMuxer(targetOutFile.absolutePath, outputFormat)
                val muxerTrackIdx = muxer.addTrack(format)
                muxer.start()

                val maxBuf = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                } else {
                    256 * 1024
                }
                val buf = ByteBuffer.allocate(maxBuf.coerceAtLeast(64 * 1024))
                val bufInfo = MediaCodec.BufferInfo()

                while (true) {
                    bufInfo.offset = 0
                    bufInfo.size = extractor.readSampleData(buf, 0)
                    if (bufInfo.size < 0) break
                    bufInfo.presentationTimeUs = extractor.sampleTime
                    bufInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(muxerTrackIdx, buf, bufInfo)
                    extractor.advance()
                }

                muxer.stop()
                muxer.release()
                muxer = null
                extractor.release()
                extractor = null

                if (targetOutFile.exists() && targetOutFile.length() > 1024) {
                    Log.i(TAG, "Successfully demuxed audio from video: original=${videoFile.length()} bytes -> audio=${targetOutFile.length()} bytes")
                    return targetOutFile
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct audio extraction from video failed or not supported, using original file: ${e.message}")
            try { targetOutFile.delete() } catch (_: Exception) {}
        } finally {
            try { extractor?.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
        return videoFile
    }

    /**
     * Slicing a long audio track into a standalone time-window clip [startMs, endMs] so each chunk
     * sends only ~3-5MB of audio instead of re-sending the entire 60-minute file on every chunk.
     */
    fun extractAudioTimeSliceIfPossible(
        sourceFile: File,
        startMs: Long,
        endMs: Long
    ): File? {
        if (endMs <= startMs) return null
        val cacheDir = sourceFile.parentFile ?: File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var sliceFile: File? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(sourceFile.absolutePath)
            var audioTrackIdx = -1
            var format: MediaFormat? = null
            var audioMime = ""

            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIdx = i
                    format = f
                    audioMime = mime
                    break
                }
            }

            if (audioTrackIdx == -1 || format == null) return null

            val useWebm = audioMime.contains("opus", ignoreCase = true) || audioMime.contains("vorbis", ignoreCase = true)
            val canMuxM4a = audioMime.contains("mp4a", ignoreCase = true) || audioMime.contains("aac", ignoreCase = true) || audioMime.contains("3gpp", ignoreCase = true) || audioMime.contains("amr", ignoreCase = true)
            if (!useWebm && !canMuxM4a) {
                // For raw MP3 files, slice approximately by byte offset if file is large
                if (sourceFile.extension.equals("mp3", ignoreCase = true)) {
                    val totalDurMs = getMediaDurationMs(sourceFile)
                    if (totalDurMs > 0 && endMs <= totalDurMs + 5000L) {
                        val fileLen = sourceFile.length()
                        val startByte = ((startMs.toDouble() / totalDurMs.toDouble()) * fileLen).toLong().coerceIn(0L, fileLen)
                        val endByte = ((endMs.toDouble() / totalDurMs.toDouble()) * fileLen).toLong().coerceIn(startByte, fileLen)
                        if (endByte - startByte > 16 * 1024L) {
                            val mp3Slice = File(cacheDir, "slice_${sourceFile.nameWithoutExtension}_${startMs}_${endMs}.mp3")
                            RandomAccessFile(sourceFile, "r").use { raf ->
                                raf.seek(startByte)
                                mp3Slice.outputStream().use { out ->
                                    val buffer = ByteArray(64 * 1024)
                                    var remaining = endByte - startByte
                                    while (remaining > 0) {
                                        val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                                        val read = raf.read(buffer, 0, toRead)
                                        if (read <= 0) break
                                        out.write(buffer, 0, read)
                                        remaining -= read
                                    }
                                }
                            }
                            if (mp3Slice.exists() && mp3Slice.length() > 4096L) {
                                return mp3Slice
                            }
                        }
                    }
                }
                return null
            }

            val ext = if (useWebm) "webm" else "m4a"
            sliceFile = File(cacheDir, "slice_${sourceFile.nameWithoutExtension}_${startMs}_${endMs}.$ext")
            val outputFormat = if (useWebm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
            } else {
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            }

            extractor.selectTrack(audioTrackIdx)
            val startUs = startMs * 1000L
            val endUs = endMs * 1000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            muxer = MediaMuxer(sliceFile.absolutePath, outputFormat)
            val muxerTrackIdx = muxer.addTrack(format)
            muxer.start()

            val maxBuf = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else {
                256 * 1024
            }
            val buf = ByteBuffer.allocate(maxBuf.coerceAtLeast(64 * 1024))
            val bufInfo = MediaCodec.BufferInfo()
            var basePtsUs = -1L

            while (true) {
                bufInfo.offset = 0
                bufInfo.size = extractor.readSampleData(buf, 0)
                if (bufInfo.size < 0) break
                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs > endUs) break
                if (sampleTimeUs >= startUs) {
                    if (basePtsUs < 0L) basePtsUs = sampleTimeUs
                    bufInfo.presentationTimeUs = (sampleTimeUs - basePtsUs).coerceAtLeast(0L)
                    bufInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(muxerTrackIdx, buf, bufInfo)
                }
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            muxer = null
            extractor.release()
            extractor = null

            if (sliceFile.exists() && sliceFile.length() > 2048L) {
                return sliceFile
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio time slice extraction failed, falling back to full file: ${e.message}")
            try { sliceFile?.delete() } catch (_: Exception) {}
        } finally {
            try { extractor?.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
        return null
    }
}
