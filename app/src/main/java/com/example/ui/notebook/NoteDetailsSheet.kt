package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AudioTrack
import com.example.data.Note
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackCueNotesBottomSheet(
    notes: List<Note>,
    noteQuestionCounts: Map<Long, Int> = emptyMap(),
    onDismiss: () -> Unit,
    onEditNote: (Note) -> Unit,
    onDeleteNote: (Note) -> Unit,
    onToggleFavorite: (Note) -> Unit,
    onCopyNote: (Note) -> Unit
) {
    val favTag = Loc.getText("favorite_tag_name")
    var noteToDelete by remember { mutableStateOf<Note?>(null) }

    if (noteToDelete != null) {
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text(Loc.getText("delete_note")) },
            text = { Text(Loc.getText("confirm_delete_note")) },
            confirmButton = {
                Button(
                    onClick = {
                        noteToDelete?.let { onDeleteNote(it) }
                        noteToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(Loc.getText("delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) {
                    Text(Loc.getText("cancel"))
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Row: Title & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.EditNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = if (notes.size > 1) {
                            "${Loc.getText("notes")} (${notes.size})"
                        } else {
                            Loc.getText("note_comment")
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Stack of Notes: Explanation & Personal Notes only + compact action row
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                notes.forEachIndexed { index, note ->
                    val isFavorite = remember(note.tags, favTag) {
                        note.getTagsList().any { it.equals("favorite", ignoreCase = true) || it == "المفضلة" || it == favTag }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            // Body: Explanation & Personal Notes ONLY
                            val commentText = note.comment.trim()
                            if (commentText.isNotBlank()) {
                                Text(
                                    text = commentText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 22.sp
                                )
                            } else {
                                Text(
                                    text = Loc.getText("no_comment_fallback"),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    lineHeight = 20.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                thickness = 0.5.dp
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            // Compact Action Buttons Row for this note individually
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (notes.size > 1) {
                                    Text(
                                        text = "#${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    Spacer(modifier = Modifier.width(1.dp))
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val qCount = noteQuestionCounts[note.id] ?: 0
                                    if (qCount > 0) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier
                                                .height(22.dp)
                                                .padding(horizontal = 2.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(horizontal = 7.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "$qCount",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }

                                    // Favorite Toggle
                                    IconButton(
                                        onClick = { onToggleFavorite(note) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                            contentDescription = Loc.getText("favorite"),
                                            tint = if (isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }

                                    // Copy Note
                                    IconButton(
                                        onClick = { onCopyNote(note) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.ContentCopy,
                                            contentDescription = Loc.getText("copy_note"),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Edit Note
                                    IconButton(
                                        onClick = { onEditNote(note) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = Loc.getText("edit_note"),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Delete Note
                                    IconButton(
                                        onClick = { noteToDelete = note },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = Loc.getText("delete_note"),
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewNoteDetailsModal(
    note: Note,
    isPlaying: Boolean,
    currentPosition: Long,
    questionCount: Int = 0,
    allTracks: List<AudioTrack>,
    onPlaySnippet: () -> Unit,
    onPlayInMainPlayer: (AudioTrack, Long) -> Unit,
    onNavigateToFolder: (Long) -> Unit,
    onNavigateToTrack: (AudioTrack) -> Unit,
    onSelectTagFilter: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onCreateQuizQuestion: () -> Unit = {},
    onRelinkTrack: ((AudioTrack) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val favTag = Loc.getText("favorite_tag_name")
    val isFavorite = remember(note.tags, favTag) {
        note.getTagsList().any { it.equals("favorite", ignoreCase = true) || it == "المفضلة" || it == favTag }
    }
    val isDetached = note.trackId == null && !note.trackName.isNullOrBlank()
    var showRelinkPicker by remember { mutableStateOf(false) }

    val associatedTrack = remember(note.trackId, note.trackName, allTracks) {
        if (note.trackId != null) {
            allTracks.find { it.id == note.trackId }
        } else if (!note.trackName.isNullOrBlank()) {
            val cleanName = note.trackName.trim().lowercase(java.util.Locale.ROOT)
            allTracks.find {
                val fn = it.fileName.trim().lowercase(java.util.Locale.ROOT)
                fn == cleanName || fn.removeSuffix(".mp3") == cleanName.removeSuffix(".mp3")
            }
        } else null
    }
    val effectiveFolderId = note.folderId ?: associatedTrack?.parentFolderId
    val effectiveFolderName = note.folderName

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Row: Title & Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = Loc.getText("note_details"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onCreateQuizQuestion,
                        modifier = Modifier.testTag("view_note_quiz_header_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = Loc.getText("notebook_quiz_single_note_btn"),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (questionCount > 0) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .height(22.dp)
                                .padding(end = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$questionCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Quote / Sentence Section (Full Text)
            Text(
                text = Loc.getText("note_text"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .width(4.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        text = note.text,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 24.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Explanation / Personal Notes Section (Full Text)
            if (note.comment.isNotBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = Loc.getText("note_comment"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = note.comment,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            // Linked Audio Track & Actions (Snippet playback, Main Player, Open in Folder)
            if (note.trackId != null || note.trackName != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            if (effectiveFolderId != null) {
                                onNavigateToFolder(effectiveFolderId)
                            } else if (associatedTrack != null) {
                                onNavigateToTrack(associatedTrack)
                            }
                        },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        // Track Info Row (Clickable to navigate to file in Library/Folder)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val icon = if (associatedTrack != null) {
                                        getTrackFileIcon(associatedTrack)
                                    } else {
                                        getTrackFileIcon(note.trackName ?: "")
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = associatedTrack?.fileName ?: note.trackName ?: Loc.getText("linked_audio_clip"),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isDetached) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                                            ) {
                                                Text(
                                                    text = Loc.getText("audio_removed_badge"),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (note.startTimestampMs > 0 || note.endTimestampMs > 0) {
                                            Text(
                                                text = "${formatDuration(note.startTimestampMs)} - ${formatDuration(note.endTimestampMs)}",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (!effectiveFolderName.isNullOrBlank()) {
                                            Text(
                                                text = "• ${effectiveFolderName}",
                                                fontSize = 11.5.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }

                            // Open in folder / library indicator or Relink action
                            if (isDetached) {
                                TextButton(
                                    onClick = { showRelinkPicker = true },
                                    modifier = Modifier.testTag("details_relink_note_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Link,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(Loc.getText("relink_note_action"), fontSize = 12.sp)
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = Loc.getText("open_in_library"),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (!isDetached) {
                            Spacer(modifier = Modifier.height(10.dp))

                            // Audio Action Buttons: Isolated Snippet Player & Full Main Player
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. Isolated Note Snippet Player Toggle
                                FilledTonalButton(
                                    onClick = onPlaySnippet,
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                        contentColor = if (isPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isPlaying) Loc.getText("pause_clip") else Loc.getText("play_clip"),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                // 2. Play Full Audio in Main Player
                                if (associatedTrack != null) {
                                    OutlinedButton(
                                        onClick = {
                                            onPlayInMainPlayer(associatedTrack, note.startTimestampMs)
                                        },
                                        modifier = Modifier.weight(1f).height(38.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.primary
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.PlayCircleOutline,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = Loc.getText("play_in_main_player"),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Interactive Tags & Folder Info Chips
            val tagsList = note.getTagsList().filter { !it.equals("favorite", ignoreCase = true) && it != "المفضلة" && it != favTag }
            if (tagsList.isNotEmpty() || !effectiveFolderName.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Clickable Folder Chip -> Navigates to folder
                    if (!effectiveFolderName.isNullOrBlank()) {
                        item {
                            SuggestionChip(
                                onClick = {
                                    if (effectiveFolderId != null) {
                                        onNavigateToFolder(effectiveFolderId)
                                    } else if (associatedTrack != null) {
                                        onNavigateToTrack(associatedTrack)
                                    }
                                },
                                icon = {
                                    Icon(
                                        Icons.Filled.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = effectiveFolderName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                            )
                        }
                    }

                    // Clickable Tag Chips -> Filters notebook by tag
                    items(tagsList, key = { it }) { t ->
                        SuggestionChip(
                            onClick = {
                                onSelectTagFilter(t)
                            },
                            icon = {
                                Icon(
                                    Icons.Filled.Tag,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = t,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )
                    }
                }
            }

            // Date / Timestamp
            if (note.createdAt > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                Text(
                    text = "${Loc.getText("created_at")}: ${sdf.format(Date(note.createdAt))}",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons Bar (Edit, Favorite, Copy, Delete)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Edit Button
                Button(
                    onClick = onEdit,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("view_note_edit_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(Loc.getText("edit_note"), fontWeight = FontWeight.Bold)
                }

                // Extract / Generate Quiz Button
                FilledTonalIconButton(
                    onClick = onCreateQuizQuestion,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("view_note_quiz_bottom_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = Loc.getText("notebook_quiz_single_note_btn"),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Favorite Button
                FilledTonalIconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = Loc.getText("favorite"),
                        tint = if (isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Copy Button
                FilledTonalIconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = Loc.getText("copy_note"))
                }

                // Delete Button
                FilledTonalIconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = Loc.getText("delete_note"))
                }
            }
        }
    }

    if (showRelinkPicker) {
        com.example.ui.dialogs.TrackPickerForNoteRelinkDialog(
            tracks = allTracks,
            noteTitle = note.text,
            onTrackSelected = { selectedTrack ->
                showRelinkPicker = false
                onRelinkTrack?.invoke(selectedTrack)
            },
            onDismiss = { showRelinkPicker = false }
        )
    }
}
