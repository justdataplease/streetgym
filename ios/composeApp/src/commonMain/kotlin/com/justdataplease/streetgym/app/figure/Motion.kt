package com.justdataplease.streetgym.app.figure

import com.justdataplease.streetgym.json.JSONArray
import com.justdataplease.streetgym.json.JSONObject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The line man's movements, read from exercise_animations.json (schema 2): ported line by line from the Android app
 * (android-streetgym, ui/ExerciseAnimationView.kt, from `Pose` to the end of the file). Plain Kotlin with no drawing,
 * so it can move into the shared engine; the names, numbers and order of the maths are Android's, to compare them side
 * by side. What Android keeps as string resource ids is a [MotionText] here, turned into words by the screens.
 *
 * The figure is an articulated side-view rig with fixed bone lengths. Every pose stores absolute joint angles plus one
 * pinned joint (hands on the bar, feet on the floor), so limbs never stretch and contact points stay put while the body
 * moves between keyframes. Poses are joined by a monotone spline ([MotionCurve]), so the figure only slows where a
 * joint reverses or holds.
 */

/** Android's Math.toRadians factor, so every angle turns into the same floats on both phones. */
internal const val DEGREES_TO_RADIANS = 0.017453292519943295

private fun lerp(start: Float, end: Float, fraction: Float): Float = start + (end - start) * fraction

/** Shortest signed arc from one absolute angle to another, in degrees. */
private fun angleDelta(start: Float, end: Float): Float {
    var delta = (end - start) % 360f
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return delta
}

/** The guides' words, by the Android string each stands for (strings_motion.xml: motion_phase_*, motion_marker_*, motion_view_*). */
enum class MotionText {
    PHASE_PULL, PHASE_HOLD, PHASE_LOWER, PHASE_HANG, PHASE_PAUSE, PHASE_PRESS, PHASE_STAND, PHASE_RESET, PHASE_UP,
    PHASE_DOWN, PHASE_MOVE, PHASE_RETURN, PHASE_HOLD_BREATHE, PHASE_LOWER_SLOW, PHASE_STEP_DOWN, PHASE_STEP_UP,
    PHASE_COME_DOWN,
    MARKER_CHIN, MARKER_RANGE, MARKER_CHEST_TO_BAR, MARKER_CHEST_TO_FIST, MARKER_CHEST_TO_EDGE, MARKER_CHEST_TO_WALL,
    MARKER_SHOULDERS_TO_ELBOWS, MARKER_HIPS_TO_KNEES, MARKER_BACK_KNEE_DOWN, MARKER_KNEES_TO_HIPS, MARKER_FEET_TO_HIPS,
    MARKER_TOES_TO_BAR, MARKER_HIPS_IN_LINE, MARKER_HEAD_AHEAD, MARKER_HEAD_TO_MAT, MARKER_SHOULDERS_DOWN,
    MARKER_HEELS_HIGH, MARKER_HIPS_LEVEL, MARKER_LEGS_LEVEL, MARKER_FEET_OFF, MARKER_STAND_TALL,
    VIEW_SIDE, VIEW_FRONT, VIEW_END,
}

internal class ApparatusPrimitive(
    val type: String,
    val role: String,
    val values: FloatArray,
    val filled: Boolean,
)

/**
 * One body configuration. Angles are absolute degrees: 0 = up, 90 = right, 180 = down.
 * [facing] is +1 when the figure's front is to the right of its torso axis, -1 otherwise.
 */
class Pose {
    var facing = 1f
    var torso = 0f
    var head = Float.NaN // NaN = follow the torso
    var spine = 0f
    var shrug = 0f
    var protract = 0f
    val armNear = floatArrayOf(180f, 180f)
    val armFar = floatArrayOf(180f, 180f)
    val legNear = floatArrayOf(180f, 180f)
    val legFar = floatArrayOf(180f, 180f)
    var footNear = Float.NaN // NaN = neutral ankle
    var footFar = Float.NaN
    var pinJoint = Rig.HIP
    var pinX = 100f
    var pinY = 73f

    fun copyFrom(other: Pose) {
        facing = other.facing
        torso = other.torso
        head = other.head
        spine = other.spine
        shrug = other.shrug
        protract = other.protract
        other.armNear.copyInto(armNear)
        other.armFar.copyInto(armFar)
        other.legNear.copyInto(legNear)
        other.legFar.copyInto(legFar)
        footNear = other.footNear
        footFar = other.footFar
        pinJoint = other.pinJoint
        pinX = other.pinX
        pinY = other.pinY
    }

    fun headAngle(): Float = if (head.isNaN()) torso else head

    fun footAngle(near: Boolean): Float {
        val explicit = if (near) footNear else footFar
        if (!explicit.isNaN()) return explicit
        val shin = if (near) legNear[1] else legFar[1]
        return shin - 90f * facing
    }

