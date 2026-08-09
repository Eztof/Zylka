package com.oliver.zylka.plants

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires when a scheduled watering reminder is due: shows the notification and immediately
 * arms the alarm for the next-most-urgent pot, keeping the chain alive even though only one
 * alarm is ever pending at a time (Vorbild: `WasteAlarmReceiver`).
 */
class PlantAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val potName = intent.getStringExtra(EXTRA_POT_NAME)
        if (potName != null) {
            PlantNotifier.show(context, potName)
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                PlantAlarmScheduler.rescheduleNext(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_POT_NAME = "pot_name"
    }
}
