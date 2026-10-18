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
            StoryOptions(
                length = obj.optString("length", "MEDIUM"),
                level = obj.optString("level", "INTERMEDIATE"),
                genre = obj.optString("genre", "SLICE_OF_LIFE")
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
    val length: String = "MEDIUM",       // SHORT, MEDIUM, LONG
    val level: String = "INTERMEDIATE",   // BEGINNER, INTERMEDIATE, ADVANCED
    val genre: String = "SLICE_OF_LIFE"  // SLICE_OF_LIFE, MYSTERY, ADVENTURE, SCI_FI, HUMOROUS, INSPIRATIONAL, HISTORICAL
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("length", length)
        put("level", level)
        put("genre", genre)
    }
}
