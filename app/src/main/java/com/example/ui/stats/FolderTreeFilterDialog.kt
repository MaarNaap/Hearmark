package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.AudioTrack
import com.example.data.Folder
import java.util.Locale

@Composable
fun FolderFileTreeFilterDialog(
    allFolders: List<Folder>,
    allTracks: List<AudioTrack>,
    initialSelectedFolderIds: Set<Long>,
    initialSelectedTrackIds: Set<Long>,
    defaultExpanded: Boolean = false,
    title: String = Loc.getText("filter_folder_file_dialog_title"),
    onDismiss: () -> Unit,
    onApply: (selectedFolderIds: Set<Long>, selectedTrackIds: Set<Long>) -> Unit
) {
    var tempSelectedFolderIds by remember { mutableStateOf(initialSelectedFolderIds) }
    var tempSelectedTrackIds by remember { mutableStateOf(initialSelectedTrackIds) }
    var searchQuery by remember { mutableStateOf("") }

    val folderTree = remember(allFolders) {
        buildFolderTree(allFolders)
    }

    var expandedFolderIds by remember(allFolders, defaultExpanded) {
        mutableStateOf(if (defaultExpanded) allFolders.map { it.id }.toSet() else emptySet())
    }
    var independentTracksExpanded by remember(defaultExpanded) { mutableStateOf(defaultExpanded) }

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
                                text = title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (totalSelectedCount > 0) {
                                Text(
                                    text = Loc.getFormattedText("selected_folders_files_count", totalSelectedCount),
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

                Spacer(modifier = Modifier.height(6.dp))

                val allFolderIds = remember(allFolders) { allFolders.map { it.id }.toSet() }
                val allTrackIds = remember(allTracks) { allTracks.map { it.id }.toSet() }
                val isAllExpanded = expandedFolderIds.containsAll(allFolderIds) && independentTracksExpanded

                // Quick Action Bar matching AI Hub design
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(
                            onClick = {
                                tempSelectedFolderIds = allFolderIds
                                tempSelectedTrackIds = allTrackIds
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(Loc.getText("select_all_tasks"), fontSize = 11.5.sp)
                        }

                        TextButton(
                            onClick = {
                                tempSelectedFolderIds = emptySet()
                                tempSelectedTrackIds = emptySet()
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(Loc.getText("deselect_all_tasks"), fontSize = 11.5.sp)
                        }
                    }

                    TextButton(
                        onClick = {
                            if (isAllExpanded) {
                                expandedFolderIds = emptySet()
                                independentTracksExpanded = false
                            } else {
                                expandedFolderIds = allFolderIds
                                independentTracksExpanded = true
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = if (isAllExpanded) Icons.Filled.UnfoldLess else Icons.Filled.UnfoldMore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isAllExpanded) Loc.getText("collapse_all") else Loc.getText("expand_all"),
                            fontSize = 11.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Tree items list framed in Surface matching AI Hub HierarchyFilePicker
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                ) {
                    if (flattenedItems.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
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
                                .fillMaxSize()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(flattenedItems, key = { item ->
                                when (item) {
                                    is FolderTreeItem.FolderRow -> "folder_${item.node.folder.id}"
                                    is FolderTreeItem.TrackRow -> "track_${item.track.id}"
                                    is FolderTreeItem.IndependentHeader -> "independent_header"
                                }
                            }) { item ->
                                FolderTreeFilterItemRow(
                                    item = item,
                                    tempSelectedFolderIds = tempSelectedFolderIds,
                                    tempSelectedTrackIds = tempSelectedTrackIds,
                                    onUpdateSelectedFolders = { tempSelectedFolderIds = it },
                                    onUpdateSelectedTracks = { tempSelectedTrackIds = it },
                                    onToggleFolderExpanded = { folderId, isExpanded ->
                                        expandedFolderIds = if (isExpanded) {
                                            expandedFolderIds - folderId
                                        } else {
                                            expandedFolderIds + folderId
                                        }
                                    },
                                    independentTracksExpanded = independentTracksExpanded,
                                    onToggleIndependentExpanded = {
                                        independentTracksExpanded = !independentTracksExpanded
                                    }
                                )
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
