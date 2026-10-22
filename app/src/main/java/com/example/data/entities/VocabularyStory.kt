package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

@Entity(
    tableName = "vocabulary_stories",
    indices = [
        Index(value = ["createdAt"])
    ]
)
data class VocabularyStory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val storyText: String,
    val targetWordsJson: String,
    val optionsJson: String,
    val missingWordsJson: String? = null,
    val audioFilePath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun getTargetWords(): List<StoryTargetWord> {
        if (targetWordsJson.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(targetWordsJson)
            val list = mutableListOf<StoryTargetWord>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    StoryTargetWord(
                        word = obj.optString("word", "").trim(),
                        meaning = obj.optString("meaning", "").trim(),
                        contextSentence = obj.optString("contextSentence", "").trim(),
                        noteId = if (obj.has("noteId") && !obj.isNull("noteId")) obj.optLong("noteId") else null
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getMissingWords(): List<String> {
        val json = missingWordsJson ?: return emptyList()
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getOptions(): StoryOptions {
        if (optionsJson.isBlank()) return StoryOptions()
        return try {
            val obj = JSONObject(optionsJson)
            val parsedCount = if (obj.has("targetWordCount")) {
                obj.optInt("targetWordCount", 100)
            } else {
                StoryOptions.defaultWordCountForLength(obj.optString("length", "SHORT"))
            }
            val parsedLength = if (obj.has("length")) {
                obj.optString("length", StoryOptions.wordCountToLengthCategory(parsedCount))
            } else {
                StoryOptions.wordCountToLengthCategory(parsedCount)
            }
            StoryOptions(
                length = parsedLength,
                level = obj.optString("level", "INTERMEDIATE"),
                genre = obj.optString("genre", "SLICE_OF_LIFE"),
                targetWordCount = parsedCount
            )
        } catch (_: Exception) {
            StoryOptions()
        }
    }
}

data class StoryTargetWord(
    val word: String,
    val meaning: String,
    val contextSentence: String,
    val noteId: Long? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("word", word)
        put("meaning", meaning)
        put("contextSentence", contextSentence)
        if (noteId != null) put("noteId", noteId)
    }
}

data class StoryOptions(
    val length: String = "SHORT",        // SHORT, MEDIUM, LONG, VERY_LONG
    val level: String = "INTERMEDIATE",   // BEGINNER, INTERMEDIATE, ADVANCED
    val genre: String = "SLICE_OF_LIFE", // SLICE_OF_LIFE, MYSTERY, ADVENTURE, SCI_FI, HUMOROUS, INSPIRATIONAL, HISTORICAL
    val targetWordCount: Int = 100        // user selected target words, default 100, step 50
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("length", length)
        put("level", level)
        put("genre", genre)
        put("targetWordCount", targetWordCount)
    }

    companion object {
        fun wordCountToLengthCategory(count: Int): String = when {
            count <= 150 -> "SHORT"
            count <= 350 -> "MEDIUM"
            count <= 600 -> "LONG"
            else -> "VERY_LONG"
        }

        fun defaultWordCountForLength(length: String): Int = when (length.uppercase()) {
            "SHORT" -> 100
            "MEDIUM" -> 250
            "LONG" -> 500
            "VERY_LONG" -> 800
            else -> 100
        }
    }
}
