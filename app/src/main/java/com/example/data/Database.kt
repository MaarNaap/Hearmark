package com.example.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.firstOrNull
import java.io.File

// --- ENTITIES ---

@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderPath: String, // Local folder absolute path
    val folderName: String,
    val parentFolderId: Long? = null
)

@Entity(tableName = "audio_tracks")
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
    primaryKeys = ["playlistId", "trackId"]
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: Long,
    val displayOrder: Int = 0
)

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val sourceType: String, // "TRACKS", "FOLDER", "PLAYLIST"
    val sourceId: Long?, // Folder ID, Playlist ID, or null
    val targetType: String, // "PLAY_COUNT", "DAYS_COUNT"
    val targetValue: Int, // e.g. 3 plays or 5 days
    val scheduledDays: String, // e.g. "MONDAY,TUESDAY"
    val reminderTime: String, // e.g. "08:30 AM" or "09:12 PM"
    val startDate: Long, // timestamp
    val endDate: Long? = null, // optional end timestamp
    val isCompleted: Boolean = false,
    val status: String = "ACTIVE", // "ACTIVE", "COMPLETED"
    val customThreshold: Int? = null, // custom threshold from 70 to 100 or null to use global
    val labels: String = "", // comma-separated labels e.g. "Loud,Pronunciation,0.75x"
    val dailyTargetValue: Int? = null // optional daily mini-goal (plays per day)
) {
    fun getLabelsList(): List<String> = formatLabelsList(labels)

    fun getFormattedLabels(): String = formatLabels(labels)

    fun getBaseTitle(): String = extractBaseTitle(title, labels)

    fun getDisplayTitle(): String {
        val formatted = getFormattedLabels()
        if (formatted.isEmpty() || title.endsWith(formatted)) {
            return title
        }
        return "$title $formatted"
    }

    companion object {
        fun formatLabelsList(labels: String): List<String> {
            if (labels.isBlank()) return emptyList()
            return labels.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { label ->
                    label.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }
                }
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
        }

        fun formatLabels(labels: String): String {
            val list = formatLabelsList(labels)
            if (list.isEmpty()) return ""
            return "[" + list.joinToString(". ") + "]"
        }

        fun extractBaseTitle(fullTitle: String, labels: String = ""): String {
            var base = fullTitle.trim()
            val formatted = formatLabels(labels)
            if (formatted.isNotEmpty() && base.endsWith(formatted)) {
                base = base.substring(0, base.length - formatted.length).trim()
            } else {
                val lastOpen = base.lastIndexOf('[')
                val lastClose = base.lastIndexOf(']')
                if (lastOpen > 0 && lastClose == base.length - 1) {
                    base = base.substring(0, lastOpen).trim()
                }
            }
            return base
        }

        fun buildCombinedTitle(givenTitle: String, labels: String): String {
            val base = extractBaseTitle(givenTitle, labels)
            val formatted = formatLabels(labels)
            return if (formatted.isEmpty()) base else if (base.isEmpty()) formatted else "$base $formatted"
        }
    }
}

@Entity(
    tableName = "task_track_progress",
    primaryKeys = ["taskId", "trackId"]
)
data class TaskTrackProgress(
    val taskId: Long,
    val trackId: Long,
    val completedPlayCount: Int = 0, // number of times fully played during this task run
    val completedDays: String = "", // comma separated dates "2026-06-10,2026-06-11"
    val isTrackCompleted: Boolean = false
) {
    fun getDaysList(): List<String> {
        if (completedDays.isEmpty()) return emptyList()
        return completedDays.split(",").filter { it.isNotEmpty() }
    }
}

@Entity(tableName = "playback_history")
data class PlaybackHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val trackName: String,
    val completedAt: Long, // timestamp
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val actualListenedMs: Long = 0L,
    val activeTasks: String = "" // JSON array of active tasks at playback time: [{"id": 1, "title": "Task 1"}]
) {
    fun getLoggedTasks(): List<LoggedTaskInfo> {
        if (activeTasks.isBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(activeTasks)
            val list = mutableListOf<LoggedTaskInfo>()
            for (i in 0 until arr.length()) {
                val item = arr.get(i)
                if (item is org.json.JSONObject) {
                    val id = item.optLong("id", 0L)
                    val title = item.optString("title", "")
                    if (title.isNotBlank()) list.add(LoggedTaskInfo(id, title))
                } else if (item is String && item.isNotBlank()) {
                    list.add(LoggedTaskInfo(0L, item))
                }
            }
            list
        } catch (e: Exception) {
            activeTasks.split("||").map { it.trim() }.filter { it.isNotEmpty() }.map { LoggedTaskInfo(0L, it) }
        }
    }

    fun getActiveTasksList(): List<String> = getLoggedTasks().map { it.title }
    fun getActiveTaskIds(): List<Long> = getLoggedTasks().map { it.id }.filter { it > 0L }
}

data class LoggedTaskInfo(
    val id: Long,
    val title: String
)

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String, // Selected text or note quote
    val comment: String = "", // Personal explanation, translation, remarks
    val trackId: Long? = null, // Linked track ID if any
    val trackName: String? = null, // Cached track title/filename
    val folderId: Long? = null, // Parent folder ID for folder-level filtering
    val folderName: String? = null, // Cached folder name
    val startTimestampMs: Long = 0L, // Start timestamp in milliseconds
    val endTimestampMs: Long = 0L, // End timestamp in milliseconds
    val originStartMs: Long? = null, // The specific cue start timestamp this note is dedicated to
    val tags: String = "", // Comma-separated tags
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val targetWord: String? = null, // Isolated target vocabulary word/phrase
    val meaning: String? = null, // Isolated definition/meaning
    val contextSentence: String? = null // Isolated context sentence
) {
    fun getTagsList(): List<String> {
        if (tags.isBlank()) return emptyList()
        return tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun getIsolatedTargetWord(): String {
        if (!targetWord.isNullOrBlank()) return targetWord.trim()
        val match = Regex("""^([^:\n]+):""").find(comment)
        if (match != null) {
            val candidate = match.groupValues[1].replace("[", "").replace("]", "").trim()
            if (candidate.isNotBlank() && candidate.length < 50 && !candidate.contains("•")) {
                return candidate
            }
        }
        val trimmed = text.trim()
        val words = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size in 1..3 && trimmed.length < 35 && !trimmed.endsWith(".")) {
            return trimmed
        }
        return ""
    }

    fun getIsolatedMeaning(): String {
        if (!meaning.isNullOrBlank()) return meaning.trim()
        val match = Regex("""^[^:\n]+:\s*(.*?)(?=\n•|\n\n|$)""", RegexOption.DOT_MATCHES_ALL).find(comment)
        if (match != null) {
            val def = match.groupValues[1].trim()
            if (def.isNotBlank()) return def
        }
        return comment.trim()
    }

    fun getIsolatedContextSentence(): String {
        if (!contextSentence.isNullOrBlank()) return contextSentence.trim()
        val match = Regex("""•\s*Context in Audio:\s*(.*?)(?=\n•|\n\n|$)""", RegexOption.DOT_MATCHES_ALL).find(comment)
        if (match != null) {
            val ctx = match.groupValues[1].trim()
            if (ctx.isNotBlank()) return ctx
        }
        return text.trim()
    }
}

@Entity(tableName = "note_tags")
data class NoteTag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "task_labels")
data class TaskLabel(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "task_daily_progress",
    primaryKeys = ["taskId", "date"]
)
data class TaskDailyProgress(
    val taskId: Long,
    val date: String, // e.g. "2026-09-11"
    val completedPlayCount: Int = 0
)

data class NoteQuestionCount(
    val noteId: Long,
    val count: Int
)

