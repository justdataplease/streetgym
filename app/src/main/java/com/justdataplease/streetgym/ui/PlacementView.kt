package com.justdataplease.streetgym.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.justdataplease.streetgym.R
import com.justdataplease.streetgym.data.model.TrainingLevel
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.progress.Placement
import java.util.Locale

/**
 * Today's first-launch card: the placement test ([Placement]), taken like an exam. The athlete does one set each of
 * pull-ups, push-ups and squats and enters the counts; the card shows the start they give and one confirm. [answers]
 * holds each count (-1 for none) and outlives a redraw of Today. [onStart] gets where to start; skipping and "Start
 * from scratch" are Level 1, day 1.
 */
fun Context.placementCard(
    programs: List<TrainingProgram>,
    levels: List<TrainingLevel>,
    locale: Locale,
    answers: IntArray,
    onStart: (Placement.Start) -> Unit,
): LinearLayout = card().apply {
    val scratch = Placement.Start(1)
    val starts = Placement.starts(programs, TrainingProgram::levelNumber, TrainingProgram::number, TrainingProgram::isExam)
    val result = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    fun renderResult(scrollToResult: Boolean) {
        result.removeAllViews()
        if (answers.any { it < 0 }) {
            result.addWithMargins(bodyText(getString(R.string.placement_answer_all), 13f), topDp = 18)
            result.addWithMargins(secondaryButton(getString(R.string.placement_skip)) { onStart(scratch) }, topDp = 10)
            return
        }
        val (pullUps, pushUps, squats) = answers.toList()
        val start = Placement.start(pullUps, pushUps, squats, starts)
        val level = levels.firstOrNull { it.number == start.level }
        val name = level?.let { displayName(it, locale) } ?: getString(R.string.level_number, start.level)
        val where = where(start)
        val text = when {
            start == scratch -> getString(R.string.placement_result_first, start.level, name)
            start.day > 1 -> getString(R.string.placement_result_day, where)
            else -> getString(R.string.placement_result, start.level, name)
        }
        result.addWithMargins(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedDrawable(AppColors.INFO_SURFACE, dp(16).toFloat())
            addView(bodyText(text).apply { setTextColor(AppColors.TEXT) })
            level?.programs?.firstOrNull { it.number == start.day }?.let { opensLine(where, it) }
                ?.let { addWithMargins(bodyText(it, 13f), topDp = 6) }
            if (start != scratch) addWithMargins(bodyText(getString(R.string.placement_no_credit), 13f), topDp = 6)
            // What a few more reps would change, so a count entered too low shows.
            Placement.better(pullUps, pushUps, squats, starts)?.let { better ->
                val more = resources.getQuantityString(better.test.more(), better.more, better.more)
                addWithMargins(bodyText(getString(R.string.placement_better, more, where(better.start)), 13f), topDp = 6)
            }
        }, topDp = 18)
        val confirm = primaryButton(getString(R.string.placement_confirm, where)) { onStart(start) }
        result.addWithMargins(confirm, topDp = 14)
        if (start != scratch) result.addWithMargins(secondaryButton(getString(R.string.placement_scratch)) { onStart(scratch) }, topDp = 8)
        // The last count brings the result in below the fold of a phone: show it.
        if (scrollToResult) confirm.post { confirm.requestRectangleOnScreen(Rect(0, 0, confirm.width, confirm.height + dp(64))) }
    }
    addView(titleText(getString(R.string.placement_title), 20f))
    addWithMargins(bodyText(getString(R.string.placement_prompt), 14f), topDp = 4)
    Placement.Test.entries.forEachIndexed { index, test ->
        val label = getString(test.title())
        addWithMargins(titleText(label, 14f), topDp = 16)
        addWithMargins(bodyText(getString(test.how()), 12f), topDp = 2)
        addWithMargins(countField(label, answers[index]) { count ->
            val completes = answers[index] < 0 && count >= 0 && answers.count { it < 0 } == 1
            answers[index] = count
            renderResult(scrollToResult = completes)
        }, width = ViewGroup.LayoutParams.WRAP_CONTENT, topDp = 6)
    }
    addView(result)
    renderResult(scrollToResult = false)
}

private fun Placement.Test.title(): Int = when (this) {
    Placement.Test.PULL_UPS -> R.string.placement_pull_ups
    Placement.Test.PUSH_UPS -> R.string.placement_push_ups
    Placement.Test.SQUATS -> R.string.placement_squats
}

private fun Placement.Test.how(): Int = when (this) {
    Placement.Test.PULL_UPS -> R.string.placement_pull_ups_how
    Placement.Test.PUSH_UPS -> R.string.placement_push_ups_how
    Placement.Test.SQUATS -> R.string.placement_squats_how
}

/** "3 more pull-ups". */
private fun Placement.Test.more(): Int = when (this) {
    Placement.Test.PULL_UPS -> R.plurals.placement_more_pull_ups
    Placement.Test.PUSH_UPS -> R.plurals.placement_more_push_ups
    Placement.Test.SQUATS -> R.plurals.placement_more_squats
}

/** A start on day 1 names the level; a start inside a level names the day. */
private fun Context.where(start: Placement.Start): String =
    if (start.day > 1) getString(R.string.placement_level_day, start.level, start.day) else getString(R.string.level_number, start.level)

/** "Level 2 opens with strict pull-up 3×4, …": the first movements of the class a start opens with. */
private fun Context.opensLine(where: String, program: TrainingProgram): String? {
    val movements = Placement.opening(program)
    if (movements.isEmpty()) return null
    val list = if (movements.size == 1) movements[0]
        else getString(R.string.placement_list_end, movements.dropLast(1).joinToString(", "), movements.last())
    return getString(R.string.placement_opens, where, list)
}

/** A count: − and + around a number the athlete can also type. [value] -1 is empty; [onChange] gets -1 when cleared. */
private fun Context.countField(label: String, value: Int, onChange: (Int) -> Unit): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    setPadding(dp(3), dp(3), dp(3), dp(3))
    background = roundedDrawable(AppColors.INFO_SURFACE, dp(14).toFloat())
    val field = EditText(context).apply {
        inputType = InputType.TYPE_CLASS_NUMBER
        filters = arrayOf(InputFilter.LengthFilter(3))
        setText(if (value >= 0) value.toString() else "")
        hint = getString(R.string.placement_count)
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(AppColors.TEXT)
        setHintTextColor(AppColors.TEXT_SECONDARY)
        background = roundedDrawable(AppColors.SURFACE, dp(11).toFloat())
        setPadding(dp(8), dp(8), dp(8), dp(8))
        contentDescription = label
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) { onChange(s?.toString()?.toIntOrNull() ?: -1) }
        })
    }
    fun step(delta: Int, text: String, description: String) = TextView(context).apply {
        this.text = text
        textSize = 22f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(AppColors.TEXT)
        background = roundedDrawable(Color.TRANSPARENT, dp(11).toFloat())
        isClickable = true
        isFocusable = true
        contentDescription = "$description, $label"
        setOnClickListener {
            val next = ((field.text.toString().toIntOrNull() ?: 0) + delta).coerceIn(0, 999)
            field.setText(next.toString())
            field.setSelection(field.text.length)
        }
    }
    addView(step(-1, "−", getString(R.string.placement_less)), LinearLayout.LayoutParams(dp(48), dp(48)))
    addView(field, LinearLayout.LayoutParams(dp(96), ViewGroup.LayoutParams.WRAP_CONTENT))
    addView(step(1, "+", getString(R.string.placement_more)), LinearLayout.LayoutParams(dp(48), dp(48)))
}
