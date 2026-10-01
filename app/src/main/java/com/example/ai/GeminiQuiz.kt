package com.example.ai

import android.util.Log
import com.example.player.SubtitleCue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object GeminiQuiz {
    private const val TAG = "GeminiService"

    suspend fun generateQuizUnified(
        source: QuizContentSource,
        customApiKey: String? = null,
        language: String = "en",
        modelName: String = GeminiModelHealth.FALLBACK_MODEL
    ): Result<List<UnifiedQuizItem>> = withContext(Dispatchers.IO) {
        try {
            val resolvedApiKey = GeminiKeyPool.resolveApiKey(customApiKey)
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
                    put("maxOutputTokens", 16384)
                    put("thinkingConfig", GeminiHttp.buildFastThinkingConfig())
                })
            }

            val candidateKeys = GeminiKeyPool.resolveCandidateApiKeys(customApiKey)

            val (code, responseBody) = GeminiHttp.executeGeminiPostWithRetry(
                urlBuilder = { m -> "${GeminiModelHealth.BASE_URL}/$m:generateContent?key=$resolvedApiKey" },
                urlWithKeyBuilder = { m, k -> "${GeminiModelHealth.BASE_URL}/$m:generateContent?key=$k" },
                candidateApiKeys = candidateKeys,
                payload = rootJson,
                primaryModel = modelName,
                fallbackModel = GeminiModelHealth.FALLBACK_MODEL,
                maxAttempts = 6,
                client = GeminiHttp.okHttpClient,
                language = language
            )

            if (code !in 200..299) {
                Log.e(TAG, "Gemini Unified Quiz Generation failed: $code -> $responseBody")
                val cleanErrorMsg = GeminiHttp.parseGeminiErrorMessage(code, responseBody, language)
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
            val rawText = GeminiHttp.extractNonThoughtTextFromParts(parts)

            if (rawText.isBlank()) {
                return@withContext Result.failure(Exception("Empty text response for quiz."))
            }

            val cleanedJson = GeminiHttp.cleanJsonText(rawText)
            val questionsJsonArray: JSONArray = if (cleanedJson.startsWith("{")) {
                val jsonObj = JSONObject(cleanedJson)
                jsonObj.optJSONArray("questions") ?: JSONArray()
            } else if (cleanedJson.startsWith("[")) {
                JSONArray(cleanedJson)
            } else {
                val fBrace = cleanedJson.indexOf('{')
                val lBrace = cleanedJson.lastIndexOf('}')
                if (fBrace != -1 && lBrace > fBrace) {
                    try {
                        val jsonObj = JSONObject(cleanedJson.substring(fBrace, lBrace + 1))
                        jsonObj.optJSONArray("questions") ?: JSONArray()
                    } catch (_: Exception) {
                        JSONArray()
                    }
                } else {
                    return@withContext Result.failure(Exception("Unexpected response format from AI."))
                }
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
        modelName: String = GeminiModelHealth.DEFAULT_MODEL
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
        modelName: String = GeminiModelHealth.DEFAULT_MODEL
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
}
