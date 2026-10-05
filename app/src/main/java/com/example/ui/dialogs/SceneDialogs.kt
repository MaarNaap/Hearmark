package com.example.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AudioTrack
import com.example.player.NoteAudioPlayer
import java.util.Locale

@Composable
fun EditVirtualSceneDialog(
    track: AudioTrack,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    var titleText by remember { mutableStateOf(track.fileName) }
    var startMs by remember { mutableLongStateOf(track.startOffsetMs) }
    var endMs by remember { mutableLongStateOf(track.endOffsetMs ?: (track.startOffsetMs + track.duration)) }
    var isPreviewPlaying by remember { mutableStateOf(false) }
    var startInputText by remember { mutableStateOf(formatTimestampMs(track.startOffsetMs)) }
    var endInputText by remember { mutableStateOf(formatTimestampMs(track.endOffsetMs ?: (track.startOffsetMs + track.duration))) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    DisposableEffect(Unit) {
        onDispose {
            NoteAudioPlayer.stop()
        }
    }

    AlertDialog(
        onDismissRequest = {
            NoteAudioPlayer.stop()
            onDismiss()
        },
        icon = {
            Icon(
                imageVector = Icons.Filled.MovieCreation,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(30.dp)
            )
        },
        title = {
            Text(
                text = Loc.getText("edit_virtual_track"),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Scene Title Field
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text(Loc.getText("scene_title_label")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Start Time Field & Controls
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = Loc.getText("scene_start"),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = startInputText,
                            onValueChange = { input ->
                                startInputText = input
                                parseTimestampToMs(input)?.let { parsed ->
                                    startMs = parsed
                                    errorMessage = if (endMs <= startMs) Loc.getText("scene_time_invalid") else null
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            leadingIcon = {
                                Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                        FilledTonalIconButton(
                            onClick = {
                                val next = (startMs - 1000L).coerceAtLeast(0L)
                                startMs = next
                                startInputText = formatTimestampMs(next)
                                errorMessage = if (endMs <= startMs) Loc.getText("scene_time_invalid") else null
                                if (isPreviewPlaying) {
                                    NoteAudioPlayer.playSnippet(context, track.filePath, -track.id, next, endMs)
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("-1s", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                        }
                        FilledTonalIconButton(
                            onClick = {
                                val next = startMs + 1000L
                                startMs = next
                                startInputText = formatTimestampMs(next)
                                errorMessage = if (endMs <= startMs) Loc.getText("scene_time_invalid") else null
                                if (isPreviewPlaying) {
                                    NoteAudioPlayer.playSnippet(context, track.filePath, -track.id, next, endMs)
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("+1s", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                        }
                    }
                }

                // End Time Field & Controls
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = Loc.getText("scene_end"),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = endInputText,
                            onValueChange = { input ->
                                endInputText = input
                                parseTimestampToMs(input)?.let { parsed ->
                                    endMs = parsed
                                    errorMessage = if (endMs <= startMs) Loc.getText("scene_time_invalid") else null
                                    if (isPreviewPlaying) {
                                        NoteAudioPlayer.playSnippet(context, track.filePath, -track.id, startMs, parsed)
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            leadingIcon = {
                                Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                        FilledTonalIconButton(
                            onClick = {
                                val next = (endMs - 1000L).coerceAtLeast(startMs + 1000L)
                                endMs = next
                                endInputText = formatTimestampMs(next)
                                errorMessage = if (endMs <= startMs) Loc.getText("scene_time_invalid") else null
                                if (isPreviewPlaying) {
                                    NoteAudioPlayer.playSnippet(context, track.filePath, -track.id, startMs, next)
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("-1s", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                        }
                        FilledTonalIconButton(
                            onClick = {
                                val next = endMs + 1000L
                                endMs = next
                                endInputText = formatTimestampMs(next)
                                errorMessage = if (endMs <= startMs) Loc.getText("scene_time_invalid") else null
                                if (isPreviewPlaying) {
                                    NoteAudioPlayer.playSnippet(context, track.filePath, -track.id, startMs, next)
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("+1s", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                        }
                    }
                }

                // Duration display & Preview snippet
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val durationCalc = maxOf(0L, endMs - startMs)
                            Text(
                                text = Loc.getFormattedText("scene_duration_label", formatDuration(durationCalc)),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "${formatTimestampMs(startMs)} -> ${formatTimestampMs(endMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                            )
                        }

                        Button(
                            onClick = {
                                if (isPreviewPlaying) {
                                    NoteAudioPlayer.stop()
                                    isPreviewPlaying = false
                                } else {
                                    NoteAudioPlayer.playSnippet(
                                        context = context,
                                        trackFilePath = track.filePath,
                                        noteId = -track.id,
                                        startMs = startMs,
                                        endMs = endMs
                                    )
                                    isPreviewPlaying = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPreviewPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (isPreviewPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isPreviewPlaying) Loc.getText("stop_preview") else Loc.getText("preview_scene"),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (endMs <= startMs) {
                        errorMessage = Loc.getText("scene_time_invalid")
                        return@Button
                    }
                    NoteAudioPlayer.stop()
                    viewModel.updateVirtualScene(
                        track = track,
                        newTitle = titleText,
                        newStartOffsetMs = startMs,
                        newEndOffsetMs = endMs,
                        onSuccess = onDismiss
                    )
                },
                enabled = endMs > startMs && titleText.isNotBlank()
            ) {
                Text(Loc.getText("save_scene_changes"))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        NoteAudioPlayer.stop()
                        viewModel.deleteVirtualScene(track, onSuccess = onDismiss)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(Loc.getText("delete_scene_btn"))
                }
                TextButton(
                    onClick = {
                        NoteAudioPlayer.stop()
                        onDismiss()
                    }
                ) {
                    Text(Loc.getText("cancel"))
                }
            }
        }
    )
}

@Composable
fun AddNewVirtualSceneDialog(
    parentTrack: AudioTrack,
    viewModel: AppViewModel,
    folderId: Long?,
    onDismiss: () -> Unit
) {
    var titleText by remember { mutableStateOf("") }
    var startMs by remember { mutableLongStateOf(0L) }
    var endMs by remember { mutableLongStateOf(minOf(60000L, parentTrack.duration)) }
    var isPreviewPlaying by remember { mutableStateOf(false) }
    var startInputText by remember { mutableStateOf(formatTimestampMs(0L)) }
    var endInputText by remember { mutableStateOf(formatTimestampMs(minOf(60000L, parentTrack.duration))) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    DisposableEffect(Unit) {
        onDispose {
            NoteAudioPlayer.stop()
        }
    }

    AlertDialog(
        onDismissRequest = {
            NoteAudioPlayer.stop()
            onDismiss()
        },
        icon = {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp)
            )
        },
        title = {
            Text(
                text = Loc.getText("create_scene_manual"),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text(Loc.getText("scene_title_label")) },
                    placeholder = { Text(Loc.getText("scene_intro_placeholder")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Start Time
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = Loc.getText("scene_start"),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = startInputText,
                            onValueChange = { input ->
                                startInputText = input
                                parseTimestampToMs(input)?.let { parsed ->
                                    startMs = parsed
                                    errorMessage = if (endMs <= startMs) Loc.getText("scene_time_invalid") else null
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            leadingIcon = {
                                Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                        FilledTonalIconButton(
                            onClick = {
                                val next = (startMs - 1000L).coerceAtLeast(0L)
                                startMs = next
                                startInputText = formatTimestampMs(next)
                                errorMessage = if (endMs <= startMs) Loc.getText("scene_time_invalid") else null
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("-1s", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                        }
                        FilledTonalIconButton(
                            onClick = {
                                val next = startMs + 1000L
                                startMs = next
                                startInputText = formatTimestampMs(next)
                                errorMessage = if (endMs <= startMs) Loc.getText("scene_time_invalid") else null
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("+1s", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                        }
                    }
                }

                // End Time
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = Loc.getText("scene_end"),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = endInputText,
                            onValueChange = { input ->
                                endInputText = input
                                parseTimestampToMs(input)?.let { parsed ->
                                    endMs = parsed
                                    errorMessage = if (endMs <= startMs) Loc.getText("scene_time_invalid") else null
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            leadingIcon = {
                                Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                        FilledTonalIconButton(
                            onClick = {
                                val next = (endMs - 1000L).coerceAtLeast(startMs + 1000L)
                                endMs = next
                                endInputText = formatTimestampMs(next)
                                errorMessage = if (endMs <= startMs) Loc.getText("scene_time_invalid") else null
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("-1s", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                        }
                        FilledTonalIconButton(
                            onClick = {
                                val next = endMs + 1000L
                                endMs = next
                                endInputText = formatTimestampMs(next)
                                errorMessage = if (endMs <= startMs) Loc.getText("scene_time_invalid") else null
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("+1s", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                        }
                    }
                }

                // Duration & Preview
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val durationCalc = maxOf(0L, endMs - startMs)
                            Text(
                                text = Loc.getFormattedText("scene_duration_label", formatDuration(durationCalc)),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "${formatTimestampMs(startMs)} -> ${formatTimestampMs(endMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                            )
                        }

                        Button(
                            onClick = {
                                if (isPreviewPlaying) {
                                    NoteAudioPlayer.stop()
                                    isPreviewPlaying = false
                                } else {
                                    NoteAudioPlayer.playSnippet(
                                        context = context,
                                        trackFilePath = parentTrack.filePath,
                                        noteId = -parentTrack.id,
                                        startMs = startMs,
                                        endMs = endMs
                                    )
                                    isPreviewPlaying = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isPreviewPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (isPreviewPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isPreviewPlaying) Loc.getText("stop_preview") else Loc.getText("preview_scene"),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (endMs <= startMs) {
                        errorMessage = Loc.getText("scene_time_invalid")
                        return@Button
                    }
                    NoteAudioPlayer.stop()
                    viewModel.addNewVirtualScene(
                        parentTrack = parentTrack,
                        folderId = folderId,
                        title = titleText,
                        startOffsetMs = startMs,
                        endOffsetMs = endMs,
                        onSuccess = onDismiss
                    )
                },
                enabled = endMs > startMs && titleText.isNotBlank()
            ) {
                Text(Loc.getText("save_scene_changes"))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    NoteAudioPlayer.stop()
                    onDismiss()
                }
            ) {
                Text(Loc.getText("cancel"))
            }
        }
    )
}

@Composable
fun ReviewScenesDialog(
    parentTrack: AudioTrack,
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
    onPlayScene: (AudioTrack) -> Unit
) {
    val scenesFlow = remember(parentTrack.id) { viewModel.getScenesForTrackFlow(parentTrack.id) }
    val scenes by scenesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var sceneToEdit by remember { mutableStateOf<AudioTrack?>(null) }
    var isAddingScene by remember { mutableStateOf(false) }
    var showImportJsonDialog by remember { mutableStateOf(false) }

    if (showImportJsonDialog) {
        ImportScenesJsonDialog(
            parentTrack = parentTrack,
            viewModel = viewModel,
            onDismiss = { showImportJsonDialog = false }
        )
    }

    if (sceneToEdit != null) {
        EditVirtualSceneDialog(
            track = sceneToEdit!!,
            viewModel = viewModel,
            onDismiss = { sceneToEdit = null }
        )
    }

    if (isAddingScene) {
        val targetFolderId = scenes.firstOrNull()?.parentFolderId ?: parentTrack.parentFolderId
        AddNewVirtualSceneDialog(
            parentTrack = parentTrack,
            viewModel = viewModel,
            folderId = targetFolderId,
            onDismiss = { isAddingScene = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.MovieCreation,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = Loc.getText("review_scenes_title"),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = parentTrack.fileName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Add Scene Manually (+) Icon Button at top
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(38.dp)
                ) {
                    IconButton(
                        onClick = { isAddingScene = true },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = Loc.getText("add_scene_btn"),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        },
        text = {
            if (scenes.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = Loc.getText("no_scenes_found"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Detect Scenes with AI button (primary action with fixed compact 44dp height)
                        Button(
                            onClick = {
                                onDismiss()
                                viewModel.openAiHub(
                                    function = AiFunctionType.SCENES,
                                    track = parentTrack
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = Loc.getText("detect_scenes_btn"),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // 2. Import Scenes from JSON (compact square 44x44dp icon button, never tall)
                        OutlinedIconButton(
                            onClick = { showImportJsonDialog = true },
                            modifier = Modifier.size(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FileUpload,
                                contentDescription = Loc.getText("import_scenes_json_btn"),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(scenes, key = { it.id }) { scene ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Scene number badge
                                val badgeText = String.format(Locale.US, "#%02d", scene.sceneNumber ?: 0)
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.padding(end = 10.dp)
                                ) {
                                    Text(
                                        text = badgeText,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = scene.fileName,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "${formatTimestampMs(scene.startOffsetMs)} - ${formatTimestampMs(scene.endOffsetMs ?: (scene.startOffsetMs + scene.duration))}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            text = "•",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        )
                                        Text(
                                            text = formatDuration(scene.duration),
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                // Actions: Play & Edit
                                IconButton(
                                    onClick = {
                                        onDismiss()
                                        onPlayScene(scene)
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "Play",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { sceneToEdit = scene },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = "Edit",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
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
                Text(Loc.getText("close_dialog"))
            }
        }
    )
}
