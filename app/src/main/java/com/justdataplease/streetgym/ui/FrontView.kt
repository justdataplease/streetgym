package com.justdataplease.streetgym.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Movements seen from the front, drawn as the side clips' line figure: the same proportions, stroke weights and
 * 200 x 120 canvas (bar at 12, floor at 106), so a front view beside a side view matches it line for line. Bar work
 * shows its grip, from a chin-up's close hands to an archer's wide ones; jacks, Cossack squats and the like show the
 * sideways movement a profile hides.
 */
internal object FrontView {
    /** [bar]: the hands hold a bar; [follows]: the height comes from the side clip, so both views rise together. */
    enum class Motion(val bar: Boolean, val follows: Boolean) {
        HANG(true, true), ACTIVE_HANG(true, true), SCAPULAR(true, true), FOOT_ASSISTED_HANG(true, true),
        JUMP_TO_HANG(true, false), PULL(true, true), CHEST_TO_BAR(true, true), NEGATIVE(true, true), ARCHER(true, true),
        TYPEWRITER(true, true), TOWEL_ONE_ARM(true, true), ONE_ARM_NEGATIVE(true, true),
        JACK(false, false), ARM_SWINGS(false, false), HIP_CIRCLES(false, false), COSSACK(false, false),
        COSSACK_HOLD(false, false), WALL_SLIDE(false, false), ARCHER_PUSH(false, true), TWIST(false, false),
    }

    /**
     * How a movement shows from the front; guides show the side clip beside it. [grip]: each hand's distance from the
     * centre line on the bar. [badge]: skill badges and picker tiles draw the front view. [sideClip]: the side clip to
     * show when the exercise's own clip faces the viewer. [marker]: the clip whose range line the guide draws when the
     * side clip has none. [mirrors]: every other rep works the other side, as the coaching says.
     */
    class Variant(
        val motion: Motion,
        val grip: Float = 0f,
        val badge: Boolean = true,
        val sideClip: String? = null,
        val marker: String? = null,
        val mirrors: Boolean = false,
    )

    private val BY_MOVEMENT = mapOf(
        "dead_hang" to Variant(Motion.HANG, 11f),
        "active_hang" to Variant(Motion.ACTIVE_HANG, 11f),
        "scapular_pull" to Variant(Motion.SCAPULAR, 11f),
        "foot_assisted_hang" to Variant(Motion.FOOT_ASSISTED_HANG, 11f),
        "jump_to_hang" to Variant(Motion.JUMP_TO_HANG, 11f),
        "pullup" to Variant(Motion.PULL, 11f),
        "chinup" to Variant(Motion.PULL, 6f),
        "chest_to_bar" to Variant(Motion.CHEST_TO_BAR, 11f),
        "eccentric_pullup" to Variant(Motion.NEGATIVE, 11f, marker = "pullup"),
        "archer_pullup" to Variant(Motion.ARCHER, 22f, sideClip = "pullup", mirrors = true),
        "typewriter_pullup" to Variant(Motion.TYPEWRITER, 22f, sideClip = "pullup", mirrors = true),
        "assisted_one_arm_pullup" to Variant(Motion.TOWEL_ONE_ARM),
        "one_arm_pullup_negative" to Variant(Motion.ONE_ARM_NEGATIVE, marker = "assisted_one_arm_pullup"),
        "low_jack" to Variant(Motion.JACK, mirrors = true),
        "arm_swings" to Variant(Motion.ARM_SWINGS, mirrors = true),
        "hip_circles" to Variant(Motion.HIP_CIRCLES),
        "cossack_squat" to Variant(Motion.COSSACK, sideClip = "cossack_side", mirrors = true),
        "adductor_stretch" to Variant(Motion.COSSACK_HOLD, sideClip = "cossack_hold_side"),
        "supine_twist" to Variant(Motion.TWIST, badge = false, mirrors = true),
        "wall_slide" to Variant(Motion.WALL_SLIDE),
        "archer_pushup" to Variant(Motion.ARCHER_PUSH, badge = false, mirrors = true),
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
        "one_arm_pullup_negative" to "one_arm_pullup_negative", "jack" to "low_jack", "arm_swings" to "arm_swings",
        "hip_circles" to "hip_circles", "cossack" to "cossack_squat", "cossack_hold" to "adductor_stretch",
        "wall_slide" to "wall_slide", "archer_pushup" to "archer_pushup", "supine_twist" to "supine_twist",
    )

    /** A strict pull-up, for the loading screen. */
    val PULL_UP: Variant = BY_MOVEMENT.getValue("pullup")

    fun variant(movementId: String?, template: String): Variant? =
        movementId?.let(BY_MOVEMENT::get) ?: BY_TEMPLATE[template]?.let(BY_MOVEMENT::get)

    /** Where the side clip is at [t] of the front motion: the typewriter's slide along the bar keeps a pull-up at the top. */
    fun sideTime(variant: Variant, t: Float): Float =
        if (variant.motion == Motion.TYPEWRITER) 0.85f * ease(phase(t, 0f, 0.2f)) * (1f - ease(phase(t, 0.68f, 0.95f))) else t

