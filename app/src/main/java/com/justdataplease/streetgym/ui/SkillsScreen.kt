package com.justdataplease.streetgym.ui

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.justdataplease.streetgym.R
import com.justdataplease.streetgym.billing.Entitlement
import com.justdataplease.streetgym.billing.LevelAccess
import com.justdataplease.streetgym.data.skills.SkillFamily
import com.justdataplease.streetgym.data.skills.SkillGroup
import com.justdataplease.streetgym.data.skills.SkillProgress
import com.justdataplease.streetgym.data.skills.SkillStatus

/** A row of options with one selected: the tabs of Training and Progress, and other small choices. */
fun Context.segmentedSwitch(labels: List<String>, selected: Int, onSelect: (Int) -> Unit): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(3), dp(3), dp(3), dp(3))
        background = roundedDrawable(AppColors.INFO_SURFACE, dp(14).toFloat())
        labels.forEachIndexed { index, label ->
            addView(TextView(context).apply {
                text = label
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(dp(18), dp(8), dp(18), dp(8))
                setTextColor(if (index == selected) AppColors.PRIMARY else AppColors.TEXT_SECONDARY)
                if (index == selected) background = roundedDrawable(AppColors.SURFACE, dp(11).toFloat())
                isClickable = true
                isFocusable = true
                contentDescription = label
                setOnClickListener { if (index != selected) onSelect(index) }
            })
        }
    }

private val GROUPS = listOf(
    SkillGroup.PULL to R.string.skills_filter_pull,
    SkillGroup.PUSH to R.string.skills_filter_push,
    SkillGroup.LEGS to R.string.skills_filter_legs,
    SkillGroup.CORE to R.string.skills_filter_core,
)

/** Skills tab: unlocked count and the four group scores, the skills closest to unlocking, a group filter and one ladder per family. */
fun Context.skillsContent(
    progress: SkillProgress,
    group: SkillGroup?,
    onGroup: (SkillGroup?) -> Unit,
    onOpen: (SkillStatus, SkillFamily, List<SkillStatus>) -> Unit,
): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    val closest = progress.closest(3)
    val owned = Entitlement.allLevels(context)

    addView(LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = roundedDrawable(AppColors.PRIMARY_DARK, dp(24).toFloat())
        addView(bodyText(getString(R.string.skills_unlocked_kicker).uppercase(), 12f).apply {
            setTextColor(AppColors.ACCENT)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.1f
        })
        addWithMargins(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            addView(titleText(progress.unlockedCount.toString(), 38f).apply { setTextColor(Color.WHITE) })
            addWithMargins(bodyText(getString(R.string.skills_unlocked_of, progress.totalCount), 15f).apply {
                setTextColor(blendColor(Color.WHITE, AppColors.PRIMARY_DARK, 0.3f))
            }, width = LinearLayout.LayoutParams.WRAP_CONTENT, leftDp = 8, bottomDp = 6)
        }, topDp = 4)
        addWithMargins(horizontalProgress(percent(progress.unlockedCount, progress.totalCount)).apply {
            progressTintList = ColorStateList.valueOf(AppColors.ACCENT)
            progressBackgroundTintList = ColorStateList.valueOf(blendColor(AppColors.ACCENT, AppColors.PRIMARY_DARK, 0.75f))
        }, height = dp(8), topDp = 10)
        addWithMargins(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            GROUPS.forEachIndexed { index, (option, label) ->
                addView(groupScoreTile(getString(label), progress.groupScore(option), option == group) {
                    onGroup(if (option == group) null else option)
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (index > 0) marginStart = dp(6)
                })
            }
        }, topDp = 14)
        closest.firstOrNull()?.let { next ->
            addWithMargins(bodyText(getString(R.string.skills_next, next.skill.name), 13f).apply {
                setTextColor(blendColor(Color.WHITE, AppColors.PRIMARY_DARK, 0.2f))
            }, topDp = 10)
        }
    })

    addWithMargins(sectionLabel(getString(R.string.skills_closest)), topDp = 14)
    if (closest.isEmpty()) {
        addView(bodyText(getString(R.string.skills_closest_empty), 14f))
    } else {
        closest.forEach { status ->
            val (family, statuses) = progress.families.first { (family, _) -> family.skills.contains(status.skill) }
            addWithMargins(closestRow(status) { onOpen(status, family, statuses) }, bottomDp = 8)
        }
    }

    addWithMargins(HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val options = listOf<Pair<SkillGroup?, Int>>(null to R.string.skills_filter_all) + GROUPS
            options.forEach { (option, label) ->
                addWithMargins(filterChip(getString(label), option == group) { onGroup(option) },
                    width = LinearLayout.LayoutParams.WRAP_CONTENT, rightDp = 8)
            }
        })
    }, topDp = 12, bottomDp = 4)

    progress.families.filter { (family, _) -> group == null || family.spec.group == group }.forEach { (family, statuses) ->
        addWithMargins(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(titleText(family.spec.name, 16f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(bodyText(getString(R.string.skills_family_count, statuses.count { it.unlocked }, statuses.size), 12f))
        }, topDp = 14, bottomDp = 8)
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                statuses.forEach { status ->
                    addWithMargins(skillCell(status, lockedFor(status, owned)) { onOpen(status, family, statuses) },
                        width = dp(82), rightDp = 10)
                }
            })
        })
    }
    addWithMargins(bodyText(getString(R.string.skills_how_points), 12f), topDp = 18)
}

