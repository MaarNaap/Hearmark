package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.player.AudioPlayerManager
import com.example.player.SubtitleParser

@Composable
fun SubtitlePasteEditDialog(
    initialText: String,
    isEditingExistingSubtitles: Boolean,
    playPositionState: Long,
    isPlayingState: Boolean,
    onDismiss: () -> Unit,
    onOpenLiveSync: () -> Unit,
    onSaveCleared: () -> Unit
) {
    val dialogTitle = if (isEditingExistingSubtitles) Loc.getText("edit_subtitles") else Loc.getText("paste_lyrics_title")
    val confirmText = if (isEditingExistingSubtitles) Loc.getText("save_changes") else Loc.getText("save_subtitles")

    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length)))
    }
    var showClearSubtitlesConfirm by remember { mutableStateOf(false) }

    if (showClearSubtitlesConfirm) {
        DeleteSubtitleConfirmDialog(
            onDismiss = { showClearSubtitlesConfirm = false },
            onConfirmDelete = {
                AudioPlayerManager.clearSubtitlesForCurrentTrack()
                showClearSubtitlesConfirm = false
                onSaveCleared()
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.95f)
                .imePadding(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // Compact Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isEditingExistingSubtitles) Icons.Filled.Edit else Icons.Filled.EditNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            text = dialogTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = Loc.getText("cancel"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Sleek Single-Row Toolbar (Timestamps & Controls)
                Surface(
                    tonalElevation = 1.dp,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Insert current timestamp at cursor button
                        FilledTonalButton(
                            onClick = {
                                val currentMs = AudioPlayerManager.currentPosition.value
                                val tag = SubtitleParser.formatShortTimeTag(currentMs)
                                val fullText = textFieldValue.text
                                val start = textFieldValue.selection.min
                                val end = textFieldValue.selection.max
                                val insertion = "$tag "
                                val updatedText = fullText.substring(0, start) + insertion + fullText.substring(end)
                                val newPos = start + insertion.length
                                textFieldValue = TextFieldValue(updatedText, TextRange(newPos))
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "${Loc.getText("insert_current_timestamp")} ${SubtitleParser.formatShortTimeTag(playPositionState)}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Mini Audio Controls & Tap-to-Sync shortcut
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { AudioPlayerManager.skipBackward() },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Filled.FastRewind, contentDescription = "Rewind", modifier = Modifier.size(16.dp))
                            }

                            FilledIconButton(
                                onClick = {
                                    if (isPlayingState) AudioPlayerManager.pause() else AudioPlayerManager.resume()
                                },
                                modifier = Modifier.size(30.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = if (isPlayingState) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = { AudioPlayerManager.skipForward() },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Filled.FastForward, contentDescription = "Forward", modifier = Modifier.size(16.dp))
                            }

                            IconButton(
                                onClick = {
                                    if (textFieldValue.text.isNotBlank()) {
                                        AudioPlayerManager.setSubtitleContentForCurrentTrack(textFieldValue.text)
                                    }
                                    onOpenLiveSync()
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    Icons.Filled.TouchApp,
                                    contentDescription = Loc.getText("open_tap_to_sync"),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Large Spacious Subtitle Editing Box taking full remaining space
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = {
                        textFieldValue = it
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    placeholder = {
                        Text(
                            Loc.getText("subtitle_edit_placeholder"),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.08f)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Footer Statistics & Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val linesCount = remember(textFieldValue.text) {
                        if (textFieldValue.text.isBlank()) 0 else textFieldValue.text.lines().size
                    }
                    val linesUnit = if (linesCount == 1) Loc.getText("lines_count_singular") else Loc.getText("lines_count_plural")
                    Text(
                        text = "$linesCount $linesUnit",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text(Loc.getText("cancel"), fontSize = 13.5.sp)
                        }
                        Button(
                            onClick = {
                                val textToSave = textFieldValue.text
                                if (textToSave.isNotBlank()) {
                                    AudioPlayerManager.setSubtitleContentForCurrentTrack(textToSave)
                                    onSaveCleared()
                                } else if (isEditingExistingSubtitles || initialText.isNotBlank()) {
                                    showClearSubtitlesConfirm = true
                                } else {
                                    AudioPlayerManager.clearSubtitlesForCurrentTrack()
                                    onSaveCleared()
                                }
                            },
                            modifier = Modifier.height(40.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(confirmText, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeleteSubtitleConfirmDialog(
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = Loc.getText("confirm_delete_subtitles_title"),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = Loc.getText("confirm_delete_subtitles_desc"),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(Loc.getText("delete_confirm"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Loc.getText("cancel"))
            }
        }
    )
}
