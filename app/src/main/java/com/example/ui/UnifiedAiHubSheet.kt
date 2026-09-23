package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.AudioContextSummary
import com.example.data.AudioTrack
import com.example.data.Folder
import com.example.data.Note
import com.example.data.Task
import com.example.player.AudioPlayerManager
import com.example.player.SubtitleParser

enum class AiFunctionType {
    CHAT,
    SUBTITLES,
    SCENES,
    QUIZ
}

enum class AiContextType {
    FREE,
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
    val allFolders by viewModel.folders.collectAsStateWithLifecycle()
    val allNotes by viewModel.notes.collectAsStateWithLifecycle()
    val activeTasks by viewModel.activeTasks.collectAsStateWithLifecycle()

    // 1. Function Selection (First Step)
    var selectedFunction by remember { mutableStateOf(initialFunction) }

    // Helper: allowed contexts based on function
    val allowedContexts = remember(selectedFunction) {
        when (selectedFunction) {
            AiFunctionType.SUBTITLES -> listOf(AiContextType.TRACK)
            AiFunctionType.SCENES -> listOf(AiContextType.TRACK)
            AiFunctionType.QUIZ -> listOf(AiContextType.TRACK, AiContextType.NOTEBOOK)
            AiFunctionType.CHAT -> listOf(AiContextType.FREE, AiContextType.TRACK, AiContextType.NOTEBOOK, AiContextType.TASK)
        }
    }

    // 2. Context Type Selection
    var selectedContextType by remember {
        val initialCtx = when {
            initialNotes != null -> AiContextType.NOTEBOOK
            initialTask != null -> AiContextType.TASK
            initialTrack != null -> AiContextType.TRACK
            initialFunction == AiFunctionType.SUBTITLES || initialFunction == AiFunctionType.SCENES -> AiContextType.TRACK
            else -> null
        }
        mutableStateOf<AiContextType?>(initialCtx)
    }

    // Ensure selectedContextType is always compatible with selectedFunction
    LaunchedEffect(selectedFunction) {
        if (selectedFunction == AiFunctionType.SUBTITLES || selectedFunction == AiFunctionType.SCENES) {
            selectedContextType = AiContextType.TRACK
        } else if (selectedContextType != null && !allowedContexts.contains(selectedContextType)) {
            selectedContextType = if (allowedContexts.size == 1) {
                allowedContexts.first()
            } else {
                null
            }
        }
    }

    // Selected Targets for Each Context Type
    var selectedTrack by remember(initialTrack, currentTrackState, allTracks) {
        mutableStateOf(initialTrack ?: currentTrackState ?: allTracks.firstOrNull())
    }

    var selectedNotesList by remember(initialNotes) {
        mutableStateOf(initialNotes ?: emptyList())
    }

    var selectedTask by remember(initialTask, activeTasks) {
        mutableStateOf(initialTask ?: activeTasks.firstOrNull())
    }

