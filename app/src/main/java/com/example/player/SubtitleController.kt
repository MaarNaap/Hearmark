package com.example.player

import android.content.Context
import android.util.Log
import com.example.data.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

internal object SubtitleController {

    fun loadSubtitlesForTrack(track: AudioTrack): Unit = with(AudioPlayerManager) {
        coroutineScope.launch(Dispatchers.IO) {
            val rawCues = when {
                !track.subtitleContent.isNullOrBlank() -> {
                    SubtitleParser.parseContent(track.subtitleContent, track.subtitleOffsetMs)
                }
                !track.subtitlePath.isNullOrBlank() -> {
                    val file = File(track.subtitlePath)
                    SubtitleParser.parseFile(file, track.subtitleOffsetMs)
                }
                else -> {
                    val autoFile = SubtitleParser.findMatchingSubtitleFile(track.filePath)
                    if (autoFile != null) {
                        updateTrackState { t ->
                            if (t.id == track.id) t.copy(subtitlePath = autoFile.absolutePath) else t
                        }
                        SubtitleParser.parseFile(autoFile, track.subtitleOffsetMs)
                    } else {
                        emptyList()
                    }
                }
            }
            val cues = if (track.isVirtualScene) {
                val sceneStart = track.startOffsetMs
                val sceneEnd = track.endOffsetMs ?: (track.startOffsetMs + track.duration)
                rawCues.filter { cue ->
                    if (!cue.isTimed || cue.startMs < 0) true
                    else {
                        val cueEnd = if (cue.endMs > cue.startMs) cue.endMs else cue.startMs + 5000L
                        cueEnd >= sceneStart && cue.startMs <= sceneEnd
                    }
                }
            } else {
                rawCues
            }
            withContext(Dispatchers.Main) {
                _subtitlesCues.value = cues
                subtitleOffsetMs.value = track.subtitleOffsetMs
                val currentPhys = if (track.isVirtualScene) track.startOffsetMs + _currentPosition.value else _currentPosition.value
                updateActiveSubtitleCue(currentPhys)
            }
        }
        Unit
    }

    fun updateActiveSubtitleCue(positionMs: Long) = with(AudioPlayerManager) {
        if (!isSubtitlesEnabled.value) {
            if (_activeSubtitleCue.value != null) _activeSubtitleCue.value = null
            return
        }
        val cues = _subtitlesCues.value
        if (cues.isEmpty()) {
            if (_activeSubtitleCue.value != null) _activeSubtitleCue.value = null
            return
        }
        // When practice pause is active, preserve the current/previous cue for speech imitation
        if (_isPracticeMode.value && _isPracticePausing.value && _activeSubtitleCue.value != null) {
            return
        }
        val track = currentTrackValue
        val effectivePos = if (track != null && track.isVirtualScene && positionMs < track.startOffsetMs) {
            track.startOffsetMs + positionMs
        } else {
            positionMs
        }
        val active = cues.firstOrNull {
            it.isTimed && it.startMs >= 0 && effectivePos >= it.startMs && (effectivePos < it.endMs || (it.id == cues.lastOrNull { c -> c.isTimed }?.id && effectivePos <= it.endMs))
        }
        if (active != null) {
            _activeSubtitleCue.value = active
        } else if (!_isPracticeMode.value || !_isPracticePausing.value) {
            _activeSubtitleCue.value = null
        }
    }

