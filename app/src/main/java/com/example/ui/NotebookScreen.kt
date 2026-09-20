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

@Composable
fun NoteCard(
    note: Note,
    isPlaying: Boolean,
    currentPosition: Long,
    questionCount: Int = 0,
    onPlaySnippet: () -> Unit,
    onToggleFavorite: () -> Unit,
    onView: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onCreateQuizQuestion: (() -> Unit)? = null
) {
    val favTag = Loc.getText("favorite_tag_name")
    val isFavorite = remember(note.tags, favTag) {
        note.getTagsList().any { it.equals("favorite", ignoreCase = true) || it == "المفضلة" || it == favTag }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onView() }
            .testTag("note_card_${note.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(
            1.dp,
            if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Main Note / Word / Quote Text (Clamped to 3 lines for easy scanning)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .width(3.5.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
                Text(
                    text = note.text,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // User Explanation / Comment (Clamped to 3 lines for compact card layout)
            if (note.comment.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                ) {
                    Text(
                        text = note.comment,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }

            // Bottom Footer Row: Actions (Delete, Copy, Edit, Favorite) on the start/left, Audio snippet play/pause on the end/right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Action Icons (Delete at far left, Copy, Edit ✏️, Favorite)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Delete Icon Button (at the far left)
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = Loc.getText("delete_note"),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                        )
                    }

                    // Copy Icon Button
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = Loc.getText("copy_note"),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }

                    // Edit Icon Button (✏️)
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("note_edit_btn_${note.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = Loc.getText("edit_note"),
                            modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Favorite Toggle Icon Button
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("note_favorite_btn_${note.id}")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = Loc.getText("favorite"),
                            modifier = Modifier.size(18.dp),
                            tint = if (isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }

                    // Quick AI Quiz Generation from Note Button
                    if (onCreateQuizQuestion != null) {
                        IconButton(
                            onClick = onCreateQuizQuestion,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("note_quiz_btn_${note.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = Loc.getText("notebook_quiz_single_note_btn"),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Display question count if questions are attached to this note
                    if (questionCount > 0) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .height(22.dp)
                                .padding(horizontal = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$questionCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                // Audio Snippet Play Button on the right (if linked to track)
                if (note.trackId != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isPlaying) {
                            Text(
                                text = formatDuration(currentPosition),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        FilledTonalIconButton(
                            onClick = onPlaySnippet,
                            modifier = Modifier.size(32.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                contentColor = if (isPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = Loc.getText("play_clip"),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackCueNotesBottomSheet(
    notes: List<Note>,
    noteQuestionCounts: Map<Long, Int> = emptyMap(),
    onDismiss: () -> Unit,
    onEditNote: (Note) -> Unit,
    onDeleteNote: (Note) -> Unit,
    onToggleFavorite: (Note) -> Unit,
    onCopyNote: (Note) -> Unit
) {
    val favTag = Loc.getText("favorite_tag_name")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Row: Title & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.EditNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = if (notes.size > 1) {
                            "${Loc.getText("notes")} (${notes.size})"
                        } else {
                            Loc.getText("note_comment")
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Stack of Notes: Explanation & Personal Notes only + compact action row
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                notes.forEachIndexed { index, note ->
                    val isFavorite = remember(note.tags, favTag) {
                        note.getTagsList().any { it.equals("favorite", ignoreCase = true) || it == "المفضلة" || it == favTag }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            // Body: Explanation & Personal Notes ONLY
                            val commentText = note.comment.trim()
                            if (commentText.isNotBlank()) {
                                Text(
                                    text = commentText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 22.sp
                                )
                            } else {
                                Text(
                                    text = Loc.getText("no_comment_fallback"),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    lineHeight = 20.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                thickness = 0.5.dp
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            // Compact Action Buttons Row for this note individually
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (notes.size > 1) {
                                    Text(
                                        text = "#${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    Spacer(modifier = Modifier.width(1.dp))
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val qCount = noteQuestionCounts[note.id] ?: 0
                                    if (qCount > 0) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier
                                                .height(22.dp)
                                                .padding(horizontal = 2.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(horizontal = 7.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "$qCount",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }

                                    // Favorite Toggle
                                    IconButton(
                                        onClick = { onToggleFavorite(note) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                            contentDescription = Loc.getText("favorite"),
                                            tint = if (isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }

                                    // Copy Note
                                    IconButton(
                                        onClick = { onCopyNote(note) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.ContentCopy,
                                            contentDescription = Loc.getText("copy_note"),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Edit Note
                                    IconButton(
                                        onClick = { onEditNote(note) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = Loc.getText("edit_note"),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Delete Note
                                    IconButton(
                                        onClick = { onDeleteNote(note) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = Loc.getText("delete_note"),
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewNoteDetailsModal(
    note: Note,
    isPlaying: Boolean,
    currentPosition: Long,
    questionCount: Int = 0,
    allTracks: List<AudioTrack>,
    onPlaySnippet: () -> Unit,
    onPlayInMainPlayer: (AudioTrack, Long) -> Unit,
    onNavigateToFolder: (Long) -> Unit,
    onNavigateToTrack: (AudioTrack) -> Unit,
    onSelectTagFilter: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onCreateQuizQuestion: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val favTag = Loc.getText("favorite_tag_name")
    val isFavorite = remember(note.tags, favTag) {
        note.getTagsList().any { it.equals("favorite", ignoreCase = true) || it == "المفضلة" || it == favTag }
    }
    val associatedTrack = remember(note.trackId, allTracks) {
        if (note.trackId != null) allTracks.find { it.id == note.trackId } else null
    }
    val effectiveFolderId = note.folderId ?: associatedTrack?.parentFolderId
    val effectiveFolderName = note.folderName

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Row: Title & Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = Loc.getText("note_details"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onCreateQuizQuestion,
                        modifier = Modifier.testTag("view_note_quiz_header_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = Loc.getText("notebook_quiz_single_note_btn"),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (questionCount > 0) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .height(22.dp)
                                .padding(end = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$questionCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Quote / Sentence Section (Full Text)
            Text(
                text = Loc.getText("note_text"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .width(4.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        text = note.text,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 24.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Explanation / Personal Notes Section (Full Text)
            if (note.comment.isNotBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = Loc.getText("note_comment"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = note.comment,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            // Linked Audio Track & Actions (Snippet playback, Main Player, Open in Folder)
            if (note.trackId != null || note.trackName != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            if (effectiveFolderId != null) {
                                onNavigateToFolder(effectiveFolderId)
                            } else if (associatedTrack != null) {
                                onNavigateToTrack(associatedTrack)
                            }
                        },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        // Track Info Row (Clickable to navigate to file in Library/Folder)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val icon = if (associatedTrack != null) {
                                        getTrackFileIcon(associatedTrack)
                                    } else {
                                        getTrackFileIcon(note.trackName ?: "")
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = associatedTrack?.fileName ?: note.trackName ?: Loc.getText("linked_audio_clip"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (note.startTimestampMs > 0 || note.endTimestampMs > 0) {
                                            Text(
                                                text = "${formatDuration(note.startTimestampMs)} - ${formatDuration(note.endTimestampMs)}",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (!effectiveFolderName.isNullOrBlank()) {
                                            Text(
                                                text = "• ${effectiveFolderName}",
                                                fontSize = 11.5.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }

                            // Open in folder / library indicator
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = Loc.getText("open_in_library"),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Audio Action Buttons: Isolated Snippet Player & Full Main Player
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Isolated Note Snippet Player Toggle
                            FilledTonalButton(
                                onClick = onPlaySnippet,
                                modifier = Modifier.weight(1f).height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                    contentColor = if (isPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isPlaying) Loc.getText("pause_clip") else Loc.getText("play_clip"),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // 2. Play Full Audio in Main Player
                            if (associatedTrack != null) {
                                OutlinedButton(
                                    onClick = {
                                        onPlayInMainPlayer(associatedTrack, note.startTimestampMs)
                                    },
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayCircleOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = Loc.getText("play_in_main_player"),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Interactive Tags & Folder Info Chips
            val tagsList = note.getTagsList().filter { !it.equals("favorite", ignoreCase = true) && it != "المفضلة" && it != favTag }
            if (tagsList.isNotEmpty() || !effectiveFolderName.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Clickable Folder Chip -> Navigates to folder
                    if (!effectiveFolderName.isNullOrBlank()) {
                        item {
                            SuggestionChip(
                                onClick = {
                                    if (effectiveFolderId != null) {
                                        onNavigateToFolder(effectiveFolderId)
                                    } else if (associatedTrack != null) {
                                        onNavigateToTrack(associatedTrack)
                                    }
                                },
                                icon = {
                                    Icon(
                                        Icons.Filled.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = effectiveFolderName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                            )
                        }
                    }

                    // Clickable Tag Chips -> Filters notebook by tag
                    items(tagsList) { t ->
                        SuggestionChip(
                            onClick = {
                                onSelectTagFilter(t)
                            },
                            icon = {
                                Icon(
                                    Icons.Filled.Tag,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = t,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )
                    }
                }
            }

            // Date / Timestamp
            if (note.createdAt > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                Text(
                    text = "${Loc.getText("created_at")}: ${sdf.format(Date(note.createdAt))}",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons Bar (Edit, Favorite, Copy, Delete)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Edit Button
                Button(
                    onClick = onEdit,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("view_note_edit_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(Loc.getText("edit_note"), fontWeight = FontWeight.Bold)
                }

                // Extract / Generate Quiz Button
                FilledTonalIconButton(
                    onClick = onCreateQuizQuestion,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("view_note_quiz_bottom_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = Loc.getText("notebook_quiz_single_note_btn"),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Favorite Button
                FilledTonalIconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = Loc.getText("favorite"),
                        tint = if (isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Copy Button
                FilledTonalIconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = Loc.getText("copy_note"))
                }

                // Delete Button
                FilledTonalIconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = Loc.getText("delete_note"))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditNoteModal(
    initialNote: Note? = null,
    prefilledText: String = "",
    prefilledComment: String = "",
    prefilledTrackId: Long? = null,
    prefilledStartMs: Long = 0L,
    prefilledEndMs: Long = 0L,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val existingTags by viewModel.noteTags.collectAsStateWithLifecycle()
    val allNotes by viewModel.notes.collectAsStateWithLifecycle()
    val allTracks by viewModel.tracks.collectAsStateWithLifecycle()
    val isSnippetPlaying by NoteAudioPlayer.isPlaying.collectAsStateWithLifecycle()
    val playingSnippetNoteId by NoteAudioPlayer.playingNoteId.collectAsStateWithLifecycle()
    val isCurrentSnippetPlaying = isSnippetPlaying && playingSnippetNoteId == -1L

    DisposableEffect(Unit) {
        onDispose {
            if (NoteAudioPlayer.playingNoteId.value == -1L) {
                NoteAudioPlayer.stop()
            }
        }
    }

    val favTag = Loc.getText("favorite_tag_name")
    val isFavoriteTag: (String) -> Boolean = remember(favTag) {
        { tag -> tag.equals("favorite", ignoreCase = true) || tag == "المفضلة" || tag.equals(favTag, ignoreCase = true) }
    }

    val availableTags = remember(allNotes, existingTags, favTag) {
        (allNotes.flatMap { it.getTagsList() } + existingTags.map { it.name })
            .map { it.trim() }
            .filter { it.isNotEmpty() && !isFavoriteTag(it) }
            .distinctBy { it.lowercase() }
            .sorted()
    }

    var noteText by remember {
        mutableStateOf(initialNote?.text ?: prefilledText)
    }
    var commentText by remember {
        mutableStateOf(initialNote?.comment ?: prefilledComment)
    }
    var selectedTrackId by remember {
        mutableStateOf(initialNote?.trackId ?: prefilledTrackId)
    }
    val selectedTrack = remember(selectedTrackId, allTracks) {
        allTracks.find { it.id == selectedTrackId }
    }
    var startMs by remember {
        val initialStart = initialNote?.startTimestampMs ?: prefilledStartMs
        val adjustedStart = if (selectedTrack?.isVirtualScene == true && initialStart > 0 && initialStart < selectedTrack.startOffsetMs) {
            selectedTrack.startOffsetMs + initialStart
        } else if (selectedTrack?.isVirtualScene == true && initialStart <= 0) {
            selectedTrack.startOffsetMs
        } else {
            initialStart
        }
        mutableLongStateOf(adjustedStart.coerceAtLeast(0L))
    }
    var endMs by remember {
        val initialEnd = initialNote?.endTimestampMs ?: if (prefilledEndMs > prefilledStartMs) prefilledEndMs else (prefilledStartMs + 5000L)
        val adjustedEnd = if (selectedTrack?.isVirtualScene == true && initialEnd > 0 && initialEnd < selectedTrack.startOffsetMs) {
            selectedTrack.startOffsetMs + initialEnd
        } else if (selectedTrack?.isVirtualScene == true && initialEnd <= 0) {
            selectedTrack.startOffsetMs + 5000L
        } else {
            initialEnd
        }
        mutableLongStateOf(adjustedEnd.coerceAtLeast(startMs))
    }

    var selectedTags by remember(initialNote, favTag) {
        val tags = initialNote?.getTagsList() ?: emptyList()
        mutableStateOf(tags.filter { !isFavoriteTag(it) }.toSet())
    }
    var newTagInput by remember { mutableStateOf("") }
    var isNewTagFieldVisible by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isGeneratingAiExplanation by remember { mutableStateOf(false) }
    var showDiscardConfirmationDialog by remember { mutableStateOf(false) }

    val handleDismissAttempt: () -> Unit = {
        if (commentText.isNotBlank()) {
            showDiscardConfirmationDialog = true
        } else {
            onDismiss()
        }
    }

    val currentPlayingTrack by AudioPlayerManager.currentTrack.collectAsStateWithLifecycle()
    val currentPlayingCues by AudioPlayerManager.subtitlesCues.collectAsStateWithLifecycle()

    val trackCues = remember(selectedTrack, currentPlayingTrack, currentPlayingCues) {
        val raw = if (selectedTrack == null) {
            emptyList<SubtitleCue>()
        } else if (selectedTrack.id == currentPlayingTrack?.id && currentPlayingCues.isNotEmpty()) {
            currentPlayingCues
        } else if (!selectedTrack.subtitleContent.isNullOrBlank()) {
            SubtitleParser.parseContent(selectedTrack.subtitleContent, selectedTrack.subtitleOffsetMs)
        } else if (!selectedTrack.subtitlePath.isNullOrBlank()) {
            SubtitleParser.parseFile(File(selectedTrack.subtitlePath), selectedTrack.subtitleOffsetMs)
        } else {
            val autoFile = SubtitleParser.findMatchingSubtitleFile(selectedTrack.filePath)
            if (autoFile != null) SubtitleParser.parseFile(autoFile, selectedTrack.subtitleOffsetMs) else emptyList()
        }
        if (selectedTrack?.isVirtualScene == true) {
            val sceneStart = selectedTrack.startOffsetMs
            val sceneEnd = selectedTrack.endOffsetMs ?: (selectedTrack.startOffsetMs + selectedTrack.duration)
            raw.filter { cue ->
                if (!cue.isTimed || cue.startMs < 0) true
                else {
                    val cueEnd = if (cue.endMs > cue.startMs) cue.endMs else cue.startMs + 5000L
                    cueEnd >= sceneStart && cue.startMs <= sceneEnd
                }
            }
        } else {
            raw
        }
    }

    var currentStartCueIndex by remember(selectedTrackId) { mutableStateOf<Int?>(null) }
    var currentEndCueIndex by remember(selectedTrackId) { mutableStateOf<Int?>(null) }

    var originStartMs by remember(initialNote, prefilledStartMs) {
        mutableStateOf<Long?>(
            initialNote?.originStartMs ?: if (prefilledStartMs > 0) prefilledStartMs else null
        )
    }

    val spannedCues = remember(trackCues, startMs, endMs) {
        if (trackCues.isEmpty()) emptyList()
        else {
            trackCues.filter { cue ->
                val cueEnd = if (cue.endMs > cue.startMs) cue.endMs else cue.startMs + 4000L
                cue.startMs < endMs && cueEnd > startMs
            }
        }
    }

    LaunchedEffect(trackCues, startMs, endMs, prefilledText) {
        if (currentStartCueIndex == null && trackCues.isNotEmpty()) {
            val trimmedNote = noteText.trim()
            val matchByText = if (trimmedNote.isNotEmpty()) {
                trackCues.indexOfFirst { it.text.trim().equals(trimmedNote, ignoreCase = true) }
            } else -1

            val matchByStart = if (matchByText < 0) {
                trackCues.indexOfFirst { Math.abs(it.startMs - startMs) <= 500 }
            } else matchByText

            val initialIdx = when {
                matchByText >= 0 -> matchByText
                matchByStart >= 0 -> matchByStart
                else -> {
                    val idx = trackCues.indexOfFirst { it.startMs >= startMs }
                    if (idx >= 0) idx else 0
                }
            }
            currentStartCueIndex = initialIdx
            currentEndCueIndex = initialIdx
            if (originStartMs == null && initialIdx in trackCues.indices) {
                originStartMs = trackCues[initialIdx].startMs
            }
        }
    }

    LaunchedEffect(spannedCues) {
        if (originStartMs == null && spannedCues.isNotEmpty()) {
            val dedicated = SubtitleParser.findDedicatedCueForNote(
                initialNote ?: Note(text = "", startTimestampMs = startMs, endTimestampMs = endMs),
                trackCues
            )
            originStartMs = dedicated?.startMs ?: spannedCues[0].startMs
        }
    }

    val minBound = remember(selectedTrack) {
        if (selectedTrack?.isVirtualScene == true) selectedTrack.startOffsetMs else 0L
    }

    val maxBound = remember(selectedTrack) {
        if (selectedTrack?.isVirtualScene == true) {
            selectedTrack.endOffsetMs ?: (selectedTrack.startOffsetMs + selectedTrack.duration)
        } else {
            selectedTrack?.duration ?: 3600000L
        }
    }

    val maxDuration = maxBound

    val canAddPrevious = remember(trackCues, currentStartCueIndex, startMs) {
        if (trackCues.isEmpty()) false
        else {
            val sIdx = currentStartCueIndex ?: trackCues.indexOfFirst { it.startMs >= startMs }.takeIf { it >= 0 } ?: 0
            sIdx > 0
        }
    }

    val canAddNext = remember(trackCues, currentEndCueIndex, endMs) {
        if (trackCues.isEmpty()) false
        else {
            val eIdx = currentEndCueIndex ?: trackCues.indexOfLast { it.startMs <= endMs }.takeIf { it >= 0 } ?: 0
            eIdx < trackCues.size - 1
        }
    }

    val handleAddPreviousCue: () -> Unit = {
        if (trackCues.isNotEmpty()) {
            val sIdx = currentStartCueIndex ?: trackCues.indexOfFirst { it.startMs >= startMs }.takeIf { it >= 0 } ?: 0
            val targetIdx = sIdx - 1
            if (targetIdx in trackCues.indices) {
                val prevCue = trackCues[targetIdx]
                currentStartCueIndex = targetIdx
                if (currentEndCueIndex == null) {
                    currentEndCueIndex = sIdx
                }
                // Update start timestamp to encompass the earlier segment
                startMs = prevCue.startMs.coerceAtLeast(minBound)
                if (endMs < startMs + 500L) {
                    endMs = (startMs + 1000L).coerceAtMost(maxBound)
                }
                // Prepend previous cue text
                val prevText = prevCue.text.trim()
                val currText = noteText.trim()
                noteText = if (currText.isEmpty()) prevText else "$prevText $currText"
                Toast.makeText(context, Loc.getText("added_previous_cue"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val handleAddNextCue: () -> Unit = {
        if (trackCues.isNotEmpty()) {
            val eIdx = currentEndCueIndex ?: trackCues.indexOfLast { it.startMs <= endMs || it.endMs <= endMs }.takeIf { it >= 0 } ?: (currentStartCueIndex ?: 0)
            val targetIdx = eIdx + 1
            if (targetIdx in trackCues.indices) {
                val nextCue = trackCues[targetIdx]
                currentEndCueIndex = targetIdx
                if (currentStartCueIndex == null) {
                    currentStartCueIndex = eIdx
                }
                // Update end timestamp to encompass the later segment
                val newEnd = if (nextCue.endMs > nextCue.startMs) nextCue.endMs else nextCue.startMs + 4000L
                endMs = newEnd.coerceAtMost(maxBound)
                if (startMs > endMs) {
                    startMs = nextCue.startMs.coerceAtLeast(minBound)
                }
                // Append next cue text
                val nextText = nextCue.text.trim()
                val currText = noteText.trim()
                noteText = if (currText.isEmpty()) nextText else "$currText $nextText"
                Toast.makeText(context, Loc.getText("added_next_cue"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val triggerAiExplanationGeneration = {
        if (!isGeneratingAiExplanation) {
            val currentPrompt = commentText.trim()
            if (noteText.isBlank() && currentPrompt.isBlank()) {
                val msg = if (Loc.currentLanguage == "ar") "يرجى كتابة كلمة أو تحديد مقطع صوتي أولاً" else "Please enter a word or select an audio quote first"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            } else {
                isGeneratingAiExplanation = true
                coroutineScope.launch {
                    val result = viewModel.generateNoteExplanation(
                        quoteText = noteText,
                        promptOrInstruction = currentPrompt,
                        trackTitle = selectedTrack?.fileName
                    )
                    isGeneratingAiExplanation = false
                    result.fold(
                        onSuccess = { generatedExplanation ->
                            commentText = generatedExplanation
                            Toast.makeText(context, Loc.getText("ai_generate_note_success"), Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { err ->
                            val msg = if (err.message == "MISSING_API_KEY") {
                                Loc.getText("missing_api_key_prompt")
                            } else {
                                err.localizedMessage ?: "Failed to generate"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = handleDismissAttempt,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { targetValue ->
                if (targetValue == SheetValue.Hidden && commentText.isNotBlank()) {
                    showDiscardConfirmationDialog = true
                    false
                } else {
                    true
                }
            }
        ),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
                .imePadding()
        ) {
            // Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (initialNote != null) Loc.getText("edit_note") else Loc.getText("add_note"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = handleDismissAttempt) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Note / Quote Text Field with Previous & Next Cue Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Loc.getText("note_text"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (trackCues.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Previous Cue Button
                        OutlinedButton(
                            onClick = handleAddPreviousCue,
                            enabled = canAddPrevious,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(30.dp)
                                .testTag("add_prev_cue_button"),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = Loc.getText("add_previous_cue"),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = Loc.getText("add_previous_cue"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Next Cue Button
                        OutlinedButton(
                            onClick = handleAddNextCue,
                            enabled = canAddNext,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(30.dp)
                                .testTag("add_next_cue_button"),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = Loc.getText("add_next_cue"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = Loc.getText("add_next_cue"),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Note / Quote Text Field
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                placeholder = { Text(Loc.getText("note_text_placeholder")) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("note_text_input"),
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp)
            )

            // Dedicated Subtitle Cue Selector (when a note covers multiple cues)
            if (spannedCues.size > 1) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bookmark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = Loc.getText("dedicated_cue"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = Loc.getText("dedicated_cue_desc"),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(spannedCues) { cue ->
                            val isSelected = originStartMs != null && (
                                originStartMs == cue.startMs || (
                                    originStartMs!! in cue.startMs..(if (cue.endMs > cue.startMs) cue.endMs else cue.startMs + 4000L)
                                )
                            )
                            FilterChip(
                                selected = isSelected,
                                onClick = { originStartMs = cue.startMs },
                                label = {
                                    Text(
                                        text = "${cue.text.take(22)} (${formatDuration(cue.startMs)})",
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else null
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Explanation / Comment Field
            Text(
                text = Loc.getText("note_comment"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Explanation / Comment Field (Prompt input or AI output)
            OutlinedTextField(
                value = commentText,
                onValueChange = { commentText = it },
                label = { Text(Loc.getText("note_comment")) },
                placeholder = { Text(Loc.getText("ai_note_comment_placeholder"), fontSize = 12.5.sp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("note_comment_input"),
                minLines = 2,
                maxLines = 5,
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    IconButton(
                        onClick = { triggerAiExplanationGeneration() },
                        enabled = !isGeneratingAiExplanation,
                        modifier = Modifier.testTag("ai_generate_note_button")
                    ) {
                        if (isGeneratingAiExplanation) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = Loc.getText("ai_generate_note_button"),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Audio Segment Time Adjuster (if track is selected)
            if (selectedTrack != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Header: Track name + Play / Unlink buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = getTrackFileIcon(selectedTrack),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = selectedTrack.fileName,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val clipDurationSec = ((endMs - startMs) / 1000).coerceAtLeast(1)
                                FilledTonalButton(
                                    onClick = {
                                        if (isCurrentSnippetPlaying) {
                                            NoteAudioPlayer.stop()
                                        } else {
                                            NoteAudioPlayer.playSnippet(context, selectedTrack.filePath, -1L, startMs, endMs)
                                        }
                                    },
                                    colors = if (isCurrentSnippetPlaying) {
                                        ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else {
                                        ButtonDefaults.filledTonalButtonColors()
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isCurrentSnippetPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (isCurrentSnippetPlaying) "Pause" else "Play",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("${clipDurationSec}s", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                TextButton(
                                    onClick = { selectedTrackId = null },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(Loc.getText("unlink_audio"), fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )

                        // Start Time Section (Single Row with -1s, Timestamp, +1s)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AccessTime,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = Loc.getText("start_time"),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        startMs = (startMs - 1000L).coerceAtLeast(minBound)
                                        if (endMs < startMs + 500L) endMs = (startMs + 1000L).coerceAtMost(maxBound)
                                        if (isCurrentSnippetPlaying) {
                                            NoteAudioPlayer.playSnippet(context, selectedTrack.filePath, -1L, startMs, endMs)
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("-1s", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                                ) {
                                    Text(
                                        text = formatDuration(startMs),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }

                                FilledTonalButton(
                                    onClick = {
                                        startMs = (startMs + 1000L).coerceAtMost(maxBound)
                                        if (endMs < startMs + 500L) endMs = (startMs + 1000L).coerceAtMost(maxBound)
                                        if (isCurrentSnippetPlaying) {
                                            NoteAudioPlayer.playSnippet(context, selectedTrack.filePath, -1L, startMs, endMs)
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("+1s", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        // End Time Section (Single Row with -1s, Timestamp, +1s)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AccessTimeFilled,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = Loc.getText("end_time"),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        endMs = (endMs - 1000L).coerceAtLeast(startMs + 500L)
                                        if (isCurrentSnippetPlaying) {
                                            NoteAudioPlayer.playSnippet(context, selectedTrack.filePath, -1L, startMs, endMs)
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("-1s", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                                ) {
                                    Text(
                                        text = formatDuration(endMs),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }

                                FilledTonalButton(
                                    onClick = {
                                        endMs = (endMs + 1000L).coerceAtMost(maxBound)
                                        if (isCurrentSnippetPlaying) {
                                            NoteAudioPlayer.playSnippet(context, selectedTrack.filePath, -1L, startMs, endMs)
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("+1s", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            } else if (allTracks.isNotEmpty()) {
                // Option to link audio track
                val currentPlayingTrack = AudioPlayerManager.currentTrack.collectAsStateWithLifecycle().value
                if (currentPlayingTrack != null) {
                    FilledTonalButton(
                        onClick = {
                            selectedTrackId = currentPlayingTrack.id
                            val currentPos = if (currentPlayingTrack.isVirtualScene) {
                                currentPlayingTrack.startOffsetMs + AudioPlayerManager.currentPosition.value
                            } else {
                                AudioPlayerManager.currentPosition.value
                            }
                            val maxLimit = if (currentPlayingTrack.isVirtualScene) {
                                currentPlayingTrack.endOffsetMs ?: (currentPlayingTrack.startOffsetMs + currentPlayingTrack.duration)
                            } else {
                                currentPlayingTrack.duration
                            }
                            startMs = currentPos
                            endMs = (startMs + 5000L).coerceAtMost(maxLimit)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = getTrackFileIcon(currentPlayingTrack),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(Loc.getText("link_current_audio"), fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tags Section
            Text(
                text = Loc.getText("tags"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Add New Tag Button
                item {
                    AssistChip(
                        onClick = { isNewTagFieldVisible = !isNewTagFieldVisible },
                        label = { Text(Loc.getText("add_new_tag"), fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                }

                // Existing Tags suggestions (unique & active)
                availableTags.forEach { tagStr ->
                    item(key = tagStr) {
                        val isSelected = selectedTags.any { it.equals(tagStr, ignoreCase = true) }
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedTags = if (isSelected) {
                                    selectedTags.filterNot { it.equals(tagStr, ignoreCase = true) }.toSet()
                                } else {
                                    selectedTags + tagStr
                                }
                            },
                            label = { Text("#$tagStr", fontSize = 11.sp) }
                        )
                    }
                }
            }

            if (isNewTagFieldVisible) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newTagInput,
                        onValueChange = { newTagInput = it },
                        placeholder = { Text(Loc.getText("new_tag_name"), fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Button(
                        onClick = {
                            val trimmed = newTagInput.trim()
                            if (trimmed.isNotEmpty()) {
                                if (!isFavoriteTag(trimmed) && selectedTags.none { it.equals(trimmed, ignoreCase = true) }) {
                                    selectedTags = selectedTags + trimmed
                                }
                                newTagInput = ""
                                isNewTagFieldVisible = false
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(Loc.getText("add"))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Save Note Button
            Button(
                onClick = {
                    if (noteText.isBlank()) return@Button
                    if (NoteAudioPlayer.playingNoteId.value == -1L) {
                        NoteAudioPlayer.stop()
                    }
                    viewModel.saveNote(
                        id = initialNote?.id ?: 0L,
                        text = noteText,
                        comment = commentText,
                        trackId = selectedTrackId,
                        startTimestampMs = startMs,
                        endTimestampMs = endMs,
                        originStartMs = originStartMs ?: startMs,
                        tags = selectedTags.toList(),
                        onSuccess = onDismiss
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("save_note_button"),
                shape = RoundedCornerShape(12.dp),
                enabled = noteText.isNotBlank()
            ) {
                Icon(Icons.Filled.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(Loc.getText("save_note"), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }

    if (showDiscardConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmationDialog = false },
            title = {
                Text(
                    text = Loc.getText("discard_note_confirm_title"),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = Loc.getText("discard_note_confirm_msg"),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirmationDialog = false
                        onDismiss()
                    }
                ) {
                    Text(
                        text = Loc.getText("discard"),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDiscardConfirmationDialog = false }
                ) {
                    Text(text = Loc.getText("keep_editing"), fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}

// --- FOLDER HIERARCHY TREE HELPERS ---

data class FolderTreeNode(
    val folder: Folder,
    val children: List<FolderTreeNode> = emptyList(),
    val depth: Int = 0
)

fun buildFolderTree(folders: List<Folder>): List<FolderTreeNode> {
    if (folders.isEmpty()) return emptyList()
    val folderMap = folders.associateBy { it.id }
    val validIds = folderMap.keys

    val childrenMap = mutableMapOf<Long?, MutableList<Folder>>()
    for (folder in folders) {
        val parentId = if (folder.parentFolderId != null && validIds.contains(folder.parentFolderId) && folder.parentFolderId != folder.id) {
            folder.parentFolderId
        } else {
            // Path-based hierarchy fallback
            val pathParent = folders.filter { other ->
                other.id != folder.id && folder.folderPath.startsWith(other.folderPath + java.io.File.separator)
            }.maxByOrNull { it.folderPath.length }
            pathParent?.id
        }
        childrenMap.getOrPut(parentId) { mutableListOf() }.add(folder)
    }

    fun buildNodes(parentId: Long?, depth: Int): List<FolderTreeNode> {
        val list = childrenMap[parentId]?.sortedBy { it.folderName.lowercase() } ?: emptyList()
        return list.map { folder ->
            FolderTreeNode(
                folder = folder,
                children = buildNodes(folder.id, depth + 1),
                depth = depth
            )
        }
    }

    val roots = buildNodes(null, 0)
    val includedIds = mutableSetOf<Long>()
    fun collectIncluded(nodes: List<FolderTreeNode>) {
        nodes.forEach {
            includedIds.add(it.folder.id)
            collectIncluded(it.children)
        }
    }
    collectIncluded(roots)

    val remaining = folders.filterNot { includedIds.contains(it.id) }.map {
        FolderTreeNode(folder = it, children = emptyList(), depth = 0)
    }

    return roots + remaining
}

fun flattenFolderTree(
    nodes: List<FolderTreeNode>,
    expandedIds: Set<Long>,
    result: MutableList<FolderTreeNode> = mutableListOf()
): List<FolderTreeNode> {
    for (node in nodes) {
        result.add(node)
        if (node.children.isNotEmpty() && expandedIds.contains(node.folder.id)) {
            flattenFolderTree(node.children, expandedIds, result)
        }
    }
    return result
}

fun getFolderNotesCount(node: FolderTreeNode, notes: List<Note>): Int {
    var count = notes.count { it.folderId == node.folder.id }
    for (child in node.children) {
        count += getFolderNotesCount(child, notes)
    }
    return count
}

fun getAllNodeIds(node: FolderTreeNode): Set<Long> {
    val set = mutableSetOf(node.folder.id)
    for (child in node.children) {
        set.addAll(getAllNodeIds(child))
    }
    return set
}

