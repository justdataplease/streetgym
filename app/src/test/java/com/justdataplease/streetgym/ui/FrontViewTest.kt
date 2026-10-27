package com.justdataplease.streetgym.ui

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot

class FrontViewTest {
    private val frame = FrontView.Frame()
    private val armLength = FrontView.UPPER_ARM + FrontView.FOREARM
    private val legLength = FrontView.THIGH + FrontView.SHIN

    private fun variant(id: String) = checkNotNull(FrontView.variant(id, ""))

    private fun pose(id: String, t: Float, mirror: Boolean = false, neckY: Float = Float.NaN, shrug: Float = 0f) =
        frame.also { FrontView.pose(variant(id), t, it, mirror, neckY, shrug) }

    private fun tipX(side: Float) = frame.neckX + side * FrontView.SHOULDER
    private fun tipY() = frame.neckY - frame.shrug

    @Test
    fun barWorkAndSidewaysMovementsAreDrawnFromTheFront() {
        listOf("dead_hang", "active_hang", "scapular_pull", "foot_assisted_hang", "jump_to_hang", "pullup", "chinup",
            "chest_to_bar", "eccentric_pullup", "archer_pullup", "typewriter_pullup", "assisted_one_arm_pullup",
            "one_arm_pullup_negative", "low_jack", "arm_swings", "hip_circles", "cossack_squat", "adductor_stretch",
            "wall_slide", "archer_pushup")
            .forEach { assertNotNull(it, FrontView.variant(it, "")) }
        // Levers, muscle-ups, leg raises, the low-bar assisted pull-up and plain push-ups show their movement side-on.
        listOf("assisted_pullup", "full_front", "strict_muscleup", "hanging_leg_raise", "parallel_dip", "pushup", "squat")
            .forEach { assertNull(it, FrontView.variant(it, it)) }
        // An exercise bound by name reaches the front view through its clip.
        assertNotNull(FrontView.variant(null, "pullup"))
        assertNotNull(FrontView.variant(null, "jack"))
    }

    @Test
    fun movementsDrawnFacingTheViewerBorrowASideClip() {
        // The Cossack clips face the viewer, so their guides show a side clip authored for them.
        assertEquals("cossack_side", variant("cossack_squat").sideClip)
        assertEquals("cossack_hold_side", variant("adductor_stretch").sideClip)
        for (id in listOf("cossack_side", "cossack_hold_side", "wiper_side")) assertNotNull(id, TestMotionClips.named(id))
        // Clips drawn facing the viewer borrow the pull-up for their side view; a push-up badge stays side-on.
        assertEquals("pullup", variant("archer_pullup").sideClip)
        assertEquals("pullup", variant("typewriter_pullup").sideClip)
        assertFalse(variant("archer_pushup").badge)
    }

    @Test
    fun theGripFollowsTheExercise() {
        val chin = variant("chinup").grip
        val pull = variant("pullup").grip
        val archer = variant("archer_pullup").grip
        assertTrue("chin-up $chin < pull-up $pull < archer $archer", chin < pull && pull < archer)
        assertEquals(variant("typewriter_pullup").grip, archer, 0f)
    }

    @Test
    fun aPullUpRunsFromAStraightArmedHangToTheChinOverTheBar() {
        for (id in listOf("pullup", "chinup", "chest_to_bar")) {
            pose(id, 0f)
            assertEquals(id, FrontView.BAR_Y, frame.leftY, 0f)
            val reach = hypot(frame.leftX - tipX(-1f), frame.leftY - tipY())
            assertTrue("$id hangs on nearly straight arms ($reach)", reach > armLength * 0.95f && reach <= armLength)
            pose(id, 1f)
            assertTrue("$id: chin ${frame.neckY - FrontView.NECK} over the bar", frame.neckY - FrontView.NECK <= FrontView.BAR_Y)
        }
    }

    @Test
    fun theHeightFollowsTheSideClipWithinReachOfTheBar() {
        pose("pullup", 0.5f, neckY = 24f, shrug = -1f)
        assertEquals("both views rise together", 24f, frame.neckY, 0f)
        assertEquals(-1f, frame.shrug, 0f)
        // The side clip's hang is lower than a wide grip allows: the front view hangs higher, arms in reach.
        pose("archer_pullup", 0f, neckY = 35.5f, shrug = -2.5f)
        assertTrue("wide grip lifts the body: ${frame.neckY}", frame.neckY < 35.5f)
        assertTrue(hypot(frame.leftX - tipX(-1f), frame.leftY - tipY()) <= armLength)
        // Floor motions keep their own height.
        pose("low_jack", 0f, neckY = 10f)
        assertTrue(frame.neckY > 40f)
    }

