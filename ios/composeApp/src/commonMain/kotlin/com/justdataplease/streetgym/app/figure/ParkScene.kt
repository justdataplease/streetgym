package com.justdataplease.streetgym.app.figure

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.park_move_dips
import com.justdataplease.streetgym.app.resources.park_move_front_lever
import com.justdataplease.streetgym.app.resources.park_move_handstand
import com.justdataplease.streetgym.app.resources.park_move_human_flag
import com.justdataplease.streetgym.app.resources.park_move_knee_raises
import com.justdataplease.streetgym.app.resources.park_move_muscle_up
import com.justdataplease.streetgym.app.resources.park_move_pistol_squats
import com.justdataplease.streetgym.app.resources.park_move_pull_ups
import com.justdataplease.streetgym.app.resources.park_move_squats
import com.justdataplease.streetgym.app.resources.park_move_tuck_lever
import com.justdataplease.streetgym.app.resources.park_scene_description
import com.justdataplease.streetgym.app.resources.park_scene_pull_description
import com.justdataplease.streetgym.app.resources.park_scene_replay
import com.justdataplease.streetgym.app.resources.park_scene_walk_description
import com.justdataplease.streetgym.app.theme.Palette
import com.justdataplease.streetgym.app.theme.Street
import com.justdataplease.streetgym.app.theme.blendColor
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Today's park and the loading screen's figure: android-streetgym, ui/ParkSceneView.kt, drawn with Compose's Canvas
 * (the one under every DrawScope) and the movement guides' line man (drawLineFigure, FrontView), paint for paint.
 * The greeting with a username ("Well done, <name>!") waits for the account (round 12).
 */

/** What the scene plays (Android: ParkSceneView.Mode). */
internal enum class ParkMode {
    /** The day's routine in the park, on Today's dark brand card. */
    STORY,
    /** The walk alone on a scrolling path: the loading screen. */
    WALK,
    /** Pull-ups seen from the front: the loading screen's other figure, one time in three. */
    PULL,
}

/**
 * Story time in seconds to hold the park at, for the screenshots; null plays it. Under [LocalFigureHold] without it the
 * park holds its final frame, as with reduced motion.
 */
val LocalParkTime = staticCompositionLocalOf<Float?> { null }

/** The words that name [move] in the scene's spoken description (Android: ParkMove.label). */
internal fun ParkMove.label(): StringResource = when (this) {
    ParkMove.SQUATS -> Res.string.park_move_squats
    ParkMove.DIPS -> Res.string.park_move_dips
    ParkMove.KNEE_RAISES -> Res.string.park_move_knee_raises
    ParkMove.PULL_UPS -> Res.string.park_move_pull_ups
    ParkMove.PISTOL_SQUATS -> Res.string.park_move_pistol_squats
    ParkMove.MUSCLE_UP -> Res.string.park_move_muscle_up
    ParkMove.HANDSTAND -> Res.string.park_move_handstand
    ParkMove.TUCK_LEVER -> Res.string.park_move_tuck_lever
    ParkMove.FRONT_LEVER -> Res.string.park_move_front_lever
    ParkMove.HUMAN_FLAG -> Res.string.park_move_human_flag
}

/**
 * The park on Today's brand card (Android: ParkSceneView STORY): the routine of local day [day], walked in and trained
 * while three friends cheer; it holds the final skill, fades the hero and starts again. It plays only while on screen,
 * starts over each time it comes back into view and each time the app comes back ([resumes]), and a tap starts it again
 * ([onReplay] first, which may move the day on after midnight). Reduced motion holds the final skill still.
 */
