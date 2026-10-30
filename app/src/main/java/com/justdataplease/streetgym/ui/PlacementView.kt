package com.justdataplease.streetgym.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.justdataplease.streetgym.R
import com.justdataplease.streetgym.billing.LevelAccess
import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.model.TrainingLevel
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.progress.Placement
import java.util.Locale

/**
 * Today's first-launch screen (design B, the owner 2026-10-30): the placement test ([Placement]), taken like an exam.
 * Under a dark header asking how strong the athlete is today, three big counters take one set each of pull-ups, push-ups
 * and squats, and a strip of tape shows the start they give, with one confirm. The skills step, folded under one line below
 * the confirm, lists the level tests whose pull-ups and push-ups the counts pass: the hardest one the athlete would pass
 * suggests the next level, held to the last level [tier] opens. The start is only a guide and says what it assumes. [answers] holds each count (-1 for none) and
 * [skill] the chosen test (0 for none); both outlive a redraw of Today. [onStart] gets where to start; skipping and "Start
 * from scratch" are Level 1, day 1. The screen closes with the progress-sharing note ([settings]).
 */
fun Context.placementCard(
    programs: List<TrainingProgram>,
    levels: List<TrainingLevel>,
    locale: Locale,
    answers: IntArray,
    skill: IntArray,
    tier: Tier,
    settings: AppSettings,
    onStart: (Placement.Start) -> Unit,
): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    val scratch = Placement.Start(1)
    val starts = Placement.starts(programs, TrainingProgram::levelNumber, TrainingProgram::number, TrainingProgram::isExam)
    val result = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    var skillsOpen = skill[0] > 0
    fun renderResult(scrollToResult: Boolean) {
        result.removeAllViews()
        if (answers.any { it < 0 }) {
            result.addWithMargins(startTape(null, getString(R.string.placement_answer_all)), topDp = 22)
            result.addWithMargins(linkButton(getString(R.string.placement_skip)) { onStart(scratch) }, topDp = 10)
            return
        }
        val (pullUps, pushUps, squats) = answers.toList()
        val counted = Placement.start(pullUps, pushUps, squats, starts)
        // The level tests (each level's last class, Level 3's to Level 9's) whose pull-ups and push-ups the counts pass.
        val tests = Placement.SKILL_TESTS.mapNotNull { number ->
            programs.filter { it.levelNumber == number }.maxByOrNull { it.number }?.takeIf { it.isExam && Placement.meets(it, pullUps, pushUps) }
        }
        if (tests.none { it.levelNumber == skill[0] }) skill[0] = 0
        val wanted = if (skill[0] > 0) Placement.skillStart(skill[0]) else counted
        val start = Placement.capped(wanted, tier)
        fun levelName(number: Int) = levels.firstOrNull { it.number == number }?.let { displayName(it, locale) }
            ?: getString(R.string.level_number, number)
        val level = levels.firstOrNull { it.number == start.level }
        val where = where(start)
        result.addWithMargins(startTape(getString(R.string.placement_start_line, where, levelName(start.level))), topDp = 22)
        // What the start rests on, in one card of labelled parts: the skills step's suggestion, the class it opens with,
        // what it takes for granted (the exam just before it, one target a line), what a few more reps would change, and
        // that it's only a guide.
        val info = card().apply { setPadding(dp(14), dp(12), dp(14), dp(14)) }
        fun part(label: Int, value: String) {
            info.addWithMargins(TextView(context).apply {
                text = getString(label).uppercase()
                textSize = 11f
                typeface = AppType.label
                letterSpacing = 0.12f
                setTextColor(AppColors.TEXT_SECONDARY)
            }, topDp = if (info.childCount == 0) 0 else 12)
            info.addWithMargins(bodyText(value, 14f).apply { setTextColor(AppColors.TEXT) }, topDp = 2)
        }
        if (skill[0] > 0) part(R.string.placement_skills_label,
            getString(R.string.placement_skill_result, skill[0], wanted.level, levelName(wanted.level)) +
                if (start == wanted) "" else " " + getString(R.string.placement_skill_capped, wanted.level,
                    getString(tierName(LevelAccess.tierFor(wanted.level))), start.level))
        level?.programs?.firstOrNull { it.number == start.day }?.let(Placement::opening)?.takeIf { it.isNotEmpty() }?.let { movements ->
            part(R.string.placement_opens_label, movements.joinToString(" · ") { it.replaceFirstChar(Char::uppercase) })
        }
        Placement.assumed(programs, start, TrainingProgram::levelNumber, TrainingProgram::number, TrainingProgram::isExam)?.let { exam ->
            part(R.string.placement_assumes_label, exam.exercises.filter { it.assessment }.joinToString("\n") { examTarget(it, locale) })
        }
        // What a few more reps would change, so a count entered too low shows.
        if (skill[0] == 0) Placement.better(pullUps, pushUps, squats, starts)?.let { better ->
            val more = resources.getQuantityString(better.test.more(), better.more, better.more)
            part(R.string.placement_better_label, getString(R.string.placement_better, more, where(better.start)))
        }
        if (start != scratch) info.addWithMargins(bodyText(getString(R.string.placement_no_credit), 12f),
            topDp = if (info.childCount == 0) 0 else 12)
        if (info.childCount > 0) result.addWithMargins(info, topDp = 14)
        val confirm = primaryButton(getString(R.string.placement_confirm, where)) { onStart(start) }
        result.addWithMargins(confirm, topDp = 20)
        if (start != scratch) result.addWithMargins(linkButton(getString(R.string.placement_scratch)) { onStart(scratch) }, topDp = 6)
        if (tests.isNotEmpty()) {
            // The skills step, folded under one line so the start stays by the counters: the level tests, each with its
            // targets; one tap picks a test, a second clears it.
            result.addWithMargins(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(52)
                isClickable = true
                isFocusable = true
                foreground = selectableForeground()
                addView(titleText(getString(R.string.placement_skills_title), 16f),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(bodyText(if (skillsOpen) "−" else "+", 24f).apply {
                    setTextColor(AppColors.TEXT)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                })
                setOnClickListener {
                    skillsOpen = !skillsOpen
                    renderResult(scrollToResult = false)
                }
            }, topDp = 10)
        }
        if (tests.isNotEmpty() && skillsOpen) {
            result.addWithMargins(bodyText(getString(R.string.placement_skills_prompt), 13f), topDp = 2)
            tests.forEach { exam ->
                val chosen = skill[0] == exam.levelNumber
                result.addWithMargins(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(14), dp(10), dp(14), dp(10))
                    background = roundedDrawable(if (chosen) AppColors.INFO_SURFACE else AppColors.SURFACE, dp(14).toFloat(),
                        if (chosen) AppColors.PRIMARY else AppColors.BORDER, dp(if (chosen) 2 else 1))
                    isClickable = true
                    isFocusable = true
                    isSelected = chosen
                    contentDescription = getString(R.string.placement_skill_test, exam.levelNumber)
                    addView(titleText(getString(R.string.placement_skill_test, exam.levelNumber), 14f))
                    addWithMargins(bodyText(targets(exam, locale), 12f), topDp = 2)
                    setOnClickListener {
                        skill[0] = if (chosen) 0 else exam.levelNumber
                        renderResult(scrollToResult = false)
                    }
                }, topDp = 8)
            }
        }
        // The last count brings the result in below the fold of a phone: show it.
        if (scrollToResult) confirm.post { confirm.requestRectangleOnScreen(Rect(0, 0, confirm.width, confirm.height + dp(64))) }
    }
    addView(card().apply {
        background = roundedDrawable(AppColors.PRIMARY_DARK, dp(24).toFloat())
        setPadding(dp(20), dp(22), dp(20), dp(20))
        addView(bodyText(getString(R.string.placement_kicker), 12f).apply { setTextColor(AppColors.HERO_KICKER) })
        addWithMargins(titleText(getString(R.string.placement_title), 30f).apply { setTextColor(AppColors.HERO_TEXT) }, topDp = 6)
        addWithMargins(bodyText(getString(R.string.placement_prompt), 14f).apply {
            setTextColor(blendColor(AppColors.HERO_TEXT, AppColors.PRIMARY_DARK, 0.22f))
        }, topDp = 8)
    })
    addWithMargins(LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        Placement.Test.entries.forEachIndexed { index, test ->
            addView(countTile(getString(test.short()), getString(test.title()), answers[index]) { count ->
                val completes = answers[index] < 0 && count >= 0 && answers.count { it < 0 } == 1
                answers[index] = count
                renderResult(scrollToResult = completes)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { if (index > 0) marginStart = dp(10) })
        }
    }, topDp = 18)
    addWithMargins(bodyText(getString(R.string.placement_how), 12.5f), leftDp = 2, topDp = 10)
    addView(result)
    renderResult(scrollToResult = false)
    // Last, below the start: what the app keeps to improve the program, with its switch.
    addWithMargins(shareProgressNote(settings), topDp = 22)
}

