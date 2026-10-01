package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                                onUpdateSelectedFolders(tempSelectedFolderIds + item.allSubfolderIds)
                                onUpdateSelectedTracks(tempSelectedTrackIds + item.allRecursiveTrackIds)
                            } else {
                                onUpdateSelectedFolders(tempSelectedFolderIds - item.allSubfolderIds)
                                onUpdateSelectedTracks(tempSelectedTrackIds - item.allRecursiveTrackIds)
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.hasChildren) {
                        IconButton(
                            onClick = {
                                onToggleFolderExpanded(folder.id, item.isExpanded)
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
                                onUpdateSelectedFolders(tempSelectedFolderIds + item.allSubfolderIds)
                                onUpdateSelectedTracks(tempSelectedTrackIds + item.allRecursiveTrackIds)
                            } else {
                                onUpdateSelectedFolders(tempSelectedFolderIds - item.allSubfolderIds)
                                onUpdateSelectedTracks(tempSelectedTrackIds - item.allRecursiveTrackIds)
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
                        .clickable { onToggleIndependentExpanded() }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onToggleIndependentExpanded() },
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
