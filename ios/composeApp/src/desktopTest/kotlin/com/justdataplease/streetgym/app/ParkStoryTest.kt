package com.justdataplease.streetgym.app

import com.justdataplease.streetgym.app.data.Program
import com.justdataplease.streetgym.app.data.loadProgram
import com.justdataplease.streetgym.app.figure.AnimationLibrary
import com.justdataplease.streetgym.app.figure.Gait
import com.justdataplease.streetgym.app.figure.MotionClip
import com.justdataplease.streetgym.app.figure.ParkMove
import com.justdataplease.streetgym.app.figure.ParkRoutine
import com.justdataplease.streetgym.app.figure.ParkStory
import com.justdataplease.streetgym.app.figure.Pose
import com.justdataplease.streetgym.app.figure.Rig
import com.justdataplease.streetgym.json.JSONArray
import com.justdataplease.streetgym.json.JSONObject
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Today's park scene, ported from Android's ParkStory: every routine played against frames Android's own code wrote
 * (resources/park-android.json: ParkStoryTest.dumpFramesForPreviews in android-streetgym, 24 days from 20766, which
 * cover all 21 routines), the routine of a local day in several time zones against java.util.TimeZone, and Android's
 * own rules for the routine order and the walk.
 */
class ParkStoryTest {
    private val library: AnimationLibrary get() = program.motions!!
    private val rig: Rig get() = library.rig

    private fun story(routine: ParkRoutine, leftEdge: Float) =
        ParkStory(rig, routine) { MotionClip.named(library, it) }.apply { plan(leftEdge) }

    private fun JSONArray.floats(): FloatArray = FloatArray(length()) { getDouble(it).toFloat() }

    private fun assertClose(expected: FloatArray, actual: FloatArray, tolerance: Float, what: String) {
        assertEquals(expected.size, actual.size, what)
        for (i in expected.indices) assertEquals(expected[i], actual[i], tolerance, "$what [$i]")
    }

    @Test
    fun everyRoutinePlaysAsAndroidsFrameByFrame() {
        val golden = JSONObject(ParkStoryTest::class.java.getResource("/park-android.json")!!.readText())
        assertEquals(golden.getDouble("headRadius").toFloat(), rig.headRadius)
        val routines = golden.getJSONArray("routines")
        val seen = HashSet<ParkRoutine>()
        val pose = Pose()
        val joints = FloatArray(Rig.JOINT_COUNT * 2)
        val friend = Pose()
        val friendJoints = FloatArray(Rig.JOINT_COUNT * 2)
        var frames = 0
        for (r in 0 until routines.length()) {
            val day = routines.getJSONObject(r)
            val epochDay = day.getLong("day")
            val routine = ParkRoutine.forEpochDay(epochDay)
            val moves = day.getJSONArray("moves").let { array -> List(array.length()) { array.getString(it) } }
            assertEquals(moves, routine.moves.map(ParkMove::name), "day $epochDay")
            seen += routine
            val story = story(routine, day.getDouble("leftEdge").toFloat())
            val what = "day $epochDay ($moves)"
            assertTrue(story.ready, what)
            assertEquals(day.getDouble("width").toFloat(), story.width, 1e-3f, what)
            assertEquals(day.getDouble("end").toFloat(), story.end, 1e-3f, what)
            assertClose(day.getJSONArray("friendX").floats(), story.friendX, 1e-3f, "$what friends")
            assertClose(day.getJSONArray("friendFacing").floats(), story.friendFacing, 0f, "$what facing")
            val props = day.getJSONArray("props")
            assertEquals(props.length(), story.props.size, what)
            story.props.forEachIndexed { i, prop ->
                val expected = props.getJSONArray(i).floats()
                assertClose(expected, floatArrayOf(prop.kind.toFloat(), prop.y, prop.left, prop.right), 1e-3f, "$what prop $i")
            }
            val shots = day.getJSONArray("frames")
            for (f in 0 until shots.length()) {
                val shot = shots.getJSONObject(f)
                val t = shot.getDouble("t").toFloat()
                story.poseHero(t, pose, joints)
                assertClose(shot.getJSONArray("hero").floats(), joints, 2e-3f, "$what hero at $t")
                assertEquals(shot.getDouble("spine").toFloat(), pose.spine, 1e-3f, "$what spine at $t")
                assertEquals(shot.getDouble("facing").toFloat(), pose.facing, "$what facing at $t")
                val friends = shot.getJSONArray("friends")
                for (index in 0 until ParkStory.FRIENDS) {
                    val expected = friends.getJSONObject(index)
                    val flash = story.poseFriend(index, t, friend, friendJoints)
                    assertClose(expected.getJSONArray("joints").floats(), friendJoints, 2e-3f, "$what friend $index at $t")
                    assertEquals(expected.getDouble("flash").toFloat(), flash, 1e-3f, "$what clap $index at $t")
                }
                frames++
            }
        }
        assertEquals(ParkRoutine.all.toSet(), seen)
        assertTrue(frames > 200, "only $frames frames compared")
    }

