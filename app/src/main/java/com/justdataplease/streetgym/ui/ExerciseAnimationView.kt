package com.justdataplease.streetgym.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.LinearInterpolator
import com.justdataplease.streetgym.R
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Offline line-figure movement guides backed by exercise_animations.json (schema 2).
 *
 * The figure is an articulated side-view rig with fixed bone lengths. Every pose stores
 * absolute joint angles plus one pinned joint (hands on the bar, feet on the floor), so
 * limbs never stretch and contact points stay put while the body moves between keyframes.
 * The canvas is scaled uniformly, so proportions are identical on every screen size.
 *
 * Poses are joined by a monotone spline (see [MotionCurve]), so the figure only slows where a
 * joint reverses or holds. Ping-pong templates play as a coached rep: a quick effort phase, a
 * short hold, a slower lowering and a reset, with a phase label, a progress strip and a range
 * marker that lights up when the key joint reaches the end of the movement.
 *
 * These illustrations are movement cues, not a substitute for the written coaching and
 * safety notes. The JSON owns poses, timing, and mappings so the artwork can be reviewed
 * and refined without touching rendering code (see tools/preview_animations.py).
 */
class ExerciseAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private val library = AnimationLibrary.load(context.applicationContext)
    private var resolved = library.fallback
    private var animationPhase = resolved.template.startPhase
    private var animator: ValueAnimator? = null
    private val visibleRect = Rect()
    private var scrollListenerAttached = false
    private val scrollChangedListener = ViewTreeObserver.OnScrollChangedListener {
        updateAnimationState()
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = AppColors.SURFACE
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = AppColors.DIVIDER
    }
    private val figurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = AppColors.TEXT
    }
    private val farLimbPaint = Paint(figurePaint).apply {
        color = blendColor(AppColors.TEXT, AppColors.SURFACE, 0.42f)
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = AppColors.TEXT
    }
    private val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = AppColors.PALM
    }
    /** Rings the hands in the text colour where the palm colour is faint on the panel (Concrete's tape yellow). */
    private val handRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = if (AppColors.contrast(AppColors.PALM, AppColors.SURFACE) < 3.0) AppColors.TEXT else Color.TRANSPARENT
    }
    private val apparatusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = AppColors.TEXT_SECONDARY
    }
    private val apparatusFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = blendColor(AppColors.TEXT_SECONDARY, AppColors.SURFACE, 0.85f)
    }
    private val floorPaint = Paint(apparatusPaint).apply { color = AppColors.DIVIDER }
    private val accentPaint = Paint(apparatusPaint).apply { color = AppColors.PRIMARY }
    private val phaseTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AppColors.PRIMARY
        textSize = dp(11f)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.08f
    }
    private val stripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(3f)
    }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val markerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(10f)
        typeface = Typeface.DEFAULT_BOLD
    }
    private var markerDash: DashPathEffect? = null
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
    /** A list keeps scrapped rows attached; their clips stop until the row is reused. */
    private var parked = false

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
        FrontView.Ink(figurePaint, headPaint, handPaint, apparatusPaint, floorPaint, apparatusFillPaint, handRingPaint)
    }
    /** Finished loops, so a movement done to alternate sides switches every other rep. */
    private var cycle = 0
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AppColors.TEXT_SECONDARY
        textSize = dp(9f)
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.12f
        textAlign = Paint.Align.CENTER
    }
    private val sideCaption = resources.getString(R.string.motion_view_side).uppercase(Locale.getDefault())
    private val frontCaption = resources.getString(R.string.motion_view_front).uppercase(Locale.getDefault())
    private val endCaption = resources.getString(R.string.motion_view_end).uppercase(Locale.getDefault())

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

    init {
        minimumWidth = dp(220f).roundToInt()
        minimumHeight = dp(156f).roundToInt()
        setPadding(dp(10f).roundToInt(), dp(8f).roundToInt(), dp(10f).roundToInt(), dp(8f).roundToInt())
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        isClickable = false
        isFocusable = false
        contentDescription = "Line-figure exercise movement guide"
        updateCoachingText()
    }

    /**
     * Selects an animation. A stable catalog movement ID gives the most precise result.
     * Exercises without one fall back through exact English aliases, then JSON
     * name/category/equipment heuristics.
     */
    fun bind(
        movementId: String?,
        exerciseName: CharSequence,
        category: String? = null,
        equipment: Collection<String> = emptyList(),
        descriptor: CharSequence? = null,
    ): ExerciseAnimationView {
        val readableName = exerciseName.toString().trim().ifBlank { "this exercise" }
        resolved = library.resolve(
            movementId = movementId,
            exerciseName = readableName,
            descriptor = descriptor?.toString().orEmpty(),
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
        contentDescription = "Line-figure movement guide for $readableName. Follow the written instructions for exact technique."
        updateCoachingText()
        animationPhase = resolved.template.startPhase
        restartAnimationIfNeeded()
        invalidate()
        return this
    }

    /** Colours for the static skill-badge rendering. */
    class BadgeStyle(val background: Int, val figure: Int, val farFigure: Int, val apparatus: Int)

    /**
     * Turns the view into a skill badge: a flat rounded tile with the movement scaled to fill it, heavier strokes
     * and no panel or overlays. A static badge holds the end pose; a [moving] one (the exercise picker) plays it.
     */
    fun setBadgeStyle(style: BadgeStyle, moving: Boolean = false) {
        badge = style
        backgroundPaint.color = style.background
        figurePaint.color = style.figure
        headPaint.color = style.figure
        handPaint.color = style.figure
        handRingPaint.color = Color.TRANSPARENT
        farLimbPaint.color = style.farFigure
        apparatusPaint.color = style.apparatus
        floorPaint.color = style.apparatus
        apparatusFillPaint.color = blendColor(style.apparatus, style.background, 0.6f)
        setPadding(0, 0, 0, 0)
        minimumWidth = 0
        minimumHeight = 0
        badgeMoves = moving
        badgeBounds = null
        if (moving) restartAnimationIfNeeded() else stopAnimator()
        invalidate()
    }

    override fun getAccessibilityClassName(): CharSequence = "android.widget.ImageView"

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = maxOf(suggestedMinimumWidth, dp(260f).roundToInt()) + paddingLeft + paddingRight
        val desiredHeight = maxOf(suggestedMinimumHeight, dp(156f).roundToInt()) + paddingTop + paddingBottom
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec),
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!scrollListenerAttached) {
            viewTreeObserver.addOnScrollChangedListener(scrollChangedListener)
            scrollListenerAttached = true
        }
        restartAnimationIfNeeded()
        post { if (isAttachedToWindow) updateAnimationState() }
    }

    override fun onDetachedFromWindow() {
        if (scrollListenerAttached) {
            viewTreeObserver.takeIf(ViewTreeObserver::isAlive)
                ?.removeOnScrollChangedListener(scrollChangedListener)
            scrollListenerAttached = false
        }
        stopAnimator()
        super.onDetachedFromWindow()
    }

    override fun onStartTemporaryDetach() {
        super.onStartTemporaryDetach()
        parked = true
        stopAnimator()
    }

    override fun onFinishTemporaryDetach() {
        super.onFinishTemporaryDetach()
        parked = false
        updateAnimationState()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (isAttachedToWindow) updateAnimationState()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (isAttachedToWindow) updateAnimationState()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        badge?.let {
            drawBadge(canvas)
            return
        }
        val left = paddingLeft.toFloat()
        val top = paddingTop.toFloat()
        val right = (width - paddingRight).toFloat()
        val bottom = (height - paddingBottom).toFloat()
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
        val below = floor + dp(4f) - captionPaint.ascent()
        if (below + captionPaint.descent() < bottom - dp(11f)) {
            captionPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(leftCaption, ox + HALF / 2f * scale, below, captionPaint)
            canvas.drawText(frontCaption, ox + HALF * 1.5f * scale, below, captionPaint)
            return
        }
        val start = ox + (HALF / 2f - HALF_FLOOR) * scale - dp(4f)
        val end = ox + (HALF * 1.5f + HALF_FLOOR) * scale + dp(4f)
        if (start - captionPaint.measureText(leftCaption) < left + dp(6f) || end + captionPaint.measureText(frontCaption) > right - dp(6f)) return
        val y = floor + (captionPaint.descent() - captionPaint.ascent()) / 2f - captionPaint.descent()
        captionPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(leftCaption, start, y, captionPaint)
        captionPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(frontCaption, end, y, captionPaint)
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
            markerDash = DashPathEffect(floatArrayOf(2.2f * scale, 2.2f * scale), 0f)
            markerDashScale = scale
        }
        markerPaint.pathEffect = if (reached) null else markerDash
        markerPaint.strokeWidth = (if (reached) 1.3f else 0.9f) * scale
        markerPaint.color = if (reached) AppColors.PRIMARY else withAlpha(AppColors.TEXT_SECONDARY, 140)
        canvas.drawLine(ox + 14f * scale, y, ox + (HALF * 2f - 14f) * scale, y, markerPaint)
        val label = if (reached) markerLabelReached else markerLabel
        markerTextPaint.color = if (reached) AppColors.PRIMARY else AppColors.TEXT_SECONDARY
        // Between the two views: under the bars, midway between their posts, when the line runs over a bar.
        val overBar = barY > 0f && template.markerY < barY
        val center = if (overBar) {
            (HALF / 2f + SIDE_BAR_HALF - 3f + HALF * 1.5f - frontBarHalf + 3f) / 2f
        } else HALF
        val textX = ox + center * scale - markerTextPaint.measureText(label) / 2f
        val textY = when {
            overBar -> oy + barY * scale + 2.5f * scale - markerTextPaint.ascent()
            template.markerY < library.canvasHeight / 2f -> y + 1.5f * scale - markerTextPaint.ascent()
            else -> y - 1.5f * scale
        }
        canvas.drawText(label, textX, textY, markerTextPaint)
    }

    private fun drawBadge(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val radius = min(w, h) * 0.22f
        badgeClip.rewind()
        badgeClip.addRoundRect(0f, 0f, w, h, radius, radius, Path.Direction.CW)
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

    /**
     * Index loops: this runs every frame, and an iterator per list would be garbage each time. [skipFrame] leaves out
     * the floor and bars, which a two-view guide draws per half.
     */
    private fun drawApparatus(canvas: Canvas, names: List<String>, ox: Float, oy: Float, scale: Float, skipFrame: Boolean = false) {
        for (i in names.indices) {
            if (skipFrame && names[i] in FRAME_APPARATUS) continue
            val primitives = library.apparatus[names[i]] ?: continue
            for (j in primitives.indices) drawPrimitive(canvas, primitives[j], ox, oy, scale)
        }
    }

    /** Maps the animator phase to a pose timeline; ping-pong templates play as a tempo rep. */
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
        val locale = Locale.getDefault()
        phaseLabels = template.phaseLabels.labels.map { resources.getString(it).uppercase(locale) }.toTypedArray()
        ownPhaseLabels = template.phaseSpec?.labels?.map { resources.getString(it).uppercase(locale) }?.toTypedArray()
        // A two-view guide may borrow another clip's range line (a negative shows the pull-up's chin line).
        markerLabel = resources.getString(markerTemplate.markerLabel)
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
            markerDash = DashPathEffect(floatArrayOf(2.2f * scale, 2.2f * scale), 0f)
            markerDashScale = scale
        }
        markerPaint.pathEffect = if (reached) null else markerDash
        markerPaint.strokeWidth = (if (reached) 1.3f else 0.9f) * scale
        markerPaint.color = if (reached) AppColors.PRIMARY else withAlpha(AppColors.TEXT_SECONDARY, 140)
        canvas.drawLine(x0, y, x1, y, markerPaint)
        val label = if (reached) markerLabelReached else markerLabel
        markerTextPaint.color = if (reached) AppColors.PRIMARY else AppColors.TEXT_SECONDARY
        val textX = if (x1 + 3f * scale + markerTextPaint.measureText(label) <= limit) x1 + 3f * scale else x0
        canvas.drawText(label, textX, y - 1.5f * scale, markerTextPaint)
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
        if (label != null) canvas.drawText(label, left + dp(12f), top + dp(10f) - phaseTextPaint.ascent(), phaseTextPaint)
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
            stripPaint.color = AppColors.DIVIDER
            canvas.drawLine(a, y, b, y, stripPaint)
            val filled = when {
                index < frameSegment -> b
                index == frameSegment -> a + (b - a) * frameProgress
                else -> a
            }
            if (filled > a + 0.5f) {
                stripPaint.color = if (index == frameSegment) AppColors.PRIMARY else withAlpha(AppColors.PRIMARY, 115)
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
                primitivePath.rewind()
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

    private fun restartAnimationIfNeeded() {
        stopAnimator()
        animationPhase = resolved.template.startPhase
        updateAnimationState()
        invalidate()
    }

    private fun updateAnimationState() {
        if (!shouldAnimate()) {
            if (animator != null) {
                stopAnimator()
                animationPhase = resolved.template.startPhase
                invalidate()
            }
            return
        }
        if (animator != null) return
        animator = ValueAnimator.ofFloat(animationPhase, animationPhase + 1f).apply {
            duration = resolved.template.cycleDurationMs
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener {
                val phase = (it.animatedValue as Float) % 1f
                if (phase < animationPhase) cycle++
                animationPhase = phase
                postInvalidateOnAnimation()
            }
            start()
        }
    }

    private fun shouldAnimate(): Boolean =
        !held &&
            (badge == null || badgeMoves) &&
            !parked &&
            isAttachedToWindow &&
            windowVisibility == VISIBLE &&
            visibility == VISIBLE &&
            isShown &&
            width > 0 &&
            height > 0 &&
            getGlobalVisibleRect(visibleRect) &&
            !visibleRect.isEmpty &&
            context.systemAnimationsEnabled()

    /** One loop of the bound clip and where it starts, for drawings made ahead with [hold] (the home-screen widget). */
    internal val loopDurationMs: Long get() = resolved.template.cycleDurationMs
    internal val loopStartPhase: Float get() = resolved.template.startPhase

    /**
     * Draws the frame at [phase] of the loop, [cycle] reps in, as if playing: the androidTest gallery's review sheets
     * and the home-screen widget's flip-book.
     */
    internal fun hold(phase: Float, cycle: Int = 0) {
        stopAnimator()
        held = true
        animationPhase = phase
        this.cycle = cycle
        invalidate()
    }

    private var held = false

    private fun isAnimationRunning(): Boolean = held || animator?.isRunning == true

    private fun stopAnimator() {
        animator?.cancel()
        animator?.removeAllUpdateListeners()
        animator = null
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}

/**
 * Strokes a solved figure: lighter far-side limbs, a curved torso, a filled head and hand dots ([hand] null
 * skips them). [joints] are canvas units mapped through [ox], [oy] and [scale]; [torsoPath] is scratch.
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
    torsoPath.rewind()
    torsoPath.moveTo(hipX, hipY)
    torsoPath.quadTo(
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

/** Read access to the authored clips for other line-figure scenes (see ParkSceneView). */
internal class MotionClip private constructor(private val curve: MotionCurve) {
    /** Writes the pose at [timeline] (0..1 along the keyframes) and its joints in clip canvas units. */
    fun sample(timeline: Float, pose: Pose, joints: FloatArray) = curve.evaluate(timeline, pose, joints)

    /** Where keyframe [index] sits on the timeline after re-spacing. */
    fun keyTime(index: Int): Float = curve.keyTime(index)

    companion object {
        /** Parses exercise_animations.json now (off the main thread), so the first scene or guide doesn't wait for it. */
        fun preload(context: Context) {
            AnimationLibrary.load(context.applicationContext)
        }

        fun rig(context: Context): Rig = AnimationLibrary.load(context.applicationContext).rig

        fun named(context: Context, name: String): MotionClip? =
            AnimationLibrary.load(context.applicationContext).template(name)?.let { MotionClip(it.curve) }
    }
}

private fun lerp(start: Float, end: Float, fraction: Float): Float = start + (end - start) * fraction

/** Shortest signed arc from one absolute angle to another, in degrees. */
private fun angleDelta(start: Float, end: Float): Float {
    var delta = (end - start) % 360f
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return delta
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

private data class ApparatusPrimitive(
    val type: String,
    val role: String,
    val values: FloatArray,
    val filled: Boolean,
)

/**
 * One body configuration. Angles are absolute degrees: 0 = up, 90 = right, 180 = down.
 * [facing] is +1 when the figure's front is to the right of its torso axis, -1 otherwise.
 */
internal class Pose {
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
internal class Rig(
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
            val radians = Math.toRadians(angle.toDouble())
            set(
                joint,
                out[fromJoint * 2] + sin(radians).toFloat() * length,
                out[fromJoint * 2 + 1] - cos(radians).toFloat() * length,
            )
        }
        set(HIP, 0f, 0f)
        step(NECK, HIP, pose.torso, torso)
        step(HEAD, NECK, pose.headAngle(), neck + headRadius)
        val torsoRadians = Math.toRadians(pose.torso.toDouble())
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

private class PoseKeyframe(val at: Float, val pose: Pose)

/**
 * Monotone cubic (Fritsch-Butland) interpolation of every pose channel through the keyframes.
 * Joints only come to rest where they reverse or hold, so the figure no longer stops at every
 * in-between keyframe; with two keyframes the result equals the previous smoothstep. For
 * "smooth" templates the in-between keyframes of one continuous movement are re-spaced by how
 * far the body travels, so uneven authored timing does not create a slow patch mid-rep.
 */
private class MotionCurve(
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
private enum class PhaseLabels(val labels: IntArray) {
    PULL(intArrayOf(R.string.motion_phase_pull, R.string.motion_phase_hold, R.string.motion_phase_lower, R.string.motion_phase_hang)),
    PRESS(intArrayOf(R.string.motion_phase_lower, R.string.motion_phase_pause, R.string.motion_phase_press, R.string.motion_phase_reset)),
    SQUAT(intArrayOf(R.string.motion_phase_lower, R.string.motion_phase_pause, R.string.motion_phase_stand, R.string.motion_phase_reset)),
    RISE(intArrayOf(R.string.motion_phase_up, R.string.motion_phase_hold, R.string.motion_phase_down, R.string.motion_phase_reset)),
    SINK(intArrayOf(R.string.motion_phase_down, R.string.motion_phase_pause, R.string.motion_phase_up, R.string.motion_phase_reset)),
    MOVE(intArrayOf(R.string.motion_phase_move, R.string.motion_phase_hold, R.string.motion_phase_return, R.string.motion_phase_reset)),
}

/**
 * A clip's own range line (the template's "marker"), for what the app can't work out from the clip: the [joint] to
 * watch (-1: the worked-out one), where the line sits ([y], NaN: where the joint ends up), its [label] (0: worked out)
 * and the moment of the timeline the joint is measured at ([at]). [off] removes the line, as on stretches.
 */
private class MarkerSpec(val joint: Int, val y: Float, val label: Int, val at: Float, val off: Boolean)

/** A clip's own phase labels (the template's "phases"): [labels][i] from [times][i] of the timeline on. */
private class PhaseSpec(val times: FloatArray, val labels: IntArray) {
    fun indexAt(timeline: Float): Int {
        var index = 0
        for (i in times.indices) if (timeline >= times[i]) index = i
        return index
    }
}

private class MotionTemplate(
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
    var markerLabel = R.string.motion_marker_range
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
            R.string.motion_marker_chin
        } else {
            R.string.motion_marker_range
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
        if (spec.label != 0) markerLabel = spec.label
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

private data class MotionReference(
    val templateName: String,
    val apparatus: List<String>?,
)

private class ResolvedMotion(
    val template: MotionTemplate,
    val apparatus: List<String>,
)

private data class NameHeuristic(
    val tokens: List<String>,
    val reference: MotionReference,
)

private class AnimationLibrary(
    val canvasWidth: Float,
    val canvasHeight: Float,
    val rig: Rig,
    val apparatus: Map<String, List<ApparatusPrimitive>>,
    private val templates: Map<String, MotionTemplate>,
    private val movements: Map<String, MotionReference>,
    private val aliases: Map<String, MotionReference>,
    private val categoryDefaults: Map<String, MotionReference>,
    private val equipmentDefaults: Map<String, MotionReference>,
    private val heuristics: List<NameHeuristic>,
    fallbackName: String,
) {
    val fallback: ResolvedMotion = resolveReference(MotionReference(fallbackName, null))
        ?: ResolvedMotion(emergencyTemplate().also { it.prepare(rig) }, listOf("floor"))

    fun template(name: String): MotionTemplate? = templates[name]

    fun resolve(
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
        @Volatile
        private var cached: AnimationLibrary? = null

        fun load(context: Context): AnimationLibrary {
            cached?.let { return it }
            return synchronized(this) {
                cached ?: runCatching {
                    context.assets.open("exercise_animations.json").bufferedReader().use { reader ->
                        parse(JSONObject(reader.readText()))
                    }
                }.getOrElse { fallbackLibrary() }.also { cached = it }
            }
        }

        private fun parse(root: JSONObject): AnimationLibrary {
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
            false -> MarkerSpec(-1, Float.NaN, 0, 1f, off = true)
            is JSONObject -> MarkerSpec(
                joint = value.optString("joint").takeIf(String::isNotBlank)?.let { Rig.jointOrNull(it) } ?: -1,
                y = value.optDouble("y", Double.NaN).toFloat(),
                label = MARKER_LABELS[value.optString("label")] ?: 0,
                at = value.optDouble("at", 1.0).toFloat().coerceIn(0f, 1f),
                off = false,
            )
            else -> null
        }

        /** A template's "phases": one label name for the whole clip, or [at, label] pairs along a loop. */
        private fun phaseSpec(value: Any?): PhaseSpec? = when (value) {
            is String -> PHASE_LABELS[value]?.let { PhaseSpec(floatArrayOf(0f), intArrayOf(it)) }
            is JSONArray -> {
                val pairs = (0 until value.length()).mapNotNull { index ->
                    val pair = value.optJSONArray(index) ?: return@mapNotNull null
                    val label = PHASE_LABELS[pair.optString(1)] ?: return@mapNotNull null
                    pair.optDouble(0, 0.0).toFloat() to label
                }.sortedBy { it.first }
                if (pairs.isEmpty()) null
                else PhaseSpec(FloatArray(pairs.size) { pairs[it].first }, IntArray(pairs.size) { pairs[it].second })
            }
            else -> null
        }

        private val MARKER_LABELS = mapOf(
            "chin_over_bar" to R.string.motion_marker_chin, "full_range" to R.string.motion_marker_range,
            "chest_to_bar" to R.string.motion_marker_chest_to_bar, "chest_to_fist" to R.string.motion_marker_chest_to_fist,
            "chest_to_edge" to R.string.motion_marker_chest_to_edge, "chest_to_wall" to R.string.motion_marker_chest_to_wall,
            "shoulders_to_elbows" to R.string.motion_marker_shoulders_to_elbows,
            "hips_to_knees" to R.string.motion_marker_hips_to_knees, "back_knee_down" to R.string.motion_marker_back_knee_down,
            "knees_to_hips" to R.string.motion_marker_knees_to_hips, "feet_to_hips" to R.string.motion_marker_feet_to_hips,
            "toes_to_bar" to R.string.motion_marker_toes_to_bar, "hips_in_line" to R.string.motion_marker_hips_in_line,
            "head_ahead" to R.string.motion_marker_head_ahead, "head_to_mat" to R.string.motion_marker_head_to_mat,
            "shoulders_down" to R.string.motion_marker_shoulders_down, "heels_high" to R.string.motion_marker_heels_high,
            "hips_level" to R.string.motion_marker_hips_level, "legs_level" to R.string.motion_marker_legs_level,
            "feet_off" to R.string.motion_marker_feet_off, "stand_tall" to R.string.motion_marker_stand_tall,
        )

        private val PHASE_LABELS = mapOf(
            "hold" to R.string.motion_phase_hold_breathe, "lower_slow" to R.string.motion_phase_lower_slow,
            "step_down" to R.string.motion_phase_step_down, "step_up" to R.string.motion_phase_step_up,
            "come_down" to R.string.motion_phase_come_down, "hang" to R.string.motion_phase_hang,
            "pull" to R.string.motion_phase_pull, "press" to R.string.motion_phase_press,
        )

        private fun fallbackLibrary(): AnimationLibrary {
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

private fun normalize(value: String): String = value
    .lowercase(Locale.ENGLISH)
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