    // Dropdown expansion states
    var functionMenuExpanded by remember { mutableStateOf(false) }
    var contextMenuExpanded by remember { mutableStateOf(false) }

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
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Clean title, icon & Close button
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
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
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
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = Loc.getText("unified_ai_hub_title"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
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

            // TWO DROPDOWN MENUS IN ONE ROW (Function first, Context second filtered by Function)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // DROPDOWN 1: FUNCTION SELECTION
                Box(modifier = Modifier.weight(1f)) {
                    Surface(
                        onClick = { functionMenuExpanded = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("ai_hub_function_dropdown_btn"),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = when (selectedFunction) {
                                        AiFunctionType.CHAT -> Icons.Filled.SmartToy
                                        AiFunctionType.SUBTITLES -> Icons.Filled.Subtitles
                                        AiFunctionType.SCENES -> Icons.Filled.MovieCreation
                                        AiFunctionType.QUIZ -> Icons.AutoMirrored.Filled.HelpOutline
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = when (selectedFunction) {
                                        AiFunctionType.CHAT -> Loc.getText("ai_hub_function_chat")
                                        AiFunctionType.SUBTITLES -> Loc.getText("ai_hub_function_subtitles")
                                        AiFunctionType.SCENES -> Loc.getText("ai_hub_function_scenes")
                                        AiFunctionType.QUIZ -> Loc.getText("ai_hub_function_quiz")
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = if (functionMenuExpanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = functionMenuExpanded,
                        onDismissRequest = { functionMenuExpanded = false },
                        modifier = Modifier.widthIn(min = 180.dp)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = Loc.getText("ai_hub_function_chat"),
                                    fontWeight = if (selectedFunction == AiFunctionType.CHAT) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.SmartToy,
                                    contentDescription = null,
                                    tint = if (selectedFunction == AiFunctionType.CHAT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                selectedFunction = AiFunctionType.CHAT
                                functionMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = Loc.getText("ai_hub_function_subtitles"),
                                    fontWeight = if (selectedFunction == AiFunctionType.SUBTITLES) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Subtitles,
                                    contentDescription = null,
                                    tint = if (selectedFunction == AiFunctionType.SUBTITLES) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                selectedFunction = AiFunctionType.SUBTITLES
                                selectedContextType = AiContextType.TRACK
                                functionMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = Loc.getText("ai_hub_function_scenes"),
                                    fontWeight = if (selectedFunction == AiFunctionType.SCENES) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.MovieCreation,
                                    contentDescription = null,
                                    tint = if (selectedFunction == AiFunctionType.SCENES) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                selectedFunction = AiFunctionType.SCENES
                                selectedContextType = AiContextType.TRACK
                                functionMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = Loc.getText("ai_hub_function_quiz"),
                                    fontWeight = if (selectedFunction == AiFunctionType.QUIZ) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.AutoMirrored.Filled.HelpOutline,
                                    contentDescription = null,
                                    tint = if (selectedFunction == AiFunctionType.QUIZ) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                selectedFunction = AiFunctionType.QUIZ
                                functionMenuExpanded = false
                            }
                        )
                    }
                }

                // DROPDOWN 2: CONTEXT SELECTION (Filtered strictly by selectedFunction)
                Box(modifier = Modifier.weight(1f)) {
                    Surface(
                        onClick = { contextMenuExpanded = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("ai_hub_context_dropdown_btn"),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(
                            1.dp,
                            if (selectedContextType != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = when (selectedContextType) {
                                        AiContextType.FREE -> Icons.Filled.Public
                                        AiContextType.TRACK -> Icons.Filled.Folder
                                        AiContextType.NOTEBOOK -> Icons.Filled.MenuBook
                                        AiContextType.TASK -> Icons.Filled.Assignment
                                        null -> Icons.Filled.Tune
                                    },
                                    contentDescription = null,
                                    tint = if (selectedContextType != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = when (selectedContextType) {
                                        AiContextType.FREE -> Loc.getText("ai_hub_context_free")
                                        AiContextType.TRACK -> Loc.getText("ai_hub_context_files")
                                        AiContextType.NOTEBOOK -> Loc.getText("ai_hub_context_notebook")
                                        AiContextType.TASK -> Loc.getText("ai_hub_context_tasks")
                                        null -> Loc.getText("ai_hub_select_context")
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selectedContextType != null) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selectedContextType != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = if (contextMenuExpanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = contextMenuExpanded,
                        onDismissRequest = { contextMenuExpanded = false },
                        modifier = Modifier.widthIn(min = 180.dp)
                    ) {
                        // Only show options present in allowedContexts
                        if (allowedContexts.contains(AiContextType.FREE)) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = Loc.getText("ai_hub_context_free"),
                                        fontWeight = if (selectedContextType == AiContextType.FREE) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Public,
                                        contentDescription = null,
                                        tint = if (selectedContextType == AiContextType.FREE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = {
                                    selectedContextType = AiContextType.FREE
                                    contextMenuExpanded = false
                                }
                            )
                        }

                        if (allowedContexts.contains(AiContextType.TRACK)) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = Loc.getText("ai_hub_context_files"),
                                        fontWeight = if (selectedContextType == AiContextType.TRACK) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Folder,
                                        contentDescription = null,
                                        tint = if (selectedContextType == AiContextType.TRACK) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = {
                                    selectedContextType = AiContextType.TRACK
                                    contextMenuExpanded = false
                                }
                            )
                        }

                        if (allowedContexts.contains(AiContextType.NOTEBOOK)) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = Loc.getText("ai_hub_context_notebook"),
                                        fontWeight = if (selectedContextType == AiContextType.NOTEBOOK) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.MenuBook,
                                        contentDescription = null,
                                        tint = if (selectedContextType == AiContextType.NOTEBOOK) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = {
                                    selectedContextType = AiContextType.NOTEBOOK
                                    contextMenuExpanded = false
                                }
                            )
                        }

                        if (allowedContexts.contains(AiContextType.TASK)) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = Loc.getText("ai_hub_context_tasks"),
                                        fontWeight = if (selectedContextType == AiContextType.TASK) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Assignment,
                                        contentDescription = null,
                                        tint = if (selectedContextType == AiContextType.TASK) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = {
                                    selectedContextType = AiContextType.TASK
                                    contextMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // DYNAMIC CONTEXT DETAILS CONTAINER (Hidden until user chooses context)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 340.dp)
            ) {
                when (selectedContextType) {
                    null -> {
                        // Prompt until user chooses context
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.TouchApp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = Loc.getText("ai_hub_choose_context_prompt"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    AiContextType.FREE -> {
                        // Free Chat preview card with clear description
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Public,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = Loc.getText("ai_hub_context_free"),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = Loc.getText("ai_hub_context_free_desc"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }

                    AiContextType.TRACK -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (selectedFunction == AiFunctionType.SCENES) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.MovieCreation,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = Loc.getText("ai_hub_scenes_desc"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Hierarchy Tree File Picker (Folders collapsed by default, with Collapse All / Expand All toggle)
                            HierarchyFilePicker(
                                folders = allFolders,
                                tracks = allTracks,
                                selectedTrack = selectedTrack,
                                onTrackSelected = { selectedTrack = it }
                            )
                        }
                    }

                    AiContextType.NOTEBOOK -> {
                        // Rich Notebook Notes Preview with Expandable Search Icon
                        NotebookNotesPicker(
                            allNotes = allNotes,
                            selectedNotes = selectedNotesList,
                            onSelectionChanged = { selectedNotesList = it }
                        )
                    }

                    AiContextType.TASK -> {
                        // Tasks Preview Cards showing ONLY ACTIVE TASKS
                        TasksPicker(
                            activeTasks = activeTasks,
                            selectedTask = selectedTask,
                            onTaskSelected = { selectedTask = it }
                        )
                    }
                }
            }

            // BOTTOM RUN ACTION BUTTON
            Button(
                onClick = {
                    onDismiss()
                    executeAiAction(
                        viewModel = viewModel,
                        context = context,
                        function = selectedFunction,
                        contextType = selectedContextType ?: AiContextType.TRACK,
                        track = selectedTrack,
                        notes = selectedNotesList,
                        task = selectedTask
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("ai_hub_launch_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = when (selectedFunction) {
                        AiFunctionType.CHAT -> Icons.Filled.Chat
                        AiFunctionType.SUBTITLES -> Icons.Filled.Subtitles
                        AiFunctionType.SCENES -> Icons.Filled.MovieCreation
                        AiFunctionType.QUIZ -> Icons.Filled.PlayArrow
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (selectedFunction) {
                        AiFunctionType.CHAT -> Loc.getText("ai_hub_function_chat")
                        AiFunctionType.SUBTITLES -> Loc.getText("ai_hub_function_subtitles")
                        AiFunctionType.SCENES -> Loc.getText("ai_hub_scenes_action")
                        AiFunctionType.QUIZ -> Loc.getText("ai_hub_function_quiz")
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
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
            when (contextType) {
                AiContextType.FREE -> {
                    // Chat freely without any attached file/note/task context
                    viewModel.openChatWithContext(null)
                }

                AiContextType.TRACK -> {
                    val targetTrack = track ?: AudioPlayerManager.currentTrack.value
                    val isSelectedTrackPlaying = targetTrack != null && AudioPlayerManager.currentTrack.value?.id == targetTrack.id
                    val dur = if (isSelectedTrackPlaying) AudioPlayerManager.duration.value else (targetTrack?.duration ?: 0L)
                    val fullTranscript = getFullSubtitlesForTrack(targetTrack)

                    val summary = AudioContextSummary(
                        trackTitle = targetTrack?.getDisplayTitle(),
                        trackArtist = null,
                        currentPositionMs = null,
                        formattedPosition = null,
                        activeSubtitleLine = null,
                        fullSubtitlesText = fullTranscript?.ifBlank { null },
                        isFullSubtitlesContext = !fullTranscript.isNullOrBlank(),
                        audioFilePath = targetTrack?.filePath,
                        trackId = targetTrack?.id,
                        totalDurationMs = dur
                    )
                    viewModel.openChatWithContext(summary)
                }

                AiContextType.NOTEBOOK -> {
                    val notesSummary = if (notes.isNotEmpty()) {
                        notes.joinToString("; ") { "${it.text}${if (it.comment.isNotBlank()) ": " + it.comment else ""}" }
                    } else null

                    val summary = AudioContextSummary(
                        trackTitle = null,
                        trackArtist = null,
                        currentPositionMs = 0L,
                        formattedPosition = "",
                        activeSubtitleLine = notesSummary
                    )
                    viewModel.openChatWithContext(summary)
                }

                AiContextType.TASK -> {
                    val summary = AudioContextSummary(
                        trackTitle = null,
                        trackArtist = null,
                        currentPositionMs = 0L,
                        formattedPosition = "",
                        activeSubtitleLine = null,
                        activeTaskTitle = task?.getDisplayTitle()
                    )
                    viewModel.openChatWithContext(summary)
                }
            }
        }

        AiFunctionType.SUBTITLES -> {
            if (track == null) {
                Toast.makeText(context, Loc.getText("no_track_selected"), Toast.LENGTH_SHORT).show()
                return
            }
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

        AiFunctionType.SCENES -> {
            if (track == null) {
                Toast.makeText(context, Loc.getText("no_track_selected"), Toast.LENGTH_SHORT).show()
                return
            }
            if (track.isVirtualScene) {
                Toast.makeText(context, Loc.getText("cannot_segment_virtual_scene"), Toast.LENGTH_LONG).show()
                return
            }
            viewModel.startAiSceneDetection(track)
        }

        AiFunctionType.QUIZ -> {
            when (contextType) {
                AiContextType.FREE, AiContextType.TRACK -> {
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
                    viewModel.openUnifiedQuiz(
                        tabMode = QuizTabMode.TRACK,
                        initialTrack = track
                    )
                }
            }
        }
    }
}

private fun getFullSubtitlesForTrack(track: AudioTrack?): String? {
    if (track == null) return null
    val isPlaying = AudioPlayerManager.currentTrack.value?.id == track.id
    if (isPlaying) {
        val raw = AudioPlayerManager.getCurrentSubtitlesRawText()
        if (raw.isNotBlank()) return raw
    }
    if (!track.subtitleContent.isNullOrBlank()) {
        val formatted = SubtitleParser.formatForEditor(track.subtitleContent)
        if (formatted.isNotBlank()) return formatted
    }
    if (!track.subtitlePath.isNullOrBlank()) {
        try {
            val file = java.io.File(track.subtitlePath)
            if (file.exists() && file.canRead()) {
                val formatted = SubtitleParser.formatForEditor(file.readText())
                if (formatted.isNotBlank()) return formatted
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    val autoFile = SubtitleParser.findMatchingSubtitleFile(track.filePath)
    if (autoFile != null && autoFile.canRead()) {
        try {
            val formatted = SubtitleParser.formatForEditor(autoFile.readText())
            if (formatted.isNotBlank()) return formatted
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    val cues = AudioPlayerManager.getOrParseCuesForTrack(track)
    if (cues.isNotEmpty()) {
        return cues.joinToString("\n") { cue ->
            val timeStr = if (cue.isTimed && cue.startMs >= 0) "[${formatDuration(cue.startMs)}] " else ""
            "$timeStr${cue.text}"
        }
    }
    return null
}

// -------------------------------------------------------------------------------------------------
// 1. FILES CONTEXT: HIERARCHICAL TREE FILE PICKER (Collapsed as default + Expand/Collapse All toggle)
// -------------------------------------------------------------------------------------------------

@Composable
private fun HierarchyFilePicker(
    folders: List<Folder>,
    tracks: List<AudioTrack>,
    selectedTrack: AudioTrack?,
    onTrackSelected: (AudioTrack) -> Unit
) {
    val folderTree = remember(folders) { buildFolderTree(folders) }

    // All folders collapsed by default
    var expandedFolderIds by remember(folders) {
        mutableStateOf(emptySet<Long>())
    }

    val allFolderIds = remember(folders) { folders.map { it.id }.toSet() }
    val isAllExpanded = remember(expandedFolderIds, allFolderIds) {
        allFolderIds.isNotEmpty() && expandedFolderIds.containsAll(allFolderIds)
    }

    // Flatten tree respecting expanded folders
    val flattenedFolders = remember(folderTree, expandedFolderIds) {
        flattenFolderTree(folderTree, expandedFolderIds)
    }

    // Independent tracks
    val independentTracks = remember(tracks, folders) {
        val folderIds = folders.map { it.id }.toSet()
        tracks.filter { it.parentFolderId == null || !folderIds.contains(it.parentFolderId) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    ) {
        if (tracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = Loc.getText("empty_tracks_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header with Expand All / Collapse All toggle button
                if (folders.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${Loc.getText("folders")} (${folders.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        TextButton(
                            onClick = {
                                expandedFolderIds = if (isAllExpanded) emptySet() else allFolderIds
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = if (isAllExpanded) Icons.Filled.UnfoldLess else Icons.Filled.UnfoldMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isAllExpanded) Loc.getText("ai_hub_collapse_all") else Loc.getText("ai_hub_expand_all"),
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        thickness = 0.5.dp
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Render Folder Tree
                    flattenedFolders.forEach { node ->
                        val folder = node.folder
                        val isExpanded = expandedFolderIds.contains(folder.id)
                        val folderTracks = tracks.filter { it.parentFolderId == folder.id }

                        item(key = "folder_${folder.id}") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = (node.depth * 14 + 6).dp, end = 10.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        expandedFolderIds = if (isExpanded) {
                                            expandedFolderIds - folder.id
                                        } else {
                                            expandedFolderIds + folder.id
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = folder.folderName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${folderTracks.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Render Tracks within this folder if expanded
                        if (isExpanded) {
                            items(folderTracks, key = { "track_${it.id}" }) { track ->
                                val isSelected = selectedTrack?.id == track.id
                                FileTrackRowItem(
                                    track = track,
                                    depth = node.depth + 1,
                                    isSelected = isSelected,
                                    onSelect = { onTrackSelected(track) }
                                )
                            }
                        }
                    }

                    // Independent tracks section
                    if (independentTracks.isNotEmpty()) {
                        item(key = "header_independent") {
                            Text(
                                text = Loc.getText("ai_hub_independent_tracks"),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(independentTracks, key = { "indep_${it.id}" }) { track ->
                            val isSelected = selectedTrack?.id == track.id
                            FileTrackRowItem(
                                track = track,
                                depth = 0,
                                isSelected = isSelected,
                                onSelect = { onTrackSelected(track) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileTrackRowItem(
    track: AudioTrack,
    depth: Int,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 14 + 10).dp, end = 10.dp, top = 1.dp, bottom = 1.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = getTrackFileIcon(track),
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = track.getDisplayTitle(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 2. NOTEBOOK CONTEXT: NOTES PREVIEW WITH EXPANDABLE SEARCH ICON
// -------------------------------------------------------------------------------------------------

@Composable
private fun NotebookNotesPicker(
    allNotes: List<Note>,
    selectedNotes: List<Note>,
    onSelectionChanged: (List<Note>) -> Unit
) {
    val selectedIds = remember(selectedNotes) { selectedNotes.map { it.id }.toSet() }

    // Search state & Expandable icon
    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    val filteredNotes = remember(allNotes, searchQuery) {
        if (searchQuery.isBlank()) {
            allNotes
        } else {
            allNotes.filter {
                it.text.contains(searchQuery, ignoreCase = true) ||
                it.comment.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            focusRequester.requestFocus()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    ) {
        if (allNotes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = Loc.getText("no_notes_found"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header with Title / Search Toggle / Select All / Deselect All
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSearchExpanded) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = Loc.getText("ai_hub_search_notes"),
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(focusRequester)
                                            .testTag("notes_search_input"),
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = { isSearchExpanded = false })
                                    )
                                }
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Clear,
                                            contentDescription = "Clear",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                IconButton(
                                    onClick = {
                                        searchQuery = ""
                                        isSearchExpanded = false
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Close search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = if (selectedNotes.isEmpty()) {
                                String.format(Loc.getText("ai_hub_all_notes_badge"), allNotes.size)
                            } else {
                                String.format(Loc.getText("ai_hub_notes_count_badge"), selectedNotes.size)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Expandable search icon button
                            IconButton(
                                onClick = { isSearchExpanded = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = "Search",
                                    tint = if (searchQuery.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            TextButton(
                                onClick = { onSelectionChanged(filteredNotes) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(Loc.getText("select_all"), fontSize = 11.sp)
                            }
                            TextButton(
                                onClick = { onSelectionChanged(emptyList()) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(Loc.getText("deselect_all"), fontSize = 11.sp)
                            }
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                    thickness = 0.5.dp
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredNotes, key = { it.id }) { note ->
                        val isChecked = selectedIds.contains(note.id)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            border = BorderStroke(
                                0.5.dp,
                                if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isChecked) {
                                        onSelectionChanged(selectedNotes.filter { it.id != note.id })
                                    } else {
                                        onSelectionChanged(selectedNotes + note)
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            onSelectionChanged(selectedNotes + note)
                                        } else {
                                            onSelectionChanged(selectedNotes.filter { it.id != note.id })
                                        }
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = note.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (note.comment.isNotBlank()) {
                                        Text(
                                            text = note.comment,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
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

// -------------------------------------------------------------------------------------------------
// 3. TASKS CONTEXT: PREVIEW CARDS (ONLY ACTIVE TASKS)
// -------------------------------------------------------------------------------------------------

@Composable
private fun TasksPicker(
    activeTasks: List<Task>,
    selectedTask: Task?,
    onTaskSelected: (Task) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    ) {
        if (activeTasks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = Loc.getText("ai_hub_no_active_tasks"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(activeTasks, key = { it.id }) { task ->
                    val isSelected = selectedTask?.id == task.id
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTaskSelected(task) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Assignment,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.getDisplayTitle(),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${task.sourceType} • ${task.targetValue} ${if (task.targetType == "PLAY_COUNT") "Plays" else "Days"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                    if (task.scheduledDays.isNotBlank()) {
                                        Text(
                                            text = "• ${task.scheduledDays}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            RadioButton(
                                selected = isSelected,
                                onClick = { onTaskSelected(task) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
