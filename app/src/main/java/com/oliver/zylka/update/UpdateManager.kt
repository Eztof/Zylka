package com.oliver.zylka.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.oliver.zylka.BuildConfig
import com.oliver.zylka.R
import java.io.File

/**
 * Kümmert sich um das Herunterladen und Anstoßen der Installation einer
 * neuen APK-Version. Die eigentliche Installation übernimmt der
 * System-Installer von Android - diese Klasse lädt nur herunter und öffnet
 * den passenden Installations-Intent.
 */
class UpdateManager(private val context: Context) {

    private val downloadManager: DownloadManager
        get() = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    /** True, wenn die auf dem Server hinterlegte Version neuer ist als die installierte. */
    fun isNewerThanInstalled(remoteVersionCode: Long): Boolean {
        return remoteVersionCode > BuildConfig.VERSION_CODE
    }

    /**
     * Ab Android 8 muss der Nutzer der App explizit erlauben, Pakete aus
     * unbekannten Quellen zu installieren (Play Store ist die "bekannte"
     * Quelle). Ohne diese Freigabe schlägt die Installation stumm fehl.
     */
    fun canInstallUnknownApps(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun downloadAndInstall(apkUrl: String, versionName: String) {
        val fileName = "zylka-update-$versionName.apk"
        val destinationFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )
        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("Zylka-Update")
            .setDescription("Version $versionName wird heruntergeladen")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setMimeType("application/vnd.android.package-archive")
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)

        val downloadId = downloadManager.enqueue(request)
        registerCompletionReceiver(downloadId, destinationFile)
    }

    private fun registerCompletionReceiver(downloadId: Long, destinationFile: File) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (completedId != downloadId) return

                receiverContext.unregisterReceiver(this)

                if (!destinationFile.exists()) {
                    Toast.makeText(
                        receiverContext,
                        R.string.update_download_failed,
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }
                openInstaller(destinationFile)
            }
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun openInstaller(apkFile: File) {
        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(installIntent)
    }
}