private fun Placement.Test.title(): Int = when (this) {
    Placement.Test.PULL_UPS -> R.string.placement_pull_ups
    Placement.Test.PUSH_UPS -> R.string.placement_push_ups
    Placement.Test.SQUATS -> R.string.placement_squats
}

/** The counter's label: "Pull-ups". */
private fun Placement.Test.short(): Int = when (this) {
    Placement.Test.PULL_UPS -> R.string.placement_pull_ups_short
    Placement.Test.PUSH_UPS -> R.string.placement_push_ups_short
    Placement.Test.SQUATS -> R.string.placement_squats_short
}

/** "3 more pull-ups". */
private fun Placement.Test.more(): Int = when (this) {
    Placement.Test.PULL_UPS -> R.plurals.placement_more_pull_ups
    Placement.Test.PUSH_UPS -> R.plurals.placement_more_push_ups
    Placement.Test.SQUATS -> R.plurals.placement_more_squats
}

/** An exam's targets in one line: "Strict Pull-Up · 15 reps, Parallel Bar Dip · 20 reps, …". */
private fun Context.targets(exam: TrainingProgram, locale: Locale): String =
    exam.exercises.filter { it.assessment }.joinToString(", ") { examTarget(it, locale) }

private fun tierName(tier: Tier): Int = when (tier) {
    Tier.FREE -> R.string.tier_free
    Tier.PLUS -> R.string.tier_plus
    Tier.PRO -> R.string.tier_pro
}

