package com.justdataplease.streetgym.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import com.justdataplease.streetgym.R
import com.justdataplease.streetgym.data.effort.Effort
import com.justdataplease.streetgym.data.model.TrainingExercise
import java.util.Locale

/** Easy green, Moderate orange, Hard red: the skills' difficulty colours (Easy, Very hard and Extreme). */
fun effortColor(effort: Effort): Int = difficultyColor(when (effort) {
    Effort.EASY -> 1
    Effort.MODERATE -> 4
    Effort.HARD -> 5
})

/** "Easy for you", "Moderate for you" or "Hard for you". */
fun Context.effortText(effort: Effort): String = getString(R.string.effort_for_you, getString(when (effort) {
    Effort.EASY -> R.string.effort_easy
    Effort.MODERATE -> R.string.effort_moderate
    Effort.HARD -> R.string.effort_hard
}))

/**
 * A workout's icon (the owner, 2.9.0: "the icon of the most difficult exercise"): its hardest exercise playing on a
 * tile [size] dp wide, ringed in the workout's effort colour for this athlete, as a skill medal is in its difficulty's.
 * It replaced the class number in the level lists too (2.9.1, the owner: "add the most difficult exercise as an
 * animation"). A list row keeps one tile and calls [bind] again as it scrolls.
 */
class WorkoutTile(context: Context, private val size: Int) : FrameLayout(context) {
    private val ring = (size / 16).coerceAtLeast(3)
    private val figure = ExerciseAnimationView(context).apply {
        setBadgeStyle(ExerciseAnimationView.BadgeStyle(
            AppColors.INFO_SURFACE, AppColors.PRIMARY, blendColor(AppColors.PRIMARY, AppColors.INFO_SURFACE, 0.5f),
            blendColor(AppColors.TEXT_SECONDARY, AppColors.INFO_SURFACE, 0.4f)), moving = true)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    init {
        addView(figure, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            val margin = context.dp(ring)
            setMargins(margin, margin, margin, margin)
        })
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun bind(exercise: TrainingExercise?, effort: Effort?, locale: Locale): WorkoutTile {
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = context.dp(ring) + 0.22f * context.dp(size - 2 * ring)
            setColor(AppColors.BACKGROUND)
            setStroke(context.dp(ring), effort?.let(::effortColor) ?: AppColors.DIVIDER)
        }
        figure.bind(exercise?.movementId, exercise?.name?.resolve(locale).orEmpty(), exercise?.category, exercise?.equipment.orEmpty())
        return this
    }
}

fun Context.workoutTile(exercise: TrainingExercise?, effort: Effort?, size: Int, locale: Locale): WorkoutTile =
    WorkoutTile(this, size).bind(exercise, effort, locale)
