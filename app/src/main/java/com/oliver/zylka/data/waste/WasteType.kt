package com.oliver.zylka.data.waste

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.oliver.zylka.R

/**
 * The waste streams that appear in the bundled collection calendar. Colors are chosen to
 * match the real bin colors used in Germany (schwarz/braun/blau/gelb).
 */
enum class WasteType(
    val id: String,
    @param:StringRes val label: Int,
    @param:ColorRes val color: Int,
) {
    RESTABFALL("RESTABFALL", R.string.waste_type_restabfall, R.color.waste_restabfall),
    BIOTONNE("BIOTONNE", R.string.waste_type_biotonne, R.color.waste_biotonne),
    ALTPAPIER("ALTPAPIER", R.string.waste_type_altpapier, R.color.waste_altpapier),
    GELBER_SACK("GELBER_SACK", R.string.waste_type_gelber_sack, R.color.waste_gelber_sack),
    ;

    companion object {
        fun fromId(id: String): WasteType = entries.first { it.id == id }
    }
}
