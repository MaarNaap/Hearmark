package com.example.ui

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.ai.AudioContextSummary
import com.example.data.AudioTrack
import com.example.data.Note
import com.example.data.Task
import com.example.player.AudioPlayerManager
import com.example.player.SubtitleParser
import java.io.File

fun executeAiAction(
    viewModel: AppViewModel,
    context: Context,
    function: AiFunctionType,
    contextType: AiContextType,
    track: AudioTrack?,
    notes: List<Note>,
    task: Task?
) {
    when (function) {
        AiFunctionType.CHAT -> {
            when (contextType) {
                AiContextType.FREE -> {
                    // Chat freely without any attached file/note/task context
                    viewModel.openChatWithContext(null)
                }

                AiContextType.TRACK -> {
                    val targetTrack = track ?: AudioPlayerManager.currentTrack.value
                    val isSelectedTrackPlaying = targetTrack != null && AudioPlayerManager.currentTrack.value?.id == targetTrack.id
                    val dur = if (isSelectedTrackPlaying) AudioPlayerManager.duration.value else (targetTrack?.duration ?: 0L)
                    val fullTranscript = getFullSubtitlesForTrack(targetTrack)

                    val summary = AudioContextSummary(
                        trackTitle = targetTrack?.getDisplayTitle(),
                        trackArtist = null,
                        currentPositionMs = null,
                        formattedPosition = null,
                        activeSubtitleLine = null,
                        fullSubtitlesText = fullTranscript?.ifBlank { null },
                        isFullSubtitlesContext = !fullTranscript.isNullOrBlank(),
                        audioFilePath = targetTrack?.filePath,
                        trackId = targetTrack?.id,
                        totalDurationMs = dur
                    )
                    viewModel.openChatWithContext(summary)
                }

                AiContextType.NOTEBOOK -> {
                    val notesSummary = if (notes.isNotEmpty()) {
                        notes.joinToString("; ") { "${it.text}${if (it.comment.isNotBlank()) ": " + it.comment else ""}" }
                    } else null

                    val summary = AudioContextSummary(
                        trackTitle = null,
                        trackArtist = null,
                        currentPositionMs = 0L,
                        formattedPosition = "",
                        activeSubtitleLine = notesSummary
                    )
                    viewModel.openChatWithContext(summary)
                }

                AiContextType.TASK -> {
                    val summary = AudioContextSummary(
                        trackTitle = null,
                        trackArtist = null,
                        currentPositionMs = 0L,
                        formattedPosition = "",
                        activeSubtitleLine = null,
                        activeTaskTitle = task?.getDisplayTitle()
                    )
                    viewModel.openChatWithContext(summary)
                }
            }
        }

        AiFunctionType.SUBTITLES -> {
            if (track == null) {
                Toast.makeText(context, Loc.getText("no_track_selected"), Toast.LENGTH_SHORT).show()
                return
            }
            val trackTitle = track.getDisplayTitle()
            Toast.makeText(
                context,
                Loc.getText("ai_generating_subtitles_background"),
                Toast.LENGTH_SHORT
            ).show()
            viewModel.generateSubtitlesForTrack(
                track = track,
                contextSummary = AudioContextSummary(trackTitle = trackTitle),
                onSuccess = {
                    Toast.makeText(
                        context,
                        "✓ " + Loc.getText("ai_subtitles_success") + ": $trackTitle",
                        Toast.LENGTH_LONG
                    ).show()
                },
                onError = { err ->
                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                }
            )
        }

        AiFunctionType.SCENES -> {
            if (track == null) {
                Toast.makeText(context, Loc.getText("no_track_selected"), Toast.LENGTH_SHORT).show()
                return
            }
            if (track.isVirtualScene) {
                Toast.makeText(context, Loc.getText("cannot_segment_virtual_scene"), Toast.LENGTH_LONG).show()
                return
            }
            val trackTitle = track.getDisplayTitle()
            Toast.makeText(
                context,
                Loc.getText("ai_scenes_background"),
                Toast.LENGTH_SHORT
            ).show()
            viewModel.startAiSceneDetection(track)
        }

        AiFunctionType.QUIZ -> {
            when (contextType) {
                AiContextType.FREE, AiContextType.TRACK -> {
                    if (track != null) {
                        viewModel.openUnifiedQuiz(
                            tabMode = QuizTabMode.TRACK,
                            initialTrack = track
                        )
                    } else {
                        viewModel.openUnifiedQuiz(tabMode = QuizTabMode.TRACK)
                    }
                }
                AiContextType.NOTEBOOK -> {
                    val effectiveNotes = if (notes.isNotEmpty()) notes else emptyList()
                    val parentTrackId = effectiveNotes.firstOrNull()?.trackId
                    val resolvedTrack = if (parentTrackId != null) {
                        viewModel.tracks.value.find { it.id == parentTrackId } ?: track
                    } else track

                    viewModel.openUnifiedQuiz(
                        tabMode = QuizTabMode.NOTEBOOK,
                        initialTrack = resolvedTrack,
                        initialNotes = effectiveNotes.ifEmpty { null }
                    )
                }
                AiContextType.TASK -> {
                    viewModel.openUnifiedQuiz(
                        tabMode = QuizTabMode.TRACK,
                        initialTrack = track
                    )
                }
            }
        }
    }
}

fun getFullSubtitlesForTrack(track: AudioTrack?): String? {
    if (track == null) return null
    val isPlaying = AudioPlayerManager.currentTrack.value?.id == track.id
    if (isPlaying) {
        val raw = AudioPlayerManager.getCurrentSubtitlesRawText()
        if (raw.isNotBlank()) return raw
    }
    if (!track.subtitleContent.isNullOrBlank()) {
        val formatted = SubtitleParser.formatForEditor(track.subtitleContent)
        if (formatted.isNotBlank()) return formatted
    }
    if (!track.subtitlePath.isNullOrBlank()) {
        try {
            val file = File(track.subtitlePath)
            if (file.exists() && file.canRead()) {
                val formatted = SubtitleParser.formatForEditor(file.readText())
                if (formatted.isNotBlank()) return formatted
            }
        } catch (e: Exception) {
            Log.w("AiHubActions", "Failed to read track subtitle file", e)
        }
    }
    val autoFile = SubtitleParser.findMatchingSubtitleFile(track.filePath)
    if (autoFile != null && autoFile.canRead()) {
        try {
            val formatted = SubtitleParser.formatForEditor(autoFile.readText())
            if (formatted.isNotBlank()) return formatted
        } catch (e: Exception) {
            Log.w("AiHubActions", "Failed to read matching subtitle file", e)
        }
    }
    val cues = AudioPlayerManager.getOrParseCuesForTrack(track)
    if (cues.isNotEmpty()) {
        return cues.joinToString("\n") { cue ->
            val timeStr = if (cue.isTimed && cue.startMs >= 0) "[${formatDuration(cue.startMs)}] " else ""
            "$timeStr${cue.text}"
        }
    }
    return null
}
