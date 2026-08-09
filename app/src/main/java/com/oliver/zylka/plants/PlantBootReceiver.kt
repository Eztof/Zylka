package com.oliver.zylka.plants

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Exakte Alarme werden vom System bei jedem Neustart des Geräts verworfen - baut die
 * Erinnerungs-Kette danach automatisch wieder auf (Vorbild: `WasteBootReceiver`). */
class PlantBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                PlantAlarmScheduler.rescheduleNext(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
