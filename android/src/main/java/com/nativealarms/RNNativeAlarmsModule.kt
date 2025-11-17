package com.nativealarms

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RNNativeAlarmsModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    private val exactAlarmManager: ExactAlarmManager by lazy {
        ExactAlarmManager(reactContext)
    }

    private val notificationFallback: NotificationFallback by lazy {
        NotificationFallback(reactContext)
    }

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun getName(): String = "RNNativeAlarms"

    override fun getConstants(): MutableMap<String, Any> = mutableMapOf(
        "ALARM_FIRED_EVENT" to "RNNativeAlarms_AlarmFired",
        "PERMISSION_CHANGED_EVENT" to "RNNativeAlarms_PermissionChanged"
    )

    // MARK: - Capability & Permissions

    @ReactMethod
    fun checkCapability(promise: Promise) {
        try {
            val capability = getCapabilityCheck()
            promise.resolve(capability)
        } catch (e: Exception) {
            promise.reject("CHECK_CAPABILITY_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun requestPermission(promise: Promise) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = reactContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

                if (alarmManager.canScheduleExactAlarms()) {
                    // Already granted
                    sendPermissionChangedEvent(true)
                    promise.resolve(true)
                } else {
                    // Open settings for user to grant permission
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    reactContext.startActivity(intent)

                    // We can't know if they granted it immediately, so return false
                    // The app should listen for the permission changed broadcast
                    promise.resolve(false)
                }
            } else {
                // Android < 12, no permission needed
                promise.resolve(true)
            }
        } catch (e: Exception) {
            promise.reject("REQUEST_PERMISSION_ERROR", e.message, e)
        }
    }

    // MARK: - Scheduling

    @ReactMethod
    fun scheduleAlarm(schedule: ReadableMap, config: ReadableMap, promise: Promise) {
        scope.launch {
            try {
                val capability = getCapabilityCheck()
                val capabilityType = capability["capability"] as? String ?: "notification"

                val scheduledAlarm: WritableMap = if (capabilityType == "native_alarms") {
                    // Use exact alarms
                    exactAlarmManager.scheduleAlarm(schedule, config)
                } else {
                    // Use notification fallback or inexact alarms
                    notificationFallback.scheduleAlarm(schedule, config)
                }

                promise.resolve(scheduledAlarm)
            } catch (e: Exception) {
                promise.reject("SCHEDULE_ALARM_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun updateAlarm(alarmId: String, schedule: ReadableMap, config: ReadableMap, promise: Promise) {
        scope.launch {
            try {
                // Cancel existing
                cancelAlarm(alarmId, object : Promise {
                    override fun resolve(value: Any?) {}
                    override fun reject(code: String?, message: String?) {}
                    override fun reject(code: String?, throwable: Throwable?) {}
                    override fun reject(code: String?, message: String?, throwable: Throwable?) {}
                    override fun reject(throwable: Throwable?) {}
                    override fun reject(throwable: Throwable?, userInfo: WritableMap?) {}
                    override fun reject(code: String?, userInfo: WritableMap) {}
                    override fun reject(code: String?, throwable: Throwable?, userInfo: WritableMap?) {}
                    override fun reject(code: String?, message: String?, userInfo: WritableMap) {}
                    override fun reject(code: String?, message: String?, throwable: Throwable?, userInfo: WritableMap?) {}
                    override fun reject(message: String?) {}
                })

                // Schedule new with same ID
                val mutableSchedule = Arguments.createMap()
                schedule.toHashMap().forEach { (key, value) ->
                    when (value) {
                        is String -> mutableSchedule.putString(key, value)
                        is Int -> mutableSchedule.putInt(key, value)
                        is Double -> mutableSchedule.putDouble(key, value)
                        is Boolean -> mutableSchedule.putBoolean(key, value)
                        is ReadableArray -> mutableSchedule.putArray(key, value as ReadableArray)
                        is ReadableMap -> mutableSchedule.putMap(key, value as ReadableMap)
                    }
                }
                mutableSchedule.putString("id", alarmId)

                scheduleAlarm(mutableSchedule, config, promise)
            } catch (e: Exception) {
                promise.reject("UPDATE_ALARM_ERROR", e.message, e)
            }
        }
    }

    // MARK: - Management

    @ReactMethod
    fun cancelAlarm(alarmId: String, promise: Promise) {
        scope.launch {
            try {
                exactAlarmManager.cancelAlarm(alarmId)
                notificationFallback.cancelAlarm(alarmId)
                promise.resolve(null)
            } catch (e: Exception) {
                promise.reject("CANCEL_ALARM_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun cancelAllAlarms(promise: Promise) {
        scope.launch {
            try {
                exactAlarmManager.cancelAllAlarms()
                notificationFallback.cancelAllAlarms()
                promise.resolve(null)
            } catch (e: Exception) {
                promise.reject("CANCEL_ALL_ALARMS_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun cancelAlarmsByCategory(category: String, promise: Promise) {
        scope.launch {
            try {
                exactAlarmManager.cancelAlarmsByCategory(category)
                notificationFallback.cancelAlarmsByCategory(category)
                promise.resolve(null)
            } catch (e: Exception) {
                promise.reject("CANCEL_ALARMS_BY_CATEGORY_ERROR", e.message, e)
            }
        }
    }

    // MARK: - Query

    @ReactMethod
    fun getAlarm(alarmId: String, promise: Promise) {
        scope.launch {
            try {
                val alarm = exactAlarmManager.getAlarm(alarmId)
                    ?: notificationFallback.getAlarm(alarmId)
                promise.resolve(alarm)
            } catch (e: Exception) {
                promise.reject("GET_ALARM_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun getAllAlarms(promise: Promise) {
        scope.launch {
            try {
                val alarms = Arguments.createArray()
                exactAlarmManager.getAllAlarms().forEach { alarms.pushMap(it) }
                notificationFallback.getAllAlarms().forEach { alarms.pushMap(it) }
                promise.resolve(alarms)
            } catch (e: Exception) {
                promise.reject("GET_ALL_ALARMS_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun getAlarmsByCategory(category: String, promise: Promise) {
        scope.launch {
            try {
                val alarms = Arguments.createArray()
                exactAlarmManager.getAlarmsByCategory(category).forEach { alarms.pushMap(it) }
                notificationFallback.getAlarmsByCategory(category).forEach { alarms.pushMap(it) }
                promise.resolve(alarms)
            } catch (e: Exception) {
                promise.reject("GET_ALARMS_BY_CATEGORY_ERROR", e.message, e)
            }
        }
    }

    // MARK: - Actions

    @ReactMethod
    fun snoozeAlarm(alarmId: String, minutes: Int, promise: Promise) {
        scope.launch {
            try {
                exactAlarmManager.snoozeAlarm(alarmId, minutes)
                    ?: notificationFallback.snoozeAlarm(alarmId, minutes)
                promise.resolve(null)
            } catch (e: Exception) {
                promise.reject("SNOOZE_ALARM_ERROR", e.message, e)
            }
        }
    }

    // MARK: - Helper Methods

    private fun getCapabilityCheck(): WritableMap {
        val capability = Arguments.createMap()
        val platformDetails = Arguments.createMap()

        platformDetails.putString("platform", "android")
        platformDetails.putInt("version", Build.VERSION.SDK_INT)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = reactContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val canScheduleExact = alarmManager.canScheduleExactAlarms()

            platformDetails.putBoolean("canScheduleExactAlarms", canScheduleExact)

            if (canScheduleExact) {
                capability.putString("capability", "native_alarms")
                capability.putString("reason", "Exact alarms available and permitted")
                capability.putBoolean("requiresPermission", false)
                capability.putBoolean("canRequestPermission", false)
            } else {
                capability.putString("capability", "inexact")
                capability.putString("reason", "Exact alarm permission denied, using inexact")
                capability.putBoolean("requiresPermission", true)
                capability.putBoolean("canRequestPermission", true)
            }
        } else {
            capability.putString("capability", "native_alarms")
            capability.putString("reason", "Android < 12, exact alarms available")
            capability.putBoolean("requiresPermission", false)
            capability.putBoolean("canRequestPermission", false)
        }

        capability.putMap("platformDetails", platformDetails)
        return capability
    }

    fun sendPermissionChangedEvent(granted: Boolean) {
        val params = Arguments.createMap()
        params.putBoolean("granted", granted)
        params.putString("capability", if (granted) "native_alarms" else "inexact")
        params.putString("platform", "android")

        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit("RNNativeAlarms_PermissionChanged", params)
    }

    fun sendAlarmFiredEvent(alarm: WritableMap) {
        val params = Arguments.createMap()
        params.putMap("alarm", alarm)
        params.putString("firedAt", System.currentTimeMillis().toString())

        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit("RNNativeAlarms_AlarmFired", params)
    }
}
