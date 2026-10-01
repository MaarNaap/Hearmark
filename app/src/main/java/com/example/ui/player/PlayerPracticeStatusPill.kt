package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AudioTrack
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PlayerPracticeModeButton(
    track: AudioTrack,
    viewModel: AppViewModel,
    context: Context,
    isPracticeMode: Boolean,
    isPracticeAnalyzing: Boolean,
    onOpenPracticeSetup: () -> Unit
) {
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
}
