package com.oliver.zylka.data.hochzeit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Firebase Storage per Hand über die REST-API angesprochen (kein `firebase-storage`-SDK, keine
 * neue Gradle-Abhängigkeit) - nach demselben Prinzip wie `WeatherRepository`/`UpdateManager`
 * (reines `HttpURLConnection`, kein neuer HTTP-Client). Das Bearer-Token kommt vom bereits
 * eingebundenen `firebase-auth-ktx`, der Bucket-Name aus den ohnehin aus `google-services.json`
 * geladenen [FirebaseApp]-Optionen - beides funktioniert unabhängig davon, ob das
 * Storage-SDK eingebunden ist.
 *
 * ⚠️ Download-URLs (siehe [upload]) funktionieren wie beim offiziellen SDK: "wer den Link hat,
 * kommt ran" - das Token in der URL trägt die Berechtigung, kein erneuter Auth-Check bei jedem
 * Abruf. Kein verstecktes Risiko, aber gut zu wissen, bevor ein Link geteilt wird.
 */
class HochzeitStorageClient(private val context: Context) {

    private val storageBucket: String
        get() = FirebaseApp.getInstance().options.storageBucket
            ?: error("Kein Firebase-Storage-Bucket konfiguriert (google-services.json prüfen)")

    /** Lädt [localUri] unter dem Objektnamen [storagePfad] (z. B. `hochzeitsplaner/{id}.jpg`)
     * hoch und liefert die fertige Download-URL (inkl. Token) zurück - direkt für
     * [Dokument.downloadUrl] nutzbar. [onProgress] wird in ca. 8-KB-Schritten aufgerufen, wie
     * `UpdateManager`s Download-Fortschritt, nur beim Schreiben statt beim Lesen. */
    suspend fun upload(localUri: Uri, storagePfad: String, mimeTyp: String, onProgress: (Int) -> Unit): String =
        withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(localUri)?.use { it.readBytes() }
                ?: error("Datei konnte nicht gelesen werden")
            val idToken = currentIdToken()
            val encodedPfad = URLEncoder.encode(storagePfad, "UTF-8")
            val url = URL(
                "https://firebasestorage.googleapis.com/v0/b/$storageBucket/o?uploadType=media&name=$encodedPfad",
            )
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("Authorization", "Bearer $idToken")
                connection.setRequestProperty("Content-Type", mimeTyp)
                connection.outputStream.use { output ->
                    val chunkSize = 8 * 1024
                    var offset = 0
                    var lastReportedPercent = -1
                    while (offset < bytes.size) {
                        val length = minOf(chunkSize, bytes.size - offset)
                        output.write(bytes, offset, length)
                        offset += length
                        val percent = (offset * 100) / bytes.size
                        if (percent != lastReportedPercent) {
                            lastReportedPercent = percent
                            withContext(Dispatchers.Main) { onProgress(percent) }
                        }
                    }
                }
                if (connection.responseCode !in 200..299) {
                    error("Upload fehlgeschlagen (Code ${connection.responseCode})")
                }
                val responseJson = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                // Kann theoretisch mehrere kommagetrennte Tokens enthalten (mehrfach hochgeladen) -
                // das erste reicht für eine frische Datei immer.
                val downloadToken = responseJson.getString("downloadTokens").substringBefore(',')
                "https://firebasestorage.googleapis.com/v0/b/$storageBucket/o/$encodedPfad?alt=media&token=$downloadToken"
            } finally {
                connection.disconnect()
            }
        }

    /** Best effort - Fehler werden vom Aufrufer verschluckt (siehe [DokumentRepository.delete]). */
    suspend fun delete(storagePfad: String) {
        withContext(Dispatchers.IO) {
            val idToken = currentIdToken()
            val encodedPfad = URLEncoder.encode(storagePfad, "UTF-8")
            val url = URL("https://firebasestorage.googleapis.com/v0/b/$storageBucket/o/$encodedPfad")
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "DELETE"
                connection.setRequestProperty("Authorization", "Bearer $idToken")
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.connect()
                connection.responseCode // Anfrage auslösen - das Ergebnis interessiert hier nicht.
            } finally {
                connection.disconnect()
            }
        }
    }

    /** Lädt ein Bild von einer Download-URL (siehe [upload]). `null` bei jedem Fehler (Netzwerk,
     * kein gültiges Bildformat, ...) statt einer Exception - eine einzelne fehlgeschlagene
     * Vorschau soll nicht die ganze Galerie zum Absturz bringen. */
    suspend fun loadBitmap(downloadUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(downloadUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            try {
                connection.inputStream.use { BitmapFactory.decodeStream(it) }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    private suspend fun currentIdToken(): String {
        val user = FirebaseAuth.getInstance().currentUser ?: error("Nicht eingeloggt")
        return user.getIdToken(false).await().token ?: error("Kein Firebase-Auth-Token verfügbar")
    }
}
