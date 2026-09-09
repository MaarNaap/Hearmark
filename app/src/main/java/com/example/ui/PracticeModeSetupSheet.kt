package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AudioTrack
import com.example.player.AudioPlayerManager
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeModeSetupSheet(
    track: AudioTrack,
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
    onOpenWaveformEditor: () -> Unit
) {
    val context = LocalContext.current
    val isPracticeActive by AudioPlayerManager.isPracticeMode.collectAsStateWithLifecycle()
    val currentPlayingTrack by AudioPlayerManager.currentTrack.collectAsStateWithLifecycle()
    val activeTrack = currentPlayingTrack ?: track

    val manualCuts = remember(activeTrack.practiceSegments) {
        if (activeTrack.practiceSegments?.startsWith("MAN:") == true) {
            activeTrack.getPracticeSegmentsList()
        } else {
            emptyList()
        }
    }
    val hasSubtitles = remember(activeTrack.id, activeTrack.subtitleContent, activeTrack.subtitlePath) {
        AudioPlayerManager.hasAvailableSubtitles(activeTrack)
    }
    val subtitleCues = remember(activeTrack.id, hasSubtitles) {
        if (hasSubtitles) AudioPlayerManager.getOrParseCuesForTrack(activeTrack) else emptyList()
    }

    val initialSource = remember(activeTrack.id) {
        val stored = activeTrack.getPracticeSegmentsSource()
        when {
            stored != null -> stored
            manualCuts.isNotEmpty() -> "MANUAL"
            hasSubtitles -> "SUBTITLES"
            else -> viewModel.segmentSourceSetting
        }
    }

    var selectedSource by remember(activeTrack.id) { mutableStateOf(initialSource) }
    var pauseMultiplier by remember { mutableStateOf(viewModel.practicePauseMultiplierSetting) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.RecordVoiceOver,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = Loc.getText("practice_setup_title"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = activeTrack.fileName,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("btn_close_practice_setup")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = Loc.getText("practice_setup_desc"),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // SECTION 1: SOURCE SELECTION CARDS
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Source 1: Manual Cuts
                PracticeSourceOptionCard(
                    title = Loc.getText("source_manual_title"),
                    description = Loc.getText("source_manual_desc"),
                    icon = Icons.Filled.GraphicEq,
                    isSelected = selectedSource == "MANUAL",
                    badgeText = if (manualCuts.isNotEmpty()) {
                        String.format(Locale.US, Loc.getText("badge_saved_cuts"), manualCuts.size)
                    } else {
                        Loc.getText("badge_no_cuts")
                    },
                    badgeHighlight = manualCuts.isNotEmpty(),
                    extraActionLabel = Loc.getText("open_waveform_editor"),
                    onExtraAction = {
                        onDismiss()
                        onOpenWaveformEditor()
                    },
                    onClick = { selectedSource = "MANUAL" },
                    testTag = "option_source_manual"
                )

                // Source 2: Subtitles
                PracticeSourceOptionCard(
                    title = Loc.getText("source_subtitles_title"),
                    description = Loc.getText("source_subtitles_desc"),
                    icon = Icons.Filled.Subtitles,
                    isSelected = selectedSource == "SUBTITLES",
                    badgeText = if (hasSubtitles) {
                        String.format(Locale.US, Loc.getText("badge_available_lines"), subtitleCues.size)
                    } else {
                        Loc.getText("badge_no_subtitles")
                    },
                    badgeHighlight = hasSubtitles,
                    extraActionLabel = null,
                    onExtraAction = null,
                    onClick = { selectedSource = "SUBTITLES" },
                    testTag = "option_source_subtitles"
                )

                // Source 3: Silence Detection
                PracticeSourceOptionCard(
                    title = Loc.getText("source_silence_title"),
                    description = Loc.getText("source_silence_desc"),
                    icon = Icons.Filled.RecordVoiceOver,
                    isSelected = selectedSource == "SILENCE",
                    badgeText = Loc.getText("badge_auto_detect"),
                    badgeHighlight = true,
                    extraActionLabel = null,
                    onExtraAction = null,
                    onClick = { selectedSource = "SILENCE" },
                    testTag = "option_source_silence"
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // SECTION 2: PAUSE MULTIPLIER (Plus / Minus and Chips as in Settings)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = Loc.getText("pause_multiplier_title"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = Loc.getText("pause_multiplier_desc"),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Stepper: Minus, Center Value Chip, Plus
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            val next = (Math.round((pauseMultiplier - 0.25f) * 4f) / 4f).coerceIn(0.25f, 4.0f)
                            pauseMultiplier = next
                        },
                        enabled = pauseMultiplier > 0.25f,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("btn_setup_pause_decrease")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Remove,
                            contentDescription = "Decrease pause duration",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .clickable { pauseMultiplier = 1.0f }
                            .testTag("chip_setup_pause_value")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.2f", pauseMultiplier) + "x",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    FilledTonalIconButton(
                        onClick = {
                            val next = (Math.round((pauseMultiplier + 0.25f) * 4f) / 4f).coerceIn(0.25f, 4.0f)
                            pauseMultiplier = next
                        },
                        enabled = pauseMultiplier < 4.0f,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("btn_setup_pause_increase")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Increase pause duration",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // SECTION 3: ACTION BUTTONS
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // If Manual is selected but no cuts exist yet, guide user directly to waveform editor
                if (selectedSource == "MANUAL" && manualCuts.isEmpty()) {
                    Button(
                        onClick = {
                            onDismiss()
                            onOpenWaveformEditor()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_setup_open_editor_primary"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Loc.getText("open_waveform_editor"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            viewModel.startPracticeWithSource(
                                track = activeTrack,
                                source = selectedSource,
                                multiplier = pauseMultiplier,
                                context = context
                            )
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_setup_apply_start"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = if (isPracticeActive) Icons.Filled.Check else Icons.Filled.RecordVoiceOver,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isPracticeActive) Loc.getText("apply_practice_settings") else Loc.getText("start_practice"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }

                if (isPracticeActive) {
                    OutlinedButton(
                        onClick = {
                            AudioPlayerManager.togglePracticeMode(context, viewModel.repository)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("btn_setup_turn_off"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Loc.getText("turn_off_practice"),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PracticeSourceOptionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    badgeText: String,
    badgeHighlight: Boolean,
    extraActionLabel: String?,
    onExtraAction: (() -> Unit)?,
    onClick: () -> Unit,
    testTag: String
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }

    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(if (isSelected) 1.8.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = onClick,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = title,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Live status badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (badgeHighlight) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (badgeHighlight) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 42.dp)
            )

            if (extraActionLabel != null && onExtraAction != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 42.dp, top = 2.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onExtraAction,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = extraActionLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
