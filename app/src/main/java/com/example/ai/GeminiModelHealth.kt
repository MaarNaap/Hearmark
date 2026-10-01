package com.example.ai

import java.util.concurrent.ConcurrentHashMap

object GeminiModelHealth {
    const val DEFAULT_MODEL = "gemini-3.5-flash"
    const val FALLBACK_MODEL = "gemini-flash-latest"
    const val LITE_FALLBACK_MODEL = "gemini-3.1-flash-lite-preview"
    const val STABLE_FLASH_MODEL = "gemini-2.5-flash"
    const val STABLE_LITE_MODEL = "gemini-flash-lite-latest"
    const val PRO_FALLBACK_MODEL = "gemini-3.1-pro-preview"
    const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

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
    }
}
