package com.justdataplease.streetgym.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.view.ViewTreeObserver
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.AnimationUtils
import com.justdataplease.streetgym.BuildConfig
import com.justdataplease.streetgym.R
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The Today hero's line-art park, drawn with the movement guides' figure ([MotionClip], [drawLineFigure]).
 *
 * [Mode.STORY]: a routine generated per local day ([ParkRoutine]): three different movements, a fundamental, a harder
 * strength move and a held skill. The figure walks into the park and trains while three smaller friends wave, clap,
 * hop and cheer; the choreography lives in [ParkStory]. A park that needs more room than 200 units zooms out. It
 * repeats all three movements, briefly holding the final skill before fading the hero out for another walk-in.
 * Returning to the visible scene starts the routine from the beginning; a tap also restarts it. Debug builds step
 * to the next day's routine on a long press.
 * [Mode.WALK]: the walk cycle alone on a scrolling path, for the loading screen.
 * [Mode.PULL]: the same figure doing pull-ups, seen from the front ([FrontView]), the loading screen's other figure;
 * it needs no clip, so it draws while the clips are still being parsed.
 *
 * Reduced motion shows the final frame (the signature skill), and nothing runs while the view is off-screen.
 * Canvas units are the clips' (floor at y = 106).
 *
 * With a [username] the park says "Well done, <name>!" as the final skill lands, in one of three [BubbleStyle]s (the
 * owner, 2026-10-31: "a nice cloud saying well done <username>"), and holds that last moment a little longer to read it.
 */
class ParkSceneView(context: Context, private val mode: Mode = Mode.STORY) : View(context) {
    enum class Mode { STORY, WALK, PULL }

    /**
     * How the greeting shows. [SPEECH]: a comic bubble pops out of a cheering friend. [CLOUD]: a line-art cloud rises
     * over the hero. [TAPE]: a strip of the theme's tape unrolls across the sky, in the display face.
     */
    enum class BubbleStyle { SPEECH, CLOUD, TAPE }

    /** The athlete's username; null (the default) keeps the park as it was, with no greeting. */
    var username: String? = null
        set(value) {
            field = value?.trim()?.takeIf(String::isNotEmpty)
            bubbleLine = null
            invalidate()
        }

    var bubbleStyle: BubbleStyle = BubbleStyle.SPEECH
        set(value) {
            field = value
            bubbleLine = null
            invalidate()
        }

    private val rig = if (mode == Mode.STORY) MotionClip.rig(context) else Rig.DEFAULT
    private val clipNamed: (String) -> MotionClip? = { name -> MotionClip.named(context, name) }
    private lateinit var story: ParkStory
    private var shownDay = 0L
    private val walk = Gait(rig)

    private val hero = Pose()
    private val heroJoints = FloatArray(Rig.JOINT_COUNT * 2)
    private val friend = Pose()
    private val friendJoints = FloatArray(Rig.JOINT_COUNT * 2)
    private val torsoPath = Path()

    private var scale = 1f
    private var originX = 0f
    private var originY = 0f
    private var clock = 0f
    private var lastFrameMs = 0L
    private var ticking = false
    private var reducedMotion = false
    private val visibleRect = Rect()
    private var scrollListenerAttached = false
    private val scrollChangedListener = ViewTreeObserver.OnScrollChangedListener { updateAnimationState() }