/** Detail sheet: the animated movement guide, skill points out of 100, difficulty, schedule and where the points came from. */
fun Context.showSkillSheet(status: SkillStatus, family: SkillFamily, statuses: List<SkillStatus>) {
    val skill = status.skill
    val locked = lockedFor(status, Entitlement.allLevels(this))
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(16), dp(20), dp(8))
        addView(ExerciseAnimationView(context).bind(skill.id, skill.name), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(168)))
        addWithMargins(titleText(skill.name, 20f), topDp = 10)
        addWithMargins(bodyText(getString(R.string.skills_ladder_rung, family.spec.name, skill.rung + 1, statuses.size), 13f), topDp = 2)
        val pill = when {
            status.unlocked -> badge(getString(R.string.skills_unlocked), AppColors.SUCCESS)
            locked -> lockBadge(getString(R.string.skills_plus))
            status.points > 0 -> badge(getString(R.string.skills_in_progress, status.score), AppColors.PRIMARY)
            else -> badge(getString(R.string.skills_locked), AppColors.TEXT_SECONDARY)
        }
        addWithMargins(pill, width = LinearLayout.LayoutParams.WRAP_CONTENT, topDp = 10)
        addWithMargins(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(oneLineText(getString(R.string.skills_points_line, status.score), bold = true),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (!status.unlocked) addView(bodyText(getString(R.string.skills_points_to_go, 100 - status.score), 13f))
        }, topDp = 12)
        addWithMargins(horizontalProgress(status.score), height = dp(8), topDp = 6)
        addWithMargins(detailRow(getString(R.string.skills_difficulty),
            getString(R.string.skills_difficulty_value, difficultyName(skill.difficulty), skill.difficulty.coerceIn(1, 5))).apply {
            (getChildAt(1) as TextView).apply {
                setCompoundDrawablesRelative(DifficultyDots(skill.difficulty, dp(50), dp(8)), null, null, null)
                compoundDrawablePadding = dp(8)
            }
        }, topDp = 12)
        val schedule = when {
            skill.expected == null -> getString(R.string.skills_beyond_program)
            skill.beyondProgram -> getString(R.string.skills_readiness_test, skill.expected.level)
            skill.expected.week != null -> getString(R.string.skills_schedule_week, skill.expected.level, skill.expected.week)
            else -> getString(R.string.skills_schedule_level, skill.expected.level)
        }
        addView(detailRow(getString(R.string.skills_on_schedule), schedule))
        statuses.getOrNull(skill.rung - 1)?.let { addView(detailRow(getString(R.string.skills_builds_on), it.skill.name)) }
        statuses.getOrNull(skill.rung + 1)?.let { addView(detailRow(getString(R.string.skills_unlocks_next), it.skill.name)) }
        addWithMargins(sectionLabel(getString(R.string.skills_sources)), topDp = 10)
        if (status.sources.isEmpty()) {
            addView(detailRow(getString(R.string.skills_no_sources), "0"))
        } else {
            status.sources.take(4).forEach { (name, share) -> addView(detailRow(name, getString(R.string.skills_source_share, share))) }
        }
    }
    AlertDialog.Builder(this)
        .setView(android.widget.ScrollView(this).apply { addView(content) })
        .setPositiveButton(R.string.skills_close, null)
        .show()
}

