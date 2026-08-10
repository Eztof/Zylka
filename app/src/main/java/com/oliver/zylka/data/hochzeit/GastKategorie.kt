package com.oliver.zylka.data.hochzeit

import androidx.annotation.StringRes
import com.oliver.zylka.R

/** Grobe Einordnung eines potenziellen Gasts (siehe [Gast]). */
enum class GastKategorie(
    val id: String,
    @param:StringRes val label: Int,
) {
    FREUNDE(id = "FREUNDE", label = R.string.hochzeit_gast_kategorie_freunde),
    FAMILIE(id = "FAMILIE", label = R.string.hochzeit_gast_kategorie_familie),
    ARBEITSKOLLEGEN(id = "ARBEITSKOLLEGEN", label = R.string.hochzeit_gast_kategorie_arbeitskollegen),
    ;

    companion object {
        fun fromId(id: String?): GastKategorie = entries.firstOrNull { it.id == id } ?: FREUNDE
    }
}
