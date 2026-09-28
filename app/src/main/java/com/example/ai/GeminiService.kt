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
    const val DEFAULT_MODEL = "gemini-3.5-flash"
    const val FALLBACK_MODEL = "gemini-flash-latest"
    const val LITE_FALLBACK_MODEL = "gemini-3.1-flash-lite-preview"
    const val PRO_FALLBACK_MODEL = "gemini-3.1-pro-preview"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    val MODEL_FALLBACK_CHAIN = listOf(
        DEFAULT_MODEL,
        FALLBACK_MODEL,
        LITE_FALLBACK_MODEL,
        PRO_FALLBACK_MODEL
    )

    const val VOCAB_NOTE_SYSTEM_PROMPT = GeminiPrompts.VOCAB_NOTE_SYSTEM_PROMPT

    private val uploadedFileCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long>>()

    // Smart Model Health & Cooldown Registry
    private val modelCooldownUntilMs = java.util.concurrent.ConcurrentHashMap<String, Long>()
    @Volatile private var lastHealthyModel: String? = null
    @Volatile private var lastHealthyModelTimestampMs: Long = 0L

    // Multi-API Key Failover Pool (synced from Settings)
    @Volatile private var configuredBackupApiKeys: List<String> = emptyList()

    // Request Pacing to avoid burst 503/429 rejections
    private val pacingLock = kotlinx.coroutines.sync.Mutex()
    @Volatile private var lastRequestTimestampMs: Long = 0L
    private const val MIN_REQUEST_INTERVAL_MS = 350L

    fun setConfiguredApiKeys(primaryKey: String?, allKeys: List<String>) {
        val cleaned = mutableListOf<String>()
        if (!primaryKey.isNullOrBlank()) cleaned.add(primaryKey.trim())
        for (k in allKeys) {
            val trimmed = k.trim()
            if (trimmed.isNotBlank() && trimmed !in cleaned) {
                cleaned.add(trimmed)
            }
        }
        configuredBackupApiKeys = cleaned
    }

    fun resolveCandidateApiKeys(customApiKey: String?): List<String> {
        val result = mutableListOf<String>()
        val primary = resolveApiKey(customApiKey)
        if (primary.isNotBlank()) result.add(primary)
        for (k in configuredBackupApiKeys) {
            if (k.isNotBlank() && k !in result) {
                result.add(k)
            }
        }
        val buildConfigKey = try {
            if (BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY") {
                BuildConfig.GEMINI_API_KEY.trim()
            } else ""
        } catch (_: Exception) { "" }
        if (buildConfigKey.isNotBlank() && buildConfigKey !in result) {
            result.add(buildConfigKey)
        }
        return result
    }

    internal fun selectOrderedCandidateModels(
        preferredModel: String = DEFAULT_MODEL,
        nowMs: Long = System.currentTimeMillis()
    ): List<String> {
        val baseOrder = mutableListOf<String>()
        val recentHealthy = lastHealthyModel
        if (
            preferredModel == DEFAULT_MODEL &&
            !recentHealthy.isNullOrBlank() &&
            (nowMs - lastHealthyModelTimestampMs) < 10 * 60 * 1000L
        ) {
            baseOrder.add(recentHealthy)
        }
        if (preferredModel !in baseOrder) {
            baseOrder.add(preferredModel)
        }
        for (m in MODEL_FALLBACK_CHAIN) {
            if (m !in baseOrder) baseOrder.add(m)
        }

        val healthy = mutableListOf<String>()
        val coolingDown = mutableListOf<Pair<String, Long>>()

        for (m in baseOrder) {
            val cooldownUntil = modelCooldownUntilMs[m] ?: 0L
            if (nowMs >= cooldownUntil) {
                healthy.add(m)
            } else {
                coolingDown.add(m to cooldownUntil)
            }
        }

        coolingDown.sortBy { it.second }
        return healthy + coolingDown.map { it.first }
    }

    internal fun markModelSuccess(model: String, nowMs: Long = System.currentTimeMillis()) {
        modelCooldownUntilMs.remove(model)
        lastHealthyModel = model
        lastHealthyModelTimestampMs = nowMs
    }

    internal fun markModelFailure(model: String, httpCode: Int, nowMs: Long = System.currentTimeMillis()) {
        val cooldownDurationMs = when {
            httpCode == 404 || httpCode == 400 -> 30 * 60 * 1000L // 30 minutes for missing/unsupported model
            httpCode == 503 || httpCode == 429 || httpCode in 500..599 -> 60 * 1000L // 60 seconds for overloaded/rate-limited pool
            else -> 30 * 1000L
        }
        modelCooldownUntilMs[model] = nowMs + cooldownDurationMs
        if (lastHealthyModel == model) {
            lastHealthyModel = null
        }
    }

    internal fun clearModelHealthStateForTesting() {
        modelCooldownUntilMs.clear()
        lastHealthyModel = null
        lastHealthyModelTimestampMs = 0L
    }

    private suspend fun paceRequestIfNeeded() {
        pacingLock.lock()
        try {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestTimestampMs
            if (elapsed in 0 until MIN_REQUEST_INTERVAL_MS) {
                delay(MIN_REQUEST_INTERVAL_MS - elapsed)
            }
            lastRequestTimestampMs = System.currentTimeMillis()
        } finally {
            pacingLock.unlock()
        }
    }

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

    fun extractAudioFromVideoIfPossible(videoFile: File): File {
        val ext = videoFile.extension.lowercase()
        val isVideo = ext in listOf("mp4", "mkv", "mov", "webm", "3gp", "avi", "m4v")
        if (!isVideo) return videoFile

        // If file is smaller than 12MB, uploading directly is already very fast
        if (videoFile.length() < 12 * 1024 * 1024L) {
            return videoFile
        }

        val cacheDir = videoFile.parentFile ?: File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        val audioOut = File(cacheDir, "extracted_${videoFile.nameWithoutExtension}_${videoFile.lastModified()}.m4a")
        if (audioOut.exists() && audioOut.length() > 1024) {
            Log.d(TAG, "Reusing already extracted audio: ${audioOut.absolutePath}")
            return audioOut
        }

        var extractor: android.media.MediaExtractor? = null
        var muxer: android.media.MediaMuxer? = null
        try {
            extractor = android.media.MediaExtractor()
            extractor.setDataSource(videoFile.absolutePath)
            var audioTrackIdx = -1
            var format: android.media.MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIdx = i
                    format = f
                    break
                }
            }

            if (audioTrackIdx != -1 && format != null) {
                extractor.selectTrack(audioTrackIdx)
                muxer = android.media.MediaMuxer(
                    audioOut.absolutePath,
                    android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                )
                val muxerTrackIdx = muxer.addTrack(format)
                muxer.start()

                val maxBuf = if (format.containsKey(android.media.MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    format.getInteger(android.media.MediaFormat.KEY_MAX_INPUT_SIZE)
                } else {
                    256 * 1024
                }
                val buf = java.nio.ByteBuffer.allocate(maxBuf.coerceAtLeast(64 * 1024))
                val bufInfo = android.media.MediaCodec.BufferInfo()

                while (true) {
                    bufInfo.offset = 0
                    bufInfo.size = extractor.readSampleData(buf, 0)
                    if (bufInfo.size < 0) break
                    bufInfo.presentationTimeUs = extractor.sampleTime
                    bufInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(muxerTrackIdx, buf, bufInfo)
                    extractor.advance()
                }

                muxer.stop()
                muxer.release()
                muxer = null
                extractor.release()
                extractor = null

                if (audioOut.exists() && audioOut.length() > 1024) {
                    Log.i(TAG, "Successfully demuxed audio from large video: original=${videoFile.length()} bytes -> audio=${audioOut.length()} bytes")
                    return audioOut
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct audio extraction from video failed or not supported, using original file: ${e.message}")
            try { audioOut.delete() } catch (_: Exception) {}
        } finally {
            try { extractor?.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
        return videoFile
    }

    suspend fun executeGeminiPostWithRetry(
        urlBuilder: (model: String) -> String,
        payload: JSONObject,
        primaryModel: String = DEFAULT_MODEL,
        fallbackModel: String = FALLBACK_MODEL,
        maxAttempts: Int = 5,
        client: OkHttpClient = okHttpClient,
        language: String = "en",
        candidateApiKeys: List<String> = emptyList(),
        urlWithKeyBuilder: ((model: String, apiKey: String) -> String)? = null,
        onProgressUpdate: ((String) -> Unit)? = null
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBodyString = payload.toString()
        val orderedModels = selectOrderedCandidateModels(primaryModel)
        val keysPool = candidateApiKeys.filter { it.isNotBlank() }

        var lastCode = -1
        var lastBody = ""
        var lastException: Exception? = null
        var baseDelayMs = 1800L

        for (attempt in 1..maxAttempts) {
            val currentModel = orderedModels[(attempt - 1) % orderedModels.size]
            val currentKey = if (keysPool.isNotEmpty()) {
                keysPool[(attempt - 1) % keysPool.size]
            } else ""

            try {
                paceRequestIfNeeded()

                val url = if (urlWithKeyBuilder != null && currentKey.isNotBlank()) {
                    urlWithKeyBuilder(currentModel, currentKey)
                } else {
                    urlBuilder(currentModel)
                }
                val body = requestBodyString.toRequestBody(mediaType)
                val request = Request.Builder().url(url).post(body).build()

                val response = client.newCall(request).execute()
                lastCode = response.code
                lastBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    markModelSuccess(currentModel)
                    return@withContext Pair(lastCode, lastBody)
                }

                val is503 = lastCode == 503 || lastBody.contains("overloaded", ignoreCase = true) || lastBody.contains("UNAVAILABLE", ignoreCase = true)
                val is429 = lastCode == 429 || lastBody.contains("RESOURCE_EXHAUSTED", ignoreCase = true) || lastBody.contains("quota", ignoreCase = true)
                val is404 = lastCode == 404 || lastBody.contains("NOT_FOUND", ignoreCase = true)
                val is5xx = lastCode in 500..599

                markModelFailure(currentModel, lastCode)
                Log.w(TAG, "Gemini POST failed (HTTP $lastCode, model $currentModel, attempt $attempt/$maxAttempts): $lastBody")

                if (attempt < maxAttempts && (is503 || is429 || is404 || is5xx)) {
                    val nextModel = orderedModels[attempt % orderedModels.size]
                    val retryAfterSec = response.header("Retry-After")?.toLongOrNull()
                    val jitterMs = kotlin.random.Random.nextLong(200L, 750L)
                    val waitMs = if (retryAfterSec != null && retryAfterSec in 1..15) {
                        retryAfterSec * 1000L + jitterMs
                    } else if (is404) {
                        350L // Fast switch when model endpoint is 404
                    } else {
                        (baseDelayMs + jitterMs).coerceAtMost(9000L)
                    }

                    val retryMsg = if (language == "ar") {
                        "خادم الذكاء الاصطناعي مشغول مؤقتاً، جارٍ التبديل للنموذج البديل ($nextModel) وإعادة المحاولة (${attempt + 1}/$maxAttempts)..."
                    } else {
                        "AI server busy (HTTP $lastCode), switching to $nextModel and retrying (${attempt + 1}/$maxAttempts)..."
                    }
                    onProgressUpdate?.invoke(retryMsg)
                    delay(waitMs)
                    if (!is404) {
                        baseDelayMs = (baseDelayMs * 1.75).toLong().coerceAtMost(8500L)
                    }
                    continue
                } else {
                    return@withContext Pair(lastCode, lastBody)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastException = e
                markModelFailure(currentModel, 503)
                Log.w(TAG, "Gemini POST exception (model $currentModel, attempt $attempt/$maxAttempts): ${e.message}")
                if (attempt < maxAttempts) {
                    val jitterMs = kotlin.random.Random.nextLong(200L, 700L)
                    val waitMs = (baseDelayMs + jitterMs).coerceAtMost(9000L)
                    val retryMsg = if (language == "ar") {
                        "جارٍ إعادة المحاولة تلقائياً عبر نموذج بديل (${attempt + 1}/$maxAttempts)..."
                    } else {
                        "Connection interrupted, retrying with backup model (${attempt + 1}/$maxAttempts)..."
                    }
                    onProgressUpdate?.invoke(retryMsg)
                    delay(waitMs)
                    baseDelayMs = (baseDelayMs * 1.75).toLong().coerceAtMost(8500L)
                } else {
                    throw e
                }
            }
        }
        if (lastException != null) throw lastException
        Pair(lastCode, lastBody)
    }

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
            val isVocabNoteRequest = customSystemInstruction == VOCAB_NOTE_SYSTEM_PROMPT
            val genConfig = JSONObject().apply {
                put("temperature", if (isVocabNoteRequest) 0.3 else 0.7)
                put("topP", 0.95)
                put("topK", 40)
                // Right-size output token reservation to prevent heavy-queue 503 rejections
                put("maxOutputTokens", if (isVocabNoteRequest) 1024 else 4096)
            }
            rootJson.put("generationConfig", genConfig)

            // Build multi-turn contents (limit prior turns to recent 12 messages to keep token payload lean)
            val contentsArray = JSONArray()
            val recentHistory = history.filter { !it.isError && it.text.isNotBlank() }.takeLast(12)

            for (msg in recentHistory) {
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

            val candidateKeys = resolveCandidateApiKeys(customApiKey)

            val (code, responseBody) = executeGeminiPostWithRetry(
                urlBuilder = { m -> "$BASE_URL/$m:generateContent?key=$resolvedApiKey" },
                urlWithKeyBuilder = { m, k -> "$BASE_URL/$m:generateContent?key=$k" },
                candidateApiKeys = candidateKeys,
                payload = rootJson,
                primaryModel = modelName,
                fallbackModel = FALLBACK_MODEL,
                maxAttempts = 5,
                client = okHttpClient,
                language = language
            )

            if (code !in 200..299) {
                Log.e(TAG, "Gemini API call failed with code: $code, body: $responseBody")
                val cleanErrorMsg = parseGeminiErrorMessage(code, responseBody, language)
                return@withContext Result.failure(Exception(cleanErrorMsg))
            }

            val respJson = JSONObject(responseBody)
            val candidates = respJson.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return@withContext Result.failure(Exception("No response candidate returned by Gemini."))
            }

            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val replyBuilder = StringBuilder()
            if (parts != null) {
                for (p in 0 until parts.length()) {
                    replyBuilder.append(parts.getJSONObject(p).optString("text", ""))
                }
            }
            val textResult = replyBuilder.toString()

            if (textResult.isBlank()) {
                return@withContext Result.failure(Exception("Empty text in Gemini response."))
            }

            Result.success(textResult)
        } catch (e: Exception) {
            Log.e(TAG, "Error in Gemini sendMessage", e)
            Result.failure(e)
        }
    }

    fun cleanJsonText(rawText: String): String {
        var text = rawText.trim()
        if (text.startsWith("```json", ignoreCase = true)) {
            text = text.substring(7).trim()
        } else if (text.startsWith("```")) {
            text = text.substring(3).trim()
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length - 3).trim()
        }
        val trailingFence = text.lastIndexOf("```")
        if (trailingFence != -1 && trailingFence > 10) {
            text = text.substring(0, trailingFence).trim()
        }
        return text
    }

    private fun parseScenesFromJSONArray(jsonArray: JSONArray): List<DetectedScene> {
        val list = mutableListOf<DetectedScene>()
        for (i in 0 until jsonArray.length()) {
            val sceneObj = jsonArray.optJSONObject(i) ?: continue
            val sceneNumber = sceneObj.optInt("sceneNumber", i + 1)
            var title = sceneObj.optString("title", "Scene ${i + 1}").trim()
            if (title.isBlank()) title = "Scene ${i + 1}"

            var startMs = sceneObj.optLong("startMs", -1L)
            var endMs = sceneObj.optLong("endMs", -1L)
            val summary = sceneObj.optString("summary", "")

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

            list.add(
                DetectedScene(
                    sceneNumber = sceneNumber,
                    title = title,
                    startMs = startMs.coerceAtLeast(0L),
                    endMs = endMs,
                    summary = summary
                )
            )
        }
        return list
    }

    private fun extractScenesWithRegex(text: String): List<DetectedScene> {
        val result = mutableListOf<DetectedScene>()
        val scenePattern = Regex(
            """\{[^{}]*?"title"\s*:\s*"([^"]+)"[^{}]*?"start(?:Ms)?"\s*:\s*(\d+)[^{}]*?"end(?:Ms)?"\s*:\s*(\d+)[^{}]*?\}""",
            RegexOption.DOT_MATCHES_ALL
        )
        var matchIdx = 1
        for (match in scenePattern.findAll(text)) {
            try {
                val title = match.groupValues[1].trim()
                val start = match.groupValues[2].toLongOrNull() ?: 0L
                val end = match.groupValues[3].toLongOrNull() ?: (start + 60000L)
                result.add(
                    DetectedScene(
                        sceneNumber = matchIdx++,
                        title = title,
                        startMs = start,
                        endMs = end
                    )
                )
            } catch (_: Exception) {}
        }
        return result
    }

    fun parseScenesResiliently(rawText: String, totalDurationMs: Long): List<DetectedScene> {
        val cleaned = cleanJsonText(rawText)
        var rawScenes = emptyList<DetectedScene>()

        // 1. Standard JSONObject with "scenes" array
        try {
            val jsonObject = JSONObject(cleaned)
            val scenesArray = jsonObject.optJSONArray("scenes")
            if (scenesArray != null && scenesArray.length() > 0) {
                rawScenes = parseScenesFromJSONArray(scenesArray)
            }
        } catch (_: Exception) {}

        // 2. Direct JSONArray
        if (rawScenes.isEmpty()) {
            try {
                val jsonArray = JSONArray(cleaned)
                if (jsonArray.length() > 0) {
                    rawScenes = parseScenesFromJSONArray(jsonArray)
                }
            } catch (_: Exception) {}
        }

        // 3. Substring between outermost { and }
        if (rawScenes.isEmpty()) {
            try {
                val firstBrace = cleaned.indexOf('{')
                val lastBrace = cleaned.lastIndexOf('}')
                if (firstBrace != -1 && lastBrace > firstBrace) {
                    val sub = cleaned.substring(firstBrace, lastBrace + 1)
                    val jsonObject = JSONObject(sub)
                    val scenesArray = jsonObject.optJSONArray("scenes")
                    if (scenesArray != null && scenesArray.length() > 0) {
                        rawScenes = parseScenesFromJSONArray(scenesArray)
                    }
                }
            } catch (_: Exception) {}
        }

        // 4. Substring between outermost [ and ]
        if (rawScenes.isEmpty()) {
            try {
                val firstBrace = cleaned.indexOf('[')
                val lastBrace = cleaned.lastIndexOf(']')
                if (firstBrace != -1 && lastBrace > firstBrace) {
                    val sub = cleaned.substring(firstBrace, lastBrace + 1)
                    val jsonArray = JSONArray(sub)
                    if (jsonArray.length() > 0) {
                        rawScenes = parseScenesFromJSONArray(jsonArray)
                    }
                }
            } catch (_: Exception) {}
        }

        // 5. Progressive Regex fallback for truncated or loosely-formatted outputs
        if (rawScenes.isEmpty()) {
            rawScenes = extractScenesWithRegex(cleaned)
        }

        if (rawScenes.isEmpty()) {
            return emptyList()
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
                .replaceFirst(Regex("""^\s*(\d{1,3}|Scene\s*\d{1,3})\s*[-:.]?\s*""", RegexOption.IGNORE_CASE), "")
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

        return validatedList
    }

    suspend fun detectScenes(
        transcriptCues: List<SubtitleCue>,
        totalDurationMs: Long,
        mediaTitle: String,
        customApiKey: String? = null,
        language: String = "en",
        modelName: String = DEFAULT_MODEL,
        onProgressUpdate: ((String) -> Unit)? = null
    ): Result<List<DetectedScene>> = withContext(Dispatchers.IO) {
        try {
            val resolvedApiKey = resolveApiKey(customApiKey)

            if (resolvedApiKey.isBlank()) {
                return@withContext Result.failure(IllegalStateException("MISSING_API_KEY"))
            }

            if (transcriptCues.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("No transcript cues provided."))
            }

            onProgressUpdate?.invoke(
                if (language == "ar") "جارٍ إعداد النص وتحليل المشاهد بالذكاء الاصطناعي..."
                else "Preparing transcript and analyzing scenes with AI..."
            )

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
                    // 8192 output tokens is more than enough for scene JSON and avoids 64K heavy-queue 503 rejections
                    put("maxOutputTokens", 8192)
                })
            }

            val candidateKeys = resolveCandidateApiKeys(customApiKey)

            val (code, responseBody) = executeGeminiPostWithRetry(
                urlBuilder = { m -> "$BASE_URL/$m:generateContent?key=$resolvedApiKey" },
                urlWithKeyBuilder = { m, k -> "$BASE_URL/$m:generateContent?key=$k" },
                candidateApiKeys = candidateKeys,
                payload = rootJson,
                primaryModel = modelName,
                fallbackModel = FALLBACK_MODEL,
                maxAttempts = 5,
                client = subtitleOkHttpClient,
                language = language,
                onProgressUpdate = onProgressUpdate
            )

            if (code !in 200..299) {
                Log.e(TAG, "Gemini Scene Detection failed: $code -> $responseBody")
                val cleanErrorMsg = parseGeminiErrorMessage(code, responseBody, language)
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
            val rawTextBuilder = StringBuilder()
            if (parts != null) {
                for (pIdx in 0 until parts.length()) {
                    rawTextBuilder.append(parts.getJSONObject(pIdx).optString("text", ""))
                }
            }
            val rawText = rawTextBuilder.toString()

            if (rawText.isBlank()) {
                return@withContext Result.failure(Exception("Empty text response for scene detection."))
            }

            val validatedList = parseScenesResiliently(rawText, totalDurationMs)
            if (validatedList.isEmpty()) {
                return@withContext Result.failure(Exception("AI did not return any valid scene items."))
            }

            Result.success(validatedList)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during detectScenes", e)
            val friendlyMsg = when {
                e is java.net.SocketTimeoutException -> {
                    if (language == "ar") "استغرق تحليل المشاهد وقتاً طويلاً. يرجى المحاولة مرة أخرى."
                    else "Scene detection request timed out. Please try again."
                }
                else -> e.message ?: "Failed to detect scenes"
            }
            Result.failure(Exception(friendlyMsg))
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
                    put("maxOutputTokens", 4096)
                })
            }

            val candidateKeys = resolveCandidateApiKeys(customApiKey)

            val (code, responseBody) = executeGeminiPostWithRetry(
                urlBuilder = { m -> "$BASE_URL/$m:generateContent?key=$resolvedApiKey" },
                urlWithKeyBuilder = { m, k -> "$BASE_URL/$m:generateContent?key=$k" },
                candidateApiKeys = candidateKeys,
                payload = rootJson,
                primaryModel = modelName,
                fallbackModel = FALLBACK_MODEL,
                maxAttempts = 5,
                client = okHttpClient,
                language = language
            )

            if (code !in 200..299) {
                Log.e(TAG, "Gemini Unified Quiz Generation failed: $code -> $responseBody")
                val cleanErrorMsg = parseGeminiErrorMessage(code, responseBody, language)
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
            val rawTextBuilder = StringBuilder()
            if (parts != null) {
                for (p in 0 until parts.length()) {
                    rawTextBuilder.append(parts.getJSONObject(p).optString("text", ""))
                }
            }
            val rawText = rawTextBuilder.toString()

            if (rawText.isBlank()) {
                return@withContext Result.failure(Exception("Empty text response for quiz."))
            }

            val cleanedJson = cleanJsonText(rawText)
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
            else -> if (language == "ar") "فشلت العملية ($code). يرجى المحاولة مرة أخرى." else "Request failed (HTTP $code). Please try again."
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
        val cacheKey = "${file.absolutePath}:${file.lastModified()}:${apiKey.hashCode()}"
        uploadedFileCache[cacheKey]?.let { (uri, time) ->
            if (System.currentTimeMillis() - time < 24 * 3600 * 1000L) {
                Log.d(TAG, "Reusing cached Gemini File URI: $uri")
                return@withContext uri
            }
        }

        var lastUploadException: Exception? = null
        var respBody = ""
        for (uploadAttempt in 1..3) {
            try {
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
                respBody = uploadResponse.body?.string() ?: ""
                if (!uploadResponse.isSuccessful) {
                    throw Exception("Files API upload failed (HTTP ${uploadResponse.code}): $respBody")
                }
                lastUploadException = null
                break
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastUploadException = e
                Log.w(TAG, "Audio upload attempt $uploadAttempt failed: ${e.message}")
                if (uploadAttempt < 3) {
                    delay(2000L * uploadAttempt)
                }
            }
        }
        if (lastUploadException != null) throw lastUploadException
        val respJson = JSONObject(respBody)
        val fileObj = respJson.getJSONObject("file")
        val fileUri = fileObj.getString("uri")
        val fileName = fileObj.optString("name")
        var state = fileObj.optString("state", "ACTIVE")

        // Audio & video files on Gemini Files API may take a few moments to process
        var attempts = 0
        while (state.equals("PROCESSING", ignoreCase = true) && attempts < 90) {
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

        if (state.equals("PROCESSING", ignoreCase = true)) {
            throw Exception("Server media processing timed out. Please try again.")
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
        val maxAttempts = 5
        var currentDelayMs = 2000L
        val orderedModels = selectOrderedCandidateModels(preferredModel)
        var lastException: Exception? = null

        val mediaType = "application/json; charset=utf-8".toMediaType()

        for (attempt in 1..maxAttempts) {
            val activeModel = orderedModels[(attempt - 1) % orderedModels.size]
            try {
                paceRequestIfNeeded()
                val useStreaming = (attempt <= 2)

                if (useStreaming) {
                    val requestUrl = "$BASE_URL/$activeModel:streamGenerateContent?key=$resolvedApiKey&alt=sse"
                    val requestBody = rootJson.toString().toRequestBody(mediaType)
                    val request = Request.Builder().url(requestUrl).post(requestBody).build()

                    val response = subtitleOkHttpClient.newCall(request).execute()
                    val responseCode = response.code
                    if (!response.isSuccessful) {
                        val errBody = response.body?.string() ?: ""
                        markModelFailure(activeModel, responseCode)
                        Log.e(TAG, "Gemini Subtitles stream failed (HTTP $responseCode, model $activeModel, attempt $attempt/$maxAttempts): $errBody")
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
                        markModelSuccess(activeModel)
                        return@withContext rawResult
                    } else {
                        markModelFailure(activeModel, 503)
                        throw Exception("Streaming returned incomplete subtitle output.")
                    }
                } else {
                    // Non-streaming generateContent fallback: robust against SSE socket drops on long audio
                    val fallbackMsg = if (language == "ar") "جارٍ إتمام معالجة الترجمة عبر خوادم الذكاء الاصطناعي ($activeModel)..."
                    else "Processing subtitles directly via Gemini API ($activeModel)..."
                    onProgressUpdate?.invoke(fallbackMsg)

                    val requestUrl = "$BASE_URL/$activeModel:generateContent?key=$resolvedApiKey"
                    val requestBody = rootJson.toString().toRequestBody(mediaType)
                    val request = Request.Builder().url(requestUrl).post(requestBody).build()

                    val response = subtitleOkHttpClient.newCall(request).execute()
                    val responseCode = response.code
                    val responseBody = response.body?.string() ?: ""

                    if (!response.isSuccessful) {
                        markModelFailure(activeModel, responseCode)
                        Log.e(TAG, "Gemini Subtitles generateContent failed (HTTP $responseCode, model $activeModel): $responseBody")
                        throw Exception("HTTP $responseCode: $responseBody")
                    }

                    val respJson = JSONObject(responseBody)
                    val candidates = respJson.optJSONArray("candidates")
                    val candidate = candidates?.optJSONObject(0)
                    val parts = candidate?.optJSONObject("content")?.optJSONArray("parts")
                    val rawBuilder = StringBuilder()
                    if (parts != null) {
                        for (p in 0 until parts.length()) {
                            rawBuilder.append(parts.getJSONObject(p).optString("text", ""))
                        }
                    }
                    val rawResult = rawBuilder.toString()

                    if (rawResult.isNotBlank() && rawResult.contains("-->")) {
                        markModelSuccess(activeModel)
                        return@withContext rawResult
                    } else {
                        markModelFailure(activeModel, 503)
                        throw Exception("Gemini returned invalid or empty subtitle text.")
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastException = e
                val msg = e.message ?: ""
                val is503 = msg.contains("503") || msg.contains("overloaded", ignoreCase = true) || msg.contains("UNAVAILABLE", ignoreCase = true)
                val is429 = msg.contains("429") || msg.contains("RESOURCE_EXHAUSTED", ignoreCase = true)
                val is404 = msg.contains("404") || msg.contains("NOT_FOUND", ignoreCase = true)
                val is5xx = msg.contains("500") || msg.contains("502") || msg.contains("504") || msg.contains("INTERNAL", ignoreCase = true)
                val isTimeout = e is java.net.SocketTimeoutException || msg.contains("timeout", ignoreCase = true) || e.cause is java.net.SocketTimeoutException
                val isConnectionErr = e is java.io.IOException || msg.contains("connection", ignoreCase = true) || msg.contains("stream", ignoreCase = true) || msg.contains("incomplete", ignoreCase = true)

                val canRetry = (is503 || is429 || is404 || is5xx || isTimeout || isConnectionErr) && attempt < maxAttempts

                if (canRetry) {
                    val nextModel = orderedModels[attempt % orderedModels.size]
                    val jitterMs = kotlin.random.Random.nextLong(250L, 800L)
                    val waitMs = if (is404) 350L else (currentDelayMs + jitterMs).coerceAtMost(10000L)
                    val waitSec = (waitMs / 1000).coerceAtLeast(1).toInt()
                    val retryNotice = if (language == "ar") {
                        if (is503) "خوادم Gemini مشغولة مؤقتاً (503)، جارٍ التبديل للنموذج البديل ($nextModel) خلال $waitSec ثوانٍ (${attempt + 1}/$maxAttempts)..."
                        else if (isTimeout) "استغرق الاتصال وقتاً أطول، جارٍ إعادة المحاولة عبر ($nextModel) خلال $waitSec ثوانٍ (${attempt + 1}/$maxAttempts)..."
                        else "جارٍ إعادة المحاولة تلقائياً عبر ($nextModel) (${attempt + 1}/$maxAttempts)..."
                    } else {
                        if (is503) "Gemini servers busy (503), switching to $nextModel in ${waitSec}s (${attempt + 1}/$maxAttempts)..."
                        else if (isTimeout) "Request timed out, retrying with $nextModel in ${waitSec}s (${attempt + 1}/$maxAttempts)..."
                        else "Switching to $nextModel and retrying (${attempt + 1}/$maxAttempts)..."
                    }
                    onProgressUpdate?.invoke(retryNotice)
                    Log.w(TAG, "Subtitle generation attempt $attempt ($activeModel) failed with: $msg. Retrying with $nextModel in ${waitMs}ms...")
                    delay(waitMs)
                    if (!is404) {
                        currentDelayMs = (currentDelayMs * 1.75).toLong().coerceAtMost(9500L)
                    }
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
            val effectiveMediaFile = extractAudioFromVideoIfPossible(audioFile)
            val mimeType = getMimeTypeForFile(effectiveMediaFile)

            // Determine accurate audio duration
            val calculatedDurationMs = if (totalDurationMs > 0) totalDurationMs else getMediaDurationMs(effectiveMediaFile)
            val durationSeconds = if (calculatedDurationMs > 0) {
                (calculatedDurationMs / 1000).toInt()
            } else {
                val approxSec = ((effectiveMediaFile.length() / (128 * 1024 / 8))).toInt().coerceIn(60, 7200)
                approxSec
            }

            val mediaPartJson = JSONObject()
            // Files over 2MB or audio longer than 120 seconds use Gemini Files API for reliable upload and server-side processing
            val maxInlineBytes = 2 * 1024 * 1024L // 2MB
            if (effectiveMediaFile.length() <= maxInlineBytes && durationSeconds <= 120) {
                onProgressUpdate?.invoke(
                    if (language == "ar") "جارٍ قراءة الملف الصوتي..." else "Reading audio data..."
                )
                val bytes = effectiveMediaFile.readBytes()
                val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
                mediaPartJson.put("inlineData", JSONObject().apply {
                    put("mimeType", mimeType)
                    put("data", base64Data)
                })
            } else {
                val uploadProgressMsg = if (language == "ar") "جارٍ رفع المقطع إلى خوادم Gemini..." else "Uploading audio to Gemini server..."
                onProgressUpdate?.invoke(uploadProgressMsg)
                val fileUri = uploadFileToGemini(effectiveMediaFile, mimeType, resolvedApiKey, onProgressUpdate)
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

            // Up to 8 minutes (480 seconds) is processed in a single pass; longer tracks use 7-minute chunks
            // so each chunk fits comfortably within 8192 output tokens without triggering heavy-queue 503 rejections.
            val singlePassMaxSeconds = 480
            val chunkWindows = mutableListOf<Pair<Int, Int>>()

            if (durationSeconds <= singlePassMaxSeconds) {
                chunkWindows.add(Pair(0, durationSeconds))
            } else {
                val chunkWindowSec = 420 // 7 minutes per chunk for long podcasts/lectures
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
                        put("maxOutputTokens", 8192)
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
