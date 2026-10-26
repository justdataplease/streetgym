package com.justdataplease.streetgym.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Plays every routine the generator can make, frame by frame, against the rules the hero card relies on. */
class ParkStoryTest {
    private val rig = TestMotionClips.rig
    private val pose = Pose()
    private val joints = FloatArray(Rig.JOINT_COUNT * 2)

    private fun story(routine: ParkRoutine) =
        ParkStory(rig, routine, TestMotionClips::named).apply { plan(leftEdge(width)) }

    /** Steps through the story and the first idle loop at 120 fps. */
    private inline fun frames(story: ParkStory, check: (Float, FloatArray) -> Unit) {
        val last = story.end + ParkStory.IDLE_WRAP_AT + ParkStory.IDLE_CYCLE
        var frame = 0
        while (true) {
            val t = frame / FPS
            if (t > last) break
            story.poseHero(t, pose, joints)
            check(t, joints)
            frame++
        }
    }

    @Test fun everyRoutineFindsItsClips() {
        for (routine in ParkRoutine.all) assertTrue("$routine", story(routine).ready)
    }

    @Test fun routinesRunTenToTwentyOneSeconds() {
        val ends = ParkRoutine.all.associateWith { story(it).end }
        println("routines run ${"%.1f".format(ends.values.min())} to ${"%.1f".format(ends.values.max())} s")
        for ((routine, end) in ends) assertTrue("$routine runs $end s", end in 10f..21f)
    }

    @Test fun mostRoutinesKeepTheFullSizeAndNoneZoomsOutFar() {
        val widths = ParkRoutine.all.map { story(it).width }
        val fullSize = widths.count { it <= ParkStory.MIN_WIDTH + 0.01f }
        println("widths: ${widths.sorted().joinToString { "%.1f".format(it) }}")
        assertTrue("a park ${widths.max()} wide", widths.max() <= MAX_WIDTH)
        assertTrue("only $fullSize full-size parks", fullSize * 2 >= widths.size)
    }

