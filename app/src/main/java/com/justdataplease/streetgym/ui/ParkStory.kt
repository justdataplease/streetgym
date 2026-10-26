package com.justdataplease.streetgym.ui

import com.justdataplease.streetgym.R
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A movement the Today hero can train; [label] names it in the scene's description. Finales end on a held skill, so
 * the reduced-motion frame and the idle show that skill.
 */
internal enum class ParkMove(val label: Int) {
    SQUATS(R.string.park_move_squats),
    DIPS(R.string.park_move_dips),
    KNEE_RAISES(R.string.park_move_knee_raises),
    PULL_UPS(R.string.park_move_pull_ups),
    PISTOL_SQUATS(R.string.park_move_pistol_squats),
    MUSCLE_UP(R.string.park_move_muscle_up),
    HANDSTAND(R.string.park_move_handstand),
    TUCK_LEVER(R.string.park_move_tuck_lever),
    FRONT_LEVER(R.string.park_move_front_lever),
    HUMAN_FLAG(R.string.park_move_human_flag),
}

/**
 * One day's Today hero routine: three different movements, easiest first. A fundamental opens ([opener]); then a
 * strength move ([middle]) leads into a skill held to the end ([finale]) on the same apparatus: pistol squats into a
 * handstand, or pull-ups or a muscle-up into a lever or a human flag on that bar. [forEpochDay] picks the day's.
 */
internal data class ParkRoutine(val opener: ParkMove, val middle: ParkMove, val finale: ParkMove) {
    val moves: List<ParkMove> get() = listOf(opener, middle, finale)

    companion object {
        val OPENERS = listOf(ParkMove.SQUATS, ParkMove.DIPS, ParkMove.KNEE_RAISES)
        val FINALES = listOf(ParkMove.HANDSTAND, ParkMove.TUCK_LEVER, ParkMove.FRONT_LEVER, ParkMove.HUMAN_FLAG)
        private val BAR_MIDDLES = listOf(ParkMove.PULL_UPS, ParkMove.MUSCLE_UP)

        /** The strength moves that can lead into [finale]. */
        fun middlesFor(finale: ParkMove): List<ParkMove> =
            if (finale == ParkMove.HANDSTAND) listOf(ParkMove.PISTOL_SQUATS) else BAR_MIDDLES

        /** Every routine the generator can make. */
        val all: List<ParkRoutine> = OPENERS.flatMap { opener ->
            FINALES.flatMap { finale -> middlesFor(finale).map { middle -> ParkRoutine(opener, middle, finale) } }
        }

        /**
         * The routine for local day [epochDay] (days since 1970-01-01). Every aligned block of as many days as there
         * are routines plays each routine once, in an order seeded by the block. No day repeats a movement of the day
         * before, and no routine comes back within a week, across block edges too.
         */
        fun forEpochDay(epochDay: Long): ParkRoutine {
            val size = all.size.toLong()
            return blockOrder(epochDay.floorDiv(size))[epochDay.mod(size).toInt()]
        }

        /** The local calendar day of [millis] in [zone], as days since 1970-01-01; it turns at local midnight. */
        fun localEpochDay(millis: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()): Long =
            (millis + zone.getOffset(millis)).floorDiv(DAY_MILLIS)

        /** Whether [b] can follow [a]: all three movements differ. */
        fun differs(a: ParkRoutine, b: ParkRoutine): Boolean =
            a.opener != b.opener && a.middle != b.middle && a.finale != b.finale

        @Volatile
        private var cached: Pair<Long, List<ParkRoutine>>? = null

        /**
         * One block's order. Its last [TAIL] days come from the block's seed alone ([tail]), so a block knows how the
         * one before ends without working out that block's order: it starts apart from that tail's last routine and
         * keeps the tail's routines out of its first [TAIL] days.
         */
        private fun blockOrder(block: Long): List<ParkRoutine> {
            cached?.let { if (it.first == block) return it.second }
            val before = tail(block - 1)
            val tail = tail(block)
            val length = all.size - TAIL
            val body = chain(length, tail.toSet(), ORDER_SEED + block * all.size) { index, previous, next ->
                differs(previous ?: before.last(), next) &&
                    (index >= TAIL || next !in before) &&
                    (index < length - 1 || differs(next, tail.first()))
            }
            val order = checkNotNull(body) { "no park routine order for block $block" } + tail
            cached = block to order
            return order
        }

        private fun tail(block: Long): List<ParkRoutine> =
            checkNotNull(chain(TAIL, emptySet(), TAIL_SEED + block * all.size) { _, previous, next -> previous == null || differs(previous, next) })

        /**
         * A depth-first chain of [length] routines outside [taken], each [allowed] at its index after the one before
         * (null for the first). Tries the routines with the fewest onward options first (Warnsdorff's rule), in an
         * order seeded by [seed] among equals.
         */
        private fun chain(
            length: Int,
            taken: Set<ParkRoutine>,
            seed: Long,
            allowed: (Int, ParkRoutine?, ParkRoutine) -> Boolean,
        ): List<ParkRoutine>? {
            val rank = all.withIndex().associate { (i, routine) -> routine to splitMix(seed + i) }
            val used = HashSet(taken)
            val path = ArrayList<ParkRoutine>(length)
            fun onward(from: ParkRoutine) = all.count { it !in used && differs(from, it) }
            fun extend(): Boolean {
                if (path.size == length) return true
                val previous = path.lastOrNull()
                val options = all.filter { it !in used && allowed(path.size, previous, it) }
                    .sortedWith(compareBy({ onward(it) }, { rank.getValue(it) }))
                for (next in options) {
                    used += next
                    path += next
                    if (extend()) return true
                    used -= next
                    path.removeAt(path.lastIndex)
                }
                return false
            }
            return if (extend()) path else null
        }

        private fun splitMix(seed: Long): Long {
            var z = seed + GOLDEN_GAMMA
            z = (z xor (z ushr 30)) * MIX_1
            z = (z xor (z ushr 27)) * MIX_2
            return z xor (z ushr 31)
        }

        private const val DAY_MILLIS = 86_400_000L

        /** Days at the end of a block that the next block keeps out of its first days. */
        private const val TAIL = 6

        // Far apart, so the two seeds never meet.
        private const val TAIL_SEED = 1L shl 40
        private const val ORDER_SEED = 2L shl 40

        // SplitMix64's constants 0x9E3779B97F4A7C15, 0xBF58476D1CE4E5B9 and 0x94D049BB133111EB as signed longs.
        private const val GOLDEN_GAMMA = -7046029254386353131L
        private const val MIX_1 = -4658895280553007687L
        private const val MIX_2 = -7723592293110705685L
    }
}

/** Park furniture in scene units: a bar at height [y] whose posts stand at [left] and [right]. */
internal class ParkProp(val kind: Int, val y: Float, val left: Float, val right: Float) {
    companion object {
        /** A bar on two posts; the bar overhangs each post by [BAR_OVERHANG]. */
        const val BAR = 0

        /** Parallel bars from the side (the near bar hides the far one); overhang [PARALLEL_OVERHANG]. */
        const val PARALLEL_BARS = 1

        const val BAR_OVERHANG = 3f
        const val PARALLEL_OVERHANG = 5f
    }
}

/**
 * One day's routine as a timeline in scene units (floor at y = 106; the authored clips are centred on x = 100): the
 * furniture it uses, where the friends stand, the hero's beats and the friends' cheers. Android-free, so unit tests
 * can play it frame by frame.
 *
 * Consecutive movements on the same apparatus share one [Spot] (knee raises, pull-ups and a lever on one bar), so the
 * hero only walks between apparatus. The spots are spread from left to right; when they need more room than the
 * 200 units the hero card shows at full size, [width] grows and the view zooms out.
 *
 * The hero's beats are walks (planned footstep by footstep), authored clips placed on the furniture, and
 * angle-space blends between the end of one beat and the start of the next. A zero-length beat only anchors a
 * blend. The last beat holds the finale; after [end] the hero breathes in that hold.
 */
