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

// --- SUB-SCREEN 3: FOLDER DETAILS VIEW ---
@Composable
fun FolderDetailsView(
    folderId: Long,
    viewModel: AppViewModel,
    backPressed: () -> Unit,
    onCreateTaskForFolder: (String, Long) -> Unit,
    onShowAssociatedTasks: (String, Long, String) -> Unit
) {
    var currentFolderId by remember(folderId) { mutableStateOf(folderId) }
    var folderHistory by remember(folderId) { mutableStateOf(listOf(folderId)) }

    val handleBack = {
        if (folderHistory.size > 1) {
            val newHistory = folderHistory.dropLast(1)
            folderHistory = newHistory
            currentFolderId = newHistory.last()
        } else {
            backPressed()
        }
    }

    BackHandler(enabled = true) {
        handleBack()
    }

    var folderBreadcrumbs by remember { mutableStateOf<List<Folder>>(emptyList()) }
    var folderName by remember { mutableStateOf("") }

    LaunchedEffect(currentFolderId) {
        val list = mutableListOf<Folder>()
        var curr: Folder? = viewModel.repository.getFolderById(currentFolderId)
        while (curr != null) {
            list.add(0, curr)
            curr = curr.parentFolderId?.let { viewModel.repository.getFolderById(it) }
        }
        folderBreadcrumbs = list
        folderName = list.lastOrNull()?.folderName ?: ""
    }

    val subfoldersFlow = remember(currentFolderId) { viewModel.getSubfolders(currentFolderId) }
    val subfolders by subfoldersFlow.collectAsStateWithLifecycle(emptyList())

    val folderFlow = remember(currentFolderId) { viewModel.repository.getTracksForFolderFlow(currentFolderId) }
    val rawTracks by folderFlow.collectAsStateWithLifecycle(emptyList())

    var folderSortBy by remember { mutableStateOf("name") }
    var folderIsAscending by remember { mutableStateOf(true) }

    val tracks = remember(rawTracks, folderSortBy, folderIsAscending) {
        getSortedTracks(rawTracks, folderSortBy, folderIsAscending)
    }
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showAddToPlaylistDialogForTrack by remember { mutableStateOf<AudioTrack?>(null) }

    var showMenu by remember { mutableStateOf(false) }
    var showRenameFolderDialog by remember { mutableStateOf(false) }
    var showDeleteFolderConfirmDialog by remember { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<Folder?>(null) }

    var isBulkSelectMode by remember { mutableStateOf(false) }
    var selectedDetailTrackIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showPlaylistSelectDialogForBulk by remember { mutableStateOf(false) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }

    if (showBulkDeleteConfirm) {
        var noteCount by remember { mutableIntStateOf(0) }
        var compCount by remember { mutableIntStateOf(0) }
        var vocabCount by remember { mutableIntStateOf(0) }

        LaunchedEffect(selectedDetailTrackIds) {
            noteCount = viewModel.repository.getNoteCountForTracks(selectedDetailTrackIds)
            compCount = viewModel.repository.getComprehensionQuestionCountForTracks(selectedDetailTrackIds)
            vocabCount = viewModel.repository.getVocabQuestionCountForTracks(selectedDetailTrackIds)
        }

        com.example.ui.dialogs.ConfirmDeleteTrackDialog(
            title = Loc.getText("delete_tracks_bulk_confirm_title"),
            desc = Loc.getFormattedText("delete_tracks_bulk_confirm_desc", selectedDetailTrackIds.size),
            isScene = false,
            noteCount = noteCount,
            comprehensionCount = compCount,
            vocabCount = vocabCount,
            onConfirmDelete = { options ->
                viewModel.deleteTracksByIds(selectedDetailTrackIds, options)
                selectedDetailTrackIds = emptySet()
                isBulkSelectMode = false
                showBulkDeleteConfirm = false
            },
            onDismiss = { showBulkDeleteConfirm = false }
        )
    }

    folderToRename?.let { targetFolder ->
        var tempName by remember(targetFolder) { mutableStateOf(targetFolder.folderName) }
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
                        if (tempName.isNotBlank()) {
                            viewModel.editFolderName(targetFolder.id, tempName)
                            if (targetFolder.id == currentFolderId) {
                                folderName = tempName
                            }
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

    if (showRenameFolderDialog) {
        var tempName by remember { mutableStateOf(folderName) }
        AlertDialog(
            onDismissRequest = { showRenameFolderDialog = false },
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
                        if (tempName.isNotBlank()) {
                            viewModel.editFolderName(currentFolderId, tempName)
                            folderName = tempName
                        }
                        showRenameFolderDialog = false
                    }
                ) {
                    Text(Loc.getText("save_settings"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameFolderDialog = false }) {
                    Text(Loc.getText("cancel"))
                }
            }
        )
    }

    if (showPlaylistSelectDialogForBulk) {
        AlertDialog(
            onDismissRequest = { showPlaylistSelectDialogForBulk = false },
            title = { Text(Loc.getText("choose_playlist_dialog_title")) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (playlists.isEmpty()) {
                        Text(
                            text = Loc.getText("empty_playlists_desc"),
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(playlists, key = { it.id }) { playlist ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.addTracksToPlaylist(playlist.id, selectedDetailTrackIds.toList())
                                            Toast.makeText(
                                                context,
                                                Loc.getFormattedText("tracks_added_to_playlist_success", playlist.name),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            selectedDetailTrackIds = emptySet()
                                            isBulkSelectMode = false
                                            showPlaylistSelectDialogForBulk = false
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Audiotrack,
                                            contentDescription = "Playlist",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = playlist.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPlaylistSelectDialogForBulk = false }) {
                    Text(Loc.getText("cancel"))
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // App header bar
        if (isBulkSelectMode) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${Loc.getText("selected")}: ${selectedDetailTrackIds.size}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val areAllTracksSelected = tracks.isNotEmpty() && selectedDetailTrackIds.size == tracks.size
                        IconButton(onClick = {
                            if (areAllTracksSelected) {
                                selectedDetailTrackIds = emptySet()
                                isBulkSelectMode = false
                            } else {
                                selectedDetailTrackIds = tracks.map { it.id }.toSet()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.SelectAll,
                                contentDescription = Loc.getText("select_all"),
                                tint = if (areAllTracksSelected) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
                        IconButton(onClick = {
                            if (selectedDetailTrackIds.isNotEmpty()) {
                                showBulkDeleteConfirm = true
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete selection",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        IconButton(onClick = {
                            if (selectedDetailTrackIds.isNotEmpty()) {
                                val selectedTracksList = tracks.filter { selectedDetailTrackIds.contains(it.id) }
                                viewModel.quickAddTaskForTracks(selectedTracksList, Loc.getText("group_goal_task_title"), 3)
                                selectedDetailTrackIds = emptySet()
                                isBulkSelectMode = false
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Bookmark,
                                contentDescription = "Add goal to selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = {
                            if (selectedDetailTrackIds.isNotEmpty()) {
                                showPlaylistSelectDialogForBulk = true
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.AddToPhotos,
                                contentDescription = "Add to Playlist",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = {
                            isBulkSelectMode = false
                            selectedDetailTrackIds = emptySet()
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Exit bulk mode"
                            )
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { handleBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "More Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(Loc.getText("play_next")) },
                            onClick = {
                                showMenu = false
                                viewModel.addTracksToPlayNext(tracks)
                                Toast.makeText(context, Loc.getText("added_to_queue"), Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(Loc.getText("edit_folder_name")) },
                            onClick = {
                                showMenu = false
                                showRenameFolderDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(Loc.getText("create_task")) },
                            onClick = {
                                showMenu = false
                                onCreateTaskForFolder("FOLDER", currentFolderId)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(Loc.getText("show_associated_tasks")) },
                            onClick = {
                                showMenu = false
                                onShowAssociatedTasks("FOLDER", currentFolderId, folderName)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(Loc.getText("delete_history"), color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                showDeleteFolderConfirmDialog = true
                            }
                        )
                    }
                }
            }
        }

        if (showDeleteFolderConfirmDialog) {
            var noteCount by remember { mutableIntStateOf(0) }
            var compCount by remember { mutableIntStateOf(0) }
            var vocabCount by remember { mutableIntStateOf(0) }

            LaunchedEffect(currentFolderId) {
                val allFolders = viewModel.folders.value
                val subIds = viewModel.repository.getAllSubfolderIds(currentFolderId, allFolders)
                val allTracks = viewModel.tracks.value
                val folderTrackIds = allTracks.filter { it.parentFolderId in subIds }.map { it.id }.toSet()
                if (folderTrackIds.isNotEmpty()) {
                    noteCount = viewModel.repository.getNoteCountForTracks(folderTrackIds)
                    compCount = viewModel.repository.getComprehensionQuestionCountForTracks(folderTrackIds)
                    vocabCount = viewModel.repository.getVocabQuestionCountForTracks(folderTrackIds)
                }
            }

            com.example.ui.dialogs.ConfirmDeleteTrackDialog(
                title = Loc.getText("delete_folder_title"),
                desc = Loc.getText("delete_folder_confirm"),
                isScene = false,
                noteCount = noteCount,
                comprehensionCount = compCount,
                vocabCount = vocabCount,
                onConfirmDelete = { options ->
                    showDeleteFolderConfirmDialog = false
                    coroutineScope.launch {
                        viewModel.repository.deleteFolder(currentFolderId, options)
                    }
                    handleBack()
                },
                onDismiss = { showDeleteFolderConfirmDialog = false }
            )
        }

        if (!isBulkSelectMode) {
            // Windows-Explorer style Breadcrumb Navigation Bar
            if (folderBreadcrumbs.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(folderBreadcrumbs, key = { _, bFolder -> bFolder.id }) { index, bFolder ->
                        val isLast = index == folderBreadcrumbs.size - 1
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = bFolder.folderName,
                                fontSize = 14.sp,
                                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                                color = if (isLast) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable(!isLast) {
                                    val targetIndex = folderHistory.indexOf(bFolder.id)
                                    if (targetIndex >= 0) {
                                        folderHistory = folderHistory.take(targetIndex + 1)
                                        currentFolderId = bFolder.id
                                    } else {
                                        folderHistory = folderHistory + bFolder.id
                                        currentFolderId = bFolder.id
                                    }
                                }
                            )
                            if (!isLast) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(18.dp).padding(horizontal = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        TrackListSortHeader(
            totalCount = tracks.size,
            sortBy = folderSortBy,
            onSortByChange = { folderSortBy = it },
            isAscending = folderIsAscending,
            onIsAscendingChange = { folderIsAscending = it },
            showCount = subfolders.isEmpty()
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. SUBFOLDERS SECTION
            if (subfolders.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${Loc.getText("folders")} (${subfolders.size})",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                items(subfolders, key = { "folder_${it.id}" }) { sub ->
                    SubfolderDetailsItem(
                        sub = sub,
                        depth = 0,
                        viewModel = viewModel,
                        onNavigate = { targetId ->
                            folderHistory = folderHistory + targetId
                            currentFolderId = targetId
                        },
                        onRenameFolder = { targetFolder ->
                            folderToRename = targetFolder
                        },
                        onCreateTaskForSource = { source, id ->
                            onCreateTaskForFolder(source, id)
                        },
                        onShowAssociatedTasks = { source, id, name ->
                            onShowAssociatedTasks(source, id, name)
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            // 2. FILES SECTION HEADER (Only when subfolders exist to cleanly separate sections)
            if (tracks.isNotEmpty() && subfolders.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Audiotrack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Loc.getText("files"),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else if (tracks.isEmpty() && subfolders.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = Loc.getText("empty_folders_desc"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // 3. TRACKS LIST
            items(tracks, key = { it.id }) { track ->
                val isCurrentExecuting = AudioPlayerManager.currentTrack.collectAsStateWithLifecycle().value?.id == track.id
                val isPlaying = AudioPlayerManager.isPlaying.collectAsStateWithLifecycle().value

                UnifiedAudioTrackRow(
                    track = track,
                    isCurrentExecuting = isCurrentExecuting,
                    isPlaying = isPlaying,
                    viewModel = viewModel,
                    onTrackPlaylistMenuClicked = { showAddToPlaylistDialogForTrack = it },
                    onCreateTask = { type, id -> onCreateTaskForFolder(type, id) },
                    onShowAssociatedTasks = { type, id, name -> onShowAssociatedTasks(type, id, name) },
                    playlistTracks = tracks,
                    isSelected = selectedDetailTrackIds.contains(track.id),
                    isBulkSelectMode = isBulkSelectMode,
                    onClick = {
                        if (isBulkSelectMode) {
                            if (selectedDetailTrackIds.contains(track.id)) {
                                selectedDetailTrackIds = selectedDetailTrackIds - track.id
                                if (selectedDetailTrackIds.isEmpty()) {
                                    isBulkSelectMode = false
                                }
                            } else {
                                selectedDetailTrackIds = selectedDetailTrackIds + track.id
                            }
                        } else {
                            viewModel.selectAndPlay(track, tracks)
                        }
                    },
                    onLongClick = {
                        if (!isBulkSelectMode) {
                            isBulkSelectMode = true
                            selectedDetailTrackIds = setOf(track.id)
                        } else {
                            if (selectedDetailTrackIds.contains(track.id)) {
                                selectedDetailTrackIds = selectedDetailTrackIds - track.id
                                if (selectedDetailTrackIds.isEmpty()) {
                                    isBulkSelectMode = false
                                }
                            } else {
                                selectedDetailTrackIds = selectedDetailTrackIds + track.id
                            }
                        }
                    }
                )
            }
        }

        if (showAddToPlaylistDialogForTrack != null) {
            AddToPlaylistDialog(
                track = showAddToPlaylistDialogForTrack!!,
                playlists = playlists,
                onDismiss = { showAddToPlaylistDialogForTrack = null },
                onPlaylistSelected = { playlist ->
                    viewModel.addTrackToPlaylist(playlist.id, showAddToPlaylistDialogForTrack!!.id)
                    Toast.makeText(context, Loc.getFormattedText("added_to_playlist_success", playlist.name), Toast.LENGTH_SHORT).show()
                },
                onCreatePlaylistClicked = {
                    // Done on main screen or prompt Toast
                }
            )
        }
    }
}
