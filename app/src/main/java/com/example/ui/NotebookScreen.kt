package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AudioTrack
import com.example.data.Folder
import com.example.data.Note
import com.example.data.NoteTag
import com.example.player.AudioPlayerManager
import com.example.player.NoteAudioPlayer
import com.example.player.SubtitleCue
import com.example.player.SubtitleParser
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookScreen(
    viewModel: AppViewModel,
    onAddNoteClicked: () -> Unit,
    onEditNoteClicked: (Note) -> Unit,
    onNavigateToFolder: (Long) -> Unit = {},
    onNavigateToTrack: (AudioTrack) -> Unit = {},
    onPlayTrackInMainPlayer: (AudioTrack, Long) -> Unit = { _, _ -> },
    onOpenVocabularyReview: () -> Unit = {}
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val tags by viewModel.noteTags.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val noteQuestionCounts by viewModel.noteQuestionCounts.collectAsStateWithLifecycle()
    val playingSnippetNoteId by NoteAudioPlayer.playingNoteId.collectAsStateWithLifecycle()
    val isSnippetPlaying by NoteAudioPlayer.isPlaying.collectAsStateWithLifecycle()
    val snippetPosition by NoteAudioPlayer.currentPosition.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var selectedFolderIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showFolderFilterDialog by remember { mutableStateOf(false) }
    var selectedTags by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showTagFilterDialog by remember { mutableStateOf(false) }
    var selectedTrackId by remember { mutableStateOf<Long?>(null) }
    var noteToDelete by remember { mutableStateOf<Note?>(null) }
    var viewingNoteTarget by remember { mutableStateOf<Note?>(null) }

    val context = LocalContext.current

    // Filter notes
    val filteredNotes = remember(notes, searchQuery, selectedFolderIds, selectedTags, selectedTrackId) {
        notes.filter { note ->
            val matchesSearch = if (searchQuery.isBlank()) true else {
                val q = searchQuery.trim().lowercase()
                note.text.lowercase().contains(q) ||
                note.comment.lowercase().contains(q) ||
                note.tags.lowercase().contains(q) ||
                (note.trackName?.lowercase()?.contains(q) == true) ||
                (note.folderName?.lowercase()?.contains(q) == true)
            }

            val matchesFolder = if (selectedFolderIds.isEmpty()) true else {
                note.folderId != null && selectedFolderIds.contains(note.folderId)
            }

            val matchesTag = if (selectedTags.isEmpty()) true else {
                val noteTagList = note.getTagsList()
                selectedTags.any { sTag ->
                    noteTagList.any { it.equals(sTag, ignoreCase = true) }
                }
            }

            val matchesTrack = if (selectedTrackId == null) true else {
                note.trackId == selectedTrackId
            }

            matchesSearch && matchesFolder && matchesTag && matchesTrack
        }
    }

    val allTagsList = remember(notes) {
        notes.flatMap { it.getTagsList() }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sorted()
    }

    Scaffold(
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Header: Top Actions Row (Collapsible Search, Filter by Folder, Filter by Tag, Add Note Icon)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isSearchExpanded) Arrangement.Start else Arrangement.End
            ) {
                if (isSearchExpanded) {
                    // Expanded Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("search_notes_field"),
                        placeholder = { Text(Loc.getText("search_notes_placeholder"), fontSize = 13.sp) },
                        leadingIcon = {
                            IconButton(onClick = {
                                isSearchExpanded = false
                                searchQuery = ""
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        )
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Folder Filter Button
                    IconButton(
                        onClick = { showFolderFilterDialog = true },
                        modifier = Modifier.testTag("filter_folders_btn")
                    ) {
                        BadgedBox(
                            badge = {
                                if (selectedFolderIds.isNotEmpty()) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ) {
                                        Text(selectedFolderIds.size.toString(), fontSize = 10.sp)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FilterList,
                                contentDescription = Loc.getText("filter_folders_dialog_title"),
                                tint = if (selectedFolderIds.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Tag Filter Button
                    IconButton(
                        onClick = { showTagFilterDialog = true },
                        modifier = Modifier.testTag("filter_tags_btn")
                    ) {
                        BadgedBox(
                            badge = {
                                if (selectedTags.isNotEmpty()) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ) {
                                        Text(selectedTags.size.toString(), fontSize = 10.sp)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Tag,
                                contentDescription = Loc.getText("filter_tags_dialog_title"),
                                tint = if (selectedTags.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // Top Action Icons (Search, Filter Folders, Filter Tags, Add Note Icon)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Expandable Search Button
                        IconButton(
                            onClick = { isSearchExpanded = true },
                            modifier = Modifier.testTag("search_notes_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Search",
                                tint = if (searchQuery.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Filter by Folders Icon Button
                        IconButton(
                            onClick = { showFolderFilterDialog = true },
                            modifier = Modifier.testTag("filter_folders_btn")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (selectedFolderIds.isNotEmpty()) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ) {
                                            Text(selectedFolderIds.size.toString(), fontSize = 10.sp)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FilterList,
                                    contentDescription = Loc.getText("filter_folders_dialog_title"),
                                    tint = if (selectedFolderIds.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Filter by Tags Icon Button
                        IconButton(
                            onClick = { showTagFilterDialog = true },
                            modifier = Modifier.testTag("filter_tags_btn")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (selectedTags.isNotEmpty()) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ) {
                                            Text(selectedTags.size.toString(), fontSize = 10.sp)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Tag,
                                    contentDescription = Loc.getText("filter_tags_dialog_title"),
                                    tint = if (selectedTags.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Single Add Note Icon Button
                        FilledTonalIconButton(
                            onClick = onAddNoteClicked,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("add_note_top_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.EditNote,
                                contentDescription = Loc.getText("add_note"),
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Filter Chips Bar (Active Folder Count + Active Tags Count)
            val hasActiveFilterChips = selectedFolderIds.isNotEmpty() || selectedTags.isNotEmpty()
            if (hasActiveFilterChips) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    // Folder Multi-selection Chip
                    if (selectedFolderIds.isNotEmpty()) {
                        item {
                            FilterChip(
                                selected = true,
                                onClick = { showFolderFilterDialog = true },
                                label = {
                                    Text(
                                        text = "${selectedFolderIds.size} ${Loc.getText("selected_folders_label")}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(14.dp))
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { selectedFolderIds = emptySet() },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(12.dp))
                                    }
                                }
                            )
                        }
                    }

                    // Tag Multi-selection Chip
                    if (selectedTags.isNotEmpty()) {
                        item {
                            FilterChip(
                                selected = true,
                                onClick = { showTagFilterDialog = true },
                                label = {
                                    Text(
                                        text = "${selectedTags.size} ${Loc.getText("selected_tags_label")}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Filled.Tag, contentDescription = null, modifier = Modifier.size(14.dp))
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { selectedTags = emptySet() },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(12.dp))
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Notes List
            if (filteredNotes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.MenuBook,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = Loc.getText("no_notes_found"),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onAddNoteClicked,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(Loc.getText("add_note"))
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 140.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredNotes, key = { it.id }) { note ->
                        val isThisSnippetPlaying = isSnippetPlaying && playingSnippetNoteId == note.id
                        val qCount = noteQuestionCounts[note.id] ?: 0

                        NoteCard(
                            note = note,
                            isPlaying = isThisSnippetPlaying,
                            currentPosition = if (isThisSnippetPlaying) snippetPosition else 0L,
                            questionCount = qCount,
                            onPlaySnippet = {
                                if (isThisSnippetPlaying) {
                                    viewModel.stopNoteSnippet()
                                } else {
                                    viewModel.playNoteSnippet(note)
                                }
                            },
                            onToggleFavorite = { viewModel.toggleNoteFavorite(note) },
                            onView = { viewingNoteTarget = note },
                            onEdit = { onEditNoteClicked(note) },
                            onDelete = { noteToDelete = note },
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clipText = buildString {
                                    append(note.text)
                                    if (note.comment.isNotBlank()) {
                                        append("\n\n")
                                        append(note.comment)
                                    }
                                    if (note.trackName != null) {
                                        append("\n— ${note.trackName}")
                                        if (note.startTimestampMs > 0) {
                                            append(" (${formatDuration(note.startTimestampMs)})")
                                        }
                                    }
                                }
                                val clip = ClipData.newPlainText("Hearmark Note", clipText)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, Loc.getText("note_copied"), Toast.LENGTH_SHORT).show()
                            },
                            onCreateQuizQuestion = {
                                val associatedTrack = if (note.trackId != null) tracks.find { it.id == note.trackId } else null
                                viewModel.openAiHub(
                                    function = AiFunctionType.QUIZ,
                                    track = associatedTrack,
                                    notes = listOf(note)
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    // View Note Details Modal (Full Text View)
    viewingNoteTarget?.let { targetNote ->
        val liveNote = notes.find { it.id == targetNote.id } ?: targetNote
        val isThisSnippetPlaying = isSnippetPlaying && playingSnippetNoteId == liveNote.id

        ViewNoteDetailsModal(
            note = liveNote,
            isPlaying = isThisSnippetPlaying,
            currentPosition = if (isThisSnippetPlaying) snippetPosition else 0L,
            questionCount = noteQuestionCounts[liveNote.id] ?: 0,
            allTracks = tracks,
            onPlaySnippet = {
                if (isThisSnippetPlaying) {
                    viewModel.stopNoteSnippet()
                } else {
                    viewModel.playNoteSnippet(liveNote)
                }
            },
            onPlayInMainPlayer = { track, startMs ->
                viewingNoteTarget = null
                onPlayTrackInMainPlayer(track, startMs)
            },
            onNavigateToFolder = { folderId ->
                viewingNoteTarget = null
                onNavigateToFolder(folderId)
            },
            onNavigateToTrack = { track ->
                viewingNoteTarget = null
                onNavigateToTrack(track)
            },
            onSelectTagFilter = { tag ->
                viewingNoteTarget = null
                selectedTags = setOf(tag)
                Toast.makeText(context, "${Loc.getText("filter_by_tag")}: #$tag", Toast.LENGTH_SHORT).show()
            },
            onToggleFavorite = { viewModel.toggleNoteFavorite(liveNote) },
            onEdit = {
                val toEdit = liveNote
                viewingNoteTarget = null
                onEditNoteClicked(toEdit)
            },
            onDelete = {
                val toDelete = liveNote
                viewingNoteTarget = null
                noteToDelete = toDelete
            },
            onCopy = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipText = buildString {
                    append(liveNote.text)
                    if (liveNote.comment.isNotBlank()) {
                        append("\n\n")
                        append(liveNote.comment)
                    }
                    if (liveNote.trackName != null) {
                        append("\n— ${liveNote.trackName}")
                        if (liveNote.startTimestampMs > 0) {
                            append(" (${formatDuration(liveNote.startTimestampMs)})")
                        }
                    }
                }
                val clip = ClipData.newPlainText("Hearmark Note", clipText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, Loc.getText("note_copied"), Toast.LENGTH_SHORT).show()
            },
            onCreateQuizQuestion = {
                val noteForQuiz = liveNote
                viewingNoteTarget = null
                val associatedTrack = if (noteForQuiz.trackId != null) tracks.find { it.id == noteForQuiz.trackId } else null
                viewModel.openAiHub(
                    function = AiFunctionType.QUIZ,
                    track = associatedTrack,
                    notes = listOf(noteForQuiz)
                )
            },
            onDismiss = { viewingNoteTarget = null }
        )
    }

    // Delete Confirmation Dialog
    if (noteToDelete != null) {
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text(Loc.getText("delete_note")) },
            text = { Text(Loc.getText("confirm_delete_note")) },
            confirmButton = {
                Button(
                    onClick = {
                        noteToDelete?.let { viewModel.deleteNote(it) }
                        noteToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(Loc.getText("delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) {
                    Text(Loc.getText("cancel"))
                }
            }
        )
    }

    // Multi-Folder Selection Dialog (Hierarchical Tree View)
    if (showFolderFilterDialog) {
        var tempSelectedFolderIds by remember(selectedFolderIds) {
            mutableStateOf(selectedFolderIds)
        }

        val folderTree = remember(folders) {
            buildFolderTree(folders)
        }

        // Keep root/parent folders expanded by default
        var expandedFolderIds by remember(folders) {
            val parentIds = folders.filter { f ->
                folders.any { it.parentFolderId == f.id || (it.id != f.id && it.folderPath.startsWith(f.folderPath + java.io.File.separator)) }
            }.map { it.id }.toSet()
            mutableStateOf(parentIds)
        }

        val flattenedVisibleTree = remember(folderTree, expandedFolderIds) {
            flattenFolderTree(folderTree, expandedFolderIds)
        }

        AlertDialog(
            onDismissRequest = { showFolderFilterDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = Loc.getText("filter_folders_dialog_title"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (folders.isNotEmpty()) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "${tempSelectedFolderIds.size} / ${folders.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    // Quick Select All / Deselect All / Expand All / Collapse All
                    if (folders.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = {
                                        tempSelectedFolderIds = folders.map { it.id }.toSet()
                                    },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(Loc.getText("select_all"), fontSize = 12.sp)
                                }
                                TextButton(
                                    onClick = {
                                        tempSelectedFolderIds = emptySet()
                                    },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(Loc.getText("deselect_all"), fontSize = 12.sp)
                                }
                            }

                            val allParentIds = folders.filter { f ->
                                folders.any { it.parentFolderId == f.id || (it.id != f.id && it.folderPath.startsWith(f.folderPath + java.io.File.separator)) }
                            }.map { it.id }.toSet()

                            if (allParentIds.isNotEmpty()) {
                                val isAllExpanded = expandedFolderIds.containsAll(allParentIds)
                                IconButton(
                                    onClick = {
                                        expandedFolderIds = if (isAllExpanded) emptySet() else allParentIds
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isAllExpanded) Icons.Filled.UnfoldLess else Icons.Filled.UnfoldMore,
                                        contentDescription = if (isAllExpanded) "Collapse All" else "Expand All",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }

                    if (folders.isEmpty()) {
                        Text(
                            text = Loc.getText("empty_folders"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 340.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            items(flattenedVisibleTree, key = { it.folder.id }) { node ->
                                val folder = node.folder
                                val hasChildren = node.children.isNotEmpty()
                                val isExpanded = expandedFolderIds.contains(folder.id)
                                val subtreeIds = remember(node) { getAllNodeIds(node) }

                                val isChecked = tempSelectedFolderIds.contains(folder.id)
                                val allSubtreeChecked = subtreeIds.all { tempSelectedFolderIds.contains(it) }
                                val folderNotesCount = notes.count { it.folderId == folder.id }
                                val totalSubtreeNotesCount = remember(node, notes) { getFolderNotesCount(node, notes) }

                                Surface(
                                   shape = RoundedCornerShape(10.dp),
                                   color = if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent,
                                   modifier = Modifier
                                       .fillMaxWidth()
                                       .padding(start = (node.depth * 18).dp)
                                       .clickable {
                                           if (hasChildren) {
                                               // Toggle folder + all subfolders if parent
                                               tempSelectedFolderIds = if (allSubtreeChecked) {
                                                   tempSelectedFolderIds - subtreeIds
                                               } else {
                                                   tempSelectedFolderIds + subtreeIds
                                               }
                                           } else {
                                               tempSelectedFolderIds = if (isChecked) {
                                                   tempSelectedFolderIds - folder.id
                                               } else {
                                                   tempSelectedFolderIds + folder.id
                                               }
                                           }
                                       }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Expand/Collapse arrow icon (if has subfolders) or Indent spacer
                                        if (hasChildren) {
                                            IconButton(
                                                onClick = {
                                                    expandedFolderIds = if (isExpanded) {
                                                        expandedFolderIds - folder.id
                                                    } else {
                                                        expandedFolderIds + folder.id
                                                    }
                                                },
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                                                    modifier = Modifier.size(18.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        } else {
                                            if (node.depth > 0) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .padding(start = 2.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                            } else {
                                                Spacer(modifier = Modifier.width(26.dp))
                                            }
                                        }

                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                if (hasChildren) {
                                                    tempSelectedFolderIds = if (checked) {
                                                        tempSelectedFolderIds + subtreeIds
                                                    } else {
                                                        tempSelectedFolderIds - subtreeIds
                                                    }
                                                } else {
                                                    tempSelectedFolderIds = if (checked) {
                                                        tempSelectedFolderIds + folder.id
                                                    } else {
                                                        tempSelectedFolderIds - folder.id
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        )

                                        Spacer(modifier = Modifier.width(4.dp))

                                        Icon(
                                            imageVector = if (hasChildren && isExpanded) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                                            contentDescription = null,
                                            tint = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )

                                        Spacer(modifier = Modifier.width(6.dp))

                                        Text(
                                            text = folder.folderName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (node.depth == 0) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        // Notes count indicator
                                        val displayCount = if (hasChildren) totalSubtreeNotesCount else folderNotesCount
                                        if (displayCount > 0) {
                                            Surface(
                                                shape = CircleShape,
                                                color = if (isChecked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                modifier = Modifier.padding(start = 4.dp)
                                            ) {
                                                Text(
                                                    text = "$displayCount",
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    color = if (isChecked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedFolderIds = tempSelectedFolderIds
                        showFolderFilterDialog = false
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(Loc.getText("apply_filter"))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showFolderFilterDialog = false }
                ) {
                    Text(Loc.getText("cancel"))
                }
            }
        )
    }

    // Multi-Tag Selection Dialog
    if (showTagFilterDialog) {
        var tempSelectedTags by remember(selectedTags) {
            mutableStateOf(selectedTags)
        }

        AlertDialog(
            onDismissRequest = { showTagFilterDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = Loc.getText("filter_tags_dialog_title"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (allTagsList.isNotEmpty()) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "${tempSelectedTags.size} / ${allTagsList.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    // Quick Select All / Deselect All
                    if (allTagsList.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    tempSelectedTags = allTagsList.toSet()
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(Loc.getText("select_all"), fontSize = 12.sp)
                            }
                            TextButton(
                                onClick = {
                                    tempSelectedTags = emptySet()
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(Loc.getText("deselect_all"), fontSize = 12.sp)
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }

                    if (allTagsList.isEmpty()) {
                        Text(
                            text = Loc.getText("empty_tags"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(allTagsList, key = { it }) { tagStr ->
                                val isChecked = tempSelectedTags.contains(tagStr)
                                val tagNotesCount = notes.count { n -> n.getTagsList().any { it.equals(tagStr, ignoreCase = true) } }

                                Surface(
                                   shape = RoundedCornerShape(10.dp),
                                   color = if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                                   modifier = Modifier
                                       .fillMaxWidth()
                                       .clickable {
                                           tempSelectedTags = if (isChecked) {
                                               tempSelectedTags - tagStr
                                           } else {
                                               tempSelectedTags + tagStr
                                           }
                                       }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                tempSelectedTags = if (checked) {
                                                    tempSelectedTags + tagStr
                                                } else {
                                                    tempSelectedTags - tagStr
                                                }
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Icon(
                                            imageVector = Icons.Filled.Tag,
                                            contentDescription = null,
                                            tint = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "#$tagStr",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (tagNotesCount > 0) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                modifier = Modifier.padding(start = 4.dp)
                                            ) {
                                                Text(
                                                    text = "$tagNotesCount",
                                                    fontSize = 11.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedTags = tempSelectedTags
                        showTagFilterDialog = false
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(Loc.getText("apply_filter"))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTagFilterDialog = false }
                ) {
                    Text(Loc.getText("cancel"))
                }
            }
        )
    }
}
