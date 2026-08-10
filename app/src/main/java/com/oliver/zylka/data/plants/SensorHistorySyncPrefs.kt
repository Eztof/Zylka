package com.oliver.zylka.data.plants

import android.content.Context

/** Merkt sich je Sensor, wann zuletzt die Gerätehistorie synchronisiert wurde (siehe
 * [SensorHistorySync]) - verhindert, dass bei jedem App-Start erneut per BLE verbunden und
 * gegen Firestore abgeglichen wird. */
class SensorHistorySyncPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun lastSyncMillis(sensorId: String): Long = prefs.getLong(sensorId, 0L)

    fun markSynced(sensorId: String) {
        prefs.edit().putLong(sensorId, System.currentTimeMillis()).apply()
    }

    companion object {
        private const val PREFS_NAME = "sensor_history_sync"
    }
}
