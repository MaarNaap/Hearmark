package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.AudioContextSummary
import com.example.data.AudioTrack
import com.example.data.Note
import com.example.data.Task
import com.example.player.AudioPlayerManager

enum class AiFunctionType {
    CHAT,
    SUBTITLES,
    QUIZ
}

enum class AiContextType {
    TRACK,
    NOTEBOOK,
    TASK
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedAiHubSheet(
    viewModel: AppViewModel,
    onDismiss: () -> Unit,
    initialFunction: AiFunctionType = AiFunctionType.CHAT,
    initialTrack: AudioTrack? = null,
    initialNotes: List<Note>? = null,
    initialTask: Task? = null
) {
    val context = LocalContext.current
    val currentTrackState by AudioPlayerManager.currentTrack.collectAsStateWithLifecycle()
    val allTracks by viewModel.tracks.collectAsStateWithLifecycle()
    val allNotes by viewModel.notes.collectAsStateWithLifecycle()
    val allTasks by viewModel.allTasks.collectAsStateWithLifecycle()

    // 1. Selected Function State
    var selectedFunction by remember { mutableStateOf(initialFunction) }

    // 2. Selected Context Type State
    var selectedContextType by remember {
        mutableStateOf(
            when {
                initialNotes != null -> AiContextType.NOTEBOOK
                initialTask != null -> AiContextType.TASK
                else -> AiContextType.TRACK
            }
        )
    }

    // Selected Targets for Each Context Type
    var selectedTrack by remember(initialTrack, currentTrackState, allTracks) {
        mutableStateOf(initialTrack ?: currentTrackState ?: allTracks.firstOrNull())
    }

    var selectedNotesList by remember(initialNotes, allNotes) {
        mutableStateOf(initialNotes ?: emptyList())
    }

    var selectedTask by remember(initialTask, allTasks) {
        mutableStateOf(initialTask ?: allTasks.firstOrNull())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                ) {}
            }
        },
        modifier = Modifier.testTag("unified_ai_hub_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Title & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = Loc.getText("unified_ai_hub_title"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = Loc.getText("unified_ai_hub_subtitle"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = Loc.getText("close"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                thickness = 0.5.dp
            )

            // STEP 1: SELECT FUNCTION
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = Loc.getText("ai_hub_step1_action"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // 3 Standard Functions: CHAT, SUBTITLES, QUIZ
                FunctionOptionCard(
                    title = Loc.getText("ai_hub_action_chat"),
                    description = Loc.getText("ai_hub_action_chat_desc"),
                    icon = Icons.Filled.SmartToy,
                    isSelected = selectedFunction == AiFunctionType.CHAT,
                    onClick = { selectedFunction = AiFunctionType.CHAT }
                )

                FunctionOptionCard(
                    title = Loc.getText("ai_hub_action_subtitles"),
                    description = Loc.getText("ai_hub_action_subtitles_desc"),
                    icon = Icons.Filled.Subtitles,
                    isSelected = selectedFunction == AiFunctionType.SUBTITLES,
                    onClick = {
                        selectedFunction = AiFunctionType.SUBTITLES
                        selectedContextType = AiContextType.TRACK
                    }
                )

                FunctionOptionCard(
                    title = Loc.getText("ai_hub_action_quiz"),
                    description = Loc.getText("ai_hub_action_quiz_desc"),
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    isSelected = selectedFunction == AiFunctionType.QUIZ,
                    onClick = { selectedFunction = AiFunctionType.QUIZ }
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // STEP 2: SELECT CONTEXT
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = Loc.getText("ai_hub_step2_context"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Context Filter Selector Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedContextType == AiContextType.TRACK,
                        onClick = { selectedContextType = AiContextType.TRACK },
                        label = { Text(Loc.getText("ai_hub_context_type_track")) },
                        leadingIcon = {
                            Icon(Icons.Filled.Audiotrack, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.weight(1f)
                    )

                    // Notes context enabled for Chat and Quiz
                    FilterChip(
                        selected = selectedContextType == AiContextType.NOTEBOOK,
                        onClick = {
                            if (selectedFunction == AiFunctionType.SUBTITLES) {
                                selectedFunction = AiFunctionType.QUIZ
                            }
                            selectedContextType = AiContextType.NOTEBOOK
                        },
                        label = { Text(Loc.getText("ai_hub_context_type_note")) },
                        leadingIcon = {
                            Icon(Icons.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.weight(1f)
                    )

                    // Task context enabled for Chat
                    FilterChip(
                        selected = selectedContextType == AiContextType.TASK,
                        onClick = {
                            if (selectedFunction != AiFunctionType.CHAT) {
                                selectedFunction = AiFunctionType.CHAT
                            }
                            selectedContextType = AiContextType.TASK
                        },
                        label = { Text(Loc.getText("ai_hub_context_type_task")) },
                        leadingIcon = {
                            Icon(Icons.Filled.Assignment, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Detailed Target Selector for Chosen Context
                when (selectedContextType) {
                    AiContextType.TRACK -> {
                        TrackContextSelector(
                            currentTrack = currentTrackState,
                            selectedTrack = selectedTrack,
                            allTracks = allTracks,
                            onTrackSelected = { selectedTrack = it }
                        )
                    }
                    AiContextType.NOTEBOOK -> {
                        NoteContextSelector(
                            allNotes = allNotes,
                            selectedNotes = selectedNotesList,
                            onSelectionChanged = { selectedNotesList = it }
                        )
                    }
                    AiContextType.TASK -> {
                        TaskContextSelector(
                            allTasks = allTasks,
                            selectedTask = selectedTask,
                            onTaskSelected = { selectedTask = it }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // STEP 3: ACTION / LAUNCH BUTTON
            Button(
                onClick = {
                    onDismiss()
                    executeAiAction(
                        viewModel = viewModel,
                        context = context,
                        function = selectedFunction,
                        contextType = selectedContextType,
                        track = selectedTrack,
                        notes = selectedNotesList,
                        task = selectedTask
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("ai_hub_launch_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = when (selectedFunction) {
                        AiFunctionType.CHAT -> Icons.Filled.Chat
                        AiFunctionType.SUBTITLES -> Icons.Filled.Subtitles
                        AiFunctionType.QUIZ -> Icons.Filled.PlayArrow
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (selectedFunction) {
                        AiFunctionType.CHAT -> Loc.getText("ai_hub_action_chat")
                        AiFunctionType.SUBTITLES -> Loc.getText("ai_hub_action_subtitles")
                        AiFunctionType.QUIZ -> Loc.getText("ai_hub_action_quiz")
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

private fun executeAiAction(
    viewModel: AppViewModel,
    context: android.content.Context,
    function: AiFunctionType,
    contextType: AiContextType,
    track: AudioTrack?,
    notes: List<Note>,
    task: Task?
) {
    when (function) {
        AiFunctionType.CHAT -> {
            val isSelectedTrackPlaying = track != null && AudioPlayerManager.currentTrack.value?.id == track.id
            val pos = if (isSelectedTrackPlaying) AudioPlayerManager.currentPosition.value else 0L
            val dur = if (isSelectedTrackPlaying) AudioPlayerManager.duration.value else (track?.duration ?: 0L)
            val cue = if (isSelectedTrackPlaying) AudioPlayerManager.activeSubtitleCue.value?.text else null
            val formattedPos = if (dur > 0) "${formatDuration(pos)} / ${formatDuration(dur)}" else formatDuration(pos)

            val notesSummary = if (contextType == AiContextType.NOTEBOOK && notes.isNotEmpty()) {
                notes.joinToString("; ") { "${it.text}${if (it.comment.isNotBlank()) ": " + it.comment else ""}" }
            } else null

            val summary = AudioContextSummary(
                trackTitle = track?.getDisplayTitle(),
                trackArtist = null,
                currentPositionMs = pos,
                formattedPosition = formattedPos,
                activeSubtitleLine = cue ?: notesSummary,
                activeTaskTitle = task?.getDisplayTitle()
            )
            viewModel.openChatWithContext(summary)
        }

        AiFunctionType.SUBTITLES -> {
            if (track == null) {
                Toast.makeText(context, Loc.getText("no_track_selected"), Toast.LENGTH_SHORT).show()
                return
            }
            // If track is not current playing track, select and play it
            if (AudioPlayerManager.currentTrack.value?.id != track.id) {
                viewModel.selectAndPlay(track)
            }
            viewModel.generateSubtitlesForCurrentTrack(
                contextSummary = AudioContextSummary(trackTitle = track.getDisplayTitle()),
                onSuccess = {
                    Toast.makeText(context, Loc.getText("ai_subtitles_success"), Toast.LENGTH_SHORT).show()
                },
                onError = { err ->
                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                }
            )
        }

        AiFunctionType.QUIZ -> {
            when (contextType) {
                AiContextType.TRACK -> {
                    if (track != null) {
                        viewModel.openUnifiedQuiz(
                            tabMode = QuizTabMode.TRACK,
                            initialTrack = track
                        )
                    } else {
                        viewModel.openUnifiedQuiz(tabMode = QuizTabMode.TRACK)
                    }
                }
                AiContextType.NOTEBOOK -> {
                    // Quiz from selected notes attached to parent track!
                    val effectiveNotes = if (notes.isNotEmpty()) notes else emptyList()
                    val parentTrackId = effectiveNotes.firstOrNull()?.trackId
                    val resolvedTrack = if (parentTrackId != null) {
                        viewModel.tracks.value.find { it.id == parentTrackId } ?: track
                    } else track

                    viewModel.openUnifiedQuiz(
                        tabMode = QuizTabMode.NOTEBOOK,
                        initialTrack = resolvedTrack,
                        initialNotes = effectiveNotes.ifEmpty { null }
                    )
                }
                AiContextType.TASK -> {
                    // Fall back to general track quiz or current track
                    viewModel.openUnifiedQuiz(
                        tabMode = QuizTabMode.TRACK,
                        initialTrack = track
                    )
                }
            }
        }
    }
}

@Composable
private fun FunctionOptionCard(
    title: String,
    description: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun TrackContextSelector(
    currentTrack: AudioTrack?,
    selectedTrack: AudioTrack?,
    allTracks: List<AudioTrack>,
    onTrackSelected: (AudioTrack) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (allTracks.isEmpty()) {
                Text(
                    text = Loc.getText("unified_quiz_no_track_selected"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "${Loc.getText("unified_quiz_select_track")}: ${selectedTrack?.getDisplayTitle() ?: Loc.getText("none")}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(allTracks, key = { it.id }) { track ->
                        val isSelected = selectedTrack?.id == track.id
                        val isPlaying = currentTrack?.id == track.id

                        FilterChip(
                            selected = isSelected,
                            onClick = { onTrackSelected(track) },
                            label = {
                                Text(
                                    text = track.getDisplayTitle(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 12.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.PlayArrow else Icons.Filled.Audiotrack,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteContextSelector(
    allNotes: List<Note>,
    selectedNotes: List<Note>,
    onSelectionChanged: (List<Note>) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedNotes.isEmpty()) {
                        String.format(Loc.getText("ai_hub_all_notes_badge"), allNotes.size)
                    } else {
                        String.format(Loc.getText("ai_hub_notes_count_badge"), selectedNotes.size)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (selectedNotes.isNotEmpty()) {
                    TextButton(
                        onClick = { onSelectionChanged(emptyList()) },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(Loc.getText("clear"), fontSize = 11.sp)
                    }
                }
            }

            if (allNotes.isEmpty()) {
                Text(
                    text = Loc.getText("no_notes_found"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(allNotes, key = { it.id }) { note ->
                        val isSelected = selectedNotes.any { it.id == note.id }

                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) {
                                    onSelectionChanged(selectedNotes.filter { it.id != note.id })
                                } else {
                                    onSelectionChanged(selectedNotes + note)
                                }
                            },
                            label = {
                                Text(
                                    text = note.text.take(24),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 12.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isSelected) Icons.Filled.Check else Icons.Filled.Bookmark,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskContextSelector(
    allTasks: List<Task>,
    selectedTask: Task?,
    onTaskSelected: (Task) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (allTasks.isEmpty()) {
                Text(
                    text = Loc.getText("no_tasks_found"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "${Loc.getText("task_label")}: ${selectedTask?.getDisplayTitle() ?: Loc.getText("none")}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(allTasks, key = { it.id }) { task ->
                        val isSelected = selectedTask?.id == task.id

                        FilterChip(
                            selected = isSelected,
                            onClick = { onTaskSelected(task) },
                            label = {
                                Text(
                                    text = task.getDisplayTitle(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 12.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Assignment,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
