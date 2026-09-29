package com.example.data.entities

import com.example.data.AudioTrack

/**
 * Represents a match candidate between an active audio file currently in the app
 * and previous recorded statistics / state logs in the database.
 */
data class TrackRelinkCandidate(
    val id: String = java.util.UUID.randomUUID().toString(),
    val activeTrack: AudioTrack,
    val historicalTrackId: Long,
    val fileName: String,
    val folderName: String,
    val historicalFolderName: String,
    val playCount: Int,
    val progressPercent: Int,
    val durationMs: Long,
    val historyEntriesCount: Int,
    val taskProgressCount: Int,
    val notesCount: Int,
    val isMissingRecord: Boolean = true
)
