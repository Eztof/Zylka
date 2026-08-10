package com.oliver.zylka.data.hochzeit

import androidx.annotation.StringRes
import com.oliver.zylka.R

/** Art des Dienstleisters/Postens (siehe [Dienstleister]). [SONSTIGES] fängt alles auf, was
 * nicht in eine der festen Kategorien passt. */
enum class DienstleisterKategorie(
    val id: String,
    @param:StringRes val label: Int,
) {
    DJ(id = "DJ", label = R.string.hochzeit_dienstleister_kategorie_dj),
    CATERING(id = "CATERING", label = R.string.hochzeit_dienstleister_kategorie_catering),
    FOTOGRAF(id = "FOTOGRAF", label = R.string.hochzeit_dienstleister_kategorie_fotograf),
    BLUMEN(id = "BLUMEN", label = R.string.hochzeit_dienstleister_kategorie_blumen),
    LOCATION(id = "LOCATION", label = R.string.hochzeit_dienstleister_kategorie_location),
    TORTE(id = "TORTE", label = R.string.hochzeit_dienstleister_kategorie_torte),
    DEKORATION(id = "DEKORATION", label = R.string.hochzeit_dienstleister_kategorie_dekoration),
    TRANSPORT(id = "TRANSPORT", label = R.string.hochzeit_dienstleister_kategorie_transport),
    BRAUTMODE(id = "BRAUTMODE", label = R.string.hochzeit_dienstleister_kategorie_brautmode),
    SONSTIGES(id = "SONSTIGES", label = R.string.hochzeit_dienstleister_kategorie_sonstiges),
    ;

    companion object {
        fun fromId(id: String?): DienstleisterKategorie = entries.firstOrNull { it.id == id } ?: SONSTIGES
    }
}
