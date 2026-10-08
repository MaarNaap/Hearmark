package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AudioTrack
import com.example.data.Folder
import com.example.data.Playlist

@Composable
fun LibraryTopSearchAndTabs(
    activeTab: Int,
    searchQuery: String,
    isSearchExpanded: Boolean,
    onSelectTab: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit
) {
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
                    onSearchExpandedChange(false)
                    onSearchQueryChange("")
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
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text(Loc.getText("search_placeholder"), fontSize = 14.sp) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
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
                                onClick = { onSelectTab(index) }
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
                    onClick = { onSearchExpandedChange(true) },
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
}

@Composable
fun LibraryTracksBulkToolbar(
    selectedTrackIdsSet: Set<Long>,
    filteredIndependentTracks: List<AudioTrack>,
    independentTracks: List<AudioTrack>,
    viewModel: AppViewModel,
    onAddToPlaylistRequest: (AudioTrack) -> Unit,
    onExitBulkMode: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        var noteCount by remember { mutableIntStateOf(0) }
        var compCount by remember { mutableIntStateOf(0) }
        var vocabCount by remember { mutableIntStateOf(0) }

        LaunchedEffect(selectedTrackIdsSet) {
            noteCount = viewModel.repository.getNoteCountForTracks(selectedTrackIdsSet)
            compCount = viewModel.repository.getComprehensionQuestionCountForTracks(selectedTrackIdsSet)
            vocabCount = viewModel.repository.getVocabQuestionCountForTracks(selectedTrackIdsSet)
        }

        com.example.ui.dialogs.ConfirmDeleteTrackDialog(
            title = Loc.getText("delete_tracks_bulk_confirm_title"),
            desc = Loc.getFormattedText("delete_tracks_bulk_confirm_desc", selectedTrackIdsSet.size),
            isScene = false,
            noteCount = noteCount,
            comprehensionCount = compCount,
            vocabCount = vocabCount,
            onConfirmDelete = { options ->
                showDeleteConfirm = false
                viewModel.deleteSelectedTracks(options)
                onExitBulkMode()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

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
                IconButton(
                    onClick = {
                        if (selectedTrackIdsSet.isNotEmpty()) {
                            showDeleteConfirm = true
                        }
                    },
                    modifier = Modifier.testTag("bulk_delete_tracks_btn")
                ) {
                    Icon(Icons.Filled.Delete, "Delete selection", tint = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = {
                    if (selectedTrackIdsSet.isNotEmpty()) {
                        val selectedTracksList = independentTracks.filter { selectedTrackIdsSet.contains(it.id) }
                        viewModel.quickAddTaskForTracks(selectedTracksList, Loc.getText("group_goal_task_title"), 3)
                        onExitBulkMode()
                        viewModel.clearTrackSelections()
                    }
                }) {
                    Icon(Icons.Filled.Bookmark, "Add goal to selected", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = {
                    if (selectedTrackIdsSet.isNotEmpty()) {
                        val firstTrack = independentTracks.find { selectedTrackIdsSet.contains(it.id) }
                        if (firstTrack != null) {
                            onAddToPlaylistRequest(firstTrack)
                        }
                    }
                }) {
                    Icon(Icons.Filled.AddToPhotos, "Add to Playlist", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = {
                    onExitBulkMode()
                    viewModel.clearTrackSelections()
                }) {
                    Icon(Icons.Filled.Close, "Exit bulk mode")
                }
            }
        }
    }
}

@Composable
fun LibraryFoldersBulkToolbar(
    selectedFolderIds: Set<Long>,
    filteredFolders: List<Folder>,
    context: Context,
    viewModel: AppViewModel,
    onSelectedFolderIdsChange: (Set<Long>) -> Unit,
    onExitBulkMode: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = Loc.getText("delete_folders_bulk_confirm_title"),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = Loc.getFormattedText("delete_folders_bulk_confirm_desc", selectedFolderIds.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteMultipleFolders(selectedFolderIds)
                        onExitBulkMode()
                        onSelectedFolderIdsChange(emptySet())
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_bulk_delete_folders_btn")
                ) {
                    Text(Loc.getText("delete"))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false },
                    modifier = Modifier.testTag("cancel_bulk_delete_folders_btn")
                ) {
                    Text(Loc.getText("cancel"))
                }
            }
        )
    }

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
                        onSelectedFolderIdsChange(emptySet())
                    } else {
                        onSelectedFolderIdsChange(filteredFolders.map { it.id }.toSet())
                    }
                }) {
                    Icon(
                        imageVector = Icons.Filled.SelectAll,
                        contentDescription = Loc.getText("select_all"),
                        tint = if (areAllFoldersSelected) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
                IconButton(
                    onClick = {
                        if (selectedFolderIds.isNotEmpty()) {
                            showDeleteConfirm = true
                        }
                    },
                    modifier = Modifier.testTag("bulk_delete_folders_btn")
                ) {
                    Icon(Icons.Filled.Delete, "Delete folders", tint = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = {
                    if (selectedFolderIds.isNotEmpty()) {
                        viewModel.bulkAddFoldersToTasks(selectedFolderIds)
                        onExitBulkMode()
                        onSelectedFolderIdsChange(emptySet())
                        Toast.makeText(context, Loc.getText("tasks_created_for_folders"), Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(Icons.Filled.Bookmark, "Create tasks", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = {
                    onExitBulkMode()
                    onSelectedFolderIdsChange(emptySet())
                }) {
                    Icon(Icons.Filled.Close, "Exit folder bulk mode")
                }
            }
        }
    }
}

@Composable
fun LibraryPlaylistsBulkToolbar(
    selectedPlaylistIds: Set<Long>,
    filteredPlaylists: List<Playlist>,
    context: Context,
    viewModel: AppViewModel,
    onSelectedPlaylistIdsChange: (Set<Long>) -> Unit,
    onExitBulkMode: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = Loc.getText("delete_playlists_bulk_confirm_title"),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = Loc.getFormattedText("delete_playlists_bulk_confirm_desc", selectedPlaylistIds.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteMultiplePlaylists(selectedPlaylistIds)
                        onExitBulkMode()
                        onSelectedPlaylistIdsChange(emptySet())
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_bulk_delete_playlists_btn")
                ) {
                    Text(Loc.getText("delete"))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false },
                    modifier = Modifier.testTag("cancel_bulk_delete_playlists_btn")
                ) {
                    Text(Loc.getText("cancel"))
                }
            }
        )
    }

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
                        onSelectedPlaylistIdsChange(emptySet())
                    } else {
                        onSelectedPlaylistIdsChange(filteredPlaylists.map { it.id }.toSet())
                    }
                }) {
                    Icon(
                        imageVector = Icons.Filled.SelectAll,
                        contentDescription = Loc.getText("select_all"),
                        tint = if (areAllPlaylistsSelected) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
                IconButton(
                    onClick = {
                        if (selectedPlaylistIds.isNotEmpty()) {
                            showDeleteConfirm = true
                        }
                    },
                    modifier = Modifier.testTag("bulk_delete_playlists_btn")
                ) {
                    Icon(Icons.Filled.Delete, "Delete playlists", tint = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = {
                    if (selectedPlaylistIds.isNotEmpty()) {
                        viewModel.bulkAddPlaylistsToTasks(selectedPlaylistIds)
                        onExitBulkMode()
                        onSelectedPlaylistIdsChange(emptySet())
                        Toast.makeText(context, Loc.getText("tasks_created_for_playlists"), Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(Icons.Filled.Bookmark, "Create tasks for playlists", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = {
                    onExitBulkMode()
                    onSelectedPlaylistIdsChange(emptySet())
                }) {
                    Icon(Icons.Filled.Close, "Exit playlist bulk mode")
                }
            }
        }
    }
}
