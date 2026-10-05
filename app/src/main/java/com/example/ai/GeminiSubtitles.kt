package com.example.ai

import android.util.Base64
import android.util.Log
import com.example.player.SubtitleCue
import com.example.player.SubtitleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object GeminiSubtitles {
    private const val TAG = "GeminiService"

    private suspend fun executeSubtitleGenerationWithRetry(
        rootJson: JSONObject,
        candidateApiKeys: List<String>,
        preferredModel: String,
        language: String,
        chunkIdx: Int,
        totalChunks: Int,
        accumulatedCuesCount: Int,
        onProgressUpdate: ((String) -> Unit)?
    ): String = withContext(Dispatchers.IO) {
        val maxAttempts = 6
        var currentDelayMs = 1500L
        val orderedModels = GeminiModelHealth.selectOrderedCandidateModels(preferredModel)
        var lastException: Exception? = null
        var currentRootJson = JSONObject(rootJson.toString())
        val keys = candidateApiKeys.ifEmpty { listOf("") }

        val mediaType = "application/json; charset=utf-8".toMediaType()

        for (attempt in 1..maxAttempts) {
            val activeModel = orderedModels[(attempt - 1) % orderedModels.size]
            val activeApiKey = keys[(attempt - 1) % keys.size]
            try {
                GeminiHttp.paceRequestIfNeeded()
                val useStreaming = (attempt == 1)

                if (useStreaming) {
                    val requestUrl = "${GeminiModelHealth.BASE_URL}/$activeModel:streamGenerateContent?alt=sse"
                    val requestBody = currentRootJson.toString().toRequestBody(mediaType)
                    val request = Request.Builder()
                        .url(requestUrl)
                        .addHeader("x-goog-api-key", activeApiKey)
                        .post(requestBody)
                        .build()

                    val response = GeminiHttp.subtitleOkHttpClient.newCall(request).execute()
                    val responseCode = response.code
                    if (!response.isSuccessful) {
                        val errBody = response.body?.string() ?: ""
                        if (responseCode == 400 && (errBody.contains("thinking", ignoreCase = true) || errBody.contains("thinkingConfig", ignoreCase = true) || errBody.contains("maxOutputTokens", ignoreCase = true) || errBody.contains("max_output_tokens", ignoreCase = true))) {
                            Log.w(TAG, "Subtitle model $activeModel rejected thinkingConfig/maxOutputTokens; adapting payload and retrying immediately")
                            currentRootJson.optJSONObject("generationConfig")?.apply {
                                remove("thinkingConfig")
                                if (optInt("maxOutputTokens", 8192) > 8192) {
                                    put("maxOutputTokens", 8192)
                                }
                            }
                            val retryReq = Request.Builder()
                                .url(requestUrl)
                                .addHeader("x-goog-api-key", activeApiKey)
                                .post(currentRootJson.toString().toRequestBody(mediaType))
                                .build()
                            val retryResp = GeminiHttp.subtitleOkHttpClient.newCall(retryReq).execute()
                            if (!retryResp.isSuccessful) {
                                val retryErr = retryResp.body?.string() ?: ""
                                GeminiModelHealth.markModelFailure(activeModel, retryResp.code)
                                throw Exception("HTTP ${retryResp.code}: $retryErr")
                            }
                            val retryBody = retryResp.body ?: throw Exception("Empty response body from Gemini.")
                            val retryText = GeminiSubtitleParsing.parseSseSubtitleStream(retryBody, language, chunkIdx, totalChunks, accumulatedCuesCount, onProgressUpdate)
                            if (retryText.isNotBlank() && retryText.contains("-->")) {
                                GeminiModelHealth.markModelSuccess(activeModel)
                                return@withContext retryText
                            }
                        }
                        GeminiModelHealth.markModelFailure(activeModel, responseCode)
                        Log.e(TAG, "Gemini Subtitles stream failed (HTTP $responseCode, model $activeModel, attempt $attempt/$maxAttempts): $errBody")
                        throw Exception("HTTP $responseCode: $errBody")
                    }

                    val body = response.body ?: throw Exception("Empty response body from Gemini.")
                    val rawResult = GeminiSubtitleParsing.parseSseSubtitleStream(body, language, chunkIdx, totalChunks, accumulatedCuesCount, onProgressUpdate)
                    if (rawResult.isNotBlank() && rawResult.contains("-->")) {
                        GeminiModelHealth.markModelSuccess(activeModel)
                        return@withContext rawResult
                    } else {
                        Log.w(TAG, "Streaming returned incomplete subtitle output on $activeModel; switching to standard generateContent")
                        throw Exception("Streaming returned incomplete subtitle output.")
                    }
                } else {
                    // Non-streaming generateContent: ultra-reliable against SSE socket drops on long audio
                    val fallbackMsg = if (language == "ar") "جارٍ معالجة الترجمة عبر الذكاء الاصطناعي ($activeModel)..."
                    else "Processing subtitles via Gemini API ($activeModel)..."
                    onProgressUpdate?.invoke(fallbackMsg)

                    val requestUrl = "${GeminiModelHealth.BASE_URL}/$activeModel:generateContent"
                    val requestBody = currentRootJson.toString().toRequestBody(mediaType)
                    val request = Request.Builder()
                        .url(requestUrl)
                        .addHeader("x-goog-api-key", activeApiKey)
                        .post(requestBody)
                        .build()

                    var response = GeminiHttp.subtitleOkHttpClient.newCall(request).execute()
                    var responseCode = response.code
                    var responseBody = response.body?.string() ?: ""

                    if (responseCode == 400 && (responseBody.contains("thinking", ignoreCase = true) || responseBody.contains("thinkingConfig", ignoreCase = true) || responseBody.contains("maxOutputTokens", ignoreCase = true) || responseBody.contains("max_output_tokens", ignoreCase = true))) {
                        Log.w(TAG, "Subtitle model $activeModel rejected thinkingConfig/maxOutputTokens in generateContent; adapting and retrying immediately")
                        currentRootJson.optJSONObject("generationConfig")?.apply {
                            remove("thinkingConfig")
                            if (optInt("maxOutputTokens", 8192) > 8192) {
                                put("maxOutputTokens", 8192)
                            }
                        }
                        val retryReq = Request.Builder()
                            .url(requestUrl)
                            .addHeader("x-goog-api-key", activeApiKey)
                            .post(currentRootJson.toString().toRequestBody(mediaType))
                            .build()
                        response = GeminiHttp.subtitleOkHttpClient.newCall(retryReq).execute()
                        responseCode = response.code
                        responseBody = response.body?.string() ?: ""
                    }

                    if (!response.isSuccessful) {
                        GeminiModelHealth.markModelFailure(activeModel, responseCode)
                        Log.e(TAG, "Gemini Subtitles generateContent failed (HTTP $responseCode, model $activeModel): $responseBody")
                        throw Exception("HTTP $responseCode: $responseBody")
                    }

                    val respJson = JSONObject(responseBody)
                    val candidateCheck = GeminiHttp.checkCandidateBlockOrFinishReason(respJson, language)
                    if (candidateCheck.blockedErrorMessage != null) {
                        throw Exception(candidateCheck.blockedErrorMessage)
                    }
                    val candidates = respJson.optJSONArray("candidates")
                    val candidate = candidates?.optJSONObject(0)
                    val parts = candidate?.optJSONObject("content")?.optJSONArray("parts")
                    val rawResult = GeminiHttp.extractNonThoughtTextFromParts(parts)

                    if (rawResult.isNotBlank() && rawResult.contains("-->")) {
                        GeminiModelHealth.markModelSuccess(activeModel)
                        return@withContext rawResult
                    } else {
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
                val isConnectionErr = e is java.io.IOException || msg.contains("connection", ignoreCase = true) || msg.contains("stream", ignoreCase = true) || msg.contains("incomplete", ignoreCase = true) || msg.contains("invalid or empty", ignoreCase = true)

                val canRetry = (is503 || is429 || is404 || is5xx || isTimeout || isConnectionErr) && attempt < maxAttempts

                if (canRetry) {
                    val nextModel = orderedModels[attempt % orderedModels.size]
                    val jitterMs = kotlin.random.Random.nextLong(200L, 600L)
                    val waitMs = if (is404 || msg.contains("incomplete", ignoreCase = true)) 300L else (currentDelayMs + jitterMs).coerceAtMost(8000L)
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
                        currentDelayMs = (currentDelayMs * 1.6).toLong().coerceAtMost(8000L)
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
        modelName: String = GeminiModelHealth.FALLBACK_MODEL,
        onProgressUpdate: ((String) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val candidateApiKeys = GeminiKeyPool.resolveCandidateApiKeys(customApiKey)
        val resolvedApiKey = candidateApiKeys.firstOrNull() ?: GeminiKeyPool.resolveApiKey(customApiKey)
        if (resolvedApiKey.isBlank()) {
            return@withContext Result.failure(Exception("MISSING_API_KEY"))
        }

        if (!audioFile.exists() || !audioFile.canRead()) {
            return@withContext Result.failure(Exception("Audio file not found or cannot be read: ${audioFile.absolutePath}"))
        }

        val tempFilesToClean = mutableListOf<File>()
        try {
            val effectiveMediaFile = GeminiMedia.extractAudioFromVideoIfPossible(audioFile)
            val mimeType = GeminiHttp.getMimeTypeForFile(effectiveMediaFile)

            // Determine accurate audio duration
            val calculatedDurationMs = if (totalDurationMs > 0) totalDurationMs else GeminiMedia.getMediaDurationMs(effectiveMediaFile)
            val durationSeconds = if (calculatedDurationMs > 0) {
                (calculatedDurationMs / 1000).toInt()
            } else {
                val approxSec = ((effectiveMediaFile.length() / (128 * 1024 / 8))).toInt().coerceIn(60, 7200)
                approxSec
            }

            // Sanitize reference text: strip inaccurate existing timestamps to eliminate bias
            val cleanReferenceText = if (!existingSubtitleText.isNullOrBlank()) {
                SubtitleParser.extractCleanTextFromReference(existingSubtitleText)
            } else {
                null
            }

            // Up to 10 minutes (600 seconds) is processed in a single pass; longer tracks use 8-minute chunks.
            val singlePassMaxSeconds = 600
            val chunkWindows = mutableListOf<Pair<Int, Int>>()

            if (durationSeconds <= singlePassMaxSeconds) {
                chunkWindows.add(Pair(0, durationSeconds))
            } else {
                val chunkWindowSec = 480 // 8 minutes per chunk
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
            val maxInlineBytes = 14 * 1024 * 1024L // 14MB inline limit (safe under 20MB Gemini payload cap)
            var fullFileFallbackJson: JSONObject? = null

            for ((chunkIdx, window) in chunkWindows.withIndex()) {
                val (winStartSec, winEndSec) = window
                val winStartMs = winStartSec * 1000L
                val winEndMs = winEndSec * 1000L

                val chunkProgressMsg = if (totalChunks > 1) {
                    if (language == "ar") {
                        "توليد الترجمة للجزء (${chunkIdx + 1}/$totalChunks): ${SubtitleParser.formatShortTimeTag(winStartMs)} - ${SubtitleParser.formatShortTimeTag(winEndMs)}..."
                    } else {
                        "Generating subtitles chunk (${chunkIdx + 1}/$totalChunks): ${SubtitleParser.formatShortTimeTag(winStartMs)} - ${SubtitleParser.formatShortTimeTag(winEndMs)}..."
                    }
                } else {
                    if (language == "ar") "جارٍ إعداد وتوليد ملف الترجمة بالذكاء الاصطناعي..."
                    else "Preparing and generating subtitles with AI..."
                }
                onProgressUpdate?.invoke(chunkProgressMsg)

                var slicedChunkFile: File? = null
                if (totalChunks > 1) {
                    try {
                        slicedChunkFile = GeminiMedia.extractAudioTimeSliceIfPossible(effectiveMediaFile, winStartMs, winEndMs)
                        if (slicedChunkFile != null) {
                            tempFilesToClean.add(slicedChunkFile)
                            Log.d(TAG, "Created physical time slice for chunk $chunkIdx: ${slicedChunkFile.length()} bytes")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Time slice extraction failed, falling back to full file with prompt offsets: ${e.message}")
                    }
                }

                val activeMediaForChunk = slicedChunkFile ?: effectiveMediaFile
                val activeMime = GeminiHttp.getMimeTypeForFile(activeMediaForChunk)
                val isSliced = (slicedChunkFile != null)

                val (systemInstruction, prompt) = GeminiPrompts.buildSubtitleGenerationPrompt(
                    totalChunks = totalChunks,
                    winStartMs = winStartMs,
                    winEndMs = winEndMs,
                    calculatedDurationMs = calculatedDurationMs,
                    cleanReferenceText = cleanReferenceText,
                    globalCueIndex = globalCueIndex,
                    isPhysicallySlicedChunk = isSliced
                )

                val rootJson = JSONObject().apply {
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", systemInstruction) })
                        })
                    })

                    val contentsParts = JSONArray()

                    if (activeMediaForChunk.length() <= maxInlineBytes) {
                        val base64Data = Base64.encodeToString(activeMediaForChunk.readBytes(), Base64.NO_WRAP)
                        contentsParts.put(JSONObject().apply {
                            put("inline_data", JSONObject().apply {
                                put("mime_type", activeMime)
                                put("data", base64Data)
                            })
                        })
                    } else {
                        val fileUri = GeminiHttp.uploadFileToGemini(
                            file = activeMediaForChunk,
                            mimeType = activeMime,
                            apiKey = resolvedApiKey,
                            onProgressUpdate = onProgressUpdate
                        )
                        contentsParts.put(JSONObject().apply {
                            put("file_data", JSONObject().apply {
                                put("mime_type", activeMime)
                                put("file_uri", fileUri)
                            })
                        })
                    }

                    contentsParts.put(JSONObject().apply { put("text", prompt) })

                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", contentsParts)
                        })
                    })

                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.0)
                        put("maxOutputTokens", 65536)
                        put("thinkingConfig", GeminiHttp.buildFastThinkingConfig())
                    })
                }

                val rawSrtChunk = try {
                    executeSubtitleGenerationWithRetry(
                        rootJson = rootJson,
                        candidateApiKeys = candidateApiKeys,
                        preferredModel = modelName,
                        language = language,
                        chunkIdx = chunkIdx,
                        totalChunks = totalChunks,
                        accumulatedCuesCount = allAccumulatedCues.size,
                        onProgressUpdate = onProgressUpdate
                    )
                } catch (e: Exception) {
                    if (isSliced) {
                        Log.w(TAG, "Chunk $chunkIdx failed with sliced audio clip. Retrying using full file reference: ${e.message}")
                        if (fullFileFallbackJson == null) {
                            val fullFileParts = JSONArray()
                            if (effectiveMediaFile.length() <= maxInlineBytes) {
                                val b64 = Base64.encodeToString(effectiveMediaFile.readBytes(), Base64.NO_WRAP)
                                fullFileParts.put(JSONObject().apply {
                                    put("inline_data", JSONObject().apply {
                                        put("mime_type", mimeType)
                                        put("data", b64)
                                    })
                                })
                            } else {
                                val uri = GeminiHttp.uploadFileToGemini(
                                    file = effectiveMediaFile,
                                    mimeType = mimeType,
                                    apiKey = resolvedApiKey,
                                    onProgressUpdate = onProgressUpdate
                                )
                                fullFileParts.put(JSONObject().apply {
                                    put("file_data", JSONObject().apply {
                                        put("mime_type", mimeType)
                                        put("file_uri", uri)
                                    })
                                })
                            }
                            fullFileFallbackJson = JSONObject().apply {
                                put("systemInstruction", JSONObject().apply {
                                    put("parts", JSONArray().apply {
                                        put(JSONObject().apply { put("text", systemInstruction) })
                                    })
                                })
                                put("partsPrefix", fullFileParts)
                            }
                        }

                        val fallbackParts = JSONArray()
                        val prefixArr = fullFileFallbackJson!!.getJSONArray("partsPrefix")
                        for (p in 0 until prefixArr.length()) {
                            fallbackParts.put(prefixArr.get(p))
                        }
                        val (_, fallbackPrompt) = GeminiPrompts.buildSubtitleGenerationPrompt(
                            totalChunks = totalChunks,
                            winStartMs = winStartMs,
                            winEndMs = winEndMs,
                            calculatedDurationMs = calculatedDurationMs,
                            cleanReferenceText = cleanReferenceText,
                            globalCueIndex = globalCueIndex,
                            isPhysicallySlicedChunk = false
                        )
                        fallbackParts.put(JSONObject().apply { put("text", fallbackPrompt) })

                        val fallbackRoot = JSONObject().apply {
                            put("systemInstruction", fullFileFallbackJson!!.getJSONObject("systemInstruction"))
                            put("contents", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("role", "user")
                                    put("parts", fallbackParts)
                                })
                            })
                            put("generationConfig", JSONObject().apply {
                                put("temperature", 0.0)
                                put("maxOutputTokens", 65536)
                                put("thinkingConfig", GeminiHttp.buildFastThinkingConfig())
                            })
                        }

                        executeSubtitleGenerationWithRetry(
                            rootJson = fallbackRoot,
                            candidateApiKeys = candidateApiKeys,
                            preferredModel = GeminiModelHealth.FALLBACK_MODEL,
                            language = language,
                            chunkIdx = chunkIdx,
                            totalChunks = totalChunks,
                            accumulatedCuesCount = allAccumulatedCues.size,
                            onProgressUpdate = onProgressUpdate
                        )
                    } else {
                        throw e
                    }
                }

                val cleanedSrt = GeminiSubtitleParsing.cleanSrtOutput(rawSrtChunk)
                val chunkCues = SubtitleParser.parseContent(cleanedSrt)

                if (chunkCues.isNotEmpty()) {
                    val firstStart = chunkCues.first().startMs
                    val needsOffsetShift = isSliced || (totalChunks > 1 && winStartMs > 30000L && firstStart < 15000L)
                    val offsetToAdd = if (needsOffsetShift) winStartMs else 0L

                    for (cue in chunkCues) {
                        val shiftedStart = (cue.startMs + offsetToAdd).coerceAtLeast(0L)
                        val shiftedEnd = (cue.endMs + offsetToAdd).coerceAtLeast(shiftedStart + 200L)

                        if (allAccumulatedCues.isNotEmpty() && shiftedStart <= allAccumulatedCues.last().startMs) {
                            continue
                        }

                        allAccumulatedCues.add(
                            SubtitleCue(
                                id = globalCueIndex++,
                                startMs = shiftedStart,
                                endMs = shiftedEnd,
                                text = cue.text
                            )
                        )
                    }
                }
            }

            if (allAccumulatedCues.isEmpty()) {
                return@withContext Result.failure(Exception("Gemini returned invalid or empty subtitle format."))
            }

            allAccumulatedCues.sortBy { it.startMs }
            val reindexed = allAccumulatedCues.mapIndexed { idx, cue -> cue.copy(id = idx + 1) }
            val finalSrtOutput = SubtitleParser.exportCuesToSrt(reindexed)

            Result.success(finalSrtOutput)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating subtitles", e)
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
        } finally {
            for (tempFile in tempFilesToClean) {
                try {
                    if (tempFile.exists()) tempFile.delete()
                } catch (_: Exception) {}
            }
        }
    }
}
