package com.example.ui

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.player.*

@Composable
fun FullScreenVideoOverlay(
    track: AudioTrack,
    videoAspectRatio: Float,
    isHorizontalVideo: Boolean,
    videoDims: Pair<Int, Int>?,
    videoSubtitleModeState: AudioPlayerManager.VideoSubtitleMode,
    activeSubtitleCueState: SubtitleCue?,
    subtitleFontSizeState: Float,
    areFullScreenControlsVisible: Boolean,
    isInPipModeState: Boolean,
    activeNotesForTime: List<Note>,
    trackNotes: List<Note>,
    subtitlesCuesState: List<SubtitleCue>,
    effectivePhysPos: Long,
    durationState: Long,
    playPositionState: Long,
    isPlayingState: Boolean,
    onToggleControlsVisibility: () -> Unit,
    onExitFullScreen: () -> Unit,
    onOpenNotes: (List<Note>) -> Unit,
    onQuickAddNotePrompt: (Long, Long, Long, String) -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onToggleControlsVisibility()
            }
    ) {
        // Video Viewport respecting original video dimensions & aspect ratio
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .aspectRatio(videoAspectRatio, matchHeightConstraintsFirst = isHorizontalVideo),
                contentAlignment = Alignment.Center
            ) {
                VideoPlayerSurface(modifier = Modifier.fillMaxSize())
            }
        }

        // Subtitle / Black Mask Overlay
        when (videoSubtitleModeState) {
            AudioPlayerManager.VideoSubtitleMode.SHOW -> {
                if (activeSubtitleCueState != null && activeSubtitleCueState.text.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = if (areFullScreenControlsVisible && !isInPipModeState) 100.dp else 16.dp, start = 16.dp, end = 16.dp)
                            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = activeSubtitleCueState.text,
                            color = Color.White,
                            fontSize = (if (isInPipModeState) subtitleFontSizeState * 0.75f else subtitleFontSizeState * 1.25f).sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            AudioPlayerManager.VideoSubtitleMode.HIDE -> {
                // Hidden
            }
            AudioPlayerManager.VideoSubtitleMode.BLACK -> {
                // Solid black box without text to hide built-in video subtitles
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (areFullScreenControlsVisible && !isInPipModeState) 100.dp else 20.dp, start = 16.dp, end = 16.dp)
                        .fillMaxWidth(0.92f)
                        .height(if (isInPipModeState) 32.dp else 52.dp)
                        .background(Color.Black, RoundedCornerShape(6.dp))
                )
            }
        }

        // Floating Registered Note Badge directly on Fullscreen Video
        if (activeNotesForTime.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(top = if (areFullScreenControlsVisible) 64.dp else 16.dp, start = 16.dp)
                    .clickable { onOpenNotes(activeNotesForTime) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.EditNote,
                        contentDescription = Loc.getText("view_note"),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (activeNotesForTime.size > 1) "${Loc.getText("notes")} (${activeNotesForTime.size})" else Loc.getText("notes"),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (!isInPipModeState) {
            // Top Bar Overlay
            AnimatedVisibility(
                visible = areFullScreenControlsVisible,
                enter = fadeIn(animationSpec = tween(250)),
                exit = fadeOut(animationSpec = tween(250)),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onExitFullScreen) {
                        Icon(Icons.Filled.FullscreenExit, contentDescription = "Exit Fullscreen", tint = Color.White)
                    }
                    Text(
                        text = track.getDisplayTitle(),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (activeNotesForTime.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { onOpenNotes(activeNotesForTime) }
                                    .padding(horizontal = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.EditNote,
                                        contentDescription = Loc.getText("view_note"),
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (activeNotesForTime.size > 1) "${Loc.getText("notes")} (${activeNotesForTime.size})" else Loc.getText("notes"),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        IconButton(onClick = {
                            val currentCue = activeSubtitleCueState
                            val currentCueText = currentCue?.text ?: ""
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
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = {
                            val next = AudioPlayerManager.toggleVideoSubtitleMode()
                            val msg = when (next) {
                                AudioPlayerManager.VideoSubtitleMode.SHOW -> Loc.getText("video_subtitles_show_toast")
                                AudioPlayerManager.VideoSubtitleMode.HIDE -> Loc.getText("video_subtitles_hide_toast")
                                AudioPlayerManager.VideoSubtitleMode.BLACK -> Loc.getText("video_subtitles_black_toast")
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }) {
                            when (videoSubtitleModeState) {
                                AudioPlayerManager.VideoSubtitleMode.SHOW -> {
                                    Icon(
                                        imageVector = Icons.Filled.Subtitles,
                                        contentDescription = Loc.getText("video_subtitles_show_toast"),
                                        tint = Color.White
                                    )
                                }
                                AudioPlayerManager.VideoSubtitleMode.HIDE -> {
                                    Icon(
                                        imageVector = Icons.Filled.SubtitlesOff,
                                        contentDescription = Loc.getText("video_subtitles_hide_toast"),
                                        tint = Color.White.copy(alpha = 0.45f)
                                    )
                                }
                                AudioPlayerManager.VideoSubtitleMode.BLACK -> {
                                    Icon(
                                        imageVector = Icons.Filled.Crop169,
                                        contentDescription = Loc.getText("video_subtitles_black_toast"),
                                        tint = Color(0xFFFFB74D)
                                    )
                                }
                            }
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            IconButton(onClick = {
                                val activity = context as? Activity
                                if (activity != null) {
                                    try {
                                        AudioPlayerManager.setInPipMode(true)
                                        val ratW = if (videoDims != null && videoDims.first > 0) videoDims.first.coerceIn(1, 10000) else 16
                                        val ratH = if (videoDims != null && videoDims.second > 0) videoDims.second.coerceIn(1, 10000) else 9
                                        val params = PictureInPictureParams.Builder()
                                            .setAspectRatio(Rational(ratW, ratH))
                                            .build()
                                        activity.enterPictureInPictureMode(params)
                                    } catch (e: Exception) {
                                        activity.enterPictureInPictureMode()
                                    }
                                }
                            }) {
                                Icon(Icons.Filled.PictureInPictureAlt, contentDescription = "PiP", tint = Color.White)
                            }
                        }
                    }
                }
            }

            // Bottom Controls Overlay
            AnimatedVisibility(
                visible = areFullScreenControlsVisible,
                enter = fadeIn(animationSpec = tween(250)),
                exit = fadeOut(animationSpec = tween(250)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))))
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (durationState > 0 && trackNotes.isNotEmpty()) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .align(Alignment.Center)
                                    .padding(horizontal = 8.dp)
                            ) {
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
                                            color = Color.White.copy(alpha = 0.85f),
                                            topLeft = androidx.compose.ui.geometry.Offset(x = noteX - (markerWidth / 2f), y = 0f),
                                            size = androidx.compose.ui.geometry.Size(width = markerWidth, height = size.height)
                                        )
                                    }
                                }
                            }
                        }
                        Slider(
                            value = if (durationState > 0) (playPositionState.toFloat() / durationState).coerceIn(0f, 1f) else 0f,
                            onValueChange = { frac ->
                                val targetMs = (frac * durationState).toLong()
                                AudioPlayerManager.seekTo(targetMs, isPhysicalTimestamp = false)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(formatDuration(playPositionState), color = Color.White, fontSize = 12.sp)

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { AudioPlayerManager.skipBackward() }) {
                                Icon(Icons.Filled.Replay10, contentDescription = "Rewind", tint = Color.White)
                            }
                            IconButton(onClick = {
                                if (isPlayingState) AudioPlayerManager.pause() else AudioPlayerManager.resume()
                            }) {
                                Icon(
                                    imageVector = if (isPlayingState) Icons.Filled.PauseCircleFilled else Icons.Filled.PlayCircleFilled,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(46.dp)
                                )
                            }
                            IconButton(onClick = { AudioPlayerManager.skipForward() }) {
                                Icon(Icons.Filled.Forward10, contentDescription = "Forward", tint = Color.White)
                            }
                        }

                        Text(formatDuration(durationState), color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
