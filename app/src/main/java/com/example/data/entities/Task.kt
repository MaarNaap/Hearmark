package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val sourceType: String, // "TRACKS", "FOLDER", "PLAYLIST"
    val sourceId: Long?, // Folder ID, Playlist ID, or null
    val targetType: String, // "PLAY_COUNT", "DAYS_COUNT"
    val targetValue: Int, // e.g. 3 plays or 5 days
    val scheduledDays: String, // e.g. "MONDAY,TUESDAY"
    val reminderTime: String, // e.g. "08:30 AM" or "09:12 PM"
    val startDate: Long, // timestamp
    val endDate: Long? = null, // optional end timestamp
    val isCompleted: Boolean = false,
    val status: String = "ACTIVE", // "ACTIVE", "COMPLETED"
    val customThreshold: Int? = null, // custom threshold from 70 to 100 or null to use global
    val labels: String = "", // comma-separated labels e.g. "Loud,Pronunciation,0.75x"
    val dailyTargetValue: Int? = null // optional daily mini-goal (plays per day)
) {
    fun getLabelsList(): List<String> = formatLabelsList(labels)

    fun getFormattedLabels(): String = formatLabels(labels)

    fun getBaseTitle(): String = extractBaseTitle(title, labels)

    fun getDisplayTitle(): String {
        val formatted = getFormattedLabels()
        if (formatted.isEmpty() || title.endsWith(formatted)) {
            return title
        }
        return "$title $formatted"
    }

    companion object {
        fun formatLabelsList(labels: String): List<String> {
            if (labels.isBlank()) return emptyList()
            return labels.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { label ->
                    label.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }
                }
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
        }

        fun formatLabels(labels: String): String {
            val list = formatLabelsList(labels)
            if (list.isEmpty()) return ""
            return "[" + list.joinToString(". ") + "]"
        }

        fun extractBaseTitle(fullTitle: String, labels: String = ""): String {
            var base = fullTitle.trim()
            val formatted = formatLabels(labels)
            if (formatted.isNotEmpty() && base.endsWith(formatted)) {
                base = base.substring(0, base.length - formatted.length).trim()
            } else {
                val lastOpen = base.lastIndexOf('[')
                val lastClose = base.lastIndexOf(']')
                if (lastOpen > 0 && lastClose == base.length - 1) {
                    base = base.substring(0, lastOpen).trim()
                }
            }
            return base
        }

        fun buildCombinedTitle(givenTitle: String, labels: String): String {
            val base = extractBaseTitle(givenTitle, labels)
            val formatted = formatLabels(labels)
            return if (formatted.isEmpty()) base else if (base.isEmpty()) formatted else "$base $formatted"
        }
    }
}

@Entity(
    tableName = "task_track_progress",
    primaryKeys = ["taskId", "trackId"],
    indices = [Index(value = ["trackId"])]
)
data class TaskTrackProgress(
    val taskId: Long,
    val trackId: Long,
    val completedPlayCount: Int = 0, // number of times fully played during this task run
    val completedDays: String = "", // comma separated dates "2026-06-10,2026-06-11"
    val isTrackCompleted: Boolean = false
) {
    fun getDaysList(): List<String> {
        if (completedDays.isEmpty()) return emptyList()
        return completedDays.split(",").filter { it.isNotEmpty() }
    }
}

@Entity(tableName = "task_labels")
data class TaskLabel(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "task_daily_progress",
    primaryKeys = ["taskId", "date"],
    indices = [Index(value = ["date"])]
)
data class TaskDailyProgress(
    val taskId: Long,
    val date: String, // e.g. "2026-09-11"
    val completedPlayCount: Int = 0
)
