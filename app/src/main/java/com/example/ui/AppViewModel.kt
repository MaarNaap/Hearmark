package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AudioContextSummary
import com.example.ai.ChatMessage
import com.example.ai.GeminiService
import com.example.data.*
import com.example.player.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    val repository = AppRepository(database.appDao(), database.vocabularyItemDao())

    val allVocabularyItems: StateFlow<List<VocabularyItem>> = repository.allVocabularyItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI exposed resources
    val folders: StateFlow<List<Folder>> = repository.allFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rootFolders: StateFlow<List<Folder>> = repository.rootFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getSubfolders(parentFolderId: Long): Flow<List<Folder>> = repository.getSubfolders(parentFolderId)

    val tracks: StateFlow<List<AudioTrack>> = repository.allTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val independentTracks: StateFlow<List<AudioTrack>> = repository.independentTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<Playlist>> = repository.allPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeTasks: StateFlow<List<Task>> = repository.activeTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedTasks: StateFlow<List<Task>> = repository.completedTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTasks: StateFlow<List<Task>> = repository.allTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTaskProgress: StateFlow<List<TaskTrackProgress>> = repository.getAllTaskProgressFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getTodayDateString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    val todayDailyProgress: StateFlow<List<TaskDailyProgress>> =
        repository.getDailyProgressForDateFlow(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trackMetadataCache = MutableStateFlow<Map<Long, com.example.util.TrackMetadata>>(emptyMap())

    val playbackHistory: StateFlow<List<PlaybackHistory>> = repository.playbackHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<Note>> = repository.allNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val noteQuestionCounts: StateFlow<Map<Long, Int>> = repository.getNoteQuestionCountsMapFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val noteTags: StateFlow<List<NoteTag>> = repository.allTags
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val taskLabels: StateFlow<List<TaskLabel>> = repository.allTaskLabels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingOpenTaskId = MutableStateFlow<Long?>(null)

    fun setPendingOpenTaskId(taskId: Long) {
        if (taskId > 0) {
            pendingOpenTaskId.value = taskId
        }
    }

    fun consumePendingOpenTaskId() {
        pendingOpenTaskId.value = null
    }

    // Settings config
    var selectedTheme by mutableStateOf("dark") // system, light, dark
    var thresholdSetting by mutableStateOf(90) // 80 - 100
    var skipSecondsSetting by mutableStateOf(10) // 5, 10, 15, 30
    var headsetControlsEnabled by mutableStateOf(true)
    var headsetMultiClickAction by mutableStateOf("NEXT_PREV") // "NEXT_PREV" or "SKIP_SECONDS"
    var segmentSourceSetting by mutableStateOf("SILENCE") // "SILENCE" or "SUBTITLES"
    var practicePauseMultiplierSetting by mutableStateOf(1.0f) // 0.5 to 2.5
    var silenceSensitivitySetting by mutableStateOf("MEDIUM") // "HIGH", "MEDIUM", "LOW"
    var silenceMinDurationSetting by mutableStateOf(500L) // 350L, 500L, 750L, 1000L
    var silencePaddingSetting by mutableStateOf(200L) // 100L, 200L, 300L, 400L
    var customGeminiApiKey by mutableStateOf("")
    var savedApiKeys by mutableStateOf<List<SavedApiKey>>(emptyList())
    var activeApiKeyId by mutableStateOf("")

    // Focused Sub-Controllers (Decomposition)
    val mediaAiViewModel by lazy {
        MediaAiViewModel(
            application = getApplication(),
            apiKeyProvider = { customGeminiApiKey },
            languageProvider = { Loc.currentLanguage }
        )
    }

    val quizViewModel by lazy {
        QuizViewModel(
            application = getApplication(),
            repository = repository,
            apiKeyProvider = { customGeminiApiKey },
            languageProvider = { Loc.currentLanguage }
        )
    }

    // Gemini Chatbot State & Actions (Delegated to mediaAiViewModel)
    val chatMessages: StateFlow<List<ChatMessage>> get() = mediaAiViewModel.chatMessages
    val isGeneratingAiResponse: StateFlow<Boolean> get() = mediaAiViewModel.isGeneratingAiResponse
    val isChatDialogOpen: MutableStateFlow<Boolean> get() = mediaAiViewModel.isChatDialogOpen
    val activeChatContext: MutableStateFlow<AudioContextSummary?> get() = mediaAiViewModel.activeChatContext

    // Subtitle Generation State & Actions (Delegated to mediaAiViewModel)
    val isGeneratingSubtitles: StateFlow<Boolean> get() = mediaAiViewModel.isGeneratingSubtitles
    val subtitleGeneratingTrack: StateFlow<AudioTrack?> get() = mediaAiViewModel.generatingTrack
    val subtitleGenerationStatus: StateFlow<String> get() = mediaAiViewModel.subtitleGenerationStatus

    fun cancelSubtitleGeneration() = mediaAiViewModel.cancelSubtitleGeneration()

    fun generateSubtitlesForCurrentTrack(
        contextSummary: AudioContextSummary? = null,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) = mediaAiViewModel.generateSubtitlesForCurrentTrack(contextSummary, onSuccess, onError)

    fun generateSubtitlesForTrack(
        track: AudioTrack,
        contextSummary: AudioContextSummary? = null,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) = mediaAiViewModel.generateSubtitlesForTrack(track, contextSummary, onSuccess, onError)

    fun openChatWithContext(contextSummary: AudioContextSummary? = null) = mediaAiViewModel.openChatWithContext(contextSummary)
    fun closeChatDialog() = mediaAiViewModel.closeChatDialog()
    fun clearChatHistory() = mediaAiViewModel.clearChatHistory()

    // AI Quiz & Practice Bank State (Delegated to quizViewModel)
    val isQuizSheetOpen: MutableStateFlow<Boolean> get() = quizViewModel.isQuizSheetOpen
    val isGeneratingQuiz: MutableStateFlow<Boolean> get() = quizViewModel.isGeneratingQuiz
    val quizGenerationError: MutableStateFlow<String?> get() = quizViewModel.quizGenerationError
    val quizGenerationSuccessMessage: MutableStateFlow<String?> get() = quizViewModel.quizGenerationSuccessMessage
    val activeQuizTargetTrack: MutableStateFlow<AudioTrack?> get() = quizViewModel.activeQuizTargetTrack
    val currentTrackQuizQuestions: MutableStateFlow<List<QuizQuestion>> get() = quizViewModel.currentTrackQuizQuestions
    val allVocabularyQuestions: StateFlow<List<QuizQuestion>> get() = quizViewModel.allVocabularyQuestions

    fun openQuizForTrack(track: AudioTrack, startPracticeSession: Boolean = true) = quizViewModel.openQuizForTrack(track, startPracticeSession)
    fun openQuizForCurrentTrack(startPracticeSession: Boolean = true) = quizViewModel.openQuizForCurrentTrack(startPracticeSession)
    fun closeQuizSheet() = quizViewModel.closeQuizSheet()
    fun clearQuizGenerationFeedback() = quizViewModel.clearQuizGenerationFeedback()
    fun loadQuizQuestionsForTrack(trackId: Long) = quizViewModel.loadQuizQuestionsForTrack(trackId)
    fun generateQuizForTrack(track: AudioTrack, count: Int = 4) = quizViewModel.generateQuizForTrack(track, count)
    fun recordQuizAnswer(question: QuizQuestion, isCorrect: Boolean) = quizViewModel.recordQuizAnswer(question, isCorrect)
    fun clearQuizBankForTrack(trackId: Long) = quizViewModel.clearQuizBankForTrack(trackId)
    fun deleteQuizQuestion(questionId: Long) = quizViewModel.deleteQuizQuestion(questionId)

    // Notebook Vocabulary Quiz State (Delegated to quizViewModel)
    val isNotebookQuizSheetOpen: MutableStateFlow<Boolean> get() = quizViewModel.isNotebookQuizSheetOpen
    val isGeneratingNotebookQuiz: MutableStateFlow<Boolean> get() = quizViewModel.isGeneratingNotebookQuiz
    val notebookQuizError: MutableStateFlow<String?> get() = quizViewModel.notebookQuizError
    val notebookQuizGeneratedQuestions: MutableStateFlow<List<com.example.ai.GeneratedNoteQuizItem>> get() = quizViewModel.notebookQuizGeneratedQuestions
    val notebookQuizSuccessMessage: MutableStateFlow<String?> get() = quizViewModel.notebookQuizSuccessMessage
    val notebookVocabularyQuestions: StateFlow<List<QuizQuestion>> get() = quizViewModel.notebookVocabularyQuestions

    fun openNotebookQuizSheet(initialNotes: List<Note>? = null) = quizViewModel.openNotebookQuizSheet(initialNotes)
    fun closeNotebookQuizSheet() = quizViewModel.closeNotebookQuizSheet()
    fun resetNotebookQuiz() = quizViewModel.resetNotebookQuiz()
    fun clearNotebookQuizFeedback() = quizViewModel.clearNotebookQuizFeedback()
    fun deleteNotebookQuizQuestion(item: com.example.ai.GeneratedNoteQuizItem) = quizViewModel.deleteNotebookQuizQuestion(item)
    fun generateQuizFromNotes(notes: List<Note>, maxQuestions: Int = 4, onSuccess: (() -> Unit)? = null) =
        quizViewModel.generateQuizFromNotes(notes, maxQuestions, onSuccess)
    fun saveNotebookQuizQuestions(items: List<com.example.ai.GeneratedNoteQuizItem>, notesMap: Map<Long, Note> = emptyMap(), onSuccess: (Int) -> Unit = {}) =
        quizViewModel.saveNotebookQuizQuestions(items, notesMap, onSuccess)
    fun generateQuizForSingleNote(note: Note, onSuccess: (QuizQuestion) -> Unit = {}, onError: (String) -> Unit = {}) =
        quizViewModel.generateQuizForSingleNote(note, onSuccess, onError)

    // Unified Quiz Hub State (Delegated to quizViewModel)
    val isUnifiedQuizSheetOpen: MutableStateFlow<Boolean> get() = quizViewModel.isUnifiedQuizSheetOpen
    val activeQuizTabMode: StateFlow<QuizTabMode> get() = quizViewModel.activeQuizTabMode

    fun openUnifiedQuiz(tabMode: QuizTabMode = QuizTabMode.TRACK, initialTrack: AudioTrack? = null, initialNotes: List<Note>? = null) =
        quizViewModel.openUnifiedQuiz(tabMode, initialTrack, initialNotes)
    fun openUnifiedQuizSheet(initialTrack: AudioTrack? = null, initialNotes: List<Note>? = null) =
        quizViewModel.openUnifiedQuizSheet(initialTrack, initialNotes)
    fun closeUnifiedQuizSheet() = quizViewModel.closeUnifiedQuizSheet()

    // Unified AI Hub State & Controls
    val isAiHubOpen = MutableStateFlow(false)
    val aiHubInitialFunction = MutableStateFlow(AiFunctionType.CHAT)
    val aiHubInitialTrack = MutableStateFlow<AudioTrack?>(null)
    val aiHubInitialNotes = MutableStateFlow<List<Note>?>(null)
    val aiHubInitialTask = MutableStateFlow<Task?>(null)

    fun openAiHub(
        function: AiFunctionType = AiFunctionType.CHAT,
        track: AudioTrack? = null,
        notes: List<Note>? = null,
        task: Task? = null
    ) {
        aiHubInitialFunction.value = function
        aiHubInitialTrack.value = track ?: AudioPlayerManager.currentTrack.value
        aiHubInitialNotes.value = notes
        aiHubInitialTask.value = task
        isAiHubOpen.value = true
    }

    fun closeAiHub() {
        isAiHubOpen.value = false
        aiHubInitialTrack.value = null
        aiHubInitialNotes.value = null
        aiHubInitialTask.value = null
    }

    data class SavedApiKey(
        val id: String = UUID.randomUUID().toString(),
        val name: String,
        val key: String
    )

    private fun persistApiKeys(keys: List<SavedApiKey>, activeId: String) {
        val sharedPref = getApplication<Application>().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val array = JSONArray()
        for (k in keys) {
            val obj = JSONObject()
            obj.put("id", k.id)
            obj.put("name", k.name)
            obj.put("key", k.key)
            array.put(obj)
        }
        val activeKeyString = keys.firstOrNull { it.id == activeId }?.key ?: ""
        sharedPref.edit()
            .putString("saved_gemini_api_keys", array.toString())
            .putString("active_gemini_api_key_id", activeId)
            .putString("custom_gemini_api_key", activeKeyString)
            .apply()

        savedApiKeys = keys
        activeApiKeyId = activeId
        customGeminiApiKey = activeKeyString
        GeminiService.setConfiguredApiKeys(activeKeyString, keys.map { it.key })
    }

    fun selectActiveApiKey(keyId: String) {
        val target = savedApiKeys.firstOrNull { it.id == keyId }
        if (target != null) {
            persistApiKeys(savedApiKeys, target.id)
        }
    }

    fun addSavedApiKey(name: String, key: String, setAsActive: Boolean = true): SavedApiKey {
        val trimmedKey = key.trim()
        val trimmedName = name.trim().ifBlank { "Key ${savedApiKeys.size + 1}" }
        val newKey = SavedApiKey(name = trimmedName, key = trimmedKey)
        val updated = savedApiKeys + newKey
        val newActiveId = if (setAsActive || activeApiKeyId.isBlank() || savedApiKeys.none { it.id == activeApiKeyId }) {
            newKey.id
        } else {
            activeApiKeyId
        }
        persistApiKeys(updated, newActiveId)
        return newKey
    }

    fun updateSavedApiKey(id: String, newName: String, newKey: String) {
        val trimmedKey = newKey.trim()
        val trimmedName = newName.trim().ifBlank { "Key" }
        val updated = savedApiKeys.map {
            if (it.id == id) it.copy(name = trimmedName, key = trimmedKey) else it
        }
        persistApiKeys(updated, activeApiKeyId)
    }

    fun deleteSavedApiKey(id: String) {
        val updated = savedApiKeys.filterNot { it.id == id }
        val newActiveId = if (activeApiKeyId == id) {
            updated.firstOrNull()?.id ?: ""
        } else {
            activeApiKeyId
        }
        persistApiKeys(updated, newActiveId)
    }

    fun updateCustomGeminiApiKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.isBlank()) {
            if (activeApiKeyId.isNotBlank()) {
                deleteSavedApiKey(activeApiKeyId)
            } else {
                customGeminiApiKey = ""
                val sharedPref = getApplication<Application>().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                sharedPref.edit().putString("custom_gemini_api_key", "").apply()
            }
        } else {
            val existing = savedApiKeys.firstOrNull { it.id == activeApiKeyId }
            if (existing != null) {
                updateSavedApiKey(existing.id, existing.name, trimmed)
            } else {
                addSavedApiKey("Key 1", trimmed, setAsActive = true)
            }
        }
    }

    fun sendChatMessage(
        prompt: String,
        contextSummary: AudioContextSummary? = null
    ) = mediaAiViewModel.sendChatMessage(prompt, contextSummary)

    suspend fun generateNoteExplanation(
        quoteText: String,
        promptOrInstruction: String,
        trackTitle: String? = null
    ): Result<String> = mediaAiViewModel.generateNoteExplanation(quoteText, promptOrInstruction, trackTitle)

    fun cleanMarkdownToPlainText(input: String): String = mediaAiViewModel.cleanMarkdownToPlainText(input)

    // Stats variables
    var statsFilter by mutableStateOf("all") // all, today, week, month

    // Bulk selection state for library tracks
    val selectedTrackIds = MutableStateFlow<Set<Long>>(emptySet())

    // --- IMPORT SUMMARY NOTIFICATION SYSTEM ---
    data class ImportSummary(
        val folderName: String,
        val totalProcessed: Int,
        val newlyIndexedCount: Int,
        val existingCount: Int,
        val newlyIndexedFiles: List<String>
    )

    val lastImportSummary = MutableStateFlow<ImportSummary?>(null)

    fun clearImportSummary() {
        lastImportSummary.value = null
    }

    init {
        // Load saved settings from shared preferences
        val sharedPref = application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        selectedTheme = sharedPref.getString("theme", "dark") ?: "dark"
        thresholdSetting = sharedPref.getInt("threshold", 90)
        skipSecondsSetting = sharedPref.getInt("skip_seconds", 10)
        headsetControlsEnabled = sharedPref.getBoolean("headset_controls_enabled", true)
        headsetMultiClickAction = sharedPref.getString("headset_multiclick_action", "NEXT_PREV") ?: "NEXT_PREV"
        
        // Load saved API keys and active selection
        val savedKeysJson = sharedPref.getString("saved_gemini_api_keys", "") ?: ""
        val legacyKey = sharedPref.getString("custom_gemini_api_key", "") ?: ""
        val activeKeyIdFromPref = sharedPref.getString("active_gemini_api_key_id", "") ?: ""

        val parsedKeys = mutableListOf<SavedApiKey>()
        if (savedKeysJson.isNotBlank()) {
            try {
                val array = JSONArray(savedKeysJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optString("id", UUID.randomUUID().toString())
                    val name = obj.optString("name", "Key ${i + 1}")
                    val key = obj.optString("key", "")
                    if (key.isNotBlank()) {
                        parsedKeys.add(SavedApiKey(id, name, key))
                    }
                }
            } catch (e: Exception) {
                Log.w("AppViewModel", "Failed to parse saved API keys: ${e.message}")
            }
        }

        if (parsedKeys.isEmpty() && legacyKey.isNotBlank()) {
            val initial = SavedApiKey(name = "Key 1", key = legacyKey)
            parsedKeys.add(initial)
            savedApiKeys = parsedKeys
            activeApiKeyId = initial.id
            customGeminiApiKey = legacyKey
            persistApiKeys(parsedKeys, initial.id)
        } else {
            savedApiKeys = parsedKeys
            val active = parsedKeys.firstOrNull { it.id == activeKeyIdFromPref } ?: parsedKeys.firstOrNull()
            if (active != null) {
                activeApiKeyId = active.id
                customGeminiApiKey = active.key
            } else {
                activeApiKeyId = ""
                customGeminiApiKey = ""
            }
            GeminiService.setConfiguredApiKeys(customGeminiApiKey, parsedKeys.map { it.key })
        }

        Loc.currentLanguage = sharedPref.getString("language", "en") ?: "en"
        segmentSourceSetting = sharedPref.getString("segment_source", "SILENCE") ?: "SILENCE"
        practicePauseMultiplierSetting = sharedPref.getFloat("practice_pause_multiplier", 1.0f)
        silenceSensitivitySetting = sharedPref.getString("silence_sensitivity", "MEDIUM") ?: "MEDIUM"
        silenceMinDurationSetting = sharedPref.getLong("silence_min_duration", 500L)
        silencePaddingSetting = sharedPref.getLong("silence_padding", 200L)

        AudioPlayerManager.init(application, repository)
        AudioPlayerManager.setSettings(thresholdSetting, skipSecondsSetting)
        AudioPlayerManager.setHeadsetSettings(headsetControlsEnabled, headsetMultiClickAction)
        AudioPlayerManager.setPracticeSettings(segmentSourceSetting, practicePauseMultiplierSetting)
        AudioPlayerManager.setSilenceSettings(silenceSensitivitySetting, silenceMinDurationSetting, silencePaddingSetting)

        viewModelScope.launch(Dispatchers.IO) {
            repository.syncAndCleanTaskLabels()
        }

        // Automatic backup snapshot & silent recovery guard
        viewModelScope.launch(Dispatchers.IO) {
            delay(2000L) // Wait for initial database flows to load
            val tasksEmpty = allTasks.value.isEmpty()
            val historyEmpty = playbackHistory.value.isEmpty()
            val notesEmpty = notes.value.isEmpty()

            if (tasksEmpty && historyEmpty && notesEmpty) {
                val latestSnapshot = com.example.util.AutoBackupManager.getLatestAutoBackupString(application)
                if (!latestSnapshot.isNullOrBlank()) {
                    Log.i("AppViewModel", "Database empty on launch. Automatically recovering from latest snapshot...")
                    restoreBackupFromJsonString(latestSnapshot)
                }
            } else {
                triggerAutoBackup()
            }
        }

        // Asynchronously extract and cache track metadata for easy real-time searching by title/artist
        viewModelScope.launch(Dispatchers.IO) {
            tracks.collect { trackList ->
                val currentCache = trackMetadataCache.value
                val updatedCache = currentCache.toMutableMap()
                var hasChanged = false
                trackList.forEach { track ->
                    if (!updatedCache.containsKey(track.id)) {
                        try {
                            val meta = com.example.util.AudioMetadataExtractor.extract(track.filePath)
                            updatedCache[track.id] = meta
                            hasChanged = true
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                if (hasChanged) {
                    trackMetadataCache.value = updatedCache
                }
            }
        }

        // Periodic system folder scanner to verify files are present
        viewModelScope.launch(Dispatchers.IO) {
            delay(3000) // Delay first run so initial app startup and first frame draw complete smoothly
            while (true) {
                checkFilesSanity()
                // Sync dynamic task dependencies in background
                syncAllDynamicTasks()
                delay(15000)
            }
        }

        // Keep note tags database strictly synced with active notes (removes unused tags with 0 notes & removes duplicates)
        viewModelScope.launch(Dispatchers.IO) {
            notes.collect { noteList ->
                val favTag = Loc.getText("favorite_tag_name")
                val activeTags = noteList
                    .flatMap { it.getTagsList() }
                    .map { it.trim() }
                    .filter {
                        it.isNotEmpty() &&
                        !it.equals("favorite", ignoreCase = true) &&
                        it != "المفضلة" &&
                        !it.equals(favTag, ignoreCase = true)
                    }
                    .distinctBy { it.lowercase() }
                    .toSet()
                repository.syncAndCleanTags(activeTags)
            }
        }
    }

    internal suspend fun syncAllDynamicTasks() = withContext(Dispatchers.IO) {
        val activeList = repository.dao.getActiveTasksFlow().firstOrNull() ?: return@withContext
        for (task in activeList) {
            if (task.sourceType == "FOLDER" || task.sourceType == "PLAYLIST") {
                repository.syncDynamicTaskTracks(task.id)
            }
        }
    }

    fun updateSettings(theme: String, threshold: Int, skipSeconds: Int, lang: String) {
        selectedTheme = theme
        thresholdSetting = threshold
        skipSecondsSetting = skipSeconds
        Loc.currentLanguage = lang

        val sharedPref = getApplication<Application>().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("theme", theme)
            putInt("threshold", threshold)
            putInt("skip_seconds", skipSeconds)
            putString("language", lang)
            apply()
        }

        AudioPlayerManager.setSettings(threshold, skipSeconds)
        AudioPlayerManager.updateNotification()
    }

    fun updateHeadsetSettings(enabled: Boolean, action: String) {
        headsetControlsEnabled = enabled
        headsetMultiClickAction = action
        AudioPlayerManager.setHeadsetSettings(enabled, action)
    }

    fun updatePracticeSettings(source: String, multiplier: Float) {
        segmentSourceSetting = source
        practicePauseMultiplierSetting = multiplier
        val sharedPref = getApplication<Application>().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("segment_source", source)
            putFloat("practice_pause_multiplier", multiplier)
            apply()
        }
        AudioPlayerManager.setPracticeSettings(source, multiplier)
    }

    fun updateSilenceSettings(sensitivity: String, minDurationMs: Long, paddingMs: Long) {
        silenceSensitivitySetting = sensitivity
        silenceMinDurationSetting = minDurationMs
        silencePaddingSetting = paddingMs
        val sharedPref = getApplication<Application>().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("silence_sensitivity", sensitivity)
            putLong("silence_min_duration", minDurationMs)
            putLong("silence_padding", paddingMs)
            apply()
        }
        AudioPlayerManager.setSilenceSettings(sensitivity, minDurationMs, paddingMs)
    }

    fun reanalyzeCurrentTrackPracticeSegments(context: Context) {
        AudioPlayerManager.reanalyzePracticeSegments(context, repository, silent = false)
    }

    fun saveManualPracticeSegments(track: AudioTrack, boundaries: List<Long>, context: Context, autoEnable: Boolean = true) {
        AudioPlayerManager.saveManualPracticeSegments(context, track, boundaries, autoEnable)
    }

    fun startPracticeWithSource(track: AudioTrack, source: String, multiplier: Float, context: Context) {
        updatePracticeSettings(source, multiplier)
        AudioPlayerManager.applyPracticeSettingsAndStart(track, source, multiplier, context, repository)
    }

    fun resetTrackSegments(track: AudioTrack) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateTrackPracticeSegments(track.id, null)
            val updated = track.copy(practiceSegments = null)
            repository.dao.updateTrack(updated)
            withContext(Dispatchers.Main) {
                if (AudioPlayerManager.currentTrack.value?.id == track.id) {
                    AudioPlayerManager.clearPracticeSegmentsForCurrentTrack()
                }
            }
        }
    }

    internal suspend fun checkFilesSanity() = withContext(Dispatchers.IO) {
        // Fix any existing folder hierarchy links and clean folder names
        val allFolders = repository.getAllFoldersDirect()
        for (folder in allFolders) {
            if (folder.folderName.contains(" / ")) {
                val parts = folder.folderName.split(" / ").map { it.trim() }.filter { it.isNotEmpty() }
                if (parts.size > 1) {
                    val cleanName = parts.last()
                    val parent = allFolders.firstOrNull { other ->
                        other.id != folder.id && (
                            folder.folderPath.startsWith(other.folderPath + File.separator) ||
                            other.folderName == parts.dropLast(1).joinToString(" / ") ||
                            other.folderName == parts[parts.size - 2]
                        )
                    }
                    val updatedFolder = folder.copy(
                        folderName = cleanName,
                        parentFolderId = parent?.id ?: folder.parentFolderId
                    )
                    repository.dao.updateFolder(updatedFolder)
                }
            }
        }

        val refreshedFolders = repository.getAllFoldersDirect()
        for (folder in refreshedFolders) {
            if (folder.parentFolderId == null) {
                val parent = refreshedFolders.filter { other ->
                    other.id != folder.id && folder.folderPath.startsWith(other.folderPath + File.separator)
                }.maxByOrNull { it.folderPath.length }

                if (parent != null) {
                    repository.dao.updateFolder(folder.copy(parentFolderId = parent.id))
                }
            }
        }

        val currentTracks = repository.dao.getAllTracksFlow().firstOrNull() ?: return@withContext
        val allHistory = repository.dao.getPlaybackHistoryFlow().firstOrNull() ?: emptyList()
        val historyGrouped = allHistory.groupBy { it.trackId }
        val validFolderIds = refreshedFolders.map { it.id }.toSet()
        
        for (track in currentTracks) {
            // Clean up any orphaned tracks left over from previously deleted folders
            if (track.parentFolderId != null && !validFolderIds.contains(track.parentFolderId) && !track.isIndependent) {
                repository.deleteTrack(track)
                continue
            }

            val file = File(track.filePath)
            val exists = file.exists()
            
            val actualHistoryPlayCount = historyGrouped[track.id]?.size ?: 0
            val desiredPlayCount = maxOf(track.playCount, actualHistoryPlayCount)

            var updatedSubPath = track.subtitlePath
            if (updatedSubPath.isNullOrBlank() && track.subtitleContent.isNullOrBlank()) {
                val autoSub = com.example.player.SubtitleParser.findMatchingSubtitleFile(track.filePath)
                if (autoSub != null && autoSub.exists()) {
                    updatedSubPath = autoSub.absolutePath
                }
            }
            
            val isMissingChanged = track.isMissing != !exists
            val isPlayCountChanged = track.playCount != desiredPlayCount
            val isSubPathChanged = track.subtitlePath != updatedSubPath
            
            if (isMissingChanged || isPlayCountChanged || isSubPathChanged) {
                val freshTrack = repository.getTrackById(track.id) ?: track
                repository.updateTrack(freshTrack.copy(
                    isMissing = !exists,
                    playCount = maxOf(freshTrack.playCount, desiredPlayCount),
                    subtitlePath = updatedSubPath
                ))
            }
        }

        // Auto-relink any unlinked notes to active tracks
        repository.relinkNotesToActiveTracks()
    }

    fun rebindMissingTrack(track: AudioTrack, newPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(newPath)
            if (file.exists()) {
                repository.updateTrack(track.copy(filePath = newPath, fileName = file.name, isMissing = false))
            }
        }
    }

    // --- AUTOMATIC FILE & STATS RELINKING ENGINE ---
    val isScanningForRelink = mutableStateOf(false)
    val relinkCandidates = mutableStateOf<List<com.example.data.entities.TrackRelinkCandidate>>(emptyList())
    val showRelinkDialog = mutableStateOf(false)

    fun deleteTrackFromApp(track: AudioTrack) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteTrack(track)
        }
    }

    // --- PLAYBACK TRIGGERS ---
    fun selectAndPlay(track: AudioTrack, playlistTracks: List<AudioTrack> = emptyList()) {
        AudioPlayerManager.playTrack(track, playlistTracks)
    }

    fun addTrackToPlayNext(track: AudioTrack) {
        AudioPlayerManager.addTrackToQueueNext(track)
    }

    fun addTracksToPlayNext(tracks: List<AudioTrack>) {
        AudioPlayerManager.addTracksToQueueNext(tracks)
    }

    // --- SELECTION CONTROL ---
    fun toggleTrackSelection(trackId: Long) {
        val current = selectedTrackIds.value.toMutableSet()
        if (current.contains(trackId)) {
            current.remove(trackId)
        } else {
            current.add(trackId)
        }
        selectedTrackIds.value = current
    }

    fun clearTrackSelections() {
        selectedTrackIds.value = emptySet()
    }

    fun deleteSelectedTracks() {
        val targets = selectedTrackIds.value
        viewModelScope.launch(Dispatchers.IO) {
            for (id in targets) {
                val t = repository.getTrackById(id)
                if (t != null) {
                    repository.deleteTrack(t)
                }
            }
            withContext(Dispatchers.Main) {
                clearTrackSelections()
            }
        }
    }

    // AI Scene Detection State
    val isDetectingScenes = MutableStateFlow(false)
    val detectingTrackName = MutableStateFlow<String?>(null)
    val sceneDetectionStatus = MutableStateFlow<String?>(null)
    internal var sceneDetectionJob: kotlinx.coroutines.Job? = null
}
