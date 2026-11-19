package com.justdataplease.streetgym.app.figure

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import com.justdataplease.streetgym.app.theme.Palette
import com.justdataplease.streetgym.app.theme.blendColor
import com.justdataplease.streetgym.app.theme.contrast
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Colours for the static skill-badge rendering (Android: ExerciseAnimationView.BadgeStyle). */
class BadgeStyle(val background: Color, val figure: Color, val farFigure: Color, val apparatus: Color)

/** Where a text is drawn from its x, as Android's Paint.Align. */
internal enum class Align { LEFT, CENTER, RIGHT }

/** A text paint as Android's (size in px, letter spacing in em, colour, alignment); measured and drawn by [FigureText]. */
internal class TextPen(val sizePx: Float, val letterSpacing: Float, var color: Color, var align: Align = Align.LEFT)

/**
 * The guide's words on the canvas: Android's Paint.measureText, ascent, descent and drawText(text, x, baseline), through
 * Compose's text measurer in [family]: Roboto Bold, the system's bold face Android paints them in (Typeface.DEFAULT_BOLD),
 * so a caption fits, or is left out, at the same widths as on Android.
 */
class FigureText(private val measurer: TextMeasurer, private val family: FontFamily, private val density: Density) {
    internal fun layout(text: String, pen: TextPen): TextLayoutResult = measurer.measure(
        text,
        TextStyle(fontFamily = family, fontSize = with(density) { pen.sizePx.toSp() }, letterSpacing = pen.letterSpacing.em),
        maxLines = 1,
        softWrap = false,
    )

    internal fun measureText(text: String, pen: TextPen): Float = layout(text, pen).size.width.toFloat()

    /** Above the baseline, negative, as Android's Paint.ascent(). */
    internal fun ascent(pen: TextPen): Float = -layout(SAMPLE, pen).firstBaseline

    internal fun descent(pen: TextPen): Float = layout(SAMPLE, pen).let { it.size.height - it.firstBaseline }

    internal fun draw(scope: DrawScope, text: String, x: Float, baseline: Float, pen: TextPen) {
        val layout = layout(text, pen)
        val width = layout.size.width
        val left = when (pen.align) {
            Align.LEFT -> x
            Align.CENTER -> x - width / 2f
            Align.RIGHT -> x - width
        }
        scope.drawText(layout, pen.color, Offset(left, baseline - layout.firstBaseline))
    }

    private companion object {
        const val SAMPLE = "Hg"
    }
}

/** How a bound movement is drawn: one view, its own front view beside it, seen from the front, or already facing. */
enum class FigureLayout { SINGLE, BOTH, PROJECTED, FACING }

/**
 * The movement guide, ported from Android's ExerciseAnimationView (android-streetgym, ui/ExerciseAnimationView.kt): the
 * same binding, the same four layouts, range line, captions, phase label and tempo strip, the same skill badge, at the
 * same sizes in dp. Android's View draws itself; here [draw] paints one frame into a DrawScope at the loop's [phase]
 * and [cycle] (finished loops, for movements that alternate sides), which the screen's frame clock moves on.
 *
 * Colours come from the theme's [palette], as Android reads AppColors when the view is made; [density] is pixels per
 * dp; [words] gives each [MotionText] in the app's language.
 */
