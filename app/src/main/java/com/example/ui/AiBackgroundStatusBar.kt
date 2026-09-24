package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Non-blocking, persistent Material 3 status banner for AI background operations.
 * Allows the user to freely browse the app, manage tasks/notes, and play other audio files
 * while Gemini AI subtitle transcription or scene segmentation runs asynchronously.
 */
@Composable
fun AiBackgroundStatusBar(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isGeneratingSubtitles by viewModel.isGeneratingSubtitles.collectAsStateWithLifecycle()
    val subtitleStatus by viewModel.subtitleGenerationStatus.collectAsStateWithLifecycle()
    val subtitleTrack by viewModel.subtitleGeneratingTrack.collectAsStateWithLifecycle()

    val isDetectingScenes by viewModel.isDetectingScenes.collectAsStateWithLifecycle()
    val sceneStatus by viewModel.sceneDetectionStatus.collectAsStateWithLifecycle()
    val sceneTrackName by viewModel.detectingTrackName.collectAsStateWithLifecycle()

    val isAnyAiActive = isGeneratingSubtitles || isDetectingScenes

    AnimatedVisibility(
        visible = isAnyAiActive,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        val title = when {
            isGeneratingSubtitles -> Loc.getText("ai_hub_function_subtitles")
            else -> Loc.getText("ai_hub_action_scenes")
        }

        val trackLabel = when {
            isGeneratingSubtitles -> subtitleTrack?.getDisplayTitle() ?: Loc.getText("tracks")
            else -> sceneTrackName ?: Loc.getText("tracks")
        }

        val statusDescription = when {
            isGeneratingSubtitles -> subtitleStatus.ifBlank { Loc.getText("ai_creating_subtitles") }
            else -> sceneStatus?.ifBlank { Loc.getText("scenes_analyzing_status") } ?: Loc.getText("scenes_analyzing_status")
        }

        Surface(
            modifier = modifier
                .fillMaxWidth()
                .clickable {
                    Toast.makeText(
                        context,
                        Loc.getText("ai_task_running_bg_hint"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .testTag("ai_background_status_bar"),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 4.dp,
            shadowElevation = 2.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Small progress indicator + icon badge
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Icon(
                            imageVector = if (isGeneratingSubtitles) Icons.Filled.ClosedCaption else Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Middle: Operation title & dynamic progress message
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = Loc.getText("ai_background_badge"),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }

                        Text(
                            text = trackLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.basicMarquee()
                        )

                        Text(
                            text = statusDescription,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Right: Cancel button
                    IconButton(
                        onClick = {
                            if (isGeneratingSubtitles) {
                                viewModel.cancelSubtitleGeneration()
                            } else {
                                viewModel.cancelSceneDetection()
                            }
                            Toast.makeText(context, Loc.getText("ai_cancel_task"), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("ai_background_cancel_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = Loc.getText("ai_cancel_task"),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Sleek bottom indeterminate progress track
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                )
            }
        }
    }
}
