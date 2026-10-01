package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NoteTagsSection(
    availableTags: List<String>,
    selectedTags: Set<String>,
    newTagInput: String,
    isNewTagFieldVisible: Boolean,
    isFavoriteTag: (String) -> Boolean,
    onSelectedTagsChange: (Set<String>) -> Unit,
    onNewTagInputChange: (String) -> Unit,
    onNewTagFieldVisibleChange: (Boolean) -> Unit
) {
    Text(
        text = Loc.getText("tags"),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(6.dp))

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Add New Tag Button
        item {
            AssistChip(
                onClick = { onNewTagFieldVisibleChange(!isNewTagFieldVisible) },
                label = { Text(Loc.getText("add_new_tag"), fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp)) }
            )
        }

        // Existing Tags suggestions (unique & active)
        availableTags.forEach { tagStr ->
            item(key = tagStr) {
                val isSelected = selectedTags.any { it.equals(tagStr, ignoreCase = true) }
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        val updated = if (isSelected) {
                            selectedTags.filterNot { it.equals(tagStr, ignoreCase = true) }.toSet()
                        } else {
                            selectedTags + tagStr
                        }
                        onSelectedTagsChange(updated)
                    },
                    label = { Text("#$tagStr", fontSize = 11.sp) }
                )
            }
        }
    }

    if (isNewTagFieldVisible) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = newTagInput,
                onValueChange = onNewTagInputChange,
                placeholder = { Text(Loc.getText("new_tag_name"), fontSize = 12.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            Button(
                onClick = {
                    val trimmed = newTagInput.trim()
                    if (trimmed.isNotEmpty()) {
                        if (!isFavoriteTag(trimmed) && selectedTags.none { it.equals(trimmed, ignoreCase = true) }) {
                            onSelectedTagsChange(selectedTags + trimmed)
                        }
                        onNewTagInputChange("")
                        onNewTagFieldVisibleChange(false)
                    }
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(Loc.getText("add"))
            }
        }
    }
}
