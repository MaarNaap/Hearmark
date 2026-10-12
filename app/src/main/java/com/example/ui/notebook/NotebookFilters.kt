package com.example.ui

import com.example.data.AudioTrack
import com.example.data.Folder
import com.example.data.Note

fun filterNotes(
    notes: List<Note>,
    searchQuery: String,
    selectedFolderIds: Set<Long> = emptySet(),
    selectedTags: Set<String> = emptySet(),
    selectedTrackId: Long? = null,
    folders: List<Folder> = emptyList(),
    tracks: List<AudioTrack> = emptyList(),
    selectedTrackIds: Set<Long> = emptySet()
): List<Note> {
    val effectiveSelectedTrackIds = if (selectedTrackId != null) {
        selectedTrackIds + selectedTrackId
    } else {
        selectedTrackIds
    }

    val allSelectedFolderSubIds = if (selectedFolderIds.isEmpty()) {
        emptySet()
    } else {
        selectedFolderIds.flatMap { getAllSubfolderIds(it, folders) }.toSet()
    }

    val selectedFolderNames = folders
        .filter { allSelectedFolderSubIds.contains(it.id) }
        .map { it.folderName.trim().lowercase() }
        .toSet()

    val selectedTracks = tracks.filter { effectiveSelectedTrackIds.contains(it.id) }
    val selectedTrackFileNames = selectedTracks.map { it.fileName.trim().lowercase() }.toSet()
    val selectedScenes = selectedTracks.filter { it.isVirtualScene }
    val trackById = tracks.associateBy { it.id }

    return notes.filter { note ->
        val matchesSearch = if (searchQuery.isBlank()) true else {
            val q = searchQuery.trim().lowercase()
            note.text.lowercase().contains(q) ||
            note.comment.lowercase().contains(q) ||
            note.tags.lowercase().contains(q) ||
            (note.trackName?.lowercase()?.contains(q) == true) ||
            (note.folderName?.lowercase()?.contains(q) == true)
        }

        val hasFolderOrTrackFilter = selectedFolderIds.isNotEmpty() || effectiveSelectedTrackIds.isNotEmpty()

        val matchesFolderOrTrack = if (!hasFolderOrTrackFilter) {
            true
        } else {
            val noteTrack = note.trackId?.let { trackById[it] }

            val matchesFolder = if (selectedFolderIds.isEmpty()) false else {
                (note.folderId != null && allSelectedFolderSubIds.contains(note.folderId)) ||
                (note.folderName != null && selectedFolderNames.contains(note.folderName.trim().lowercase())) ||
                (noteTrack?.parentFolderId != null && allSelectedFolderSubIds.contains(noteTrack.parentFolderId))
            }

            val matchesTrack = if (effectiveSelectedTrackIds.isEmpty()) false else {
                (note.trackId != null && effectiveSelectedTrackIds.contains(note.trackId)) ||
                (note.trackName != null && selectedTrackFileNames.contains(note.trackName.trim().lowercase())) ||
                selectedScenes.any { scene -> belongsToScene(note, scene, tracks) }
            }

            matchesFolder || matchesTrack
        }

        val matchesTag = if (selectedTags.isEmpty()) true else {
            val noteTagList = note.getTagsList()
            selectedTags.any { sTag ->
                noteTagList.any { it.equals(sTag, ignoreCase = true) }
            }
        }

        matchesSearch && matchesFolderOrTrack && matchesTag
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
