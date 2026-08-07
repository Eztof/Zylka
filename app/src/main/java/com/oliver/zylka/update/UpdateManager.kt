package com.oliver.zylka.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.oliver.zylka.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Kümmert sich um das Herunterladen und Anstoßen der Installation einer
 * neuen APK-Version. Der Download läuft komplett innerhalb der App (mit
 * Fortschrittsanzeige über [onProgress]); nur die abschließende
 * Installationsbestätigung übernimmt der System-Installer von Android -
 * das lässt sich aus Sicherheitsgründen nicht automatisieren.
 */
class UpdateManager(private val context: Context) {

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

    /**
     * Lädt die APK herunter und meldet den Fortschritt in Prozent (0-100)
     * über [onProgress] (wird auf dem Hauptthread aufgerufen). Öffnet nach
     * Abschluss automatisch den System-Installer.
     */
    suspend fun downloadAndInstall(
        apkUrl: String,
        versionName: String,
        onProgress: (percent: Int) -> Unit
    ) {
        val destinationFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "zylka-update-$versionName.apk"
        )
        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        withContext(Dispatchers.IO) {
            val connection = URL(apkUrl).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.connect()
                val totalBytes = connection.contentLength
                var lastReportedPercent = -1

                connection.inputStream.use { input ->
                    destinationFile.outputStream().use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesCopied = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesCopied += read
                            if (totalBytes > 0) {
                                val percent = ((bytesCopied * 100) / totalBytes).toInt()
                                if (percent != lastReportedPercent) {
                                    lastReportedPercent = percent
                                    withContext(Dispatchers.Main) { onProgress(percent) }
                                }
                            }
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
        }

        withContext(Dispatchers.Main) { onProgress(100) }
        openInstaller(destinationFile)
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
