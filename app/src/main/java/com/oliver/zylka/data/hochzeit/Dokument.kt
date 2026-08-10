package com.oliver.zylka.data.hochzeit

import java.util.Date

/** Ein hochgeladenes Bild oder PDF (siehe [HochzeitStorageClient]) - [storagePfad] und
 * [downloadUrl] beziehen sich auf dasselbe Objekt in Firebase Storage: [storagePfad] wird zum
 * Löschen gebraucht, [downloadUrl] zum Anzeigen/Öffnen. [verknuepfterDienstleisterId] ordnet
 * die Datei optional einem [Dienstleister] zu (z. B. ein Vertrag), ohne dass sie deshalb aus
 * der allgemeinen Galerie verschwindet - die zeigt immer alles. */
data class Dokument(
    val id: String = "",
    val uid: String = "",
    val name: String = "",
    val mimeTyp: String = "",
    val storagePfad: String = "",
    val downloadUrl: String = "",
    val verknuepfterDienstleisterId: String? = null,
    val hochgeladenAm: Date? = null,
) {
    val istBild: Boolean get() = mimeTyp.startsWith("image/")
    val istPdf: Boolean get() = mimeTyp == "application/pdf"
}