    /** Applies JSON fields on top of the current values (used for both poses and keyframe overrides). */
    fun apply(json: JSONObject) {
        if (json.has("facing")) facing = if (json.optDouble("facing", 1.0) >= 0) 1f else -1f
        if (json.has("torso")) torso = json.optDouble("torso", 0.0).toFloat()
        if (json.has("head")) head = if (json.isNull("head")) Float.NaN else json.optDouble("head", 0.0).toFloat()
        if (json.has("spine")) spine = json.optDouble("spine", 0.0).toFloat().coerceIn(-12f, 12f)
        if (json.has("shrug")) shrug = json.optDouble("shrug", 0.0).toFloat().coerceIn(-6f, 6f)
        if (json.has("protract")) protract = json.optDouble("protract", 0.0).toFloat().coerceIn(-6f, 6f)
        json.optJSONArray("arms")?.let { pair -> readPair(pair, armNear); readPair(pair, armFar) }
        json.optJSONArray("legs")?.let { pair -> readPair(pair, legNear); readPair(pair, legFar) }
        json.optJSONArray("arm_near")?.let { readPair(it, armNear) }
        json.optJSONArray("arm_far")?.let { readPair(it, armFar) }
        json.optJSONArray("leg_near")?.let { readPair(it, legNear) }
        json.optJSONArray("leg_far")?.let { readPair(it, legFar) }
        if (json.has("feet")) {
            val value = if (json.isNull("feet")) Float.NaN else json.optDouble("feet", 0.0).toFloat()
            footNear = value
            footFar = value
        }
        if (json.has("foot_near")) footNear = if (json.isNull("foot_near")) Float.NaN else json.optDouble("foot_near", 0.0).toFloat()
        if (json.has("foot_far")) footFar = if (json.isNull("foot_far")) Float.NaN else json.optDouble("foot_far", 0.0).toFloat()
        json.optJSONObject("pin")?.let { pin ->
            pinJoint = Rig.jointIndex(pin.optString("joint", "hip"))
            pin.optJSONArray("at")?.let { at ->
                pinX = at.optDouble(0, 100.0).toFloat()
                pinY = at.optDouble(1, 73.0).toFloat()
            }
        }
        json.optJSONArray("hip")?.let { at ->
            pinJoint = Rig.HIP
            pinX = at.optDouble(0, 100.0).toFloat()
            pinY = at.optDouble(1, 73.0).toFloat()
        }
    }

    private fun readPair(array: JSONArray, target: FloatArray) {
        if (array.length() < 2) return
        target[0] = array.optDouble(0, target[0].toDouble()).toFloat()
        target[1] = array.optDouble(1, target[1].toDouble()).toFloat()
    }
}

/** Fixed bone lengths in canvas units plus the forward-kinematics solver. */
class Rig(
    val headRadius: Float,
    private val neck: Float,
    private val torso: Float,
    private val upperArm: Float,
    private val forearm: Float,
    val thigh: Float,
    val shin: Float,
    val foot: Float,
) {
    /** Writes joint positions (x, y pairs indexed by the joint constants) into [out]. */
    fun solve(pose: Pose, out: FloatArray, hipOverride: FloatArray?) {
        fun set(joint: Int, x: Float, y: Float) {
            out[joint * 2] = x
            out[joint * 2 + 1] = y
        }
        fun step(joint: Int, fromJoint: Int, angle: Float, length: Float) {
            val radians = angle.toDouble() * DEGREES_TO_RADIANS
            set(
                joint,
                out[fromJoint * 2] + sin(radians).toFloat() * length,
                out[fromJoint * 2 + 1] - cos(radians).toFloat() * length,
            )
        }
        set(HIP, 0f, 0f)
        step(NECK, HIP, pose.torso, torso)
        step(HEAD, NECK, pose.headAngle(), neck + headRadius)
        val torsoRadians = pose.torso.toDouble() * DEGREES_TO_RADIANS
        val torsoX = sin(torsoRadians).toFloat()
        val torsoY = -cos(torsoRadians).toFloat()
        val frontX = -torsoY * pose.facing
        val frontY = torsoX * pose.facing
        set(
            SHOULDER,
            out[NECK * 2] + torsoX * pose.shrug + frontX * pose.protract,
            out[NECK * 2 + 1] + torsoY * pose.shrug + frontY * pose.protract,
        )
        step(ELBOW_NEAR, SHOULDER, pose.armNear[0], upperArm)
        step(HAND_NEAR, ELBOW_NEAR, pose.armNear[1], forearm)
        step(ELBOW_FAR, SHOULDER, pose.armFar[0], upperArm)
        step(HAND_FAR, ELBOW_FAR, pose.armFar[1], forearm)
        step(KNEE_NEAR, HIP, pose.legNear[0], thigh)
        step(FOOT_NEAR, KNEE_NEAR, pose.legNear[1], shin)
        step(TOE_NEAR, FOOT_NEAR, pose.footAngle(true), foot)
        step(KNEE_FAR, HIP, pose.legFar[0], thigh)
        step(FOOT_FAR, KNEE_FAR, pose.legFar[1], shin)
        step(TOE_FAR, FOOT_FAR, pose.footAngle(false), foot)
        set(HANDS, (out[HAND_NEAR * 2] + out[HAND_FAR * 2]) * 0.5f, (out[HAND_NEAR * 2 + 1] + out[HAND_FAR * 2 + 1]) * 0.5f)
        set(FEET, (out[FOOT_NEAR * 2] + out[FOOT_FAR * 2]) * 0.5f, (out[FOOT_NEAR * 2 + 1] + out[FOOT_FAR * 2 + 1]) * 0.5f)

        val offsetX: Float
        val offsetY: Float
        if (hipOverride != null) {
            offsetX = hipOverride[0]
            offsetY = hipOverride[1]
        } else {
            val anchor = pose.pinJoint.coerceIn(0, JOINT_COUNT - 1)
            offsetX = pose.pinX - out[anchor * 2]
            offsetY = pose.pinY - out[anchor * 2 + 1]
        }
        for (joint in 0 until JOINT_COUNT) {
            out[joint * 2] += offsetX
            out[joint * 2 + 1] += offsetY
        }
    }

    companion object {
        const val HIP = 0
        const val NECK = 1
        const val HEAD = 2
        const val SHOULDER = 3
        const val ELBOW_NEAR = 4
        const val HAND_NEAR = 5
        const val ELBOW_FAR = 6
        const val HAND_FAR = 7
        const val KNEE_NEAR = 8
        const val FOOT_NEAR = 9
        const val TOE_NEAR = 10
        const val KNEE_FAR = 11
        const val FOOT_FAR = 12
        const val TOE_FAR = 13
        const val HANDS = 14
        const val FEET = 15
        const val JOINT_COUNT = 16

        private val names = listOf(
            "hip", "neck", "head", "shoulder", "elbow_near", "hand_near", "elbow_far", "hand_far",
            "knee_near", "foot_near", "toe_near", "knee_far", "foot_far", "toe_far", "hands", "feet",
        )

        fun jointIndex(name: String): Int = names.indexOf(name).takeIf { it >= 0 } ?: HIP

        fun jointOrNull(name: String): Int? = names.indexOf(name).takeIf { it >= 0 }

        val DEFAULT = Rig(
            headRadius = 4.5f, neck = 3f, torso = 20f, upperArm = 13.5f,
            forearm = 12.5f, thigh = 16f, shin = 15f, foot = 5f,
        )
    }
}

