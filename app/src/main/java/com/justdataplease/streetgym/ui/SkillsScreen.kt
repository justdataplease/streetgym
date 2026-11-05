package com.justdataplease.streetgym.ui

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.justdataplease.streetgym.R
import com.justdataplease.streetgym.billing.Entitlement
import com.justdataplease.streetgym.billing.LevelAccess
import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.skills.SkillFamily
import com.justdataplease.streetgym.data.skills.SkillLevel
import com.justdataplease.streetgym.data.skills.SkillProgress
import com.justdataplease.streetgym.data.skills.SkillStandard
import com.justdataplease.streetgym.data.skills.SkillStatus
import com.justdataplease.streetgym.data.skills.SkillSwitches
import java.text.NumberFormat
import java.util.Locale

/** A row of options with one selected: the tabs of Training and Progress, and other small choices. */
/** [compact]: narrower segments, so four labels fit a small phone (Training's tabs). */
fun Context.segmentedSwitch(labels: List<String>, selected: Int, compact: Boolean = false, onSelect: (Int) -> Unit): LinearLayout =
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
                setPadding(dp(if (compact) 11 else 18), dp(8), dp(if (compact) 11 else 18), dp(8))
                setTextColor(if (index == selected) AppColors.PRIMARY else AppColors.TEXT_SECONDARY)
                if (index == selected) background = roundedDrawable(AppColors.SURFACE, dp(11).toFloat())
                isClickable = true
                isFocusable = true
                contentDescription = label
                setOnClickListener { if (index != selected) onSelect(index) }
            })
        }
    }

/** Points as the athlete reads them: "12,480". */
fun pointsText(points: Int): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(points)

/**
 * Skills tab (the owner, 2.10.0: "only show the overall score as points, nothing else, and below the chevrons"): the
 * athlete's points ([com.justdataplease.streetgym.data.points.Points]), a tap away from how they and the ranks work,
 * then one ladder of medals per family ([skillMedal]), each wearing its rank.
 */
fun Context.skillsContent(
    progress: SkillProgress,
    points: Int,
    onOpen: (SkillStatus, SkillFamily, List<SkillStatus>) -> Unit,
    switches: SkillSwitches = SkillSwitches(),
    /** Whether a skill's movement needs equipment the athlete switched off: its medal greys like a switched-off rung. */
    lacks: (String) -> Boolean = { false },
    /** An ad card ([com.justdataplease.streetgym.ads.ManagedNativePlacement]) after the fourth ladder, or the last. */
    ad: View? = null,
): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    val tier = Entitlement.tier(context)
    addWithMargins(LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = tappable(AppColors.PRIMARY_DARK, dp(24).toFloat())
        isClickable = true
        isFocusable = true
        contentDescription = getString(R.string.skills_points_description, pointsText(points))
        setOnClickListener { showSkillsHelp() }
        addView(bodyText(getString(R.string.skills_points_kicker).uppercase(), 12f).apply {
            setTextColor(AppColors.ACCENT)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.1f
        })
        addWithMargins(titleText(pointsText(points), 38f).apply { setTextColor(Color.WHITE) }, topDp = 4)
    }, topDp = 4)

    // Every shield a skill can earn, so anyone sees what's ahead (the owner, 2.11.0: "i liked the chevrons"); since
    // 2.11.5 only their names, the sets they take are a tap away.
    addWithMargins(sectionLabel(getString(R.string.skills_ranks)), topDp = 14)
    addWithMargins(rankLegend(), topDp = 6)

    val adAt = minOf(4, progress.families.size)
    fun placeAd() { if (ad != null) addWithMargins(ad, topDp = if (tier.adFree) 0 else 14) }
    progress.families.forEachIndexed { index, (family, statuses) ->
        if (index == adAt) placeAd()
        addWithMargins(titleText(family.spec.name, 16f), topDp = 14)
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                // The tags on the medals' corners reach past their cells: room above for Plus / Pro, wider gaps beside.
                clipChildren = false
                clipToPadding = false
                setPadding(0, dp(10), 0, 0)
                statuses.forEach { status ->
                    val off = switches.isRungOff(family.spec.id, status.skill.rung) || lacks(status.skill.id)
                    addWithMargins(skillCell(status, lockFor(status, tier), off) { onOpen(status, family, statuses) },
                        width = dp(86), rightDp = 14)
                }
            })
        })
    }
    if (adAt == progress.families.size) placeAd()
}

