package com.example.ui.waveform

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.AudioPlayerManager
import com.example.ui.Loc
import java.util.Locale

@Composable
internal fun WaveformTransportAndCutCard(
    isPlaying: Boolean,
    positionMs: () -> Long,
    effectiveDuration: Long,
    cuts: List<Long>,
    selectedCutIndex: Int?,
    onAddCutAtPlayhead: () -> Unit,
    onPreviewSegment: (Int) -> Unit,
    onNudgeSelectedCut: (Long) -> Unit,
    onRemoveCut: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Playback & "+ Add Cut Here" Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Playback buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(
                        onClick = {
                            val target = (positionMs() - 2000L).coerceAtLeast(0L)
                            AudioPlayerManager.seekTo(target, isPhysicalTimestamp = false)
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Filled.Replay, contentDescription = "-2s", modifier = Modifier.size(16.dp))
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    FilledIconButton(
                        onClick = {
                            if (isPlaying) AudioPlayerManager.pause() else AudioPlayerManager.resume()
                        },
                        modifier = Modifier.size(38.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play/Pause",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    FilledTonalIconButton(
                        onClick = {
                            val target = (positionMs() + 2000L).coerceAtMost(effectiveDuration)
                            AudioPlayerManager.seekTo(target, isPhysicalTimestamp = false)
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Filled.Forward5, contentDescription = "+2s", modifier = Modifier.size(16.dp))
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = formatWaveformTimestamp(positionMs()),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // "+ Add Cut Here" Button
                Button(
                    onClick = onAddCutAtPlayhead,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("btn_add_cut_at_playhead")
                ) {
                    Icon(Icons.Filled.ContentCut, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(Loc.getText("add_cut_at_playhead"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Selected Cut Nudge & Preview Bar
            val selIdx = selectedCutIndex
            if (selIdx != null && selIdx in cuts.indices) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = Loc.getFormattedText("segment_num", selIdx + 1) +
                                    " (${formatWaveformTimestamp(cuts[selIdx])})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Preview segment
                        OutlinedButton(
                            onClick = { onPreviewSegment(selIdx) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Filled.PlayCircleOutline, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(Loc.getText("play_segment_preview"), fontSize = 10.sp)
                        }

                        // Nudge backward 50ms
                        FilledTonalButton(
                            onClick = { onNudgeSelectedCut(-50L) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 5.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp).testTag("btn_nudge_backward")
                        ) {
                            Text("-50ms", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        // Micro-nudge backward 10ms
                        FilledTonalButton(
                            onClick = { onNudgeSelectedCut(-10L) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp).testTag("btn_nudge_back_10ms")
                        ) {
                            Text("-10ms", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        // Micro-nudge forward 10ms
                        FilledTonalButton(
                            onClick = { onNudgeSelectedCut(10L) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp).testTag("btn_nudge_fwd_10ms")
                        ) {
                            Text("+10ms", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        // Nudge forward 50ms
                        FilledTonalButton(
                            onClick = { onNudgeSelectedCut(50L) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 5.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp).testTag("btn_nudge_forward")
                        ) {
                            Text("+50ms", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        // Delete cut
                        IconButton(
                            onClick = { onRemoveCut(selIdx) },
                            modifier = Modifier.size(28.dp).testTag("btn_delete_selected_cut")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = Loc.getText("delete_cut"),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun WaveformPresetsRow(
    hasSubtitles: Boolean,
    hasCuts: Boolean,
    onImportFromSilence: () -> Unit,
    onImportFromSubtitles: () -> Unit,
    onClearAllCuts: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilledTonalButton(
                onClick = onImportFromSilence,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.height(28.dp).testTag("btn_import_from_silence")
            ) {
                Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(Loc.getText("copy_from_silence"), fontSize = 10.sp)
            }

            if (hasSubtitles) {
                FilledTonalButton(
                    onClick = onImportFromSubtitles,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp).testTag("btn_import_from_subtitles")
                ) {
                    Icon(Icons.Filled.Subtitles, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(Loc.getText("copy_from_subtitles"), fontSize = 10.sp)
                }
            }
        }

        if (hasCuts) {
            TextButton(
                onClick = onClearAllCuts,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                modifier = Modifier.height(26.dp)
            ) {
                Text(Loc.getText("clear_all_cuts"), fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
