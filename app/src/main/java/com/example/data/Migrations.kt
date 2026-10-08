package com.example.data

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object DatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Version 1 to 2 schema adjustments if applicable
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE audio_tracks ADD COLUMN subtitlePath TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE audio_tracks ADD COLUMN subtitleContent TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE audio_tracks ADD COLUMN subtitleOffsetMs INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE folders ADD COLUMN parentFolderId INTEGER DEFAULT NULL")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE playback_history ADD COLUMN durationMs INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE playback_history ADD COLUMN playbackSpeed REAL NOT NULL DEFAULT 1.0")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE playback_history ADD COLUMN actualListenedMs INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `notes` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `text` TEXT NOT NULL,
                    `comment` TEXT NOT NULL,
                    `trackId` INTEGER,
                    `trackName` TEXT,
                    `folderId` INTEGER,
                    `folderName` TEXT,
                    `startTimestampMs` INTEGER NOT NULL,
                    `endTimestampMs` INTEGER NOT NULL,
                    `tags` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `note_tags` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `colorHex` TEXT,
                    `createdAt` INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE audio_tracks ADD COLUMN startOffsetMs INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE audio_tracks ADD COLUMN endOffsetMs INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE audio_tracks ADD COLUMN isVirtualScene INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE audio_tracks ADD COLUMN parentTrackId INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE audio_tracks ADD COLUMN sceneNumber INTEGER DEFAULT NULL")
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN labels TEXT NOT NULL DEFAULT ''")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `task_labels` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE notes ADD COLUMN originStartMs INTEGER DEFAULT NULL")
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE playback_history ADD COLUMN activeTasks TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE audio_tracks ADD COLUMN practiceSegments TEXT DEFAULT NULL")
        }
    }

    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN dailyTargetValue INTEGER DEFAULT NULL")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `task_daily_progress` (
                    `taskId` INTEGER NOT NULL,
                    `date` TEXT NOT NULL,
                    `completedPlayCount` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`taskId`, `date`)
                )
            """.trimIndent())
        }
    }

    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `quiz_questions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `trackId` INTEGER NOT NULL,
                    `questionType` TEXT NOT NULL,
                    `question` TEXT NOT NULL,
                    `optionsJson` TEXT NOT NULL,
                    `correctIndex` INTEGER NOT NULL,
                    `explanation` TEXT NOT NULL,
                    `timestampMs` INTEGER,
                    `timesAnswered` INTEGER NOT NULL DEFAULT 0,
                    `timesCorrect` INTEGER NOT NULL DEFAULT 0,
                    `lastAnsweredAt` INTEGER,
                    `createdAt` INTEGER NOT NULL,
                    FOREIGN KEY(`trackId`) REFERENCES `audio_tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_questions_trackId` ON `quiz_questions` (`trackId`)")
        }
    }

    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE quiz_questions ADD COLUMN category TEXT NOT NULL DEFAULT 'COMPREHENSION'")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_questions_category` ON `quiz_questions` (`category`)")
        }
    }

    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `quiz_questions_temp` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `trackId` INTEGER,
                    `noteId` INTEGER,
                    `questionType` TEXT NOT NULL,
                    `category` TEXT NOT NULL DEFAULT 'COMPREHENSION',
                    `question` TEXT NOT NULL,
                    `optionsJson` TEXT NOT NULL,
                    `correctIndex` INTEGER NOT NULL,
                    `explanation` TEXT NOT NULL,
                    `timestampMs` INTEGER,
                    `timesAnswered` INTEGER NOT NULL DEFAULT 0,
                    `timesCorrect` INTEGER NOT NULL DEFAULT 0,
                    `lastAnsweredAt` INTEGER,
                    `createdAt` INTEGER NOT NULL,
                    FOREIGN KEY(`trackId`) REFERENCES `audio_tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO `quiz_questions_temp` 
                (`id`, `trackId`, `noteId`, `questionType`, `category`, `question`, `optionsJson`, `correctIndex`, `explanation`, `timestampMs`, `timesAnswered`, `timesCorrect`, `lastAnsweredAt`, `createdAt`)
                SELECT `id`, `trackId`, NULL, `questionType`, `category`, `question`, `optionsJson`, `correctIndex`, `explanation`, `timestampMs`, `timesAnswered`, `timesCorrect`, `lastAnsweredAt`, `createdAt`
                FROM `quiz_questions`
            """.trimIndent())
            db.execSQL("DROP TABLE `quiz_questions`")
            db.execSQL("ALTER TABLE `quiz_questions_temp` RENAME TO `quiz_questions`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_questions_trackId` ON `quiz_questions` (`trackId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_questions_category` ON `quiz_questions` (`category`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_questions_noteId` ON `quiz_questions` (`noteId`)")
        }
    }

    val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. Create vocabulary_items table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `vocabulary_items` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `targetWord` TEXT NOT NULL,
                    `meaning` TEXT NOT NULL,
                    `contextSentence` TEXT NOT NULL DEFAULT '',
                    `noteId` INTEGER,
                    `trackId` INTEGER,
                    `timestampMs` INTEGER,
                    `timesReviewed` INTEGER NOT NULL DEFAULT 0,
                    `timesCorrect` INTEGER NOT NULL DEFAULT 0,
                    `isMastered` INTEGER NOT NULL DEFAULT 0,
                    `lastReviewedAt` INTEGER,
                    `createdAt` INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_vocabulary_items_targetWord` ON `vocabulary_items` (`targetWord`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_vocabulary_items_noteId` ON `vocabulary_items` (`noteId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_vocabulary_items_trackId` ON `vocabulary_items` (`trackId`)")

            // 2. Add isolated fields to notes table
            try {
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `targetWord` TEXT DEFAULT NULL")
            } catch (_: Exception) {}
            try {
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `meaning` TEXT DEFAULT NULL")
            } catch (_: Exception) {}
            try {
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `contextSentence` TEXT DEFAULT NULL")
            } catch (_: Exception) {}

            // 3. Add isolated fields to quiz_questions table
            try {
                db.execSQL("ALTER TABLE `quiz_questions` ADD COLUMN `targetWord` TEXT DEFAULT NULL")
            } catch (_: Exception) {}
            try {
                db.execSQL("ALTER TABLE `quiz_questions` ADD COLUMN `meaning` TEXT DEFAULT NULL")
            } catch (_: Exception) {}
            try {
                db.execSQL("ALTER TABLE `quiz_questions` ADD COLUMN `contextSentence` TEXT DEFAULT NULL")
            } catch (_: Exception) {}
        }
    }

    val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                db.execSQL("ALTER TABLE `audio_tracks` ADD COLUMN `currentPlayActualListeningMs` INTEGER NOT NULL DEFAULT 0")
            } catch (_: Exception) {}
        }
    }

    val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_folders_parentFolderId` ON `folders` (`parentFolderId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_tracks_parentFolderId` ON `audio_tracks` (`parentFolderId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_tracks_filePath` ON `audio_tracks` (`filePath`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_tracks_parentTrackId` ON `audio_tracks` (`parentTrackId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_tracks_trackId` ON `playlist_tracks` (`trackId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_track_progress_trackId` ON `task_track_progress` (`trackId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_daily_progress_date` ON `task_daily_progress` (`date`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_history_completedAt` ON `playback_history` (`completedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_history_trackId` ON `playback_history` (`trackId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_trackId` ON `notes` (`trackId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_folderId` ON `notes` (`folderId`)")
        }
    }

    val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. Add spaced repetition columns to quiz_questions
            try {
                db.execSQL("ALTER TABLE `quiz_questions` ADD COLUMN `srRepetitions` INTEGER NOT NULL DEFAULT 0")
            } catch (e: Exception) {
                Log.w("Migrations", "MIGRATION_19_20 srRepetitions column warning: ${e.message}")
            }
            try {
                db.execSQL("ALTER TABLE `quiz_questions` ADD COLUMN `srIntervalDays` INTEGER NOT NULL DEFAULT 0")
            } catch (e: Exception) {
                Log.w("Migrations", "MIGRATION_19_20 srIntervalDays column warning: ${e.message}")
            }
            try {
                db.execSQL("ALTER TABLE `quiz_questions` ADD COLUMN `srEase` REAL NOT NULL DEFAULT 2.5")
            } catch (e: Exception) {
                Log.w("Migrations", "MIGRATION_19_20 srEase column warning: ${e.message}")
            }
            try {
                db.execSQL("ALTER TABLE `quiz_questions` ADD COLUMN `srLapses` INTEGER NOT NULL DEFAULT 0")
            } catch (e: Exception) {
                Log.w("Migrations", "MIGRATION_19_20 srLapses column warning: ${e.message}")
            }
            try {
                db.execSQL("ALTER TABLE `quiz_questions` ADD COLUMN `srNextReviewAt` INTEGER DEFAULT NULL")
            } catch (e: Exception) {
                Log.w("Migrations", "MIGRATION_19_20 srNextReviewAt column warning: ${e.message}")
            }

            // 2. Create index on srNextReviewAt
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_questions_srNextReviewAt` ON `quiz_questions` (`srNextReviewAt`)")

            // 3. Confirmed Decision 1: Seed existing items with strong accuracy (timesAnswered > 0 and accuracy >= 75%)
            // with a 3-day interval so they do not flood "Due Today" on first launch
            try {
                db.execSQL("""
                    UPDATE `quiz_questions` 
                    SET `srRepetitions` = 2, 
                        `srIntervalDays` = 3, 
                        `srNextReviewAt` = COALESCE(`lastAnsweredAt`, CAST(strftime('%s', 'now') AS INTEGER) * 1000) + 259200000
                    WHERE `timesAnswered` > 0 AND (`timesCorrect` * 1.0 / `timesAnswered`) >= 0.75
                """.trimIndent())
            } catch (e: Exception) {
                Log.w("Migrations", "MIGRATION_19_20 seed update warning: ${e.message}")
            }
        }
    }

    val MIGRATION_20_21 = object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. Point scene notes at their physical parent track, updating trackId, trackName, folderId, folderName
            try {
                db.execSQL("""
                    UPDATE `notes`
                    SET `trackId` = (
                            SELECT p.`id` FROM `audio_tracks` s
                            JOIN `audio_tracks` p ON (s.`parentTrackId` = p.`id` OR (p.`filePath` = s.`filePath` AND p.`isVirtualScene` = 0))
                            WHERE s.`id` = `notes`.`trackId` AND s.`isVirtualScene` = 1
                            LIMIT 1
                        ),
                        `trackName` = (
                            SELECT p.`fileName` FROM `audio_tracks` s
                            JOIN `audio_tracks` p ON (s.`parentTrackId` = p.`id` OR (p.`filePath` = s.`filePath` AND p.`isVirtualScene` = 0))
                            WHERE s.`id` = `notes`.`trackId` AND s.`isVirtualScene` = 1
                            LIMIT 1
                        ),
                        `folderId` = (
                            SELECT p.`parentFolderId` FROM `audio_tracks` s
                            JOIN `audio_tracks` p ON (s.`parentTrackId` = p.`id` OR (p.`filePath` = s.`filePath` AND p.`isVirtualScene` = 0))
                            WHERE s.`id` = `notes`.`trackId` AND s.`isVirtualScene` = 1
                            LIMIT 1
                        ),
                        `folderName` = (
                            SELECT f.`folderName` FROM `audio_tracks` s
                            JOIN `audio_tracks` p ON (s.`parentTrackId` = p.`id` OR (p.`filePath` = s.`filePath` AND p.`isVirtualScene` = 0))
                            LEFT JOIN `folders` f ON f.`id` = p.`parentFolderId`
                            WHERE s.`id` = `notes`.`trackId` AND s.`isVirtualScene` = 1
                            LIMIT 1
                        )
                    WHERE `trackId` IN (SELECT `id` FROM `audio_tracks` WHERE `isVirtualScene` = 1)
                """.trimIndent())
            } catch (e: Exception) {
                Log.w("Migrations", "MIGRATION_20_21 re-pointing scene notes warning: ${e.message}")
            }

            // Also re-point vocabulary_items attached to scene tracks to parent
            try {
                db.execSQL("""
                    UPDATE `vocabulary_items`
                    SET `trackId` = (
                            SELECT p.`id` FROM `audio_tracks` s
                            JOIN `audio_tracks` p ON (s.`parentTrackId` = p.`id` OR (p.`filePath` = s.`filePath` AND p.`isVirtualScene` = 0))
                            WHERE s.`id` = `vocabulary_items`.`trackId` AND s.`isVirtualScene` = 1
                            LIMIT 1
                        )
                    WHERE `trackId` IN (SELECT `id` FROM `audio_tracks` WHERE `isVirtualScene` = 1)
                """.trimIndent())
            } catch (e: Exception) {
                Log.w("Migrations", "MIGRATION_20_21 re-pointing vocab trackId warning: ${e.message}")
            }

            // 2. Re-point quiz_questions attached to scene tracks to parent track
            try {
                db.execSQL("""
                    UPDATE `quiz_questions`
                    SET `trackId` = (
                            SELECT p.`id` FROM `audio_tracks` s
                            JOIN `audio_tracks` p ON (s.`parentTrackId` = p.`id` OR (p.`filePath` = s.`filePath` AND p.`isVirtualScene` = 0))
                            WHERE s.`id` = `quiz_questions`.`trackId` AND s.`isVirtualScene` = 1
                            LIMIT 1
                        )
                    WHERE `trackId` IN (SELECT `id` FROM `audio_tracks` WHERE `isVirtualScene` = 1)
                """.trimIndent())
            } catch (e: Exception) {
                Log.w("Migrations", "MIGRATION_20_21 re-pointing quiz_questions trackId warning: ${e.message}")
            }

            // 3. Rebuild quiz_questions table with ON DELETE SET NULL and cached trackName
            try {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `quiz_questions_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `trackId` INTEGER,
                        `trackName` TEXT DEFAULT NULL,
                        `noteId` INTEGER,
                        `questionType` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `question` TEXT NOT NULL,
                        `optionsJson` TEXT NOT NULL,
                        `correctIndex` INTEGER NOT NULL,
                        `explanation` TEXT NOT NULL,
                        `timestampMs` INTEGER,
                        `timesAnswered` INTEGER NOT NULL,
                        `timesCorrect` INTEGER NOT NULL,
                        `lastAnsweredAt` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        `targetWord` TEXT,
                        `meaning` TEXT,
                        `contextSentence` TEXT,
                        `srRepetitions` INTEGER NOT NULL DEFAULT 0,
                        `srIntervalDays` INTEGER NOT NULL DEFAULT 0,
                        `srEase` REAL NOT NULL DEFAULT 2.5,
                        `srLapses` INTEGER NOT NULL DEFAULT 0,
                        `srNextReviewAt` INTEGER DEFAULT NULL,
                        FOREIGN KEY(`trackId`) REFERENCES `audio_tracks`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                """.trimIndent())

                // Copy data from old quiz_questions and backfill trackName from audio_tracks
                db.execSQL("""
                    INSERT INTO `quiz_questions_new` (
                        `id`, `trackId`, `trackName`, `noteId`, `questionType`, `category`, `question`,
                        `optionsJson`, `correctIndex`, `explanation`, `timestampMs`, `timesAnswered`,
                        `timesCorrect`, `lastAnsweredAt`, `createdAt`, `targetWord`, `meaning`,
                        `contextSentence`, `srRepetitions`, `srIntervalDays`, `srEase`, `srLapses`, `srNextReviewAt`
                    )
                    SELECT 
                        q.`id`, q.`trackId`, t.`fileName` AS `trackName`, q.`noteId`, q.`questionType`, q.`category`, q.`question`,
                        q.`optionsJson`, q.`correctIndex`, q.`explanation`, q.`timestampMs`, q.`timesAnswered`,
                        q.`timesCorrect`, q.`lastAnsweredAt`, q.`createdAt`, q.`targetWord`, q.`meaning`,
                        q.`contextSentence`, q.`srRepetitions`, q.`srIntervalDays`, q.`srEase`, q.`srLapses`, q.`srNextReviewAt`
                    FROM `quiz_questions` q
                    LEFT JOIN `audio_tracks` t ON q.`trackId` = t.`id`
                """.trimIndent())

                db.execSQL("DROP TABLE `quiz_questions`")
                db.execSQL("ALTER TABLE `quiz_questions_new` RENAME TO `quiz_questions`")

                // Re-create indices
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_questions_trackId` ON `quiz_questions` (`trackId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_questions_category` ON `quiz_questions` (`category`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_questions_noteId` ON `quiz_questions` (`noteId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_questions_srNextReviewAt` ON `quiz_questions` (`srNextReviewAt`)")
            } catch (e: Exception) {
                Log.e("Migrations", "MIGRATION_20_21 rebuild quiz_questions failed: ${e.message}", e)
                throw e
            }
        }
    }

    val ALL_MIGRATIONS = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
        MIGRATION_13_14,
        MIGRATION_14_15,
        MIGRATION_15_16,
        MIGRATION_16_17,
        MIGRATION_17_18,
        MIGRATION_18_19,
        MIGRATION_19_20,
        MIGRATION_20_21
    )
}
