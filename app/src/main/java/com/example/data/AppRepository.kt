package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.io.File

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
                dao.getTracksForFolder(-1)
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
        for (p in existingProgress) {
            if (p.trackId !in currentTrackIds) {
                // Filtered dynamically in ViewModel
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
