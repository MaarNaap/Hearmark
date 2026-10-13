package com.example.ai

import android.util.Log
import com.example.player.SubtitleCue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object GeminiQuiz {
    private const val TAG = "GeminiService"

    internal fun parseQuizQuestionsFromResponse(
        responseBody: String,
        source: QuizContentSource,
        notesMap: Map<Long, NoteInputForQuiz>,
        language: String = "en"
    ): Result<List<UnifiedQuizItem>> {
        val respJson = JSONObject(responseBody)
        val candidateCheck = GeminiHttp.checkCandidateBlockOrFinishReason(respJson, language)
        if (candidateCheck.blockedErrorMessage != null) {
            return Result.failure(Exception(candidateCheck.blockedErrorMessage))
        }

        val candidates = respJson.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            val noCandMsg = if (language == "ar") "لم يُرجع الذكاء الاصطناعي أي إجابة." else "No candidate returned by Gemini."
            return Result.failure(Exception(noCandMsg))
        }

        val firstCandidate = candidates.getJSONObject(0)
        val content = firstCandidate.optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        val rawText = GeminiHttp.extractNonThoughtTextFromParts(parts)

        if (rawText.isBlank()) {
            val emptyMsg = if (language == "ar") "استجابة نصية فارغة من الذكاء الاصطناعي للاختبار." else "Empty text response for quiz."
            return Result.failure(Exception(emptyMsg))
        }

        val cleanedJson = GeminiHttp.cleanJsonText(rawText)
        val questionsJsonArray: JSONArray = try {
            if (cleanedJson.startsWith("{")) {
                val jsonObj = JSONObject(cleanedJson)
                jsonObj.optJSONArray("questions") ?: JSONArray()
            } else if (cleanedJson.startsWith("[")) {
                JSONArray(cleanedJson)
            } else {
                val fBrace = cleanedJson.indexOf('{')
                val lBrace = cleanedJson.lastIndexOf('}')
                if (fBrace != -1 && lBrace > fBrace) {
                    val jsonObj = JSONObject(cleanedJson.substring(fBrace, lBrace + 1))
                    jsonObj.optJSONArray("questions") ?: JSONArray()
                } else {
                    val badFmtMsg = if (language == "ar") "تنسيق استجابة غير متوقع من الذكاء الاصطناعي." else "Unexpected response format from AI."
                    return Result.failure(Exception(badFmtMsg))
                }
            }
        } catch (e: Exception) {
            if (candidateCheck.isTruncatedByMaxTokens) {
                val truncMsg = if (language == "ar") {
                    "تم اقتطاع أسئلة الاختبار بسبب تجاوز الحد الأقصى لطول الاستجابة. يرجى المحاولة على جزء أقصر."
                } else {
                    "Quiz response was truncated due to token limit. Please try again with a smaller selection."
                }
                return Result.failure(Exception(truncMsg))
            }
            val badFmtMsg = if (language == "ar") "تنسيق استجابة غير متوقع من الذكاء الاصطناعي." else "Unexpected response format from AI."
            return Result.failure(Exception(badFmtMsg))
        }

        val resultList = mutableListOf<UnifiedQuizItem>()
        for (i in 0 until questionsJsonArray.length()) {
            val qObj = questionsJsonArray.optJSONObject(i) ?: continue
            val typeRaw = qObj.optString("type", qObj.optString("questionType", "MCQ")).uppercase()
            val qType = if (typeRaw.contains("TRUE") || typeRaw.contains("FALSE")) "TRUE_FALSE" else "MCQ"
            val questionText = qObj.optString("question", "").trim()
            if (questionText.isBlank()) continue

            val optionsArr = qObj.optJSONArray("options")
            val rawOptionsList = mutableListOf<String>()
            if (optionsArr != null) {
                for (j in 0 until optionsArr.length()) {
                    val opt = optionsArr.optString(j, "").trim()
                    if (opt.isNotBlank()) rawOptionsList.add(opt)
                }
            }
            val optionsList = rawOptionsList.distinctBy { it.lowercase() }.toMutableList()

            // Fallbacks for TRUE_FALSE options if not provided; strict validation for MCQ
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

            // Explicitly validate correctIndex instead of silently defaulting out-of-range/missing indices to 0
            if (!qObj.has("correctIndex") || qObj.isNull("correctIndex")) {
                continue
            }
            val rawCorrectIdx = qObj.optInt("correctIndex", -1)
            if (rawCorrectIdx !in 0 until optionsList.size) {
                continue
            }
            val correctIdx = rawCorrectIdx

            val explanation = qObj.optString("explanation", "").trim()
            val rawTimestamp = qObj.optLong("timestampMs", -1L).takeIf { it >= 0 }
            val sourceNoteId = qObj.optLong("sourceNoteId", -1L).takeIf { it > 0 }
            val sourceNote = sourceNoteId?.let { notesMap[it] }

            val rawCategory = qObj.optString("category", "").uppercase()
            val category = when {
                source is QuizContentSource.Transcript && source.focusMode == QuizFocusMode.VOCABULARY_ONLY -> "VOCABULARY"
                source is QuizContentSource.Transcript && source.focusMode == QuizFocusMode.COMPREHENSION_ONLY -> "COMPREHENSION"
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

            val sanitizedQuestion = if (category == "VOCABULARY") {
                QuizSanitizer.sanitizeVocabularyQuestion(questionText, targetWord, contextSentence)
            } else {
                questionText
            }

            if (category == "VOCABULARY" && contextSentence == null) {
                val extractedFromQuestion = Regex(
                    """(?:in the sentence:\s*|في الجملة:\s*)["'“]([^"'”]+)["'”]""",
                    RegexOption.IGNORE_CASE
                ).find(sanitizedQuestion)?.groupValues?.get(1)?.trim()
                    ?: Regex(
                        """(?:in the sentence:\s*|في الجملة:\s*)["'“]([^"'”]+)["'”]""",
                        RegexOption.IGNORE_CASE
                    ).find(questionText)?.groupValues?.get(1)?.trim()
                contextSentence = extractedFromQuestion?.takeIf { it.isNotBlank() }
                    ?: sourceNote?.contextSentence?.takeIf { it.isNotBlank() }
                    ?: sourceNote?.text?.trim()
            }

            val sanitizedExplanation = if (category == "VOCABULARY") {
                QuizSanitizer.sanitizeExplanation(explanation)
            } else {
                explanation
            }

            val finalTimestamp = rawTimestamp ?: sourceNote?.startTimestampMs?.takeIf { it > 0 }
            val trackId = sourceNote?.trackId

            resultList.add(
                UnifiedQuizItem(
                    questionType = qType,
                    category = category,
                    question = sanitizedQuestion,
                    options = optionsList,
                    correctIndex = correctIdx,
                    explanation = sanitizedExplanation,
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
                if (language == "ar") "لم يقم الذكاء الاصطناعي بتوليد أي أسئلة صالحة للمقطع."
                else "AI did not generate any valid quiz questions."
            }
            return Result.failure(Exception(errorMsg))
        }

        return Result.success(resultList)
    }

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
                        val emptyTranscriptMsg = if (language == "ar") {
                            "لا يتوفر نص أو ترجمة لهذا المقطع."
                        } else {
                            "No transcript or subtitles available for this track."
                        }
                        return@withContext Result.failure(IllegalArgumentException(emptyTranscriptMsg))
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
                urlBuilder = { m -> "${GeminiModelHealth.BASE_URL}/$m:generateContent" },
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

            parseQuizQuestionsFromResponse(
                responseBody = responseBody,
                source = source,
                notesMap = notesMap,
                language = language
            )
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
                else -> e.message ?: if (language == "ar") "فشل توليد أسئلة الاختبار" else "Failed to generate quiz questions"
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
        modelName: String = GeminiModelHealth.DEFAULT_MODEL,
        focusMode: QuizFocusMode = QuizFocusMode.BOTH
    ): Result<List<GeneratedQuizItem>> {
        val source = QuizContentSource.Transcript(
            mediaTitle = mediaTitle,
            cues = transcriptCues,
            existingQuestions = existingQuestions,
            focusMode = focusMode
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

object QuizSanitizer {
    private val DIALOGUE_PREFIX_REGEX = Regex(
        """^(?:in this (?:dialogue|conversation|audio|episode|recording|transcript|lesson|clip|interview|text|article)|in the (?:dialogue|conversation|audio|episode|recording|transcript|lesson|clip|interview)|according to the (?:speaker|audio|dialogue|recording|transcript|lesson)|based on the (?:audio|dialogue|recording|transcript|lesson)|as mentioned in the (?:audio|dialogue|recording|lesson|website)|as stated by the speaker)[,\s]+""",
        RegexOption.IGNORE_CASE
    )

    private val ARABIC_DIALOGUE_PREFIX_REGEX = Regex(
        """^(?:في هذا (?:الحوار|المقطع|التسجيل|الدرس|المحادثة|المحتوى)|في هذه (?:المحادثة|الحلقة)|وفقاً للمتحدث|بحسب المتحدث|في النص المسموع|بناءً على الحوار|في الموقع المذكور)[،,\s]+"""
    )

    private val DIALOGUE_PHRASE_REGEX = Regex(
        """\b(?:in this (?:dialogue|conversation|audio|episode|recording|transcript|lesson|clip)|in the (?:dialogue|conversation|audio|episode|recording|transcript|clip)|according to the speaker|as used by the speaker|as mentioned on the website|in the website mentioned)\b""",
        RegexOption.IGNORE_CASE
    )

    private val ARABIC_DIALOGUE_PHRASE_REGEX = Regex(
        """(?:في هذا (?:الحوار|المقطع|التسجيل|الدرس|المحادثة)|في هذه المحادثة|وفقاً للمتحدث|بحسب المتحدث|في الموقع المذكور)"""
    )

    private val EXPLANATION_PREFIX_REGEX = Regex(
        """^(?:in this (?:context|dialogue|conversation|audio|episode|recording|transcript|lesson)|in the (?:context|dialogue|conversation|audio|episode|recording|transcript|lesson)|according to the (?:speaker|audio|dialogue|recording|transcript))[,\s]+""",
        RegexOption.IGNORE_CASE
    )

    private val ARABIC_EXPLANATION_PREFIX_REGEX = Regex(
        """^(?:في هذا (?:السياق|الحوار|المقطع|التسجيل|الدرس|المحادثة)|في هذه (?:المحادثة|الحلقة)|وفقاً للمتحدث|بحسب المتحدث)[،,\s]+"""
    )

    private val EXPLANATION_CLEAN_REGEX = Regex(
        """[,\s]*(?:as used when the speaker describes|as mentioned by the speaker|as stated by the speaker|as said in the (?:dialogue|audio|conversation|lesson))\b.*$""",
        RegexOption.IGNORE_CASE
    )

    private val ARABIC_EXPLANATION_CLEAN_REGEX = Regex(
        """[،,\s]*(?:كما ذكر المتحدث|كما ورد في الحوار|وفقاً لما قاله المتحدث).*$"""
    )

    private fun capitalizeFirstLetter(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return trimmed
        val firstChar = trimmed[0]
        return if ((firstChar == '\'' || firstChar == '"' || firstChar == '“' || firstChar == '‘') && trimmed.length > 1) {
            "$firstChar${trimmed[1].uppercaseChar()}${trimmed.substring(2)}"
        } else {
            trimmed.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    fun sanitizeVocabularyQuestion(
        rawQuestion: String,
        targetWord: String?,
        contextSentence: String?
    ): String {
        var q = rawQuestion.trim()
        if (q.isBlank()) return q

        // 1. Remove leading dialogue / speaker prefixes
        if (DIALOGUE_PREFIX_REGEX.containsMatchIn(q)) {
            q = q.replace(DIALOGUE_PREFIX_REGEX, "").trim()
            if (q.isNotEmpty()) {
                q = capitalizeFirstLetter(q)
            }
        }
        if (ARABIC_DIALOGUE_PREFIX_REGEX.containsMatchIn(q)) {
            q = q.replace(ARABIC_DIALOGUE_PREFIX_REGEX, "").trim()
        }

        // 2. Remove mid-sentence / trailing references
        q = q.replace(DIALOGUE_PHRASE_REGEX, "").trim()
        q = q.replace(ARABIC_DIALOGUE_PHRASE_REGEX, "").trim()

        // 3. Clean up spaces and punctuation artifacts
        q = q.replace(Regex("""\s+"""), " ")
            .replace(" ,", ",")
            .replace(" ?", "?")
            .replace(" .", ".")
            .replace(" ؛", "؛")
            .replace(" ؟", "؟")
            .trim()

        // 4. Handle dangling "in this context" or "here" when question has no embedded sentence
        val hasEmbeddedSentence = q.contains("In the sentence", ignoreCase = true) ||
                q.contains("في الجملة", ignoreCase = true) ||
                (contextSentence != null && contextSentence.isNotBlank() && q.contains(contextSentence))

        val wordToUse = targetWord?.trim()?.ifBlank { null }
        if (!hasEmbeddedSentence && !contextSentence.isNullOrBlank() && wordToUse != null) {
            val asksContextSpecifically = q.contains("in this context", ignoreCase = true) ||
                    q.contains("في هذا السياق", ignoreCase = true) ||
                    q.contains("in context", ignoreCase = true) ||
                    q.contains("here?", ignoreCase = true)
            if (asksContextSpecifically) {
                val isArabic = q.any { it in '\u0600'..'\u06FF' }
                q = if (isArabic) {
                    "في الجملة: \"${contextSentence.trim()}\"، ما معنى كلمة \"$wordToUse\"؟"
                } else {
                    "In the sentence: \"${contextSentence.trim()}\", what does the word \"$wordToUse\" mean?"
                }
            }
        }

        return q
    }

    fun sanitizeExplanation(rawExplanation: String): String {
        var exp = rawExplanation.trim()
        if (exp.isBlank()) return exp

        // 1. Remove leading prefix like "In this context, ", "In this dialogue, "
        if (EXPLANATION_PREFIX_REGEX.containsMatchIn(exp)) {
            exp = exp.replace(EXPLANATION_PREFIX_REGEX, "").trim()
            if (exp.isNotEmpty()) {
                exp = capitalizeFirstLetter(exp)
            }
        }
        if (ARABIC_EXPLANATION_PREFIX_REGEX.containsMatchIn(exp)) {
            exp = exp.replace(ARABIC_EXPLANATION_PREFIX_REGEX, "").trim()
        }

        // 2. Remove trailing speaker/dialogue clauses
        exp = exp.replace(EXPLANATION_CLEAN_REGEX, "").trim()
        exp = exp.replace(ARABIC_EXPLANATION_CLEAN_REGEX, "").trim()
        exp = exp.replace(DIALOGUE_PHRASE_REGEX, "").trim()
        exp = exp.replace(ARABIC_DIALOGUE_PHRASE_REGEX, "").trim()
        exp = exp.replace(Regex("""\s+"""), " ")
            .replace(" ,", ",")
            .replace(" .", ".")
            .trim()

        if (exp.isNotEmpty() && !exp.endsWith(".") && !exp.endsWith("!") && !exp.endsWith("?") && !exp.endsWith("؟")) {
            exp += "."
        }
        return exp
    }
}

