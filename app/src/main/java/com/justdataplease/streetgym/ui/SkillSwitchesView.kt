package com.justdataplease.streetgym.ui

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import com.justdataplease.streetgym.R
import com.justdataplease.streetgym.data.skills.SkillBook
import com.justdataplease.streetgym.data.skills.SkillFamily
import com.justdataplease.streetgym.data.skills.SkillProgressions

/**
 * Settings' skill switches (the owner, review 12). One row per skill with something to switch off: its name, what is
 * on, and a switch for the whole skill. A tap on the row opens the skill's progression, rung by rung with the steps
 * leading to each, to switch it off from a harder rung on. The basics of every class stay on. Switched-off exercises
 * stay in the classes, greyed, and leave the skill progress; [onChanged] runs after each change.
 */
fun Context.skillSwitchesCard(book: SkillBook, settings: AppSettings, onChanged: () -> Unit = {}): View = card().apply {
    addView(bodyText(getString(R.string.switches_intro), 14f))
    SkillProgressions.switchable(book.families.map { it.spec }).forEach { spec ->
        val family = book.families.first { it.spec.id == spec.id }
        val state = bodyText("", 13f)
        @Suppress("DEPRECATION") val toggle = Switch(context).apply { contentDescription = spec.name }
        fun bind() {
            val from = settings.skillSwitches.offFrom[spec.id]
            state.text = switchState(family, from)
            state.setTextColor(if (from == null) AppColors.TEXT_SECONDARY else AppColors.ACTION)
            toggle.setOnCheckedChangeListener(null)
            // On while any step past the basics is in the classes; off switches the whole skill off.
            toggle.isChecked = from == null || from > SkillProgressions.firstSwitchable(spec)
            toggle.setOnCheckedChangeListener { _, on ->
                settings.skillSwitches = settings.skillSwitches.with(spec.id, if (on) null else SkillProgressions.firstSwitchable(spec))
                bind()
                onChanged()
            }
        }
        bind()
        addWithMargins(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(60)
            setPadding(dp(4), dp(6), 0, dp(6))
            background = RippleDrawable(ColorStateList.valueOf(0x22000000), roundedDrawable(Color.TRANSPARENT, dp(12).toFloat()),
                roundedDrawable(Color.BLACK, dp(12).toFloat()))
            isClickable = true
            isFocusable = true
            setOnClickListener { showFamilySwitches(book, family, settings) { bind(); onChanged() } }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(titleText(spec.name, 16f))
                addView(state)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(bodyText("›", 24f).apply {
                setPadding(dp(6), 0, dp(10), 0)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
            addView(toggle)
        }, topDp = 6)
    }
}

/** What a skill's switches leave on: every step, up to a rung, or nothing beyond the basics. */
private fun Context.switchState(family: SkillFamily, from: Int?): String {
    val first = SkillProgressions.firstSwitchable(family.spec)
    return when {
        from == null -> getString(R.string.switches_all_on)
        from <= first && first == 0 -> getString(R.string.switches_off)
        from <= first -> getString(R.string.switches_off_basics, family.skills[first - 1].name)
        else -> getString(R.string.switches_up_to, family.skills[from - 1].name)
    }
}

/**
 * A skill's progression: its rungs easiest first, each with the steps the classes teach toward it, and a switch per rung.
 * Switching a rung off switches off every harder one; switching one on brings back every easier one. The basics show
 * as always on.
 */
fun Context.showFamilySwitches(book: SkillBook, family: SkillFamily, settings: AppSettings, onChanged: () -> Unit) {
    val spec = family.spec
    val first = SkillProgressions.firstSwitchable(spec)
    val steps = SkillProgressions.LINES[spec.id].orEmpty()
    val toggles = mutableMapOf<Int, Switch>()
    val rows = mutableMapOf<Int, View>()
    fun bind() {
        val from = settings.skillSwitches.offFrom[spec.id]
        toggles.forEach { (rung, toggle) ->
            toggle.setOnCheckedChangeListener(null)
            toggle.isChecked = from == null || rung < from
            rows.getValue(rung).alpha = if (toggle.isChecked) 1f else 0.5f
            toggle.setOnCheckedChangeListener { _, on ->
                val next = if (on) (rung + 1).takeIf { it < family.skills.size } else rung
                settings.skillSwitches = settings.skillSwitches.with(spec.id, next)
                bind()
                onChanged()
            }
        }
    }
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(16), dp(20), dp(8))
        val top = family.skills.last()
        addView(ExerciseAnimationView(context).bind(top.id, top.name), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(150)))
        addWithMargins(titleText(spec.name, 22f), topDp = 8)
        addWithMargins(bodyText(getString(R.string.switches_family_intro), 14f), topDp = 4)
        family.skills.forEachIndexed { rung, skill ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(10), 0, dp(4))
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dp(48)
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(bodyText(getString(R.string.switches_rung, rung + 1), 12f).apply {
                            setTextColor(AppColors.PRIMARY)
                            typeface = Typeface.DEFAULT_BOLD
                        })
                        addView(titleText(skill.name, 16f))
                    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    if (rung < first) {
                        addView(bodyText(getString(R.string.switches_always_on), 12f))
                    } else {
                        @Suppress("DEPRECATION") val toggle = Switch(context).apply { contentDescription = skill.name }
                        toggles[rung] = toggle
                        addView(toggle)
                    }
                })
                val names = steps.filter { it.rung == rung && it.movementId != skill.id }.map { book.nameOf(it.movementId) ?: it.movementId }
                if (names.isNotEmpty()) addView(bodyText(getString(R.string.switches_steps, names.joinToString(" · ")), 13f))
            }
            rows[rung] = row
            addView(row)
        }
    }
    bind()
    AlertDialog.Builder(this)
        .setView(ScrollView(this).apply { addView(content) })
        .setPositiveButton(R.string.switches_done, null)
        .show()
}
