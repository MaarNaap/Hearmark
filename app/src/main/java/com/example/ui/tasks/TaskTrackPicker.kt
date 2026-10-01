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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AudioTrack
import com.example.data.Folder
import java.io.File

@Composable
fun TaskTrackPicker(
    allFolders: List<Folder>,
    allTracks: List<AudioTrack>,
    selectedManualTrackIds: Set<Long>,
    onSelectedManualTrackIdsChange: (Set<Long>) -> Unit
) {
    val folderTree = remember(allFolders) {
        buildFolderTree(allFolders)
    }
    // Keep root/parent folders and any folders with selected tracks expanded by default
    var expandedFolderIds by remember(allFolders) {
        val parentIds = allFolders.filter { f ->
            allFolders.any { it.parentFolderId == f.id || (it.id != f.id && it.folderPath.startsWith(f.folderPath + File.separator)) }
        }.map { it.id }.toMutableSet()
        val foldersWithSelected = allTracks.filter { selectedManualTrackIds.contains(it.id) && it.parentFolderId != null }.mapNotNull { it.parentFolderId }
        parentIds.addAll(foldersWithSelected)
        if (parentIds.isEmpty() && allFolders.isNotEmpty()) {
            parentIds.addAll(allFolders.map { it.id })
        }
        mutableStateOf(parentIds.toSet())
    }
    var independentTracksExpanded by remember { mutableStateOf(true) }
    val flattenedVisibleTree = remember(folderTree, expandedFolderIds) {
        flattenFolderTree(folderTree, expandedFolderIds)
    }
    val allParentFolderIds = remember(allFolders) {
        allFolders.map { it.id }.toSet()
    }

    // Header with selection summary and Expand/Collapse All controls
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (selectedManualTrackIds.isNotEmpty()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Audiotrack,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (selectedManualTrackIds.isNotEmpty()) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${selectedManualTrackIds.size} ${Loc.getText("selected")}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedManualTrackIds.isNotEmpty()) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedManualTrackIds.isNotEmpty()) {
                TextButton(
                    onClick = { onSelectedManualTrackIdsChange(emptySet()) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = Loc.getText("deselect_all_tasks"),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (allParentFolderIds.isNotEmpty()) {
                val isAllExpanded = expandedFolderIds.containsAll(allParentFolderIds) && independentTracksExpanded
                TextButton(
                    onClick = {
                        if (isAllExpanded) {
                            expandedFolderIds = emptySet()
                            independentTracksExpanded = false
                        } else {
                            expandedFolderIds = allParentFolderIds
                            independentTracksExpanded = true
                        }
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
    }

    if (allTracks.isEmpty()) {
        Text(
            text = Loc.getText("empty_tracks_desc"),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(vertical = 8.dp)
        )
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Render folders in tree hierarchy
            flattenedVisibleTree.forEach { node ->
                val folder = node.folder
                val hasSubfolders = node.children.isNotEmpty()
                val isExpanded = expandedFolderIds.contains(folder.id)
                val directTracks = allTracks.filter { it.parentFolderId == folder.id }
                val hasItems = hasSubfolders || directTracks.isNotEmpty()
                val selectedInFolderCount = directTracks.count { selectedManualTrackIds.contains(it.id) }

                // 1. Folder row (selection of files only, no folders)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selectedInFolderCount > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f) else Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = (node.depth * 18).dp)
                        .clickable {
                            expandedFolderIds = if (isExpanded) {
                                expandedFolderIds - folder.id
                            } else {
                                expandedFolderIds + folder.id
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (hasItems) {
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
                        Icon(
                            imageVector = if (hasItems && isExpanded) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                            contentDescription = null,
                            tint = if (selectedInFolderCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = folder.folderName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (node.depth == 0) FontWeight.SemiBold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (selectedInFolderCount > 0) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Text(
                                    text = "$selectedInFolderCount",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        if (directTracks.isNotEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.padding(start = 2.dp)
                            ) {
                                Text(
                                    text = "${directTracks.size}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 2. Direct files inside this folder (when expanded)
                if (isExpanded) {
                    directTracks.forEach { track ->
                        val isTrackSelected = selectedManualTrackIds.contains(track.id)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isTrackSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = ((node.depth + 1) * 18 + 8).dp)
                                .clickable {
                                    val current = selectedManualTrackIds.toMutableSet()
                                    if (current.contains(track.id)) current.remove(track.id) else current.add(track.id)
                                    onSelectedManualTrackIdsChange(current)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isTrackSelected,
                                    onCheckedChange = { checked ->
                                        val current = selectedManualTrackIds.toMutableSet()
                                        if (checked) current.add(track.id) else current.remove(track.id)
                                        onSelectedManualTrackIdsChange(current)
                                    },
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = getTrackFileIcon(track),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = middleEllipse(track.getDisplayTitle(), maxLength = 42),
                                        fontSize = 13.sp,
                                        fontWeight = if (isTrackSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isTrackSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (track.duration > 0) {
                                            Text(
                                                text = formatDuration(track.duration),
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                        Text(
                                            text = "${track.getProgressPercent()}%",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Independent tracks (not in any scanned folder)
            val validFolderIds = allFolders.map { it.id }.toSet()
            val independentTracks = allTracks.filter { (it.parentFolderId == null || !validFolderIds.contains(it.parentFolderId)) }
            if (independentTracks.isNotEmpty()) {
                val selectedIndCount = independentTracks.count { selectedManualTrackIds.contains(it.id) }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selectedIndCount > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f) else Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { independentTracksExpanded = !independentTracksExpanded }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { independentTracksExpanded = !independentTracksExpanded },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = if (independentTracksExpanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = if (independentTracksExpanded) "Collapse" else "Expand",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            imageVector = if (independentTracksExpanded) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                            contentDescription = null,
                            tint = if (selectedIndCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = Loc.getText("independent_tracks_section"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (selectedIndCount > 0) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Text(
                                    text = "$selectedIndCount",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(start = 2.dp)
                        ) {
                            Text(
                                text = "${independentTracks.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (independentTracksExpanded) {
                    independentTracks.forEach { track ->
                        val isTrackSelected = selectedManualTrackIds.contains(track.id)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isTrackSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 26.dp)
                                .clickable {
                                    val current = selectedManualTrackIds.toMutableSet()
                                    if (current.contains(track.id)) current.remove(track.id) else current.add(track.id)
                                    onSelectedManualTrackIdsChange(current)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isTrackSelected,
                                    onCheckedChange = { checked ->
                                        val current = selectedManualTrackIds.toMutableSet()
                                        if (checked) current.add(track.id) else current.remove(track.id)
                                        onSelectedManualTrackIdsChange(current)
                                    },
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = getTrackFileIcon(track),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = middleEllipse(track.getDisplayTitle(), maxLength = 42),
                                        fontSize = 13.sp,
                                        fontWeight = if (isTrackSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isTrackSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (track.duration > 0) {
                                            Text(
                                                text = formatDuration(track.duration),
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                        Text(
                                            text = "${track.getProgressPercent()}%",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
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
