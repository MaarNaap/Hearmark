package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NotebookFilterChips(
    selectedFolderCount: Int,
    selectedTagCount: Int,
    onOpenFolderFilter: () -> Unit,
    onClearFolderFilter: () -> Unit,
    onOpenTagFilter: () -> Unit,
    onClearTagFilter: () -> Unit
) {
    val hasActiveFilterChips = selectedFolderCount > 0 || selectedTagCount > 0
    if (hasActiveFilterChips) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            // Folder Multi-selection Chip
            if (selectedFolderCount > 0) {
                item {
                    FilterChip(
                        selected = true,
                        onClick = onOpenFolderFilter,
                        label = {
                            Text(
                                text = "$selectedFolderCount ${Loc.getText("selected_folders_label")}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = onClearFolderFilter,
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(12.dp))
                            }
                        }
                    )
                }
            }

            // Tag Multi-selection Chip
            if (selectedTagCount > 0) {
                item {
                    FilterChip(
                        selected = true,
                        onClick = onOpenTagFilter,
                        label = {
                            Text(
                                text = "$selectedTagCount ${Loc.getText("selected_tags_label")}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.Tag, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = onClearTagFilter,
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(12.dp))
                            }
                        }
                    )
                }
            }
        }
    }
}
