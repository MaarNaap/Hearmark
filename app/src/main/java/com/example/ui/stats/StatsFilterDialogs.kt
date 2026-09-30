package com.example.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.*
import com.example.ui.theme.ColorSuccess
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- FOLDER & FILE MULTI-SELECT TREE DIALOG ---

@Composable
fun FolderFileTreeFilterDialog(
    allFolders: List<Folder>,
    allTracks: List<AudioTrack>,
    initialSelectedFolderIds: Set<Long>,
    initialSelectedTrackIds: Set<Long>,
    onDismiss: () -> Unit,
    onApply: (selectedFolderIds: Set<Long>, selectedTrackIds: Set<Long>) -> Unit
) {
    var tempSelectedFolderIds by remember { mutableStateOf(initialSelectedFolderIds) }
    var tempSelectedTrackIds by remember { mutableStateOf(initialSelectedTrackIds) }
    var searchQuery by remember { mutableStateOf("") }

    val folderTree = remember(allFolders) {
        buildFolderTree(allFolders)
    }

    var expandedFolderIds by remember(allFolders) {
        val parentIds = allFolders.map { it.id }.toSet()
        mutableStateOf(parentIds)
    }
    var independentTracksExpanded by remember { mutableStateOf(true) }

    val flattenedItems = remember(
        folderTree,
        allFolders,
        allTracks,
        expandedFolderIds,
        searchQuery,
        independentTracksExpanded
    ) {
        flattenFolderFileTree(
            nodes = folderTree,
            allFolders = allFolders,
            allTracks = allTracks,
            expandedFolderIds = expandedFolderIds,
            searchQuery = searchQuery,
            independentExpanded = independentTracksExpanded
        )
    }

    val totalSelectedCount = tempSelectedFolderIds.size + tempSelectedTrackIds.size

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
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
                            imageVector = Icons.Filled.AccountTree,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = Loc.getText("filter_folder_file_dialog_title"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (totalSelectedCount > 0) {
                                Text(
                                    text = String.format(Locale.US, Loc.getText("selected_folders_files_count"), totalSelectedCount),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(Loc.getText("search_folders_files_hint"), fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val allFolderIds = remember(allFolders) { allFolders.map { it.id }.toSet() }
                    val allTrackIds = remember(allTracks) { allTracks.map { it.id }.toSet() }
                    val isAllExpanded = expandedFolderIds.containsAll(allFolderIds) && independentTracksExpanded

                    OutlinedButton(
                        onClick = {
                            tempSelectedFolderIds = allFolderIds
                            tempSelectedTrackIds = allTrackIds
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        Text(Loc.getText("select_all_tasks"), fontSize = 10.sp, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = {
                            tempSelectedFolderIds = emptySet()
                            tempSelectedTrackIds = emptySet()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        Text(Loc.getText("deselect_all_tasks"), fontSize = 10.sp, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = {
                            if (isAllExpanded) {
                                expandedFolderIds = emptySet()
                                independentTracksExpanded = false
                            } else {
                                expandedFolderIds = allFolderIds
                                independentTracksExpanded = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isAllExpanded) Loc.getText("collapse_all") else Loc.getText("expand_all"),
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tree items list
                if (flattenedItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = Loc.getText("no_folders_or_files_match"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(flattenedItems, key = { item ->
                            when (item) {
                                is FolderTreeItem.FolderRow -> "folder_${item.node.folder.id}"
                                is FolderTreeItem.TrackRow -> "track_${item.track.id}"
                                is FolderTreeItem.IndependentHeader -> "independent_header"
                            }
                        }) { item ->
                            when (item) {
                                is FolderTreeItem.FolderRow -> {
                                    val folder = item.node.folder
                                    val isFolderDirectlySelected = folder.id in tempSelectedFolderIds
                                    val allTracksSelected = item.allRecursiveTrackIds.isNotEmpty() && item.allRecursiveTrackIds.all { it in tempSelectedTrackIds }
                                    val someTracksSelected = item.allRecursiveTrackIds.any { it in tempSelectedTrackIds }
                                    val isCheckedOrFull = isFolderDirectlySelected || (item.allRecursiveTrackIds.isNotEmpty() && allTracksSelected)

                                    val triState = when {
                                        isCheckedOrFull -> ToggleableState.On
                                        someTracksSelected -> ToggleableState.Indeterminate
                                        else -> ToggleableState.Off
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (triState == ToggleableState.On) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                        } else if (triState == ToggleableState.Indeterminate) {
                                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = (item.depth * 14).dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val willSelect = (triState != ToggleableState.On)
                                                    if (willSelect) {
                                                        tempSelectedFolderIds = tempSelectedFolderIds + item.allSubfolderIds
                                                        tempSelectedTrackIds = tempSelectedTrackIds + item.allRecursiveTrackIds
                                                    } else {
                                                        tempSelectedFolderIds = tempSelectedFolderIds - item.allSubfolderIds
                                                        tempSelectedTrackIds = tempSelectedTrackIds - item.allRecursiveTrackIds
                                                    }
                                                }
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (item.hasChildren) {
                                                IconButton(
                                                    onClick = {
                                                        expandedFolderIds = if (item.isExpanded) {
                                                            expandedFolderIds - folder.id
                                                        } else {
                                                            expandedFolderIds + folder.id
                                                        }
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (item.isExpanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            } else {
                                                Spacer(modifier = Modifier.width(24.dp))
                                            }

                                            Icon(
                                                imageVector = if (item.isExpanded) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )

                                            Spacer(modifier = Modifier.width(6.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = folder.folderName,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${item.totalTracksCount} 🎵",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                            }

                                            TriStateCheckbox(
                                                state = triState,
                                                onClick = {
                                                    val willSelect = (triState != ToggleableState.On)
                                                    if (willSelect) {
                                                        tempSelectedFolderIds = tempSelectedFolderIds + item.allSubfolderIds
                                                        tempSelectedTrackIds = tempSelectedTrackIds + item.allRecursiveTrackIds
                                                    } else {
                                                        tempSelectedFolderIds = tempSelectedFolderIds - item.allSubfolderIds
                                                        tempSelectedTrackIds = tempSelectedTrackIds - item.allRecursiveTrackIds
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }

                                is FolderTreeItem.TrackRow -> {
                                    val track = item.track
                                    val isTrackChecked = track.id in tempSelectedTrackIds

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isTrackChecked) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = (item.depth * 14 + 12).dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    tempSelectedTrackIds = if (isTrackChecked) {
                                                        tempSelectedTrackIds - track.id
                                                    } else {
                                                        tempSelectedTrackIds + track.id
                                                    }
                                                    if (isTrackChecked && track.parentFolderId != null) {
                                                        tempSelectedFolderIds = tempSelectedFolderIds - track.parentFolderId
                                                    }
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = getTrackFileIcon(track),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(15.dp)
                                            )

                                            Spacer(modifier = Modifier.width(6.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = track.getDisplayTitle(),
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isTrackChecked) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (track.duration > 0L) {
                                                    Text(
                                                        text = formatStatsDuration(track.duration),
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                    )
                                                }
                                            }

                                            Checkbox(
                                                checked = isTrackChecked,
                                                onCheckedChange = { checked ->
                                                    tempSelectedTrackIds = if (checked) {
                                                        tempSelectedTrackIds + track.id
                                                    } else {
                                                        tempSelectedTrackIds - track.id
                                                    }
                                                    if (!checked && track.parentFolderId != null) {
                                                        tempSelectedFolderIds = tempSelectedFolderIds - track.parentFolderId
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }

                                is FolderTreeItem.IndependentHeader -> {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { independentTracksExpanded = !independentTracksExpanded }
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = { independentTracksExpanded = !independentTracksExpanded },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (item.isExpanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            Icon(
                                                imageVector = Icons.Filled.Folder,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )

                                            Spacer(modifier = Modifier.width(6.dp))

                                            Text(
                                                text = Loc.getText("independent_tracks_section"),
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )

                                            Text(
                                                text = "${item.count} 🎵",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onApply(emptySet(), emptySet())
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(Loc.getText("all_folders_files"), fontSize = 12.sp, maxLines = 1)
                    }

                    Button(
                        onClick = {
                            onApply(tempSelectedFolderIds, tempSelectedTrackIds)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(Loc.getText("apply_filter"), fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun StatsTaskFilterDialog(
    allTasks: List<Task>,
    initialSelectedTaskIds: Set<Long>,
    onDismiss: () -> Unit,
    onApply: (Set<Long>) -> Unit
) {
    var tempSelectedTaskIds by remember { mutableStateOf(initialSelectedTaskIds) }
    var selectedLabelFilterInDialog by remember { mutableStateOf<String?>(null) }
    var taskSearchQuery by remember { mutableStateOf("") }

    val allDialogTaskLabels = remember(allTasks) {
        allTasks.flatMap { it.getLabelsList() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.getDefault()) }
    }

    val filteredDialogTasks = remember(allTasks, selectedLabelFilterInDialog, taskSearchQuery) {
        val query = taskSearchQuery.trim().lowercase(Locale.getDefault())
        allTasks.filter { task ->
            val matchesLabel = if (selectedLabelFilterInDialog == null) {
                true
            } else {
                task.getLabelsList().any { it.equals(selectedLabelFilterInDialog, ignoreCase = true) }
            }
            val matchesSearch = if (query.isEmpty()) {
                true
            } else {
                task.getDisplayTitle().lowercase(Locale.getDefault()).contains(query) ||
                        task.getLabelsList().any { it.lowercase(Locale.getDefault()).contains(query) }
            }
            matchesLabel && matchesSearch
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
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
                            imageVector = Icons.Filled.Bookmark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = Loc.getText("select_tasks_dialog_title"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (tempSelectedTaskIds.isNotEmpty()) {
                                Text(
                                    text = String.format(Locale.US, Loc.getText("selected_tasks_count"), tempSelectedTaskIds.size),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search field
                OutlinedTextField(
                    value = taskSearchQuery,
                    onValueChange = { taskSearchQuery = it },
                    placeholder = { Text(Loc.getText("search_tasks_hint"), fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (taskSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { taskSearchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Selection Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val targetIds = filteredDialogTasks.map { it.id }.toSet()
                            tempSelectedTaskIds = tempSelectedTaskIds + targetIds
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Text(Loc.getText("select_all_tasks"), fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            if (selectedLabelFilterInDialog == null && taskSearchQuery.isBlank()) {
                                tempSelectedTaskIds = emptySet()
                            } else {
                                val toRemove = filteredDialogTasks.map { it.id }.toSet()
                                tempSelectedTaskIds = tempSelectedTaskIds - toRemove
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Text(Loc.getText("deselect_all_tasks"), fontSize = 11.sp)
                    }
                }

                // Label filter row
                if (allDialogTaskLabels.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Label,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = Loc.getText("filter_tasks_by_label"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            FilterChip(
                                selected = selectedLabelFilterInDialog == null,
                                onClick = { selectedLabelFilterInDialog = null },
                                label = { Text(Loc.getText("all_labels_filter"), fontSize = 11.sp) },
                                modifier = Modifier.height(32.dp)
                            )
                        }
                        items(allDialogTaskLabels) { label ->
                            val isSelected = selectedLabelFilterInDialog.equals(label, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedLabelFilterInDialog = if (isSelected) null else label
                                },
                                label = { Text(label, fontSize = 11.sp) },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                } else null,
                                modifier = Modifier.height(32.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Task List
                if (filteredDialogTasks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (taskSearchQuery.isNotBlank()) Loc.getText("no_tasks_match_search") else Loc.getText("no_tasks_for_label"),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredDialogTasks, key = { it.id }) { task ->
                            val isChecked = task.id in tempSelectedTaskIds
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        tempSelectedTaskIds = if (isChecked) {
                                            tempSelectedTaskIds - task.id
                                        } else {
                                            tempSelectedTaskIds + task.id
                                        }
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            tempSelectedTaskIds = if (checked) {
                                                tempSelectedTaskIds + task.id
                                            } else {
                                                tempSelectedTaskIds - task.id
                                            }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Icon(
                                        imageVector = if (task.isCompleted) Icons.Filled.CheckCircle else Icons.Filled.Bookmark,
                                        contentDescription = null,
                                        tint = if (task.isCompleted) ColorSuccess else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = task.getDisplayTitle(),
                                        fontWeight = if (isChecked) FontWeight.Bold else FontWeight.SemiBold,
                                        fontSize = 13.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 18.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onApply(emptySet())
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(Loc.getText("all_tasks"), fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            onApply(tempSelectedTaskIds)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(Loc.getText("apply_filter"), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun DetailedHistoryLogDialog(
    viewModel: AppViewModel,
    context: Context,
    history: List<PlaybackHistory>,
    rangeFiltered: List<PlaybackHistory>,
    allTracks: List<AudioTrack>,
    allTasks: List<Task>,
    allTaskProgress: List<TaskTrackProgress>,
    selectedTaskIds: Set<Long>,
    selectedFolderIds: Set<Long>,
    selectedFileTrackIds: Set<Long>,
    onDismiss: () -> Unit
) {
    val filteredHistory = remember(rangeFiltered) {
        rangeFiltered.sortedByDescending { it.completedAt }
    }
    val isAr = Loc.currentLanguage == "ar"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = Loc.getText("listening_history_title"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Quick Copy/Export filtered history button
                        if (filteredHistory.isNotEmpty()) {
                            IconButton(onClick = {
                                viewModel.copyHistoryClipboard(context, filteredHistory)
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "Copy filtered history",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close")
                        }
                    }
                }

                // Active Filters & Summary Strip
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            val periodLabel = when (viewModel.statsFilter) {
                                "today" -> Loc.getText("filter_today")
                                "week" -> Loc.getText("filter_week")
                                "month" -> Loc.getText("filter_month")
                                "ninety" -> Loc.getText("filter_ninety")
                                else -> Loc.getText("filter_all")
                            }
                            SuggestionChip(
                                onClick = {},
                                label = { Text(periodLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    labelColor = MaterialTheme.colorScheme.primary
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                modifier = Modifier.height(26.dp)
                            )

                            if (selectedTaskIds.isNotEmpty()) {
                                val taskFilterText = if (isAr) "${Loc.getText("filter_by_task")}: ${selectedTaskIds.size}" else "Tasks: ${selectedTaskIds.size}"
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(taskFilterText, fontSize = 10.sp) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                        labelColor = MaterialTheme.colorScheme.secondary
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
                                    modifier = Modifier.height(26.dp)
                                )
                            }

                            if (selectedFolderIds.isNotEmpty() || selectedFileTrackIds.isNotEmpty()) {
                                val count = selectedFolderIds.size + selectedFileTrackIds.size
                                val folderFilterText = if (isAr) "${Loc.getText("filter_by_folder_file")}: $count" else "Files: $count"
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(folderFilterText, fontSize = 10.sp) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                                        labelColor = MaterialTheme.colorScheme.tertiary
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)),
                                    modifier = Modifier.height(26.dp)
                                )
                            }
                        }

                        val countLabel = if (isAr) {
                            "${filteredHistory.size} ${if (filteredHistory.size in 3..10) "جلسات" else "جلسة"}"
                        } else {
                            "${filteredHistory.size} ${if (filteredHistory.size == 1) "session" else "sessions"}"
                        }
                        Text(
                            text = countLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (filteredHistory.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.HistoryToggleOff,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (history.isEmpty()) Loc.getText("no_history_yet") else if (isAr) "لا توجد جلسات استماع مطابقة للفلاتر المحددة" else "No listening history matches the selected filters",
                                color = Color.Gray,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    val trackMap = remember(allTracks) { allTracks.associateBy { it.id } }
                    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredHistory, key = { it.id }) { logItem ->
                            val track = trackMap[logItem.trackId]
                            val title = track?.getDisplayTitle() ?: logItem.trackName
                            val formattedDate = sdf.format(Date(logItem.completedAt))

                            val attachedTaskNames = remember(logItem, allTasks, allTaskProgress) {
                                val logged = logItem.getActiveTasksList()
                                if (logged.isNotEmpty()) {
                                    logged
                                } else if (logItem.activeTasks.isBlank()) {
                                    val trackId = logItem.trackId
                                    allTasks.filter { task ->
                                        val wasActiveThen = logItem.completedAt >= task.startDate && (task.endDate == null || logItem.completedAt <= task.endDate)
                                        wasActiveThen && when (task.sourceType) {
                                            "FOLDER" -> track?.parentFolderId == task.sourceId
                                            "TRACKS" -> allTaskProgress.any { it.taskId == task.id && it.trackId == trackId }
                                            else -> allTaskProgress.any { it.taskId == task.id && it.trackId == trackId }
                                        }
                                    }.map { it.getDisplayTitle() }.distinct()
                                } else {
                                    emptyList()
                                }
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = title,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = formattedDate,
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                        if (attachedTaskNames.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = (if (isAr) "المهام: " else "Tasks: ") + attachedTaskNames.joinToString(", "),
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Column(horizontalAlignment = Alignment.End) {
                                        val dur = if (logItem.actualListenedMs > 0L) logItem.actualListenedMs else logItem.durationMs
                                        Text(
                                            text = formatStatsDuration(dur),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (logItem.playbackSpeed > 0f && logItem.playbackSpeed != 1.0f) {
                                            Text(
                                                text = "${String.format(Locale.US, "%.1f", logItem.playbackSpeed)}x",
                                                fontSize = 10.sp,
                                                color = Color.Gray
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

