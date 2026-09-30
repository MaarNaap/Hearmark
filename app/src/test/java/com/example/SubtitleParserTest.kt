package com.example

import com.example.data.Note
import com.example.player.SubtitleCue
import com.example.player.SubtitleParser
import org.junit.Assert.*
import org.junit.Test

class SubtitleParserTest {

    @Test
    fun parseContent_parsesSrtAndappliesOffset() {
        val srt = """
            1
            00:00:01,500 --> 00:00:04,000
            <i>Hello world!</i>

            2
            00:00:05,000 --> 00:00:08,250
            Welcome to the lesson.
        """.trimIndent()

        val cues = SubtitleParser.parseContent(srt, offsetMs = 500L)
        assertEquals(2, cues.size)
        assertEquals("Hello world!", cues[0].text)
        assertEquals(2000L, cues[0].startMs)
        // Chained endMs to next cue's startMs (5000 + 500 = 5500)
        assertEquals(5500L, cues[0].endMs)
        assertEquals("Welcome to the lesson.", cues[1].text)
        assertEquals(5500L, cues[1].startMs)
    }

    @Test
    fun parseContent_parsesLrcWithInlineAndStandaloneTimestamps() {
        val lrc = """
            [ti:Sample Song]
            [ar:Hearmark]
            [00:02.50]First line of lyrics
            [00:06.00]
            Second line on next row
        """.trimIndent()

        val cues = SubtitleParser.parseContent(lrc)
        assertEquals(2, cues.size)
        assertEquals(2500L, cues[0].startMs)
        assertEquals("First line of lyrics", cues[0].text)
        assertEquals(6000L, cues[1].startMs)
        assertEquals("Second line on next row", cues[1].text)
    }

    @Test
    fun findDedicatedCueForNote_matchesOriginAndMultiCueSpans() {
        val cues = listOf(
            SubtitleCue(id = 1, startMs = 1000L, endMs = 4000L, text = "Cue 1"),
            SubtitleCue(id = 2, startMs = 4000L, endMs = 8000L, text = "Cue 2"),
            SubtitleCue(id = 3, startMs = 8000L, endMs = 12000L, text = "Cue 3")
        )

        val noteWithOrigin = Note(
            id = 1L,
            trackId = 1L,
            trackName = "test.mp3",
            startTimestampMs = 1000L,
            endTimestampMs = 12000L,
            originStartMs = 4500L,
            text = "Cue 2",
            comment = "Note on cue 2"
        )
        assertEquals(cues[1], SubtitleParser.findDedicatedCueForNote(noteWithOrigin, cues))

        // Legacy 3-cue context note without originStartMs -> selects middle cue (index 1)
        val legacyThreeCueNote = noteWithOrigin.copy(originStartMs = null)
        assertEquals(cues[1], SubtitleParser.findDedicatedCueForNote(legacyThreeCueNote, cues))
    }

    @Test
    fun noteVocabularyIsolation_extractsTargetWordMeaningAndContext() {
        val note = Note(
            id = 2L,
            text = "ephemeral moment",
            comment = "[ephemeral]: lasting for a very short time\n• Context in Audio: It was an ephemeral moment of clarity.",
            tags = "vocab, gre , study"
        )
        assertEquals("ephemeral", note.getIsolatedTargetWord())
        assertEquals("lasting for a very short time", note.getIsolatedMeaning())
        assertEquals("It was an ephemeral moment of clarity.", note.getIsolatedContextSentence())
        assertEquals(listOf("vocab", "gre", "study"), note.getTagsList())
    }

    @Test
    fun exportCuesToSrt_andExtractCleanText_roundTrip() {
        val cues = listOf(
            SubtitleCue(id = 1, startMs = 1000L, endMs = 3500L, text = "First sentence."),
            SubtitleCue(id = 2, startMs = 4000L, endMs = 7000L, text = "Second sentence.")
        )
        val srt = SubtitleParser.exportCuesToSrt(cues)
        assertTrue(srt.contains("00:00:01,000 --> 00:00:03,500"))
        assertTrue(srt.contains("First sentence."))

        val cleanText = SubtitleParser.extractCleanTextFromReference(srt)
        assertEquals("First sentence.\nSecond sentence.", cleanText)
    }

    @Test
    fun isVideoFile_recognizesVideoContainersOnly() {
        assertTrue(SubtitleParser.isVideoFile("/movies/clip.mp4"))
        assertTrue(SubtitleParser.isVideoFile("/movies/lecture.MKV"))
        assertFalse(SubtitleParser.isVideoFile("/audio/podcast.mp3"))
        assertFalse(SubtitleParser.isVideoFile("/audio/voice.m4a"))
    }
}
