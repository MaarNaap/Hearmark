package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.ui.Loc
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.player.AudioPlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val taskId = intent.getLongExtra("TASK_ID", -1L)
        val taskTitle = intent.getStringExtra("TASK_TITLE") ?: "Goal Task"
        val scheduledDays = intent.getStringExtra("SCHEDULED_DAYS") ?: ""
        val reminderTime = intent.getStringExtra("REMINDER_TIME") ?: "09:00 AM"

        if (action == ACTION_PLAY_TASK_TRACK) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(taskId.toInt())
            
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val appContext = context.applicationContext
                    val db = AppDatabase.getDatabase(appContext)
                    val repo = AppRepository(db.appDao())
                    
                    val task = db.appDao().getTaskById(taskId)
                    val taskProgresses = db.appDao().getProgressForTask(taskId)
                    val unfinishedProgress = taskProgresses.firstOrNull { !it.isTrackCompleted } ?: taskProgresses.firstOrNull()
                    
                    val allTaskTracks = if (taskProgresses.isNotEmpty()) {
                        taskProgresses.mapNotNull { db.appDao().getTrackById(it.trackId) }
                    } else if (task != null && task.sourceId != null) {
                        when (task.sourceType) {
                            "TRACKS" -> listOfNotNull(db.appDao().getTrackById(task.sourceId))
                            "PLAYLIST" -> db.appDao().getTracksForPlaylist(task.sourceId)
                            "FOLDER" -> db.appDao().getTracksForFolder(task.sourceId)
                            else -> emptyList()
                        }
                    } else emptyList()

                    val trackToPlay = if (unfinishedProgress != null) {
                        allTaskTracks.find { it.id == unfinishedProgress.trackId } ?: db.appDao().getTrackById(unfinishedProgress.trackId)
                    } else if (allTaskTracks.isNotEmpty()) {
                        allTaskTracks.first()
                    } else if (task != null && task.sourceId != null) {
                        when (task.sourceType) {
                            "TRACKS" -> db.appDao().getTrackById(task.sourceId)
                            "PLAYLIST" -> db.appDao().getTracksForPlaylist(task.sourceId).firstOrNull()
                            "FOLDER" -> db.appDao().getTracksForFolder(task.sourceId).firstOrNull()
                            else -> null
                        }
                    } else null

                    if (trackToPlay != null) {
                        withContext(Dispatchers.Main) {
                            AudioPlayerManager.init(appContext, repo)
                            AudioPlayerManager.playTrack(trackToPlay, allTaskTracks)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AlarmReceiver", "Error playing track from notification action", e)
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        if (action == ACTION_SNOOZE_TASK) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(taskId.toInt())
            snoozeAlarm(context, taskId, taskTitle, scheduledDays, reminderTime, 60)
            return
        }
        
        Log.d("AlarmReceiver", "Alarm onReceive triggered for Task ID $taskId: $taskTitle")

        val pendingResult = goAsync()
        val db = AppDatabase.getDatabase(context.applicationContext)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val task = db.appDao().getTaskById(taskId)
                if (task == null || task.isCompleted || task.status != "ACTIVE") {
                    Log.d("AlarmReceiver", "Task $taskId is completed, deleted, or inactive. Skipping notification and canceling future alarms.")
                    cancelAlarm(context, taskId)
                    return@launch
                }

                // Match day of week
                val todayCalendar = Calendar.getInstance()
                val dayOfWeekInt = todayCalendar.get(Calendar.DAY_OF_WEEK)
                val dayName = when (dayOfWeekInt) {
                    Calendar.SUNDAY -> "SUNDAY"
                    Calendar.MONDAY -> "MONDAY"
                    Calendar.TUESDAY -> "TUESDAY"
                    Calendar.WEDNESDAY -> "WEDNESDAY"
                    Calendar.THURSDAY -> "THURSDAY"
                    Calendar.FRIDAY -> "FRIDAY"
                    Calendar.SATURDAY -> "SATURDAY"
                    else -> ""
                }
                
                val isScheduledForToday = scheduledDays.isBlank() || scheduledDays.split(",").map { it.trim().uppercase() }.contains(dayName)
                
                if (isScheduledForToday) {
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    val channelId = "task_reminders"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val channel = NotificationChannel(
                            channelId,
                            "Goal Task Reminders",
                            NotificationManager.IMPORTANCE_HIGH
                        ).apply {
                            description = "Reminders for scheduled goal tasks"
                            enableVibration(true)
                            setShowBadge(true)
                        }
                        notificationManager.createNotificationChannel(channel)
                    }

                    // Calculate real progress for the progress bar
                    val taskProgresses = db.appDao().getProgressForTask(taskId)
                    var totalCompleted = 0
                    var totalRequired = 0
                    val targetVal = task.targetValue
                    if (task.targetType == "PLAY_COUNT") {
                        taskProgresses.forEach { p ->
                            totalCompleted += minOf(p.completedPlayCount, targetVal)
                            totalRequired += targetVal
                        }
                    } else {
                        taskProgresses.forEach { p ->
                            totalCompleted += minOf(p.getDaysList().size, targetVal)
                            totalRequired += targetVal
                        }
                    }
                    val progressPercent = if (totalRequired > 0) {
                        ((totalCompleted.toFloat() / totalRequired.toFloat()) * 100).toInt().coerceIn(0, 100)
                    } else 0

                    val launchIntent = Intent(context, com.example.MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("OPEN_TASK_ID", taskId)
                    }
                    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }
                    val pendingIntent = PendingIntent.getActivity(context, taskId.toInt(), launchIntent, flags)

                    val displayTaskTitle = task.getDisplayTitle()

                    // Action 1: Play Now action intent
                    val playIntent = Intent(context, AlarmReceiver::class.java).apply {
                        this.action = ACTION_PLAY_TASK_TRACK
                        putExtra("TASK_ID", taskId)
                        putExtra("TASK_TITLE", displayTaskTitle)
                    }
                    val playPendingIntent = PendingIntent.getBroadcast(
                        context,
                        (taskId * 10 + 1).toInt(),
                        playIntent,
                        flags
                    )

                    // Action 2: Snooze 1h action intent
                    val snoozeIntent = Intent(context, AlarmReceiver::class.java).apply {
                        this.action = ACTION_SNOOZE_TASK
                        putExtra("TASK_ID", taskId)
                        putExtra("TASK_TITLE", displayTaskTitle)
                        putExtra("SCHEDULED_DAYS", scheduledDays)
                        putExtra("REMINDER_TIME", reminderTime)
                    }
                    val snoozePendingIntent = PendingIntent.getBroadcast(
                        context,
                        (taskId * 10 + 2).toInt(),
                        snoozeIntent,
                        flags
                    )
                    
                    // Notification title and description
                    val title = Loc.getText("reminder_desc")
                    val progressText = Loc.getFormattedText("task_progress_format", progressPercent)
                    val bigTextContent = "$displayTaskTitle\n$progressText"

                    val isDark = try {
                        val sharedPref = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                        val themeSetting = sharedPref.getString("theme", "dark") ?: "dark"
                        when (themeSetting) {
                            "light" -> false
                            "dark" -> true
                            else -> {
                                val nightModeFlags = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                                nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
                            }
                        }
                    } catch (e: Exception) {
                        true
                    }

                    val notifBgColor = if (isDark) 0xFF1E1A22.toInt() else 0xFFF4EEF8.toInt()
                    val largeIcon = getLargeIconBitmap(context, isDark)

                    val bigTextStyle = NotificationCompat.BigTextStyle()
                        .setBigContentTitle(title)
                        .bigText(bigTextContent)
                        .setSummaryText(Loc.getText("tasks"))

                    val playActionLabel = Loc.getText("play_now_action")
                    val snoozeActionLabel = Loc.getText("snooze_1h_action")

                    val notification = NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(com.example.R.drawable.ic_logo)
                        .setContentTitle(title)
                        .setContentText(displayTaskTitle)
                        .setStyle(bigTextStyle)
                        .setProgress(100, progressPercent, false)
                        .setColor(notifBgColor)
                        .setColorized(true)
                        .apply {
                            if (largeIcon != null) {
                                setLargeIcon(largeIcon)
                            }
                        }
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setDefaults(NotificationCompat.DEFAULT_ALL)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)
                        .addAction(android.R.drawable.ic_media_play, "▶ $playActionLabel", playPendingIntent)
                        .addAction(android.R.drawable.ic_lock_idle_alarm, "⏱ $snoozeActionLabel", snoozePendingIntent)
                        .build()
                        
                    notificationManager.notify(taskId.toInt(), notification)
                }
                
                // Reschedule alarm for tomorrow at this same time
                rescheduleNextDay(context, taskId, task.getDisplayTitle(), scheduledDays, reminderTime)
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Error in async onReceive for task $taskId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
    
    private fun rescheduleNextDay(
        context: Context,
        taskId: Long,
        taskTitle: String,
        scheduledDays: String,
        reminderTime: String
    ) {
        val normalizedTime = normalizeReminderTime(reminderTime)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val nextIntent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("TASK_ID", taskId)
            putExtra("TASK_TITLE", taskTitle)
            putExtra("SCHEDULED_DAYS", scheduledDays)
            putExtra("REMINDER_TIME", normalizedTime)
        }
        
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            nextIntent,
            flags
        )
        
        val cal = Calendar.getInstance()
        val sdf = java.text.SimpleDateFormat("hh:mm a", Locale.US)
        try {
            val date = sdf.parse(normalizedTime)
            if (date != null) {
                val parsedCal = Calendar.getInstance().apply { time = date }
                cal.set(Calendar.HOUR_OF_DAY, parsedCal.get(Calendar.HOUR_OF_DAY))
                cal.set(Calendar.MINUTE, parsedCal.get(Calendar.MINUTE))
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                
                // Set to tomorrow at this same time
                cal.add(Calendar.DAY_OF_YEAR, 1)
                
                setExactAlarmSafely(alarmManager, cal.timeInMillis, pendingIntent)
            }
        } catch (e: Exception) {
            try {
                Log.w("AlarmReceiver", "Failed to parse reminderTime '$reminderTime' (normalized: '$normalizedTime') in rescheduleNextDay", e)
            } catch (_: Throwable) {}
        }
    }
    
    companion object {
        const val ACTION_PLAY_TASK_TRACK = "com.example.ACTION_PLAY_TASK_TRACK"
        const val ACTION_SNOOZE_TASK = "com.example.ACTION_SNOOZE_TASK"

        /**
         * Normalizes localized digits (Arabic-Indic ٠-٩ and Extended ۰-۹) and localized AM/PM markers
         * into canonical Locale.US "hh:mm a" format so SimpleDateFormat(..., Locale.US) always succeeds.
         */
        fun normalizeReminderTime(raw: String): String {
            val digitNormalized = buildString(raw.length) {
                for (ch in raw) {
                    when (ch) {
                        in '\u0660'..'\u0669' -> append(('0'.code + (ch.code - '\u0660'.code)).toChar())
                        in '\u06F0'..'\u06F9' -> append(('0'.code + (ch.code - '\u06F0'.code)).toChar())
                        else -> append(ch)
                    }
                }
            }
            return digitNormalized
                .replace("صباحاً", "AM")
                .replace("صباحا", "AM")
                .replace("مساءً", "PM")
                .replace("مساء", "PM")
                .replace(Regex("""(?<=\s|\d)ص(?=\s|$)"""), "AM")
                .replace(Regex("""(?<=\s|\d)م(?=\s|$)"""), "PM")
                .trim()
                .replace(Regex("""\s+"""), " ")
                .uppercase(Locale.US)
        }

        suspend fun rescheduleAllActiveTasks(
            context: Context,
            tasks: List<com.example.data.Task>? = null
        ): Int {
            val appContext = context.applicationContext
            val allTasks = tasks ?: AppDatabase.getDatabase(appContext).appDao().getAllTasksDirect()
            var scheduledCount = 0
            for (task in allTasks) {
                if (!task.isCompleted && task.status == "ACTIVE" && task.reminderTime.isNotBlank()) {
                    scheduleAlarm(
                        context = appContext,
                        taskId = task.id,
                        taskTitle = task.getDisplayTitle(),
                        scheduledDays = task.scheduledDays,
                        reminderTime = task.reminderTime
                    )
                    scheduledCount++
                }
            }
            return scheduledCount
        }

        private fun setExactAlarmSafely(
            alarmManager: android.app.AlarmManager,
            triggerAtMillis: Long,
            pendingIntent: PendingIntent
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        android.app.AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        android.app.AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    android.app.AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        }

        fun snoozeAlarm(
            context: Context,
            taskId: Long,
            taskTitle: String,
            scheduledDays: String,
            reminderTime: String,
            minutes: Int = 60
        ) {
            val normalizedTime = normalizeReminderTime(reminderTime)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("TASK_ID", taskId)
                putExtra("TASK_TITLE", taskTitle)
                putExtra("SCHEDULED_DAYS", scheduledDays)
                putExtra("REMINDER_TIME", normalizedTime)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                taskId.toInt(),
                intent,
                flags
            )
            val triggerTime = System.currentTimeMillis() + (minutes * 60 * 1000L)
            setExactAlarmSafely(alarmManager, triggerTime, pendingIntent)
            try {
                Log.d("AlarmReceiver", "Snoozed alarm for Task ID $taskId by $minutes minutes")
            } catch (_: Throwable) {}
        }

        fun cancelAlarm(context: Context, taskId: Long) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_NO_CREATE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                taskId.toInt(),
                intent,
                flags
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
        
        fun scheduleAlarm(
            context: Context,
            taskId: Long,
            taskTitle: String,
            scheduledDays: String,
            reminderTime: String
        ) {
            cancelAlarm(context, taskId)
            
            val normalizedTime = normalizeReminderTime(reminderTime)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("TASK_ID", taskId)
                putExtra("TASK_TITLE", taskTitle)
                putExtra("SCHEDULED_DAYS", scheduledDays)
                putExtra("REMINDER_TIME", normalizedTime)
            }
            
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                taskId.toInt(),
                intent,
                flags
            )
            
            val cal = Calendar.getInstance()
            val sdf = java.text.SimpleDateFormat("hh:mm a", Locale.US)
            try {
                val date = sdf.parse(normalizedTime)
                if (date != null) {
                    val parsedCal = Calendar.getInstance().apply { time = date }
                    cal.set(Calendar.HOUR_OF_DAY, parsedCal.get(Calendar.HOUR_OF_DAY))
                    cal.set(Calendar.MINUTE, parsedCal.get(Calendar.MINUTE))
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    
                    if (cal.timeInMillis <= System.currentTimeMillis()) {
                        cal.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    
                    setExactAlarmSafely(alarmManager, cal.timeInMillis, pendingIntent)
                    try {
                        Log.d("AlarmReceiver", "Scheduled alarm for Task ID $taskId at ${cal.time}")
                    } catch (_: Throwable) {}
                }
            } catch (e: Exception) {
                try {
                    Log.w("AlarmReceiver", "Failed to parse reminderTime '$reminderTime' (normalized: '$normalizedTime') for Task ID $taskId", e)
                } catch (_: Throwable) {}
            }
        }

        private fun getLargeIconBitmap(context: Context, isDark: Boolean): android.graphics.Bitmap? {
            return try {
                val size = (64 * context.resources.displayMetrics.density).toInt()
                val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)

                val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (isDark) 0xFF352C42.toInt() else 0xFFEADBFC.toInt()
                    style = android.graphics.Paint.Style.FILL
                }
                val radius = size / 2f
                canvas.drawCircle(radius, radius, radius, bgPaint)

                val drawable = androidx.core.content.ContextCompat.getDrawable(context, com.example.R.drawable.ic_logo)?.mutate() ?: return bitmap
                val tintColor = if (isDark) 0xFFD3C2FF.toInt() else 0xFF6750A4.toInt()
                androidx.core.graphics.drawable.DrawableCompat.setTint(drawable, tintColor)

                val padding = (size * 0.16f).toInt()
                drawable.setBounds(padding, padding, size - padding, size - padding)
                drawable.draw(canvas)
                bitmap
            } catch (e: Exception) {
                null
            }
        }
    }
}