internal class ParkStory(private val rig: Rig, val routine: ParkRoutine, clip: (String) -> MotionClip?) {
    /** Furniture this routine uses, drawn behind the figures (the tree is always there). */
    val props = ArrayList<ParkProp>()

    /** Where the three friends stand, and which way each faces (+1 right). */
    val friendX = floatArrayOf(FRIEND_LEFT_X, 152f, 178f)
    val friendFacing = floatArrayOf(1f, -1f, -1f)

    /** The scene's width in scene units: [MIN_WIDTH], or more when the day's spots need it. */
    var width = MIN_WIDTH
        private set

    /** Where the hero trains, left to right. */
    val spots = ArrayList<Spot>()

    /** When the hero reaches the final hold; set by [plan]. */
    var end = 0f
        private set

    /** False when an authored clip is missing: the hero then only walks in. */
    val ready: Boolean

    private val clips = HashMap<String, MotionClip>()
    private val beats = ArrayList<Beat>()
    private val cheers = ArrayList<Cheer>()
    private var standX = 0f
    private var idleSway = 0f
    private val probe = Pose()
    private val probeJoints = FloatArray(Rig.JOINT_COUNT * 2)
    private var friendHipDrop = 0f
    private var clapFlash = 0f

    init {
        val needed = clipNames(routine)
        for (name in needed) clip(name)?.let { clips[name] = it }
        ready = clips.size == needed.size
        if (ready) {
            layOut()
        } else {
            walkTo(MIN_WIDTH / 2f)
            hold(0.5f)
        }
        for (index in beats.indices) {
            val beat = beats[index]
            if (beat is Blend) {
                beat.from = beats[index - 1]
                beat.to = beats[index + 1]
            }
        }
    }

    /** Lays the beats on the clock. [leftEdge] is the leftmost visible x; the walk-in starts beyond it. */
    fun plan(leftEdge: Float) {
        val entry = min(-16f, leftEdge - 14f)
        var t = 0f
        for (beat in beats) {
            beat.start = t
            if (beat is Walk) beat.plan(if (beat.from.isNaN()) entry else beat.from, t)
            t += beat.seconds
        }
        end = t
        for (cheer in cheers) cheer.at = beats[cheer.beat].end - cheer.lead
        for (spot in spots) {
            spot.start = beats[spot.first].start
            spot.end = beats[spot.last].end
        }
    }

    /** The hero at story time [t]; from [end] on, the final hold breathing. */
    fun poseHero(t: Float, out: Pose, joints: FloatArray) {
        if (t >= end) {
            poseIdle(t - end, out, joints)
        } else {
            var index = 0
            while (index < beats.lastIndex && (beats[index].seconds <= 0f || t >= beats[index].end)) index++
            val beat = beats[index]
            val s = if (beat.seconds > 0f) ((t - beat.start) / beat.seconds).coerceIn(0f, 1f) else 1f
            beat.pose(s, out, joints)
        }
        keepFeetOnFloor(rig, out, joints)
        keepToesUp(rig, out, joints)
    }

    private fun poseIdle(idle: Float, out: Pose, joints: FloatArray) {
        val last = beats.last()
        last.pose(1f, out, joints)
        val phase = TAU * idle / HERO_BREATH
        val breath = sin(phase)
        out.spine += 0.3f * (1f - cos(phase))
        val contact = last.contact
        if (contact == Rig.HAND_NEAR || contact == Rig.HAND_FAR) {
            // Hanging or pressing on the hands, the breath lifts the body a little and a held balance sways.
            out.shrug += 0.35f * breath
            val sway = idleSway * sin(2f * phase)
            out.torso += 0.4f * sway
            for (i in 0..1) {
                out.legNear[i] += sway
                out.legFar[i] += sway
            }
        }
        out.pinJoint = contact
        out.pinX = joints[contact * 2]
        out.pinY = joints[contact * 2 + 1]
        rig.solve(out, joints, null)
    }

    /**
     * One place the hero trains: the hero walks to [x], and it and the furniture stay between [left] and [right]
     * from [start] (after the walk) to [end].
     */
    class Spot internal constructor(val x: Float, val left: Float, val right: Float, internal val first: Int, internal val last: Int) {
        var start = 0f
            internal set
        var end = 0f
            internal set
    }

    // ---- the generated routine

    /** Groups the movements into spots, spreads them across the scene and places the friends around them. */
    private fun layOut() {
        val groups = ArrayList<ArrayList<ParkMove>>()
        for (move in routine.moves) {
            val group = groups.lastOrNull()
            if (group != null && apparatusOf(group.last()) == apparatusOf(move)) group += move else groups += arrayListOf(move)
        }
        val lefts = FloatArray(groups.size) { i -> groups[i].minOf { reach(it, apparatusOf(it)).start } }
        val rights = FloatArray(groups.size) { i -> groups[i].maxOf { reach(it, apparatusOf(it)).endInclusive } }
        var used = SPOT_GAP * (groups.size - 1)
        for (i in groups.indices) used += rights[i] - lefts[i]
        // Spare room at full size goes evenly before, between and after the spots.
        val spare = max(0f, MIN_WIDTH - STAGE_LEFT - FRIENDS_ROOM - used) / (groups.size + 1)
        var left = STAGE_LEFT + spare
        for ((i, group) in groups.withIndex()) {
            val x = left - lefts[i]
            train(group, x, lefts[i], rights[i])
            left = x + rights[i] + SPOT_GAP + spare
        }
        val friend = left - SPOT_GAP + FRIEND_GAP
        friends(FRIEND_LEFT_X, 1f, friend, -1f, friend + FRIEND_SPACING, -1f)
        width = max(MIN_WIDTH, friendX[2] + FRIEND_MARGIN)
    }

    private fun apparatusOf(move: ParkMove): Int = apparatus(move, routine.middle)

    /** Walks to [x] and trains [moves] there on their shared apparatus. */
    private fun train(moves: List<ParkMove>, x: Float, left: Float, right: Float) {
        val apparatus = apparatusOf(moves[0])
        when (apparatus) {
            ON_HIGH_BAR, ON_MID_BAR -> {
                // A bar skill needs room right of the hands: the lever's body, or the flag on the left post.
                var barRight = if (apparatus == ON_HIGH_BAR) 18f else 30f
                for (move in moves) barRight = max(barRight, barReach(move, apparatus))
                val y = if (apparatus == ON_HIGH_BAR) HIGH_BAR_Y else MID_BAR_Y
                props += ParkProp(ParkProp.BAR, y, x + barLeft(apparatus), x + barRight)
            }
            ON_PARALLEL_BARS -> props += ParkProp(ParkProp.PARALLEL_BARS, DIP_BAR_Y, x - 16f, x + 16f)
        }
        if (beats.isNotEmpty()) blend(0.4f, Rig.FOOT_NEAR)
        walkTo(x)
        val first = beats.size
        when (apparatus) {
            ON_HIGH_BAR -> jumpToHang(x)
            ON_PARALLEL_BARS -> jumpToSupport(x)
            ON_MID_BAR -> {
                blend(0.45f, Rig.FOOT_NEAR)
                key(clips.getValue(MUSCLE_UP), GRIP_TIME, x, Rig.HAND_NEAR, y = MUSCLE_UP_DY)
            }
        }
        for (move in moves) perform(move, x, apparatus)
        if (moves.last() != routine.finale) dismount(x, apparatus)
        spots += Spot(x, x + left, x + right, first, beats.lastIndex)
    }

    /** From the apparatus' resting state at [x] back to standing on the floor. */
    private fun dismount(x: Float, apparatus: Int) {
        when (apparatus) {
            ON_HIGH_BAR -> {
                blend(0.25f, Rig.HAND_NEAR)
                dropFromBar(x)
            }
            ON_PARALLEL_BARS -> {
                blend(0.34f, Rig.HIP, EASE_IN)
                still(Rig.FOOT_NEAR) { pose, joints -> landPose(x, pose, joints) }
            }
            // The muscle-up rests in its opening grip, already standing.
            ON_MID_BAR -> standX = clipJointX(clips.getValue(MUSCLE_UP), GRIP_TIME, x, MUSCLE_UP_DY, Rig.FOOT_NEAR)
        }
    }

