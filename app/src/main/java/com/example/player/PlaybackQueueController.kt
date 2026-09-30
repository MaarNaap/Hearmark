package com.example.player

import android.content.Context
import android.util.Log
import com.example.data.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal object PlaybackQueueController {

    fun removeTracksFromQueue(indices: List<Int>) {
        with(AudioPlayerManager) {
            val newList = currentQueue.filterIndexed { index, _ -> index !in indices }
            currentQueue = newList
        }
    }

    fun removeTracksByIds(trackIds: Set<Long>) {
        with(AudioPlayerManager) {
            if (trackIds.isEmpty()) return
            val newList = currentQueue.filter { it.id !in trackIds }
            if (newList.size != currentQueue.size) {
                currentQueue = newList
            }
        }
    }

    fun clearQueue() {
        with(AudioPlayerManager) {
            val current = currentTrackValue
            currentQueue = if (current != null) listOf(current) else emptyList()
        }
    }

    fun playNextTrack() {
        with(AudioPlayerManager) {
            val current = currentTrackValue ?: return
            val currentIndex = currentQueue.indexOfFirst { it.id == current.id }
            if (currentIndex != -1 && currentIndex < currentQueue.size - 1) {
                playTrack(currentQueue[currentIndex + 1], currentQueue)
            }
        }
    }

    fun playPreviousTrack() {
        with(AudioPlayerManager) {
            val current = currentTrackValue ?: return
            val currentIndex = currentQueue.indexOfFirst { it.id == current.id }
            if (currentIndex > 0) {
                playTrack(currentQueue[currentIndex - 1], currentQueue)
            }
        }
    }

    fun addTrackToQueueNext(track: AudioTrack) {
        with(AudioPlayerManager) {
            val current = currentTrackValue
            if (current == null) {
                playTrack(track)
                return
            }
            val withoutTarget = currentQueue.filter { it.id != track.id }
            val currentIndex = withoutTarget.indexOfFirst { it.id == current.id }
            val newList = if (currentIndex != -1) {
                val left = withoutTarget.subList(0, currentIndex + 1)
                val right = withoutTarget.subList(currentIndex + 1, withoutTarget.size)
                left + track + right
            } else {
                withoutTarget + track
            }
            currentQueue = newList
        }
    }

    fun addTracksToQueueNext(tracks: List<AudioTrack>) {
        with(AudioPlayerManager) {
            if (tracks.isEmpty()) return
            val current = currentTrackValue
            if (current == null) {
                playTrack(tracks.first(), tracks)
                return
            }
            val targetIds = tracks.map { it.id }.toSet()
            val withoutTarget = currentQueue.filter { it.id !in targetIds }
            val currentIndex = withoutTarget.indexOfFirst { it.id == current.id }
            val newList = if (currentIndex != -1) {
                val left = withoutTarget.subList(0, currentIndex + 1)
                val right = withoutTarget.subList(currentIndex + 1, withoutTarget.size)
                left + tracks + right
            } else {
                withoutTarget + tracks
            }
            currentQueue = newList
        }
    }

    fun saveQueueToPreferences(queue: List<AudioTrack>) {
        with(AudioPlayerManager) {
            val context = appContext ?: return
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val sharedPref = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    val idsStr = queue.map { it.id }.joinToString(",")
                    sharedPref.edit().putString("current_queue_ids", idsStr).apply()
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving queue to preferences: ${e.message}")
                }
            }
        }
    }

    fun saveCurrentTrackToPreferences(trackId: Long?) {
        with(AudioPlayerManager) {
            val context = appContext ?: return
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val sharedPref = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    sharedPref.edit().putLong("current_track_id", trackId ?: -1L).apply()
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving track to preferences: ${e.message}")
                }
            }
        }
    }

    fun restoreQueueAndTrack() {
        with(AudioPlayerManager) {
            val context = appContext ?: return
            val repo = repository ?: return
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    if (_isPlaying.value || mediaPlayer != null || currentTrackValue != null || _currentTrack.value != null) {
                        Log.d(TAG, "Playback is already active or track is loaded; skipping restore.")
                        return@launch
                    }
                    val sharedPref = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    val currentTrackId = sharedPref.getLong("current_track_id", -1L)
                    val queueIdsStr = sharedPref.getString("current_queue_ids", "") ?: ""

                    if (queueIdsStr.isNotEmpty()) {
                        val idList = queueIdsStr.split(",").mapNotNull { it.toLongOrNull() }
                        if (idList.isNotEmpty()) {
                            val fetchedTracks = mutableListOf<AudioTrack>()
                            for (id in idList) {
                                val track = repo.getTrackById(id)
                                if (track != null) {
                                    fetchedTracks.add(track)
                                }
                            }

                            withContext(Dispatchers.Main) {
                                if (!_isPlaying.value && mediaPlayer == null && currentTrackValue == null && _currentTrack.value == null) {
                                    currentQueue = fetchedTracks
                                }
                            }
                        }
                    }

                    if (currentTrackId != -1L) {
                        val track = repo.getTrackById(currentTrackId)
                        if (track != null) {
                            trackAccumulatedListeningMsMap[track.id] = track.currentPlayActualListeningMs
                            // Let's make sure the track isn't missing
                            val file = File(track.filePath)
                            if (file.exists()) {
                                withContext(Dispatchers.Main) {
                                    if (!_isPlaying.value && mediaPlayer == null && currentTrackValue == null && _currentTrack.value == null) {
                                        currentTrackValue = track
                                        _currentTrack.value = track
                                        _duration.value = track.duration
                                        _currentPosition.value = track.lastPosition
                                        loadSubtitlesForTrack(track)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error restoring queue/track state: ${e.message}")
                }
            }
        }
    }
}
