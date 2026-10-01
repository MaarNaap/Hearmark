package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AudioTrack
import com.example.data.Folder
import com.example.data.Playlist
import java.io.File

@Composable
fun TaskSourceStep(
    formState: TaskFormState,
    allFolders: List<Folder>,
    allPlaylists: List<Playlist>,
    allTracks: List<AudioTrack>
) {
    Text(Loc.getText("step_1"), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.secondary)
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = formState.title,
        onValueChange = { 
            formState.title = it
            formState.isTitleManuallyEdited = true
        },
        label = { Text(Loc.getText("task_title_label")) },
        placeholder = { Text(Loc.getText("placeholder_task_title")) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_title_input")
    )
    
    Spacer(modifier = Modifier.height(16.dp))
    Text(Loc.getText("source_type_label"), fontWeight = FontWeight.Bold)
    
    // Radios
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = formState.sourceType == "FOLDER", onClick = { formState.sourceType = "FOLDER"; formState.sourceId = null })
        Text(Loc.getText("use_folder"))
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = formState.sourceType == "PLAYLIST", onClick = { formState.sourceType = "PLAYLIST"; formState.sourceId = null })
        Text(Loc.getText("use_playlist"))
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = formState.sourceType == "TRACKS", onClick = { formState.sourceType = "TRACKS"; formState.sourceId = null })
        Text(Loc.getText("use_tracks"))
    }

    HorizontalDivider(
        modifier = Modifier.padding(vertical = 12.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    )

    // Dropdowns for folder / playlists
    Spacer(modifier = Modifier.height(4.dp))
    if (formState.sourceType == "FOLDER") {
        val folderTree = remember(allFolders) {
            buildFolderTree(allFolders)
        }
        // Keep root/parent folders and any ancestors of selected folder expanded by default
        var expandedFolderIds by remember(allFolders, formState.sourceId) {
            val parentIds = allFolders.filter { f ->
                allFolders.any { it.parentFolderId == f.id || (it.id != f.id && it.folderPath.startsWith(f.folderPath + File.separator)) }
            }.map { it.id }.toMutableSet()
            if (formState.sourceId != null) {
                var curr = allFolders.find { it.id == formState.sourceId }
                while (curr != null && curr.parentFolderId != null) {
                    parentIds.add(curr.parentFolderId!!)
                    curr = allFolders.find { it.id == curr.parentFolderId }
                }
            }
            mutableStateOf(parentIds.toSet())
        }
        val flattenedVisibleTree = remember(folderTree, expandedFolderIds) {
            flattenFolderTree(folderTree, expandedFolderIds)
        }
        val allParentIds = remember(allFolders) {
            allFolders.filter { f ->
                allFolders.any { it.parentFolderId == f.id || (it.id != f.id && it.folderPath.startsWith(f.folderPath + File.separator)) }
            }.map { it.id }.toSet()
        }
        if (allParentIds.isNotEmpty()) {
            val isAllExpanded = expandedFolderIds.containsAll(allParentIds)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        expandedFolderIds = if (isAllExpanded) emptySet() else allParentIds
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
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
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        if (flattenedVisibleTree.isEmpty()) {
            Text(
                text = Loc.getText("no_folders_found"),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                flattenedVisibleTree.forEach { node ->
                    val folder = node.folder
                    val isSelected = (formState.sourceId == folder.id)
                    val hasChildren = node.children.isNotEmpty()
                    val isExpanded = expandedFolderIds.contains(folder.id)
                    val trackCount = allTracks.count { it.parentFolderId == folder.id }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (node.depth * 18).dp)
                            .clickable { formState.sourceId = folder.id }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp),
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
                            RadioButton(
                                selected = isSelected,
                                onClick = { formState.sourceId = folder.id },
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (hasChildren && isExpanded) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = folder.folderName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (node.depth == 0) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (trackCount > 0) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp)
                                ) {
                                    Text(
                                        text = "$trackCount",
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    } else if (formState.sourceType == "PLAYLIST") {
        allPlaylists.forEach { p ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { formState.sourceId = p.id }
                .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = formState.sourceId == p.id, onClick = { formState.sourceId = p.id })
                Text(p.name, modifier = Modifier.padding(start = 8.dp))
            }
        }
    } else if (formState.sourceType == "TRACKS") {
        TaskTrackPicker(
            allFolders = allFolders,
            allTracks = allTracks,
            selectedManualTrackIds = formState.selectedManualTrackIds,
            onSelectedManualTrackIdsChange = { formState.selectedManualTrackIds = it }
        )
    }
}
