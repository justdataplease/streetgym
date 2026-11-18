package com.justdataplease.streetgym.app.figure

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/*
 * The line man's drawing, ported from Android's Canvas calls to Compose's Canvas (the one under every DrawScope) with
 * the same mutable paints, so each line reads as on Android: android-streetgym, ui/ExerciseAnimationView.kt
 * (drawLineFigure) and ui/FrontView.kt (Ink, hand, draw, rig, drawProjected).
 */

/** A filled paint in [color]. */
internal fun fillPaint(color: Color): Paint = Paint().apply {
    isAntiAlias = true
    style = PaintingStyle.Fill
    this.color = color
}

/** A stroked paint in [color], with round ends and joins when [round]. */
internal fun strokePaint(color: Color, round: Boolean = true): Paint = Paint().apply {
    isAntiAlias = true
    style = PaintingStyle.Stroke
    this.color = color
    if (round) {
        strokeCap = StrokeCap.Round
        strokeJoin = StrokeJoin.Round
    }
}

/** A copy of [this] (Android's Paint(Paint)), to change its colour alone. */
internal fun Paint.copy(): Paint = Paint().also {
    it.isAntiAlias = isAntiAlias
    it.style = style
    it.color = color
    it.strokeWidth = strokeWidth
    it.strokeCap = strokeCap
    it.strokeJoin = strokeJoin
    it.pathEffect = pathEffect
}

/** Android's drawLine(x0, y0, x1, y1, paint). */
internal fun Canvas.drawLine(x0: Float, y0: Float, x1: Float, y1: Float, paint: Paint) = drawLine(Offset(x0, y0), Offset(x1, y1), paint)

/** Android's drawCircle(cx, cy, radius, paint). */
internal fun Canvas.drawCircle(x: Float, y: Float, radius: Float, paint: Paint) = drawCircle(Offset(x, y), radius, paint)

/**
 * Strokes a solved figure: lighter far-side limbs, a curved torso, a filled head and hand dots ([hand] null skips
 * them). [joints] are canvas units mapped through [ox], [oy] and [scale]; [torsoPath] is scratch.
 */
internal fun Canvas.drawLineFigure(
    joints: FloatArray,
    spine: Float,
    facing: Float,
    headRadius: Float,
    ox: Float,
    oy: Float,
    scale: Float,
    near: Paint,
    far: Paint,
    head: Paint,
    hand: Paint?,
    torsoPath: Path,
    strokeBoost: Float = 1f,
    handRing: Paint? = null,
) {
    fun x(joint: Int): Float = ox + joints[joint * 2] * scale
    fun y(joint: Int): Float = oy + joints[joint * 2 + 1] * scale
    fun bone(from: Int, to: Int, paint: Paint) = drawLine(x(from), y(from), x(to), y(to), paint)

    near.strokeWidth = 2.6f * scale * strokeBoost
    far.strokeWidth = 2.2f * scale * strokeBoost

    // Far-side limbs are lighter so the small figure keeps its depth and stays legible.
    bone(Rig.HIP, Rig.KNEE_FAR, far)
    bone(Rig.KNEE_FAR, Rig.FOOT_FAR, far)
    bone(Rig.FOOT_FAR, Rig.TOE_FAR, far)
    bone(Rig.SHOULDER, Rig.ELBOW_FAR, far)
    bone(Rig.ELBOW_FAR, Rig.HAND_FAR, far)

    val hipX = x(Rig.HIP)
    val hipY = y(Rig.HIP)
    val neckX = x(Rig.NECK)
    val neckY = y(Rig.NECK)
    val dx = neckX - hipX
    val dy = neckY - hipY
    val length = hypot(dx, dy).coerceAtLeast(1f)
    val curve = spine * scale
    val frontX = -dy / length * facing
    val frontY = dx / length * facing
    torsoPath.reset()
    torsoPath.moveTo(hipX, hipY)
    torsoPath.quadraticTo(
        (hipX + neckX) * 0.5f + frontX * curve,
        (hipY + neckY) * 0.5f + frontY * curve,
        neckX,
        neckY,
    )
    drawPath(torsoPath, near)
    bone(Rig.NECK, Rig.HEAD, near)
    drawCircle(x(Rig.HEAD), y(Rig.HEAD), headRadius * scale * (1f + (strokeBoost - 1f) * 0.4f), head)

    bone(Rig.HIP, Rig.KNEE_NEAR, near)
    bone(Rig.KNEE_NEAR, Rig.FOOT_NEAR, near)
    bone(Rig.FOOT_NEAR, Rig.TOE_NEAR, near)
    bone(Rig.SHOULDER, Rig.ELBOW_NEAR, near)
    bone(Rig.ELBOW_NEAR, Rig.HAND_NEAR, near)

    if (hand != null) {
        val handRadius = 1.6f * scale
        drawCircle(x(Rig.HAND_FAR), y(Rig.HAND_FAR), handRadius, hand)
        drawCircle(x(Rig.HAND_NEAR), y(Rig.HAND_NEAR), handRadius, hand)
        if (handRing != null) {
            handRing.strokeWidth = FrontView.HAND_RING * scale
            drawCircle(x(Rig.HAND_FAR), y(Rig.HAND_FAR), handRadius, handRing)
            drawCircle(x(Rig.HAND_NEAR), y(Rig.HAND_NEAR), handRadius, handRing)
        }
    }
}

