package com.nativealarms

import android.content.Context
import android.content.SharedPreferences
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent storage for alarm metadata using SharedPreferences
 */
object AlarmStorage {
    private const val PREFS_NAME = "RNNativeAlarms"
    private const val KEY_ALARMS = "alarms"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Save alarm schedule and config
     */
    fun saveAlarm(
        context: Context,
        alarmId: String,
        schedule: ReadableMap,
        config: ReadableMap,
        nextFireDate: Long
    ) {
        val prefs = getPrefs(context)
        val alarmsJson = prefs.getString(KEY_ALARMS, "{}") ?: "{}"
        val alarmsObj = JSONObject(alarmsJson)

        val alarmData = JSONObject().apply {
            put("schedule", readableMapToJson(schedule))
            put("config", readableMapToJson(config))
            put("nextFireDate", nextFireDate)
            put("savedAt", System.currentTimeMillis())
        }

        alarmsObj.put(alarmId, alarmData)

        prefs.edit()
            .putString(KEY_ALARMS, alarmsObj.toString())
            .apply()
    }

    /**
     * Get alarm data by ID
     */
    fun getAlarm(context: Context, alarmId: String): AlarmData? {
        val prefs = getPrefs(context)
        val alarmsJson = prefs.getString(KEY_ALARMS, "{}") ?: "{}"
        val alarmsObj = JSONObject(alarmsJson)

        if (!alarmsObj.has(alarmId)) {
            return null
        }

        val alarmData = alarmsObj.getJSONObject(alarmId)
        return AlarmData(
            id = alarmId,
            schedule = jsonToWritableMap(alarmData.getJSONObject("schedule")),
            config = jsonToWritableMap(alarmData.getJSONObject("config")),
            nextFireDate = alarmData.getLong("nextFireDate")
        )
    }

    /**
     * Get all stored alarms
     */
    fun getAllAlarms(context: Context): List<AlarmData> {
        val prefs = getPrefs(context)
        val alarmsJson = prefs.getString(KEY_ALARMS, "{}") ?: "{}"
        val alarmsObj = JSONObject(alarmsJson)

        val alarms = mutableListOf<AlarmData>()
        val keys = alarmsObj.keys()

        while (keys.hasNext()) {
            val alarmId = keys.next()
            val alarmData = alarmsObj.getJSONObject(alarmId)

            alarms.add(
                AlarmData(
                    id = alarmId,
                    schedule = jsonToWritableMap(alarmData.getJSONObject("schedule")),
                    config = jsonToWritableMap(alarmData.getJSONObject("config")),
                    nextFireDate = alarmData.getLong("nextFireDate")
                )
            )
        }

        return alarms
    }

    /**
     * Get alarms by category
     */
    fun getAlarmsByCategory(context: Context, category: String): List<AlarmData> {
        return getAllAlarms(context).filter { alarm ->
            alarm.config.hasKey("category") &&
                    alarm.config.getString("category") == category
        }
    }

    /**
     * Delete alarm by ID
     */
    fun deleteAlarm(context: Context, alarmId: String) {
        val prefs = getPrefs(context)
        val alarmsJson = prefs.getString(KEY_ALARMS, "{}") ?: "{}"
        val alarmsObj = JSONObject(alarmsJson)

        alarmsObj.remove(alarmId)

        prefs.edit()
            .putString(KEY_ALARMS, alarmsObj.toString())
            .apply()
    }

    /**
     * Delete all alarms
     */
    fun deleteAllAlarms(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit()
            .putString(KEY_ALARMS, "{}")
            .apply()
    }

    /**
     * Delete alarms by category
     */
    fun deleteAlarmsByCategory(context: Context, category: String) {
        val alarmsToDelete = getAlarmsByCategory(context, category)
        alarmsToDelete.forEach { alarm ->
            deleteAlarm(context, alarm.id)
        }
    }

    // Helper: Convert ReadableMap to JSONObject
    private fun readableMapToJson(map: ReadableMap): JSONObject {
        val json = JSONObject()
        val iterator = map.keySetIterator()

        while (iterator.hasNextKey()) {
            val key = iterator.nextKey()
            val type = map.getType(key)

            when (type) {
                com.facebook.react.bridge.ReadableType.Null -> json.put(key, JSONObject.NULL)
                com.facebook.react.bridge.ReadableType.Boolean -> json.put(key, map.getBoolean(key))
                com.facebook.react.bridge.ReadableType.Number -> json.put(key, map.getDouble(key))
                com.facebook.react.bridge.ReadableType.String -> json.put(key, map.getString(key))
                com.facebook.react.bridge.ReadableType.Map -> json.put(
                    key,
                    readableMapToJson(map.getMap(key)!!)
                )
                com.facebook.react.bridge.ReadableType.Array -> json.put(
                    key,
                    readableArrayToJson(map.getArray(key)!!)
                )
            }
        }

        return json
    }

    // Helper: Convert ReadableArray to JSONArray
    private fun readableArrayToJson(array: com.facebook.react.bridge.ReadableArray): JSONArray {
        val json = JSONArray()

        for (i in 0 until array.size()) {
            val type = array.getType(i)

            when (type) {
                com.facebook.react.bridge.ReadableType.Null -> json.put(JSONObject.NULL)
                com.facebook.react.bridge.ReadableType.Boolean -> json.put(array.getBoolean(i))
                com.facebook.react.bridge.ReadableType.Number -> json.put(array.getDouble(i))
                com.facebook.react.bridge.ReadableType.String -> json.put(array.getString(i))
                com.facebook.react.bridge.ReadableType.Map -> json.put(
                    readableMapToJson(array.getMap(i))
                )
                com.facebook.react.bridge.ReadableType.Array -> json.put(
                    readableArrayToJson(array.getArray(i))
                )
            }
        }

        return json
    }

    // Helper: Convert JSONObject to WritableMap
    private fun jsonToWritableMap(json: JSONObject): WritableMap {
        val map = Arguments.createMap()
        val keys = json.keys()

        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.get(key)

            when (value) {
                JSONObject.NULL -> map.putNull(key)
                is Boolean -> map.putBoolean(key, value)
                is Int -> map.putInt(key, value)
                is Double -> map.putDouble(key, value)
                is String -> map.putString(key, value)
                is JSONObject -> map.putMap(key, jsonToWritableMap(value))
                is JSONArray -> map.putArray(key, jsonToWritableArray(value))
            }
        }

        return map
    }

    // Helper: Convert JSONArray to WritableArray
    private fun jsonToWritableArray(json: JSONArray): com.facebook.react.bridge.WritableArray {
        val array = Arguments.createArray()

        for (i in 0 until json.length()) {
            val value = json.get(i)

            when (value) {
                JSONObject.NULL -> array.pushNull()
                is Boolean -> array.pushBoolean(value)
                is Int -> array.pushInt(value)
                is Double -> array.pushDouble(value)
                is String -> array.pushString(value)
                is JSONObject -> array.pushMap(jsonToWritableMap(value))
                is JSONArray -> array.pushArray(jsonToWritableArray(value))
            }
        }

        return array
    }

    /**
     * Data class for alarm storage
     */
    data class AlarmData(
        val id: String,
        val schedule: WritableMap,
        val config: WritableMap,
        val nextFireDate: Long
    )
}
