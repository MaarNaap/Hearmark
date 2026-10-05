package com.example

import com.example.ai.*
import com.example.player.SubtitleCue
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeminiQuizAndHttpRetryTest {

    private val dummyTranscriptSource = QuizContentSource.Transcript(
        mediaTitle = "Test Audio",
        cues = listOf(SubtitleCue(1, 0L, 5000L, "Hello world")),
        existingQuestions = emptyList()
    )

    private fun wrapCandidateJson(innerText: String, finishReason: String = "STOP"): String {
        return JSONObject().apply {
            put("candidates", JSONArray().apply {
                put(JSONObject().apply {
                    put("finishReason", finishReason)
                    put("content", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", innerText)
                            })
                        })
                    })
                })
            })
        }.toString()
    }

    @Test
    fun parseQuizQuestionsFromResponse_acceptsValidAndRejectsInvalidCorrectIndex() {
        val quizJson = """
            {
              "questions": [
                {
                  "type": "MCQ",
                  "category": "COMPREHENSION",
                  "question": "Valid question?",
                  "options": ["Option A", "Option B", "Option C"],
                  "correctIndex": 2,
                  "explanation": "Option C is right"
                },
                {
                  "type": "MCQ",
                  "category": "COMPREHENSION",
                  "question": "Out of bounds index question?",
                  "options": ["Option A", "Option B"],
                  "correctIndex": 5
                },
                {
                  "type": "MCQ",
                  "category": "COMPREHENSION",
                  "question": "Missing correctIndex question?",
                  "options": ["Option A", "Option B"]
                },
                {
                  "type": "MCQ",
                  "category": "COMPREHENSION",
                  "question": "Duplicate options leaving only 1 unique?",
                  "options": ["Same", "same"]
                }
              ]
            }
        """.trimIndent()

        val responseBody = wrapCandidateJson(quizJson)
        val result = GeminiQuiz.parseQuizQuestionsFromResponse(
            responseBody = responseBody,
            source = dummyTranscriptSource,
            notesMap = emptyMap(),
            language = "en"
        )

        assertTrue(result.isSuccess)
        val items = result.getOrThrow()
        assertEquals(1, items.size)
        assertEquals("Valid question?", items[0].question)
        assertEquals(2, items[0].correctIndex)
    }

    @Test
    fun parseQuizQuestionsFromResponse_handlesSafetyBlockAndMaxTokens() {
        val safetyBlockedJson = JSONObject().apply {
            put("promptFeedback", JSONObject().apply {
                put("blockReason", "SAFETY")
            })
        }.toString()

        val blockedResult = GeminiQuiz.parseQuizQuestionsFromResponse(
            responseBody = safetyBlockedJson,
            source = dummyTranscriptSource,
            notesMap = emptyMap(),
            language = "ar"
        )
        assertTrue(blockedResult.isFailure)
        assertTrue(blockedResult.exceptionOrNull()?.message?.contains("SAFETY") == true)

        val maxTokensCheck = GeminiHttp.checkCandidateBlockOrFinishReason(
            JSONObject(wrapCandidateJson("Partial answer", finishReason = "MAX_TOKENS")),
            language = "en"
        )
        assertNull(maxTokensCheck.blockedErrorMessage)
        assertTrue(maxTokensCheck.isTruncatedByMaxTokens)
    }

    @Test
    fun applyLiveModelsCatalog_placesUnavailablePreviewModelsInCooldown() {
        GeminiModelHealth.clearModelHealthStateForTesting()
        val now = 5_000_000L

        val listModelsBody = """
            {
              "models": [
                {
                  "name": "models/gemini-2.5-flash",
                  "supportedGenerationMethods": ["generateContent", "countTokens"]
                },
                {
                  "name": "models/gemini-flash-latest",
                  "supportedGenerationMethods": ["generateContent"]
                },
                {
                  "name": "models/text-embedding-004",
                  "supportedGenerationMethods": ["embedContent"]
                }
              ]
            }
        """.trimIndent()

        val liveSet = GeminiModelHealth.applyLiveModelsCatalog(listModelsBody, nowMs = now)
        assertTrue(liveSet.contains("gemini-2.5-flash"))
        assertTrue(liveSet.contains("gemini-flash-latest"))
        assertFalse(liveSet.contains("text-embedding-004"))

        val ordered = GeminiModelHealth.selectOrderedCandidateModels(nowMs = now + 100)
        // Live models should come ahead of unavailable preview models placed in cooldown
        assertEquals("gemini-flash-latest", ordered[0])
        assertEquals("gemini-2.5-flash", ordered[1])

        GeminiModelHealth.clearModelHealthStateForTesting()
    }

    @Test
    fun executeGeminiPostWithRetry_sendsHeaderKeyAndStripsThinkingOn400() = runBlocking {
        GeminiModelHealth.clearModelHealthStateForTesting()
        val recordedHeaderKeys = mutableListOf<String?>()
        val recordedUrls = mutableListOf<String>()
        var callCount = 0

        val testClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                callCount++
                val req = chain.request()
                recordedUrls.add(req.url.toString())
                recordedHeaderKeys.add(req.header("x-goog-api-key"))

                val (code, bodyStr) = if (callCount == 1) {
                    400 to """{"error":{"message":"thinkingConfig is not supported"}}"""
                } else {
                    200 to """{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"OK"}]}}]}"""
                }

                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code == 200) "OK" else "Bad Request")
                    .body(bodyStr.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val payload = JSONObject().apply {
            put("generationConfig", JSONObject().apply {
                put("thinkingConfig", GeminiHttp.buildFastThinkingConfig())
            })
        }

        val (statusCode, respBody) = GeminiHttp.executeGeminiPostWithRetry(
            urlBuilder = { m -> "${GeminiModelHealth.BASE_URL}/$m:generateContent" },
            payload = payload,
            candidateApiKeys = listOf("test-secret-key-123"),
            maxAttempts = 3,
            client = testClient
        )

        assertEquals(200, statusCode)
        assertTrue(respBody.contains("OK"))
        assertEquals(2, callCount)
        assertTrue(recordedUrls.all { !it.contains("?key=") })
        assertTrue(recordedHeaderKeys.all { it == "test-secret-key-123" })
        GeminiModelHealth.clearModelHealthStateForTesting()
    }
}
