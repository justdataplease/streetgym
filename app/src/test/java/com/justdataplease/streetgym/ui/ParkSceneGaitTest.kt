package com.justdataplease.streetgym.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class ParkSceneGaitTest {
    private val gait = Gait(Rig.DEFAULT).apply { plan(-26f, 66f, 0f) }
    private val pose = Pose()
    private val joints = FloatArray(Rig.JOINT_COUNT * 2)

    @Test fun plantedFeetNeitherSlideNorSink() {
        val previous = FloatArray(Rig.JOINT_COUNT * 2)
        var time = 0f
        var first = true
        var largestStep = 0f
        var largestAt = 0f
        while (time <= gait.end + 0.2f) {
            gait.evaluate(time, pose, joints)
            for (foot in intArrayOf(Rig.FOOT_NEAR, Rig.FOOT_FAR, Rig.TOE_NEAR, Rig.TOE_FAR)) {
                assertTrue("joint $foot below the ground at $time", joints[foot * 2 + 1] <= 104.01f)
            }
            if (!first) {
                for (ankle in intArrayOf(Rig.FOOT_NEAR, Rig.FOOT_FAR)) {
                    val grounded = joints[ankle * 2 + 1] >= 103.99f && previous[ankle * 2 + 1] >= 103.99f
                    if (grounded) {
                        assertEquals("ankle $ankle slid at $time", previous[ankle * 2], joints[ankle * 2], 0.02f)
                    }
                }
                for (joint in 0 until 14) {
                    val step = hypot(joints[joint * 2] - previous[joint * 2], joints[joint * 2 + 1] - previous[joint * 2 + 1])
                    if (step > largestStep) {
                        largestStep = step
                        largestAt = time
                    }
                }
            }
            joints.copyInto(previous)
            first = false
            time += 1f / 240f
        }
        assertTrue("joints jumped $largestStep units in one frame at $largestAt", largestStep < 1f)
    }

    @Test fun walkEndsStandingOnTheTarget() {
        gait.evaluate(gait.end, pose, joints)
        assertEquals(66f, joints[Rig.FOOT_NEAR * 2], 0.01f)
        assertEquals(66f, joints[Rig.FOOT_FAR * 2], 0.01f)
        assertEquals(66f, joints[Rig.HIP * 2], 0.01f)
        assertTrue(abs(joints[Rig.HIP * 2 + 1] - 73f) < 0.05f)
    }
}
