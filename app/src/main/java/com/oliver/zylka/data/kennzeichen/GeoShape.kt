package com.oliver.zylka.data.kennzeichen

/** One ring (closed polygon outline) of longitude/latitude points. */
typealias LonLatRing = List<Pair<Double, Double>>

/**
 * A region's outline on the map, matched to a [code] from the corresponding
 * [PlateRegion]. A region can be made of several polygons (e.g. islands); for map
 * shading purposes we only need the outer ring of each.
 */
data class GeoShape(
    val code: String,
    val name: String,
    val polygons: List<LonLatRing>,
)

data class GeoBounds(
    val minLon: Double,
    val maxLon: Double,
    val minLat: Double,
    val maxLat: Double,
) {
    val centerLat: Double get() = (minLat + maxLat) / 2.0

    companion object {
        fun of(shapes: List<GeoShape>): GeoBounds {
            var minLon = Double.MAX_VALUE
            var maxLon = -Double.MAX_VALUE
            var minLat = Double.MAX_VALUE
            var maxLat = -Double.MAX_VALUE
            for (shape in shapes) {
                for (ring in shape.polygons) {
                    for ((lon, lat) in ring) {
                        if (lon < minLon) minLon = lon
                        if (lon > maxLon) maxLon = lon
                        if (lat < minLat) minLat = lat
                        if (lat > maxLat) maxLat = lat
                    }
                }
            }
            return GeoBounds(minLon, maxLon, minLat, maxLat)
        }
    }
}
