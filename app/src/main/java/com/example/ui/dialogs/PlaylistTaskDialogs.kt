package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*

@Composable
fun AddToPlaylistDialog(
    track: AudioTrack,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPlaylistSelected: (Playlist) -> Unit,
    onCreatePlaylistClicked: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Loc.getText("choose_playlist_dialog_title")) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (playlists.isEmpty()) {
                    Text(
                        text = Loc.getText("empty_playlists_desc"),
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            onDismiss()
                            onCreatePlaylistClicked()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(Loc.getText("add_playlist"))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(playlists) { playlist ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onPlaylistSelected(playlist)
                                        onDismiss()
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlaylistPlay,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = playlist.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(Loc.getText("cancel"))
            }
        }
    )
}

fun formatScheduledDays(daysString: String): String {
    val days = daysString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    if (days.isEmpty()) return ""
    
    // Check if contains all 7 days of the week
    val weekDays = setOf("SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY")
    val upperDays = days.map { it.uppercase() }.toSet()
    if (upperDays.containsAll(weekDays) || days.size >= 7) {
        return Loc.getText("all_days")
    }
    
    val dayTranslations = mapOf(
        "SUNDAY" to mapOf("en" to "Sun", "ar" to "أحد"),
        "MONDAY" to mapOf("en" to "Mon", "ar" to "اثنين"),
        "TUESDAY" to mapOf("en" to "Tue", "ar" to "ثلاث"),
        "WEDNESDAY" to mapOf("en" to "Wed", "ar" to "أربع"),
        "THURSDAY" to mapOf("en" to "Thu", "ar" to "خميس"),
        "FRIDAY" to mapOf("en" to "Fri", "ar" to "جمعة"),
        "SATURDAY" to mapOf("en" to "Sat", "ar" to "سبت")
    )
    val localizedDays = days.map { dayName ->
        val trans = dayTranslations[dayName.uppercase()]
        if (trans != null) {
            trans[Loc.currentLanguage] ?: trans["en"] ?: dayName
        } else {
            dayName
        }
    }
    return localizedDays.joinToString(", ")
}

@Composable
fun AssociatedTasksDialog(
    itemType: String, // "TRACKS", "FOLDER", "PLAYLIST"
    itemId: Long,
    itemName: String,
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
    onViewTaskDetails: (Task) -> Unit,
    onCreateTask: () -> Unit
) {
    val allTasks by viewModel.allTasks.collectAsStateWithLifecycle()
    var trackTasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    
    LaunchedEffect(itemId, allTasks) {
        if (itemType == "TRACKS") {
            trackTasks = viewModel.getAllTasksForTrack(itemId)
        }
    }
    
    val itemTasks = if (itemType == "TRACKS") {
        trackTasks
    } else {
        remember(allTasks) {
            allTasks.filter { it.sourceType == itemType && it.sourceId == itemId }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "${Loc.getText("associated_tasks_title")}: $itemName",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (itemTasks.isEmpty()) {
                    Text(
                        text = Loc.getText("no_associated_tasks"),
                        fontSize = 14.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            onDismiss()
                            onCreateTask()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(Loc.getText("create_task"))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(itemTasks) { task ->
                            val progresses by viewModel.repository.getProgressForTaskFlow(task.id).collectAsStateWithLifecycle(emptyList())
                            val overallPercent = if (progresses.isNotEmpty()) {
                                var totalCompleted = 0
                                var totalRequired = 0
                                val targetVal = task.targetValue
                                if (task.targetType == "PLAY_COUNT") {
                                    progresses.forEach { p ->
                                        totalCompleted += minOf(p.completedPlayCount, targetVal)
                                        totalRequired += targetVal
                                    }
                                } else {
                                    progresses.forEach { p ->
                                        totalCompleted += minOf(p.getDaysList().size, targetVal)
                                        totalRequired += targetVal
                                    }
                                }
                                val isAllTracksDone = progresses.isNotEmpty() && progresses.all { it.isTrackCompleted }
                                if (isAllTracksDone) 1.0f else if (totalRequired > 0) totalCompleted.toFloat() / totalRequired.toFloat() else 0f
                            } else {
                                0f
                            }
                            
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onDismiss()
                                        onViewTaskDetails(task)
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (task.isCompleted) Icons.Filled.CheckCircle else Icons.Filled.PendingActions,
                                            contentDescription = "State",
                                            tint = if (task.isCompleted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(19.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(task.getDisplayTitle(), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text(
                                                text = formatScheduledDays(task.scheduledDays),
                                                fontSize = 11.sp,
                                                color = Color.Gray,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column(horizontalAlignment = Alignment.End) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "${(overallPercent * 100).toInt()}%",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                    contentDescription = "Navigate to Task Details",
                                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { overallPercent },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(CircleShape),
                                        color = if (task.isCompleted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(Loc.getText("cancel"))
            }
        }
    )
}
