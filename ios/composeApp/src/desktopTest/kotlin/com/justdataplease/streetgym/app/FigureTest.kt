package com.justdataplease.streetgym.app

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.justdataplease.streetgym.app.data.Program
import com.justdataplease.streetgym.app.data.loadProgram
import com.justdataplease.streetgym.app.figure.AnimationLibrary
import com.justdataplease.streetgym.app.figure.BadgeStyle
import com.justdataplease.streetgym.app.figure.FigureLayout
import com.justdataplease.streetgym.app.figure.FigureRenderer
import com.justdataplease.streetgym.app.figure.FigureText
import com.justdataplease.streetgym.app.figure.FrontView
import com.justdataplease.streetgym.app.figure.MotionText
import com.justdataplease.streetgym.app.figure.Pose
import com.justdataplease.streetgym.app.figure.Rig
import com.justdataplease.streetgym.app.theme.StreetTheme
import com.justdataplease.streetgym.data.model.TrainingExercise
import com.justdataplease.streetgym.json.JSONObject
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The line man as ported from Android: its joints for known poses against numbers worked out with Android's own
 * formulas, Android's FrontViewTest and AnimationCuesTest checks run on the port, and every movement of the program,
 * the ready workouts, the easier options and the movement library drawn in every layout without failing.
 */
class FigureTest {
    private val library: AnimationLibrary get() = Companion.library

    private fun sample(clip: String, t: Float, pose: Pose = Pose()): FloatArray =
        FloatArray(Rig.JOINT_COUNT * 2).also { checkNotNull(library.template(clip)) { clip }.curve.evaluate(t, pose, it) }

    private fun assertJoint(joints: FloatArray, joint: Int, x: Float, y: Float, label: String) {
        assertEquals(x, joints[joint * 2], 0.002f, "$label x")
        assertEquals(y, joints[joint * 2 + 1], 0.002f, "$label y")
    }

    @Test
    fun keyPointsMatchAndroidsRig() {
        // Worked out with Android's Pose.apply and Rig.solve over the same file: a pull-up's hang and its top, a
        // push-up's plank, a squat standing and at depth, a wall handstand, a tuck front lever.
        sample("pullup", 0f).let {
            assertJoint(it, Rig.HIP, 100f, 55.5f, "pull-up hang hip")
            assertJoint(it, Rig.HEAD, 100f, 28f, "pull-up hang head")
            assertJoint(it, Rig.HAND_NEAR, 100f, 12f, "pull-up hang hand")
            assertJoint(it, Rig.FOOT_NEAR, 100f, 86.5f, "pull-up hang foot")
        }
        sample("pullup", 1f).let {
            assertJoint(it, Rig.HIP, 95.3385f, 31.9486f, "pull-up top hip")
            assertJoint(it, Rig.NECK, 92.5550f, 12.1432f, "pull-up top neck")
            assertJoint(it, Rig.HEAD, 91.5112f, 4.7162f, "pull-up top head")
            assertJoint(it, Rig.FOOT_NEAR, 91.3243f, 62.0050f, "pull-up top foot")
        }
        sample("pushup", 0f).let {
            assertJoint(it, Rig.HIP, 118.0199f, 87.6542f, "push-up hip")
            assertJoint(it, Rig.HEAD, 93.1988f, 75.8151f, "push-up head")
            assertJoint(it, Rig.HAND_NEAR, 100.9854f, 105.0003f, "push-up hand")
            assertJoint(it, Rig.FOOT_NEAR, 146f, 101f, "push-up foot")
        }
        sample("squat", 0f).let {
            assertJoint(it, Rig.HIP, 100f, 73f, "squat standing hip")
            assertJoint(it, Rig.HEAD, 100f, 45.5f, "squat standing head")
            assertJoint(it, Rig.FOOT_NEAR, 100f, 104f, "squat standing foot")
        }
        sample("squat", 1f).let {
            assertJoint(it, Rig.HIP, 92.0133f, 87.9939f, "squat depth hip")
            assertJoint(it, Rig.HEAD, 102.4068f, 62.6233f, "squat depth head")
            assertJoint(it, Rig.HAND_NEAR, 126.3667f, 72.1338f, "squat depth hand")
        }
        sample("wall_handstand", 0f).let {
            assertJoint(it, Rig.HIP, 145f, 59f, "handstand hip")
            assertJoint(it, Rig.HEAD, 145f, 86.5f, "handstand head")
            assertJoint(it, Rig.HAND_NEAR, 145f, 105f, "handstand hand")
            assertJoint(it, Rig.FOOT_NEAR, 145f, 28f, "handstand foot")
        }
        sample("front_lever_tuck", 0f).let {
            assertJoint(it, Rig.HIP, 113.2707f, 37.1141f, "tuck lever hip")
            assertJoint(it, Rig.HEAD, 85.8846f, 35.8117f, "tuck lever head")
            assertJoint(it, Rig.FOOT_NEAR, 115.1125f, 34.9004f, "tuck lever foot")
        }

        // The front view, from FrontView.pose's formulas: a pull-up's hang and top, a chin-up's closer grip, a jack's end.
        val frame = FrontView.Frame()
        FrontView.pose(FrontView.variant("pullup", "")!!, 0f, frame)
        assertEquals(36.8972f, frame.neckY, 0.001f)
        assertEquals(56.8972f, frame.pelvisY, 0.001f)
        assertEquals(87.8972f, frame.leftFootY, 0.001f)
        assertEquals(89f, frame.leftX, 0f)
        FrontView.pose(FrontView.variant("pullup", "")!!, 1f, frame)
        assertEquals(FrontView.TOP_Y, frame.neckY, 0.0001f)
        FrontView.pose(FrontView.variant("chinup", "")!!, 0f, frame)
        assertEquals(37.5905f, frame.neckY, 0.001f)
        FrontView.pose(FrontView.variant("low_jack", "")!!, 1f, frame)
        assertEquals(74.2105f, frame.pelvisY, 0.001f)
        assertEquals(54.2105f, frame.neckY, 0.001f)
        assertEquals(94.9389f, frame.leftX, 0.001f)
        assertEquals(29.7243f, frame.leftY, 0.001f)
        assertEquals(118.0611f, frame.rightX, 0.001f)
    }

