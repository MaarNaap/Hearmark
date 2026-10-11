package com.example

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.receiver.AlarmReceiver
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AlarmScheduleComputationTest {

    @Test
    fun scheduleAndCancelAlarm_registersAndRemovesPendingAlarmInAlarmManager() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarmManager = shadowOf(alarmManager)

        AlarmReceiver.scheduleAlarm(
            context = context,
            taskId = 555L,
            taskTitle = "Morning Listening",
            scheduledDays = "MONDAY,WEDNESDAY,FRIDAY",
            reminderTime = "09:15 AM"
        )

        val scheduledAlarms = shadowAlarmManager.scheduledAlarms
        assertFalse("Expected at least one scheduled alarm", scheduledAlarms.isEmpty())
        val latest = scheduledAlarms.last()
        assertTrue("Trigger time should be in the future", latest.triggerAtTime > System.currentTimeMillis() - 5000L)

        AlarmReceiver.cancelAlarm(context, 555L)
        assertTrue("Expected alarm to be cancelled", shadowAlarmManager.scheduledAlarms.isEmpty())
    }

    @Test
    fun snoozeAlarm_schedulesFutureTriggerOffsetByMinutes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarmManager = shadowOf(alarmManager)

        val beforeMs = System.currentTimeMillis()
        AlarmReceiver.snoozeAlarm(
            context = context,
            taskId = 777L,
            taskTitle = "Evening Review",
            scheduledDays = "SUNDAY",
            reminderTime = "٠٨:٠٠ مساءً",
            minutes = 45
        )

        val scheduled = shadowAlarmManager.scheduledAlarms.lastOrNull()
        assertNotNull(scheduled)
        val expectedMinTrigger = beforeMs + 44 * 60 * 1000L
        val expectedMaxTrigger = beforeMs + 46 * 60 * 1000L
        assertTrue(scheduled!!.triggerAtTime in expectedMinTrigger..expectedMaxTrigger)

        AlarmReceiver.cancelAlarm(context, 777L)
    }

    @Test
    fun snoozeVocabReminder_andActionSnoozeVocabReminder_schedulesFutureAlarmAndCancelsNotification() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarmManager = shadowOf(alarmManager)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val shadowNotificationManager = shadowOf(notificationManager)

        // Post a dummy vocab notification first
        val dummyNotif = androidx.core.app.NotificationCompat.Builder(context, AlarmReceiver.VOCAB_REMINDER_CHANNEL_ID)
            .setSmallIcon(com.example.R.drawable.ic_logo)
            .setContentTitle("Vocabulary Review Due")
            .setContentText("3 items due")
            .build()
        notificationManager.notify(AlarmReceiver.DAILY_VOCAB_NOTIFICATION_ID, dummyNotif)
        assertNotNull(shadowNotificationManager.getNotification(AlarmReceiver.DAILY_VOCAB_NOTIFICATION_ID))

        val beforeMs = System.currentTimeMillis()
        val snoozeIntent = android.content.Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_SNOOZE_VOCAB_REMINDER
            putExtra("REMINDER_TIME", "09:00 AM")
        }
        AlarmReceiver().onReceive(context, snoozeIntent)

        // Notification should be cancelled
        assertNull(shadowNotificationManager.getNotification(AlarmReceiver.DAILY_VOCAB_NOTIFICATION_ID))

        // Snoozed alarm should be scheduled ~60 minutes from now
        val scheduled = shadowAlarmManager.scheduledAlarms.lastOrNull()
        assertNotNull(scheduled)
        val expectedMinTrigger = beforeMs + 59 * 60 * 1000L
        val expectedMaxTrigger = beforeMs + 61 * 60 * 1000L
        assertTrue(scheduled!!.triggerAtTime in expectedMinTrigger..expectedMaxTrigger)

        AlarmReceiver.cancelDailyVocabReminder(context)
    }
}
