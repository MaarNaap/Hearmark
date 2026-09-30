package com.example

import com.example.ui.SceneJsonParser
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SceneJsonImportTest {

    @Test
    fun testParseValidJsonArray() {
        val json = """
            [
              {
                "sceneNumber": 1,
                "title": "Introduction",
                "startMs": 0,
                "endMs": 15000,
                "summary": "First scene"
              },
              {
                "sceneNumber": 2,
                "title": "Main Content",
                "startMs": 15000,
                "endMs": 60000,
                "summary": "Second scene"
              }
            ]
        """.trimIndent()

        val result = SceneJsonParser.parse(json)
        assertTrue(result.isSuccess)
        val scenes = result.getOrNull()!!
        assertEquals(2, scenes.size)
        assertEquals("Introduction", scenes[0].title)
        assertEquals(0L, scenes[0].startMs)
        assertEquals(15000L, scenes[0].endMs)
        assertEquals("Main Content", scenes[1].title)
    }

    @Test
    fun testParseWithUtf8BomAndMarkdownCodeBlock() {
        val jsonWithBomAndFences = "\uFEFF```json\n" +
            "[\n" +
            "  {\n" +
            "    \"name\": \"Scene with BOM\",\n" +
            "    \"start\": \"00:01:10\",\n" +
            "    \"end\": \"00:02:30\"\n" +
            "  }\n" +
            "]\n" +
            "```"

        val result = SceneJsonParser.parse(jsonWithBomAndFences)
        assertTrue(result.isSuccess)
        val scenes = result.getOrNull()!!
        assertEquals(1, scenes.size)
        assertEquals("Scene with BOM", scenes[0].title)
        assertEquals(70000L, scenes[0].startMs)
        assertEquals(150000L, scenes[0].endMs)
    }

    @Test
    fun testParseObjectWithScenesKey() {
        val json = """
            {
              "track_scenes": [
                {
                  "scene_number": 1,
                  "scene_title": "Chapter 1",
                  "startTime": "01:15",
                  "endTime": "02:45"
                }
              ]
            }
        """.trimIndent()

        val result = SceneJsonParser.parse(json)
        assertTrue(result.isSuccess)
        val scenes = result.getOrNull()!!
        assertEquals(1, scenes.size)
        assertEquals("Chapter 1", scenes[0].title)
        assertEquals(75000L, scenes[0].startMs)
        assertEquals(165000L, scenes[0].endMs)
    }

    @Test
    fun testTimestampParserFormats() {
        assertEquals(0L, SceneJsonParser.parseTimestamp(0))
        assertEquals(45000L, SceneJsonParser.parseTimestamp(45))
        assertEquals(1500L, SceneJsonParser.parseTimestamp(1.5))
        assertEquals(65000L, SceneJsonParser.parseTimestamp("01:05"))
        assertEquals(3665000L, SceneJsonParser.parseTimestamp("01:01:05"))
        assertEquals(1234L, SceneJsonParser.parseTimestamp("1234"))
        assertNull(SceneJsonParser.parseTimestamp(null))
        assertNull(SceneJsonParser.parseTimestamp(""))
    }
}
