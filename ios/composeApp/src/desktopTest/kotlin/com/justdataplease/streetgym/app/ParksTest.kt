package com.justdataplease.streetgym.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.AppSettings
import com.justdataplease.streetgym.app.data.Fix
import com.justdataplease.streetgym.app.data.LocationPermission
import com.justdataplease.streetgym.app.data.MemoryStore
import com.justdataplease.streetgym.app.data.NearbyPark
import com.justdataplease.streetgym.app.data.Park
import com.justdataplease.streetgym.app.data.Parks
import com.justdataplease.streetgym.app.data.ParksNote
import com.justdataplease.streetgym.app.data.ParksPolicy
import com.justdataplease.streetgym.app.data.PhoneLocation
import com.justdataplease.streetgym.app.data.Program
import com.justdataplease.streetgym.app.data.StoredProgress
import com.justdataplease.streetgym.app.data.loadProgram
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.leaderboard_you_top
import com.justdataplease.streetgym.app.ui.DotsMap
import com.justdataplease.streetgym.app.ui.PARK_ZOOM
import com.justdataplease.streetgym.app.ui.Screen
import com.justdataplease.streetgym.app.ui.TRAINING_PARKS
import com.justdataplease.streetgym.app.ui.distanceText
import com.justdataplease.streetgym.app.ui.oneDecimal
import com.justdataplease.streetgym.billing.Tier
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.getString
import java.io.Reader
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Training › Parks (Android: data/parks/Parks.kt, renderParks, renderParksExample, searchParks, locateForParks): the
 * bundled parks read as Android reads them, the nearest parks and their distances for fixed positions and the search
 * against Android's own code, the distance's words, the maps link, Free's example under its layer, and Plus's list and
 * map by taps with a fixed position and the stand-in map.
 */
@OptIn(ExperimentalTestApi::class)
class ParksTest {
    private val now = 20_775 * 86_400_000L + 12 * 3_600_000L

    private fun model(
        tier: Tier = Tier.PLUS,
        store: MemoryStore = MemoryStore(),
        location: PhoneLocation = PhoneLocation.Fixed(PhoneLocation.ATHENS.copy(time = now)),
        list: Boolean? = null,
    ): AppModel {
        val settings = AppSettings(store)
        settings.placementDone = true
        list?.let { settings.parksList = it }
        return AppModel(program, StoredProgress(store), tier = tier, now = { now }, zone = TimeZone.UTC, settings = settings, location = location)
    }

    @Test
    fun theBundledParksReadAsAndroidReadsThem() {
        assertEquals(androidParks.size, parks.size)
        assertEquals(73_303, parks.size)
        assertEquals(androidParks.map { Triple(it.name, it.lat, it.lng) }, parks.map { Triple(it.name, it.lat, it.lng) })
        // The notice line is skipped; the example's parks are among them.
        assertTrue(Parks.EXAMPLE.all { park -> parks.any { it.name == park.name && it.lat == park.lat && it.lng == park.lng } })
    }

    @Test
    fun theNearestParksAndTheirDistancesMatchAndroidsCode() {
        val places = listOf(37.97553 to 23.73485, 40.7128 to -74.006, -33.8688 to 151.2093, 35.6762 to 139.6503, 0.0 to -30.0,
            64.1466 to -21.9426, -17.7134 to 179.9, 51.5072 to -0.1276, 89.0 to 0.0)
        for ((lat, lng) in places) {
            val android = AndroidParks.nearest(androidParks, lat, lng)
            val ours = Parks.nearest(parks, lat, lng)
            assertEquals(android.map { it.first.name }, ours.map { it.park.name }, "at $lat, $lng")
            android.zip(ours).forEach { (a, b) -> assertEquals(a.second, b.kilometres, 1e-9 * maxOf(1.0, a.second), "at $lat, $lng") }
        }
        // Syntagma's nearest: Pangrati, a kilometre away.
        val athens = Parks.nearest(parks, 37.97553, 23.73485)
        assertEquals("Athens - Workout park - Pangrati - Greece", athens.first().park.name)
        assertEquals("1.0", oneDecimal(athens.first().kilometres))
        assertEquals(30, athens.size)
    }

