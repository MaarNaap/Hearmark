package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.data.Folder
import com.example.data.Note

@Composable
fun NotebookFolderFilterDialog(
    folders: List<Folder>,
    notes: List<Note>,
    selectedFolderIds: Set<Long>,
    onApplyFilter: (Set<Long>) -> Unit,
    onDismiss: () -> Unit
) {
    var tempSelectedFolderIds by remember(selectedFolderIds) {
        mutableStateOf(selectedFolderIds)
    }

    val folderTree = remember(folders) {
        buildFolderTree(folders)
    }

    // Keep root/parent folders expanded by default
    var expandedFolderIds by remember(folders) {
        val parentIds = folders.filter { f ->
            folders.any { it.parentFolderId == f.id || (it.id != f.id && it.folderPath.startsWith(f.folderPath + java.io.File.separator)) }
        }.map { it.id }.toSet()
        mutableStateOf(parentIds)
    }

    val flattenedVisibleTree = remember(folderTree, expandedFolderIds) {
        flattenFolderTree(folderTree, expandedFolderIds)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Loc.getText("filter_folders_dialog_title"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (folders.isNotEmpty()) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "${tempSelectedFolderIds.size} / ${folders.size}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                // Quick Select All / Deselect All / Expand All / Collapse All
                if (folders.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    tempSelectedFolderIds = folders.map { it.id }.toSet()
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(Loc.getText("select_all"), fontSize = 12.sp)
                            }
                            TextButton(
                                onClick = {
                                    tempSelectedFolderIds = emptySet()
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(Loc.getText("deselect_all"), fontSize = 12.sp)
                            }
                        }

                        val allParentIds = folders.filter { f ->
                            folders.any { it.parentFolderId == f.id || (it.id != f.id && it.folderPath.startsWith(f.folderPath + java.io.File.separator)) }
                        }.map { it.id }.toSet()

                        if (allParentIds.isNotEmpty()) {
                            val isAllExpanded = expandedFolderIds.containsAll(allParentIds)
                            IconButton(
                                onClick = {
                                    expandedFolderIds = if (isAllExpanded) emptySet() else allParentIds
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (isAllExpanded) Icons.Filled.UnfoldLess else Icons.Filled.UnfoldMore,
                                    contentDescription = if (isAllExpanded) "Collapse All" else "Expand All",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }

                if (folders.isEmpty()) {
                    Text(
                        text = Loc.getText("empty_folders"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        items(flattenedVisibleTree, key = { it.folder.id }) { node ->
                            val folder = node.folder
                            val hasChildren = node.children.isNotEmpty()
                            val isExpanded = expandedFolderIds.contains(folder.id)
                            val subtreeIds = remember(node) { getAllNodeIds(node) }

                            val isChecked = tempSelectedFolderIds.contains(folder.id)
                            val allSubtreeChecked = subtreeIds.all { tempSelectedFolderIds.contains(it) }
                            val folderNotesCount = notes.count {
                                it.folderId == folder.id ||
                                (!it.folderName.isNullOrBlank() && it.folderName.equals(folder.folderName, ignoreCase = true))
                            }
                            val totalSubtreeNotesCount = remember(node, notes) { getFolderNotesCount(node, notes) }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = (node.depth * 18).dp)
                                    .clickable {
                                        if (hasChildren) {
                                            // Toggle folder + all subfolders if parent
                                            tempSelectedFolderIds = if (allSubtreeChecked) {
                                                tempSelectedFolderIds - subtreeIds
                                            } else {
                                                tempSelectedFolderIds + subtreeIds
                                            }
                                        } else {
                                            tempSelectedFolderIds = if (isChecked) {
                                                tempSelectedFolderIds - folder.id
                                            } else {
                                                tempSelectedFolderIds + folder.id
                                            }
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 3.dp),
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

                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            if (hasChildren) {
                                                tempSelectedFolderIds = if (checked) {
                                                    tempSelectedFolderIds + subtreeIds
                                                } else {
                                                    tempSelectedFolderIds - subtreeIds
                                                }
                                            } else {
                                                tempSelectedFolderIds = if (checked) {
                                                    tempSelectedFolderIds + folder.id
                                                } else {
                                                    tempSelectedFolderIds - folder.id
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    )

                                    Spacer(modifier = Modifier.width(4.dp))

                                    Icon(
                                        imageVector = if (hasChildren && isExpanded) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                                        contentDescription = null,
                                        tint = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))

                                    Text(
                                        text = folder.folderName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (node.depth == 0) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    // Notes count indicator
                                    val displayCount = if (hasChildren) totalSubtreeNotesCount else folderNotesCount
                                    if (displayCount > 0) {
                                        Surface(
                                            shape = CircleShape,
                                            color = if (isChecked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.padding(start = 4.dp)
                                        ) {
                                            Text(
                                                text = "$displayCount",
                                                fontSize = 11.sp,
                                                fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                color = if (isChecked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApplyFilter(tempSelectedFolderIds) },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(Loc.getText("apply_filter"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Loc.getText("cancel"))
            }
        }
    )
}