/**
 * Paints: the figure's limbs and torso, its head, its hands, the bar and boxes, the posts and floor, box fills, and a
 * ring around each hand where its colour alone is too faint on the panel (Android: FrontView.Ink).
 */
internal class FrontInk(
    val limb: Paint, val head: Paint, val hand: Paint, val bar: Paint, val rig: Paint, val fill: Paint? = null,
    val handRing: Paint? = null,
)

/** A hand: a dot in the hand paint, ringed when the ink has a ring. */
private fun hand(canvas: Canvas, x: Float, y: Float, scale: Float, ink: FrontInk) {
    canvas.drawCircle(x, y, 1.6f * scale, ink.hand)
    ink.handRing?.let {
        it.strokeWidth = FrontView.HAND_RING * scale
        canvas.drawCircle(x, y, 1.6f * scale, it)
    }
}

/**
 * Strokes a posed [f] in canvas units mapped through [ox], [oy] and [scale], at the side clips' weights ([boost]
 * thickens the figure as the skill badges do). [floorHalf] lays a floor that far either side of the centre, with the
 * bar's [posts] down to it; 0 draws neither.
 */
internal fun FrontView.draw(
    canvas: Canvas, f: FrontView.Frame, ox: Float, oy: Float, scale: Float, ink: FrontInk, boost: Float, floorHalf: Float,
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
internal fun FrontView.rig(
    canvas: Canvas, centerX: Float, barY: Float, barHalf: Float, floorHalf: Float, ox: Float, oy: Float, scale: Float,
    ink: FrontInk, posts: Boolean = true,
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

/**
 * Strokes a projected figure the way [draw] strokes a front motion, mapped through [ox], [oy] and [scale]. Parts well
 * behind the shoulders, such as the legs of a push-up seen head-on, take the [far] paint the side view gives its far
 * limbs.
 */
internal fun FrontView.drawProjected(
    canvas: Canvas, p: FrontView.Projection, ox: Float, oy: Float, scale: Float, ink: FrontInk, far: Paint, boost: Float = 1f,
) {
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

private fun limb(
    sx: Float, sy: Float, hx: Float, hy: Float, side: Float, upper: Float, lower: Float, spread: Float, paint: Paint,
    line: (Float, Float, Float, Float, Paint) -> Unit,
) {
    val joint = FrontView.joint(sx, sy, hx, hy, side, upper, lower, spread)
    if (joint == null) {
        line(sx, sy, hx, hy, paint)
    } else {
        line(sx, sy, joint.first, joint.second, paint)
        line(joint.first, joint.second, hx, hy, paint)
    }
}