    @Test
    fun theSearchMatchesAndroidsCode() {
        val from = 52.52 to 13.405
        for (query in listOf("berlin", "Workout park Berlin", "  ATHENS  greece ", "Αθήνα", "zzzz", "", "park")) {
            for (origin in listOf(null, from)) {
                val android = AndroidParks.search(androidParks, query, origin)
                val ours = Parks.search(parks, query, origin)
                assertEquals(android.map { it.first.name to it.first.lat }, ours.map { it.park.name to it.park.lat }, "'$query' from $origin")
                assertEquals(android.map { it.second }, ours.map { it.kilometres })
            }
        }
        assertTrue(Parks.search(parks, "berlin").isNotEmpty())
        assertTrue(Parks.search(parks, "berlin").all { it.kilometres == -1.0 })
    }

    @Test
    fun theDistancesWordsAndAndroidsFormats() = runComposeUiTest {
        // Android's "%.1f" in English, the shortest decimal rounded half up.
        val random = Random(7)
        val values = listOf(0.0, 0.05, 0.15, 0.25, 0.95, 1.0, 1.05, 2.45, 2.449, 9.95, 99.95, 999.96, 12_345.678, 0.04, 1.0E-5,
            1.2345E7, 20_015.086796) + List(2_000) { random.nextDouble() * 20_000 } + List(500) { random.nextInt(0, 2_000) / 100.0 }
        for (value in values) assertEquals(String.format(Locale.US, "%.1f", value), oneDecimal(value), "$value")
        assertEquals("-0.0", oneDecimal(-0.04))
        assertEquals(String.format(Locale.US, "%.1f", -2.45), oneDecimal(-2.45))

        setContent {
            BasicText(listOf(distanceText(0.35), distanceText(0.9999), distanceText(1.0), distanceText(2.449), distanceText(12.25)).joinToString("|"))
        }
        onNodeWithText("350 m away|999 m away|1.0 km away|2.4 km away|12.3 km away").assertExists()
        // Android's "%%" is a percent sign once its numbers are in.
        assertEquals("You’re in the top 5% · 1,200 points", runBlocking { getString(Res.string.leaderboard_you_top, 5, "1,200") })
    }

    @Test
    fun theMapsLinkAndTheRefreshRules() {
        // Google Maps at the park, as Android's geo: link opens it on most phones, and Android's own fallback link
        // (HomeActivity.openPark) word for word; Apple Maps is never opened.
        assertEquals("https://www.google.com/maps/search/?api=1&query=37.97851,23.71266", Parks.mapsLink(37.97851, 23.71266))
        assertEquals("https://www.google.com/maps/search/?api=1&query=-33.8688,151.2093", Parks.mapsLink(-33.8688, 151.2093))
        // Android's ParksPolicy, kept for the weekly refresh that waits for the owner's Firebase setup.
        assertTrue(ParksPolicy.dueForCheck(0L, ParksPolicy.CHECK_MILLIS))
        assertFalse(ParksPolicy.dueForCheck(1_000L, ParksPolicy.CHECK_MILLIS))
        assertTrue(ParksPolicy.dueForCheck(5_000L, 1_000L))
        assertTrue(ParksPolicy.accept(80, 100))
        assertFalse(ParksPolicy.accept(79, 100))
        assertFalse(ParksPolicy.accept(0, 0))
    }

