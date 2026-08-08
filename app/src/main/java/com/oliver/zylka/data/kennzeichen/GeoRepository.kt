package com.oliver.zylka.data.kennzeichen

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Loads bundled GeoJSON assets (real, simplified geographic boundaries) and turns them
 * into lightweight [GeoShape]s the map screen can draw on a [android.graphics.Canvas].
 *
 * Only Polygon/MultiPolygon outer rings are kept - holes are ignored since we only need
 * silhouettes to shade, not precise cartography.
 */
class GeoRepository(private val context: Context) {

    private val cache = mutableMapOf<String, List<GeoShape>>()

    suspend fun load(assetPath: String): List<GeoShape> = withContext(Dispatchers.IO) {
        cache.getOrPut(assetPath) { parse(assetPath) }
    }

    private fun parse(assetPath: String): List<GeoShape> {
        val text = context.assets.open(assetPath).bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        val features = root.getJSONArray("features")
        return buildList {
            for (i in 0 until features.length()) {
                val feature = features.getJSONObject(i)
                val props = feature.getJSONObject("properties")
                val geometry = feature.getJSONObject("geometry")
                val polygons = when (geometry.getString("type")) {
                    "Polygon" -> listOf(outerRing(geometry.getJSONArray("coordinates")))
                    "MultiPolygon" -> {
                        val polys = geometry.getJSONArray("coordinates")
                        buildList {
                            for (p in 0 until polys.length()) {
                                add(outerRing(polys.getJSONArray(p)))
                            }
                        }
                    }
                    else -> emptyList()
                }
                val codesArray = props.getJSONArray("codes")
                val codes = buildList { for (c in 0 until codesArray.length()) add(codesArray.getString(c)) }
                add(
                    GeoShape(
                        codes = codes,
                        name = props.getString("name"),
                        polygons = polygons,
                        state = props.optString("state").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
    }

    /** Reads only the first (outer) ring of a Polygon's coordinate array. */
    private fun outerRing(polygonCoords: JSONArray): LonLatRing {
        val ring = polygonCoords.getJSONArray(0)
        return buildList {
            for (i in 0 until ring.length()) {
                val point = ring.getJSONArray(i)
                add(point.getDouble(0) to point.getDouble(1))
            }
        }
    }
}
