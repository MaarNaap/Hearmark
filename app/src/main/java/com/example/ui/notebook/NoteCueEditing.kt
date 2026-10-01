package com.example.ui

import com.example.player.SubtitleCue

data class CueMergeResult(
    val newStartCueIndex: Int,
    val newEndCueIndex: Int,
    val newStartMs: Long,
    val newEndMs: Long,
    val newNoteText: String
)

fun canAddPreviousCue(
    trackCues: List<SubtitleCue>,
    currentStartCueIndex: Int?,
    startMs: Long
): Boolean {
    if (trackCues.isEmpty()) return false
    val sIdx = currentStartCueIndex ?: trackCues.indexOfFirst { it.startMs >= startMs }.takeIf { it >= 0 } ?: 0
    return sIdx > 0
}

fun canAddNextCue(
    trackCues: List<SubtitleCue>,
    currentEndCueIndex: Int?,
    endMs: Long
): Boolean {
    if (trackCues.isEmpty()) return false
    val eIdx = currentEndCueIndex ?: trackCues.indexOfLast { it.startMs <= endMs }.takeIf { it >= 0 } ?: 0
    return eIdx < trackCues.size - 1
}

fun mergePreviousCue(
    trackCues: List<SubtitleCue>,
    currentStartCueIndex: Int?,
    currentEndCueIndex: Int?,
    startMs: Long,
    endMs: Long,
    noteText: String,
    minBound: Long,
    maxBound: Long
): CueMergeResult? {
    if (trackCues.isEmpty()) return null
    val sIdx = currentStartCueIndex ?: trackCues.indexOfFirst { it.startMs >= startMs }.takeIf { it >= 0 } ?: 0
    val targetIdx = sIdx - 1
    if (targetIdx !in trackCues.indices) return null

    val prevCue = trackCues[targetIdx]
    val resolvedEndCueIdx = currentEndCueIndex ?: sIdx
    val updatedStartMs = prevCue.startMs.coerceAtLeast(minBound)
    val updatedEndMs = if (endMs < updatedStartMs + 500L) {
        (updatedStartMs + 1000L).coerceAtMost(maxBound)
    } else {
        endMs
    }
    val prevText = prevCue.text.trim()
    val currText = noteText.trim()
    val updatedText = if (currText.isEmpty()) prevText else "$prevText $currText"

    return CueMergeResult(
        newStartCueIndex = targetIdx,
        newEndCueIndex = resolvedEndCueIdx,
        newStartMs = updatedStartMs,
        newEndMs = updatedEndMs,
        newNoteText = updatedText
    )
}

fun mergeNextCue(
    trackCues: List<SubtitleCue>,
    currentStartCueIndex: Int?,
    currentEndCueIndex: Int?,
    startMs: Long,
    endMs: Long,
    noteText: String,
    minBound: Long,
    maxBound: Long
): CueMergeResult? {
    if (trackCues.isEmpty()) return null
    val eIdx = currentEndCueIndex
        ?: trackCues.indexOfLast { it.startMs <= endMs || it.endMs <= endMs }.takeIf { it >= 0 }
        ?: (currentStartCueIndex ?: 0)
    val targetIdx = eIdx + 1
    if (targetIdx !in trackCues.indices) return null

    val nextCue = trackCues[targetIdx]
    val resolvedStartCueIdx = currentStartCueIndex ?: eIdx
    val rawNewEnd = if (nextCue.endMs > nextCue.startMs) nextCue.endMs else nextCue.startMs + 4000L
    val updatedEndMs = rawNewEnd.coerceAtMost(maxBound)
    val updatedStartMs = if (startMs > updatedEndMs) {
        nextCue.startMs.coerceAtLeast(minBound)
    } else {
        startMs
    }
    val nextText = nextCue.text.trim()
    val currText = noteText.trim()
    val updatedText = if (currText.isEmpty()) nextText else "$currText $nextText"

    return CueMergeResult(
        newStartCueIndex = resolvedStartCueIdx,
        newEndCueIndex = targetIdx,
        newStartMs = updatedStartMs,
        newEndMs = updatedEndMs,
        newNoteText = updatedText
    )
}
