package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.player.*
import com.example.ui.theme.*

@Composable
fun PlayerSegmentedProgressBar(
    track: AudioTrack,
    liveTrack: AudioTrack,
    durationState: Long,
    playPositionState: Long,
    maxListenedPercent: Int,
    arePlaybackButtonsActive: Boolean,
    trackNotes: List<Note>,
    subtitlesCuesState: List<SubtitleCue>
) {
    var isDraggingState by remember { mutableStateOf(false) }
    var dragPercentState by remember { mutableStateOf(0f) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = Loc.getText("max_listened_progress"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Text(
                text = "$maxListenedPercent%",
                color = ColorSuccess,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp
            )
        }
        Spacer(modifier = Modifier.height(6.dp))

        val currentNeedlePercent = if (isDraggingState) dragPercentState else (if (durationState > 0) playPositionState.toFloat() / durationState.toFloat() else 0f)

        val numSegments = liveTrack.getAdaptiveNumSegments()
        val listenedRanges = remember(liveTrack.listenedSegments, numSegments) {
            val bitSet = liveTrack.getListenedBitSet(numSegments)
            val ranges = mutableListOf<IntRange>()
            var start = -1
            var prev = -1
            var i = bitSet.nextSetBit(0)
            while (i in 0 until numSegments) {
                if (start == -1) {
                    start = i
                    prev = i
                } else if (i == prev + 1) {
                    prev = i
                } else {
                    ranges.add(start..prev)
                    start = i
                    prev = i
                }
                i = bitSet.nextSetBit(i + 1)
            }
            if (start != -1) {
                ranges.add(start..prev)
            }
            ranges
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ) {
                val segmentsColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                val noteMarkerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (numSegments > 0) {
                        val segmentWidth = size.width / numSegments
                        for (range in listenedRanges) {
                            val startX = range.first * segmentWidth
                            val rangeWidth = (range.last - range.first + 1) * segmentWidth
                            drawRect(
                                color = segmentsColor,
                                topLeft = androidx.compose.ui.geometry.Offset(x = startX, y = 0f),
                                size = androidx.compose.ui.geometry.Size(width = rangeWidth, height = size.height)
                            )
                        }
                    }
                    if (durationState > 0 && trackNotes.isNotEmpty()) {
                        val markerWidth = 2.dp.toPx()
                        for (note in trackNotes) {
                            val noteTargetMs = note.originStartMs
                                ?: SubtitleParser.findDedicatedCueForNote(note, subtitlesCuesState)?.startMs
                                ?: note.startTimestampMs
                            val relTargetMs = if (track.isVirtualScene) {
                                if (noteTargetMs >= track.startOffsetMs) noteTargetMs - track.startOffsetMs else noteTargetMs
                            } else {
                                noteTargetMs
                            }
                            if (relTargetMs in 0L..durationState) {
                                val frac = (relTargetMs.toFloat() / durationState.toFloat()).coerceIn(0f, 1f)
                                val noteX = frac * size.width
                                drawRect(
                                    color = noteMarkerColor,
                                    topLeft = androidx.compose.ui.geometry.Offset(x = noteX - (markerWidth / 2f), y = 0f),
                                    size = androidx.compose.ui.geometry.Size(width = markerWidth, height = size.height)
                                )
                            }
                        }
                    }
                }
            }

            val thumbSize = 10.dp
            val safeNeedlePercent = currentNeedlePercent.coerceIn(0f, 1f)
            val thumbOffset = ((maxWidth * safeNeedlePercent) - 5.dp).coerceIn(0.dp, maxWidth - 10.dp)
            Box(
                modifier = Modifier
                    .size(thumbSize)
                    .offset(x = thumbOffset)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )

            Slider(
                value = safeNeedlePercent,
                enabled = arePlaybackButtonsActive,
                onValueChange = { percent ->
                    isDraggingState = true
                    dragPercentState = percent
                },
                onValueChangeFinished = {
                    val target = (dragPercentState * durationState).toLong()
                    AudioPlayerManager.seekTo(target, isPhysicalTimestamp = false)
                    isDraggingState = false
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.Transparent,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                    disabledThumbColor = Color.Transparent,
                    disabledActiveTrackColor = Color.Transparent,
                    disabledInactiveTrackColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        val displayPlayPosition = if (isDraggingState) (dragPercentState * durationState).toLong() else playPositionState
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(displayPlayPosition), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text(formatDuration(durationState), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}