    /**
     * One movement at [x], from its apparatus' resting state (standing, a dead hang, support or the muscle-up's
     * standing grip) back to it; a finale ends on its held skill instead.
     */
    private fun perform(move: ParkMove, x: Float, apparatus: Int) {
        val kinds = if (move == routine.opener) OPENER_CHEER else MIDDLE_CHEER
        when (move) {
            ParkMove.SQUATS -> {
                blend(0.25f, Rig.FOOT_NEAR)
                play(clips.getValue(SQUAT), x, 3.2f, Rig.FOOT_NEAR) { press(repPhase(it, 2)) }
                cheer(kinds)
            }
            ParkMove.PISTOL_SQUATS -> {
                blend(0.45f, Rig.FOOT_NEAR)
                play(clips.getValue(PISTOL), x, 3.4f, Rig.FOOT_NEAR) { press(repPhase(it, 2)) }
                cheer(kinds)
            }
            ParkMove.DIPS -> {
                play(clips.getValue(DIP), x, 2f, Rig.HAND_NEAR) { press(repPhase(it, 2)) }
                cheer(kinds)
            }
            ParkMove.KNEE_RAISES -> {
                blend(0.25f, Rig.HAND_NEAR)
                play(clips.getValue(KNEE_RAISE), x, 2.6f, Rig.HAND_NEAR) { pull(repPhase(it, 2)) }
                cheer(kinds)
            }
            ParkMove.PULL_UPS -> {
                blend(0.25f, Rig.HAND_NEAR)
                play(clips.getValue(PULL_UP), x, 2f * PULL_REP, Rig.HAND_NEAR) { pull(repPhase(it, 2)) }
                cheer(kinds)
            }
            ParkMove.MUSCLE_UP -> {
                val muscleUp = clips.getValue(MUSCLE_UP)
                play(muscleUp, x, 1.6f, Rig.HAND_NEAR, y = MUSCLE_UP_DY) { GRIP_TIME + (SUPPORT_TIME - GRIP_TIME) * it }
                cheer(kinds)
                hold(0.3f)
                play(clips.getValue(NEGATIVE_MUSCLE_UP), x, 1.2f, Rig.HAND_NEAR, y = MUSCLE_UP_DY) { 0.75f * it }
                blend(0.35f, Rig.HAND_NEAR)
                key(muscleUp, GRIP_TIME, x, Rig.HAND_NEAR, y = MUSCLE_UP_DY)
            }
            ParkMove.HANDSTAND -> {
                kickUp(x)
                hold(1.3f)
                idleSway = 1.2f
            }
            ParkMove.TUCK_LEVER, ParkMove.FRONT_LEVER -> {
                // The lever clips hang from the high bar (y = 12); on the mid bar they come down with it.
                val y = if (apparatus == ON_MID_BAR) MID_BAR_Y - HIGH_BAR_Y else 0f
                blend(0.8f, Rig.HAND_NEAR)
                key(clips.getValue(TUCK_LEVER), 0f, x, Rig.HAND_NEAR, y = y)
                if (move == ParkMove.FRONT_LEVER) {
                    hold(0.5f)
                    blend(0.7f, Rig.HAND_NEAR)
                    key(clips.getValue(FULL_LEVER), 0f, x, Rig.HAND_NEAR, y = y)
                }
                hold(1.3f)
            }
            ParkMove.HUMAN_FLAG -> {
                // Down from the bar, then along under it to the left post: the flag clips grip a post 38 left of
                // the clip origin and stand on the floor, so only x moves.
                dismount(x, apparatus)
                val origin = x + barLeft(apparatus) + 38f
                blend(0.4f, Rig.FOOT_NEAR)
                walkTo(origin - 1f)
                blend(0.5f, Rig.FOOT_NEAR)
                key(clips.getValue(FLAG_SUPPORT), 0f, origin, Rig.FOOT_NEAR)
                // The straddle clip opens from a tuck, then the full flag closes the legs.
                blend(0.6f, Rig.HAND_FAR)
                play(clips.getValue(STRADDLE_FLAG), origin, 0.8f, Rig.HAND_FAR) { it }
                play(clips.getValue(FULL_FLAG), origin, 0.6f, Rig.HAND_FAR) { 0.55f * it }
                hold(1.3f)
            }
        }
        if (move == routine.finale) cheer(finaleCheer(move), lead = FINALE_LEAD)
    }

    // ---- building blocks

    private fun friends(x0: Float, f0: Float, x1: Float, f1: Float, x2: Float, f2: Float) {
        friendX[0] = x0
        friendX[1] = x1
        friendX[2] = x2
        friendFacing[0] = f0
        friendFacing[1] = f1
        friendFacing[2] = f2
    }

    /** Walks from where the hero stands (the first walk comes in from off-screen) to [x]. */
    private fun walkTo(x: Float) {
        beats += Walk(if (beats.isEmpty()) Float.NaN else standX, x)
        standX = x
    }

    private fun blend(seconds: Float, pin: Int, ease: Int = SMOOTH, swing: Int = -1, lift: Float = 0f) {
        beats += Blend(seconds, pin, ease, swing, lift)
    }

    private fun play(
        clip: MotionClip,
        x: Float,
        seconds: Float,
        contact: Int,
        y: Float = 0f,
        mirror: Boolean = false,
        sameArms: Boolean = false,
        timeline: Timeline,
    ) {
        beats += Play(clip, x, y, mirror, sameArms, contact, seconds, timeline)
    }

    /** A single clip frame that anchors the blends around it. */
    private fun key(clip: MotionClip, timeline: Float, x: Float, contact: Int, y: Float = 0f, mirror: Boolean = false) =
        play(clip, x, 0f, contact, y, mirror) { timeline }

    private fun still(contact: Int, body: (Pose, FloatArray) -> Unit) {
        beats += Custom(0f, contact) { _, pose, joints -> body(pose, joints) }
    }

    /** Keeps the previous beat's last pose for [seconds]. */
    private fun hold(seconds: Float) {
        val previous = beats.last()
        beats += Custom(seconds, previous.contact) { _, pose, joints -> previous.pose(1f, pose, joints) }
    }

    /** Friends react as the last beat ends: one kind per friend ([NONE] sits it out), slightly staggered. */
    private fun cheer(kinds: IntArray, lead: Float = 0.15f) {
        val delays = if (cheers.size % 2 == 0) FIRST_DELAYS else SECOND_DELAYS
        cheers += Cheer(beats.lastIndex, lead, kinds, delays)
    }

    /** From standing at [x], jumps into support on the parallel bars. */
    private fun jumpToSupport(x: Float) {
        val jump = clips.getValue(JUMP_TO_HANG)
        blend(0.34f, Rig.FOOT_NEAR)
        key(jump, jump.keyTime(1), x, Rig.FOOT_NEAR)
        blend(0.32f, Rig.HIP, EASE_OUT)
        key(clips.getValue(DIP), 0f, x, Rig.HAND_NEAR)
    }

    /** From standing at [x], jumps to a dead hang on the high bar. */
    private fun jumpToHang(x: Float) {
        val jump = clips.getValue(JUMP_TO_HANG)
        val crouch = jump.keyTime(1)
        val hang = jump.keyTime(4)
        blend(0.34f, Rig.FOOT_NEAR)
        play(jump, x, 1.05f, Rig.HAND_NEAR) { crouch + (hang - crouch) * it }
    }

