package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<AudioTrack>): List<Long>

    @Query("DELETE FROM audio_tracks WHERE parentTrackId = :parentTrackId")
    suspend fun deleteScenesForParentTrack(parentTrackId: Long)

    @Transaction
    suspend fun replaceScenesForParentTrack(parentTrackId: Long, newScenes: List<AudioTrack>): List<Long> {
        val oldScenes = getScenesForParentTrack(parentTrackId)
        for (old in oldScenes) {
            deleteNotesForTrack(old.id)
            deleteQuizQuestionsForTrack(old.id)
        }
        deleteScenesForParentTrack(parentTrackId)
        return if (newScenes.isNotEmpty()) {
            insertTracks(newScenes)
        } else {
            emptyList()
        }
    }

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
