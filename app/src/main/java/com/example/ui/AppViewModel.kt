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
import com.example.player.AudioPlayerManager
import com.example.player.NoteAudioPlayer
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

    // Gemini Chatbot State
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isGeneratingAiResponse = MutableStateFlow(false)
    val isGeneratingAiResponse: StateFlow<Boolean> = _isGeneratingAiResponse.asStateFlow()

    val isChatDialogOpen = MutableStateFlow(false)
    val activeChatContext = MutableStateFlow<AudioContextSummary?>(null)

    // Subtitle Generation State
    private val _isGeneratingSubtitles = MutableStateFlow(false)
    val isGeneratingSubtitles: StateFlow<Boolean> = _isGeneratingSubtitles.asStateFlow()

    private val _subtitleGenerationStatus = MutableStateFlow("")
    val subtitleGenerationStatus: StateFlow<String> = _subtitleGenerationStatus.asStateFlow()

    fun generateSubtitlesForCurrentTrack(
        contextSummary: AudioContextSummary? = null,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        if (_isGeneratingSubtitles.value) return
        val track = AudioPlayerManager.currentTrack.value
        if (track == null) {
            val noTrackMsg = Loc.getText("no_track_selected") ?: "No audio track selected"
            onError?.invoke(noTrackMsg)
            return
        }

        val audioFile = java.io.File(track.filePath)
        if (!audioFile.exists() || !audioFile.canRead()) {
            val missingMsg = Loc.getText("ai_subtitles_audio_missing")
            onError?.invoke(missingMsg)
            _chatMessages.value = _chatMessages.value + ChatMessage(
                role = "model",
                text = missingMsg,
                isError = true
            )
            return
        }

        _isGeneratingSubtitles.value = true
        _subtitleGenerationStatus.value = Loc.getText("ai_creating_subtitles")

        val rawReference = AudioPlayerManager.getCurrentSubtitlesRawText()
        val referenceSubtitles = if (rawReference.isNotBlank()) rawReference else null

        viewModelScope.launch {
            val result = GeminiService.generateSubtitles(
                audioFile = audioFile,
                existingSubtitleText = referenceSubtitles,
                totalDurationMs = AudioPlayerManager.duration.value,
                customApiKey = customGeminiApiKey,
                language = Loc.currentLanguage,
                onProgressUpdate = { progressText ->
                    _subtitleGenerationStatus.value = progressText
                }
            )

            result.fold(
                onSuccess = { cleanSrt ->
                    AudioPlayerManager.setSubtitleContentForCurrentTrack(cleanSrt)
                    val successMsg = Loc.getText("ai_subtitles_success")
                    _chatMessages.value = _chatMessages.value + ChatMessage(
                        role = "model",
                        text = "✨ $successMsg"
                    )
                    _isGeneratingSubtitles.value = false
                    _subtitleGenerationStatus.value = ""
                    onSuccess?.invoke()
                },
                onFailure = { error ->
                    val errorMsg = when {
                        error.message == "MISSING_API_KEY" -> Loc.getText("missing_api_key_prompt")
                        else -> String.format(Loc.getText("ai_subtitles_failed"), error.localizedMessage ?: "Unknown error")
                    }
                    _chatMessages.value = _chatMessages.value + ChatMessage(
                        role = "model",
                        text = errorMsg,
                        isError = true
                    )
                    _isGeneratingSubtitles.value = false
                    _subtitleGenerationStatus.value = ""
                    onError?.invoke(errorMsg)
                }
            )
        }
    }

    fun openChatWithContext(contextSummary: AudioContextSummary? = null) {
        activeChatContext.value = contextSummary
        isChatDialogOpen.value = true
    }

    fun closeChatDialog() {
        isChatDialogOpen.value = false
    }

    fun clearChatHistory() {
        _chatMessages.value = emptyList()
    }

    // AI Quiz & Practice Bank State
    val isQuizSheetOpen = MutableStateFlow(false)
    val isGeneratingQuiz = MutableStateFlow(false)
    val quizGenerationError = MutableStateFlow<String?>(null)
    val quizGenerationSuccessMessage = MutableStateFlow<String?>(null)
    val activeQuizTargetTrack = MutableStateFlow<AudioTrack?>(null)
    val currentTrackQuizQuestions = MutableStateFlow<List<QuizQuestion>>(emptyList())
    val allVocabularyQuestions: StateFlow<List<QuizQuestion>> = repository.getAllVocabularyQuestionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun openQuizForTrack(track: AudioTrack) {
        activeQuizTargetTrack.value = track
        quizGenerationError.value = null
        quizGenerationSuccessMessage.value = null
        viewModelScope.launch {
            loadQuizQuestionsForTrack(track.id)
            isQuizSheetOpen.value = true
        }
    }

    fun openQuizForCurrentTrack() {
        val current = AudioPlayerManager.currentTrack.value ?: return
        openQuizForTrack(current)
    }

    fun closeQuizSheet() {
        isQuizSheetOpen.value = false
        quizGenerationError.value = null
        quizGenerationSuccessMessage.value = null
    }

    fun clearQuizGenerationFeedback() {
        quizGenerationError.value = null
        quizGenerationSuccessMessage.value = null
    }

    fun loadQuizQuestionsForTrack(trackId: Long) {
        viewModelScope.launch {
            val questions = repository.getQuestionsForTrackDirect(trackId)
            currentTrackQuizQuestions.value = questions
        }
    }

    fun generateQuizForTrack(track: AudioTrack) {
        if (isGeneratingQuiz.value) return

        quizGenerationError.value = null
        quizGenerationSuccessMessage.value = null

        val cuesToUse = when {
            AudioPlayerManager.currentTrack.value?.id == track.id && AudioPlayerManager.subtitlesCues.value.isNotEmpty() -> {
                AudioPlayerManager.subtitlesCues.value
            }
            !track.subtitleContent.isNullOrBlank() -> {
                com.example.player.SubtitleParser.parseContent(track.subtitleContent ?: "", track.subtitleOffsetMs)
            }
            !track.subtitlePath.isNullOrBlank() && java.io.File(track.subtitlePath).exists() -> {
                com.example.player.SubtitleParser.parseFile(java.io.File(track.subtitlePath), track.subtitleOffsetMs)
            }
            else -> {
                val matching = com.example.player.SubtitleParser.findMatchingSubtitleFile(track.filePath)
                if (matching != null && matching.exists()) {
                    com.example.player.SubtitleParser.parseFile(matching, track.subtitleOffsetMs)
                } else if (AudioPlayerManager.subtitlesCues.value.isNotEmpty()) {
                    AudioPlayerManager.subtitlesCues.value
                } else {
                    emptyList()
                }
            }
        }

        if (cuesToUse.isEmpty()) {
            quizGenerationError.value = Loc.getText("quiz_no_transcript_error")
            return
        }

        isGeneratingQuiz.value = true

        viewModelScope.launch {
            try {
                // Fetch any existing questions already in the bank for this track to avoid duplicates
                val existingQuestions = repository.getQuestionsForTrackDirect(track.id)
                val existingQuestionTexts = existingQuestions.map { it.question.trim() }.filter { it.isNotBlank() }

                val result = GeminiService.generateQuizQuestions(
                    mediaTitle = track.getDisplayTitle(),
                    transcriptCues = cuesToUse,
                    existingQuestions = existingQuestionTexts,
                    customApiKey = customGeminiApiKey,
                    language = Loc.currentLanguage
                )

                result.onSuccess { generatedItems ->
                    // Filter out any duplicate questions on the client side as a safeguard
                    val existingNormalized = existingQuestionTexts.map { it.lowercase() }.toSet()
                    val uniqueGenerated = generatedItems.filter { item ->
                        item.question.trim().lowercase() !in existingNormalized
                    }.ifEmpty { generatedItems }

                    val entities = uniqueGenerated.map { item ->
                        val optionsJson = org.json.JSONArray(item.options).toString()
                        QuizQuestion(
                            trackId = track.id,
                            questionType = item.questionType,
                            category = item.category,
                            question = item.question,
                            optionsJson = optionsJson,
                            correctIndex = item.correctIndex,
                            explanation = item.explanation,
                            timestampMs = item.timestampMs,
                            targetWord = item.targetWord,
                            meaning = item.meaning,
                            contextSentence = item.contextSentence
                        )
                    }
                    repository.insertQuizQuestions(entities)
                    loadQuizQuestionsForTrack(track.id)
                    isGeneratingQuiz.value = false
                    quizGenerationSuccessMessage.value = String.format(Loc.getText("quiz_generated_success"), entities.size)
                }.onFailure { err ->
                    isGeneratingQuiz.value = false
                    val msg = err.message ?: ""
                    quizGenerationError.value = when {
                        msg == "MISSING_API_KEY" -> Loc.getText("missing_api_key_prompt")
                        msg.isNotBlank() -> msg
                        else -> Loc.getText("quiz_error_generic")
                    }
                }
            } catch (e: Exception) {
                isGeneratingQuiz.value = false
                quizGenerationError.value = e.localizedMessage ?: Loc.getText("quiz_error_generic")
            }
        }
    }

    fun recordQuizAnswer(question: QuizQuestion, isCorrect: Boolean) {
        viewModelScope.launch {
            repository.recordQuestionAnswer(question.id, isCorrect)
            activeQuizTargetTrack.value?.let { loadQuizQuestionsForTrack(it.id) }
        }
    }

    fun clearQuizBankForTrack(trackId: Long) {
        viewModelScope.launch {
            repository.deleteQuizQuestionsForTrack(trackId)
            loadQuizQuestionsForTrack(trackId)
        }
    }

    fun deleteQuizQuestion(questionId: Long) {
        viewModelScope.launch {
            repository.deleteQuizQuestionById(questionId)
            activeQuizTargetTrack.value?.let { loadQuizQuestionsForTrack(it.id) }
        }
    }

    // --- NOTEBOOK VOCABULARY QUIZ GENERATION ---
    val isNotebookQuizSheetOpen = MutableStateFlow(false)
    val isGeneratingNotebookQuiz = MutableStateFlow(false)
    val notebookQuizError = MutableStateFlow<String?>(null)
    val notebookQuizGeneratedQuestions = MutableStateFlow<List<com.example.ai.GeneratedNoteQuizItem>>(emptyList())
    val notebookQuizSuccessMessage = MutableStateFlow<String?>(null)
    val notebookVocabularyQuestions: StateFlow<List<QuizQuestion>> = repository.getNotebookVocabularyQuestionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun openNotebookQuizSheet() {
        // Do not wipe existing generated questions or progress so user never loses work on accidental swipe
        notebookQuizError.value = null
        isNotebookQuizSheetOpen.value = true
    }

    fun closeNotebookQuizSheet() {
        // Only hide the sheet; keep state so reopening restores generated results or in-progress status
        isNotebookQuizSheetOpen.value = false
    }

    fun resetNotebookQuiz() {
        notebookQuizGeneratedQuestions.value = emptyList()
        notebookQuizError.value = null
        notebookQuizSuccessMessage.value = null
    }

    fun clearNotebookQuizFeedback() {
        notebookQuizError.value = null
    }

    fun deleteNotebookQuizQuestion(item: com.example.ai.GeneratedNoteQuizItem) {
        viewModelScope.launch {
            val qId = item.savedQuestionId
            if (qId != null && qId > 0) {
                repository.deleteQuizQuestionById(qId)
                activeQuizTargetTrack.value?.let { loadQuizQuestionsForTrack(it.id) }
            }
            notebookQuizGeneratedQuestions.value = notebookQuizGeneratedQuestions.value.filter { it != item }
            if (notebookQuizGeneratedQuestions.value.isEmpty()) {
                notebookQuizSuccessMessage.value = null
            }
        }
    }

    fun generateQuizFromNotes(
        notes: List<Note>,
        maxQuestions: Int = 4,
        onSuccess: (() -> Unit)? = null
    ) {
        if (isGeneratingNotebookQuiz.value) return
        notebookQuizError.value = null
        notebookQuizSuccessMessage.value = null
        isGeneratingNotebookQuiz.value = true

        viewModelScope.launch {
            try {
                val inputList = notes.map { n ->
                    com.example.ai.NoteInputForQuiz(
                        id = n.id,
                        text = n.text,
                        comment = n.comment,
                        tags = n.getTagsList(),
                        trackId = n.trackId,
                        trackName = n.trackName,
                        startTimestampMs = n.startTimestampMs,
                        targetWord = n.targetWord,
                        meaning = n.meaning,
                        contextSentence = n.contextSentence
                    )
                }
                val notesMap = notes.associateBy { it.id }

                val result = com.example.ai.GeminiService.generateQuizFromNotebookNotes(
                    notes = inputList,
                    maxQuestions = maxQuestions,
                    customApiKey = customGeminiApiKey,
                    language = Loc.currentLanguage
                )

                result.onSuccess { generated ->
                    isGeneratingNotebookQuiz.value = false
                    if (generated.isNotEmpty()) {
                        // AUTO-SAVE IMMEDIATELY TO GLOBAL QUIZ BANK WITHOUT WAITING OR REQUIRING MANUAL REVIEW
                        val entities = generated.map { item ->
                            val sourceNote = notesMap[item.sourceNoteId]
                            val isolatedWord = item.targetWord.ifBlank { sourceNote?.getIsolatedTargetWord() ?: sourceNote?.text ?: "" }
                            val isolatedMeaning = item.meaning.ifBlank { sourceNote?.getIsolatedMeaning() ?: item.options.getOrNull(item.correctIndex) ?: item.explanation }
                            val isolatedContext = item.contextSentence.ifBlank { sourceNote?.getIsolatedContextSentence() ?: sourceNote?.text ?: "" }
                            QuizQuestion(
                                trackId = item.trackId ?: sourceNote?.trackId,
                                noteId = item.sourceNoteId,
                                questionType = item.questionType,
                                category = "VOCABULARY",
                                question = item.question,
                                optionsJson = org.json.JSONArray(item.options).toString(),
                                correctIndex = item.correctIndex,
                                explanation = item.explanation,
                                timestampMs = item.timestampMs ?: sourceNote?.startTimestampMs,
                                targetWord = isolatedWord.takeIf { it.isNotBlank() },
                                meaning = isolatedMeaning.takeIf { it.isNotBlank() },
                                contextSentence = isolatedContext.takeIf { it.isNotBlank() }
                            )
                        }

                        viewModelScope.launch(Dispatchers.IO) {
                            val insertedIds = repository.insertQuizQuestions(entities)
                            activeQuizTargetTrack.value?.let { loadQuizQuestionsForTrack(it.id) }

                            val itemsWithIds = generated.mapIndexed { idx, item ->
                                val id = insertedIds.getOrNull(idx)
                                item.copy(savedQuestionId = id)
                            }

                            withContext(Dispatchers.Main) {
                                notebookQuizGeneratedQuestions.value = itemsWithIds
                                notebookQuizSuccessMessage.value = String.format(
                                    Loc.getText("notebook_quiz_saved_success"),
                                    entities.size
                                )
                                onSuccess?.invoke()
                            }
                        }
                    } else {
                        notebookQuizError.value = Loc.getText("notebook_quiz_no_vocab_found")
                    }
                }.onFailure { err ->
                    isGeneratingNotebookQuiz.value = false
                    val msg = err.message ?: ""
                    notebookQuizError.value = when {
                        msg == "MISSING_API_KEY" -> Loc.getText("missing_api_key_prompt")
                        msg.isNotBlank() -> msg
                        else -> Loc.getText("quiz_error_generic")
                    }
                }
            } catch (e: Exception) {
                isGeneratingNotebookQuiz.value = false
                notebookQuizError.value = e.localizedMessage ?: Loc.getText("quiz_error_generic")
            }
        }
    }

    fun saveNotebookQuizQuestions(
        items: List<com.example.ai.GeneratedNoteQuizItem>,
        notesMap: Map<Long, Note> = emptyMap(),
        onSuccess: (Int) -> Unit = {}
    ) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            val entities = items.map { item ->
                val sourceNote = notesMap[item.sourceNoteId]
                val isolatedWord = item.targetWord.ifBlank { sourceNote?.getIsolatedTargetWord() ?: sourceNote?.text ?: "" }
                val isolatedMeaning = item.meaning.ifBlank { sourceNote?.getIsolatedMeaning() ?: item.options.getOrNull(item.correctIndex) ?: item.explanation }
                val isolatedContext = item.contextSentence.ifBlank { sourceNote?.getIsolatedContextSentence() ?: sourceNote?.text ?: "" }
                QuizQuestion(
                    trackId = item.trackId ?: sourceNote?.trackId,
                    noteId = item.sourceNoteId,
                    questionType = item.questionType,
                    category = "VOCABULARY",
                    question = item.question,
                    optionsJson = org.json.JSONArray(item.options).toString(),
                    correctIndex = item.correctIndex,
                    explanation = item.explanation,
                    timestampMs = item.timestampMs ?: sourceNote?.startTimestampMs,
                    targetWord = isolatedWord.takeIf { it.isNotBlank() },
                    meaning = isolatedMeaning.takeIf { it.isNotBlank() },
                    contextSentence = isolatedContext.takeIf { it.isNotBlank() }
                )
            }
            repository.insertQuizQuestions(entities)
            activeQuizTargetTrack.value?.let { loadQuizQuestionsForTrack(it.id) }
            notebookQuizSuccessMessage.value = String.format(Loc.getText("notebook_quiz_saved_success"), entities.size)
            onSuccess(entities.size)
        }
    }

    fun generateQuizForSingleNote(
        note: Note,
        onSuccess: (QuizQuestion) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val input = listOf(
                    com.example.ai.NoteInputForQuiz(
                        id = note.id,
                        text = note.text,
                        comment = note.comment,
                        tags = note.getTagsList(),
                        trackId = note.trackId,
                        trackName = note.trackName,
                        startTimestampMs = note.startTimestampMs,
                        targetWord = note.targetWord,
                        meaning = note.meaning,
                        contextSentence = note.contextSentence
                    )
                )
                val result = com.example.ai.GeminiService.generateQuizFromNotebookNotes(
                    notes = input,
                    maxQuestions = 1,
                    customApiKey = customGeminiApiKey,
                    language = Loc.currentLanguage
                )
                result.onSuccess { generatedList ->
                    val first = generatedList.firstOrNull()
                    if (first != null) {
                        val isolatedWord = first.targetWord.ifBlank { note.getIsolatedTargetWord() }
                        val isolatedMeaning = first.meaning.ifBlank { note.getIsolatedMeaning() }
                        val isolatedContext = first.contextSentence.ifBlank { note.getIsolatedContextSentence() }
                        val entity = QuizQuestion(
                            trackId = first.trackId ?: note.trackId,
                            noteId = note.id,
                            questionType = first.questionType,
                            category = "VOCABULARY",
                            question = first.question,
                            optionsJson = org.json.JSONArray(first.options).toString(),
                            correctIndex = first.correctIndex,
                            explanation = first.explanation,
                            timestampMs = first.timestampMs ?: note.startTimestampMs,
                            targetWord = isolatedWord.takeIf { it.isNotBlank() },
                            meaning = isolatedMeaning.takeIf { it.isNotBlank() },
                            contextSentence = isolatedContext.takeIf { it.isNotBlank() }
                        )
                        val insertedId = repository.insertQuizQuestion(entity)
                        activeQuizTargetTrack.value?.let { loadQuizQuestionsForTrack(it.id) }
                        onSuccess(entity.copy(id = insertedId))
                    } else {
                        onError(Loc.getText("notebook_quiz_no_vocab_found"))
                    }
                }.onFailure { err ->
                    val msg = err.message ?: ""
                    val errText = when {
                        msg == "MISSING_API_KEY" -> Loc.getText("missing_api_key_prompt")
                        msg.isNotBlank() -> msg
                        else -> Loc.getText("quiz_error_generic")
                    }
                    onError(errText)
                }
            } catch (e: Exception) {
                onError(e.localizedMessage ?: Loc.getText("quiz_error_generic"))
            }
        }
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
    ) {
        val trimmedPrompt = prompt.trim()
        if (trimmedPrompt.isBlank() || _isGeneratingAiResponse.value) return

        val userMessage = ChatMessage(
            role = "user",
            text = trimmedPrompt,
            attachedContext = contextSummary
        )

        val updatedHistory = _chatMessages.value + userMessage
        _chatMessages.value = updatedHistory
        _isGeneratingAiResponse.value = true

        viewModelScope.launch {
            val result = GeminiService.sendMessage(
                history = updatedHistory.dropLast(1),
                newUserMessage = trimmedPrompt,
                contextSummary = contextSummary,
                customApiKey = customGeminiApiKey,
                language = Loc.currentLanguage
            )

            result.fold(
                onSuccess = { modelReply ->
                    _chatMessages.value = _chatMessages.value + ChatMessage(
                        role = "model",
                        text = modelReply
                    )
                    _isGeneratingAiResponse.value = false
                },
                onFailure = { error ->
                    val errorText = when {
                        error.message == "MISSING_API_KEY" -> Loc.getText("missing_api_key_prompt")
                        else -> "Error: ${error.localizedMessage ?: "Failed to generate response"}"
                    }
                    _chatMessages.value = _chatMessages.value + ChatMessage(
                        role = "model",
                        text = errorText,
                        isError = true
                    )
                    _isGeneratingAiResponse.value = false
                }
            )
        }
    }

    suspend fun generateNoteExplanation(
        quoteText: String,
        promptOrInstruction: String,
        trackTitle: String? = null
    ): Result<String> {
        val trimmedQuote = quoteText.trim()
        val trimmedPrompt = promptOrInstruction.trim()
        val defaultPrompt = Loc.getText("ai_default_note_prompt")

        val isDefaultOrBlankPrompt = trimmedPrompt.isBlank() ||
                trimmedPrompt.equals(defaultPrompt, ignoreCase = true) ||
                trimmedPrompt.equals("define vocabulary with examples", ignoreCase = true) ||
                trimmedPrompt.equals("عرّف المفردات مع أمثلة", ignoreCase = true)

        val userSpecifiedTarget = if (!isDefaultOrBlankPrompt) trimmedPrompt else null

        val promptBuilder = StringBuilder()
        promptBuilder.append("Define the vocabulary/expression using the strict fixed template without translation.\n\n")

        if (userSpecifiedTarget != null) {
            promptBuilder.append("Target word/phrase or instruction: \"$userSpecifiedTarget\"\n")
        }

        if (trimmedQuote.isNotBlank()) {
            promptBuilder.append("Audio quote / sentence context: \"$trimmedQuote\"\n")
        }

        if (!trackTitle.isNullOrBlank()) {
            promptBuilder.append("Audio track title: \"$trackTitle\"\n")
        }

        promptBuilder.append("\nStrict Rules to Follow:")
        promptBuilder.append("\n1. Strictly follow the fixed template format line by line.")
        promptBuilder.append("\n2. NO TRANSLATION. Do not provide any translation into Arabic or any other language. Everything must be strictly in English.")
        promptBuilder.append("\n3. Keep the definition concise and clear (1-2 sentences).")
        promptBuilder.append("\n4. Start immediately with the word/phrase line without any introductory greetings or headers.")
        promptBuilder.append("\n\nRequired Template Structure:")
        promptBuilder.append("\n[Word/Phrase]: [Meaning: Clear, direct, 1-2-sentence definition in simple language]")
        promptBuilder.append("\n• Context in Audio: [How it was used in this specific line/sentence]")
        promptBuilder.append("\n• Examples:")
        promptBuilder.append("\n  1. \"[Natural everyday example sentence showing typical usage]\"")
        promptBuilder.append("\n  2. \"[Second contrast or collocation example sentence]\"")
        promptBuilder.append("\n• Key Collocations / Synonyms: [2–3 relevant words/phrases]")

        val context = if (!trackTitle.isNullOrBlank()) {
            AudioContextSummary(
                trackTitle = trackTitle,
                activeSubtitleLine = trimmedQuote.takeIf { it.isNotBlank() }
            )
        } else if (trimmedQuote.isNotBlank()) {
            AudioContextSummary(
                trackTitle = null,
                activeSubtitleLine = trimmedQuote
            )
        } else null

        val rawResult = GeminiService.sendMessage(
            history = emptyList(),
            newUserMessage = promptBuilder.toString(),
            contextSummary = context,
            customApiKey = customGeminiApiKey,
            language = "en",
            customSystemInstruction = GeminiService.VOCAB_NOTE_SYSTEM_PROMPT
        )

        return rawResult.map { cleanMarkdownToPlainText(it) }
    }

    private fun cleanMarkdownToPlainText(input: String): String {
        var text = input
        // Strip fenced code blocks
        text = text.replace(Regex("```[a-zA-Z]*\n?"), "").replace("```", "")
        // Strip bold & italic markdown syntax: **word**, *word*, __word__, _word_
        text = text.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        text = text.replace(Regex("__([^_]+)__"), "$1")
        text = text.replace(Regex("\\*([^*]+)\\*"), "$1")
        text = text.replace(Regex("(?<!\\w)_([^_]+)_(?!\\w)"), "$1")
        // Strip inline backticks
        text = text.replace(Regex("`([^`]+)`"), "$1")
        // Strip header hashes at line starts like # Title, ## Subtitle, ### Header
        text = text.replace(Regex("(?m)^#{1,6}\\s*"), "")
        // Convert markdown bullets (*, -, +) at line starts to standard clean bullet symbol •
        text = text.replace(Regex("(?m)^[\\*\\-\\+]\\s+"), "• ")
        // Strip blockquotes >
        text = text.replace(Regex("(?m)^>\\s*"), "")
        // Remove any remaining stray asterisks or hashes
        text = text.replace("**", "").replace("*", "")
        return text.trim()
    }

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
        
        for (track in currentTracks) {
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
                repository.updateTrack(track.copy(
                    isMissing = !exists,
                    playCount = desiredPlayCount,
                    subtitlePath = updatedSubPath
                ))
            }
        }
    }

    fun rebindMissingTrack(track: AudioTrack, newPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(newPath)
            if (file.exists()) {
                repository.updateTrack(track.copy(filePath = newPath, fileName = file.name, isMissing = false))
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
                        repository.updateTrack(existingTrack.copy(isMissing = false, subtitlePath = updatedSubPath))
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

    fun exportBackupToJson(outputStream: java.io.OutputStream): Boolean {
        return try {
            val root = org.json.JSONObject()
            root.put("version", 1)
            root.put("exportedAt", System.currentTimeMillis())
            root.put("appName", "Hearmark")

            // Playback history
            val historyArray = org.json.JSONArray()
            playbackHistory.value.forEach { item ->
                val obj = org.json.JSONObject()
                obj.put("id", item.id)
                obj.put("trackId", item.trackId)
                obj.put("trackName", item.trackName)
                obj.put("completedAt", item.completedAt)
                obj.put("durationMs", item.durationMs)
                obj.put("playbackSpeed", item.playbackSpeed.toDouble())
                obj.put("actualListenedMs", item.actualListenedMs)
                obj.put("activeTasks", item.activeTasks)
                historyArray.put(obj)
            }
            root.put("playbackHistory", historyArray)

            // Tasks
            val tasksArray = org.json.JSONArray()
            allTasks.value.forEach { task ->
                val obj = org.json.JSONObject()
                obj.put("id", task.id)
                obj.put("title", task.title)
                obj.put("sourceType", task.sourceType)
                obj.put("sourceId", task.sourceId)
                obj.put("targetType", task.targetType)
                obj.put("targetValue", task.targetValue)
                obj.put("scheduledDays", task.scheduledDays)
                obj.put("reminderTime", task.reminderTime)
                obj.put("startDate", task.startDate)
                if (task.endDate != null) obj.put("endDate", task.endDate)
                obj.put("isCompleted", task.isCompleted)
                obj.put("status", task.status)
                if (task.customThreshold != null) obj.put("customThreshold", task.customThreshold)
                obj.put("labels", task.labels)
                if (task.dailyTargetValue != null) obj.put("dailyTargetValue", task.dailyTargetValue)
                tasksArray.put(obj)
            }
            root.put("tasks", tasksArray)

            // Task Progress
            val progressArray = org.json.JSONArray()
            allTaskProgress.value.forEach { progress ->
                val obj = org.json.JSONObject()
                obj.put("taskId", progress.taskId)
                obj.put("trackId", progress.trackId)
                obj.put("completedPlayCount", progress.completedPlayCount)
                obj.put("completedDays", progress.completedDays)
                obj.put("isTrackCompleted", progress.isTrackCompleted)
                progressArray.put(obj)
            }
            root.put("taskProgress", progressArray)

            // Notes & Tags Backup
            val notesArray = org.json.JSONArray()
            notes.value.forEach { note ->
                val obj = org.json.JSONObject()
                obj.put("id", note.id)
                obj.put("text", note.text)
                obj.put("comment", note.comment)
                if (note.trackId != null) obj.put("trackId", note.trackId)
                if (note.trackName != null) obj.put("trackName", note.trackName)
                if (note.folderId != null) obj.put("folderId", note.folderId)
                if (note.folderName != null) obj.put("folderName", note.folderName)
                obj.put("startTimestampMs", note.startTimestampMs)
                obj.put("endTimestampMs", note.endTimestampMs)
                if (note.originStartMs != null) obj.put("originStartMs", note.originStartMs)
                obj.put("tags", note.tags)
                obj.put("createdAt", note.createdAt)
                obj.put("updatedAt", note.updatedAt)
                notesArray.put(obj)
            }
            root.put("notes", notesArray)

            val tagsArray = org.json.JSONArray()
            noteTags.value.forEach { tag ->
                val obj = org.json.JSONObject()
                obj.put("id", tag.id)
                obj.put("name", tag.name)
                if (tag.colorHex != null) obj.put("colorHex", tag.colorHex)
                obj.put("createdAt", tag.createdAt)
                tagsArray.put(obj)
            }
            root.put("noteTags", tagsArray)

            outputStream.write(root.toString(2).toByteArray(Charsets.UTF_8))
            outputStream.flush()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun restoreBackupFromJson(inputStream: java.io.InputStream) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                val root = org.json.JSONObject(jsonString)

                // Restore playback history
                if (root.has("playbackHistory")) {
                    val historyArray = root.getJSONArray("playbackHistory")
                    for (i in 0 until historyArray.length()) {
                        val obj = historyArray.getJSONObject(i)
                        val history = PlaybackHistory(
                            id = if (obj.has("id")) obj.getLong("id") else 0L,
                            trackId = obj.optLong("trackId", 0L),
                            trackName = obj.optString("trackName", "Track"),
                            completedAt = obj.optLong("completedAt", System.currentTimeMillis()),
                            durationMs = obj.optLong("durationMs", 0L),
                            playbackSpeed = obj.optDouble("playbackSpeed", 1.0).toFloat(),
                            actualListenedMs = obj.optLong("actualListenedMs", 0L),
                            activeTasks = obj.optString("activeTasks", "")
                        )
                        repository.insertPlaybackHistory(history)
                    }
                }

                // Restore tasks
                if (root.has("tasks")) {
                    val tasksArray = root.getJSONArray("tasks")
                    for (i in 0 until tasksArray.length()) {
                        val obj = tasksArray.getJSONObject(i)
                        val task = Task(
                            id = if (obj.has("id")) obj.getLong("id") else 0L,
                            title = obj.optString("title", "Task"),
                            sourceType = obj.optString("sourceType", "TRACKS"),
                            sourceId = if (obj.has("sourceId") && !obj.isNull("sourceId")) obj.getLong("sourceId") else null,
                            targetType = obj.optString("targetType", "PLAY_COUNT"),
                            targetValue = obj.optInt("targetValue", 1),
                            scheduledDays = obj.optString("scheduledDays", ""),
                            reminderTime = obj.optString("reminderTime", "08:00 AM"),
                            startDate = obj.optLong("startDate", System.currentTimeMillis()),
                            endDate = if (obj.has("endDate") && !obj.isNull("endDate")) obj.getLong("endDate") else null,
                            isCompleted = obj.optBoolean("isCompleted", false),
                            status = obj.optString("status", "ACTIVE"),
                            customThreshold = if (obj.has("customThreshold") && !obj.isNull("customThreshold")) obj.getInt("customThreshold") else null,
                            labels = obj.optString("labels", ""),
                            dailyTargetValue = if (obj.has("dailyTargetValue") && !obj.isNull("dailyTargetValue")) obj.getInt("dailyTargetValue") else null
                        )
                        repository.insertTask(task)
                    }
                }

                // Restore progress
                if (root.has("taskProgress")) {
                    val progressArray = root.getJSONArray("taskProgress")
                    for (i in 0 until progressArray.length()) {
                        val obj = progressArray.getJSONObject(i)
                        val p = TaskTrackProgress(
                            taskId = obj.optLong("taskId", 0L),
                            trackId = obj.optLong("trackId", 0L),
                            completedPlayCount = obj.optInt("completedPlayCount", 0),
                            completedDays = obj.optString("completedDays", ""),
                            isTrackCompleted = obj.optBoolean("isTrackCompleted", false)
                        )
                        repository.insertTaskProgress(p)
                    }
                }

                // Restore note tags
                if (root.has("noteTags")) {
                    val tagsArray = root.getJSONArray("noteTags")
                    for (i in 0 until tagsArray.length()) {
                        val obj = tagsArray.getJSONObject(i)
                        val name = obj.optString("name", "").trim()
                        if (name.isNotEmpty()) {
                            repository.insertTag(name, if (obj.has("colorHex") && !obj.isNull("colorHex")) obj.getString("colorHex") else null)
                        }
                    }
                }

                // Restore notes
                if (root.has("notes")) {
                    val notesArray = root.getJSONArray("notes")
                    for (i in 0 until notesArray.length()) {
                        val obj = notesArray.getJSONObject(i)
                        val note = Note(
                            id = if (obj.has("id")) obj.getLong("id") else 0L,
                            text = obj.optString("text", ""),
                            comment = obj.optString("comment", ""),
                            trackId = if (obj.has("trackId") && !obj.isNull("trackId")) obj.getLong("trackId") else null,
                            trackName = if (obj.has("trackName") && !obj.isNull("trackName")) obj.getString("trackName") else null,
                            folderId = if (obj.has("folderId") && !obj.isNull("folderId")) obj.getLong("folderId") else null,
                            folderName = if (obj.has("folderName") && !obj.isNull("folderName")) obj.getString("folderName") else null,
                            startTimestampMs = obj.optLong("startTimestampMs", 0L),
                            endTimestampMs = obj.optLong("endTimestampMs", 0L),
                            originStartMs = if (obj.has("originStartMs") && !obj.isNull("originStartMs")) obj.getLong("originStartMs") else null,
                            tags = obj.optString("tags", ""),
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                        )
                        repository.insertNote(note)
                    }
                }

                withContext(Dispatchers.Main) {
                    val context = getApplication<Application>()
                    Toast.makeText(context, Loc.getText("restore_success"), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    val context = getApplication<Application>()
                    Toast.makeText(context, Loc.getText("restore_failed"), Toast.LENGTH_LONG).show()
                }
            }
        }
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
        if (note.trackId == null) return
        val targetTrack = tracks.value.find { it.id == note.trackId }
        val context = getApplication<Application>()
        if (targetTrack != null) {
            NoteAudioPlayer.playSnippet(
                context = context,
                trackFilePath = targetTrack.filePath,
                noteId = note.id,
                startMs = note.startTimestampMs,
                endMs = if (note.endTimestampMs > note.startTimestampMs) note.endTimestampMs else null
            )
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                val dbTrack = repository.dao.getTrackById(note.trackId)
                if (dbTrack != null) {
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

    fun startAiSceneDetection(track: AudioTrack, onSuccess: ((folderId: Long, sceneCount: Int) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            try {
                isDetectingScenes.value = true
                detectingTrackName.value = track.fileName
                sceneDetectionStatus.value = Loc.getText("ai_scene_extracting_subtitles")

                // 1. Get subtitle cues
                var cues: List<com.example.player.SubtitleCue> = emptyList()
                if (!track.subtitleContent.isNullOrBlank()) {
                    cues = com.example.player.SubtitleParser.parseContent(track.subtitleContent, track.subtitleOffsetMs)
                } else if (!track.subtitlePath.isNullOrBlank()) {
                    cues = com.example.player.SubtitleParser.parseFile(File(track.subtitlePath), track.subtitleOffsetMs)
                } else {
                    val matching = com.example.player.SubtitleParser.findMatchingSubtitleFile(track.filePath)
                    if (matching != null && matching.exists()) {
                        cues = com.example.player.SubtitleParser.parseFile(matching, track.subtitleOffsetMs)
                    }
                }

                if (cues.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        isDetectingScenes.value = false
                        detectingTrackName.value = null
                        sceneDetectionStatus.value = null
                        Toast.makeText(context, Loc.getText("ai_scene_no_subtitles_error"), Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                // 2. Call Gemini
                sceneDetectionStatus.value = Loc.getText("ai_scene_analyzing")
                val totalDuration = if (track.duration > 0) track.duration else (cues.lastOrNull()?.endMs ?: 60000L)
                val langCode = Loc.currentLanguage
                val result = GeminiService.detectScenes(
                    transcriptCues = cues,
                    totalDurationMs = totalDuration,
                    mediaTitle = track.fileName,
                    customApiKey = customGeminiApiKey,
                    language = langCode
                )

                val detectedScenes = result.getOrNull()
                if (detectedScenes.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        isDetectingScenes.value = false
                        detectingTrackName.value = null
                        sceneDetectionStatus.value = null
                        val errMsg = result.exceptionOrNull()?.message ?: Loc.getText("ai_scene_detection_failed")
                        Toast.makeText(context, "${Loc.getText("ai_scene_detection_failed")}: $errMsg", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                // 3. Create Scenes Folder
                sceneDetectionStatus.value = Loc.getText("ai_scene_saving")
                val baseCleanName = track.fileName.substringBeforeLast(".")
                val folderName = if (langCode == "ar") "مشاهد - $baseCleanName" else "$baseCleanName - Scenes"

                val parentDir = File(context.filesDir, "imported")
                val folderDir = File(parentDir, folderName.replace("/", "_").replace(" ", "_"))
                folderDir.mkdirs()
                val folderPath = folderDir.absolutePath

                val folderId = repository.addFolder(
                    path = folderPath,
                    name = folderName,
                    parentFolderId = track.parentFolderId
                )

                // 4. Insert virtual AudioTrack for each scene
                detectedScenes.forEachIndexed { index, scene ->
                    val sceneNum = index + 1
                    val formattedNumber = String.format(Locale.US, "%03d", sceneNum)
                    val sceneTitle = scene.title.ifBlank { "Scene $sceneNum" }
                    val virtualFileName = "$formattedNumber - $sceneTitle"
                    val sceneDuration = (scene.endMs - scene.startMs).coerceAtLeast(1000L)

                    val virtualTrack = AudioTrack(
                        filePath = track.filePath,
                        fileName = virtualFileName,
                        duration = sceneDuration,
                        playCount = 0,
                        lastPosition = 0L,
                        parentFolderId = folderId,
                        isMissing = false,
                        isIndependent = false,
                        listenedSegments = "",
                        subtitlePath = track.subtitlePath,
                        subtitleContent = track.subtitleContent,
                        subtitleOffsetMs = track.subtitleOffsetMs,
                        startOffsetMs = scene.startMs,
                        endOffsetMs = scene.endMs,
                        isVirtualScene = true,
                        parentTrackId = track.id,
                        sceneNumber = sceneNum
                    )
                    repository.insertTrack(virtualTrack)
                }

                withContext(Dispatchers.Main) {
                    isDetectingScenes.value = false
                    detectingTrackName.value = null
                    sceneDetectionStatus.value = null
                    val successMsg = String.format(Locale.getDefault(), Loc.getText("ai_scenes_created_success"), detectedScenes.size, folderName)
                    Toast.makeText(context, successMsg, Toast.LENGTH_LONG).show()
                    onSuccess?.invoke(folderId, detectedScenes.size)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isDetectingScenes.value = false
                    detectingTrackName.value = null
                    sceneDetectionStatus.value = null
                    Toast.makeText(context, "${Loc.getText("ai_scene_detection_failed")}: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun getScenesForTrackFlow(parentTrackId: Long): Flow<List<AudioTrack>> {
        return repository.getScenesForParentTrackFlow(parentTrackId)
    }

    fun updateVirtualScene(
        track: AudioTrack,
        newTitle: String,
        newStartOffsetMs: Long,
        newEndOffsetMs: Long,
        onSuccess: (() -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val durationMs = (newEndOffsetMs - newStartOffsetMs).coerceAtLeast(1000L)
            val updated = track.copy(
                fileName = newTitle.trim().ifBlank { track.fileName },
                startOffsetMs = newStartOffsetMs.coerceAtLeast(0L),
                endOffsetMs = newEndOffsetMs,
                duration = durationMs
            )
            repository.updateTrack(updated)

            // If currently playing, update in player
            if (AudioPlayerManager.currentTrack.value?.id == track.id) {
                withContext(Dispatchers.Main) {
                    AudioPlayerManager.playTrack(updated)
                }
            }

            withContext(Dispatchers.Main) {
                val context = getApplication<Application>()
                Toast.makeText(context, Loc.getText("scene_updated_success"), Toast.LENGTH_SHORT).show()
                onSuccess?.invoke()
            }
        }
    }

    fun deleteVirtualScene(track: AudioTrack, onSuccess: (() -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            // If currently playing, stop
            if (AudioPlayerManager.currentTrack.value?.id == track.id) {
                withContext(Dispatchers.Main) {
                    AudioPlayerManager.pause()
                }
            }
            repository.deleteTrack(track)
            withContext(Dispatchers.Main) {
                val context = getApplication<Application>()
                Toast.makeText(context, Loc.getText("scene_deleted_success"), Toast.LENGTH_SHORT).show()
                onSuccess?.invoke()
            }
        }
    }

    fun addNewVirtualScene(
        parentTrack: AudioTrack,
        folderId: Long?,
        title: String,
        startOffsetMs: Long,
        endOffsetMs: Long,
        onSuccess: (() -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val sceneDuration = (endOffsetMs - startOffsetMs).coerceAtLeast(1000L)
            val existingScenes = repository.getScenesForParentTrack(parentTrack.id)
            val nextSceneNumber = (existingScenes.maxOfOrNull { it.sceneNumber ?: 0 } ?: 0) + 1
            val formattedNumber = String.format(Locale.US, "%03d", nextSceneNumber)
            val cleanTitle = title.trim().ifBlank { "Scene $nextSceneNumber" }
            val virtualFileName = "$formattedNumber - $cleanTitle"

            val virtualTrack = AudioTrack(
                filePath = parentTrack.filePath,
                fileName = virtualFileName,
                duration = sceneDuration,
                playCount = 0,
                lastPosition = 0L,
                parentFolderId = folderId ?: parentTrack.parentFolderId,
                isMissing = false,
                isIndependent = false,
                listenedSegments = "",
                subtitlePath = parentTrack.subtitlePath,
                subtitleContent = parentTrack.subtitleContent,
                subtitleOffsetMs = parentTrack.subtitleOffsetMs,
                startOffsetMs = startOffsetMs.coerceAtLeast(0L),
                endOffsetMs = endOffsetMs,
                isVirtualScene = true,
                parentTrackId = parentTrack.id,
                sceneNumber = nextSceneNumber
            )
            repository.insertTrack(virtualTrack)

            withContext(Dispatchers.Main) {
                val context = getApplication<Application>()
                Toast.makeText(context, Loc.getText("scene_created_success"), Toast.LENGTH_SHORT).show()
                onSuccess?.invoke()
            }
        }
    }
}