    @Test
    fun theTempoAndStartMatchAndroid() {
        val pull = checkNotNull(library.template("pullup"))
        // A pull rises: 1 count up, half a count at the top, 2 down, half a count of hang, 1.6 times the authored 2 s.
        assertEquals(1, pull.effort)
        assertEquals(listOf(1f, 0.5f, 2f, 0.5f), pull.tempoUnits.toList())
        assertEquals(3_200L, pull.cycleDurationMs)
        assertEquals(0.25f, pull.startPhase)
        assertEquals(MotionText.MARKER_CHIN, pull.markerLabel)
        assertEquals(Rig.HEAD, pull.markerJoint)
        // A push-up sinks first; a hold stands still.
        val push = checkNotNull(library.template("pushup"))
        assertEquals(-1, push.effort)
        assertEquals(Rig.SHOULDER, push.markerJoint)
        assertTrue(checkNotNull(library.template("front_lever_tuck")).still)
        assertFalse(pull.still)
    }

    // Android's FrontViewTest, on the port.

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
            "one_arm_pullup_negative", "low_jack", "jumping_jack", "arm_swings", "hip_circles", "cossack_squat",
            "adductor_stretch", "wall_slide", "archer_pushup")
            .forEach { assertNotNull(FrontView.variant(it, ""), it) }
        listOf("assisted_pullup", "full_front", "strict_muscleup", "hanging_leg_raise", "parallel_dip", "pushup", "squat")
            .forEach { assertNull(FrontView.variant(it, it), it) }
        assertNotNull(FrontView.variant(null, "pullup"))
        assertNotNull(FrontView.variant(null, "jack"))
    }

    @Test
    fun movementsDrawnFacingTheViewerBorrowASideClip() {
        assertEquals("cossack_side", variant("cossack_squat").sideClip)
        assertEquals("cossack_hold_side", variant("adductor_stretch").sideClip)
        for (id in listOf("cossack_side", "cossack_hold_side", "wiper_side")) assertNotNull(library.template(id), id)
        assertEquals("pullup", variant("archer_pullup").sideClip)
        assertEquals("pullup", variant("typewriter_pullup").sideClip)
        assertFalse(variant("archer_pushup").badge)
    }

    @Test
    fun aPullUpRunsFromAStraightArmedHangToTheChinOverTheBar() {
        for (id in listOf("pullup", "chinup", "chest_to_bar")) {
            pose(id, 0f)
            assertEquals(FrontView.BAR_Y, frame.leftY, 0f, id)
            val reach = hypot(frame.leftX - tipX(-1f), frame.leftY - tipY())
            assertTrue(reach > armLength * 0.95f && reach <= armLength, "$id hangs on nearly straight arms ($reach)")
            pose(id, 1f)
            assertTrue(frame.neckY - FrontView.NECK <= FrontView.BAR_Y, "$id: chin over the bar")
        }
    }

    @Test
    fun theHeightFollowsTheSideClipWithinReachOfTheBar() {
        pose("pullup", 0.5f, neckY = 24f, shrug = -1f)
        assertEquals(24f, frame.neckY, 0f)
        assertEquals(-1f, frame.shrug, 0f)
        pose("archer_pullup", 0f, neckY = 35.5f, shrug = -2.5f)
        assertTrue(frame.neckY < 35.5f, "wide grip lifts the body: ${frame.neckY}")
        assertTrue(hypot(frame.leftX - tipX(-1f), frame.leftY - tipY()) <= armLength)
        pose("low_jack", 0f, neckY = 10f)
        assertTrue(frame.neckY > 40f)
    }

    @Test
    fun elbowsBendOutwardAndAnOutOfReachArmIsStraight() {
        val left = checkNotNull(FrontView.elbow(95f, 15f, 89f, 12f, -1f))
        assertTrue(left.first < 95f && left.second > 15f, "left elbow out and below: $left")
        val right = checkNotNull(FrontView.elbow(105f, 15f, 111f, 12f, 1f))
        assertTrue(right.first > 105f && right.second > 15f, "right elbow out and below: $right")
        assertNull(FrontView.elbow(92f, 16f, 122f, 12f, 1f))
    }

    @Test
    fun theArcherPullsTowardOneHandAndTheTypewriterSlidesAcross() {
        pose("archer_pullup", 1f)
        assertTrue(frame.neckX < FrontView.CENTER - 7f)
        assertTrue(hypot(frame.rightX - tipX(1f), frame.rightY - tipY()) > armLength * 0.97f)
        pose("archer_pullup", 1f, mirror = true)
        assertTrue(frame.neckX > FrontView.CENTER + 7f)

        val top = FrontView.BAR_Y + 6f
        val xs = (0..20).map { pose("typewriter_pullup", it / 20f); frame.neckX to frame.neckY }
        val leftTop = xs.indexOfFirst { it.first < FrontView.CENTER - 7f && it.second < top }
        val rightTop = xs.indexOfFirst { it.first > FrontView.CENTER + 7f && it.second < top }
        assertTrue(leftTop in 0 until rightTop, "typewriter goes left at the top, then right: $xs")
        assertTrue(abs(xs.last().first - xs.first().first) < 0.5f && abs(xs.last().second - xs.first().second) < 0.5f)
        val typewriter = variant("typewriter_pullup")
        assertEquals(0f, FrontView.sideTime(typewriter, 0f), 0f)
        assertEquals(0.85f, FrontView.sideTime(typewriter, 0.45f), 0.001f)
        assertEquals(0f, FrontView.sideTime(typewriter, 1f), 0.001f)
    }

    @Test
    fun oneArmWorkLetsGoOfTheBarOrHoldsATowel() {
        pose("one_arm_pullup_negative", 0.5f)
        assertTrue(!frame.rightHolds)
        pose("one_arm_pullup_negative", 0.05f)
        assertTrue(frame.rightHolds && frame.boxHalf > 0f)
        pose("assisted_one_arm_pullup", 0.5f)
        assertTrue(frame.towelBottom > FrontView.BAR_Y && frame.rightX == frame.towelX)
        assertTrue(frame.rightY > FrontView.BAR_Y)
    }

    @Test
    fun hangsKeepTheFeetWhereTheExerciseSays() {
        pose("foot_assisted_hang", 0f)
        assertTrue(frame.grounded && frame.barY > FrontView.BAR_Y)
        assertTrue(FrontView.ANKLE_Y - frame.pelvisY < legLength)
        pose("jump_to_hang", 0f)
        assertTrue(frame.grounded && frame.leftY > FrontView.BAR_Y)
        pose("jump_to_hang", 0.6f)
        assertTrue(!frame.grounded && frame.leftY == FrontView.BAR_Y)
        pose("dead_hang", 0.5f)
        assertTrue(!frame.grounded)
        pose("eccentric_pullup", 0f)
        assertTrue(frame.boxHalf > 0f)
    }

    @Test
    fun aJackStepsOutAndAJumpingJackJumpsBothFeetOut() {
        pose("low_jack", 0f)
        val narrow = frame.rightFootX - frame.leftFootX
        pose("low_jack", 1f)
        assertTrue(frame.rightFootX - FrontView.CENTER > 12f && frame.leftFootX < FrontView.CENTER)
        assertTrue(frame.rightFootX - frame.leftFootX > narrow + 10f)
        assertTrue(frame.leftY < frame.neckY - 20f && frame.rightY < frame.neckY - 20f)
        pose("low_jack", 1f, mirror = true)
        assertTrue(FrontView.CENTER - frame.leftFootX > 12f)

        pose("jumping_jack", 0f)
        val stance = frame.rightFootX - frame.leftFootX
        val floor = frame.leftFootY
        pose("jumping_jack", 0.5f)
        assertTrue(frame.leftFootY < floor - 4f && frame.rightFootY < floor - 4f)
        pose("jumping_jack", 1f)
        assertTrue(frame.rightFootX - frame.leftFootX > stance + 18f)
        assertEquals(floor, frame.leftFootY, 0.01f)
    }

    @Test
    fun aCossackSinksOverOneFootAndHipCirclesSway() {
        pose("cossack_squat", 0f)
        val high = frame.pelvisY
        pose("cossack_squat", 1f)
        assertTrue(frame.pelvisY > high + 6f)
        assertTrue(frame.pelvisX < FrontView.CENTER - 6f)
        val longLeg = hypot(frame.rightFootX - (frame.pelvisX + FrontView.HIP), frame.rightFootY - frame.pelvisY)
        assertTrue(longLeg > legLength * 0.97f)
        pose("cossack_squat", 1f, mirror = true)
        assertTrue(frame.pelvisX > FrontView.CENTER + 6f)
        pose("hip_circles", 0.25f)
        assertTrue(frame.pelvisX > FrontView.CENTER + 3f)
        assertTrue(abs(frame.leftY - frame.pelvisY) < 3f)
        pose("hip_circles", 0.75f)
        assertTrue(frame.pelvisX < FrontView.CENTER - 3f)
    }

    @Test
    fun wallSlidesRaiseTheArmsAndArcherPushUpsLowerToOneHand() {
        pose("wall_slide", 0f)
        val low = frame.leftY
        assertTrue(low < frame.neckY)
        pose("wall_slide", 1f)
        assertTrue(frame.leftY < low - 10f)
        pose("archer_pushup", 0f, neckY = 80.3f)
        assertTrue(frame.leftY > 100f && frame.rightY > 100f && frame.prone)
        pose("archer_pushup", 1f, neckY = 92.1f)
        assertTrue(frame.neckX < FrontView.CENTER - 6f)
        assertTrue(hypot(frame.rightX - tipX(1f), frame.rightY - tipY()) > armLength * 0.97f)
    }

    @Test
    fun noLimbIsLongerThanItsBones() {
        val ids = listOf("dead_hang", "active_hang", "scapular_pull", "foot_assisted_hang", "jump_to_hang", "pullup",
            "chinup", "chest_to_bar", "eccentric_pullup", "archer_pullup", "typewriter_pullup", "assisted_one_arm_pullup",
            "one_arm_pullup_negative", "low_jack", "jumping_jack", "hip_circles", "cossack_squat", "adductor_stretch", "wall_slide",
            "archer_pushup")
        for (id in ids) for (step in 0..40) for (mirror in listOf(false, true)) {
            val t = step / 40f
            pose(id, t, mirror)
            if (frame.leftHolds) assertTrue(hypot(frame.leftX - tipX(-1f), frame.leftY - tipY()) <= armLength + 0.05f, "$id t=$t left arm")
            if (frame.rightHolds) assertTrue(hypot(frame.rightX - tipX(1f), frame.rightY - tipY()) <= armLength + 0.05f, "$id t=$t right arm")
            if (frame.grounded) for (side in listOf(-1f, 1f)) {
                val footX = if (side < 0f) frame.leftFootX else frame.rightFootX
                val footY = if (side < 0f) frame.leftFootY else frame.rightFootY
                assertTrue(hypot(footX - (frame.pelvisX + side * FrontView.HIP), footY - frame.pelvisY) <= legLength + 0.05f, "$id t=$t leg")
            }
            val bounds = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
            FrontView.bounds(frame, bounds)
            assertTrue(bounds[0] > 50f && bounds[2] < 150f && bounds[1] - FrontView.HEAD_RADIUS >= -2f && bounds[3] <= FrontView.FLOOR_Y,
                "$id t=$t inside the canvas: ${bounds.toList()}")
        }
    }

    private fun projected(clip: String, t: Float, hands: Float = FrontView.handWidth(0f, 0f)): Pair<FloatArray, FrontView.Projection> {
        val pose = Pose()
        val joints = sample(clip, t, pose)
        return joints to FrontView.Projection().also { FrontView.project(joints, pose.facing, hands, hands, it) }
    }

    @Test
    fun everyClipSeenFromTheFrontKeepsItsHeightsInTheFrontHalf() {
        for (name in library.templateNames) for (step in 0..8) {
            val (joints, p) = projected(name, step / 8f, hands = 12f)
            val heights = (0 until Rig.HANDS).map { joints[it * 2 + 1] }
            for (point in p.y.indices) {
                assertTrue(heights.any { abs(it - p.y[point]) < 0.001f }, "$name: every point is at a side-clip joint's height")
                assertTrue(abs(p.x[point] - FrontView.CENTER) < FrontView.CENTER / 2f - 4f, "$name: inside the front half")
            }
        }
    }

    @Test
    fun aPushUpSeenHeadOnSetsTheHandsWideAndPaintsTheHeadOverTheLegs() {
        val (_, p) = projected("pushup", 0f, hands = FrontView.handWidth(1f, 8.5f))
        for (side in 0..1) assertEquals(8.5f, abs(p.x[FrontView.HAND + side * FrontView.LIMB_POINTS] - FrontView.CENTER), 0.001f)
        assertEquals(FrontView.HEAD_PART, p.order.last(), "the head is nearest the viewer")
        val legs = (FrontView.LEG_PART until FrontView.PARTS).map { p.order.indexOf(it) }
        assertTrue(legs.all { it < p.order.indexOf(FrontView.HEAD_PART) }, "legs reach away from the viewer")
    }

    @Test
    fun aSupineTwistAndAClipSeenEndOnAndAHandstandFromTheFront() {
        pose("supine_twist", 0f)
        assertTrue(frame.supine && frame.kneeTurn == 0f)
        pose("supine_twist", 1f)
        val turn = frame.kneeTurn
        assertTrue(turn > 1f)
        pose("supine_twist", 1f, mirror = true)
        assertEquals(-turn, frame.kneeTurn, 0.0001f)

        val pose = Pose()
        val joints = sample("side_plank", 0.5f, pose)
        val wide = FrontView.Projection().also { FrontView.project(joints, pose.facing, 8.5f, 8.5f, it) }
        val end = FrontView.Projection().also { FrontView.project(joints, pose.facing, 8.5f, 8.5f, it, width = 0.35f) }
        val spread = { p: FrontView.Projection -> p.x.maxOf { abs(it - FrontView.CENTER) } }
        assertEquals(spread(wide) * 0.35f, spread(end), 0.001f)

        val (_, standing) = projected("squat", 0f)
        val (_, inverted) = projected("wall_handstand", 0f)
        for (side in 0..1) {
            assertEquals(3f, abs(standing.x[FrontView.FOOT + side * FrontView.LIMB_POINTS] - FrontView.CENTER), 0.001f)
            assertTrue(abs(inverted.x[FrontView.FOOT + side * FrontView.LIMB_POINTS] - FrontView.CENTER) < 1.5f)
        }
    }

    // Android's AnimationCuesTest, on the port.

    @Test
    fun rowsDrawTheirLineAtTheBarAndStretchesHoldWithoutOne() {
        for ((name, bar) in listOf("row" to 66f, "high_row" to 60f, "feet_elevated_row" to 66f, "chest_to_bar" to 12f)) {
            val clip = checkNotNull(library.template(name))
            assertEquals(Rig.SHOULDER, clip.markerJoint, name)
            assertEquals(bar, clip.markerY, 0.01f, name)
            assertEquals(MotionText.MARKER_CHEST_TO_BAR, clip.markerLabel, name)
        }
        assertNotEquals(library.template("row")!!.markerY, library.template("row")!!.markerReachY)
        for (name in listOf("hamstring_stretch", "ninety_ninety_stretch", "hip_flexor_stretch")) {
            val clip = checkNotNull(library.template(name))
            assertEquals(-1, clip.markerJoint, name)
            assertEquals(MotionText.PHASE_HOLD_BREATHE, clip.phaseSpec!!.labels.single(), name)
        }
        val negative = library.template("eccentric_pullup")!!.phaseSpec!!
        assertEquals(MotionText.PHASE_LOWER_SLOW, negative.labels[negative.indexAt(0.4f)])
        assertEquals(MotionText.PHASE_STEP_DOWN, negative.labels[negative.indexAt(0.95f)])
        val wall = library.template("wall_hspu_negative")!!.phaseSpec!!
        assertEquals(MotionText.PHASE_COME_DOWN, wall.labels[wall.indexAt(0.9f)])
    }

    @Test
    fun everyCueInTheFileNamesAKnownLabel() {
        val templates = JSONObject(animationsFile.readText()).getJSONObject("templates")
        for (name in templates.keySet()) {
            val json = templates.getJSONObject(name)
            val clip = checkNotNull(library.template(name)) { name }
            val label = json.optJSONObject("marker")?.optString("label").orEmpty()
            if (label.isNotEmpty() && label != "full_range") assertNotEquals(MotionText.MARKER_RANGE, clip.markerLabel, "$name: '$label'")
            if (json.has("phases")) {
                assertNotNull(clip.phaseSpec, "$name: unknown phase labels")
                assertEquals(json.optJSONArray("phases")?.length() ?: 1, clip.phaseSpec?.labels?.size, name)
            }
        }
    }

    // Every movement draws.

    private class Bound(val movementId: String?, val name: String, val category: String?, val equipment: List<String>, val descriptor: String)

    /** Every exercise of every class and ready workout, every easier option and every movement of the library. */
    private fun everything(): List<Bound> {
        val catalog = program.catalog
        val classes = catalog.levels.flatMap { it.programs } + catalog.workouts
        fun bound(exercise: TrainingExercise) = Bound(exercise.movementId, exercise.name.resolve(), exercise.category,
            exercise.equipment, exercise.description.resolve())
        val exercises = classes.flatMap { it.warmup + it.practice + it.exercises + it.cooldown }
        val movements = JSONObject(curriculumFile.readText()).getJSONArray("movement_library").let { array ->
            (0 until array.length()).map { array.getJSONObject(it) }.map { movement ->
                Bound(movement.getString("id"), movement.getJSONObject("name").optString("en"), movement.optString("category"),
                    movement.optJSONArray("equipment")?.let { tags -> (0 until tags.length()).map(tags::getString) }.orEmpty(), "")
            }
        }
        val easier = exercises.mapNotNull { it.easier }.map { Bound(it.movementId, it.name.resolve(), null, emptyList(), "") }
        return (exercises.map(::bound) + easier + movements).distinctBy { listOf(it.movementId, it.name, it.descriptor) }
    }

    @Test
    fun everyMovementOfTheProgramAndTheLibraryHasAFigureAndDraws() {
        val all = everything()
        assertTrue(all.size > 300, "the program, workouts and library: ${all.size}")
        val ids = all.mapNotNull { it.movementId }.toSet()
        val missing = ids.filter { library.clipOf(it) == null }
        assertTrue(missing.isEmpty(), "movements with no clip of their own: $missing")
        val unnamed = all.filter { it.movementId == null }
            .filter { library.resolve(null, it.name, it.descriptor, it.category, it.equipment) === library.fallback }
        assertTrue(unnamed.isEmpty(), "exercises that only find the fallback: ${unnamed.map { it.name }}")

        val failures = mutableListOf<String>()
        val layouts = mutableMapOf<FigureLayout, Int>()
        for (theme in StreetTheme.entries) for (movement in all.distinctBy { it.movementId ?: it.name }) {
            val label = "${theme.key} ${movement.movementId ?: movement.name}"
            runCatching {
                val renderer = FigureRenderer(library, theme.palette, DENSITY) { it.name }
                    .bind(movement.movementId, movement.name, movement.category, movement.equipment, movement.descriptor)
                if (theme == StreetTheme.CONCRETE) layouts.merge(renderer.layout, 1, Int::plus)
                // A guide on the class page, and the focus workout's tall panel, through a loop and the next rep.
                for ((phase, cycle) in listOf(0f to 0, 0.12f to 0, 0.28f to 0, 0.6f to 0, 0.28f to 1)) {
                    val ink = ink(draw(340, 168) { renderer.draw(this, text, phase, cycle, playing = true) }, theme.palette.text)
                    if (ink < 60) failures += "$label at $phase: only $ink figure pixels"
                }
                val tall = ink(draw(360, 330) { renderer.draw(this, text, 0.28f, 0, playing = true) }, theme.palette.text)
                if (tall < 120) failures += "$label tall: only $tall figure pixels"
                // Still, as Reduce Motion shows it.
                val still = ink(draw(340, 168) { renderer.draw(this, text, 0f, 0, playing = false) }, theme.palette.text)
                if (still < 60) failures += "$label still: only $still figure pixels"
                // A workout tile and a skill badge.
                val figure = theme.palette.primary
                val style = BadgeStyle(theme.palette.infoSurface, figure, theme.palette.divider, theme.palette.textSecondary)
                val tile = FigureRenderer(library, theme.palette, DENSITY) { it.name }
                    .bind(movement.movementId, movement.name, movement.category, movement.equipment).setBadgeStyle(style, moving = true)
                val badge = ink(draw(52, 52) { tile.draw(this, text, 0.28f, 0, playing = true) }, figure)
                if (badge < 15) failures += "$label tile: only $badge figure pixels"
            }.onFailure { failures += "$label threw ${it::class.simpleName}: ${it.message}" }
        }
        assertTrue(failures.isEmpty(), "${failures.size} failures:\n${failures.take(40).joinToString("\n")}")
        // Android's three guide layouts are all in use; its single view is only reached by a movement with no front
        // motion that is neither projected nor facing, which a guide never is.
        assertTrue(listOf(FigureLayout.BOTH, FigureLayout.PROJECTED, FigureLayout.FACING).all { (layouts[it] ?: 0) > 0 }, "layouts: $layouts")
        assertEquals(null, layouts[FigureLayout.SINGLE])
    }

    /** Draws at [widthDp] x [heightDp] at the test's density into a picture. */
    private fun draw(widthDp: Int, heightDp: Int, block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit): ImageBitmap {
        val width = (widthDp * DENSITY).toInt()
        val height = (heightDp * DENSITY).toInt()
        val bitmap = ImageBitmap(width, height)
        CanvasDrawScope().draw(Density(DENSITY), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat()), block)
        return bitmap
    }

    /** How many pixels are the figure's colour (the anti-aliased edges aside). */
    private fun ink(bitmap: ImageBitmap, color: androidx.compose.ui.graphics.Color): Int {
        val pixels = bitmap.toPixelMap()
        var count = 0
        for (x in 0 until pixels.width) for (y in 0 until pixels.height) {
            val pixel = pixels[x, y]
            if (abs(pixel.red - color.red) < 0.02f && abs(pixel.green - color.green) < 0.02f && abs(pixel.blue - color.blue) < 0.02f &&
                pixel.alpha > 0.98f) count++
        }
        return count
    }

    private companion object {
        const val DENSITY = 2f
        val program: Program by lazy { runBlocking { loadProgram() } }
        val library: AnimationLibrary by lazy { checkNotNull(program.motions) { "exercise_animations.json was not read" } }
        val text = FigureText(TextMeasurer(createFontFamilyResolver(), Density(DENSITY), LayoutDirection.Ltr), FontFamily.Default, Density(DENSITY))
        /** The Android app's files, which the build copies in. */
        private val assets = listOf("../../android-streetgym/app/src/main/assets", "../../app/src/main/assets").map(::File).first { it.isDirectory }
        val animationsFile = File(assets, "exercise_animations.json")
        val curriculumFile = File(assets, "curriculum.json")
    }
}
