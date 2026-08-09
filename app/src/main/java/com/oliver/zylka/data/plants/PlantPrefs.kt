package com.oliver.zylka.data.plants

import android.content.Context

/** Erinnerungs-Einstellungen des Gießplaners: an/aus + der Feuchte-Schwellenwert (in % der
 * Kapazität), unter dem benachrichtigt wird - keine feste Uhrzeit, die Erinnerung feuert
 * genau dann, wenn die Prognose diesen Wert unterschreitet. */
class PlantPrefs(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var reminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var schwellenwertProzent: Int
        get() = prefs.getInt(KEY_SCHWELLENWERT, DEFAULT_SCHWELLENWERT)
        set(value) = prefs.edit().putInt(KEY_SCHWELLENWERT, value).apply()

    companion object {
        private const val PREFS_NAME = "plant_prefs"
        private const val KEY_ENABLED = "reminder_enabled"
        private const val KEY_SCHWELLENWERT = "schwellenwert_prozent"
        const val DEFAULT_SCHWELLENWERT = 50
    }
}
