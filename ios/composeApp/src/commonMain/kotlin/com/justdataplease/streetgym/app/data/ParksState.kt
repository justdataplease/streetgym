package com.justdataplease.streetgym.app.data

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A position from the phone: latitude, longitude and when it was taken (epoch ms). */
data class Fix(val lat: Double, val lng: Double, val time: Long)

/** Whether Street Gym may read the phone's location: allowed, never asked, or refused. */
enum class LocationPermission { ALLOWED, NOT_ASKED, DENIED }

/**
 * The phone's location for Parks near me, approximate (Android: ACCESS_COARSE_LOCATION and LocationManager). The
 * iPhone's is Core Location (iosMain); the desktop window and tests use [Fixed]. It also opens the phone's maps app at a
 * park, for its directions.
 */
interface PhoneLocation {
    fun permission(): LocationPermission

    /** Asks the athlete once; [result] says whether it was allowed. */
    fun ask(result: (Boolean) -> Unit)

    /** The last position the phone knows, or null. */
    fun last(): Fix?

    /** The phone can find a position now (Android: a location provider is on). */
    fun available(): Boolean

    /** A fresh position, or null when none comes; [result] may be called later. */
    fun current(result: (Fix?) -> Unit)

    /** The phone's maps app at the park [name] at [lat], [lng] (Android: openPark's geo: link). */
    fun openDirections(name: String, lat: Double, lng: Double)

    /**
     * A fixed position, for the desktop window and tests: [last] known since [lastAt] and [fresh] as the new one;
     * [permission] as given, and [ask] answers [allow]. Directions asked for are kept in [opened].
     */
    class Fixed(
        val fix: Fix? = ATHENS,
        private val fresh: Fix? = fix,
        var granted: LocationPermission = LocationPermission.ALLOWED,
        private val allow: Boolean = true,
        private val on: Boolean = true,
    ) : PhoneLocation {
        val opened = mutableListOf<Park>()
        var asked = 0
        var requests = 0
        override fun permission(): LocationPermission = granted
        override fun ask(result: (Boolean) -> Unit) {
            asked++
            granted = if (allow) LocationPermission.ALLOWED else LocationPermission.DENIED
            result(allow)
        }
        override fun last(): Fix? = fix
        override fun available(): Boolean = on
        override fun current(result: (Fix?) -> Unit) {
            requests++
            result(fresh)
        }
        override fun openDirections(name: String, lat: Double, lng: Double) { opened += Park(name, lat, lng) }
    }

    companion object {
        /** Syntagma, in the centre of Athens: the desktop window's position, among the example's parks. */
        val ATHENS = Fix(37.97553, 23.73485, 0L)
    }
}

/**
 * What Parks needs from a map (Android: ui/ParksMap.kt). The iPhone's is MapLibre, written in Swift
 * (iosApp/StreetGym/ParksMapLibre.swift); the desktop draws a plain stand-in ([com.justdataplease.streetgym.app.ui.StandInMaps]).
 */
interface ParksMap {
    /** The parks, and the same as GeoJSON ([Parks.geoJson]), as clustered pins. */
    fun showParks(parks: List<Park>, geoJson: String)

    /** The athlete's approximate position as a blue dot. */
    fun showMe(lat: Double, lng: Double)

    /** The camera on [lat], [lng] at a city's zoom, at once (the map opening on the athlete). */
    fun centre(lat: Double, lng: Double)

    /** The athlete's position with the [nearest] parks in view. */
    fun showNearby(lat: Double, lng: Double, nearest: List<Park>)

    /** The camera fitted to [matches] (a search, the example); one park is shown close. */
    fun focus(matches: List<Park>)

    /**
     * The theme: OpenFreeMap's dark style on a [dark] theme, its light one otherwise; the pins and clusters in [pin] with
     * their counts in [count] (colours as 0xAARRGGBB).
     */
    fun setLook(dark: Boolean, pin: Long, count: Long)
}

/** What the map tells Parks: a park's pin was tapped ([index] in the parks shown), or the map elsewhere. */
interface ParksMapListener {
    fun parkTapped(index: Int)
    fun mapTapped()
}

/** Notes Parks shows (Android: its Toasts). */
enum class ParksNote { LOCATING, NO_LOCATION, DENIED, NO_MATCH }

/** How many of the nearest parks the map frames around the athlete (Android: NEARBY_ON_MAP). */
const val NEARBY_ON_MAP = 5

/** A fix older than this is followed by a fresh one (Android: locateForParks' ten minutes). */
const val FRESH_FIX_MILLIS = 10 * 60_000L

/**
 * Training › Parks while the app runs (android-streetgym, HomeActivity's parks fields, renderParks, searchParks,
 * locateForParks, requestParksNearMe): the parks once read, the athlete's position, the search and its results, the
 * park whose card shows, and the map, kept while another tab shows so that coming back shows it at once. The Map / List
 * choice and whether the map asked for the location are kept on the phone under Android's keys ([AppSettings]).
 */
