package com.example.ai

import android.util.Log
import com.example.data.StoryOptions
import com.example.data.StoryTargetWord
import com.example.data.VocabularyStory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object GeminiStory {
    private const val TAG = "GeminiStory"

    /**
     * Checks if a target vocabulary word or expression is present in the generated story.
     * Supports exact match, word-boundary match, and common inflectional suffixes.
     */
    fun isWordInStory(targetWord: String, storyText: String): Boolean {
        val cleanTarget = targetWord.trim()
        if (cleanTarget.isBlank()) return true

        // Direct case-insensitive substring
        if (storyText.contains(cleanTarget, ignoreCase = true)) return true

        // Word-boundary match
        val escaped = Regex.escape(cleanTarget)
        if (Regex("""(?i)\b$escaped\b""").containsMatchIn(storyText)) return true

        // Common inflectional suffixes for single words (e.g. walk -> walks, walked, walking)
        if (!cleanTarget.contains(" ") && cleanTarget.length >= 3) {
            val flexRegex = Regex("""(?i)\b${Regex.escape(cleanTarget)}(s|es|ed|d|ing|ly)?\b""")
            if (flexRegex.containsMatchIn(storyText)) return true

            val stem = cleanTarget.removeSuffix("e").removeSuffix("y")
            if (stem.length >= 3) {
                val stemRegex = Regex("""(?i)\b${Regex.escape(stem)}(ies|ied|ing|ed|es|s)?\b""")
                if (stemRegex.containsMatchIn(storyText)) return true
            }
        }
        return false
    }

    private fun buildStoryGenerationPrompt(
        targetWords: List<StoryTargetWord>,
        options: StoryOptions
    ): Pair<String, String> {
        val lengthCategory = StoryOptions.wordCountToLengthCategory(options.targetWordCount)
        val lengthGuide = "Approximately ${options.targetWordCount} words ($lengthCategory length category). Ensure the narrative is well-paced and approximately ${options.targetWordCount} words in length without abrupt truncation."

        val levelGuide = when (options.level.uppercase()) {
            "BEGINNER" -> "Beginner (CEFR A1-A2). Use simple sentence structures, clear everyday language, and straightforward storytelling."
            "ADVANCED" -> "Advanced (CEFR C1-C2). Use rich narrative prose, nuanced phrasing, varied sentence lengths, and evocative descriptions."
            else -> "Intermediate (CEFR B1-B2). Use natural, engaging storytelling with varied phrasing and clear, flowing narrative."
        }

        val genreGuide = when (options.genre.uppercase()) {
            "MYSTERY" -> "Mystery / Detective / Puzzle solving"
            "ADVENTURE" -> "Adventure / Exploration / Exciting journey"
            "SCI_FI" -> "Science Fiction / Futuristic / Technological wonder"
            "HUMOROUS" -> "Humorous / Lighthearted / Witty and fun"
            "INSPIRATIONAL" -> "Inspirational / Overcoming obstacles / Heartwarming"
            "HISTORICAL" -> "Historical / Classic period / Cultural heritage"
            else -> "Slice of Life / Everyday human experience / Warm and relatable"
        }

        val systemInstruction = """You are a master creative writer and English language educator.
Your task is to write a cohesive, delightful, and natural story that seamlessly weaves in a specific list of target vocabulary words.
Each vocabulary word must appear naturally in context, without feeling forced.
Always output strictly a valid JSON object."""

        val promptBuilder = StringBuilder()
        promptBuilder.appendLine("Please write a story based on the following target vocabulary and criteria:")
        promptBuilder.appendLine()
        promptBuilder.appendLine("TARGET VOCABULARY WORDS:")
        targetWords.forEachIndexed { index, item ->
            val defPart = if (item.meaning.isNotBlank()) " | Meaning: ${item.meaning}" else ""
            val ctxPart = if (item.contextSentence.isNotBlank()) " | Original Context: ${item.contextSentence}" else ""
            promptBuilder.appendLine("${index + 1}. \"${item.word}\"$defPart$ctxPart")
        }
        promptBuilder.appendLine()
        promptBuilder.appendLine("STORY SPECIFICATIONS:")
        promptBuilder.appendLine("- Genre/Tone: $genreGuide")
        promptBuilder.appendLine("- Language Level: $levelGuide")
        promptBuilder.appendLine("- Target Length: $lengthGuide")
        promptBuilder.appendLine()
        promptBuilder.appendLine("STRICT RULES:")
        promptBuilder.appendLine("1. Every single target vocabulary word above MUST appear in the story text (either exact word or natural grammatical inflection).")
        promptBuilder.appendLine("2. The narrative must make sense, be engaging, and feel like a real story rather than a dry vocabulary exercise.")
        promptBuilder.appendLine("3. Output strictly a JSON object with keys 'title' and 'story'. Do NOT add introductory or concluding text outside the JSON.")
        promptBuilder.appendLine()
        promptBuilder.appendLine("JSON FORMAT:")
        promptBuilder.appendLine("{")
        promptBuilder.appendLine("  \"title\": \"Catchy & evocative title\",")
        promptBuilder.appendLine("  \"story\": \"Full story text here...\"")
        promptBuilder.appendLine("}")

        return Pair(systemInstruction, promptBuilder.toString())
    }

    private fun buildRetryPrompt(
        targetWords: List<StoryTargetWord>,
        missingWords: List<StoryTargetWord>,
        previousTitle: String,
        previousStory: String,
        options: StoryOptions
    ): String {
        return """You wrote this story titled "$previousTitle":
\"\"\"
$previousStory
\"\"\"

However, the following target vocabulary words were MISSING or not found in the story:
${missingWords.joinToString("\n") { "- \"${it.word}\"" + (if (it.meaning.isNotBlank()) " (${it.meaning})" else "") }}

Please rewrite and revise the story so that ALL of the target vocabulary words are explicitly and naturally included:
${targetWords.joinToString(", ") { "\"${it.word}\"" }}

Keep the genre (${options.genre}), language level (${options.level}), and target length (approximately ${options.targetWordCount} words).
Output strictly valid JSON:
{
  \"title\": \"Creative Title\",
  \"story\": \"Revised full story text incorporating all required words...\"
}"""
    }

    private fun parseStoryJson(rawResponse: String): Pair<String, String>? {
        return try {
            val cleaned = GeminiHttp.cleanJsonText(rawResponse)
            val json = if (cleaned.startsWith("{")) {
                JSONObject(cleaned)
            } else {
                val f = cleaned.indexOf('{')
                val l = cleaned.lastIndexOf('}')
                if (f != -1 && l > f) JSONObject(cleaned.substring(f, l + 1)) else null
            } ?: return null

            val title = json.optString("title", "").trim().ifBlank { "A Vocabulary Story" }
            val story = json.optString("story", "").trim()
            if (story.isBlank()) null else Pair(title, story)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse story JSON", e)
            null
        }
    }

    suspend fun generateVocabularyStory(
        targetWords: List<StoryTargetWord>,
        options: StoryOptions,
        customApiKey: String? = null,
        language: String = "en",
        modelName: String = GeminiModelHealth.DEFAULT_MODEL,
        onProgressUpdate: ((String) -> Unit)? = null
    ): Result<VocabularyStory> = withContext(Dispatchers.IO) {
        if (targetWords.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No vocabulary words selected for story."))
        }

        try {
            onProgressUpdate?.invoke(
                if (language == "ar") "جارٍ صياغة القصة بواسطة الذكاء الاصطناعي..."
                else "Crafting story with Gemini AI..."
            )

            val (systemInstruction, prompt) = buildStoryGenerationPrompt(targetWords, options)
            val candidateKeys = GeminiKeyPool.resolveCandidateApiKeys(customApiKey)

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
                    put("temperature", 0.75)
                    put("maxOutputTokens", 8192)
                    put("thinkingConfig", GeminiHttp.buildFastThinkingConfig())
                })
            }

            val (code, responseBody) = GeminiHttp.executeGeminiPostWithRetry(
                urlBuilder = { m -> "${GeminiModelHealth.BASE_URL}/$m:generateContent" },
                candidateApiKeys = candidateKeys,
                payload = rootJson,
                primaryModel = modelName,
                fallbackModel = GeminiModelHealth.FALLBACK_MODEL,
                maxAttempts = 5,
                client = GeminiHttp.okHttpClient,
                language = language,
                onProgressUpdate = onProgressUpdate
            )

            if (code !in 200..299) {
                val cleanErrorMsg = GeminiHttp.parseGeminiErrorMessage(code, responseBody, language)
                return@withContext Result.failure(Exception(cleanErrorMsg))
            }

            val respJson = JSONObject(responseBody)
            val candidateCheck = GeminiHttp.checkCandidateBlockOrFinishReason(respJson, language)
            if (candidateCheck.blockedErrorMessage != null) {
                return@withContext Result.failure(Exception(candidateCheck.blockedErrorMessage))
            }

            val candidates = respJson.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val parts = firstCandidate?.optJSONObject("content")?.optJSONArray("parts")
            val rawStoryText = GeminiHttp.extractNonThoughtTextFromParts(parts)

            var parsed = parseStoryJson(rawStoryText)
            if (parsed == null) {
                return@withContext Result.failure(Exception("Could not parse story format from AI."))
            }

            var (currentTitle, currentStory) = parsed

            // Verify if any target words are missing
            var missingWords = targetWords.filter { !isWordInStory(it.word, currentStory) }

            // RETRY ONCE if there are missing words
            if (missingWords.isNotEmpty()) {
                Log.d(TAG, "Missing ${missingWords.size} words on first pass. Retrying once...")
                onProgressUpdate?.invoke(
                    if (language == "ar") "جارٍ تحسين القصة لتضمين كافة المفردات المطلوبة..."
                    else "Refining story to include all target words..."
                )

                val retryPrompt = buildRetryPrompt(
                    targetWords = targetWords,
                    missingWords = missingWords,
                    previousTitle = currentTitle,
                    previousStory = currentStory,
                    options = options
                )

                val retryPayload = JSONObject().apply {
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", systemInstruction) })
                        })
                    })
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply { put("text", retryPrompt) })
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("responseMimeType", "application/json")
                        put("temperature", 0.6)
                        put("maxOutputTokens", 8192)
                        put("thinkingConfig", GeminiHttp.buildFastThinkingConfig())
                    })
                }

                val (retryCode, retryBody) = GeminiHttp.executeGeminiPostWithRetry(
                    urlBuilder = { m -> "${GeminiModelHealth.BASE_URL}/$m:generateContent" },
                    candidateApiKeys = candidateKeys,
                    payload = retryPayload,
                    primaryModel = modelName,
                    fallbackModel = GeminiModelHealth.FALLBACK_MODEL,
                    maxAttempts = 4,
                    client = GeminiHttp.okHttpClient,
                    language = language
                )

                if (retryCode in 200..299) {
                    val retryJson = JSONObject(retryBody)
                    val retryCandidates = retryJson.optJSONArray("candidates")
                    val retryParts = retryCandidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                    val retryRawText = GeminiHttp.extractNonThoughtTextFromParts(retryParts)
                    val retryParsed = parseStoryJson(retryRawText)
                    if (retryParsed != null) {
                        currentTitle = retryParsed.first
                        currentStory = retryParsed.second
                        missingWords = targetWords.filter { !isWordInStory(it.word, currentStory) }
                    }
                }
            }

            val finalMissingWords = missingWords.map { it.word }
            val wordsJsonArray = JSONArray().apply {
                targetWords.forEach { put(it.toJson()) }
            }

            val storyEntity = VocabularyStory(
                title = currentTitle,
                storyText = currentStory,
                targetWordsJson = wordsJsonArray.toString(),
                optionsJson = options.toJson().toString(),
                missingWordsJson = if (finalMissingWords.isNotEmpty()) JSONArray(finalMissingWords).toString() else null,
                audioFilePath = null,
                createdAt = System.currentTimeMillis()
            )

            Result.success(storyEntity)
        } catch (e: Exception) {
            Log.e(TAG, "Story generation failed", e)
            val msg = if (language == "ar") {
                "فشل توليد القصة: ${e.message ?: "خطأ غير متوقع"}"
            } else {
                "Story generation failed: ${e.message ?: "Unexpected error"}"
            }
            Result.failure(Exception(msg))
        }
    }
}
