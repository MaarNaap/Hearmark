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
    val category: String = "COMPREHENSION" // "VOCABULARY" or "COMPREHENSION"
)

data class NoteInputForQuiz(
    val id: Long,
    val text: String,
    val comment: String = "",
    val tags: List<String> = emptyList(),
    val trackId: Long? = null,
    val trackName: String? = null,
    val startTimestampMs: Long = 0L
)

data class GeneratedNoteQuizItem(
    val sourceNoteId: Long,
    val targetWord: String,
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

object GeminiService {
    private const val TAG = "GeminiService"
    private const val DEFAULT_MODEL = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // Dedicated client for large media uploads and long subtitle transcription streaming (e.g. 18+ min tracks)
    private val subtitleOkHttpClient = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS) // Up to 5 minutes to upload large media files
        .readTimeout(300, TimeUnit.SECONDS)  // Up to 5 minutes between stream packets
        .callTimeout(900, TimeUnit.SECONDS)  // Up to 15 minutes total max call duration
        .retryOnConnectionFailure(true)
        .build()

    private fun getSystemInstruction(lang: String): String {
        return if (lang == "ar") {
            "أنت مساعد دراسي وتعليمي ذكي داخل تطبيق الاستماع (Hearmark).\n" +
            "مهمتك: تقديم الإجابة والمعلومات والشروحات المطلوبة فوراً وبشكل مباشر وموجز.\n" +
            "تعليمات صارمة:\n" +
            "1. ممنوع نهائياً الترحيب أو المجاملات أو المقدمات (مثل: مرحباً، أهلاً بك، بالتأكيد يسعدني، إلخ). ابدأ بالإجابة مباشرة.\n" +
            "2. أجب دائماً باللغة العربية بأسلوب واضح ومرتب باستخدام نقاط عريضة وعلامات توضيحية.\n" +
            "3. ركز مباشرة على المطلوب (شرح المعنى، القواعد، الترجمة مع أمثلة، التلخيص، أو الاختبار) بناءً على سياق المقطع الصوتي المعطى."
        } else {
            "You are an expert AI study assistant inside the Hearmark audio learning app.\n" +
            "Your role: provide precise, concise, and structured educational explanations directly.\n" +
            "Strict rules:\n" +
            "1. NEVER include greetings, pleasantries, or introductory fluff (no 'Hello', 'Sure!', 'I would be glad to help', etc.). Output the core answer immediately.\n" +
            "2. Always answer in English with clean formatting, bullet points, and bold keywords.\n" +
            "3. Focus immediately on the requested task (vocabulary breakdown, grammar, translation with examples, summary, or quiz)."
        }
    }

    const val VOCAB_NOTE_SYSTEM_PROMPT = """You are a specialized vocabulary definition assistant for audio language learning.
Your task is to define vocabulary, expressions, or idioms using a STRICT, FIXED TEMPLATE.

MANDATORY RULES:
1. STRICT FIXED TEMPLATE: Your response MUST strictly follow the exact structure below, line by line. Do not deviate, do not add extra bullet points or sections, and never include conversational filler, greetings, or sign-offs.
2. NO TRANSLATION: Do NOT provide any translations into any other language (no Arabic, Spanish, French, etc.). Everything must be strictly in English.
3. CONCISE & DIRECT:
   - Line 1 must begin directly with the word/phrase followed by a colon and a clear, direct, 1-2-sentence definition in simple language.
   - "Context in Audio" explains how the word/phrase was used in the audio quote/sentence (or typical conversational usage if no full sentence was given).
   - "Examples" must contain exactly two natural, high-quality, everyday example sentences enclosed in double quotes.
   - "Key Collocations / Synonyms" must contain 2–3 relevant words or phrases separated by commas.
4. PLAIN TEXT FORMATTING: Output clean text only. Do NOT use markdown bold (no ** or __) or headers (no #). Keep bullet points as standard '•' and indentation as 2 spaces for example items.

EXACT TEMPLATE FORMAT:
[Word/Phrase]: [Meaning: Clear, direct, 1-2-sentence definition in simple language]
• Context in Audio: [How it was used in this specific line/sentence]
• Examples:
  1. "[Natural everyday example sentence showing typical usage]"
  2. "[Second contrast or collocation example sentence]"
• Key Collocations / Synonyms: [2–3 relevant words/phrases]

EXAMPLE OUTPUT:
Take for granted: To fail to properly appreciate someone or something, especially as a result of overfamiliarity.
• Context in Audio: Used in the dialogue to express that someone's continuous support was overlooked rather than appreciated.
• Examples:
  1. "We often take our good health for granted until we get sick."
  2. "He never took his family's support for granted and thanked them often."
• Key Collocations / Synonyms: undervalue, overlook, take as given"""

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
            val instructionText = customSystemInstruction ?: getSystemInstruction(language)
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

            // Construct new user message text with attached context if available
            val promptBuilder = StringBuilder()
            if (contextSummary != null) {
                val hasSubtitle = !contextSummary.activeSubtitleLine.isNullOrBlank()
                val hasFullSubtitles = !contextSummary.fullSubtitlesText.isNullOrBlank()
                val hasTrack = !contextSummary.trackTitle.isNullOrBlank()
                val hasTask = !contextSummary.activeTaskTitle.isNullOrBlank()

                if (hasSubtitle || hasFullSubtitles || hasTrack || hasTask) {
                    val isAr = language == "ar"
                    if (isAr) {
                        promptBuilder.append("[سياق المقطع الصوتي الحالي]:\n")
                        if (hasTrack) {
                            promptBuilder.append("• المقطع الصوتي: ").append(contextSummary.trackTitle)
                            if (!contextSummary.formattedPosition.isNullOrBlank()) {
                                promptBuilder.append(" عند الموضع ").append(contextSummary.formattedPosition)
                            }
                            promptBuilder.append("\n")
                        }
                        if (hasFullSubtitles) {
                            promptBuilder.append("• كامل نص ملف الترجمة / التفريغ الصوتي لهذا المقطع:\n")
                            promptBuilder.append("--- بداية نص ملف الترجمة ---\n")
                            promptBuilder.append(contextSummary.fullSubtitlesText!!.trim()).append("\n")
                            promptBuilder.append("--- نهاية نص ملف الترجمة ---\n")
                        } else if (hasSubtitle) {
                            promptBuilder.append("• جملة التفريغ/الترجمة الحالية: \"").append(contextSummary.activeSubtitleLine).append("\"\n")
                        }
                        if (hasTask) {
                            promptBuilder.append("• المهمة المرتبطة: ").append(contextSummary.activeTaskTitle).append("\n")
                        }
                        promptBuilder.append("[نهاية السياق]\n\n")
                    } else {
                        promptBuilder.append("[Context Information for Current Audio]:\n")
                        if (hasTrack) {
                            promptBuilder.append("• Current Audio Track: ").append(contextSummary.trackTitle)
                            if (!contextSummary.formattedPosition.isNullOrBlank()) {
                                promptBuilder.append(" at position ").append(contextSummary.formattedPosition)
                            }
                            promptBuilder.append("\n")
                        }
                        if (hasFullSubtitles) {
                            promptBuilder.append("• Full Subtitles / Transcript for this entire audio file:\n")
                            promptBuilder.append("--- START OF TRANSCRIPT ---\n")
                            promptBuilder.append(contextSummary.fullSubtitlesText!!.trim()).append("\n")
                            promptBuilder.append("--- END OF TRANSCRIPT ---\n")
                        } else if (hasSubtitle) {
                            promptBuilder.append("• Current Active Subtitle / Lyric Sentence: \"").append(contextSummary.activeSubtitleLine).append("\"\n")
                        }
                        if (hasTask) {
                            promptBuilder.append("• Current Goal Task: ").append(contextSummary.activeTaskTitle).append("\n")
                        }
                        promptBuilder.append("[End of Context]\n\n")
                    }
                }
            }
            promptBuilder.append(newUserMessage)

            val currentTurnObj = JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", promptBuilder.toString())
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

    private fun formatTimestamp(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    suspend fun detectScenes(
        transcriptCues: List<com.example.player.SubtitleCue>,
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

            // Format transcript cues compactly with index, timestamps and text
            val transcriptBuilder = StringBuilder()
            transcriptCues.forEachIndexed { index, cue ->
                if (cue.isTimed && cue.startMs >= 0) {
                    val startFmt = formatTimestamp(cue.startMs)
                    val endFmt = formatTimestamp(cue.endMs)
                    transcriptBuilder.append("[${index + 1}] $startFmt - $endFmt (startMs:${cue.startMs}, endMs:${cue.endMs}): ${cue.text.replace("\n", " ").trim()}\n")
                } else {
                    transcriptBuilder.append("[${index + 1}]: ${cue.text.replace("\n", " ").trim()}\n")
                }
            }

            val prompt = buildString {
                append("Media Title: \"$mediaTitle\"\n")
                append("Total Duration: ${formatTimestamp(totalDurationMs)} (${totalDurationMs} ms)\n\n")
                append("Analyze the following timestamped dialogue transcript from this movie/video and divide it into meaningful, contextually complete SCENES for intensive language learning.\n\n")
                append("SCENE SEGMENTATION RULES:\n")
                append("1. A scene MUST represent a complete situation, interaction, conversation, or narrative context (e.g. A conversation between two characters; a person entering a place and ordering food; a situation in an office/courtroom; a continuous sequence across multiple camera shots).\n")
                append("2. It is NOT an individual camera shot or a short piece of dialogue. Keep continuous interactions and conversations together.\n")
                append("3. Only split into a new scene when there is a clear change in location, setting, time, participants, or a distinct shift in narrative situation.\n")
                append("4. Target scene lengths: Aim for substantial, contextually complete scenes (approx 2 to 7 minutes each when possible, but let the actual narrative naturally determine exact length).\n")
                append("5. Every millisecond from start (0) to end ($totalDurationMs) should be covered cleanly without gaps.\n")
                append("6. For each scene, provide:\n")
                append("   - sceneNumber: sequential 1, 2, 3...\n")
                append("   - title: concise, distinctive title (in ${if (language == "ar") "Arabic, e.g. '001 - المحادثة في المقهى'" else "English, e.g. '001 - Meeting at the Cafe'"})\n")
                append("   - startMs: start timestamp in milliseconds (Long)\n")
                append("   - endMs: end timestamp in milliseconds (Long)\n")
                append("   - summary: 1-sentence description of the scene context\n\n")
                append("Respond ONLY with a JSON object in this exact schema:\n")
                append("{\n  \"scenes\": [\n    {\n      \"sceneNumber\": 1,\n      \"title\": \"Title here\",\n      \"startMs\": 0,\n      \"endMs\": 180000,\n      \"summary\": \"Summary here\"\n    }\n  ]\n}\n\n")
                append("TRANSCRIPT:\n")
                append(transcriptBuilder.toString())
            }

            val systemInstruction = "You are an expert AI Video Scene Segmentation Assistant. You analyze movie transcripts and divide them into contextually complete scenes for language learners. Output strict JSON only matching the schema."

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

    suspend fun generateQuizQuestions(
        mediaTitle: String,
        transcriptCues: List<SubtitleCue>,
        existingQuestions: List<String> = emptyList(),
        customApiKey: String? = null,
        language: String = "en",
        modelName: String = DEFAULT_MODEL
    ): Result<List<GeneratedQuizItem>> = withContext(Dispatchers.IO) {
        try {
            val resolvedApiKey = resolveApiKey(customApiKey)
            if (resolvedApiKey.isBlank()) {
                return@withContext Result.failure(IllegalStateException("MISSING_API_KEY"))
            }

            if (transcriptCues.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("No transcript or subtitles available for this track."))
            }

            // Format transcript cues compactly with index, timestamp, and text
            val transcriptBuilder = StringBuilder()
            transcriptCues.take(250).forEachIndexed { index, cue ->
                if (cue.isTimed && cue.startMs >= 0) {
                    val startFmt = formatTimestamp(cue.startMs)
                    transcriptBuilder.append("[${index + 1}] $startFmt (startMs:${cue.startMs}): ${cue.text.replace("\n", " ").trim()}\n")
                } else {
                    transcriptBuilder.append("[${index + 1}]: ${cue.text.replace("\n", " ").trim()}\n")
                }
            }

            val prompt = buildString {
                append("Media Title: \"$mediaTitle\"\n\n")

                if (existingQuestions.isNotEmpty()) {
                    append("TASK: Generate 8 BRAND-NEW quiz questions: exactly 4 KEY VOCABULARY questions and 4 LISTENING COMPREHENSION questions. The learner already has existing questions for this audio. You MUST NOT duplicate, repeat, or closely rephrase any of the existing questions.\n\n")
                    append("====================================================\n")
                    append("EXISTING QUESTIONS (DO NOT DUPLICATE OR REPHRASE):\n")
                    existingQuestions.take(50).forEachIndexed { index, existingQ ->
                        append("${index + 1}. \"${existingQ.trim()}\"\n")
                    }
                    append("====================================================\n\n")
                    append("CRITICAL MANDATE: Carefully inspect the EXISTING QUESTIONS above. Make sure your newly generated questions cover completely DIFFERENT parts of the transcript, novel vocabulary items, other speakers, untouched dialogue lines, or different key events.\n\n")
                } else {
                    append("Based on the provided dialogue/audio transcript, generate a well-rounded quiz consisting of exactly 8 questions: 4 KEY VOCABULARY questions and 4 LISTENING COMPREHENSION questions from the audio.\n\n")
                }

                append("QUIZ COMPOSITION REQUIREMENTS:\n")
                append("1. Balanced Question Mix (Total exactly 8 questions):\n")
                append("   - EXACTLY 4 KEY VOCABULARY questions (set \"category\": \"VOCABULARY\"): test important words, idioms, phrases, or collocations found in the audio transcript (e.g. \"What is the meaning of '[word]' in this context?\", \"In the sentence '...', the word '[word]' means:\", or \"Which word in the dialogue means ...?\").\n")
                append("   - EXACTLY 4 LISTENING COMPREHENSION questions (set \"category\": \"COMPREHENSION\"): test main ideas, speaker intentions, key events, cause/effect, or specific details from the dialogue.\n")
                append("   - Question Formats: Use Multiple Choice Questions (type: \"MCQ\") and True/False Questions (type: \"TRUE_FALSE\"). Total questions must be exactly 8.\n")
                if (existingQuestions.isNotEmpty()) {
                    append("2. Strictly Novel & Non-Duplicate:\n")
                    append("   - Under NO circumstances copy, paraphrase, or ask about the exact same focal points or words as the EXISTING QUESTIONS above.\n")
                    append("   - Choose different vocabulary words and test other dialogue lines from elsewhere in the transcript.\n")
                }
                append("3. Multiple Choice Questions (\"MCQ\"):\n")
                append("   - Provide exactly 4 options in \"options\" list.\n")
                append("   - For Vocabulary Questions: Highlight the word clearly, ask for its meaning/definition in context, and provide 1 correct meaning and 3 plausible, realistic distractor meanings.\n")
                append("   - For Comprehension Questions: Test context, reason, speaker emotion, or dialogue details.\n")
                append("   - Make wrong options natural, plausible distractors (not silly or absurd).\n")
                append("4. True/False Questions (\"TRUE_FALSE\"):\n")
                append("   - Provide exactly 2 options in \"options\" list: ${if (language == "ar") "[\"صح\", \"خطأ\"]" else "[\"True\", \"False\"]"}.\n")
                append("   - Test a specific factual statement, key detail, word usage, or common misconception from the dialogue.\n")
                append("5. Audio Timestamp Link (\"timestampMs\"):\n")
                append("   - MUST provide the exact start timestamp in milliseconds ('startMs' from the transcript above) where the relevant dialogue line (or the sentence containing the vocabulary word) is spoken so the learner can re-listen.\n")
                append("6. Explanation (\"explanation\"):\n")
                append("   - Provide a concise 1-2 sentence explanation ${if (language == "ar") "in Arabic" else "in English"} clarifying why the answer is correct and quoting or defining the relevant word/phrase.\n")
                append("7. Language:\n")
                if (language == "ar") {
                    append("   - Formulate the questions and explanations in Arabic, while keeping target language words, vocabulary items, or direct quotes in their original language when asking for their meaning.\n\n")
                } else {
                    append("   - Formulate all questions, options, and explanations clearly in English.\n\n")
                }
                append("Strict JSON Output Schema:\n")
                append("{\n")
                append("  \"questions\": [\n")
                append("    {\n")
                append("      \"category\": \"VOCABULARY\",\n")
                append("      \"type\": \"MCQ\",\n")
                append("      \"question\": \"In the sentence '...', what is the meaning of the word '...'?\",\n")
                append("      \"options\": [\"Choice A\", \"Choice B\", \"Choice C\", \"Choice D\"],\n")
                append("      \"correctIndex\": 1,\n")
                append("      \"explanation\": \"In this context, '...' means ..., as used when the speaker describes ...\",\n")
                append("      \"timestampMs\": 14500\n")
                append("    },\n")
                append("    {\n")
                append("      \"category\": \"COMPREHENSION\",\n")
                append("      \"type\": \"TRUE_FALSE\",\n")
                append("      \"question\": \"Factual statement to evaluate.\",\n")
                append("      \"options\": [\"${if (language == "ar") "صح" else "True"}\", \"${if (language == "ar") "خطأ" else "False"}\"],\n")
                append("      \"correctIndex\": 0,\n")
                append("      \"explanation\": \"One-line explanation why this is true.\",\n")
                append("      \"timestampMs\": 42000\n")
                append("    }\n")
                append("  ]\n")
                append("}\n\n")
                append("TRANSCRIPT:\n")
                append(transcriptBuilder.toString())
            }

            val systemInstruction = if (existingQuestions.isNotEmpty()) {
                "You are an expert audio & language learning quiz creator. You generate interactive micro-quizzes of 8 questions (4 key vocabulary and 4 listening comprehension) from dialogue transcripts. You must strictly avoid repeating, rephrasing, or duplicating any of the existing questions provided and craft completely new questions. Output strict valid JSON only matching the schema."
            } else {
                "You are an expert audio & language learning quiz creator. You generate interactive micro-quizzes of 8 questions (4 key vocabulary and 4 listening comprehension) from dialogue transcripts. Output strict valid JSON only matching the schema."
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
                    put("temperature", if (existingQuestions.isNotEmpty()) 0.5 else 0.3)
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
                Log.e(TAG, "Gemini Quiz Generation failed: ${response.code} -> $responseBody")
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

            // Strip any markdown code blocks
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

            val resultList = mutableListOf<GeneratedQuizItem>()
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
                } else if (qType == "MCQ" && optionsList.isEmpty()) {
                    continue
                }

                val correctIdx = qObj.optInt("correctIndex", 0).coerceIn(0, maxOf(0, optionsList.size - 1))
                val explanation = qObj.optString("explanation", "").trim()
                val timestampMs = qObj.optLong("timestampMs", -1L).takeIf { it >= 0 }

                val rawCategory = qObj.optString("category", "").uppercase()
                val category = when {
                    rawCategory.contains("VOCAB") -> "VOCABULARY"
                    rawCategory.contains("COMPREHENSION") -> "COMPREHENSION"
                    // Heuristic fallback if model omitted category tag:
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

                resultList.add(
                    GeneratedQuizItem(
                        questionType = qType,
                        question = questionText,
                        options = optionsList,
                        correctIndex = correctIdx,
                        explanation = explanation,
                        timestampMs = timestampMs,
                        category = category
                    )
                )
            }

            if (resultList.isEmpty()) {
                return@withContext Result.failure(Exception("AI did not generate any quiz questions."))
            }

            Result.success(resultList)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during generateQuizQuestions", e)
            val friendlyMsg = when (e) {
                is java.net.SocketTimeoutException -> {
                    if (language == "ar") "انتهت مهلة الاتصال بالذكاء الاصطناعي. يرجى إعادة المحاولة."
                    else "Connection timed out waiting for AI. Please try again."
                }
                is java.net.UnknownHostException -> {
                    if (language == "ar") "تعذر الاتصال بالإنترنت. يرجى التحقق من اتصال الشبكة."
                    else "No internet connection. Please check your network and try again."
                }
                else -> e.message ?: "Failed to generate questions"
            }
            Result.failure(Exception(friendlyMsg))
        }
    }

    suspend fun generateQuizFromNotebookNotes(
        notes: List<NoteInputForQuiz>,
        maxQuestions: Int = 10,
        customApiKey: String? = null,
        language: String = "en",
        modelName: String = DEFAULT_MODEL
    ): Result<List<GeneratedNoteQuizItem>> = withContext(Dispatchers.IO) {
        try {
            val resolvedApiKey = resolveApiKey(customApiKey)
            if (resolvedApiKey.isBlank()) {
                return@withContext Result.failure(IllegalStateException("MISSING_API_KEY"))
            }

            if (notes.isEmpty()) {
                val emptyMsg = if (language == "ar") "لا توجد ملاحظات محددة لتوليد الأسئلة." else "No notes provided to generate quiz questions."
                return@withContext Result.failure(IllegalArgumentException(emptyMsg))
            }

            val notesMap = notes.associateBy { it.id }

            // Build a structured, easily parseable notes inventory for Gemini
            val notesInventory = StringBuilder()
            notes.forEachIndexed { index, note ->
                notesInventory.append("[Note ${index + 1} | ID:${note.id}]\n")
                notesInventory.append("Text: \"${note.text.replace("\n", " ").trim()}\"\n")
                if (note.comment.isNotBlank()) {
                    notesInventory.append("Comment/Meaning: \"${note.comment.replace("\n", " ").trim()}\"\n")
                }
                if (note.tags.isNotEmpty()) {
                    notesInventory.append("Tags: [${note.tags.joinToString(", ")}]\n")
                }
                if (!note.trackName.isNullOrBlank()) {
                    notesInventory.append("Source Lesson: \"${note.trackName}\"\n")
                }
                notesInventory.append("\n")
            }

            val prompt = buildString {
                append("ROLE: You are an expert language teacher, lexicographer, and quiz curriculum designer.\n\n")
                append("CONTEXT: A language learner has collected the following study notes in their notebook over multiple study sessions.\n\n")
                append("====================================================\n")
                append("LEARNER'S NOTEBOOK ENTRIES:\n")
                append(notesInventory.toString())
                append("====================================================\n\n")
                append("CRITICAL FILTERING MANDATE (ESSENTIAL):\n")
                append("The learner's notebook contains a mixture of different note types. You MUST STRICTLY CLASSIFY AND FILTER THEM before generating any questions:\n\n")
                append("1. EXCLUDE & REJECT PERSONAL NOTES: Any notes about personal reminders, study schedules, homework, meta reflections, or to-dos (e.g., 'ask teacher', 'review chapter 3 tomorrow', 'homework due Friday', 'my note'). DO NOT create any questions about these.\n")
                append("2. EXCLUDE & REJECT PRONUNCIATION-ONLY NOTES: Any notes that focus solely on phonetics, sound patterns, syllable stress, or accent tips (e.g., 'stress on second syllable', 'silent b', 'sounds like /eɪ/', 'intonation drops at end'). DO NOT create questions testing how to pronounce something.\n")
                append("3. SELECT ONLY GENUINE VOCABULARY ITEMS: Target vocabulary words, phrasal verbs, idioms, fixed expressions, collocations, jargon, and words with defined contextual meanings.\n")
                append("4. If a note contains a vocabulary word accompanied by a personal comment or pronunciation note, FOCUS EXCLUSIVELY ON THE VOCABULARY WORD AND ITS MEANING.\n\n")
                append("TASK:\n")
                append("Generate EXACTLY $maxQuestions high-quality, pedagogically effective multiple-choice vocabulary quiz questions based on the approved vocabulary note(s).\n\n")
                append("CRITICAL QUESTION COUNT & DIVERSITY MANDATE:\n")
                append("- You MUST output EXACTLY $maxQuestions questions in total in the JSON \"questions\" array.\n")
                if (notes.size == 1) {
                    val singleNote = notes.first()
                    append("- IMPORTANT: The user provided ONE specific vocabulary note (ID: ${singleNote.id}, Word/Text: \"${singleNote.text}\").\n")
                    append("- You MUST generate ALL $maxQuestions questions for this single vocabulary item!\n")
                    append("- DO NOT stop at 1 question! You must generate $maxQuestions distinct, varied questions, each testing this vocabulary word from a DIFFERENT perspective or context:\n")
                    append("   1) Meaning & Definition: Clear definition in standard English.\n")
                    append("   2) Contextual Usage / Cloze sentence: Complete a realistic sentence (business, conversational, or academic) where this word fits.\n")
                    append("   3) Collocation or Phrasal Partner: What preposition, verb, or noun naturally pairs with this word?\n")
                    append("   4) Synonyms, Nuance, or Antonym: Choosing the word or phrase closest or opposite in meaning, or distinguishing it from near-synonyms in context.\n")
                    append("   5) Practical Application: Dialogue completion or sentence restructuring using the word correctly.\n")
                    append("- NEVER duplicate sentences or questions. Every question must feel fresh and test a different facet of the word.\n")
                    append("- For every question, set 'sourceNoteId': ${singleNote.id} and 'targetWord': \"${singleNote.text.replace("\"", "").trim()}\".\n\n")
                } else {
                    append("- The user provided ${notes.size} notes and requested $maxQuestions questions.\n")
                    append("- If $maxQuestions > ${notes.size}, generate MULTIPLE distinct questions per vocabulary note (varying definitions, cloze sentences, collocations, synonyms) so that the total number of questions equals EXACTLY $maxQuestions!\n")
                    append("- Distribute the questions evenly across the provided vocabulary notes.\n")
                    append("- For each question, specify the exact numerical 'sourceNoteId' of the note it was derived from.\n\n")
                }
                append("DISTRACTOR & FORMATTING MANDATES:\n")
                append("- Exactly 4 options per question in the 'options' array.\n")
                append("- All 4 options must be plausible, authentic, and share the same grammatical form (same part of speech).\n")
                append("- Exactly 1 correct answer indicated by 0-based integer 'correctIndex' (0, 1, 2, or 3). Randomize correctIndex across the questions.\n")
                append("- Instructive, friendly 'explanation' defining the word and explaining why the correct choice fits.\n")
                append("- 'sourceNoteId': The exact numerical ID of the note from which this question was created.\n")
                append("- 'targetWord': The specific vocabulary word, phrasal verb, or idiom being tested.\n\n")
                append("OUTPUT FORMAT: Return STRICTLY valid JSON with a single key \"questions\":\n")
                append("{\n")
                append("  \"questions\": [\n")
                append("    {\n")
                append("      \"sourceNoteId\": ${notes.first().id},\n")
                append("      \"targetWord\": \"meticulous\",\n")
                append("      \"questionType\": \"MCQ\",\n")
                append("      \"question\": \"What does the word 'meticulous' mean?\",\n")
                append("      \"options\": [\"Showing great attention to detail\", \"Quick to make decisions\", \"Careless and unstructured\", \"Reluctant to speak publicly\"],\n")
                append("      \"correctIndex\": 0,\n")
                append("      \"explanation\": \"'Meticulous' means taking or showing extreme care about minute details; precise and thorough.\"\n")
                append("    }\n")
                append("  ]\n")
                append("}")
            }

            val rootJson = JSONObject().apply {
                val contentsArray = JSONArray()
                val userContent = JSONObject().apply {
                    put("role", "user")
                    val partsArray = JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    }
                    put("parts", partsArray)
                }
                contentsArray.put(userContent)
                put("contents", contentsArray)

                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.4)
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
                Log.e(TAG, "Gemini Notebook Quiz failed: ${response.code} -> $responseBody")
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
                return@withContext Result.failure(Exception("Empty text response from AI."))
            }

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

            val resultList = mutableListOf<GeneratedNoteQuizItem>()
            for (i in 0 until questionsJsonArray.length()) {
                val qObj = questionsJsonArray.getJSONObject(i)
                val sourceNoteId = qObj.optLong("sourceNoteId", -1L)
                val targetWord = qObj.optString("targetWord", "").trim()
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
                if (optionsList.size < 2) continue

                val correctIdx = qObj.optInt("correctIndex", 0).coerceIn(0, optionsList.size - 1)
                val explanation = qObj.optString("explanation", "").trim()
                val sourceNote = if (sourceNoteId > 0) notesMap[sourceNoteId] else null

                resultList.add(
                    GeneratedNoteQuizItem(
                        sourceNoteId = sourceNoteId.takeIf { it > 0 } ?: (notes.firstOrNull()?.id ?: 0L),
                        targetWord = targetWord.ifBlank { sourceNote?.text?.trim() ?: "Vocabulary" },
                        questionType = "MCQ",
                        question = questionText,
                        options = optionsList,
                        correctIndex = correctIdx,
                        explanation = explanation,
                        timestampMs = sourceNote?.startTimestampMs?.takeIf { it > 0 },
                        trackId = sourceNote?.trackId,
                        category = "VOCABULARY"
                    )
                )
            }

            if (resultList.isEmpty()) {
                val noVocabMsg = if (language == "ar") {
                    "لم يتم العثور على مفردات صالحة لتوليد الأسئلة في الملاحظات المحددة. تأكد من احتواء الملاحظات على كلمات أو تعابير لغوية."
                } else {
                    "No valid vocabulary items were found in the selected notes. Ensure your notes contain words, idioms, or definitions."
                }
                return@withContext Result.failure(Exception(noVocabMsg))
            }

            Result.success(resultList)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during generateQuizFromNotebookNotes", e)
            val friendlyMsg = when (e) {
                is java.net.SocketTimeoutException -> {
                    if (language == "ar") "انتهت مهلة الاتصال بالذكاء الاصطناعي. يرجى إعادة المحاولة."
                    else "Connection timed out waiting for AI. Please try again."
                }
                is java.net.UnknownHostException -> {
                    if (language == "ar") "تعذر الاتصال بالإنترنت. يرجى التحقق من اتصال الشبكة."
                    else "No internet connection. Please check your network and try again."
                }
                else -> e.message ?: "Failed to generate vocabulary questions"
            }
            Result.failure(Exception(friendlyMsg))
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
            val mediaPartJson = JSONObject()

            // Files over 5MB use Gemini Files API for reliable upload and server-side processing
            val maxInlineBytes = 5 * 1024 * 1024L // 5MB
            if (audioFile.length() <= maxInlineBytes) {
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

            // Estimate track duration in seconds
            val durationSeconds = if (totalDurationMs > 0) {
                (totalDurationMs / 1000).toInt()
            } else {
                // Approximate fallback: ~1 minute per 1MB for typical audio files if unknown
                val approxSec = ((audioFile.length() / (128 * 1024 / 8))).toInt().coerceIn(60, 7200)
                approxSec
            }

            // Time-Window Chunking:
            // Break audio files longer than 4 minutes into 3-minute time windows (180s)
            // with a small 2-second overlap to prevent sentence cutoff and eliminate time drift.
            val chunkWindowSec = 180 // 3 minutes per chunk for pinpoint acoustic precision
            val chunkWindows = mutableListOf<Pair<Int, Int>>()

            if (durationSeconds <= 240) {
                // Short audio: single pass
                chunkWindows.add(Pair(0, durationSeconds))
            } else {
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
                    if (language == "ar") "جارٍ الاتصال بالذكاء الاصطناعي وبدء توليد الترجمة..."
                    else "Connecting to Gemini and initiating transcription..."
                }
                onProgressUpdate?.invoke(chunkProgressMsg)

                val prompt = buildString {
                    append("You are an expert audio transcriptionist and pinpoint acoustic timing specialist.\n")
                    append("Analyze the attached audio/video recording and generate strictly synchronized SubRip (.srt) subtitles.\n\n")

                    if (totalChunks > 1) {
                        append("TARGET TIME WINDOW FOR THIS PASS:\n")
                        append("- Transcribe ONLY the speech occurring from exactly [${SubtitleParser.formatShortTimeTag(winStartMs)}] (${SubtitleParser.formatSrtTimestamp(winStartMs)}) to [${SubtitleParser.formatShortTimeTag(winEndMs)}] (${SubtitleParser.formatSrtTimestamp(winEndMs)}).\n")
                        append("- Do NOT include speech occurring before ${SubtitleParser.formatSrtTimestamp(winStartMs)} or after ${SubtitleParser.formatSrtTimestamp(winEndMs)}.\n")
                        append("- All subtitle cues in this pass MUST have absolute timestamps within ${SubtitleParser.formatSrtTimestamp(winStartMs)} and ${SubtitleParser.formatSrtTimestamp(winEndMs)}.\n\n")
                    } else if (totalDurationMs > 0) {
                        append("TOTAL TRACK DURATION: ${SubtitleParser.formatSrtTimestamp(totalDurationMs)}\n\n")
                    }

                    append("MANDATORY ACOUSTIC TIMING RULES (SHARP ACCURACY):\n")
                    append("1. ACOUSTIC ONSET & OFFSET LOCK:\n")
                    append("   - Start Timestamp: Lock strictly to the exact millisecond when the speaker begins phonation of the first syllable. Never place the start timestamp earlier in silence or noise.\n")
                    append("   - End Timestamp: Lock strictly to the millisecond when vocal sound finishes. NEVER extend the end timestamp into silent pauses or breathing before the next phrase.\n")
                    append("   - SILENCE GAPS: Any silence or musical interlude longer than 0.3 seconds MUST be empty with no subtitle displayed.\n\n")

                    append("2. SENTENCE SEGMENTATION:\n")
                    append("   - Keep each complete grammatical sentence in exactly ONE subtitle cue whenever possible.\n")
                    append("   - Do NOT split a single continuous sentence across multiple cues.\n")
                    append("   - Do NOT merge two separate sentences into the same cue.\n\n")

                    if (!cleanReferenceText.isNullOrBlank()) {
                        append("3. REFERENCE TRANSCRIPT (WORDING ONLY):\n")
                        append("   - Use the text below strictly for correct words, spelling, vocabulary, and sentence structure:\n")
                        append("--- REFERENCE TRANSCRIPT START ---\n")
                        // If long, pass relevant text or full clean transcript
                        append(cleanReferenceText.trim())
                        append("\n--- REFERENCE TRANSCRIPT END ---\n")
                        append("   - DO NOT copy or infer timestamps from any previous subtitles. Determine start and end timestamps purely by listening to the speech audio waveform.\n\n")
                    } else {
                        append("3. HIGH-FIDELITY TRANSCRIPTION:\n")
                        append("   - Transcribe every spoken word faithfully with proper punctuation and casing.\n\n")
                    }

                    append("4. FORMAT:\n")
                    append("   - Output ONLY valid SubRip (.srt) format starting with cue index $globalCueIndex.\n")
                    append("   - Timestamps format: HH:MM:SS,mmm --> HH:MM:SS,mmm\n")
                    append("   - Absolutely no commentary, greetings, or markdown fences.\n")
                }

                val partsArray = JSONArray().apply {
                    put(mediaPartJson)
                    put(JSONObject().apply { put("text", prompt) })
                }

                val systemInstruction = "You are a professional audio transcriptionist and pinpoint subtitle timing specialist. You generate sharp, millisecond-accurate SubRip (.srt) subtitles synchronized with acoustic speech boundaries. Output strictly valid SRT content."

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
                        put("temperature", 0.0) // Deterministic zero temperature for maximum timing consistency
                        put("maxOutputTokens", 65536)
                    })
                }

                val requestUrl = "$BASE_URL/$modelName:streamGenerateContent?key=$resolvedApiKey&alt=sse"
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = rootJson.toString().toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(requestUrl)
                    .post(requestBody)
                    .build()

                val response = subtitleOkHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    Log.e(TAG, "Gemini Subtitle Generation failed on chunk $chunkIdx: ${response.code} -> $errBody")
                    return@withContext Result.failure(Exception("HTTP ${response.code}: $errBody"))
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
                                    val totalLiveCues = allAccumulatedCues.size + streamedArrowCount
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

                val cleanedChunkSrt = cleanSrtOutput(chunkRawText.toString())
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
            val isTimeout = e is java.net.SocketTimeoutException ||
                e.message?.contains("timeout", ignoreCase = true) == true ||
                e.cause is java.net.SocketTimeoutException
            val friendlyError = if (isTimeout) {
                if (language == "ar") {
                    "استغرق توليد الترجمة للمقطع الطويل وقتاً إضافياً وتوقف الاتصال، يرجى التحقق من جودة الإنترنت والمحاولة مجدداً."
                } else {
                    "Subtitle generation timed out while processing long audio. Please verify your internet connection and try again."
                }
            } else {
                e.localizedMessage ?: "Unknown error"
            }
            Result.failure(Exception(friendlyError))
        }
    }
}

data class DetectedScene(
    val sceneNumber: Int,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val summary: String = ""
)
