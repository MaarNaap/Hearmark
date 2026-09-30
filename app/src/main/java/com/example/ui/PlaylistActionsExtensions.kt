package com.example.ui

import androidx.lifecycle.viewModelScope
import com.example.data.AudioTrack
import com.example.data.Playlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun AppViewModel.createPlaylist(name: String, tracksToAdd: List<AudioTrack> = emptyList()) {
    viewModelScope.launch(Dispatchers.IO) {
        val playlistId = repository.addPlaylist(name)
        tracksToAdd.forEachIndexed { i, track ->
            repository.addTrackToPlaylist(playlistId, track.id, i)
        }
    }
}

fun AppViewModel.editPlaylistTitle(id: Long, newName: String) {
    viewModelScope.launch(Dispatchers.IO) {
        val pl = repository.getPlaylistById(id) ?: return@launch
        repository.updatePlaylist(pl.copy(name = newName))
    }
}

fun AppViewModel.editFolderName(id: Long, newName: String) {
    viewModelScope.launch(Dispatchers.IO) {
        val folder = repository.getFolderById(id) ?: return@launch
        repository.updateFolder(folder.copy(folderName = newName))
    }
}

fun AppViewModel.deleteFolder(id: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        repository.deleteFolder(id)
    }
}

fun AppViewModel.deletePlaylist(id: Long, removeAudioFilesToo: Boolean) {
    viewModelScope.launch(Dispatchers.IO) {
        if (removeAudioFilesToo) {
            val tracks = repository.getTracksForPlaylist(id)
            for (track in tracks) {
                repository.deleteTrack(track)
            }
        } else {
            // Keep them - since they were in playlist, make sure they are flagged as independent
            val tracks = repository.getTracksForPlaylist(id)
            for (track in tracks) {
                if (track.parentFolderId == null) {
                    repository.updateTrack(track.copy(isIndependent = true))
                }
            }
        }
        repository.deletePlaylist(id)
    }
}

fun AppViewModel.removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        repository.deletePlaylistTrack(playlistId, trackId)
    }
}

fun AppViewModel.addTrackToPlaylist(playlistId: Long, trackId: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        repository.addTrackToPlaylist(playlistId, trackId)

        // Sync any active tasks on this playlist
        val activeTasksList = repository.getActiveTasksDirect()
        activeTasksList.filter { it.sourceType == "PLAYLIST" && it.sourceId == playlistId }.forEach {
            repository.syncDynamicTaskTracks(it.id)
        }
    }
}

fun AppViewModel.addTracksToPlaylist(playlistId: Long, trackIds: List<Long>) {
    viewModelScope.launch(Dispatchers.IO) {
        trackIds.forEach { id ->
            repository.addTrackToPlaylist(playlistId, id)
        }

        // Sync any active tasks on this playlist
        val activeTasksList = repository.getActiveTasksDirect()
        activeTasksList.filter { it.sourceType == "PLAYLIST" && it.sourceId == playlistId }.forEach {
            repository.syncDynamicTaskTracks(it.id)
        }
    }
}

suspend fun AppViewModel.getPlaylistsForTrack(trackId: Long): List<Playlist> {
    return repository.getPlaylistsForTrack(trackId)
}
