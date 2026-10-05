package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val pendingResult = goAsync()
            val appContext = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val count = AlarmReceiver.rescheduleAllActiveTasks(appContext)
                    AlarmReceiver.rescheduleDailyVocabReminderIfNeeded(appContext)
                    try {
                        Log.i("BootReceiver", "Rescheduled $count active task reminders and checked daily vocab reminder after $action")
                    } catch (_: Throwable) {}
                } catch (e: Exception) {
                    try {
                        Log.e("BootReceiver", "Failed to reschedule active task reminders after $action", e)
                    } catch (_: Throwable) {}
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
