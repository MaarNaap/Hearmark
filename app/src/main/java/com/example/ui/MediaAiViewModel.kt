package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.*
import com.example.data.*
import com.example.player.AudioPlayerManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Focused controller for Media AI tasks:
 * - Conversational AI Assistant / Audio Chat
 * - Automated Subtitle Transcription & Synchronization
 * - Contextual Vocabulary Note Definition & Clean Formatting
 */
class MediaAiViewModel(
    application: Application,
    private val apiKeyProvider: () -> String,
    private val languageProvider: () -> String = { Loc.currentLanguage }
) : AndroidViewModel(application) {

    // --- GEMINI CHATBOT STATE ---
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isGeneratingAiResponse = MutableStateFlow(false)
    val isGeneratingAiResponse: StateFlow<Boolean> = _isGeneratingAiResponse.asStateFlow()

    val isChatDialogOpen = MutableStateFlow(false)
    val activeChatContext = MutableStateFlow<AudioContextSummary?>(null)

    // --- SUBTITLE GENERATION STATE ---
    private val _isGeneratingSubtitles = MutableStateFlow(false)
    val isGeneratingSubtitles: StateFlow<Boolean> = _isGeneratingSubtitles.asStateFlow()

    private val _subtitleGenerationStatus = MutableStateFlow("")
    val subtitleGenerationStatus: StateFlow<String> = _subtitleGenerationStatus.asStateFlow()

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
                customApiKey = apiKeyProvider(),
                language = languageProvider()
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
                customApiKey = apiKeyProvider(),
                language = languageProvider(),
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
            customApiKey = apiKeyProvider(),
            language = "en",
            customSystemInstruction = GeminiService.VOCAB_NOTE_SYSTEM_PROMPT
        )

        return rawResult.map { cleanMarkdownToPlainText(it) }
    }

    fun cleanMarkdownToPlainText(input: String): String {
        var text = input
        text = text.replace(Regex("```[a-zA-Z]*\n?"), "").replace("```", "")
        text = text.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        text = text.replace(Regex("__([^_]+)__"), "$1")
        text = text.replace(Regex("\\*([^*]+)\\*"), "$1")
        text = text.replace(Regex("(?<!\\w)_([^_]+)_(?!\\w)"), "$1")
        text = text.replace(Regex("`([^`]+)`"), "$1")
        text = text.replace(Regex("(?m)^#{1,6}\\s*"), "")
        text = text.replace(Regex("(?m)^[\\*\\-\\+]\\s+"), "• ")
        text = text.replace(Regex("(?m)^>\\s*"), "")
        text = text.replace("**", "").replace("*", "")
        return text.trim()
    }
}
