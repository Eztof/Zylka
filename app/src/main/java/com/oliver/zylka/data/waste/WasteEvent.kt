package com.oliver.zylka.data.waste

import java.time.LocalDate

/** One collection date, possibly carrying more than one waste type (Biotonne + Altpapier). */
data class WasteEvent(
    val date: LocalDate,
    val types: List<WasteType>,
)
