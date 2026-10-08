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
            if (selectedTrack?.isVirtualScene == true) {
                belongsToScene(note, selectedTrack, tracks)
            } else {
                note.trackId == selectedTrackId ||
                (selectedTrackName != null && note.trackName?.trim()?.lowercase() == selectedTrackName)
            }
        }

        matchesSearch && matchesFolder && matchesTag && matchesTrack
    }
}

/**
 * Standard scene membership rule across Hearmark:
 * Notes belong to the physical file and are anchored on originStartMs ?: startTimestampMs.
 * A scene owns a note if the anchor falls within [start, end), where start is startOffsetMs
 * and end is endOffsetMs ?: (startOffsetMs + duration).
 */
fun belongsToScene(note: Note, scene: AudioTrack, allTracks: List<AudioTrack> = emptyList()): Boolean {
    if (!scene.isVirtualScene) return note.trackId == scene.id

    val parentId = scene.parentTrackId
        ?: allTracks.find { it.filePath == scene.filePath && !it.isVirtualScene }?.id
    val isParentMatch = if (parentId != null) {
        note.trackId == parentId || note.trackId == scene.id
    } else {
        note.trackId == scene.id
    }
    if (!isParentMatch) return false

    val anchor = note.originStartMs ?: note.startTimestampMs
    val sceneStart = scene.startOffsetMs
    val sceneEnd = scene.endOffsetMs ?: (scene.startOffsetMs + scene.duration)

    return anchor >= sceneStart && anchor < sceneEnd
}

fun extractAllNotebookTags(notes: List<Note>): List<String> {
    return notes.flatMap { it.getTagsList() }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
        .sorted()
}
