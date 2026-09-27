package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.AudioTrack
import com.example.data.Folder
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
