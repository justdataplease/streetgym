package com.justdataplease.streetgym.app.figure

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.motion_marker_back_knee_down
import com.justdataplease.streetgym.app.resources.motion_marker_chest_to_bar
import com.justdataplease.streetgym.app.resources.motion_marker_chest_to_edge
import com.justdataplease.streetgym.app.resources.motion_marker_chest_to_fist
import com.justdataplease.streetgym.app.resources.motion_marker_chest_to_wall
import com.justdataplease.streetgym.app.resources.motion_marker_chin
import com.justdataplease.streetgym.app.resources.motion_marker_feet_off
import com.justdataplease.streetgym.app.resources.motion_marker_feet_to_hips
import com.justdataplease.streetgym.app.resources.motion_marker_head_ahead
import com.justdataplease.streetgym.app.resources.motion_marker_head_to_mat
import com.justdataplease.streetgym.app.resources.motion_marker_heels_high
import com.justdataplease.streetgym.app.resources.motion_marker_hips_in_line
import com.justdataplease.streetgym.app.resources.motion_marker_hips_level
import com.justdataplease.streetgym.app.resources.motion_marker_hips_to_knees
import com.justdataplease.streetgym.app.resources.motion_marker_knees_to_hips
import com.justdataplease.streetgym.app.resources.motion_marker_legs_level
import com.justdataplease.streetgym.app.resources.motion_marker_range
import com.justdataplease.streetgym.app.resources.motion_marker_shoulders_down
import com.justdataplease.streetgym.app.resources.motion_marker_shoulders_to_elbows
import com.justdataplease.streetgym.app.resources.motion_marker_stand_tall
import com.justdataplease.streetgym.app.resources.motion_marker_toes_to_bar
import com.justdataplease.streetgym.app.resources.motion_phase_come_down
import com.justdataplease.streetgym.app.resources.motion_phase_down
import com.justdataplease.streetgym.app.resources.motion_phase_hang
import com.justdataplease.streetgym.app.resources.motion_phase_hold
import com.justdataplease.streetgym.app.resources.motion_phase_hold_breathe
import com.justdataplease.streetgym.app.resources.motion_phase_lower
import com.justdataplease.streetgym.app.resources.motion_phase_lower_slow
import com.justdataplease.streetgym.app.resources.motion_phase_move
import com.justdataplease.streetgym.app.resources.motion_phase_pause
import com.justdataplease.streetgym.app.resources.motion_phase_press
import com.justdataplease.streetgym.app.resources.motion_phase_pull
import com.justdataplease.streetgym.app.resources.motion_phase_reset
import com.justdataplease.streetgym.app.resources.motion_phase_return
import com.justdataplease.streetgym.app.resources.motion_phase_stand
import com.justdataplease.streetgym.app.resources.motion_phase_step_down
import com.justdataplease.streetgym.app.resources.motion_phase_step_up
import com.justdataplease.streetgym.app.resources.motion_phase_up
import com.justdataplease.streetgym.app.resources.motion_view_end
import com.justdataplease.streetgym.app.resources.motion_view_front
import com.justdataplease.streetgym.app.resources.motion_view_side
import com.justdataplease.streetgym.app.theme.Street
import com.justdataplease.streetgym.app.theme.blendColor
import com.justdataplease.streetgym.data.effort.Effort
import com.justdataplease.streetgym.data.model.TrainingExercise
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.floor

/** The animations file, read once with the program; without it every figure is Android's standing fallback. */
val LocalMotionLibrary = staticCompositionLocalOf<AnimationLibrary?> { null }

/** The guides' words in the app's language. */
val LocalMotionWords = staticCompositionLocalOf<Map<MotionText, String>> { emptyMap() }

/**
 * Holds every figure still at [phase] of its loop, [cycle] loops in, as if playing (Android: ExerciseAnimationView.hold,
 * for the gallery's review sheets): the screenshots draw the same frame on every run.
 */
class FigureHold(val phase: Float, val cycle: Int = 0)

val LocalFigureHold = staticCompositionLocalOf<FigureHold?> { null }

