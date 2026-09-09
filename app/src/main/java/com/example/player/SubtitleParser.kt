package com.example.player

import com.example.data.Note
import java.io.File
import java.util.regex.Pattern

data class SubtitleCue(
    val id: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val isTimed: Boolean = true
)

object SubtitleParser {

    fun parseContent(content: String, offsetMs: Long = 0L): List<SubtitleCue> {
        if (content.isBlank()) return emptyList()

        val clean = content.removePrefix("\uFEFF").replace("\u0000", "")
        val trimmed = clean.trim()
        if (trimmed.isEmpty()) return emptyList()

        val cues = when {
            trimmed.contains("-->") -> {
                val parsed = parseSrtOrVtt(trimmed)
                if (parsed.isNotEmpty()) parsed else parsePlainText(trimmed)
            }
            trimmed.contains(Regex("\\[\\d{1,2}:\\d{2}")) -> {
                val parsed = parseLrc(trimmed)
                if (parsed.isNotEmpty()) parsed else parsePlainText(trimmed)
            }
            else -> parsePlainText(trimmed)
        }

        if (cues.isEmpty()) {
            return parsePlainText(trimmed)
        }

        if (offsetMs == 0L) return cues
        return cues.map { cue ->
            if (!cue.isTimed) {
                cue
            } else {
                cue.copy(
                    startMs = (cue.startMs + offsetMs).coerceAtLeast(0L),
                    endMs = (cue.endMs + offsetMs).coerceAtLeast(0L)
                )
            }
        }
    }

    fun parseFile(file: File, offsetMs: Long = 0L): List<SubtitleCue> {
        if (!file.exists() || !file.canRead()) return emptyList()
        return try {
            val content = file.readText(Charsets.UTF_8)
            parseContent(content, offsetMs)
        } catch (e: Exception) {
            try {
                parseContent(file.readText(), offsetMs)
            } catch (ex: Exception) {
                emptyList()
            }
        }
    }

    /**
     * Parses SRT or WebVTT files.
     * Timestamps format:
     * SRT: 00:01:20,000 --> 00:01:23,500 or 01:20,000 --> 01:23,500 or 01:20 --> 01:23
     * VTT: 00:01:20.000 --> 00:01:23.500 or 01:20.000 --> 01:23.500
     */
    private fun parseSrtOrVtt(text: String): List<SubtitleCue> {
        val list = mutableListOf<SubtitleCue>()
        val timePattern = Pattern.compile("(?:(\\d{1,2}):)?(\\d{1,2}):(\\d{2})(?:[,.](\\d{1,3}))?\\s*-->\\s*(?:(\\d{1,2}):)?(\\d{1,2}):(\\d{2})(?:[,.](\\d{1,3}))?")
        val normalized = text.replace("\r\n", "\n")
        val blocks = normalized.split(Regex("\n\\s*\n"))

        var index = 1
        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue

            var timeMatcher = timePattern.matcher("")
            var timeLineIndex = -1

            for (i in lines.indices) {
                val m = timePattern.matcher(lines[i])
                if (m.find()) {
                    timeMatcher = m
                    timeLineIndex = i
                    break
                }
            }

            if (timeLineIndex != -1) {
                val startMs = parseFlexibleTimestampToMs(
                    hStr = timeMatcher.group(1),
                    mStr = timeMatcher.group(2) ?: "0",
                    sStr = timeMatcher.group(3) ?: "0",
                    msStr = timeMatcher.group(4)
                )
                val endMs = parseFlexibleTimestampToMs(
                    hStr = timeMatcher.group(5),
                    mStr = timeMatcher.group(6) ?: "0",
                    sStr = timeMatcher.group(7) ?: "0",
                    msStr = timeMatcher.group(8)
                )

                val textLines = lines.subList(timeLineIndex + 1, lines.size)
                val cueText = textLines.joinToString("\n").replace(Regex("<[^>]*>"), "").trim()
                if (cueText.isNotBlank()) {
                    list.add(SubtitleCue(id = index++, startMs = startMs, endMs = endMs.coerceAtLeast(startMs + 1000L), text = cueText))
                }
            } else {
                // Untimed text block inside SRT file
                val blockText = lines.joinToString("\n").replace(Regex("<[^>]*>"), "").trim()
                if (blockText.isNotBlank() && !blockText.equals("WEBVTT", ignoreCase = true)) {
                    list.add(SubtitleCue(id = index++, startMs = -1L, endMs = -1L, text = blockText, isTimed = false))
                }
            }
        }