    @Test
    fun elbowsBendOutwardAndAnOutOfReachArmIsStraight() {
        val left = checkNotNull(FrontView.elbow(95f, 15f, 89f, 12f, -1f))
        assertTrue("left elbow out and below: $left", left.first < 95f && left.second > 15f)
        val right = checkNotNull(FrontView.elbow(105f, 15f, 111f, 12f, 1f))
        assertTrue("right elbow out and below: $right", right.first > 105f && right.second > 15f)
        assertNull(FrontView.elbow(92f, 16f, 122f, 12f, 1f))
    }

    @Test
    fun theArcherPullsTowardOneHandAndTheTypewriterSlidesAcross() {
        pose("archer_pullup", 1f)
        assertTrue("archer leans to the left hand", frame.neckX < FrontView.CENTER - 7f)
        assertTrue("the other arm stays nearly straight", hypot(frame.rightX - tipX(1f), frame.rightY - tipY()) > armLength * 0.97f)
        pose("archer_pullup", 1f, mirror = true)
        assertTrue("the next rep goes to the right hand", frame.neckX > FrontView.CENTER + 7f)

        val top = FrontView.BAR_Y + 6f
        val xs = (0..20).map { pose("typewriter_pullup", it / 20f); frame.neckX to frame.neckY }
        val leftTop = xs.indexOfFirst { it.first < FrontView.CENTER - 7f && it.second < top }
        val rightTop = xs.indexOfFirst { it.first > FrontView.CENTER + 7f && it.second < top }
        assertTrue("typewriter goes left at the top, then right: $xs", leftTop in 0 until rightTop)
        assertTrue("and ends back in the hang it started from: $xs",
            abs(xs.last().first - xs.first().first) < 0.5f && abs(xs.last().second - xs.first().second) < 0.5f)
        // Its side view stays at the top of a pull-up while it slides.
        val typewriter = variant("typewriter_pullup")
        assertEquals(0f, FrontView.sideTime(typewriter, 0f), 0f)
        assertEquals(0.85f, FrontView.sideTime(typewriter, 0.45f), 0.001f)
        assertEquals(0f, FrontView.sideTime(typewriter, 1f), 0.001f)
    }

    @Test
    fun oneArmWorkLetsGoOfTheBarOrHoldsATowel() {
        pose("one_arm_pullup_negative", 0.5f)
        assertTrue("one hand lets go while lowering", !frame.rightHolds)
        pose("one_arm_pullup_negative", 0.05f)
        assertTrue("both hands on the way up", frame.rightHolds && frame.boxHalf > 0f)
        pose("assisted_one_arm_pullup", 0.5f)
        assertTrue("the towel hangs from the bar", frame.towelBottom > FrontView.BAR_Y && frame.rightX == frame.towelX)
        assertTrue("the towel hand is below the bar", frame.rightY > FrontView.BAR_Y)
    }

    @Test
    fun hangsKeepTheFeetWhereTheExerciseSays() {
        pose("foot_assisted_hang", 0f)
        assertTrue("feet on the floor under a lower bar", frame.grounded && frame.barY > FrontView.BAR_Y)
        assertTrue("the legs reach the floor", FrontView.ANKLE_Y - frame.pelvisY < legLength)
        pose("jump_to_hang", 0f)
        assertTrue("the jump starts standing", frame.grounded && frame.leftY > FrontView.BAR_Y)
        pose("jump_to_hang", 0.6f)
        assertTrue("then hangs from the bar", !frame.grounded && frame.leftY == FrontView.BAR_Y)
        pose("dead_hang", 0.5f)
        assertTrue("a hang keeps the feet off the floor", !frame.grounded)
        pose("eccentric_pullup", 0f)
        assertTrue("a negative starts from a box", frame.boxHalf > 0f)
    }

