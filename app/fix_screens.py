import os

with open('app/src/main/java/com/example/ui/Screens.kt', 'r', encoding='utf-8', errors='ignore') as f:
    text = f.read()

target = 'fun middleEllipse(text: String, maxLength: Int = 30): String {'
idx = text.find(target)
if idx == -1:
    print('Target not found!')
    exit(1)

clean_prefix = text[:idx]

clean_suffix = """fun middleEllipse(text: String, maxLength: Int = 30): String {
    if (text.length <= maxLength) return text
    
    val extIndex = text.lastIndexOf('.')
    val hasExtension = extIndex != -1 && (text.length - extIndex) <= 6
    
    val extension = if (hasExtension) text.substring(extIndex) else ""
    val base = if (hasExtension) text.substring(0, extIndex) else text
    
    val availableSpace = maxLength - 3 - extension.length
    if (availableSpace <= 0) {
        return text.take(maxLength - 3) + "..."
    }
    
    val half = availableSpace / 2
    val firstPart = base.take(half)
    val secondPart = base.takeLast(availableSpace - half)
    return "$firstPart...$secondPart$extension"
}

@Composable
fun SmartFileNameText(
    fileName: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    maxLength: Int = 38
) {
    val displayName = remember(fileName, maxLength) {
        val text = fileName.trim()
        if (text.length <= maxLength) {
            text
        } else {
            val extIndex = text.lastIndexOf('.')
            val hasExtension = extIndex != -1 && (text.length - extIndex) <= 6
            val extension = if (hasExtension) text.substring(extIndex) else ""
            val base = if (hasExtension) text.substring(0, extIndex) else text
            val availableSpace = maxLength - 3 - extension.length
            if (availableSpace > 0) {
                base.take(availableSpace) + "..." + extension
            } else {
                text.take(maxLength - 3) + "..."
            }
        }
    }

    Text(
        text = displayName,
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

fun getSortedTracks(tracks: List<AudioTrack>, sortBy: String, isAscending: Boolean): List<AudioTrack> {
    val sorted = when (sortBy) {
        "name" -> tracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.fileName })
        "duration" -> tracks.sortedBy { it.duration }
        "date_added" -> tracks.sortedBy { it.id }
        "play_count" -> tracks.sortedBy { it.playCount }
        "progress" -> tracks.sortedBy { it.getProgressPercent() }
        else -> tracks
    }
    return if (isAscending) sorted else sorted.reversed()
}

@Composable
fun TrackListSortHeader(
    totalCount: Int,
    sortBy: String,
    onSortByChange: (String) -> Unit,
    isAscending: Boolean,
    onIsAscendingChange: (Boolean) -> Unit,
    showCount: Boolean = true,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showCount) {
            Text(
                text = "$totalCount ${Loc.getText(\"audio_files\")}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Spacer(modifier = Modifier.width(1.dp))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box {
                TextButton(
                    onClick = { showSortMenu = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Sort,
                        contentDescription = Loc.getText("sort_by"),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when (sortBy) {
                            "name" -> Loc.getText("sort_name")
                            "duration" -> Loc.getText("sort_duration")
                            "date_added" -> Loc.getText("sort_date")
                            "play_count" -> Loc.getText("sort_plays")
                            "progress" -> Loc.getText("sort_progress")
                            else -> Loc.getText("sort_name")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(Loc.getText("sort_name")) },
                        onClick = {
                            onSortByChange("name")
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(Loc.getText("sort_duration")) },
                        onClick = {
                            onSortByChange("duration")
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(Loc.getText("sort_date")) },
                        onClick = {
                            onSortByChange("date_added")
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(Loc.getText("sort_plays")) },
                        onClick = {
                            onSortByChange("play_count")
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(Loc.getText("sort_progress")) },
                        onClick = {
                            onSortByChange("progress")
                            showSortMenu = false
                        }
                    )
                }
            }

            IconButton(
                onClick = { onIsAscendingChange(!isAscending) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isAscending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                    contentDescription = if (isAscending) Loc.getText("ascending") else Loc.getText("descending"),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FolderTreeNodeItem(
    folder: Folder,
    depth: Int = 0,
    viewModel: AppViewModel,
    isFolderBulkSelectMode: Boolean,
    selectedFolderIds: Set<Long>,
    onToggleSelectFolder: (Long) -> Unit,
    onFolderDetailsClicked: (Long) -> Unit,
    onLongClickFolder: (Long) -> Unit,
    onRenameFolder: (Folder) -> Unit,
    onCreateTaskForSource: (String, Long) -> Unit,
    onShowAssociatedTasks: (String, Long, String) -> Unit
) {
    val subfolders by viewModel.getSubfolders(folder.id).collectAsStateWithLifecycle(emptyList())
    val tracks by viewModel.repository.getTracksForFolderFlow(folder.id).collectAsStateWithLifecycle(emptyList())
    var isExpanded by remember { mutableStateOf(false) }
    val isSelected = selectedFolderIds.contains(folder.id)
    var showFolderMenu by remember { mutableStateOf(false) }

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
                )
                .testTag("folder_node_${folder.id}"),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                }
            ),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(
                1.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isFolderBulkSelectMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelectFolder(folder.id) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = "Folder",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folder.folderName,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${tracks.size} ${Loc.getText("tracks_tab")} • ${subfolders.size} ${Loc.getText("folders")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (subfolders.isNotEmpty()) {
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { showFolderMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showFolderMenu,
                        onDismissRequest = { showFolderMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(Loc.getText("edit_folder_name")) },
                            leadingIcon = { Icon(Icons.Filled.Edit, null) },
                            onClick = {
                                showFolderMenu = false
                                onRenameFolder(folder)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(Loc.getText("create_task_action")) },
                            leadingIcon = { Icon(Icons.Filled.Bookmark, null) },
                            onClick = {
                                showFolderMenu = false
                                onCreateTaskForSource("FOLDER", folder.id)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(Loc.getText("view_associated_tasks_action")) },
                            leadingIcon = { Icon(Icons.Filled.ListAlt, null) },
                            onClick = {
                                showFolderMenu = false
                                onShowAssociatedTasks("FOLDER", folder.id, folder.folderName)
                            }
                        )
                    }
                }
            }
        }

        if (isExpanded) {
            Spacer(modifier = Modifier.height(6.dp))
            subfolders.forEach { subfolder ->
                FolderTreeNodeItem(
                    folder = subfolder,
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
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}
"""

with open('app/src/main/java/com/example/ui/Screens.kt', 'w', encoding='utf-8-sig') as f:
    f.write(clean_prefix + clean_suffix)
print('Successfully fixed Screens.kt')
