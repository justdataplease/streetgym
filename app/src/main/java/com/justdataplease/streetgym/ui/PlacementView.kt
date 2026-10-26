package com.justdataplease.streetgym.ui

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.justdataplease.streetgym.R
import com.justdataplease.streetgym.data.model.TrainingLevel
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.progress.Placement
import java.util.Locale

/**
 * Today's first-launch card: the three placement questions, the start they give and one confirm ([Placement]).
 * [answers] holds the band picked for each question (-1 for none) and outlives a redraw of Today. [onStart] gets
 * where to start; skipping and "Start from scratch" are Level 1, day 1.
 */
fun Context.placementCard(
    levels: List<TrainingLevel>,
    locale: Locale,
    answers: IntArray,
    onStart: (Placement.Start) -> Unit,
): LinearLayout = card().apply {
    val scratch = Placement.Start(1)
    fun render() {
        removeAllViews()
        addView(titleText(getString(R.string.placement_title), 20f))
        addWithMargins(bodyText(getString(R.string.placement_prompt), 14f), topDp = 4)
        val labels = listOf(R.string.placement_push_ups, R.string.placement_pull_ups, R.string.placement_squats)
        Placement.Question.entries.forEachIndexed { index, question ->
            val label = getString(labels[index])
            addWithMargins(titleText(label, 14f), topDp = 14)
            addWithMargins(choiceRow(question, label, answers[index]) { answers[index] = it; render() }, topDp = 6)
        }
        val reps = Placement.Question.entries.mapIndexedNotNull { index, question -> question.bands.getOrNull(answers[index]) }
        if (reps.size < Placement.Question.entries.size) {
            addWithMargins(secondaryButton(getString(R.string.placement_skip)) { onStart(scratch) }, topDp = 18)
            return
        }
        val start = Placement.start(pushUps = reps[0], pullUps = reps[1], squats = reps[2])
        val level = levels.firstOrNull { it.number == start.level }
        val name = level?.let { displayName(it, locale) } ?: getString(R.string.level_number, start.level)
        // A start on day 1 names the level; a start inside a level names the day.
        val where = if (start.day > 1) getString(R.string.placement_level_day, start.level, start.day)
            else getString(R.string.level_number, start.level)
        val result = when {
            start == scratch -> getString(R.string.placement_result_first, start.level, name)
            start.day > 1 -> getString(R.string.placement_result_day, where)
            else -> getString(R.string.placement_result, start.level, name)
        }
        addWithMargins(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedDrawable(AppColors.INFO_SURFACE, dp(16).toFloat())
            addView(bodyText(result).apply { setTextColor(AppColors.TEXT) })
            level?.programs?.firstOrNull { it.number == start.day }?.let { opensLine(where, it) }
                ?.let { addWithMargins(bodyText(it, 13f), topDp = 6) }
        }, topDp = 18)
        addWithMargins(primaryButton(getString(R.string.placement_confirm, where)) { onStart(start) }, topDp = 14)
        if (start != scratch) addWithMargins(secondaryButton(getString(R.string.placement_scratch)) { onStart(scratch) }, topDp = 8)
    }
    render()
}

/** "Level 2 opens with strict pull-up 3×4, …": the first movements of the class a start opens with. */
private fun Context.opensLine(where: String, program: TrainingProgram): String? {
    val movements = Placement.opening(program)
    if (movements.isEmpty()) return null
    val list = if (movements.size == 1) movements[0]
        else getString(R.string.placement_list_end, movements.dropLast(1).joinToString(", "), movements.last())
    return getString(R.string.placement_opens, where, list)
}

/** One question's bands as equal segments, in the look of [segmentedSwitch], so four fit a small phone. */
private fun Context.choiceRow(
    question: Placement.Question,
    label: String,
    selected: Int,
    onSelect: (Int) -> Unit,
): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    setPadding(dp(3), dp(3), dp(3), dp(3))
    background = roundedDrawable(AppColors.INFO_SURFACE, dp(14).toFloat())
    question.bands.indices.forEach { band ->
        val picked = band == selected
        addView(TextView(context).apply {
            text = question.label(band)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(dp(4), dp(9), dp(4), dp(9))
            setTextColor(if (picked) AppColors.PRIMARY else AppColors.TEXT_SECONDARY)
            if (picked) background = roundedDrawable(AppColors.SURFACE, dp(11).toFloat())
            isSelected = picked
            isClickable = true
            isFocusable = true
            contentDescription = "$label: $text"
            setOnClickListener { if (!picked) onSelect(band) }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }
}
