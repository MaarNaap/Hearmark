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
        VocabularyItem::class,
        VocabularyStory::class
    ],
    version = 22,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
    abstract fun vocabularyItemDao(): VocabularyItemDao

    companion object {
        const val DATABASE_VERSION = 22

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // Safeguard existing SQLite database synchronously before Room opens or migrates it
                com.example.util.AutoBackupManager.safetyBackupDatabaseFile(
                    context.applicationContext,
                    DATABASE_VERSION
                )
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
