package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Folder::class,
        AudioTrack::class,
        Playlist::class,
        PlaylistTrackCrossRef::class,
        Task::class,
        TaskTrackProgress::class,
        PlaybackHistory::class,
        Note::class,
        NoteTag::class,
        TaskLabel::class,
        TaskDailyProgress::class,
        QuizQuestion::class,
        VocabularyItem::class
    ],
    version = 18,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
    abstract fun vocabularyItemDao(): VocabularyItemDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // Safeguard existing SQLite database before Room applies any migrations
                com.example.util.AutoBackupManager.safetyBackupDatabaseFile(context.applicationContext)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_audio_tasks_db"
                )
                    .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