    /** Lets go of the bar at [x], lands softly and lowers the arms. */
    private fun dropFromBar(x: Float) {
        val jump = clips.getValue(JUMP_TO_HANG)
        val hang = jump.keyTime(4)
        val land = jump.keyTime(5)
        beats += Custom(0.55f, Rig.HIP) { s, pose, joints ->
            place(jump, hang + (land - hang) * s, x, 0f, false, Rig.HIP, pose, joints)
            landingStance(pose, joints, smooth(s))
        }
        val landed = Pose()
        val landedJoints = FloatArray(Rig.JOINT_COUNT * 2)
        val stand = Pose()
        val standJoints = FloatArray(Rig.JOINT_COUNT * 2)
        beats += Custom(0.5f, Rig.FOOT_NEAR) { s, pose, joints ->
            // Arms come down with soft elbows while the knees absorb the landing.
            place(jump, land, x, 0f, false, Rig.HIP, landed, landedJoints)
            landingStance(landed, landedJoints, 1f)
            standPose(x, stand)
            rig.solve(stand, standJoints, null)
            val w = smooth(s)
            crossfade(landed, landedJoints, stand, standJoints, w, Rig.FOOT_NEAR, pose, joints)
            val relax = 70f * sin(PI_F * w)
            pose.armNear[1] += relax
            pose.armFar[1] += relax
            rig.solve(pose, joints, null)
            val give = sin(PI_F * min(1f, s * 1.25f))
            bendKnees(pose, joints, 3.2f * give * give)
        }
        standX = x
    }

    /** From standing at [x], steps the near foot forward and kicks up to a freestanding handstand. */
    private fun kickUp(x: Float) {
        val origin = x + KICK_STANCE
        blend(0.5f, Rig.FOOT_FAR, swing = Rig.FOOT_NEAR, lift = 4f)
        play(clips.getValue(KICK_UP), origin, 1.3f, Rig.HAND_NEAR, sameArms = true) {
            KICK_START + (KICK_END - KICK_START) * it
        }
        blend(0.35f, Rig.HAND_NEAR)
        // The balance clip faces the wall; mirrored, its chest faces the way the kick-up arrives.
        key(clips.getValue(HANDSTAND), 0f, origin + 2f * KICK_HAND_DX, Rig.HAND_NEAR, mirror = true)
    }

    private fun clipJointX(clip: MotionClip, timeline: Float, x: Float, y: Float, joint: Int): Float {
        place(clip, timeline, x, y, false, joint, probe, probeJoints)
        return probeJoints[joint * 2]
    }

    // ---- beats

    /** A stretch of the timeline; [pose] writes the hero at fraction s of it. [contact] stays put on re-solves. */
    private abstract inner class Beat(var seconds: Float, val contact: Int) {
        var start = 0f
        val end: Float get() = start + seconds

        abstract fun pose(s: Float, out: Pose, joints: FloatArray)
    }

    private inner class Walk(val from: Float, private val to: Float) : Beat(0f, Rig.FOOT_NEAR) {
        private val gait = Gait(rig)

        fun plan(from: Float, at: Float) {
            gait.plan(from, to, at)
            seconds = gait.end - at
        }

        override fun pose(s: Float, out: Pose, joints: FloatArray) = gait.evaluate(start + s * seconds, out, joints)
    }

    /** Plays [clip] along [timeline]; [sameArms] lays the far arm on the near one (both hands share a spot in side view). */
    private inner class Play(
        private val clip: MotionClip,
        private val x: Float,
        private val y: Float,
        private val mirror: Boolean,
        private val sameArms: Boolean,
        contact: Int,
        seconds: Float,
        private val timeline: Timeline,
    ) : Beat(seconds, contact) {
        override fun pose(s: Float, out: Pose, joints: FloatArray) {
            place(clip, timeline.at(s), x, y, mirror, contact, out, joints)
            if (!sameArms) return
            out.armFar[0] = out.armNear[0]
            out.armFar[1] = out.armNear[1]
            rig.solve(out, joints, null)
        }
    }

    /**
     * Crossfades the previous beat's last pose into the next beat's first; the [contact] joint travels straight.
     * A [swing] foot is lifted clear of the floor on the way, so a stepping foot never drags.
     */
    private inner class Blend(
        seconds: Float,
        pin: Int,
        private val ease: Int,
        private val swing: Int,
        private val lift: Float,
    ) : Beat(seconds, pin) {
        lateinit var from: Beat
        lateinit var to: Beat
        private val a = Pose()
        private val jointsA = FloatArray(Rig.JOINT_COUNT * 2)
        private val b = Pose()
        private val jointsB = FloatArray(Rig.JOINT_COUNT * 2)

        override fun pose(s: Float, out: Pose, joints: FloatArray) {
            from.pose(1f, a, jointsA)
            to.pose(0f, b, jointsB)
            val w = when (ease) {
                EASE_OUT -> easeOut(s)
                EASE_IN -> s * s
                else -> smooth(s)
            }
            crossfade(a, jointsA, b, jointsB, w, contact, out, joints)
            if (swing < 0) return
            val ankleY = min(joints[swing * 2 + 1], ANKLE_Y) - lift * sin(PI_F * w)
            val leg = if (swing == Rig.FOOT_NEAR) out.legNear else out.legFar
            legIk(rig, joints[Rig.HIP * 2], joints[Rig.HIP * 2 + 1], joints[swing * 2], ankleY, leg)
            out.pinJoint = contact
            out.pinX = joints[contact * 2]
            out.pinY = joints[contact * 2 + 1]
            rig.solve(out, joints, null)
        }
    }

    private inner class Custom(seconds: Float, contact: Int, private val body: Posing) : Beat(seconds, contact) {
        override fun pose(s: Float, out: Pose, joints: FloatArray) = body.pose(s, out, joints)
    }

    /** A beat's progress (0..1) to a clip timeline. Primitive, so the per-frame call does not box. */
    private fun interface Timeline {
        fun at(s: Float): Float
    }

    /** Writes the hero at a beat's progress (0..1). */
    private fun interface Posing {
        fun pose(s: Float, out: Pose, joints: FloatArray)
    }

    /** One reaction moment: friend i plays kinds[i] starting delays[i] after [at]. */
    private class Cheer(val beat: Int, val lead: Float, val kinds: IntArray, val delays: FloatArray) {
        var at = 0f
    }

    // ---- posing helpers

    /**
     * Samples [clip] and moves its origin (x = 100 in clip units) to [x] and down by [y]; [mirror] turns it to face
     * the other way. The pose is pinned at [contact], so a later re-solve keeps that joint where it is.
     */
    private fun place(
        clip: MotionClip,
        timeline: Float,
        x: Float,
        y: Float,
        mirror: Boolean,
        contact: Int,
        pose: Pose,
        joints: FloatArray,
    ) {
        clip.sample(timeline, pose, joints)
        if (mirror) mirror(pose, joints)
        val dx = x - CLIP_X
        for (joint in 0 until Rig.JOINT_COUNT) {
            joints[joint * 2] += dx
            joints[joint * 2 + 1] += y
        }
        pose.pinJoint = contact
        pose.pinX = joints[contact * 2]
        pose.pinY = joints[contact * 2 + 1]
    }

    /** Reflects a solved pose about x = 100: every absolute angle and the facing change sign. */
    private fun mirror(pose: Pose, joints: FloatArray) {
        val footNear = pose.footAngle(true)
        val footFar = pose.footAngle(false)
        pose.facing = -pose.facing
        pose.torso = -pose.torso
        if (!pose.head.isNaN()) pose.head = -pose.head
        for (i in 0..1) {
            pose.armNear[i] = -pose.armNear[i]
            pose.armFar[i] = -pose.armFar[i]
            pose.legNear[i] = -pose.legNear[i]
            pose.legFar[i] = -pose.legFar[i]
        }
        pose.footNear = -footNear
        pose.footFar = -footFar
        for (joint in 0 until Rig.JOINT_COUNT) joints[joint * 2] = 2f * CLIP_X - joints[joint * 2]
    }