/**
 * Detail sheet: the animated movement guide, the skill as a medal with its difficulty and level, its sets and best set,
 * what the next rank takes, every rank's sets at its target and where the program teaches it.
 */
fun Context.showSkillSheet(status: SkillStatus, family: SkillFamily, statuses: List<SkillStatus>, off: Boolean = false) {
    val skill = status.skill
    val standard = skill.standard
    val lock = lockFor(status, Entitlement.tier(this))
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(16), dp(20), dp(8))
        addView(ExerciseAnimationView(context).bind(skill.id, skill.name), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(168)))
        addWithMargins(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // The shield hangs past the medal's corner, so the medal gets the room for it.
            addView(FrameLayout(context).apply {
                clipChildren = false
                addView(skillMedal(status, 56), FrameLayout.LayoutParams(dp(56), medalHeight(56)))
            }, LinearLayout.LayoutParams(dp(56) + shieldOverhang(56), medalHeight(56) + dp(8)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(titleText(skill.name, 20f))
                addView(bodyText(getString(R.string.skills_ladder_rung, family.spec.name, skill.rung + 1, statuses.size), 13f))
                lock?.let { addWithMargins(lockBadge(partOf(it)), width = LinearLayout.LayoutParams.WRAP_CONTENT, topDp = 4) }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4) })
        }, topDp = 10)
        if (off) addWithMargins(bodyText(getString(R.string.skills_switched_off_note), 13f).apply { setTextColor(AppColors.ACTION) }, topDp = 8)
        addWithMargins(detailRow(getString(R.string.skills_difficulty), difficultyName(skill.difficulty)), topDp = 10)
        addView(detailRow(getString(R.string.skills_level), levelName(status.level)))
        status.carriedBy?.let { addView(detailRow(getString(R.string.skills_held_through), it.name)) }
        addView(detailRow(getString(R.string.skills_sets_done), status.sets.toString()))
        addView(detailRow(getString(R.string.skills_best),
            if (status.best > 0) maxText(standard, status.best) else getString(R.string.skills_best_none)))
        status.next?.let { next ->
            addView(detailRow(getString(R.string.skills_next_level, levelName(next)), needText(status, named = false)))
            addWithMargins(horizontalProgress(status.progress), height = dp(8), topDp = 4)
        }
        addWithMargins(sectionLabel(getString(R.string.skills_levels_title)), topDp = 12)
        SkillLevel.entries.drop(1).forEach { level ->
            val needs = getString(R.string.skills_sets_at, sets(level.sets), targetText(standard, standard.target(level) ?: 0))
            addView(detailRow((if (status.level >= level) "✓ " else "") + levelName(level), needs))
        }
        skill.counts?.let {
            addWithMargins(sectionLabel(getString(R.string.skills_counts_title)), topDp = 12)
            addWithMargins(bodyText(it, 14f), topDp = 4)
        }
        val expected = skill.expected
        val schedule = when {
            expected == null -> getString(R.string.skills_beyond_program)
            skill.beyondProgram -> getString(R.string.skills_readiness_test, expected.level)
            expected.number != null -> getString(R.string.skills_schedule_class, expected.level, expected.number)
            else -> getString(R.string.skills_schedule_level, expected.level)
        }
        addView(detailRow(getString(R.string.skills_on_schedule), schedule))
        statuses.getOrNull(skill.rung - 1)?.let { addView(detailRow(getString(R.string.skills_builds_on), it.skill.name)) }
        statuses.getOrNull(skill.rung + 1)?.let { addView(detailRow(getString(R.string.skills_unlocks_next), it.skill.name)) }
    }
    AlertDialog.Builder(this)
        .setView(android.widget.ScrollView(this).apply { addView(content) })
        .setPositiveButton(R.string.skills_close, null)
        .show()
}

