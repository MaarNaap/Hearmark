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
import com.example.data.Folder
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
    val allFolders by viewModel.folders.collectAsStateWithLifecycle()
    val allNotes by viewModel.notes.collectAsStateWithLifecycle()
    val allTasks by viewModel.allTasks.collectAsStateWithLifecycle()

    // 1. Function Selection
    var selectedFunction by remember { mutableStateOf(initialFunction) }

    // 2. Context Type Selection (FILES, NOTEBOOK, TASKS)
    // Initially null unless caller provided a specific initial context
    var selectedContextType by remember {
        mutableStateOf<AiContextType?>(
            when {
                initialNotes != null -> AiContextType.NOTEBOOK
                initialTask != null -> AiContextType.TASK
                initialTrack != null -> AiContextType.TRACK
                else -> null
            }
        )
    }

    // Selected Targets for Each Context Type
    var selectedTrack by remember(initialTrack, currentTrackState, allTracks) {
        mutableStateOf(initialTrack ?: currentTrackState ?: allTracks.firstOrNull())
    }

    var selectedNotesList by remember(initialNotes) {
        mutableStateOf(initialNotes ?: emptyList())
    }

    var selectedTask by remember(initialTask, allTasks) {
        mutableStateOf(initialTask ?: allTasks.firstOrNull())
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

            // TWO DROPDOWN MENUS IN ONE COMPACT SECTION
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

                // DROPDOWN 2: CONTEXT SELECTION (Files, Notebook, Tasks)
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
                                if (selectedFunction == AiFunctionType.SUBTITLES) {
                                    selectedFunction = AiFunctionType.QUIZ
                                }
                                selectedContextType = AiContextType.NOTEBOOK
                                contextMenuExpanded = false
                            }
                        )
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
                                if (selectedFunction != AiFunctionType.CHAT) {
                                    selectedFunction = AiFunctionType.CHAT
                                }
                                selectedContextType = AiContextType.TASK
                                contextMenuExpanded = false
                            }
                        )
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
                        // Clean empty state prompt
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

                    AiContextType.TRACK -> {
                        // Hierarchy Tree File Picker (Folders & Audio Tracks)
                        HierarchyFilePicker(
                            folders = allFolders,
                            tracks = allTracks,
                            selectedTrack = selectedTrack,
                            onTrackSelected = { selectedTrack = it }
                        )
                    }

                    AiContextType.NOTEBOOK -> {
                        // Rich Notebook Notes Preview & Multi-Selection List
                        NotebookNotesPicker(
                            allNotes = allNotes,
                            selectedNotes = selectedNotesList,
                            onSelectionChanged = { selectedNotesList = it }
                        )
                    }

                    AiContextType.TASK -> {
                        // Tasks Preview Cards with Progress & Single Selection
                        TasksPicker(
                            allTasks = allTasks,
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

// -------------------------------------------------------------------------------------------------
// 1. FILES CONTEXT: HIERARCHICAL TREE FILE PICKER
// -------------------------------------------------------------------------------------------------

@Composable
private fun HierarchyFilePicker(
    folders: List<Folder>,
    tracks: List<AudioTrack>,
    selectedTrack: AudioTrack?,
    onTrackSelected: (AudioTrack) -> Unit
) {
    // Build hierarchical tree nodes
    val folderTree = remember(folders) { buildFolderTree(folders) }

    // State of expanded folders: expand root folders by default
    var expandedFolderIds by remember(folders) {
        mutableStateOf(folders.map { it.id }.toSet())
    }

    // Flatten tree respecting expanded folders
    val flattenedFolders = remember(folderTree, expandedFolderIds) {
        flattenFolderTree(folderTree, expandedFolderIds)
    }

    // Independent tracks (no parent folder or parent folder not found)
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 6.dp),
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
                imageVector = Icons.Filled.Audiotrack,
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
// 2. NOTEBOOK CONTEXT: NOTES PREVIEW & SELECTION
// -------------------------------------------------------------------------------------------------

@Composable
private fun NotebookNotesPicker(
    allNotes: List<Note>,
    selectedNotes: List<Note>,
    onSelectionChanged: (List<Note>) -> Unit
) {
    val selectedIds = remember(selectedNotes) { selectedNotes.map { it.id }.toSet() }

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
                // Header with Select All / Deselect All
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
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

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = { onSelectionChanged(allNotes) },
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

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                    thickness = 0.5.dp
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(allNotes, key = { it.id }) { note ->
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
// 3. TASKS CONTEXT: PREVIEW CARDS
// -------------------------------------------------------------------------------------------------

@Composable
private fun TasksPicker(
    allTasks: List<Task>,
    selectedTask: Task?,
    onTaskSelected: (Task) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    ) {
        if (allTasks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = Loc.getText("no_tasks_found"),
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
                items(allTasks, key = { it.id }) { task ->
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
                                    imageVector = if (task.isCompleted) Icons.Filled.CheckCircle else Icons.Filled.Assignment,
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