internal class PoseKeyframe(val at: Float, val pose: Pose)

/**
 * Monotone cubic (Fritsch-Butland) interpolation of every pose channel through the keyframes. Joints only come to rest
 * where they reverse or hold, so the figure doesn't stop at every in-between keyframe; with two keyframes the result
 * equals a smoothstep. For "smooth" templates the in-between keyframes of one continuous movement are re-spaced by how
 * far the body travels, so uneven authored timing does not create a slow patch mid-rep.
 */
internal class MotionCurve(
    private val keys: List<PoseKeyframe>,
    retime: Boolean,
    loop: Boolean,
    private val rig: Rig,
) {
    private val count = keys.size
    private val times = FloatArray(count) { keys[it].at }
    private val values = Array(CHANNELS) { FloatArray(count) }
    private val tangents = Array(CHANNELS) { FloatArray(count) }
    private val hipOverride = FloatArray(2)

    init {
        val solved = Array(count) { index -> FloatArray(Rig.JOINT_COUNT * 2).also { rig.solve(keys[index].pose, it, null) } }
        if (retime && count > 2) respace(solved)
        for (k in 0 until count) {
            val pose = keys[k].pose
            val raw = floatArrayOf(
                pose.torso, pose.headAngle(), pose.spine, pose.shrug, pose.protract,
                pose.armNear[0], pose.armNear[1], pose.armFar[0], pose.armFar[1],
                pose.legNear[0], pose.legNear[1], pose.legFar[0], pose.legFar[1],
                pose.footAngle(true), pose.footAngle(false),
                solved[k][Rig.HIP * 2], solved[k][Rig.HIP * 2 + 1],
            )
            for (channel in 0 until CHANNELS) {
                values[channel][k] = if (k > 0 && isAngle(channel)) {
                    values[channel][k - 1] + angleDelta(values[channel][k - 1], raw[channel])
                } else {
                    raw[channel]
                }
            }
        }
        val cyclic = loop && (0 until CHANNELS).all { channel ->
            val first = values[channel][0]
            val last = values[channel][count - 1]
            if (isAngle(channel)) abs(angleDelta(first, last)) < 1f else abs(first - last) < 0.5f
        }
        for (channel in 0 until CHANNELS) fillTangents(values[channel], tangents[channel], cyclic)
    }

    fun evaluate(timeline: Float, out: Pose, joints: FloatArray) {
        var k = count - 2
        for (index in 0 until count - 1) {
            if (timeline <= times[index + 1]) {
                k = index
                break
            }
        }
        val h = (times[k + 1] - times[k]).coerceAtLeast(MIN_SPAN)
        val s = ((timeline - times[k]) / h).coerceIn(0f, 1f)
        val s2 = s * s
        val s3 = s2 * s
        val h00 = 2f * s3 - 3f * s2 + 1f
        val h10 = s3 - 2f * s2 + s
        val h01 = -2f * s3 + 3f * s2
        val h11 = s3 - s2
        fun value(channel: Int): Float {
            val v = values[channel]
            val m = tangents[channel]
            return h00 * v[k] + h10 * h * m[k] + h01 * v[k + 1] + h11 * h * m[k + 1]
        }
        val a = keys[k].pose
        val b = keys[k + 1].pose
        out.torso = value(0)
        out.head = value(1)
        out.spine = value(2)
        out.shrug = value(3)
        out.protract = value(4)
        out.armNear[0] = value(5)
        out.armNear[1] = value(6)
        out.armFar[0] = value(7)
        out.armFar[1] = value(8)
        out.legNear[0] = value(9)
        out.legNear[1] = value(10)
        out.legFar[0] = value(11)
        out.legFar[1] = value(12)
        out.footNear = value(13)
        out.footFar = value(14)
        out.facing = if (s < 0.5f) a.facing else b.facing
        if (a.pinJoint == b.pinJoint) {
            out.pinJoint = a.pinJoint
            out.pinX = lerp(a.pinX, b.pinX, s)
            out.pinY = lerp(a.pinY, b.pinY, s)
            rig.solve(out, joints, null)
        } else {
            hipOverride[0] = value(15)
            hipOverride[1] = value(16)
            rig.solve(out, joints, hipOverride)
        }
    }

    fun keyTime(index: Int): Float = times[index.coerceIn(0, count - 1)]

    /** Within each run bounded by holds, reversals or the ends, place keyframes by distance travelled. */
    private fun respace(solved: Array<FloatArray>) {
        val distance = FloatArray(count - 1) { k ->
            var sum = 0f
            for (joint in 0 until BODY_JOINTS) {
                sum += hypot(solved[k + 1][joint * 2] - solved[k][joint * 2], solved[k + 1][joint * 2 + 1] - solved[k][joint * 2 + 1])
            }
            sum
        }
        fun hold(k: Int) = distance[k] < 0.5f
        fun reversal(k: Int): Boolean {
            var dot = 0f
            var before = 0f
            var after = 0f
            for (i in 0 until BODY_JOINTS * 2) {
                val d0 = solved[k][i] - solved[k - 1][i]
                val d1 = solved[k + 1][i] - solved[k][i]
                dot += d0 * d1
                before += d0 * d0
                after += d1 * d1
            }
            val norm = sqrt(before * after)
            return norm > 0f && dot / norm < -0.3f
        }
        val bounds = ArrayList<Int>().apply { add(0) }
        for (k in 1 until count - 1) if (hold(k - 1) || hold(k) || reversal(k)) bounds.add(k)
        bounds.add(count - 1)
        for (b in 0 until bounds.lastIndex) {
            val i = bounds[b]
            val j = bounds[b + 1]
            if (j - i < 2) continue
            var total = 0f
            for (k in i until j) total += distance[k]
            if (total < 1e-3f) continue
            val t0 = keys[i].at
            val t1 = keys[j].at
            var travelled = 0f
            for (k in i + 1 until j) {
                travelled += distance[k - 1]
                times[k] = t0 + (t1 - t0) * travelled / total
            }
        }
    }

    private fun fillTangents(v: FloatArray, m: FloatArray, cyclic: Boolean) {
        fun span(i: Int) = (times[i + 1] - times[i]).coerceAtLeast(MIN_SPAN)
        fun secant(i: Int) = (v[i + 1] - v[i]) / span(i)
        fun butland(d0: Float, d1: Float, h0: Float, h1: Float): Float =
            if (d0 * d1 <= 0f) 0f else 3f * (h0 + h1) / ((2f * h1 + h0) / d0 + (h1 + 2f * h0) / d1)
        for (i in 1 until count - 1) m[i] = butland(secant(i - 1), secant(i), span(i - 1), span(i))
        if (cyclic && count > 2) {
            val edge = butland(secant(count - 2), secant(0), span(count - 2), span(0))
            m[0] = edge
            m[count - 1] = edge
        }
    }

    private companion object {
        const val CHANNELS = 17
        const val BODY_JOINTS = 14
        const val MIN_SPAN = 0.0001f
        fun isAngle(channel: Int) = channel == 0 || channel == 1 || channel in 5..14
    }
}

