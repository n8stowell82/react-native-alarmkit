package com.nativealarms

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Reschedules alarms after device reboot
 * Android clears all alarms on reboot, so we need to restore them
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        // Reschedule all stored alarms
        rescheduleAllAlarms(context)
    }

    private fun rescheduleAllAlarms(context: Context) {
        try {
            val alarms = AlarmStorage.getAllAlarms(context)

            if (alarms.isEmpty()) {
                return
            }

            // Check if we can schedule exact alarms
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }

            for (alarmData in alarms) {
                try {
                    val isInexact = alarmData.id.startsWith("fallback_")

                    if (canScheduleExact && !isInexact) {
                        // Reschedule as exact alarm
                        val exactManager = ExactAlarmManager(context)
                        exactManager.scheduleAlarm(alarmData.schedule, alarmData.config)
                    } else {
                        // Reschedule as inexact alarm
                        val fallback = NotificationFallback(context)

                        // Remove fallback_ prefix if present for scheduling
                        val cleanId = alarmData.id.removePrefix("fallback_")
                        val scheduleWithCleanId = alarmData.schedule.copy().apply {
                            putString("id", cleanId)
                        }

                        fallback.scheduleAlarm(scheduleWithCleanId, alarmData.config)
                    }
                } catch (e: Exception) {
                    // Log error but continue with other alarms
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
