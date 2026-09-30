package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.player.*
import java.util.Locale

@Composable
fun PlayerPracticeStatusPill(
    track: AudioTrack,
    isPracticePausing: Boolean,
    practicePauseRemaining: Float,
    onOpenPracticeSetup: () -> Unit
) {
    val practiceSegmentsList by AudioPlayerManager.currentPracticeSegments.collectAsStateWithLifecycle()
    val practiceMultiplierState by AudioPlayerManager.practicePauseMultiplierFlow.collectAsStateWithLifecycle()
    val currentPlayingTrack by AudioPlayerManager.currentTrack.collectAsStateWithLifecycle()
    val activeTrack = currentPlayingTrack ?: track
    val activePracticeSource = AudioPlayerManager.getActivePracticeSourceForTrack(activeTrack)
    val effectiveSegments = if (practiceSegmentsList.isNotEmpty()) practiceSegmentsList else activeTrack.getPracticeSegmentsList()
    val cutsCount = effectiveSegments.size

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.60f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .testTag("pill_active_practice_status")
    ) {
        if (isPracticePausing) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.RecordVoiceOver,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(
                        text = Loc.getText("practice_your_turn"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.50f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Timer,
                                contentDescription = "Stopwatch",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "${String.format(Locale.US, "%.1f", practicePauseRemaining)}s",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Repeat button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                            .clickable { AudioPlayerManager.repeatPracticeSegment() }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Replay,
                            contentDescription = Loc.getText("practice_repeat_segment"),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = Loc.getText("practice_repeat_segment"),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Skip Pause button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                            .clickable { AudioPlayerManager.skipPracticePause() }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = Loc.getText("practice_skip_pause"),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = Loc.getText("practice_skip_pause"),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable { onOpenPracticeSetup() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (activePracticeSource) {
                                "MANUAL" -> Icons.Filled.GraphicEq
                                "SUBTITLES" -> Icons.Filled.Subtitles
                                else -> Icons.Filled.RecordVoiceOver
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(
                        text = when (activePracticeSource) {
                            "MANUAL" -> Loc.getText("practice_source_manual_short")
                            "SUBTITLES" -> Loc.getText("practice_source_subtitles_short")
                            else -> Loc.getText("practice_source_silence_short")
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.50f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCut,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "$cutsCount ${Loc.getText("cuts_label")} • ${String.format(Locale.US, "%.2f", practiceMultiplierState)}x",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Customize button styled exactly like Repeat/Skip buttons
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                            .clickable { onOpenPracticeSetup() }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = Loc.getText("tap_to_change_source"),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = Loc.getText("tap_to_change_source"),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Exit button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                            .clickable { AudioPlayerManager.setFocusMode(false) }
                            .padding(horizontal = 5.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Exit Practice Mode",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
}

@Composable
fun PlayerTransportControls(
    isPlayingState: Boolean,
    arePlaybackButtonsActive: Boolean,
    skipSecondsSetting: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
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
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
                    val practiceColor = if (isPracticeMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isPracticeMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent
                            )
                            .combinedClickable(
                                onClick = {
                                    if (isPracticeMode) {
                                        AudioPlayerManager.togglePracticeMode(context, viewModel.repository)
                                        Toast.makeText(context, Loc.getText("practice_mode_off"), Toast.LENGTH_SHORT).show()
                                    } else {
                                        if (track.practiceSegments.isNullOrBlank()) {
                                            // Present clear setup sheet so user can choose source and pause multiplier
                                            onOpenPracticeSetup()
                                        } else {
                                            val repo = viewModel.repository
                                            val willAnalyze = AudioPlayerManager.needsReanalysis(track, context)
                                            AudioPlayerManager.togglePracticeMode(context, repo)
                                            if (willAnalyze) {
                                                Toast.makeText(context, Loc.getText("practice_mode_analyzing"), Toast.LENGTH_SHORT).show()
                                            } else {
                                                val src = track.getPracticeSegmentsSource() ?: AudioPlayerManager.segmentSource
                                                val srcName = when (src) {
                                                    "MANUAL" -> Loc.getText("practice_source_manual_short")
                                                    "SUBTITLES" -> Loc.getText("practice_source_subtitles_short")
                                                    else -> Loc.getText("practice_source_silence_short")
                                                }
                                                Toast.makeText(context, "${Loc.getText("practice_mode_on")}: $srcName", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                onLongClick = {
                                    onOpenPracticeSetup()
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPracticeAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.RecordVoiceOver,
                                contentDescription = Loc.getText("practice_mode"),
                                tint = practiceColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

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
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        // Header bar with title and clear button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.QueueMusic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${Loc.getText("queue")} (${currentQueueState.size})",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (currentQueueState.size > 1) {
                                TextButton(
                                    onClick = { AudioPlayerManager.clearQueue() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ClearAll,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = Loc.getText("clear_queue"),
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // Action bar for selected queue tracks
                        if (selectedQueueIndices.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp, top = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${selectedQueueIndices.size} ${Loc.getText("selected")}",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = { selectedQueueIndices = emptySet() }) {
                                        Text(Loc.getText("cancel"), fontSize = 11.sp)
                                    }
                                    IconButton(
                                        onClick = {
                                            AudioPlayerManager.removeTracksFromQueue(selectedQueueIndices.toList())
                                            selectedQueueIndices = emptySet()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "Remove chosen files from queue",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        if (currentQueueState.isEmpty()) {
                            Text(
                                text = Loc.getText("empty_queue"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        } else {
                            // Scrollable container bounded to comfortable height so all 20+ items are scrollable & visible
                            val queueScrollState = rememberScrollState()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .verticalScroll(queueScrollState)
                            ) {
                                currentQueueState.forEachIndexed { idx, qTrack ->
                                    val isRunning = qTrack.id == track.id
                                    val isChosen = selectedQueueIndices.contains(idx)

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (isChosen) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                else if (isRunning) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                                else Color.Transparent,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .combinedClickable(
                                                onClick = {
                                                    if (selectedQueueIndices.isNotEmpty()) {
                                                        selectedQueueIndices = if (isChosen) {
                                                            selectedQueueIndices - idx
                                                        } else {
                                                            selectedQueueIndices + idx
                                                        }
                                                    } else {
                                                        AudioPlayerManager.playTrack(qTrack, currentQueueState)
                                                    }
                                                },
                                                onLongClick = {
                                                    selectedQueueIndices = selectedQueueIndices + idx
                                                }
                                            )
                                            .padding(vertical = 8.dp, horizontal = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${idx + 1}.",
                                                fontSize = 12.sp,
                                                color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = if (isRunning) FontWeight.Bold else FontWeight.Normal,
                                                modifier = Modifier.width(24.dp)
                                            )
                                            Text(
                                                text = if (isRunning) qTrack.getDisplayTitle() else middleEllipse(qTrack.getDisplayTitle(), 26),
                                                modifier = if (isRunning) Modifier.basicMarquee() else Modifier,
                                                fontSize = 13.sp,
                                                fontWeight = if (isRunning) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (isChosen) {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = "Selected indicator",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp).padding(end = 4.dp)
                                                )
                                            }
                                            Text(
                                                text = formatDuration(qTrack.duration),
                                                fontSize = 12.sp,
                                                color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "•",
                                                fontSize = 12.sp,
                                                color = if (isRunning) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "${qTrack.getProgressPercent()}%",
                                                fontSize = 12.sp,
                                                color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (idx < currentQueueState.size - 1) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
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
