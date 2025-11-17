package com.nativealarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import java.util.Calendar

/**
 * Fallback alarm manager using inexact alarms
 * Used when exact alarm permission is not granted
 */
class NotificationFallback(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val storagePrefix = "fallback_"

    /**
     * Schedule an inexact alarm
     */
    fun scheduleAlarm(schedule: ReadableMap, config: ReadableMap): WritableMap {
        val alarmId = schedule.getString("id") ?: throw IllegalArgumentException("Alarm ID required")
        val prefixedId = storagePrefix + alarmId
        val type = schedule.getString("type") ?: "fixed"

        // Calculate trigger time
        val triggerTime = when (type) {
            "recurring" -> calculateRecurringTriggerTime(schedule)
            "interval" -> calculateIntervalTriggerTime(schedule)
            "fixed" -> calculateFixedTriggerTime(schedule)
            else -> throw IllegalArgumentException("Invalid schedule type: $type")
        }

        // Schedule inexact alarm
        when (type) {
            "recurring" -> scheduleRecurringAlarm(prefixedId, schedule, config, triggerTime)
            "interval", "fixed" -> scheduleSingleAlarm(prefixedId, schedule, config, triggerTime)
        }

        // Save to storage
        AlarmStorage.saveAlarm(context, prefixedId, schedule, config, triggerTime)

        // Build response
        return Arguments.createMap().apply {
            putString("id", alarmId)
            putMap("schedule", schedule)
            putMap("config", config)
            putString("nextFireDate", triggerTime.toString())
            putString("capability", "inexact")
            putBoolean("isActive", true)
            putString("platformAlarmId", prefixedId)
        }
    }

    /**
     * Cancel alarm by ID
     */
    fun cancelAlarm(alarmId: String) {
        val prefixedId = storagePrefix + alarmId
        val alarmData = AlarmStorage.getAlarm(context, prefixedId)

        if (alarmData != null) {
            val type = alarmData.schedule.getString("type")

            if (type == "recurring") {
                // Cancel all day-specific alarms
                val daysOfWeek = alarmData.schedule.getArray("daysOfWeek")
                if (daysOfWeek != null) {
                    for (i in 0 until daysOfWeek.size()) {
                        val day = daysOfWeek.getInt(i)
                        cancelSingleAlarm("$prefixedId-day$day")
                    }
                } else {
                    cancelSingleAlarm(prefixedId)
                }
            } else {
                cancelSingleAlarm(prefixedId)
            }
        } else {
            cancelSingleAlarm(prefixedId)
        }

        AlarmStorage.deleteAlarm(context, prefixedId)
    }

    /**
     * Cancel all alarms
     */
    fun cancelAllAlarms() {
        val alarms = getAllAlarms()
        alarms.forEach { alarm ->
            alarm.getString("id")?.let { cancelAlarm(it) }
        }
    }

    /**
     * Cancel alarms by category
     */
    fun cancelAlarmsByCategory(category: String) {
        val alarms = getAlarmsByCategory(category)
        alarms.forEach { alarm ->
            alarm.getString("id")?.let { cancelAlarm(it) }
        }
    }

    /**
     * Get alarm by ID
     */
    fun getAlarm(alarmId: String): WritableMap? {
        val prefixedId = storagePrefix + alarmId
        val alarmData = AlarmStorage.getAlarm(context, prefixedId) ?: return null

        return Arguments.createMap().apply {
            putString("id", alarmId) // Return original ID
            putMap("schedule", alarmData.schedule)
            putMap("config", alarmData.config)
            putString("nextFireDate", alarmData.nextFireDate.toString())
            putString("capability", "inexact")
            putBoolean("isActive", true)
            putString("platformAlarmId", prefixedId)
        }
    }

    /**
     * Get all alarms
     */
    fun getAllAlarms(): List<WritableMap> {
        val allAlarms = AlarmStorage.getAllAlarms(context)
        val fallbackAlarms = allAlarms.filter { it.id.startsWith(storagePrefix) }

        return fallbackAlarms.map { alarmData ->
            Arguments.createMap().apply {
                putString("id", alarmData.id.removePrefix(storagePrefix)) // Return original ID
                putMap("schedule", alarmData.schedule)
                putMap("config", alarmData.config)
                putString("nextFireDate", alarmData.nextFireDate.toString())
                putString("capability", "inexact")
                putBoolean("isActive", true)
                putString("platformAlarmId", alarmData.id)
            }
        }
    }

    /**
     * Get alarms by category
     */
    fun getAlarmsByCategory(category: String): List<WritableMap> {
        val categoryAlarms = AlarmStorage.getAlarmsByCategory(context, category)
        val fallbackAlarms = categoryAlarms.filter { it.id.startsWith(storagePrefix) }

        return fallbackAlarms.map { alarmData ->
            Arguments.createMap().apply {
                putString("id", alarmData.id.removePrefix(storagePrefix))
                putMap("schedule", alarmData.schedule)
                putMap("config", alarmData.config)
                putString("nextFireDate", alarmData.nextFireDate.toString())
                putString("capability", "inexact")
                putBoolean("isActive", true)
                putString("platformAlarmId", alarmData.id)
            }
        }
    }

    /**
     * Snooze alarm
     */
    fun snoozeAlarm(alarmId: String, minutes: Int) {
        val prefixedId = storagePrefix + alarmId
        val alarmData = AlarmStorage.getAlarm(context, prefixedId) ?: return

        // Cancel current alarm
        cancelAlarm(alarmId)

        // Calculate snooze time
        val snoozeTime = System.currentTimeMillis() + (minutes * 60 * 1000)

        // Schedule new alarm for snooze time
        val intent = createAlarmIntent(alarmId, alarmData.schedule, alarmData.config)
        val pendingIntent = createPendingIntent(prefixedId, intent)

        scheduleInexactAlarm(snoozeTime, pendingIntent)

        // Update storage with new time
        AlarmStorage.saveAlarm(
            context,
            prefixedId,
            alarmData.schedule,
            alarmData.config,
            snoozeTime
        )
    }

    // Private helper methods

    private fun scheduleSingleAlarm(
        alarmId: String,
        schedule: ReadableMap,
        config: ReadableMap,
        triggerTime: Long
    ) {
        val intent = createAlarmIntent(alarmId.removePrefix(storagePrefix), schedule, config)
        val pendingIntent = createPendingIntent(alarmId, intent)

        scheduleInexactAlarm(triggerTime, pendingIntent)
    }

    private fun scheduleRecurringAlarm(
        alarmId: String,
        schedule: ReadableMap,
        config: ReadableMap,
        triggerTime: Long
    ) {
        val daysOfWeek = schedule.getArray("daysOfWeek")

        if (daysOfWeek == null || daysOfWeek.size() == 0) {
            // Daily alarm
            scheduleSingleAlarm(alarmId, schedule, config, triggerTime)
        } else {
            // Specific days - schedule one alarm per day
            for (i in 0 until daysOfWeek.size()) {
                val day = daysOfWeek.getInt(i)
                val dayAlarmId = "$alarmId-day$day"

                // Calculate next occurrence for this day
                val dayTriggerTime = calculateNextOccurrenceForDay(schedule, day)

                val intent = createAlarmIntent(alarmId.removePrefix(storagePrefix), schedule, config)
                val pendingIntent = createPendingIntent(dayAlarmId, intent)

                scheduleInexactAlarm(dayTriggerTime, pendingIntent)
            }
        }
    }

    private fun cancelSingleAlarm(alarmId: String) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    private fun scheduleInexactAlarm(triggerTime: Long, pendingIntent: PendingIntent) {
        // Use setInexactRepeating for better battery life
        // Note: This won't repeat automatically, we reschedule in AlarmReceiver
        alarmManager.set(
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            pendingIntent
        )
    }

    private fun createAlarmIntent(
        alarmId: String,
        schedule: ReadableMap,
        config: ReadableMap
    ): Intent {
        return Intent(context, AlarmReceiver::class.java).apply {
            action = "com.nativealarms.ALARM_ACTION"
            putExtra("alarmId", alarmId)
            putExtra("title", config.getString("title"))
            putExtra("body", config.getString("body"))
            putExtra("sound", config.getString("sound"))
            putExtra("category", config.getString("category"))
            putExtra("scheduleType", schedule.getString("type"))
            putExtra("isInexact", true)
        }
    }

    private fun createPendingIntent(alarmId: String, intent: Intent): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            alarmId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun calculateFixedTriggerTime(schedule: ReadableMap): Long {
        val timeMap = schedule.getMap("time")
            ?: throw IllegalArgumentException("Time required for fixed alarm")

        val hour = timeMap.getInt("hour")
        val minute = timeMap.getInt("minute")

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        return calendar.timeInMillis
    }

    private fun calculateRecurringTriggerTime(schedule: ReadableMap): Long {
        val timeMap = schedule.getMap("time")
            ?: throw IllegalArgumentException("Time required for recurring alarm")

        val hour = timeMap.getInt("hour")
        val minute = timeMap.getInt("minute")
        val daysOfWeek = schedule.getArray("daysOfWeek")

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (daysOfWeek == null || daysOfWeek.size() == 0) {
            // Daily alarm
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
        } else {
            // Find next occurrence based on days of week
            val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1
            var daysToAdd = 1

            for (i in 1..7) {
                val checkDay = (currentDayOfWeek + i) % 7

                for (j in 0 until daysOfWeek.size()) {
                    if (daysOfWeek.getInt(j) == checkDay) {
                        daysToAdd = i
                        break
                    }
                }

                if (daysToAdd != 1 || i == 1) break
            }

            calendar.add(Calendar.DAY_OF_YEAR, daysToAdd)
        }

        return calendar.timeInMillis
    }

    private fun calculateNextOccurrenceForDay(schedule: ReadableMap, dayOfWeek: Int): Long {
        val timeMap = schedule.getMap("time")
            ?: throw IllegalArgumentException("Time required")

        val hour = timeMap.getInt("hour")
        val minute = timeMap.getInt("minute")

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1
        var daysToAdd = (dayOfWeek - currentDayOfWeek + 7) % 7

        if (daysToAdd == 0 && calendar.timeInMillis <= System.currentTimeMillis()) {
            daysToAdd = 7
        }

        calendar.add(Calendar.DAY_OF_YEAR, daysToAdd)

        return calendar.timeInMillis
    }

    private fun calculateIntervalTriggerTime(schedule: ReadableMap): Long {
        val intervalMinutes = schedule.getInt("intervalMinutes")
        if (intervalMinutes <= 0) {
            throw IllegalArgumentException("Invalid interval")
        }

        val startTime = if (schedule.hasKey("startTime")) {
            schedule.getDouble("startTime").toLong()
        } else {
            System.currentTimeMillis()
        }

        return startTime + (intervalMinutes * 60 * 1000)
    }
}
