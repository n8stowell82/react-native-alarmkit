package com.nativealarms

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import com.facebook.react.bridge.Arguments
import com.facebook.react.modules.core.DeviceEventManagerModule

/**
 * Listens for exact alarm permission changes on Android 12+
 */
class PermissionReceiver : BroadcastReceiver() {

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) {
            return
        }

        // Check new permission state
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canScheduleExact = alarmManager.canScheduleExactAlarms()

        // Send event to React Native
        sendPermissionChangedEvent(context, canScheduleExact)

        // If permission was granted, migrate inexact alarms to exact alarms
        if (canScheduleExact) {
            migrateInexactAlarmsToExact(context)
        }
    }

    private fun sendPermissionChangedEvent(context: Context, granted: Boolean) {
        try {
            val params = Arguments.createMap().apply {
                putBoolean("granted", granted)
                putString("capability", if (granted) "native_alarms" else "inexact")
                putString("platform", "android")
            }

            val reactContext = getReactContext(context)
            reactContext?.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                ?.emit("RNNativeAlarms_PermissionChanged", params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun migrateInexactAlarmsToExact(context: Context) {
        try {
            val allAlarms = AlarmStorage.getAllAlarms(context)
            val inexactAlarms = allAlarms.filter { it.id.startsWith("fallback_") }

            val exactManager = ExactAlarmManager(context)
            val fallback = NotificationFallback(context)

            for (alarmData in inexactAlarms) {
                try {
                    // Cancel inexact alarm
                    val cleanId = alarmData.id.removePrefix("fallback_")
                    fallback.cancelAlarm(cleanId)

                    // Schedule as exact alarm
                    val scheduleWithCleanId = alarmData.schedule.copy().apply {
                        putString("id", cleanId)
                    }
                    exactManager.scheduleAlarm(scheduleWithCleanId, alarmData.config)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
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