class FigureRenderer(
    private val library: AnimationLibrary,
    palette: Palette,
    private val density: Float,
    private val words: (MotionText) -> String,
) {
    private var resolved = library.fallback
    private var animationPhase = resolved.template.startPhase

    private val primary = palette.primary
    private val textSecondary = palette.textSecondary
    private val divider = palette.divider

    private val backgroundPaint = fillPaint(palette.surface)
    private val borderPaint = strokePaint(palette.divider, round = false).apply { strokeWidth = dp(1f) }
    private val figurePaint = strokePaint(palette.text)
    private val farLimbPaint = figurePaint.copy().apply { color = blendColor(palette.text, palette.surface, 0.42f) }
    private val headPaint = fillPaint(palette.text)
    private val handPaint = fillPaint(palette.palm)
    /** Rings the hands in the text colour where the palm colour is faint on the panel (Concrete's tape yellow). */
    private val handRingPaint = strokePaint(
        if (contrast(palette.palm, palette.surface) < 3f) palette.text else Color.Transparent, round = false,
    )
    private val apparatusPaint = strokePaint(palette.textSecondary)
    private val apparatusFillPaint = fillPaint(blendColor(palette.textSecondary, palette.surface, 0.85f))
    private val floorPaint = apparatusPaint.copy().apply { color = palette.divider }
    private val accentPaint = apparatusPaint.copy().apply { color = palette.primary }
    private val phaseTextPaint = TextPen(dp(11f), 0.08f, palette.primary)
    private val stripPaint = strokePaint(palette.divider, round = false).apply {
        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        strokeWidth = dp(3f)
    }
    private val markerPaint = strokePaint(palette.textSecondary, round = false)
    private val markerTextPaint = TextPen(dp(10f), 0f, palette.textSecondary)
    private var markerDash: PathEffect? = null
    private var markerDashScale = 0f
    private val torsoPath = Path()
    private val primitivePath = Path()
    private val joints = FloatArray(Rig.JOINT_COUNT * 2)
    private val blendedPose = Pose()

    private var badge: BadgeStyle? = null
    private val badgeClip = Path()
    /** A moving badge plays the clip in its tile, framed on the whole cycle so the figure doesn't zoom. */
    private var badgeMoves = false
    private var badgeBounds: FloatArray? = null

    /**
     * Bar work and sideways movements also show from the front ([FrontView]), so the grip and any shift to a side show;
     * guides put the side clip beside it.
     */
    private var front: FrontView.Variant? = null
    /** The side clip shown beside the front view, and the clip whose range line the two views share. */
    private var sideTemplate = resolved.template
    private var markerTemplate = resolved.template
    /** How far the side clip moves to sit in the left half of a two-view guide; NaN until measured. */
    private var sideShift = Float.NaN
    private val frontFrame = FrontView.Frame()
    private val frontInk by lazy {
        FrontInk(figurePaint, headPaint, handPaint, apparatusPaint, floorPaint, apparatusFillPaint, handRingPaint)
    }
    /** Finished loops, so a movement done to alternate sides switches every other rep. */
    private var cycle = 0
    private val captionPaint = TextPen(dp(9f), 0.12f, palette.textSecondary, Align.CENTER)
    private val sideCaption = words(MotionText.VIEW_SIDE).uppercase()
    private val frontCaption = words(MotionText.VIEW_FRONT).uppercase()
    private val endCaption = words(MotionText.VIEW_END).uppercase()

    /** Every other movement shows its side clip seen from the front too ([FrontView.project]), unless it [faces]. */
    private var projected = false
    /**
     * The clip already faces the viewer ([FACING_CLIPS]): it plays in the front half, beside its own side clip or, for
     * planks and flags, itself seen end-on along the body.
     */
    private var faces = false
    private var facingSide: MotionTemplate? = null
    /** Where the facing clip's side clip sits in the left half, and where its hands hold the bar; NaN until measured. */
    private var facingSideShift = Float.NaN
    private var facingSideGrip = Float.NaN
    private val projection = FrontView.Projection()
    /** The clip's facing at its start, which puts the front viewer at that end. */
    private var projectFacing = 1f
    /** The front view's scene, read from the clip's apparatus: a bar across, dip bars, handles, and boxes (top, half width). */
    private var frontBarY = 0f
    private var dipY = 0f
    private var handleY = 0f
    private val boxes = FloatArray(MAX_BOXES * 2)
    private var boxCount = 0
    /**
     * The side half's bar and posts: centred on the grip and wide enough that the whole movement, a lever's body
     * included, stays between the posts. NaN until measured.
     */
    private var sideBarX = Float.NaN
    private var sideBarHalf = SIDE_BAR_HALF
    /** A post the hands hold ([postX] in the side clip, NaN for none), and where the front view stands it. */
    private var postX = Float.NaN
    private var postTop = 0f
    private var postLateral = 0f
    /** Which hands hold the post; with both on it the hands meet at a post in front of the body. */
    private var nearOnPost = false
    private var farOnPost = false

    // Playback frame, resolved once per draw.
    private var frameTimeline = 0f
    private var frameSegment = -1
    private var frameProgress = 0f
    private var phaseLabels = emptyArray<String>()
    private var ownPhaseLabels: Array<String>? = null
    private var markerLabel = ""
    private var markerLabelReached = ""

    // The frame being drawn: Android's onDraw gets its Canvas; text goes through the DrawScope on the same canvas.
    private var running = false
    private lateinit var scope: DrawScope
    private lateinit var text: FigureText

    init {
        updateCoachingText()
    }

    /**
     * Selects an animation. A stable catalog movement ID gives the most precise result. Exercises without one fall back
     * through exact English aliases, then JSON name/category/equipment heuristics.
     */
    fun bind(
        movementId: String?,
        exerciseName: String,
        category: String? = null,
        equipment: Collection<String> = emptyList(),
        descriptor: String? = null,
    ): FigureRenderer {
        val readableName = exerciseName.trim().ifBlank { "this exercise" }
        resolved = library.resolve(
            movementId = movementId,
            exerciseName = readableName,
            descriptor = descriptor.orEmpty(),
            category = category,
            equipment = equipment,
        )
        front = FrontView.variant(movementId, resolved.template.name)
        sideTemplate = front?.sideClip?.let(library::template) ?: resolved.template
        markerTemplate = front?.let { it.marker ?: it.sideClip }?.let(library::template) ?: sideTemplate
        faces = front == null && resolved.template.name in FACING_CLIPS
        facingSide = if (faces) FACING_CLIPS[resolved.template.name]?.let(library::template) else null
        facingSideShift = Float.NaN
        projected = front == null && !faces
        if (projected || faces) readFrontScene()
        sideShift = Float.NaN
        sideBarX = Float.NaN
        sideBarHalf = SIDE_BAR_HALF
        cycle = 0
        badgeBounds = null
        updateCoachingText()
        animationPhase = resolved.template.startPhase
        return this
    }

    /**
     * Turns the guide into a skill badge: a flat rounded tile with the movement scaled to fill it, heavier strokes and
     * no panel or overlays. A static badge holds the end pose; a [moving] one (the exercise picker, a workout's tile)
     * plays it.
     */
    fun setBadgeStyle(style: BadgeStyle, moving: Boolean = false): FigureRenderer {
        badge = style
        backgroundPaint.color = style.background
        figurePaint.color = style.figure
        headPaint.color = style.figure
        handPaint.color = style.figure
        handRingPaint.color = Color.Transparent
        farLimbPaint.color = style.farFigure
        apparatusPaint.color = style.apparatus
        floorPaint.color = style.apparatus
        apparatusFillPaint.color = blendColor(style.apparatus, style.background, 0.6f)
        badgeMoves = moving
        badgeBounds = null
        return this
    }

    /** The clip it plays: its name in the animations file. */
    val clipName: String get() = resolved.template.name

    /** How the movement is laid out. */
    val layout: FigureLayout get() = when {
        front != null -> FigureLayout.BOTH
        projected -> FigureLayout.PROJECTED
        faces -> FigureLayout.FACING
        else -> FigureLayout.SINGLE
    }

    /** Whether it plays at all: a guide and a moving badge do; a still badge holds its end pose. */
    val plays: Boolean get() = badge == null || badgeMoves

    /** One loop of the bound clip and where it starts. */
    val loopDurationMs: Long get() = resolved.template.cycleDurationMs
    val loopStartPhase: Float get() = resolved.template.startPhase

    /**
     * Draws one frame into [scope]: at [phase] of the loop and [cycle] loops in while [playing] (with the phase label
     * and tempo strip), or the clip's still frame when not (reduced motion, out of sight).
     */
    fun draw(scope: DrawScope, text: FigureText, phase: Float, cycle: Int, playing: Boolean) {
        this.scope = scope
        this.text = text
        this.running = playing
        this.animationPhase = if (playing) phase else resolved.template.startPhase
        this.cycle = cycle
        onDraw(scope.drawContext.canvas, scope.size.width, scope.size.height)
    }

    private fun onDraw(canvas: Canvas, width: Float, height: Float) {
        if (badge != null) {
            drawBadge(canvas, width, height)
            return
        }
        // Android's padding: 10 dp at the sides, 8 dp above and below.
        val left = dp(10f)
        val top = dp(8f)
        val right = width - dp(10f)
        val bottom = height - dp(8f)
        if (right <= left || bottom <= top) return

        val corner = dp(12f)
        canvas.drawRoundRect(left, top, right, bottom, corner, corner, backgroundPaint)
        canvas.drawRoundRect(left, top, right, bottom, corner, corner, borderPaint)

        // Uniform scale: the logical canvas keeps its aspect ratio and is centered.
        val innerWidth = (right - left - dp(8f)).coerceAtLeast(1f)
        val innerHeight = (bottom - top - dp(6f)).coerceAtLeast(1f)
        val scale = min(innerWidth / library.canvasWidth, innerHeight / library.canvasHeight)
        val originX = left + (right - left - library.canvasWidth * scale) * 0.5f
        val originY = top + (bottom - top - library.canvasHeight * scale) * 0.5f

        val running = isAnimationRunning()
        resolveFrame(running)
        val variant = front
        canvas.save()
        canvas.clipRect(left, top, right, bottom)
        when {
            variant == null && projected -> drawProjectedViews(canvas, originX, originY, scale, left, top, right, bottom)
            variant == null && faces -> drawFacingViews(canvas, originX, originY, scale, left, top, right, bottom)
            variant == null -> {
                resolved.template.curve.evaluate(frameTimeline, blendedPose, joints)
                drawApparatus(canvas, resolved.apparatus, originX, originY, scale)
                drawRangeMarker(canvas, originX, originY, scale, right - dp(6f))
                drawFigure(canvas, originX, originY, scale)
            }
            else -> drawBothViews(canvas, variant, originX, originY, scale, left, top, right, bottom)
        }
        canvas.restore()
        if (running) drawPhase(canvas, left, top, right, bottom)
    }

    /**
     * The side clip in the left half and the front view in the right, each with its own bar or floor, at the scale every
     * guide uses and at the same moment, joined by the range line and captioned.
     */
    private fun drawBothViews(
        canvas: Canvas, variant: FrontView.Variant, ox: Float, oy: Float, scale: Float,
        left: Float, top: Float, right: Float, bottom: Float,
    ) {
        val shift = sideShift(variant)
        evaluateSide(variant, frameTimeline)
        poseFront(variant, frameTimeline, mirrored(variant), sideReady = true)
        val mid = ox + HALF * scale
        val sideX = ox + shift * scale
        canvas.save()
        canvas.clipRect(left, top, mid, bottom)
        FrontView.rig(canvas, HALF / 2f - shift, frontFrame.barY, SIDE_BAR_HALF, HALF_FLOOR, sideX, oy, scale, frontInk)
        drawApparatus(canvas, if (variant.sideClip != null) sideTemplate.apparatus else resolved.apparatus, sideX, oy, scale,
            skipFrame = true)
        drawFigure(canvas, sideX, oy, scale)
        canvas.restore()
        canvas.save()
        canvas.clipRect(mid, top, right, bottom)
        FrontView.draw(canvas, frontFrame, ox + HALF / 2f * scale, oy, scale, frontInk, 1f, HALF_FLOOR)
        canvas.restore()
        drawSharedMarker(canvas, ox, oy, scale, frontFrame.barY, frontFrame.barHalf)
        drawCaptions(canvas, ox, oy, scale, left, right, bottom)
    }

    /**
     * The same two views for a movement without a front motion of its own: the side clip in the left half, and in the
     * right the clip seen from the front with its bar, dip bars, handles or boxes, at the same moment and scale.
     */
    private fun drawProjectedViews(
        canvas: Canvas, ox: Float, oy: Float, scale: Float, left: Float, top: Float, right: Float, bottom: Float,
    ) {
        val shift = projectedShift()
        resolved.template.curve.evaluate(frameTimeline, blendedPose, joints)
        val mid = ox + HALF * scale
        val sideX = ox + shift * scale
        canvas.save()
        canvas.clipRect(left, top, mid, bottom)
        FrontView.rig(canvas, HALF / 2f - shift, 0f, 0f, HALF_FLOOR, sideX, oy, scale, frontInk)
        if (frontBarY > 0f) FrontView.rig(canvas, sideBarX, frontBarY, sideBarHalf, sideBarHalf, sideX, oy, scale, frontInk)
        drawApparatus(canvas, resolved.apparatus, sideX, oy, scale, skipFrame = true)
        drawFigure(canvas, sideX, oy, scale)
        canvas.restore()

        val frontX = ox + HALF / 2f * scale
        canvas.save()
        canvas.clipRect(mid, top, right, bottom)
        drawFrontScene(canvas, frontX, oy, scale)
        FrontView.project(joints, projectFacing, handWidth(Rig.HAND_NEAR), handWidth(Rig.HAND_FAR), projection)
        FrontView.drawProjected(canvas, projection, frontX, oy, scale, frontInk, farLimbPaint)
        canvas.restore()
        drawSharedMarker(canvas, ox, oy, scale, frontBarY, FRONT_BAR_HALF)
        drawCaptions(canvas, ox, oy, scale, left, right, bottom)
    }

    /**
     * A clip that already faces the viewer ([faces]) in the right half, captioned as the front, and beside it in the left
     * half its own side clip or, without one, the clip seen end-on along the body ([END_WIDTH]).
     */
    private fun drawFacingViews(
        canvas: Canvas, ox: Float, oy: Float, scale: Float, left: Float, top: Float, right: Float, bottom: Float,
    ) {
        val shift = projectedShift()
        val mid = ox + HALF * scale
        val side = facingSide
        canvas.save()
        canvas.clipRect(left, top, mid, bottom)
        if (side != null) {
            val sideShift = facingSideShift(side)
            val sideX = ox + sideShift * scale
            side.curve.evaluate(frameTimeline, blendedPose, joints)
            FrontView.rig(canvas, HALF / 2f - sideShift, 0f, 0f, HALF_FLOOR, sideX, oy, scale, frontInk)
            if (frontBarY > 0f) FrontView.rig(canvas, facingSideGrip, frontBarY, sideBarHalf, sideBarHalf, sideX, oy, scale, frontInk)
            drawApparatus(canvas, side.apparatus, sideX, oy, scale, skipFrame = true)
            drawFigure(canvas, sideX, oy, scale)
        } else {
            resolved.template.curve.evaluate(frameTimeline, blendedPose, joints)
            val endX = ox - HALF / 2f * scale
            drawFrontScene(canvas, endX, oy, scale)
            FrontView.project(joints, projectFacing, handWidth(Rig.HAND_NEAR), handWidth(Rig.HAND_FAR), projection, END_WIDTH)
            FrontView.drawProjected(canvas, projection, endX, oy, scale, frontInk, farLimbPaint)
        }
        canvas.restore()

        val frontX = ox + (shift + HALF) * scale
        resolved.template.curve.evaluate(frameTimeline, blendedPose, joints)
        canvas.save()
        canvas.clipRect(mid, top, right, bottom)
        FrontView.rig(canvas, HALF / 2f - shift, 0f, 0f, HALF_FLOOR, frontX, oy, scale, frontInk)
        if (frontBarY > 0f) FrontView.rig(canvas, sideBarX, frontBarY, sideBarHalf, sideBarHalf, frontX, oy, scale, frontInk)
        drawApparatus(canvas, resolved.apparatus, frontX, oy, scale, skipFrame = true)
        drawFigure(canvas, frontX, oy, scale)
        canvas.restore()
        drawSharedMarker(canvas, ox, oy, scale, frontBarY, sideBarHalf)
        drawCaptions(canvas, ox, oy, scale, left, right, bottom, if (side != null) sideCaption else endCaption)
    }

    /** Moves a facing clip's side clip into the left half, centred on its whole cycle, and finds its grip on the bar. */
    private fun facingSideShift(side: MotionTemplate): Float {
        if (!facingSideShift.isNaN()) return facingSideShift
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var gripSum = 0f
        var grips = 0
        for (step in 0..CYCLE_SAMPLES) {
            side.curve.evaluate(step / CYCLE_SAMPLES.toFloat(), blendedPose, joints)
            for (joint in 0 until BADGE_JOINTS) {
                minX = min(minX, joints[joint * 2])
                maxX = max(maxX, joints[joint * 2])
            }
            for (hand in intArrayOf(Rig.HAND_NEAR, Rig.HAND_FAR)) {
                if (frontBarY > 0f && FrontView.contact(joints[hand * 2 + 1], frontBarY) > 0.5f) {
                    gripSum += joints[hand * 2]
                    grips++
                }
            }
        }
        val center = (minX + maxX) / 2f
        facingSideGrip = if (grips > 0) gripSum / grips else center
        facingSideShift = HALF / 2f - center
        return facingSideShift
    }

    /** Reads what the front view stands on or holds from the clip's apparatus, and which end the clip faces. */
    private fun readFrontScene() {
        resolved.template.curve.evaluate(0f, blendedPose, joints)
        projectFacing = blendedPose.facing
        frontBarY = 0f
        dipY = 0f
        handleY = 0f
        boxCount = 0
        postX = Float.NaN
        for (name in resolved.apparatus) {
            val first = library.apparatus[name]?.firstOrNull() ?: continue
            when {
                name in CROSS_BARS -> frontBarY = first.values[1]
                name == "parallel_bars" -> dipY = first.values[1]
                name == "parallettes" -> handleY = first.values[1]
                name in POSTS -> {
                    postX = first.values[0]
                    postTop = first.values[1]
                }
                first.type == "round_rect" && boxCount < MAX_BOXES -> {
                    // Boxes keep their height and take a box's width; a mat a little less.
                    boxes[boxCount * 2] = first.values[1]
                    boxes[boxCount * 2 + 1] = if (first.values[3] - first.values[1] < 5f) MAT_HALF else BOX_HALF
                    boxCount++
                }
            }
        }
    }

    /** How far a hand sits from the centre line: by the side, or wider where it holds a bar or rests on a support. */
    private fun handWidth(hand: Int): Float {
        val y = joints[hand * 2 + 1]
        var width = supportWidth(y, FLOOR_HAND_Y, PRESS_WIDTH)
        width = max(width, supportWidth(y, frontBarY, GRIP_WIDTH))
        width = max(width, supportWidth(y, dipY, DIP_WIDTH))
        width = max(width, supportWidth(y, handleY, HANDLE_WIDTH))
        for (box in 0 until boxCount) width = max(width, supportWidth(y, boxes[box * 2] - 1.5f, PRESS_WIDTH))
        if (if (hand == Rig.HAND_NEAR) nearOnPost else farOnPost) {
            // Out to a post beside the body, or both hands in to one in front of it.
            val reach = 1f - FrontView.ease((abs(joints[hand * 2] - postX) - 4f) / 6f)
            width = width + ((if (nearOnPost && farOnPost) POST_GRIP_WIDTH else abs(postLateral)) - width) * reach
        }
        return width
    }

    private fun supportWidth(handY: Float, supportY: Float, width: Float): Float =
        FrontView.handWidth(if (supportY > 0f) FrontView.contact(handY, supportY) else 0f, width)

    /** The front view's floor and bar, dip bars and handles seen end-on, and boxes, behind the figure. */
    private fun drawFrontScene(canvas: Canvas, ox: Float, oy: Float, scale: Float) {
        val center = FrontView.CENTER
        FrontView.rig(canvas, center, frontBarY, if (frontBarY > 0f) FRONT_BAR_HALF else 0f, HALF_FLOOR, ox, oy, scale, frontInk)
        for (box in 0 until boxCount) {
            val half = boxes[box * 2 + 1]
            val radius = 2.5f * scale
            val boxTop = oy + boxes[box * 2] * scale
            val boxBottom = oy + FrontView.FLOOR_Y * scale
            canvas.drawRoundRect(ox + (center - half) * scale, boxTop, ox + (center + half) * scale, boxBottom, radius, radius, apparatusFillPaint)
            apparatusPaint.strokeWidth = 1.7f * scale
            canvas.drawRoundRect(ox + (center - half) * scale, boxTop, ox + (center + half) * scale, boxBottom, radius, radius, apparatusPaint)
        }
        drawEndOnBars(canvas, dipY, DIP_WIDTH, ox, oy, scale)
        drawEndOnBars(canvas, handleY, HANDLE_WIDTH, ox, oy, scale)
        if (nearOnPost || farOnPost) {
            apparatusPaint.strokeWidth = 1.7f * scale
            val x = ox + (center + postLateral) * scale
            canvas.drawLine(x, oy + postTop * scale, x, oy + FrontView.FLOOR_Y * scale, apparatusPaint)
        }
    }

    /** A pair of bars running toward the viewer at [barY], [spread] either side of the centre, each on a post. */
    private fun drawEndOnBars(canvas: Canvas, barY: Float, spread: Float, ox: Float, oy: Float, scale: Float) {
        if (barY <= 0f) return
        floorPaint.strokeWidth = 1.4f * scale
        apparatusPaint.strokeWidth = 1.7f * scale
        for (side in -1..1 step 2) {
            val x = ox + (FrontView.CENTER + side * spread) * scale
            canvas.drawLine(x, oy + (barY + 2f) * scale, x, oy + FrontView.FLOOR_Y * scale, floorPaint)
            canvas.drawCircle(x, oy + barY * scale, 2f * scale, apparatusPaint)
        }
    }

    /**
     * Moves the side clip into the left half, centred on its whole cycle (with any box, post or wall beside it when the
     * half has room), and finds where its hands hold a bar.
     */
    private fun projectedShift(): Float {
        if (!sideShift.isNaN()) return sideShift
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var gripSum = 0f
        var grips = 0
        var nearToPost = Float.MAX_VALUE
        var farToPost = Float.MAX_VALUE
        for (step in 0..CYCLE_SAMPLES) {
            resolved.template.curve.evaluate(step / CYCLE_SAMPLES.toFloat(), blendedPose, joints)
            for (joint in 0 until BADGE_JOINTS) {
                minX = min(minX, joints[joint * 2])
                maxX = max(maxX, joints[joint * 2])
            }
            for (hand in intArrayOf(Rig.HAND_NEAR, Rig.HAND_FAR)) {
                if (frontBarY > 0f && FrontView.contact(joints[hand * 2 + 1], frontBarY) > 0.5f) {
                    gripSum += joints[hand * 2]
                    grips++
                }
            }
            if (!postX.isNaN()) {
                nearToPost = min(nearToPost, abs(joints[Rig.HAND_NEAR * 2] - postX))
                farToPost = min(farToPost, abs(joints[Rig.HAND_FAR * 2] - postX))
            }
        }
        var sceneMin = minX
        var sceneMax = maxX
        for (name in resolved.apparatus) {
            if (name in FRAME_APPARATUS || name == "parallel_bars") continue
            val primitives = library.apparatus[name] ?: continue
            for (primitive in primitives) {
                val values = primitive.values
                if (primitive.type == "circle" && values.size >= 3) {
                    sceneMin = min(sceneMin, values[0] - values[2]); sceneMax = max(sceneMax, values[0] + values[2])
                } else if (values.size >= 4) {
                    sceneMin = min(sceneMin, min(values[0], values[2])); sceneMax = max(sceneMax, max(values[0], values[2]))
                }
            }
        }
        val center = if (sceneMax - sceneMin <= HALF - 6f) (sceneMin + sceneMax) / 2f else (minX + maxX) / 2f
        if (grips > 0) {
            // The posts stand clear of the whole movement, so a lever's body doesn't cross one.
            val grip = gripSum / grips
            val barLeft = min(grip - SIDE_BAR_HALF, minX - 6f)
            val barRight = max(grip + SIDE_BAR_HALF, maxX + 6f)
            sideBarX = (barLeft + barRight) / 2f
            sideBarHalf = (barRight - barLeft) / 2f
        } else {
            sideBarX = center
        }
        nearOnPost = nearToPost < POST_REACH
        farOnPost = farToPost < POST_REACH
        // One hand reaches out to a post on its side of the body; held with both, the post stands on the centre line.
        val nearSide = -projectFacing
        postLateral = when {
            nearOnPost && farOnPost -> 0f
            nearOnPost -> nearSide * POST_WIDTH
            farOnPost -> -nearSide * POST_WIDTH
            else -> 0f
        }
        sideShift = HALF / 2f - center
        return sideShift
    }

    /**
     * "Side" and "Front" under each half when the panel has room below the floor (a tall focus screen), otherwise at
     * the two outer ends of the floor in the margins a wide guide leaves; neither when there is no room.
     */
    private fun drawCaptions(
        canvas: Canvas, ox: Float, oy: Float, scale: Float, left: Float, right: Float, bottom: Float,
        leftCaption: String = sideCaption,
    ) {
        val floor = oy + FrontView.FLOOR_Y * scale
        val below = floor + dp(4f) - text.ascent(captionPaint)
        if (below + text.descent(captionPaint) < bottom - dp(11f)) {
            captionPaint.align = Align.CENTER
            drawText(leftCaption, ox + HALF / 2f * scale, below, captionPaint)
            drawText(frontCaption, ox + HALF * 1.5f * scale, below, captionPaint)
            return
        }
        val start = ox + (HALF / 2f - HALF_FLOOR) * scale - dp(4f)
        val end = ox + (HALF * 1.5f + HALF_FLOOR) * scale + dp(4f)
        if (start - text.measureText(leftCaption, captionPaint) < left + dp(6f) ||
            end + text.measureText(frontCaption, captionPaint) > right - dp(6f)) return
        val y = floor + (text.descent(captionPaint) - text.ascent(captionPaint)) / 2f - text.descent(captionPaint)
        captionPaint.align = Align.RIGHT
        drawText(leftCaption, start, y, captionPaint)
        captionPaint.align = Align.LEFT
        drawText(frontCaption, end, y, captionPaint)
    }

    /** Moves the side clip into the left half: bar work by its grip, anything else by the middle of its whole cycle. */
    private fun sideShift(variant: FrontView.Variant): Float {
        if (!sideShift.isNaN()) return sideShift
        var center = FrontView.CENTER
        if (!variant.motion.bar) {
            var minX = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            for (step in 0..CYCLE_SAMPLES) {
                sideTemplate.curve.evaluate(step / CYCLE_SAMPLES.toFloat(), blendedPose, joints)
                for (joint in 0 until BADGE_JOINTS) {
                    minX = min(minX, joints[joint * 2])
                    maxX = max(maxX, joints[joint * 2])
                }
            }
            center = (minX + maxX) / 2f
        }
        sideShift = HALF / 2f - center
        return sideShift
    }

    private fun mirrored(variant: FrontView.Variant) = variant.mirrors && cycle % 2 == 1

    private fun evaluateSide(variant: FrontView.Variant, timeline: Float) =
        sideTemplate.curve.evaluate(FrontView.sideTime(variant, timeline), blendedPose, joints)

    /**
     * Poses the front view at [timeline]; a motion that follows the side clip takes its height from the clip's joints,
     * which [sideReady] says are already evaluated at that moment.
     */
    private fun poseFront(variant: FrontView.Variant, timeline: Float, mirror: Boolean, sideReady: Boolean = false) {
        var neck = Float.NaN
        var shrug = 0f
        if (variant.motion.follows) {
            if (!sideReady) evaluateSide(variant, timeline)
            neck = joints[Rig.NECK * 2 + 1]
            shrug = neck - joints[Rig.SHOULDER * 2 + 1]
        }
        FrontView.pose(variant, timeline, frontFrame, mirror, neck, shrug)
    }

    /**
     * The range line across both views at the side clip's end height, its label in the gap between them; [barY] and
     * [frontBarHalf] are the front view's bar (0 for none).
     */
    private fun drawSharedMarker(canvas: Canvas, ox: Float, oy: Float, scale: Float, barY: Float, frontBarHalf: Float) {
        val template = markerTemplate
        val joint = template.markerJoint
        if (joint < 0) return
        val reached = abs(joints[joint * 2 + 1] - template.markerReachY) < 1.2f
        val y = oy + template.markerY * scale
        if (markerDashScale != scale) {
            markerDash = PathEffect.dashPathEffect(floatArrayOf(2.2f * scale, 2.2f * scale), 0f)
            markerDashScale = scale
        }
        markerPaint.pathEffect = if (reached) null else markerDash
        markerPaint.strokeWidth = (if (reached) 1.3f else 0.9f) * scale
        markerPaint.color = if (reached) primary else withAlpha(textSecondary, 140)
        canvas.drawLine(ox + 14f * scale, y, ox + (HALF * 2f - 14f) * scale, y, markerPaint)
        val label = if (reached) markerLabelReached else markerLabel
        markerTextPaint.color = if (reached) primary else textSecondary
        // Between the two views: under the bars, midway between their posts, when the line runs over a bar.
        val overBar = barY > 0f && template.markerY < barY
        val center = if (overBar) {
            (HALF / 2f + SIDE_BAR_HALF - 3f + HALF * 1.5f - frontBarHalf + 3f) / 2f
        } else HALF
        val textX = ox + center * scale - text.measureText(label, markerTextPaint) / 2f
        val textY = when {
            overBar -> oy + barY * scale + 2.5f * scale - text.ascent(markerTextPaint)
            template.markerY < library.canvasHeight / 2f -> y + 1.5f * scale - text.ascent(markerTextPaint)
            else -> y - 1.5f * scale
        }
        drawText(label, textX, textY, markerTextPaint)
    }

    private fun drawBadge(canvas: Canvas, w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return
        val radius = min(w, h) * 0.22f
        badgeClip.reset()
        badgeClip.addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(radius, radius)))
        canvas.drawPath(badgeClip, backgroundPaint)
        val running = badgeMoves && isAnimationRunning()
        front?.takeIf { it.badge }?.let { variant ->
            resolveFrame(running)
            // Centred as the side badges are: on this pose, or on the whole cycle when the badge moves.
            val bounds = if (badgeMoves) frontCycleBounds(variant) else frontBounds.also {
                it[0] = Float.MAX_VALUE; it[1] = Float.MAX_VALUE; it[2] = -Float.MAX_VALUE; it[3] = -Float.MAX_VALUE
                poseFront(variant, frameTimeline, mirrored(variant))
                FrontView.bounds(frontFrame, it)
            }
            val scale = badgeScale(w, h)
            poseFront(variant, frameTimeline, mirrored(variant))
            canvas.save()
            canvas.clipPath(badgeClip)
            FrontView.draw(canvas, frontFrame, w * 0.5f - (bounds[0] + bounds[2]) * 0.5f * scale,
                h * 0.5f - (bounds[1] + bounds[3]) * 0.5f * scale, scale, frontInk, BADGE_STROKE, FLOOR_HALF, posts = false)
            canvas.restore()
            return
        }

        val cycle = if (badgeMoves) cycleBounds() else null
        resolveFrame(running)
        resolved.template.curve.evaluate(frameTimeline, blendedPose, joints)
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (joint in 0 until BADGE_JOINTS) {
            minX = min(minX, joints[joint * 2])
            maxX = max(maxX, joints[joint * 2])
            minY = min(minY, joints[joint * 2 + 1])
            maxY = max(maxY, joints[joint * 2 + 1])
        }
        if (cycle != null) { minX = cycle[0]; minY = cycle[1]; maxX = cycle[2]; maxY = cycle[3] }
        val scale = badgeScale(w, h)
        val ox = w * 0.5f - (minX + maxX) * 0.5f * scale
        val oy = h * 0.5f - (minY + maxY) * 0.5f * scale

        canvas.save()
        canvas.clipPath(badgeClip)
        drawApparatus(canvas, resolved.apparatus, ox, oy, scale)
        drawFigure(canvas, ox, oy, scale, BADGE_STROKE)
        canvas.restore()
    }

    private val frontBounds = FloatArray(4)

    /** One figure scale for every badge, so a pike push-up and a handstand show the same-sized athlete. */
    private fun badgeScale(w: Float, h: Float) = min(w, h) * BADGE_FILL / BADGE_SPAN

    /** The front figure's bounds over the whole clip, both sides when it alternates, sampled once per binding. */
    private fun frontCycleBounds(variant: FrontView.Variant): FloatArray =
        badgeBounds ?: floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE).also { b ->
            for (mirror in listOf(false, variant.mirrors).distinct()) for (step in 0..CYCLE_SAMPLES) {
                poseFront(variant, step / CYCLE_SAMPLES.toFloat(), mirror)
                FrontView.bounds(frontFrame, b)
            }
            badgeBounds = b
        }

    /** The body's bounds over the whole clip (minX, minY, maxX, maxY), sampled once per binding. Uses the pose scratch. */
    private fun cycleBounds(): FloatArray = badgeBounds ?: floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE).also { b ->
        for (step in 0..CYCLE_SAMPLES) {
            resolved.template.curve.evaluate(step / CYCLE_SAMPLES.toFloat(), blendedPose, joints)
            for (joint in 0 until BADGE_JOINTS) {
                b[0] = min(b[0], joints[joint * 2]); b[2] = max(b[2], joints[joint * 2])
                b[1] = min(b[1], joints[joint * 2 + 1]); b[3] = max(b[3], joints[joint * 2 + 1])
            }
        }
        badgeBounds = b
    }

    /** [skipFrame] leaves out the floor and bars, which a two-view guide draws per half. */
    private fun drawApparatus(canvas: Canvas, names: List<String>, ox: Float, oy: Float, scale: Float, skipFrame: Boolean = false) {
        for (i in names.indices) {
            if (skipFrame && names[i] in FRAME_APPARATUS) continue
            val primitives = library.apparatus[names[i]] ?: continue
            for (j in primitives.indices) drawPrimitive(canvas, primitives[j], ox, oy, scale)
        }
    }

    /** Maps the loop's phase to a pose timeline; ping-pong templates play as a tempo rep. */
    private fun resolveFrame(running: Boolean) {
        val template = resolved.template
        if (!running) {
            frameTimeline = template.timeline(template.reducedMotionFrame)
            frameSegment = -1
            return
        }
        if (!template.usesTempo) {
            frameTimeline = template.timeline(animationPhase)
            frameSegment = 0
            frameProgress = animationPhase
            return
        }
        val units = template.tempoUnits
        var start = 0f
        for (index in units.indices) {
            val end = start + units[index] / MotionTemplate.TEMPO_TOTAL
            if (animationPhase < end || index == units.lastIndex) {
                frameSegment = index
                frameProgress = ((animationPhase - start) / (end - start).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
                break
            }
            start = end
        }
        frameTimeline = when (frameSegment) {
            0 -> frameProgress
            1 -> 1f
            2 -> 1f - frameProgress
            else -> 0f
        }
    }

    private fun updateCoachingText() {
        val template = resolved.template
        phaseLabels = template.phaseLabels.labels.map { words(it).uppercase() }.toTypedArray()
        ownPhaseLabels = template.phaseSpec?.labels?.map { words(it).uppercase() }?.toTypedArray()
        // A two-view guide may borrow another clip's range line (a negative shows the pull-up's chin line).
        markerLabel = words(markerTemplate.markerLabel)
        markerLabelReached = "✓ $markerLabel"
    }

    /** A faint line at the end of the range that turns to the accent colour when the key joint reaches it. */
    private fun drawRangeMarker(canvas: Canvas, ox: Float, oy: Float, scale: Float, limit: Float) {
        val template = resolved.template
        val joint = template.markerJoint
        if (joint < 0) return
        val reached = abs(joints[joint * 2 + 1] - template.markerReachY) < 1.2f
        val y = oy + template.markerY * scale
        val centerX = ox + template.markerX * scale
        val x0 = max(ox + 10f * scale, centerX - 30f * scale)
        val x1 = min(ox + 190f * scale, centerX + 30f * scale)
        if (markerDashScale != scale) {
            markerDash = PathEffect.dashPathEffect(floatArrayOf(2.2f * scale, 2.2f * scale), 0f)
            markerDashScale = scale
        }
        markerPaint.pathEffect = if (reached) null else markerDash
        markerPaint.strokeWidth = (if (reached) 1.3f else 0.9f) * scale
        markerPaint.color = if (reached) primary else withAlpha(textSecondary, 140)
        canvas.drawLine(x0, y, x1, y, markerPaint)
        val label = if (reached) markerLabelReached else markerLabel
        markerTextPaint.color = if (reached) primary else textSecondary
        val textX = if (x1 + 3f * scale + text.measureText(label, markerTextPaint) <= limit) x1 + 3f * scale else x0
        drawText(label, textX, y - 1.5f * scale, markerTextPaint)
    }

    /** Corner label for the current phase and a segmented progress strip along the bottom. */
    private fun drawPhase(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        val template = resolved.template
        val own = template.phaseSpec
        val ownLabels = ownPhaseLabels
        // A clip's own labels (holds, slow negatives) come first; otherwise neutral movements (stretches, mobility) show
        // the strip only, as "Move / Return" adds nothing.
        val label = when {
            own != null && ownLabels != null -> ownLabels[own.indexAt(frameTimeline)]
            template.usesTempo && template.phaseLabels != PhaseLabels.MOVE && frameSegment in phaseLabels.indices ->
                phaseLabels[frameSegment]
            else -> null
        }
        if (label != null) drawText(label, left + dp(12f), top + dp(10f) - text.ascent(phaseTextPaint), phaseTextPaint)
        val units = if (template.usesTempo) template.tempoUnits else LOOP_UNITS
        val total = units.sum()
        val startX = left + dp(14f)
        val width = right - left - dp(28f)
        val y = bottom - dp(7f)
        val gap = dp(3f)
        var accumulated = 0f
        units.forEachIndexed { index, unit ->
            val a = startX + width * accumulated / total + if (index > 0) gap / 2 else 0f
            val b = startX + width * (accumulated + unit) / total - if (index < units.lastIndex) gap / 2 else 0f
            stripPaint.color = divider
            canvas.drawLine(a, y, b, y, stripPaint)
            val filled = when {
                index < frameSegment -> b
                index == frameSegment -> a + (b - a) * frameProgress
                else -> a
            }
            if (filled > a + 0.5f) {
                stripPaint.color = if (index == frameSegment) primary else withAlpha(primary, 115)
                canvas.drawLine(a, y, filled, y, stripPaint)
            }
            accumulated += unit
        }
    }

    private fun drawPrimitive(canvas: Canvas, primitive: ApparatusPrimitive, ox: Float, oy: Float, scale: Float) {
        val values = primitive.values
        val stroke = when (primitive.role) {
            "floor" -> floorPaint
            "accent" -> accentPaint
            else -> apparatusPaint
        }
        stroke.strokeWidth = (if (primitive.role == "floor") 1.4f else 1.7f) * scale
        fun x(index: Int) = ox + values[index] * scale
        fun y(index: Int) = oy + values[index] * scale
        when (primitive.type) {
            "line" -> if (values.size >= 4) canvas.drawLine(x(0), y(1), x(2), y(3), stroke)
            "polyline" -> if (values.size >= 4) {
                primitivePath.reset()
                primitivePath.moveTo(x(0), y(1))
                var index = 2
                while (index + 1 < values.size) {
                    primitivePath.lineTo(x(index), y(index + 1))
                    index += 2
                }
                canvas.drawPath(primitivePath, stroke)
            }
            "rect", "round_rect" -> if (values.size >= 4) {
                val radius = if (primitive.type == "round_rect") 2.5f * scale else 0f
                if (primitive.filled) canvas.drawRoundRect(x(0), y(1), x(2), y(3), radius, radius, apparatusFillPaint)
                canvas.drawRoundRect(x(0), y(1), x(2), y(3), radius, radius, stroke)
            }
            "circle" -> if (values.size >= 3) canvas.drawCircle(x(0), y(1), values[2] * scale, stroke)
        }
    }

    private fun drawFigure(canvas: Canvas, ox: Float, oy: Float, scale: Float, strokeBoost: Float = 1f) =
        canvas.drawLineFigure(
            joints, blendedPose.spine, blendedPose.facing, library.rig.headRadius, ox, oy, scale,
            figurePaint, farLimbPaint, headPaint, handPaint, torsoPath, strokeBoost, handRingPaint,
        )

    private fun isAnimationRunning(): Boolean = running

    private fun drawText(label: String, x: Float, baseline: Float, pen: TextPen) = text.draw(scope, label, x, baseline, pen)

    private fun dp(value: Float): Float = value * density

    private fun withAlpha(color: Color, alpha: Int): Color = color.copy(alpha = alpha / 255f)
}

