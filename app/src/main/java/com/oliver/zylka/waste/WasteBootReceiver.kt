package com.oliver.zylka.waste

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Exact alarms are cleared on reboot - re-arm the reminder chain once the device (and
 * this receiver) comes back up. */
class WasteBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WasteAlarmScheduler.rescheduleNext(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