/** Phase names for a tempo rep: effort, hold, return, reset. */
internal enum class PhaseLabels(val labels: Array<MotionText>) {
    PULL(arrayOf(MotionText.PHASE_PULL, MotionText.PHASE_HOLD, MotionText.PHASE_LOWER, MotionText.PHASE_HANG)),
    PRESS(arrayOf(MotionText.PHASE_LOWER, MotionText.PHASE_PAUSE, MotionText.PHASE_PRESS, MotionText.PHASE_RESET)),
    SQUAT(arrayOf(MotionText.PHASE_LOWER, MotionText.PHASE_PAUSE, MotionText.PHASE_STAND, MotionText.PHASE_RESET)),
    RISE(arrayOf(MotionText.PHASE_UP, MotionText.PHASE_HOLD, MotionText.PHASE_DOWN, MotionText.PHASE_RESET)),
    SINK(arrayOf(MotionText.PHASE_DOWN, MotionText.PHASE_PAUSE, MotionText.PHASE_UP, MotionText.PHASE_RESET)),
    MOVE(arrayOf(MotionText.PHASE_MOVE, MotionText.PHASE_HOLD, MotionText.PHASE_RETURN, MotionText.PHASE_RESET)),
}

/**
 * A clip's own range line (the template's "marker"), for what the app can't work out from the clip: the [joint] to
 * watch (-1: the worked-out one), where the line sits ([y], NaN: where the joint ends up), its [label] (null: worked
 * out) and the moment of the timeline the joint is measured at ([at]). [off] removes the line, as on stretches.
 */
internal class MarkerSpec(val joint: Int, val y: Float, val label: MotionText?, val at: Float, val off: Boolean)

/** A clip's own phase labels (the template's "phases"): [labels][i] from [times][i] of the timeline on. */
internal class PhaseSpec(val times: FloatArray, val labels: Array<MotionText>) {
    fun indexAt(timeline: Float): Int {
        var index = 0
        for (i in times.indices) if (timeline >= times[i]) index = i
        return index
    }
}

