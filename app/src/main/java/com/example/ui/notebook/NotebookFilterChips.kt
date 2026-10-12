package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
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
    selectedFolderCount: Int = 0,
    selectedTrackCount: Int = 0,
    selectedTagCount: Int,
    onOpenFilter: () -> Unit,
    onClearFolderTrackFilter: () -> Unit,
    onOpenTagFilter: () -> Unit,
    onClearTagFilter: () -> Unit,
    onOpenFolderFilter: () -> Unit = onOpenFilter,
    onClearFolderFilter: () -> Unit = onClearFolderTrackFilter
) {
    val totalFolderTrackCount = selectedFolderCount + selectedTrackCount
    val hasActiveFilterChips = totalFolderTrackCount > 0 || selectedTagCount > 0

    if (hasActiveFilterChips) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            // Folder and/or File Multi-selection Chip
            if (totalFolderTrackCount > 0) {
                item {
                    val labelText = when {
                        selectedFolderCount > 0 && selectedTrackCount == 0 ->
                            "$selectedFolderCount ${Loc.getText("selected_folders_label")}"
                        selectedFolderCount == 0 && selectedTrackCount > 0 ->
                            "$selectedTrackCount ${Loc.getText("selected_files_label")}"
                        else ->
                            "$totalFolderTrackCount ${Loc.getText("selected_folders_files_label")}"
                    }

                    val leadingIconVector = when {
                        selectedFolderCount > 0 && selectedTrackCount == 0 -> Icons.Filled.Folder
                        selectedFolderCount == 0 && selectedTrackCount > 0 -> Icons.AutoMirrored.Filled.InsertDriveFile
                        else -> Icons.Filled.FilterList
                    }

                    FilterChip(
                        selected = true,
                        onClick = onOpenFilter,
                        label = {
                            Text(
                                text = labelText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        leadingIcon = {
                            Icon(leadingIconVector, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = onClearFolderTrackFilter,
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
