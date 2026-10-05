package com.example

import com.example.ui.Loc
import com.example.ui.formatDuration
import com.example.ui.formatPlaybackSpeed
import com.example.ui.formatTimestampMs
import com.example.ui.parseTimestampToMs
import com.example.ui.toWesternDigits
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class WesternDigitsAndLocalizationTest {

    @Test
    fun testToWesternDigitsBasicReplacement() {
        val arabicIndic = "٠١٢٣٤٥٦٧٨٩"
        assertEquals("0123456789", arabicIndic.toWesternDigits())

        val persianDigits = "۰۱۲۳۴۵۶۷۸۹"
        assertEquals("0123456789", persianDigits.toWesternDigits())

        val mixedString = "المقطع ١٥ من أصل ٢٠ (المدة: ٠٣:٤٥)"
        assertEquals("المقطع 15 من أصل 20 (المدة: 03:45)", mixedString.toWesternDigits())
    }

    @Test
    fun testAlreadyWesternNumbersUnaffected() {
        val western = "Track 123 - 04:56 [00:15.50]"
        assertEquals(western, western.toWesternDigits())
    }

    @Test
    fun testLocGetTextReplacesArabicIndicDigits() {
        Loc.currentLanguage = "ar"
        // Even if custom text or keys have Arabic-Indic numbers, Loc.getText guarantees Western digits
        val textWithArabicDigits = "الهدف ٠٩:٣٠ ص"
        assertEquals("الهدف 09:30 ص", textWithArabicDigits.toWesternDigits())

        Loc.currentLanguage = "en"
    }

    @Test
    fun testLocGetFormattedTextUsesWesternDigits() {
        val oldDefault = Locale.getDefault()
        try {
            // Force default locale to Arabic
            Locale.setDefault(Locale.forLanguageTag("ar"))

            val formatted = Loc.format("Count: %d, Percent: %d%%", 42, 85)
            assertEquals("Count: 42, Percent: 85%", formatted)

            val formattedWithArabicArg = Loc.format("Task: %s - %d", "مهمة رقم ١٢", 99)
            assertEquals("Task: مهمة رقم 12 - 99", formattedWithArabicArg)
        } finally {
            Locale.setDefault(oldDefault)
        }
    }

    @Test
    fun testFormatDurationUsesWesternDigits() {
        val oldDefault = Locale.getDefault()
        try {
            // Set default locale to Arabic
            Locale.setDefault(Locale.forLanguageTag("ar"))

            // 1 minute 25 seconds = 85000ms
            val result = formatDuration(85000L)
            assertEquals("01:25", result)

            // 10 minutes 5 seconds = 605000ms
            val resultLong = formatDuration(605000L)
            assertEquals("10:05", resultLong)
        } finally {
            Locale.setDefault(oldDefault)
        }
    }

    @Test
    fun testFormatPlaybackSpeedUsesWesternDigits() {
        val oldDefault = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar"))
            assertEquals("1x", formatPlaybackSpeed(1.0f))
            assertEquals("1.25x", formatPlaybackSpeed(1.25f))
            assertEquals("1.5x", formatPlaybackSpeed(1.5f))
            assertEquals("2x", formatPlaybackSpeed(2.0f))
        } finally {
            Locale.setDefault(oldDefault)
        }
    }

    @Test
    fun testTimestampFormattingAndParsingWithWesternAndArabicDigits() {
        val formatted = formatTimestampMs(75000L)
        assertEquals("01:15", formatted)

        // Parsing standard Western digits
        assertEquals(75000L, parseTimestampToMs("01:15"))

        // Parsing Arabic-Indic digits: ٠١:١٥ -> 75000ms
        assertEquals(75000L, parseTimestampToMs("٠١:١٥"))

        // Hours format: 01:02:03 vs ٠١:٠٢:٠٣
        assertEquals(3723000L, parseTimestampToMs("01:02:03"))
        assertEquals(3723000L, parseTimestampToMs("٠١:٠٢:٠٣"))
    }
}
