package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "folders",
    indices = [Index(value = ["parentFolderId"])]
)
data class Folder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderPath: String, // Local folder absolute path
    val folderName: String,
    val parentFolderId: Long? = null
)

@Entity(
    tableName = "audio_tracks",
    indices = [
        Index(value = ["parentFolderId"]),
        Index(value = ["filePath"]),
        Index(value = ["parentTrackId"])
    ]
)
data class AudioTrack(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val fileName: String,
    val duration: Long, // in milliseconds
    val playCount: Int = 0,
    val lastPosition: Long = 0, // last position resume timestamp
    val parentFolderId: Long? = null,
    val isMissing: Boolean = false,
    val isIndependent: Boolean = true,
    // Comma-separated list of completed segment indices (e.g. "1,2,5,99")
    val listenedSegments: String = "",
    val subtitlePath: String? = null,
    val subtitleContent: String? = null,
    val subtitleOffsetMs: Long = 0L,
    val startOffsetMs: Long = 0L,
    val endOffsetMs: Long? = null,
    val isVirtualScene: Boolean = false,
    val parentTrackId: Long? = null,
    val sceneNumber: Int? = null,
    val practiceSegments: String? = null,
    val currentPlayActualListeningMs: Long = 0L
) {
    fun getPracticeSegmentsSource(): String? {
        if (practiceSegments.isNullOrBlank()) return null
        return when {
            practiceSegments.startsWith("SUB:") -> "SUBTITLES"
            practiceSegments.startsWith("SIL:") -> "SILENCE"
            practiceSegments.startsWith("MAN:") -> "MANUAL"
            else -> null
        }
    }

    fun getPracticeSegmentsList(): List<Long> {
        if (practiceSegments.isNullOrBlank()) return emptyList()
        val raw = if (practiceSegments.contains(":")) {
            practiceSegments.substringAfter(":")
        } else {
            practiceSegments
        }
        return raw.split(",")
            .mapNotNull { it.trim().toLongOrNull() }
            .filter { it > 0 }
            .distinct()
            .sorted()
    }

    fun getAdaptiveNumSegments(): Int {
        val durationS = duration / 1000
        return if (durationS > 0) minOf(durationS.toInt(), 100).coerceAtLeast(10) else 100
    }

    fun getListenedCount(): Int {
        return getListenedCount(getAdaptiveNumSegments())
    }

    fun getProgressPercent(): Int {
        return getProgressPercent(getAdaptiveNumSegments())
    }

    /**
     * Efficiently counts unique listened segments without string splitting or allocating intermediate Sets/Lists.
     */
    fun getListenedCount(numSegments: Int): Int {
        if (listenedSegments.isEmpty() || numSegments <= 0) return 0
        
        // Fast path for small segment counts (up to 128) using two 64-bit Long bitmasks: zero GC allocations
        if (numSegments <= 128) {
            var mask0 = 0L
            var mask1 = 0L
            var current = 0
            var hasDigits = false
            for (i in 0 until listenedSegments.length) {
                val ch = listenedSegments[i]
                if (ch in '0'..'9') {
                    current = current * 10 + (ch - '0')
                    hasDigits = true
                } else if (ch == ',') {
                    if (hasDigits && current < numSegments) {
                        if (current < 64) {
                            mask0 = mask0 or (1L shl current)
                        } else {
                            mask1 = mask1 or (1L shl (current - 64))
                        }
                    }
                    current = 0
                    hasDigits = false
                }
            }
            if (hasDigits && current < numSegments) {
                if (current < 64) {
                    mask0 = mask0 or (1L shl current)
                } else {
                    mask1 = mask1 or (1L shl (current - 64))
                }
            }
            return java.lang.Long.bitCount(mask0) + java.lang.Long.bitCount(mask1)
        }

        // General path for larger segment sets using Java BitSet (minimal memory allocation)
        val bitSet = java.util.BitSet(numSegments)
        var current = 0
        var hasDigits = false
        for (i in 0 until listenedSegments.length) {
            val ch = listenedSegments[i]
            if (ch in '0'..'9') {
                current = current * 10 + (ch - '0')
                hasDigits = true
            } else if (ch == ',') {
                if (hasDigits && current < numSegments) {
                    bitSet.set(current)
                }
                current = 0
                hasDigits = false
            }
        }
        if (hasDigits && current < numSegments) {
            bitSet.set(current)
        }
        return bitSet.cardinality()
    }

    /**
     * Parses listened segment indices into a java.util.BitSet efficiently.
     */
    fun getListenedBitSet(maxSegments: Int = 100): java.util.BitSet {
        val bitSet = java.util.BitSet(maxSegments)
        if (listenedSegments.isEmpty()) return bitSet
        var current = 0
        var hasDigits = false
        for (i in 0 until listenedSegments.length) {
            val ch = listenedSegments[i]
            if (ch in '0'..'9') {
                current = current * 10 + (ch - '0')
                hasDigits = true
            } else if (ch == ',') {
                if (hasDigits && current < maxSegments) {
                    bitSet.set(current)
                }
                current = 0
                hasDigits = false
            }
        }
        if (hasDigits && current < maxSegments) {
            bitSet.set(current)
        }
        return bitSet
    }

    fun getProgressPercent(numSegments: Int): Int {
        if (numSegments <= 0) return 0
        val listenedSize = getListenedCount(numSegments)
        return ((listenedSize * 100) / numSegments).coerceIn(0, 100)
    }

    fun getDisplayTitle(): String {
        return if (isVirtualScene) {
            fileName.ifBlank { "Scene ${sceneNumber ?: id}" }
        } else {
            if (fileName.isNotBlank()) fileName.substringBeforeLast(".")
            else filePath.substringAfterLast("/").substringBeforeLast(".")
        }
    }
}

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    indices = [Index(value = ["trackId"])]
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: Long,
    val displayOrder: Int = 0
)
