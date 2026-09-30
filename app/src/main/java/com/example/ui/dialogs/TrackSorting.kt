package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AudioTrack

fun getSortedTracks(tracks: List<AudioTrack>, sortBy: String, isAscending: Boolean): List<AudioTrack> {
    val sorted = when (sortBy) {
        "name" -> tracks.sortedBy { it.getDisplayTitle().lowercase() }
        "duration" -> tracks.sortedBy { it.duration }
        "progress" -> tracks.sortedBy { it.getProgressPercent() }
        "play_count" -> tracks.sortedBy { it.playCount }
        "date" -> tracks.sortedBy { it.id }
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
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showCount) {
            Text(
                text = String.format(Loc.getText("files_count"), totalCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Spacer(modifier = Modifier.width(1.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                TextButton(
                    onClick = { expanded = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    val sortLabel = when (sortBy) {
                        "name" -> Loc.getText("sort_name")
                        "duration" -> Loc.getText("sort_duration")
                        "progress" -> Loc.getText("sort_progress")
                        "play_count" -> Loc.getText("sort_play_count")
                        "date" -> Loc.getText("sort_date")
                        else -> Loc.getText("sort_by")
                    }
                    Text(
                        text = sortLabel,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    val sortOptions = listOf(
                        "name" to Loc.getText("sort_name"),
                        "duration" to Loc.getText("sort_duration"),
                        "progress" to Loc.getText("sort_progress"),
                        "play_count" to Loc.getText("sort_play_count"),
                        "date" to Loc.getText("sort_date")
                    )
                    sortOptions.forEach { (key, label) ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = label,
                                    fontWeight = if (sortBy == key) FontWeight.Bold else FontWeight.Normal,
                                    color = if (sortBy == key) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                onSortByChange(key)
                                expanded = false
                            }
                        )
                    }
                }
            }
            IconButton(
                onClick = { onIsAscendingChange(!isAscending) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isAscending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                    contentDescription = "Sort order",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
