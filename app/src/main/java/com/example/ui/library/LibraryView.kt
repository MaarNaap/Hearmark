package com.example.ui

import android.app.TimePickerDialog
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.roundToInt
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.delay
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import android.content.pm.ActivityInfo
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.WindowManager
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.RectangleShape
import com.example.data.*
import com.example.player.*
import com.example.ui.theme.*
import com.example.R
import com.example.util.AudioMetadataExtractor
import com.example.util.TrackMetadata
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
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
        AnimatedContent(
            targetState = isSearchExpanded,
            label = "search_expansion_row",
            modifier = Modifier.fillMaxWidth()
        ) { expanded ->
            if (expanded) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    IconButton(onClick = {
                        isSearchExpanded = false
                        searchQuery = ""
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Collapse search",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(Loc.getText("search_placeholder"), fontSize = 14.sp) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(20.dp))
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("global_library_search"),
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                        )
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        TabRow(
                            selectedTabIndex = activeTab,
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.primary,
                            indicator = { tabPositions ->
                                if (activeTab < tabPositions.size) {
                                    TabRowDefaults.SecondaryIndicator(
                                        Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                                        color = MaterialTheme.colorScheme.primary,
                                        height = 3.dp
                                    )
                                }
                            },
                            divider = {}
                        ) {
                            val tabs = listOf(
                                Pair("folders", 0),
                                Pair("independent_files", 1),
                                Pair("playlists", 2)
                            )
                            tabs.forEach { (key, index) ->
                                Tab(
                                    selected = activeTab == index,
                                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } }
                                ) {
                                    Text(
                                        text = Loc.getText(key),
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        fontWeight = if (activeTab == index) FontWeight.SemiBold else FontWeight.Normal,
                                        style = MaterialTheme.typography.bodyLarge.copy(letterSpacing = 0.15.sp),
                                        color = if (activeTab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { isSearchExpanded = true },
                        modifier = Modifier.testTag("search_icon")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

        // TOOLBAR SYSTEM - TRACKS (Tab 1)
        if (isBulkSelectMode && activeTab == 1) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${Loc.getText("selected")}: ${selectedTrackIdsSet.size}",
                        fontWeight = FontWeight.Bold
                    )
                    Row {
                        val areAllTracksSelected = filteredIndependentTracks.isNotEmpty() && selectedTrackIdsSet.size == filteredIndependentTracks.size
                        IconButton(onClick = {
                            if (areAllTracksSelected) {
                                viewModel.clearTrackSelections()
                            } else {
                                viewModel.selectedTrackIds.value = filteredIndependentTracks.map { it.id }.toSet()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.SelectAll,
                                contentDescription = Loc.getText("select_all"),
                                tint = if (areAllTracksSelected) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
                        IconButton(onClick = {
                            viewModel.deleteSelectedTracks()
                            isBulkSelectMode = false
                        }) {
                            Icon(Icons.Filled.Delete, "Delete selection", tint = MaterialTheme.colorScheme.error)
                        }
                        IconButton(onClick = {
                            if (selectedTrackIdsSet.isNotEmpty()) {
                                // Add unified task for selected tracks
                                val selectedTracksList = independentTracks.filter { selectedTrackIdsSet.contains(it.id) }
                                viewModel.quickAddTaskForTracks(selectedTracksList, Loc.getText("group_goal_task_title"), 3)
                                isBulkSelectMode = false
                                viewModel.clearTrackSelections()
                            }
                        }) {
                            Icon(Icons.Filled.Bookmark, "Add goal to selected", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = {
                            if (selectedTrackIdsSet.isNotEmpty()) {
                                val firstTrack = independentTracks.find { selectedTrackIdsSet.contains(it.id) }
                                if (firstTrack != null) {
                                    showAddToPlaylistDialogForTrack = firstTrack
                                }
                            }
                        }) {
                            Icon(Icons.Filled.AddToPhotos, "Add to Playlist", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = {
                            isBulkSelectMode = false
                            viewModel.clearTrackSelections()
                        }) {
                            Icon(Icons.Filled.Close, "Exit bulk mode")
                        }
                    }
                }
            }
        }

        // TOOLBAR SYSTEM - FOLDERS (Tab 0)
        if (isFolderBulkSelectMode && activeTab == 0) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${Loc.getText("selected")}: ${selectedFolderIds.size}",
                        fontWeight = FontWeight.Bold
                    )
                    Row {
                        val areAllFoldersSelected = filteredFolders.isNotEmpty() && selectedFolderIds.size == filteredFolders.size
                        IconButton(onClick = {
                            if (areAllFoldersSelected) {
                                selectedFolderIds = emptySet()
                            } else {
                                selectedFolderIds = filteredFolders.map { it.id }.toSet()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.SelectAll,
                                contentDescription = Loc.getText("select_all"),
                                tint = if (areAllFoldersSelected) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
                        IconButton(onClick = {
                            viewModel.deleteMultipleFolders(selectedFolderIds)
                            isFolderBulkSelectMode = false
                            selectedFolderIds = emptySet()
                        }) {
                            Icon(Icons.Filled.Delete, "Delete folders", tint = MaterialTheme.colorScheme.error)
                        }
                        IconButton(onClick = {
                            if (selectedFolderIds.isNotEmpty()) {
                                viewModel.bulkAddFoldersToTasks(selectedFolderIds)
                                isFolderBulkSelectMode = false
                                selectedFolderIds = emptySet()
                                Toast.makeText(context, Loc.getText("tasks_created_for_folders"), Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Filled.Bookmark, "Create tasks", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = {
                            isFolderBulkSelectMode = false
                            selectedFolderIds = emptySet()
                        }) {
                            Icon(Icons.Filled.Close, "Exit folder bulk mode")
                        }
                    }
                }
            }
        }

        // TOOLBAR SYSTEM - PLAYLISTS (Tab 2)
        if (isPlaylistBulkSelectMode && activeTab == 2) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${Loc.getText("selected")}: ${selectedPlaylistIds.size}",
                        fontWeight = FontWeight.Bold
                    )
                    Row {
                        val areAllPlaylistsSelected = filteredPlaylists.isNotEmpty() && selectedPlaylistIds.size == filteredPlaylists.size
                        IconButton(onClick = {
                            if (areAllPlaylistsSelected) {
                                selectedPlaylistIds = emptySet()
                            } else {
                                selectedPlaylistIds = filteredPlaylists.map { it.id }.toSet()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.SelectAll,
                                contentDescription = Loc.getText("select_all"),
                                tint = if (areAllPlaylistsSelected) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
                        IconButton(onClick = {
                            viewModel.deleteMultiplePlaylists(selectedPlaylistIds)
                            isPlaylistBulkSelectMode = false
                            selectedPlaylistIds = emptySet()
                        }) {
                            Icon(Icons.Filled.Delete, "Delete playlists", tint = MaterialTheme.colorScheme.error)
                        }
                        IconButton(onClick = {
                            if (selectedPlaylistIds.isNotEmpty()) {
                                viewModel.bulkAddPlaylistsToTasks(selectedPlaylistIds)
                                isPlaylistBulkSelectMode = false
                                selectedPlaylistIds = emptySet()
                                Toast.makeText(context, Loc.getText("tasks_created_for_playlists"), Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Filled.Bookmark, "Create tasks for playlists", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = {
                            isPlaylistBulkSelectMode = false
                            selectedPlaylistIds = emptySet()
                        }) {
                            Icon(Icons.Filled.Close, "Exit playlist bulk mode")
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    // folders
                    0 -> {
                    var folderToRename by remember { mutableStateOf<com.example.data.Folder?>(null) }
                    
                    if (folderToRename != null) {
                        var tempName by remember(folderToRename) { mutableStateOf(folderToRename!!.folderName) }
                        AlertDialog(
                            onDismissRequest = { folderToRename = null },
                            title = { Text(Loc.getText("edit_folder_name")) },
                            text = {
                                OutlinedTextField(
                                    value = tempName,
                                    onValueChange = { tempName = it },
                                    label = { Text(Loc.getText("folder_name_label")) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        val toRename = folderToRename
                                        if (toRename != null && tempName.isNotBlank()) {
                                            viewModel.editFolderName(toRename.id, tempName)
                                        }
                                        folderToRename = null
                                    }
                                ) {
                                    Text(Loc.getText("save_settings"))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { folderToRename = null }) {
                                    Text(Loc.getText("cancel"))
                                }
                            }
                        )
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        val rootFolders by viewModel.rootFolders.collectAsStateWithLifecycle()
                        val displayFolders = if (searchQuery.isBlank()) rootFolders else filteredFolders

                        if (displayFolders.isEmpty()) {
                            Box(modifier = Modifier.weight(1f)) {
                                EmptyLibraryState("empty_folders_desc")
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(displayFolders, key = { it.id }) { folder ->
                                    FolderTreeNodeItem(
                                        folder = folder,
                                        depth = 0,
                                        viewModel = viewModel,
                                        isFolderBulkSelectMode = isFolderBulkSelectMode,
                                        selectedFolderIds = selectedFolderIds,
                                        onToggleSelectFolder = { id ->
                                            selectedFolderIds = if (selectedFolderIds.contains(id)) selectedFolderIds - id else selectedFolderIds + id
                                        },
                                        onFolderDetailsClicked = onFolderDetailsClicked,
                                        onLongClickFolder = { id ->
                                            isFolderBulkSelectMode = true
                                            selectedFolderIds = selectedFolderIds + id
                                        },
                                        onRenameFolder = { f -> folderToRename = f },
                                        onCreateTaskForSource = onCreateTaskForSource,
                                        onShowAssociatedTasks = onShowAssociatedTasks
                                    )
                                }
                            }
                        }
                    }
                }

                // independent tracks
                1 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (filteredIndependentTracks.isEmpty()) {
                            Box(modifier = Modifier.weight(1f)) {
                                EmptyLibraryState("empty_tracks_desc")
                            }
                        } else {
                            TrackListSortHeader(
                                totalCount = filteredIndependentTracks.size,
                                sortBy = tracksSortBy,
                                onSortByChange = { tracksSortBy = it },
                                isAscending = tracksIsAscending,
                                onIsAscendingChange = { tracksIsAscending = it }
                            )
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredIndependentTracks) { track ->
                                    val isSelected = selectedTrackIdsSet.contains(track.id)
                                    val isCurrentExecuting = AudioPlayerManager.currentTrack.collectAsStateWithLifecycle().value?.id == track.id
                                    val isPlaying = AudioPlayerManager.isPlaying.collectAsStateWithLifecycle().value

                                    UnifiedAudioTrackRow(
                                        track = track,
                                        isCurrentExecuting = isCurrentExecuting,
                                        isPlaying = isPlaying,
                                        viewModel = viewModel,
                                        onTrackPlaylistMenuClicked = { showAddToPlaylistDialogForTrack = it },
                                        onCreateTask = { type, id -> onCreateTaskForSource(type, id) },
                                        onShowAssociatedTasks = { type, id, name -> onShowAssociatedTasks(type, id, name) },
                                        playlistTracks = filteredIndependentTracks,
                                        isSelected = isSelected,
                                        isBulkSelectMode = isBulkSelectMode,
                                        onClick = {
                                            if (isBulkSelectMode) {
                                                viewModel.toggleTrackSelection(track.id)
                                            } else {
                                                viewModel.selectAndPlay(track, filteredIndependentTracks)
                                            }
                                        },
                                        onLongClick = {
                                            isBulkSelectMode = true
                                            viewModel.toggleTrackSelection(track.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // playlists
                2 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (filteredPlaylists.isEmpty()) {
                            Box(modifier = Modifier.weight(1f)) {
                                EmptyLibraryState("empty_playlists_desc")
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredPlaylists) { playlist ->
                                    val tracksInPl by viewModel.repository.getTracksForPlaylistFlow(playlist.id).collectAsStateWithLifecycle(emptyList())
                                    val isSelected = selectedPlaylistIds.contains(playlist.id)
                                    var showMenu by remember { mutableStateOf(false) }

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = {
                                                    if (isPlaylistBulkSelectMode) {
                                                        selectedPlaylistIds = if (isSelected) selectedPlaylistIds - playlist.id else selectedPlaylistIds + playlist.id
                                                    } else {
                                                        onPlaylistDetailsClicked(playlist.id)
                                                    }
                                                },
                                                onLongClick = {
                                                    isPlaylistBulkSelectMode = true
                                                    selectedPlaylistIds = selectedPlaylistIds + playlist.id
                                                }
                                            ),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) {
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
                                             } else {
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                                             }
                                         ),
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 14.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (isPlaylistBulkSelectMode) {
                                                Checkbox(
                                                    checked = isSelected,
                                                    onCheckedChange = {
                                                        selectedPlaylistIds = if (isSelected) selectedPlaylistIds - playlist.id else selectedPlaylistIds + playlist.id
                                                    }
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(52.dp)
                                                        .background(
                                                            Brush.radialGradient(
                                                                listOf(
                                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
                                                                )
                                                            ),
                                                            RoundedCornerShape(16.dp)
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                 ) {
                                                     Icon(
                                                         imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                                                         contentDescription = "Playlist",
                                                         tint = MaterialTheme.colorScheme.primary,
                                                         modifier = Modifier.size(26.dp)
                                                     )
                                                 }
                                                 Spacer(modifier = Modifier.width(16.dp))
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                     text = playlist.name,
                                                     style = MaterialTheme.typography.bodyLarge.copy(
                                                         fontSize = 15.sp,
                                                         fontWeight = FontWeight.SemiBold,
                                                         fontFamily = FontFamily.SansSerif
                                                     ),
                                                     color = MaterialTheme.colorScheme.onSurface
                                                 )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                     text = String.format(Loc.getText("files_count"), tracksInPl.size),
                                                     style = MaterialTheme.typography.bodySmall,
                                                     color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                                 )
                                            }

                                            // Options menu
                                            if (!isPlaylistBulkSelectMode) {
                                                Box {
                                                    IconButton(
                                                        onClick = { showMenu = true },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Filled.MoreVert,
                                                            contentDescription = "options",
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                                        DropdownMenuItem(
                                                            text = { Text(Loc.getText("play_next")) },
                                                            onClick = {
                                                                showMenu = false
                                                                viewModel.addTracksToPlayNext(tracksInPl)
                                                                Toast.makeText(context, Loc.getText("added_to_queue"), Toast.LENGTH_SHORT).show()
                                                            }
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text(Loc.getText("create_task")) },
                                                            onClick = {
                                                                showMenu = false
                                                                onCreateTaskForSource("PLAYLIST", playlist.id)
                                                            }
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text(Loc.getText("show_associated_tasks")) },
                                                            onClick = {
                                                                showMenu = false
                                                                onShowAssociatedTasks("PLAYLIST", playlist.id, playlist.name)
                                                            }
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text(Loc.getText("delete_history"), color = MaterialTheme.colorScheme.error) },
                                                            onClick = {
                                                                showMenu = false
                                                                viewModel.deletePlaylist(playlist.id, false)
                                                            }
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
                        else -> Icons.Filled.PlaylistAdd
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

    // IMPORT FOLDER DIALOG (REPAIRED & FULLY NATIVE - SUPPORTING BULK MULTI-DIRECTORIES)
    if (showImportFolderDialog) {
        AlertDialog(
            onDismissRequest = { 
                showImportFolderDialog = false
                queuedFoldersToImport = emptyList()
            },
            title = { Text(Loc.getText("create_folder_dialog_title")) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (queuedFoldersToImport.isEmpty()) Loc.getText("create_folder_dialog_desc") 
                               else Loc.getText("folders_selected_for_bulk"),
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                    
                    if (queuedFoldersToImport.isNotEmpty()) {
                        queuedFoldersToImport.forEachIndexed { index, item ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = String.format(Loc.getText("folder_number"), index + 1),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        IconButton(
                                            modifier = Modifier.size(24.dp),
                                            onClick = {
                                                queuedFoldersToImport = queuedFoldersToImport.filterIndexed { i, _ -> i != index }
                                            }
                                        ) {
                                            Icon(Icons.Filled.Delete, "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = item.first,
                                        onValueChange = { newName ->
                                            queuedFoldersToImport = queuedFoldersToImport.mapIndexed { i, old ->
                                                if (i == index) Pair(newName, old.second) else old
                                            }
                                        },
                                        label = { Text(Loc.getText("folder_name_label")) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = importFolderNameInput,
                            onValueChange = { importFolderNameInput = it },
                            label = { Text(Loc.getText("folder_name_label") + " (" + Loc.getText("optional") + ")") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    Button(
                        onClick = { importFolderTreeLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                    ) {
                        Text(Loc.getText("add_another_dir"))
                    }
                }
            },
            confirmButton = {
                if (queuedFoldersToImport.isNotEmpty()) {
                    Button(onClick = {
                        for (item in queuedFoldersToImport) {
                            viewModel.importFolderFromTreeUri(item.first, item.second)
                        }
                        queuedFoldersToImport = emptyList()
                        showImportFolderDialog = false
                        Toast.makeText(context, Loc.getText("importing_folders_bg"), Toast.LENGTH_SHORT).show()
                    }) {
                        Text(String.format(Loc.getText("start_bulk_import"), queuedFoldersToImport.size))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showImportFolderDialog = false
                    queuedFoldersToImport = emptyList()
                }) {
                    Text(Loc.getText("cancel"))
                }
            }
        )
    }

    // IMPORT SUMMARY / LOG DIALOG
    lastImportSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = { viewModel.clearImportSummary() },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = "Import Summary Icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = Loc.getText("import_summary_title"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Folder name badge
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Folder,
                                contentDescription = "Folder Icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = summary.folderName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Stat rows
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = String.format(Loc.getText("total_processed_files"), summary.totalProcessed),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = String.format(Loc.getText("existing_files_preserved"), summary.existingCount),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = String.format(Loc.getText("newly_indexed_files"), summary.newlyIndexedCount),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Newly indexed files detail
                    if (summary.newlyIndexedCount > 0) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = Loc.getText("newly_indexed_heading"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            summary.newlyIndexedFiles.forEach { fileName ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Audiotrack,
                                        contentDescription = "New Audio File",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = fileName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "NEW",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .background(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // All are clean and up to date!
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Up-to-date Icon",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = Loc.getText("no_newly_indexed_files"),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearImportSummary() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(Loc.getText("close_dialog"))
                }
            }
        )
    }

    // ADD TO PLAYLIST DIALOG
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

    // CREATE PLAYLIST DIALOG
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text(Loc.getText("add_playlist")) },
            text = {
                OutlinedTextField(
                    value = playlistInputName,
                    onValueChange = { playlistInputName = it },
                    label = { Text(Loc.getText("playlist_name_label")) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (playlistInputName.isNotEmpty()) {
                        viewModel.createPlaylist(playlistInputName)
                        playlistInputName = ""
                        showCreatePlaylistDialog = false
                    }
                }) {
                    Text(Loc.getText("create"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) { Text(Loc.getText("cancel")) }
            }
        )
    }
}

@Composable
fun EmptyLibraryState(messageKey: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_logo),
                contentDescription = "Empty Library Logo",
                modifier = Modifier
                    .size(80.dp)
                    .padding(bottom = 8.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = Loc.getText(messageKey),
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