    /** Crossfades [a] into [b] in angle space; the [pin] joint travels straight between them. */
    private fun crossfade(a: Pose, jointsA: FloatArray, b: Pose, jointsB: FloatArray, w: Float, pin: Int, out: Pose, joints: FloatArray) {
        out.facing = if (w < 0.5f) a.facing else b.facing
        out.torso = mixAngle(a.torso, b.torso, w)
        out.head = mixAngle(a.headAngle(), b.headAngle(), w)
        out.spine = mix(a.spine, b.spine, w)
        out.shrug = mix(a.shrug, b.shrug, w)
        out.protract = mix(a.protract, b.protract, w)
        for (i in 0..1) {
            out.armNear[i] = mixAngle(a.armNear[i], b.armNear[i], w)
            out.armFar[i] = mixAngle(a.armFar[i], b.armFar[i], w)
            out.legNear[i] = mixAngle(a.legNear[i], b.legNear[i], w)
            out.legFar[i] = mixAngle(a.legFar[i], b.legFar[i], w)
        }
        out.footNear = mixAngle(a.footAngle(true), b.footAngle(true), w)
        out.footFar = mixAngle(a.footAngle(false), b.footAngle(false), w)
        out.pinJoint = pin
        out.pinX = mix(jointsA[pin * 2], jointsB[pin * 2], w)
        out.pinY = mix(jointsA[pin * 2 + 1], jointsB[pin * 2 + 1], w)
        rig.solve(out, joints, null)
    }

    private fun standPose(x: Float, pose: Pose) {
        pose.facing = 1f
        pose.torso = 0f
        pose.head = 0f
        pose.spine = 0f
        pose.shrug = 0f
        pose.protract = 0f
        pose.armNear[0] = 180f
        pose.armNear[1] = 180f
        pose.armFar[0] = 180f
        pose.armFar[1] = 180f
        pose.legNear[0] = 180f
        pose.legNear[1] = 180f
        // Straight and a touch back, unlike the clips' slightly hyperextended far knee, so knee-forward
        // IK takes it over without a snap.
        pose.legFar[0] = STAND_FAR_LEG
        pose.legFar[1] = STAND_FAR_LEG
        pose.footNear = 90f
        pose.footFar = 90f
        pose.pinJoint = Rig.FOOT_NEAR
        pose.pinX = x
        pose.pinY = ANKLE_Y
    }

    /** Eases the far leg into the standing stance while the feet are still in the air. */
    private fun landingStance(pose: Pose, joints: FloatArray, w: Float) {
        pose.legFar[0] = mixAngle(pose.legFar[0], STAND_FAR_LEG, w)
        pose.legFar[1] = mixAngle(pose.legFar[1], STAND_FAR_LEG, w)
        pose.pinJoint = Rig.HIP
        pose.pinX = joints[Rig.HIP * 2]
        pose.pinY = joints[Rig.HIP * 2 + 1]
        rig.solve(pose, joints, null)
    }

    private fun landPose(x: Float, pose: Pose, joints: FloatArray) {
        standPose(x, pose)
        rig.solve(pose, joints, null)
        pose.armNear[0] = 140f
        pose.armNear[1] = 112f
        pose.armFar[0] = 136f
        pose.armFar[1] = 106f
        bendKnees(pose, joints, 6.5f)
    }

    /** Lowers the hip by [drop] while both feet stay where they are (knees forward). */
    private fun bendKnees(pose: Pose, joints: FloatArray, drop: Float) {
        val hipX = joints[Rig.HIP * 2]
        val hipY = joints[Rig.HIP * 2 + 1] + drop
        pose.footNear = pose.footAngle(true)
        pose.footFar = pose.footAngle(false)
        legIk(rig, hipX, hipY, joints[Rig.FOOT_NEAR * 2], joints[Rig.FOOT_NEAR * 2 + 1], pose.legNear)
        legIk(rig, hipX, hipY, joints[Rig.FOOT_FAR * 2], joints[Rig.FOOT_FAR * 2 + 1], pose.legFar)
        pose.torso += drop * 1.4f
        pose.pinJoint = Rig.HIP
        pose.pinX = hipX
        pose.pinY = hipY
        rig.solve(pose, joints, null)
    }

    // ---- friends

    /**
     * Friend [index] at story time [t] in local units (feet at x = 100), authored facing right and mirrored when
     * drawn. Returns how bright the clap flash between the hands is (0 when not clapping).
     */
    fun poseFriend(index: Int, t: Float, p: Pose, joints: FloatArray): Float {
        val breath = sin(TAU * (t / FRIEND_BREATH + FRIEND_PHASE[index]))
        p.facing = 1f
        p.torso = 0.6f * breath
        p.head = 0f
        p.spine = 0.5f + 0.3f * breath
        p.shrug = 0.3f * breath
        p.protract = 0f
        p.armNear[0] = 176f
        p.armNear[1] = 168f
        p.armFar[0] = 183f
        p.armFar[1] = 176f
        friendHipDrop = 0f
        clapFlash = 0f
        if (ready) {
            if (index == 0) react(p, WAVE, t - HELLO_AT)
            // Three friends a frame: an index loop, not an iterator.
            for (i in cheers.indices) {
                val moment = cheers[i]
                react(p, moment.kinds[index], t - moment.at - moment.delays[index])
            }
        }
        if (t >= end) {
            val idle = t - end - IDLE_WAVE_FIRST
            if (idle >= 0f) {
                val turn = floor(idle / IDLE_WAVE_EVERY).toInt()
                if (IDLE_WAVE_ORDER[turn % IDLE_WAVE_ORDER.size] == index) react(p, WAVE, idle - turn * IDLE_WAVE_EVERY)
            }
        }
        // Friends stand with soft knees; when a hop lifts the hip past that, the feet leave the ground.
        val hipY = ANKLE_Y - FRIEND_LEG + friendHipDrop
        val airborne = max(0f, -friendHipDrop)
        val ankleY = min(ANKLE_Y, hipY + FRIEND_LEG)
        legIk(rig, CLIP_X, hipY, CLIP_X + 0.4f, ankleY, p.legNear)
        legIk(rig, CLIP_X, hipY, CLIP_X - 0.4f, ankleY, p.legFar)
        p.footNear = 90f + min(airborne, 4f) * 9f
        p.footFar = p.footNear - 3f
        p.pinJoint = Rig.HIP
        p.pinX = CLIP_X
        p.pinY = hipY
        rig.solve(p, joints, null)
        keepToesUp(rig, p, joints)
        return clapFlash
    }

    /** Layers one reaction, [elapsed] seconds after it starts, onto the friend's pose. */
    private fun react(p: Pose, kind: Int, elapsed: Float) {
        if (elapsed < 0f) return
        when (kind) {
            ARMS_UP -> if (elapsed < 1.5f) {
                val w = smooth(elapsed / 0.25f) * (1f - smooth((elapsed - 1.1f) / 0.4f))
                val pump = 8f * sin(TAU * ((elapsed - 0.25f) / 0.8f).coerceIn(0f, 1f) * 2f)
                armTo(p.armNear, 22f + pump, 12f + pump, w)
                armTo(p.armFar, 345f - pump, 338f - pump, w)
            }
            HOP -> if (elapsed < 1.3f) {
                val w = smooth(elapsed / 0.2f) * (1f - smooth((elapsed - 1f) / 0.3f))
                armTo(p.armNear, 16f, 6f, w)
                armTo(p.armFar, 4f, 354f, w)
                friendHipDrop += hop((elapsed - 0.1f) / 0.45f) + hop((elapsed - 0.55f) / 0.45f)
            }
            CLAP -> if (elapsed < 1.5f) {
                val w = smooth(elapsed / 0.25f) * (1f - smooth((elapsed - 1.15f) / 0.35f))
                val open = 0.5f - 0.5f * cos(TAU * ((elapsed - 0.25f) / 0.9f).coerceIn(0f, 1f) * 3f)
                armTo(p.armNear, 128f, 62f - 26f * open, w)
                armTo(p.armFar, 122f, 70f + 26f * open, w)
                if (elapsed > 0.25f && elapsed < 1.15f && open < 0.12f) clapFlash = (1f - open / 0.12f) * w
            }
            WAVE -> if (elapsed < 1.9f) {
                val w = smooth(elapsed / 0.3f) * (1f - smooth((elapsed - 1.5f) / 0.4f))
                val wave = 16f * sin(TAU * ((elapsed - 0.3f) / 1.2f).coerceIn(0f, 1f) * 3f)
                armTo(p.armNear, 32f, 8f + wave, w)
            }
            JUMP -> if (elapsed < 1.9f) {
                // Both arms up in a V and three hops: the finale.
                val w = smooth(elapsed / 0.2f) * (1f - smooth((elapsed - 1.55f) / 0.35f))
                val pump = 6f * sin(TAU * ((elapsed - 0.2f) / 1.35f).coerceIn(0f, 1f) * 3f)
                armTo(p.armNear, 24f + pump, 14f + pump, w)
                armTo(p.armFar, 343f - pump, 336f - pump, w)
                friendHipDrop += hop((elapsed - 0.1f) / 0.45f) + hop((elapsed - 0.55f) / 0.45f) + hop((elapsed - 1f) / 0.45f)
            }
        }
    }

