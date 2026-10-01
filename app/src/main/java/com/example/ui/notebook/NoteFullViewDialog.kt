package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.example.data.AudioTrack
import com.example.data.Note

@Composable
fun NoteFullViewDialog(
    targetNote: Note,
    notes: List<Note>,
    isSnippetPlaying: Boolean,
    playingSnippetNoteId: Long?,
    snippetPosition: Long,
    noteQuestionCounts: Map<Long, Int>,
    tracks: List<AudioTrack>,
    context: Context,
    viewModel: AppViewModel,
    onPlayTrackInMainPlayer: (AudioTrack, Long) -> Unit,
    onNavigateToFolder: (Long) -> Unit,
    onNavigateToTrack: (AudioTrack) -> Unit,
    onSelectTagFilter: (String) -> Unit,
    onEditNoteClicked: (Note) -> Unit,
    onRequestDelete: (Note) -> Unit,
    onDismiss: () -> Unit
) {
    val liveNote = notes.find { it.id == targetNote.id } ?: targetNote
    val isThisSnippetPlaying = isSnippetPlaying && playingSnippetNoteId == liveNote.id

    ViewNoteDetailsModal(
        note = liveNote,
        isPlaying = isThisSnippetPlaying,
        currentPosition = if (isThisSnippetPlaying) snippetPosition else 0L,
        questionCount = noteQuestionCounts[liveNote.id] ?: 0,
        allTracks = tracks,
        onPlaySnippet = {
            if (isThisSnippetPlaying) {
                viewModel.stopNoteSnippet()
            } else {
                viewModel.playNoteSnippet(liveNote)
            }
        },
        onPlayInMainPlayer = { track, startMs ->
            onDismiss()
            onPlayTrackInMainPlayer(track, startMs)
        },
        onNavigateToFolder = { folderId ->
            onDismiss()
            onNavigateToFolder(folderId)
        },
        onNavigateToTrack = { track ->
            onDismiss()
            onNavigateToTrack(track)
        },
        onSelectTagFilter = { tag ->
            onDismiss()
            onSelectTagFilter(tag)
            Toast.makeText(context, "${Loc.getText("filter_by_tag")}: #$tag", Toast.LENGTH_SHORT).show()
        },
        onToggleFavorite = { viewModel.toggleNoteFavorite(liveNote) },
        onEdit = {
            val toEdit = liveNote
            onDismiss()
            onEditNoteClicked(toEdit)
        },
        onDelete = {
            val toDelete = liveNote
            onDismiss()
            onRequestDelete(toDelete)
        },
        onCopy = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipText = buildString {
                append(liveNote.text)
                if (liveNote.comment.isNotBlank()) {
                    append("\n\n")
                    append(liveNote.comment)
                }
                if (liveNote.trackName != null) {
                    append("\n— ${liveNote.trackName}")
                    if (liveNote.startTimestampMs > 0) {
                        append(" (${formatDuration(liveNote.startTimestampMs)})")
                    }
                }
            }
            val clip = ClipData.newPlainText("Hearmark Note", clipText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, Loc.getText("note_copied"), Toast.LENGTH_SHORT).show()
        },
        onCreateQuizQuestion = {
            val noteForQuiz = liveNote
            onDismiss()
            val associatedTrack = if (noteForQuiz.trackId != null) tracks.find { it.id == noteForQuiz.trackId } else null
            viewModel.openAiHub(
                function = AiFunctionType.QUIZ,
                track = associatedTrack,
                notes = listOf(noteForQuiz)
            )
        },
        onDismiss = onDismiss
    )
}

@Composable
fun NoteDeleteConfirmationDialog(
    noteToDelete: Note,
    onConfirmDelete: (Note) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Loc.getText("delete_note")) },
        text = { Text(Loc.getText("confirm_delete_note")) },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmDelete(noteToDelete)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(Loc.getText("delete"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Loc.getText("cancel"))
            }
        }
    )
}