    // STORY sits on the dark hero card in both themes; WALK and PULL on the page background.
    private val backdrop = if (mode == Mode.STORY) AppColors.PRIMARY_DARK else AppColors.BACKGROUND
    private val ink = if (mode == Mode.STORY) Color.WHITE else AppColors.TEXT
    private val accent = if (mode == Mode.STORY) AppColors.ACCENT else AppColors.PRIMARY
    private val heroNear = stroke(blendColor(ink, backdrop, if (mode == Mode.STORY) 0.06f else 0f))
    private val heroFar = stroke(blendColor(ink, backdrop, if (mode == Mode.STORY) 0.45f else 0.42f))
    private val heroHead = fill(heroNear.color)
    private val heroHand = fill(accent)
    private val friendNear = stroke(blendColor(ink, backdrop, 0.52f))
    private val friendFar = stroke(blendColor(ink, backdrop, 0.68f))
    private val friendHead = fill(friendNear.color)
    private val clapPaint = stroke(blendColor(accent, backdrop, 0.25f))
    private val barPaint = stroke(blendColor(ink, backdrop, 0.42f))
    private val postPaint = stroke(blendColor(ink, backdrop, 0.62f))
    private val trunkPaint = stroke(blendColor(ink, backdrop, 0.62f))
    private val leafPaint = stroke(blendColor(accent, backdrop, 0.5f))
    private val leafFill = fill(blendColor(accent, backdrop, 0.9f))
    private val groundColor = if (mode == Mode.STORY) blendColor(ink, backdrop, 0.6f) else blendColor(AppColors.TEXT_SECONDARY, backdrop, 0.35f)
    private val dashColor = blendColor(AppColors.TEXT_SECONDARY, backdrop, 0.55f)
    private val groundPaint = stroke(groundColor).apply { strokeCap = Paint.Cap.BUTT }
    private val dashPaint = stroke(dashColor)
    private val pullInk = FrontView.Ink(heroNear, heroHead, heroHand, barPaint, postPaint)
    private val pullFrame = FrontView.Frame()
    private val pullRect = RectF()