    @Test fun spotsKeepTheHeroAndFurnitureApartFromTheFriends() {
        val failures = ArrayList<String>()
        for (routine in ParkRoutine.all) {
            val story = story(routine)
            val spots = story.spots
            assertTrue("$routine first spot at ${spots.first().left}", spots.first().left >= story.friendX[0] + FRIEND_HALF)
            for (i in 1 until spots.size) assertTrue("$routine spots $i overlap", spots[i].left > spots[i - 1].right)
            assertTrue("$routine friends crowd the last spot", story.friendX[1] - FRIEND_HALF > spots.last().right)
            assertTrue("$routine friend at ${story.friendX[2]}", story.friendX[2] + FRIEND_HALF <= story.width)
            for (prop in story.props) {
                val overhang = overhang(prop)
                assertTrue(
                    "$routine prop ${prop.kind} at ${prop.left}..${prop.right} outside its spot",
                    spots.any { prop.left - overhang >= it.left && prop.right + overhang <= it.right },
                )
            }
            // The hero stays inside its spot from the end of the walk there until it walks on.
            for ((i, spot) in spots.withIndex()) {
                var left = Float.MAX_VALUE
                var right = -Float.MAX_VALUE
                var t = spot.start
                while (t <= spot.end) {
                    story.poseHero(t, pose, joints)
                    val (l, r) = heroExtent(joints)
                    left = min(left, l)
                    right = max(right, r)
                    t += 1f / FPS
                }
                if (left < spot.left || right > spot.right) {
                    failures += "$routine spot $i reaches ${"%.1f".format(left - spot.x)}..${"%.1f".format(right - spot.x)}, " +
                        "allowed ${spot.left - spot.x}..${spot.right - spot.x}"
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun heroStaysInsideTheCardAndOnTheFloor() {
        for (routine in ParkRoutine.all) {
            val story = story(routine)
            var entered = false
            frames(story) { t, j ->
                for (joint in 0 until BODY_JOINTS) {
                    val y = j[joint * 2 + 1]
                    val floor = if (joint == Rig.HAND_NEAR || joint == Rig.HAND_FAR) HAND_FLOOR else FOOT_FLOOR
                    assertTrue("$routine joint $joint at y $y below the floor at $t", y <= floor + 0.05f)
                }
                val headTop = j[Rig.HEAD * 2 + 1] - rig.headRadius
                val (left, right) = heroExtent(j)
                assertTrue("$routine head top $headTop above the card at $t", headTop >= SCENE_TOP)
                assertTrue("$routine reaches x $right at $t", right <= story.width)
                if (left >= 0f) entered = true
                assertTrue("$routine leaves to x $left at $t", !entered || left >= 0f)
            }
        }
    }

    @Test fun heroNeverJumpsBetweenFrames() {
        val previous = FloatArray(Rig.JOINT_COUNT * 2)
        var worst = 0f
        var worstRoutine: ParkRoutine? = null
        for (routine in ParkRoutine.all) {
            val story = story(routine)
            var first = true
            var largest = 0f
            var largestAt = 0f
            frames(story) { t, j ->
                if (!first) {
                    for (joint in 0 until BODY_JOINTS) {
                        val step = hypot(j[joint * 2] - previous[joint * 2], j[joint * 2 + 1] - previous[joint * 2 + 1])
                        if (step > largest) {
                            largest = step
                            largestAt = t
                        }
                    }
                }
                j.copyInto(previous)
                first = false
            }
            if (largest > worst) {
                worst = largest
                worstRoutine = routine
            }
            assertTrue("$routine jumps $largest units in one frame at $largestAt", largest < MAX_FRAME_STEP)
        }
        println("largest step ${"%.2f".format(worst)} units in $worstRoutine")
    }

    @Test fun heroNeverFloats() {
        var worst = 0f
        for (routine in ParkRoutine.all) {
            val story = story(routine)
            var since = -1f
            var longest = 0f
            var longestAt = 0f
            frames(story) { t, j ->
                val footDown = intArrayOf(Rig.FOOT_NEAR, Rig.FOOT_FAR, Rig.TOE_NEAR, Rig.TOE_FAR).any { j[it * 2 + 1] >= FOOT_FLOOR - 0.1f }
                val holding = onFurniture(story, j, Rig.HAND_NEAR) || onFurniture(story, j, Rig.HAND_FAR)
                if (footDown || holding) {
                    since = -1f
                } else {
                    if (since < 0f) since = t
                    if (t - since > longest) {
                        longest = t - since
                        longestAt = since
                    }
                }
            }
            worst = max(worst, longest)
            assertTrue("$routine floats for $longest s from $longestAt", longest <= MAX_FLIGHT)
        }
        println("longest flight ${"%.2f".format(worst)} s")
    }

    @Test fun finalFrameHoldsTheSkillOnTheFurniture() {
        for (routine in ParkRoutine.all) {
            val story = story(routine)
            story.poseHero(story.end, pose, joints)
            for (hand in intArrayOf(Rig.HAND_NEAR, Rig.HAND_FAR)) {
                assertTrue("$routine hand $hand is not on the furniture", onFurniture(story, joints, hand))
            }
            // Not just standing: either the hands carry the body or they are planted well away from the hips.
            val hipY = joints[Rig.HIP * 2 + 1]
            assertTrue("$routine ends standing", hipY < 70f || joints[Rig.HAND_NEAR * 2 + 1] < 85f)
        }
    }

    @Test fun idleKeepsTheGripAndLoopsSeamlessly() {
        val friend = Pose()
        val before = FloatArray(Rig.JOINT_COUNT * 2)
        val after = FloatArray(Rig.JOINT_COUNT * 2)
        for (routine in ParkRoutine.all) {
            val story = story(routine)
            story.poseHero(story.end, pose, before)
            var t = story.end
            while (t < story.end + ParkStory.IDLE_CYCLE) {
                story.poseHero(t, pose, joints)
                for (hand in intArrayOf(Rig.HAND_NEAR, Rig.HAND_FAR)) {
                    if (onFurniture(story, before, hand)) {
                        assertTrue("$routine hand $hand slips at $t", onFurniture(story, joints, hand))
                    }
                }
                t += 0.05f
            }
            // The view jumps back one cycle here; hero and friends must look the same on both sides.
            val wrap = story.end + ParkStory.IDLE_WRAP_AT
            story.poseHero(wrap, pose, before)
            story.poseHero(wrap + ParkStory.IDLE_CYCLE, pose, after)
            assertClose("$routine hero", before, after)
            for (index in 0 until ParkStory.FRIENDS) {
                story.poseFriend(index, wrap, friend, before)
                story.poseFriend(index, wrap + ParkStory.IDLE_CYCLE, friend, after)
                assertClose("$routine friend $index", before, after)
            }
        }
    }

    /**
     * Writes routines' frames as JSON for the preview renderer when PARK_SCENE_DUMP names a file: the days in
     * PARK_SCENE_DAYS ("first epoch day:count"), by default today and the week after.
     */
    @Test fun dumpFramesForPreviews() {
        val path = System.getenv("PARK_SCENE_DUMP")
        assumeTrue(!path.isNullOrBlank())
        val range = System.getenv("PARK_SCENE_DAYS")?.split(':')
        val firstDay = range?.get(0)?.toLong() ?: ParkRoutine.localEpochDay()
        val count = range?.get(1)?.toInt() ?: 8
        val friend = Pose()
        val friendJoints = FloatArray(Rig.JOINT_COUNT * 2)
        val out = StringBuilder()
        out.append("{\"headRadius\":").append(rig.headRadius).append(",\"routines\":[")
        for (d in 0 until count) {
            val day = firstDay + d
            val routine = ParkRoutine.forEpochDay(day)
            val story = story(routine)
            if (d > 0) out.append(',')
            out.append("{\"day\":").append(day).append(",\"moves\":")
            out.append(routine.moves.joinToString(",", "[", "]") { "\"${it.name}\"" })
            out.append(",\"width\":").append(story.width).append(",\"leftEdge\":").append(leftEdge(story.width))
            out.append(",\"end\":").append(story.end)
            out.append(",\"friendX\":").append(story.friendX.joinToString(",", "[", "]"))
            out.append(",\"friendFacing\":").append(story.friendFacing.joinToString(",", "[", "]"))
            out.append(",\"props\":").append(story.props.joinToString(",", "[", "]") { "[${it.kind},${it.y},${it.left},${it.right}]" })
            out.append(",\"fps\":").append(DUMP_FPS).append(",\"frames\":[")
            val times = ArrayList<Float>()
            var frame = 0
            while (frame / DUMP_FPS <= story.end + DUMP_IDLE) times += frame++ / DUMP_FPS
            times += story.end
            for ((i, t) in times.withIndex()) {
                if (i > 0) out.append(',')
                story.poseHero(t, pose, joints)
                out.append("{\"t\":").append(t).append(",\"hero\":").append(joints.joinToString(",", "[", "]"))
                out.append(",\"spine\":").append(pose.spine).append(",\"facing\":").append(pose.facing).append(",\"friends\":[")
                for (index in 0 until ParkStory.FRIENDS) {
                    val flash = story.poseFriend(index, t, friend, friendJoints)
                    if (index > 0) out.append(',')
                    out.append("{\"joints\":").append(friendJoints.joinToString(",", "[", "]"))
                    out.append(",\"spine\":").append(friend.spine).append(",\"flash\":").append(flash).append('}')
                }
                out.append("]}")
            }
            out.append("]}")
        }
        out.append("]}")
        File(path!!).writeText(out.toString())
    }

    /** The hero's leftmost and rightmost x, head included. */
    private fun heroExtent(j: FloatArray): Pair<Float, Float> {
        var left = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        for (joint in 0 until BODY_JOINTS) {
            left = min(left, j[joint * 2])
            right = max(right, j[joint * 2])
        }
        val headX = j[Rig.HEAD * 2]
        return Pair(min(left, headX - rig.headRadius), max(right, headX + rig.headRadius))
    }

    private fun overhang(prop: ParkProp) = when (prop.kind) {
        ParkProp.BAR -> ParkProp.BAR_OVERHANG
        ParkProp.PARALLEL_BARS -> ParkProp.PARALLEL_OVERHANG
        else -> 0f
    }

    private fun onFurniture(story: ParkStory, j: FloatArray, hand: Int): Boolean {
        val x = j[hand * 2]
        val y = j[hand * 2 + 1]
        if (y >= FOOT_FLOOR - 0.1f) return true
        for (prop in story.props) {
            val overhang = overhang(prop)
            if (abs(y - prop.y) <= GRIP && x >= prop.left - overhang - GRIP && x <= prop.right + overhang + GRIP) return true
            // A post: the bar's uprights, gripped anywhere below the bar.
            if (prop.kind == ParkProp.BAR && y >= prop.y && (abs(x - prop.left) <= GRIP || abs(x - prop.right) <= GRIP)) return true
        }
        return false
    }

    private fun assertClose(what: String, expected: FloatArray, actual: FloatArray) {
        for (i in 0 until BODY_JOINTS * 2) assertEquals("$what coordinate $i", expected[i], actual[i], 0.01f)
    }

    private companion object {
        const val FPS = 120f
        const val DUMP_FPS = 20f
        const val DUMP_IDLE = 5f
        const val BODY_JOINTS = 14

        // A 340 x 180 dp hero card shows a scene of [width] at scale min(340 / width, 180 / 119), centred.
        const val CARD_WIDTH = 340f
        const val CARD_HEIGHT = 180f
        const val SCENE_TOP = -9f
        const val SCENE_BOTTOM = 110f

        /** Wider than this and the figures shrink by more than a twentieth. */
        const val MAX_WIDTH = 210f
        const val FRIEND_HALF = 6f
        const val FOOT_FLOOR = 104f
        const val HAND_FLOOR = 105f
        const val GRIP = 0.6f
        const val MAX_FRAME_STEP = 3.5f
        const val MAX_FLIGHT = 0.6f

        /** The leftmost x the card shows for a scene [width] wide. */
        fun leftEdge(width: Float): Float {
            val scale = min(CARD_WIDTH / width, CARD_HEIGHT / (SCENE_BOTTOM - SCENE_TOP))
            return -(CARD_WIDTH - width * scale) / 2f / scale
        }
    }
}
