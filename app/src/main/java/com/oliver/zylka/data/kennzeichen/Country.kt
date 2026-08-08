package com.oliver.zylka.data.kennzeichen

/** A country/region system supported by the Kennzeichen game. */
enum class Country(
    val id: String,
    val displayName: String,
    val flagEmoji: String,
    /** Länderkürzel wie auf dem blauen EU-Streifen eines Kennzeichens. */
    val badgeLetter: String,
    val assetFile: String,
    val geoFile: String?,
) {
    GERMANY("de", "Deutschland", "🇩🇪", "D", "catalog/de.json", "geo/de_kreise.geojson"),
    AUSTRIA("at", "Österreich", "🇦🇹", "A", "catalog/at.json", "geo/at_bezirke.geojson"),
    SWITZERLAND("ch", "Schweiz", "🇨🇭", "CH", "catalog/ch.json", "geo/ch_kantone.geojson"),
    FRANCE("fr", "Frankreich", "🇫🇷", "F", "catalog/fr.json", "geo/fr_departements.geojson");

    companion object {
        fun fromId(id: String?): Country = entries.firstOrNull { it.id == id } ?: GERMANY
    }
}
