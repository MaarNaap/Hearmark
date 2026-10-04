package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.AudioTrack
import com.example.data.Folder
import com.example.data.Playlist
import com.example.player.AudioPlayerManager

@Composable
fun LibraryFoldersPage(
    searchQuery: String,
    filteredFolders: List<Folder>,
    isFolderBulkSelectMode: Boolean,
    selectedFolderIds: Set<Long>,
    viewModel: AppViewModel,
    onToggleSelectFolder: (Long) -> Unit,
    onEnterBulkSelectForFolder: (Long) -> Unit,
    onFolderDetailsClicked: (Long) -> Unit,
    onCreateTaskForSource: (String, Long?) -> Unit,
    onShowAssociatedTasks: (String, Long, String) -> Unit
) {
    var folderToRename by remember { mutableStateOf<Folder?>(null) }

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
                        onToggleSelectFolder = onToggleSelectFolder,
                        onFolderDetailsClicked = onFolderDetailsClicked,
                        onLongClickFolder = onEnterBulkSelectForFolder,
                        onRenameFolder = { f -> folderToRename = f },
                        onCreateTaskForSource = onCreateTaskForSource,
                        onShowAssociatedTasks = onShowAssociatedTasks
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryTracksPage(
    filteredIndependentTracks: List<AudioTrack>,
    selectedTrackIdsSet: Set<Long>,
    isBulkSelectMode: Boolean,
    tracksSortBy: String,
    tracksIsAscending: Boolean,
    viewModel: AppViewModel,
    onSortByChange: (String) -> Unit,
    onIsAscendingChange: (Boolean) -> Unit,
    onTrackPlaylistMenuClicked: (AudioTrack) -> Unit,
    onCreateTaskForSource: (String, Long?) -> Unit,
    onShowAssociatedTasks: (String, Long, String) -> Unit,
    onEnterBulkSelectMode: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (filteredIndependentTracks.isEmpty()) {
            Box(modifier = Modifier.weight(1f)) {
                EmptyLibraryState("empty_tracks_desc")
            }
        } else {
            TrackListSortHeader(
                totalCount = filteredIndependentTracks.size,
                sortBy = tracksSortBy,
                onSortByChange = onSortByChange,
                isAscending = tracksIsAscending,
                onIsAscendingChange = onIsAscendingChange
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredIndependentTracks, key = { it.id }) { track ->
                    val isSelected = selectedTrackIdsSet.contains(track.id)
                    val isCurrentExecuting = AudioPlayerManager.currentTrack.collectAsStateWithLifecycle().value?.id == track.id
                    val isPlaying = AudioPlayerManager.isPlaying.collectAsStateWithLifecycle().value

                    UnifiedAudioTrackRow(
                        track = track,
                        isCurrentExecuting = isCurrentExecuting,
                        isPlaying = isPlaying,
                        viewModel = viewModel,
                        onTrackPlaylistMenuClicked = onTrackPlaylistMenuClicked,
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
                            onEnterBulkSelectMode()
                            viewModel.toggleTrackSelection(track.id)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryPlaylistsPage(
    filteredPlaylists: List<Playlist>,
    selectedPlaylistIds: Set<Long>,
    isPlaylistBulkSelectMode: Boolean,
    context: Context,
    viewModel: AppViewModel,
    onToggleSelectPlaylist: (Long) -> Unit,
    onEnterBulkSelectForPlaylist: (Long) -> Unit,
    onPlaylistDetailsClicked: (Long) -> Unit,
    onCreateTaskForSource: (String, Long?) -> Unit,
    onShowAssociatedTasks: (String, Long, String) -> Unit
) {
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
                items(filteredPlaylists, key = { it.id }) { playlist ->
                    val tracksInPl by viewModel.repository.getTracksForPlaylistFlow(playlist.id).collectAsStateWithLifecycle(emptyList())
                    val isSelected = selectedPlaylistIds.contains(playlist.id)
                    var showMenu by remember { mutableStateOf(false) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (isPlaylistBulkSelectMode) {
                                        onToggleSelectPlaylist(playlist.id)
                                    } else {
                                        onPlaylistDetailsClicked(playlist.id)
                                    }
                                },
                                onLongClick = {
                                    onEnterBulkSelectForPlaylist(playlist.id)
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
                                    onCheckedChange = { onToggleSelectPlaylist(playlist.id) }
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