    // The line figure's proportions (Rig.DEFAULT) and the side clips' scene, seen from the front.
    const val CENTER = 100f
    const val BAR_Y = 12f
    /** The foot-assisted hang's lower bar. */
    const val MID_BAR_Y = 40f
    const val FLOOR_Y = 106f
    /** Where standing ankles sit, as the side clips pin them. */
    const val ANKLE_Y = 104f
    const val SHOULDER = 5f
    const val HIP = 1.5f
    const val UPPER_ARM = 13.5f
    const val FOREARM = 12.5f
    const val TORSO = 20f
    const val THIGH = 16f
    const val SHIN = 15f
    const val NECK = 3f
    const val HEAD_RADIUS = 4.5f
    /** The neck just under the bar: the chin is over it. */
    const val TOP_Y = BAR_Y + 3f
    const val CHEST_TOP_Y = BAR_Y - 2f
    /** Hanging legs: knee and foot out from the centre line, so the legs open a little with the knees showing. */
    const val HANG_KNEE = 3.4f
    const val HANG_FOOT = 4f
    private const val ARM = UPPER_ARM + FOREARM
    /** How far the archer and typewriter move toward one hand: the other arm, 22 out, still just reaches. */
    private const val SHIFT = 8.5f
    private const val LEG = THIGH + SHIN

    /** One posed frame in canvas units. Left is the viewer's left. */
    class Frame {
        var neckX = CENTER
        var neckY = 0f
        /** How far the shoulder tips sit above [neckY]: up toward the ears in a passive hang. */
        var shrug = 0f
        var pelvisX = CENTER
        var pelvisY = 0f
        var leftX = 0f
        var leftY = 0f
        var rightX = 0f
        var rightY = 0f
        var leftFootX = 0f
        var leftFootY = 0f
        var rightFootX = 0f
        var rightFootY = 0f
        /** False when that hand has let go of the bar and hangs by the side. */
        var leftHolds = true
        var rightHolds = true
        /** The hands grip a bar, so near the top of a pull the forearms hang straight under it (the "W"). */
        var grip = false
        /** The arms point at the viewer, so each is drawn straight and shortened instead of bent. */
        var reach = false
        /** How far elbows and knees bend out sideways (1) rather than toward the viewer (0). */
        var spread = 1f
        /** Feet standing on the floor; false while they hang. */
        var grounded = false
        /** The body points away from the viewer, as a push-up seen head-on: a stub of torso and no legs. */
        var prone = false
        /** Lying on the back seen from the feet: knees up over the feet, turned together by [kneeTurn] radians. */
        var supine = false
        var kneeTurn = 0f
        /** The bar's height, 0 for no bar. */
        var barY = 0f
        var barHalf = 0f
        /** A towel looped over the bar at [towelX], hanging to [towelBottom]; 0 when there is none. */
        var towelX = 0f
        var towelBottom = 0f
        /** A box under the feet, centred: half its width and its height; 0 for none. */
        var boxHalf = 0f
        var boxHeight = 0f
    }

    /** Shoulders in a nearly straight-armed hang from a bar at [barY] with the hand [dx] to the side of its shoulder. */
    fun hangY(dx: Float, barY: Float = BAR_Y): Float = barY + sqrt(max(0f, (ARM * 0.985f).let { it * it } - dx * dx))