    // The greeting: its line (ellipsized to fit), where the friends' heads are this frame, and scratch shapes.
    private var bubbleLine: String? = null
    private var bubbleLineWidth = 0
    private val friendHeadX = FloatArray(ParkStory.FRIENDS)
    private val friendHeadY = FloatArray(ParkStory.FRIENDS)
    private val heroBox = RectF()
    private val bubbleRect = RectF()
    private val bubblePath = Path()
    private val puffPath = Path()
    private val bubbleText = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val bubbleFill = fill(Color.WHITE)
    private val bubbleShadow = fill(accent)
    private val bubbleStroke = stroke(heroNear.color)

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        if (mode == Mode.WALK) {
            contentDescription = resources.getString(R.string.park_scene_walk_description)
            walk.plan(0f, Gait.STEP_LENGTH * LOOP_INTERVALS, 0f)
        } else if (mode == Mode.PULL) {
            contentDescription = resources.getString(R.string.park_scene_pull_description)
        } else {
            showRoutine(ParkRoutine.localEpochDay())
            if (story.ready) setOnClickListener { replay() }
            if (BuildConfig.DEBUG) {
                setOnLongClickListener {
                    showRoutine(shownDay + 1)
                    clock = 0f
                    lastFrameMs = 0L
                    invalidate()
                    true
                }
            }
        }
    }

    override fun getAccessibilityClassName(): CharSequence = "android.widget.ImageView"

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        if (isClickable) {
            info.addAction(
                AccessibilityNodeInfo.AccessibilityAction(
                    AccessibilityNodeInfo.ACTION_CLICK,
                    resources.getString(R.string.park_scene_replay),
                ),
            )
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val desiredWidth = (if (mode == Mode.STORY) 320f else 200f) * density
        val desiredHeight = (when (mode) { Mode.STORY -> 180f; Mode.WALK -> 96f; Mode.PULL -> 150f }) * density
        setMeasuredDimension(
            resolveSize(desiredWidth.roundToInt(), widthMeasureSpec),
            resolveSize(desiredHeight.roundToInt(), heightMeasureSpec),
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val fade: Float
        if (mode == Mode.WALK) {
            scale = h / (WALK_BOTTOM - WALK_TOP)
            originY = -WALK_TOP * scale
            fade = 0.3f
        } else if (mode == Mode.PULL) {
            fade = 0.3f
        } else {
            fitStory()
            fade = 0.12f
        }
        groundPaint.shader = edgeFade(groundColor, w, fade)
        dashPaint.shader = edgeFade(dashColor, w, fade)
        bubbleLine = null
        scaleStrokes()
        post { if (isAttachedToWindow) updateAnimationState() }
    }

    /** Fits the day's park into the view, zooming out when it is wider than [ParkStory.MIN_WIDTH]. */
    private fun fitStory() {
        scale = min(width / story.width, height / (SCENE_BOTTOM - SCENE_TOP))
        originX = (width - story.width * scale) * 0.5f
        originY = height - SCENE_BOTTOM * scale
        story.plan(-originX / scale)
    }

    private fun scaleStrokes() {
        groundPaint.strokeWidth = 1.4f * scale
        dashPaint.strokeWidth = 1.2f * scale
        barPaint.strokeWidth = 1.8f * scale
        postPaint.strokeWidth = 1.6f * scale
        leafPaint.strokeWidth = 1.5f * scale
        clapPaint.strokeWidth = scale
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!scrollListenerAttached) {
            viewTreeObserver.addOnScrollChangedListener(scrollChangedListener)
            scrollListenerAttached = true
        }
        post { if (isAttachedToWindow) updateAnimationState() }
    }

    override fun onDetachedFromWindow() {
        if (scrollListenerAttached) {
            viewTreeObserver.takeIf(ViewTreeObserver::isAlive)?.removeOnScrollChangedListener(scrollChangedListener)
            scrollListenerAttached = false
        }
        ticking = false
        super.onDetachedFromWindow()
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
        if (width == 0 || height == 0) return
        if (ticking) {
            val now = AnimationUtils.currentAnimationTimeMillis()
            if (lastFrameMs > 0L) advance((now - lastFrameMs) / 1000f)
            lastFrameMs = now
        }
        when (mode) {
            Mode.STORY -> drawStory(canvas)
            Mode.WALK -> drawWalk(canvas)
            Mode.PULL -> drawPull(canvas)
        }
        if (ticking) postInvalidateOnAnimation()
    }

    private fun advance(seconds: Float) {
        clock += seconds.coerceIn(0f, MAX_FRAME_SECONDS)
        if (mode == Mode.WALK) {
            clock %= 2f * Gait.STEP_TIME
            return
        }
        if (mode == Mode.PULL) {
            clock %= PULL_REP
            return
        }
        // The final cheer finishes, then the hero fades out before walking in for the next full routine.
        clock %= story.end + finaleHold + LOOP_FADE_SECONDS
    }

    /** How long the final skill holds before the fade: longer while the greeting shows, so it can be read. */
    private val finaleHold: Float get() = if (username != null) BUBBLE_HOLD_SECONDS else FINALE_HOLD_SECONDS

    /** Sets story time [seconds] for a view that isn't playing: the androidTest review shots draw it there. */
    internal fun showAt(seconds: Float) {
        clock = seconds
        lastFrameMs = 0L
        invalidate()
    }

    /** When the final skill lands (story time). */
    internal val finaleAt: Float get() = story.end

    /** When the loop starts over (story time). */
    internal val loopSeconds: Float get() = story.end + finaleHold + LOOP_FADE_SECONDS

    private fun replay() {
        if (reducedMotion) return
        // A tap after midnight plays the new day's routine.
        val today = ParkRoutine.localEpochDay()
        if (today != shownDay) showRoutine(today)
        clock = 0f
        lastFrameMs = 0L
        invalidate()
    }

    private fun showRoutine(day: Long) {
        shownDay = day
        val routine = ParkRoutine.forEpochDay(day)
        story = ParkStory(rig, routine, clipNamed)
        if (width > 0 && height > 0) {
            fitStory()
            scaleStrokes()
        }
        contentDescription = resources.getString(
            R.string.park_scene_description,
            resources.getString(routine.opener.label),
            resources.getString(routine.middle.label),
            resources.getString(routine.finale.label),
        )
    }

    private fun updateAnimationState() {
        val wasReduced = reducedMotion
        reducedMotion = !context.systemAnimationsEnabled()
        val animate = !reducedMotion &&
            isAttachedToWindow &&
            windowVisibility == VISIBLE &&
            isShown &&
            width > 0 &&
            height > 0 &&
            getGlobalVisibleRect(visibleRect) &&
            !visibleRect.isEmpty
        if (animate == ticking && wasReduced == reducedMotion) return
        if (animate && !ticking && mode == Mode.STORY) clock = 0f
        ticking = animate
        lastFrameMs = 0L
        invalidate()
    }

    // ---- drawing

    private fun drawStory(canvas: Canvas) {
        val t = if (reducedMotion) story.end else clock
        fun x(value: Float) = originX + value * scale
        fun y(value: Float) = originY + value * scale

        canvas.drawLine(0f, y(FLOOR_Y), width.toFloat(), y(FLOOR_Y), groundPaint)
        canvas.drawCircle(x(TREE_X), y(40f), 15f * scale, leafFill)
        canvas.drawCircle(x(TREE_X), y(40f), 15f * scale, leafPaint)
        trunkPaint.strokeWidth = 2f * scale
        canvas.drawLine(x(TREE_X), y(FLOOR_Y), x(TREE_X), y(44f), trunkPaint)
        trunkPaint.strokeWidth = 1.5f * scale
        canvas.drawLine(x(TREE_X), y(58f), x(TREE_X + 7f), y(47f), trunkPaint)
        canvas.drawLine(x(TREE_X), y(53f), x(TREE_X - 6f), y(44f), trunkPaint)
        // Every frame: an index loop, not an iterator.
        for (i in story.props.indices) {
            val prop = story.props[i]
            val overhang = if (prop.kind == ParkProp.BAR) ParkProp.BAR_OVERHANG else ParkProp.PARALLEL_OVERHANG
            canvas.drawLine(x(prop.left), y(prop.y), x(prop.left), y(FLOOR_Y), postPaint)
            canvas.drawLine(x(prop.right), y(prop.y), x(prop.right), y(FLOOR_Y), postPaint)
            canvas.drawLine(x(prop.left - overhang), y(prop.y), x(prop.right + overhang), y(prop.y), barPaint)
        }

        for (index in 0 until ParkStory.FRIENDS) drawFriend(canvas, index, t)

        story.poseHero(t, hero, heroJoints)
        val fadeOut = if (reducedMotion) 1f else ((loopSeconds - clock) / LOOP_FADE_SECONDS).coerceIn(0f, 1f)
        val opacity = (255f * fadeOut).roundToInt()
        heroNear.alpha = opacity
        heroFar.alpha = opacity
        heroHead.alpha = opacity
        heroHand.alpha = opacity
        canvas.drawLineFigure(
            heroJoints, hero.spine, hero.facing, rig.headRadius, originX, originY, scale,
            heroNear, heroFar, heroHead, heroHand, torsoPath,
        )
        if (username != null && story.ready) drawGreeting(canvas, t, fadeOut)
    }

    // ---- the greeting

    /**
     * "Well done, <name>!" from just before the final skill lands until the loop fades ([fadeOut] runs 1 to 0 with the
     * hero). Reduced motion shows it fully in, still.
     */
    private fun drawGreeting(canvas: Canvas, t: Float, fadeOut: Float) {
        val since = if (reducedMotion) GREETING_IN_FULL else t - (story.end - BUBBLE_LEAD_SECONDS)
        if (since <= 0f || fadeOut <= 0f) return
        val density = resources.displayMetrics.density
        val line = greetingLine(density) ?: return
        heroBounds()
        val alpha = (255f * fadeOut).roundToInt()
        when (bubbleStyle) {
            BubbleStyle.SPEECH -> drawSpeech(canvas, line, since, alpha, density)
            BubbleStyle.CLOUD -> drawCloud(canvas, line, since, alpha, density)
            BubbleStyle.TAPE -> drawTape(canvas, line, since, alpha, density)
        }
    }

    /** Sets the greeting's face and size, and gives its line, the name ellipsized so the whole fits the scene. */
    private fun greetingLine(density: Float): String? {
        val tape = bubbleStyle == BubbleStyle.TAPE
        bubbleText.typeface = if (tape) AppType.display else AppType.bold
        bubbleText.letterSpacing = if (tape) 0.03f else 0f
        bubbleText.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, if (tape) 15f else 12.5f, resources.displayMetrics)
        bubbleLine?.let { return it }
        val name = username ?: return null
        val upper = tape && AppColors.theme.shape.upperTitles
        fun styled(text: String) = if (upper) text.uppercase(Locale.getDefault()) else text
        val padding = when (bubbleStyle) { BubbleStyle.SPEECH -> SPEECH_PAD_X; BubbleStyle.CLOUD -> CLOUD_PAD_X; BubbleStyle.TAPE -> TAPE_PAD_X + TAPE_TOOTH }
        val room = width - 2f * (EDGE_DP + padding) * density - bubbleText.measureText(styled(resources.getString(R.string.park_well_done, "")))
        val shown = TextUtils.ellipsize(styled(name), bubbleText, room.coerceAtLeast(0f), TextUtils.TruncateAt.END).toString()
        if (shown.isEmpty()) return null
        return styled(resources.getString(R.string.park_well_done, shown)).also {
            bubbleLine = it
            bubbleLineWidth = bubbleText.measureText(it).roundToInt()
        }
    }

    /** The hero's outline this frame, in view pixels. */
    private fun heroBounds() {
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for (joint in 0 until Rig.JOINT_COUNT) {
            left = min(left, heroJoints[joint * 2])
            right = max(right, heroJoints[joint * 2])
            top = min(top, heroJoints[joint * 2 + 1])
            bottom = max(bottom, heroJoints[joint * 2 + 1])
        }
        val pad = rig.headRadius
        heroBox.set(originX + (left - pad) * scale, originY + (top - pad) * scale, originX + (right + pad) * scale, originY + (bottom + pad) * scale)
    }

    /**
     * Where a shape [w] wide and spanning [top]..[bottom] goes: centred over the hero, or over the wider free side
     * when the hero reaches up into that band, so the greeting never hides the skill.
     */
    private fun clearX(w: Float, top: Float, bottom: Float, density: Float): Float {
        val edge = EDGE_DP * density
        var centre = heroBox.centerX()
        if (heroBox.top < bottom && heroBox.bottom > top) {
            val leftRoom = heroBox.left
            val rightRoom = width - heroBox.right
            centre = if (leftRoom >= rightRoom) leftRoom * 0.5f else heroBox.right + rightRoom * 0.5f
        }
        return centre.coerceIn(edge + w * 0.5f, max(edge + w * 0.5f, width - edge - w * 0.5f))
    }

    /** A comic bubble from the friend farthest from the hero, popping out of their head, with the theme's hard shadow. */
    private fun drawSpeech(canvas: Canvas, line: String, since: Float, alpha: Int, density: Float) {
        val heroX = heroBox.centerX()
        var speaker = 0
        for (i in 1 until ParkStory.FRIENDS) if (abs(friendHeadX[i] - heroX) > abs(friendHeadX[speaker] - heroX)) speaker = i
        val tipX = friendHeadX[speaker]
        val tipY = friendHeadY[speaker] - 2f * density
        val metrics = bubbleText.fontMetrics
        val padX = SPEECH_PAD_X * density
        val padY = 5f * density
        val w = bubbleLineWidth + 2f * padX
        val h = metrics.descent - metrics.ascent + 2f * padY
        val edge = EDGE_DP * density
        val left = (tipX - w * 0.5f).coerceIn(edge, max(edge, width - edge - w))
        val bottom = max(edge + h, tipY - 8f * density)
        bubbleRect.set(left, bottom - h, left + w, bottom)
        val pop = if (reducedMotion) 1f else backOut((since / BUBBLE_IN_SECONDS).coerceIn(0f, 1f))
        if (pop <= 0.01f) return
        val radius = h * 0.5f
        bubblePath.rewind()
        bubblePath.addRoundRect(bubbleRect, radius, radius, Path.Direction.CW)
        val baseX = tipX.coerceIn(bubbleRect.left + radius, bubbleRect.right - radius)
        puffPath.rewind()
        puffPath.moveTo(baseX - 6f * density, bubbleRect.bottom - 1f)
        puffPath.lineTo(tipX, tipY)
        puffPath.lineTo(baseX + 2f * density, bubbleRect.bottom - 1f)
        puffPath.close()
        bubblePath.op(puffPath, Path.Op.UNION)
        canvas.save()
        canvas.scale(pop, pop, tipX, tipY)
        val shadow = 2.5f * density
        canvas.translate(shadow, shadow)
        bubbleShadow.color = accent
        bubbleShadow.alpha = alpha
        canvas.drawPath(bubblePath, bubbleShadow)
        canvas.translate(-shadow, -shadow)
        bubbleFill.color = AppColors.HERO_TEXT
        bubbleFill.alpha = alpha
        canvas.drawPath(bubblePath, bubbleFill)
        bubbleText.color = AppColors.theme.palette.ink
        bubbleText.alpha = alpha
        canvas.drawText(line, bubbleRect.left + padX, bubbleRect.top + padY - metrics.ascent, bubbleText)
        canvas.restore()
    }

    /** A puffy line-art cloud in the park's sky, rising into place over the hero and bobbing while it's read. */
    private fun drawCloud(canvas: Canvas, line: String, since: Float, alpha: Int, density: Float) {
        val metrics = bubbleText.fontMetrics
        val padX = CLOUD_PAD_X * density
        val padY = 6f * density
        val w = bubbleLineWidth + 2f * padX
        val h = metrics.descent - metrics.ascent + 2f * padY
        val appear = if (reducedMotion) 1f else smoothstep((since / CLOUD_IN_SECONDS).coerceIn(0f, 1f))
        if (appear <= 0.01f) return
        val bob = if (reducedMotion) 0f else sin(TAU * since / CLOUD_BOB_SECONDS) * 1.2f * density
        // The puffs rise 0.44 of the body's height above it; the bob needs a little more.
        val puffs = h * (CLOUD_TOP_R.max() - 0.12f)
        val restTop = EDGE_DP * density + puffs + 1.5f * density
        val top = restTop + (1f - appear) * 10f * density + bob
        val centre = clearX(w + 8f * density, restTop - puffs, restTop + h + 4f * density, density)
        // It puffs up from 80% as it rises, and turns solid early, so nothing behind shows through its words.
        val grow = 0.8f + 0.2f * appear
        canvas.save()
        canvas.scale(grow, grow, centre, top + h * 0.5f)
        bubbleRect.set(centre - w * 0.5f, top, centre + w * 0.5f, top + h)
        bubblePath.rewind()
        bubblePath.addRoundRect(bubbleRect, h * 0.5f, h * 0.5f, Path.Direction.CW)
        for (i in CLOUD_TOP_X.indices) {
            puffPath.rewind()
            puffPath.addCircle(bubbleRect.left + w * CLOUD_TOP_X[i], bubbleRect.top + h * 0.12f, h * CLOUD_TOP_R[i], Path.Direction.CW)
            bubblePath.op(puffPath, Path.Op.UNION)
        }
        for (i in CLOUD_BOTTOM_X.indices) {
            puffPath.rewind()
            puffPath.addCircle(bubbleRect.left + w * CLOUD_BOTTOM_X[i], bubbleRect.bottom - h * 0.1f, h * 0.36f, Path.Direction.CW)
            bubblePath.op(puffPath, Path.Op.UNION)
        }
        val shown = (alpha * (appear / 0.3f).coerceAtMost(1f)).roundToInt()
        bubbleFill.color = blendColor(Color.WHITE, backdrop, 0.84f)
        bubbleFill.alpha = shown
        canvas.drawPath(bubblePath, bubbleFill)
        bubbleStroke.color = heroNear.color
        bubbleStroke.alpha = shown
        bubbleStroke.strokeWidth = 1.3f * scale
        canvas.drawPath(bubblePath, bubbleStroke)
        bubbleText.color = AppColors.HERO_TEXT
        bubbleText.alpha = shown
        canvas.drawText(line, bubbleRect.left + padX, bubbleRect.top + padY - metrics.ascent, bubbleText)
        canvas.restore()
    }

    /** A strip of the theme's tape, torn at both ends, unrolling left to right across the sky at a slight tilt. */
    private fun drawTape(canvas: Canvas, line: String, since: Float, alpha: Int, density: Float) {
        val metrics = bubbleText.fontMetrics
        val padX = TAPE_PAD_X * density
        val tooth = TAPE_TOOTH * density
        val padY = 3.5f * density
        val w = bubbleLineWidth + 2f * (padX + tooth)
        val h = metrics.descent - metrics.ascent + 2f * padY
        val unroll = if (reducedMotion) 1f else easeOut((since / TAPE_IN_SECONDS).coerceIn(0f, 1f))
        if (unroll <= 0.01f) return
        val top = (EDGE_DP + 3f) * density
        val centre = clearX(w, top - 4f * density, top + h + 4f * density, density)
        val left = centre - w * 0.5f
        val right = left + w
        val bottom = top + h
        canvas.save()
        canvas.rotate(TAPE_TILT, centre, top + h * 0.5f)
        canvas.clipRect(left - 1f, top - h, left + w * unroll + 1f, bottom + h)
        bubblePath.rewind()
        bubblePath.moveTo(left + tooth, top)
        bubblePath.lineTo(right - tooth, top)
        for (i in 1..TAPE_TEETH * 2) bubblePath.lineTo(if (i % 2 == 1) right else right - tooth, top + h * i / (TAPE_TEETH * 2))
        for (i in 1..TAPE_TEETH * 2) bubblePath.lineTo(if (i % 2 == 1) left else left + tooth, bottom - h * i / (TAPE_TEETH * 2))
        bubblePath.close()
        bubbleFill.color = AppColors.ACCENT
        bubbleFill.alpha = alpha
        canvas.drawPath(bubblePath, bubbleFill)
        // The sheen along the top that makes it read as tape, not a label.
        bubbleShadow.color = Color.WHITE
        bubbleShadow.alpha = alpha / 5
        canvas.drawRect(left + tooth, top + 1.5f * density, right - tooth, top + 3f * density, bubbleShadow)
        bubbleText.color = AppColors.contentOn(AppColors.ACCENT)
        bubbleText.alpha = alpha
        canvas.drawText(line, left + tooth + padX, top + padY - metrics.ascent, bubbleText)
        canvas.restore()
    }

    private fun drawFriend(canvas: Canvas, index: Int, t: Float) {
        val clapFlash = story.poseFriend(index, t, friend, friendJoints)
        val facing = story.friendFacing[index]
        if (facing < 0f) {
            for (joint in 0 until Rig.JOINT_COUNT) friendJoints[joint * 2] = 2f * CLIP_X - friendJoints[joint * 2]
        }
        val ox = originX + (story.friendX[index] - CLIP_X * FRIEND_SCALE) * scale
        val oy = originY + FLOOR_Y * (1f - FRIEND_SCALE) * scale
        val s = scale * FRIEND_SCALE
        canvas.drawLineFigure(
            friendJoints, friend.spine, facing, rig.headRadius, ox, oy, s,
            friendNear, friendFar, friendHead, null, torsoPath, FRIEND_STROKE,
        )
        friendHeadX[index] = ox + friendJoints[Rig.HEAD * 2] * s
        friendHeadY[index] = oy + (friendJoints[Rig.HEAD * 2 + 1] - rig.headRadius) * s
        if (clapFlash > 0.02f) {
            val hx = ox + friendJoints[Rig.HAND_NEAR * 2] * s
            val hy = oy + friendJoints[Rig.HAND_NEAR * 2 + 1] * s
            clapPaint.alpha = (clapFlash * 255f).roundToInt()
            for (ray in -1..1) {
                val angle = Math.toRadians((ray * 50f + 90f * facing).toDouble())
                val dx = sin(angle).toFloat()
                val dy = -cos(angle).toFloat()
                val r0 = 3.2f * scale
                val r1 = (3.2f + 2.6f * clapFlash) * scale
                canvas.drawLine(hx + dx * r0, hy + dy * r0, hx + dx * r1, hy + dy * r1, clapPaint)
            }
        }
    }

    private fun drawWalk(canvas: Canvas) {
        val t = if (reducedMotion) 0.05f * Gait.STEP_TIME else clock
        walk.evaluate(walk.loopStart() + t, hero, heroJoints)
        val left = heroJoints[Rig.HIP * 2] - width / scale * 0.5f
        val right = left + width / scale
        val ox = -left * scale
        val groundY = originY + FLOOR_Y * scale
        canvas.drawLine(0f, groundY, width.toFloat(), groundY, groundPaint)
        // Path marks scroll with the planted foot, so the walk reads as moving forward.
        val gap = 2f * walk.stride / 3f
        var mark = floor(left / gap) * gap
        val markY = originY + (FLOOR_Y + 3.2f) * scale
        while (mark < right) {
            canvas.drawLine(ox + mark * scale, markY, ox + (mark + 3f) * scale, markY, dashPaint)
            mark += gap
        }
        canvas.drawLineFigure(
            heroJoints, hero.spine, hero.facing, rig.headRadius, ox, originY, scale,
            heroNear, heroFar, heroHead, heroHand, torsoPath,
        )
    }

    /** Pull-ups from the front: the chin clears the bar, then the figure lowers to a straight-armed hang. */
    private fun drawPull(canvas: Canvas) {
        // Reduced motion holds the top of the rep, chin over the bar.
        val lift = if (reducedMotion) 1f else ParkStory.pull(clock / PULL_REP)
        pullRect.set(0f, 0f, width.toFloat(), height.toFloat())
        FrontView.draw(canvas, FrontView.PULL_UP, lift, pullRect, FrontView.LOADING_WINDOW, pullInk, pullFrame)
    }

    private fun edgeFade(color: Int, width: Int, fade: Float): Shader {
        val clear = color and 0x00FFFFFF
        return LinearGradient(
            0f, 0f, width.toFloat(), 0f,
            intArrayOf(clear, color, color, clear),
            floatArrayOf(0f, fade, 1f - fade, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    private fun stroke(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = color
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color
    }

    private companion object {
        const val FRIEND_SCALE = 0.82f
        const val FRIEND_STROKE = 1.1f
        const val FINALE_HOLD_SECONDS = 2f
        const val LOOP_FADE_SECONDS = 0.45f
        const val MAX_FRAME_SECONDS = 0.1f

        const val SCENE_TOP = -9f
        const val SCENE_BOTTOM = 110f
        const val WALK_TOP = 36f
        const val WALK_BOTTOM = 112f
        const val PULL_REP = 1.6f
        const val TREE_X = 15f
        const val LOOP_INTERVALS = 20
        const val FLOOR_Y = ParkStory.FLOOR_Y
        const val CLIP_X = 100f // the authored clips are centred on x = 100

        // The greeting: the final skill holds longer while it shows; it starts just before the skill lands.
        const val BUBBLE_HOLD_SECONDS = 3.4f
        const val BUBBLE_LEAD_SECONDS = 0.35f
        const val BUBBLE_IN_SECONDS = 0.35f
        const val CLOUD_IN_SECONDS = 0.7f
        const val CLOUD_BOB_SECONDS = 2.4f
        const val TAPE_IN_SECONDS = 0.55f
        const val GREETING_IN_FULL = 10f
        const val TAPE_TILT = -4f
        const val TAPE_TEETH = 3
        // In dp: from the view's edges, and inside each shape.
        const val EDGE_DP = 6f
        const val SPEECH_PAD_X = 9f
        const val CLOUD_PAD_X = 13f
        const val TAPE_PAD_X = 9f
        const val TAPE_TOOTH = 3.5f
        val CLOUD_TOP_X = floatArrayOf(0.24f, 0.5f, 0.76f)
        val CLOUD_TOP_R = floatArrayOf(0.42f, 0.56f, 0.4f)
        val CLOUD_BOTTOM_X = floatArrayOf(0.34f, 0.68f)
        const val TAU = 6.2831855f

        fun smoothstep(x: Float) = x * x * (3f - 2f * x)
        fun easeOut(x: Float) = 1f - (1f - x) * (1f - x) * (1f - x)

        /** Overshoots a little before settling: the bubble's pop. */
        fun backOut(x: Float): Float {
            val c1 = 1.70158f
            val q = x - 1f
            return 1f + (c1 + 1f) * q * q * q + c1 * q * q
        }
    }
}
