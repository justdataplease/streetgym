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
 * A workout's icon (the owner, 2.9.0: "the icon of the most difficult exercise"): its hardest exercise's figure on a
 * tile [size] dp wide, ringed in the workout's effort colour for this athlete, as a skill medal is in its difficulty's.
 */
fun Context.workoutTile(exercise: TrainingExercise?, effort: Effort?, size: Int, locale: Locale): FrameLayout = FrameLayout(this).apply {
    val ring = (size / 16).coerceAtLeast(3)
    background = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(ring) + 0.22f * dp(size - 2 * ring)
        setColor(AppColors.BACKGROUND)
        setStroke(dp(ring), effort?.let(::effortColor) ?: AppColors.DIVIDER)
    }
    addView(ExerciseAnimationView(context).apply {
        bind(exercise?.movementId, exercise?.name?.resolve(locale).orEmpty(), exercise?.category, exercise?.equipment.orEmpty())
        setBadgeStyle(ExerciseAnimationView.BadgeStyle(
            AppColors.INFO_SURFACE, AppColors.PRIMARY, blendColor(AppColors.PRIMARY, AppColors.INFO_SURFACE, 0.5f),
            blendColor(AppColors.TEXT_SECONDARY, AppColors.INFO_SURFACE, 0.4f)))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).apply {
        setMargins(dp(ring), dp(ring), dp(ring), dp(ring))
    })
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
}
