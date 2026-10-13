package com.example.ui

import com.example.player.SubtitleCue

data class CueMergeResult(
    val newStartCueIndex: Int,
    val newEndCueIndex: Int,
    val newStartMs: Long,
    val newEndMs: Long,
    val newNoteText: String
)

data class CueSnapshot(
    val startCueIndex: Int?,
    val endCueIndex: Int?,
    val startMs: Long,
    val endMs: Long,
    val noteText: String,
    val originStartMs: Long?
)

data class CueRemoveResult(
    val newStartCueIndex: Int,
    val newEndCueIndex: Int,
    val newStartMs: Long,
    val newEndMs: Long,
    val newNoteText: String,
    val newOriginStartMs: Long?
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

fun stripCueText(fullText: String, cueText: String): String {
    val trimmedFull = fullText.trim()
    val trimmedCue = cueText.trim()
    if (trimmedCue.isEmpty()) return trimmedFull
    if (trimmedFull.equals(trimmedCue, ignoreCase = true)) return ""

    if (trimmedFull.startsWith(trimmedCue, ignoreCase = true)) {
        return trimmedFull.substring(trimmedCue.length).trim()
    }
    if (trimmedFull.endsWith(trimmedCue, ignoreCase = true)) {
        return trimmedFull.substring(0, trimmedFull.length - trimmedCue.length).trim()
    }
    val idx = trimmedFull.indexOf(trimmedCue, ignoreCase = true)
    if (idx >= 0) {
        val before = trimmedFull.substring(0, idx).trim()
        val after = trimmedFull.substring(idx + trimmedCue.length).trim()
        return if (before.isEmpty()) after else if (after.isEmpty()) before else "$before $after"
    }
    return trimmedFull
}

fun removeCue(
    cueToRemove: SubtitleCue,
    spannedCues: List<SubtitleCue>,
    trackCues: List<SubtitleCue>,
    noteText: String,
    originStartMs: Long?,
    minBound: Long = 0L,
    maxBound: Long = Long.MAX_VALUE
): CueRemoveResult? {
    if (spannedCues.size <= 1) return null
    val remaining = spannedCues.filter { it.startMs != cueToRemove.startMs || it.endMs != cueToRemove.endMs }
    if (remaining.isEmpty()) return null

    val sortedRemaining = remaining.sortedBy { it.startMs }
    val firstRemaining = sortedRemaining.first()
    val lastRemaining = sortedRemaining.last()

    val newStartMs = firstRemaining.startMs.coerceAtLeast(minBound)
    val lastEnd = if (lastRemaining.endMs > lastRemaining.startMs) lastRemaining.endMs else lastRemaining.startMs + 4000L
    val newEndMs = lastEnd.coerceAtMost(maxBound)

    val newStartIdx = trackCues.indexOfFirst { it.startMs == firstRemaining.startMs }.takeIf { it >= 0 } ?: 0
    val newEndIdx = trackCues.indexOfLast { it.startMs == lastRemaining.startMs }.takeIf { it >= 0 } ?: newStartIdx

    val newText = stripCueText(noteText, cueToRemove.text)

    val cueToRemoveEnd = if (cueToRemove.endMs > cueToRemove.startMs) cueToRemove.endMs else cueToRemove.startMs + 4000L
    val wasOriginInRemovedCue = originStartMs != null && (
        originStartMs == cueToRemove.startMs || originStartMs in cueToRemove.startMs..cueToRemoveEnd
    )
    val newOriginStartMs = if (wasOriginInRemovedCue) {
        sortedRemaining.minByOrNull { Math.abs(it.startMs - (originStartMs ?: firstRemaining.startMs)) }?.startMs ?: firstRemaining.startMs
    } else {
        originStartMs ?: firstRemaining.startMs
    }

    return CueRemoveResult(
        newStartCueIndex = newStartIdx,
        newEndCueIndex = newEndIdx,
        newStartMs = newStartMs,
        newEndMs = newEndMs,
        newNoteText = newText,
        newOriginStartMs = newOriginStartMs
    )
}

