package com.oliver.zylka.waste

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.oliver.zylka.data.waste.WasteType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires when a scheduled waste-pickup reminder is due: shows the notification and
 * immediately arms the alarm for the next upcoming event, keeping the chain alive even
 * though only one alarm is ever pending at a time.
 */
class WasteAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val dateText = intent.getStringExtra(EXTRA_DATE)
        val typeIds = intent.getStringArrayExtra(EXTRA_TYPES)
        if (dateText != null && typeIds != null) {
            WasteNotifier.show(context, dateText, typeIds.map { WasteType.fromId(it) })
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WasteAlarmScheduler.rescheduleNext(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_DATE = "date"
        const val EXTRA_TYPES = "types"
    }
}
