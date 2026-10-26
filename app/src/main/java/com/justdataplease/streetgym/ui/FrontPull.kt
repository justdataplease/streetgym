package com.justdataplease.streetgym.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Hanging and pulling seen from the front, in the line figure's own style and proportions (head, single-line torso,
 * two-bone arms and legs, accent hands), so the grip shows: a chin-up keeps the hands close and an archer pull-up opens
 * them wide. Every other movement keeps its side-view clip.
 */
internal object FrontPull {
    enum class Motion {
        HANG, ACTIVE_HANG, SCAPULAR, FOOT_ASSISTED_HANG, JUMP_TO_HANG, PULL, CHEST_TO_BAR, NEGATIVE, ARCHER, TYPEWRITER,
        TOWEL_ONE_ARM, ONE_ARM_NEGATIVE,
    }

    /** [grip]: each hand's distance from the body's centre line. One-arm motions place their own hands. */
    class Variant(val motion: Motion, val grip: Float)

    private val BY_MOVEMENT = mapOf(
        "dead_hang" to Variant(Motion.HANG, 11f),
        "active_hang" to Variant(Motion.ACTIVE_HANG, 11f),
        "scapular_pull" to Variant(Motion.SCAPULAR, 11f),
        "foot_assisted_hang" to Variant(Motion.FOOT_ASSISTED_HANG, 11f),
        "jump_to_hang" to Variant(Motion.JUMP_TO_HANG, 11f),
        "pullup" to Variant(Motion.PULL, 11f),
        "chinup" to Variant(Motion.PULL, 6f),
        "chest_to_bar" to Variant(Motion.CHEST_TO_BAR, 11f),
        "eccentric_pullup" to Variant(Motion.NEGATIVE, 11f),
        "archer_pullup" to Variant(Motion.ARCHER, 22f),
        "typewriter_pullup" to Variant(Motion.TYPEWRITER, 22f),
        "assisted_one_arm_pullup" to Variant(Motion.TOWEL_ONE_ARM, 0f),
        "one_arm_pullup_negative" to Variant(Motion.ONE_ARM_NEGATIVE, 0f),
    )

    /**
     * Exercises bound by name reach these through their clip. The foot-assisted pull-up (legs forward to a low bar),
     * levers, muscle-ups and leg raises stay side-on, where their movement shows.
     */
    private val BY_TEMPLATE = mapOf(
        "hang" to "dead_hang", "active_hang" to "active_hang", "scapular_pull" to "scapular_pull",
        "foot_assisted_hang" to "foot_assisted_hang", "jump_to_hang" to "jump_to_hang", "pullup" to "pullup",
        "chest_to_bar" to "chest_to_bar", "eccentric_pullup" to "eccentric_pullup", "archer_pullup" to "archer_pullup",
        "typewriter_pullup" to "typewriter_pullup", "assisted_one_arm_pullup" to "assisted_one_arm_pullup",
        "one_arm_pullup_negative" to "one_arm_pullup_negative",
    )

    /** A strict pull-up, for the loading screen. */
    val PULL_UP: Variant = BY_MOVEMENT.getValue("pullup")

    fun variant(movementId: String?, template: String): Variant? =
        movementId?.let(BY_MOVEMENT::get) ?: BY_TEMPLATE[template]?.let(BY_MOVEMENT::get)

    // The line figure's proportions (Rig.DEFAULT), seen from the front.
    const val CENTER = 100f
    const val BAR_Y = 26f
    /** The foot-assisted hang's lower bar. */
    const val MID_BAR_Y = 54f
    const val SHOULDER = 5f
    const val HIP = 1.5f
    const val UPPER_ARM = 13.5f
    const val FOREARM = 12.5f
    const val TORSO = 20f
    const val THIGH = 16f
    const val SHIN = 15f
    const val NECK = 3f
    const val HEAD_RADIUS = 4.5f
    /** Shoulders just under the bar: the chin is over it. */
    const val TOP_Y = 29f
    const val CHEST_TOP_Y = 24f
    const val FLOOR_Y = 112f
    /** Hanging legs: knee and foot out from the centre line, so the legs open a little with the knees showing. */
    const val HANG_KNEE = 3.4f
    const val HANG_FOOT = 4f

    /** One frame in figure units. Left is the viewer's left. */
    class Frame {
        var bodyX = CENTER
        var shoulderY = 50f
        var leftX = 0f
        var leftY = BAR_Y
        var rightX = 0f
        var rightY = BAR_Y
        var barY = BAR_Y
        /** Feet standing on the floor; false while they hang. */
        var grounded = false
        /** False when the right hand has let go and hangs by the side. */
        var rightHolds = true
        /** A towel looped over the bar at [towelX], hanging to [towelBottom]; 0 when there is none. */
        var towelX = 0f
        var towelBottom = 0f
        var barHalf = 24f
    }