        // Fallback: If no timed cues found via double-newline block splitting, scan line-by-line
        if (list.none { it.isTimed }) {
            val lines = normalized.lines().map { it.trim() }
            var currentStart = -1L
            var currentEnd = -1L
            val currentText = mutableListOf<String>()

            for (line in lines) {
                val m = timePattern.matcher(line)
                if (m.find()) {
                    if (currentStart >= 0L && currentText.isNotEmpty()) {
                        list.add(SubtitleCue(
                            id = index++,
                            startMs = currentStart,
                            endMs = currentEnd.coerceAtLeast(currentStart + 1000L),
                            text = currentText.joinToString("\n").trim()
                        ))
                        currentText.clear()
                    }
                    currentStart = parseFlexibleTimestampToMs(
                        hStr = m.group(1),
                        mStr = m.group(2) ?: "0",
                        sStr = m.group(3) ?: "0",
                        msStr = m.group(4)
                    )
                    currentEnd = parseFlexibleTimestampToMs(
                        hStr = m.group(5),
                        mStr = m.group(6) ?: "0",
                        sStr = m.group(7) ?: "0",
                        msStr = m.group(8)
                    )
                } else if (currentStart >= 0L) {
                    if (line.isNotEmpty() && !line.matches(Regex("^\\d+$"))) {
                        currentText.add(line.replace(Regex("<[^>]*>"), ""))
                    }
                }
            }

            if (currentStart >= 0L && currentText.isNotEmpty()) {
                list.add(SubtitleCue(
                    id = index++,
                    startMs = currentStart,
                    endMs = currentEnd.coerceAtLeast(currentStart + 1000L),
                    text = currentText.joinToString("\n").trim()
                ))
            }
        }

