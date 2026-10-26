package com.justdataplease.streetgym.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Shader
import android.view.View
import android.view.ViewTreeObserver
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.AnimationUtils
import com.justdataplease.streetgym.BuildConfig
import com.justdataplease.streetgym.R
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The Today hero's line-art park, drawn with the movement guides' figure ([MotionClip], [drawLineFigure]).
 *
 * [Mode.STORY]: a routine generated per local day ([ParkRoutine]): three different movements, a fundamental, a harder
 * strength move and a held skill. The figure walks into the park and trains while three smaller friends wave, clap,
 * hop and cheer; the choreography lives in [ParkStory]. A park that needs more room than 200 units zooms out. It
 * runs once per process and ends on the held skill, where the figure keeps breathing; a tap replays it. Debug
 * builds step to the next day's routine on a long press.
 * [Mode.WALK]: the walk cycle alone on a scrolling path, for the loading screen.
 *
 * Reduced motion shows the final frame (the signature skill), and nothing runs while the view is off-screen.
 * Canvas units are the clips' (floor at y = 106).
 */
class ParkSceneView(context: Context, private val mode: Mode = Mode.STORY) : View(context) {
    enum class Mode { STORY, WALK }

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
    private var startIdle = mode == Mode.STORY && storyPlayed
    private val visibleRect = Rect()
    private var scrollListenerAttached = false
    private val scrollChangedListener = ViewTreeObserver.OnScrollChangedListener { updateAnimationState() }
    private val frameTick = Runnable { invalidate() }

    // STORY sits on the dark hero card in both themes; WALK on the page background.
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

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        if (mode == Mode.WALK) {
            contentDescription = resources.getString(R.string.park_scene_walk_description)
            walk.plan(0f, Gait.STEP_LENGTH * LOOP_INTERVALS, 0f)
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
        val desiredWidth = (if (mode == Mode.WALK) 200f else 320f) * density
        val desiredHeight = (if (mode == Mode.WALK) 96f else 180f) * density
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
        } else {
            val idle = startIdle || (story.end > 0f && clock >= story.end)
            fitStory()
            if (idle) clock = story.end + ParkStory.IDLE_WRAP_AT
            startIdle = false
            fade = 0.12f
        }
        groundPaint.shader = edgeFade(groundColor, w, fade)
        dashPaint.shader = edgeFade(dashColor, w, fade)
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
        removeCallbacks(frameTick)
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
        if (mode == Mode.WALK) drawWalk(canvas) else drawStory(canvas)
        if (!ticking) return
        if (mode == Mode.STORY && clock >= story.end) {
            // The idle only breathes and waves, so it does not need every vsync.
            removeCallbacks(frameTick)
            postDelayed(frameTick, IDLE_FRAME_MS)
        } else {
            postInvalidateOnAnimation()
        }
    }

    private fun advance(seconds: Float) {
        clock += seconds.coerceIn(0f, MAX_FRAME_SECONDS)
        if (mode == Mode.WALK) {
            clock %= 2f * Gait.STEP_TIME
            return
        }
        if (clock < story.end) storyPlayed = true
        // Wrap after the last cheer has faded and before the first idle wave, so nothing jumps.
        if (clock - story.end >= ParkStory.IDLE_WRAP_AT + ParkStory.IDLE_CYCLE) clock -= ParkStory.IDLE_CYCLE
    }

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
        ticking = animate
        lastFrameMs = 0L
        if (!animate) removeCallbacks(frameTick)
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
        canvas.drawLineFigure(
            heroJoints, hero.spine, hero.facing, rig.headRadius, originX, originY, scale,
            heroNear, heroFar, heroHead, heroHand, torsoPath,
        )
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
        /** The story plays once per process; later Today screens open on the final hold. */
        var storyPlayed = false

        const val FRIEND_SCALE = 0.82f
        const val FRIEND_STROKE = 1.1f
        const val IDLE_FRAME_MS = 33L
        const val MAX_FRAME_SECONDS = 0.1f

        const val SCENE_TOP = -9f
        const val SCENE_BOTTOM = 110f
        const val WALK_TOP = 36f
        const val WALK_BOTTOM = 112f
        const val TREE_X = 15f
        const val LOOP_INTERVALS = 20
        const val FLOOR_Y = ParkStory.FLOOR_Y
        const val CLIP_X = 100f // the authored clips are centred on x = 100
    }
}
