package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.*
import com.example.data.*
import com.example.player.AudioPlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class QuizTabMode {
    TRACK,
    NOTEBOOK,
    BANK
}

/**
 * Focused controller for all quiz operations:
 * - Track-based quizzes generated from dialogue/subtitles
 * - Notebook-based vocabulary quizzes
 * - Quiz question banks, answering, and score tracking
 * - Unified quiz modal sheet state
 */
class QuizViewModel(
    application: Application,
    private val repository: AppRepository,
    private val apiKeyProvider: () -> String,
    private val languageProvider: () -> String = { Loc.currentLanguage }
) : AndroidViewModel(application) {

    // --- TRACK QUIZ & PRACTICE BANK STATE ---
    val isQuizSheetOpen = MutableStateFlow(false)
    val isGeneratingQuiz = MutableStateFlow(false)
    val quizGenerationError = MutableStateFlow<String?>(null)
    val quizGenerationSuccessMessage = MutableStateFlow<String?>(null)
    val activeQuizTargetTrack = MutableStateFlow<AudioTrack?>(null)
    val currentTrackQuizQuestions = MutableStateFlow<List<QuizQuestion>>(emptyList())
    val allVocabularyQuestions: StateFlow<List<QuizQuestion>> = repository.getAllVocabularyQuestionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- NOTEBOOK VOCABULARY QUIZ STATE ---
    val isNotebookQuizSheetOpen = MutableStateFlow(false)
    val isGeneratingNotebookQuiz = MutableStateFlow(false)
    val notebookQuizError = MutableStateFlow<String?>(null)
    val notebookQuizGeneratedQuestions = MutableStateFlow<List<GeneratedNoteQuizItem>>(emptyList())
    val notebookQuizSuccessMessage = MutableStateFlow<String?>(null)
    val notebookVocabularyQuestions: StateFlow<List<QuizQuestion>> = repository.getNotebookVocabularyQuestionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- UNIFIED QUIZ ENTRY POINT STATE (Phase 3) ---
    val isUnifiedQuizSheetOpen = MutableStateFlow(false)
    val activeQuizTabMode = MutableStateFlow(QuizTabMode.TRACK)
    val unifiedQuizInitialTrack = MutableStateFlow<AudioTrack?>(null)
    val unifiedQuizInitialNotes = MutableStateFlow<List<Note>?>(null)

    fun setQuizTabMode(mode: QuizTabMode) {
        activeQuizTabMode.value = mode
    }

    fun openUnifiedQuiz(
        tabMode: QuizTabMode = QuizTabMode.TRACK,
        initialTrack: AudioTrack? = null,
        initialNotes: List<Note>? = null
    ) {
        activeQuizTabMode.value = tabMode
        val trackToUse = initialTrack ?: activeQuizTargetTrack.value ?: AudioPlayerManager.currentTrack.value
        if (trackToUse != null) {
            activeQuizTargetTrack.value = trackToUse
            loadQuizQuestionsForTrack(trackToUse.id)
        }
        unifiedQuizInitialTrack.value = trackToUse
        unifiedQuizInitialNotes.value = initialNotes
        isUnifiedQuizSheetOpen.value = true
        isQuizSheetOpen.value = true
    }

    fun openUnifiedQuizSheet(initialTrack: AudioTrack? = null, initialNotes: List<Note>? = null) {
        openUnifiedQuiz(
            tabMode = if (initialNotes != null) QuizTabMode.NOTEBOOK else QuizTabMode.TRACK,
            initialTrack = initialTrack,
            initialNotes = initialNotes
        )
    }

    fun closeUnifiedQuizSheet() {
        isUnifiedQuizSheetOpen.value = false
        isQuizSheetOpen.value = false
        isNotebookQuizSheetOpen.value = false
        unifiedQuizInitialTrack.value = null
        unifiedQuizInitialNotes.value = null
        quizGenerationError.value = null
        notebookQuizError.value = null
    }

    // --- TRACK QUIZ METHODS ---

    fun openQuizForTrack(track: AudioTrack) {
        activeQuizTabMode.value = QuizTabMode.TRACK
        activeQuizTargetTrack.value = track
        unifiedQuizInitialTrack.value = track
        quizGenerationError.value = null
        quizGenerationSuccessMessage.value = null
        viewModelScope.launch {
            loadQuizQuestionsForTrack(track.id)
            isQuizSheetOpen.value = true
            isUnifiedQuizSheetOpen.value = true
        }
    }

    fun openQuizForCurrentTrack() {
        val current = AudioPlayerManager.currentTrack.value ?: return
        openQuizForTrack(current)
    }

    fun closeQuizSheet() {
        closeUnifiedQuizSheet()
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
                val existingQuestions = repository.getQuestionsForTrackDirect(track.id)
                val existingQuestionTexts = existingQuestions.map { it.question.trim() }.filter { it.isNotBlank() }

                val source = QuizContentSource.Transcript(
                    mediaTitle = track.getDisplayTitle(),
                    cues = cuesToUse,
                    existingQuestions = existingQuestionTexts
                )

                val result = GeminiService.generateQuizUnified(
                    source = source,
                    customApiKey = apiKeyProvider(),
                    language = languageProvider()
                )

                result.onSuccess { generatedItems ->
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

    // --- NOTEBOOK VOCABULARY QUIZ METHODS ---

    fun openNotebookQuizSheet(initialNotes: List<Note>? = null) {
        activeQuizTabMode.value = QuizTabMode.NOTEBOOK
        unifiedQuizInitialNotes.value = initialNotes
        notebookQuizError.value = null
        isNotebookQuizSheetOpen.value = true
        isUnifiedQuizSheetOpen.value = true
        isQuizSheetOpen.value = true
    }

    fun closeNotebookQuizSheet() {
        closeUnifiedQuizSheet()
    }

    fun resetNotebookQuiz() {
        notebookQuizGeneratedQuestions.value = emptyList()
        notebookQuizError.value = null
        notebookQuizSuccessMessage.value = null
    }

    fun clearNotebookQuizFeedback() {
        notebookQuizError.value = null
    }

    fun deleteNotebookQuizQuestion(item: GeneratedNoteQuizItem) {
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
                    NoteInputForQuiz(
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

                val source = QuizContentSource.NotebookNotes(
                    notes = inputList,
                    maxQuestions = maxQuestions
                )

                val result = GeminiService.generateQuizUnified(
                    source = source,
                    customApiKey = apiKeyProvider(),
                    language = languageProvider()
                )

                result.onSuccess { generatedUnified ->
                    isGeneratingNotebookQuiz.value = false
                    val defaultNoteId = notes.firstOrNull()?.id ?: 0L
                    val generated = generatedUnified.map { it.toGeneratedNoteQuizItem(defaultNoteId) }

                    if (generated.isNotEmpty()) {
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
        items: List<GeneratedNoteQuizItem>,
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
                    NoteInputForQuiz(
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
                val source = QuizContentSource.NotebookNotes(
                    notes = input,
                    maxQuestions = 1
                )
                val result = GeminiService.generateQuizUnified(
                    source = source,
                    customApiKey = apiKeyProvider(),
                    language = languageProvider()
                )
                result.onSuccess { generatedUnifiedList ->
                    val first = generatedUnifiedList.firstOrNull()?.toGeneratedNoteQuizItem(note.id)
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
}
