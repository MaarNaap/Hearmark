package com.example.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object GeminiHttp {
    private const val TAG = "GeminiService"
    private const val MIN_REQUEST_INTERVAL_MS = 350L

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // Dedicated client for large media uploads and long subtitle transcription streaming (e.g. 18+ min tracks)
    val subtitleOkHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS) // Up to 3 minutes to upload large media files
        .readTimeout(180, TimeUnit.SECONDS)  // Up to 3 minutes between stream packets
        .callTimeout(0, TimeUnit.SECONDS)    // No arbitrary global timeout cutoff
        .retryOnConnectionFailure(true)
        .build()

    private val uploadedFileCache = ConcurrentHashMap<String, Pair<String, Long>>()

    // Request Pacing to avoid burst 503/429 rejections
    private val pacingLock = Mutex()
    @Volatile private var lastRequestTimestampMs: Long = 0L

    suspend fun paceRequestIfNeeded() {
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

    fun extractNonThoughtTextFromParts(parts: JSONArray?): String {
        if (parts == null || parts.length() == 0) return ""
        val nonThoughtBuilder = StringBuilder()
        val fallbackBuilder = StringBuilder()
        for (i in 0 until parts.length()) {
            val partObj = parts.optJSONObject(i) ?: continue
            val text = partObj.optString("text", "")
            if (text.isEmpty()) continue
            fallbackBuilder.append(text)
            if (!partObj.optBoolean("thought", false)) {
                nonThoughtBuilder.append(text)
            }
        }
        return if (nonThoughtBuilder.isNotEmpty()) nonThoughtBuilder.toString() else fallbackBuilder.toString()
    }

    fun buildFastThinkingConfig(): JSONObject {
        return JSONObject().apply {
            put("thinkingBudget", 0)
        }
    }

    fun stripThinkingOrClampTokensFromPayload(payload: JSONObject, errorBody: String): JSONObject {
        val copy = JSONObject(payload.toString())
        val genConfig = copy.optJSONObject("generationConfig") ?: return copy
        if (errorBody.contains("thinking", ignoreCase = true) || genConfig.has("thinkingConfig")) {
            genConfig.remove("thinkingConfig")
        }
        if (errorBody.contains("max_output_tokens", ignoreCase = true) || errorBody.contains("maxOutputTokens", ignoreCase = true)) {
            genConfig.put("maxOutputTokens", 8192)
        }
        return copy
    }

    data class CandidateCheckResult(
        val blockedErrorMessage: String? = null,
        val finishReason: String = "",
        val isTruncatedByMaxTokens: Boolean = false
    )

    fun checkCandidateBlockOrFinishReason(
        respJson: JSONObject,
        language: String = "en"
    ): CandidateCheckResult {
        val promptFeedback = respJson.optJSONObject("promptFeedback")
        val blockReason = promptFeedback?.optString("blockReason", "")?.uppercase() ?: ""
        if (blockReason.isNotBlank() && blockReason != "BLOCK_REASON_UNSPECIFIED") {
            val msg = if (language == "ar") {
                "تم حظر الطلب بواسطة فلاتر الأمان الخاصة بالذكاء الاصطناعي ($blockReason)."
            } else {
                "Request was blocked by AI safety filters ($blockReason)."
            }
            return CandidateCheckResult(blockedErrorMessage = msg, finishReason = blockReason)
        }

        val candidates = respJson.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            return CandidateCheckResult()
        }
        val firstCandidate = candidates.optJSONObject(0) ?: return CandidateCheckResult()
        val finishReason = firstCandidate.optString("finishReason", "").uppercase()

        if (finishReason in setOf("SAFETY", "RECITATION", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII")) {
            val msg = if (language == "ar") {
                "توقف توليد الرد بسبب سياسات الأمان أو حقوق النشر ($finishReason)."
            } else {
                "AI response was stopped due to safety or recitation policy ($finishReason)."
            }
            return CandidateCheckResult(blockedErrorMessage = msg, finishReason = finishReason)
        }

        return CandidateCheckResult(
            blockedErrorMessage = null,
            finishReason = finishReason,
            isTruncatedByMaxTokens = finishReason == "MAX_TOKENS"
        )
    }

    suspend fun executeGeminiPostWithRetry(
        urlBuilder: (model: String) -> String,
        payload: JSONObject,
        primaryModel: String = GeminiModelHealth.DEFAULT_MODEL,
        fallbackModel: String = GeminiModelHealth.FALLBACK_MODEL,
        maxAttempts: Int = 6,
        client: OkHttpClient = okHttpClient,
        language: String = "en",
        candidateApiKeys: List<String> = emptyList(),
        urlWithKeyBuilder: ((model: String, apiKey: String) -> String)? = null,
        onProgressUpdate: ((String) -> Unit)? = null
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        var activePayload = payload
        val keysPool = candidateApiKeys.filter { it.isNotBlank() }
        val modelsFailedWith404 = mutableSetOf<String>()

        if (keysPool.isNotEmpty() && (client === okHttpClient || client === subtitleOkHttpClient)) {
            GeminiModelHealth.refreshAvailableModelsIfNeeded(keysPool.first(), client)
        }

        var lastCode = -1
        var lastBody = ""
        var lastException: Exception? = null
        var baseDelayMs = 1500L

        for (attempt in 1..maxAttempts) {
            val dynamicModels = GeminiModelHealth.selectOrderedCandidateModels(primaryModel)
                .filter { it !in modelsFailedWith404 }
                .ifEmpty { listOf(GeminiModelHealth.FALLBACK_MODEL, GeminiModelHealth.STABLE_FLASH_MODEL, GeminiModelHealth.LITE_FALLBACK_MODEL) }
            val currentModel = dynamicModels[(attempt - 1) % dynamicModels.size]
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
                val currentPayloadForModel = if (currentModel.contains("pro", ignoreCase = true)) {
                    stripThinkingOrClampTokensFromPayload(activePayload, "thinking")
                } else {
                    activePayload
                }
                val body = currentPayloadForModel.toString().toRequestBody(mediaType)
                val requestBuilder = Request.Builder().url(url).post(body)
                if (currentKey.isNotBlank()) {
                    requestBuilder.addHeader("x-goog-api-key", currentKey)
                }
                val request = requestBuilder.build()

                val response = client.newCall(request).execute()
                lastCode = response.code
                lastBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    GeminiModelHealth.markModelSuccess(currentModel)
                    return@withContext Pair(lastCode, lastBody)
                }

                // If 400 due to thinkingConfig or maxOutputTokens on a specific model, sanitize payload and retry immediately
                if (lastCode == 400 && (
                    lastBody.contains("thinking", ignoreCase = true) ||
                    lastBody.contains("max_output_tokens", ignoreCase = true) ||
                    lastBody.contains("maxOutputTokens", ignoreCase = true) ||
                    lastBody.contains("generation_config", ignoreCase = true)
                )) {
                    activePayload = stripThinkingOrClampTokensFromPayload(activePayload, lastBody)
                    if (attempt < maxAttempts) {
                        continue
                    }
                }

                val is503 = lastCode == 503 || lastBody.contains("overloaded", ignoreCase = true) || lastBody.contains("UNAVAILABLE", ignoreCase = true)
                val is429 = lastCode == 429 || lastBody.contains("RESOURCE_EXHAUSTED", ignoreCase = true) || lastBody.contains("quota", ignoreCase = true)
                val is404 = lastCode == 404 || lastBody.contains("NOT_FOUND", ignoreCase = true)
                val is5xx = lastCode in 500..599

                if (is404) {
                    modelsFailedWith404.add(currentModel)
                }
                GeminiModelHealth.markModelFailure(currentModel, lastCode)
                Log.w(TAG, "Gemini POST failed (HTTP $lastCode, model $currentModel, attempt $attempt/$maxAttempts): $lastBody")

                if (attempt < maxAttempts && (is503 || is429 || is404 || is5xx)) {
                    val nextModels = GeminiModelHealth.selectOrderedCandidateModels(primaryModel).filter { it !in modelsFailedWith404 }
                    val nextModel = nextModels.firstOrNull() ?: GeminiModelHealth.FALLBACK_MODEL
                    val retryAfterSec = response.header("Retry-After")?.toLongOrNull()
                    val jitterMs = kotlin.random.Random.nextLong(200L, 650L)
                    val waitMs = if (is404) {
                        150L // Instant switch when model endpoint is 404
                    } else if (retryAfterSec != null && retryAfterSec in 1..15) {
                        retryAfterSec * 1000L + jitterMs
                    } else {
                        (baseDelayMs + jitterMs).coerceAtMost(8000L)
                    }

                    val retryMsg = if (language == "ar") {
                        "خادم الذكاء الاصطناعي مشغول مؤقتاً، جارٍ التبديل للنموذج البديل ($nextModel) وإعادة المحاولة (${attempt + 1}/$maxAttempts)..."
                    } else {
                        "AI server busy (HTTP $lastCode), switching to $nextModel and retrying (${attempt + 1}/$maxAttempts)..."
                    }
                    onProgressUpdate?.invoke(retryMsg)
                    delay(waitMs)
                    if (!is404) {
                        baseDelayMs = (baseDelayMs * 1.65).toLong().coerceAtMost(7500L)
                    }
                    continue
                } else {
                    return@withContext Pair(lastCode, lastBody)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastException = e
                GeminiModelHealth.markModelFailure(currentModel, 503)
                Log.w(TAG, "Gemini POST exception (model $currentModel, attempt $attempt/$maxAttempts): ${e.message}")
                if (attempt < maxAttempts) {
                    val jitterMs = kotlin.random.Random.nextLong(200L, 650L)
                    val waitMs = (baseDelayMs + jitterMs).coerceAtMost(8000L)
                    val retryMsg = if (language == "ar") {
                        "جارٍ إعادة المحاولة تلقائياً عبر نموذج بديل (${attempt + 1}/$maxAttempts)..."
                    } else {
                        "Connection interrupted, retrying with backup model (${attempt + 1}/$maxAttempts)..."
                    }
                    onProgressUpdate?.invoke(retryMsg)
                    delay(waitMs)
                    baseDelayMs = (baseDelayMs * 1.65).toLong().coerceAtMost(7500L)
                } else {
                    throw e
                }
            }
        }
        if (lastException != null) throw lastException
        Pair(lastCode, lastBody)
    }

    fun parseGeminiErrorMessage(code: Int, body: String, language: String): String {
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

    fun getMimeTypeForFile(file: File): String {
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

    suspend fun uploadFileToGemini(
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
                val initUrl = "https://generativelanguage.googleapis.com/upload/v1beta/files"
                val metadataJson = JSONObject().apply {
                    put("file", JSONObject().apply {
                        put("display_name", file.name)
                    })
                }
                val mediaTypeJson = "application/json; charset=utf-8".toMediaType()
                val initRequest = Request.Builder()
                    .url(initUrl)
                    .addHeader("x-goog-api-key", apiKey)
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
                .url("https://generativelanguage.googleapis.com/v1beta/$fileName")
                .addHeader("x-goog-api-key", apiKey)
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
}