    fun getCurrentSubtitlesRawText(): String = with(AudioPlayerManager) {
        val track = currentTrackValue ?: return ""
        if (!track.subtitleContent.isNullOrBlank()) {
            return SubtitleParser.formatForEditor(track.subtitleContent)
        }
        if (!track.subtitlePath.isNullOrBlank()) {
            try {
                val file = File(track.subtitlePath)
                if (file.exists() && file.canRead()) {
                    return SubtitleParser.formatForEditor(file.readText())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val cues = _subtitlesCues.value
        if (cues.isNotEmpty()) {
            val hasTimings = cues.any { it.isTimed && it.startMs >= 0 }
            return if (hasTimings) {
                val isSrtLike = cues.any { it.endMs > it.startMs + 1000L }
                if (isSrtLike) {
                    cues.joinToString("\n\n") { cue ->
                        val start = formatSubtitleTimestamp(cue.startMs)
                        val end = formatSubtitleTimestamp(cue.endMs)
                        "${cue.id}\n$start --> $end\n${cue.text}"
                    }
                } else {
                    cues.joinToString("\n\n") { cue ->
                        "${SubtitleParser.formatTimestampTag(cue.startMs)} ${cue.text}"
                    }
                }
            } else {
                cues.joinToString("\n\n") { it.text }
            }
        }
        return ""
    }

    private fun formatSubtitleTimestamp(ms: Long): String {
        val safeMs = ms.coerceAtLeast(0L)
        val hours = safeMs / 3600_000
        val rem = safeMs % 3600_000
        val mins = rem / 60_000
        val secs = (rem % 60_000) / 1000
        val millis = rem % 1000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, mins, secs, millis)
    }

    fun setSubtitleContentForCurrentTrack(content: String) {
        with(AudioPlayerManager) {
            val trackId = currentTrackValue?.id ?: return
            setSubtitleContentForTrack(trackId, content)
        }
    }

    fun setSubtitleContentForTrack(targetTrackId: Long, content: String) {
        with(AudioPlayerManager) {
            val repo = repository ?: return
            coroutineScope.launch(Dispatchers.IO) {
                val current = currentTrackValue
                if (current != null && (current.id == targetTrackId || current.parentTrackId == targetTrackId)) {
                    updateTrackState { t -> t.copy(subtitleContent = content) }
                    val updated = currentTrackValue ?: current.copy(subtitleContent = content)
                    loadSubtitlesForTrack(updated)
                    if (segmentSource == "SUBTITLES" || _isPracticeMode.value) {
                        val ctx = appContext
                        if (ctx != null) {
                            reanalyzePracticeSegmentsInternal(updated, ctx, repo, silent = false)
                        }
                    }
                } else {
                    // Background update: persist to database so track has subtitles ready when played later
                    val targetTrack = repo.getTrackById(targetTrackId)
                    if (targetTrack != null) {
                        val updated = targetTrack.copy(subtitleContent = content)
                        repo.updateTrack(updated)
                    }
                }
            }
        }
    }

    fun setSubtitleFileForCurrentTrack(filePath: String) {
        with(AudioPlayerManager) {
            val track = currentTrackValue ?: return
            coroutineScope.launch(Dispatchers.IO) {
                updateTrackState { t ->
                    if (t.id == track.id) t.copy(subtitlePath = filePath) else t
                }
                val updated = currentTrackValue ?: track.copy(subtitlePath = filePath)
                loadSubtitlesForTrack(updated)
                if (segmentSource == "SUBTITLES" || _isPracticeMode.value) {
                    val repo = repository
                    val ctx = appContext
                    if (repo != null && ctx != null) {
                        reanalyzePracticeSegmentsInternal(updated, ctx, repo, silent = false)
                    }
                }
            }
        }
    }

    fun clearSubtitlesForCurrentTrack() {
        with(AudioPlayerManager) {
            val track = currentTrackValue ?: return
            coroutineScope.launch(Dispatchers.IO) {
                updateTrackState { t ->
                    if (t.id == track.id) t.copy(subtitleContent = null, subtitlePath = null) else t
                }
                withContext(Dispatchers.Main) {
                    _subtitlesCues.value = emptyList()
                    _activeSubtitleCue.value = null
                }
            }
        }
    }

    fun adjustSubtitleOffset(deltaMs: Long) {
        with(AudioPlayerManager) {
            val track = currentTrackValue ?: return
            val newOffset = track.subtitleOffsetMs + deltaMs
            subtitleOffsetMs.value = newOffset
            coroutineScope.launch(Dispatchers.IO) {
                updateTrackState { t ->
                    if (t.id == track.id) t.copy(subtitleOffsetMs = newOffset) else t
                }
                val updated = currentTrackValue ?: track.copy(subtitleOffsetMs = newOffset)
                loadSubtitlesForTrack(updated)
            }
        }
    }

    fun resetSubtitleOffset() {
        with(AudioPlayerManager) {
            val track = currentTrackValue ?: return
            subtitleOffsetMs.value = 0L
            coroutineScope.launch(Dispatchers.IO) {
                updateTrackState { t ->
                    if (t.id == track.id) t.copy(subtitleOffsetMs = 0L) else t
                }
                val updated = currentTrackValue ?: track.copy(subtitleOffsetMs = 0L)
                loadSubtitlesForTrack(updated)
            }
        }
    }

    fun setSubtitleFontSize(size: Float) {
        with(AudioPlayerManager) {
            subtitleFontSize.value = size
            val ctx = appContext ?: return
            try {
                val sharedPref = ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                sharedPref.edit().putFloat("subtitle_font_size", size).apply()
            } catch (e: Exception) {
                Log.e(TAG, "Error saving subtitle font size: ${e.message}")
            }
        }
    }

    fun hasAvailableSubtitles(track: AudioTrack): Boolean = with(AudioPlayerManager) {
        if (!track.subtitleContent.isNullOrBlank()) return true
        if (!track.subtitlePath.isNullOrBlank()) {
            val f = File(track.subtitlePath)
            if (f.exists() && f.length() > 0) return true
        }
        val autoFile = SubtitleParser.findMatchingSubtitleFile(track.filePath)
        if (autoFile != null && autoFile.length() > 0) return true
        if (_subtitlesCues.value.isNotEmpty() && currentTrackValue?.id == track.id) return true
        return false
    }

    fun getOrParseCuesForTrack(track: AudioTrack): List<SubtitleCue> = with(AudioPlayerManager) {
        if (!track.subtitleContent.isNullOrBlank()) {
            val parsed = SubtitleParser.parseContent(track.subtitleContent, track.subtitleOffsetMs)
            if (parsed.isNotEmpty()) return parsed
        }
        if (!track.subtitlePath.isNullOrBlank()) {
            val file = File(track.subtitlePath)
            if (file.exists() && file.canRead()) {
                val parsed = SubtitleParser.parseFile(file, track.subtitleOffsetMs)
                if (parsed.isNotEmpty()) return parsed
            }
        }
        val autoFile = SubtitleParser.findMatchingSubtitleFile(track.filePath)
        if (autoFile != null && autoFile.canRead()) {
            val parsed = SubtitleParser.parseFile(autoFile, track.subtitleOffsetMs)
            if (parsed.isNotEmpty()) return parsed
        }
        if (currentTrackValue?.id == track.id && _subtitlesCues.value.isNotEmpty()) {
            return _subtitlesCues.value
        }
        return emptyList()
    }
}
