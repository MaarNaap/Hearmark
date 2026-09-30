package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.player.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerTopBarAndFocusHeader(
    isDistractionFree: Boolean,
    isPracticeMode: Boolean,
    track: AudioTrack,
    viewModel: AppViewModel,
    pagerState: PagerState,
    subtitlesCuesState: List<SubtitleCue>,
    activeSubtitleCueState: SubtitleCue?,
    playPositionState: Long,
    durationState: Long,
    relatedTasks: List<Task>,
    dismiss: () -> Unit,
    onQuickAddNotePrompt: (Long, Long, Long, String) -> Unit,
    onQuickAddTaskPrompt: (Long) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    AnimatedVisibility(
        visible = isDistractionFree,
        enter = fadeIn(tween(250)) + expandVertically(tween(250)),
        exit = fadeOut(tween(250)) + shrinkVertically(tween(250))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = {
                    AudioPlayerManager.setFocusMode(false)
                },
                modifier = Modifier
                    .size(38.dp)
                    .testTag("btn_exit_focus_mode")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = Loc.getText("exit_focus_mode"),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }

            if (isPracticeMode) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                    modifier = Modifier.clickable {
                        AudioPlayerManager.setFocusMode(false)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            text = Loc.getText("practice_mode"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = Loc.getText("exit_focus_mode"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            Box(modifier = Modifier.size(38.dp))
        }
    }

    AnimatedVisibility(
        visible = !isDistractionFree,
        enter = fadeIn(tween(250)) + expandVertically(tween(250)),
        exit = fadeOut(tween(250)) + shrinkVertically(tween(250))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // iOS drag handle that detects swiping down to minimize
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dragAmount ->
                                if (dragAmount > 25f) {
                                    dismiss()
                                }
                            }
                        )
                    }
                    .padding(top = 10.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }

            // Top Header row with Huawei Music style swipable tab headers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dragAmount ->
                                if (dragAmount > 25f) {
                                    dismiss()
                                }
                            }
                        )
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = dismiss) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Small tiny circle navigation indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    (0 until 2).forEach { index ->
                        val selected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .size(if (selected) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                )
                                .clickable {
                                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                }
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = {
                        val fullSubtitles = AudioPlayerManager.getCurrentSubtitlesRawText()
                        val fullTranscript = if (fullSubtitles.isNotBlank()) {
                            fullSubtitles
                        } else {
                            subtitlesCuesState.joinToString("\n") { cue ->
                                val timeStr = if (cue.isTimed && cue.startMs >= 0) "[${formatDuration(cue.startMs)}] " else ""
                                "$timeStr${cue.text}"
                            }
                        }
                        val dur = durationState
                        val formattedPos = if (dur > 0) "${formatDuration(playPositionState)} / ${formatDuration(dur)}" else formatDuration(playPositionState)
                        viewModel.openChatWithContext(
                            com.example.ai.AudioContextSummary(
                                trackTitle = track.getDisplayTitle(),
                                trackArtist = null,
                                currentPositionMs = playPositionState,
                                formattedPosition = formattedPos,
                                activeSubtitleLine = null,
                                activeTaskTitle = relatedTasks.firstOrNull()?.getDisplayTitle(),
                                fullSubtitlesText = fullTranscript.ifBlank { null },
                                isFullSubtitlesContext = true,
                                audioFilePath = track.filePath,
                                trackId = track.id,
                                totalDurationMs = dur
                            )
                        )
                    }) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = Loc.getText("gemini_ai_assistant"),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = { viewModel.openQuizForTrack(track) },
                        modifier = Modifier.testTag("player_ai_quiz_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Quiz,
                            contentDescription = Loc.getText("ai_quiz_action"),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = {
                        val currentCue = activeSubtitleCueState
                        val currentCueText = currentCue?.text ?: ""
                        val effectivePhysPos = if (track.isVirtualScene) (track.startOffsetMs + playPositionState) else playPositionState
                        val startMs = currentCue?.startMs ?: effectivePhysPos
                        val maxEndLimit = if (track.isVirtualScene) {
                            track.endOffsetMs ?: (track.startOffsetMs + track.duration)
                        } else {
                            durationState
                        }
                        val endMs = currentCue?.let { if (it.endMs > it.startMs) it.endMs else (it.startMs + 5000L) } ?: (startMs + 5000L).coerceAtMost(maxEndLimit)
                        onQuickAddNotePrompt(track.id, startMs, endMs, currentCueText)
                    }) {
                        Icon(
                            imageVector = Icons.Filled.EditNote,
                            contentDescription = Loc.getText("add_note"),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = {
                        onQuickAddTaskPrompt(track.id)
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Bookmark,
                            contentDescription = "Bookmark",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}