    @Test
    fun aJackStepsOutAndRaisesTheArmsOneSideThenTheOther() {
        pose("low_jack", 0f)
        val narrow = frame.rightFootX - frame.leftFootX
        pose("low_jack", 1f)
        assertTrue("the right foot steps out", frame.rightFootX - FrontView.CENTER > 12f && frame.leftFootX < FrontView.CENTER)
        assertTrue("stance widens", frame.rightFootX - frame.leftFootX > narrow + 10f)
        assertTrue("hands overhead", frame.leftY < frame.neckY - 20f && frame.rightY < frame.neckY - 20f)
        pose("low_jack", 1f, mirror = true)
        assertTrue("then the left foot", FrontView.CENTER - frame.leftFootX > 12f)
    }

    @Test
    fun aCossackSinksOverOneFootWhileTheOtherLegStaysLong() {
        pose("cossack_squat", 0f)
        val high = frame.pelvisY
        pose("cossack_squat", 1f)
        assertTrue("hips sink: $high -> ${frame.pelvisY}", frame.pelvisY > high + 6f)
        assertTrue("over the left foot", frame.pelvisX < FrontView.CENTER - 6f)
        val longLeg = hypot(frame.rightFootX - (frame.pelvisX + FrontView.HIP), frame.rightFootY - frame.pelvisY)
        assertTrue("the right leg stays nearly straight ($longLeg)", longLeg > legLength * 0.97f)
        pose("cossack_squat", 1f, mirror = true)
        assertTrue("the next rep sinks to the right", frame.pelvisX > FrontView.CENTER + 6f)
    }

    @Test
    fun hipCirclesSwayRightThenLeftWithHandsOnTheHips() {
        pose("hip_circles", 0.25f)
        assertTrue(frame.pelvisX > FrontView.CENTER + 3f)
        assertTrue("hands on the hips", abs(frame.leftY - frame.pelvisY) < 3f)
        pose("hip_circles", 0.75f)
        assertTrue(frame.pelvisX < FrontView.CENTER - 3f)
    }

    @Test
    fun wallSlidesRaiseTheArmsAndArcherPushUpsLowerToOneHand() {
        pose("wall_slide", 0f)
        val low = frame.leftY
        assertTrue("a W: hands above the elbows' shoulder height", low < frame.neckY)
        pose("wall_slide", 1f)
        assertTrue("then up to a Y", frame.leftY < low - 10f)

        pose("archer_pushup", 0f, neckY = 80.3f)
        assertTrue("hands on the floor", frame.leftY > 100f && frame.rightY > 100f && frame.prone)
        pose("archer_pushup", 1f, neckY = 92.1f)
        assertTrue("lowered toward the left hand", frame.neckX < FrontView.CENTER - 6f)
        assertTrue("the right arm slides out straight",
            hypot(frame.rightX - tipX(1f), frame.rightY - tipY()) > armLength * 0.97f)
    }

    @Test
    fun noLimbIsLongerThanItsBones() {
        val ids = listOf("dead_hang", "active_hang", "scapular_pull", "foot_assisted_hang", "jump_to_hang", "pullup",
            "chinup", "chest_to_bar", "eccentric_pullup", "archer_pullup", "typewriter_pullup", "assisted_one_arm_pullup",
            "one_arm_pullup_negative", "low_jack", "hip_circles", "cossack_squat", "adductor_stretch", "wall_slide",
            "archer_pushup")
        for (id in ids) for (step in 0..40) for (mirror in listOf(false, true)) {
            val t = step / 40f
            pose(id, t, mirror)
            if (frame.leftHolds) {
                val arm = hypot(frame.leftX - tipX(-1f), frame.leftY - tipY())
                assertTrue("$id t=$t left arm $arm", arm <= armLength + 0.05f)
            }
            if (frame.rightHolds) {
                val arm = hypot(frame.rightX - tipX(1f), frame.rightY - tipY())
                assertTrue("$id t=$t right arm $arm", arm <= armLength + 0.05f)
            }
            if (frame.grounded) for (side in listOf(-1f, 1f)) {
                val footX = if (side < 0f) frame.leftFootX else frame.rightFootX
                val footY = if (side < 0f) frame.leftFootY else frame.rightFootY
                val leg = hypot(footX - (frame.pelvisX + side * FrontView.HIP), footY - frame.pelvisY)
                assertTrue("$id t=$t leg $leg", leg <= legLength + 0.05f)
            }
            val bounds = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
            FrontView.bounds(frame, bounds)
            assertTrue("$id t=$t inside the canvas: ${bounds.toList()}",
                bounds[0] > 50f && bounds[2] < 150f && bounds[1] - FrontView.HEAD_RADIUS >= -2f && bounds[3] <= FrontView.FLOOR_Y)
        }
    }