internal class MotionTemplate(
    val name: String,
    val durationMs: Long,
    val mode: String,
    val easing: String,
    val reducedMotionFrame: Float,
    val apparatus: List<String>,
    val keyframes: List<PoseKeyframe>,
    val markerSpec: MarkerSpec? = null,
    val phaseSpec: PhaseSpec? = null,
) {
    lateinit var curve: MotionCurve
        private set

    /** +1 when the body rises from the first to the last keyframe (that half is the effort), -1 when it sinks. */
    var effort = 0
        private set
    var markerJoint = -1
        private set
    var markerX = 0f
        private set
    var markerY = 0f
        private set
    /** Where the marker joint ends up: the line lights up when the joint gets there, wherever the line itself sits. */
    var markerReachY = 0f
        private set
    var phaseLabels = PhaseLabels.MOVE
        private set
    var markerLabel = MotionText.MARKER_RANGE
        private set
    /** No joint travels more than 3 canvas units: a hold. */
    var still = false
        private set

    val usesTempo: Boolean get() = mode == "ping_pong"

    /** Effort 1 count, hold 1/2, return 2 counts (the slow lowering), reset 1/2. */
    val tempoUnits: FloatArray by lazy {
        when {
            effort > 0 -> floatArrayOf(1f, 0.5f, 2f, 0.5f)
            effort < 0 -> floatArrayOf(2f, 0.5f, 1f, 0.5f)
            else -> floatArrayOf(1.5f, 0.5f, 1.5f, 0.5f)
        }
    }

    /** The authored duration covers one even out-and-back; the tempo rep takes 1.6 times as long. */
    val cycleDurationMs: Long get() = if (usesTempo) (durationMs * TEMPO_TOTAL / 2.5f).toLong() else durationMs

    /** Starts the loop at the end of the movement, matching the static reduced-motion frame. */
    val startPhase: Float get() = if (usesTempo) tempoUnits[0] / TEMPO_TOTAL else reducedMotionFrame

    fun prepare(rig: Rig) {
        curve = MotionCurve(keyframes, easing == "smooth", mode == "loop", rig)
        val pose = Pose()
        val start = FloatArray(Rig.JOINT_COUNT * 2)
        val end = FloatArray(Rig.JOINT_COUNT * 2)
        curve.evaluate(0f, pose, start)
        curve.evaluate(1f, pose, end)
        fun bodyY(joints: FloatArray) = (joints[Rig.HEAD * 2 + 1] + joints[Rig.HIP * 2 + 1]) * 0.5f
        effort = when {
            bodyY(end) < bodyY(start) - 3f -> 1
            bodyY(end) > bodyY(start) + 3f -> -1
            else -> 0
        }
        phaseLabels = when {
            effort > 0 && PULL_NAMES.any { name.contains(it) } -> PhaseLabels.PULL
            effort < 0 && PRESS_NAMES.any { name.contains(it) } -> PhaseLabels.PRESS
            effort < 0 && SQUAT_NAMES.any { name.contains(it) } -> PhaseLabels.SQUAT
            effort > 0 -> PhaseLabels.RISE
            effort < 0 -> PhaseLabels.SINK
            else -> PhaseLabels.MOVE
        }
        // A hold keeps one pose all the way through; a workout's tile prefers a clip that moves (2.9.2).
        val sample = FloatArray(Rig.JOINT_COUNT * 2)
        still = floatArrayOf(0.25f, 0.5f, 0.75f, 1f).all { at ->
            curve.evaluate(at, pose, sample)
            sample.indices.all { abs(sample[it] - start[it]) < 3f }
        }
        if (usesTempo) {
            fun travel(joint: Int) = abs(end[joint * 2 + 1] - start[joint * 2 + 1])
            // The joint coaches look at: chin on pulls, shoulders on presses, hips on squats.
            val preferred = when (phaseLabels) {
                PhaseLabels.PULL -> Rig.HEAD
                PhaseLabels.PRESS -> Rig.SHOULDER
                PhaseLabels.SQUAT -> Rig.HIP
                else -> -1
            }
            var best = 0f
            if (preferred >= 0 && travel(preferred) >= 6f) {
                markerJoint = preferred
                best = travel(preferred)
            } else {
                for (joint in MARKER_JOINTS) {
                    if (travel(joint) > best) {
                        best = travel(joint)
                        markerJoint = joint
                    }
                }
            }
            if (best < 6f) {
                markerJoint = -1
            } else {
                markerX = end[markerJoint * 2]
                markerY = end[markerJoint * 2 + 1]
            }
        }
        markerLabel = if (markerJoint == Rig.HEAD && phaseLabels == PhaseLabels.PULL) {
            MotionText.MARKER_CHIN
        } else {
            MotionText.MARKER_RANGE
        }
        markerReachY = markerY
        markerSpec?.let { applyMarker(it, pose) }
    }

    private fun applyMarker(spec: MarkerSpec, pose: Pose) {
        if (spec.off) {
            markerJoint = -1
            return
        }
        if (spec.joint >= 0) markerJoint = spec.joint
        if (markerJoint < 0) return
        val joints = FloatArray(Rig.JOINT_COUNT * 2)
        curve.evaluate(spec.at, pose, joints)
        markerX = joints[markerJoint * 2]
        markerReachY = joints[markerJoint * 2 + 1]
        markerY = if (spec.y.isNaN()) markerReachY else spec.y
        if (spec.label != null) markerLabel = spec.label
    }

    fun timeline(rawPhase: Float): Float = when (mode) {
        "ping_pong" -> if (rawPhase <= 0.5f) rawPhase * 2f else (1f - rawPhase) * 2f
        else -> rawPhase
    }.coerceIn(0f, 1f)

    companion object {
        const val TEMPO_TOTAL = 4f
        private val MARKER_JOINTS = intArrayOf(Rig.HEAD, Rig.HIP, Rig.SHOULDER, Rig.FEET, Rig.HANDS)
        private val PULL_NAMES = listOf("pullup", "chest_to_bar", "chinup", "row")
        private val PRESS_NAMES = listOf("dip", "pushup")
        private val SQUAT_NAMES = listOf("squat", "pistol", "lunge")
    }
}

internal data class MotionReference(
    val templateName: String,
    val apparatus: List<String>?,
)

internal class ResolvedMotion(
    val template: MotionTemplate,
    val apparatus: List<String>,
)

