package com.example.ui

import com.example.data.AudioTrack
import com.example.data.Folder
import com.example.data.Note

fun filterNotes(
    notes: List<Note>,
    searchQuery: String,
    selectedFolderIds: Set<Long>,
    selectedTags: Set<String>,
    selectedTrackId: Long?,
    folders: List<Folder>,
    tracks: List<AudioTrack>
): List<Note> {
    val selectedFolderNames = folders
        .filter { selectedFolderIds.contains(it.id) }
        .map { it.folderName.trim().lowercase() }
        .toSet()
    val selectedTrack = selectedTrackId?.let { tid -> tracks.find { it.id == tid } }
    val selectedTrackName = selectedTrack?.fileName?.trim()?.lowercase()

    return notes.filter { note ->
        val matchesSearch = if (searchQuery.isBlank()) true else {
            val q = searchQuery.trim().lowercase()
            note.text.lowercase().contains(q) ||
            note.comment.lowercase().contains(q) ||
            note.tags.lowercase().contains(q) ||
            (note.trackName?.lowercase()?.contains(q) == true) ||
            (note.folderName?.lowercase()?.contains(q) == true)
        }

        val matchesFolder = if (selectedFolderIds.isEmpty()) true else {
            (note.folderId != null && selectedFolderIds.contains(note.folderId)) ||
            (note.folderName != null && selectedFolderNames.contains(note.folderName.trim().lowercase()))
        }

        val matchesTag = if (selectedTags.isEmpty()) true else {
            val noteTagList = note.getTagsList()
            selectedTags.any { sTag ->
                noteTagList.any { it.equals(sTag, ignoreCase = true) }
            }
        }

        val matchesTrack = if (selectedTrackId == null) true else {
            note.trackId == selectedTrackId ||
            (selectedTrackName != null && note.trackName?.trim()?.lowercase() == selectedTrackName)
        }

        matchesSearch && matchesFolder && matchesTag && matchesTrack
    }
}

fun extractAllNotebookTags(notes: List<Note>): List<String> {
    return notes.flatMap { it.getTagsList() }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
        .sorted()
}
