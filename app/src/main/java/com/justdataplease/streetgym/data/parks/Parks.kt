package com.justdataplease.streetgym.data.parks

import java.io.Reader
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * An outdoor workout park from OpenStreetMap (© OpenStreetMap contributors, ODbL): its name and where it is.
 * Bundled; nothing is sent anywhere.
 */
data class Park(val name: String, val lat: Double, val lng: Double)

data class NearbyPark(val park: Park, val kilometres: Double)

object Parks {
    const val ASSET = "spots.tsv"

    /** One park per line: name, latitude and longitude separated by tabs. Other lines (the notice first) are skipped. */
    fun read(reader: Reader): List<Park> = reader.buffered().lineSequence().mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size != 3) return@mapNotNull null
        val lat = parts[1].toDoubleOrNull() ?: return@mapNotNull null
        val lng = parts[2].toDoubleOrNull() ?: return@mapNotNull null
        Park(parts[0], lat, lng).takeIf { it.name.isNotBlank() }
    }.toList()

    /** Great-circle distance in kilometres. */
    fun distance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * 6371.0 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    fun nearest(parks: List<Park>, lat: Double, lng: Double, count: Int = 30): List<NearbyPark> =
        parks.asSequence().map { NearbyPark(it, distance(lat, lng, it.lat, it.lng)) }
            .sortedBy { it.kilometres }.take(count).toList()

    /** Parks whose name holds every word of [query] (the names carry city and country), nearest first when [from] is known. */
    fun search(parks: List<Park>, query: String, from: Pair<Double, Double>? = null, count: Int = 30): List<NearbyPark> {
        val words = query.lowercase(Locale.ROOT).split(' ').filter(String::isNotBlank)
        if (words.isEmpty()) return emptyList()
        val matches = parks.filter { park -> park.name.lowercase(Locale.ROOT).let { name -> words.all(name::contains) } }
        return matches.map { NearbyPark(it, from?.let { (lat, lng) -> distance(lat, lng, it.lat, it.lng) } ?: -1.0) }
            .sortedWith(compareBy<NearbyPark> { if (from == null) 0.0 else it.kilometres }.thenBy { it.park.name })
            .take(count)
    }

    /** The parks as a GeoJSON FeatureCollection for the map; each point's "i" is its index in [parks]. */
    fun geoJson(parks: List<Park>): String = buildString(parks.size * 80) {
        append("""{"type":"FeatureCollection","features":[""")
        parks.forEachIndexed { index, park ->
            if (index > 0) append(',')
            append("""{"type":"Feature","geometry":{"type":"Point","coordinates":[""")
            append(park.lng).append(',').append(park.lat)
            append("""]},"properties":{"i":""").append(index).append("}}")
        }
        append("]}")
    }
}
