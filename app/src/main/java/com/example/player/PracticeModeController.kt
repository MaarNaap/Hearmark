package com.example.player

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.data.AppRepository
import com.example.data.AudioTrack
import com.example.ui.Loc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun AudioPlayerManager.needsReanalysis(track: AudioTrack, context: Context? = null): Boolean {
    val existing = track.getPracticeSegmentsList()
    if (existing.isEmpty()) return true

    val storedSource = track.getPracticeSegmentsSource()
    if (storedSource == "MANUAL") {
        return false
    }
    // If stored source is known and differs from active source:
    if (storedSource != null && segmentSource != "MANUAL" && storedSource != segmentSource) {
        return true
    }

    // If legacy stored segments (no prefix):
    if (storedSource == null) {
        if (segmentSource == "SUBTITLES" && hasAvailableSubtitles(track)) {
            return true
        }
    }

    if (segmentSource == "SUBTITLES") {
        val cues = getOrParseCuesForTrack(track)
        if (cues.isNotEmpty() && Math.abs(cues.size - existing.size) > 1) {
            return true
        }
        return false
    }

    val effectiveContext = context ?: appContext
    val effectiveDuration = if (track.duration > 0) {
        track.duration
    } else if (_duration.value > 0) {
        _duration.value
    } else {
        val mpDur = try { mediaPlayer?.duration?.toLong() ?: 0L } catch (e: Exception) { 0L }
        if (mpDur > 0) mpDur else (effectiveContext?.let { SilenceDetector.getAudioDuration(it, track.filePath) } ?: 0L)
    }

    if (effectiveDuration > 15000L) {
        val hasPrematureCutoffGap = existing.zipWithNext().any { (a, b) -> (b - a) > 12000L }
        if (hasPrematureCutoffGap) return true
        if (existing.first() > 12000L) return true
        if (existing.last() < (effectiveDuration - 4000L)) return true
        if (effectiveDuration > 25000L && existing.size < 3) return true
    }

    return false
}

