package com.example.ai

import com.example.player.SubtitleCue
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object GeminiService {
    const val DEFAULT_MODEL = GeminiModelHealth.DEFAULT_MODEL
    const val FALLBACK_MODEL = GeminiModelHealth.FALLBACK_MODEL
    const val LITE_FALLBACK_MODEL = GeminiModelHealth.LITE_FALLBACK_MODEL
    const val STABLE_FLASH_MODEL = GeminiModelHealth.STABLE_FLASH_MODEL
    const val STABLE_LITE_MODEL = GeminiModelHealth.STABLE_LITE_MODEL
    const val PRO_FALLBACK_MODEL = GeminiModelHealth.PRO_FALLBACK_MODEL

    val MODEL_FALLBACK_CHAIN: List<String>
        get() = GeminiModelHealth.MODEL_FALLBACK_CHAIN

    const val VOCAB_NOTE_SYSTEM_PROMPT = GeminiPrompts.VOCAB_NOTE_SYSTEM_PROMPT

    internal fun extractNonThoughtTextFromParts(parts: JSONArray?): String =
        GeminiHttp.extractNonThoughtTextFromParts(parts)

    internal fun buildFastThinkingConfig(): JSONObject =
        GeminiHttp.buildFastThinkingConfig()

    internal fun stripThinkingOrClampTokensFromPayload(payload: JSONObject, errorBody: String): JSONObject =
        GeminiHttp.stripThinkingOrClampTokensFromPayload(payload, errorBody)

    fun setConfiguredApiKeys(primaryKey: String?, allKeys: List<String>) =
        GeminiKeyPool.setConfiguredApiKeys(primaryKey, allKeys)

    fun resolveCandidateApiKeys(customApiKey: String?): List<String> =
        GeminiKeyPool.resolveCandidateApiKeys(customApiKey)

    fun resolveApiKey(customApiKey: String?): String =
        GeminiKeyPool.resolveApiKey(customApiKey)

    internal fun selectOrderedCandidateModels(
        preferredModel: String = DEFAULT_MODEL,
        nowMs: Long = System.currentTimeMillis()
    ): List<String> = GeminiModelHealth.selectOrderedCandidateModels(preferredModel, nowMs)

    internal fun markModelSuccess(model: String, nowMs: Long = System.currentTimeMillis()) =
        GeminiModelHealth.markModelSuccess(model, nowMs)

    internal fun markModelFailure(model: String, httpCode: Int, nowMs: Long = System.currentTimeMillis()) =
        GeminiModelHealth.markModelFailure(model, httpCode, nowMs)

    internal fun clearModelHealthStateForTesting() =
        GeminiModelHealth.clearModelHealthStateForTesting()

    fun getMediaDurationMs(file: File): Long =
        GeminiMedia.getMediaDurationMs(file)

    fun extractAudioFromVideoIfPossible(videoFile: File): File =
        GeminiMedia.extractAudioFromVideoIfPossible(videoFile)

    internal fun extractAudioTimeSliceIfPossible(
        sourceFile: File,
        startMs: Long,
        endMs: Long
    ): File? = GeminiMedia.extractAudioTimeSliceIfPossible(sourceFile, startMs, endMs)

    suspend fun executeGeminiPostWithRetry(
        urlBuilder: (model: String) -> String,
        payload: JSONObject,
        primaryModel: String = DEFAULT_MODEL,
        fallbackModel: String = FALLBACK_MODEL,
        maxAttempts: Int = 6,
        client: OkHttpClient = GeminiHttp.okHttpClient,
        language: String = "en",
        candidateApiKeys: List<String> = emptyList(),
        urlWithKeyBuilder: ((model: String, apiKey: String) -> String)? = null,
        onProgressUpdate: ((String) -> Unit)? = null
    ): Pair<Int, String> = GeminiHttp.executeGeminiPostWithRetry(
        urlBuilder = urlBuilder,
        payload = payload,
        primaryModel = primaryModel,
        fallbackModel = fallbackModel,
        maxAttempts = maxAttempts,
        client = client,
        language = language,
        candidateApiKeys = candidateApiKeys,
        urlWithKeyBuilder = urlWithKeyBuilder,
        onProgressUpdate = onProgressUpdate
    )

    suspend fun sendMessage(
        history: List<ChatMessage>,
        newUserMessage: String,
        contextSummary: AudioContextSummary? = null,
        customApiKey: String? = null,
        language: String = "en",
        modelName: String = DEFAULT_MODEL,
        customSystemInstruction: String? = null
    ): Result<String> = GeminiChat.sendMessage(
        history = history,
        newUserMessage = newUserMessage,
        contextSummary = contextSummary,
        customApiKey = customApiKey,
        language = language,
        modelName = modelName,
        customSystemInstruction = customSystemInstruction
    )

    fun cleanJsonText(rawText: String): String =
        GeminiHttp.cleanJsonText(rawText)

    fun parseScenesResiliently(rawText: String, totalDurationMs: Long): List<DetectedScene> =
        GeminiSceneDetection.parseScenesResiliently(rawText, totalDurationMs)

    suspend fun detectScenes(
        transcriptCues: List<SubtitleCue>,
        totalDurationMs: Long,
        mediaTitle: String,
        customApiKey: String? = null,
        language: String = "en",
        modelName: String = FALLBACK_MODEL,
        onProgressUpdate: ((String) -> Unit)? = null
    ): Result<List<DetectedScene>> = GeminiSceneDetection.detectScenes(
        transcriptCues = transcriptCues,
        totalDurationMs = totalDurationMs,
        mediaTitle = mediaTitle,
        customApiKey = customApiKey,
        language = language,
        modelName = modelName,
        onProgressUpdate = onProgressUpdate
    )

    suspend fun generateQuizUnified(
        source: QuizContentSource,
        customApiKey: String? = null,
        language: String = "en",
        modelName: String = FALLBACK_MODEL
    ): Result<List<UnifiedQuizItem>> = GeminiQuiz.generateQuizUnified(
        source = source,
        customApiKey = customApiKey,
        language = language,
        modelName = modelName
    )

    suspend fun generateQuizQuestions(
        mediaTitle: String,
        transcriptCues: List<SubtitleCue>,
        existingQuestions: List<String> = emptyList(),
        customApiKey: String? = null,
        language: String = "en",
        modelName: String = DEFAULT_MODEL
    ): Result<List<GeneratedQuizItem>> = GeminiQuiz.generateQuizQuestions(
        mediaTitle = mediaTitle,
        transcriptCues = transcriptCues,
        existingQuestions = existingQuestions,
        customApiKey = customApiKey,
        language = language,
        modelName = modelName
    )

    suspend fun generateQuizFromNotebookNotes(
        notes: List<NoteInputForQuiz>,
        maxQuestions: Int = 10,
        customApiKey: String? = null,
        language: String = "en",
        modelName: String = DEFAULT_MODEL
    ): Result<List<GeneratedNoteQuizItem>> = GeminiQuiz.generateQuizFromNotebookNotes(
        notes = notes,
        maxQuestions = maxQuestions,
        customApiKey = customApiKey,
        language = language,
        modelName = modelName
    )

    internal fun parseGeminiErrorMessage(code: Int, body: String, language: String): String =
        GeminiHttp.parseGeminiErrorMessage(code, body, language)

    internal fun getMimeTypeForFile(file: File): String =
        GeminiHttp.getMimeTypeForFile(file)

    internal suspend fun uploadFileToGemini(
        file: File,
        mimeType: String,
        apiKey: String,
        onProgressUpdate: ((String) -> Unit)? = null
    ): String = GeminiHttp.uploadFileToGemini(
        file = file,
        mimeType = mimeType,
        apiKey = apiKey,
        onProgressUpdate = onProgressUpdate
    )

    fun cleanSrtOutput(rawText: String): String =
        GeminiSubtitleParsing.cleanSrtOutput(rawText)

    internal fun parseSseSubtitleStream(
        body: ResponseBody,
        language: String,
        chunkIdx: Int,
        totalChunks: Int,
        accumulatedCuesCount: Int,
        onProgressUpdate: ((String) -> Unit)?
    ): String = GeminiSubtitleParsing.parseSseSubtitleStream(
        body = body,
        language = language,
        chunkIdx = chunkIdx,
        totalChunks = totalChunks,
        accumulatedCuesCount = accumulatedCuesCount,
        onProgressUpdate = onProgressUpdate
    )

    suspend fun generateSubtitles(
        audioFile: File,
        existingSubtitleText: String? = null,
        totalDurationMs: Long = 0L,
        customApiKey: String? = null,
        language: String = "en",
        modelName: String = FALLBACK_MODEL,
        onProgressUpdate: ((String) -> Unit)? = null
    ): Result<String> = GeminiSubtitles.generateSubtitles(
        audioFile = audioFile,
        existingSubtitleText = existingSubtitleText,
        totalDurationMs = totalDurationMs,
        customApiKey = customApiKey,
        language = language,
        modelName = modelName,
        onProgressUpdate = onProgressUpdate
    )
}