        return list
    }

    private fun parseFlexibleTimestampToMs(hStr: String?, mStr: String, sStr: String, msStr: String?): Long {
        val h = hStr?.toLongOrNull() ?: 0L
        val m = mStr.toLongOrNull() ?: 0L
        val s = sStr.toLongOrNull() ?: 0L
        var ms = 0L
        if (!msStr.isNullOrBlank()) {
            val rawMs = msStr.toLongOrNull() ?: 0L
            ms = when (msStr.length) {
                1 -> rawMs * 100
                2 -> rawMs * 10
                else -> rawMs
            }
        }
        return (h * 3600_000L) + (m * 60_000L) + (s * 1000L) + ms
    }

    /**
     * Parses LRC lyrics files.
     * Format:
     * [00:12.34] Lyrics text
     * [00:12] Lyrics text
     * [01:00:12.34] Lyrics text
     * [06:12]
     * Lyrics text on next line
     * Supports mixed lines (timed & untimed) without losing any content.
     */
    private fun parseLrc(text: String): List<SubtitleCue> {
        val tagPattern = Pattern.compile("\\[(?:(\\d{1,2}):)?(\\d{1,2}):(\\d{2})(?:[.,:](\\d{1,3}))?\\]")
        val lines = text.lines()
        val list = mutableListOf<SubtitleCue>()
        var index = 1
        val pendingTimestamps = mutableListOf<Long>()

        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) continue

            // Check for metadata tags like [ar: singer], [ti: title], ignore them
            if (trimmedLine.matches(Regex("^\\[[a-zA-Z]{2,8}:.*?\\]$"))) {
                continue
            }

            val matcher = tagPattern.matcher(trimmedLine)
            val lineTimestamps = mutableListOf<Long>()
            while (matcher.find()) {
                val ms = parseFlexibleTimestampToMs(
                    hStr = matcher.group(1),
                    mStr = matcher.group(2) ?: "0",
                    sStr = matcher.group(3) ?: "0",
                    msStr = matcher.group(4)
                )
                lineTimestamps.add(ms)
            }

            val lyricText = trimmedLine.replace(Regex("\\[[^\\]]*\\]"), "").trim()

            if (lineTimestamps.isNotEmpty()) {
                if (lyricText.isNotBlank()) {
                    // Standard LRC: timestamp and text on the same line
                    pendingTimestamps.clear()
                    for (startMs in lineTimestamps) {
                        list.add(SubtitleCue(id = index++, startMs = startMs, endMs = -1L, text = lyricText, isTimed = true))
                    }
                } else {
                    // Standalone timestamp line -> keep in buffer for the next text line(s)
                    pendingTimestamps.addAll(lineTimestamps)
                }
            } else if (trimmedLine.isNotBlank()) {
                if (pendingTimestamps.isNotEmpty()) {
                    // Text line following a standalone timestamp line
                    for (startMs in pendingTimestamps) {
                        list.add(SubtitleCue(id = index++, startMs = startMs, endMs = -1L, text = trimmedLine, isTimed = true))
                    }
                    pendingTimestamps.clear()
                } else {
                    // Untimed line in mixed document
                    list.add(SubtitleCue(id = index++, startMs = -1L, endMs = -1L, text = trimmedLine, isTimed = false))
                }
            }
        }

        // Calculate endMs for timed cues
        val timedCues = list.filter { it.isTimed && it.startMs >= 0L }.sortedBy { it.startMs }
        val cueEndTimes = mutableMapOf<Int, Long>()
        for (i in timedCues.indices) {
            val cur = timedCues[i]
            val nextStart = if (i < timedCues.size - 1) timedCues[i + 1].startMs else cur.startMs + 5000L
            cueEndTimes[cur.id] = (nextStart).coerceAtLeast(cur.startMs + 1000L)
        }

        return list.map { cue ->
            if (cue.isTimed && cue.startMs >= 0L) {
                cue.copy(endMs = cueEndTimes[cue.id] ?: (cue.startMs + 5000L))
            } else {
                cue
            }
        }
    }

    /**
     * Plain text parser: parses non-timed lyrics, articles, or transcripts
     */
    private fun parsePlainText(text: String): List<SubtitleCue> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val list = mutableListOf<SubtitleCue>()
        for (i in lines.indices) {
            list.add(SubtitleCue(id = i + 1, startMs = -1L, endMs = -1L, text = lines[i], isTimed = false))
        }
        return list
    }

    /**
     * Finds matching subtitle file for audio track in same directory (.srt, .vtt, .lrc)
     * Handles language tags like song.en.srt, song.eng.vtt, song_en.srt, song-ar.lrc
     */
    fun findMatchingSubtitleFile(audioFilePath: String): File? {
        val audioFile = File(audioFilePath)
        if (!audioFile.exists()) return null
        val parent = audioFile.parentFile ?: return null
        val baseName = audioFile.nameWithoutExtension
        val cleanBase = baseName.replace("_", " ").replace("-", " ").trim()

        val extensions = setOf("srt", "vtt", "lrc", "txt")

        // 1. Direct extension or common language tag match
        val commonLangs = listOf("", ".en", ".eng", ".ar", ".es", ".fr", ".de", ".it", ".ja", ".zh", ".ko", ".pt", ".ru", "_en", "-en", "_ar", "-ar")
        for (lang in commonLangs) {
            for (ext in extensions) {
                val subFile = File(parent, "$baseName$lang.$ext")
                if (subFile.exists() && subFile.canRead()) {
                    return subFile
                }
            }
        }

        // 2. Directory scan for case-insensitive or normalized base name match (including any .<lang> prefix)
        val siblings = parent.listFiles() ?: return null
        for (f in siblings) {
            if (!f.isFile || !f.canRead()) continue
            val ext = f.extension.lowercase()
            if (ext in extensions) {
                val fBase = f.nameWithoutExtension
                if (fBase.equals(baseName, ignoreCase = true)) {
                    return f
                }
                val fCleanBase = fBase.replace("_", " ").replace("-", " ").trim()
                if (fCleanBase.equals(cleanBase, ignoreCase = true)) {
                    return f
                }

                // Match language suffixes: e.g. "song.en" -> base "song", "song-en" -> base "song"
                val fBaseWithoutLang = fBase.substringBeforeLast(".")
                if (fBaseWithoutLang.equals(baseName, ignoreCase = true)) {
                    return f
                }
                val fCleanBaseWithoutLang = fBaseWithoutLang.replace("_", " ").replace("-", " ").trim()
                if (fCleanBaseWithoutLang.equals(cleanBase, ignoreCase = true)) {
                    return f
                }
            }
        }
        return null
    }

    fun formatTimestampTag(ms: Long): String {
        val safeMs = ms.coerceAtLeast(0L)
        val minutes = (safeMs / 1000) / 60
        val seconds = (safeMs / 1000) % 60
        val hundredths = (safeMs % 1000) / 10
        return String.format(java.util.Locale.US, "[%02d:%02d.%02d]", minutes, seconds, hundredths)
    }

    fun formatShortTimeTag(ms: Long): String {
        val safeMs = ms.coerceAtLeast(0L)
        val minutes = (safeMs / 1000) / 60
        val seconds = (safeMs / 1000) % 60
        return String.format(java.util.Locale.US, "[%02d:%02d]", minutes, seconds)
    }

    fun formatLinesToLrc(lines: List<Pair<Long?, String>>): String {
        return lines.filter { it.second.isNotBlank() }.joinToString("\n\n") { (timeMs, text) ->
            if (timeMs != null && timeMs >= 0L) {
                "${formatTimestampTag(timeMs)} ${text.trim()}"
            } else {
                text.trim()
            }
        }
    }

    /**
     * Formats raw subtitle/transcript text for the editor so that each cue or paragraph
     * is separated by a clear empty line (double line break), giving distinct visual separation
     * between different timed translation blocks.
     */
    fun formatForEditor(text: String): String {
        if (text.isBlank()) return ""
        val trimmed = text.trim()
        if (trimmed.contains("-->")) {
            // SRT or VTT: normalize blocks to have empty line between cues
            val blocks = trimmed.replace("\r\n", "\n").split(Regex("\n\\s*\n"))
            return blocks.filter { it.isNotBlank() }.joinToString("\n\n") { it.trim() }
        }

        // Check if text already has double newlines
        val normalized = trimmed.replace("\r\n", "\n")
        if (normalized.contains("\n\n")) {
            val paragraphs = normalized.split(Regex("\n{2,}"))
            return paragraphs.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n\n")
        }

        // Single newlines: separate each non-empty line with double newlines
        val lines = normalized.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return lines.joinToString("\n\n")
    }

    /**
     * Determines if a given file path is a video container based on file extension
     */
    fun isVideoFile(filePath: String): Boolean {
        val ext = filePath.substringAfterLast(".", "").lowercase()
        return ext in setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "flv", "m4v", "ts")
    }

    /**
     * Resolves the single dedicated cue for a note within a list of cues.
     * If note.originStartMs is stored, matches that cue directly.
     * If note.originStartMs is null (e.g. legacy notes spanning multiple cues),
     * determines the primary cue so that a note is strictly dedicated to ONE cue only.
     */
    fun findDedicatedCueForNote(note: Note, cues: List<SubtitleCue>): SubtitleCue? {
        if (cues.isEmpty()) return null

        val origin = note.originStartMs
        if (origin != null && origin > 0) {
            val directMatch = cues.find { cue ->
                val cueEnd = if (cue.endMs > cue.startMs) cue.endMs else cue.startMs + 4000L
                origin in cue.startMs..cueEnd
            }
            if (directMatch != null) return directMatch
            return cues.minByOrNull { Math.abs(it.startMs - origin) }
        }

        val nStart = note.startTimestampMs
        val nEnd = if (note.endTimestampMs > nStart) note.endTimestampMs else nStart + 3000L

        val spannedCues = cues.filter { cue ->
            val cueEnd = if (cue.endMs > cue.startMs) cue.endMs else cue.startMs + 4000L
            cue.startMs < nEnd && cueEnd > nStart
        }

        if (spannedCues.isEmpty()) {
            return cues.minByOrNull { Math.abs(it.startMs - nStart) }
        }

        if (spannedCues.size == 1) {
            return spannedCues[0]
        }

        // Multi-cue note created with previous cue + next cue context:
        // When previous cue was prepended for context, the original cue was index 1 (for 3+ cues),
        // or index 0 for 2 cues.
        return if (spannedCues.size >= 3) {
            spannedCues[1]
        } else {
            spannedCues[0]
        }
    }

    fun formatSrtTimestamp(ms: Long): String {
        val safeMs = ms.coerceAtLeast(0L)
        val hours = (safeMs / 3600_000L)
        val minutes = (safeMs % 3600_000L) / 60_000L
        val seconds = (safeMs % 60_000L) / 1000L
        val millis = safeMs % 1000L
        return String.format(java.util.Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    fun exportCuesToSrt(cues: List<SubtitleCue>): String {
        if (cues.isEmpty()) return ""
        val sortedCues = cues.sortedBy { it.startMs }
        val sb = StringBuilder()
        var index = 1
        for (cue in sortedCues) {
            if (cue.text.isBlank()) continue
            val startMs = cue.startMs.coerceAtLeast(0L)
            val endMs = if (cue.endMs > startMs) cue.endMs else startMs + 1500L
            sb.append(index++)
            sb.append("\n")
            sb.append(formatSrtTimestamp(startMs))
            sb.append(" --> ")
            sb.append(formatSrtTimestamp(endMs))
            sb.append("\n")
            sb.append(cue.text.trim())
            sb.append("\n\n")
        }
        return sb.toString().trim()
    }

    /**
     * Extracts pure conversational text from reference subtitles, removing timestamps,
     * cue indexes, and metadata tags so that AI timing is not contaminated by outdated timestamps.
     */
    fun extractCleanTextFromReference(referenceContent: String): String {
        if (referenceContent.isBlank()) return ""
        val cues = parseContent(referenceContent)
        if (cues.isNotEmpty()) {
            val validTexts = cues.map { it.text.trim() }.filter { it.isNotBlank() }
            if (validTexts.isNotEmpty()) {
                return validTexts.joinToString("\n")
            }
        }
        return referenceContent
            .replace(Regex("""(?m)^\s*\d+\s*$"""), "")
            .replace(Regex("""(?m)^\s*(\d{1,2}:)?\d{2}:\d{2}[,\.]\d{1,3}\s*-->\s*(\d{1,2}:)?\d{2}:\d{2}[,\.]\d{1,3}.*$"""), "")
            .replace(Regex("""\[\d{1,2}:\d{2}(?:[.,:]\d{1,3})?\]"""), "")
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }
}


