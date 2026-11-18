package com.justdataplease.streetgym.app.data

import com.justdataplease.streetgym.app.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The parks behind Training › Parks (android-streetgym, data/parks/Parks.kt and ParksUpdater.kt), in plain Kotlin with
 * the same names, numbers and order. ParksTest checks the nearest parks and their distances against Android's code.
 */

/**
 * An outdoor workout park from OpenStreetMap (© OpenStreetMap contributors, ODbL): its name and where it is. Bundled;
 * nothing is sent anywhere.
 */
data class Park(val name: String, val lat: Double, val lng: Double)

data class NearbyPark(val park: Park, val kilometres: Double)

object Parks {
    const val ASSET = "spots.tsv"

    /**
     * Free's example map (the owner, 2026-11-05: "behind have a sample, not the database"): the 12 parks nearest the
     * centre of Athens in the bundled copy, written out here so Free never reads [ASSET].
     */
    val EXAMPLE: List<Park> = listOf(
        Park("Athens - Workout park - Gkazi - Greece", 37.97851, 23.71266),
        Park("Athens - Workout park - Pangrati - Greece", 37.96825, 23.74212),
        Park("Athens - Workout park - Alsos Pagkratiou - Pangrati - Greece", 37.96811, 23.74611),
        Park("Athens - Workout park - Περιβολάκι Εθνικής Αντίστασης - Koukaki - Greece", 37.96186, 23.72234),
        Park("Athens - Workout park - Πλατεία Ολυμπιονίκου Σκαρλάτου - Iλίσια - Greece", 37.97971, 23.75967),
        Park("Athens - Workout park - Λόφος Σικελίας - Greece", 37.95916, 23.71502),
        Park("Athens - Workout park - Zografou - Greece", 37.97942, 23.76242),
        Park("Athens - Workout park - Zografou - Greece", 37.97851, 23.76606),
        Park("Athens - Workout park - Kaisariani - Greece", 37.97082, 23.76419),
        Park("Athens - Υπαίθριο γυμναστήριο - Greece", 37.95962, 23.69867),
        Park("Athens - Workout park - Peristeri - Greece", 38.00962, 23.69825),
        Park("Athens - Workout park - Άλσος Νεας Σμύρνης - Nea Smyrni - Greece", 37.94961, 23.71779),
    )

    /** One park per line: name, latitude and longitude separated by tabs. Other lines (the notice first) are skipped. */
    fun read(text: String): List<Park> = text.lineSequence().mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size != 3) return@mapNotNull null
        val lat = parts[1].toDoubleOrNull() ?: return@mapNotNull null
        val lng = parts[2].toDoubleOrNull() ?: return@mapNotNull null
        Park(parts[0], lat, lng).takeIf { it.name.isNotBlank() }
    }.toList()

    private fun radians(degrees: Double): Double = degrees * DEGREES_TO_RADIANS
    private const val DEGREES_TO_RADIANS = PI / 180.0

    /** Great-circle distance in kilometres. */
    fun distance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = radians(lat2 - lat1)
        val dLng = radians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(radians(lat1)) * cos(radians(lat2)) * sin(dLng / 2).pow(2)
        return 2 * 6371.0 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /**
     * The [count] parks nearest [lat], [lng], nearest first, parks as far as each other in their order in [parks]
     * (Android: a stable sort of every park, then the first [count]; here only the nearest are kept as it goes).
     */
    fun nearest(parks: List<Park>, lat: Double, lng: Double, count: Int = 30): List<NearbyPark> {
        val kept = ArrayList<NearbyPark>(count + 1)
        for (park in parks) {
            val km = distance(lat, lng, park.lat, park.lng)
            if (kept.size == count && km >= kept.last().kilometres) continue
            // After every park as near: the order a stable sort gives.
            var at = kept.size
            while (at > 0 && kept[at - 1].kilometres > km) at--
            kept.add(at, NearbyPark(park, km))
            if (kept.size > count) kept.removeAt(kept.lastIndex)
        }
        return kept
    }

    /** Parks whose name holds every word of [query] (the names carry city and country), nearest first when [from] is known. */
    fun search(parks: List<Park>, query: String, from: Pair<Double, Double>? = null, count: Int = 30): List<NearbyPark> {
        val words = query.lowercase().split(' ').filter(String::isNotBlank)
        if (words.isEmpty()) return emptyList()
        val matches = parks.filter { park -> park.name.lowercase().let { name -> words.all(name::contains) } }
        return matches.map { NearbyPark(it, from?.let { (lat, lng) -> distance(lat, lng, it.lat, it.lng) } ?: -1.0) }
            .sortedWith(compareBy<NearbyPark> { if (from == null) 0.0 else it.kilometres }.thenBy { it.park.name })
            .take(count)
    }

    /**
     * The phone's maps app at a park (Android: openPark's "geo:lat,lng?q=lat,lng(name)"): Apple Maps' link, a pin at
     * [lat], [lng] labelled [name], from which the app gives directions.
     */
    fun mapsLink(name: String, lat: Double, lng: Double): String = "https://maps.apple.com/?ll=$lat,$lng&q=${queryText(name)}"

    /** [text] for a link's query: UTF-8, every byte but letters, digits and - . _ ~ written as %XX. */
    fun queryText(text: String): String = buildString {
        for (byte in text.encodeToByteArray()) {
            val code = byte.toInt() and 0xFF
            val char = code.toChar()
            if (char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in "-._~") append(char)
            else append('%').append(HEX[code shr 4]).append(HEX[code and 0xF])
        }
    }

    private const val HEX = "0123456789ABCDEF"

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

/** When the published parks file is checked again and whether a new one is taken (Android: ParksPolicy). */
object ParksPolicy {
    /** How often Parks asks whether the published file changed (it changes about four times a year). */
    const val CHECK_MILLIS = 7 * 24 * 60 * 60_000L
    /** The largest download accepted, unpacked: the real file is about 5 MB. */
    const val MAX_BYTES = 30 * 1024 * 1024

    fun dueForCheck(checkedAt: Long, now: Long): Boolean = now - checkedAt !in 0 until CHECK_MILLIS

    /** A new file replaces the current parks only if it isn't far smaller (the same 0.8 floor as the job). */
    fun accept(newCount: Int, currentCount: Int): Boolean = newCount > 0 && newCount >= 0.8 * currentCount
}

/**
 * Where the parks come from (Android: ParksUpdater): [load] the copy at hand, then [update] for a newer one, null when
 * nothing changed. Both may read files or the network: call them off the screen's thread.
 */
interface ParksSource {
    suspend fun load(): List<Park>
    suspend fun update(currentCount: Int): List<Park>? = null
}

/**
 * The parks shipped with the app (`spots.tsv`, read in from the Android app at build time). Android also checks a
 * quarterly file once a week, at the `parks_url` of its Firebase setup ([ParksPolicy]); the iPhone's waits for the
 * owner's Firebase iPhone app, so this source never updates.
 */
object BundledParks : ParksSource {
    override suspend fun load(): List<Park> = withContext(Dispatchers.Default) {
        Parks.read(Res.readBytes("files/${Parks.ASSET}").decodeToString())
    }
}
