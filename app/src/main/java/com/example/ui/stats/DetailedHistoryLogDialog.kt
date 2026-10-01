package com.example.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HistoryToggleOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.AudioTrack
import com.example.data.PlaybackHistory
import com.example.data.Task
import com.example.data.TaskTrackProgress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DetailedHistoryLogDialog(
    viewModel: AppViewModel,
    context: Context,
    history: List<PlaybackHistory>,
    rangeFiltered: List<PlaybackHistory>,
    allTracks: List<AudioTrack>,
    allTasks: List<Task>,
    allTaskProgress: List<TaskTrackProgress>,
    selectedTaskIds: Set<Long>,
    selectedFolderIds: Set<Long>,
    selectedFileTrackIds: Set<Long>,
    onDismiss: () -> Unit
) {
    val filteredHistory = remember(rangeFiltered) {
        rangeFiltered.sortedByDescending { it.completedAt }
    }
    val isAr = Loc.currentLanguage == "ar"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Loc.getText("listening_history_title"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Quick Copy/Export filtered history button
                        if (filteredHistory.isNotEmpty()) {
                            IconButton(onClick = {
                                viewModel.copyHistoryClipboard(context, filteredHistory)
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "Copy filtered history",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close")
                        }
                    }
                }

                // Active Filters & Summary Strip
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            val periodLabel = when (viewModel.statsFilter) {
                                "today" -> Loc.getText("filter_today")
                                "week" -> Loc.getText("filter_week")
                                "month" -> Loc.getText("filter_month")
                                "ninety" -> Loc.getText("filter_ninety")
                                else -> Loc.getText("filter_all")
                            }
                            SuggestionChip(
                                onClick = {},
                                label = { Text(periodLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    labelColor = MaterialTheme.colorScheme.primary
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                modifier = Modifier.height(26.dp)
                            )

                            if (selectedTaskIds.isNotEmpty()) {
                                val taskFilterText = if (isAr) "${Loc.getText("filter_by_task")}: ${selectedTaskIds.size}" else "Tasks: ${selectedTaskIds.size}"
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(taskFilterText, fontSize = 10.sp) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                        labelColor = MaterialTheme.colorScheme.secondary
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
                                    modifier = Modifier.height(26.dp)
                                )
                            }

                            if (selectedFolderIds.isNotEmpty() || selectedFileTrackIds.isNotEmpty()) {
                                val count = selectedFolderIds.size + selectedFileTrackIds.size
                                val folderFilterText = if (isAr) "${Loc.getText("filter_by_folder_file")}: $count" else "Files: $count"
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(folderFilterText, fontSize = 10.sp) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                                        labelColor = MaterialTheme.colorScheme.tertiary
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)),
                                    modifier = Modifier.height(26.dp)
                                )
                            }
                        }

                        val countLabel = if (isAr) {
                            "${filteredHistory.size} ${if (filteredHistory.size in 3..10) "جلسات" else "جلسة"}"
                        } else {
                            "${filteredHistory.size} ${if (filteredHistory.size == 1) "session" else "sessions"}"
                        }
                        Text(
                            text = countLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (filteredHistory.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.HistoryToggleOff,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (history.isEmpty()) Loc.getText("no_history_yet") else if (isAr) "لا توجد جلسات استماع مطابقة للفلاتر المحددة" else "No listening history matches the selected filters",
                                color = Color.Gray,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    val trackMap = remember(allTracks) { allTracks.associateBy { it.id } }
                    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredHistory, key = { it.id }) { logItem ->
                            val track = trackMap[logItem.trackId]
                            val title = track?.getDisplayTitle() ?: logItem.trackName
                            val formattedDate = sdf.format(Date(logItem.completedAt))

                            val attachedTaskNames = remember(logItem, allTasks, allTaskProgress) {
                                val logged = logItem.getActiveTasksList()
                                if (logged.isNotEmpty()) {
                                    logged
                                } else if (logItem.activeTasks.isBlank()) {
                                    val trackId = logItem.trackId
                                    allTasks.filter { task ->
                                        val wasActiveThen = logItem.completedAt >= task.startDate && (task.endDate == null || logItem.completedAt <= task.endDate)
                                        wasActiveThen && when (task.sourceType) {
                                            "FOLDER" -> track?.parentFolderId == task.sourceId
                                            "TRACKS" -> allTaskProgress.any { it.taskId == task.id && it.trackId == trackId }
                                            else -> allTaskProgress.any { it.taskId == task.id && it.trackId == trackId }
                                        }
                                    }.map { it.getDisplayTitle() }.distinct()
                                } else {
                                    emptyList()
                                }
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = title,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = formattedDate,
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                        if (attachedTaskNames.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = (if (isAr) "المهام: " else "Tasks: ") + attachedTaskNames.joinToString(", "),
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Column(horizontalAlignment = Alignment.End) {
                                        val dur = if (logItem.actualListenedMs > 0L) logItem.actualListenedMs else logItem.durationMs
                                        Text(
                                            text = formatStatsDuration(dur),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (logItem.playbackSpeed > 0f && logItem.playbackSpeed != 1.0f) {
                                            Text(
                                                text = "${String.format(Locale.US, "%.1f", logItem.playbackSpeed)}x",
                                                fontSize = 10.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
