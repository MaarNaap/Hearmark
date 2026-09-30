package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AudioTrack
import com.example.player.SubtitleParser

@Composable
fun UnifiedTrackDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    track: AudioTrack,
    viewModel: AppViewModel,
    playlistId: Long? = null,
    taskId: Long? = null,
    onCreateTask: () -> Unit = {},
    onShowAssociatedTasks: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onPlay: () -> Unit = {},
    onViewInfo: () -> Unit = {},
    onEditScene: () -> Unit = {},
    onReviewScenes: () -> Unit = {},
    onImportScenesJson: () -> Unit = {}
) {
    val context = LocalContext.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        DropdownMenuItem(
            text = { Text(Loc.getText("play")) },
            onClick = {
                onDismissRequest()
                onPlay()
            }
        )
        DropdownMenuItem(
            text = { Text(Loc.getText("play_next")) },
            onClick = {
                onDismissRequest()
                viewModel.addTrackToPlayNext(track)
                Toast.makeText(context, Loc.getText("added_to_queue"), Toast.LENGTH_SHORT).show()
            }
        )
        DropdownMenuItem(
            text = { Text(Loc.getText("add_to_playlist")) },
            onClick = {
                onDismissRequest()
                onAddToPlaylist()
            }
        )
        DropdownMenuItem(
            text = { Text(Loc.getText("create_task")) },
            onClick = {
                onDismissRequest()
                onCreateTask()
            }
        )
        DropdownMenuItem(
            text = { Text(Loc.getText("show_associated_tasks")) },
            onClick = {
                onDismissRequest()
                onShowAssociatedTasks()
            }
        )
        DropdownMenuItem(
            text = { Text(Loc.getText("info")) },
            onClick = {
                onDismissRequest()
                onViewInfo()
            }
        )
        DropdownMenuItem(
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(Loc.getText("reset_segments"))
                }
            },
            onClick = {
                onDismissRequest()
                viewModel.resetTrackSegments(track)
                Toast.makeText(context, Loc.getText("segments_reset_success"), Toast.LENGTH_SHORT).show()
            }
        )
        if (track.isVirtualScene) {
            DropdownMenuItem(
                text = { Text(Loc.getText("edit_scene_title")) },
                onClick = {
                    onDismissRequest()
                    onEditScene()
                }
            )
        } else {
            DropdownMenuItem(
                text = { Text(Loc.getText("review_edit_scenes")) },
                onClick = {
                    onDismissRequest()
                    onReviewScenes()
                }
            )
            DropdownMenuItem(
                text = { Text(Loc.getText("ai_scene_detection_option")) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.MovieCreation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = {
                    onDismissRequest()
                    viewModel.openAiHub(
                        function = AiFunctionType.SCENES,
                        track = track
                    )
                }
            )
            DropdownMenuItem(
                text = { Text(Loc.getText("import_scenes_json_option")) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.FileUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = {
                    onDismissRequest()
                    onImportScenesJson()
                }
            )
        }
        DropdownMenuItem(
            text = { Text(Loc.getText("delete_history"), color = MaterialTheme.colorScheme.error) },
            onClick = {
                onDismissRequest()
                when {
                    taskId != null -> {
                        viewModel.resetTaskTrackProgress(taskId, track.id)
                        Toast.makeText(context, Loc.getText("re_activated_msg"), Toast.LENGTH_SHORT).show()
                    }
                    playlistId != null -> {
                        viewModel.removeTrackFromPlaylist(playlistId, track.id)
                        Toast.makeText(context, Loc.getText("delete_history"), Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        viewModel.deleteTrackFromApp(track)
                        Toast.makeText(context, Loc.getText("delete_history"), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun UnifiedAudioTrackRow(
    track: AudioTrack,
    isCurrentExecuting: Boolean,
    isPlaying: Boolean,
    viewModel: AppViewModel,
    playlistId: Long? = null,
    taskId: Long? = null,
    onTrackPlaylistMenuClicked: (AudioTrack) -> Unit = {},
    onCreateTask: (String, Long) -> Unit = { _, _ -> },
    onShowAssociatedTasks: (String, Long, String) -> Unit = { _, _, _ -> },
    playlistTracks: List<AudioTrack> = emptyList(),
    isSelected: Boolean = false,
    isBulkSelectMode: Boolean = false,
    onClick: () -> Unit = { viewModel.selectAndPlay(track, playlistTracks) },
    onLongClick: () -> Unit = {},
    customStartIcon: ImageVector? = null,
    customStartIconTint: Color? = null,
    taskProgressText: String? = null
) {
    var showTrackMenu by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showEditSceneDialog by remember { mutableStateOf(false) }
    var showReviewScenesDialog by remember { mutableStateOf(false) }
    var showImportScenesJsonDialog by remember { mutableStateOf(false) }

    if (showInfoDialog) {
        TrackInfoDialog(track = track, onDismiss = { showInfoDialog = false })
    }

    if (showEditSceneDialog) {
        EditVirtualSceneDialog(
            track = track,
            viewModel = viewModel,
            onDismiss = { showEditSceneDialog = false }
        )
    }

    if (showReviewScenesDialog) {
        ReviewScenesDialog(
            parentTrack = track,
            viewModel = viewModel,
            onDismiss = { showReviewScenesDialog = false },
            onPlayScene = { scene -> viewModel.selectAndPlay(scene, playlistTracks) }
        )
    }

    if (showImportScenesJsonDialog) {
        ImportScenesJsonDialog(
            parentTrack = track,
            viewModel = viewModel,
            onDismiss = { showImportScenesJsonDialog = false }
        )
    }

    val isVideoTrack = remember(track.filePath) { SubtitleParser.isVideoFile(track.filePath) }
    val displayName = remember(track.fileName, track.filePath, track.isVirtualScene) {
        track.getDisplayTitle()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
            } else if (isCurrentExecuting) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            }
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (isSelected || isCurrentExecuting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isBulkSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { _ -> onLongClick() }
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (track.isMissing) {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                            },
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = customStartIcon ?: if (isCurrentExecuting && isPlaying) {
                            Icons.Filled.PlayArrow
                        } else if (track.isMissing) {
                            Icons.Filled.Warning
                        } else {
                            getTrackFileIcon(track)
                        },
                        contentDescription = "Track",
                        tint = customStartIconTint ?: if (track.isMissing) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isCurrentExecuting) displayName else middleEllipse(displayName, 26), modifier = if (isCurrentExecuting) Modifier.basicMarquee() else Modifier,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.SansSerif
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (track.isMissing) {
                    Text(
                        text = Loc.getText("missing_file_warning"),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatDuration(track.duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${track.getProgressPercent()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )
                        if (!taskProgressText.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (taskProgressText.startsWith("🎧")) {
                                    Icon(
                                        imageVector = Icons.Filled.Headphones,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = taskProgressText.substring(2).trim(),
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                } else if (taskProgressText.startsWith("📅")) {
                                    Icon(
                                        imageVector = Icons.Filled.CalendarToday,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = taskProgressText.substring(2).trim(),
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                } else {
                                    Text(
                                        text = taskProgressText,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (taskId == null && taskProgressText.isNullOrEmpty() && !track.isMissing && track.playCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Headphones,
                        contentDescription = "Plays count",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${track.playCount}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (!isBulkSelectMode) {
                Box {
                    IconButton(
                        onClick = { showTrackMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    UnifiedTrackDropdownMenu(
                        expanded = showTrackMenu,
                        onDismissRequest = { showTrackMenu = false },
                        track = track,
                        viewModel = viewModel,
                        playlistId = playlistId,
                        taskId = taskId,
                        onCreateTask = { onCreateTask("TRACKS", track.id) },
                        onShowAssociatedTasks = { onShowAssociatedTasks("TRACKS", track.id, track.getDisplayTitle()) },
                        onAddToPlaylist = { onTrackPlaylistMenuClicked(track) },
                        onPlay = { onClick() },
                        onViewInfo = { showInfoDialog = true },
                        onEditScene = { showEditSceneDialog = true },
                        onReviewScenes = { showReviewScenesDialog = true },
                        onImportScenesJson = { showImportScenesJsonDialog = true }
                    )
                }
            }
        }
    }
}

fun middleEllipse(text: String, maxLength: Int = 26): String {
    if (text.length <= maxLength) return text
    val half = (maxLength - 3) / 2
    return text.take(half) + "..." + text.takeLast(maxLength - 3 - half)
}

@Composable
fun SmartFileNameText(
    text: String,
    isActive: Boolean = false,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    modifier: Modifier = Modifier,
    maxLength: Int = 26
) {
    Text(
        text = if (isActive) text else middleEllipse(text, maxLength),
        style = style,
        modifier = if (isActive) modifier.basicMarquee() else modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
