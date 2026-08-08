package com.oliver.zylka.data.waste

import android.content.Context

/**
 * Reminder settings for the Abfallkalender: on/off + the time on the evening before a
 * pickup at which the notification should fire (bins must be out by 6:00 the next
 * morning per the source calendar, so "evening before" is the only rhythm offered).
 */
class WastePrefs(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var reminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var reminderHour: Int
        get() = prefs.getInt(KEY_HOUR, DEFAULT_HOUR)
        set(value) = prefs.edit().putInt(KEY_HOUR, value).apply()

    var reminderMinute: Int
        get() = prefs.getInt(KEY_MINUTE, DEFAULT_MINUTE)
        set(value) = prefs.edit().putInt(KEY_MINUTE, value).apply()

    fun setReminderTime(hour: Int, minute: Int) {
        prefs.edit().putInt(KEY_HOUR, hour).putInt(KEY_MINUTE, minute).apply()
    }

    companion object {
        private const val PREFS_NAME = "waste_prefs"
        private const val KEY_ENABLED = "reminder_enabled"
        private const val KEY_HOUR = "reminder_hour"
        private const val KEY_MINUTE = "reminder_minute"
        const val DEFAULT_HOUR = 18
        const val DEFAULT_MINUTE = 0
    }
}
