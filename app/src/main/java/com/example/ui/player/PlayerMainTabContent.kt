package com.example.ui

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.player.*

@Composable
fun PlayerMainTabContent(
    track: AudioTrack,
    liveTrack: AudioTrack,
    isDistractionFree: Boolean,
    isTrackVideo: Boolean,
    isPlayingState: Boolean,
    isFocusModeState: Boolean,
    fullWidthVideoHeight: Dp,
    videoAspectRatio: Float,
    videoDims: Pair<Int, Int>?,
    videoSubtitleModeState: AudioPlayerManager.VideoSubtitleMode,
    activeSubtitleCueState: SubtitleCue?,
    subtitleFontSizeState: Float,
    areVideoControlsVisible: Boolean,
    activeNotesForTime: List<Note>,
    folderOfTrack: Folder?,
    playlistsOfTrack: List<Playlist>,
    relatedTasks: List<Task>,
    dismiss: () -> Unit,
    onNavigateToFolder: (Long) -> Unit,
    onNavigateToPlaylist: (Long) -> Unit,
    onNavigateToTask: (Task) -> Unit,
    onOpenNotes: (List<Note>) -> Unit,
    onToggleVideoControls: () -> Unit,
    onUpdateVideoInteractionTime: () -> Unit,
    onEnterFullScreen: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (isDistractionFree) Arrangement.Center else Arrangement.spacedBy(16.dp)
    ) {
        AnimatedVisibility(
            visible = !isDistractionFree,
            enter = fadeIn(tween(250)) + expandVertically(tween(250)),
            exit = fadeOut(tween(250)) + shrinkVertically(tween(250))
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Title of active track
                SmartFileNameText(
                    text = track.getDisplayTitle(),
                    isActive = true,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.padding(horizontal = 24.dp),
                    maxLength = 32
                )

                // Play count & Registered Note Indicator (Steady layout without shifts)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left balancing space to ensure play count is always rock-steady centered
                    Spacer(modifier = Modifier.weight(1f))

                    // Center Play Count Indicator - perfectly stationary
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Headphones,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${liveTrack.playCount}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Right space for Note Indicator - appears next to play count without shifting anything
                    val isNoteActive = activeNotesForTime.isNotEmpty()
                    val noteAlpha by animateFloatAsState(
                        targetValue = if (isNoteActive) 1f else 0f,
                        animationSpec = tween(220),
                        label = "noteAlpha"
                    )
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (isNoteActive || noteAlpha > 0.01f) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(start = 6.dp)
                                    .graphicsLayer { alpha = noteAlpha }
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f * noteAlpha))
                                    .clickable(enabled = isNoteActive) { onOpenNotes(activeNotesForTime) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.EditNote,
                                    contentDescription = Loc.getText("view_note"),
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (activeNotesForTime.size > 1) "${Loc.getText("notes")} (${activeNotesForTime.size})" else Loc.getText("notes"),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Unified Metadata & Origin Layout
                if (folderOfTrack != null || playlistsOfTrack.isNotEmpty() || relatedTasks.isNotEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    ) {
                        if (folderOfTrack != null || playlistsOfTrack.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (folderOfTrack != null) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                dismiss()
                                                onNavigateToFolder(folderOfTrack.id)
                                            }
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Folder,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            text = folderOfTrack.folderName,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                if (folderOfTrack != null && playlistsOfTrack.isNotEmpty()) {
                                    Text(
                                        " • ",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp)
                                    )
                                }
                                playlistsOfTrack.forEachIndexed { index, playlist ->
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                dismiss()
                                                onNavigateToPlaylist(playlist.id)
                                            }
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.PlaylistPlay,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            text = playlist.name,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    if (index < playlistsOfTrack.lastIndex) {
                                        Text(
                                            " • ",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp)
                                        )
                                    }
                                }
                            }
                        }

                        if (relatedTasks.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Bookmark,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                relatedTasks.forEachIndexed { index, task ->
                                    Text(
                                        text = task.getDisplayTitle(),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable { onNavigateToTask(task) }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                    if (index < relatedTasks.lastIndex) {
                                        Text(", ", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Video Player Screen or Audio Artwork Representation
        if (isTrackVideo) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fullWidthVideoHeight),
                shape = RoundedCornerShape(0.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    onUpdateVideoInteractionTime()
                                    val isNowFocus = AudioPlayerManager.toggleVideoFocusMode()
                                    val msg = if (isNowFocus) Loc.getText("video_focus_mode_on") else Loc.getText("video_focus_mode_off")
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                },
                                onTap = {
                                    onToggleVideoControls()
                                    onUpdateVideoInteractionTime()
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .aspectRatio(videoAspectRatio, matchHeightConstraintsFirst = false),
                        contentAlignment = Alignment.Center
                    ) {
                        VideoPlayerSurface(modifier = Modifier.fillMaxSize())
                    }

                    // Subtitle / Black Mask Overlay directly on video
                    when (videoSubtitleModeState) {
                        AudioPlayerManager.VideoSubtitleMode.SHOW -> {
                            if (activeSubtitleCueState != null && activeSubtitleCueState.text.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 8.dp, start = 12.dp, end = 12.dp)
                                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = activeSubtitleCueState.text,
                                        color = Color.White,
                                        fontSize = (subtitleFontSizeState * 0.9f).sp,
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
                                    .padding(bottom = 8.dp, start = 12.dp, end = 12.dp)
                                    .fillMaxWidth(0.9f)
                                    .height(36.dp)
                                    .background(Color.Black, RoundedCornerShape(6.dp))
                            )
                        }
                    }

                    // Registered Note Floating Indicator on Video Card
                    if (activeNotesForTime.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .clickable { onOpenNotes(activeNotesForTime) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.EditNote,
                                    contentDescription = Loc.getText("view_note"),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (activeNotesForTime.size > 1) "${Loc.getText("notes")} (${activeNotesForTime.size})" else Loc.getText("notes"),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Quick Floating Actions (Subtitle Toggle, PiP & Fullscreen)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = areVideoControlsVisible,
                        enter = fadeIn(animationSpec = tween(250)),
                        exit = fadeOut(animationSpec = tween(250)),
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Row(
                            modifier = Modifier.padding(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    onUpdateVideoInteractionTime()
                                    val next = AudioPlayerManager.toggleVideoSubtitleMode()
                                    val msg = when (next) {
                                        AudioPlayerManager.VideoSubtitleMode.SHOW -> Loc.getText("video_subtitles_show_toast")
                                        AudioPlayerManager.VideoSubtitleMode.HIDE -> Loc.getText("video_subtitles_hide_toast")
                                        AudioPlayerManager.VideoSubtitleMode.BLACK -> Loc.getText("video_subtitles_black_toast")
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                when (videoSubtitleModeState) {
                                    AudioPlayerManager.VideoSubtitleMode.SHOW -> {
                                        Icon(
                                            imageVector = Icons.Filled.Subtitles,
                                            contentDescription = Loc.getText("video_subtitles_show_toast"),
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    AudioPlayerManager.VideoSubtitleMode.HIDE -> {
                                        Icon(
                                            imageVector = Icons.Filled.SubtitlesOff,
                                            contentDescription = Loc.getText("video_subtitles_hide_toast"),
                                            tint = Color.White.copy(alpha = 0.5f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    AudioPlayerManager.VideoSubtitleMode.BLACK -> {
                                        Icon(
                                            imageVector = Icons.Filled.Crop169,
                                            contentDescription = Loc.getText("video_subtitles_black_toast"),
                                            tint = Color(0xFFFFB74D),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                IconButton(
                                    onClick = {
                                        onUpdateVideoInteractionTime()
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
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PictureInPictureAlt,
                                        contentDescription = "PiP",
                                        tint = Color.White,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    onUpdateVideoInteractionTime()
                                    val isNowFocus = AudioPlayerManager.toggleVideoFocusMode()
                                    val msg = if (isNowFocus) Loc.getText("video_focus_mode_on") else Loc.getText("video_focus_mode_off")
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(36.dp).testTag("video_focus_mode_button")
                            ) {
                                Icon(
                                    imageVector = if (isFocusModeState) Icons.Filled.CenterFocusStrong else Icons.Filled.FilterCenterFocus,
                                    contentDescription = Loc.getText("video_focus_mode_title"),
                                    tint = if (isFocusModeState) MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    onUpdateVideoInteractionTime()
                                    onEnterFullScreen()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Fullscreen,
                                    contentDescription = "Fullscreen",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Artwork & Radar Waves Representation
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                val primaryRadarColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
                        .drawBehind {
                            drawCircle(
                                color = primaryRadarColor,
                                radius = size.minDimension / 1.4f,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getTrackFileIcon(track),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )

                    Row(
                        modifier = Modifier
                            .height(32.dp)
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val barCount = 13
                        val infiniteTransition = rememberInfiniteTransition(label = "waveform")
                        val heights = (0 until barCount).map { index ->
                            if (isPlayingState) {
                                val duration = 350 + (index % 4) * 120
                                val target = 0.35f + (index % 3) * 0.25f
                                infiniteTransition.animateFloat(
                                    initialValue = 0.15f,
                                    targetValue = target.coerceIn(0.22f, 1f),
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(durationMillis = duration, easing = FastOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "bar_$index"
                                )
                            } else {
                                remember { mutableStateOf(0.18f) }
                            }
                        }
                        for (i in 0 until barCount) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .fillMaxHeight(heights[i].value)
                                    .clip(RoundedCornerShape(1.5.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        }
    }
}
