package com.justdataplease.streetgym.ui

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.justdataplease.streetgym.R
import com.justdataplease.streetgym.billing.Entitlement
import com.justdataplease.streetgym.billing.LevelAccess
import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.skills.OverallScore
import com.justdataplease.streetgym.data.skills.SkillFamily
import com.justdataplease.streetgym.data.skills.SkillGroup
import com.justdataplease.streetgym.data.skills.SkillLevel
import com.justdataplease.streetgym.data.skills.SkillProgress
import com.justdataplease.streetgym.data.skills.SkillStandard
import com.justdataplease.streetgym.data.skills.SkillStatus
import com.justdataplease.streetgym.data.skills.SkillSwitches
import com.justdataplease.streetgym.data.skills.trained
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * An overall-score amount as the athlete sees it. The score is kept out of [OverallScore.MAX] (1000), so the smallest
 * rungs still count, and shown out of 100 with one decimal: 425 reads "42.5", 200 reads "20".
 */
internal fun scoreText(points: Number): String = SCORE_FORMAT.format(points.toDouble() * 100 / OverallScore.MAX)

private val SCORE_FORMAT = DecimalFormat("0.#", DecimalFormatSymbols(Locale.ENGLISH))

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

/**
 * Skills tab: the overall score with its tier and the four group scores, the strongest families and the next level,
 * the skills closest to their next level, a group filter and one ladder of medals per family ([skillMedal]).
 * [markedComplete]: days marked complete without training, by a tap or the placement test, which give nothing.
 */
