package com.justdataplease.streetgym.data

import com.justdataplease.streetgym.data.points.Points
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import com.justdataplease.streetgym.json.JSONArray as SharedArray
import com.justdataplease.streetgym.json.JSONObject as SharedObject

/**
 * The engine moved to the shared Kotlin Multiplatform module in 2.11.5 and stopped using Android's org.json and
 * java.util's calendar. These pin the replacements to what they replaced, so stored data and leaderboard months stay.
 */
class SharedEngineTest {
    @Test
    fun utcMonthsMatchJavaCalendar() {
        val utc = TimeZone.getTimeZone("UTC")
        var time = -2_208_988_800_000L // 1900-01-01
        val end = 4_102_444_800_000L // 2100-01-01
        while (time < end) {
            val calendar = Calendar.getInstance(utc, Locale.ROOT).apply { timeInMillis = time }
            val expected = String.format(Locale.ROOT, "%04d-%02d", calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1)
            assertEquals("month of $time", expected, Points.month(time))
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
            assertEquals("month start of $time", calendar.timeInMillis, Points.monthStart(time))
            time += 47_123_456L // about 13 hours, so every hour of the day and both month edges come round
        }
        // The last millisecond of a month and the first of the next.
        assertEquals("2026-10", Points.month(1_793_491_199_999L))
        assertEquals("2026-11", Points.month(1_793_491_200_000L))
    }

    @Test
    fun sharedJsonReadsWhatOrgJsonWrote() {
        val written = org.json.JSONObject()
            .put("text", "quote \" slash / </tag> ünï \n end")
            .put("int", 42).put("long", 9_000_000_000L).put("double", 1.5).put("whole", 2.0)
            .put("yes", true).put("nothing", org.json.JSONObject.NULL).put("numberText", "17")
            .put("list", org.json.JSONArray().put(1).put("two").put(org.json.JSONObject().put("three", 3)))
            .toString()
        val read = SharedObject(written)
        assertEquals("quote \" slash / </tag> ünï \n end", read.getString("text"))
        assertEquals(42, read.optInt("int"))
        assertEquals(9_000_000_000L, read.getLong("long"))
        assertEquals(1.5, read.optDouble("double"), 0.0)
        assertEquals(2, read.optInt("whole"))
        assertTrue(read.optBoolean("yes"))
        assertTrue(read.has("nothing") && read.isNull("nothing"))
        assertEquals("", read.optString("nothing"))
        assertEquals("fallback", read.optString("missing", "fallback"))
        assertEquals(17, read.optInt("numberText"))
        assertEquals(0, read.optInt("text"))
        val list = read.getJSONArray("list")
        assertEquals(3, list.length())
        assertEquals("1", list.optString(0))
        assertEquals("two", list.optString(1))
        assertEquals(3, list.getJSONObject(2).optInt("three"))
        assertEquals(null, read.optJSONArray("text"))
    }

    @Test
    fun orgJsonReadsWhatSharedJsonWrote() {
        val written = SharedObject()
            .put("text", "a \"b\" / c")
            .put("int", 7).put("long", 9_000_000_000L).put("double", 0.25).put("whole", 3.0)
            .put("no", false).put("gone", null)
            .put("list", SharedArray(listOf("x", 2, null)))
            .put("nested", mapOf("k" to listOf(1, 2)))
            .toString()
        val read = org.json.JSONObject(written)
        assertEquals("a \"b\" / c", read.getString("text"))
        assertEquals(7, read.getInt("int"))
        assertEquals(9_000_000_000L, read.getLong("long"))
        assertEquals(0.25, read.getDouble("double"), 0.0)
        assertEquals(3, read.getInt("whole"))
        assertFalse(read.getBoolean("no"))
        assertFalse(read.has("gone"))
        val list = read.getJSONArray("list")
        assertEquals(listOf("x", 2), listOf(list.get(0), list.get(1)))
        assertTrue(list.isNull(2))
        assertEquals(2, read.getJSONObject("nested").getJSONArray("k").getInt(1))
    }

    @Test
    fun theCurriculumReadsTheSameEitherWay() {
        val text = java.io.File("src/main/assets/curriculum.json").readText()
        val catalog = CurriculumJsonParser.parse(text)
        val levels = org.json.JSONObject(text).getJSONArray("levels")
        assertEquals(levels.length(), catalog.levels.size)
        for (index in 0 until levels.length()) {
            val programs = levels.getJSONObject(index).getJSONArray("programs")
            assertEquals(programs.length(), catalog.levels[index].programs.size)
            for (day in 0 until programs.length()) {
                assertEquals(programs.getJSONObject(day).getString("id"), catalog.levels[index].programs[day].id)
            }
        }
    }
}