private fun Context.closestRow(status: SkillStatus, onClick: () -> Unit): View = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    setPadding(dp(8), dp(8), dp(14), dp(8))
    background = tappable(AppColors.SURFACE, dp(18).toFloat())
    isClickable = true
    isFocusable = true
    contentDescription = getString(R.string.skills_badge_description, status.skill.name,
        getString(R.string.skills_points_short, status.score))
    setOnClickListener { onClick() }
    addView(skillBadge(status), LinearLayout.LayoutParams(dp(44), dp(44)))
    addView(LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(oneLineText(status.skill.name, bold = true).apply { textSize = 14f })
        addWithMargins(horizontalProgress(status.score), height = dp(5), topDp = 6)
    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
        setMargins(dp(12), 0, dp(12), 0)
    })
    addView(titleText(getString(R.string.skills_points_short, status.score), 14f).apply { setTextColor(AppColors.PRIMARY) })
}

private fun Context.skillCell(status: SkillStatus, locked: Boolean, onClick: () -> Unit): View = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    isClickable = true
    isFocusable = true
    background = tappable(Color.TRANSPARENT, dp(14).toFloat())
    val state = when {
        status.unlocked -> getString(R.string.skills_unlocked)
        locked -> getString(R.string.skills_plus)
        else -> getString(R.string.skills_points_short, status.score)
    }
    contentDescription = getString(R.string.skills_badge_description, status.skill.name,
        "$state, ${difficultyName(status.skill.difficulty)}")
    setOnClickListener { onClick() }
    addView(FrameLayout(context).apply {
        addView(skillBadge(status), FrameLayout.LayoutParams(dp(78), dp(78)))
        val marker = TextView(context).apply {
            gravity = Gravity.CENTER
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        when {
            status.unlocked -> marker.apply {
                text = "✓"
                setTextColor(AppColors.PRIMARY_DARK)
                background = roundedDrawable(AppColors.ACCENT, dp(11).toFloat(), AppColors.BACKGROUND, dp(2))
            }
            status.points == 0 -> marker.apply {
                text = "•"
                setTextColor(AppColors.TEXT_SECONDARY)
                background = roundedDrawable(AppColors.DIVIDER, dp(11).toFloat(), AppColors.BACKGROUND, dp(2))
            }
            else -> null
        }?.let { addView(it, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP or Gravity.END)) }
    }, LinearLayout.LayoutParams(dp(82), dp(80)))
    addWithMargins(TextView(context).apply {
        text = status.skill.name
        textSize = 12f
        setTextColor(AppColors.TEXT)
        maxLines = 2
        minLines = 2
        ellipsize = TextUtils.TruncateAt.END
    }, topDp = 4)
    if (!status.unlocked && !locked) {
        addWithMargins(horizontalProgress(status.score), width = dp(78), height = dp(4), topDp = 4)
    }
    if (locked) {
        addWithMargins(lockBadge(), width = LinearLayout.LayoutParams.WRAP_CONTENT, topDp = 4)
    } else {
        addWithMargins(bodyText(state, 11f).apply {
            if (status.unlocked) {
                setTextColor(AppColors.PRIMARY)
                typeface = Typeface.DEFAULT_BOLD
            }
        }, topDp = 2)
    }
    addWithMargins(ImageView(context).apply {
        setImageDrawable(DifficultyDots(status.skill.difficulty, dp(44), dp(7)))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }, topDp = 6)
}