private val LOOP_UNITS = floatArrayOf(1f)

/** Joints that bound the body for badge framing (the averaged hands/feet points are skipped). */
private const val BADGE_JOINTS = 14
private const val BADGE_STROKE = 1.6f
/**
 * Badges draw every figure at one scale: [BADGE_SPAN] canvas units (a hang from the bar to the toes, a lying body)
 * across [BADGE_FILL] of the tile.
 */
private const val BADGE_SPAN = 92f
private const val BADGE_FILL = 0.86f
/** Poses sampled to frame a moving badge. */
private const val CYCLE_SAMPLES = 12
/** Half the canvas width: a two-view guide's side clip fills the left half and its front view the right. */
private const val HALF = 100f
/** Floors either side of a view's centre: across the whole canvas, or across one half of a two-view guide. */
private const val FLOOR_HALF = 92f
private const val HALF_FLOOR = 46f
/** The bar in a two-view guide's side half: seen end-on its width means nothing, so every grip gets the same frame. */
private const val SIDE_BAR_HALF = 19f
/** Bars that run across the body, so a side view sees them end-on and a front view along their length. */
private val CROSS_BARS = setOf("high_bar", "mid_bar", "low_bar", "chest_bar", "assisted_bar", "gh_bar")
private val FRAME_APPARATUS = setOf("floor") + CROSS_BARS
/** The front view's bar, as a pull-up's: wide enough for the grip, with its posts clear of the body. */
private const val FRONT_BAR_HALF = 19f
/** Hands' distance from the centre line on supports: a push-up's, a bar grip, dip bars, parallettes. */
private const val PRESS_WIDTH = 8.5f
private const val GRIP_WIDTH = 11f
private const val DIP_WIDTH = 9f
private const val HANDLE_WIDTH = 8f
/** Posts a hand holds, and how close a hand comes to one that it holds. */
private val POSTS = setOf("post_left", "post_right")
private const val POST_REACH = 9f
/** A post beside the body, and the hands either side of one held in front with both. */
private const val POST_WIDTH = 12f
private const val POST_GRIP_WIDTH = 2.5f
/** Where a hand flat on the floor sits in the side clips. */
private const val FLOOR_HAND_Y = 104.5f
private const val MAX_BOXES = 3
private const val BOX_HALF = 12f
private const val MAT_HALF = 9f
/**
 * Clips drawn facing the viewer, so a front view can't be worked out from them: each with its own side clip, or null
 * for planks and flags, whose other view is the clip seen end-on along the body. The one-arm handstand line (round 11)
 * straddles, shifts and frees an arm sideways, so it is drawn from the front beside a handstand seen from the side (the
 * chest-to-wall one for the supported shift).
 */
private val FACING_CLIPS: Map<String, String?> = mapOf(
    "windshield_wiper" to "wiper_side", "pancake_compression" to "pancake_side",
    "assisted_tuck_flag" to null, "vertical_flag" to null, "tuck_flag" to null, "straddle_flag" to null, "full_flag" to null,
    "flag_negative" to null, "flag_ground_support" to null, "side_plank" to null, "straight_side_plank" to null, "kneeling_side_plank" to null,
    "straddle_handstand" to "freestanding_handstand", "supported_handstand_shift" to "wall_handstand",
    "handstand_weight_shift" to "freestanding_handstand", "fingertip_one_arm_handstand" to "freestanding_handstand",
    "one_arm_handstand_hover" to "freestanding_handstand", "straddle_one_arm_handstand" to "freestanding_handstand",
    "one_arm_handstand" to "freestanding_handstand",
)
/** How wide a figure seen end-on along its body is drawn: nearly stacked in line. */
private const val END_WIDTH = 0.35f
