package com.example.ai

import com.example.BuildConfig

object GeminiKeyPool {
    // Multi-API Key Failover Pool (synced from Settings)
    @Volatile private var configuredBackupApiKeys: List<String> = emptyList()

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

    fun resolveApiKey(customApiKey: String?): String {
        return when {
            !customApiKey.isNullOrBlank() -> customApiKey.trim()
            try { BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY" } catch (e: Exception) { false } -> BuildConfig.GEMINI_API_KEY
            else -> ""
        }
    }
}