fun Context.skillsContent(
    progress: SkillProgress,
    markedComplete: Int,
    group: SkillGroup?,
    onGroup: (SkillGroup?) -> Unit,
    onOpen: (SkillStatus, SkillFamily, List<SkillStatus>) -> Unit,
    switches: SkillSwitches = SkillSwitches(),
    /** An ad card ([com.justdataplease.streetgym.ads.ManagedNativePlacement]) after the fourth ladder, or the last. */
    ad: View? = null,
): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    // The score, the closest skills and the level count are out of the skills the athlete trains (review 12).
    val trained = progress.trained(switches)
    val closest = trained.closest(3)
    val tier = Entitlement.tier(context)
    val overall = trained.overall
    val faint = blendColor(Color.WHITE, AppColors.PRIMARY_DARK, 0.25f)

    // How levels are earned, one tap away above the score.
    addWithMargins(TextView(context).apply {
        text = getString(R.string.skills_help)
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(AppColors.PRIMARY)
        setPadding(dp(4), dp(8), dp(4), dp(8))
        background = tappable(Color.TRANSPARENT, dp(10).toFloat())
        isClickable = true
        isFocusable = true
        setOnClickListener {
            AlertDialog.Builder(context).setTitle(R.string.skills_help).setMessage(R.string.skills_how_points)
                .setPositiveButton(android.R.string.ok, null).show()
        }
    }, width = LinearLayout.LayoutParams.WRAP_CONTENT, bottomDp = 6)
    addView(LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = roundedDrawable(AppColors.PRIMARY_DARK, dp(24).toFloat())
        addView(bodyText(getString(R.string.skills_score_kicker).uppercase(), 12f).apply {
            setTextColor(AppColors.ACCENT)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.1f
        })
        val tier = overall.tier
        addWithMargins(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            contentDescription = getString(R.string.skills_score_description, scoreText(overall.score),
                scoreText(OverallScore.MAX), tierName(tier))
            addView(titleText(scoreText(overall.score), 38f).apply { setTextColor(Color.WHITE) })
            addWithMargins(bodyText(getString(R.string.skills_score_of, scoreText(OverallScore.MAX)), 15f).apply {
                setTextColor(blendColor(Color.WHITE, AppColors.PRIMARY_DARK, 0.3f))
            }, width = LinearLayout.LayoutParams.WRAP_CONTENT, leftDp = 8, bottomDp = 6)
        }, topDp = 4)
        if (switches.any) addView(bodyText(getString(R.string.skills_score_trained, scoreText(progress.overall.score),
            scoreText(OverallScore.MAX)), 12f).apply { setTextColor(blendColor(Color.WHITE, AppColors.PRIMARY_DARK, 0.3f)) })
        val next = tier.next
        addView(bodyText(if (next == null) getString(R.string.skills_tier_top, tierName(tier))
            else getString(R.string.skills_tier_next, tierName(tier), scoreText(next.floor - overall.score), tierName(next)), 14f).apply {
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        })
        val inTier = if (next == null) 100 else percent(overall.score - tier.floor, next.floor - tier.floor)
        addWithMargins(horizontalProgress(inTier).apply {
            progressTintList = ColorStateList.valueOf(AppColors.ACCENT)
            progressBackgroundTintList = ColorStateList.valueOf(blendColor(AppColors.ACCENT, AppColors.PRIMARY_DARK, 0.75f))
        }, height = dp(8), topDp = 10)
        addWithMargins(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            GROUPS.forEachIndexed { index, (option, label) ->
                val (points, ceiling) = overall.group(option)
                addView(groupScoreTile(getString(label), points, ceiling, option == group) {
                    onGroup(if (option == group) null else option)
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (index > 0) marginStart = dp(6)
                })
            }
        }, topDp = 14)
        // The three strongest families: the skill each counts, its level and its points.
        overall.families.filter { it.points > 0 }.sortedByDescending { it.points }.take(3).forEachIndexed { index, family ->
            val best = family.best ?: return@forEachIndexed
            addWithMargins(bodyText(getString(R.string.skills_family_line, family.family.spec.name,
                getString(R.string.skills_skill_level, best.skill.name, levelName(best.level)),
                scoreText(overall.scaled(family.points))), 13f).apply { setTextColor(faint) }, topDp = if (index == 0) 12 else 2)
        }
        closest.firstOrNull()?.let { status ->
            addWithMargins(bodyText(getString(R.string.skills_next, status.skill.name, needText(status)), 13f).apply {
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
            }, topDp = 10)
        }
        addWithMargins(bodyText(getString(R.string.skills_levels, trained.levels, trained.totalLevels), 12f).apply {
            setTextColor(faint)
        }, topDp = 6)
        if (markedComplete > 0) addView(bodyText(getString(R.string.skills_marked_complete, markedComplete), 12f).apply { setTextColor(faint) })
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

    val shown = progress.families.filter { (family, _) -> group == null || family.spec.group == group }
    val adAt = minOf(4, shown.size)
    fun placeAd() { if (ad != null) addWithMargins(ad, topDp = if (tier.adFree) 0 else 14) }
    shown.forEachIndexed { index, (family, statuses) ->
        if (index == adAt) placeAd()
        addWithMargins(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(titleText(family.spec.name, 16f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(bodyText(getString(R.string.skills_family_count, statuses.sumOf { it.level.ordinal },
                statuses.size * SkillLevel.ELITE.ordinal), 12f))
        }, topDp = 14, bottomDp = 8)
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                statuses.forEach { status ->
                    val off = switches.isRungOff(family.spec.id, status.skill.rung)
                    addWithMargins(skillCell(status, lockFor(status, tier), off) { onOpen(status, family, statuses) },
                        width = dp(86), rightDp = 8)
                }
            })
        })
    }
    if (adAt == shown.size) placeAd()
}

/**
 * Detail sheet: the animated movement guide, the skill as a medal with its difficulty and level, its sets and best set,
 * what the next level takes, every level's sets at its target, its value in the overall score and where the program
 * teaches it.
 */
fun Context.showSkillSheet(status: SkillStatus, family: SkillFamily, statuses: List<SkillStatus>, overall: OverallScore,
    off: Boolean = false) {
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
            addView(skillMedal(status, 56), LinearLayout.LayoutParams(dp(60), medalHeight(56)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(titleText(skill.name, 20f))
                addView(bodyText(getString(R.string.skills_ladder_rung, family.spec.name, skill.rung + 1, statuses.size), 13f))
                lock?.let { addWithMargins(lockBadge(partOf(it)), width = LinearLayout.LayoutParams.WRAP_CONTENT, topDp = 4) }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) })
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
        addWithMargins(detailRow(getString(R.string.skills_value), getString(R.string.skills_value_line,
            scoreText(overall.scaled(skill.value.toDouble())), family.spec.name, scoreText(overall.scaled(family.ceiling.toDouble())))),
            topDp = 8)
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

