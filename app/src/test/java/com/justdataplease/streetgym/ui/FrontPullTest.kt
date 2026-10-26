package com.justdataplease.streetgym.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class FrontPullTest {
    private val frame = FrontPull.Frame()
    private val armLength = FrontPull.UPPER_ARM + FrontPull.FOREARM

    private fun pose(id: String, t: Float) = frame.also { FrontPull.pose(checkNotNull(FrontPull.variant(id, "")), t, it) }

    @Test
    fun barWorkThatMovesUpAndDownIsDrawnFromTheFront() {
        listOf("dead_hang", "active_hang", "scapular_pull", "foot_assisted_hang", "jump_to_hang", "pullup", "chinup",
            "chest_to_bar", "eccentric_pullup", "archer_pullup", "typewriter_pullup", "assisted_one_arm_pullup",
            "one_arm_pullup_negative")
            .forEach { assertNotNull(it, FrontPull.variant(it, "")) }
        // Levers, muscle-ups, leg raises and the low-bar assisted pull-up show their movement side-on.
        listOf("assisted_pullup", "full_front", "strict_muscleup", "hanging_leg_raise", "parallel_dip")
            .forEach { assertNull(it, FrontPull.variant(it, it)) }
        // An exercise bound by name reaches the front view through its clip.
        assertNotNull(FrontPull.variant(null, "pullup"))
    }

    @Test
    fun theGripFollowsTheExercise() {
        val chin = checkNotNull(FrontPull.variant("chinup", "")).grip
        val pull = checkNotNull(FrontPull.variant("pullup", "")).grip
        val archer = checkNotNull(FrontPull.variant("archer_pullup", "")).grip
        assertTrue("chin-up $chin < pull-up $pull < archer $archer", chin < pull && pull < archer)
        assertEquals(checkNotNull(FrontPull.variant("typewriter_pullup", "")).grip, archer, 0f)
    }

    @Test
    fun aPullUpRunsFromAStraightArmedHangToTheChinOverTheBar() {
        for (id in listOf("pullup", "chinup", "chest_to_bar")) {
            pose(id, 0f)
            assertEquals(id, FrontPull.BAR_Y, frame.leftY, 0f)
            val reach = hypot(frame.leftX - (frame.bodyX - FrontPull.SHOULDER), frame.leftY - frame.shoulderY)
            assertTrue("$id hangs on nearly straight arms ($reach)", reach > armLength * 0.95f)
            pose(id, 1f)
            val chin = frame.shoulderY - FrontPull.NECK
            assertTrue("$id: chin $chin over the bar", chin <= FrontPull.BAR_Y)
        }
    }

    @Test
    fun elbowsBendOutwardAndAnOutOfReachArmIsStraight() {
        val left = checkNotNull(FrontPull.elbow(95f, 29f, 89f, 26f, -1f))
        assertTrue("left elbow out and below: $left", left.first < 95f && left.second > 29f)
        val right = checkNotNull(FrontPull.elbow(105f, 29f, 111f, 26f, 1f))
        assertTrue("right elbow out and below: $right", right.first > 105f && right.second > 29f)
        assertNull(FrontPull.elbow(92f, 30f, 122f, 26f, 1f))
    }

    @Test
    fun theArcherPullsTowardOneHandAndTheTypewriterSlidesAcross() {
        pose("archer_pullup", 1f)
        assertTrue("archer leans to the left hand", frame.bodyX < FrontPull.CENTER - 8f)
        assertNull("the other arm stays straight",
            FrontPull.elbow(frame.bodyX + FrontPull.SHOULDER, frame.shoulderY, frame.rightX, frame.rightY, 1f))

        val xs = (0..20).map { pose("typewriter_pullup", it / 20f); frame.bodyX to frame.shoulderY }
        val leftTop = xs.indexOfFirst { it.first < FrontPull.CENTER - 8f && it.second < 32f }
        val rightTop = xs.indexOfFirst { it.first > FrontPull.CENTER + 8f && it.second < 32f }
        assertTrue("typewriter goes left at the top, then right: $xs", leftTop in 0 until rightTop)
        assertTrue("and ends back in the hang it started from: $xs",
            abs(xs.last().first - xs.first().first) < 0.5f && abs(xs.last().second - xs.first().second) < 0.5f)
    }

    @Test
    fun oneArmWorkLetsGoOfTheBarOrHoldsATowel() {
        pose("one_arm_pullup_negative", 0.5f)
        assertTrue("one hand lets go while lowering", !frame.rightHolds)
        pose("one_arm_pullup_negative", 0.05f)
        assertTrue("both hands on the way up", frame.rightHolds)
        pose("assisted_one_arm_pullup", 0.5f)
        assertTrue("the towel hangs from the bar", frame.towelBottom > FrontPull.BAR_Y && frame.rightX == frame.towelX)
        assertTrue("the towel hand is below the bar", frame.rightY > FrontPull.BAR_Y)
    }

    @Test
    fun hangsKeepTheFeetWhereTheExerciseSays() {
        pose("foot_assisted_hang", 0f)
        assertTrue("feet on the floor under a lower bar", frame.grounded && frame.barY > FrontPull.BAR_Y)
        val legs = FrontPull.THIGH + FrontPull.SHIN
        assertTrue("the legs reach the floor", FrontPull.FLOOR_Y - (frame.shoulderY + FrontPull.TORSO) < legs)
        pose("jump_to_hang", 0f)
        assertTrue("the jump starts standing", frame.grounded && frame.leftY > FrontPull.BAR_Y)
        pose("jump_to_hang", 0.6f)
        assertTrue("then hangs from the bar", !frame.grounded && frame.leftY == FrontPull.BAR_Y)
        pose("dead_hang", 0.5f)
        assertTrue("a hang keeps the feet off the floor", !frame.grounded)
    }
}
