package com.example.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object GeminiChat {
    private const val TAG = "GeminiService"

    suspend fun sendMessage(
        history: List<ChatMessage>,
        newUserMessage: String,
        contextSummary: AudioContextSummary? = null,
        customApiKey: String? = null,
        language: String = "en",
        modelName: String = GeminiModelHealth.DEFAULT_MODEL,
        customSystemInstruction: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val resolvedApiKey = GeminiKeyPool.resolveApiKey(customApiKey)

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
            val isVocabNoteRequest = customSystemInstruction == GeminiPrompts.VOCAB_NOTE_SYSTEM_PROMPT
            val genConfig = JSONObject().apply {
                put("temperature", if (isVocabNoteRequest) 0.3 else 0.7)
                put("topP", 0.95)
                put("topK", 40)
                put("maxOutputTokens", if (isVocabNoteRequest) 2048 else 8192)
                if (isVocabNoteRequest) {
                    put("thinkingConfig", GeminiHttp.buildFastThinkingConfig())
                }
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
                Log.e(TAG, "Gemini API call failed with code: $code, body: $responseBody")
                val cleanErrorMsg = GeminiHttp.parseGeminiErrorMessage(code, responseBody, language)
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
            val textResult = GeminiHttp.extractNonThoughtTextFromParts(parts)

            if (textResult.isBlank()) {
                return@withContext Result.failure(Exception("Empty text in Gemini response."))
            }

            Result.success(textResult)
        } catch (e: Exception) {
            Log.e(TAG, "Error in Gemini sendMessage", e)
            Result.failure(e)
        }
    }
}
