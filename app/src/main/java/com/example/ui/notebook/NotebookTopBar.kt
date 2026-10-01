package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NotebookTopBar(
    searchQuery: String,
    isSearchExpanded: Boolean,
    selectedFolderCount: Int,
    selectedTagCount: Int,
    onSearchQueryChange: (String) -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit,
    onOpenFolderFilter: () -> Unit,
    onOpenTagFilter: () -> Unit,
    onAddNoteClicked: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isSearchExpanded) Arrangement.Start else Arrangement.End
    ) {
        if (isSearchExpanded) {
            // Expanded Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .testTag("search_notes_field"),
                placeholder = { Text(Loc.getText("search_notes_placeholder"), fontSize = 13.sp) },
                leadingIcon = {
                    IconButton(onClick = {
                        onSearchExpandedChange(false)
                        onSearchQueryChange("")
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Folder Filter Button
            IconButton(
                onClick = onOpenFolderFilter,
                modifier = Modifier.testTag("filter_folders_btn")
            ) {
                BadgedBox(
                    badge = {
                        if (selectedFolderCount > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Text(selectedFolderCount.toString(), fontSize = 10.sp)
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.FilterList,
                        contentDescription = Loc.getText("filter_folders_dialog_title"),
                        tint = if (selectedFolderCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Tag Filter Button
            IconButton(
                onClick = onOpenTagFilter,
                modifier = Modifier.testTag("filter_tags_btn")
            ) {
                BadgedBox(
                    badge = {
                        if (selectedTagCount > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ) {
                                Text(selectedTagCount.toString(), fontSize = 10.sp)
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tag,
                        contentDescription = Loc.getText("filter_tags_dialog_title"),
                        tint = if (selectedTagCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Top Action Icons (Search, Filter Folders, Filter Tags, Add Note Icon)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Expandable Search Button
                IconButton(
                    onClick = { onSearchExpandedChange(true) },
                    modifier = Modifier.testTag("search_notes_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search",
                        tint = if (searchQuery.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Filter by Folders Icon Button
                IconButton(
                    onClick = onOpenFolderFilter,
                    modifier = Modifier.testTag("filter_folders_btn")
                ) {
                    BadgedBox(
                        badge = {
                            if (selectedFolderCount > 0) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Text(selectedFolderCount.toString(), fontSize = 10.sp)
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FilterList,
                            contentDescription = Loc.getText("filter_folders_dialog_title"),
                            tint = if (selectedFolderCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Filter by Tags Icon Button
                IconButton(
                    onClick = onOpenTagFilter,
                    modifier = Modifier.testTag("filter_tags_btn")
                ) {
                    BadgedBox(
                        badge = {
                            if (selectedTagCount > 0) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Text(selectedTagCount.toString(), fontSize = 10.sp)
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tag,
                            contentDescription = Loc.getText("filter_tags_dialog_title"),
                            tint = if (selectedTagCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Single Add Note Icon Button
                FilledTonalIconButton(
                    onClick = onAddNoteClicked,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("add_note_top_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.EditNote,
                        contentDescription = Loc.getText("add_note"),
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
