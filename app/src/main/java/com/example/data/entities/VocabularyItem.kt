package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "quiz_questions",
    foreignKeys = [
        ForeignKey(
            entity = AudioTrack::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["trackId"]),
        Index(value = ["category"]),
        Index(value = ["noteId"])
    ]
)
data class QuizQuestion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long? = null,
    val noteId: Long? = null,
    val questionType: String, // "MCQ" or "TRUE_FALSE"
    val category: String = "COMPREHENSION", // "VOCABULARY" or "COMPREHENSION"
    val question: String,
    val optionsJson: String, // JSON array string e.g. ["Choice A", "Choice B", "Choice C", "Choice D"]
    val correctIndex: Int,
    val explanation: String,
    val timestampMs: Long? = null,
    val timesAnswered: Int = 0,
    val timesCorrect: Int = 0,
    val lastAnsweredAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val targetWord: String? = null, // Isolated target word
    val meaning: String? = null, // Isolated meaning/definition
    val contextSentence: String? = null // Isolated context sentence
) {
    fun getOptions(): List<String> {
        return try {
            val arr = org.json.JSONArray(optionsJson)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getIsolatedTargetWord(): String {
        if (!targetWord.isNullOrBlank()) return targetWord.trim()
        val quoted = Regex("['\"]([^'\"]+)['\"]").find(question)?.groupValues?.get(1)?.trim()
        if (!quoted.isNullOrBlank() && quoted.length in 2..30) {
            return quoted
        }
        return ""
    }

    fun getIsolatedMeaning(): String {
        if (!meaning.isNullOrBlank()) return meaning.trim()
        val options = getOptions()
        return options.getOrNull(correctIndex) ?: explanation.trim()
    }

    fun getIsolatedContextSentence(): String {
        if (!contextSentence.isNullOrBlank()) return contextSentence.trim()
        return ""
    }
}

@Entity(
    tableName = "vocabulary_items",
    indices = [
        Index(value = ["targetWord"]),
        Index(value = ["noteId"]),
        Index(value = ["trackId"])
    ]
)
data class VocabularyItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetWord: String,
    val meaning: String,
    val contextSentence: String = "",
    val noteId: Long? = null,
    val trackId: Long? = null,
    val timestampMs: Long? = null,
    val timesReviewed: Int = 0,
    val timesCorrect: Int = 0,
    val isMastered: Boolean = false,
    val lastReviewedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