    /** Shoulders in a nearly straight-armed hang with the hand [dx] to the side of its shoulder. */
    fun hangY(dx: Float): Float = BAR_Y + sqrt(max(0f, ((UPPER_ARM + FOREARM) * 0.985f).let { it * it } - dx * dx))

    private fun ease(x: Float): Float = x.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun phase(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

    /**
     * Poses [variant] at [t]: for back-and-forth clips 0 is the start and 1 the end of the effort (the guide's own
     * tempo moves between them); for looping clips [t] runs once through the loop.
     */
    fun pose(variant: Variant, t: Float, f: Frame) {
        val g = variant.grip
        f.bodyX = CENTER
        f.leftX = CENTER - g
        f.rightX = CENTER + g
        f.leftY = BAR_Y
        f.rightY = BAR_Y
        f.rightHolds = true
        f.barY = BAR_Y
        f.grounded = false
        f.towelX = 0f
        f.towelBottom = 0f
        f.barHalf = max(18f, g + 8f)
        val hang = hangY(g - SHOULDER)
        val lift = ease(t / 0.85f)
        when (variant.motion) {
            Motion.HANG -> f.shoulderY = hang
            Motion.ACTIVE_HANG -> f.shoulderY = hang - 2f * ease(t)
            Motion.SCAPULAR -> f.shoulderY = hang - 3.5f * ease(t)
            Motion.FOOT_ASSISTED_HANG -> {
                // A lower bar, knees bent and feet on the floor carrying some of the weight.
                f.barY = MID_BAR_Y
                f.leftY = MID_BAR_Y
                f.rightY = MID_BAR_Y
                f.grounded = true
                f.shoulderY = MID_BAR_Y + (hang - BAR_Y) - 2f * ease(t)
            }
            Motion.JUMP_TO_HANG -> {
                // Stand under the bar, dip, jump to grip it, hang, then drop back to the floor.
                val stand = FLOOR_Y - THIGH - SHIN - TORSO + 0.5f
                val dip = ease(phase(t, 0.12f, 0.28f)) * (1f - ease(phase(t, 0.28f, 0.36f)))
                val jump = ease(phase(t, 0.3f, 0.45f))
                val drop = ease(phase(t, 0.84f, 0.96f))
                val air = jump * (1f - drop)
                f.shoulderY = lerp(stand + 6f * dip, hang, air)
                f.grounded = air < 0.35f
                val sideX = SHOULDER + 1f
                val reach = ease(phase(t, 0.26f, 0.42f)) * (1f - ease(phase(t, 0.86f, 0.98f)))
                f.leftX = lerp(CENTER - sideX, CENTER - g, reach)
                f.rightX = lerp(CENTER + sideX, CENTER + g, reach)
                val down = f.shoulderY + UPPER_ARM + FOREARM - 1f
                f.leftY = lerp(down, BAR_Y, reach)
                f.rightY = f.leftY
            }
            Motion.PULL -> f.shoulderY = lerp(hang, TOP_Y, lift)
            Motion.CHEST_TO_BAR -> f.shoulderY = lerp(hang, CHEST_TOP_Y, lift)
            Motion.NEGATIVE -> f.shoulderY = when {
                // Step up to the top, hold, lower slowly, then hang and step down.
                t < 0.12f -> lerp(hang, TOP_Y, ease(phase(t, 0f, 0.12f)))
                t < 0.2f -> TOP_Y
                t < 0.85f -> lerp(TOP_Y, hang, phase(t, 0.2f, 0.85f))
                else -> hang
            }
            Motion.ARCHER -> {
                // Toward the left hand; the right arm stays nearly straight along the bar.
                f.shoulderY = lerp(hang, TOP_Y + 1f, lift)
                f.bodyX = CENTER - 13f * lift
            }
            Motion.TYPEWRITER -> {
                // Up to the left hand, slide along the bar to the right, then lower.
                val up = ease(phase(t, 0f, 0.2f))
                val down = ease(phase(t, 0.68f, 0.95f))
                f.shoulderY = lerp(lerp(hang, TOP_Y + 1f, up), hang, down)
                val across = ease(phase(t, 0.3f, 0.6f))
                f.bodyX = lerp(lerp(CENTER - 13f * up, CENTER + 13f, across), CENTER, down)
            }
            Motion.TOWEL_ONE_ARM -> {
                // One hand on the bar, the other low on a towel looped over it.
                f.leftX = CENTER - 4f
                f.towelX = CENTER + 10f
                f.rightX = f.towelX
                f.rightY = 44f
                f.towelBottom = 49f
                f.shoulderY = lerp(hangY(SHOULDER - 4f), TOP_Y + 1f, lift)
                f.bodyX = CENTER - 2f * lift
            }
            Motion.ONE_ARM_NEGATIVE -> {
                // Step up with both hands, let go with one, lower on the other arm, then reset.
                val up = ease(phase(t, 0f, 0.12f))
                val letGo = phase(t, 0.12f, 0.18f)
                val lower = phase(t, 0.18f, 0.78f)
                val reset = ease(phase(t, 0.88f, 1f))
                f.leftX = lerp(CENTER - 6f, CENTER - 3f, ease(letGo))
                f.rightX = CENTER + 6f
                f.rightHolds = letGo <= 0f || reset >= 1f
                // The body hangs nearly under the working hand.
                val oneArmHang = hangY(3.5f)
                val bothHang = hangY(1f)
                f.shoulderY = when {
                    t < 0.18f -> lerp(bothHang, TOP_Y, up)
                    t < 0.88f -> lerp(TOP_Y, oneArmHang, lower)
                    else -> lerp(oneArmHang, bothHang, reset)
                }
                f.bodyX = CENTER - 1.5f * ease(letGo) * (1f - reset)
            }
        }
    }

    /** Paints: the figure's limbs and torso, its head, its hands, the bar, and the posts, floor and towel. */
    class Ink(val limb: Paint, val head: Paint, val hand: Paint, val bar: Paint, val rig: Paint)

    /** A figure-space rectangle to fit into a view. */
    class Window(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width get() = right - left
        val height get() = bottom - top
        val centerX get() = (left + right) / 2f
        val centerY get() = (top + bottom) / 2f
    }

    /** The guide panel's frame with posts and floor, the loading screen's, and a skill badge's. */
    val GUIDE_WINDOW = Window(CENTER - 46f, 10f, CENTER + 46f, FLOOR_Y + 3f)
    val LOADING_WINDOW = Window(CENTER - 30f, 12f, CENTER + 30f, FLOOR_Y + 2f)
    val BADGE_WINDOW = Window(CENTER - 36f, 13f, CENTER + 36f, 102f)

    /**
     * Draws [variant] at [t] fitted into [target] through [window]. [boost] thickens the strokes as the skill badges
     * do; [posts] adds the bar's posts and the floor.
     */
    fun draw(
        canvas: Canvas, variant: Variant, t: Float, target: RectF, window: Window, ink: Ink, boost: Float,
        posts: Boolean, frame: Frame,
    ) {
        pose(variant, t, frame)
        val scale = min(target.width() / window.width, target.height() / window.height)
        val ox = target.centerX() - window.centerX * scale
        val oy = target.centerY() - window.centerY * scale
        fun x(v: Float) = ox + v * scale
        fun y(v: Float) = oy + v * scale
        fun line(x0: Float, y0: Float, x1: Float, y1: Float, paint: Paint) = canvas.drawLine(x(x0), y(y0), x(x1), y(y1), paint)

        val f = frame
        val barLeft = CENTER - f.barHalf
        val barRight = CENTER + f.barHalf
        if (posts) {
            ink.rig.strokeWidth = 1.6f * scale
            line(barLeft + 3f, f.barY, barLeft + 3f, FLOOR_Y, ink.rig)
            line(barRight - 3f, f.barY, barRight - 3f, FLOOR_Y, ink.rig)
            line(window.left, FLOOR_Y, window.right, FLOOR_Y, ink.rig)
        }
        ink.bar.strokeWidth = 1.8f * boost * scale
        line(barLeft, f.barY, barRight, f.barY, ink.bar)
        if (f.towelBottom > 0f) {
            ink.rig.strokeWidth = 1.4f * boost * scale
            line(f.towelX, BAR_Y, f.towelX, f.towelBottom, ink.rig)
        }

        ink.limb.strokeWidth = 2.6f * boost * scale
        val s = f.shoulderY
        val bx = f.bodyX
        val pelvis = s + TORSO
        // Shoulders, torso and neck, as the side view's single torso line.
        line(bx - SHOULDER, s, bx + SHOULDER, s, ink.limb)
        line(bx, s, bx, pelvis, ink.limb)
        line(bx, s, bx, s - NECK, ink.limb)
        canvas.drawCircle(x(bx), y(s - NECK - HEAD_RADIUS), HEAD_RADIUS * scale * (1f + (boost - 1f) * 0.4f), ink.head)
        // Thigh and shin, as the side view draws them: hanging a little apart, or standing with the knees bending outward.
        if (f.grounded) {
            limb(bx - HIP, pelvis, bx - 3f, FLOOR_Y, -1f, THIGH, SHIN, ink.limb, ::line)
            limb(bx + HIP, pelvis, bx + 3f, FLOOR_Y, 1f, THIGH, SHIN, ink.limb, ::line)
        } else for (side in intArrayOf(-1, 1)) {
            val knee = bx + side * HANG_KNEE
            line(bx + side * HIP, pelvis, knee, pelvis + THIGH, ink.limb)
            line(knee, pelvis + THIGH, bx + side * HANG_FOOT, pelvis + THIGH + SHIN, ink.limb)
        }
        // Arms: two bones to each hand, the elbows bending outward.
        arm(bx - SHOULDER, s, f.leftX, f.leftY, -1f, ink.limb, ::line)
        if (f.rightHolds) arm(bx + SHOULDER, s, f.rightX, f.rightY, 1f, ink.limb, ::line)
        else {
            line(bx + SHOULDER, s, bx + SHOULDER + 2.5f, s + UPPER_ARM, ink.limb)
            line(bx + SHOULDER + 2.5f, s + UPPER_ARM, bx + SHOULDER + 3f, s + UPPER_ARM + FOREARM, ink.limb)
        }
        val handRadius = 1.6f * scale
        canvas.drawCircle(x(f.leftX), y(f.leftY), handRadius, ink.hand)
        if (f.rightHolds) canvas.drawCircle(x(f.rightX), y(f.rightY), handRadius, ink.hand)
        else canvas.drawCircle(x(bx + SHOULDER + 3f), y(s + UPPER_ARM + FOREARM), handRadius, ink.hand)
    }

    private fun arm(
        sx: Float, sy: Float, hx: Float, hy: Float, side: Float, paint: Paint,
        line: (Float, Float, Float, Float, Paint) -> Unit,
    ) = limb(sx, sy, hx, hy, side, UPPER_ARM, FOREARM, paint, line)

    private fun limb(
        sx: Float, sy: Float, hx: Float, hy: Float, side: Float, upper: Float, lower: Float, paint: Paint,
        line: (Float, Float, Float, Float, Paint) -> Unit,
    ) {
        val joint = joint(sx, sy, hx, hy, side, upper, lower)
        if (joint == null) {
            line(sx, sy, hx, hy, paint)
        } else {
            line(sx, sy, joint.first, joint.second, paint)
            line(joint.first, joint.second, hx, hy, paint)
        }
    }

    /**
     * The elbow from shoulder to hand ([side] -1 left, 1 right), as a pull looks from the front: the forearm hangs
     * straight down from the grip whenever the upper arm reaches (the "W" at the top of a pull-up); nearer the hang
     * the arm bends a little outward; null when it is straight.
     */
    fun elbow(sx: Float, sy: Float, hx: Float, hy: Float, side: Float): Pair<Float, Float>? {
        if (hypot(hx - sx, hy - sy) >= UPPER_ARM + FOREARM) return null
        val ex = hx + side
        val reach = UPPER_ARM * UPPER_ARM - (ex - sx) * (ex - sx)
        if (reach >= 0f) {
            val rise = sy - hy
            val drop = min(FOREARM, rise + sqrt(reach))
            if (drop >= max(0f, rise - sqrt(reach))) return ex to hy + drop
        }
        return joint(sx, sy, hx, hy, side, UPPER_ARM, FOREARM)
    }

    /** The middle joint of two bones [upper] and [lower] from root to tip, bending outward; null when straight. */
    private fun joint(sx: Float, sy: Float, hx: Float, hy: Float, side: Float, upper: Float, lower: Float): Pair<Float, Float>? {
        val dx = hx - sx
        val dy = hy - sy
        val d = hypot(dx, dy)
        if (d >= upper + lower || d < 0.01f) return null
        // Law of cosines: how far along the root-tip line the joint sits, and how far off it.
        val along = (upper * upper - lower * lower + d * d) / (2f * d)
        val off = sqrt(max(0f, upper * upper - along * along))
        var px = -dy / d
        var py = dx / d
        if (px * side < 0f) { px = -px; py = -py }
        return (sx + dx / d * along + px * off) to (sy + dy / d * along + py * off)
    }
}
