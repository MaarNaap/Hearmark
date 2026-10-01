package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AudioTrack
import com.example.player.*

@Composable
fun PlayerFooterUtilitiesCard(
    track: AudioTrack,
    viewModel: AppViewModel,
    isDistractionFree: Boolean,
    speedState: Float,
    isPracticeMode: Boolean,
    isPracticeAnalyzing: Boolean,
    sleepTimerState: Int,
    onOpenPracticeSetup: () -> Unit
) {
    val context = LocalContext.current
    var showQueueSheet by remember { mutableStateOf(false) }
    var selectedQueueIndices by remember { mutableStateOf(setOf<Int>()) }
    val currentQueueState by AudioPlayerManager.currentQueueFlow.collectAsStateWithLifecycle()

    AnimatedVisibility(
        visible = !isDistractionFree,
        enter = fadeIn(tween(250)) + expandVertically(tween(250)),
        exit = fadeOut(tween(250)) + shrinkVertically(tween(250))
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Secondary Tools Row (Speed, Autoplay, Practice Mode, Queue, Sleep Timer)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Speed Button
                    var showSpeedDropdown by remember { mutableStateOf(false) }
                    val speedActive = Math.abs(speedState - 1.0f) > 0.01f
                    val speedColor = if (speedActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    Box {
                        TextButton(
                            onClick = { showSpeedDropdown = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = speedColor),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Speed,
                                    contentDescription = "Speed",
                                    modifier = Modifier.size(18.dp),
                                    tint = speedColor
                                )
                                Text(
                                    text = formatPlaybackSpeed(speedState),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showSpeedDropdown,
                            onDismissRequest = { showSpeedDropdown = false }
                        ) {
                            // + Button to fine tune speed by +0.1x per click
                            DropdownMenuItem(
                                text = {
                                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "+0.1x",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 13.sp
                                        )
                                    }
                                },
                                onClick = {
                                    val next = ((Math.round((speedState + 0.1f) * 10f)) / 10f).coerceIn(0.25f, 3.0f)
                                    AudioPlayerManager.setSpeed(next)
                                }
                            )

                            // Fixed speed options in descending order: 2x, 1.5x, 1x, 0.75x, .50x
                            val fixedSpeeds = listOf(2.0f, 1.5f, 1.0f, 0.75f, 0.5f)
                            fixedSpeeds.forEach { speed ->
                                val isSelected = Math.abs(speedState - speed) < 0.01f
                                val label = when (speed) {
                                    2.0f -> "2x"
                                    1.5f -> "1.5x"
                                    1.0f -> "1x"
                                    0.75f -> "0.75x"
                                    0.5f -> ".50x"
                                    else -> formatPlaybackSpeed(speed)
                                }
                                DropdownMenuItem(
                                    text = {
                                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                            Text(
                                                text = label,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    },
                                    onClick = {
                                        AudioPlayerManager.setSpeed(speed)
                                        showSpeedDropdown = false
                                    }
                                )
                            }

                            // - Button to fine tune speed by -0.1x per click
                            DropdownMenuItem(
                                text = {
                                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "-0.1x",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 13.sp
                                        )
                                    }
                                },
                                onClick = {
                                    val next = ((Math.round((speedState - 0.1f) * 10f)) / 10f).coerceIn(0.25f, 3.0f)
                                    AudioPlayerManager.setSpeed(next)
                                }
                            )
                        }
                    }

                    // 2. Autoplay Toggle (Icon only, no background change, no text, no toast)
                    val isAutoPlay by AudioPlayerManager.isAutoPlayEnabled.collectAsStateWithLifecycle()
                    IconButton(
                        onClick = {
                            AudioPlayerManager.toggleAutoPlay()
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (isAutoPlay) Icons.Filled.PlaylistPlay else Icons.Filled.PlaylistRemove,
                            contentDescription = "Autoplay",
                            tint = if (isAutoPlay) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Practice Mode (Listen & Imitate with Silence Detection)
                    PlayerPracticeModeButton(
                        track = track,
                        viewModel = viewModel,
                        context = context,
                        isPracticeMode = isPracticeMode,
                        isPracticeAnalyzing = isPracticeAnalyzing,
                        onOpenPracticeSetup = onOpenPracticeSetup
                    )

                    // 3. Queue Button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showQueueSheet = !showQueueSheet }
                            .height(38.dp)
                            .background(
                                if (showQueueSheet) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent
                            )
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.QueueMusic,
                            contentDescription = "Queue Icon",
                            tint = if (showQueueSheet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "${currentQueueState.size}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (showQueueSheet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // 5. Sleep Timer Button
                    var showSleepDropdown by remember { mutableStateOf(false) }
                    Box {
                        val isTimerRunning = sleepTimerState > 0 || sleepTimerState == -1
                        val timerColor = if (isTimerRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showSleepDropdown = true }
                                .height(44.dp)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Timer,
                                contentDescription = "Sleep Timer",
                                tint = timerColor,
                                modifier = Modifier.size(20.dp)
                            )
                            if (isTimerRunning) {
                                Text(
                                    text = if (sleepTimerState == -1) "EOF" else "${(sleepTimerState + 59) / 60}m",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = timerColor
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showSleepDropdown,
                            onDismissRequest = { showSleepDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Off") },
                                onClick = {
                                    AudioPlayerManager.cancelSleepTimer()
                                    showSleepDropdown = false
                                }
                            )
                            listOf(5, 10, 15, 30, 45, 60).forEach { min ->
                                DropdownMenuItem(
                                    text = { Text("${min}m") },
                                    onClick = {
                                        AudioPlayerManager.startSleepTimer(min)
                                        showSleepDropdown = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(Loc.getText("ends_at")) },
                                onClick = {
                                    AudioPlayerManager.setSleepAtEnd()
                                    showSleepDropdown = false
                                }
                            )
                        }
                    }
                }

                // Playback Queue sheet inside the glassmorphic card itself
                if (showQueueSheet) {
                    PlayerQueueSheet(
                        track = track,
                        currentQueueState = currentQueueState,
                        selectedQueueIndices = selectedQueueIndices,
                        onUpdateSelectedIndices = { selectedQueueIndices = it }
                    )
                }
            }
        }
    }
}
