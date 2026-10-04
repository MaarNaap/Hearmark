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
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.MenuBook
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

            // Active Background AI Operation Indicator (if any is already running)
            AiBackgroundStatusBar(
                viewModel = viewModel,
                modifier = Modifier.fillMaxWidth()
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
                                        AiContextType.NOTEBOOK -> Icons.AutoMirrored.Filled.MenuBook
                                        AiContextType.TASK -> Icons.AutoMirrored.Filled.Assignment
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
                        AiFunctionType.CHAT -> Icons.AutoMirrored.Filled.Chat
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