    /** Hip offset of one small hop at [q] (0..1): dip, flight, landing. */
    private fun hop(q: Float): Float = when {
        q < 0f || q >= 1f -> 0f
        q < 0.22f -> 2.2f * smooth(q / 0.22f)
        q < 0.78f -> {
            val f = (q - 0.22f) / 0.56f
            2.2f * (1f - smooth(f / 0.15f)) - 24f * f * (1f - f)
        }
        else -> 2.2f * sin(PI_F * (q - 0.78f) / 0.22f)
    }

    companion object {
        const val FRIENDS = 3
        const val FLOOR_Y = 106f

        /** The idle loops with this period (a multiple of both breaths and the wave turn)... */
        const val IDLE_CYCLE = 21f

        /** ...wrapping this long after [end], once the last cheer has faded and before the first idle wave. */
        const val IDLE_WRAP_AT = 2f

        const val NONE = -1
        const val ARMS_UP = 0
        const val HOP = 1
        const val CLAP = 2
        const val WAVE = 3
        const val JUMP = 4

        // Template ids in exercise_animations.json.
        private const val SQUAT = "squat"
        private const val JUMP_TO_HANG = "jump_to_hang"
        private const val DIP = "dip"
        private const val PULL_UP = "pullup"
        private const val KNEE_RAISE = "hanging_knee_raise"
        private const val PISTOL = "pistol"
        private const val MUSCLE_UP = "muscleup"
        private const val NEGATIVE_MUSCLE_UP = "negative_muscleup"
        private const val TUCK_LEVER = "front_lever_tuck"
        private const val FULL_LEVER = "front_lever_full"
        private const val FLAG_SUPPORT = "flag_ground_support"
        private const val STRADDLE_FLAG = "straddle_flag"
        private const val FULL_FLAG = "full_flag"
        private const val KICK_UP = "handstand_exit"
        private const val HANDSTAND = "handstand_balance"

        /** The authored clips [routine] needs. */
        fun clipNames(routine: ParkRoutine): Set<String> = routine.moves.flatMapTo(LinkedHashSet()) { clipNames(it).asList() }

        private fun clipNames(move: ParkMove): Array<String> = when (move) {
            ParkMove.SQUATS -> arrayOf(SQUAT)
            ParkMove.DIPS -> arrayOf(JUMP_TO_HANG, DIP)
            ParkMove.KNEE_RAISES -> arrayOf(JUMP_TO_HANG, KNEE_RAISE)
            ParkMove.PULL_UPS -> arrayOf(JUMP_TO_HANG, PULL_UP)
            ParkMove.PISTOL_SQUATS -> arrayOf(PISTOL)
            ParkMove.MUSCLE_UP -> arrayOf(MUSCLE_UP, NEGATIVE_MUSCLE_UP)
            ParkMove.HANDSTAND -> arrayOf(KICK_UP, HANDSTAND)
            ParkMove.TUCK_LEVER -> arrayOf(TUCK_LEVER)
            ParkMove.FRONT_LEVER -> arrayOf(TUCK_LEVER, FULL_LEVER)
            ParkMove.HUMAN_FLAG -> arrayOf(FLAG_SUPPORT, STRADDLE_FLAG, FULL_FLAG)
        }

        // Apparatus. Consecutive movements on the same one share a spot.
        private const val ON_FLOOR = 0
        private const val ON_HIGH_BAR = 1
        private const val ON_PARALLEL_BARS = 2
        private const val ON_MID_BAR = 3

        /** Where [move] trains; the bar skills take the bar of the [middle] movement before them. */
        private fun apparatus(move: ParkMove, middle: ParkMove): Int = when (move) {
            ParkMove.SQUATS, ParkMove.PISTOL_SQUATS, ParkMove.HANDSTAND -> ON_FLOOR
            ParkMove.KNEE_RAISES, ParkMove.PULL_UPS -> ON_HIGH_BAR
            ParkMove.DIPS -> ON_PARALLEL_BARS
            ParkMove.MUSCLE_UP -> ON_MID_BAR
            ParkMove.TUCK_LEVER, ParkMove.FRONT_LEVER, ParkMove.HUMAN_FLAG -> apparatus(middle, middle)
        }

        /** The bar's left end, relative to the hands. */
        private fun barLeft(apparatus: Int) = if (apparatus == ON_HIGH_BAR) -18f else -16f

        /** How far right of the hands the bar must run for [move]: past a lever's feet, or the flag's. */
        private fun barReach(move: ParkMove, apparatus: Int): Float = when (move) {
            ParkMove.TUCK_LEVER -> 30f
            ParkMove.FRONT_LEVER -> 62f
            ParkMove.HUMAN_FLAG -> barLeft(apparatus) + FLAG_BAR_LENGTH
            else -> 0f
        }

        /** The x range, relative to where the hero walks to, that [move] and its furniture use; ParkStoryTest checks it. */
        private fun reach(move: ParkMove, apparatus: Int): ClosedFloatingPointRange<Float> = when (move) {
            ParkMove.SQUATS -> -10f..28f
            ParkMove.PISTOL_SQUATS -> -10f..34f
            ParkMove.HANDSTAND -> -8f..66f
            // The landings' arms swing forward past the furniture.
            ParkMove.KNEE_RAISES, ParkMove.PULL_UPS -> -22f..25f
            ParkMove.DIPS -> -22f..25f
            ParkMove.MUSCLE_UP -> -20f..34f
            ParkMove.TUCK_LEVER, ParkMove.FRONT_LEVER, ParkMove.HUMAN_FLAG ->
                (barLeft(apparatus) - ParkProp.BAR_OVERHANG - 1f)..(barReach(move, apparatus) + ParkProp.BAR_OVERHANG + 1f)
        }

        private val OPENER_CHEER = intArrayOf(CLAP, ARMS_UP, HOP)
        private val MIDDLE_CHEER = intArrayOf(HOP, CLAP, ARMS_UP)

        /** The hardest finales get the friends jumping. */
        private fun finaleCheer(move: ParkMove): IntArray = when (move) {
            ParkMove.HANDSTAND -> intArrayOf(ARMS_UP, HOP, CLAP)
            ParkMove.TUCK_LEVER -> intArrayOf(HOP, CLAP, ARMS_UP)
            else -> intArrayOf(JUMP, JUMP, JUMP)
        }

        /** The hero card shows at least x = 0..200 at full size. */
        const val MIN_WIDTH = 200f

        // Layout, in scene units. Spots start right of the tree and the first friend; two friends stand after them.
        private const val STAGE_LEFT = 34f
        private const val SPOT_GAP = 6f
        private const val FRIEND_LEFT_X = 24f
        private const val FRIEND_GAP = 11f
        private const val FRIEND_SPACING = 15f
        private const val FRIEND_MARGIN = 8f
        private const val FRIENDS_ROOM = FRIEND_GAP + FRIEND_SPACING + FRIEND_MARGIN
        private const val DIP_BAR_Y = 50f
        private const val HIGH_BAR_Y = 12f

        /** A bar with the flag on its left post runs this long, so the flag's feet stay clear of the right post. */
        private const val FLAG_BAR_LENGTH = 78f

        /**
         * The muscle-up clips hang from a bar at y = 40 with the feet below the floor. Raised to y = 30 the
         * opening hang becomes a standing grip, and the support at the top still clears the scene's top edge.
         */
        private const val MID_BAR_Y = 30f
        private const val MUSCLE_UP_DY = MID_BAR_Y - 40f
        private const val GRIP_TIME = 0.1f
        private const val SUPPORT_TIME = 0.8f

        // The kick-up is handstand_exit's second half: a lunge reaching forward (feet 14 either side of the clip
        // origin), then hands down 45 ahead and legs over.
        private const val KICK_START = 0.633f
        private const val KICK_END = 0.88f
        private const val KICK_STANCE = 14f
        private const val KICK_HAND_DX = 45f

        /** The last cheer peaks as the story ends, so the final (reduced-motion) frame shows the friends celebrating. */
        private const val FINALE_LEAD = 0.7f
        private const val PULL_REP = 1.25f

        private const val SMOOTH = 0
        private const val EASE_OUT = 1
        private const val EASE_IN = 2

        private val FIRST_DELAYS = floatArrayOf(0f, 0.12f, 0.05f)
        private val SECOND_DELAYS = floatArrayOf(0.1f, 0f, 0.18f)
        private val FRIEND_PHASE = floatArrayOf(0f, 0.37f, 0.71f)
        private val IDLE_WAVE_ORDER = intArrayOf(1, 2, 0)
        private const val HELLO_AT = 0.35f
        private const val IDLE_WAVE_FIRST = 2.5f
        private const val IDLE_WAVE_EVERY = 7f
        private const val HERO_BREATH = 5.25f
        private const val FRIEND_BREATH = 4.2f

        fun repPhase(s: Float, reps: Int): Float = if (s < 1f) (s * reps) % 1f else 0f

        /** Lower, pause, press, lock out: dips, squats, push-ups. */
        fun press(r: Float): Float = when {
            r < 0.42f -> r / 0.42f
            r < 0.52f -> 1f
            r < 0.85f -> 1f - (r - 0.52f) / 0.33f
            else -> 0f
        }

        /** Pull, hold the top, lower, dead hang: pull-ups and raises. */
        fun pull(r: Float): Float = when {
            r < 0.36f -> r / 0.36f
            r < 0.48f -> 1f
            r < 0.88f -> 1f - (r - 0.48f) / 0.4f
            else -> 0f
        }
    }
}