@Stable
class ParksState(
    private val source: ParksSource,
    val location: PhoneLocation,
    private val settings: AppSettings,
    private val now: () -> Long,
) {
    /** Every park, once read; null before. */
    var all by mutableStateOf<List<Park>?>(null)
        private set

    /** The parks as GeoJSON for the map, made once beside [all]. */
    var geoJson: String? = null
        private set

    /** The athlete's position, once found (Android: parkOrigin). */
    var origin by mutableStateOf<Pair<Double, Double>?>(null)
        private set

    /** The last search's words (Android: parkQuery); blank for the nearest. */
    var query by mutableStateOf("")
        private set

    /** The words in the search field while the app runs. */
    var typed by mutableStateOf("")

    /** The last search's parks (Android: parkResults); null before any. */
    var results by mutableStateOf<List<NearbyPark>?>(null)
        private set

    /** The park whose card shows on the map. */
    var card by mutableStateOf<Park?>(null)

    /** The map, once made (Android: parksMap); [example] is Free's still one. */
    var map: ParksMap? = null
    var example: ParksMap? = null

    /** No map could start here: the list from then on (Android: parksMapFailed). */
    var mapFailed by mutableStateOf(false)

    /** The map asked for the location once while the app runs (Android: parksLocateTried). */
    var locateTried = false

    /** Short notes, shown by the screen. */
    var notes: (ParksNote) -> Unit = {}

    /** The list rather than the map (Android: streetgym_parks show_list). */
    var showList: Boolean
        get() = list
        set(value) {
            list = value
            settings.parksList = value
        }
    private var list by mutableStateOf(settings.parksList)

    /** Reads the parks once (Android: loadParks), and the map's GeoJSON with them, off the screen's thread. */
    suspend fun load() {
        if (all != null) return
        val parks = runCatching { source.load() }.getOrDefault(emptyList())
        geoJson = withContext(Dispatchers.Default) { Parks.geoJson(parks) }
        all = parks
        // The quarterly file, when one is published (at most one small request a week); none on iPhone yet.
        runCatching { source.update(parks.size) }.getOrNull()?.let { fresh ->
            geoJson = withContext(Dispatchers.Default) { Parks.geoJson(fresh) }
            all = fresh
        }
    }

    /**
     * Nearest parks for a blank [query] when the position is known, else the matches (Android: searchParks); with the map
     * on screen it frames them: the athlete with the nearest five, the matches, or "No park matches."
     */
    fun search(query: String, mapShown: Boolean = map != null && !showList) {
        val all = all ?: return
        this.query = query
        val origin = origin
        val found = if (query.isBlank() && origin != null) Parks.nearest(all, origin.first, origin.second)
            else Parks.search(all, query, origin)
        results = found
        val map = map?.takeIf { mapShown } ?: return
        when {
            query.isBlank() -> origin?.let { map.showNearby(it.first, it.second, found.take(NEARBY_ON_MAP).map(NearbyPark::park)) }
            found.isEmpty() -> notes(ParksNote.NO_MATCH)
            else -> {
                // One match opens its card; several are framed without one (Android: ParksMap.focus).
                map.focus(found.map { it.park })
                card = found.singleOrNull()?.park
            }
        }
    }

    /** Parks near me, or the map's location button: asks first if needed (Android: requestParksNearMe). */
    fun requestNearMe() {
        when (location.permission()) {
            LocationPermission.ALLOWED -> locate()
            LocationPermission.NOT_ASKED -> location.ask { allowed -> if (allowed) locate() else notes(ParksNote.DENIED) }
            LocationPermission.DENIED -> notes(ParksNote.DENIED)
        }
    }

    /**
     * The map's first showing (Android: renderParks): on the athlete when known; otherwise the location is asked for
     * once, then only from the location button.
     */
    fun mapOpened() {
        val map = map ?: return
        origin?.let { (lat, lng) -> map.showMe(lat, lng) }
        if (origin != null || locateTried) return
        locateTried = true
        when {
            location.permission() == LocationPermission.ALLOWED -> locate()
            !settings.parksLocationAsked -> {
                settings.parksLocationAsked = true
                requestNearMe()
            }
        }
    }

    /**
     * The last known position shows parks at once; a fresh one follows when it is over ten minutes old (Android:
     * locateForParks).
     */
    fun locate() {
        val last = location.last()
        last?.let(::show)
        if (last != null && now() - last.time < FRESH_FIX_MILLIS) return
        if (!location.available()) {
            if (last == null) notes(ParksNote.NO_LOCATION)
            return
        }
        if (last == null) notes(ParksNote.LOCATING)
        location.current { fix ->
            if (fix != null) show(fix) else if (last == null) notes(ParksNote.NO_LOCATION)
        }
    }

    private fun show(fix: Fix) {
        if (all == null) return
        origin = fix.lat to fix.lng
        // The dot at once; the camera follows with the nearest parks in view.
        map?.showMe(fix.lat, fix.lng)
        search("")
    }

    /** The phone's maps app at [park] (Android: openPark). */
    fun openPark(park: Park) = location.openDirections(park.name, park.lat, park.lng)

    /** How far [park] is from the athlete, or null before the position is known. */
    fun kilometresTo(park: Park): Double? = origin?.let { (lat, lng) -> Parks.distance(lat, lng, park.lat, park.lng) }
}
