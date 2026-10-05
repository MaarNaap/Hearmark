package com.example.ui

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.player.NoteAudioPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

// --- NOTEBOOK OPERATIONS EXTENSIONS ---
fun AppViewModel.saveNote(
    id: Long = 0L,
    text: String,
    comment: String,
    trackId: Long?,
    startTimestampMs: Long,
    endTimestampMs: Long,
    originStartMs: Long? = null,
    tags: List<String>,
    targetWord: String? = null,
    meaning: String? = null,
    contextSentence: String? = null,
    onSuccess: (() -> Unit)? = null
) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            var resolvedTrackName: String? = null
            var resolvedFolderId: Long? = null
            var resolvedFolderName: String? = null

            if (trackId != null) {
                val track = tracks.value.find { it.id == trackId }
                    ?: repository.dao.getTrackById(trackId)
                if (track != null) {
                    resolvedTrackName = track.fileName
                    resolvedFolderId = track.parentFolderId
                    if (resolvedFolderId != null) {
                        val folder = folders.value.find { it.id == resolvedFolderId }
                            ?: repository.dao.getFolderById(resolvedFolderId)
                        resolvedFolderName = folder?.folderName
                    }
                }
            }

            val favTag = Loc.getText("favorite_tag_name")
            val isFavoriteTag = { t: String ->
                t.equals("favorite", ignoreCase = true) || t == "المفضلة" || t.equals(favTag, ignoreCase = true)
            }

            // If editing existing note, preserve its favorite status
            val existingNote = if (id != 0L) notes.value.find { it.id == id } else null
            val wasFavorite = existingNote?.getTagsList()?.any { isFavoriteTag(it) } == true

            val cleanUserTags = tags.map { it.trim() }.filter { it.isNotEmpty() && !isFavoriteTag(it) }
            val finalTagsList = if (wasFavorite) {
                listOf(favTag) + cleanUserTags
            } else {
                cleanUserTags
            }
            val tagsString = finalTagsList.distinctBy { it.lowercase() }.joinToString(",")

            val resolvedWord = targetWord?.trim()?.ifEmpty { null }
                ?: (if (id != 0L) existingNote?.targetWord else null)
            val resolvedMeaning = meaning?.trim()?.ifEmpty { null }
                ?: (if (id != 0L) existingNote?.meaning else null)
            val resolvedContext = contextSentence?.trim()?.ifEmpty { null }
                ?: (if (id != 0L) existingNote?.contextSentence else null)

            val note = Note(
                id = id,
                text = text.trim(),
                comment = comment.trim(),
                trackId = trackId,
                trackName = resolvedTrackName,
                folderId = resolvedFolderId,
                folderName = resolvedFolderName,
                startTimestampMs = startTimestampMs.coerceAtLeast(0L),
                endTimestampMs = endTimestampMs.coerceAtLeast(startTimestampMs),
                originStartMs = originStartMs ?: if (id != 0L) existingNote?.originStartMs else null,
                tags = tagsString,
                createdAt = if (id == 0L) System.currentTimeMillis() else (notes.value.find { it.id == id }?.createdAt ?: System.currentTimeMillis()),
                updatedAt = System.currentTimeMillis(),
                targetWord = resolvedWord,
                meaning = resolvedMeaning,
                contextSentence = resolvedContext
            )

            if (id == 0L) {
                repository.insertNote(note)
            } else {
                repository.updateNote(note)
            }

            withContext(Dispatchers.Main) {
                val context = getApplication<Application>()
                Toast.makeText(context, Loc.getText("note_saved_success"), Toast.LENGTH_SHORT).show()
                onSuccess?.invoke()
            }
        } catch (e: Exception) {
            Log.e("NotesActions", "Failed to save note", e)
        }
    }
}

fun AppViewModel.toggleNoteFavorite(note: Note) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val currentTags = note.getTagsList().toMutableList()
            val favTag = Loc.getText("favorite_tag_name")
            val isFav = currentTags.any { 
                it.equals("favorite", ignoreCase = true) || it == "المفضلة" || it == favTag 
            }

            if (isFav) {
                currentTags.removeAll { 
                    it.equals("favorite", ignoreCase = true) || it == "المفضلة" || it == favTag 
                }
            } else {
                currentTags.add(0, favTag)
            }

            val newTagsStr = currentTags.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.joinToString(",")
            val updatedNote = note.copy(
                tags = newTagsStr,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateNote(updatedNote)
        } catch (e: Exception) {
            Log.e("NotesActions", "Failed to toggle note favorite", e)
        }
    }
}

fun AppViewModel.deleteNote(note: Note) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            if (NoteAudioPlayer.playingNoteId.value == note.id) {
                NoteAudioPlayer.stop()
            }
            repository.deleteNote(note)
            withContext(Dispatchers.Main) {
                val context = getApplication<Application>()
                Toast.makeText(context, Loc.getText("note_deleted_success"), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("NotesActions", "Failed to delete note", e)
        }
    }
}

fun AppViewModel.addTag(name: String) {
    viewModelScope.launch(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) {
            repository.insertTag(trimmed)
        }
    }
}

fun AppViewModel.deleteTag(tagId: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        repository.deleteTag(tagId)
    }
}

fun AppViewModel.playNoteSnippet(note: Note) {
    val context = getApplication<Application>()
    var targetTrack = if (note.trackId != null) tracks.value.find { it.id == note.trackId } else null
    if (targetTrack == null && !note.trackName.isNullOrBlank()) {
        val cleanName = note.trackName.trim().lowercase(Locale.ROOT)
        targetTrack = tracks.value.find { 
            val fn = it.fileName.trim().lowercase(Locale.ROOT)
            fn == cleanName || fn.removeSuffix(".mp3") == cleanName.removeSuffix(".mp3")
        }
        if (targetTrack != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val updated = note.copy(
                    trackId = targetTrack.id,
                    trackName = targetTrack.fileName,
                    folderId = targetTrack.parentFolderId,
                    folderName = targetTrack.parentFolderId?.let { repository.getFolderById(it)?.folderName } ?: note.folderName
                )
                repository.updateNote(updated)
            }
        }
    }
    if (targetTrack != null) {
        NoteAudioPlayer.playSnippet(
            context = context,
            trackFilePath = targetTrack.filePath,
            noteId = note.id,
            startMs = note.startTimestampMs,
            endMs = if (note.endTimestampMs > note.startTimestampMs) note.endTimestampMs else null
        )
    } else if (note.trackId != null) {
        viewModelScope.launch(Dispatchers.IO) {
            val dbTrack = repository.dao.getTrackById(note.trackId)
            if (dbTrack != null && File(dbTrack.filePath).exists()) {
                withContext(Dispatchers.Main) {
                    NoteAudioPlayer.playSnippet(
                        context = context,
                        trackFilePath = dbTrack.filePath,
                        noteId = note.id,
                        startMs = note.startTimestampMs,
                        endMs = if (note.endTimestampMs > note.startTimestampMs) note.endTimestampMs else null
                    )
                }
            }
        }
    }
}

fun AppViewModel.stopNoteSnippet() {
    NoteAudioPlayer.stop()
}
