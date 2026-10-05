package com.example.ai

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

object GeminiModelHealth {
    private const val TAG = "GeminiModelHealth"

    const val DEFAULT_MODEL = "gemini-3.5-flash"
    const val FALLBACK_MODEL = "gemini-flash-latest"
    const val LITE_FALLBACK_MODEL = "gemini-3.1-flash-lite-preview"
    const val STABLE_FLASH_MODEL = "gemini-2.5-flash"
    const val STABLE_LITE_MODEL = "gemini-flash-lite-latest"
    const val PRO_FALLBACK_MODEL = "gemini-3.1-pro-preview"
    const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    private const val LIST_MODELS_TTL_MS = 6 * 60 * 60 * 1000L // 6 hours

    val MODEL_FALLBACK_CHAIN = listOf(
        DEFAULT_MODEL,
        FALLBACK_MODEL,
        LITE_FALLBACK_MODEL,
        STABLE_FLASH_MODEL,
        STABLE_LITE_MODEL,
        PRO_FALLBACK_MODEL
    )

    // Smart Model Health & Cooldown Registry
    private val modelCooldownUntilMs = ConcurrentHashMap<String, Long>()
    @Volatile private var lastHealthyModel: String? = null
    @Volatile private var lastHealthyModelTimestampMs: Long = 0L
    @Volatile private var lastListModelsCheckMs: Long = 0L

    /**
     * Parses a Gemini `ListModels` JSON response (`GET /v1beta/models`) and places any model
     * from [MODEL_FALLBACK_CHAIN] that is absent or does not support `generateContent` into cooldown.
     */
    fun applyLiveModelsCatalog(
        responseBody: String,
        nowMs: Long = System.currentTimeMillis()
    ): Set<String> {
        val availableModels = mutableSetOf<String>()
        try {
            val root = JSONObject(responseBody)
            val modelsArr = root.optJSONArray("models") ?: return emptySet()
            for (i in 0 until modelsArr.length()) {
                val modelObj = modelsArr.optJSONObject(i) ?: continue
                val rawName = modelObj.optString("name", "").trim()
                if (rawName.isBlank()) continue
                val shortName = rawName.removePrefix("models/").trim()

                val methodsArr = modelObj.optJSONArray("supportedGenerationMethods")
                val supportsGenerate = if (methodsArr == null || methodsArr.length() == 0) {
                    true
                } else {
                    var found = false
                    for (j in 0 until methodsArr.length()) {
                        if (methodsArr.optString(j) == "generateContent") {
                            found = true
                            break
                        }
                    }
                    found
                }

                if (supportsGenerate && shortName.isNotBlank()) {
                    availableModels.add(shortName)
                }
            }

            if (availableModels.isNotEmpty()) {
                for (candidate in MODEL_FALLBACK_CHAIN) {
                    if (candidate !in availableModels) {
                        modelCooldownUntilMs[candidate] = nowMs + LIST_MODELS_TTL_MS
                        if (lastHealthyModel == candidate) {
                            lastHealthyModel = null
                        }
                    }
                }
            }
        } catch (e: Exception) {
            try {
                Log.w(TAG, "Failed to parse ListModels response: ${e.message}")
            } catch (_: Throwable) {}
        }
        return availableModels
    }

    /**
     * Queries `GET https://generativelanguage.googleapis.com/v1beta/models` at most once per 6 hours
     * to proactively filter out retired or unavailable preview models from [MODEL_FALLBACK_CHAIN].
     */
    fun refreshAvailableModelsIfNeeded(
        apiKey: String,
        client: OkHttpClient,
        nowMs: Long = System.currentTimeMillis()
    ) {
        if (apiKey.isBlank()) return
        if (lastListModelsCheckMs > 0L && (nowMs - lastListModelsCheckMs) < LIST_MODELS_TTL_MS) {
            return
        }
        lastListModelsCheckMs = nowMs
        try {
            val request = Request.Builder()
                .url(BASE_URL)
                .addHeader("x-goog-api-key", apiKey)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (body.isNotBlank()) {
                        applyLiveModelsCatalog(body, nowMs)
                    }
                }
            }
        } catch (e: Exception) {
            try {
                Log.w(TAG, "Live ListModels health check skipped: ${e.message}")
            } catch (_: Throwable) {}
        }
    }

    fun selectOrderedCandidateModels(
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

    fun markModelSuccess(model: String, nowMs: Long = System.currentTimeMillis()) {
        modelCooldownUntilMs.remove(model)
        lastHealthyModel = model
        lastHealthyModelTimestampMs = nowMs
    }

    fun markModelFailure(model: String, httpCode: Int, nowMs: Long = System.currentTimeMillis()) {
        val cooldownDurationMs = when {
            httpCode == 404 -> 6 * 60 * 60 * 1000L // 6 hours for non-existent model endpoint
            httpCode == 400 -> 15 * 60 * 1000L
            httpCode == 503 || httpCode == 429 || httpCode in 500..599 -> 60 * 1000L // 60 seconds for overloaded/rate-limited pool
            else -> 30 * 1000L
        }
        modelCooldownUntilMs[model] = nowMs + cooldownDurationMs
        if (lastHealthyModel == model) {
            lastHealthyModel = null
        }
    }

    fun clearModelHealthStateForTesting() {
        modelCooldownUntilMs.clear()
        lastHealthyModel = null
        lastHealthyModelTimestampMs = 0L
        lastListModelsCheckMs = 0L
    }
}
