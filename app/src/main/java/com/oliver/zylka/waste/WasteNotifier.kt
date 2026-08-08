package com.oliver.zylka.waste

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.oliver.zylka.R
import com.oliver.zylka.data.waste.WasteType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Builds and shows the "Abfall morgen abholen" notification for a due reminder. */
object WasteNotifier {

    private const val CHANNEL_ID = "waste_reminders"
    private const val NOTIFICATION_ID = 5001
    private val dateFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMANY)

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.feature_abfallkalender),
            NotificationManager.IMPORTANCE_HIGH,
        )
        manager.createNotificationChannel(channel)
    }

    fun show(context: Context, dateText: String, types: List<WasteType>) {
        ensureChannel(context)
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val title = types.joinToString(" · ") { context.getString(it.label) }
        val body = LocalDate.parse(dateText).format(dateFormatter)

        val openIntent = Intent(context, WasteCalendarActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_waste_bin)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