internal class NameHeuristic(
    val tokens: List<String>,
    val reference: MotionReference,
)

/**
 * Every clip, the apparatus they stand on or hold, and how an exercise finds its clip: by movement id, then by exact
 * English alias, name heuristics, equipment and category, and last the fallback. Read once ([parse]), as Android
 * caches it per process.
 */
class AnimationLibrary internal constructor(
    val canvasWidth: Float,
    val canvasHeight: Float,
    val rig: Rig,
    internal val apparatus: Map<String, List<ApparatusPrimitive>>,
    private val templates: Map<String, MotionTemplate>,
    private val movements: Map<String, MotionReference>,
    private val aliases: Map<String, MotionReference>,
    private val categoryDefaults: Map<String, MotionReference>,
    private val equipmentDefaults: Map<String, MotionReference>,
    private val heuristics: List<NameHeuristic>,
    fallbackName: String,
) {
    internal val fallback: ResolvedMotion = resolveReference(MotionReference(fallbackName, null))
        ?: ResolvedMotion(emergencyTemplate().also { it.prepare(rig) }, listOf("floor"))

    internal fun template(name: String): MotionTemplate? = templates[name]

    /** Every clip's name, in the file's order. */
    val templateNames: Set<String> get() = templates.keys

    /** The movement ids that name a clip of their own ("movements" in the file). */
    val movementIds: Set<String> get() = movements.keys

    /** The clip [movementId] names, when the file has one for it and the clip exists. */
    fun clipOf(movementId: String): String? =
        movements[normalize(movementId)]?.templateName?.takeIf { it in templates }

    internal fun resolve(
        movementId: String?,
        exerciseName: String,
        descriptor: String,
        category: String?,
        equipment: Collection<String>,
    ): ResolvedMotion {
        val idKey = normalize(movementId.orEmpty())
        movements[idKey]?.let(::resolveReference)?.let { return it }

        val nameKey = normalize(exerciseName)
        val exactAlias = aliases[nameKey]?.let(::resolveReference)
        if (exactAlias != null && nameKey != "compound_exercise") {
            // A specific descriptor ("Hang - Hanging Shoulder Shrugs", "Push Up - Hand Release")
            // refines the generic alias; otherwise the recovered name maps directly.
            matchHeuristic(normalize(descriptor))?.let { return it }
            return exactAlias
        }
        // Compound exercises carry their real content in the set names, so heuristics go first there.
        matchHeuristic(normalize("$exerciseName $descriptor"))?.let { return it }
        exactAlias?.let { return it }

        equipment.asSequence()
            .map(::normalize)
            .mapNotNull(equipmentDefaults::get)
            .mapNotNull(::resolveReference)
            .firstOrNull()
            ?.let { return it }

        normalize(category.orEmpty()).takeIf(String::isNotEmpty)
            ?.let(categoryDefaults::get)
            ?.let(::resolveReference)
            ?.let { return it }

        return fallback
    }

    /** Whether the clip an exercise shows moves; a hold's stands still (Android: MotionClip.moves). */
    fun moves(movementId: String?, name: String, category: String?, equipment: Collection<String>): Boolean =
        !resolve(movementId, name, "", category, equipment).template.still

    /** Earliest token match in [searchable] wins; ties fall back to heuristic order (most specific first). */
    private fun matchHeuristic(searchable: String): ResolvedMotion? {
        if (searchable.isBlank()) return null
        return heuristics.asSequence()
            .mapIndexedNotNull { priority, heuristic ->
                heuristic.tokens
                    .map(searchable::indexOf)
                    .filter { it >= 0 }
                    .minOrNull()
                    ?.let { matchIndex -> Triple(matchIndex, priority, heuristic.reference) }
            }
            .minWithOrNull(compareBy<Triple<Int, Int, MotionReference>> { it.first }.thenBy { it.second })
            ?.third
            ?.let(::resolveReference)
    }

    private fun resolveReference(reference: MotionReference): ResolvedMotion? {
        val template = templates[reference.templateName] ?: return null
        return ResolvedMotion(template, reference.apparatus ?: template.apparatus)
    }

    companion object {
        /** Reads exercise_animations.json; a file that can't be read gives the one standing figure Android falls back to. */
        fun read(text: String): AnimationLibrary = runCatching { parse(JSONObject(text)) }.getOrElse { fallbackLibrary() }

        internal fun parse(root: JSONObject): AnimationLibrary {
            require(root.optInt("schema_version", 0) == 2) { "Unsupported animation schema" }
            // The JSON's "palette" is for the preview tools; the app draws in the theme's colours.
            val canvas = root.optJSONObject("canvas") ?: JSONObject()
            val segments = root.optJSONObject("figure")?.optJSONObject("segments") ?: JSONObject()
            fun segment(key: String, fallback: Float): Float =
                segments.optDouble(key, fallback.toDouble()).toFloat().coerceIn(0.5f, 60f)
            val rig = Rig(
                headRadius = segment("head_radius", 4.5f),
                neck = segment("neck", 3f),
                torso = segment("torso", 20f),
                upperArm = segment("upper_arm", 13.5f),
                forearm = segment("forearm", 12.5f),
                thigh = segment("thigh", 16f),
                shin = segment("shin", 15f),
                foot = segment("foot", 5f),
            )
            val apparatus = root.optJSONObject("apparatus").mapObject { _, value ->
                (value as? JSONArray).mapObjects { primitive ->
                    ApparatusPrimitive(
                        type = primitive.optString("type", "line"),
                        role = primitive.optString("role", "apparatus"),
                        values = primitive.optJSONArray("values").floatArray(),
                        filled = primitive.optBoolean("filled", false),
                    )
                }
            }
            val posesJson = root.optJSONObject("poses") ?: JSONObject()
            val poses = HashMap<String, Pose>()
            fun poseNamed(name: String, stack: Set<String>): Pose? {
                poses[name]?.let { return it }
                val json = posesJson.optJSONObject(name) ?: return null
                if (name in stack) return null
                val pose = Pose()
                json.optString("base").takeIf(String::isNotBlank)?.let { baseName ->
                    poseNamed(baseName, stack + name)?.let(pose::copyFrom)
                }
                pose.apply(json)
                poses[name] = pose
                return pose
            }
            val poseKeys = posesJson.keys()
            while (poseKeys.hasNext()) poseNamed(poseKeys.next(), emptySet())

            val templates = root.optJSONObject("templates").mapObjectNotNull { name, value ->
                val json = value as? JSONObject ?: return@mapObjectNotNull null
                val keyframes = json.optJSONArray("keyframes").mapObjectsNotNull { frame ->
                    val pose = Pose()
                    val named = frame.optString("pose").takeIf(String::isNotBlank)?.let { poses[it] }
                    if (named != null) pose.copyFrom(named) else if (frame.has("pose")) return@mapObjectsNotNull null
                    pose.apply(frame)
                    PoseKeyframe(frame.optDouble("at", 0.0).toFloat().coerceIn(0f, 1f), pose)
                }.sortedBy(PoseKeyframe::at)
                if (keyframes.isEmpty()) return@mapObjectNotNull null
                name to MotionTemplate(
                    name = name,
                    durationMs = json.optLong("duration_ms", 1_600L).coerceIn(500L, 8_000L),
                    mode = json.optString("mode", "ping_pong"),
                    easing = json.optString("easing", "smooth"),
                    reducedMotionFrame = json.optDouble("reduced_motion_frame", 0.5).toFloat().coerceIn(0f, 1f),
                    apparatus = json.optJSONArray("apparatus").strings(),
                    keyframes = if (keyframes.size == 1) listOf(
                        keyframes.single(),
                        PoseKeyframe(1f, keyframes.single().pose),
                    ) else keyframes,
                    markerSpec = markerSpec(json.opt("marker")),
                    phaseSpec = phaseSpec(json.opt("phases")),
                )
            }
            require(templates.isNotEmpty()) { "No animation templates" }
            templates.values.forEach { it.prepare(rig) }
            return AnimationLibrary(
                canvasWidth = canvas.optDouble("width", 200.0).toFloat().coerceIn(50f, 1000f),
                canvasHeight = canvas.optDouble("height", 120.0).toFloat().coerceIn(30f, 1000f),
                rig = rig,
                apparatus = apparatus,
                templates = templates,
                movements = root.optJSONObject("movements").references(),
                aliases = root.optJSONObject("aliases").references(normalizeKeys = true),
                categoryDefaults = root.optJSONObject("category_defaults").references(normalizeKeys = true),
                equipmentDefaults = root.optJSONObject("equipment_defaults").references(normalizeKeys = true),
                heuristics = root.optJSONArray("name_heuristics").mapObjectsNotNull { item ->
                    val reference = item.opt("animation").asReference() ?: return@mapObjectsNotNull null
                    NameHeuristic(item.optJSONArray("contains").strings().map(::normalize), reference)
                }.filter { it.tokens.isNotEmpty() },
                fallbackName = root.optString("fallback", templates.keys.first()),
            )
        }

        /** A template's "marker": false for none, or an object with any of joint, y, label and at. */
        private fun markerSpec(value: Any?): MarkerSpec? = when (value) {
            false -> MarkerSpec(-1, Float.NaN, null, 1f, off = true)
            is JSONObject -> MarkerSpec(
                joint = value.optString("joint").takeIf(String::isNotBlank)?.let { Rig.jointOrNull(it) } ?: -1,
                y = value.optDouble("y", Double.NaN).toFloat(),
                label = MARKER_LABELS[value.optString("label")],
                at = value.optDouble("at", 1.0).toFloat().coerceIn(0f, 1f),
                off = false,
            )
            else -> null
        }

        /** A template's "phases": one label name for the whole clip, or [at, label] pairs along a loop. */
        private fun phaseSpec(value: Any?): PhaseSpec? = when (value) {
            is String -> PHASE_LABELS[value]?.let { PhaseSpec(floatArrayOf(0f), arrayOf(it)) }
            is JSONArray -> {
                val pairs = (0 until value.length()).mapNotNull { index ->
                    val pair = value.optJSONArray(index) ?: return@mapNotNull null
                    val label = PHASE_LABELS[pair.optString(1)] ?: return@mapNotNull null
                    pair.optDouble(0, 0.0).toFloat() to label
                }.sortedBy { it.first }
                if (pairs.isEmpty()) null
                else PhaseSpec(FloatArray(pairs.size) { pairs[it].first }, Array(pairs.size) { pairs[it].second })
            }
            else -> null
        }

        private val MARKER_LABELS = mapOf(
            "chin_over_bar" to MotionText.MARKER_CHIN, "full_range" to MotionText.MARKER_RANGE,
            "chest_to_bar" to MotionText.MARKER_CHEST_TO_BAR, "chest_to_fist" to MotionText.MARKER_CHEST_TO_FIST,
            "chest_to_edge" to MotionText.MARKER_CHEST_TO_EDGE, "chest_to_wall" to MotionText.MARKER_CHEST_TO_WALL,
            "shoulders_to_elbows" to MotionText.MARKER_SHOULDERS_TO_ELBOWS,
            "hips_to_knees" to MotionText.MARKER_HIPS_TO_KNEES, "back_knee_down" to MotionText.MARKER_BACK_KNEE_DOWN,
            "knees_to_hips" to MotionText.MARKER_KNEES_TO_HIPS, "feet_to_hips" to MotionText.MARKER_FEET_TO_HIPS,
            "toes_to_bar" to MotionText.MARKER_TOES_TO_BAR, "hips_in_line" to MotionText.MARKER_HIPS_IN_LINE,
            "head_ahead" to MotionText.MARKER_HEAD_AHEAD, "head_to_mat" to MotionText.MARKER_HEAD_TO_MAT,
            "shoulders_down" to MotionText.MARKER_SHOULDERS_DOWN, "heels_high" to MotionText.MARKER_HEELS_HIGH,
            "hips_level" to MotionText.MARKER_HIPS_LEVEL, "legs_level" to MotionText.MARKER_LEGS_LEVEL,
            "feet_off" to MotionText.MARKER_FEET_OFF, "stand_tall" to MotionText.MARKER_STAND_TALL,
        )

        private val PHASE_LABELS = mapOf(
            "hold" to MotionText.PHASE_HOLD_BREATHE, "lower_slow" to MotionText.PHASE_LOWER_SLOW,
            "step_down" to MotionText.PHASE_STEP_DOWN, "step_up" to MotionText.PHASE_STEP_UP,
            "come_down" to MotionText.PHASE_COME_DOWN, "hang" to MotionText.PHASE_HANG,
            "pull" to MotionText.PHASE_PULL, "press" to MotionText.PHASE_PRESS,
        )

        /** The one standing figure Android draws when the file can't be read. */
        fun fallbackLibrary(): AnimationLibrary {
            val template = emergencyTemplate().also { it.prepare(Rig.DEFAULT) }
            return AnimationLibrary(
                canvasWidth = 200f,
                canvasHeight = 120f,
                rig = Rig.DEFAULT,
                apparatus = mapOf(
                    "floor" to listOf(
                        ApparatusPrimitive("line", "floor", floatArrayOf(8f, 106f, 192f, 106f), false),
                    ),
                ),
                templates = mapOf(template.name to template),
                movements = emptyMap(),
                aliases = emptyMap(),
                categoryDefaults = emptyMap(),
                equipmentDefaults = emptyMap(),
                heuristics = emptyList(),
                fallbackName = template.name,
            )
        }

        private fun emergencyTemplate(): MotionTemplate {
            val stand = Pose().apply {
                pinJoint = Rig.FOOT_NEAR
                pinX = 100f
                pinY = 104f
            }
            val reach = Pose().apply {
                copyFrom(stand)
                armNear[0] = 90f
                armNear[1] = 90f
                armFar[0] = 90f
                armFar[1] = 90f
            }
            return MotionTemplate(
                name = "standing_mobility",
                durationMs = 2_000L,
                mode = "ping_pong",
                easing = "smooth",
                reducedMotionFrame = 0.5f,
                apparatus = listOf("floor"),
                keyframes = listOf(PoseKeyframe(0f, stand), PoseKeyframe(1f, reach)),
            )
        }
    }
}

