package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.AudioTrack
import com.example.data.Note

@Composable
fun NotebookNotesList(
    filteredNotes: List<Note>,
    isSnippetPlaying: Boolean,
    playingSnippetNoteId: Long?,
    snippetPosition: Long,
    noteQuestionCounts: Map<Long, Int>,
    tracks: List<AudioTrack>,
    context: Context,
    viewModel: AppViewModel,
    onAddNoteClicked: () -> Unit,
    onViewNote: (Note) -> Unit,
    onEditNoteClicked: (Note) -> Unit,
    onDeleteNote: (Note) -> Unit
) {
    if (filteredNotes.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 100.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = Loc.getText("no_notes_found"),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onAddNoteClicked,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(Loc.getText("add_note"))
                }
            }
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 140.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredNotes, key = { it.id }) { note ->
                val isThisSnippetPlaying = isSnippetPlaying && playingSnippetNoteId == note.id
                val qCount = noteQuestionCounts[note.id] ?: 0

                NoteCard(
                    note = note,
                    isPlaying = isThisSnippetPlaying,
                    currentPosition = if (isThisSnippetPlaying) snippetPosition else 0L,
                    questionCount = qCount,
                    onPlaySnippet = {
                        if (isThisSnippetPlaying) {
                            viewModel.stopNoteSnippet()
                        } else {
                            viewModel.playNoteSnippet(note)
                        }
                    },
                    onToggleFavorite = { viewModel.toggleNoteFavorite(note) },
                    onView = { onViewNote(note) },
                    onEdit = { onEditNoteClicked(note) },
                    onDelete = { onDeleteNote(note) },
                    onCopy = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clipText = buildString {
                            append(note.text)
                            if (note.comment.isNotBlank()) {
                                append("\n\n")
                                append(note.comment)
                            }
                            if (note.trackName != null) {
                                append("\n— ${note.trackName}")
                                if (note.startTimestampMs > 0) {
                                    append(" (${formatDuration(note.startTimestampMs)})")
                                }
                            }
                        }
                        val clip = ClipData.newPlainText("Hearmark Note", clipText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, Loc.getText("note_copied"), Toast.LENGTH_SHORT).show()
                    },
                    onCreateQuizQuestion = {
                        val associatedTrack = if (note.trackId != null) tracks.find { it.id == note.trackId } else null
                        viewModel.openAiHub(
                            function = AiFunctionType.QUIZ,
                            track = associatedTrack,
                            notes = listOf(note)
                        )
                    }
                )
            }
        }
    }
}