/**
 * A walk between two standing positions. Footfalls are planned first, the hip follows a monotone spline
 * over them and leg IK reaches each ankle, so a planted foot never slides. Each foot rolls heel to toe.
 */
internal class Gait(private val rig: Rig) {
    var start = 0f
        private set
    var end = 0f
        private set
    var stride = 0f
        private set
    private var from = 0f
    private var count = 0
    private val lift = FloatArray(MAX_STEPS)
    private val contact = FloatArray(MAX_STEPS)
    private val target = FloatArray(MAX_STEPS)
    private val hip = Spline(MAX_STEPS)
    private val feet = FloatArray(8) // per foot: ankle x, ankle y, foot angle, planted

    fun plan(from: Float, to: Float, at: Float) {
        this.from = from
        start = at
        val intervals = ceil((to - from) / STEP_LENGTH - 0.2f).toInt().coerceIn(1, MAX_STEPS - 1)
        stride = (to - from) / intervals
        count = intervals + 1
        for (i in 0 until count) {
            contact[i] = at + (SWING + i) * STEP_TIME
            lift[i] = contact[i] - SWING * STEP_TIME
            target[i] = from + min(i + 1, intervals) * stride
        }
        end = contact[count - 1] + 0.35f * STEP_TIME
        hip.clear()
        hip.add(at, from)
        for (i in 0 until count - 2) hip.add(contact[i] + 0.6f * STEP_TIME, target[i])
        hip.add(end, to)
        hip.build()
    }

    /** A footfall inside the steady part of a long walk; two steps from here loop seamlessly. */
    fun loopStart(): Float = contact[LOOP_STEP]

    fun evaluate(time: Float, pose: Pose, joints: FloatArray) {
        val hipX = hip.value(time)
        val speed = (hip.slope(time) * STEP_TIME / stride).coerceIn(0f, 1f)
        var last = -1
        for (i in 0 until count) if (contact[i] <= time) last = i
        val phase = (time - if (last >= 0) contact[last] else contact[0] - STEP_TIME) / STEP_TIME
        // The hip is lowest in double support and highest over the stance foot. Squaring the speed lets
        // the knees straighten gradually when the walk stops (near straight, IK angles move fastest).
        val sway = speed * speed
        var hipY = STAND_HIP_Y + sway * (1.3f - 0.8f * cos(TAU * (phase - 0.6f)))
        foot(0, time, 0)
        foot(1, time, 4)
        // Each ankle caps how high the hip may ride, so a leg never over-reaches; while walking the
        // knees keep a little bend. Both ankles move continuously, so the cap does too.
        val reach = rig.thigh + rig.shin - 0.3f * sway
        hipY = max(hipY, max(lowestHip(0, hipX, reach), lowestHip(4, hipX, reach)))
        pose.facing = 1f
        pose.torso = 3f * speed
        pose.head = speed
        pose.spine = 0.6f * speed
        pose.shrug = 0f
        pose.protract = 0f
        legIk(rig, hipX, hipY, feet[0], feet[1], pose.legNear)
        legIk(rig, hipX, hipY, feet[4], feet[5], pose.legFar)
        pose.footNear = feet[2]
        pose.footFar = feet[6]
        // Arms swing against the legs and settle as the feet come together.
        val swing = ((feet[4] - feet[0]) / stride).coerceIn(-1f, 1f)
        pose.armNear[0] = 180f - 22f * swing
        pose.armNear[1] = pose.armNear[0] - 8f * speed - 18f * max(swing, 0f)
        pose.armFar[0] = 180f + 22f * swing
        pose.armFar[1] = pose.armFar[0] - 8f * speed - 18f * max(-swing, 0f)
        pose.pinJoint = Rig.HIP
        pose.pinX = hipX
        pose.pinY = hipY
        rig.solve(pose, joints, null)
    }

    private fun lowestHip(o: Int, hipX: Float, reach: Float): Float {
        val dx = feet[o] - hipX
        return feet[o + 1] - sqrt(max(reach * reach - dx * dx, 0f))
    }

    private fun foot(side: Int, time: Float, o: Int) {
        var x = from
        var since = Float.NaN
        var i = side
        while (i < count) {
            if (time < lift[i]) {
                planted(x, since, lift[i], i >= 1, time, o)
                return
            }
            if (time < contact[i]) {
                planted(x, since, lift[i], i >= 1, lift[i], o)
                val sx = feet[o]
                val sy = feet[o + 1]
                val sa = feet[o + 2]
                val s = (time - lift[i]) / (contact[i] - lift[i])
                // A first step leaves a straight leg, so it lifts gently; later ones leave from the toe.
                val arc = sin(PI_F * s)
                feet[o] = sx + (target[i] - sx) * smooth(s)
                feet[o + 1] = sy + (ANKLE_Y - sy) * s - LIFT * if (i >= 1) arc else arc * arc
                feet[o + 2] = sa + (HEEL_STRIKE_ANGLE - sa) * smooth(s * 1.35f)
                feet[o + 3] = 0f
                return
            }
            x = target[i]
            since = contact[i]
            i += 2
        }
        planted(x, since, Float.NaN, false, time, o)
    }

