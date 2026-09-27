package com.example.ai

import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.player.SubtitleCue
import com.example.player.SubtitleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object GeminiService {
    private const val TAG = "GeminiService"
    private const val DEFAULT_MODEL = "gemini-3.5-flash"
    private const val FALLBACK_MODEL = "gemini-flash-latest"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    const val VOCAB_NOTE_SYSTEM_PROMPT = GeminiPrompts.VOCAB_NOTE_SYSTEM_PROMPT

    private val uploadedFileCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()

    fun getMediaDurationMs(file: File): Long {
        var retriever: android.media.MediaMetadataRetriever? = null
        return try {
            retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val time = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            time?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            try { retriever?.release() } catch (ignored: Exception) {}
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // Dedicated client for large media uploads and long subtitle transcription streaming (e.g. 18+ min tracks)
    private val subtitleOkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS) // Up to 3 minutes to upload large media files
        .readTimeout(180, TimeUnit.SECONDS)  // Up to 3 minutes between stream packets
        .callTimeout(0, TimeUnit.SECONDS)    // No arbitrary global timeout cutoff
        .retryOnConnectionFailure(true)
        .build()

    fun resolveApiKey(customApiKey: String?): String {
        return when {
            !customApiKey.isNullOrBlank() -> customApiKey.trim()
            try { BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY" } catch (e: Exception) { false } -> BuildConfig.GEMINI_API_KEY
            else -> ""
        }
    }

    suspend fun sendMessage(
        history: List<ChatMessage>,
        newUserMessage: String,
        contextSummary: AudioContextSummary? = null,
        customApiKey: String? = null,
        language: String = "en",
        modelName: String = DEFAULT_MODEL,
        customSystemInstruction: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val resolvedApiKey = resolveApiKey(customApiKey)

            if (resolvedApiKey.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("MISSING_API_KEY")
                )
            }

            // Build request payload
            val rootJson = JSONObject()

            // System instruction tailored to current language with strict no-fluff rules (or custom system instruction)
            val instructionText = customSystemInstruction ?: GeminiPrompts.getSystemInstruction(language)
            val systemInstructionObj = JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", instructionText)
                    })
                })
            }
            rootJson.put("systemInstruction", systemInstructionObj)

            // Generation config
            val genConfig = JSONObject().apply {
                put("temperature", 0.7)
                put("topP", 0.95)
                put("topK", 40)
            }
            rootJson.put("generationConfig", genConfig)

            // Build multi-turn contents
            val contentsArray = JSONArray()

            // Prior conversation turns
            for (msg in history) {
                if (msg.isError || msg.text.isBlank()) continue
                val role = if (msg.role == "user") "user" else "model"
                val contentObj = JSONObject().apply {
                    put("role", role)
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", msg.text)
                        })
                    })
                }
                contentsArray.put(contentObj)
            }

            val promptText = GeminiPrompts.buildChatPromptWithContext(
                newUserMessage = newUserMessage,
                contextSummary = contextSummary,
                language = language
            )

            val currentTurnObj = JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", promptText)
                    })
                })
            }
            contentsArray.put(currentTurnObj)
            rootJson.put("contents", contentsArray)

            val requestUrl = "$BASE_URL/$modelName:generateContent?key=$resolvedApiKey"
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = rootJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(requestUrl)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Gemini API call failed with code: ${response.code}, body: $responseBody")
                return@withContext Result.failure(
                    Exception("HTTP ${response.code}: $responseBody")
                )
            }

            val respJson = JSONObject(responseBody)
            val candidates = respJson.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return@withContext Result.failure(Exception("No response candidate returned by Gemini."))
            }

            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val firstPart = parts?.optJSONObject(0)
            val textResult = firstPart?.optString("text")

            if (textResult.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Empty text in Gemini response."))
            }

            Result.success(textResult)
        } catch (e: Exception) {
            Log.e(TAG, "Error in Gemini sendMessage", e)
            Result.failure(e)
        }
    }

    suspend fun detectScenes(
        transcriptCues: List<SubtitleCue>,
        totalDurationMs: Long,
        mediaTitle: String,
        customApiKey: String? = null,
        language: String = "en",
        modelName: String = DEFAULT_MODEL
    ): Result<List<DetectedScene>> = withContext(Dispatchers.IO) {
        try {
            val resolvedApiKey = when {
                !customApiKey.isNullOrBlank() -> customApiKey.trim()
                try { BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY" } catch (e: Exception) { false } -> BuildConfig.GEMINI_API_KEY
                else -> ""
            }

            if (resolvedApiKey.isBlank()) {
                return@withContext Result.failure(IllegalStateException("MISSING_API_KEY"))
            }

            if (transcriptCues.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("No transcript cues provided."))
            }

            val (systemInstruction, prompt) = GeminiPrompts.buildSceneDetectionPrompt(
                transcriptCues = transcriptCues,
                totalDurationMs = totalDurationMs,
                mediaTitle = mediaTitle,
                language = language
            )

            val rootJson = JSONObject().apply {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemInstruction) })
                    })
                })
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.2)
                })
            }

            val requestUrl = "$BASE_URL/$modelName:generateContent?key=$resolvedApiKey"
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = rootJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(requestUrl)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Gemini Scene Detection failed: ${response.code} -> $responseBody")
                return@withContext Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }

            val respJson = JSONObject(responseBody)
            val candidates = respJson.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return@withContext Result.failure(Exception("No candidate returned by Gemini."))
            }

            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val firstPart = parts?.optJSONObject(0)
            var rawText = firstPart?.optString("text") ?: ""

            if (rawText.isBlank()) {
                return@withContext Result.failure(Exception("Empty text response for scene detection."))
            }

            // Strip any accidental markdown formatting
            rawText = rawText.trim()
            if (rawText.startsWith("```json")) {
                rawText = rawText.removePrefix("```json").trim()
            }
            if (rawText.startsWith("```")) {
                rawText = rawText.removePrefix("```").trim()
            }
            if (rawText.endsWith("```")) {
                rawText = rawText.removeSuffix("```").trim()
            }

            val scenesJsonArray: JSONArray = if (rawText.startsWith("{")) {
                val jsonObject = JSONObject(rawText)
                jsonObject.optJSONArray("scenes") ?: JSONArray()
            } else if (rawText.startsWith("[")) {
                JSONArray(rawText)
            } else {
                return@withContext Result.failure(Exception("Unexpected response format from AI."))
            }

            val rawScenes = mutableListOf<DetectedScene>()
            for (i in 0 until scenesJsonArray.length()) {
                val sceneObj = scenesJsonArray.getJSONObject(i)
                val sceneNumber = sceneObj.optInt("sceneNumber", i + 1)
                var title = sceneObj.optString("title", "Scene ${i + 1}").trim()
                if (title.isBlank()) title = "Scene ${i + 1}"
                
                var startMs = sceneObj.optLong("startMs", -1L)
                var endMs = sceneObj.optLong("endMs", -1L)
                val summary = sceneObj.optString("summary", "")

                // Fallbacks if formatted in seconds
                if (startMs < 0) {
                    val sec = sceneObj.optDouble("start", sceneObj.optDouble("startSec", 0.0))
                    startMs = (sec * 1000).toLong()
                }
                if (endMs < 0) {
                    val sec = sceneObj.optDouble("end", sceneObj.optDouble("endSec", 0.0))
                    endMs = (sec * 1000).toLong()
                }

                if (endMs <= startMs) {
                    endMs = startMs + 120_000L
                }

                rawScenes.add(
                    DetectedScene(
                        sceneNumber = sceneNumber,
                        title = title,
                        startMs = startMs.coerceAtLeast(0L),
                        endMs = endMs,
                        summary = summary
                    )
                )
            }

            if (rawScenes.isEmpty()) {
                return@withContext Result.failure(Exception("AI did not return any scene items."))
            }

            // Sort & Normalize boundaries
            val sorted = rawScenes.sortedBy { it.startMs }
            val validatedList = mutableListOf<DetectedScene>()

            for (i in sorted.indices) {
                val current = sorted[i]
                val actualStart = if (i == 0) 0L else current.startMs
                val actualEnd = if (i == sorted.size - 1) {
                    if (totalDurationMs > 0) maxOf(current.endMs, totalDurationMs) else current.endMs
                } else {
                    val nextStart = sorted[i + 1].startMs
                    if (current.endMs <= actualStart || current.endMs > nextStart) nextStart else current.endMs
                }

                // Strip leading numbering from title if already provided by AI (e.g. "001 - Title" -> "Title")
                val cleanTitle = current.title
                    .replaceFirst(Regex("^\\s*(\\d{1,3}|Scene\\s*\\d{1,3})\\s*[-:.]?\\s*", RegexOption.IGNORE_CASE), "")
                    .ifBlank { "Scene ${i + 1}" }

                validatedList.add(
                    DetectedScene(
                        sceneNumber = i + 1,
                        title = cleanTitle,
                        startMs = actualStart,
                        endMs = maxOf(actualEnd, actualStart + 3000L),
                        summary = current.summary
                    )
                )
            }

            Result.success(validatedList)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during detectScenes", e)
            Result.failure(e)
        }
    }

    suspend fun generateQuizUnified(
        source: QuizContentSource,
        customApiKey: String? = null,
        language: String = "en",
        modelName: String = DEFAULT_MODEL
    ): Result<List<UnifiedQuizItem>> = withContext(Dispatchers.IO) {
        try {
            val resolvedApiKey = resolveApiKey(customApiKey)
            if (resolvedApiKey.isBlank()) {
                return@withContext Result.failure(IllegalStateException("MISSING_API_KEY"))
            }

            // Input validations
            when (source) {
                is QuizContentSource.Transcript -> {
                    if (source.cues.isEmpty()) {
                        return@withContext Result.failure(IllegalArgumentException("No transcript or subtitles available for this track."))
                    }
                }
                is QuizContentSource.NotebookNotes -> {
                    if (source.notes.isEmpty()) {
                        val emptyMsg = if (language == "ar") "لا توجد ملاحظات محددة لتوليد الأسئلة." else "No notes provided to generate quiz questions."
                        return@withContext Result.failure(IllegalArgumentException(emptyMsg))
                    }
                }
            }

            val notesMap: Map<Long, NoteInputForQuiz> = when (source) {
                is QuizContentSource.NotebookNotes -> source.notes.associateBy { it.id }
                else -> emptyMap()
            }

            val (systemInstruction, prompt) = GeminiPrompts.buildUnifiedQuizPrompt(source, language)

            val temperature = when (source) {
                is QuizContentSource.Transcript -> if (source.existingQuestions.isNotEmpty()) 0.5 else 0.3
                is QuizContentSource.NotebookNotes -> 0.4
            }

            val rootJson = JSONObject().apply {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemInstruction) })
                    })
                })
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", temperature)
                })
            }

            val requestUrl = "$BASE_URL/$modelName:generateContent?key=$resolvedApiKey"
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = rootJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(requestUrl)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Gemini Unified Quiz Generation failed: ${response.code} -> $responseBody")
                val cleanErrorMsg = parseGeminiErrorMessage(response.code, responseBody, language)
                return@withContext Result.failure(Exception(cleanErrorMsg))
            }

            val respJson = JSONObject(responseBody)
            val candidates = respJson.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return@withContext Result.failure(Exception("No candidate returned by Gemini."))
            }

            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val firstPart = parts?.optJSONObject(0)
            var rawText = firstPart?.optString("text") ?: ""

            if (rawText.isBlank()) {
                return@withContext Result.failure(Exception("Empty text response for quiz."))
            }

            // Strip markdown code fences
            rawText = rawText.trim()
            if (rawText.startsWith("```json")) {
                rawText = rawText.removePrefix("```json").trim()
            }
            if (rawText.startsWith("```")) {
                rawText = rawText.removePrefix("```").trim()
            }
            if (rawText.endsWith("```")) {
                rawText = rawText.removeSuffix("```").trim()
            }

            val questionsJsonArray: JSONArray = if (rawText.startsWith("{")) {
                val jsonObj = JSONObject(rawText)
                jsonObj.optJSONArray("questions") ?: JSONArray()
            } else if (rawText.startsWith("[")) {
                JSONArray(rawText)
            } else {
                return@withContext Result.failure(Exception("Unexpected response format from AI."))
            }

            val resultList = mutableListOf<UnifiedQuizItem>()
            for (i in 0 until questionsJsonArray.length()) {
                val qObj = questionsJsonArray.getJSONObject(i)
                val typeRaw = qObj.optString("type", qObj.optString("questionType", "MCQ")).uppercase()
                val qType = if (typeRaw.contains("TRUE") || typeRaw.contains("FALSE")) "TRUE_FALSE" else "MCQ"
                val questionText = qObj.optString("question", "").trim()
                if (questionText.isBlank()) continue

                val optionsArr = qObj.optJSONArray("options")
                val optionsList = mutableListOf<String>()
                if (optionsArr != null) {
                    for (j in 0 until optionsArr.length()) {
                        val opt = optionsArr.optString(j, "").trim()
                        if (opt.isNotBlank()) optionsList.add(opt)
                    }
                }

                // Fallbacks for options if not provided
                if (qType == "TRUE_FALSE" && optionsList.size < 2) {
                    optionsList.clear()
                    if (language == "ar") {
                        optionsList.add("صح")
                        optionsList.add("خطأ")
                    } else {
                        optionsList.add("True")
                        optionsList.add("False")
                    }
                } else if (qType == "MCQ" && optionsList.size < 2) {
                    continue
                }

                val correctIdx = qObj.optInt("correctIndex", 0).coerceIn(0, maxOf(0, optionsList.size - 1))
                val explanation = qObj.optString("explanation", "").trim()
                val rawTimestamp = qObj.optLong("timestampMs", -1L).takeIf { it >= 0 }
                val sourceNoteId = qObj.optLong("sourceNoteId", -1L).takeIf { it > 0 }
                val sourceNote = sourceNoteId?.let { notesMap[it] }

                val rawCategory = qObj.optString("category", "").uppercase()
                val category = when {
                    rawCategory.contains("VOCAB") -> "VOCABULARY"
                    rawCategory.contains("COMPREHENSION") -> "COMPREHENSION"
                    source is QuizContentSource.NotebookNotes -> "VOCABULARY"
                    questionText.contains("meaning", ignoreCase = true) ||
                    questionText.contains("means", ignoreCase = true) ||
                    questionText.contains("word", ignoreCase = true) ||
                    questionText.contains("definition", ignoreCase = true) ||
                    questionText.contains("phrase", ignoreCase = true) ||
                    questionText.contains("idiom", ignoreCase = true) ||
                    questionText.contains("معنى") ||
                    questionText.contains("مرادف") ||
                    questionText.contains("كلمة") -> "VOCABULARY"
                    else -> "COMPREHENSION"
                }

                var targetWord = qObj.optString("targetWord", "").trim().takeIf { it.isNotBlank() }
                var meaning = qObj.optString("meaning", "").trim().takeIf { it.isNotBlank() }
                var contextSentence = qObj.optString("contextSentence", "").trim().takeIf { it.isNotBlank() }

                // Fallback resolution for vocabulary fields
                if (category == "VOCABULARY" && targetWord == null) {
                    val quoted = Regex("['\"]([^'\"]+)['\"]").find(questionText)?.groupValues?.get(1)?.trim()
                    if (!quoted.isNullOrBlank() && quoted.length in 2..30) {
                        targetWord = quoted
                    } else if (sourceNote != null) {
                        targetWord = sourceNote.targetWord?.takeIf { it.isNotBlank() } ?: sourceNote.text.trim()
                    }
                }

                if (category == "VOCABULARY" && meaning == null) {
                    meaning = optionsList.getOrNull(correctIdx)
                        ?: sourceNote?.meaning?.takeIf { it.isNotBlank() }
                        ?: explanation.takeIf { it.isNotBlank() }
                }

                if (category == "VOCABULARY" && contextSentence == null) {
                    contextSentence = sourceNote?.contextSentence?.takeIf { it.isNotBlank() }
                        ?: sourceNote?.text?.trim()
                }

                val finalTimestamp = rawTimestamp ?: sourceNote?.startTimestampMs?.takeIf { it > 0 }
                val trackId = sourceNote?.trackId

                resultList.add(
                    UnifiedQuizItem(
                        questionType = qType,
                        category = category,
                        question = questionText,
                        options = optionsList,
                        correctIndex = correctIdx,
                        explanation = explanation,
                        timestampMs = finalTimestamp,
                        sourceNoteId = sourceNoteId,
                        trackId = trackId,
                        targetWord = targetWord,
                        meaning = meaning,
                        contextSentence = contextSentence
                    )
                )
            }

            if (resultList.isEmpty()) {
                val errorMsg = if (source is QuizContentSource.NotebookNotes) {
                    if (language == "ar") "لم يتم العثور على مفردات صالحة لتوليد الأسئلة في الملاحظات المحددة."
                    else "No valid vocabulary items were found in the selected notes."
                } else {
                    if (language == "ar") "لم يقم الذكاء الاصطناعي بتوليد أي أسئلة للمقطع."
                    else "AI did not generate any quiz questions."
                }
                return@withContext Result.failure(Exception(errorMsg))
            }

            Result.success(resultList)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during generateQuizUnified", e)
            val friendlyMsg = when (e) {
                is java.net.SocketTimeoutException -> {
                    if (language == "ar") "انتهت مهلة الاتصال بالذكاء الاصطناعي. يرجى إعادة المحاولة."
                    else "Connection timed out waiting for AI. Please try again."
                }
                is java.net.UnknownHostException -> {
                    if (language == "ar") "تعذر الاتصال بالإنترنت. يرجى التحقق من اتصال الشبكة."
                    else "No internet connection. Please check your network and try again."
                }
                else -> e.message ?: "Failed to generate quiz questions"
            }
            Result.failure(Exception(friendlyMsg))
        }
    }

    suspend fun generateQuizQuestions(
        mediaTitle: String,
        transcriptCues: List<SubtitleCue>,
        existingQuestions: List<String> = emptyList(),
        customApiKey: String? = null,
        language: String = "en",
        modelName: String = DEFAULT_MODEL
    ): Result<List<GeneratedQuizItem>> {
        val source = QuizContentSource.Transcript(
            mediaTitle = mediaTitle,
            cues = transcriptCues,
            existingQuestions = existingQuestions
        )
        return generateQuizUnified(source, customApiKey, language, modelName).map { list ->
            list.map { it.toGeneratedQuizItem() }
        }
    }

    suspend fun generateQuizFromNotebookNotes(
        notes: List<NoteInputForQuiz>,
        maxQuestions: Int = 10,
        customApiKey: String? = null,
        language: String = "en",
        modelName: String = DEFAULT_MODEL
    ): Result<List<GeneratedNoteQuizItem>> {
        val source = QuizContentSource.NotebookNotes(
            notes = notes,
            maxQuestions = maxQuestions
        )
        val defaultId = notes.firstOrNull()?.id ?: 0L
        return generateQuizUnified(source, customApiKey, language, modelName).map { list ->
            list.map { it.toGeneratedNoteQuizItem(defaultId) }
        }
    }

    private fun parseGeminiErrorMessage(code: Int, body: String, language: String): String {
        try {
            if (body.isNotBlank()) {
                val json = JSONObject(body)
                val errorObj = json.optJSONObject("error")
                val msg = errorObj?.optString("message", "") ?: ""
                val status = errorObj?.optString("status", "") ?: ""

                if (code == 503 || status == "UNAVAILABLE" || msg.contains("overloaded", ignoreCase = true) || msg.contains("busy", ignoreCase = true)) {
                    return if (language == "ar") "نموذج الذكاء الاصطناعي مشغول حالياً. يرجى المحاولة مرة أخرى بعد قليل."
                           else "The AI model is currently busy or overloaded. Please try again in a few moments."
                }
                if (code == 429 || status == "RESOURCE_EXHAUSTED" || msg.contains("quota", ignoreCase = true) || msg.contains("exhausted", ignoreCase = true)) {
                    return if (language == "ar") "تم الوصول إلى الحد الأقصى لطلبات الذكاء الاصطناعي مؤقتاً. يرجى الانتظار قليلاً ثم إعادة المحاولة."
                           else "AI quota or rate limit reached. Please wait a moment before trying again."
                }
                if (code == 400 || code == 403 || msg.contains("API_KEY", ignoreCase = true) || msg.contains("API key", ignoreCase = true)) {
                    return if (language == "ar") "مفتاح Gemini API غير صالح أو غير موجود. يرجى التحقق من المفتاح في الإعدادات."
                           else "Gemini API key is invalid or missing. Please check your API key in Settings."
                }
                if (msg.isNotBlank()) {
                    return msg
                }
            }
        } catch (_: Exception) {}

        return when (code) {
            503 -> if (language == "ar") "نموذج الذكاء الاصطناعي مشغول حالياً. يرجى المحاولة بعد قليل." else "The AI model is currently busy. Please try again shortly."
            429 -> if (language == "ar") "تم الوصول إلى الحد الأقصى لطلبات الذكاء الاصطناعي. يرجى الانتظار قليلاً ثم المحاولة." else "AI request rate limit reached. Please wait a moment and try again."
            400, 401, 403 -> if (language == "ar") "يرجى التحقق من صلاحية مفتاح Gemini API في الإعدادات." else "Please verify your Gemini API key in Settings."
            else -> if (language == "ar") "فشل توليد الأسئلة ($code). يرجى المحاولة مرة أخرى." else "Failed to generate questions (HTTP $code). Please try again."
        }
    }

    private fun getMimeTypeForFile(file: File): String {
        return when (file.extension.lowercase()) {
            "mp3" -> "audio/mp3"
            "wav" -> "audio/wav"
            "m4a" -> "audio/m4a"
            "aac" -> "audio/aac"
            "ogg", "oga" -> "audio/ogg"
            "flac" -> "audio/flac"
            "opus" -> "audio/opus"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "3gp" -> "video/3gpp"
            else -> "audio/mpeg"
        }
    }

    private suspend fun uploadFileToGemini(
        file: File,
        mimeType: String,
        apiKey: String,
        onProgressUpdate: ((String) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        val cacheKey = "${file.absolutePath}:${file.lastModified()}"
        uploadedFileCache[cacheKey]?.let { (uri, time) ->
            if (System.currentTimeMillis() - time < 24 * 3600 * 1000L) {
                Log.d(TAG, "Reusing cached Gemini File URI: $uri")
                return@withContext uri
            }
        }

        onProgressUpdate?.invoke("Uploading audio to Gemini server...")
        val initUrl = "https://generativelanguage.googleapis.com/upload/v1beta/files?key=$apiKey"
        val metadataJson = JSONObject().apply {
            put("file", JSONObject().apply {
                put("display_name", file.name)
            })
        }
        val mediaTypeJson = "application/json; charset=utf-8".toMediaType()
        val initRequest = Request.Builder()
            .url(initUrl)
            .addHeader("X-Goog-Upload-Protocol", "resumable")
            .addHeader("X-Goog-Upload-Command", "start")
            .addHeader("X-Goog-Upload-Header-Content-Length", file.length().toString())
            .addHeader("X-Goog-Upload-Header-Content-Type", mimeType)
            .post(metadataJson.toString().toRequestBody(mediaTypeJson))
            .build()

        val initResponse = subtitleOkHttpClient.newCall(initRequest).execute()
        val uploadUrl = initResponse.header("X-Goog-Upload-URL")
            ?: initResponse.header("x-goog-upload-url")
            ?: throw Exception("Failed to get upload URL from Gemini Files API (HTTP ${initResponse.code})")

        val uploadBody = file.asRequestBody(mimeType.toMediaType())
        val uploadRequest = Request.Builder()
            .url(uploadUrl)
            .addHeader("Content-Length", file.length().toString())
            .addHeader("X-Goog-Upload-Offset", "0")
            .addHeader("X-Goog-Upload-Command", "upload, finalize")
            .put(uploadBody)
            .build()

        val uploadResponse = subtitleOkHttpClient.newCall(uploadRequest).execute()
        val respBody = uploadResponse.body?.string() ?: ""
        if (!uploadResponse.isSuccessful) {
            throw Exception("Files API upload failed (HTTP ${uploadResponse.code}): $respBody")
        }
        val respJson = JSONObject(respBody)
        val fileObj = respJson.getJSONObject("file")
        val fileUri = fileObj.getString("uri")
        val fileName = fileObj.optString("name")
        var state = fileObj.optString("state", "ACTIVE")

        // Audio & video files on Gemini Files API may take a few seconds to process
        var attempts = 0
        while (state.equals("PROCESSING", ignoreCase = true) && attempts < 45) {
            delay(2000)
            attempts++
            onProgressUpdate?.invoke("Processing audio on server... (${attempts * 2}s)")
            val checkRequest = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/$fileName?key=$apiKey")
                .get()
                .build()
            val checkResp = subtitleOkHttpClient.newCall(checkRequest).execute()
            if (checkResp.isSuccessful) {
                val checkBody = checkResp.body?.string() ?: ""
                val checkJson = JSONObject(checkBody)
                val updatedFile = checkJson.optJSONObject("file") ?: checkJson
                state = updatedFile.optString("state", "ACTIVE")
                if (state.equals("FAILED", ignoreCase = true)) {
                    throw Exception("Gemini server failed to process uploaded audio file.")
                }
            }
        }

        uploadedFileCache[cacheKey] = Pair(fileUri, System.currentTimeMillis())
        fileUri
    }

    fun cleanSrtOutput(rawText: String): String {
        var text = rawText.trim()
        if (text.startsWith("```srt", ignoreCase = true)) {
            text = text.substring(6).trim()
        } else if (text.startsWith("```")) {
            text = text.substring(3).trim()
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length - 3).trim()
        }
        val trailingFences = text.lastIndexOf("```")
        if (trailingFences != -1 && trailingFences > 20) {
            text = text.substring(0, trailingFences).trim()
        }

        // Find the start of the first cue index line or timestamp
        val firstCueRegex = Regex("""(?m)^\s*1\s*[\r\n]+\s*\d{1,2}:\d{2}:\d{2}""")
        val match = firstCueRegex.find(text)
        if (match != null) {
            text = text.substring(match.range.first).trim()
        } else {
            val timestampRegex = Regex("""(?m)^\s*\d{1,2}:\d{2}:\d{2}[,\.]\d{1,3}\s*-->\s*\d{1,2}:\d{2}:\d{2}""")
            val tsMatch = timestampRegex.find(text)
            if (tsMatch != null) {
                text = text.substring(tsMatch.range.first).trim()
            }
        }
        return text
    }

    private suspend fun executeSubtitleGenerationWithRetry(
        rootJson: JSONObject,
        resolvedApiKey: String,
        preferredModel: String,
        language: String,
        chunkIdx: Int,
        totalChunks: Int,
        accumulatedCuesCount: Int,
        onProgressUpdate: ((String) -> Unit)?
    ): String = withContext(Dispatchers.IO) {
        val maxAttempts = 3
        var currentDelayMs = 2500L
        var activeModel = preferredModel
        var lastException: Exception? = null

        val mediaType = "application/json; charset=utf-8".toMediaType()

        for (attempt in 1..maxAttempts) {
            try {
                // If attempt 1 or 2 failed with 503/timeout, fallback to FALLBACK_MODEL on attempt 3
                if (attempt == 3 && activeModel == DEFAULT_MODEL) {
                    activeModel = FALLBACK_MODEL
                }

                val useStreaming = (attempt <= 2)

                if (useStreaming) {
                    val requestUrl = "$BASE_URL/$activeModel:streamGenerateContent?key=$resolvedApiKey&alt=sse"
                    val requestBody = rootJson.toString().toRequestBody(mediaType)
                    val request = Request.Builder().url(requestUrl).post(requestBody).build()

                    val response = subtitleOkHttpClient.newCall(request).execute()
                    val responseCode = response.code
                    if (!response.isSuccessful) {
                        val errBody = response.body?.string() ?: ""
                        Log.e(TAG, "Gemini Subtitles stream failed (HTTP $responseCode, attempt $attempt): $errBody")
                        throw Exception("HTTP $responseCode: $errBody")
                    }

                    val body = response.body ?: throw Exception("Empty response body from Gemini.")
                    val reader = body.byteStream().bufferedReader(Charsets.UTF_8)
                    val chunkRawText = StringBuilder()
                    var streamedArrowCount = 0

                    reader.useLines { lines ->
                        for (line in lines) {
                            if (line.startsWith("data: ")) {
                                val jsonStr = line.substring(6).trim()
                                if (jsonStr.isNotBlank() && jsonStr != "[DONE]") {
                                    try {
                                        val chunkJson = JSONObject(jsonStr)
                                        val candidates = chunkJson.optJSONArray("candidates")
                                        if (candidates != null && candidates.length() > 0) {
                                            val candidate = candidates.getJSONObject(0)
                                            val content = candidate.optJSONObject("content")
                                            val parts = content?.optJSONArray("parts")
                                            if (parts != null && parts.length() > 0) {
                                                for (i in 0 until parts.length()) {
                                                    val partText = parts.getJSONObject(i).optString("text", "")
                                                    if (partText.isNotEmpty()) {
                                                        chunkRawText.append(partText)
                                                    }
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Error parsing SSE chunk: ${e.message}")
                                    }

                                    val currentTotal = chunkRawText.toString()
                                    val arrowCount = currentTotal.split("-->").size - 1
                                    if (arrowCount > streamedArrowCount) {
                                        streamedArrowCount = arrowCount
                                        val totalLiveCues = accumulatedCuesCount + streamedArrowCount
                                        val updateMsg = if (totalChunks > 1) {
                                            if (language == "ar") {
                                                "مزامنة الجزء (${chunkIdx + 1}/$totalChunks)... ($totalLiveCues مقطعاً)"
                                            } else {
                                                "Syncing chunk (${chunkIdx + 1}/$totalChunks)... ($totalLiveCues cues synced)"
                                            }
                                        } else {
                                            if (language == "ar") {
                                                "جارٍ توليد ومزامنة الترجمة... ($totalLiveCues مقطعاً)"
                                            } else {
                                                "Generating subtitles... ($totalLiveCues cues synced)"
                                            }
                                        }
                                        onProgressUpdate?.invoke(updateMsg)
                                    }
                                }
                            }
                        }
                    }

                    val rawResult = chunkRawText.toString()
                    if (rawResult.isNotBlank() && rawResult.contains("-->")) {
                        return@withContext rawResult
                    } else {
                        throw Exception("Streaming returned incomplete subtitle output.")
                    }
                } else {
                    // Non-streaming generateContent fallback: robust against SSE socket drops on long audio
                    val fallbackMsg = if (language == "ar") "جارٍ إتمام معالجة الترجمة عبر خوادم الذكاء الاصطناعي..."
                    else "Processing subtitles directly via Gemini API..."
                    onProgressUpdate?.invoke(fallbackMsg)

                    val requestUrl = "$BASE_URL/$activeModel:generateContent?key=$resolvedApiKey"
                    val requestBody = rootJson.toString().toRequestBody(mediaType)
                    val request = Request.Builder().url(requestUrl).post(requestBody).build()

                    val response = subtitleOkHttpClient.newCall(request).execute()
                    val responseCode = response.code
                    val responseBody = response.body?.string() ?: ""

                    if (!response.isSuccessful) {
                        Log.e(TAG, "Gemini Subtitles generateContent failed (HTTP $responseCode): $responseBody")
                        throw Exception("HTTP $responseCode: $responseBody")
                    }

                    val respJson = JSONObject(responseBody)
                    val candidates = respJson.optJSONArray("candidates")
                    val candidate = candidates?.optJSONObject(0)
                    val parts = candidate?.optJSONObject("content")?.optJSONArray("parts")
                    val rawResult = parts?.optJSONObject(0)?.optString("text", "") ?: ""

                    if (rawResult.isNotBlank() && rawResult.contains("-->")) {
                        return@withContext rawResult
                    } else {
                        throw Exception("Gemini returned invalid or empty subtitle text.")
                    }
                }
            } catch (e: Exception) {
                lastException = e
                val msg = e.message ?: ""
                val is503 = msg.contains("503") || msg.contains("overloaded", ignoreCase = true) || msg.contains("UNAVAILABLE", ignoreCase = true)
                val is429 = msg.contains("429") || msg.contains("RESOURCE_EXHAUSTED", ignoreCase = true)
                val isTimeout = e is java.net.SocketTimeoutException || msg.contains("timeout", ignoreCase = true) || e.cause is java.net.SocketTimeoutException
                val isConnectionErr = e is java.io.IOException || msg.contains("connection", ignoreCase = true) || msg.contains("stream", ignoreCase = true)

                val canRetry = (is503 || is429 || isTimeout || isConnectionErr) && attempt < maxAttempts

                if (canRetry) {
                    val waitSec = (currentDelayMs / 1000).toInt()
                    val retryNotice = if (language == "ar") {
                        if (is503) "خوادم Gemini مشغولة مؤقتاً (503)، جارٍ إعادة المحاولة تلقائياً خلال $waitSec ثوانٍ ($attempt/$maxAttempts)..."
                        else if (isTimeout) "استغرق الاتصال وقتاً أطول، جارٍ إعادة المحاولة خلال $waitSec ثوانٍ ($attempt/$maxAttempts)..."
                        else "جارٍ إعادة المحاولة تلقائياً خلال $waitSec ثوانٍ ($attempt/$maxAttempts)..."
                    } else {
                        if (is503) "Gemini servers are busy (503), retrying in ${waitSec}s ($attempt/$maxAttempts)..."
                        else if (isTimeout) "Request timed out, retrying in ${waitSec}s ($attempt/$maxAttempts)..."
                        else "Retrying in ${waitSec}s ($attempt/$maxAttempts)..."
                    }
                    onProgressUpdate?.invoke(retryNotice)
                    Log.w(TAG, "Subtitle generation attempt $attempt failed with: $msg. Retrying in ${currentDelayMs}ms...")
                    delay(currentDelayMs)
                    currentDelayMs = (currentDelayMs * 1.8).toLong().coerceAtMost(10000L)
                } else {
                    throw e
                }
            }
        }
        throw lastException ?: Exception("Subtitle generation failed after $maxAttempts attempts.")
    }

    suspend fun generateSubtitles(
        audioFile: File,
        existingSubtitleText: String? = null,
        totalDurationMs: Long = 0L,
        customApiKey: String? = null,
        language: String = "en",
        modelName: String = DEFAULT_MODEL,
        onProgressUpdate: ((String) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val resolvedApiKey = resolveApiKey(customApiKey)
        if (resolvedApiKey.isBlank()) {
            return@withContext Result.failure(Exception("MISSING_API_KEY"))
        }

        if (!audioFile.exists() || !audioFile.canRead()) {
            return@withContext Result.failure(Exception("Audio file not found or cannot be read: ${audioFile.absolutePath}"))
        }

        try {
            val mimeType = getMimeTypeForFile(audioFile)

            // Determine accurate audio duration
            val calculatedDurationMs = if (totalDurationMs > 0) totalDurationMs else getMediaDurationMs(audioFile)
            val durationSeconds = if (calculatedDurationMs > 0) {
                (calculatedDurationMs / 1000).toInt()
            } else {
                val approxSec = ((audioFile.length() / (128 * 1024 / 8))).toInt().coerceIn(60, 7200)
                approxSec
            }

            val mediaPartJson = JSONObject()
            // Files over 2MB or audio longer than 120 seconds use Gemini Files API for reliable upload and server-side processing
            val maxInlineBytes = 2 * 1024 * 1024L // 2MB
            if (audioFile.length() <= maxInlineBytes && durationSeconds <= 120) {
                onProgressUpdate?.invoke(
                    if (language == "ar") "جارٍ قراءة الملف الصوتي..." else "Reading audio data..."
                )
                val bytes = audioFile.readBytes()
                val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
                mediaPartJson.put("inlineData", JSONObject().apply {
                    put("mimeType", mimeType)
                    put("data", base64Data)
                })
            } else {
                val uploadProgressMsg = if (language == "ar") "جارٍ رفع المقطع إلى خوادم Gemini..." else "Uploading audio to Gemini server..."
                onProgressUpdate?.invoke(uploadProgressMsg)
                val fileUri = uploadFileToGemini(audioFile, mimeType, resolvedApiKey, onProgressUpdate)
                mediaPartJson.put("fileData", JSONObject().apply {
                    put("mimeType", mimeType)
                    put("fileUri", fileUri)
                })
            }

            // Sanitize reference text: strip inaccurate existing timestamps to eliminate bias
            val cleanReferenceText = if (!existingSubtitleText.isNullOrBlank()) {
                SubtitleParser.extractCleanTextFromReference(existingSubtitleText)
            } else {
                null
            }

            // Up to 20 minutes (1200 seconds) is processed in a SINGLE pass for continuous accuracy and fast execution!
            // This prevents arbitrary splitting of standard 10-11 minute audios into multiple failing chunks.
            val singlePassMaxSeconds = 1200
            val chunkWindows = mutableListOf<Pair<Int, Int>>()

            if (durationSeconds <= singlePassMaxSeconds) {
                chunkWindows.add(Pair(0, durationSeconds))
            } else {
                val chunkWindowSec = 600 // 10 minutes per chunk for very long podcasts/lectures
                var curStart = 0
                while (curStart < durationSeconds) {
                    val curEnd = (curStart + chunkWindowSec).coerceAtMost(durationSeconds)
                    chunkWindows.add(Pair(curStart, curEnd))
                    if (curEnd >= durationSeconds) break
                    curStart = curEnd
                }
            }

            val totalChunks = chunkWindows.size
            val allAccumulatedCues = mutableListOf<SubtitleCue>()
            var globalCueIndex = 1

            for ((chunkIdx, window) in chunkWindows.withIndex()) {
                val (winStartSec, winEndSec) = window
                val winStartMs = winStartSec * 1000L
                val winEndMs = winEndSec * 1000L

                val chunkProgressMsg = if (totalChunks > 1) {
                    if (language == "ar") {
                        "مزامنة المقطع ${chunkIdx + 1} من $totalChunks [${SubtitleParser.formatShortTimeTag(winStartMs)} - ${SubtitleParser.formatShortTimeTag(winEndMs)}]..."
                    } else {
                        "Syncing chunk ${chunkIdx + 1}/$totalChunks [${SubtitleParser.formatShortTimeTag(winStartMs)} - ${SubtitleParser.formatShortTimeTag(winEndMs)}]..."
                    }
                } else {
                    if (language == "ar") "جارٍ تحليل الصوت وتوليد الترجمة بالذكاء الاصطناعي..."
                    else "Analyzing audio and generating subtitles with AI..."
                }
                onProgressUpdate?.invoke(chunkProgressMsg)

                val (systemInstruction, prompt) = GeminiPrompts.buildSubtitleGenerationPrompt(
                    totalChunks = totalChunks,
                    winStartMs = winStartMs,
                    winEndMs = winEndMs,
                    calculatedDurationMs = calculatedDurationMs,
                    cleanReferenceText = cleanReferenceText,
                    globalCueIndex = globalCueIndex
                )

                val partsArray = JSONArray().apply {
                    put(mediaPartJson)
                    put(JSONObject().apply { put("text", prompt) })
                }

                val rootJson = JSONObject().apply {
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", systemInstruction) })
                        })
                    })
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", partsArray)
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.1)
                        put("maxOutputTokens", 65536)
                    })
                }

                val chunkRawText = executeSubtitleGenerationWithRetry(
                    rootJson = rootJson,
                    resolvedApiKey = resolvedApiKey,
                    preferredModel = modelName,
                    language = language,
                    chunkIdx = chunkIdx,
                    totalChunks = totalChunks,
                    accumulatedCuesCount = allAccumulatedCues.size,
                    onProgressUpdate = onProgressUpdate
                )

                val cleanedChunkSrt = cleanSrtOutput(chunkRawText)
                val parsedChunkCues = SubtitleParser.parseContent(cleanedChunkSrt)
                for (cue in parsedChunkCues) {
                    if (cue.isTimed && cue.startMs >= 0L) {
                        allAccumulatedCues.add(cue.copy(id = globalCueIndex++))
                    }
                }
            }

            if (allAccumulatedCues.isEmpty()) {
                return@withContext Result.failure(Exception("AI did not produce valid subtitle cues."))
            }

            val finalSrt = SubtitleParser.exportCuesToSrt(allAccumulatedCues)
            Result.success(finalSrt)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during generateSubtitles", e)
            val msg = e.message ?: ""
            val isTimeout = e is java.net.SocketTimeoutException ||
                msg.contains("timeout", ignoreCase = true) ||
                e.cause is java.net.SocketTimeoutException
            val is503 = msg.contains("503") || msg.contains("overloaded", ignoreCase = true) || msg.contains("UNAVAILABLE", ignoreCase = true)

            val friendlyError = when {
                is503 -> {
                    if (language == "ar") {
                        "خوادم Gemini مشغولة حالياً (HTTP 503). يرجى الانتظار لحظات ثم إعادة المحاولة."
                    } else {
                        "Gemini servers are currently experiencing high load (HTTP 503). Please wait a moment and try again."
                    }
                }
                isTimeout -> {
                    if (language == "ar") {
                        "استغرق اتصال الشبكة وقتاً أطول من المتوقع، يرجى التحقق من اتصال الإنترنت وإعادة المحاولة."
                    } else {
                        "Network connection timed out while processing audio. Please verify your internet connection and try again."
                    }
                }
                else -> {
                    e.localizedMessage ?: "Unknown error"
                }
            }
            Result.failure(Exception(friendlyError))
        }
    }
}
