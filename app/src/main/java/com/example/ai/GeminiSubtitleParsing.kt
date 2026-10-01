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

        // Find the start of the first cue index line or timestamp
        val firstCueRegex = Regex("""(?m)^\s*1\s*[\r\n]+\s*\d{1,2}:\d{2}:\d{2}""")
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
