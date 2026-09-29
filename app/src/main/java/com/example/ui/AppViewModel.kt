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
import com.example.util.SampleAudioGenerator
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

    private suspend fun syncAllDynamicTasks() = withContext(Dispatchers.IO) {
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

    private suspend fun checkFilesSanity() = withContext(Dispatchers.IO) {
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

    fun searchRelinkCandidates(autoOpenDialog: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            isScanningForRelink.value = true
            try {
                val allTracks = repository.dao.getAllTracksFlow().firstOrNull() ?: emptyList()
                val allFolders = repository.dao.getAllFoldersDirect()
                val folderMap = allFolders.associateBy { it.id }
                val allHistory = repository.dao.getPlaybackHistoryFlow().firstOrNull() ?: emptyList()
                val allTaskProgress = repository.dao.getAllTaskProgressFlow().firstOrNull() ?: emptyList()
                val allNotes = repository.dao.getAllNotesFlow().firstOrNull() ?: emptyList()

                fun cleanStr(s: String?): String {
                    if (s.isNullOrBlank()) return ""
                    return s.trim().lowercase(Locale.ROOT)
                        .removeSuffix(".mp3")
                        .removeSuffix(".m4a")
                        .removeSuffix(".wav")
                        .removeSuffix(".aac")
                        .removeSuffix(".ogg")
                        .removeSuffix(".flac")
                        .removeSuffix(".opus")
                        .replace("_", " ")
                        .replace("-", " ")
                        .replace(".", " ")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                }

                fun getFolderForTrack(track: AudioTrack): String {
                    val fName = track.parentFolderId?.let { folderMap[it]?.folderName }
                    if (!fName.isNullOrBlank()) return fName
                    val parentDir = try { File(track.filePath).parentFile?.name } catch (e: Exception) { null }
                    if (!parentDir.isNullOrBlank() && parentDir != "files" && parentDir != "imported") return parentDir
                    return ""
                }

                val activeTracks = allTracks.filter { !it.isVirtualScene && File(it.filePath).exists() && !it.isMissing }
                val activeTrackIds = activeTracks.map { it.id }.toSet()

                // Historical candidate sources
                val missingTracks = allTracks.filter { !it.isVirtualScene && (it.isMissing || !File(it.filePath).exists()) }
                val olderDuplicateTracks = allTracks.filter { old ->
                    !old.isVirtualScene && old.playCount > 0 && activeTracks.any { active ->
                        active.id != old.id && cleanStr(active.fileName) == cleanStr(old.fileName) && active.playCount == 0
                    }
                }
                val historicalAudioTracks = (missingTracks + olderDuplicateTracks).distinctBy { it.id }

                val candidatesList = mutableListOf<com.example.data.entities.TrackRelinkCandidate>()
                val matchedActiveIds = mutableSetOf<Long>()
                val matchedHistoricalTrackIds = mutableSetOf<Long>()

                fun countMatchingNotes(activeTrack: AudioTrack, histId: Long?): Int {
                    val activeCleanName = cleanStr(activeTrack.fileName)
                    val activeCleanFolder = cleanStr(getFolderForTrack(activeTrack))
                    return allNotes.count { note ->
                        (histId != null && note.trackId == histId) ||
                        note.trackId == activeTrack.id ||
                        run {
                            val noteCleanName = cleanStr(note.trackName)
                            val nameMatch = noteCleanName.isNotBlank() && (noteCleanName == activeCleanName || noteCleanName.replace(" ", "") == activeCleanName.replace(" ", ""))
                            if (!nameMatch) return@run false
                            val noteCleanFolder = cleanStr(note.folderName)
                            when {
                                activeCleanFolder.isNotBlank() && noteCleanFolder.isNotBlank() ->
                                    noteCleanFolder == activeCleanFolder || noteCleanFolder.contains(activeCleanFolder) || activeCleanFolder.contains(noteCleanFolder)
                                else -> true
                            }
                        }
                    }
                }

                // 1. Match active tracks against missing/older AudioTracks in DB
                for (active in activeTracks) {
                    val activeCleanName = cleanStr(active.fileName)
                    val activeFolder = getFolderForTrack(active)
                    val activeCleanFolder = cleanStr(activeFolder)

                    val match = historicalAudioTracks.firstOrNull { h ->
                        h.id != active.id && !matchedHistoricalTrackIds.contains(h.id) && run {
                            val hCleanName = cleanStr(h.fileName)
                            val nameMatch = hCleanName == activeCleanName ||
                                    hCleanName.replace(" ", "") == activeCleanName.replace(" ", "")
                            if (!nameMatch) return@run false

                            val hFolder = getFolderForTrack(h)
                            val hCleanFolder = cleanStr(hFolder)
                            val folderMatch = when {
                                activeCleanFolder.isNotBlank() && hCleanFolder.isNotBlank() ->
                                    hCleanFolder == activeCleanFolder ||
                                    hCleanFolder.contains(activeCleanFolder) ||
                                    activeCleanFolder.contains(hCleanFolder)
                                activeCleanFolder.isBlank() && hCleanFolder.isBlank() -> true
                                else -> true // If one side had no folder, allow name match
                            }
                            folderMatch
                        }
                    }

                    if (match != null) {
                        val hFolder = getFolderForTrack(match)
                        val hHistoryCount = allHistory.count { it.trackId == match.id }
                        val hTaskCount = allTaskProgress.count { it.trackId == match.id }
                        val hNoteCount = countMatchingNotes(active, match.id)
                        val playCount = maxOf(match.playCount, hHistoryCount)
                        val progressPercent = match.getProgressPercent()

                        candidatesList.add(
                            com.example.data.entities.TrackRelinkCandidate(
                                activeTrack = active,
                                historicalTrackId = match.id,
                                fileName = active.fileName,
                                folderName = activeFolder.ifBlank { hFolder },
                                historicalFolderName = hFolder,
                                playCount = playCount,
                                progressPercent = progressPercent,
                                durationMs = if (match.duration > 0) match.duration else active.duration,
                                historyEntriesCount = hHistoryCount,
                                taskProgressCount = hTaskCount,
                                notesCount = hNoteCount,
                                isMissingRecord = match.isMissing || !File(match.filePath).exists()
                            )
                        )
                        matchedActiveIds.add(active.id)
                        matchedHistoricalTrackIds.add(match.id)
                    }
                }

                // 2. Check orphaned history logs that might not have an AudioTrack entity row
                val orphanedHistory = allHistory
                    .filter { it.trackId !in activeTrackIds && it.trackId !in matchedHistoricalTrackIds }
                    .groupBy { it.trackId }

                for ((orphanedId, historyList) in orphanedHistory) {
                    val rawTrackName = historyList.firstOrNull()?.trackName ?: continue
                    val cleanHistoryName = cleanStr(rawTrackName)

                    val matchingActive = activeTracks.firstOrNull { active ->
                        !matchedActiveIds.contains(active.id) && cleanStr(active.fileName) == cleanHistoryName
                    }

                    if (matchingActive != null) {
                        val activeFolder = getFolderForTrack(matchingActive)
                        val noteFolder = allNotes.firstOrNull { it.trackId == orphanedId }?.folderName ?: ""
                        val taskCount = allTaskProgress.count { it.trackId == orphanedId }
                        val noteCount = countMatchingNotes(matchingActive, orphanedId)

                        candidatesList.add(
                            com.example.data.entities.TrackRelinkCandidate(
                                activeTrack = matchingActive,
                                historicalTrackId = orphanedId,
                                fileName = matchingActive.fileName,
                                folderName = activeFolder.ifBlank { noteFolder },
                                historicalFolderName = noteFolder,
                                playCount = historyList.size,
                                progressPercent = 100, // completed history record
                                durationMs = historyList.firstOrNull()?.durationMs ?: matchingActive.duration,
                                historyEntriesCount = historyList.size,
                                taskProgressCount = taskCount,
                                notesCount = noteCount,
                                isMissingRecord = true
                            )
                        )
                        matchedActiveIds.add(matchingActive.id)
                    }
                }

                // 3. Check active tracks that have unlinked/imported notes matching their file name & folder
                for (active in activeTracks) {
                    if (matchedActiveIds.contains(active.id)) continue
                    val activeCleanName = cleanStr(active.fileName)
                    val activeFolder = getFolderForTrack(active)
                    val activeCleanFolder = cleanStr(activeFolder)

                    // Find notes belonging to this active track that are unlinked or have outdated folderId/trackId
                    val matchingNotes = allNotes.filter { note ->
                        val noteCleanName = cleanStr(note.trackName)
                        val nameMatch = (noteCleanName.isNotBlank() && (noteCleanName == activeCleanName || noteCleanName.replace(" ", "") == activeCleanName.replace(" ", ""))) ||
                                (note.trackId != null && note.trackId == active.id)

                        if (!nameMatch) return@filter false

                        val noteCleanFolder = cleanStr(note.folderName)
                        val folderMatch = when {
                            activeCleanFolder.isNotBlank() && noteCleanFolder.isNotBlank() ->
                                noteCleanFolder == activeCleanFolder ||
                                noteCleanFolder.contains(activeCleanFolder) ||
                                activeCleanFolder.contains(noteCleanFolder)
                            activeCleanFolder.isBlank() && noteCleanFolder.isBlank() -> true
                            else -> true
                        }
                        folderMatch && (note.trackId != active.id || note.folderId != active.parentFolderId)
                    }

                    if (matchingNotes.isNotEmpty()) {
                        val historyCount = allHistory.count { it.trackId == active.id }
                        val taskCount = allTaskProgress.count { it.trackId == active.id }
                        val playCount = maxOf(active.playCount, historyCount)
                        val noteFolder = matchingNotes.firstOrNull { !it.folderName.isNullOrBlank() }?.folderName ?: activeFolder

                        candidatesList.add(
                            com.example.data.entities.TrackRelinkCandidate(
                                activeTrack = active,
                                historicalTrackId = matchingNotes.firstOrNull()?.trackId ?: active.id,
                                fileName = active.fileName,
                                folderName = activeFolder.ifBlank { noteFolder },
                                historicalFolderName = noteFolder,
                                playCount = playCount,
                                progressPercent = active.getProgressPercent(),
                                durationMs = active.duration,
                                historyEntriesCount = historyCount,
                                taskProgressCount = taskCount,
                                notesCount = matchingNotes.size,
                                isMissingRecord = false
                            )
                        )
                        matchedActiveIds.add(active.id)
                    }
                }

                val sortedCandidates = candidatesList.sortedWith(
                    compareBy({ it.folderName.lowercase(Locale.ROOT) }, { it.fileName.lowercase(Locale.ROOT) })
                )

                withContext(Dispatchers.Main) {
                    relinkCandidates.value = sortedCandidates
                    isScanningForRelink.value = false
                    if (autoOpenDialog) {
                        showRelinkDialog.value = true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isScanningForRelink.value = false
                }
            }
        }
    }

    fun applyRelinkCandidates(
        selectedCandidates: List<com.example.data.entities.TrackRelinkCandidate>,
        onComplete: ((Int) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            var linkedCount = 0
            for (candidate in selectedCandidates) {
                try {
                    val active = repository.getTrackById(candidate.activeTrack.id) ?: candidate.activeTrack
                    val oldId = candidate.historicalTrackId
                    val oldTrack = repository.getTrackById(oldId)

                    val finalTrackId: Long
                    val finalFileName: String
                    val finalFolderId: Long?
                    val finalFolderName: String

                    if (oldTrack != null && oldTrack.id != active.id) {
                        // Merge active physical file into historical track record
                        val effectivePlayCount = maxOf(oldTrack.playCount, active.playCount, candidate.playCount)
                        val effectiveListenedSegments = if (oldTrack.listenedSegments.isNotBlank()) oldTrack.listenedSegments else active.listenedSegments
                        val effectiveLastPosition = if (oldTrack.lastPosition > 0) oldTrack.lastPosition else active.lastPosition
                        val effectiveDuration = if (active.duration > 0) active.duration else oldTrack.duration

                        val mergedTrack = oldTrack.copy(
                            filePath = active.filePath,
                            fileName = active.fileName,
                            duration = effectiveDuration,
                            playCount = effectivePlayCount,
                            lastPosition = effectiveLastPosition,
                            listenedSegments = effectiveListenedSegments,
                            parentFolderId = active.parentFolderId ?: oldTrack.parentFolderId,
                            isIndependent = active.isIndependent,
                            isMissing = false,
                            subtitlePath = active.subtitlePath ?: oldTrack.subtitlePath,
                            subtitleContent = active.subtitleContent ?: oldTrack.subtitleContent,
                            practiceSegments = oldTrack.practiceSegments ?: active.practiceSegments,
                            currentPlayActualListeningMs = maxOf(oldTrack.currentPlayActualListeningMs, active.currentPlayActualListeningMs)
                        )

                        // Reassign any new references to historical track ID
                        repository.reassignTrackReferences(fromTrackId = active.id, toTrackId = oldTrack.id)

                        // Update historical track with live file & preserved stats
                        repository.updateTrack(mergedTrack)

                        // Remove duplicate active track
                        repository.deleteTrackById(active.id)

                        finalTrackId = oldTrack.id
                        finalFileName = active.fileName
                        finalFolderId = mergedTrack.parentFolderId
                        finalFolderName = candidate.folderName

                        linkedCount++
                    } else if (oldTrack != null && oldTrack.id == active.id) {
                        // Same entity, mark active and update play count
                        repository.updateTrack(oldTrack.copy(
                            isMissing = false,
                            playCount = maxOf(oldTrack.playCount, candidate.playCount)
                        ))
                        finalTrackId = oldTrack.id
                        finalFileName = oldTrack.fileName
                        finalFolderId = oldTrack.parentFolderId
                        finalFolderName = candidate.folderName
                        linkedCount++
                    } else {
                        // Historical record lived in logs; reassign logs to active track ID
                        repository.reassignTrackReferences(fromTrackId = oldId, toTrackId = active.id)
                        repository.updateTrack(active.copy(
                            playCount = maxOf(active.playCount, candidate.playCount),
                            isMissing = false
                        ))
                        finalTrackId = active.id
                        finalFileName = active.fileName
                        finalFolderId = active.parentFolderId
                        finalFolderName = candidate.folderName
                        linkedCount++
                    }

                    // Re-link and sync all matching notes to final track & folder
                    val currentNotes = repository.dao.getAllNotesDirect()
                    fun cleanS(s: String?): String {
                        if (s.isNullOrBlank()) return ""
                        return s.trim().lowercase(Locale.ROOT)
                            .removeSuffix(".mp3").removeSuffix(".m4a").removeSuffix(".wav")
                            .removeSuffix(".aac").removeSuffix(".ogg").removeSuffix(".flac").removeSuffix(".opus")
                            .replace("_", " ").replace("-", " ").replace(".", " ").replace(Regex("\\s+"), " ").trim()
                    }
                    val activeCleanName = cleanS(finalFileName)
                    val activeCleanFolder = cleanS(finalFolderName)

                    val matchingNotes = currentNotes.filter { note ->
                        note.trackId == oldId ||
                        note.trackId == active.id ||
                        run {
                            val noteCleanName = cleanS(note.trackName)
                            val nameMatch = noteCleanName.isNotBlank() && (noteCleanName == activeCleanName || noteCleanName.replace(" ", "") == activeCleanName.replace(" ", ""))
                            if (!nameMatch) return@run false
                            val noteCleanFolder = cleanS(note.folderName)
                            when {
                                activeCleanFolder.isNotBlank() && noteCleanFolder.isNotBlank() ->
                                    noteCleanFolder == activeCleanFolder || noteCleanFolder.contains(activeCleanFolder) || activeCleanFolder.contains(noteCleanFolder)
                                else -> true
                            }
                        }
                    }

                    for (n in matchingNotes) {
                        repository.dao.updateNote(
                            n.copy(
                                trackId = finalTrackId,
                                trackName = finalFileName,
                                folderId = finalFolderId,
                                folderName = finalFolderName
                            )
                        )
                        repository.dao.updateVocabularyTrackForNote(n.id, finalTrackId)
                        repository.dao.updateQuizQuestionsTrackForNote(n.id, finalTrackId)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Sync sanity, tasks, and run comprehensive note sweep
            repository.relinkNotesToActiveTracks()
            checkFilesSanity()
            syncAllDynamicTasks()

            withContext(Dispatchers.Main) {
                showRelinkDialog.value = false
                relinkCandidates.value = emptyList()
                val context = getApplication<Application>()
                Toast.makeText(
                    context,
                    String.format(Locale.getDefault(), Loc.getText("relink_success_toast"), linkedCount),
                    Toast.LENGTH_LONG
                ).show()
                onComplete?.invoke(linkedCount)
            }
        }
    }

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

    // --- DIRECT CREATE PLAYLISTS & EDIT ---
    fun createPlaylist(name: String, tracksToAdd: List<AudioTrack> = emptyList()) {
        viewModelScope.launch(Dispatchers.IO) {
            val playlistId = repository.addPlaylist(name)
            tracksToAdd.forEachIndexed { i, track ->
                repository.addTrackToPlaylist(playlistId, track.id, i)
            }
        }
    }

    fun editPlaylistTitle(id: Long, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val pl = repository.getPlaylistById(id) ?: return@launch
            repository.updatePlaylist(pl.copy(name = newName))
        }
    }

    fun editFolderName(id: Long, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val folder = repository.getFolderById(id) ?: return@launch
            repository.updateFolder(folder.copy(folderName = newName))
        }
    }

    fun deleteFolder(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteFolder(id)
        }
    }

    fun deletePlaylist(id: Long, removeAudioFilesToo: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (removeAudioFilesToo) {
                val tracks = repository.getTracksForPlaylist(id)
                for (track in tracks) {
                    repository.deleteTrack(track)
                }
            } else {
                // Keep them - since they were in playlist, make sure they are flagged as independent
                val tracks = repository.getTracksForPlaylist(id)
                for (track in tracks) {
                    if (track.parentFolderId == null) {
                        repository.updateTrack(track.copy(isIndependent = true))
                    }
                }
            }
            repository.deletePlaylist(id)
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deletePlaylistTrack(playlistId, trackId)
        }
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addTrackToPlaylist(playlistId, trackId)
            
            // Sync any active tasks on this playlist
            val activeTasksList = repository.getActiveTasksDirect()
            activeTasksList.filter { it.sourceType == "PLAYLIST" && it.sourceId == playlistId }.forEach {
                repository.syncDynamicTaskTracks(it.id)
            }
        }
    }

    fun addTracksToPlaylist(playlistId: Long, trackIds: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            trackIds.forEach { id ->
                repository.addTrackToPlaylist(playlistId, id)
            }
            
            // Sync any active tasks on this playlist
            val activeTasksList = repository.getActiveTasksDirect()
            activeTasksList.filter { it.sourceType == "PLAYLIST" && it.sourceId == playlistId }.forEach {
                repository.syncDynamicTaskTracks(it.id)
            }
        }
    }

    // --- TASK CONTROL ---
    fun createTask(
        title: String,
        sourceType: String,
        sourceId: Long?,
        targetType: String,
        targetValue: Int,
        scheduledDays: String,
        reminderTime: String,
        startDate: Long,
        endDate: Long?,
        manualTrackIds: List<Long> = emptyList(),
        customThreshold: Int? = null,
        labels: String = "",
        dailyTargetValue: Int? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val finalTitle = Task.buildCombinedTitle(title, labels)
            val taskId = repository.addTask(
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

            // If manual, setup progresses
            if (sourceType == "TRACKS") {
                manualTrackIds.forEach { trackId ->
                    repository.dao.insertTaskProgress(TaskTrackProgress(taskId, trackId))
                }
            }

            val createdTask = repository.getTaskById(taskId)
            // Schedule daily alarm reminder
            com.example.receiver.AlarmReceiver.scheduleAlarm(
                getApplication(),
                taskId,
                createdTask?.getDisplayTitle() ?: finalTitle,
                scheduledDays,
                reminderTime
            )
        }
    }

    fun editTask(
        taskId: Long,
        title: String,
        targetType: String,
        targetValue: Int,
        scheduledDays: String,
        reminderTime: String,
        startDate: Long,
        endDate: Long?,
        sourceType: String,
        sourceId: Long?,
        manualTrackIds: List<Long> = emptyList(),
        customThreshold: Int? = null,
        labels: String = "",
        dailyTargetValue: Int? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val previousTask = repository.getTaskById(taskId) ?: return@launch
            val finalTitle = Task.buildCombinedTitle(title, labels)
            val updatedTask = previousTask.copy(
                title = finalTitle,
                targetType = targetType,
                targetValue = targetValue,
                scheduledDays = scheduledDays,
                reminderTime = reminderTime,
                startDate = startDate,
                endDate = endDate,
                sourceType = sourceType,
                sourceId = sourceId,
                customThreshold = customThreshold,
                labels = labels,
                dailyTargetValue = dailyTargetValue
            )
            repository.updateTask(updatedTask)

            // Preserve old track progress item states if any of them remain in the newly associated tracks
            val existingProgressMap = repository.getProgressForTask(taskId).associateBy { it.trackId }

            val newTrackIds = when (sourceType) {
                "TRACKS" -> manualTrackIds
                "FOLDER" -> {
                    if (sourceId != null) {
                        repository.getTracksForFolderRecursive(sourceId).map { it.id }
                    } else emptyList()
                }
                "PLAYLIST" -> {
                    if (sourceId != null) {
                        repository.getTracksForPlaylist(sourceId).map { it.id }
                    } else emptyList()
                }
                else -> emptyList()
            }

            val newTrackIdsSet = newTrackIds.toSet()

            // 1. Delete progress entries for tracks that have been removed
            existingProgressMap.keys.forEach { oldTrackId ->
                if (oldTrackId !in newTrackIdsSet) {
                    repository.deleteSingleTaskProgress(taskId, oldTrackId)
                }
            }

            // 2. Add progress entries for newly added tracks, but keep existing progress entries completely untouched!
            newTrackIds.forEach { trackId ->
                if (trackId !in existingProgressMap) {
                    repository.insertTaskProgress(TaskTrackProgress(taskId, trackId))
                }
            }

            // 3. Immediately evaluate updated progress states matching the new targetType and targetValue
            val refreshedProgressList = repository.getProgressForTask(taskId)
            var allTracksCompleted = refreshedProgressList.isNotEmpty()
            refreshedProgressList.forEach { progress ->
                val isDone = if (targetType == "PLAY_COUNT") {
                    progress.completedPlayCount >= targetValue
                } else {
                    progress.getDaysList().size >= targetValue
                }
                if (progress.isTrackCompleted != isDone) {
                    val updatedPrg = progress.copy(isTrackCompleted = isDone)
                    repository.insertTaskProgress(updatedPrg)
                }
                if (!isDone) {
                    allTracksCompleted = false
                }
            }

            // 4. Update task completion status
            val finalTask = updatedTask.copy(
                isCompleted = allTracksCompleted,
                status = if (allTracksCompleted) "COMPLETED" else "ACTIVE"
            )
            repository.updateTask(finalTask)

            // Reschedule alarm if the task is active and not completed
            if (!allTracksCompleted) {
                com.example.receiver.AlarmReceiver.scheduleAlarm(
                    getApplication(),
                    taskId,
                    finalTask.getDisplayTitle(),
                    scheduledDays,
                    reminderTime
                )
            } else {
                com.example.receiver.AlarmReceiver.cancelAlarm(getApplication(), taskId)
            }
        }
    }

    fun deleteTask(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteTask(id)
            com.example.receiver.AlarmReceiver.cancelAlarm(getApplication(), id)
        }
    }

    fun resetTaskTrackProgress(taskId: Long, trackId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertTaskProgress(TaskTrackProgress(taskId, trackId, completedPlayCount = 0, completedDays = "", isTrackCompleted = false))
        }
    }

    fun reactivateTask(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.reactivateTask(id)
            val task = repository.getTaskById(id)
            if (task != null) {
                com.example.receiver.AlarmReceiver.scheduleAlarm(
                    getApplication(),
                    task.id,
                    task.getDisplayTitle(),
                    task.scheduledDays,
                    task.reminderTime
                )
            }
        }
    }

    fun archiveTask(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val task = repository.getTaskById(id) ?: return@launch
            repository.updateTask(task.copy(isCompleted = true, status = "COMPLETED"))
            com.example.receiver.AlarmReceiver.cancelAlarm(getApplication(), id)
        }
    }

    fun duplicateTask(id: Long, onCompleted: (() -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val originalTask = repository.getTaskById(id) ?: return@launch
            val now = System.currentTimeMillis()
            val newTaskId = repository.addTask(
                title = originalTask.title,
                sourceType = originalTask.sourceType,
                sourceId = originalTask.sourceId,
                targetType = originalTask.targetType,
                targetValue = originalTask.targetValue,
                scheduledDays = originalTask.scheduledDays,
                reminderTime = originalTask.reminderTime,
                startDate = now,
                endDate = originalTask.endDate,
                customThreshold = originalTask.customThreshold,
                labels = originalTask.labels,
                dailyTargetValue = originalTask.dailyTargetValue
            )

            // If it's a manual TRACKS task, copy the track IDs from existing progress
            if (originalTask.sourceType == "TRACKS") {
                val existingProgress = repository.getProgressForTask(originalTask.id)
                for (progress in existingProgress) {
                    repository.dao.insertTaskProgress(
                        TaskTrackProgress(
                            taskId = newTaskId,
                            trackId = progress.trackId,
                            completedPlayCount = 0,
                            completedDays = "",
                            isTrackCompleted = false
                        )
                    )
                }
            }

            val createdTask = repository.getTaskById(newTaskId)
            // Schedule daily alarm reminder for the new task
            com.example.receiver.AlarmReceiver.scheduleAlarm(
                getApplication(),
                newTaskId,
                createdTask?.getDisplayTitle() ?: originalTask.getDisplayTitle(),
                originalTask.scheduledDays,
                originalTask.reminderTime
            )

            withContext(Dispatchers.Main) {
                onCompleted?.invoke()
            }
        }
    }

    fun deleteMultipleFolders(ids: Set<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            for (id in ids) {
                repository.deleteFolder(id)
            }
        }
    }

    fun deleteMultiplePlaylists(ids: Set<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            for (id in ids) {
                val tracks = repository.getTracksForPlaylist(id)
                for (track in tracks) {
                    if (track.parentFolderId == null) {
                        repository.updateTrack(track.copy(isIndependent = true))
                    }
                }
                repository.deletePlaylist(id)
            }
        }
    }

    fun deleteMultipleTasks(ids: Set<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            for (id in ids) {
                repository.deleteTask(id)
                com.example.receiver.AlarmReceiver.cancelAlarm(getApplication(), id)
            }
        }
    }

    fun bulkAddFoldersToTasks(folderIds: Set<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            for (id in folderIds) {
                val folder = repository.getFolderById(id) ?: continue
                val tracks = repository.getTracksForFolderRecursive(id)
                val titleString = folder.folderName
                
                val taskId = repository.addTask(
                    title = titleString,
                    sourceType = "FOLDER",
                    sourceId = id,
                    targetType = "PLAY_COUNT",
                    targetValue = 3,
                    scheduledDays = "SUNDAY,MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY",
                    reminderTime = "09:00 AM",
                    startDate = System.currentTimeMillis(),
                    endDate = null
                )

                for (track in tracks) {
                    repository.dao.insertTaskProgress(
                        com.example.data.TaskTrackProgress(
                            taskId = taskId,
                            trackId = track.id,
                            completedPlayCount = 0,
                            completedDays = "",
                            isTrackCompleted = false
                        )
                    )
                }

                com.example.receiver.AlarmReceiver.scheduleAlarm(
                    getApplication(),
                    taskId,
                    titleString,
                    "SUNDAY,MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY",
                    "09:00 AM"
                )
            }
        }
    }

    fun bulkAddPlaylistsToTasks(playlistIds: Set<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            for (id in playlistIds) {
                val playlist = repository.getPlaylistById(id) ?: continue
                val tracks = repository.getTracksForPlaylist(id)
                val titleString = playlist.name
                
                val taskId = repository.addTask(
                    title = titleString,
                    sourceType = "PLAYLIST",
                    sourceId = id,
                    targetType = "PLAY_COUNT",
                    targetValue = 3,
                    scheduledDays = "SUNDAY,MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY",
                    reminderTime = "09:00 AM",
                    startDate = System.currentTimeMillis(),
                    endDate = null
                )

                for (track in tracks) {
                    repository.dao.insertTaskProgress(
                        com.example.data.TaskTrackProgress(
                            taskId = taskId,
                            trackId = track.id,
                            completedPlayCount = 0,
                            completedDays = "",
                            isTrackCompleted = false
                        )
                    )
                }

                com.example.receiver.AlarmReceiver.scheduleAlarm(
                    getApplication(),
                    taskId,
                    titleString,
                    "SUNDAY,MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY",
                    "09:00 AM"
                )
            }
        }
    }

    suspend fun getActiveTasksForTrack(trackId: Long): List<Task> {
        return repository.getActiveTasksForTrack(trackId)
    }

    suspend fun getAllTasksForTrack(trackId: Long): List<Task> {
        return repository.getAllTasksForTrack(trackId)
    }

    suspend fun getProgressForTask(taskId: Long): List<com.example.data.TaskTrackProgress> {
        return repository.getProgressForTask(taskId)
    }

    // --- QUICK ADD TASK FROM LIBRARY ---
    fun quickAddTaskForTracks(tracks: List<AudioTrack>, title: String, playsTarget: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val taskId = repository.addTask(
                title = title,
                sourceType = "TRACKS",
                sourceId = null,
                targetType = "PLAY_COUNT",
                targetValue = playsTarget,
                scheduledDays = "SUNDAY,MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY",
                reminderTime = "09:00 AM",
                startDate = System.currentTimeMillis(),
                endDate = null
            )
            tracks.forEach { track ->
                repository.dao.insertTaskProgress(TaskTrackProgress(taskId, track.id))
            }
        }
    }

    // --- EXPORT HISTORY SYSTEM ---
    fun copyHistoryClipboard(context: Context, history: List<PlaybackHistory>) {
        val isAr = Loc.currentLanguage == "ar"
        val sb = java.lang.StringBuilder()

        // First row headers (Tab-Separated for Google Sheets & Excel)
        val headers = if (isAr) {
            listOf(
                "التاريخ والوقت",
                "اسم الملف",
                "المهام المرتبطة",
                "وقت الاستماع الفعلي",
                "مدة الملف",
                "سرعة التشغيل",
                "الوقت الموفر"
            )
        } else {
            listOf(
                "Date & Time",
                "Track Name",
                "Associated Tasks",
                "Actual Listen Time",
                "File Duration",
                "Playback Speed",
                "Time Saved"
            )
        }
        sb.append(headers.joinToString("\t")).append("\n")

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val tracksMap = tracks.value.associateBy { it.id }
        val tasksList = allTasks.value
        val progressList = allTaskProgress.value

        fun formatClock(ms: Long): String {
            val totalSeconds = (ms / 1000).coerceAtLeast(0)
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        }

        history.forEach { log ->
            val dateTime = sdf.format(Date(log.completedAt))
            val track = tracksMap[log.trackId]
            val rawName = log.trackName.ifBlank { track?.fileName ?: "Track #${log.trackId}" }
            val cleanName = rawName.substringBeforeLast(".").replace("\t", " ").replace("\r", "").replace("\n", " ").trim()

            val tasksForTrack = run {
                val logged = log.getActiveTasksList()
                if (logged.isNotEmpty()) {
                    logged
                } else if (log.activeTasks.isBlank()) {
                    // Fallback for legacy records: check tasks active at log.completedAt
                    val trackId = log.trackId
                    tasksList.filter { task ->
                        val wasActiveThen = log.completedAt >= task.startDate && (task.endDate == null || log.completedAt <= task.endDate)
                        wasActiveThen && when (task.sourceType) {
                            "FOLDER" -> track?.parentFolderId != null && track.parentFolderId == task.sourceId
                            else -> progressList.any { it.taskId == task.id && it.trackId == trackId }
                        }
                    }.map { it.getDisplayTitle() }
                } else {
                    emptyList()
                }
            }.map { it.replace("\t", " ").replace("\r", "").replace("\n", " ").trim() }.distinct()
            val tasksStr = if (tasksForTrack.isNotEmpty()) tasksForTrack.joinToString(", ") else "-"

            val speed = if (log.playbackSpeed > 0f) log.playbackSpeed else 1.0f
            val fileDur = if (log.durationMs > 0L) log.durationMs else (track?.duration ?: 0L)
            val actualDur = if (log.actualListenedMs > 0L) log.actualListenedMs else (if (speed > 0f) (fileDur / speed).toLong() else fileDur)
            val timeSaved = maxOf(0L, fileDur - actualDur)

            val row = listOf(
                dateTime,
                cleanName,
                tasksStr,
                formatClock(actualDur),
                formatClock(fileDur),
                String.format(Locale.US, "%.1fx", speed),
                formatClock(timeSaved)
            )
            sb.append(row.joinToString("\t")).append("\n")
        }

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Listening Log", sb.toString().trimEnd())
        clipboard.setPrimaryClip(clip)

        Toast.makeText(context, Loc.getText("export_success"), Toast.LENGTH_LONG).show()
    }

    fun clearAllPlaybackHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAllPlaybackHistory()
        }
    }

    fun prunePlaybackHistoryOlderThanOneYear() {
        viewModelScope.launch(Dispatchers.IO) {
            val oneYearAgoTime = System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000L
            repository.deletePlaybackHistoryOlderThan(oneYearAgoTime)
        }
    }

    fun prunePlaybackHistoryToRecent1000() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deletePlaybackHistoryExceptTop(1000)
        }
    }

    // --- REAL AUDIO IMPORT SYSTEM ---
    fun importFolder(folderName: String, uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val resolvedFolderName = if (folderName.isBlank()) {
                if (Loc.currentLanguage == "ar") "مجلد مستورد" else "Imported Folder"
            } else folderName
            val parentDir = File(context.filesDir, "imported")
            val folderDir = File(parentDir, resolvedFolderName.replace("/", "_").replace(" ", "_"))
            folderDir.mkdirs()

            // 1. Create Folder record in Room
            val folderPath = folderDir.absolutePath
            val existingFolder = repository.getFolderByPath(folderPath)
            val folderId = if (existingFolder != null) {
                existingFolder.id
            } else {
                repository.addFolder(folderPath, resolvedFolderName)
            }

            var totalProcessed = 0
            var newlyIndexedCount = 0
            var existingCount = 0
            val newlyIndexedFiles = mutableListOf<String>()

            // Separate subtitle URIs (.srt, .vtt, .lrc) and audio URIs
            val subUris = mutableListOf<Uri>()
            val audioUris = mutableListOf<Uri>()
            uris.forEach { uri ->
                val rawName = getFileNameFromUri(context, uri)?.lowercase() ?: ""
                if (rawName.endsWith(".srt") || rawName.endsWith(".vtt") || rawName.endsWith(".lrc")) {
                    subUris.add(uri)
                } else {
                    audioUris.add(uri)
                }
            }

            // Copy subtitle files into folderDir first
            subUris.forEach { uri ->
                try {
                    val rawFileName = getFileNameFromUri(context, uri) ?: "sub_${System.currentTimeMillis()}"
                    val extension = rawFileName.substringAfterLast(".", "srt")
                    val cleanBase = rawFileName.substringBeforeLast(".").replace("/", "_").replace(" ", "_")
                    val fileName = "$cleanBase.$extension"
                    val targetFile = File(folderDir, fileName)
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        targetFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 2. Copy each audio file and attach matching subtitle
            audioUris.forEach { uri ->
                try {
                    totalProcessed++
                    val rawFileName = getFileNameFromUri(context, uri) ?: "track_${System.currentTimeMillis()}"
                    // Safely format name keeping extension, but replacing bad chars
                    val extension = rawFileName.substringAfterLast(".", "mp3")
                    val cleanBase = rawFileName.substringBeforeLast(".").replace("/", "_").replace(" ", "_")
                    val fileName = "$cleanBase.$extension"
                    
                    val targetFile = File(folderDir, fileName)
                    
                    // Difference check: if track already exists, reuse it and preserve database metadata
                    val existingTrack = repository.getTrackByPath(targetFile.absolutePath)
                    if (existingTrack != null) {
                        if (!targetFile.exists() || targetFile.length() == 0L) {
                            // Copy stream only if physical file is missing or empty
                            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                targetFile.outputStream().use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                        }
                        val autoSub = com.example.player.SubtitleParser.findMatchingSubtitleFile(targetFile.absolutePath)
                        val updatedSubPath = if (existingTrack.subtitlePath.isNullOrBlank() && existingTrack.subtitleContent.isNullOrBlank() && autoSub != null) {
                            autoSub.absolutePath
                        } else {
                            existingTrack.subtitlePath
                        }
                        repository.updateTrack(existingTrack.copy(
                            isMissing = false,
                            subtitlePath = updatedSubPath,
                            parentFolderId = folderId,
                            isIndependent = false
                        ))
                        existingCount++
                        return@forEach
                    }
                    
                    // Copy stream
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        targetFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    if (targetFile.exists() && targetFile.length() > 0) {
                        val duration = getAudioDuration(targetFile.absolutePath)
                        val fileNameWithoutExt = rawFileName.substringBeforeLast(".")
                        val autoSub = com.example.player.SubtitleParser.findMatchingSubtitleFile(targetFile.absolutePath)
                        val track = AudioTrack(
                            filePath = targetFile.absolutePath,
                            fileName = fileNameWithoutExt,
                            duration = duration,
                            parentFolderId = folderId,
                            isIndependent = false,
                            subtitlePath = autoSub?.absolutePath
                        )
                        repository.insertTrack(track)
                        newlyIndexedCount++
                        newlyIndexedFiles.add(fileNameWithoutExt)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            withContext(Dispatchers.Main) {
                lastImportSummary.value = ImportSummary(
                    folderName = resolvedFolderName,
                    totalProcessed = totalProcessed,
                    newlyIndexedCount = newlyIndexedCount,
                    existingCount = existingCount,
                    newlyIndexedFiles = newlyIndexedFiles
                )
                Toast.makeText(context, String.format(Loc.getText("imported_files_success"), newlyIndexedCount), Toast.LENGTH_SHORT).show()
                checkFilesSanity()
            }
        }
    }

    fun importFolderFromTreeUri(folderName: String, treeUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val resolvedFolderName = if (folderName.isBlank()) {
                getDirNameFromTreeUri(context, treeUri)
            } else {
                folderName
            }
            val resolver = context.contentResolver
            
            // Prepare local target dir
            val parentDir = File(context.filesDir, "imported")
            
            var totalProcessed = 0
            var newlyIndexedCount = 0
            var existingCount = 0
            val newlyIndexedFiles = mutableListOf<String>()
            
            try {
                // Ensure proper persistable permission (for security standard)
                try {
                    resolver.takePersistableUriPermission(
                        treeUri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val rootDocId = if (DocumentsContract.isDocumentUri(context, treeUri)) {
                    DocumentsContract.getDocumentId(treeUri)
                } else {
                    DocumentsContract.getTreeDocumentId(treeUri)
                }
                
                // Define local ScanTask representation with parentFolderId and parentLocalDir
                class ScanTask(
                    val docId: String,
                    val folderName: String,
                    val parentFolderId: Long?,
                    val parentLocalDir: File
                )
                val scanQueue = kotlin.collections.ArrayDeque<ScanTask>()
                scanQueue.add(ScanTask(rootDocId, resolvedFolderName, null, parentDir))
                
                val projection = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                )
                
                while (scanQueue.isNotEmpty()) {
                    val currentTask = scanQueue.removeFirst()
                    val currentDocId = currentTask.docId
                    val currentFolderName = currentTask.folderName
                    val currentParentFolderId = currentTask.parentFolderId
                    val currentParentLocalDir = currentTask.parentLocalDir
                    
                    val safeFolderName = currentFolderName.replace("/", "_").replace(" ", "_").replace(":", "_")
                    val uniqueId = Math.abs(currentDocId.hashCode())
                    val folderDir = File(currentParentLocalDir, "${safeFolderName}_$uniqueId")
                    folderDir.mkdirs()

                    // Always ensure a Room Folder record exists for this directory and links to parent
                    val folderPath = folderDir.absolutePath
                    val existingFolder = repository.getFolderByPath(folderPath)
                    val folderId = if (existingFolder != null) {
                        if (currentParentFolderId != null && existingFolder.parentFolderId != currentParentFolderId) {
                            repository.dao.updateFolder(existingFolder.copy(parentFolderId = currentParentFolderId))
                        }
                        existingFolder.id
                    } else {
                        repository.addFolder(folderPath, currentFolderName, currentParentFolderId)
                    }

                    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, currentDocId)
                    val filesToImport = mutableListOf<Triple<String, String, String>>() // childDocId, displayName, mimeType
                    val subtitlesToImport = mutableListOf<Triple<String, String, String>>() // childDocId, displayName, mimeType
                    val subfoldersToScan = mutableListOf<Pair<String, String>>() // childDocId, subfolderName
                    
                    try {
                        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                            val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                            val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                            val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                            
                            if (idCol != -1 && nameCol != -1 && mimeCol != -1) {
                                while (cursor.moveToNext()) {
                                    val childDocId = cursor.getString(idCol)
                                    val displayName = cursor.getString(nameCol) ?: "track_${System.currentTimeMillis()}"
                                    val mimeType = cursor.getString(mimeCol)
                                    
                                    val isDir = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
                                    if (isDir) {
                                        subfoldersToScan.add(Pair(childDocId, displayName))
                                    } else {
                                        val lowerName = displayName.lowercase()
                                        val isSubtitle = lowerName.endsWith(".srt") || lowerName.endsWith(".vtt") || lowerName.endsWith(".lrc")
                                        val isMedia = mimeType?.startsWith("audio/") == true || 
                                                      mimeType?.startsWith("video/") == true ||
                                                      lowerName.endsWith(".mp3") || lowerName.endsWith(".wav") || 
                                                      lowerName.endsWith(".m4a") || lowerName.endsWith(".ogg") || 
                                                      lowerName.endsWith(".aac") || lowerName.endsWith(".wma") || lowerName.endsWith(".flac") ||
                                                      lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") || lowerName.endsWith(".webm") ||
                                                      lowerName.endsWith(".avi") || lowerName.endsWith(".mov") || lowerName.endsWith(".3gp") ||
                                                      lowerName.endsWith(".flv") || lowerName.endsWith(".m4v") || lowerName.endsWith(".ts")
                                        if (isSubtitle) {
                                            subtitlesToImport.add(Triple(childDocId, displayName, mimeType ?: ""))
                                        } else if (isMedia) {
                                            filesToImport.add(Triple(childDocId, displayName, mimeType ?: ""))
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    
                    // Copy subtitle files first
                    for ((childDocId, displayName, _) in subtitlesToImport) {
                        try {
                            val subUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                            val extension = displayName.substringAfterLast(".", "srt")
                            val cleanBase = displayName.substringBeforeLast(".").replace("/", "_").replace(" ", "_").replace(":", "_")
                            val fileName = "$cleanBase.$extension"
                            val targetSubFile = File(folderDir, fileName)
                            resolver.openInputStream(subUri)?.use { inputStream ->
                                targetSubFile.outputStream().use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    
                    for ((childDocId, displayName, mimeType) in filesToImport) {
                        try {
                            totalProcessed++
                            val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                            val extension = displayName.substringAfterLast(".", "mp3")
                            val cleanBase = displayName.substringBeforeLast(".").replace("/", "_").replace(" ", "_").replace(":", "_")
                            val fileName = "$cleanBase.$extension"
                            
                            val targetFile = File(folderDir, fileName)
                            
                            // Difference check: if track already exists, reuse it and preserve database metadata
                            val existingTrack = repository.getTrackByPath(targetFile.absolutePath)
                            if (existingTrack != null) {
                                if (!targetFile.exists() || targetFile.length() == 0L) {
                                    // Copy stream only if physical file is missing or empty
                                    resolver.openInputStream(childUri)?.use { inputStream ->
                                        targetFile.outputStream().use { outputStream ->
                                            inputStream.copyTo(outputStream)
                                        }
                                    }
                                }
                                val autoSub = com.example.player.SubtitleParser.findMatchingSubtitleFile(targetFile.absolutePath)
                                val updatedSubPath = if (existingTrack.subtitlePath.isNullOrBlank() && existingTrack.subtitleContent.isNullOrBlank() && autoSub != null) {
                                    autoSub.absolutePath
                                } else {
                                    existingTrack.subtitlePath
                                }
                                repository.updateTrack(existingTrack.copy(isMissing = false, subtitlePath = updatedSubPath, parentFolderId = folderId))
                                existingCount++
                                continue
                            }

                            resolver.openInputStream(childUri)?.use { inputStream ->
                                targetFile.outputStream().use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                            
                            if (targetFile.exists() && targetFile.length() > 0) {
                                val duration = getAudioDuration(targetFile.absolutePath)
                                val fileNameWithoutExt = displayName.substringBeforeLast(".")
                                val autoSub = com.example.player.SubtitleParser.findMatchingSubtitleFile(targetFile.absolutePath)
                                val track = AudioTrack(
                                    filePath = targetFile.absolutePath,
                                    fileName = fileNameWithoutExt,
                                    duration = duration,
                                    parentFolderId = folderId,
                                    isIndependent = false,
                                    subtitlePath = autoSub?.absolutePath
                                )
                                repository.insertTrack(track)
                                newlyIndexedCount++
                                newlyIndexedFiles.add(fileNameWithoutExt)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    
                    // Add subdirectories to scanning queue with folderId as parentFolderId and folderDir as parentLocalDir
                    for ((subDocId, subName) in subfoldersToScan) {
                        scanQueue.add(ScanTask(subDocId, subName, folderId, folderDir))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            withContext(Dispatchers.Main) {
                lastImportSummary.value = ImportSummary(
                    folderName = resolvedFolderName,
                    totalProcessed = totalProcessed,
                    newlyIndexedCount = newlyIndexedCount,
                    existingCount = existingCount,
                    newlyIndexedFiles = newlyIndexedFiles
                )
                Toast.makeText(context, String.format(Loc.getText("imported_files_success"), newlyIndexedCount), Toast.LENGTH_SHORT).show()
                checkFilesSanity()
            }
        }
    }

    fun importIndependentTracks(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val parentDir = File(context.filesDir, "imported")
            val independentDir = File(parentDir, "Independent")
            independentDir.mkdirs()

            val subUris = mutableListOf<Uri>()
            val audioUris = mutableListOf<Uri>()
            uris.forEach { uri ->
                val rawName = getFileNameFromUri(context, uri)?.lowercase() ?: ""
                if (rawName.endsWith(".srt") || rawName.endsWith(".vtt") || rawName.endsWith(".lrc")) {
                    subUris.add(uri)
                } else {
                    audioUris.add(uri)
                }
            }

            // Copy subtitle files first
            subUris.forEach { uri ->
                try {
                    val rawFileName = getFileNameFromUri(context, uri) ?: "sub_${System.currentTimeMillis()}"
                    val extension = rawFileName.substringAfterLast(".", "srt")
                    val cleanBase = rawFileName.substringBeforeLast(".").replace("/", "_").replace(" ", "_")
                    val fileName = "$cleanBase.$extension"
                    val targetFile = File(independentDir, fileName)
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        targetFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            var successCount = 0
            audioUris.forEach { uri ->
                try {
                    val rawFileName = getFileNameFromUri(context, uri) ?: "track_${System.currentTimeMillis()}"
                    val extension = rawFileName.substringAfterLast(".", "mp3")
                    val cleanBase = rawFileName.substringBeforeLast(".").replace("/", "_").replace(" ", "_")
                    val fileName = "$cleanBase.$extension"
                    
                    val targetFile = File(independentDir, fileName)
                    
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        targetFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    if (targetFile.exists() && targetFile.length() > 0) {
                        val duration = getAudioDuration(targetFile.absolutePath)
                        val autoSub = com.example.player.SubtitleParser.findMatchingSubtitleFile(targetFile.absolutePath)
                        val track = AudioTrack(
                            filePath = targetFile.absolutePath,
                            fileName = rawFileName.substringBeforeLast("."),
                            duration = duration,
                            parentFolderId = null,
                            isIndependent = true,
                            subtitlePath = autoSub?.absolutePath
                        )
                        repository.insertTrack(track)
                        successCount++
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            withContext(Dispatchers.Main) {
                Toast.makeText(context, String.format(Loc.getText("imported_files_success"), successCount), Toast.LENGTH_SHORT).show()
                checkFilesSanity()
            }
        }
    }

    fun getDirNameFromTreeUri(context: Context, treeUri: Uri): String {
        var folderName = Loc.getText("imported_folder_default")
        try {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
            )
            val cursor = context.contentResolver.query(docUri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val name = it.getString(nameIndex)
                        if (!name.isNullOrBlank()) {
                            folderName = name
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            treeUri.lastPathSegment?.let { segment ->
                val clean = segment.substringAfterLast(":").substringAfterLast("/")
                if (clean.isNotBlank()) {
                    folderName = clean
                }
            }
        }
        return folderName
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    private fun getAudioDuration(filePath: String): Long {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            val time = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            time?.toLongOrNull() ?: 10000L // 10s fallback
        } catch (e: Exception) {
            try {
                val mp = android.media.MediaPlayer()
                mp.setDataSource(filePath)
                mp.prepare()
                val duration = mp.duration.toLong()
                mp.release()
                duration
            } catch (ex: Exception) {
                10000L // 10s fallback
            }
        }
    }

    suspend fun getPlaylistsForTrack(trackId: Long): List<Playlist> {
        return repository.getPlaylistsForTrack(trackId)
    }

    // --- NOTEBOOK OPERATIONS ---
    fun saveNote(
        id: Long = 0L,
        text: String,
        comment: String,
        trackId: Long?,
        startTimestampMs: Long,
        endTimestampMs: Long,
        originStartMs: Long? = null,
        tags: List<String>,
        targetWord: String? = null,
        meaning: String? = null,
        contextSentence: String? = null,
        onSuccess: (() -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var resolvedTrackName: String? = null
                var resolvedFolderId: Long? = null
                var resolvedFolderName: String? = null

                if (trackId != null) {
                    val track = tracks.value.find { it.id == trackId }
                        ?: repository.dao.getTrackById(trackId)
                    if (track != null) {
                        resolvedTrackName = track.fileName
                        resolvedFolderId = track.parentFolderId
                        if (resolvedFolderId != null) {
                            val folder = folders.value.find { it.id == resolvedFolderId }
                                ?: repository.dao.getFolderById(resolvedFolderId)
                            resolvedFolderName = folder?.folderName
                        }
                    }
                }

                val favTag = Loc.getText("favorite_tag_name")
                val isFavoriteTag = { t: String ->
                    t.equals("favorite", ignoreCase = true) || t == "المفضلة" || t.equals(favTag, ignoreCase = true)
                }

                // If editing existing note, preserve its favorite status
                val existingNote = if (id != 0L) notes.value.find { it.id == id } else null
                val wasFavorite = existingNote?.getTagsList()?.any { isFavoriteTag(it) } == true

                val cleanUserTags = tags.map { it.trim() }.filter { it.isNotEmpty() && !isFavoriteTag(it) }
                val finalTagsList = if (wasFavorite) {
                    listOf(favTag) + cleanUserTags
                } else {
                    cleanUserTags
                }
                val tagsString = finalTagsList.distinctBy { it.lowercase() }.joinToString(",")

                val resolvedWord = targetWord?.trim()?.ifEmpty { null }
                    ?: (if (id != 0L) existingNote?.targetWord else null)
                val resolvedMeaning = meaning?.trim()?.ifEmpty { null }
                    ?: (if (id != 0L) existingNote?.meaning else null)
                val resolvedContext = contextSentence?.trim()?.ifEmpty { null }
                    ?: (if (id != 0L) existingNote?.contextSentence else null)

                val note = Note(
                    id = id,
                    text = text.trim(),
                    comment = comment.trim(),
                    trackId = trackId,
                    trackName = resolvedTrackName,
                    folderId = resolvedFolderId,
                    folderName = resolvedFolderName,
                    startTimestampMs = startTimestampMs.coerceAtLeast(0L),
                    endTimestampMs = endTimestampMs.coerceAtLeast(startTimestampMs),
                    originStartMs = originStartMs ?: if (id != 0L) existingNote?.originStartMs else null,
                    tags = tagsString,
                    createdAt = if (id == 0L) System.currentTimeMillis() else (notes.value.find { it.id == id }?.createdAt ?: System.currentTimeMillis()),
                    updatedAt = System.currentTimeMillis(),
                    targetWord = resolvedWord,
                    meaning = resolvedMeaning,
                    contextSentence = resolvedContext
                )

                if (id == 0L) {
                    repository.insertNote(note)
                } else {
                    repository.updateNote(note)
                }

                withContext(Dispatchers.Main) {
                    val context = getApplication<Application>()
                    Toast.makeText(context, Loc.getText("note_saved_success"), Toast.LENGTH_SHORT).show()
                    onSuccess?.invoke()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleNoteFavorite(note: Note) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentTags = note.getTagsList().toMutableList()
                val favTag = Loc.getText("favorite_tag_name")
                val isFav = currentTags.any { 
                    it.equals("favorite", ignoreCase = true) || it == "المفضلة" || it == favTag 
                }

                if (isFav) {
                    currentTags.removeAll { 
                        it.equals("favorite", ignoreCase = true) || it == "المفضلة" || it == favTag 
                    }
                } else {
                    currentTags.add(0, favTag)
                }

                val newTagsStr = currentTags.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.joinToString(",")
                val updatedNote = note.copy(
                    tags = newTagsStr,
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateNote(updatedNote)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (NoteAudioPlayer.playingNoteId.value == note.id) {
                    NoteAudioPlayer.stop()
                }
                repository.deleteNote(note)
                withContext(Dispatchers.Main) {
                    val context = getApplication<Application>()
                    Toast.makeText(context, Loc.getText("note_deleted_success"), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addTag(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val trimmed = name.trim()
            if (trimmed.isNotEmpty()) {
                repository.insertTag(trimmed)
            }
        }
    }

    fun deleteTag(tagId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteTag(tagId)
        }
    }

    fun playNoteSnippet(note: Note) {
        val context = getApplication<Application>()
        var targetTrack = if (note.trackId != null) tracks.value.find { it.id == note.trackId } else null
        if (targetTrack == null && !note.trackName.isNullOrBlank()) {
            val cleanName = note.trackName.trim().lowercase(Locale.ROOT)
            targetTrack = tracks.value.find { 
                val fn = it.fileName.trim().lowercase(Locale.ROOT)
                fn == cleanName || fn.removeSuffix(".mp3") == cleanName.removeSuffix(".mp3")
            }
            if (targetTrack != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    val updated = note.copy(
                        trackId = targetTrack.id,
                        trackName = targetTrack.fileName,
                        folderId = targetTrack.parentFolderId,
                        folderName = targetTrack.parentFolderId?.let { repository.getFolderById(it)?.folderName } ?: note.folderName
                    )
                    repository.updateNote(updated)
                }
            }
        }
        if (targetTrack != null) {
            NoteAudioPlayer.playSnippet(
                context = context,
                trackFilePath = targetTrack.filePath,
                noteId = note.id,
                startMs = note.startTimestampMs,
                endMs = if (note.endTimestampMs > note.startTimestampMs) note.endTimestampMs else null
            )
        } else if (note.trackId != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val dbTrack = repository.dao.getTrackById(note.trackId)
                if (dbTrack != null && File(dbTrack.filePath).exists()) {
                    withContext(Dispatchers.Main) {
                        NoteAudioPlayer.playSnippet(
                            context = context,
                            trackFilePath = dbTrack.filePath,
                            noteId = note.id,
                            startMs = note.startTimestampMs,
                            endMs = if (note.endTimestampMs > note.startTimestampMs) note.endTimestampMs else null
                        )
                    }
                }
            }
        }
    }

    fun stopNoteSnippet() {
        NoteAudioPlayer.stop()
    }

    // AI Scene Detection State
    val isDetectingScenes = MutableStateFlow(false)
    val detectingTrackName = MutableStateFlow<String?>(null)
    val sceneDetectionStatus = MutableStateFlow<String?>(null)
    internal var sceneDetectionJob: kotlinx.coroutines.Job? = null
}
