package com.oliver.zylka.data.kennzeichen

/**
 * A single collectible region/Kennzeichen entry, e.g. "M" -> "München" in Germany,
 * or "75" -> "Paris" in France.
 */
data class PlateRegion(
    val code: String,
    val name: String,
    val country: Country,
    /** Full state/canton name, if the source data has one (DE, AT). */
    val state: String? = null,
    /** Short state code used to join against a geo file, e.g. "BW" for Baden-Württemberg. */
    val stateCode: String? = null,
    /** "metropole" or "outremer" for France; null otherwise. */
    val group: String? = null,
)
