package com.example.ui.quiz

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Note
import com.example.ui.Loc

internal fun LazyListScope.quizNoteSelectorItems(
    allNotes: List<Note>,
    filteredNotes: List<Note>,
    allTags: List<String>,
    selectedNoteIds: Set<Long>,
    selectedFolderIds: Set<Long> = emptySet(),
    selectedTrackIds: Set<Long> = emptySet(),
    selectedTagFilter: String?,
    isNotesExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onOpenFolderFileFilter: () -> Unit = {},
    onClearFolderFileFilter: () -> Unit = {},
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onSelectTagFilter: (String?) -> Unit,
    onToggleNoteSelection: (Long, Boolean) -> Unit
) {
    val totalFilterCount = selectedFolderIds.size + selectedTrackIds.size

    // Note selection list header & toggle
    item {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpanded() }
                .testTag("notebook_quiz_notes_toggle_card")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = Loc.getText("notebook_quiz_target_notes"),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        val subtitleText = if (totalFilterCount > 0 || selectedTagFilter != null) {
                            "${selectedNoteIds.size}/${allNotes.size} ${Loc.getText("selected")} • ${filteredNotes.size} ${Loc.getText("active_days_count")?.let { "" } ?: ""}${Loc.getText("matches") ?: "matched"}"
                        } else {
                            "${selectedNoteIds.size}/${allNotes.size} ${Loc.getText("selected")}"
                        }
                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.5.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Filter icon button for folders and files
                    IconButton(
                        onClick = onOpenFolderFileFilter,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("quiz_notes_filter_btn")
                    ) {
                        BadgedBox(
                            badge = {
                                if (totalFilterCount > 0) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ) {
                                        Text(totalFilterCount.toString(), fontSize = 10.sp)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FilterList,
                                contentDescription = Loc.getText("filter_folder_file_dialog_title"),
                                tint = if (totalFilterCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (isNotesExpanded) {
                        TextButton(
                            onClick = onSelectAll,
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Text(Loc.getText("notebook_quiz_select_all"), fontSize = 11.sp)
                        }
                        TextButton(
                            onClick = onDeselectAll,
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Text(Loc.getText("notebook_quiz_deselect_all"), fontSize = 11.sp)
                        }
                    }
                    IconButton(
                        onClick = onToggleExpanded,
                        modifier = Modifier.size(32.dp).testTag("notebook_quiz_notes_expand_icon")
                    ) {
                        Icon(
                            imageVector = if (isNotesExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (isNotesExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Expandable notes content (folder/file filter chip, tag chips and full list of notes)
    if (isNotesExpanded) {
        // Active Filter Chips & Tag Chips
        if (totalFilterCount > 0 || allTags.isNotEmpty()) {
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Active Folder and/or File Filter Chip
                    if (totalFilterCount > 0) {
                        item {
                            val labelText = when {
                                selectedFolderIds.isNotEmpty() && selectedTrackIds.isEmpty() ->
                                    "${selectedFolderIds.size} ${Loc.getText("selected_folders_label")}"
                                selectedFolderIds.isEmpty() && selectedTrackIds.isNotEmpty() ->
                                    "${selectedTrackIds.size} ${Loc.getText("selected_files_label")}"
                                else ->
                                    "$totalFilterCount ${Loc.getText("selected_folders_files_label")}"
                            }

                            val leadingIconVector = when {
                                selectedFolderIds.isNotEmpty() && selectedTrackIds.isEmpty() -> Icons.Filled.Folder
                                selectedFolderIds.isEmpty() && selectedTrackIds.isNotEmpty() -> Icons.AutoMirrored.Filled.InsertDriveFile
                                else -> Icons.Filled.FilterList
                            }

                            FilterChip(
                                selected = true,
                                onClick = onOpenFolderFileFilter,
                                label = {
                                    Text(
                                        text = labelText,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                leadingIcon = {
                                    Icon(leadingIconVector, contentDescription = null, modifier = Modifier.size(13.dp))
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = onClearFolderFileFilter,
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(12.dp))
                                    }
                                }
                            )
                        }
                    }

                    // All Tags chip
                    item {
                        FilterChip(
                            selected = selectedTagFilter == null,
                            onClick = { onSelectTagFilter(null) },
                            label = { Text(Loc.getText("notebook_quiz_all_tags"), fontSize = 11.5.sp) }
                        )
                    }
                    items(allTags, key = { it }) { tag ->
                        FilterChip(
                            selected = selectedTagFilter == tag,
                            onClick = { onSelectTagFilter(if (selectedTagFilter == tag) null else tag) },
                            label = { Text("#$tag", fontSize = 11.5.sp) }
                        )
                    }
                }
            }
        }

        if (filteredNotes.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = Loc.getText("no_notes_found"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(filteredNotes, key = { it.id }) { note ->
                val isChecked = selectedNoteIds.contains(note.id)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(0.5.dp, if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onToggleNoteSelection(note.id, !isChecked)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                onToggleNoteSelection(note.id, checked)
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(note.text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (note.comment.isNotBlank()) {
                                Text(note.comment, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}