    @Test
    fun aLocalDayHasItsRoutineInEveryZoneAsOnAndroid() {
        // Android: (millis + zone.getOffset(millis)).floorDiv(day), with java.util.TimeZone.
        fun android(millis: Long, zone: String): Long {
            val tz = java.util.TimeZone.getTimeZone(zone)
            return (millis + tz.getOffset(millis)).floorDiv(86_400_000L)
        }
        val zones = listOf("UTC", "Europe/Athens", "America/New_York", "America/Los_Angeles", "Asia/Tokyo", "Australia/Adelaide",
            "Pacific/Kiritimati", "Pacific/Pago_Pago", "Asia/Kathmandu")
        val kotlinZones = zones.map(TimeZone::of)
        // 18 Nov 2026, 10:00 UTC: still Tuesday in Pago Pago, already Thursday on Kiritimati, Wednesday elsewhere.
        val now = 20_775 * 86_400_000L + 10 * 3_600_000L
        val days = kotlinZones.map { ParkRoutine.localEpochDay(now, it) }
        assertEquals(listOf(20_775L, 20_775L, 20_775L, 20_775L, 20_775L, 20_775L, 20_776L, 20_774L, 20_775L), days)
        // Every hour of three years, in every zone, the day (and so its routine) is Android's.
        var millis = 20_454L * 86_400_000L
        while (millis < 21_550L * 86_400_000L) {
            zones.forEachIndexed { index, zone ->
                assertEquals(android(millis, zone), ParkRoutine.localEpochDay(millis, kotlinZones[index]), "$zone at $millis")
            }
            millis += 3_600_000L
        }
        // The routines of those days, as Android named them for 17, 18 and 19 November 2026.
        assertEquals(listOf(ParkMove.KNEE_RAISES, ParkMove.PULL_UPS, ParkMove.HUMAN_FLAG), ParkRoutine.forEpochDay(20_774).moves)
        assertEquals(listOf(ParkMove.SQUATS, ParkMove.MUSCLE_UP, ParkMove.FRONT_LEVER), ParkRoutine.forEpochDay(20_775).moves)
        assertEquals(listOf(ParkMove.KNEE_RAISES, ParkMove.PULL_UPS, ParkMove.TUCK_LEVER), ParkRoutine.forEpochDay(20_776).moves)
    }

    @Test
    fun aLocalDayKeepsItsRoutineUntilMidnight() {
        val zone = java.util.TimeZone.getTimeZone("Europe/Athens")
        // 2026-03-29 is 23 hours long in Athens (summer time starts), 2026-10-25 is 25 (Android's ParkRoutineTest).
        for ((year, month, date) in listOf(Triple(2026, Calendar.MARCH, 29), Triple(2026, Calendar.OCTOBER, 25), Triple(2026, Calendar.JUNE, 1))) {
            val calendar = Calendar.getInstance(zone).apply {
                clear()
                set(year, month, date, 0, 0, 0)
            }
            val midnight = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            val nextMidnight = calendar.timeInMillis
            val athens = TimeZone.of("Europe/Athens")
            val day = ParkRoutine.localEpochDay(midnight, athens)
            assertEquals(day, ParkRoutine.localEpochDay(nextMidnight - 1, athens))
            assertEquals(day + 1, ParkRoutine.localEpochDay(nextMidnight, athens))
            assertEquals(day - 1, ParkRoutine.localEpochDay(midnight - 1, athens))
        }
        assertEquals(0L, ParkRoutine.localEpochDay(-7_200_000L, TimeZone.of("Europe/Athens")))
    }

    @Test
    fun theRoutineOrderKeepsAndroidsRules() {
        val size = ParkRoutine.all.size.toLong()
        assertEquals(21, ParkRoutine.all.size)
        val days = 18_000L..24_000L
        for (day in days) {
            val today = ParkRoutine.forEpochDay(day)
            val tomorrow = ParkRoutine.forEpochDay(day + 1)
            for (i in 0..2) assertNotEquals(today.moves[i], tomorrow.moves[i], "day $day repeats")
        }
        for (block in days.first / size..days.last / size) {
            assertEquals(ParkRoutine.all.toSet(), (0L until size).map { ParkRoutine.forEpochDay(block * size + it) }.toSet(), "block $block")
        }
    }

    @Test
    fun theWalkPlantsItsFeetAndEndsStanding() {
        // Android's ParkSceneGaitTest on the port.
        val gait = Gait(Rig.DEFAULT).apply { plan(-26f, 66f, 0f) }
        val pose = Pose()
        val joints = FloatArray(Rig.JOINT_COUNT * 2)
        val previous = FloatArray(Rig.JOINT_COUNT * 2)
        var time = 0f
        var first = true
        while (time <= gait.end + 0.2f) {
            gait.evaluate(time, pose, joints)
            for (foot in intArrayOf(Rig.FOOT_NEAR, Rig.FOOT_FAR, Rig.TOE_NEAR, Rig.TOE_FAR)) assertTrue(joints[foot * 2 + 1] <= 104.01f)
            if (!first) {
                for (ankle in intArrayOf(Rig.FOOT_NEAR, Rig.FOOT_FAR)) {
                    if (joints[ankle * 2 + 1] >= 103.99f && previous[ankle * 2 + 1] >= 103.99f) {
                        assertEquals(previous[ankle * 2], joints[ankle * 2], 0.02f, "ankle $ankle slid at $time")
                    }
                }
                for (joint in 0 until 14) {
                    assertTrue(hypot(joints[joint * 2] - previous[joint * 2], joints[joint * 2 + 1] - previous[joint * 2 + 1]) < 1f)
                }
            }
            joints.copyInto(previous)
            first = false
            time += 1f / 240f
        }
        gait.evaluate(gait.end, pose, joints)
        assertEquals(66f, joints[Rig.FOOT_NEAR * 2], 0.01f)
        assertEquals(66f, joints[Rig.HIP * 2], 0.01f)
        assertTrue(abs(joints[Rig.HIP * 2 + 1] - 73f) < 0.05f)
    }

    private companion object {
        val program: Program by lazy { runBlocking { loadProgram() } }
    }
}
