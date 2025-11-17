package com.nativealarms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.facebook.react.bridge.Arguments
import com.facebook.react.modules.core.DeviceEventManagerModule

/**
 * BroadcastReceiver that handles alarm trigger events
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "native_alarms_channel"
        const val CHANNEL_NAME = "Alarms"
        const val ACTION_DISMISS = "com.nativealarms.ACTION_DISMISS"
        const val ACTION_SNOOZE = "com.nativealarms.ACTION_SNOOZE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra("alarmId") ?: return
        val title = intent.getStringExtra("title") ?: "Alarm"
        val body = intent.getStringExtra("body")
        val sound = intent.getStringExtra("sound")
        val scheduleType = intent.getStringExtra("scheduleType")
        val isInexact = intent.getBooleanExtra("isInexact", false)

        // Create notification channel
        createNotificationChannel(context)

        // Show notification
        showAlarmNotification(context, alarmId, title, body, sound)

        // Send event to React Native
        sendAlarmFiredEvent(context, alarmId)

        // Reschedule if recurring or interval
        if (scheduleType == "recurring" || scheduleType == "interval") {
            rescheduleAlarm(context, alarmId, isInexact)
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Alarm notifications"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showAlarmNotification(
        context: Context,
        alarmId: String,
        title: String,
        body: String?,
        sound: String?
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create intent to launch app when notification is tapped
        val packageName = context.packageName
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("alarmId", alarmId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            alarmId.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dismiss action
        val dismissIntent = Intent(context, AlarmActionReceiver::class.java).apply {
            action = ACTION_DISMISS
            putExtra("alarmId", alarmId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            (alarmId + "_dismiss").hashCode(),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze action
        val snoozeIntent = Intent(context, AlarmActionReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra("alarmId", alarmId)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            (alarmId + "_snooze").hashCode(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .addAction(android.R.drawable.ic_delete, "Dismiss", dismissPendingIntent)
            .addAction(android.R.drawable.ic_menu_recent_history, "Snooze", snoozePendingIntent)

        if (body != null) {
            notificationBuilder.setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }

        // Sound
        if (sound != null && sound != "none") {
            val soundUri = if (sound == "default") {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            } else {
                // Custom sound from res/raw
                val soundResId = context.resources.getIdentifier(
                    sound,
                    "raw",
                    context.packageName
                )
                if (soundResId != 0) {
                    android.net.Uri.parse("android.resource://${context.packageName}/$soundResId")
                } else {
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                }
            }
            notificationBuilder.setSound(soundUri)
        } else {
            notificationBuilder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
        }

        // Show notification
        notificationManager.notify(alarmId.hashCode(), notificationBuilder.build())
    }

    private fun sendAlarmFiredEvent(context: Context, alarmId: String) {
        try {
            // Get alarm data from storage
            val alarmData = AlarmStorage.getAlarm(context, alarmId)
                ?: AlarmStorage.getAlarm(context, "fallback_$alarmId")

            if (alarmData != null) {
                val params = Arguments.createMap().apply {
                    putMap("alarm", Arguments.createMap().apply {
                        putString("id", alarmId)
                        putMap("schedule", alarmData.schedule)
                        putMap("config", alarmData.config)
                        putString("nextFireDate", alarmData.nextFireDate.toString())
                        putBoolean("isActive", true)
                    })
                    putString("firedAt", System.currentTimeMillis().toString())
                }

                // Send event through React Native module
                val reactContext = getReactContext(context)
                reactContext?.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                    ?.emit("RNNativeAlarms_AlarmFired", params)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun rescheduleAlarm(context: Context, alarmId: String, isInexact: Boolean) {
        try {
            // Get alarm data
            val storageId = if (isInexact) "fallback_$alarmId" else alarmId
            val alarmData = AlarmStorage.getAlarm(context, storageId) ?: return

            // Reschedule using appropriate manager
            if (isInexact) {
                val fallback = NotificationFallback(context)
                fallback.scheduleAlarm(alarmData.schedule, alarmData.config)
            } else {
                val exactManager = ExactAlarmManager(context)
                exactManager.scheduleAlarm(alarmData.schedule, alarmData.config)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getReactContext(context: Context): com.facebook.react.bridge.ReactApplicationContext? {
        return try {
            context.applicationContext as? com.facebook.react.bridge.ReactApplicationContext
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Handles alarm action buttons (dismiss, snooze)
 */
class AlarmActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra("alarmId") ?: return
        val action = intent.action

        when (action) {
            AlarmReceiver.ACTION_DISMISS -> {
                // Just dismiss the notification
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(alarmId.hashCode())
            }

            AlarmReceiver.ACTION_SNOOZE -> {
                // Dismiss notification
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(alarmId.hashCode())

                // Snooze for 10 minutes
                try {
                    val alarmData = AlarmStorage.getAlarm(context, alarmId)
                        ?: AlarmStorage.getAlarm(context, "fallback_$alarmId")

                    if (alarmData != null) {
                        val isInexact = alarmData.id.startsWith("fallback_")

                        if (isInexact) {
                            val fallback = NotificationFallback(context)
                            fallback.snoozeAlarm(alarmId, 10)
                        } else {
                            val exactManager = ExactAlarmManager(context)
                            exactManager.snoozeAlarm(alarmId, 10)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