/**
 * The phone asks for less motion (iPhone: Reduce Motion; Android: animations switched off): every figure stands still
 * on its clip's reduced-motion frame, without the phase label and tempo strip.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

private fun MotionText.resource(): StringResource = when (this) {
    MotionText.PHASE_PULL -> Res.string.motion_phase_pull
    MotionText.PHASE_HOLD -> Res.string.motion_phase_hold
    MotionText.PHASE_LOWER -> Res.string.motion_phase_lower
    MotionText.PHASE_HANG -> Res.string.motion_phase_hang
    MotionText.PHASE_PAUSE -> Res.string.motion_phase_pause
    MotionText.PHASE_PRESS -> Res.string.motion_phase_press
    MotionText.PHASE_STAND -> Res.string.motion_phase_stand
    MotionText.PHASE_RESET -> Res.string.motion_phase_reset
    MotionText.PHASE_UP -> Res.string.motion_phase_up
    MotionText.PHASE_DOWN -> Res.string.motion_phase_down
    MotionText.PHASE_MOVE -> Res.string.motion_phase_move
    MotionText.PHASE_RETURN -> Res.string.motion_phase_return
    MotionText.PHASE_HOLD_BREATHE -> Res.string.motion_phase_hold_breathe
    MotionText.PHASE_LOWER_SLOW -> Res.string.motion_phase_lower_slow
    MotionText.PHASE_STEP_DOWN -> Res.string.motion_phase_step_down
    MotionText.PHASE_STEP_UP -> Res.string.motion_phase_step_up
    MotionText.PHASE_COME_DOWN -> Res.string.motion_phase_come_down
    MotionText.MARKER_CHIN -> Res.string.motion_marker_chin
    MotionText.MARKER_RANGE -> Res.string.motion_marker_range
    MotionText.MARKER_CHEST_TO_BAR -> Res.string.motion_marker_chest_to_bar
    MotionText.MARKER_CHEST_TO_FIST -> Res.string.motion_marker_chest_to_fist
    MotionText.MARKER_CHEST_TO_EDGE -> Res.string.motion_marker_chest_to_edge
    MotionText.MARKER_CHEST_TO_WALL -> Res.string.motion_marker_chest_to_wall
    MotionText.MARKER_SHOULDERS_TO_ELBOWS -> Res.string.motion_marker_shoulders_to_elbows
    MotionText.MARKER_HIPS_TO_KNEES -> Res.string.motion_marker_hips_to_knees
    MotionText.MARKER_BACK_KNEE_DOWN -> Res.string.motion_marker_back_knee_down
    MotionText.MARKER_KNEES_TO_HIPS -> Res.string.motion_marker_knees_to_hips
    MotionText.MARKER_FEET_TO_HIPS -> Res.string.motion_marker_feet_to_hips
    MotionText.MARKER_TOES_TO_BAR -> Res.string.motion_marker_toes_to_bar
    MotionText.MARKER_HIPS_IN_LINE -> Res.string.motion_marker_hips_in_line
    MotionText.MARKER_HEAD_AHEAD -> Res.string.motion_marker_head_ahead
    MotionText.MARKER_HEAD_TO_MAT -> Res.string.motion_marker_head_to_mat
    MotionText.MARKER_SHOULDERS_DOWN -> Res.string.motion_marker_shoulders_down
    MotionText.MARKER_HEELS_HIGH -> Res.string.motion_marker_heels_high
    MotionText.MARKER_HIPS_LEVEL -> Res.string.motion_marker_hips_level
    MotionText.MARKER_LEGS_LEVEL -> Res.string.motion_marker_legs_level
    MotionText.MARKER_FEET_OFF -> Res.string.motion_marker_feet_off
    MotionText.MARKER_STAND_TALL -> Res.string.motion_marker_stand_tall
    MotionText.VIEW_SIDE -> Res.string.motion_view_side
    MotionText.VIEW_FRONT -> Res.string.motion_view_front
    MotionText.VIEW_END -> Res.string.motion_view_end
}

/** Every guide word from the Android strings, read once where the theme is set. */
@Composable
fun motionWords(): Map<MotionText, String> {
    val values = MotionText.entries.map { stringResource(it.resource()) }
    return remember(values) { MotionText.entries.zip(values).toMap() }
}

/** Where a playing figure is in its loop: the phase (0..1) and the loops finished. Read while drawing only. */
@Stable
private class FigureClock {
    var phase by mutableFloatStateOf(0f)
        private set
    var cycle by mutableIntStateOf(0)
        private set

    fun restart(start: Float) {
        phase = start
        cycle = 0
    }

    /** [total] loops from the start: its whole part is the loops finished, the rest the phase. */
    fun at(total: Double) {
        val whole = floor(total)
        phase = (total - whole).toFloat()
        cycle = whole.toInt()
    }
}

/**
 * An exercise's line man (Android: ExerciseAnimationView bound to it), in the panel with its rounded hairline frame and
 * Android's 10 dp and 8 dp padding inside the given size: the side view, and the front view beside it where Android
 * shows one. It plays while any of it is on screen, a loop of its clip at Android's tempo, and stands still on its
 * reduced-motion frame otherwise. [badge]: the flat tile of a skill or a workout instead, [moving] when it plays.
 */