/** A set's exact reps or seconds: "8 reps", "5 reps each side", "30 s", "10 s each side". */
fun Context.maxText(standard: SkillStandard, amount: Int): String = when {
    standard.seconds -> getString(if (standard.perSide) R.string.skills_max_seconds_side else R.string.skills_max_seconds, amount)
    else -> resources.getQuantityString(if (standard.perSide) R.plurals.skills_max_reps_side else R.plurals.skills_max_reps, amount, amount)
}

/** A target: "12+ reps", "5+ reps each side", "60+ s". */
private fun Context.targetText(standard: SkillStandard, amount: Int): String = when {
    standard.seconds -> getString(if (standard.perSide) R.string.skills_target_seconds_side else R.string.skills_target_seconds, amount)
    else -> resources.getQuantityString(if (standard.perSide) R.plurals.skills_target_reps_side else R.plurals.skills_target_reps, amount, amount)
}

private fun Context.sets(count: Int): String = resources.getQuantityString(R.plurals.skills_sets, count, count)

/** The five ranks a skill can earn, each as its shield with its name; tapping explains them and the sets they take. */
private fun Context.rankLegend(): View = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.BOTTOM
    setPadding(dp(4), dp(10), dp(4), dp(10))
    background = tappable(AppColors.SURFACE, dp(18).toFloat())
    isClickable = true
    isFocusable = true
    val levels = SkillLevel.entries.filter { it > SkillLevel.NONE }
    contentDescription = getString(R.string.skills_ranks_description, levels.joinToString { levelName(it) })
    setOnClickListener { showSkillsHelp() }
    levels.forEach { level ->
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            addView(RankBadgeView(context, level, 0.85f), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT))
            addWithMargins(oneLineText(levelName(level), bold = true).apply {
                textSize = 11f
                gravity = Gravity.CENTER_HORIZONTAL
            }, topDp = 4)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }
}

/** What the next level still takes: "18 sets of 12+ reps for Intermediate", or without the level's name. */
fun Context.needText(status: SkillStatus, named: Boolean = true): String {
    val next = status.next ?: return levelName(status.level)
    val needs = getString(R.string.skills_sets_at, sets(status.setsToGo), targetText(status.skill.standard, status.skill.standard.target(next) ?: 0))
    return if (named) getString(R.string.skills_need_for, needs, levelName(next)) else needs
}

fun Context.levelName(level: SkillLevel): String = getString(when (level) {
    SkillLevel.NONE -> R.string.skills_level_0
    SkillLevel.BEGINNER -> R.string.skills_level_1
    SkillLevel.INTERMEDIATE -> R.string.skills_level_2
    SkillLevel.ADVANCED -> R.string.skills_level_3
    SkillLevel.MASTER -> R.string.skills_level_4
    SkillLevel.ELITE -> R.string.skills_level_5
})

/** Easy, Moderate, Hard, Very hard or Extreme: a skill's difficulty, as its medal's outline shows it. */
fun Context.difficultyName(grade: Int): String = getString(when (grade.coerceIn(1, 5)) {
    1 -> R.string.skills_difficulty_1
    2 -> R.string.skills_difficulty_2
    3 -> R.string.skills_difficulty_3
    4 -> R.string.skills_difficulty_4
    else -> R.string.skills_difficulty_5
})

/** "Strict Pull-Up, Moderate skill, Intermediate": what a medal shows, for screen readers. */
private fun Context.medalDescription(status: SkillStatus, state: String): String =
    getString(R.string.skills_medal_description, status.skill.name, difficultyName(status.skill.difficulty), state)

private fun Context.showSkillsHelp() {
    AlertDialog.Builder(this).setTitle(R.string.skills_help).setMessage(R.string.skills_how_points)
        .setPositiveButton(android.R.string.ok, null).show()
}