private val NOT_ALPHANUMERIC = Regex("[^a-z0-9]+")

/** Lower case (locale-free in Kotlin, as Android's Locale.ENGLISH), runs of anything else as one underscore. */
internal fun normalize(value: String): String = value
    .lowercase()
    .replace(NOT_ALPHANUMERIC, "_")
    .trim('_')

private fun JSONObject?.references(normalizeKeys: Boolean = false): Map<String, MotionReference> =
    mapObjectNotNull { key, value ->
        value.asReference()?.let { (if (normalizeKeys) normalize(key) else key) to it }
    }

private fun Any?.asReference(): MotionReference? = when (this) {
    is String -> takeIf(String::isNotBlank)?.let { MotionReference(it, null) }
    is JSONObject -> optString("template").takeIf(String::isNotBlank)?.let { name ->
        MotionReference(
            templateName = name,
            apparatus = optJSONArray("apparatus")?.strings(),
        )
    }
    else -> null
}

private inline fun <T> JSONObject?.mapObject(transform: (String, Any?) -> T): Map<String, T> {
    if (this == null) return emptyMap()
    return buildMap {
        val iterator = keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            put(key, transform(key, opt(key)))
        }
    }
}

private inline fun <T> JSONObject?.mapObjectNotNull(transform: (String, Any?) -> Pair<String, T>?): Map<String, T> {
    if (this == null) return emptyMap()
    return buildMap {
        val iterator = keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            transform(key, opt(key))?.let { (mappedKey, value) -> put(mappedKey, value) }
        }
    }
}

private inline fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return buildList(length()) {
        for (index in 0 until length()) optJSONObject(index)?.let { add(transform(it)) }
    }
}

private inline fun <T : Any> JSONArray?.mapObjectsNotNull(transform: (JSONObject) -> T?): List<T> {
    if (this == null) return emptyList()
    return buildList(length()) {
        for (index in 0 until length()) optJSONObject(index)?.let(transform)?.let(::add)
    }
}

private fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    return buildList(length()) {
        for (index in 0 until length()) optString(index).takeIf(String::isNotBlank)?.let(::add)
    }
}

private fun JSONArray?.floatArray(): FloatArray {
    if (this == null) return FloatArray(0)
    return FloatArray(length()) { index -> optDouble(index, 0.0).toFloat() }
}