internal suspend fun AudioPlayerManager.extractSubtitleBoundaries(
    track: AudioTrack,
    effectiveDuration: Long
): List<Long> {
    val rawCues = getOrParseCuesForTrack(track)
    if (rawCues.isEmpty()) return emptyList()

    if (_subtitlesCues.value.isEmpty() && currentTrackValue?.id == track.id) {
        withContext(Dispatchers.Main) {
            _subtitlesCues.value = rawCues
        }
    }

    val rawBoundaries: List<Long>
    if (track.isVirtualScene) {
        val sceneStart = track.startOffsetMs
        val sceneEnd = track.endOffsetMs ?: (track.startOffsetMs + track.duration)
        val sceneDuration = track.duration.coerceAtLeast(1000L)
        val timedCues = rawCues.filter { it.isTimed && it.startMs >= 0L }
        val sceneCues = timedCues.filter { it.endMs > sceneStart && it.startMs < sceneEnd }

        rawBoundaries = if (sceneCues.isNotEmpty()) {
            sceneCues.map { cue ->
                val cueEnd = if (cue.endMs > cue.startMs) cue.endMs else (cue.startMs + 3000L)
                (cueEnd - sceneStart).coerceIn(400L, sceneDuration)
            }
        } else {
            val lines = rawCues.take(minOf(rawCues.size, 10))
            val lineDur = sceneDuration / lines.size.coerceAtLeast(1)
            lines.indices.map { (it + 1) * lineDur }
        }
    } else {
        val timedCues = rawCues.filter { it.isTimed && it.startMs >= 0L }.sortedBy { it.startMs }
        rawBoundaries = if (timedCues.isNotEmpty()) {
            timedCues.map { cue ->
                val cueEnd = if (cue.endMs > cue.startMs) cue.endMs else (cue.startMs + 3000L)
                if (effectiveDuration > 0L) {
                    cueEnd.coerceIn(400L, effectiveDuration)
                } else {
                    cueEnd.coerceAtLeast(400L)
                }
            }
        } else if (effectiveDuration > 0L) {
            val nonBlank = rawCues.filter { it.text.isNotBlank() }
            if (nonBlank.isNotEmpty()) {
                val lineDur = effectiveDuration / nonBlank.size
                nonBlank.indices.map { (it + 1) * lineDur }
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    val boundaries = mutableListOf<Long>()
    val maxLimit = if (effectiveDuration > 0L) effectiveDuration else Long.MAX_VALUE
    val sorted = rawBoundaries
        .filter { it > 300L && it <= maxLimit }
        .distinct()
        .sorted()

    for (b in sorted) {
        if (boundaries.isEmpty() || b - boundaries.last() >= 500L) {
            boundaries.add(b)
        }
    }
    return boundaries
}

suspend fun AudioPlayerManager.reanalyzePracticeSegmentsInternal(
    track: AudioTrack,
    context: Context,
    targetRepo: AppRepository,
    silent: Boolean = false,
    onComplete: ((Int) -> Unit)? = null
) {
    _isPracticeAnalyzing.value = true
    try {
        val effectiveDuration = if (track.duration > 0) {
            track.duration
        } else if (_duration.value > 0) {
            _duration.value
        } else {
            val mpDur = try { mediaPlayer?.duration?.toLong() ?: 0L } catch (e: Exception) { 0L }
            if (mpDur > 0) mpDur else SilenceDetector.getAudioDuration(context, track.filePath)
        }

        val boundaries: List<Long>
        val isSubtitleSource = (segmentSource == "SUBTITLES")
        val isManualSource = (segmentSource == "MANUAL")
        var usedPrefix = "SIL:"

        if (isManualSource) {
            if (track.practiceSegments?.startsWith("MAN:") == true) {
                val existing = track.getPracticeSegmentsList()
                if (existing.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        _currentPracticeSegments.value = existing
                        _isPracticeAnalyzing.value = false
                        resetPracticeSegmentTracking(_currentPosition.value)
                        onComplete?.invoke(existing.size)
                    }
                    return
                }
            }
            // When MANUAL is selected but no manual cuts exist for this track,
            // fallback gracefully to Subtitles if available, otherwise Silence detection.
            // We MUST NEVER save SilenceDetector results as "MAN:".
            if (hasAvailableSubtitles(track)) {
                val subBoundaries = extractSubtitleBoundaries(track, effectiveDuration)
                if (subBoundaries.isNotEmpty()) {
                    boundaries = subBoundaries
                    usedPrefix = "SUB:"
                    if (!silent) {
                        withContext(Dispatchers.Main) {
                            val msg = String.format(java.util.Locale.US, Loc.getText("segments_created_from_subtitles_count"), subBoundaries.size)
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    boundaries = SilenceDetector.detectBoundaries(
                        context = context,
                        filePath = track.filePath,
                        totalDurationMs = effectiveDuration,
                        sensitivity = silenceSensitivity,
                        minSilenceMs = silenceMinDurationMs,
                        tailPaddingMs = silencePaddingMs
                    )
                    usedPrefix = "SIL:"
                    if (!silent) {
                        withContext(Dispatchers.Main) {
                            val msg = String.format(java.util.Locale.US, Loc.getText("segments_created_from_silence_count"), boundaries.size)
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                boundaries = SilenceDetector.detectBoundaries(
                    context = context,
                    filePath = track.filePath,
                    totalDurationMs = effectiveDuration,
                    sensitivity = silenceSensitivity,
                    minSilenceMs = silenceMinDurationMs,
                    tailPaddingMs = silencePaddingMs
                )
                usedPrefix = "SIL:"
                if (!silent) {
                    withContext(Dispatchers.Main) {
                        val msg = String.format(java.util.Locale.US, Loc.getText("segments_created_from_silence_count"), boundaries.size)
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else if (isSubtitleSource) {
            val subBoundaries = extractSubtitleBoundaries(track, effectiveDuration)
            if (subBoundaries.isNotEmpty()) {
                boundaries = subBoundaries
                usedPrefix = "SUB:"
                if (!silent) {
                    withContext(Dispatchers.Main) {
                        val msg = String.format(java.util.Locale.US, Loc.getText("segments_created_from_subtitles_count"), subBoundaries.size)
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                if (!silent) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, Loc.getText("no_subtitles_for_segments"), Toast.LENGTH_LONG).show()
                    }
                }
                boundaries = SilenceDetector.detectBoundaries(
                    context = context,
                    filePath = track.filePath,
                    totalDurationMs = effectiveDuration,
                    sensitivity = silenceSensitivity,
                    minSilenceMs = silenceMinDurationMs,
                    tailPaddingMs = silencePaddingMs
                )
                usedPrefix = "SIL:"
            }
        } else {
            boundaries = SilenceDetector.detectBoundaries(
                context = context,
                filePath = track.filePath,
                totalDurationMs = effectiveDuration,
                sensitivity = silenceSensitivity,
                minSilenceMs = silenceMinDurationMs,
                tailPaddingMs = silencePaddingMs
            )
            usedPrefix = "SIL:"
            if (!silent) {
                withContext(Dispatchers.Main) {
                    val msg = String.format(java.util.Locale.US, Loc.getText("segments_created_from_silence_count"), boundaries.size)
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }

        val segmentsStr = usedPrefix + boundaries.joinToString(",")
        targetRepo.updateTrackPracticeSegments(track.id, segmentsStr)
        updateTrackState { it.copy(practiceSegments = segmentsStr) }

        withContext(Dispatchers.Main) {
            _currentPracticeSegments.value = boundaries
            _isPracticeAnalyzing.value = false
            resetPracticeSegmentTracking(_currentPosition.value)
            onComplete?.invoke(boundaries.size)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Practice segmentation error: ${e.message}")
        withContext(Dispatchers.Main) {
            _isPracticeAnalyzing.value = false
        }
    }
}

fun AudioPlayerManager.reanalyzePracticeSegments(
    context: Context,
    repository: AppRepository,
    silent: Boolean = false,
    onComplete: ((Int) -> Unit)? = null
) {
    val track = currentTrackValue ?: return
    coroutineScope.launch(Dispatchers.IO) {
        reanalyzePracticeSegmentsInternal(track, context, repository, silent, onComplete)
    }
}

fun AudioPlayerManager.getActivePracticeSourceForTrack(track: AudioTrack): String {
    val stored = track.getPracticeSegmentsSource()
    if (stored != null) return stored
    val hasManual = track.practiceSegments?.startsWith("MAN:") == true
    val hasSub = hasAvailableSubtitles(track)
    return when (segmentSource) {
        "MANUAL" -> if (hasManual) "MANUAL" else if (hasSub) "SUBTITLES" else "SILENCE"
        "SUBTITLES" -> if (hasSub) "SUBTITLES" else "SILENCE"
        else -> "SILENCE"
    }
}

fun AudioPlayerManager.getPracticeTrackSourcesInfo(track: AudioTrack): AudioPlayerManager.PracticeTrackSourcesInfo {
    val manualCuts = if (track.practiceSegments?.startsWith("MAN:") == true) {
        track.getPracticeSegmentsList()
    } else emptyList()

    val cues = getOrParseCuesForTrack(track)
    val activeSource = getActivePracticeSourceForTrack(track)
    val count = if (_isPracticeMode.value && _currentPracticeSegments.value.isNotEmpty()) {
        _currentPracticeSegments.value.size
    } else {
        track.getPracticeSegmentsList().size
    }
    return AudioPlayerManager.PracticeTrackSourcesInfo(
        hasManualCuts = manualCuts.isNotEmpty(),
        manualCutsCount = manualCuts.size,
        hasSubtitles = cues.isNotEmpty(),
        subtitlesCount = cues.size,
        activeSource = activeSource,
        activeSegmentsCount = count
    )
}

fun AudioPlayerManager.applyPracticeSettingsAndStart(
    track: AudioTrack,
    source: String,
    multiplier: Float,
    context: Context,
    repository: AppRepository,
    repeatCount: Int = practiceSegmentRepeatCount
) {
    val normalizedSource = when (source.uppercase()) {
        "MANUAL" -> "MANUAL"
        "SUBTITLES" -> "SUBTITLES"
        else -> "SILENCE"
    }
    segmentSource = normalizedSource
    val coerced = multiplier.coerceIn(0.25f, 10.0f)
    practicePauseMultiplier = coerced
    _practicePauseMultiplierFlow.value = coerced

    val coercedRepeats = repeatCount.coerceAtLeast(1)
    practiceSegmentRepeatCount = coercedRepeats
    _practiceSegmentRepeatsFlow.value = coercedRepeats
    practiceCurrentSegmentPlayCount = 1
    _practiceCurrentSegmentPlayCountFlow.value = 1

    // If manual and cuts exist, directly apply without reanalysis
    if (normalizedSource == "MANUAL" && track.practiceSegments?.startsWith("MAN:") == true) {
        val list = track.getPracticeSegmentsList()
        if (list.isNotEmpty()) {
            _currentPracticeSegments.value = list
            _isPracticeMode.value = true
            setFocusMode(true)
            resetPracticeSegmentTracking(_currentPosition.value)
            if (!_isPlaying.value) {
                resume()
            }
            Toast.makeText(
                context,
                "${Loc.getText("practice_source_manual_short")} (${list.size} ${Loc.getText("cuts_label")})",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
    }

    coroutineScope.launch(Dispatchers.IO) {
        reanalyzePracticeSegmentsInternal(track, context, repository, silent = false) { count ->
            _isPracticeMode.value = true
            setFocusMode(true)
            if (!_isPlaying.value) {
                resume()
            }
        }
    }
}

fun AudioPlayerManager.saveManualPracticeSegments(context: Context, track: AudioTrack, boundaries: List<Long>, autoEnable: Boolean = true) {
    val sorted = boundaries.filter { it > 0 }.distinct().sorted()
    val segmentsStr = "MAN:" + sorted.joinToString(",")
    segmentSource = "MANUAL"
    val repo = repository
    coroutineScope.launch(Dispatchers.IO) {
        repo?.updateTrackPracticeSegments(track.id, segmentsStr)
        updateTrackState { it.copy(practiceSegments = segmentsStr) }
        withContext(Dispatchers.Main) {
            _currentPracticeSegments.value = sorted
            resetPracticeSegmentTracking(_currentPosition.value)
            if (autoEnable) {
                _isPracticeMode.value = true
                setFocusMode(true)
                if (!_isPlaying.value) {
                    resume()
                }
            }
            Toast.makeText(
                context,
                if (autoEnable) Loc.getText("manual_cuts_applied_and_active")
                else String.format(java.util.Locale.US, Loc.getText("manual_cuts_saved"), sorted.size),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

fun AudioPlayerManager.clearPracticeSegmentsForCurrentTrack() {
    practicePauseJob?.cancel()
    pendingEndOfTrackAfterPracticePause = false
    _isPracticePausing.value = false
    _practicePauseRemainingSeconds.value = 0f
    _currentPracticeSegments.value = emptyList()
    practiceNextBoundaryIndex = 0
    practiceLastSegmentStartMs = 0L
    practiceLastPlayedSegmentStartMs = 0L
    practiceRepeatSegmentStartMs = 0L
    val track = currentTrackValue
    if (track != null) {
        coroutineScope.launch(Dispatchers.IO) {
            repository?.updateTrackPracticeSegments(track.id, null)
            updateTrackState { it.copy(practiceSegments = null) }
        }
    }
}

fun AudioPlayerManager.stopPracticeMode() {
    if (_isPracticeMode.value) {
        accumulatePracticePauseTime()
        lastPracticePauseTimestamp = 0L
        _isPracticeMode.value = false
        practicePauseJob?.cancel()
        val wasPausing = _isPracticePausing.value
        val wasPendingEnd = pendingEndOfTrackAfterPracticePause
        pendingEndOfTrackAfterPracticePause = false
        _isPracticePausing.value = false
        _practicePauseRemainingSeconds.value = 0f
        if (wasPendingEnd) {
            handlePhysicalEndOfTrack()
        } else if (wasPausing) {
            resume()
        }
    }
}

fun AudioPlayerManager.togglePracticeMode(context: Context? = null, repository: AppRepository? = null, forceReanalyze: Boolean = false) {
    if (_isPracticeMode.value && !forceReanalyze) {
        stopPracticeMode()
        setFocusMode(false)
    } else {
        val targetContext = context ?: appContext ?: return
        val targetRepo = repository ?: this.repository ?: return
        val track = currentTrackValue ?: return
        val shouldReanalyze = forceReanalyze || needsReanalysis(track, targetContext)

        if (!shouldReanalyze) {
            _currentPracticeSegments.value = track.getPracticeSegmentsList()
            _isPracticeMode.value = true
            setFocusMode(true)
            resetPracticeSegmentTracking(_currentPosition.value)
            if (!_isPlaying.value) {
                resume()
            }
        } else {
            coroutineScope.launch(Dispatchers.IO) {
                reanalyzePracticeSegmentsInternal(track, targetContext, targetRepo, silent = false) {
                    _isPracticeMode.value = true
                    setFocusMode(true)
                    if (!_isPlaying.value) {
                        resume()
                    }
                }
            }
        }
    }
}

private data class VirtualSubtitleCue(
    val rawCue: SubtitleCue,
    val startVirtualMs: Long,
    val endVirtualMs: Long
)

private fun AudioPlayerManager.resolveEffectivePracticeDuration(track: AudioTrack): Long {
    return if (track.isVirtualScene) {
        track.duration.takeIf { it > 0L } ?: _duration.value
    } else {
        val mpDur = try { mediaPlayer?.duration?.toLong() ?: 0L } catch (e: Exception) { 0L }
        if (mpDur > 0L) mpDur else (track.duration.takeIf { it > 0L } ?: _duration.value)
    }
}

private fun AudioPlayerManager.resolveCurrentVirtualPos(track: AudioTrack): Long {
    if (track.isVirtualScene || isSeeking || _isPracticePausing.value || !_isPlaying.value) {
        return _currentPosition.value
    }
    val mpPos = try { mediaPlayer?.currentPosition?.toLong() } catch (e: Exception) { null }
    return mpPos ?: _currentPosition.value
}

private fun AudioPlayerManager.resolveVirtualTimedCues(track: AudioTrack, effectiveDuration: Long): List<VirtualSubtitleCue> {
    val rawCues = if (_subtitlesCues.value.isNotEmpty()) _subtitlesCues.value else getOrParseCuesForTrack(track)
    if (rawCues.isEmpty()) return emptyList()
    return if (track.isVirtualScene) {
        val sceneStart = track.startOffsetMs
        val sceneEnd = track.endOffsetMs ?: (track.startOffsetMs + effectiveDuration)
        rawCues
            .filter {
                it.isTimed && it.startMs >= 0L &&
                    (if (it.endMs > it.startMs) it.endMs else it.startMs + 3000L) > sceneStart &&
                    it.startMs < sceneEnd
            }
            .sortedBy { it.startMs }
            .map { cue ->
                val rawEnd = if (cue.endMs > cue.startMs) cue.endMs else cue.startMs + 3000L
                val vStart = (cue.startMs - sceneStart).coerceAtLeast(0L)
                val vEnd = (rawEnd - sceneStart).let {
                    if (effectiveDuration > 0L) it.coerceIn(vStart, effectiveDuration) else it.coerceAtLeast(vStart)
                }
                VirtualSubtitleCue(cue, vStart, vEnd)
            }
    } else {
        rawCues
            .filter { it.isTimed && it.startMs >= 0L }
            .sortedBy { it.startMs }
            .map { cue ->
                val rawEnd = if (cue.endMs > cue.startMs) cue.endMs else cue.startMs + 3000L
                val vStart = cue.startMs.coerceAtLeast(0L)
                val vEnd = if (effectiveDuration > 0L) rawEnd.coerceIn(vStart, effectiveDuration) else rawEnd.coerceAtLeast(vStart)
                VirtualSubtitleCue(cue, vStart, vEnd)
            }
    }
}

private fun AudioPlayerManager.seekPracticeTarget(
    targetVirtualMs: Long,
    effectiveDuration: Long,
    targetSubtitleCue: VirtualSubtitleCue? = null
) {
    val clamped = if (effectiveDuration > 0L) {
        targetVirtualMs.coerceIn(0L, effectiveDuration)
    } else {
        targetVirtualMs.coerceAtLeast(0L)
    }
    val track = currentTrackValue
    val physTarget = if (track != null && track.isVirtualScene) track.startOffsetMs + clamped else clamped
    if (mediaPlayer != null) {
        seekTo(clamped, isPhysicalTimestamp = false)
    } else {
        _currentPosition.value = clamped
        setLastTrackedPosition(physTarget)
        onSeekInPracticeMode(clamped)
    }

    if (targetSubtitleCue != null) {
        if (isSubtitlesEnabled.value) {
            _activeSubtitleCue.value = targetSubtitleCue.rawCue
        }
        val segments = _currentPracticeSegments.value
        if (segments.isNotEmpty()) {
            val matchingBoundaryIdx = segments.indexOfFirst {
                it >= targetSubtitleCue.endVirtualMs - 250L && it > targetSubtitleCue.startVirtualMs + 150L
            }.takeIf { it != -1 } ?: segments.indexOfFirst { it > clamped + 150L }
            practiceNextBoundaryIndex = if (matchingBoundaryIdx == -1) segments.size else matchingBoundaryIdx
        }
        practiceLastSegmentStartMs = clamped
        practiceLastPlayedSegmentStartMs = clamped
        practiceRepeatSegmentStartMs = clamped
    } else {
        updateActiveSubtitleCue(physTarget)
    }
}

private fun AudioPlayerManager.resolvePausedSubtitleCueIndex(
    timedCues: List<VirtualSubtitleCue>,
    currentVirtualPos: Long
): Int {
    val activeId = _activeSubtitleCue.value?.id
    if (activeId != null) {
        val byActive = timedCues.indexOfFirst { it.rawCue.id == activeId }
        if (byActive != -1) return byActive
    }
    val closestEndIdx = timedCues.indices.minByOrNull { idx ->
        Math.abs(timedCues[idx].endVirtualMs - currentVirtualPos)
    }
    if (closestEndIdx != null && Math.abs(timedCues[closestEndIdx].endVirtualMs - currentVirtualPos) <= 600L) {
        return closestEndIdx
    }
    val byRepeat = timedCues.indexOfFirst { Math.abs(it.startVirtualMs - practiceRepeatSegmentStartMs) <= 300L }
    if (byRepeat != -1) return byRepeat
    return timedCues.indexOfLast { it.startVirtualMs < currentVirtualPos - 100L }.coerceAtLeast(0)
}

fun AudioPlayerManager.skipForwardInPracticeMode(): Boolean {
    if (!_isPracticeMode.value) return false
    val track = currentTrackValue ?: return false
    val effectiveDuration = resolveEffectivePracticeDuration(track)
    val currentVirtualPos = resolveCurrentVirtualPos(track)
    val activeSource = getActivePracticeSourceForTrack(track)
    val useSubtitleCues = (activeSource == "SUBTITLES" || (segmentSource == "SUBTITLES" && activeSource != "MANUAL"))

    if (useSubtitleCues) {
        val timedCues = resolveVirtualTimedCues(track, effectiveDuration)
        if (timedCues.isNotEmpty()) {
            val nextCueIdx = if (_isPracticePausing.value) {
                resolvePausedSubtitleCueIndex(timedCues, currentVirtualPos) + 1
            } else {
                val currentIdx = timedCues.indexOfLast { it.startVirtualMs <= currentVirtualPos + 150L }
                currentIdx + 1
            }
            if (nextCueIdx in timedCues.indices) {
                val targetCue = timedCues[nextCueIdx]
                seekPracticeTarget(targetCue.startVirtualMs, effectiveDuration, targetCue)
            } else {
                val endTarget = if (effectiveDuration > 0L) effectiveDuration else timedCues.last().endVirtualMs
                seekPracticeTarget(endTarget, effectiveDuration)
            }
            return true
        }
    }

    val boundaries = if (_currentPracticeSegments.value.isNotEmpty()) {
        _currentPracticeSegments.value
    } else {
        track.getPracticeSegmentsList()
    }
    if (boundaries.isEmpty()) return false

    val cutStarts = buildList {
        add(0L)
        for (b in boundaries) {
            if (b > 0L && (effectiveDuration <= 0L || b < effectiveDuration - 200L)) {
                if (b - last() > 100L) {
                    add(b)
                }
            }
        }
    }

    val currentCutIdx = if (_isPracticePausing.value) {
        cutStarts.indexOfLast { it <= practiceLastPlayedSegmentStartMs + 150L }.coerceAtLeast(0)
    } else {
        cutStarts.indexOfLast { it <= currentVirtualPos + 150L }.coerceAtLeast(0)
    }

    val nextCutIdx = currentCutIdx + 1
    if (nextCutIdx in cutStarts.indices) {
        seekPracticeTarget(cutStarts[nextCutIdx], effectiveDuration)
    } else {
        val endTarget = if (effectiveDuration > 0L) effectiveDuration else boundaries.last()
        seekPracticeTarget(endTarget, effectiveDuration)
    }
    return true
}

fun AudioPlayerManager.skipBackwardInPracticeMode(): Boolean {
    if (!_isPracticeMode.value) return false
    val track = currentTrackValue ?: return false
    val effectiveDuration = resolveEffectivePracticeDuration(track)
    val currentVirtualPos = resolveCurrentVirtualPos(track)
    val activeSource = getActivePracticeSourceForTrack(track)
    val useSubtitleCues = (activeSource == "SUBTITLES" || (segmentSource == "SUBTITLES" && activeSource != "MANUAL"))

    if (useSubtitleCues) {
        val timedCues = resolveVirtualTimedCues(track, effectiveDuration)
        if (timedCues.isNotEmpty()) {
            val prevCueIdx = if (_isPracticePausing.value) {
                (resolvePausedSubtitleCueIndex(timedCues, currentVirtualPos) - 1).coerceAtLeast(0)
            } else {
                val currentIdx = timedCues.indexOfLast { it.startVirtualMs <= currentVirtualPos + 150L }
                when {
                    currentIdx < 0 -> -1
                    currentIdx == 0 -> {
                        if (currentVirtualPos > timedCues[0].endVirtualMs + 400L) 0 else 0
                    }
                    else -> {
                        if (currentVirtualPos > timedCues[currentIdx].endVirtualMs + 400L) currentIdx else currentIdx - 1
                    }
                }
            }
            if (prevCueIdx in timedCues.indices) {
                val targetCue = timedCues[prevCueIdx]
                seekPracticeTarget(targetCue.startVirtualMs, effectiveDuration, targetCue)
            } else {
                seekPracticeTarget(0L, effectiveDuration)
            }
            return true
        }
    }

    val boundaries = if (_currentPracticeSegments.value.isNotEmpty()) {
        _currentPracticeSegments.value
    } else {
        track.getPracticeSegmentsList()
    }
    if (boundaries.isEmpty()) return false

    val cutStarts = buildList {
        add(0L)
        for (b in boundaries) {
            if (b > 0L && (effectiveDuration <= 0L || b < effectiveDuration - 200L)) {
                if (b - last() > 100L) {
                    add(b)
                }
            }
        }
    }

    val currentCutIdx = if (_isPracticePausing.value) {
        cutStarts.indexOfLast { it <= practiceLastPlayedSegmentStartMs + 150L }.coerceAtLeast(0)
    } else {
        cutStarts.indexOfLast { it <= currentVirtualPos + 150L }.coerceAtLeast(0)
    }

    val prevCutIdx = (currentCutIdx - 1).coerceAtLeast(0)
    seekPracticeTarget(cutStarts[prevCutIdx], effectiveDuration)
    return true
}

// =========================================================================
// @LOCKED: Shadowing / Practice Mode Boundary & Pause State Machine - STRICT FREEZE
// DO NOT MODIFY OR REFACTOR THIS BLOCK WITHOUT EXPLICIT PERMISSION IN PROMPT
// =========================================================================
internal fun AudioPlayerManager.resetPracticeSegmentTracking(pos: Long) {
    val segments = _currentPracticeSegments.value
    val nextIdx = segments.indexOfFirst { it > pos }
    practiceNextBoundaryIndex = if (nextIdx == -1) segments.size else nextIdx
    practiceLastSegmentStartMs = if (practiceNextBoundaryIndex > 0) segments[practiceNextBoundaryIndex - 1] else 0L
    practiceLastPlayedSegmentStartMs = practiceLastSegmentStartMs
    practiceRepeatSegmentStartMs = practiceLastSegmentStartMs
    if (!isInternalPracticeRepeating) {
        practiceCurrentSegmentPlayCount = 1
        _practiceCurrentSegmentPlayCountFlow.value = 1
    }
}

fun AudioPlayerManager.onSeekInPracticeMode(targetMs: Long) {
    if (!_isPracticeMode.value) return
    accumulatePracticePauseTime()
    lastPracticePauseTimestamp = 0L
    val wasPausing = _isPracticePausing.value
    practicePauseJob?.cancel()
    pendingEndOfTrackAfterPracticePause = false
    _isPracticePausing.value = false
    _practicePauseRemainingSeconds.value = 0f
    resetPracticeSegmentTracking(targetMs)
    if (wasPausing) {
        resume()
    }
}

internal fun AudioPlayerManager.handleEndOfTrackPracticePauseIfNeeded(): Boolean {
    if (!_isPracticeMode.value) return false
    if (_isPracticePausing.value) {
        pendingEndOfTrackAfterPracticePause = true
        stopProgressTracking()
        return true
    }

    val track = currentTrackValue ?: return false
    val effectiveDuration = if (track.isVirtualScene) {
        track.duration
    } else {
        val mpDur = try { mediaPlayer?.duration?.toLong() ?: 0L } catch (e: Exception) { 0L }
        if (mpDur > 0) mpDur else (track.duration.takeIf { it > 0 } ?: _duration.value)
    }
    if (effectiveDuration <= 0L) return false

    val segments = _currentPracticeSegments.value
    val hasUnpausedBoundary = segments.isNotEmpty() && practiceNextBoundaryIndex < segments.size
    val remainingTailMs = effectiveDuration - practiceLastSegmentStartMs
    if (!hasUnpausedBoundary && remainingTailMs < 800L) {
        return false
    }

    val boundary = if (hasUnpausedBoundary) segments.last() else effectiveDuration
    val endMs = maxOf(boundary, effectiveDuration)

    // Check if the final segment needs to repeat before pausing
    if (practiceCurrentSegmentPlayCount < practiceSegmentRepeatCount) {
        practiceCurrentSegmentPlayCount++
        _practiceCurrentSegmentPlayCountFlow.value = practiceCurrentSegmentPlayCount
        val replayStart = practiceLastSegmentStartMs.coerceAtLeast(0L)
        isInternalPracticeRepeating = true
        try {
            if (mediaPlayer != null) {
                seekTo(replayStart)
            } else {
                _currentPosition.value = replayStart
                onSeekInPracticeMode(replayStart)
            }
            coroutineScope.launch(Dispatchers.Main) {
                try {
                    mediaPlayer?.start()
                    _isPlaying.value = true
                    startProgressTracking()
                } catch (e: Exception) {
                    Log.w(TAG, "Replay end segment error: ${e.message}")
                }
            }
        } finally {
            isInternalPracticeRepeating = false
        }
        return true
    }

    practiceCurrentSegmentPlayCount = 1
    _practiceCurrentSegmentPlayCountFlow.value = 1

    val segmentLengthMs = if (segmentSource == "SUBTITLES") {
        val cues = _subtitlesCues.value
        val matchingCue = cues.find { cue ->
            val cueEnd = if (track.isVirtualScene) {
                cue.endMs - track.startOffsetMs
            } else {
                cue.endMs
            }
            Math.abs(cueEnd - boundary) <= 600L || Math.abs(cueEnd - effectiveDuration) <= 600L
        }
        if (matchingCue != null && matchingCue.endMs > matchingCue.startMs) {
            (matchingCue.endMs - matchingCue.startMs).coerceIn(1200L, 25000L)
        } else {
            (endMs - practiceLastSegmentStartMs).coerceIn(1200L, 25000L)
        }
    } else {
        (endMs - practiceLastSegmentStartMs).coerceAtLeast(1200L)
    }

    practiceLastPlayedSegmentStartMs = practiceLastSegmentStartMs
    practiceLastSegmentStartMs = endMs
    practiceNextBoundaryIndex = segments.size
    pendingEndOfTrackAfterPracticePause = true
    stopProgressTracking()
    triggerPracticePause(segmentLengthMs, boundary)
    return true
}

internal fun AudioPlayerManager.checkPracticeSegmentBoundary(currentPosMs: Long) {
    val segments = _currentPracticeSegments.value
    if (segments.isEmpty()) return

    // Auto re-sync when position jumped backwards (rewind) or jumped forward past next boundary window
    val currentExpectedBoundary = if (practiceNextBoundaryIndex in segments.indices) segments[practiceNextBoundaryIndex] else -1L
    if (practiceNextBoundaryIndex >= segments.size || currentPosMs < (practiceLastSegmentStartMs - 500L) || (currentExpectedBoundary != -1L && currentPosMs > currentExpectedBoundary + 1500L)) {
        resetPracticeSegmentTracking(currentPosMs)
    }

    if (practiceNextBoundaryIndex >= segments.size) return

    val boundary = segments[practiceNextBoundaryIndex]
    if (currentPosMs >= boundary && currentPosMs <= boundary + 1500L) {
        // Repeat segment if not yet played practiceSegmentRepeatCount times
        if (practiceCurrentSegmentPlayCount < practiceSegmentRepeatCount) {
            practiceCurrentSegmentPlayCount++
            _practiceCurrentSegmentPlayCountFlow.value = practiceCurrentSegmentPlayCount

            val replayStart = if (segmentSource == "SUBTITLES") {
                val cues = _subtitlesCues.value
                val matchingCue = cues.find { cue ->
                    val cueEnd = if (currentTrackValue?.isVirtualScene == true) {
                        cue.endMs - (currentTrackValue?.startOffsetMs ?: 0L)
                    } else {
                        cue.endMs
                    }
                    Math.abs(cueEnd - boundary) <= 500L
                }
                if (matchingCue != null) {
                    val cueStart = if (currentTrackValue?.isVirtualScene == true) {
                        (matchingCue.startMs - (currentTrackValue?.startOffsetMs ?: 0L)).coerceAtLeast(0L)
                    } else {
                        matchingCue.startMs.coerceAtLeast(0L)
                    }
                    cueStart
                } else {
                    practiceLastSegmentStartMs.coerceAtLeast(0L)
                }
            } else {
                practiceLastSegmentStartMs.coerceAtLeast(0L)
            }

            isInternalPracticeRepeating = true
            try {
                if (mediaPlayer != null) {
                    seekTo(replayStart)
                } else {
                    _currentPosition.value = replayStart
                    onSeekInPracticeMode(replayStart)
                }
                coroutineScope.launch(Dispatchers.Main) {
                    try {
                        if (_isPlaying.value) {
                            mediaPlayer?.start()
                            startProgressTracking()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Replay segment start error: ${e.message}")
                    }
                }
            } finally {
                isInternalPracticeRepeating = false
            }
            return
        }

        // All repetitions of this segment completed!
        practiceCurrentSegmentPlayCount = 1
        _practiceCurrentSegmentPlayCountFlow.value = 1

        val segmentLengthMs = if (segmentSource == "SUBTITLES") {
            val cues = _subtitlesCues.value
            val matchingCue = cues.find { cue ->
                val cueEnd = if (currentTrackValue?.isVirtualScene == true) {
                    cue.endMs - (currentTrackValue?.startOffsetMs ?: 0L)
                } else {
                    cue.endMs
                }
                Math.abs(cueEnd - boundary) <= 500L
            }
            if (matchingCue != null && matchingCue.endMs > matchingCue.startMs) {
                (matchingCue.endMs - matchingCue.startMs).coerceIn(1200L, 25000L)
            } else {
                (boundary - practiceLastSegmentStartMs).coerceIn(1200L, 25000L)
            }
        } else {
            (boundary - practiceLastSegmentStartMs).coerceAtLeast(1200L)
        }
        practiceLastPlayedSegmentStartMs = practiceLastSegmentStartMs
        practiceLastSegmentStartMs = boundary
        practiceNextBoundaryIndex++
        val track = currentTrackValue
        val effectiveDuration = if (track != null && track.isVirtualScene) {
            track.duration
        } else {
            val mpDur = try { mediaPlayer?.duration?.toLong() ?: 0L } catch (e: Exception) { 0L }
            if (mpDur > 0) mpDur else (track?.duration?.takeIf { it > 0 } ?: _duration.value)
        }
        if (effectiveDuration > 0L && boundary >= effectiveDuration - 150L) {
            flushContinuousSegmentsBeforeCompletion()
            pendingEndOfTrackAfterPracticePause = true
        }
        triggerPracticePause(segmentLengthMs, boundary)
    }
}

internal fun AudioPlayerManager.triggerPracticePause(segmentLengthMs: Long, boundaryMs: Long = 0L) {
    val multiplier = practicePauseMultiplier.coerceIn(0.25f, 10.0f)
    val pauseDurationMs = (segmentLengthMs * multiplier).toLong().coerceIn(1000L, 30000L)
    _isPracticePausing.value = true
    val totalSec = pauseDurationMs / 1000f
    _practicePauseTotalSeconds.value = totalSec
    _practicePauseRemainingSeconds.value = totalSec

    // Ensure active subtitle cue points to the segment that just finished speaking
    var matchingCueStartVirtual: Long? = null
    if (isSubtitlesEnabled.value) {
        val cues = _subtitlesCues.value
        if (cues.isNotEmpty()) {
            val track = currentTrackValue
            val physBoundary = if (track != null && track.isVirtualScene) track.startOffsetMs + boundaryMs else boundaryMs
            val matchingCue = cues.find { cue ->
                Math.abs(cue.endMs - physBoundary) <= 600L || (physBoundary >= cue.startMs && physBoundary <= cue.endMs + 300L)
            } ?: cues.lastOrNull { it.startMs <= physBoundary }
            if (matchingCue != null) {
                _activeSubtitleCue.value = matchingCue
                matchingCueStartVirtual = if (track != null && track.isVirtualScene) {
                    (matchingCue.startMs - track.startOffsetMs).coerceAtLeast(0L)
                } else {
                    matchingCue.startMs.coerceAtLeast(0L)
                }
            }
        }
    }

    practiceRepeatSegmentStartMs = matchingCueStartVirtual ?: practiceLastPlayedSegmentStartMs.coerceAtLeast(0L)

    accumulateActiveListeningTime()
    lastActivePlayTimestamp = 0L
    lastPracticePauseTimestamp = System.currentTimeMillis()
    if (!pendingEndOfTrackAfterPracticePause) {
        flushContinuousSegmentsOnEvent()
    }

    coroutineScope.launch(Dispatchers.Main) {
        try {
            mediaPlayer?.pause()
            _isPlaying.value = false
        } catch (e: Exception) {
            Log.w(TAG, "Practice pause error: ${e.message}")
        }
    }

    practicePauseJob?.cancel()
    practicePauseJob = coroutineScope.launch {
        val startTime = System.currentTimeMillis()
        val endTime = startTime + pauseDurationMs
        while (System.currentTimeMillis() < endTime && _isPracticeMode.value && _isPracticePausing.value) {
            val remaining = (endTime - System.currentTimeMillis()).coerceAtLeast(0L)
            _practicePauseRemainingSeconds.value = remaining / 1000f
            accumulatePracticePauseTime()
            delay(100L)
        }
        if (_isPracticeMode.value && _isPracticePausing.value) {
            resumeFromPracticePause()
        }
    }
}

fun AudioPlayerManager.resumeFromPracticePause() {
    accumulatePracticePauseTime()
    lastPracticePauseTimestamp = 0L
    practicePauseJob?.cancel()
    _isPracticePausing.value = false
    _practicePauseRemainingSeconds.value = 0f
    practiceCurrentSegmentPlayCount = 1
    _practiceCurrentSegmentPlayCountFlow.value = 1
    if (pendingEndOfTrackAfterPracticePause) {
        pendingEndOfTrackAfterPracticePause = false
        handlePhysicalEndOfTrack()
        return
    }
    coroutineScope.launch(Dispatchers.Main) {
        try {
            mediaPlayer?.start()
            lastActivePlayTimestamp = System.currentTimeMillis()
            _isPlaying.value = true
            startProgressTracking()
        } catch (e: Exception) {
            Log.e(TAG, "Resume from practice pause error: ${e.message}")
        }
    }
}

fun AudioPlayerManager.skipPracticePause() {
    resumeFromPracticePause()
}

fun AudioPlayerManager.repeatPracticeSegment() {
    accumulatePracticePauseTime()
    lastPracticePauseTimestamp = 0L
    practicePauseJob?.cancel()
    pendingEndOfTrackAfterPracticePause = false
    _isPracticePausing.value = false
    _practicePauseRemainingSeconds.value = 0f
    practiceCurrentSegmentPlayCount = 1
    _practiceCurrentSegmentPlayCountFlow.value = 1

    val targetMs = practiceRepeatSegmentStartMs.coerceAtLeast(0L)
    seekTo(targetMs)
    coroutineScope.launch(Dispatchers.Main) {
        try {
            mediaPlayer?.start()
            lastActivePlayTimestamp = System.currentTimeMillis()
            _isPlaying.value = true
            startProgressTracking()
        } catch (e: Exception) {
            Log.e(TAG, "repeatPracticeSegment error: ${e.message}")
        }
    }
}
// =========================================================================
// @END_LOCKED: Shadowing / Practice Mode Boundary & Pause State Machine
// =========================================================================
