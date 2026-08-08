package com.oliver.zylka.data.notenspiegel

import androidx.annotation.StringRes
import com.oliver.zylka.R

/** Auf welche Schrittweite die berechneten Punkte-Grenzen gerundet werden. */
enum class PointsPrecision(
    val id: String,
    val step: Double,
    @param:StringRes val label: Int,
) {
    WHOLE(id = "whole", step = 1.0, label = R.string.notenspiegel_precision_whole),
    HALF(id = "half", step = 0.5, label = R.string.notenspiegel_precision_half),
    ;

    companion object {
        val DEFAULT = HALF

        fun fromId(id: String?): PointsPrecision = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
