package com.example

import com.example.player.SubtitleParser
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleChainingTest {

    @Test
    fun testYouTubeOverlappingCuesChainedToNextStartTime() {
        val srtContent = """
            1
            00:00:00,000 --> 00:00:03,439
            I grew up in a household that was very

            2
            00:00:01,760 --> 00:00:04,080
            stressful and chaotic and there wasn't

            3
            00:00:03,439 --> 00:00:05,520
            stability.

            4
            00:00:04,080 --> 00:00:07,839
            So did my kids by the way.
        """.trimIndent()

        val cues = SubtitleParser.parseContent(srtContent)
        assertEquals(4, cues.size)

        // Cue 1 end must equal Cue 2 start (1760ms)
        assertEquals(0L, cues[0].startMs)
        assertEquals(1760L, cues[0].endMs)

        // Cue 2 end must equal Cue 3 start (3439ms)
        assertEquals(1760L, cues[1].startMs)
        assertEquals(3439L, cues[1].endMs)

        // Cue 3 end must equal Cue 4 start (4080ms)
        assertEquals(3439L, cues[2].startMs)
        assertEquals(4080L, cues[2].endMs)

        // Last cue keeps its end time (7839ms)
        assertEquals(4080L, cues[3].startMs)
        assertEquals(7839L, cues[3].endMs)
    }
}
