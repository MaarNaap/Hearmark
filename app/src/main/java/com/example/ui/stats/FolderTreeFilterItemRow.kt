package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun FolderTreeFilterItemRow(
    item: FolderTreeItem,
    tempSelectedFolderIds: Set<Long>,
    tempSelectedTrackIds: Set<Long>,
    onUpdateSelectedFolders: (Set<Long>) -> Unit,
    onUpdateSelectedTracks: (Set<Long>) -> Unit,
    onToggleFolderExpanded: (Long, Boolean) -> Unit,
    independentTracksExpanded: Boolean,
    onToggleIndependentExpanded: () -> Unit
) {
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

            // Clean folder row matching AI Hub HierarchyFilePicker styling
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = when (triState) {
                    ToggleableState.On -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ToggleableState.Indeterminate -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.20f)
                    ToggleableState.Off -> Color.Transparent
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = (item.depth * 14 + 4).dp, end = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            onToggleFolderExpanded(folder.id, item.isExpanded)
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Folder Icon
                    Icon(
                        imageVector = if (item.isExpanded) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )

                    // Folder Title
                    Text(
                        text = folder.folderName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Track count badge
                    Text(
                        text = "${item.totalTracksCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Expand / Collapse Chevron
                    if (item.hasChildren) {
                        Icon(
                            imageVector = if (item.isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (item.isExpanded) Loc.getText("collapse_all") else Loc.getText("expand_all"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Multi-select Checkbox
                    TriStateCheckbox(
                        state = triState,
                        onClick = {
                            val willSelect = (triState != ToggleableState.On)
                            if (willSelect) {
                                onUpdateSelectedFolders(tempSelectedFolderIds + item.allSubfolderIds)
                                onUpdateSelectedTracks(tempSelectedTrackIds + item.allRecursiveTrackIds)
                            } else {
                                onUpdateSelectedFolders(tempSelectedFolderIds - item.allSubfolderIds)
                                onUpdateSelectedTracks(tempSelectedTrackIds - item.allRecursiveTrackIds)
                            }
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        is FolderTreeItem.TrackRow -> {
            val track = item.track
            val isTrackChecked = track.id in tempSelectedTrackIds

            // File / Track Row matching AI Hub FileTrackRowItem styling
            Surface(
                onClick = {
                    onUpdateSelectedTracks(
                        if (isTrackChecked) {
                            tempSelectedTrackIds - track.id
                        } else {
                            tempSelectedTrackIds + track.id
                        }
                    )
                    if (isTrackChecked && track.parentFolderId != null) {
                        onUpdateSelectedFolders(tempSelectedFolderIds - track.parentFolderId)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = (item.depth * 14 + 10).dp, end = 6.dp, top = 1.dp, bottom = 1.dp),
                shape = RoundedCornerShape(8.dp),
                color = if (isTrackChecked) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                } else {
                    Color.Transparent
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = getTrackFileIcon(track),
                        contentDescription = null,
                        tint = if (isTrackChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.getDisplayTitle(),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isTrackChecked) FontWeight.Bold else FontWeight.Normal,
                            color = if (isTrackChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
                            onUpdateSelectedTracks(
                                if (checked) {
                                    tempSelectedTrackIds + track.id
                                } else {
                                    tempSelectedTrackIds - track.id
                                }
                            )
                            if (!checked && track.parentFolderId != null) {
                                onUpdateSelectedFolders(tempSelectedFolderIds - track.parentFolderId)
                            }
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        is FolderTreeItem.IndependentHeader -> {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button) { onToggleIndependentExpanded() }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (item.isExpanded) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )

                    Text(
                        text = Loc.getText("independent_tracks_section"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "${item.count}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Icon(
                        imageVector = if (item.isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
