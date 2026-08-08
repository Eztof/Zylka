package com.oliver.zylka.data.kennzeichen

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Loads the static, bundled region catalogs (one JSON file per country, shipped as an
 * Android asset) that define every collectible Kennzeichen/plate code.
 *
 * The data itself is sourced from openpotato/kfz-kennzeichen (DE/AT/CH) and
 * gregoiredavid/france-geojson (FR) - see README.md for provenance.
 */
class CatalogRepository(private val context: Context) {

    private val cache = mutableMapOf<Country, List<PlateRegion>>()

    suspend fun regionsFor(country: Country): List<PlateRegion> = withContext(Dispatchers.IO) {
        cache.getOrPut(country) { parse(country) }
    }

    private fun parse(country: Country): List<PlateRegion> {
        val text = context.assets.open(country.assetFile).bufferedReader().use { it.readText() }
        val arr = JSONArray(text)
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(
                    PlateRegion(
                        code = obj.getString("code"),
                        name = obj.getString("name"),
                        country = country,
                        state = obj.optString("state").takeIf { it.isNotBlank() },
                        stateCode = obj.optString("stateCode").takeIf { it.isNotBlank() },
                        group = obj.optString("group").takeIf { it.isNotBlank() },
                    )
                )
            }
        }
    }
}
