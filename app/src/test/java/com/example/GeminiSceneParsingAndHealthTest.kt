package com.example

import com.example.ai.GeminiService
import org.junit.Assert.*
import org.junit.Test

class GeminiSceneParsingAndHealthTest {

    @Test
    fun geminiService_parseScenesResiliently_handlesStandardJson() {
        val json = """
            {
              "scenes": [
                {
                  "sceneNumber": 1,
                  "title": "Meeting at the cafe",
                  "startMs": 0,
                  "endMs": 120000,
                  "summary": "Characters meet and talk."
                },
                {
                  "sceneNumber": 2,
                  "title": "Walking home",
                  "startMs": 120000,
                  "endMs": 300000,
                  "summary": "Walking and discussing tomorrow."
                }
              ]
            }
        """.trimIndent()

        val scenes = GeminiService.parseScenesResiliently(json, 300000L)
        assertEquals(2, scenes.size)
        assertEquals("Meeting at the cafe", scenes[0].title)
        assertEquals(0L, scenes[0].startMs)
        assertEquals(120000L, scenes[0].endMs)
        assertEquals("Walking home", scenes[1].title)
    }

    @Test
    fun geminiService_parseScenesResiliently_handlesMarkdownAndIntroText() {
        val markdown = """
            Here are the extracted scenes for this video:
            ```json
            [
              {
                "sceneNumber": 1,
                "title": "001 - Introduction",
                "startMs": 0,
                "endMs": 60000,
                "summary": "Opening speech"
              },
              {
                "sceneNumber": 2,
                "title": "002 - Main Lesson",
                "startMs": 60000,
                "endMs": 180000,
                "summary": "Explanation"
              }
            ]
            ```
            Hope this helps!
        """.trimIndent()

        val scenes = GeminiService.parseScenesResiliently(markdown, 180000L)
        assertEquals(2, scenes.size)
        assertEquals("Introduction", scenes[0].title)
        assertEquals("Main Lesson", scenes[1].title)
    }

    @Test
    fun geminiService_parseScenesResiliently_recoversTruncatedJson() {
        val truncatedJson = """
            {
              "scenes": [
                {
                  "sceneNumber": 1,
                  "title": "Scene One",
                  "startMs": 0,
                  "endMs": 90000
                },
                {
                  "sceneNumber": 2,
                  "title": "Scene Two",
                  "startMs": 90000,
                  "endMs": 240000
                },
                {
                  "sceneNumber": 3,
                  "title": "Scene Three",
                  "startMs": 240000
        """.trimIndent()

        val scenes = GeminiService.parseScenesResiliently(truncatedJson, 300000L)
        assertTrue("Should recover at least the completed scenes before truncation", scenes.size >= 2)
        assertEquals("Scene One", scenes[0].title)
        assertEquals("Scene Two", scenes[1].title)
    }

    @Test
    fun geminiService_modelHealthCooldown_rotatesAndPrioritizesHealthyModels() {
        GeminiService.clearModelHealthStateForTesting()
        val now = 1_000_000L

        val initialOrder = GeminiService.selectOrderedCandidateModels(nowMs = now)
        assertEquals(GeminiService.DEFAULT_MODEL, initialOrder.first())
        assertTrue(initialOrder.size >= 4)

        GeminiService.markModelFailure(GeminiService.DEFAULT_MODEL, 503, nowMs = now)
        GeminiService.markModelFailure(GeminiService.FALLBACK_MODEL, 503, nowMs = now + 100)

        val afterFailureOrder = GeminiService.selectOrderedCandidateModels(nowMs = now + 200)
        assertEquals(GeminiService.LITE_FALLBACK_MODEL, afterFailureOrder.first())

        GeminiService.markModelSuccess(GeminiService.LITE_FALLBACK_MODEL, nowMs = now + 300)
        val afterSuccessOrder = GeminiService.selectOrderedCandidateModels(nowMs = now + 120_000L)
        assertEquals(GeminiService.LITE_FALLBACK_MODEL, afterSuccessOrder.first())

        GeminiService.clearModelHealthStateForTesting()
    }
}
