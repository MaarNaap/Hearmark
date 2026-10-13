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

    @Test
    fun quizFocusMode_buildsCorrectPromptAndEnforcesCategory() {
        val vocabOnlySource = dummyTranscriptSource.copy(questionCount = 4, focusMode = QuizFocusMode.VOCABULARY_ONLY)
        val (_, vocabPrompt) = GeminiPrompts.buildUnifiedQuizPrompt(vocabOnlySource, "en")
        assertTrue(vocabPrompt.contains("EXACTLY 4 KEY VOCABULARY questions"))
        assertTrue(vocabPrompt.contains("ZERO comprehension questions"))

        val compOnlySource = dummyTranscriptSource.copy(questionCount = 4, focusMode = QuizFocusMode.COMPREHENSION_ONLY)
        val (_, compPrompt) = GeminiPrompts.buildUnifiedQuizPrompt(compOnlySource, "en")
        assertTrue(compPrompt.contains("EXACTLY 4 LISTENING COMPREHENSION questions"))
        assertTrue(compPrompt.contains("ZERO vocabulary questions"))

        val bothSource = dummyTranscriptSource.copy(questionCount = 4, focusMode = QuizFocusMode.BOTH)
        val (_, bothPrompt) = GeminiPrompts.buildUnifiedQuizPrompt(bothSource, "en")
        assertTrue(bothPrompt.contains("EXACTLY 2 KEY VOCABULARY questions"))
        assertTrue(bothPrompt.contains("EXACTLY 2 LISTENING COMPREHENSION questions"))

        val rawResponse = wrapCandidateJson(
            """
            {
              "questions": [
                {
                  "type": "MCQ",
                  "question": "What does the word 'resilient' mean?",
                  "options": ["Strong", "Weak", "Slow", "Fast"],
                  "correctIndex": 0,
                  "explanation": "Exp"
                }
              ]
            }
            """.trimIndent()
        )

        val parsedCompOnly = GeminiQuiz.parseQuizQuestionsFromResponse(
            responseBody = rawResponse,
            source = compOnlySource,
            notesMap = emptyMap()
        ).getOrThrow()
        assertEquals("COMPREHENSION", parsedCompOnly[0].category)

        val parsedVocabOnly = GeminiQuiz.parseQuizQuestionsFromResponse(
            responseBody = rawResponse,
            source = vocabOnlySource,
            notesMap = emptyMap()
        ).getOrThrow()
        assertEquals("VOCABULARY", parsedVocabOnly[0].category)
    }

    @Test
    fun vocabularyPrompt_enforcesIsolationAndNoExternalDialogueReferences() {
        val vocabSource = dummyTranscriptSource.copy(questionCount = 3, focusMode = QuizFocusMode.VOCABULARY_ONLY)
        val (_, transcriptPrompt) = GeminiPrompts.buildUnifiedQuizPrompt(vocabSource, "en")

        assertTrue(transcriptPrompt.contains("MANDATORY VOCABULARY ISOLATION"))
        assertTrue(transcriptPrompt.contains("ZERO EXTERNAL DIALOGUE REFERENCES"))
        assertTrue(transcriptPrompt.contains("PRESERVE TARGET SENSE & MEANING"))
        assertTrue(transcriptPrompt.contains("FLEXIBLE CONTEXT SENTENCE GENERATION"))
        assertTrue(transcriptPrompt.contains("FORBIDDEN: 'in this dialogue'"))

        // Test Arabic mandate
        val (_, arabicPrompt) = GeminiPrompts.buildUnifiedQuizPrompt(vocabSource, "ar")
        assertTrue(arabicPrompt.contains("STRICT ARABIC ISOLATION MANDATE"))
        assertTrue(arabicPrompt.contains("ممنوع: 'في هذا الحوار'"))

        // Test NotebookNotes prompt isolation and removal of Source Lesson
        val notesSource = QuizContentSource.NotebookNotes(
            notes = listOf(
                NoteInputForQuiz(
                    id = 101L,
                    text = "meticulous",
                    comment = "very careful",
                    tags = listOf("vocab"),
                    trackId = 5L,
                    trackName = "Dialogue_01_Audio",
                    startTimestampMs = 12000L,
                    targetWord = "meticulous",
                    meaning = "very careful and precise",
                    contextSentence = "She was meticulous in her work."
                )
            ),
            maxQuestions = 2
        )
        val (_, notesPrompt) = GeminiPrompts.buildUnifiedQuizPrompt(notesSource, "en")
        assertFalse(
            "Prompt must not leak Source Lesson to prevent dialogue referencing",
            notesPrompt.contains("Source Lesson:")
        )
        assertTrue(notesPrompt.contains("CRITICAL VOCABULARY ISOLATION & SENSE RETENTION MANDATE"))
        assertTrue(notesPrompt.contains("FLEXIBLE CONTEXT SENTENCE GENERATION"))
    }

    @Test
    fun quizSanitizer_stripsExternalDialoguePrefixesAndMidSentenceReferences() {
        // Test prefix stripping
        assertEquals(
            "What does the word 'resilient' mean?",
            QuizSanitizer.sanitizeVocabularyQuestion(
                rawQuestion = "In this dialogue, what does the word 'resilient' mean?",
                targetWord = "resilient",
                contextSentence = null
            )
        )
        assertEquals(
            "What does 'pilot' mean?",
            QuizSanitizer.sanitizeVocabularyQuestion(
                rawQuestion = "According to the speaker, what does 'pilot' mean?",
                targetWord = "pilot",
                contextSentence = null
            )
        )
        assertEquals(
            "Which word means 'agreement'?",
            QuizSanitizer.sanitizeVocabularyQuestion(
                rawQuestion = "Which word in this dialogue means 'agreement'?",
                targetWord = null,
                contextSentence = null
            )
        )

        // Test Arabic prefix stripping
        assertEquals(
            "ما معنى كلمة 'resilient'؟",
            QuizSanitizer.sanitizeVocabularyQuestion(
                rawQuestion = "في هذا الحوار، ما معنى كلمة 'resilient'؟",
                targetWord = "resilient",
                contextSentence = null
            )
        )

        // Test auto-embedding when asking about context specifically without providing it
        val withEmbeddedSentence = QuizSanitizer.sanitizeVocabularyQuestion(
            rawQuestion = "What does the word 'strike' mean in this context?",
            targetWord = "strike",
            contextSentence = "The union decided to strike after talks failed."
        )
        assertEquals(
            "In the sentence: \"The union decided to strike after talks failed.\", what does the word \"strike\" mean?",
            withEmbeddedSentence
        )

        // Test explanation sanitizing
        val cleanedExplanation = QuizSanitizer.sanitizeExplanation(
            "In this context, 'pilot' means an experimental trial, as used when the speaker describes the new project."
        )
        assertEquals(
            "'Pilot' means an experimental trial.",
            cleanedExplanation
        )

        val cleanedArabicExp = QuizSanitizer.sanitizeExplanation(
            "تعني الكلمة برنامجاً تجريبياً، كما ذكر المتحدث في الحوار."
        )
        assertEquals(
            "تعني الكلمة برنامجاً تجريبياً.",
            cleanedArabicExp
        )
    }

    @Test
    fun parseQuizQuestionsFromResponse_extractsContextSentenceFromQuestion_andSanitizes() {
        val rawResponse = wrapCandidateJson(
            """
            {
              "questions": [
                {
                  "category": "VOCABULARY",
                  "type": "MCQ",
                  "question": "In this dialogue, in the sentence: 'The committee launched a pilot project before the rollout', what does 'pilot' mean?",
                  "options": ["Experimental trial", "Airplane driver", "Guide", "Announcer"],
                  "correctIndex": 0,
                  "explanation": "'Pilot' means experimental trial, as mentioned by the speaker in the dialogue.",
                  "targetWord": "pilot"
                }
              ]
            }
            """.trimIndent()
        )

        val parsed = GeminiQuiz.parseQuizQuestionsFromResponse(
            responseBody = rawResponse,
            source = dummyTranscriptSource.copy(focusMode = QuizFocusMode.VOCABULARY_ONLY),
            notesMap = emptyMap()
        ).getOrThrow()

        val item = parsed[0]
        assertEquals("VOCABULARY", item.category)
        assertEquals(
            "In the sentence: 'The committee launched a pilot project before the rollout', what does 'pilot' mean?",
            item.question
        )
        assertEquals(
            "'Pilot' means experimental trial.",
            item.explanation
        )
        assertEquals(
            "The committee launched a pilot project before the rollout",
            item.contextSentence
        )
    }
}


