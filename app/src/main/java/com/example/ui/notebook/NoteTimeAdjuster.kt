package com.example.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccessTimeFilled
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AudioTrack
import com.example.player.NoteAudioPlayer

@Composable
fun NoteTimeAdjuster(
    selectedTrack: AudioTrack,
    startMs: Long,
    endMs: Long,
    minBound: Long,
    maxBound: Long,
    isCurrentSnippetPlaying: Boolean,
    context: Context,
    onUpdateRange: (Long, Long) -> Unit,
    onUnlinkTrack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Track name + Play / Unlink buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = getTrackFileIcon(selectedTrack),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = selectedTrack.fileName,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val clipDurationSec = ((endMs - startMs) / 1000).coerceAtLeast(1)
                    FilledTonalButton(
                        onClick = {
                            if (isCurrentSnippetPlaying) {
                                NoteAudioPlayer.stop()
                            } else {
                                NoteAudioPlayer.playSnippet(context, selectedTrack.filePath, -1L, startMs, endMs)
                            }
                        },
                        colors = if (isCurrentSnippetPlaying) {
                            ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            ButtonDefaults.filledTonalButtonColors()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isCurrentSnippetPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isCurrentSnippetPlaying) "Pause" else "Play",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("${clipDurationSec}s", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = onUnlinkTrack,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(Loc.getText("unlink_audio"), fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            // Start Time Section (Single Row with -1s, Timestamp, +1s)
            TimeAdjustRow(
                icon = Icons.Filled.AccessTime,
                label = Loc.getText("start_time"),
                timestampMs = startMs,
                onMinusOneSecond = {
                    val newStart = (startMs - 1000L).coerceAtLeast(minBound)
                    val newEnd = if (endMs < newStart + 500L) (newStart + 1000L).coerceAtMost(maxBound) else endMs
                    onUpdateRange(newStart, newEnd)
                    if (isCurrentSnippetPlaying) {
                        NoteAudioPlayer.playSnippet(context, selectedTrack.filePath, -1L, newStart, newEnd)
                    }
                },
                onPlusOneSecond = {
                    val newStart = (startMs + 1000L).coerceAtMost(maxBound)
                    val newEnd = if (endMs < newStart + 500L) (newStart + 1000L).coerceAtMost(maxBound) else endMs
                    onUpdateRange(newStart, newEnd)
                    if (isCurrentSnippetPlaying) {
                        NoteAudioPlayer.playSnippet(context, selectedTrack.filePath, -1L, newStart, newEnd)
                    }
                }
            )

            // End Time Section (Single Row with -1s, Timestamp, +1s)
            TimeAdjustRow(
                icon = Icons.Filled.AccessTimeFilled,
                label = Loc.getText("end_time"),
                timestampMs = endMs,
                onMinusOneSecond = {
                    val newEnd = (endMs - 1000L).coerceAtLeast(startMs + 500L)
                    onUpdateRange(startMs, newEnd)
                    if (isCurrentSnippetPlaying) {
                        NoteAudioPlayer.playSnippet(context, selectedTrack.filePath, -1L, startMs, newEnd)
                    }
                },
                onPlusOneSecond = {
                    val newEnd = (endMs + 1000L).coerceAtMost(maxBound)
                    onUpdateRange(startMs, newEnd)
                    if (isCurrentSnippetPlaying) {
                        NoteAudioPlayer.playSnippet(context, selectedTrack.filePath, -1L, startMs, newEnd)
                    }
                }
            )
        }
    }
}

@Composable
fun TimeAdjustRow(
    icon: ImageVector,
    label: String,
    timestampMs: Long,
    onMinusOneSecond: () -> Unit,
    onPlusOneSecond: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilledTonalButton(
                onClick = onMinusOneSecond,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text("-1s", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
            ) {
                Text(
                    text = formatDuration(timestampMs),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            FilledTonalButton(
                onClick = onPlusOneSecond,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text("+1s", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
