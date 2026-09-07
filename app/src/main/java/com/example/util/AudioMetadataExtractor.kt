package com.example.util

import android.media.MediaMetadataRetriever
import java.io.File
import java.util.Locale

data class TrackMetadata(
    val title: String,
    val duration: String,
    val artist: String,
    val album: String,
    val fileName: String,
    val filePath: String,
    val fileSize: String
)

object AudioMetadataExtractor {
    fun extract(filePath: String): TrackMetadata {
        val file = File(filePath)
        if (!file.exists()) {
            return TrackMetadata(
                title = "Unknown",
                duration = "Unknown",
                artist = "Unknown",
                album = "Unknown",
                fileName = file.name,
                filePath = filePath,
                fileSize = "0 Bytes"
            )
        }

        val retriever = MediaMetadataRetriever()
        var title: String? = null
        var duration: String? = null
        var artist: String? = null
        var album: String? = null

        try {
            retriever.setDataSource(filePath)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            
            val durationMsStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (durationMsStr != null) {
                val durationMs = durationMsStr.toLongOrNull() ?: 0L
                val seconds = (durationMs / 1000) % 60
                val minutes = (durationMs / (1000 * 60)) % 60
                val hours = (durationMs / (1000 * 60 * 60))
                duration = if (hours > 0) {
                    String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
                } else {
                    String.format(Locale.US, "%02d:%02d", minutes, seconds)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val sizeInBytes = file.length()
        val sizeText = when {
            sizeInBytes >= 1024 * 1024 -> String.format(Locale.US, "%.2f MB", sizeInBytes.toFloat() / (1024 * 1024))
            sizeInBytes >= 1024 -> String.format(Locale.US, "%.2f KB", sizeInBytes.toFloat() / 1024)
            else -> "$sizeInBytes Bytes"
        }

        return TrackMetadata(
            title = title ?: file.name.substringBeforeLast("."),
            duration = duration ?: "Unknown",
            artist = artist ?: "Unknown",
            album = album ?: "Unknown",
            fileName = file.name,
            filePath = filePath,
            fileSize = sizeText
        )
    }
}