    /** Smoothstep: 0 below 0, 1 above 1, easing between. */
    fun ease(x: Float): Float = x.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun phase(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

    /**
     * Poses [variant] at [t]: for back-and-forth clips 0 is the start and 1 the end of the effort (the guide's own tempo
     * moves between them); for looping clips [t] runs once through the loop. [mirror] works the other side. [neckY] and
     * [shrug] are the side clip's at the same moment, for motions that follow it; NaN keeps the front view's own.
     */
    fun pose(variant: Variant, t: Float, f: Frame, mirror: Boolean = false, neckY: Float = Float.NaN, shrug: Float = 0f) {
        f.shrug = 0f
        f.leftHolds = true
        f.rightHolds = true
        f.grip = false
        f.reach = false
        f.spread = 1f
        f.grounded = false
        f.prone = false
        f.supine = false
        f.kneeTurn = 0f
        f.barY = 0f
        f.barHalf = 0f
        f.towelX = 0f
        f.towelBottom = 0f
        f.boxHalf = 0f
        f.boxHeight = 0f
        val follow = if (variant.motion.follows) neckY else Float.NaN
        if (variant.motion.bar) hang(variant, t, f, follow, shrug) else stand(variant, t, f, follow)
        if (mirror) mirror(f)
    }

    private fun hang(variant: Variant, t: Float, f: Frame, follow: Float, shrug: Float) {
        val g = variant.grip
        f.grip = true
        f.barY = BAR_Y
        f.barHalf = max(18f, g + 8f)
        f.leftX = CENTER - g
        f.rightX = CENTER + g
        f.leftY = BAR_Y
        f.rightY = BAR_Y
        f.neckX = CENTER
        val hang = hangY(g - SHOULDER)
        val lift = ease(t / 0.85f)
        when (variant.motion) {
            Motion.HANG -> f.neckY = hang
            Motion.ACTIVE_HANG -> f.neckY = hang - 2f * ease(t)
            Motion.SCAPULAR -> f.neckY = hang - 3.5f * ease(t)
            Motion.FOOT_ASSISTED_HANG -> {
                // A lower bar, knees bent forward and feet on the floor carrying some of the weight.
                f.barY = MID_BAR_Y
                f.leftY = MID_BAR_Y
                f.rightY = MID_BAR_Y
                f.grounded = true
                f.spread = 0.4f
                f.neckY = hangY(g - SHOULDER, MID_BAR_Y) - 2f * ease(t)
            }
            Motion.JUMP_TO_HANG -> {
                // Stand under the bar, dip, jump to grip it, hang, then drop back to the floor, on the side clip's beats
                // (dip at 0.15, catch at 0.32, hang until 0.68, standing again at 0.85).
                val stand = ANKLE_Y - LEG - TORSO + 0.5f
                val dip = ease(phase(t, 0.03f, 0.15f)) * (1f - ease(phase(t, 0.15f, 0.22f)))
                val jump = ease(phase(t, 0.17f, 0.32f))
                val drop = ease(phase(t, 0.68f, 0.85f))
                val air = jump * (1f - drop)
                f.neckY = lerp(stand + 6f * dip, hang, air)
                f.grounded = air < 0.35f
                f.spread = 0.6f
                val sideX = SHOULDER + 1f
                val reach = ease(phase(t, 0.12f, 0.28f)) * (1f - ease(phase(t, 0.7f, 0.82f)))
                f.leftX = lerp(CENTER - sideX, CENTER - g, reach)
                f.rightX = lerp(CENTER + sideX, CENTER + g, reach)
                f.leftY = lerp(f.neckY + ARM - 1f, BAR_Y, reach)
                f.rightY = f.leftY
                f.grip = reach > 0.99f
            }
            Motion.PULL -> f.neckY = lerp(hang, TOP_Y, lift)
            Motion.CHEST_TO_BAR -> f.neckY = lerp(hang, CHEST_TOP_Y, lift)
            Motion.NEGATIVE -> {
                // Step up from a box to the top, hold, lower slowly, then hang and step down.
                f.boxHalf = 16f
                f.boxHeight = 14f
                f.neckY = when {
                    t < 0.12f -> lerp(hang, TOP_Y, ease(phase(t, 0f, 0.12f)))
                    t < 0.2f -> TOP_Y
                    t < 0.85f -> lerp(TOP_Y, hang, phase(t, 0.2f, 0.85f))
                    else -> hang
                }
            }
            Motion.ARCHER -> {
                // Toward the left hand, as far as the right arm reaches nearly straight along the bar.
                f.neckY = lerp(hang, TOP_Y + 1f, lift)
                f.neckX = CENTER - SHIFT * lift
            }
            Motion.TYPEWRITER -> {
                // Up to the left hand, slide along the bar to the right, then lower.
                val up = ease(phase(t, 0f, 0.2f))
                val down = ease(phase(t, 0.68f, 0.95f))
                f.neckY = lerp(lerp(hang, TOP_Y + 1f, up), hang, down)
                val across = ease(phase(t, 0.3f, 0.6f))
                f.neckX = lerp(lerp(CENTER - SHIFT * up, CENTER + SHIFT, across), CENTER, down)
            }
            Motion.TOWEL_ONE_ARM -> {
                // One hand on the bar, the other low on a towel looped over it.
                f.leftX = CENTER - 4f
                f.towelX = CENTER + 10f
                f.rightX = f.towelX
                f.rightY = BAR_Y + 18f
                f.towelBottom = BAR_Y + 23f
                f.neckY = lerp(hangY(SHOULDER - 4f), TOP_Y + 1f, lift)
                f.neckX = CENTER - 2f * lift
            }
            Motion.ONE_ARM_NEGATIVE -> {
                // Step up with both hands, let go with one, lower on the other arm, then reset.
                f.boxHalf = 15f
                f.boxHeight = 9f
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
                f.neckY = when {
                    t < 0.18f -> lerp(bothHang, TOP_Y, up)
                    t < 0.88f -> lerp(TOP_Y, oneArmHang, lower)
                    else -> lerp(oneArmHang, bothHang, reset)
                }
                f.neckX = CENTER - 1.5f * ease(letGo) * (1f - reset)
            }
            else -> Unit
        }
        if (!follow.isNaN()) {
            f.neckY = follow
            f.shrug = shrug
        }
        // A grip wider than the shoulders hangs higher: lift the body until every held hand is in reach.
        var lift2 = 0f
        if (f.leftHolds && f.grip) lift2 = max(lift2, overreach(f.leftX - (f.neckX - SHOULDER), f.leftY, f.neckY - f.shrug))
        if (f.rightHolds && f.grip && f.towelBottom <= 0f) {
            lift2 = max(lift2, overreach(f.rightX - (f.neckX + SHOULDER), f.rightY, f.neckY - f.shrug))
        }
        f.neckY -= lift2
        if (!f.grip) {
            // Reaching up for the bar mid-jump: the hands go no further than the arms do.
            f.leftY = f.neckY + max(f.leftY - f.neckY, -sqrt(max(0f, (ARM * 0.995f).let { it * it } - (f.leftX - (f.neckX - SHOULDER)).let { it * it })))
            f.rightY = f.leftY
        }
        f.pelvisX = f.neckX
        f.pelvisY = f.neckY + TORSO
        if (f.grounded) {
            // Feet on the floor, or just leaving it as a jump straightens the legs.
            f.leftFootX = f.pelvisX - 3f
            f.rightFootX = f.pelvisX + 3f
            f.leftFootY = min(ANKLE_Y, f.pelvisY + sqrt(LEG * LEG * 0.99f - 1.5f * 1.5f))
            f.rightFootY = f.leftFootY
        } else {
            f.leftFootX = f.pelvisX - HANG_FOOT
            f.rightFootX = f.pelvisX + HANG_FOOT
            f.leftFootY = f.pelvisY + LEG
            f.rightFootY = f.leftFootY
        }
    }

    /** How far a shoulder at [shoulderY] sits below where an arm reaching [dx] sideways to a hand at [handY] can hang. */
    private fun overreach(dx: Float, handY: Float, shoulderY: Float): Float =
        max(0f, shoulderY - (handY + sqrt(max(0f, (ARM * 0.995f).let { it * it } - dx * dx))))

    private fun stand(variant: Variant, t: Float, f: Frame, follow: Float) {
        f.grounded = true
        f.leftFootX = CENTER - 3f
        f.rightFootX = CENTER + 3f
        f.leftFootY = ANKLE_Y
        f.rightFootY = ANKLE_Y
        settle(f, CENTER)
        armsDown(f)
        val e = ease(t)
        when (variant.motion) {
            Motion.JACK -> {
                // One foot steps out as both arms arc overhead, the hips shifting over the wider stance.
                f.rightFootX = lerp(CENTER + 3f, CENTER + 16f, e)
                settle(f, lerp(CENTER, CENTER + 6.5f, e))
                arc(f, -1f, 180f + 165f * e)
                arc(f, 1f, 180f - 165f * e)
            }
            Motion.ARM_SWINGS -> {
                // Open wide, then swing across so the hands pass each other in front of the chest.
                f.reach = true
                arc(f, -1f, 255f)
                arc(f, 1f, 105f)
                f.leftX = lerp(f.leftX, CENTER + 8f, e)
                f.leftY = lerp(f.leftY, f.neckY + 9f, e)
                f.rightX = lerp(f.rightX, CENTER - 8f, e)
                f.rightY = lerp(f.rightY, f.neckY + 6.5f, e)
            }
            Motion.HIP_CIRCLES -> {
                // Once round the loop: hips to the right, back through the middle, to the left. The side clip carries
                // them forward and back, so together the two views trace the circle. Hands rest on the hips.
                val sway = sin(2f * PI.toFloat() * t)
                f.leftFootX = CENTER - 4f
                f.rightFootX = CENTER + 4f
                settle(f, CENTER + 4f * sway, 0.985f)
                f.neckX = CENTER + sway
                f.leftX = f.pelvisX - HIP - 3f
                f.rightX = f.pelvisX + HIP + 3f
                f.leftY = f.pelvisY - 2f
                f.rightY = f.leftY
            }
            Motion.COSSACK, Motion.COSSACK_HOLD -> {
                // A wide stance; the hips sink over the left foot while the right leg lengthens out to the side.
                f.leftFootX = CENTER - 17f
                f.rightFootX = CENTER + 17f
                f.spread = 0.55f
                val depth = if (variant.motion == Motion.COSSACK) e else 0.8f + 0.2f * e
                settle(f, CENTER - 10f * depth)
                f.neckX = f.pelvisX - 1.5f * depth
                // The chest leans toward the viewer, so the torso looks shorter.
                f.neckY = f.pelvisY - TORSO * (1f - 0.18f * depth)
                if (variant.motion == Motion.COSSACK) {
                    // Hands together in front of the chest for balance.
                    f.leftX = f.neckX - 1.2f
                    f.rightX = f.neckX + 1.2f
                    f.leftY = f.neckY + 8f
                    f.rightY = f.leftY
                } else {
                    // One hand on the bent knee, the other on the long thigh.
                    val knee = joint(f.pelvisX - HIP, f.pelvisY, f.leftFootX, f.leftFootY, -1f, THIGH, SHIN, f.spread)
                    f.leftX = knee?.first ?: f.leftFootX
                    f.leftY = (knee?.second ?: f.pelvisY) - 1f
                    f.rightX = lerp(f.pelvisX + HIP, f.rightFootX, 0.35f)
                    f.rightY = lerp(f.pelvisY, f.rightFootY, 0.35f) - 1f
                }
            }
            Motion.WALL_SLIDE -> {
                // Back to the wall, the arms slide from a "W" (elbows below the shoulders, hands about head height) up to
                // a narrow "Y", as the side clip shows.
                for (side in SIDES) {
                    val tipX = f.neckX + side * SHOULDER
                    val x = tipX + side * lerp(13f, 5f, e)
                    val y = f.neckY - lerp(5f, 25.5f, e)
                    if (side < 0f) { f.leftX = x; f.leftY = y } else { f.rightX = x; f.rightY = y }
                }
            }
            Motion.TWIST -> {
                // On the back seen from the feet: shoulders and arms flat along the floor, knees up together, lowering
                // to one side while the shoulders stay down.
                f.grounded = false
                f.prone = true
                f.supine = true
                f.reach = true
                f.neckX = CENTER
                f.neckY = FLOOR_Y - 6f
                f.pelvisX = CENTER
                f.pelvisY = FLOOR_Y - 3.5f
                f.leftX = CENTER - SHOULDER - ARM * 0.75f
                f.rightX = CENTER + SHOULDER + ARM * 0.75f
                f.leftY = FLOOR_Y - 2f
                f.rightY = f.leftY
                f.leftFootX = CENTER - 3f
                f.rightFootX = CENTER + 3f
                f.kneeTurn = Math.toRadians(70.0 * e).toFloat()
            }
            Motion.ARCHER_PUSH -> {
                // A wide push-up seen head-on: the body lowers toward the left hand while the right slides out straight.
                f.prone = true
                f.grounded = false
                f.spread = 0.7f
                val y = if (follow.isNaN()) lerp(80.5f, 92f, e) else follow
                val depth = ((y - 80.5f) / (92f - 80.5f)).coerceIn(0f, 1f)
                f.neckX = CENTER - 9f * depth
                f.neckY = y
                f.pelvisX = f.neckX
                f.pelvisY = y + 6f
                val handY = FLOOR_Y - 1f
                f.leftX = CENTER - 13f
                f.leftY = handY
                f.rightX = max(CENTER + 13f, f.neckX + SHOULDER + sqrt(max(0f, (ARM * 0.99f).let { it * it } - (handY - y) * (handY - y))))
                f.rightY = handY
            }
            else -> Unit
        }
    }

    /** Puts the pelvis over [x] as high as legs at [straight] of their length reach both feet, the torso upright above. */
    private fun settle(f: Frame, x: Float, straight: Float = 0.995f) {
        val leg = LEG * straight
        fun rise(footX: Float, hipX: Float) = sqrt(max(0f, leg * leg - (footX - hipX) * (footX - hipX)))
        f.pelvisX = x
        f.pelvisY = ANKLE_Y - min(rise(f.leftFootX, x - HIP), rise(f.rightFootX, x + HIP))
        f.neckX = x
        f.neckY = f.pelvisY - TORSO
    }

    private fun armsDown(f: Frame) {
        arc(f, -1f, 186f)
        arc(f, 1f, 174f)
    }

    /** Places a hand nearly an arm's length from its shoulder at [angle] degrees (0 up, 90 the viewer's right, 180 down). */
    private fun arc(f: Frame, side: Float, angle: Float) {
        val radians = Math.toRadians(angle.toDouble())
        val x = f.neckX + side * SHOULDER + sin(radians).toFloat() * ARM * 0.975f
        val y = f.neckY - f.shrug - cos(radians).toFloat() * ARM * 0.975f
        if (side < 0f) { f.leftX = x; f.leftY = y } else { f.rightX = x; f.rightY = y }
    }

    private fun mirror(f: Frame) {
        fun m(x: Float) = 2f * CENTER - x
        f.neckX = m(f.neckX)
        f.pelvisX = m(f.pelvisX)
        val handX = f.leftX
        val handY = f.leftY
        f.leftX = m(f.rightX)
        f.leftY = f.rightY
        f.rightX = m(handX)
        f.rightY = handY
        val footX = f.leftFootX
        val footY = f.leftFootY
        f.leftFootX = m(f.rightFootX)
        f.leftFootY = f.rightFootY
        f.rightFootX = m(footX)
        f.rightFootY = footY
        val holds = f.leftHolds
        f.leftHolds = f.rightHolds
        f.rightHolds = holds
        if (f.towelBottom > 0f) f.towelX = m(f.towelX)
        f.kneeTurn = -f.kneeTurn
    }

    /**
     * Paints: the figure's limbs and torso, its head, its hands, the bar and boxes, the posts and floor, box fills, and a
     * ring around each hand where its colour alone is too faint on the panel.
     */
    class Ink(
        val limb: Paint, val head: Paint, val hand: Paint, val bar: Paint, val rig: Paint, val fill: Paint? = null,
        val handRing: Paint? = null,
    )

    /** A hand: a dot in the hand paint, ringed when the ink has a ring. */
    private fun hand(canvas: Canvas, x: Float, y: Float, scale: Float, ink: Ink) {
        canvas.drawCircle(x, y, 1.6f * scale, ink.hand)
        ink.handRing?.let {
            it.strokeWidth = HAND_RING * scale
            canvas.drawCircle(x, y, 1.6f * scale, it)
        }
    }

    /** A figure-space rectangle to fit into a view. */
    class Window(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width get() = right - left
        val height get() = bottom - top
        val centerX get() = (left + right) / 2f
        val centerY get() = (top + bottom) / 2f
    }

    /** The loading screen's frame: the bar, a standing start and the floor. */
    val LOADING_WINDOW = Window(CENTER - 30f, BAR_Y - 14f, CENTER + 30f, FLOOR_Y + 2f)

    /** Poses [variant] at [t] on its own timing and draws it fitted into [target] through [window], posts and floor included. */
    fun draw(canvas: Canvas, variant: Variant, t: Float, target: RectF, window: Window, ink: Ink, frame: Frame) {
        pose(variant, t, frame)
        val scale = min(target.width() / window.width, target.height() / window.height)
        draw(canvas, frame, target.centerX() - window.centerX * scale, target.centerY() - window.centerY * scale, scale,
            ink, 1f, window.width / 2f)
    }

    /**
     * Strokes a posed [f] in canvas units mapped through [ox], [oy] and [scale], at the side clips' weights ([boost]
     * thickens the figure as the skill badges do). [floorHalf] lays a floor that far either side of the centre, with the
     * bar's [posts] down to it; 0 draws neither.
     */
    fun draw(
        canvas: Canvas, f: Frame, ox: Float, oy: Float, scale: Float, ink: Ink, boost: Float, floorHalf: Float,
        posts: Boolean = true,
    ) {
        fun x(v: Float) = ox + v * scale
        fun y(v: Float) = oy + v * scale
        fun line(x0: Float, y0: Float, x1: Float, y1: Float, paint: Paint) = canvas.drawLine(x(x0), y(y0), x(x1), y(y1), paint)

        rig(canvas, CENTER, f.barY, f.barHalf, floorHalf, ox, oy, scale, ink, posts)
        if (f.boxHalf > 0f) {
            val radius = 2.5f * scale
            val top = FLOOR_Y - f.boxHeight
            ink.fill?.let { canvas.drawRoundRect(x(CENTER - f.boxHalf), y(top), x(CENTER + f.boxHalf), y(FLOOR_Y), radius, radius, it) }
            ink.bar.strokeWidth = 1.7f * scale
            canvas.drawRoundRect(x(CENTER - f.boxHalf), y(top), x(CENTER + f.boxHalf), y(FLOOR_Y), radius, radius, ink.bar)
        }
        if (f.towelBottom > 0f) {
            // A towel looped over the bar: two strands, as the side clip draws it.
            ink.bar.strokeWidth = 1.7f * scale
            line(f.towelX - 1.3f, f.barY, f.towelX - 1.3f, f.towelBottom, ink.bar)
            line(f.towelX + 1.3f, f.barY, f.towelX + 1.3f, f.towelBottom, ink.bar)
        }

        ink.limb.strokeWidth = 2.6f * boost * scale
        val dx = f.neckX - f.pelvisX
        val dy = f.neckY - f.pelvisY
        val length = hypot(dx, dy).coerceAtLeast(0.01f)
        val ux = dx / length
        val uy = dy / length
        fun tipX(side: Float) = f.neckX - side * SHOULDER * uy + ux * f.shrug
        fun tipY(side: Float) = f.neckY + side * SHOULDER * ux + uy * f.shrug
        // Thigh and shin, as the side clips draw them: standing with the knees bending out, or hanging a little apart.
        if (!f.prone) for (side in SIDES) {
            val hipX = f.pelvisX + side * HIP
            val footX = if (side < 0f) f.leftFootX else f.rightFootX
            val footY = if (side < 0f) f.leftFootY else f.rightFootY
            if (f.grounded) {
                limb(hipX, f.pelvisY, footX, footY, side, THIGH, SHIN, f.spread, ink.limb, ::line)
                line(footX, footY, footX + side * 2f, footY + FLOOR_Y - ANKLE_Y - 0.4f, ink.limb)
            } else {
                val knee = f.pelvisX + side * HANG_KNEE
                line(hipX, f.pelvisY, knee, f.pelvisY + THIGH, ink.limb)
                line(knee, f.pelvisY + THIGH, footX, footY, ink.limb)
            }
        }
        // Torso, shoulders (a tent when they shrug toward the ears), neck and head.
        line(f.pelvisX, f.pelvisY, f.neckX, f.neckY, ink.limb)
        line(tipX(-1f), tipY(-1f), f.neckX, f.neckY, ink.limb)
        line(f.neckX, f.neckY, tipX(1f), tipY(1f), ink.limb)
        val headRadius = HEAD_RADIUS * scale * (1f + (boost - 1f) * 0.4f)
        if (f.prone) {
            // Seen head-on the head sits just over the shoulders, with no neck showing.
            canvas.drawCircle(x(f.neckX + ux * HEAD_RADIUS * 0.8f), y(f.neckY + uy * HEAD_RADIUS * 0.8f), headRadius, ink.head)
        } else {
            line(f.neckX, f.neckY, f.neckX + ux * NECK, f.neckY + uy * NECK, ink.limb)
            canvas.drawCircle(x(f.neckX + ux * (NECK + HEAD_RADIUS)), y(f.neckY + uy * (NECK + HEAD_RADIUS)), headRadius, ink.head)
        }
        // Arms: two bones to each hand, the elbows bending outward; a hand that let go hangs by the side.
        for (side in SIDES) {
            val sx = tipX(side)
            val sy = tipY(side)
            val holds = if (side < 0f) f.leftHolds else f.rightHolds
            var hx = if (side < 0f) f.leftX else f.rightX
            var hy = if (side < 0f) f.leftY else f.rightY
            when {
                !holds -> {
                    hx = sx + side * 3f
                    hy = sy + ARM
                    line(sx, sy, sx + side * 2.5f, sy + UPPER_ARM, ink.limb)
                    line(sx + side * 2.5f, sy + UPPER_ARM, hx, hy, ink.limb)
                }
                f.reach -> line(sx, sy, hx, hy, ink.limb)
                f.grip -> {
                    val joint = elbow(sx, sy, hx, hy, side)
                    if (joint == null) line(sx, sy, hx, hy, ink.limb) else {
                        line(sx, sy, joint.first, joint.second, ink.limb)
                        line(joint.first, joint.second, hx, hy, ink.limb)
                    }
                }
                else -> limb(sx, sy, hx, hy, side, UPPER_ARM, FOREARM, f.spread, ink.limb, ::line)
            }
            hand(canvas, x(hx), y(hy), scale, ink)
        }
        // Lying on the back, the legs are nearest the viewer: thighs up to the knees and shins back down to the feet.
        if (f.supine) for (side in SIDES) {
            val hipX = f.pelvisX + side * HIP
            val footX = if (side < 0f) f.leftFootX else f.rightFootX
            val kneeX = (hipX + footX) / 2f + sin(f.kneeTurn) * KNEE_UP
            val kneeY = ANKLE_Y - cos(f.kneeTurn) * KNEE_UP
            line(hipX, f.pelvisY, kneeX, kneeY, ink.limb)
            line(kneeX, kneeY, footX, ANKLE_Y, ink.limb)
        }
    }

    /** The bar at [barY] (0 for none) [barHalf] either side of [centerX], and with [floorHalf] > 0 the floor and [posts]. */
    fun rig(
        canvas: Canvas, centerX: Float, barY: Float, barHalf: Float, floorHalf: Float, ox: Float, oy: Float, scale: Float,
        ink: Ink, posts: Boolean = true,
    ) {
        fun line(x0: Float, y0: Float, x1: Float, y1: Float, paint: Paint) =
            canvas.drawLine(ox + x0 * scale, oy + y0 * scale, ox + x1 * scale, oy + y1 * scale, paint)
        if (floorHalf > 0f) {
            ink.rig.strokeWidth = 1.4f * scale
            line(centerX - floorHalf, FLOOR_Y, centerX + floorHalf, FLOOR_Y, ink.rig)
            if (barY > 0f && posts) {
                line(centerX - barHalf + 3f, barY, centerX - barHalf + 3f, FLOOR_Y, ink.rig)
                line(centerX + barHalf - 3f, barY, centerX + barHalf - 3f, FLOOR_Y, ink.rig)
            }
        }
        if (barY > 0f) {
            ink.bar.strokeWidth = 1.7f * scale
            line(centerX - barHalf, barY, centerX + barHalf, barY, ink.bar)
        }
    }

    /** Widens [b] (minX, minY, maxX, maxY) to take in [f]'s head, shoulders, hands, hips and feet. */
    fun bounds(f: Frame, b: FloatArray) {
        fun take(x: Float, y: Float) {
            b[0] = min(b[0], x)
            b[1] = min(b[1], y)
            b[2] = max(b[2], x)
            b[3] = max(b[3], y)
        }
        val dx = f.neckX - f.pelvisX
        val dy = f.neckY - f.pelvisY
        val length = hypot(dx, dy).coerceAtLeast(0.01f)
        val head = if (f.prone) HEAD_RADIUS * 0.8f else NECK + HEAD_RADIUS
        take(f.neckX + dx / length * head, f.neckY + dy / length * head)
        take(f.neckX - SHOULDER, f.neckY - f.shrug)
        take(f.neckX + SHOULDER, f.neckY - f.shrug)
        take(f.pelvisX, f.pelvisY)
        take(if (f.leftHolds) f.leftX else f.neckX - SHOULDER - 3f, if (f.leftHolds) f.leftY else f.neckY + ARM)
        take(if (f.rightHolds) f.rightX else f.neckX + SHOULDER + 3f, if (f.rightHolds) f.rightY else f.neckY + ARM)
        if (!f.prone) {
            take(f.leftFootX, f.leftFootY)
            take(f.rightFootX, f.rightFootY)
        }
    }

    /**
     * A side clip seen from the front, for movements without a front motion of their own. Each joint keeps its height
     * and its distance toward the viewer, which only decides what covers what; shoulders, hips and feet take their usual
     * width, and hands the width their grip or support gives them.
     */
    class Projection {
        val x = FloatArray(POINTS)
        val y = FloatArray(POINTS)
        internal val depth = FloatArray(PARTS)
        internal val order = IntArray(PARTS)
    }

    /** A hand's distance from the centre line: by the side, unless [contact] (0..1) puts it on a support [width] out. */
    fun handWidth(contact: Float, width: Float): Float = lerp(SHOULDER + 1.5f, width, contact.coerceIn(0f, 1f))

    /** How much a hand at [handY] rests on a support at [supportY]: fully within 2 units, not at all past 7. */
    fun contact(handY: Float, supportY: Float): Float = 1f - ease((abs(handY - supportY) - 2f) / 5f)

    /**
     * Projects solved side-clip [joints] (Rig order) for a viewer in front, where the clip [facing] looks: the near limbs
     * on the viewer's left when it faces right. [nearHand] and [farHand] are the hands' widths ([handWidth]). [width]
     * narrows the figure: a clip that already faces the viewer, seen end-on along the body, stacks up nearly in line.
     */
    fun project(joints: FloatArray, facing: Float, nearHand: Float, farHand: Float, p: Projection, width: Float = 1f) {
        fun jx(joint: Int) = joints[joint * 2]
        fun jy(joint: Int) = joints[joint * 2 + 1]
        fun toward(joint: Int) = facing * jx(joint)
        fun put(point: Int, lateral: Float, joint: Int) {
            p.x[point] = CENTER + lateral * width
            p.y[point] = jy(joint)
        }
        // A bent arm or leg shows its elbow or knee a little out to the side; straight, it runs root to tip.
        fun bend(root: Int, tip: Int, length: Float) = (1f - hypot(jx(tip) - jx(root), jy(tip) - jy(root)) / length).coerceIn(0f, 1f)
        val near = -facing
        put(PELVIS, 0f, Rig.HIP)
        put(NECK_POINT, 0f, Rig.NECK)
        put(HEAD, 0f, Rig.HEAD)
        for (side in 0..1) {
            val s = if (side == 0) near else -near
            val elbow = if (side == 0) Rig.ELBOW_NEAR else Rig.ELBOW_FAR
            val hand = if (side == 0) Rig.HAND_NEAR else Rig.HAND_FAR
            val knee = if (side == 0) Rig.KNEE_NEAR else Rig.KNEE_FAR
            val foot = if (side == 0) Rig.FOOT_NEAR else Rig.FOOT_FAR
            val toe = if (side == 0) Rig.TOE_NEAR else Rig.TOE_FAR
            val width = if (side == 0) nearHand else farHand
            val base = side * LIMB_POINTS
            put(TIP + side, s * SHOULDER, Rig.SHOULDER)
            put(base + ELBOW, s * ((SHOULDER + width) / 2f + 6f * bend(Rig.SHOULDER, hand, ARM)), elbow)
            put(base + HAND, s * width, hand)
            put(base + HIP_POINT, s * HIP, Rig.HIP)
            // Feet a little apart when standing or hanging, together once they are over the hips (a handstand).
            val inverted = ease((jy(Rig.HIP) - jy(foot)) / 20f)
            val stance = lerp(STANCE, HIP - 0.3f, inverted)
            put(base + KNEE, s * ((HIP + stance) / 2f + 5f * bend(Rig.HIP, foot, LEG)), knee)
            put(base + FOOT, s * stance, foot)
            put(base + TOE, s * (stance + 2f * (1f - inverted)), toe)
            p.depth[ARM_PART + side * 2] = (toward(Rig.SHOULDER) + toward(elbow)) / 2f
            p.depth[ARM_PART + side * 2 + 1] = (toward(elbow) + toward(hand)) / 2f
            p.depth[LEG_PART + side * 2] = (toward(Rig.HIP) + toward(knee)) / 2f
            p.depth[LEG_PART + side * 2 + 1] = (toward(knee) + toward(foot)) / 2f
        }
        p.depth[TORSO_PART] = (toward(Rig.HIP) + toward(Rig.NECK)) / 2f
        p.depth[SHOULDER_PART] = toward(Rig.SHOULDER)
        p.depth[HEAD_PART] = toward(Rig.HEAD)
        // Far parts first, so whatever is nearer the viewer is painted over them.
        for (i in 0 until PARTS) {
            var at = i
            while (at > 0 && p.depth[p.order[at - 1]] > p.depth[i]) {
                p.order[at] = p.order[at - 1]
                at--
            }
            p.order[at] = i
        }
    }

    /**
     * Strokes a projected figure the way [draw] strokes a front motion, mapped through [ox], [oy] and [scale]. Parts well
     * behind the shoulders, such as the legs of a push-up seen head-on, take the [far] paint the side view gives its far
     * limbs.
     */
    fun drawProjected(canvas: Canvas, p: Projection, ox: Float, oy: Float, scale: Float, ink: Ink, far: Paint, boost: Float = 1f) {
        fun x(point: Int) = ox + p.x[point] * scale
        fun y(point: Int) = oy + p.y[point] * scale
        fun line(from: Int, to: Int, paint: Paint) = canvas.drawLine(x(from), y(from), x(to), y(to), paint)
        ink.limb.strokeWidth = 2.6f * boost * scale
        far.strokeWidth = 2.2f * boost * scale
        val behind = p.depth[SHOULDER_PART] - FAR_BEHIND
        for (i in 0 until PARTS) {
            val part = p.order[i]
            val paint = if (p.depth[part] < behind) far else ink.limb
            when (part) {
                TORSO_PART -> line(PELVIS, NECK_POINT, paint)
                SHOULDER_PART -> { line(TIP, NECK_POINT, paint); line(NECK_POINT, TIP + 1, paint) }
                HEAD_PART -> {
                    line(NECK_POINT, HEAD, paint)
                    canvas.drawCircle(x(HEAD), y(HEAD), HEAD_RADIUS * scale * (1f + (boost - 1f) * 0.4f), ink.head)
                }
                else -> {
                    val side = if (part >= LEG_PART) (part - LEG_PART) / 2 else (part - ARM_PART) / 2
                    val base = side * LIMB_POINTS
                    val lower = (part - if (part >= LEG_PART) LEG_PART else ARM_PART) % 2 == 1
                    when {
                        part < LEG_PART && !lower -> line(TIP + side, base + ELBOW, paint)
                        part < LEG_PART -> {
                            line(base + ELBOW, base + HAND, paint)
                            hand(canvas, x(base + HAND), y(base + HAND), scale, ink)
                        }
                        !lower -> line(base + HIP_POINT, base + KNEE, paint)
                        else -> { line(base + KNEE, base + FOOT, paint); line(base + FOOT, base + TOE, paint) }
                    }
                }
            }
        }
    }

    // Projection points (internal for tests): the centre line, the shoulder tips, then each side's limb points from the
    // near side.
    internal const val PELVIS = 0
    internal const val NECK_POINT = 1
    internal const val HEAD = 2
    internal const val TIP = 3
    internal const val LIMB_START = 5
    internal const val ELBOW = LIMB_START
    internal const val HAND = LIMB_START + 1
    internal const val HIP_POINT = LIMB_START + 2
    internal const val KNEE = LIMB_START + 3
    internal const val FOOT = LIMB_START + 4
    internal const val TOE = LIMB_START + 5
    internal const val LIMB_POINTS = 6
    internal const val POINTS = LIMB_START + 2 * LIMB_POINTS
    // Parts painted far to near: torso, shoulders, head, then upper and lower arm and leg on each side.
    internal const val TORSO_PART = 0
    internal const val SHOULDER_PART = 1
    internal const val HEAD_PART = 2
    internal const val ARM_PART = 3
    internal const val LEG_PART = 7
    internal const val PARTS = 11
    /** How high bent knees stand over the feet when lying on the back. */
    private const val KNEE_UP = 14f
    /** The ring's width around a faint hand dot. */
    const val HAND_RING = 0.6f
    /** Each foot's distance from the centre line when standing, as the front motions stand. */
    private const val STANCE = 3f
    /** How far behind the shoulders a part is drawn as a far one. */
    private const val FAR_BEHIND = 14f

    private val SIDES = floatArrayOf(-1f, 1f)

    private fun limb(
        sx: Float, sy: Float, hx: Float, hy: Float, side: Float, upper: Float, lower: Float, spread: Float, paint: Paint,
        line: (Float, Float, Float, Float, Paint) -> Unit,
    ) {
        val joint = joint(sx, sy, hx, hy, side, upper, lower, spread)
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
        if (hypot(hx - sx, hy - sy) >= ARM) return null
        val ex = hx + side
        val reach = UPPER_ARM * UPPER_ARM - (ex - sx) * (ex - sx)
        if (reach >= 0f) {
            val rise = sy - hy
            val drop = min(FOREARM, rise + sqrt(reach))
            if (drop >= max(0f, rise - sqrt(reach))) return ex to hy + drop
        }
        return joint(sx, sy, hx, hy, side, UPPER_ARM, FOREARM, 1f)
    }

    /**
     * The middle joint of two bones [upper] and [lower] from root to tip, bending outward; null when straight. [spread]
     * below 1 swings it toward the viewer, so it shows that much less to the side.
     */
    private fun joint(
        sx: Float, sy: Float, hx: Float, hy: Float, side: Float, upper: Float, lower: Float, spread: Float,
    ): Pair<Float, Float>? {
        val dx = hx - sx
        val dy = hy - sy
        val d = hypot(dx, dy)
        if (d >= upper + lower || d < 0.01f) return null
        // Law of cosines: how far along the root-tip line the joint sits, and how far off it.
        val along = (upper * upper - lower * lower + d * d) / (2f * d)
        val off = sqrt(max(0f, upper * upper - along * along)) * spread
        var px = -dy / d
        var py = dx / d
        if (px * side < 0f) { px = -px; py = -py }
        return (sx + dx / d * along + px * off) to (sy + dy / d * along + py * off)
    }
}
