package com.example.ai

import com.example.player.SubtitleCue

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String, // "user" or "model"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val attachedContext: AudioContextSummary? = null
)

data class AudioContextSummary(
    val trackTitle: String? = null,
    val trackArtist: String? = null,
    val currentPositionMs: Long? = null,
    val formattedPosition: String? = null,
    val activeSubtitleLine: String? = null,
    val activeTaskTitle: String? = null,
    val fullSubtitlesText: String? = null,
    val isFullSubtitlesContext: Boolean = false,
    val audioFilePath: String? = null,
    val trackId: Long? = null,
    val totalDurationMs: Long? = null
)

data class GeneratedQuizItem(
    val questionType: String, // "MCQ" or "TRUE_FALSE"
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String,
    val timestampMs: Long? = null,
    val category: String = "COMPREHENSION", // "VOCABULARY" or "COMPREHENSION"
    val targetWord: String? = null,
    val meaning: String? = null,
    val contextSentence: String? = null
)

sealed class QuizContentSource {
    data class Transcript(
        val mediaTitle: String,
        val cues: List<SubtitleCue>,
        val existingQuestions: List<String> = emptyList(),
        val questionCount: Int = 4
    ) : QuizContentSource()

    data class NotebookNotes(
        val notes: List<NoteInputForQuiz>,
        val maxQuestions: Int = 4
    ) : QuizContentSource()
}

data class UnifiedQuizItem(
    val questionType: String = "MCQ", // "MCQ" or "TRUE_FALSE"
    val category: String = "VOCABULARY", // "VOCABULARY" or "COMPREHENSION"
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String,
    val timestampMs: Long? = null,
    val sourceNoteId: Long? = null,
    val trackId: Long? = null,
    val targetWord: String? = null,
    val meaning: String? = null,
    val contextSentence: String? = null,
    val savedQuestionId: Long? = null
) {
    fun toGeneratedQuizItem(): GeneratedQuizItem = GeneratedQuizItem(
        questionType = questionType,
        question = question,
        options = options,
        correctIndex = correctIndex,
        explanation = explanation,
        timestampMs = timestampMs,
        category = category,
        targetWord = targetWord,
        meaning = meaning,
        contextSentence = contextSentence
    )

    fun toGeneratedNoteQuizItem(defaultNoteId: Long = 0L): GeneratedNoteQuizItem = GeneratedNoteQuizItem(
        sourceNoteId = sourceNoteId ?: defaultNoteId,
        targetWord = targetWord ?: "Vocabulary",
        meaning = meaning ?: "",
        contextSentence = contextSentence ?: "",
        questionType = questionType,
        question = question,
        options = options,
        correctIndex = correctIndex,
        explanation = explanation,
        timestampMs = timestampMs,
        trackId = trackId,
        category = category,
        savedQuestionId = savedQuestionId
    )
}

data class NoteInputForQuiz(
    val id: Long,
    val text: String,
    val comment: String = "",
    val tags: List<String> = emptyList(),
    val trackId: Long? = null,
    val trackName: String? = null,
    val startTimestampMs: Long = 0L,
    val targetWord: String? = null,
    val meaning: String? = null,
    val contextSentence: String? = null
)

data class GeneratedNoteQuizItem(
    val sourceNoteId: Long,
    val targetWord: String,
    val meaning: String = "",
    val contextSentence: String = "",
    val questionType: String = "MCQ",
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String,
    val timestampMs: Long? = null,
    val trackId: Long? = null,
    val category: String = "VOCABULARY",
    val savedQuestionId: Long? = null
)

data class DetectedScene(
    val sceneNumber: Int,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val summary: String = ""
)
