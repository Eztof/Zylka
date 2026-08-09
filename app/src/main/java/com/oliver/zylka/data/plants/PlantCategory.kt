package com.oliver.zylka.data.plants

import androidx.annotation.StringRes
import com.oliver.zylka.R

/** Grobe Pflanzenkategorie - liefert den Startwert für [Plant.kcBasis] (den
 * Verdunstungs-Faktor relativ zur Referenzverdunstung ET0), der beim Anlegen einer Pflanze
 * vorbelegt, aber frei überschreibbar ist. */
enum class PlantCategory(
    val id: String,
    @param:StringRes val label: Int,
    val kcBasisDefault: Double,
) {
    SUKKULENTE(id = "SUKKULENTE", label = R.string.plant_category_sukkulente, kcBasisDefault = 0.3),
    MEDITERRAN(id = "MEDITERRAN", label = R.string.plant_category_mediterran, kcBasisDefault = 0.6),
    STANDARD(id = "STANDARD", label = R.string.plant_category_standard, kcBasisDefault = 1.0),
    DURSTIG(id = "DURSTIG", label = R.string.plant_category_durstig, kcBasisDefault = 1.4),
    GEMUESE(id = "GEMUESE", label = R.string.plant_category_gemuese, kcBasisDefault = 1.15),
    ;

    companion object {
        fun fromId(id: String?): PlantCategory = entries.firstOrNull { it.id == id } ?: STANDARD
    }
}
