package com.example.ai

import android.util.Log
import okhttp3.ResponseBody
import org.json.JSONObject

object GeminiSubtitleParsing {
    private const val TAG = "GeminiService"

    fun cleanSrtOutput(rawText: String): String {
        var text = rawText.trim()
        if (text.startsWith("```srt", ignoreCase = true)) {
            text = text.substring(6).trim()
        } else if (text.startsWith("```")) {
            text = text.substring(3).trim()
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length - 3).trim()
        }
        val trailingFences = text.lastIndexOf("```")
        if (trailingFences != -1 && trailingFences > 20) {
            text = text.substring(0, trailingFences).trim()
        }

        // Find the start of the first cue index line or timestamp (matching any cue index number)
        val firstCueRegex = Regex("""(?m)^\s*\d+\s*[\r\n]+\s*\d{1,2}:\d{2}:\d{2}""")
        val match = firstCueRegex.find(text)
        if (match != null) {
            text = text.substring(match.range.first).trim()
        } else {
            val timestampRegex = Regex("""(?m)^\s*\d{1,2}:\d{2}:\d{2}[,\.]\d{1,3}\s*-->\s*\d{1,2}:\d{2}:\d{2}""")
            val tsMatch = timestampRegex.find(text)
            if (tsMatch != null) {
                text = text.substring(tsMatch.range.first).trim()
            }
        }
        return text
    }

    /**
     * Sanitizes and enforces strictly forward-advancing chronological order for subtitle cues.
     * Guarantees:
     * 1. Strictly increasing timeline (startMs[i] >= startMs[i-1]).
     * 2. Positive, valid duration for every cue (endMs > startMs).
     * 3. Overlap resolution between adjacent cues.
     * 4. Deduplication of accidental repeat lines.
     * 5. Clean 1-based sequential re-indexing.
     */
    fun sanitizeAndOrderCues(rawCues: List<com.example.player.SubtitleCue>): List<com.example.player.SubtitleCue> {
        val validTimed = rawCues.filter { it.isTimed && it.startMs >= 0L && it.text.isNotBlank() }
        if (validTimed.isEmpty()) return emptyList()

        // Sort primarily by start timestamp, secondary by end timestamp
        val sorted = validTimed.sortedWith(compareBy({ it.startMs }, { it.endMs }))
        val sanitized = mutableListOf<com.example.player.SubtitleCue>()

        for (cue in sorted) {
            val cleanText = cue.text.trim()
            if (cleanText.isEmpty()) continue

            // Deduplicate immediately adjacent identical text cues within close proximity
            if (sanitized.isNotEmpty()) {
                val lastCue = sanitized.last()
                val isIdenticalText = lastCue.text.trim().equals(cleanText, ignoreCase = true)
                val isCloseTime = (cue.startMs - lastCue.endMs) < 2000L
                if (isIdenticalText && isCloseTime) {
                    // Extend the last cue's end time rather than having a duplicate cue
                    sanitized[sanitized.lastIndex] = lastCue.copy(
                        endMs = maxOf(lastCue.endMs, cue.endMs)
                    )
                    continue
                }
            }

            var start = cue.startMs.coerceAtLeast(0L)
            var end = cue.endMs

            // Fix inverted or missing duration
            if (end <= start) {
                val charLength = cleanText.length
                val estimatedDur = (charLength * 55L).coerceIn(1200L, 8000L)
                end = start + estimatedDur
            } else if ((end - start) < 200L) {
                end = start + 200L
            }

            if (sanitized.isNotEmpty()) {
                val prev = sanitized.last()

                // Enforce strictly non-regressive start time
                if (start < prev.startMs) {
                    start = prev.startMs + 50L
                }

                // Resolve overlaps with previous cue
                if (start < prev.endMs) {
                    val overlap = prev.endMs - start
                    if (overlap > 100L) {
                        // If the previous cue was long enough, trim its end time to avoid overlapping
                        if (prev.endMs - prev.startMs > 600L) {
                            val newPrevEnd = maxOf(prev.startMs + 300L, start - 30L)
                            sanitized[sanitized.lastIndex] = prev.copy(endMs = newPrevEnd)
                            start = maxOf(start, newPrevEnd + 30L)
                        } else {
                            // Otherwise push current cue forward slightly
                            start = prev.endMs + 30L
                        }
                    }
                }

                // Re-verify positive duration after any start adjustments
                if (end <= start) {
                    end = start + 300L
                }
            }

            sanitized.add(
                com.example.player.SubtitleCue(
                    id = sanitized.size + 1,
                    startMs = start,
                    endMs = end,
                    text = cleanText,
                    isTimed = true
                )
            )
        }

        return sanitized
    }

    fun parseSseSubtitleStream(
        body: ResponseBody,
        language: String,
        chunkIdx: Int,
        totalChunks: Int,
        accumulatedCuesCount: Int,
        onProgressUpdate: ((String) -> Unit)?
    ): String {
        val reader = body.byteStream().bufferedReader(Charsets.UTF_8)
        val chunkRawText = StringBuilder()
        var streamedArrowCount = 0

        reader.useLines { lines ->
            for (line in lines) {
                if (line.startsWith("data: ")) {
                    val jsonStr = line.substring(6).trim()
                    if (jsonStr.isNotBlank() && jsonStr != "[DONE]") {
                        try {
                            val chunkJson = JSONObject(jsonStr)
                            val candidates = chunkJson.optJSONArray("candidates")
                            if (candidates != null && candidates.length() > 0) {
                                val candidate = candidates.getJSONObject(0)
                                val content = candidate.optJSONObject("content")
                                val parts = content?.optJSONArray("parts")
                                val partText = GeminiHttp.extractNonThoughtTextFromParts(parts)
                                if (partText.isNotEmpty()) {
                                    chunkRawText.append(partText)
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parsing SSE chunk: ${e.message}")
                        }

                        val currentTotal = chunkRawText.toString()
                        val arrowCount = currentTotal.split("-->").size - 1
                        if (arrowCount > streamedArrowCount) {
                            streamedArrowCount = arrowCount
                            val totalLiveCues = accumulatedCuesCount + streamedArrowCount
                            val updateMsg = if (totalChunks > 1) {
                                if (language == "ar") {
                                    "مزامنة الجزء (${chunkIdx + 1}/$totalChunks)... ($totalLiveCues مقطعاً)"
                                } else {
                                    "Syncing chunk (${chunkIdx + 1}/$totalChunks)... ($totalLiveCues cues synced)"
                                }
                            } else {
                                if (language == "ar") {
                                    "جارٍ توليد ومزامنة الترجمة... ($totalLiveCues مقطعاً)"
                                } else {
                                    "Generating subtitles... ($totalLiveCues cues synced)"
                                }
                            }
                            onProgressUpdate?.invoke(updateMsg)
                        }
                    }
                }
            }
        }
        return chunkRawText.toString()
    }
}