    private fun projected(clip: String, t: Float, hands: Float = FrontView.handWidth(0f, 0f)): Pair<FloatArray, FrontView.Projection> {
        val joints = FloatArray(Rig.JOINT_COUNT * 2)
        val pose = Pose()
        checkNotNull(TestMotionClips.named(clip)).sample(t, pose, joints)
        return joints to FrontView.Projection().also { FrontView.project(joints, pose.facing, hands, hands, it) }
    }

    private fun hand(side: Int) = FrontView.HAND + side * FrontView.LIMB_POINTS
    private fun foot(side: Int) = FrontView.FOOT + side * FrontView.LIMB_POINTS

    @Test
    fun everyClipSeenFromTheFrontKeepsItsHeightsInTheFrontHalf() {
        val templates = JSONObject(File("src/main/assets/exercise_animations.json").readText()).getJSONObject("templates")
        for (name in templates.keys()) for (step in 0..8) {
            val (joints, p) = projected(name, step / 8f, hands = 12f)
            val heights = (0 until Rig.HANDS).map { joints[it * 2 + 1] }
            for (point in p.y.indices) {
                assertTrue("$name: every point is at a side-clip joint's height", heights.any { abs(it - p.y[point]) < 0.001f })
                assertTrue("$name: inside the front half", abs(p.x[point] - FrontView.CENTER) < FrontView.CENTER / 2f - 4f)
            }
        }
    }

    @Test
    fun aPushUpSeenHeadOnSetsTheHandsWideAndPaintsTheHeadOverTheLegs() {
        val (_, p) = projected("pushup", 0f, hands = FrontView.handWidth(1f, 8.5f))
        for (side in 0..1) assertEquals(8.5f, abs(p.x[hand(side)] - FrontView.CENTER), 0.001f)
        assertEquals("the head is nearest the viewer", FrontView.HEAD_PART, p.order.last())
        val legs = (FrontView.LEG_PART until FrontView.PARTS).map { p.order.indexOf(it) }
        assertTrue("legs reach away from the viewer", legs.all { it < p.order.indexOf(FrontView.HEAD_PART) })
    }

    @Test
    fun aSupineTwistSeenFromTheFeetLowersTheKneesToOneSideThenTheOther() {
        pose("supine_twist", 0f)
        assertTrue(frame.supine && frame.kneeTurn == 0f)
        pose("supine_twist", 1f)
        val turn = frame.kneeTurn
        assertTrue("the knees lower to one side", turn > 1f)
        pose("supine_twist", 1f, mirror = true)
        assertEquals("and to the other on the next rep", -turn, frame.kneeTurn, 0.0001f)
    }

    @Test
    fun aClipSeenEndOnStacksNearlyInLine() {
        val joints = FloatArray(Rig.JOINT_COUNT * 2)
        val pose = Pose()
        checkNotNull(TestMotionClips.named("side_plank")).sample(0.5f, pose, joints)
        val wide = FrontView.Projection().also { FrontView.project(joints, pose.facing, 8.5f, 8.5f, it) }
        val end = FrontView.Projection().also { FrontView.project(joints, pose.facing, 8.5f, 8.5f, it, width = 0.35f) }
        val spread = { p: FrontView.Projection -> p.x.maxOf { abs(it - FrontView.CENTER) } }
        assertEquals(spread(wide) * 0.35f, spread(end), 0.001f)
    }

    @Test
    fun aHandstandSeenFromTheFrontHasItsLegsTogether() {
        val (_, standing) = projected("squat", 0f)
        val (_, inverted) = projected("wall_handstand", 0f)
        for (side in 0..1) {
            assertEquals(3f, abs(standing.x[foot(side)] - FrontView.CENTER), 0.001f)
            assertTrue(abs(inverted.x[foot(side)] - FrontView.CENTER) < 1.5f)
        }
    }
}
