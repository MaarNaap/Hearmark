package com.example.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DeleteOptions
import com.example.ui.Loc

/**
 * Modern Material 3 deletion confirmation dialog featuring three independent checkboxes:
 * - Notes (count) - unchecked by default (keep / detach)
 * - Comprehension questions (count) - unchecked by default (keep / detach)
 * - Vocabulary questions (count) - unchecked by default (keep / detach)
 *
 * For virtual scenes, notes belong to the parent file and are not touched, so only
 * the comprehension checkbox is displayed, with a reassuring notice confirming notes remain intact.
 */
@Composable
fun ConfirmDeleteTrackDialog(
    title: String,
    desc: String,
    isScene: Boolean = false,
    noteCount: Int = 0,
    comprehensionCount: Int = 0,
    vocabCount: Int = 0,
    onConfirmDelete: (DeleteOptions) -> Unit,
    onDismiss: () -> Unit
) {
    var deleteNotes by remember { mutableStateOf(false) }
    var deleteComprehension by remember { mutableStateOf(false) }
    var deleteVocabulary by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = desc,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )

                if (isScene) {
                    // Confirmation that notes are safely anchored to parent track
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = Loc.getText("scene_notes_safe_hint"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    // Only show comprehension question option for scenes
                    if (comprehensionCount > 0) {
                        DeleteOptionCheckboxRow(
                            checked = deleteComprehension,
                            onCheckedChange = { deleteComprehension = it },
                            label = Loc.getFormattedText("delete_option_comprehension", comprehensionCount),
                            testTag = "checkbox_delete_comprehension"
                        )
                    }
                } else {
                    // Standard track or bulk delete: show all 3 independent checkboxes
                    val hasAnyOptions = noteCount > 0 || comprehensionCount > 0 || vocabCount > 0

                    if (hasAnyOptions) {
                        Text(
                            text = Loc.getText("delete_options_keep_hint"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.5.sp
                        )

                        if (noteCount > 0) {
                            DeleteOptionCheckboxRow(
                                checked = deleteNotes,
                                onCheckedChange = { deleteNotes = it },
                                label = Loc.getFormattedText("delete_option_notes", noteCount),
                                testTag = "checkbox_delete_notes"
                            )
                        }

                        if (comprehensionCount > 0) {
                            DeleteOptionCheckboxRow(
                                checked = deleteComprehension,
                                onCheckedChange = { deleteComprehension = it },
                                label = Loc.getFormattedText("delete_option_comprehension", comprehensionCount),
                                testTag = "checkbox_delete_comprehension"
                            )
                        }

                        if (vocabCount > 0) {
                            DeleteOptionCheckboxRow(
                                checked = deleteVocabulary,
                                onCheckedChange = { deleteVocabulary = it },
                                label = Loc.getFormattedText("delete_option_vocabulary", vocabCount),
                                testTag = "checkbox_delete_vocabulary"
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmDelete(
                        DeleteOptions(
                            deleteNotes = if (isScene) false else deleteNotes,
                            deleteComprehension = deleteComprehension,
                            deleteVocabulary = if (isScene) false else deleteVocabulary
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("confirm_delete_dialog_btn")
            ) {
                Text(Loc.getText("delete"))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_delete_dialog_btn")
            ) {
                Text(Loc.getText("cancel"))
            }
        }
    )
}

@Composable
private fun DeleteOptionCheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (checked) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(testTag),
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.error
                )
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
