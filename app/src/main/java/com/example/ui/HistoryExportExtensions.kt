package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import com.example.data.PlaybackHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// --- EXPORT HISTORY SYSTEM EXTENSIONS ---
fun AppViewModel.copyHistoryClipboard(context: Context, history: List<PlaybackHistory>) {
    val sb = java.lang.StringBuilder()

    // First row headers (Tab-Separated for Google Sheets & Excel)
    val headers = listOf(
        Loc.getText("export_header_date_time"),
        Loc.getText("export_header_track_name"),
        Loc.getText("export_header_associated_tasks"),
        Loc.getText("export_header_actual_listen_time"),
        Loc.getText("export_header_file_duration"),
        Loc.getText("export_header_playback_speed"),
        Loc.getText("export_header_time_saved")
    )
    sb.append(headers.joinToString("\t")).append("\n")

    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    val tracksMap = tracks.value.associateBy { it.id }
    val tasksList = allTasks.value
    val progressList = allTaskProgress.value

    fun formatClock(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    history.forEach { log ->
        val dateTime = sdf.format(Date(log.completedAt))
        val track = tracksMap[log.trackId]
        val rawName = log.trackName.ifBlank { track?.fileName ?: "Track #${log.trackId}" }
        val cleanName = rawName.substringBeforeLast(".").replace("\t", " ").replace("\r", "").replace("\n", " ").trim()

        val tasksForTrack = run {
            val logged = log.getActiveTasksList()
            if (logged.isNotEmpty()) {
                logged
            } else if (log.activeTasks.isBlank()) {
                // Fallback for legacy records: check tasks active at log.completedAt
                val trackId = log.trackId
                tasksList.filter { task ->
                    val wasActiveThen = log.completedAt >= task.startDate && (task.endDate == null || log.completedAt <= task.endDate)
                    wasActiveThen && when (task.sourceType) {
                        "FOLDER" -> track?.parentFolderId != null && track.parentFolderId == task.sourceId
                        else -> progressList.any { it.taskId == task.id && it.trackId == trackId }
                    }
                }.map { it.getDisplayTitle() }
            } else {
                emptyList()
            }
        }.map { it.replace("\t", " ").replace("\r", "").replace("\n", " ").trim() }.distinct()
        val tasksStr = if (tasksForTrack.isNotEmpty()) tasksForTrack.joinToString(", ") else "-"

        val speed = if (log.playbackSpeed > 0f) log.playbackSpeed else 1.0f
        val fileDur = if (log.durationMs > 0L) log.durationMs else (track?.duration ?: 0L)
        val actualDur = if (log.actualListenedMs > 0L) log.actualListenedMs else (if (speed > 0f) (fileDur / speed).toLong() else fileDur)
        val timeSaved = maxOf(0L, fileDur - actualDur)

        val row = listOf(
            dateTime,
            cleanName,
            tasksStr,
            formatClock(actualDur),
            formatClock(fileDur),
            String.format(Locale.US, "%.1fx", speed),
            formatClock(timeSaved)
        )
        sb.append(row.joinToString("\t")).append("\n")
    }

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Listening Log", sb.toString().trimEnd())
    clipboard.setPrimaryClip(clip)

    Toast.makeText(context, Loc.getText("export_success"), Toast.LENGTH_LONG).show()
}

fun AppViewModel.clearAllPlaybackHistory() {
    viewModelScope.launch(Dispatchers.IO) {
        repository.clearAllPlaybackHistory()
    }
}

fun AppViewModel.prunePlaybackHistoryOlderThanOneYear() {
    viewModelScope.launch(Dispatchers.IO) {
        val oneYearAgoTime = System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000L
        repository.deletePlaybackHistoryOlderThan(oneYearAgoTime)
    }
}

fun AppViewModel.prunePlaybackHistoryToRecent1000() {
    viewModelScope.launch(Dispatchers.IO) {
        repository.deletePlaybackHistoryExceptTop(1000)
    }
}
