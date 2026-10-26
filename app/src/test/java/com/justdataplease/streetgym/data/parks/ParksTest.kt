package com.justdataplease.streetgym.data.parks

import org.junit.Assert.*
import org.junit.Test

class ParksTest {
    private val parks = Parks.read("""
        Berlin - Outdoor pull up bars - Moabit - Germany	52.52328	13.31811
        Berlin - Calisthenics Park - Poelzigpark - Germany	52.55938	13.21844
        Athens - Calisthenics Park - Pedion tou Areos - Greece	37.99540	23.73600
        broken line
        Nowhere	not-a-number	1.0
        Paris - Street Workout - Parc de Belleville - France	48.87140	2.38450
    """.trimIndent().reader())

    @Test fun readingSkipsMalformedLines() {
        assertEquals(4, parks.size)
        assertEquals(Park("Athens - Calisthenics Park - Pedion tou Areos - Greece", 37.9954, 23.736), parks[2])
    }

    @Test fun distanceIsGreatCircleKilometres() {
        // Berlin to Paris is about 878 km
        assertEquals(878.0, Parks.distance(52.52, 13.405, 48.8566, 2.3522), 5.0)
        assertEquals(0.0, Parks.distance(10.0, 10.0, 10.0, 10.0), 1e-9)
    }

    @Test fun nearestComeFirst() {
        val near = Parks.nearest(parks, 52.52, 13.40, count = 3)
        assertEquals(listOf("Moabit", "Poelzigpark", "Belleville"), near.map { it.park.name.split(" - ")[2].removePrefix("Parc de ") })
        assertTrue(near[0].kilometres < 10)
    }

    @Test fun searchMatchesEveryWordOfTheName() {
        assertEquals(2, Parks.search(parks, "berlin").size)
        assertEquals(listOf("Athens - Calisthenics Park - Pedion tou Areos - Greece"), Parks.search(parks, "calisthenics GREECE").map { it.park.name })
        assertTrue(Parks.search(parks, "  ").isEmpty())
        assertTrue(Parks.search(parks, "tokyo").isEmpty())
    }

    @Test fun theQuarterlyFileIsCheckedAtMostWeeklyAndOnlyReplacesWhenNotFarSmaller() {
        assertTrue(ParksPolicy.dueForCheck(0, ParksPolicy.CHECK_MILLIS))
        assertFalse(ParksPolicy.dueForCheck(1_000, 1_000 + ParksPolicy.CHECK_MILLIS - 1))
        assertTrue(ParksPolicy.dueForCheck(10_000, 5_000))   // a clock moved back checks again
        assertTrue(ParksPolicy.accept(23_000, 23_512))
        assertTrue(ParksPolicy.accept(25_000, 23_512))
        assertFalse(ParksPolicy.accept(18_000, 23_512))
        assertFalse(ParksPolicy.accept(0, 0))
    }

    @Test fun theMapGetsEveryParkAsLngLatWithItsIndex() {
        val json = org.json.JSONObject(Parks.geoJson(parks))
        val features = json.getJSONArray("features")
        assertEquals("FeatureCollection", json.getString("type"))
        assertEquals(parks.size, features.length())
        val third = features.getJSONObject(2)
        assertEquals(2, third.getJSONObject("properties").getInt("i"))
        val coordinates = third.getJSONObject("geometry").getJSONArray("coordinates")
        assertEquals(23.736, coordinates.getDouble(0), 1e-9)
        assertEquals(37.9954, coordinates.getDouble(1), 1e-9)
        assertEquals(0, org.json.JSONObject(Parks.geoJson(emptyList())).getJSONArray("features").length())
    }

    @Test fun searchFromALocationSortsByDistance() {
        val fromSpandau = Parks.search(parks, "berlin", from = 52.536 to 13.20)
        assertTrue(fromSpandau[0].park.name.contains("Poelzigpark"))
        assertTrue(fromSpandau[0].kilometres < fromSpandau[1].kilometres)
    }
}
