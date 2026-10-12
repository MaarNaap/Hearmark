package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.player.AudioPlayerManager

@Composable
fun PlayerTransportControls(
    isPlayingState: Boolean,
    arePlaybackButtonsActive: Boolean,
    skipSecondsSetting: Int,
    isPracticeMode: Boolean = false,
    viewModel: AppViewModel? = null
) {
    val currentSpeed by AudioPlayerManager.playbackSpeed.collectAsStateWithLifecycle()
    val currentRepeats by AudioPlayerManager.practiceSegmentRepeatsFlow.collectAsStateWithLifecycle()

    var showSpeedMenu by remember { mutableStateOf(false) }
    var showRepeatsMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isPracticeMode) {
            // Standard Mode: Previous Track
            IconButton(
                onClick = { AudioPlayerManager.playPreviousTrack() },
                enabled = arePlaybackButtonsActive,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "Previous Track",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
            }
        } else {
            // Practice Mode Far Left: Speed Button (transparent background, primary color)
            Box {
                IconButton(
                    onClick = { showSpeedMenu = true },
                    enabled = arePlaybackButtonsActive,
                    modifier = Modifier.size(48.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Speed,
                            contentDescription = "Playback Speed",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = formatPlaybackSpeed(currentSpeed),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                DropdownMenu(
                    expanded = showSpeedMenu,
                    onDismissRequest = { showSpeedMenu = false }
                ) {
                    Text(
                        text = "Playback Speed",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                    )

                    // Fine-tuning buttons (-0.1x and +0.1x)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val next = ((Math.round((currentSpeed - 0.1f) * 10f)) / 10f).coerceIn(0.25f, 3.0f)
                                AudioPlayerManager.setSpeed(next)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("-0.1x", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Text(
                            text = formatPlaybackSpeed(currentSpeed),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )

                        FilledTonalButton(
                            onClick = {
                                val next = ((Math.round((currentSpeed + 0.1f) * 10f)) / 10f).coerceIn(0.25f, 3.0f)
                                AudioPlayerManager.setSpeed(next)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("+0.1x", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Instant learning presets: 0.75x, 0.85x, 1.0x, 1.25x (plus 0.5x and 1.5x)
                    val speedPresets = listOf(0.5f, 0.75f, 0.85f, 1.0f, 1.25f, 1.5f)
                    speedPresets.forEach { preset ->
                        val isSelected = Math.abs(currentSpeed - preset) < 0.03f
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${preset}x",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isSelected) {
                                        Text(
                                            text = "✓",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            },
                            onClick = {
                                AudioPlayerManager.setSpeed(preset)
                                showSpeedMenu = false
                            }
                        )
                    }
                }
            }
        }

        if (!isPracticeMode) {
            // Standard Mode: Rewind seconds
            Box(contentAlignment = Alignment.Center) {
                IconButton(
                    onClick = { AudioPlayerManager.skipBackward() },
                    enabled = arePlaybackButtonsActive
                ) {
                    Icon(
                        imageVector = Icons.Filled.FastRewind,
                        contentDescription = "Rewind",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text(
                    text = "$skipSecondsSetting",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(y = 12.dp)
                )
            }
        } else {
            // Practice Mode Inner Left: Previous Cut (clear, primary color, no badge)
            IconButton(
                onClick = { AudioPlayerManager.skipBackward() },
                enabled = arePlaybackButtonsActive,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "Previous Cut",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Center: Play / Pause Button (Standard and Practice Mode)
        IconButton(
            onClick = {
                if (isPlayingState) AudioPlayerManager.pause() else AudioPlayerManager.resume()
            },
            enabled = arePlaybackButtonsActive,
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        ) {
            Icon(
                imageVector = if (isPlayingState) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = "Play pause overlay",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(36.dp)
            )
        }

        if (!isPracticeMode) {
            // Standard Mode: ForwardFast seconds
            Box(contentAlignment = Alignment.Center) {
                IconButton(
                    onClick = { AudioPlayerManager.skipForward() },
                    enabled = arePlaybackButtonsActive
                ) {
                    Icon(
                        imageVector = Icons.Filled.FastForward,
                        contentDescription = "ForwardFast",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text(
                    text = "$skipSecondsSetting",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(y = 12.dp)
                )
            }
        } else {
            // Practice Mode Inner Right: Next Cut (clear, primary color, no badge)
            IconButton(
                onClick = { AudioPlayerManager.skipForward() },
                enabled = arePlaybackButtonsActive,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Next Cut",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        if (!isPracticeMode) {
            // Standard Mode: Next Track
            IconButton(
                onClick = { AudioPlayerManager.playNextTrack() },
                enabled = arePlaybackButtonsActive,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Next Track",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
            }
        } else {
            // Practice Mode Far Right: Repeat Segment Button (transparent background, primary color, presets 1x, 3x, 5x, 10x)
            Box {
                IconButton(
                    onClick = { showRepeatsMenu = true },
                    enabled = arePlaybackButtonsActive,
                    modifier = Modifier.size(48.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Repeat,
                            contentDescription = "Repeats",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "${currentRepeats}x",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                DropdownMenu(
                    expanded = showRepeatsMenu,
                    onDismissRequest = { showRepeatsMenu = false }
                ) {
                    // Fine-tuning stepper buttons (-1 and +1)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val next = (currentRepeats - 1).coerceAtLeast(1)
                                AudioPlayerManager.setPracticeRepeatCount(next)
                                viewModel?.practiceSegmentRepeatsSetting = next
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("-1", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Text(
                            text = "${currentRepeats}x",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        FilledTonalButton(
                            onClick = {
                                val next = (currentRepeats + 1).coerceAtMost(99)
                                AudioPlayerManager.setPracticeRepeatCount(next)
                                viewModel?.practiceSegmentRepeatsSetting = next
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("+1", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Presets: 1x, 3x, 5x, 10x
                    val repeatPresets = listOf(1, 3, 5, 10)
                    repeatPresets.forEach { count ->
                        val isSelected = currentRepeats == count
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${count}x",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isSelected) {
                                        Text(
                                            text = "✓",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            },
                            onClick = {
                                AudioPlayerManager.setPracticeRepeatCount(count)
                                viewModel?.practiceSegmentRepeatsSetting = count
                                showRepeatsMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}