@Composable
fun ParkScene(day: Long, resumes: Int, modifier: Modifier = Modifier, onReplay: () -> Unit = {}) {
    val palette = Street.colors
    val library = LocalMotionLibrary.current ?: remember { AnimationLibrary.fallbackLibrary() }
    val routine = remember(day) { ParkRoutine.forEpochDay(day) }
    val scene = remember(palette, library, routine) { ParkRenderer(ParkMode.STORY, palette, library, routine) }
    val description = stringResource(Res.string.park_scene_description,
        stringResource(routine.opener.label()), stringResource(routine.middle.label()), stringResource(routine.finale.label()))
    val replayLabel = stringResource(Res.string.park_scene_replay)
    val reduced = LocalReduceMotion.current
    // A held moment (the screenshots); the final hold is known only once the park is laid out on its canvas.
    val heldAt = LocalParkTime.current
    val holdFinale = heldAt == null && LocalFigureHold.current != null
    var shown by remember { mutableStateOf(false) }
    var replays by remember { mutableIntStateOf(0) }
    var clock by remember(scene) { mutableFloatStateOf(0f) }
    val playing = !reduced && heldAt == null && !holdFinale && shown
    LaunchedEffect(scene, playing, replays, resumes) {
        // Coming back into view, a tap and a return to the app all start the routine from its walk-in.
        clock = 0f
        if (!playing) return@LaunchedEffect
        var last = -1L
        while (true) withInfiniteAnimationFrameNanos { now ->
            if (last >= 0L) clock = scene.advance(clock, (now - last) / 1_000_000_000f)
            last = now
        }
    }
    val replay = rememberUpdatedState {
        if (!reduced) {
            onReplay()
            replays++
        }
    }
    val ready = scene.ready
    Canvas(
        modifier
            .onGloballyPositioned { shown = !it.boundsInWindow().isEmpty }
            .then(if (ready) Modifier.pointerInput(Unit) { detectTapGestures { replay.value() } } else Modifier)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Image
                if (ready) onClick(label = replayLabel) { replay.value(); true }
            },
    ) {
        scene.layout(size.width, size.height)
        drawIntoCanvas { scene.draw(it, heldAt ?: if (holdFinale) scene.finaleAt else clock, still = reduced) }
    }
}

/**
 * The loading screen's figure (Android: renderLoading's ParkSceneView): the walk on a scrolling path, or with [pull]
 * pull-ups seen from the front, on the page's colours. Reduced motion holds a still frame.
 */
@Composable
fun LoadingFigure(pull: Boolean, modifier: Modifier = Modifier) {
    val palette = Street.colors
    val scene = remember(palette, pull) { ParkRenderer(if (pull) ParkMode.PULL else ParkMode.WALK, palette, null, null) }
    val description = stringResource(if (pull) Res.string.park_scene_pull_description else Res.string.park_scene_walk_description)
    val reduced = LocalReduceMotion.current
    val held = LocalFigureHold.current
    var clock by remember(scene) { mutableFloatStateOf(0f) }
    LaunchedEffect(scene, reduced, held) {
        if (reduced || held != null) return@LaunchedEffect
        var last = -1L
        while (true) withInfiniteAnimationFrameNanos { now ->
            if (last >= 0L) clock = scene.advance(clock, (now - last) / 1_000_000_000f)
            last = now
        }
    }
    Canvas(modifier.clearAndSetSemantics {
        contentDescription = description
        role = Role.Image
    }) {
        scene.layout(size.width, size.height)
        val time = if (held != null) held.phase * scene.loopSeconds else clock
        drawIntoCanvas { scene.draw(it, time, still = reduced) }
    }
}

/**
 * The scene's drawing, in the canvas's pixels (Android: ParkSceneView's paints, onSizeChanged and onDraw). [routine]
 * is the day's (STORY only); [library] gives the clips and the rig.
 */
