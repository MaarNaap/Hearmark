package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AudioTrack
import com.example.data.Note
import com.example.player.NoteAudioPlayer

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
    var noteToRelink by remember { mutableStateOf<Note?>(null) }

    val context = LocalContext.current

    val filteredNotes = remember(notes, searchQuery, selectedFolderIds, selectedTags, selectedTrackId, folders, tracks) {
        filterNotes(
            notes = notes,
            searchQuery = searchQuery,
            selectedFolderIds = selectedFolderIds,
            selectedTags = selectedTags,
            selectedTrackId = selectedTrackId,
            folders = folders,
            tracks = tracks
        )
    }

    val allTagsList = remember(notes) {
        extractAllNotebookTags(notes)
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
            NotebookTopBar(
                searchQuery = searchQuery,
                isSearchExpanded = isSearchExpanded,
                selectedFolderCount = selectedFolderIds.size,
                selectedTagCount = selectedTags.size,
                onSearchQueryChange = { searchQuery = it },
                onSearchExpandedChange = { isSearchExpanded = it },
                onOpenFolderFilter = { showFolderFilterDialog = true },
                onOpenTagFilter = { showTagFilterDialog = true },
                onAddNoteClicked = onAddNoteClicked
            )

            NotebookFilterChips(
                selectedFolderCount = selectedFolderIds.size,
                selectedTagCount = selectedTags.size,
                onOpenFolderFilter = { showFolderFilterDialog = true },
                onClearFolderFilter = { selectedFolderIds = emptySet() },
                onOpenTagFilter = { showTagFilterDialog = true },
                onClearTagFilter = { selectedTags = emptySet() }
            )

            Spacer(modifier = Modifier.height(6.dp))

            NotebookNotesList(
                filteredNotes = filteredNotes,
                isSnippetPlaying = isSnippetPlaying,
                playingSnippetNoteId = playingSnippetNoteId,
                snippetPosition = snippetPosition,
                noteQuestionCounts = noteQuestionCounts,
                tracks = tracks,
                context = context,
                viewModel = viewModel,
                onAddNoteClicked = onAddNoteClicked,
                onViewNote = { viewingNoteTarget = it },
                onEditNoteClicked = onEditNoteClicked,
                onDeleteNote = { noteToDelete = it },
                onRelinkNote = { noteToRelink = it }
            )
        }
    }

    viewingNoteTarget?.let { targetNote ->
        NoteFullViewDialog(
            targetNote = targetNote,
            notes = notes,
            isSnippetPlaying = isSnippetPlaying,
            playingSnippetNoteId = playingSnippetNoteId,
            snippetPosition = snippetPosition,
            noteQuestionCounts = noteQuestionCounts,
            tracks = tracks,
            context = context,
            viewModel = viewModel,
            onPlayTrackInMainPlayer = onPlayTrackInMainPlayer,
            onNavigateToFolder = onNavigateToFolder,
            onNavigateToTrack = onNavigateToTrack,
            onSelectTagFilter = { tag -> selectedTags = setOf(tag) },
            onEditNoteClicked = onEditNoteClicked,
            onRequestDelete = { noteToDelete = it },
            onDismiss = { viewingNoteTarget = null }
        )
    }

    noteToRelink?.let { targetNote ->
        com.example.ui.dialogs.TrackPickerForNoteRelinkDialog(
            tracks = tracks,
            noteTitle = targetNote.text,
            onTrackSelected = { selectedTrack ->
                noteToRelink = null
                viewModel.relinkNoteToTrack(targetNote, selectedTrack)
            },
            onDismiss = { noteToRelink = null }
        )
    }

    noteToDelete?.let { target ->
        NoteDeleteConfirmationDialog(
            noteToDelete = target,
            onConfirmDelete = { viewModel.deleteNote(it) },
            onDismiss = { noteToDelete = null }
        )
    }

    if (showFolderFilterDialog) {
        NotebookFolderFilterDialog(
            folders = folders,
            notes = notes,
            selectedFolderIds = selectedFolderIds,
            onApplyFilter = {
                selectedFolderIds = it
                showFolderFilterDialog = false
            },
            onDismiss = { showFolderFilterDialog = false }
        )
    }

    if (showTagFilterDialog) {
        NotebookTagFilterDialog(
            allTagsList = allTagsList,
            notes = notes,
            selectedTags = selectedTags,
            onApplyFilter = {
                selectedTags = it
                showTagFilterDialog = false
            },
            onDismiss = { showTagFilterDialog = false }
        )
    }
}
