package com.oliver.zylka.data.kennzeichen

/** A country/region system supported by the Kennzeichen game. */
enum class Country(
    val id: String,
    val displayName: String,
    val flagEmoji: String,
    val assetFile: String,
    val geoFile: String?,
) {
    GERMANY("de", "Deutschland", "🇩🇪", "catalog/de.json", null),
    AUSTRIA("at", "Österreich", "🇦🇹", "catalog/at.json", "geo/at_bezirke.geojson"),
    SWITZERLAND("ch", "Schweiz", "🇨🇭", "catalog/ch.json", "geo/ch_kantone.geojson"),
    FRANCE("fr", "Frankreich", "🇫🇷", "catalog/fr.json", "geo/fr_departements.geojson");

    companion object {
        fun fromId(id: String?): Country = entries.firstOrNull { it.id == id } ?: GERMANY
    }
}