/** One of the four group scores in the summary card; tapping filters the ladders to that group. */
private fun Context.groupScoreTile(label: String, score: Int, selected: Boolean, onClick: () -> Unit): View =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(4), dp(8), dp(4), dp(8))
        background = tappable(blendColor(AppColors.ACCENT, AppColors.PRIMARY_DARK, if (selected) 0.55f else 0.85f), dp(14).toFloat())
        isClickable = true
        isFocusable = true
        contentDescription = getString(R.string.skills_group_score_description, label, score)
        setOnClickListener { onClick() }
        addView(titleText(score.toString(), 20f).apply { setTextColor(Color.WHITE) })
        addView(bodyText(getString(R.string.skills_group_score_of, label), 11f).apply {
            setTextColor(blendColor(Color.WHITE, AppColors.PRIMARY_DARK, 0.25f))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
    }

/** Skills the program first teaches in Levels 4 and up stay locked until Plus. */
private fun lockedFor(status: SkillStatus, owned: Boolean): Boolean =
    !status.unlocked && status.skill.expected?.let { LevelAccess.locked(it.level, owned) } == true

/** Difficulty runs green, blue, amber, orange, red from Beginner to Elite, like trail grades. */
private fun difficultyColor(level: Int): Int {
    val dark = AppColors.darkMode
    return when (level.coerceIn(1, 5)) {
        1 -> AppColors.PRIMARY
        2 -> if (dark) 0xFF7CC8F0.toInt() else 0xFF2B78A0.toInt()
        3 -> if (dark) 0xFFF2C14E.toInt() else 0xFFB7791F.toInt()
        4 -> if (dark) 0xFFFF9466.toInt() else 0xFFC4521C.toInt()
        else -> if (dark) 0xFFFF7A90.toInt() else 0xFFB3263E.toInt()
    }
}

/** Five dots, the first [level] filled in that level's colour; the dots are as tall as the drawable. */
private class DifficultyDots(level: Int, private val width: Int, private val height: Int) : Drawable() {
    private val filled = level.coerceIn(1, 5)
    private val on = difficultyColor(filled)
    private val off = blendColor(AppColors.TEXT_SECONDARY, AppColors.BACKGROUND, 0.65f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init { setBounds(0, 0, width, height) }

    override fun getIntrinsicWidth() = width
    override fun getIntrinsicHeight() = height

    override fun draw(canvas: Canvas) {
        val area = bounds
        val radius = area.height() / 2f
        val step = (area.width() - area.height()) / 4f
        for (i in 0 until 5) {
            paint.color = if (i < filled) on else off
            canvas.drawCircle(area.left + radius + i * step, area.exactCenterY(), radius, paint)
        }
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

private fun Context.difficultyName(level: Int): String = getString(when (level.coerceIn(1, 5)) {
    1 -> R.string.skills_difficulty_1
    2 -> R.string.skills_difficulty_2
    3 -> R.string.skills_difficulty_3
    4 -> R.string.skills_difficulty_4
    else -> R.string.skills_difficulty_5
})

/** Unlocked: night tile with mint figure. In progress: light tint. Locked: muted. */
private fun Context.skillBadge(status: SkillStatus): ExerciseAnimationView {
    val style = when {
        status.unlocked -> ExerciseAnimationView.BadgeStyle(
            AppColors.PRIMARY_DARK, AppColors.ACCENT, blendColor(AppColors.ACCENT, AppColors.PRIMARY_DARK, 0.5f),
            blendColor(Color.WHITE, AppColors.PRIMARY_DARK, 0.55f))
        status.points > 0 -> ExerciseAnimationView.BadgeStyle(
            AppColors.INFO_SURFACE, AppColors.PRIMARY, blendColor(AppColors.PRIMARY, AppColors.INFO_SURFACE, 0.5f),
            blendColor(AppColors.TEXT_SECONDARY, AppColors.INFO_SURFACE, 0.4f))
        else -> ExerciseAnimationView.BadgeStyle(
            AppColors.DIVIDER, blendColor(AppColors.TEXT_SECONDARY, AppColors.DIVIDER, 0.3f),
            blendColor(AppColors.TEXT_SECONDARY, AppColors.DIVIDER, 0.6f), blendColor(AppColors.TEXT_SECONDARY, AppColors.DIVIDER, 0.6f))
    }
    return ExerciseAnimationView(this).bind(status.skill.id, status.skill.name).apply {
        setBadgeStyle(style)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
}

private fun Context.filterChip(label: String, selected: Boolean, onClick: () -> Unit): TextView = TextView(this).apply {
    text = label
    textSize = 13f
    typeface = Typeface.DEFAULT_BOLD
    gravity = Gravity.CENTER
    setPadding(dp(16), dp(8), dp(16), dp(8))
    setTextColor(if (selected) AppColors.ON_ACCENT else AppColors.TEXT)
    background = if (selected) roundedDrawable(AppColors.PRIMARY, dp(18).toFloat())
    else roundedDrawable(AppColors.SURFACE, dp(18).toFloat(), AppColors.DIVIDER, dp(1))
    isClickable = true
    isFocusable = true
    setOnClickListener { onClick() }
}

private fun Context.detailRow(label: String, value: String): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    setPadding(0, dp(7), 0, dp(7))
    addView(bodyText(label, 13f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    addView(bodyText(value, 13f).apply { setTextColor(AppColors.TEXT); gravity = Gravity.END })
}

private fun Context.tappable(color: Int, radius: Float) =
    RippleDrawable(ColorStateList.valueOf(0x22000000), roundedDrawable(color, radius), roundedDrawable(Color.BLACK, radius))

private fun percent(part: Int, whole: Int): Int = if (whole <= 0) 0 else (part * 100 / whole)
