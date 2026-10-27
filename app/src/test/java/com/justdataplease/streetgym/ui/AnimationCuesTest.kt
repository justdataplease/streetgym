package com.justdataplease.streetgym.ui

import com.justdataplease.streetgym.R
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

/** The range lines and phase labels a clip sets itself ("marker" and "phases" in the asset JSON). */
class AnimationCuesTest {
    private fun cue(name: String, getter: String) = TestMotionClips.property(name, getter)

    private fun phaseIndex(name: String, timeline: Float): Int {
        val spec = checkNotNull(cue(name, "getPhaseSpec")) { "$name has no phase labels" }
        return spec.javaClass.getDeclaredMethod("indexAt", Float::class.java).apply { isAccessible = true }
            .invoke(spec, timeline) as Int
    }

    private fun phaseLabels(name: String): IntArray {
        val spec = checkNotNull(cue(name, "getPhaseSpec")) { "$name has no phase labels" }
        return spec.javaClass.getDeclaredMethod("getLabels").apply { isAccessible = true }.invoke(spec) as IntArray
    }

    @Test
    fun rowsDrawTheirLineAtTheBarAndLightItWhenTheShouldersArrive() {
        for ((name, bar) in listOf("row" to 66f, "high_row" to 60f, "feet_elevated_row" to 66f, "chest_to_bar" to 12f)) {
            assertEquals(name, Rig.SHOULDER, cue(name, "getMarkerJoint"))
            assertEquals(name, bar, cue(name, "getMarkerY") as Float, 0.01f)
            assertEquals(name, R.string.motion_marker_chest_to_bar, cue(name, "getMarkerLabel"))
        }
        // The line sits at the bar; it lights up when the shoulders reach the end of the row, just below it.
        assertNotEquals(cue("row", "getMarkerY") as Float, cue("row", "getMarkerReachY") as Float, 1f)
    }

    @Test
    fun stretchesHoldWithoutARangeLine() {
        for (name in listOf("hamstring_stretch", "ninety_ninety_stretch", "hip_flexor_stretch")) {
            assertEquals(name, -1, cue(name, "getMarkerJoint"))
            assertEquals(name, R.string.motion_phase_hold_breathe, phaseLabels(name).single())
        }
    }

    @Test
    fun negativesLabelTheSlowLoweringThenTheWayDown() {
        assertEquals(R.string.motion_phase_lower_slow, phaseLabels("eccentric_pullup")[phaseIndex("eccentric_pullup", 0.4f)])
        assertEquals(R.string.motion_phase_step_down, phaseLabels("eccentric_pullup")[phaseIndex("eccentric_pullup", 0.95f)])
        assertEquals(R.string.motion_phase_come_down, phaseLabels("wall_hspu_negative")[phaseIndex("wall_hspu_negative", 0.9f)])
    }

    @Test
    fun everyCueInTheAssetNamesAKnownLabel() {
        val templates = JSONObject(File("src/main/assets/exercise_animations.json").readText()).getJSONObject("templates")
        for (name in templates.keys()) {
            val json = templates.getJSONObject(name)
            val label = json.optJSONObject("marker")?.optString("label").orEmpty()
            if (label.isNotEmpty() && label != "full_range") {
                assertNotEquals("$name: unknown range-line label '$label'", R.string.motion_marker_range, cue(name, "getMarkerLabel"))
            }
            if (json.has("phases")) {
                assertNotNull("$name: unknown phase labels ${json.get("phases")}", cue(name, "getPhaseSpec"))
                assertEquals("$name: every phase label is known", json.optJSONArray("phases")?.length() ?: 1, phaseLabels(name).size)
            }
        }
    }
}
