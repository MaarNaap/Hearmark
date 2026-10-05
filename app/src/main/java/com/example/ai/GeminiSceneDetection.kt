package com.example.ai

import android.util.Log
import com.example.player.SubtitleCue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object GeminiSceneDetection {
    private const val TAG = "GeminiService"

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
            } catch (e: Exception) {
                logParseFallback("regex_item", e)
            }
        }
        return result
    }

    private fun logParseFallback(stage: String, e: Exception) {
        try {
            Log.w(TAG, "Scene parse fallback ($stage): ${e.message}")
        } catch (_: Throwable) {}
    }

    fun parseScenesResiliently(rawText: String, totalDurationMs: Long): List<DetectedScene> {
        val cleaned = GeminiHttp.cleanJsonText(rawText)
        var rawScenes = emptyList<DetectedScene>()

        // 1. Standard JSONObject with "scenes" array
        try {
            val jsonObject = JSONObject(cleaned)
            val scenesArray = jsonObject.optJSONArray("scenes")
            if (scenesArray != null && scenesArray.length() > 0) {
                rawScenes = parseScenesFromJSONArray(scenesArray)
            }
        } catch (e: Exception) {
            logParseFallback("json_object_scenes", e)
        }

        // 2. Direct JSONArray
        if (rawScenes.isEmpty()) {
            try {
                val jsonArray = JSONArray(cleaned)
                if (jsonArray.length() > 0) {
                    rawScenes = parseScenesFromJSONArray(jsonArray)
                }
            } catch (e: Exception) {
                logParseFallback("direct_json_array", e)
            }
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
            } catch (e: Exception) {
                logParseFallback("outer_braces_object", e)
            }
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
            } catch (e: Exception) {
                logParseFallback("outer_brackets_array", e)
            }
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

    private suspend fun detectScenesSinglePass(
        transcriptCues: List<SubtitleCue>,
        totalDurationMs: Long,
        mediaTitle: String,
        resolvedApiKey: String,
        candidateKeys: List<String>,
        language: String,
        modelName: String,
        onProgressUpdate: ((String) -> Unit)?
    ): Result<List<DetectedScene>> {
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
                put("maxOutputTokens", 65536)
                put("thinkingConfig", GeminiHttp.buildFastThinkingConfig())
            })
        }

        val (code, responseBody) = GeminiHttp.executeGeminiPostWithRetry(
            urlBuilder = { m -> "${GeminiModelHealth.BASE_URL}/$m:generateContent" },
            candidateApiKeys = candidateKeys,
            payload = rootJson,
            primaryModel = modelName,
            fallbackModel = GeminiModelHealth.FALLBACK_MODEL,
            maxAttempts = 6,
            client = GeminiHttp.subtitleOkHttpClient,
            language = language,
            onProgressUpdate = onProgressUpdate
        )

        if (code !in 200..299) {
            Log.e(TAG, "Gemini Scene Detection failed: $code -> $responseBody")
            val cleanErrorMsg = GeminiHttp.parseGeminiErrorMessage(code, responseBody, language)
            return Result.failure(Exception(cleanErrorMsg))
        }

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
            val emptyMsg = if (language == "ar") "استجابة نصية فارغة لتقسيم المشاهد." else "Empty text response for scene detection."
            return Result.failure(Exception(emptyMsg))
        }

        val validatedList = parseScenesResiliently(rawText, totalDurationMs)
        if (validatedList.isEmpty()) {
            val noScenesMsg = if (language == "ar") "لم يُرجع الذكاء الاصطناعي أي مشاهد صالحة." else "AI did not return any valid scene items."
            return Result.failure(Exception(noScenesMsg))
        }
        return Result.success(validatedList)
    }

    suspend fun detectScenes(
        transcriptCues: List<SubtitleCue>,
        totalDurationMs: Long,
        mediaTitle: String,
        customApiKey: String? = null,
        language: String = "en",
        modelName: String = GeminiModelHealth.FALLBACK_MODEL,
        onProgressUpdate: ((String) -> Unit)? = null
    ): Result<List<DetectedScene>> = withContext(Dispatchers.IO) {
        try {
            val resolvedApiKey = GeminiKeyPool.resolveApiKey(customApiKey)

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

            val candidateKeys = GeminiKeyPool.resolveCandidateApiKeys(customApiKey)

            val singlePassResult = detectScenesSinglePass(
                transcriptCues = transcriptCues,
                totalDurationMs = totalDurationMs,
                mediaTitle = mediaTitle,
                resolvedApiKey = resolvedApiKey,
                candidateKeys = candidateKeys,
                language = language,
                modelName = modelName,
                onProgressUpdate = onProgressUpdate
            )

            if (singlePassResult.isSuccess) {
                return@withContext singlePassResult
            }

            // Fallback for very long transcripts: split into 2 halves so each half has 50% token load
            if (transcriptCues.size >= 80) {
                onProgressUpdate?.invoke(
                    if (language == "ar") "المقطع طويل، جارٍ تقسيم تحليل المشاهد إلى جزأين لضمان النجاح..."
                    else "Long transcript detected, analyzing scenes in 2 parts for reliability..."
                )
                val midIdx = transcriptCues.size / 2
                val firstCues = transcriptCues.subList(0, midIdx)
                val secondCues = transcriptCues.subList(midIdx, transcriptCues.size)
                val midDurationMs = secondCues.firstOrNull()?.startMs?.takeIf { it > 0 } ?: (totalDurationMs / 2)

                val part1 = detectScenesSinglePass(
                    transcriptCues = firstCues,
                    totalDurationMs = midDurationMs,
                    mediaTitle = "$mediaTitle (Part 1)",
                    resolvedApiKey = resolvedApiKey,
                    candidateKeys = candidateKeys,
                    language = language,
                    modelName = GeminiModelHealth.LITE_FALLBACK_MODEL,
                    onProgressUpdate = onProgressUpdate
                ).getOrNull().orEmpty()

                val part2 = detectScenesSinglePass(
                    transcriptCues = secondCues,
                    totalDurationMs = totalDurationMs,
                    mediaTitle = "$mediaTitle (Part 2)",
                    resolvedApiKey = resolvedApiKey,
                    candidateKeys = candidateKeys,
                    language = language,
                    modelName = GeminiModelHealth.LITE_FALLBACK_MODEL,
                    onProgressUpdate = onProgressUpdate
                ).getOrNull().orEmpty()

                val combined = (part1 + part2.map { scene ->
                    if (scene.startMs < midDurationMs / 2 && midDurationMs > 0) {
                        scene.copy(startMs = scene.startMs + midDurationMs, endMs = scene.endMs + midDurationMs)
                    } else scene
                }).sortedBy { it.startMs }.mapIndexed { idx, scene ->
                    scene.copy(sceneNumber = idx + 1)
                }

                if (combined.isNotEmpty()) {
                    return@withContext Result.success(combined)
                }
            }

            singlePassResult
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
}