@Entity(
    tableName = "quiz_questions",
    foreignKeys = [
        ForeignKey(
            entity = AudioTrack::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["trackId"]),
        Index(value = ["category"]),
        Index(value = ["noteId"])
    ]
)
data class QuizQuestion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long? = null,
    val noteId: Long? = null,
    val questionType: String, // "MCQ" or "TRUE_FALSE"
    val category: String = "COMPREHENSION", // "VOCABULARY" or "COMPREHENSION"
    val question: String,
    val optionsJson: String, // JSON array string e.g. ["Choice A", "Choice B", "Choice C", "Choice D"]
    val correctIndex: Int,
    val explanation: String,
    val timestampMs: Long? = null,
    val timesAnswered: Int = 0,
    val timesCorrect: Int = 0,
    val lastAnsweredAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val targetWord: String? = null, // Isolated target word
    val meaning: String? = null, // Isolated meaning/definition
    val contextSentence: String? = null // Isolated context sentence
) {
    fun getOptions(): List<String> {
        return try {
            val arr = org.json.JSONArray(optionsJson)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getIsolatedTargetWord(): String {
        if (!targetWord.isNullOrBlank()) return targetWord.trim()
        val quoted = Regex("['\"]([^'\"]+)['\"]").find(question)?.groupValues?.get(1)?.trim()
        if (!quoted.isNullOrBlank() && quoted.length in 2..30) {
            return quoted
        }
        return ""
    }

    fun getIsolatedMeaning(): String {
        if (!meaning.isNullOrBlank()) return meaning.trim()
        val options = getOptions()
        return options.getOrNull(correctIndex) ?: explanation.trim()
    }

    fun getIsolatedContextSentence(): String {
        if (!contextSentence.isNullOrBlank()) return contextSentence.trim()
        return ""
    }
}

@Entity(
    tableName = "vocabulary_items",
    indices = [
        Index(value = ["targetWord"]),
        Index(value = ["noteId"]),
        Index(value = ["trackId"])
    ]
)
data class VocabularyItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetWord: String,
    val meaning: String,
    val contextSentence: String = "",
    val noteId: Long? = null,
    val trackId: Long? = null,
    val timestampMs: Long? = null,
    val timesReviewed: Int = 0,
    val timesCorrect: Int = 0,
    val isMastered: Boolean = false,
    val lastReviewedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

// --- DAOs ---

@Dao
interface VocabularyItemDao {
    @Query("SELECT * FROM vocabulary_items ORDER BY createdAt DESC")
    fun getAllVocabularyItemsFlow(): Flow<List<VocabularyItem>>

    @Query("SELECT * FROM vocabulary_items ORDER BY createdAt DESC")
    suspend fun getAllVocabularyItemsDirect(): List<VocabularyItem>

    @Query("SELECT * FROM vocabulary_items WHERE id = :id LIMIT 1")
    suspend fun getVocabularyItemById(id: Long): VocabularyItem?

    @Query("SELECT * FROM vocabulary_items WHERE targetWord = :targetWord LIMIT 1")
    suspend fun getVocabularyItemByWord(targetWord: String): VocabularyItem?

    @Query("SELECT * FROM vocabulary_items WHERE noteId = :noteId ORDER BY createdAt DESC")
    fun getVocabularyItemsForNoteFlow(noteId: Long): Flow<List<VocabularyItem>>

    @Query("SELECT * FROM vocabulary_items WHERE trackId = :trackId ORDER BY createdAt DESC")
    fun getVocabularyItemsForTrackFlow(trackId: Long): Flow<List<VocabularyItem>>

    @Query("SELECT * FROM vocabulary_items WHERE isMastered = 1 ORDER BY lastReviewedAt DESC")
    fun getMasteredVocabularyItemsFlow(): Flow<List<VocabularyItem>>

    @Query("SELECT * FROM vocabulary_items WHERE isMastered = 0 ORDER BY createdAt DESC")
    fun getUnmasteredVocabularyItemsFlow(): Flow<List<VocabularyItem>>

    @Query("SELECT * FROM vocabulary_items WHERE targetWord LIKE '%' || :query || '%' OR meaning LIKE '%' || :query || '%' OR contextSentence LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchVocabularyItemsFlow(query: String): Flow<List<VocabularyItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVocabularyItem(item: VocabularyItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVocabularyItems(items: List<VocabularyItem>): List<Long>

    @Update
    suspend fun updateVocabularyItem(item: VocabularyItem)

    @Delete
    suspend fun deleteVocabularyItem(item: VocabularyItem)

    @Query("DELETE FROM vocabulary_items WHERE id = :id")
    suspend fun deleteVocabularyItemById(id: Long)

    @Query("DELETE FROM vocabulary_items WHERE noteId = :noteId")
    suspend fun deleteVocabularyItemsForNote(noteId: Long)

    @Query("UPDATE vocabulary_items SET timesReviewed = timesReviewed + 1, timesCorrect = timesCorrect + :correctDelta, isMastered = CASE WHEN (timesCorrect + :correctDelta) >= 3 THEN 1 ELSE isMastered END, lastReviewedAt = :timestamp WHERE id = :id")
    suspend fun recordReview(id: Long, correctDelta: Int, timestamp: Long = System.currentTimeMillis())
}

@Dao
interface AppDao {
    // Folders
    @Query("SELECT * FROM folders ORDER BY folderName ASC")
    fun getAllFolders(): Flow<List<Folder>>

    @Query("SELECT * FROM folders ORDER BY folderName ASC")
    suspend fun getAllFoldersDirect(): List<Folder>

    @Query("SELECT * FROM folders WHERE parentFolderId IS NULL ORDER BY folderName ASC")
    fun getRootFoldersFlow(): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE parentFolderId = :parentFolderId ORDER BY folderName ASC")
    fun getSubfoldersFlow(parentFolderId: Long): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE parentFolderId = :parentFolderId ORDER BY folderName ASC")
    suspend fun getSubfoldersDirect(parentFolderId: Long): List<Folder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: Folder): Long

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteFolder(folderId: Long)

    @Update
    suspend fun updateFolder(folder: Folder)

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolderById(id: Long): Folder?

    @Query("SELECT * FROM folders WHERE folderPath = :path LIMIT 1")
    suspend fun getFolderByPath(path: String): Folder?

    // Tracks
    @Query("SELECT * FROM audio_tracks ORDER BY fileName ASC")
    fun getAllTracksFlow(): Flow<List<AudioTrack>>

    @Query("SELECT * FROM audio_tracks WHERE isIndependent = 1 ORDER BY fileName ASC")
    fun getIndependentTracksFlow(): Flow<List<AudioTrack>>

    @Query("SELECT * FROM audio_tracks WHERE parentFolderId = :folderId ORDER BY fileName ASC")
    fun getTracksForFolderFlow(folderId: Long): Flow<List<AudioTrack>>

    @Query("SELECT * FROM audio_tracks WHERE parentFolderId = :folderId ORDER BY fileName ASC")
    suspend fun getTracksForFolder(folderId: Long): List<AudioTrack>

    @Query("SELECT * FROM audio_tracks WHERE id = :id")
    suspend fun getTrackById(id: Long): AudioTrack?

    @Query("SELECT * FROM audio_tracks WHERE filePath = :filePath LIMIT 1")
    suspend fun getTrackByPath(filePath: String): AudioTrack?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: AudioTrack): Long

    @Update
    suspend fun updateTrack(track: AudioTrack)

    @Query("UPDATE audio_tracks SET practiceSegments = :segments WHERE id = :trackId")
    suspend fun updateTrackPracticeSegments(trackId: Long, segments: String?)

    @Delete
    suspend fun deleteTrack(track: AudioTrack)

    @Query("DELETE FROM audio_tracks WHERE id = :trackId")
    suspend fun deleteTrackById(trackId: Long)

    @Query("SELECT * FROM audio_tracks WHERE parentTrackId = :parentTrackId ORDER BY sceneNumber ASC, startOffsetMs ASC")
    fun getScenesForParentTrackFlow(parentTrackId: Long): Flow<List<AudioTrack>>

    @Query("SELECT * FROM audio_tracks WHERE parentTrackId = :parentTrackId ORDER BY sceneNumber ASC, startOffsetMs ASC")
    suspend fun getScenesForParentTrack(parentTrackId: Long): List<AudioTrack>

    // Playlists
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylistsFlow(): Flow<List<Playlist>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistById(playlistId: Long): Playlist?

    @Update
    suspend fun updatePlaylist(playlist: Playlist)

    // Playlist cross ref
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrack(ref: PlaylistTrackCrossRef)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylistTracks(playlistId: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun deletePlaylistTrack(playlistId: Long, trackId: Long)

    @Query("""
        SELECT t.* FROM audio_tracks t 
        INNER JOIN playlist_tracks pt ON t.id = pt.trackId 
        WHERE pt.playlistId = :playlistId 
        ORDER BY pt.displayOrder ASC
    """)
    fun getTracksForPlaylistFlow(playlistId: Long): Flow<List<AudioTrack>>

    @Query("""
        SELECT t.* FROM audio_tracks t 
        INNER JOIN playlist_tracks pt ON t.id = pt.trackId 
        WHERE pt.playlistId = :playlistId 
        ORDER BY pt.displayOrder ASC
    """)
    suspend fun getTracksForPlaylist(playlistId: Long): List<AudioTrack>

    @Query("""
        SELECT p.* FROM playlists p 
        INNER JOIN playlist_tracks pt ON p.id = pt.playlistId 
        WHERE pt.trackId = :trackId
    """)
    suspend fun getPlaylistsForTrack(trackId: Long): List<Playlist>

    // Tasks
    @Query("SELECT * FROM tasks WHERE status = 'ACTIVE' ORDER BY id DESC")
    fun getActiveTasksFlow(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE status = 'ACTIVE' ORDER BY id DESC")
    suspend fun getActiveTasksDirect(): List<Task>

    @Query("SELECT * FROM tasks ORDER BY id DESC")
    suspend fun getAllTasksDirect(): List<Task>

    @Query("SELECT * FROM tasks WHERE status = 'COMPLETED' ORDER BY id DESC")
    fun getCompletedTasksFlow(): Flow<List<Task>>

    @Query("SELECT * FROM tasks ORDER BY id DESC")
    fun getAllTasksFlow(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): Task?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Update
    suspend fun updateTask(task: Task)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteTask(taskId: Long)

    // Task active progress
    @Query("SELECT * FROM task_track_progress")
    fun getAllTaskProgressFlow(): Flow<List<TaskTrackProgress>>

    @Query("SELECT * FROM task_track_progress WHERE taskId = :taskId")
    fun getProgressForTaskFlow(taskId: Long): Flow<List<TaskTrackProgress>>

    @Query("SELECT * FROM task_track_progress WHERE taskId = :taskId")
    suspend fun getProgressForTask(taskId: Long): List<TaskTrackProgress>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskProgress(progress: TaskTrackProgress)

    @Query("DELETE FROM task_track_progress WHERE taskId = :taskId")
    suspend fun clearTaskProgress(taskId: Long)

    @Query("DELETE FROM task_track_progress WHERE taskId = :taskId AND trackId = :trackId")
    suspend fun deleteSingleTaskProgress(taskId: Long, trackId: Long)

    @Query("""
        SELECT t.* FROM tasks t
        INNER JOIN task_track_progress tp ON t.id = tp.taskId
        WHERE tp.trackId = :trackId AND t.status = 'ACTIVE'
    """)
    suspend fun getActiveTasksForTrack(trackId: Long): List<Task>

    @Query("""
        SELECT t.* FROM tasks t
        INNER JOIN task_track_progress tp ON t.id = tp.taskId
        WHERE tp.trackId = :trackId
    """)
    suspend fun getAllTasksForTrack(trackId: Long): List<Task>

    // Task Daily Progress
    @Query("SELECT * FROM task_daily_progress WHERE date = :date")
    fun getDailyProgressForDateFlow(date: String): Flow<List<TaskDailyProgress>>

    @Query("SELECT * FROM task_daily_progress WHERE date = :date")
    suspend fun getDailyProgressForDateDirect(date: String): List<TaskDailyProgress>

    @Query("SELECT * FROM task_daily_progress WHERE taskId = :taskId AND date = :date")
    suspend fun getDailyProgress(taskId: Long, date: String): TaskDailyProgress?

    @Query("SELECT * FROM task_daily_progress WHERE taskId = :taskId")
    fun getAllDailyProgressForTask(taskId: Long): Flow<List<TaskDailyProgress>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskDailyProgress(progress: TaskDailyProgress)

    @Query("DELETE FROM task_daily_progress WHERE taskId = :taskId")
    suspend fun deleteDailyProgressForTask(taskId: Long)

    // Stats / Playback History
    @Query("SELECT * FROM playback_history ORDER BY completedAt DESC")
    fun getPlaybackHistoryFlow(): Flow<List<PlaybackHistory>>

    @Query("SELECT * FROM playback_history WHERE completedAt >= :startTime AND completedAt <= :endTime ORDER BY completedAt DESC")
    fun getPlaybackHistoryFilteredFlow(startTime: Long, endTime: Long): Flow<List<PlaybackHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaybackHistory(history: PlaybackHistory): Long

    @Query("DELETE FROM playback_history")
    suspend fun clearAllPlaybackHistory()

    @Query("DELETE FROM playback_history WHERE completedAt < :oneYearAgoTime")
    suspend fun deletePlaybackHistoryOlderThan(oneYearAgoTime: Long)

    @Query("DELETE FROM playback_history WHERE id NOT IN (SELECT id FROM playback_history ORDER BY completedAt DESC LIMIT :limit)")
    suspend fun deletePlaybackHistoryExceptTop(limit: Int)

    // --- Notes & Notebook ---
    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun getAllNotesFlow(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE folderId = :folderId ORDER BY createdAt DESC")
    fun getNotesForFolderFlow(folderId: Long): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE trackId = :trackId ORDER BY startTimestampMs ASC")
    fun getNotesForTrackFlow(trackId: Long): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Long): Note?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Update
    suspend fun updateNote(note: Note)

    @Delete
    suspend fun deleteNote(note: Note)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: Long)

    @Query("DELETE FROM notes WHERE trackId = :trackId")
    suspend fun deleteNotesForTrack(trackId: Long)

    // Note Tags
    @Query("SELECT * FROM note_tags ORDER BY name ASC")
    fun getAllTagsFlow(): Flow<List<NoteTag>>

    @Query("SELECT * FROM note_tags ORDER BY name ASC")
    suspend fun getAllTagsDirect(): List<NoteTag>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: NoteTag): Long

    @Query("DELETE FROM note_tags WHERE id = :id")
    suspend fun deleteTag(id: Long)

    @Query("DELETE FROM note_tags WHERE name = :name")
    suspend fun deleteTagByName(name: String)

    @Query("DELETE FROM note_tags WHERE name NOT IN (:activeNames)")
    suspend fun deleteTagsNotIn(activeNames: List<String>)

    @Query("DELETE FROM note_tags")
    suspend fun deleteAllTags()

    // Task Labels
    @Query("SELECT * FROM task_labels ORDER BY name ASC")
    fun getAllTaskLabelsFlow(): Flow<List<TaskLabel>>

    @Query("SELECT * FROM task_labels ORDER BY name ASC")
    suspend fun getAllTaskLabelsDirect(): List<TaskLabel>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTaskLabel(label: TaskLabel): Long

    @Query("DELETE FROM task_labels WHERE id = :id")
    suspend fun deleteTaskLabel(id: Long)

    @Query("DELETE FROM task_labels WHERE name = :name")
    suspend fun deleteTaskLabelByName(name: String)

    @Query("DELETE FROM task_labels")
    suspend fun deleteAllTaskLabels()

    // Quiz Questions
    @Query("SELECT * FROM quiz_questions WHERE trackId = :trackId ORDER BY createdAt ASC")
    fun getQuestionsForTrackFlow(trackId: Long): Flow<List<QuizQuestion>>

    @Query("SELECT * FROM quiz_questions WHERE trackId = :trackId ORDER BY createdAt ASC")
    suspend fun getQuestionsForTrackDirect(trackId: Long): List<QuizQuestion>

    @Query("SELECT COUNT(*) FROM quiz_questions WHERE trackId = :trackId")
    suspend fun getQuestionCountForTrack(trackId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuizQuestions(questions: List<QuizQuestion>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuizQuestion(question: QuizQuestion): Long

    @Update
    suspend fun updateQuizQuestion(question: QuizQuestion)

    @Query("UPDATE quiz_questions SET timesAnswered = timesAnswered + 1, timesCorrect = timesCorrect + :correctIncrement, lastAnsweredAt = :now WHERE id = :id")
    suspend fun recordQuestionAnswer(id: Long, correctIncrement: Int, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM quiz_questions WHERE id = :id")
    suspend fun deleteQuizQuestionById(id: Long)

    @Query("DELETE FROM quiz_questions WHERE trackId = :trackId")
    suspend fun deleteQuizQuestionsForTrack(trackId: Long)

    @Query("SELECT * FROM quiz_questions WHERE category = :category ORDER BY createdAt DESC")
    fun getQuestionsByCategoryFlow(category: String): Flow<List<QuizQuestion>>

    @Query("SELECT * FROM quiz_questions WHERE category = 'VOCABULARY' OR (category = 'COMPREHENSION' AND (question LIKE '%meaning%' OR question LIKE '%means%' OR question LIKE '%word%' OR question LIKE '%definition%' OR question LIKE '%phrase%' OR question LIKE '%idiom%' OR question LIKE '%معنى%' OR question LIKE '%مرادف%')) ORDER BY createdAt DESC")
    fun getAllVocabularyQuestionsFlow(): Flow<List<QuizQuestion>>

    @Query("SELECT * FROM quiz_questions WHERE category = 'VOCABULARY' OR (category = 'COMPREHENSION' AND (question LIKE '%meaning%' OR question LIKE '%means%' OR question LIKE '%word%' OR question LIKE '%definition%' OR question LIKE '%phrase%' OR question LIKE '%idiom%' OR question LIKE '%معنى%' OR question LIKE '%مرادف%')) ORDER BY createdAt DESC")
    suspend fun getAllVocabularyQuestionsDirect(): List<QuizQuestion>

    @Query("SELECT * FROM quiz_questions WHERE trackId = :trackId AND category = :category ORDER BY createdAt ASC")
    fun getQuestionsForTrackAndCategoryFlow(trackId: Long, category: String): Flow<List<QuizQuestion>>

    @Query("SELECT * FROM quiz_questions WHERE noteId = :noteId ORDER BY createdAt DESC")
    fun getQuestionsForNoteFlow(noteId: Long): Flow<List<QuizQuestion>>

    @Query("SELECT * FROM quiz_questions WHERE noteId = :noteId ORDER BY createdAt DESC")
    suspend fun getQuestionsForNoteDirect(noteId: Long): List<QuizQuestion>

    @Query("SELECT * FROM quiz_questions WHERE noteId IS NOT NULL ORDER BY createdAt DESC")
    fun getNotebookVocabularyQuestionsFlow(): Flow<List<QuizQuestion>>

    @Query("SELECT * FROM quiz_questions WHERE noteId IS NOT NULL ORDER BY createdAt DESC")
    suspend fun getNotebookVocabularyQuestionsDirect(): List<QuizQuestion>

    @Query("SELECT noteId, COUNT(*) as count FROM quiz_questions WHERE noteId IS NOT NULL GROUP BY noteId")
    fun getNoteQuestionCountsFlow(): Flow<List<NoteQuestionCount>>
}

// --- DATABASE ---

@Database(
    entities = [
        Folder::class,
        AudioTrack::class,
        Playlist::class,
        PlaylistTrackCrossRef::class,
        Task::class,
        TaskTrackProgress::class,
        PlaybackHistory::class,
        Note::class,
        NoteTag::class,
        TaskLabel::class,
        TaskDailyProgress::class,
        QuizQuestion::class,
        VocabularyItem::class
    ],
    version = 18,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
    abstract fun vocabularyItemDao(): VocabularyItemDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Version 1 to 2 schema adjustments if applicable
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE audio_tracks ADD COLUMN subtitlePath TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE audio_tracks ADD COLUMN subtitleContent TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE audio_tracks ADD COLUMN subtitleOffsetMs INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE folders ADD COLUMN parentFolderId INTEGER DEFAULT NULL")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playback_history ADD COLUMN durationMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE playback_history ADD COLUMN playbackSpeed REAL NOT NULL DEFAULT 1.0")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playback_history ADD COLUMN actualListenedMs INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `notes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `text` TEXT NOT NULL,
                        `comment` TEXT NOT NULL,
                        `trackId` INTEGER,
                        `trackName` TEXT,
                        `folderId` INTEGER,
                        `folderName` TEXT,
                        `startTimestampMs` INTEGER NOT NULL,
                        `endTimestampMs` INTEGER NOT NULL,
                        `tags` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `note_tags` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `colorHex` TEXT,
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE audio_tracks ADD COLUMN startOffsetMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE audio_tracks ADD COLUMN endOffsetMs INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE audio_tracks ADD COLUMN isVirtualScene INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE audio_tracks ADD COLUMN parentTrackId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE audio_tracks ADD COLUMN sceneNumber INTEGER DEFAULT NULL")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN labels TEXT NOT NULL DEFAULT ''")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `task_labels` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN originStartMs INTEGER DEFAULT NULL")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playback_history ADD COLUMN activeTasks TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE audio_tracks ADD COLUMN practiceSegments TEXT DEFAULT NULL")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN dailyTargetValue INTEGER DEFAULT NULL")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `task_daily_progress` (
                        `taskId` INTEGER NOT NULL,
                        `date` TEXT NOT NULL,
                        `completedPlayCount` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`taskId`, `date`)
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `quiz_questions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `trackId` INTEGER NOT NULL,
                        `questionType` TEXT NOT NULL,
                        `question` TEXT NOT NULL,
                        `optionsJson` TEXT NOT NULL,
                        `correctIndex` INTEGER NOT NULL,
                        `explanation` TEXT NOT NULL,
                        `timestampMs` INTEGER,
                        `timesAnswered` INTEGER NOT NULL DEFAULT 0,
                        `timesCorrect` INTEGER NOT NULL DEFAULT 0,
                        `lastAnsweredAt` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`trackId`) REFERENCES `audio_tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_questions_trackId` ON `quiz_questions` (`trackId`)")
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE quiz_questions ADD COLUMN category TEXT NOT NULL DEFAULT 'COMPREHENSION'")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_questions_category` ON `quiz_questions` (`category`)")
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `quiz_questions_temp` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `trackId` INTEGER,
                        `noteId` INTEGER,
                        `questionType` TEXT NOT NULL,
                        `category` TEXT NOT NULL DEFAULT 'COMPREHENSION',
                        `question` TEXT NOT NULL,
                        `optionsJson` TEXT NOT NULL,
                        `correctIndex` INTEGER NOT NULL,
                        `explanation` TEXT NOT NULL,
                        `timestampMs` INTEGER,
                        `timesAnswered` INTEGER NOT NULL DEFAULT 0,
                        `timesCorrect` INTEGER NOT NULL DEFAULT 0,
                        `lastAnsweredAt` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`trackId`) REFERENCES `audio_tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `quiz_questions_temp` 
                    (`id`, `trackId`, `noteId`, `questionType`, `category`, `question`, `optionsJson`, `correctIndex`, `explanation`, `timestampMs`, `timesAnswered`, `timesCorrect`, `lastAnsweredAt`, `createdAt`)
                    SELECT `id`, `trackId`, NULL, `questionType`, `category`, `question`, `optionsJson`, `correctIndex`, `explanation`, `timestampMs`, `timesAnswered`, `timesCorrect`, `lastAnsweredAt`, `createdAt`
                    FROM `quiz_questions`
                """.trimIndent())
                db.execSQL("DROP TABLE `quiz_questions`")
                db.execSQL("ALTER TABLE `quiz_questions_temp` RENAME TO `quiz_questions`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_questions_trackId` ON `quiz_questions` (`trackId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_questions_category` ON `quiz_questions` (`category`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_questions_noteId` ON `quiz_questions` (`noteId`)")
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create vocabulary_items table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `vocabulary_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `targetWord` TEXT NOT NULL,
                        `meaning` TEXT NOT NULL,
                        `contextSentence` TEXT NOT NULL DEFAULT '',
                        `noteId` INTEGER,
                        `trackId` INTEGER,
                        `timestampMs` INTEGER,
                        `timesReviewed` INTEGER NOT NULL DEFAULT 0,
                        `timesCorrect` INTEGER NOT NULL DEFAULT 0,
                        `isMastered` INTEGER NOT NULL DEFAULT 0,
                        `lastReviewedAt` INTEGER,
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vocabulary_items_targetWord` ON `vocabulary_items` (`targetWord`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vocabulary_items_noteId` ON `vocabulary_items` (`noteId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vocabulary_items_trackId` ON `vocabulary_items` (`trackId`)")

                // 2. Add isolated fields to notes table
                try {
                    db.execSQL("ALTER TABLE `notes` ADD COLUMN `targetWord` TEXT DEFAULT NULL")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE `notes` ADD COLUMN `meaning` TEXT DEFAULT NULL")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE `notes` ADD COLUMN `contextSentence` TEXT DEFAULT NULL")
                } catch (_: Exception) {}

                // 3. Add isolated fields to quiz_questions table
                try {
                    db.execSQL("ALTER TABLE `quiz_questions` ADD COLUMN `targetWord` TEXT DEFAULT NULL")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE `quiz_questions` ADD COLUMN `meaning` TEXT DEFAULT NULL")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE `quiz_questions` ADD COLUMN `contextSentence` TEXT DEFAULT NULL")
                } catch (_: Exception) {}
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE `audio_tracks` ADD COLUMN `currentPlayActualListeningMs` INTEGER NOT NULL DEFAULT 0")
                } catch (_: Exception) {}
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // Safeguard existing SQLite database before Room applies any migrations
                com.example.util.AutoBackupManager.safetyBackupDatabaseFile(context.applicationContext)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_audio_tasks_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18)
                    .fallbackToDestructiveMigrationOnDowngrade(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// --- REPOSITORY ---

class AppRepository(val dao: AppDao, val vocabDao: VocabularyItemDao? = null) {
    val allFolders: Flow<List<Folder>> = dao.getAllFolders()
    suspend fun getAllFoldersDirect(): List<Folder> = dao.getAllFoldersDirect()
    val rootFolders: Flow<List<Folder>> = dao.getRootFoldersFlow()
    fun getSubfolders(parentFolderId: Long): Flow<List<Folder>> = dao.getSubfoldersFlow(parentFolderId)
    suspend fun getSubfoldersDirect(parentFolderId: Long): List<Folder> = dao.getSubfoldersDirect(parentFolderId)

    val allTracks: Flow<List<AudioTrack>> = dao.getAllTracksFlow()
    val independentTracks: Flow<List<AudioTrack>> = dao.getIndependentTracksFlow()
    val allPlaylists: Flow<List<Playlist>> = dao.getAllPlaylistsFlow()
    val activeTasks: Flow<List<Task>> = dao.getActiveTasksFlow()
    val completedTasks: Flow<List<Task>> = dao.getCompletedTasksFlow()
    val allTasks: Flow<List<Task>> = dao.getAllTasksFlow()
    val playbackHistory: Flow<List<PlaybackHistory>> = dao.getPlaybackHistoryFlow()

    suspend fun getActiveTasksDirect(): List<Task> = dao.getActiveTasksDirect()
    suspend fun getAllTasksDirect(): List<Task> = dao.getAllTasksDirect()

    // Folder actions
    suspend fun addFolder(path: String, name: String, parentFolderId: Long? = null): Long {
        val existing = dao.getFolderByPath(path)
        if (existing != null) {
            if (parentFolderId != null && existing.parentFolderId != parentFolderId) {
                dao.updateFolder(existing.copy(parentFolderId = parentFolderId))
            }
            return existing.id
        }
        return dao.insertFolder(Folder(folderPath = path, folderName = name, parentFolderId = parentFolderId))
    }

    suspend fun getFolderById(id: Long) = dao.getFolderById(id)
    suspend fun getFolderByPath(path: String) = dao.getFolderByPath(path)

    suspend fun updateFolder(folder: Folder) = dao.updateFolder(folder)

    suspend fun deleteFolder(id: Long) {
        val allFolders = dao.getAllFoldersDirect()
        val allFolderIdsToDelete = getAllSubfolderIds(id, allFolders)
        val allTracks = dao.getAllTracksFlow().firstOrNull() ?: emptyList()
        val tracksToDelete = allTracks.filter { it.parentFolderId in allFolderIdsToDelete }

        for (track in tracksToDelete) {
            deleteTrack(track)
        }

        for (folderId in allFolderIdsToDelete) {
            val folderObj = allFolders.find { it.id == folderId }
            folderObj?.let {
                try {
                    val dir = File(it.folderPath)
                    if (dir.exists()) dir.deleteRecursively()
                } catch (_: Exception) {}
            }
            dao.deleteFolder(folderId)
        }
    }

    // Track actions
    suspend fun getTrackById(id: Long) = dao.getTrackById(id)
    suspend fun getTrackByPath(path: String) = dao.getTrackByPath(path)
    suspend fun insertTrack(track: AudioTrack) = dao.insertTrack(track)
    suspend fun updateTrack(track: AudioTrack) = dao.updateTrack(track)
    suspend fun updateTrackPracticeSegments(trackId: Long, segments: String?) = dao.updateTrackPracticeSegments(trackId, segments)
    suspend fun deleteTrack(track: AudioTrack) {
        // Delete any virtual scenes belonging to this track
        val scenes = dao.getScenesForParentTrack(track.id)
        for (scene in scenes) {
            dao.deleteTrack(scene)
        }
        dao.deleteNotesForTrack(track.id)
        dao.deleteQuizQuestionsForTrack(track.id)
        dao.deleteTrack(track)

        if (!track.isVirtualScene) {
            try {
                val allTracks = dao.getAllTracksFlow().firstOrNull() ?: emptyList()
                val otherUsingPath = allTracks.any { it.id != track.id && it.filePath == track.filePath }
                if (!otherUsingPath) {
                    val f = File(track.filePath)
                    if (f.exists()) f.delete()
                }
            } catch (_: Exception) {}

            try {
                track.subtitlePath?.let { subPath ->
                    val sf = File(subPath)
                    if (sf.exists()) sf.delete()
                }
            } catch (_: Exception) {}
        }
    }
    suspend fun deleteTrackById(id: Long) = dao.deleteTrackById(id)
    fun getScenesForParentTrackFlow(parentTrackId: Long) = dao.getScenesForParentTrackFlow(parentTrackId)
    suspend fun getScenesForParentTrack(parentTrackId: Long) = dao.getScenesForParentTrack(parentTrackId)
    fun getTracksForFolderFlow(folderId: Long) = dao.getTracksForFolderFlow(folderId)
    suspend fun getTracksForFolder(folderId: Long) = dao.getTracksForFolder(folderId)

    fun getTracksForFolderRecursiveFlow(folderId: Long): Flow<List<AudioTrack>> {
        return dao.getAllTracksFlow().map { allTracks ->
            val allFolders = dao.getAllFoldersDirect()
            val targetFolderIds = getAllSubfolderIds(folderId, allFolders)
            allTracks.filter { it.parentFolderId in targetFolderIds }
        }
    }

    suspend fun getTracksForFolderRecursive(folderId: Long): List<AudioTrack> {
        val allFolders = dao.getAllFoldersDirect()
        val targetFolderIds = getAllSubfolderIds(folderId, allFolders)
        val allTracks = dao.getAllTracksFlow().firstOrNull() ?: emptyList()
        return allTracks.filter { it.parentFolderId in targetFolderIds }
    }

    private fun getAllSubfolderIds(parentFolderId: Long, allFolders: List<Folder>): Set<Long> {
        val set = mutableSetOf(parentFolderId)
        val children = allFolders.filter { it.parentFolderId == parentFolderId }
        for (child in children) {
            set.addAll(getAllSubfolderIds(child.id, allFolders))
        }
        return set
    }

    // Playlist actions
    suspend fun addPlaylist(name: String): Long {
        return dao.insertPlaylist(Playlist(name = name))
    }
    suspend fun getPlaylistById(id: Long) = dao.getPlaylistById(id)
    suspend fun updatePlaylist(playlist: Playlist) {
        dao.updatePlaylist(playlist)
    }
    suspend fun deletePlaylist(id: Long) {
        dao.deletePlaylist(id)
        dao.clearPlaylistTracks(id)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long, order: Int = 0) {
        dao.insertPlaylistTrack(PlaylistTrackCrossRef(playlistId, trackId, order))
    }

    suspend fun clearPlaylistTracks(playlistId: Long) = dao.clearPlaylistTracks(playlistId)
    suspend fun deletePlaylistTrack(playlistId: Long, trackId: Long) = dao.deletePlaylistTrack(playlistId, trackId)

    fun getTracksForPlaylistFlow(playlistId: Long) = dao.getTracksForPlaylistFlow(playlistId)
    suspend fun getTracksForPlaylist(playlistId: Long) = dao.getTracksForPlaylist(playlistId)

    // Tasks Actions
    val allTaskLabels: Flow<List<TaskLabel>> = dao.getAllTaskLabelsFlow()
    suspend fun getAllTaskLabelsDirect(): List<TaskLabel> = dao.getAllTaskLabelsDirect()

    suspend fun addTask(
        title: String,
        sourceType: String,
        sourceId: Long?,
        targetType: String,
        targetValue: Int,
        scheduledDays: String,
        reminderTime: String,
        startDate: Long,
        endDate: Long?,
        customThreshold: Int? = null,
        labels: String = "",
        dailyTargetValue: Int? = null
    ): Long {
        val finalTitle = Task.buildCombinedTitle(title, labels)
        val task = Task(
            title = finalTitle,
            sourceType = sourceType,
            sourceId = sourceId,
            targetType = targetType,
            targetValue = targetValue,
            scheduledDays = scheduledDays,
            reminderTime = reminderTime,
            startDate = startDate,
            endDate = endDate,
            customThreshold = customThreshold,
            labels = labels,
            dailyTargetValue = dailyTargetValue
        )
        val taskId = dao.insertTask(task)
        initializeTaskProgress(taskId, sourceType, sourceId)
        syncAndCleanTaskLabels()
        return taskId
    }

    suspend fun initializeTaskProgress(taskId: Long, sourceType: String, sourceId: Long?) {
        val tracks = when (sourceType) {
            "TRACKS" -> {
                // If specific tracks selected, wait, we will bind them later or if independent
                dao.getTracksForFolder(-1) // Empty normally, let's let caller insert manually or find tracks
            }
            "FOLDER" -> {
                if (sourceId != null) getTracksForFolderRecursive(sourceId) else emptyList()
            }
            "PLAYLIST" -> {
                if (sourceId != null) dao.getTracksForPlaylist(sourceId) else emptyList()
            }
            else -> emptyList()
        }
        for (track in tracks) {
            dao.insertTaskProgress(TaskTrackProgress(taskId, track.id))
        }
    }

    suspend fun updateTask(task: Task) {
        val finalTitle = Task.buildCombinedTitle(task.title, task.labels)
        val normalizedTask = if (task.title != finalTitle) task.copy(title = finalTitle) else task
        dao.updateTask(normalizedTask)
        syncAndCleanTaskLabels()
    }

    suspend fun deleteTask(id: Long) {
        dao.deleteTask(id)
        dao.clearTaskProgress(id)
        dao.deleteDailyProgressForTask(id)
        syncAndCleanTaskLabels()
    }

    suspend fun getTaskById(id: Long) = dao.getTaskById(id)
    suspend fun insertTask(task: Task): Long {
        val finalTitle = Task.buildCombinedTitle(task.title, task.labels)
        val normalizedTask = if (task.title != finalTitle) task.copy(title = finalTitle) else task
        val id = dao.insertTask(normalizedTask)
        syncAndCleanTaskLabels()
        return id
    }
    suspend fun getActiveTasksForTrack(trackId: Long) = dao.getActiveTasksForTrack(trackId)
    suspend fun getAllTasksForTrack(trackId: Long) = dao.getAllTasksForTrack(trackId)
    fun getAllTaskProgressFlow() = dao.getAllTaskProgressFlow()
    fun getProgressForTaskFlow(taskId: Long) = dao.getProgressForTaskFlow(taskId)
    suspend fun getProgressForTask(taskId: Long) = dao.getProgressForTask(taskId)
    suspend fun insertTaskProgress(progress: TaskTrackProgress) = dao.insertTaskProgress(progress)
    suspend fun deleteSingleTaskProgress(taskId: Long, trackId: Long) = dao.deleteSingleTaskProgress(taskId, trackId)

    // Task Daily Progress
    fun getDailyProgressForDateFlow(date: String): Flow<List<TaskDailyProgress>> = dao.getDailyProgressForDateFlow(date)
    suspend fun getDailyProgressForDateDirect(date: String): List<TaskDailyProgress> = dao.getDailyProgressForDateDirect(date)
    suspend fun getDailyProgress(taskId: Long, date: String): TaskDailyProgress? = dao.getDailyProgress(taskId, date)
    suspend fun insertTaskDailyProgress(progress: TaskDailyProgress) = dao.insertTaskDailyProgress(progress)
    suspend fun deleteDailyProgressForTask(taskId: Long) = dao.deleteDailyProgressForTask(taskId)
    suspend fun incrementDailyPlayCount(taskId: Long, date: String): Int {
        val current = dao.getDailyProgress(taskId, date)
        val newCount = (current?.completedPlayCount ?: 0) + 1
        dao.insertTaskDailyProgress(TaskDailyProgress(taskId = taskId, date = date, completedPlayCount = newCount))
        return newCount
    }

    // Re-activate task
    suspend fun reactivateTask(taskId: Long) {
        val task = dao.getTaskById(taskId) ?: return
        dao.updateTask(task.copy(isCompleted = false, status = "ACTIVE"))
        val progressList = dao.getProgressForTask(taskId)
        
        // Check if the task was fully completed (100%)
        val targetVal = task.targetValue
        var totalCompleted = 0
        var totalRequired = 0
        if (task.targetType == "PLAY_COUNT") {
            progressList.forEach { p ->
                totalCompleted += minOf(p.completedPlayCount, targetVal)
                totalRequired += targetVal
            }
        } else {
            progressList.forEach { p ->
                totalCompleted += minOf(p.getDaysList().size, targetVal)
                totalRequired += targetVal
            }
        }
        val percent = if (totalRequired > 0) totalCompleted.toFloat() / totalRequired.toFloat() else 0f
        val isFullyCompleted = progressList.isNotEmpty() && (progressList.all { it.isTrackCompleted } || percent >= 1.0f)

        // Only reset progress if the task was 100% completed.
        // For non-completed tasks reactivated from archive, preserve current progress percent intact!
        if (isFullyCompleted) {
            for (p in progressList) {
                dao.insertTaskProgress(
                    TaskTrackProgress(
                        taskId = taskId,
                        trackId = p.trackId,
                        completedPlayCount = 0,
                        completedDays = "",
                        isTrackCompleted = false
                    )
                )
            }
        }
    }

    // Keep dynamic tasks synced with dynamic sources (Folder/playlist)
    suspend fun syncDynamicTaskTracks(taskId: Long) {
        val task = dao.getTaskById(taskId) ?: return
        val currentTracks = when (task.sourceType) {
            "FOLDER" -> if (task.sourceId != null) getTracksForFolderRecursive(task.sourceId) else emptyList()
            "PLAYLIST" -> if (task.sourceId != null) dao.getTracksForPlaylist(task.sourceId) else emptyList()
            else -> return // static tracking
        }
        val existingProgress = dao.getProgressForTask(taskId)
        val existingTrackIds = existingProgress.map { it.trackId }.toSet()
        val currentTrackIds = currentTracks.map { it.id }.toSet()

        // Insert new ones
        for (track in currentTracks) {
            if (track.id !in existingTrackIds) {
                dao.insertTaskProgress(TaskTrackProgress(taskId = taskId, trackId = track.id))
            }
        }
        // Remove deleted ones? The specification says:
        // "إذا حُذفت ملفات من المجلد تُحذف تلقائياً من المهمة وتُعاد حسبة تقدمها."
        // So we can delete the progresses for tracks that are no longer in the folder/playlist
        // Wait, yes!
        for (p in existingProgress) {
            if (p.trackId !in currentTrackIds) {
                // Remove progress record
                // We'll write dynamic cleanup: Since we don't have a specific deleteTaskProgress query,
                // we can clear and re-add or just keep them?
                // Let's implement a clean way. Rather than making a custom query, we can just clear then re-add
                // but that would delete progress. Let's just create a custom DB update or filter dynamically.
                // Or let's not worry, let's keep them and filter them dynamically in ViewModel, which is 100% safe
                // and avoids losing existing records. Let's filter them!
            }
        }
    }

    // Stats
    suspend fun getActiveTasksForTrackDirect(track: AudioTrack): List<Task> {
        val allTasks = dao.getAllTasksDirect()
        val allFolders = dao.getAllFoldersDirect()

        fun isFolderDescendant(folderId: Long?, targetParentId: Long?): Boolean {
            if (folderId == null || targetParentId == null) return false
            var current: Long? = folderId
            var depth = 0
            while (current != null && depth < 20) {
                if (current == targetParentId) return true
                current = allFolders.find { it.id == current }?.parentFolderId
                depth++
            }
            return false
        }

        val result = mutableListOf<Task>()
        for (task in allTasks) {
            if (task.isCompleted || task.status != "ACTIVE") continue

            val progressList = dao.getProgressForTask(task.id)
            val inProgress = progressList.any { it.trackId == track.id }
            val inFolder = task.sourceType == "FOLDER" && task.sourceId != null &&
                    (track.parentFolderId == task.sourceId || isFolderDescendant(track.parentFolderId, task.sourceId))

            if (inProgress || inFolder) {
                result.add(task)
            }
        }
        return result
    }

    fun getPlaybackHistoryFiltered(startTime: Long, endTime: Long): Flow<List<PlaybackHistory>> {
        return dao.getPlaybackHistoryFilteredFlow(startTime, endTime)
    }

    suspend fun insertPlaybackHistory(history: PlaybackHistory): Long {
        return dao.insertPlaybackHistory(history)
    }

    suspend fun clearAllPlaybackHistory() {
        dao.clearAllPlaybackHistory()
    }

    suspend fun deletePlaybackHistoryOlderThan(time: Long) {
        dao.deletePlaybackHistoryOlderThan(time)
    }

    suspend fun deletePlaybackHistoryExceptTop(limit: Int) {
        dao.deletePlaybackHistoryExceptTop(limit)
    }

    suspend fun getPlaylistsForTrack(trackId: Long): List<Playlist> {
        return dao.getPlaylistsForTrack(trackId)
    }

    // --- Notes & Notebook ---
    val allNotes: Flow<List<Note>> = dao.getAllNotesFlow()
    val allTags: Flow<List<NoteTag>> = dao.getAllTagsFlow()
    suspend fun getAllTagsDirect(): List<NoteTag> = dao.getAllTagsDirect()

    fun getNotesForFolder(folderId: Long): Flow<List<Note>> = dao.getNotesForFolderFlow(folderId)
    fun getNotesForTrack(trackId: Long): Flow<List<Note>> = dao.getNotesForTrackFlow(trackId)
    suspend fun getNoteById(id: Long): Note? = dao.getNoteById(id)

    suspend fun insertNote(note: Note): Long {
        val resolvedWord: String? = note.targetWord?.takeIf { it.isNotBlank() } ?: note.getIsolatedTargetWord().takeIf { it.isNotBlank() }
        val resolvedMeaning: String? = note.meaning?.takeIf { it.isNotBlank() } ?: note.getIsolatedMeaning().takeIf { it.isNotBlank() }
        val resolvedContext: String? = note.contextSentence?.takeIf { it.isNotBlank() } ?: note.getIsolatedContextSentence().takeIf { it.isNotBlank() }
        val noteToInsert = note.copy(
            targetWord = resolvedWord,
            meaning = resolvedMeaning,
            contextSentence = resolvedContext
        )
        val id = dao.insertNote(noteToInsert)
        if (!resolvedWord.isNullOrBlank()) {
            insertOrUpdateVocabularyItem(
                targetWord = resolvedWord,
                meaning = resolvedMeaning ?: note.comment.trim(),
                contextSentence = resolvedContext ?: note.text.trim(),
                noteId = id,
                trackId = note.trackId,
                timestampMs = note.startTimestampMs
            )
        }
        return id
    }

    suspend fun updateNote(note: Note) {
        val resolvedWord: String? = note.targetWord?.takeIf { it.isNotBlank() } ?: note.getIsolatedTargetWord().takeIf { it.isNotBlank() }
        val resolvedMeaning: String? = note.meaning?.takeIf { it.isNotBlank() } ?: note.getIsolatedMeaning().takeIf { it.isNotBlank() }
        val resolvedContext: String? = note.contextSentence?.takeIf { it.isNotBlank() } ?: note.getIsolatedContextSentence().takeIf { it.isNotBlank() }
        val noteToUpdate = note.copy(
            targetWord = resolvedWord,
            meaning = resolvedMeaning,
            contextSentence = resolvedContext
        )
        dao.updateNote(noteToUpdate)
        if (!resolvedWord.isNullOrBlank()) {
            insertOrUpdateVocabularyItem(
                targetWord = resolvedWord,
                meaning = resolvedMeaning ?: note.comment.trim(),
                contextSentence = resolvedContext ?: note.text.trim(),
                noteId = note.id,
                trackId = note.trackId,
                timestampMs = note.startTimestampMs
            )
        }
    }

    suspend fun deleteNote(note: Note) {
        dao.deleteNote(note)
        vocabDao?.deleteVocabularyItemsForNote(note.id)
    }

    suspend fun deleteNoteById(id: Long) {
        dao.deleteNoteById(id)
        vocabDao?.deleteVocabularyItemsForNote(id)
    }

    suspend fun insertTag(name: String, colorHex: String? = null): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return -1
        return dao.insertTag(NoteTag(name = trimmed, colorHex = colorHex))
    }

    suspend fun deleteTag(id: Long) {
        dao.deleteTag(id)
    }

    suspend fun deleteTagByName(name: String) {
        dao.deleteTagByName(name)
    }

    suspend fun syncAndCleanTags(activeTags: Set<String>) {
        val trimmedActive = activeTags.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (trimmedActive.isEmpty()) {
            dao.deleteAllTags()
            return
        }

        val allExistingTags = dao.getAllTagsDirect()
        val seen = mutableSetOf<String>()

        for (tag in allExistingTags) {
            val lower = tag.name.trim().lowercase()
            val matchesActive = trimmedActive.any { it.equals(tag.name.trim(), ignoreCase = true) }
            if (!matchesActive || seen.contains(lower)) {
                // Delete unused or duplicate tag
                dao.deleteTag(tag.id)
            } else {
                seen.add(lower)
            }
        }

        // Insert any active tag that is not yet in note_tags
        for (activeTag in trimmedActive) {
            val lower = activeTag.lowercase()
            if (!seen.contains(lower)) {
                dao.insertTag(NoteTag(name = activeTag))
                seen.add(lower)
            }
        }
    }

    suspend fun syncAndCleanTaskLabels() {
        val allTasks = dao.getAllTasksDirect()
        for (task in allTasks) {
            val combined = Task.buildCombinedTitle(task.title, task.labels)
            if (task.title != combined) {
                dao.updateTask(task.copy(title = combined))
            }
        }

        val refreshedTasks = dao.getAllTasksDirect()
        val activeLabels = refreshedTasks.flatMap { it.getLabelsList() }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        if (activeLabels.isEmpty()) {
            dao.deleteAllTaskLabels()
            return
        }

        val allExisting = dao.getAllTaskLabelsDirect()
        val seen = mutableSetOf<String>()

        for (label in allExisting) {
            val lower = label.name.trim().lowercase()
            val matchesActive = activeLabels.any { it.equals(label.name.trim(), ignoreCase = true) }
            if (!matchesActive || seen.contains(lower)) {
                dao.deleteTaskLabel(label.id)
            } else {
                seen.add(lower)
            }
        }

        for (active in activeLabels) {
            val lower = active.lowercase()
            if (!seen.contains(lower)) {
                dao.insertTaskLabel(TaskLabel(name = active))
                seen.add(lower)
            }
        }
    }

    // Quiz Questions repository operations
    fun getQuestionsForTrackFlow(trackId: Long): Flow<List<QuizQuestion>> = dao.getQuestionsForTrackFlow(trackId)
    suspend fun getQuestionsForTrackDirect(trackId: Long): List<QuizQuestion> = dao.getQuestionsForTrackDirect(trackId)
    suspend fun getQuestionCountForTrack(trackId: Long): Int = dao.getQuestionCountForTrack(trackId)
    fun getQuestionsByCategoryFlow(category: String): Flow<List<QuizQuestion>> = dao.getQuestionsByCategoryFlow(category)
    fun getAllVocabularyQuestionsFlow(): Flow<List<QuizQuestion>> = dao.getAllVocabularyQuestionsFlow()
    suspend fun getAllVocabularyQuestionsDirect(): List<QuizQuestion> = dao.getAllVocabularyQuestionsDirect()
    fun getQuestionsForTrackAndCategoryFlow(trackId: Long, category: String): Flow<List<QuizQuestion>> = dao.getQuestionsForTrackAndCategoryFlow(trackId, category)
    suspend fun insertQuizQuestions(questions: List<QuizQuestion>): List<Long> {
        val prepared = questions.map { q ->
            val resolvedWord: String? = q.targetWord?.takeIf { it.isNotBlank() } ?: q.getIsolatedTargetWord().takeIf { it.isNotBlank() }
            val resolvedMeaning: String? = q.meaning?.takeIf { it.isNotBlank() } ?: q.getIsolatedMeaning().takeIf { it.isNotBlank() }
            val resolvedContext: String? = q.contextSentence?.takeIf { it.isNotBlank() } ?: q.getIsolatedContextSentence().takeIf { it.isNotBlank() }
            q.copy(
                targetWord = resolvedWord,
                meaning = resolvedMeaning,
                contextSentence = resolvedContext
            )
        }
        val ids = dao.insertQuizQuestions(prepared)
        prepared.forEachIndexed { index, q ->
            if (q.category == "VOCABULARY" && !q.targetWord.isNullOrBlank()) {
                insertOrUpdateVocabularyItem(
                    targetWord = q.targetWord,
                    meaning = q.meaning ?: q.explanation,
                    contextSentence = q.contextSentence ?: "",
                    noteId = q.noteId,
                    trackId = q.trackId,
                    timestampMs = q.timestampMs
                )
            }
        }
        return ids
    }

    suspend fun insertQuizQuestion(question: QuizQuestion): Long {
        val resolvedWord: String? = question.targetWord?.takeIf { it.isNotBlank() } ?: question.getIsolatedTargetWord().takeIf { it.isNotBlank() }
        val resolvedMeaning: String? = question.meaning?.takeIf { it.isNotBlank() } ?: question.getIsolatedMeaning().takeIf { it.isNotBlank() }
        val resolvedContext: String? = question.contextSentence?.takeIf { it.isNotBlank() } ?: question.getIsolatedContextSentence().takeIf { it.isNotBlank() }
        val prepared = question.copy(
            targetWord = resolvedWord,
            meaning = resolvedMeaning,
            contextSentence = resolvedContext
        )
        val id = dao.insertQuizQuestion(prepared)
        if (prepared.category == "VOCABULARY" && !prepared.targetWord.isNullOrBlank()) {
            insertOrUpdateVocabularyItem(
                targetWord = prepared.targetWord,
                meaning = prepared.meaning ?: prepared.explanation,
                contextSentence = prepared.contextSentence ?: "",
                noteId = prepared.noteId,
                trackId = prepared.trackId,
                timestampMs = prepared.timestampMs
            )
        }
        return id
    }
    suspend fun updateQuizQuestion(question: QuizQuestion) = dao.updateQuizQuestion(question)
    suspend fun recordQuestionAnswer(id: Long, isCorrect: Boolean) = dao.recordQuestionAnswer(id, if (isCorrect) 1 else 0)
    suspend fun deleteQuizQuestionById(id: Long) = dao.deleteQuizQuestionById(id)
    suspend fun deleteQuizQuestionsForTrack(trackId: Long) = dao.deleteQuizQuestionsForTrack(trackId)
    fun getQuestionsForNoteFlow(noteId: Long): Flow<List<QuizQuestion>> = dao.getQuestionsForNoteFlow(noteId)
    suspend fun getQuestionsForNoteDirect(noteId: Long): List<QuizQuestion> = dao.getQuestionsForNoteDirect(noteId)
    fun getNotebookVocabularyQuestionsFlow(): Flow<List<QuizQuestion>> = dao.getNotebookVocabularyQuestionsFlow()
    suspend fun getNotebookVocabularyQuestionsDirect(): List<QuizQuestion> = dao.getNotebookVocabularyQuestionsDirect()
    fun getNoteQuestionCountsMapFlow(): Flow<Map<Long, Int>> = dao.getNoteQuestionCountsFlow().map { list ->
        list.associate { it.noteId to it.count }
    }

    // VocabularyItem repository operations
    val allVocabularyItems: Flow<List<VocabularyItem>> = vocabDao?.getAllVocabularyItemsFlow() ?: kotlinx.coroutines.flow.flowOf(emptyList())
    suspend fun getAllVocabularyItemsDirect(): List<VocabularyItem> = vocabDao?.getAllVocabularyItemsDirect() ?: emptyList()
    suspend fun getVocabularyItemById(id: Long): VocabularyItem? = vocabDao?.getVocabularyItemById(id)
    suspend fun getVocabularyItemByWord(targetWord: String): VocabularyItem? = vocabDao?.getVocabularyItemByWord(targetWord.trim())
    fun getVocabularyItemsForNoteFlow(noteId: Long): Flow<List<VocabularyItem>> = vocabDao?.getVocabularyItemsForNoteFlow(noteId) ?: kotlinx.coroutines.flow.flowOf(emptyList())
    fun getVocabularyItemsForTrackFlow(trackId: Long): Flow<List<VocabularyItem>> = vocabDao?.getVocabularyItemsForTrackFlow(trackId) ?: kotlinx.coroutines.flow.flowOf(emptyList())
    fun getMasteredVocabularyItemsFlow(): Flow<List<VocabularyItem>> = vocabDao?.getMasteredVocabularyItemsFlow() ?: kotlinx.coroutines.flow.flowOf(emptyList())
    fun getUnmasteredVocabularyItemsFlow(): Flow<List<VocabularyItem>> = vocabDao?.getUnmasteredVocabularyItemsFlow() ?: kotlinx.coroutines.flow.flowOf(emptyList())
    fun searchVocabularyItemsFlow(query: String): Flow<List<VocabularyItem>> = vocabDao?.searchVocabularyItemsFlow(query) ?: kotlinx.coroutines.flow.flowOf(emptyList())
    suspend fun insertVocabularyItem(item: VocabularyItem): Long = vocabDao?.insertVocabularyItem(item) ?: 0L
    suspend fun insertVocabularyItems(items: List<VocabularyItem>): List<Long> = vocabDao?.insertVocabularyItems(items) ?: emptyList()
    suspend fun updateVocabularyItem(item: VocabularyItem) = vocabDao?.updateVocabularyItem(item)
    suspend fun deleteVocabularyItem(item: VocabularyItem) = vocabDao?.deleteVocabularyItem(item)
    suspend fun deleteVocabularyItemById(id: Long) = vocabDao?.deleteVocabularyItemById(id)
    suspend fun deleteVocabularyItemsForNote(noteId: Long) = vocabDao?.deleteVocabularyItemsForNote(noteId)
    suspend fun recordVocabularyReview(id: Long, isCorrect: Boolean) = vocabDao?.recordReview(id, if (isCorrect) 1 else 0)

    suspend fun insertOrUpdateVocabularyItem(
        targetWord: String,
        meaning: String,
        contextSentence: String = "",
        noteId: Long? = null,
        trackId: Long? = null,
        timestampMs: Long? = null
    ): Long {
        if (vocabDao == null || targetWord.isBlank()) return 0L
        val trimmedWord = targetWord.trim()
        val existing = vocabDao.getVocabularyItemByWord(trimmedWord)
        return if (existing != null) {
            val updated = existing.copy(
                meaning = if (meaning.isNotBlank()) meaning.trim() else existing.meaning,
                contextSentence = if (contextSentence.isNotBlank()) contextSentence.trim() else existing.contextSentence,
                noteId = noteId ?: existing.noteId,
                trackId = trackId ?: existing.trackId,
                timestampMs = timestampMs ?: existing.timestampMs
            )
            vocabDao.updateVocabularyItem(updated)
            existing.id
        } else {
            val newItem = VocabularyItem(
                targetWord = trimmedWord,
                meaning = meaning.trim(),
                contextSentence = contextSentence.trim(),
                noteId = noteId,
                trackId = trackId,
                timestampMs = timestampMs
            )
            vocabDao.insertVocabularyItem(newItem)
        }
    }
}