    /** A stance foot: heel strike, flat, then a toe roll before it lifts. */
    private fun planted(x: Float, since: Float, nextLift: Float, roll: Boolean, time: Float, o: Int) {
        var angle = 90f
        if (!since.isNaN() && time - since < HEEL_STRIKE * STEP_TIME) {
            angle = HEEL_STRIKE_ANGLE + (90f - HEEL_STRIKE_ANGLE) * smooth((time - since) / (HEEL_STRIKE * STEP_TIME))
        }
        feet[o] = x
        feet[o + 1] = ANKLE_Y
        feet[o + 3] = 1f
        if (roll && !nextLift.isNaN() && nextLift - time < HEEL_OFF * STEP_TIME) {
            angle = 90f + 38f * smooth(1f - (nextLift - time) / (HEEL_OFF * STEP_TIME))
            val radians = Math.toRadians(angle.toDouble())
            feet[o] = x + rig.foot - sin(radians).toFloat() * rig.foot
            feet[o + 1] = ANKLE_Y + cos(radians).toFloat() * rig.foot
        }
        feet[o + 2] = angle
    }

    companion object {
        const val STEP_TIME = 0.46f
        const val STEP_LENGTH = 19f
        private const val MAX_STEPS = 48
        private const val LOOP_STEP = 6
        private const val SWING = 0.8f // of a step: each foot swings for 40 % of the stride
        private const val HEEL_OFF = 0.3f
        private const val HEEL_STRIKE = 0.14f
        private const val HEEL_STRIKE_ANGLE = 72f
        private const val LIFT = 2.6f
    }
}

/** Monotone cubic (Fritsch-Butland) through time/value keys, flat at both ends. */
private class Spline(capacity: Int) {
    private val times = FloatArray(capacity)
    private val values = FloatArray(capacity)
    private val slopes = FloatArray(capacity)
    private var count = 0

    fun clear() {
        count = 0
    }

    fun add(time: Float, value: Float) {
        times[count] = time
        values[count] = value
        count++
    }

    fun build() {
        for (i in 0 until count) slopes[i] = 0f
        for (i in 1 until count - 1) {
            val h0 = times[i] - times[i - 1]
            val h1 = times[i + 1] - times[i]
            val d0 = (values[i] - values[i - 1]) / h0
            val d1 = (values[i + 1] - values[i]) / h1
            slopes[i] = if (d0 * d1 <= 0f) 0f else 3f * (h0 + h1) / ((2f * h1 + h0) / d0 + (h1 + 2f * h0) / d1)
        }
    }

    fun value(x: Float): Float = evaluate(x, false)

    fun slope(x: Float): Float = evaluate(x, true)

    private fun evaluate(x: Float, derivative: Boolean): Float {
        if (x <= times[0]) return if (derivative) 0f else values[0]
        if (x >= times[count - 1]) return if (derivative) 0f else values[count - 1]
        var k = 0
        while (x > times[k + 1]) k++
        val h = times[k + 1] - times[k]
        val s = (x - times[k]) / h
        val s2 = s * s
        val v0 = values[k]
        val v1 = values[k + 1]
        val m0 = slopes[k] * h
        val m1 = slopes[k + 1] * h
        if (derivative) {
            return ((6f * s2 - 6f * s) * v0 + (3f * s2 - 4f * s + 1f) * m0 + (6f * s - 6f * s2) * v1 + (3f * s2 - 2f * s) * m1) / h
        }
        val s3 = s2 * s
        return (2f * s3 - 3f * s2 + 1f) * v0 + (s3 - 2f * s2 + s) * m0 + (3f * s2 - 2f * s3) * v1 + (s3 - s2) * m1
    }
}

/** A few clips sink an ankle a fraction below the floor; bend that leg so the ankle rests on it. */
private fun keepFeetOnFloor(rig: Rig, pose: Pose, joints: FloatArray) {
    val near = joints[Rig.FOOT_NEAR * 2 + 1] > ANKLE_Y
    val far = joints[Rig.FOOT_FAR * 2 + 1] > ANKLE_Y
    if (!near && !far) return
    val hipX = joints[Rig.HIP * 2]
    val hipY = joints[Rig.HIP * 2 + 1]
    pose.footNear = pose.footAngle(true)
    pose.footFar = pose.footAngle(false)
    if (near) legIk(rig, hipX, hipY, joints[Rig.FOOT_NEAR * 2], ANKLE_Y, pose.legNear)
    if (far) legIk(rig, hipX, hipY, joints[Rig.FOOT_FAR * 2], ANKLE_Y, pose.legFar)
    pose.pinJoint = Rig.HIP
    pose.pinX = hipX
    pose.pinY = hipY
    rig.solve(pose, joints, null)
}

/** Some clips point the toes into the floor during a crouch; roll the foot so they rest on it. */
private fun keepToesUp(rig: Rig, pose: Pose, joints: FloatArray) {
    val near = groundedFootAngle(rig, pose, true, joints[Rig.FOOT_NEAR * 2 + 1])
    val far = groundedFootAngle(rig, pose, false, joints[Rig.FOOT_FAR * 2 + 1])
    if (near.isNaN() && far.isNaN()) return
    if (!near.isNaN()) pose.footNear = near
    if (!far.isNaN()) pose.footFar = far
    pose.pinJoint = Rig.HIP
    pose.pinX = joints[Rig.HIP * 2]
    pose.pinY = joints[Rig.HIP * 2 + 1]
    rig.solve(pose, joints, null)
}

private fun groundedFootAngle(rig: Rig, pose: Pose, near: Boolean, ankleY: Float): Float {
    val angle = pose.footAngle(near)
    if (ankleY - cos(Math.toRadians(angle.toDouble())).toFloat() * rig.foot <= ANKLE_Y + 0.01f) return Float.NaN
    val fixed = Math.toDegrees(acos(((ankleY - ANKLE_Y) / rig.foot).coerceIn(-1f, 1f).toDouble())).toFloat()
    return if (pose.facing < 0f) 360f - fixed else fixed
}

/** Thigh and shin angles that put the ankle at ([ax], [ay]) with the knee forward (facing right). */
private fun legIk(rig: Rig, hipX: Float, hipY: Float, ax: Float, ay: Float, out: FloatArray) {
    val dx = ax - hipX
    val dy = ay - hipY
    val reach = hypot(dx, dy).coerceIn(abs(rig.thigh - rig.shin) + 0.01f, rig.thigh + rig.shin)
    val cosine = (rig.thigh * rig.thigh + reach * reach - rig.shin * rig.shin) / (2f * rig.thigh * reach)
    val thigh = angleOf(dx, dy) - Math.toDegrees(acos(cosine.coerceIn(-1f, 1f).toDouble())).toFloat()
    val radians = Math.toRadians(thigh.toDouble())
    val kneeX = hipX + sin(radians).toFloat() * rig.thigh
    val kneeY = hipY - cos(radians).toFloat() * rig.thigh
    out[0] = thigh
    out[1] = angleOf(ax - kneeX, ay - kneeY)
}

/** Moves an arm toward ([upper], [fore]); the forearm leads mid-way so big swings bend the elbow. */
private fun armTo(arm: FloatArray, upper: Float, fore: Float, w: Float) {
    val delta = angleBetween(arm[0], upper)
    arm[1] = mixAngle(arm[1], fore, w) + 0.3f * delta * sin(PI_F * w)
    arm[0] += delta * w
}

/** Absolute angle of a vector: 0 = up, 90 = right. */
private fun angleOf(dx: Float, dy: Float): Float = Math.toDegrees(atan2(dx.toDouble(), (-dy).toDouble())).toFloat()

private fun angleBetween(start: Float, end: Float): Float {
    var delta = (end - start) % 360f
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return delta
}

private fun mix(start: Float, end: Float, w: Float): Float = start + (end - start) * w

private fun mixAngle(start: Float, end: Float, w: Float): Float = start + angleBetween(start, end) * w

private fun smooth(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

private fun easeOut(x: Float): Float {
    val c = 1f - x.coerceIn(0f, 1f)
    return 1f - c * c * c
}

private const val ANKLE_Y = 104f
private const val STAND_HIP_Y = 73f
private const val STAND_FAR_LEG = 182f
private const val CLIP_X = 100f // the authored clips are centred on x = 100
private const val FRIEND_LEG = 30.9f
private const val PI_F = 3.1415927f
private const val TAU = 6.2831855f