internal class ParkRenderer(
    private val mode: ParkMode,
    palette: Palette,
    library: AnimationLibrary?,
    routine: ParkRoutine?,
) {
    private val rig = if (mode == ParkMode.STORY) library?.rig ?: Rig.DEFAULT else Rig.DEFAULT
    private val story: ParkStory? = if (mode == ParkMode.STORY && routine != null)
        ParkStory(rig, routine) { name -> library?.let { MotionClip.named(it, name) } } else null
    private val walk = Gait(rig).apply { if (mode == ParkMode.WALK) plan(0f, Gait.STEP_LENGTH * LOOP_INTERVALS, 0f) }

    private val hero = Pose()
    private val heroJoints = FloatArray(Rig.JOINT_COUNT * 2)
    private val friend = Pose()
    private val friendJoints = FloatArray(Rig.JOINT_COUNT * 2)
    private val torsoPath = Path()

    private var width = 0f
    private var height = 0f
    private var scale = 1f
    private var originX = 0f
    private var originY = 0f

    // STORY sits on the dark brand card in every theme; WALK and PULL on the page.
    private val backdrop = if (mode == ParkMode.STORY) palette.primaryDark else palette.background
    private val ink = if (mode == ParkMode.STORY) Color.White else palette.text
    private val accent = if (mode == ParkMode.STORY) palette.accent else palette.primary
    private val heroNear = strokePaint(blendColor(ink, backdrop, if (mode == ParkMode.STORY) 0.06f else 0f))
    private val heroFar = strokePaint(blendColor(ink, backdrop, if (mode == ParkMode.STORY) 0.45f else 0.42f))
    private val heroHead = fillPaint(heroNear.color)
    private val heroHand = fillPaint(accent)
    private val friendNear = strokePaint(blendColor(ink, backdrop, 0.52f))
    private val friendFar = strokePaint(blendColor(ink, backdrop, 0.68f))
    private val friendHead = fillPaint(friendNear.color)
    private val clapPaint = strokePaint(blendColor(accent, backdrop, 0.25f))
    private val barPaint = strokePaint(blendColor(ink, backdrop, 0.42f))
    private val postPaint = strokePaint(blendColor(ink, backdrop, 0.62f))
    private val trunkPaint = strokePaint(blendColor(ink, backdrop, 0.62f))
    private val leafPaint = strokePaint(blendColor(accent, backdrop, 0.5f))
    private val leafFill = fillPaint(blendColor(accent, backdrop, 0.9f))
    private val groundColor = if (mode == ParkMode.STORY) blendColor(ink, backdrop, 0.6f) else blendColor(palette.textSecondary, backdrop, 0.35f)
    private val dashColor = blendColor(palette.textSecondary, backdrop, 0.55f)
    private val groundPaint = strokePaint(groundColor).apply { strokeCap = StrokeCap.Butt }
    private val dashPaint = strokePaint(dashColor)
    private val pullInk = FrontInk(heroNear, heroHead, heroHand, barPaint, postPaint)
    private val pullFrame = FrontView.Frame()

    /** False when a clip is missing: the hero then only walks in. */
    val ready: Boolean get() = story?.ready == true

    /** When the final skill lands (story time). */
    val finaleAt: Float get() = story?.end ?: 0f

    /** When the loop starts over: the final skill holds, then the hero fades out. */
    val loopSeconds: Float get() = when (mode) {
        ParkMode.STORY -> finaleAt + FINALE_HOLD_SECONDS + LOOP_FADE_SECONDS
        ParkMode.WALK -> 2f * Gait.STEP_TIME
        ParkMode.PULL -> PULL_REP
    }

    /** [clock] moved on by a frame of [seconds], at most a tenth of a second, wrapping at the loop. */
    fun advance(clock: Float, seconds: Float): Float = (clock + seconds.coerceIn(0f, MAX_FRAME_SECONDS)) % loopSeconds

    /** Fits the scene to a canvas of [w] by [h] pixels (Android: onSizeChanged). */
    fun layout(w: Float, h: Float) {
        if (w == width && h == height || w <= 0f || h <= 0f) return
        width = w
        height = h
        val fade = when (mode) {
            ParkMode.WALK -> {
                scale = h / (WALK_BOTTOM - WALK_TOP)
                originY = -WALK_TOP * scale
                0.3f
            }
            ParkMode.PULL -> 0.3f
            ParkMode.STORY -> {
                story?.let { story ->
                    // A park wider than 200 units zooms out.
                    scale = min(w / story.width, h / (SCENE_BOTTOM - SCENE_TOP))
                    originX = (w - story.width * scale) * 0.5f
                    originY = h - SCENE_BOTTOM * scale
                    story.plan(-originX / scale)
                }
                0.12f
            }
        }
        groundPaint.shader = edgeFade(groundColor, w, fade)
        dashPaint.shader = edgeFade(dashColor, w, fade)
        groundPaint.strokeWidth = 1.4f * scale
        dashPaint.strokeWidth = 1.2f * scale
        barPaint.strokeWidth = 1.8f * scale
        postPaint.strokeWidth = 1.6f * scale
        leafPaint.strokeWidth = 1.5f * scale
        clapPaint.strokeWidth = scale
    }

    /** Draws the scene at [time] seconds; [still] for reduced motion. */
    fun draw(canvas: Canvas, time: Float, still: Boolean) {
        if (width <= 0f || height <= 0f) return
        when (mode) {
            ParkMode.STORY -> drawStory(canvas, time, still)
            ParkMode.WALK -> drawWalk(canvas, if (still) 0.05f * Gait.STEP_TIME else time)
            ParkMode.PULL -> drawPull(canvas, if (still) 1f else ParkStory.pull(time / PULL_REP))
        }
    }

    private fun drawStory(canvas: Canvas, clock: Float, still: Boolean) {
        val story = story ?: return
        val t = if (still) story.end else clock
        fun x(value: Float) = originX + value * scale
        fun y(value: Float) = originY + value * scale

        canvas.drawLine(0f, y(FLOOR_Y), width, y(FLOOR_Y), groundPaint)
        canvas.drawCircle(x(TREE_X), y(40f), 15f * scale, leafFill)
        canvas.drawCircle(x(TREE_X), y(40f), 15f * scale, leafPaint)
        trunkPaint.strokeWidth = 2f * scale
        canvas.drawLine(x(TREE_X), y(FLOOR_Y), x(TREE_X), y(44f), trunkPaint)
        trunkPaint.strokeWidth = 1.5f * scale
        canvas.drawLine(x(TREE_X), y(58f), x(TREE_X + 7f), y(47f), trunkPaint)
        canvas.drawLine(x(TREE_X), y(53f), x(TREE_X - 6f), y(44f), trunkPaint)
        for (i in story.props.indices) {
            val prop = story.props[i]
            val overhang = if (prop.kind == ParkProp.BAR) ParkProp.BAR_OVERHANG else ParkProp.PARALLEL_OVERHANG
            canvas.drawLine(x(prop.left), y(prop.y), x(prop.left), y(FLOOR_Y), postPaint)
            canvas.drawLine(x(prop.right), y(prop.y), x(prop.right), y(FLOOR_Y), postPaint)
            canvas.drawLine(x(prop.left - overhang), y(prop.y), x(prop.right + overhang), y(prop.y), barPaint)
        }

        for (index in 0 until ParkStory.FRIENDS) drawFriend(canvas, story, index, t)

        story.poseHero(t, hero, heroJoints)
        val fadeOut = if (still) 1f else ((loopSeconds - clock) / LOOP_FADE_SECONDS).coerceIn(0f, 1f)
        val opacity = (255f * fadeOut).roundToInt() / 255f
        heroNear.alpha = opacity
        heroFar.alpha = opacity
        heroHead.alpha = opacity
        heroHand.alpha = opacity
        canvas.drawLineFigure(
            heroJoints, hero.spine, hero.facing, rig.headRadius, originX, originY, scale,
            heroNear, heroFar, heroHead, heroHand, torsoPath,
        )
    }

    private fun drawFriend(canvas: Canvas, story: ParkStory, index: Int, t: Float) {
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
            clapPaint.alpha = (clapFlash * 255f).roundToInt() / 255f
            for (ray in -1..1) {
                val angle = (ray * 50f + 90f * facing).toDouble() * DEGREES_TO_RADIANS
                val dx = sin(angle).toFloat()
                val dy = -cos(angle).toFloat()
                val r0 = 3.2f * scale
                val r1 = (3.2f + 2.6f * clapFlash) * scale
                canvas.drawLine(hx + dx * r0, hy + dy * r0, hx + dx * r1, hy + dy * r1, clapPaint)
            }
        }
    }

    private fun drawWalk(canvas: Canvas, t: Float) {
        walk.evaluate(walk.loopStart() + t, hero, heroJoints)
        val left = heroJoints[Rig.HIP * 2] - width / scale * 0.5f
        val right = left + width / scale
        val ox = -left * scale
        val groundY = originY + FLOOR_Y * scale
        canvas.drawLine(0f, groundY, width, groundY, groundPaint)
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

    /** Pull-ups from the front, fitted through the loading window: posts and floor included (Android: FrontView.draw). */
    private fun drawPull(canvas: Canvas, lift: Float) {
        FrontView.pose(FrontView.PULL_UP, lift, pullFrame)
        val windowWidth = LOADING_RIGHT - LOADING_LEFT
        val windowHeight = LOADING_BOTTOM - LOADING_TOP
        val fit = min(width / windowWidth, height / windowHeight)
        val ox = width / 2f - (LOADING_LEFT + LOADING_RIGHT) / 2f * fit
        val oy = height / 2f - (LOADING_TOP + LOADING_BOTTOM) / 2f * fit
        FrontView.draw(canvas, pullFrame, ox, oy, fit, pullInk, 1f, windowWidth / 2f)
    }

    private fun edgeFade(color: Color, width: Float, fade: Float) = LinearGradientShader(
        from = Offset(0f, 0f),
        to = Offset(width, 0f),
        colors = listOf(color.copy(alpha = 0f), color, color, color.copy(alpha = 0f)),
        colorStops = listOf(0f, fade, 1f - fade, 1f),
    )

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

        // The loading screen's frame: the bar, a standing start and the floor (Android: FrontView.LOADING_WINDOW).
        const val LOADING_LEFT = FrontView.CENTER - 30f
        const val LOADING_TOP = FrontView.BAR_Y - 14f
        const val LOADING_RIGHT = FrontView.CENTER + 30f
        const val LOADING_BOTTOM = FrontView.FLOOR_Y + 2f
    }
}
