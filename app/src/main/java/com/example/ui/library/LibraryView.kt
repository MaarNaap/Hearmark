package com.example.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AudioTrack
import kotlinx.coroutines.launch

// --- SUB-SCREEN 2: LIBRARY VIEW (Folder / Independent / Playlists) ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryView(
    viewModel: AppViewModel,
    onFolderDetailsClicked: (Long) -> Unit,
    onPlaylistDetailsClicked: (Long) -> Unit,
    onCreateTaskForSource: (String, Long?) -> Unit,
    onShowAssociatedTasks: (String, Long, String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(0) } // 0=Folders, 1=Independent Tracks, 2=Playlists
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    LaunchedEffect(pagerState.currentPage) {
        activeTab = pagerState.currentPage
    }
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val independentTracks by viewModel.independentTracks.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val selectedTrackIdsSet by viewModel.selectedTrackIds.collectAsStateWithLifecycle()
    val lastImportSummary by viewModel.lastImportSummary.collectAsStateWithLifecycle()

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistInputName by remember { mutableStateOf("") }

    // Bulk select modes
    var isBulkSelectMode by remember { mutableStateOf(false) }
    var isFolderBulkSelectMode by remember { mutableStateOf(false) }
    var selectedFolderIds by remember { mutableStateOf(emptySet<Long>()) }

    var isPlaylistBulkSelectMode by remember { mutableStateOf(false) }
    var selectedPlaylistIds by remember { mutableStateOf(emptySet<Long>()) }

    // Global library search state
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }

    val filteredFolders = remember(folders, searchQuery) {
        if (searchQuery.isBlank()) folders else folders.filter { it.folderName.contains(searchQuery, ignoreCase = true) }
    }
    var tracksSortBy by remember { mutableStateOf("name") }
    var tracksIsAscending by remember { mutableStateOf(true) }

    val filteredIndependentTracks = remember(independentTracks, searchQuery, tracksSortBy, tracksIsAscending) {
        val filtered = if (searchQuery.isBlank()) independentTracks else independentTracks.filter {
            val name = it.getDisplayTitle()
            name.contains(searchQuery, ignoreCase = true) || it.fileName.contains(searchQuery, ignoreCase = true)
        }
        getSortedTracks(filtered, tracksSortBy, tracksIsAscending)
    }
    val filteredPlaylists = remember(playlists, searchQuery) {
        if (searchQuery.isBlank()) playlists else playlists.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    // Folder and tracks import control states
    var showImportFolderDialog by remember { mutableStateOf(false) }
    var importFolderNameInput by remember { mutableStateOf("") }
    var showAddToPlaylistDialogForTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var queuedFoldersToImport by remember { mutableStateOf<List<Pair<String, Uri>>>(emptyList()) }

    val importTracksLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importIndependentTracks(uris)
        }
    }

    val importFolderFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importFolder(importFolderNameInput.ifBlank { "Imported Files" }, uris)
            importFolderNameInput = ""
            showImportFolderDialog = false
        }
    }

    val importFolderTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val resolvedName = viewModel.getDirNameFromTreeUri(context, uri)
            queuedFoldersToImport = queuedFoldersToImport + Pair(resolvedName, uri)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LibraryTopSearchAndTabs(
            activeTab = activeTab,
            searchQuery = searchQuery,
            isSearchExpanded = isSearchExpanded,
            onSelectTab = { index -> coroutineScope.launch { pagerState.animateScrollToPage(index) } },
            onSearchQueryChange = { searchQuery = it },
            onSearchExpandedChange = { isSearchExpanded = it }
        )

        if (isBulkSelectMode && activeTab == 1) {
            LibraryTracksBulkToolbar(
                selectedTrackIdsSet = selectedTrackIdsSet,
                filteredIndependentTracks = filteredIndependentTracks,
                independentTracks = independentTracks,
                viewModel = viewModel,
                onAddToPlaylistRequest = { showAddToPlaylistDialogForTrack = it },
                onExitBulkMode = { isBulkSelectMode = false }
            )
        }

        if (isFolderBulkSelectMode && activeTab == 0) {
            LibraryFoldersBulkToolbar(
                selectedFolderIds = selectedFolderIds,
                filteredFolders = filteredFolders,
                context = context,
                viewModel = viewModel,
                onSelectedFolderIdsChange = { selectedFolderIds = it },
                onExitBulkMode = { isFolderBulkSelectMode = false }
            )
        }

        if (isPlaylistBulkSelectMode && activeTab == 2) {
            LibraryPlaylistsBulkToolbar(
                selectedPlaylistIds = selectedPlaylistIds,
                filteredPlaylists = filteredPlaylists,
                context = context,
                viewModel = viewModel,
                onSelectedPlaylistIdsChange = { selectedPlaylistIds = it },
                onExitBulkMode = { isPlaylistBulkSelectMode = false }
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> LibraryFoldersPage(
                        searchQuery = searchQuery,
                        filteredFolders = filteredFolders,
                        isFolderBulkSelectMode = isFolderBulkSelectMode,
                        selectedFolderIds = selectedFolderIds,
                        viewModel = viewModel,
                        onToggleSelectFolder = { id ->
                            selectedFolderIds = if (selectedFolderIds.contains(id)) selectedFolderIds - id else selectedFolderIds + id
                        },
                        onEnterBulkSelectForFolder = { id ->
                            isFolderBulkSelectMode = true
                            selectedFolderIds = selectedFolderIds + id
                        },
                        onFolderDetailsClicked = onFolderDetailsClicked,
                        onCreateTaskForSource = onCreateTaskForSource,
                        onShowAssociatedTasks = onShowAssociatedTasks
                    )
                    1 -> LibraryTracksPage(
                        filteredIndependentTracks = filteredIndependentTracks,
                        selectedTrackIdsSet = selectedTrackIdsSet,
                        isBulkSelectMode = isBulkSelectMode,
                        tracksSortBy = tracksSortBy,
                        tracksIsAscending = tracksIsAscending,
                        viewModel = viewModel,
                        onSortByChange = { tracksSortBy = it },
                        onIsAscendingChange = { tracksIsAscending = it },
                        onTrackPlaylistMenuClicked = { showAddToPlaylistDialogForTrack = it },
                        onCreateTaskForSource = onCreateTaskForSource,
                        onShowAssociatedTasks = onShowAssociatedTasks,
                        onEnterBulkSelectMode = { isBulkSelectMode = true }
                    )
                    2 -> LibraryPlaylistsPage(
                        filteredPlaylists = filteredPlaylists,
                        selectedPlaylistIds = selectedPlaylistIds,
                        isPlaylistBulkSelectMode = isPlaylistBulkSelectMode,
                        context = context,
                        viewModel = viewModel,
                        onToggleSelectPlaylist = { id ->
                            selectedPlaylistIds = if (selectedPlaylistIds.contains(id)) selectedPlaylistIds - id else selectedPlaylistIds + id
                        },
                        onEnterBulkSelectForPlaylist = { id ->
                            isPlaylistBulkSelectMode = true
                            selectedPlaylistIds = selectedPlaylistIds + id
                        },
                        onPlaylistDetailsClicked = onPlaylistDetailsClicked,
                        onCreateTaskForSource = onCreateTaskForSource,
                        onShowAssociatedTasks = onShowAssociatedTasks
                    )
                }
            }

            // Bottom-right Floating Action Button, context-aware and elegant
            FloatingActionButton(
                onClick = {
                    when (activeTab) {
                        0 -> { showImportFolderDialog = true }
                        1 -> { importTracksLauncher.launch("audio/*") }
                        2 -> { showCreatePlaylistDialog = true }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .testTag(
                        when (activeTab) {
                            0 -> "btn_import_folder"
                            1 -> "btn_import_tracks"
                            else -> "btn_create_playlist"
                        }
                    ),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = when (activeTab) {
                        0 -> Icons.Filled.Folder
                        1 -> Icons.Filled.Audiotrack
                        else -> Icons.AutoMirrored.Filled.PlaylistAdd
                    },
                    contentDescription = when (activeTab) {
                        0 -> Loc.getText("add_folder")
                        1 -> Loc.getText("add_individual_track")
                        else -> Loc.getText("add_playlist")
                    },
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    if (showImportFolderDialog) {
        LibraryImportFolderDialog(
            queuedFoldersToImport = queuedFoldersToImport,
            importFolderNameInput = importFolderNameInput,
            context = context,
            viewModel = viewModel,
            onQueuedFoldersChange = { queuedFoldersToImport = it },
            onImportFolderNameInputChange = { importFolderNameInput = it },
            onLaunchFolderTreePicker = { importFolderTreeLauncher.launch(null) },
            onDismiss = {
                showImportFolderDialog = false
                queuedFoldersToImport = emptyList()
            }
        )
    }

    lastImportSummary?.let { summary ->
        LibraryImportSummaryDialog(
            summary = summary,
            onDismiss = { viewModel.clearImportSummary() }
        )
    }

    if (showAddToPlaylistDialogForTrack != null) {
        AddToPlaylistDialog(
            track = showAddToPlaylistDialogForTrack!!,
            playlists = playlists,
            onDismiss = { showAddToPlaylistDialogForTrack = null },
            onPlaylistSelected = { playlist ->
                viewModel.addTrackToPlaylist(playlist.id, showAddToPlaylistDialogForTrack!!.id)
                Toast.makeText(context, String.format(Loc.getText("added_to_playlist_success"), playlist.name), Toast.LENGTH_SHORT).show()
            },
            onCreatePlaylistClicked = {
                showCreatePlaylistDialog = true
            }
        )
    }

    if (showCreatePlaylistDialog) {
        LibraryCreatePlaylistDialog(
            playlistInputName = playlistInputName,
            onPlaylistInputNameChange = { playlistInputName = it },
            onConfirmCreate = {
                if (playlistInputName.isNotEmpty()) {
                    viewModel.createPlaylist(playlistInputName)
                    playlistInputName = ""
                    showCreatePlaylistDialog = false
                }
            },
            onDismiss = { showCreatePlaylistDialog = false }
        )
    }
}
