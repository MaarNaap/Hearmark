package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playback_history",
    indices = [
        Index(value = ["completedAt"]),
        Index(value = ["trackId"])
    ]
)
data class PlaybackHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val trackName: String,
    val completedAt: Long, // timestamp
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val actualListenedMs: Long = 0L,
    val activeTasks: String = "" // JSON array of active tasks at playback time: [{"id": 1, "title": "Task 1"}]
) {
    fun getLoggedTasks(): List<LoggedTaskInfo> {
        if (activeTasks.isBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(activeTasks)
            val list = mutableListOf<LoggedTaskInfo>()
            for (i in 0 until arr.length()) {
                val item = arr.get(i)
                if (item is org.json.JSONObject) {
                    val id = item.optLong("id", 0L)
                    val title = item.optString("title", "")
                    if (title.isNotBlank()) list.add(LoggedTaskInfo(id, title))
                } else if (item is String && item.isNotBlank()) {
                    list.add(LoggedTaskInfo(0L, item))
                }
            }
            list
        } catch (e: Exception) {
            activeTasks.split("||").map { it.trim() }.filter { it.isNotEmpty() }.map { LoggedTaskInfo(0L, it) }
        }
    }

    fun getActiveTasksList(): List<String> = getLoggedTasks().map { it.title }
    fun getActiveTaskIds(): List<Long> = getLoggedTasks().map { it.id }.filter { it > 0L }
}

data class LoggedTaskInfo(
    val id: Long,
    val title: String
)