private fun Context.skillCell(status: SkillStatus, lock: Tier?, off: Boolean, onClick: () -> Unit): View = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    gravity = Gravity.CENTER_HORIZONTAL
    isClickable = true
    isFocusable = true
    background = tappable(Color.TRANSPARENT, dp(14).toFloat())
    // A switched-off skill (review 12) is greyed and says so; its level stays on its page.
    if (off) alpha = 0.4f
    val state = when {
        off -> getString(R.string.skills_switched_off)
        lock != null -> partOf(lock)
        else -> levelName(status.level)
    }
    contentDescription = medalDescription(status, state)
    setOnClickListener { onClick() }
    // Everything sits on the medal (the owner, 2.11.4), and since 2.11.5 the rank shield and Plus or Pro are tags half
    // outside its corners: the shield at the bottom right, Plus or Pro across the top edge. The bar to the next rank runs
    // beside the shield, so only the name is below and every row of the ladder lines up. The tags draw past the cell.
    clipChildren = false
    addView(FrameLayout(context).apply {
        clipChildren = false
        addView(skillMedal(status, 78), FrameLayout.LayoutParams(dp(78), medalHeight(78), Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        if (lock != null && !off) {
            addView(lockBadge(getString(if (lock == Tier.PRO) R.string.tier_pro else R.string.tier_plus)).apply {
                textSize = 10f
                setPadding(dp(6), dp(3), dp(6), dp(3))
                // A rim in the page colour cuts the tag out of the medal's outline.
                background = roundedDrawable(AppColors.TEXT_SECONDARY, dp(12).toFloat(), AppColors.BACKGROUND, dp(2))
                translationX = dp(8).toFloat()
                translationY = -dp(10).toFloat()
            }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END))
        }
    }, LinearLayout.LayoutParams(dp(86), medalHeight(78)))
    // The bar keeps its place when there is nothing to show (a top rank, a lock), so the names start level. Beside a
    // shield it starts under the medal's left edge and stops short of it.
    // In five blocks, not one line (the owner, 2.11.5: "make it distinct, not continuous"): each a fifth of the sets
    // the next rank takes, filled only once whole.
    addView(SegmentBar(context, SEGMENTS, status.progress * SEGMENTS / 100).apply {
        visibility = if (status.next != null && lock == null && !off) View.VISIBLE else View.INVISIBLE
    }, LinearLayout.LayoutParams(dp(54), dp(5)).apply {
        // Beside the shield when there is one; centred under the medal before the first rank (the owner, 2.11.6).
        if (status.level > SkillLevel.NONE) {
            gravity = Gravity.START
            marginStart = dp(8)
        } else {
            gravity = Gravity.CENTER_HORIZONTAL
        }
        topMargin = dp(4)
    })
    addWithMargins(TextView(context).apply {
        text = status.skill.name
        textSize = 12f
        gravity = Gravity.CENTER_HORIZONTAL
        setTextColor(AppColors.TEXT)
        includeFontPadding = false
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
    }, topDp = 5)
}

/**
 * The tier a skill waits for: one the program first teaches in Levels 4 and up stays locked until the tier that opens
 * that level, unless the athlete already has a level in it. Null when it is open.
 */
private fun lockFor(status: SkillStatus, tier: Tier): Tier? =
    status.skill.expected?.level?.takeIf { status.level == SkillLevel.NONE && LevelAccess.locked(it, tier) }?.let(LevelAccess::tierFor)

private fun Context.partOf(tier: Tier): String = getString(if (tier == Tier.PRO) R.string.skills_pro else R.string.skills_plus)

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

private const val SEGMENTS = 5

/** A progress bar as [segments] separate blocks, the first [filled] of them in the primary colour. */
private class SegmentBar(context: Context, private val segments: Int, private val filled: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gap = context.dp(2).toFloat()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        val block = (width - gap * (segments - 1)) / segments
        for (i in 0 until segments) {
            paint.color = if (i < filled) AppColors.PRIMARY else AppColors.DIVIDER
            val left = i * (block + gap)
            canvas.drawRect(left, 0f, left + block, height.toFloat(), paint)
        }
    }
}

private fun Context.tappable(color: Int, radius: Float) =
    RippleDrawable(ColorStateList.valueOf(0x22000000), roundedDrawable(color, radius), roundedDrawable(Color.BLACK, radius))

private fun percent(part: Int, whole: Int): Int = if (whole <= 0) 0 else (part * 100 / whole)
