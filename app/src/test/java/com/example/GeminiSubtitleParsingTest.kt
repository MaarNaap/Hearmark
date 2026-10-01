package com.example

import com.example.ai.GeminiService
import com.example.ai.GeminiSubtitleParsing
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeminiSubtitleParsingTest {

    @Test
    fun cleanSrtOutput_stripsMarkdownFencesAndConversationalPreamble() {
        val raw = """
            Sure! Here is the generated SRT file for your audio track:
            ```srt
            1
            00:00:01,000 --> 00:00:04,500
            Welcome to the first lesson.

            2
            00:00:05,000 --> 00:00:08,200
            Let's begin with a quick review.
            ```
            Let me know if you need adjustments!
        """.trimIndent()

        val cleaned = GeminiSubtitleParsing.cleanSrtOutput(raw)
        assertFalse("Should not contain ```srt fence", cleaned.contains("```"))
        assertFalse("Should not contain conversational preamble", cleaned.contains("Sure!"))
        assertFalse("Should not contain trailing commentary", cleaned.contains("Let me know"))
        assertTrue("Should start at cue 1", cleaned.startsWith("1"))
        assertTrue("Should preserve timestamps and text", cleaned.contains("00:00:01,000 --> 00:00:04,500"))
        assertEquals(cleaned, GeminiService.cleanSrtOutput(raw))
    }

    @Test
    fun cleanSrtOutput_startsAtTimestampWhenCueIndexIsOmitted() {
        val raw = """
            Generated output:
            00:00:00,500 --> 00:00:03,000
            Direct timestamp cue without index 1.
        """.trimIndent()

        val cleaned = GeminiSubtitleParsing.cleanSrtOutput(raw)
        assertTrue(cleaned.startsWith("00:00:00,500 --> 00:00:03,000"))
        assertTrue(cleaned.contains("Direct timestamp cue without index 1."))
    }

    @Test
    fun parseSseSubtitleStream_concatenatesChunksFiltersThoughtsAndReportsProgress() {
        val ssePayload = """
            data: {"candidates":[{"content":{"parts":[{"text":"Internal reasoning...","thought":true},{"text":"1\n00:00:00,000 --> 00:00:02,500\nFirst cue line\n\n"}]}}]}

            data: {"candidates":[{"content":{"parts":[{"text":"2\n00:00:02,500 --> 00:00:05,000\nSecond cue line\n\n"}]}}]}

            data: [DONE]
        """.trimIndent()

        val responseBody = ssePayload.toResponseBody("text/event-stream".toMediaType())
        val progressMessages = mutableListOf<String>()

        val result = GeminiSubtitleParsing.parseSseSubtitleStream(
            body = responseBody,
            language = "en",
            chunkIdx = 0,
            totalChunks = 2,
            accumulatedCuesCount = 5,
            onProgressUpdate = { progressMessages.add(it) }
        )

        assertFalse("Should filter out thought parts", result.contains("Internal reasoning"))
        assertTrue(result.contains("First cue line"))
        assertTrue(result.contains("Second cue line"))
        assertEquals(2, progressMessages.size)
        assertTrue(progressMessages[0].contains("Syncing chunk (1/2)"))
        assertTrue(progressMessages[0].contains("(6 cues synced)"))
        assertTrue(progressMessages[1].contains("(7 cues synced)"))
    }

    @Test
    fun parseSseSubtitleStream_handlesArabicSingleChunkAndMalformedLinesResiliently() {
        val ssePayload = """
            data: {malformed_json_that_should_be_ignored}
            data: {"candidates":[{"content":{"parts":[{"text":"1\n00:00:01,000 --> 00:00:03,000\nمرحباً بكم\n\n"}]}}]}
            data: [DONE]
        """.trimIndent()

        val responseBody = ssePayload.toResponseBody("text/event-stream".toMediaType())
        val progressMessages = mutableListOf<String>()

        val result = GeminiSubtitleParsing.parseSseSubtitleStream(
            body = responseBody,
            language = "ar",
            chunkIdx = 0,
            totalChunks = 1,
            accumulatedCuesCount = 0,
            onProgressUpdate = { progressMessages.add(it) }
        )

        assertTrue(result.contains("مرحباً بكم"))
        assertEquals(1, progressMessages.size)
        assertTrue(progressMessages[0].contains("جارٍ توليد ومزامنة الترجمة"))
        assertTrue(progressMessages[0].contains("(1 مقطعاً)"))
    }
}