    @Test
    fun freeSeesTheExampleUnderALayerThatOpensTheOffer() = runComposeUiTest {
        val model = model(Tier.FREE)
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Training, trainingTab = TRAINING_PARKS) }
        onNodeWithText("More than 70,000 outdoor workout parks worldwide, nearest first, with directions.").assertExists()
        onNodeWithText("EXAMPLE: PARKS IN ATHENS").assertExists()
        val example = assertIs<DotsMap>(model.parks.example)
        assertFalse(example.interactive)
        assertEquals(Parks.EXAMPLE, example.parks)
        assertIs<DotsMap.Camera.Fit>(example.camera)
        onNodeWithContentDescription("Unlock Plus").performClick()
        onNodeWithText("Parks near you are part of Plus", ignoreCase = true).assertExists()
        onNodeWithText("CANCEL").performClick()
        // Nothing of the real parks is read on Free.
        assertNull(model.parks.all)
    }

    @Test
    fun whereNoMapCanStartFreeSeesFiveOfTheParks() = runComposeUiTest {
        val model = model(Tier.FREE)
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Training, trainingTab = TRAINING_PARKS, maps = null) }
        // The cards sit under the layer, hidden from VoiceOver as Android hides them (parks-free-cards-*.png shows them).
        assertNull(model.parks.example)
        onNodeWithText("EXAMPLE: PARKS IN ATHENS").assertExists()
        onNodeWithContentDescription("Unlock Plus").performClick()
        onNodeWithText("Parks near you are part of Plus", ignoreCase = true).assertExists()
    }

    @Test
    fun plusFindsTheNearestParksInTheListAndOpensDirections() = runComposeUiTest {
        val location = PhoneLocation.Fixed(PhoneLocation.ATHENS.copy(time = now))
        val store = MemoryStore()
        val model = model(store = store, location = location, list = true)
        runBlocking { model.parks.load() }
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Training, trainingTab = TRAINING_PARKS) }
        onNodeWithText("73,303 workout parks worldwide. Find the nearest, or search by city or park.").assertExists()
        onNodeWithText("PARKS NEAR ME").performClick()
        onNodeWithText("NEAREST TO YOU").assertExists()
        onNodeWithText("Tap a park for directions in your maps app.").assertExists()
        // A fresh position: no new request.
        assertEquals(0, location.requests)
        val nearest = model.parks.results!!
        assertEquals(Parks.nearest(model.parks.all!!, PhoneLocation.ATHENS.lat, PhoneLocation.ATHENS.lng), nearest)
        onAllNodesWithText("1.0 km away").onFirst().assertExists()
        onAllNodesWithText(nearest.first().park.name.uppercase()).onFirst().performClick()
        assertEquals(listOf(nearest.first().park), location.opened)

        // A search with no match.
        onNodeWithContentDescription("City or park name").performTextInput("zzqq")
        onNodeWithText("SEARCH").performClick()
        onNodeWithText("MATCHES FOR “ZZQQ”").assertExists()
        onNodeWithText("No park matches.").assertExists()

        // The choice is kept under Android's key; the map comes back with Map.
        assertEquals("true", store.get("streetgym_parks.show_list"))
        onNodeWithText("Map").performClick()
        assertEquals("false", store.get("streetgym_parks.show_list"))
        onNodeWithText("© OpenStreetMap contributors. Your location isn’t sent.").assertExists()
    }

    @Test
    fun theMapAsksForTheLocationOnceThenOnlyFromItsButton() = runComposeUiTest {
        val location = PhoneLocation.Fixed(PhoneLocation.ATHENS.copy(time = now), granted = LocationPermission.NOT_ASKED)
        val store = MemoryStore()
        val model = model(store = store, location = location)
        runBlocking { model.parks.load() }
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Training, trainingTab = TRAINING_PARKS) }
        waitForIdle()
        assertEquals(1, location.asked)
        assertEquals("true", store.get("streetgym_parks.location_asked"))
        // Located: the blue dot, and the five nearest in view with it.
        val map = assertIs<DotsMap>(model.parks.map)
        assertEquals(PhoneLocation.ATHENS.lat to PhoneLocation.ATHENS.lng, map.me)
        assertEquals(6, assertIs<DotsMap.Camera.Fit>(map.camera).points.size)
        assertEquals(model.parks.all, map.parks)

        // A pin's card: its name, distance and Directions.
        val park = model.parks.results!![2].park
        map.tapPark(model.parks.all!!.indexOf(park))
        waitForIdle()
        onNodeWithText(oneDecimal(model.parks.kilometresTo(park)!!) + " km away").assertExists()
        onNodeWithText("DIRECTIONS").performClick()
        assertEquals(listOf(park), location.opened)

        // A search with one match frames it close and opens its card; none says so.
        val unique = model.parks.all!!.first { Parks.search(model.parks.all!!, it.name).size == 1 }
        onNodeWithContentDescription("City or park name").performTextInput(unique.name)
        onNodeWithText("SEARCH").performClick()
        assertEquals(unique, model.parks.card)
        assertEquals(DotsMap.Camera.At(unique.lat, unique.lng, PARK_ZOOM), map.camera)

        // Another tab and back: the same map, no second question; a restart of the app doesn't ask either.
        onNodeWithContentDescription("Today").performClick()
        onNodeWithContentDescription("Training").performClick()
        waitForIdle()
        assertTrue(model.parks.map === map)
        assertEquals(1, location.asked)
        val again = model(store = store, location = PhoneLocation.Fixed(null, granted = LocationPermission.NOT_ASKED))
        runBlocking { again.parks.load() }
        assertEquals(true, AppSettings(store).parksLocationAsked)
    }

    @Test
    fun theLocationsRulesAndNotes() {
        val notes = mutableListOf<ParksNote>()
        fun state(location: PhoneLocation) = model(location = location).parks.also {
            runBlocking { it.load() }
            it.notes = { note -> notes += note }
        }
        // An old fix shows at once and a fresh one follows.
        val old = PhoneLocation.Fixed(Fix(37.0, 23.0, now - 11 * 60_000L), fresh = Fix(38.0, 23.7, now))
        val parks = state(old)
        parks.locate()
        assertEquals(1, old.requests)
        assertEquals(38.0 to 23.7, parks.origin)
        assertTrue(notes.isEmpty())
        // None known: "Finding your location…", then the fix; none comes: "Couldn't get your location…".
        state(PhoneLocation.Fixed(null, fresh = Fix(38.0, 23.7, now))).locate()
        assertEquals(listOf(ParksNote.LOCATING), notes)
        notes.clear()
        state(PhoneLocation.Fixed(null, fresh = null)).locate()
        assertEquals(listOf(ParksNote.LOCATING, ParksNote.NO_LOCATION), notes)
        notes.clear()
        state(PhoneLocation.Fixed(null, on = false)).locate()
        assertEquals(listOf(ParksNote.NO_LOCATION), notes)
        notes.clear()
        // Refused: "Location is off for Street Gym…", now and from the button later.
        val refused = PhoneLocation.Fixed(null, granted = LocationPermission.NOT_ASKED, allow = false)
        val asked = state(refused)
        asked.requestNearMe()
        asked.requestNearMe()
        assertEquals(1, refused.asked)
        assertEquals(listOf(ParksNote.DENIED, ParksNote.DENIED), notes)
    }

    @Test
    fun aSearchOnTheMapFramesTheMatchesOrSaysThereAreNone() {
        val notes = mutableListOf<ParksNote>()
        val model = model()
        val parks = model.parks
        runBlocking { parks.load() }
        val map = com.justdataplease.streetgym.app.ui.StandInMaps.create(true, object : com.justdataplease.streetgym.app.data.ParksMapListener {
            override fun parkTapped(index: Int) = Unit
            override fun mapTapped() = Unit
        }) as DotsMap
        parks.map = map
        parks.notes = { notes += it }
        parks.search("berlin")
        assertIs<DotsMap.Camera.Fit>(map.camera)
        assertNull(parks.card)
        parks.search("zzqq")
        assertEquals(listOf(ParksNote.NO_MATCH), notes)
        assertEquals(emptyList<NearbyPark>(), parks.results)
        // In the list, the results show there instead.
        parks.showList = true
        parks.search("berlin")
        assertEquals(listOf(ParksNote.NO_MATCH), notes)
    }

    /** Android's data/parks/Parks.kt, word for word (with java.util.Locale and Math.toRadians), to check the port against. */
    private object AndroidParks {
        fun read(reader: Reader): List<Park> = reader.buffered().lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size != 3) return@mapNotNull null
            val lat = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            val lng = parts[2].toDoubleOrNull() ?: return@mapNotNull null
            Park(parts[0], lat, lng).takeIf { it.name.isNotBlank() }
        }.toList()

        fun distance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLng = Math.toRadians(lng2 - lng1)
            val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
            return 2 * 6371.0 * asin(sqrt(a.coerceIn(0.0, 1.0)))
        }

        fun nearest(parks: List<Park>, lat: Double, lng: Double, count: Int = 30): List<Pair<Park, Double>> =
            parks.asSequence().map { it to distance(lat, lng, it.lat, it.lng) }.sortedBy { it.second }.take(count).toList()

        fun search(parks: List<Park>, query: String, from: Pair<Double, Double>? = null, count: Int = 30): List<Pair<Park, Double>> {
            val words = query.lowercase(Locale.ROOT).split(' ').filter(String::isNotBlank)
            if (words.isEmpty()) return emptyList()
            val matches = parks.filter { park -> park.name.lowercase(Locale.ROOT).let { name -> words.all(name::contains) } }
            return matches.map { it to (from?.let { (lat, lng) -> distance(lat, lng, it.lat, it.lng) } ?: -1.0) }
                .sortedWith(compareBy<Pair<Park, Double>> { if (from == null) 0.0 else it.second }.thenBy { it.first.name })
                .take(count)
        }
    }

    private companion object {
        val program: Program by lazy { runBlocking { loadProgram() } }
        val bytes: ByteArray by lazy { runBlocking { Res.readBytes("files/spots.tsv") } }
        val parks: List<Park> by lazy { Parks.read(bytes.decodeToString()) }
        val androidParks: List<Park> by lazy { AndroidParks.read(bytes.inputStream().reader(Charsets.UTF_8)) }
    }
}