fun Context.tierName(tier: OverallScore.Tier): String = getString(when (tier) {
    OverallScore.Tier.BEGINNER -> R.string.skills_tier_beginner
    OverallScore.Tier.NOVICE -> R.string.skills_tier_novice
    OverallScore.Tier.INTERMEDIATE -> R.string.skills_tier_intermediate
    OverallScore.Tier.ADVANCED -> R.string.skills_tier_advanced
    OverallScore.Tier.ELITE -> R.string.skills_tier_elite
    OverallScore.Tier.WORLD_CLASS -> R.string.skills_tier_world_class
})

/** "Strict Pull-Up, Moderate skill, Intermediate": what a medal shows, for screen readers. */
private fun Context.medalDescription(status: SkillStatus, state: String): String =
    getString(R.string.skills_medal_description, status.skill.name, difficultyName(status.skill.difficulty), state)

private fun Context.closestRow(status: SkillStatus, onClick: () -> Unit): View = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    setPadding(dp(8), dp(8), dp(14), dp(8))
    background = tappable(AppColors.SURFACE, dp(18).toFloat())
    isClickable = true
    isFocusable = true
    contentDescription = medalDescription(status, levelName(status.level))
    setOnClickListener { onClick() }
    addView(skillMedal(status, 44), LinearLayout.LayoutParams(dp(48), medalHeight(44)))
    addView(LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(oneLineText(status.skill.name, bold = true).apply { textSize = 14f })
        addWithMargins(oneLineText(needText(status)).apply { textSize = 12f }, topDp = 2)
        addWithMargins(horizontalProgress(status.progress), height = dp(5), topDp = 6)
    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
        setMargins(dp(12), 0, dp(12), 0)
    })
    // Short of Beginner, the line above and the bar already say where the skill stands.
    if (status.level > SkillLevel.NONE) addView(titleText(levelName(status.level), 14f).apply { setTextColor(AppColors.PRIMARY) })
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
    addView(skillMedal(status, 78), LinearLayout.LayoutParams(dp(86), medalHeight(78)))
    addWithMargins(TextView(context).apply {
        text = status.skill.name
        textSize = 12f
        gravity = Gravity.CENTER_HORIZONTAL
        setTextColor(AppColors.TEXT)
        maxLines = 2
        minLines = 2
        ellipsize = TextUtils.TruncateAt.END
    }, topDp = 4)
    if (status.next != null && lock == null && !off) {
        addWithMargins(horizontalProgress(status.progress), width = dp(70), height = dp(4), topDp = 4)
    }
    if (lock != null && !off) {
        addWithMargins(lockBadge(getString(if (lock == Tier.PRO) R.string.tier_pro else R.string.tier_plus)), width = LinearLayout.LayoutParams.WRAP_CONTENT, topDp = 4)
    } else {
        addWithMargins(bodyText(state, 11f).apply {
            if (status.level > SkillLevel.NONE) {
                setTextColor(AppColors.PRIMARY)
                typeface = Typeface.DEFAULT_BOLD
            }
        }, width = LinearLayout.LayoutParams.WRAP_CONTENT, topDp = 2)
    }
}

/** One of the four group scores in the summary card, out of the group's share of 100; tapping filters the ladders. */
private fun Context.groupScoreTile(label: String, score: Int, ceiling: Int, selected: Boolean, onClick: () -> Unit): View =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(4), dp(8), dp(4), dp(8))
        background = tappable(blendColor(AppColors.ACCENT, AppColors.PRIMARY_DARK, if (selected) 0.55f else 0.85f), dp(14).toFloat())
        isClickable = true
        isFocusable = true
        contentDescription = getString(R.string.skills_group_score_description, label, scoreText(score), scoreText(ceiling))
        setOnClickListener { onClick() }
        addView(titleText(scoreText(score), 20f).apply { setTextColor(Color.WHITE) })
        addView(bodyText(getString(R.string.skills_group_score_of, label, scoreText(ceiling)), 11f).apply {
            setTextColor(blendColor(Color.WHITE, AppColors.PRIMARY_DARK, 0.25f))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
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

private fun Context.tappable(color: Int, radius: Float) =
    RippleDrawable(ColorStateList.valueOf(0x22000000), roundedDrawable(color, radius), roundedDrawable(Color.BLACK, radius))

private fun percent(part: Int, whole: Int): Int = if (whole <= 0) 0 else (part * 100 / whole)
