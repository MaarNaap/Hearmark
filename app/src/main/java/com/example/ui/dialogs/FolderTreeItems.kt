package com.example.ui

import androidx.compose.foundation.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Folder

@Composable
fun SubfolderDetailsItem(
    sub: Folder,
    depth: Int = 0,
    viewModel: AppViewModel,
    onNavigate: (Long) -> Unit,
    onRenameFolder: (Folder) -> Unit = {},
    onCreateTaskForSource: (String, Long) -> Unit = { _, _ -> },
    onShowAssociatedTasks: (String, Long, String) -> Unit = { _, _, _ -> }
) {
    val subTracks by viewModel.repository.getTracksForFolderFlow(sub.id).collectAsStateWithLifecycle(emptyList())
    val childSubfolders by viewModel.getSubfolders(sub.id).collectAsStateWithLifecycle(emptyList())
    var isExpanded by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 16).dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigate(sub.id) }
                .testTag("subfolder_card_${sub.id}"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            RoundedCornerShape(12.dp)
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .then(
                            if (childSubfolders.isNotEmpty()) {
                                Modifier.clickable { isExpanded = !isExpanded }
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isExpanded && childSubfolders.isNotEmpty()) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                        contentDescription = "Folder",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    if (childSubfolders.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(2.dp)
                                .size(15.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sub.folderName,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val summaryParts = mutableListOf<String>()
                    if (subTracks.isNotEmpty()) {
                        summaryParts.add(Loc.getFormattedText("files_count", subTracks.size))
                    }
                    if (childSubfolders.isNotEmpty()) {
                        summaryParts.add(Loc.getFormattedText("folders_count", childSubfolders.size))
                    }
                    val folderSummaryText = summaryParts.joinToString(" • ")
                    if (folderSummaryText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = folderSummaryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Folder Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(Loc.getText("rename")) },
                            onClick = {
                                showMenu = false
                                onRenameFolder(sub)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(Loc.getText("create_task")) },
                            onClick = {
                                showMenu = false
                                onCreateTaskForSource("FOLDER", sub.id)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(Loc.getText("show_associated_tasks")) },
                            onClick = {
                                showMenu = false
                                onShowAssociatedTasks("FOLDER", sub.id, sub.folderName)
                            }
                        )
                    }
                }
            }
        }

        if (isExpanded && childSubfolders.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                childSubfolders.forEach { childSub ->
                    SubfolderDetailsItem(
                        sub = childSub,
                        depth = depth + 1,
                        viewModel = viewModel,
                        onNavigate = onNavigate,
                        onRenameFolder = onRenameFolder,
                        onCreateTaskForSource = onCreateTaskForSource,
                        onShowAssociatedTasks = onShowAssociatedTasks
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun FolderTreeNodeItem(
    folder: Folder,
    depth: Int = 0,
    viewModel: AppViewModel,
    isFolderBulkSelectMode: Boolean = false,
    selectedFolderIds: Set<Long> = emptySet(),
    onToggleSelectFolder: (Long) -> Unit = {},
    onFolderDetailsClicked: (Long) -> Unit = {},
    onLongClickFolder: (Long) -> Unit = {},
    onRenameFolder: (Folder) -> Unit = {},
    onCreateTaskForSource: (String, Long) -> Unit = { _, _ -> },
    onShowAssociatedTasks: (String, Long, String) -> Unit = { _, _, _ -> }
) {
    var showMenu by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }
    var showDeleteFolderConfirm by remember { mutableStateOf(false) }
    val isSelected = selectedFolderIds.contains(folder.id)
    val folderTracks by viewModel.repository.getTracksForFolderFlow(folder.id).collectAsStateWithLifecycle(emptyList())
    val childSubfolders by viewModel.getSubfolders(folder.id).collectAsStateWithLifecycle(emptyList())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 16).dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (isFolderBulkSelectMode) {
                            onToggleSelectFolder(folder.id)
                        } else {
                            onFolderDetailsClicked(folder.id)
                        }
                    },
                    onLongClick = {
                        onLongClickFolder(folder.id)
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
            border = BorderStroke(
                1.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isFolderBulkSelectMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelectFolder(folder.id) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                RoundedCornerShape(12.dp)
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (childSubfolders.isNotEmpty()) {
                                    Modifier.clickable { isExpanded = !isExpanded }
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isExpanded && childSubfolders.isNotEmpty()) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                            contentDescription = "Folder",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        if (childSubfolders.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(2.dp)
                                    .size(15.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folder.folderName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val summaryParts = mutableListOf<String>()
                    if (folderTracks.isNotEmpty()) {
                        summaryParts.add(Loc.getFormattedText("files_count", folderTracks.size))
                    }
                    if (childSubfolders.isNotEmpty()) {
                        summaryParts.add(Loc.getFormattedText("folders_count", childSubfolders.size))
                    }
                    val folderSummaryText = summaryParts.joinToString(" • ")
                    if (folderSummaryText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = folderSummaryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                if (!isFolderBulkSelectMode) {
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "Folder Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(Loc.getText("rename")) },
                                onClick = {
                                    showMenu = false
                                    onRenameFolder(folder)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(Loc.getText("create_task")) },
                                onClick = {
                                    showMenu = false
                                    onCreateTaskForSource("FOLDER", folder.id)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(Loc.getText("show_associated_tasks")) },
                                onClick = {
                                    showMenu = false
                                    onShowAssociatedTasks("FOLDER", folder.id, folder.folderName)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(Loc.getText("delete"), color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    showDeleteFolderConfirm = true
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showDeleteFolderConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteFolderConfirm = false },
                title = { Text(Loc.getText("delete_folder_title")) },
                text = { Text(Loc.getText("delete_folder_confirm")) },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteFolderConfirm = false
                            viewModel.deleteFolder(folder.id)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(Loc.getText("delete"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteFolderConfirm = false }) {
                        Text(Loc.getText("cancel"))
                    }
                }
            )
        }

        if (isExpanded && childSubfolders.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                childSubfolders.forEach { child ->
                    FolderTreeNodeItem(
                        folder = child,
                        depth = depth + 1,
                        viewModel = viewModel,
                        isFolderBulkSelectMode = isFolderBulkSelectMode,
                        selectedFolderIds = selectedFolderIds,
                        onToggleSelectFolder = onToggleSelectFolder,
                        onFolderDetailsClicked = onFolderDetailsClicked,
                        onLongClickFolder = onLongClickFolder,
                        onRenameFolder = onRenameFolder,
                        onCreateTaskForSource = onCreateTaskForSource,
                        onShowAssociatedTasks = onShowAssociatedTasks
                    )
                }
            }
        }
    }
}
