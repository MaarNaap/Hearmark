package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String, // Selected text or note quote
    val comment: String = "", // Personal explanation, translation, remarks
    val trackId: Long? = null, // Linked track ID if any
    val trackName: String? = null, // Cached track title/filename
    val folderId: Long? = null, // Parent folder ID for folder-level filtering
    val folderName: String? = null, // Cached folder name
    val startTimestampMs: Long = 0L, // Start timestamp in milliseconds
    val endTimestampMs: Long = 0L, // End timestamp in milliseconds
    val originStartMs: Long? = null, // The specific cue start timestamp this note is dedicated to
    val tags: String = "", // Comma-separated tags
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val targetWord: String? = null, // Isolated target vocabulary word/phrase
    val meaning: String? = null, // Isolated definition/meaning
    val contextSentence: String? = null // Isolated context sentence
) {
    fun getTagsList(): List<String> {
        if (tags.isBlank()) return emptyList()
        return tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun getIsolatedTargetWord(): String {
        if (!targetWord.isNullOrBlank()) return targetWord.trim()
        val match = Regex("""^([^:\n]+):""").find(comment)
        if (match != null) {
            val candidate = match.groupValues[1].replace("[", "").replace("]", "").trim()
            if (candidate.isNotBlank() && candidate.length < 50 && !candidate.contains("•")) {
                return candidate
            }
        }
        val trimmed = text.trim()
        val words = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size in 1..3 && trimmed.length < 35 && !trimmed.endsWith(".")) {
            return trimmed
        }
        return ""
    }

    fun getIsolatedMeaning(): String {
        if (!meaning.isNullOrBlank()) return meaning.trim()
        val match = Regex("""^[^:\n]+:\s*(.*?)(?=\n•|\n\n|$)""", RegexOption.DOT_MATCHES_ALL).find(comment)
        if (match != null) {
            val def = match.groupValues[1].trim()
            if (def.isNotBlank()) return def
        }
        return comment.trim()
    }

    fun getIsolatedContextSentence(): String {
        if (!contextSentence.isNullOrBlank()) return contextSentence.trim()
        val match = Regex("""•\s*Context in Audio:\s*(.*?)(?=\n•|\n\n|$)""", RegexOption.DOT_MATCHES_ALL).find(comment)
        if (match != null) {
            val ctx = match.groupValues[1].trim()
            if (ctx.isNotBlank()) return ctx
        }
        return text.trim()
    }
}

@Entity(tableName = "note_tags")
data class NoteTag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class NoteQuestionCount(
    val noteId: Long,
    val count: Int
)
