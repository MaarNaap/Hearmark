package com.example.ui

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import com.example.data.AudioTrack
import com.example.data.entities.TrackRelinkCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

// =========================================================================
// @LOCKED: Automatic File & Stats Relinking Engine - STRICT FREEZE
// DO NOT MODIFY OR REFACTOR THIS BLOCK WITHOUT EXPLICIT PERMISSION IN PROMPT
// =========================================================================
// --- AUTOMATIC FILE & STATS RELINKING ENGINE EXTENSIONS ---
fun AppViewModel.searchRelinkCandidates(autoOpenDialog: Boolean = true) {
    viewModelScope.launch(Dispatchers.IO) {
        isScanningForRelink.value = true
        try {
            val allTracks = repository.dao.getAllTracksFlow().firstOrNull() ?: emptyList()
            val allFolders = repository.dao.getAllFoldersDirect()
            val folderMap = allFolders.associateBy { it.id }
            val allHistory = repository.dao.getPlaybackHistoryFlow().firstOrNull() ?: emptyList()
            val allTaskProgress = repository.dao.getAllTaskProgressFlow().firstOrNull() ?: emptyList()
            val allNotes = repository.dao.getAllNotesFlow().firstOrNull() ?: emptyList()

            fun cleanStr(s: String?): String {
                if (s.isNullOrBlank()) return ""
                return s.trim().lowercase(Locale.ROOT)
                    .removeSuffix(".mp3")
                    .removeSuffix(".m4a")
                    .removeSuffix(".wav")
                    .removeSuffix(".aac")
                    .removeSuffix(".ogg")
                    .removeSuffix(".flac")
                    .removeSuffix(".opus")
                    .replace("_", " ")
                    .replace("-", " ")
                    .replace(".", " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            }

            fun getFolderForTrack(track: AudioTrack): String {
                val fName = track.parentFolderId?.let { folderMap[it]?.folderName }
                if (!fName.isNullOrBlank()) return fName
                val parentDir = try { File(track.filePath).parentFile?.name } catch (e: Exception) { null }
                if (!parentDir.isNullOrBlank() && parentDir != "files" && parentDir != "imported") return parentDir
                return ""
            }

            val activeTracks = allTracks.filter { !it.isVirtualScene && File(it.filePath).exists() && !it.isMissing }
            val activeTrackIds = activeTracks.map { it.id }.toSet()

            // Historical candidate sources
            val missingTracks = allTracks.filter { !it.isVirtualScene && (it.isMissing || !File(it.filePath).exists()) }
            val olderDuplicateTracks = allTracks.filter { old ->
                !old.isVirtualScene && old.playCount > 0 && activeTracks.any { active ->
                    active.id != old.id && cleanStr(active.fileName) == cleanStr(old.fileName) && active.playCount == 0
                }
            }
            val historicalAudioTracks = (missingTracks + olderDuplicateTracks).distinctBy { it.id }

            val candidatesList = mutableListOf<TrackRelinkCandidate>()
            val matchedActiveIds = mutableSetOf<Long>()
            val matchedHistoricalTrackIds = mutableSetOf<Long>()

            fun countMatchingNotes(activeTrack: AudioTrack, histId: Long?): Int {
                val activeCleanName = cleanStr(activeTrack.fileName)
                val activeCleanFolder = cleanStr(getFolderForTrack(activeTrack))
                return allNotes.count { note ->
                    (histId != null && note.trackId == histId) ||
                    note.trackId == activeTrack.id ||
                    run {
                        val noteCleanName = cleanStr(note.trackName)
                        val nameMatch = noteCleanName.isNotBlank() && (noteCleanName == activeCleanName || noteCleanName.replace(" ", "") == activeCleanName.replace(" ", ""))
                        if (!nameMatch) return@run false
                        val noteCleanFolder = cleanStr(note.folderName)
                        when {
                            activeCleanFolder.isNotBlank() && noteCleanFolder.isNotBlank() ->
                                noteCleanFolder == activeCleanFolder || noteCleanFolder.contains(activeCleanFolder) || activeCleanFolder.contains(noteCleanFolder)
                            else -> true
                        }
                    }
                }
            }

            // 1. Match active tracks against missing/older AudioTracks in DB
            for (active in activeTracks) {
                val activeCleanName = cleanStr(active.fileName)
                val activeFolder = getFolderForTrack(active)
                val activeCleanFolder = cleanStr(activeFolder)

                val match = historicalAudioTracks.firstOrNull { h ->
                    h.id != active.id && !matchedHistoricalTrackIds.contains(h.id) && run {
                        val hCleanName = cleanStr(h.fileName)
                        val nameMatch = hCleanName == activeCleanName ||
                                hCleanName.replace(" ", "") == activeCleanName.replace(" ", "")
                        if (!nameMatch) return@run false

                        val hFolder = getFolderForTrack(h)
                        val hCleanFolder = cleanStr(hFolder)
                        val folderMatch = when {
                            activeCleanFolder.isNotBlank() && hCleanFolder.isNotBlank() ->
                                hCleanFolder == activeCleanFolder ||
                                hCleanFolder.contains(activeCleanFolder) ||
                                activeCleanFolder.contains(hCleanFolder)
                            activeCleanFolder.isBlank() && hCleanFolder.isBlank() -> true
                            else -> true // If one side had no folder, allow name match
                        }
                        folderMatch
                    }
                }

                if (match != null) {
                    val hFolder = getFolderForTrack(match)
                    val hHistoryCount = allHistory.count { it.trackId == match.id }
                    val hTaskCount = allTaskProgress.count { it.trackId == match.id }
                    val hNoteCount = countMatchingNotes(active, match.id)
                    val playCount = maxOf(match.playCount, hHistoryCount)
                    val progressPercent = match.getProgressPercent()

                    candidatesList.add(
                        TrackRelinkCandidate(
                            activeTrack = active,
                            historicalTrackId = match.id,
                            fileName = active.fileName,
                            folderName = activeFolder.ifBlank { hFolder },
                            historicalFolderName = hFolder,
                            playCount = playCount,
                            progressPercent = progressPercent,
                            durationMs = if (match.duration > 0) match.duration else active.duration,
                            historyEntriesCount = hHistoryCount,
                            taskProgressCount = hTaskCount,
                            notesCount = hNoteCount,
                            isMissingRecord = match.isMissing || !File(match.filePath).exists()
                        )
                    )
                    matchedActiveIds.add(active.id)
                    matchedHistoricalTrackIds.add(match.id)
                }
            }

            // 2. Check orphaned history logs that might not have an AudioTrack entity row
            val orphanedHistory = allHistory
                .filter { it.trackId !in activeTrackIds && it.trackId !in matchedHistoricalTrackIds }
                .groupBy { it.trackId }

            for ((orphanedId, historyList) in orphanedHistory) {
                val rawTrackName = historyList.firstOrNull()?.trackName ?: continue
                val cleanHistoryName = cleanStr(rawTrackName)

                val matchingActive = activeTracks.firstOrNull { active ->
                    !matchedActiveIds.contains(active.id) && cleanStr(active.fileName) == cleanHistoryName
                }

                if (matchingActive != null) {
                    val activeFolder = getFolderForTrack(matchingActive)
                    val noteFolder = allNotes.firstOrNull { it.trackId == orphanedId }?.folderName ?: ""
                    val taskCount = allTaskProgress.count { it.trackId == orphanedId }
                    val noteCount = countMatchingNotes(matchingActive, orphanedId)

                    candidatesList.add(
                        TrackRelinkCandidate(
                            activeTrack = matchingActive,
                            historicalTrackId = orphanedId,
                            fileName = matchingActive.fileName,
                            folderName = activeFolder.ifBlank { noteFolder },
                            historicalFolderName = noteFolder,
                            playCount = historyList.size,
                            progressPercent = 100, // completed history record
                            durationMs = historyList.firstOrNull()?.durationMs ?: matchingActive.duration,
                            historyEntriesCount = historyList.size,
                            taskProgressCount = taskCount,
                            notesCount = noteCount,
                            isMissingRecord = true
                        )
                    )
                    matchedActiveIds.add(matchingActive.id)
                }
            }

            // 3. Check active tracks that have unlinked/imported notes matching their file name & folder
            for (active in activeTracks) {
                if (matchedActiveIds.contains(active.id)) continue
                val activeCleanName = cleanStr(active.fileName)
                val activeFolder = getFolderForTrack(active)
                val activeCleanFolder = cleanStr(activeFolder)

                // Find notes belonging to this active track that are unlinked or have outdated folderId/trackId
                val matchingNotes = allNotes.filter { note ->
                    val noteCleanName = cleanStr(note.trackName)
                    val nameMatch = (noteCleanName.isNotBlank() && (noteCleanName == activeCleanName || noteCleanName.replace(" ", "") == activeCleanName.replace(" ", ""))) ||
                            (note.trackId != null && note.trackId == active.id)

                    if (!nameMatch) return@filter false

                    val noteCleanFolder = cleanStr(note.folderName)
                    val folderMatch = when {
                        activeCleanFolder.isNotBlank() && noteCleanFolder.isNotBlank() ->
                            noteCleanFolder == activeCleanFolder ||
                            noteCleanFolder.contains(activeCleanFolder) ||
                            activeCleanFolder.contains(noteCleanFolder)
                        activeCleanFolder.isBlank() && noteCleanFolder.isBlank() -> true
                        else -> true
                    }
                    folderMatch && (note.trackId != active.id || note.folderId != active.parentFolderId)
                }

                if (matchingNotes.isNotEmpty()) {
                    val historyCount = allHistory.count { it.trackId == active.id }
                    val taskCount = allTaskProgress.count { it.trackId == active.id }
                    val playCount = maxOf(active.playCount, historyCount)
                    val noteFolder = matchingNotes.firstOrNull { !it.folderName.isNullOrBlank() }?.folderName ?: activeFolder

                    candidatesList.add(
                        TrackRelinkCandidate(
                            activeTrack = active,
                            historicalTrackId = matchingNotes.firstOrNull()?.trackId ?: active.id,
                            fileName = active.fileName,
                            folderName = activeFolder.ifBlank { noteFolder },
                            historicalFolderName = noteFolder,
                            playCount = playCount,
                            progressPercent = active.getProgressPercent(),
                            durationMs = active.duration,
                            historyEntriesCount = historyCount,
                            taskProgressCount = taskCount,
                            notesCount = matchingNotes.size,
                            isMissingRecord = false
                        )
                    )
                    matchedActiveIds.add(active.id)
                }
            }

            val sortedCandidates = candidatesList.sortedWith(
                compareBy({ it.folderName.lowercase(Locale.ROOT) }, { it.fileName.lowercase(Locale.ROOT) })
            )

            withContext(Dispatchers.Main) {
                relinkCandidates.value = sortedCandidates
                isScanningForRelink.value = false
                if (autoOpenDialog) {
                    showRelinkDialog.value = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                isScanningForRelink.value = false
            }
        }
    }
}

fun AppViewModel.applyRelinkCandidates(
    selectedCandidates: List<TrackRelinkCandidate>,
    onComplete: ((Int) -> Unit)? = null
) {
    viewModelScope.launch(Dispatchers.IO) {
        var linkedCount = 0
        for (candidate in selectedCandidates) {
            try {
                val active = repository.getTrackById(candidate.activeTrack.id) ?: candidate.activeTrack
                val oldId = candidate.historicalTrackId
                val oldTrack = repository.getTrackById(oldId)

                val finalTrackId: Long
                val finalFileName: String
                val finalFolderId: Long?
                val finalFolderName: String

                if (oldTrack != null && oldTrack.id != active.id) {
                    // Merge active physical file into historical track record
                    val effectivePlayCount = maxOf(oldTrack.playCount, active.playCount, candidate.playCount)
                    val effectiveListenedSegments = if (oldTrack.listenedSegments.isNotBlank()) oldTrack.listenedSegments else active.listenedSegments
                    val effectiveLastPosition = if (oldTrack.lastPosition > 0) oldTrack.lastPosition else active.lastPosition
                    val effectiveDuration = if (active.duration > 0) active.duration else oldTrack.duration

                    val mergedTrack = oldTrack.copy(
                        filePath = active.filePath,
                        fileName = active.fileName,
                        duration = effectiveDuration,
                        playCount = effectivePlayCount,
                        lastPosition = effectiveLastPosition,
                        listenedSegments = effectiveListenedSegments,
                        parentFolderId = active.parentFolderId ?: oldTrack.parentFolderId,
                        isIndependent = active.isIndependent,
                        isMissing = false,
                        subtitlePath = active.subtitlePath ?: oldTrack.subtitlePath,
                        subtitleContent = active.subtitleContent ?: oldTrack.subtitleContent,
                        practiceSegments = oldTrack.practiceSegments ?: active.practiceSegments,
                        currentPlayActualListeningMs = maxOf(oldTrack.currentPlayActualListeningMs, active.currentPlayActualListeningMs)
                    )

                    // Reassign any new references to historical track ID
                    repository.reassignTrackReferences(fromTrackId = active.id, toTrackId = oldTrack.id)

                    // Update historical track with live file & preserved stats
                    repository.updateTrack(mergedTrack)

                    // Remove duplicate active track
                    repository.deleteTrackById(active.id)

                    finalTrackId = oldTrack.id
                    finalFileName = active.fileName
                    finalFolderId = mergedTrack.parentFolderId
                    finalFolderName = candidate.folderName

                    linkedCount++
                } else if (oldTrack != null && oldTrack.id == active.id) {
                    // Same entity, mark active and update play count
                    repository.updateTrack(oldTrack.copy(
                        isMissing = false,
                        playCount = maxOf(oldTrack.playCount, candidate.playCount)
                    ))
                    finalTrackId = oldTrack.id
                    finalFileName = oldTrack.fileName
                    finalFolderId = oldTrack.parentFolderId
                    finalFolderName = candidate.folderName
                    linkedCount++
                } else {
                    // Historical record lived in logs; reassign logs to active track ID
                    repository.reassignTrackReferences(fromTrackId = oldId, toTrackId = active.id)
                    repository.updateTrack(active.copy(
                        playCount = maxOf(active.playCount, candidate.playCount),
                        isMissing = false
                    ))
                    finalTrackId = active.id
                    finalFileName = active.fileName
                    finalFolderId = active.parentFolderId
                    finalFolderName = candidate.folderName
                    linkedCount++
                }

                // Re-link and sync all matching notes to final track & folder
                val currentNotes = repository.dao.getAllNotesDirect()
                fun cleanS(s: String?): String {
                    if (s.isNullOrBlank()) return ""
                    return s.trim().lowercase(Locale.ROOT)
                        .removeSuffix(".mp3").removeSuffix(".m4a").removeSuffix(".wav")
                        .removeSuffix(".aac").removeSuffix(".ogg").removeSuffix(".flac").removeSuffix(".opus")
                        .replace("_", " ").replace("-", " ").replace(".", " ").replace(Regex("\\s+"), " ").trim()
                }
                val activeCleanName = cleanS(finalFileName)
                val activeCleanFolder = cleanS(finalFolderName)

                val matchingNotes = currentNotes.filter { note ->
                    note.trackId == oldId ||
                    note.trackId == active.id ||
                    run {
                        val noteCleanName = cleanS(note.trackName)
                        val nameMatch = noteCleanName.isNotBlank() && (noteCleanName == activeCleanName || noteCleanName.replace(" ", "") == activeCleanName.replace(" ", ""))
                        if (!nameMatch) return@run false
                        val noteCleanFolder = cleanS(note.folderName)
                        when {
                            activeCleanFolder.isNotBlank() && noteCleanFolder.isNotBlank() ->
                                noteCleanFolder == activeCleanFolder || noteCleanFolder.contains(activeCleanFolder) || activeCleanFolder.contains(noteCleanFolder)
                            else -> true
                        }
                    }
                }

                for (n in matchingNotes) {
                    repository.dao.updateNote(
                        n.copy(
                            trackId = finalTrackId,
                            trackName = finalFileName,
                            folderId = finalFolderId,
                            folderName = finalFolderName
                        )
                    )
                    repository.dao.updateVocabularyTrackForNote(n.id, finalTrackId)
                    repository.dao.updateQuizQuestionsTrackForNote(n.id, finalTrackId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Sync sanity, tasks, and run comprehensive note sweep
        repository.relinkNotesToActiveTracks()
        checkFilesSanity()
        syncAllDynamicTasks()

        withContext(Dispatchers.Main) {
            showRelinkDialog.value = false
            relinkCandidates.value = emptyList()
            val context = getApplication<Application>()
            Toast.makeText(
                context,
                String.format(Locale.getDefault(), Loc.getText("relink_success_toast"), linkedCount),
                Toast.LENGTH_LONG
            ).show()
            onComplete?.invoke(linkedCount)
        }
    }
}
// =========================================================================
// @END_LOCKED: Automatic File & Stats Relinking Engine
// =========================================================================