/** A start on day 1 names the level; a start inside a level names the day. */
private fun Context.where(start: Placement.Start): String =
    if (start.day > 1) getString(R.string.placement_level_day, start.level, start.day) else getString(R.string.level_number, start.level)

/**
 * One test's counter: its [label] over a big number the athlete can also type, and − and + under a rule. [description]
 * names the field for TalkBack ("Strict pull-ups"). [value] -1 is empty; [onChange] gets -1 when cleared.
 */
private fun Context.countTile(label: String, description: String, value: Int, onChange: (Int) -> Unit): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(4), dp(12), dp(4), 0)
        val border = dp(AppColors.theme.shape.borderDp.coerceAtLeast(1))
        background = roundedDrawable(AppColors.SURFACE, dp(18).toFloat(), AppColors.BORDER, border)
        addView(TextView(context).apply {
            text = label.uppercase()
            textSize = 11f
            typeface = AppType.label
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            setTextColor(AppColors.TEXT)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })
        val field = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(3))
            setText(if (value >= 0) value.toString() else "")
            hint = "–"
            textSize = 40f
            typeface = AppType.numeral
            gravity = Gravity.CENTER
            setTextColor(AppColors.TEXT)
            setHintTextColor(AppColors.DIVIDER)
            background = null
            setPadding(0, dp(2), 0, dp(6))
            contentDescription = description
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) { onChange(s?.toString()?.toIntOrNull() ?: -1) }
            })
        }
        addView(field, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(View(context).apply { setBackgroundColor(AppColors.BORDER) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, border))
        fun step(delta: Int, text: String, name: String) = TextView(context).apply {
            this.text = text
            textSize = 22f
            typeface = AppType.bold
            gravity = Gravity.CENTER
            setTextColor(AppColors.TEXT)
            background = roundedDrawable(Color.TRANSPARENT, dp(11).toFloat())
            foreground = selectableForeground()
            isClickable = true
            isFocusable = true
            contentDescription = "$name, $description"
            setOnClickListener {
                val next = ((field.text.toString().toIntOrNull() ?: 0) + delta).coerceIn(0, 999)
                field.setText(next.toString())
                field.setSelection(field.text.length)
            }
        }
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(step(-1, "−", getString(R.string.placement_less)), LinearLayout.LayoutParams(0, dp(48), 1f))
            addView(step(1, "+", getString(R.string.placement_more)), LinearLayout.LayoutParams(0, dp(48), 1f))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

/**
 * The start on a strip of the theme's tape, a little askew in the poster themes: "Your start" over [line] ("Level 2 · Bar
 * Strength"), or over [note] while counts are missing.
 */
private fun Context.startTape(line: String?, note: String? = null): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(14), dp(10), dp(14), dp(12))
    val ink = AppColors.contentOn(AppColors.ACCENT)
    background = roundedDrawable(AppColors.ACCENT, dp(14).toFloat(), AppColors.BORDER, dp(AppColors.theme.shape.borderDp))
    if (AppColors.theme != StreetTheme.NIGHT_PARK) rotation = -1.2f
    addView(TextView(context).apply {
        text = getString(R.string.placement_your_start).uppercase()
        textSize = 11f
        typeface = AppType.label
        letterSpacing = 0.14f
        setTextColor(ink)
    })
    line?.let { addWithMargins(titleText(it, 22f).apply { setTextColor(ink) }, topDp = 2) }
    note?.let { addWithMargins(bodyText(it, 14f).apply { setTextColor(ink) }, topDp = 4) }
}

/** A quiet way out, as underlined text: skipping the test, or starting from scratch. */
private fun Context.linkButton(text: String, onClick: () -> Unit): TextView = TextView(this).apply {
    this.text = text
    textSize = 14f
    typeface = AppType.bold
    gravity = Gravity.CENTER
    paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
    setTextColor(AppColors.TEXT)
    minHeight = dp(48)
    foreground = selectableForeground()
    isClickable = true
    isFocusable = true
    setOnClickListener { onClick() }
}