@Composable
fun ExerciseFigure(
    movementId: String?,
    exerciseName: String,
    modifier: Modifier = Modifier,
    category: String? = null,
    equipment: List<String> = emptyList(),
    descriptor: String? = null,
    badge: BadgeStyle? = null,
    moving: Boolean = false,
) {
    val library = LocalMotionLibrary.current ?: remember { AnimationLibrary.fallbackLibrary() }
    val palette = Street.colors
    val density = LocalDensity.current
    val words = LocalMotionWords.current
    val renderer = remember(library, palette, density.density, words, movementId, exerciseName, category, equipment, descriptor, badge, moving) {
        FigureRenderer(library, palette, density.density) { words[it].orEmpty() }.apply {
            bind(movementId, exerciseName, category, equipment, descriptor)
            if (badge != null) setBadgeStyle(badge, moving)
        }
    }
    val measurer = rememberTextMeasurer(cacheSize = 16)
    val family = Street.type.bold
    val text = remember(measurer, family, density) { FigureText(measurer, family, density) }
    val hold = LocalFigureHold.current
    val still = LocalReduceMotion.current
    var shown by remember { mutableStateOf(false) }
    val clock = remember(renderer) { FigureClock() }
    // Android plays a guide only while some of it is on screen (getGlobalVisibleRect) and animations are on.
    val plays = renderer.plays && hold == null && !still && shown
    LaunchedEffect(renderer, plays) {
        clock.restart(renderer.loopStartPhase)
        if (!plays) return@LaunchedEffect
        val loop = renderer.loopDurationMs.coerceAtLeast(1L) * 1_000_000.0
        var start = -1L
        while (true) withInfiniteAnimationFrameNanos { now ->
            if (start < 0L) start = now
            clock.at(renderer.loopStartPhase + (now - start) / loop)
        }
    }
    val description = if (badge == null) "Line-figure movement guide for ${exerciseName.trim().ifBlank { "this exercise" }}. " +
        "Follow the written instructions for exact technique." else null
    Canvas(
        modifier
            .onGloballyPositioned { shown = !it.boundsInWindow().isEmpty }
            .then(if (description != null) Modifier.semantics {
                contentDescription = description
                role = Role.Image
            } else Modifier),
    ) {
        if (hold != null) renderer.draw(this, text, hold.phase, hold.cycle, playing = renderer.plays)
        else renderer.draw(this, text, clock.phase, clock.cycle, playing = plays)
    }
}

/** Easy green, Moderate orange, Hard red: the skills' difficulty colours for Easy, Very hard and Extreme (Android: effortColor). */
fun effortColor(effort: Effort): Color = when (effort) {
    Effort.EASY -> Color(0xFF2E9E5B)
    Effort.MODERATE -> Color(0xFFE07B2A)
    Effort.HARD -> Color(0xFFD2353F)
}

/**
 * A workout's icon (Android: EffortUi.WorkoutTile, the level lists' rows since 2.9.1): its hardest [exercise] playing on
 * a tile [size] wide, ringed in the workout's [effort] colour for this athlete. Ready for the Training tab's rows.
 */
@Composable
fun WorkoutTile(exercise: TrainingExercise?, effort: Effort?, size: Dp, modifier: Modifier = Modifier) {
    val palette = Street.colors
    val ring = (size.value / 16f).toInt().coerceAtLeast(3).dp
    val style = remember(palette) {
        BadgeStyle(palette.infoSurface, palette.primary, blendColor(palette.primary, palette.infoSurface, 0.5f),
            blendColor(palette.textSecondary, palette.infoSurface, 0.4f))
    }
    val outline = effort?.let(::effortColor) ?: palette.divider
    Box(
        modifier.size(size).drawBehind {
            // As Android's GradientDrawable: fill and stroke on the bounds inset by half the stroke.
            val stroke = ring.toPx()
            val radius = stroke + 0.22f * (size - ring * 2).toPx()
            val inset = Offset(stroke / 2f, stroke / 2f)
            val inner = Size(this.size.width - stroke, this.size.height - stroke)
            drawRoundRect(palette.background, inset, inner, CornerRadius(radius))
            drawRoundRect(outline, inset, inner, CornerRadius(radius), style = Stroke(stroke))
        },
    ) {
        ExerciseFigure(
            exercise?.movementId, exercise?.name?.resolve().orEmpty(), Modifier.fillMaxSize().padding(ring),
            category = exercise?.category, equipment = exercise?.equipment.orEmpty(), badge = style, moving = true,
        )
    }
}
