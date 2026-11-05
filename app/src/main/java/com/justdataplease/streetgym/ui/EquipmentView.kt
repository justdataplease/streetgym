package com.justdataplease.streetgym.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import com.justdataplease.streetgym.R
import com.justdataplease.streetgym.data.equipment.Equipment
import com.justdataplease.streetgym.data.equipment.EquipmentNeeds

/** The equipment's name in Settings and on a left-out exercise ("Switched off · Pull-up bar"). */
fun Context.equipmentName(equipment: Equipment): String = getString(when (equipment) {
    Equipment.PULLUP_BAR -> R.string.equipment_pullup_bar
    Equipment.LOW_BAR -> R.string.equipment_low_bar
    Equipment.PARALLEL_BARS -> R.string.equipment_parallel_bars
    Equipment.BOX -> R.string.equipment_box
    Equipment.WALL -> R.string.equipment_wall
    Equipment.POLE -> R.string.equipment_pole
    Equipment.BAND -> R.string.equipment_band
})

private fun Context.equipmentHint(equipment: Equipment): String = getString(when (equipment) {
    Equipment.PULLUP_BAR -> R.string.equipment_pullup_bar_hint
    Equipment.LOW_BAR -> R.string.equipment_low_bar_hint
    Equipment.PARALLEL_BARS -> R.string.equipment_parallel_bars_hint
    Equipment.BOX -> R.string.equipment_box_hint
    Equipment.WALL -> R.string.equipment_wall_hint
    Equipment.POLE -> R.string.equipment_pole_hint
    Equipment.BAND -> R.string.equipment_band_hint
})

private fun Context.equipmentShort(equipment: Equipment): String = getString(when (equipment) {
    Equipment.PULLUP_BAR -> R.string.equipment_pullup_bar_short
    Equipment.LOW_BAR -> R.string.equipment_low_bar_short
    Equipment.PARALLEL_BARS -> R.string.equipment_parallel_bars_short
    Equipment.BOX -> R.string.equipment_box_short
    Equipment.WALL -> R.string.equipment_wall_short
    Equipment.POLE -> R.string.equipment_pole_short
    Equipment.BAND -> R.string.equipment_band_short
})

/**
 * What a movement tagged [tags] needs, inline: "pull-up bar", "low bar + box", "box, low bar or parallel bars"; null
 * when any spot will do.
 */
fun Context.equipmentText(tags: Collection<String>): String? = EquipmentNeeds.of(tags).takeIf { it.isNotEmpty() }?.joinToString(" + ") { need ->
    val names = need.map { equipmentShort(it) }
    if (names.size == 1) names[0] else getString(R.string.equipment_or, names.dropLast(1).joinToString(", "), names.last())
}

/** "Needs: pull-up bar" under an exercise, or null. */
fun Context.equipmentNeeds(tags: Collection<String>): String? = equipmentText(tags)?.let { getString(R.string.equipment_needs, it) }

/**
 * Settings' equipment switches (the owner, 2026-11-05): one switch per piece of equipment, all on until the athlete
 * switches off what their spot lacks. [movementEquipment]: every movement's tags, to count the exercises that need each.
 */
fun Context.equipmentCard(settings: AppSettings, movementEquipment: Map<String, List<String>>, onChanged: () -> Unit = {}): View = card().apply {
    addView(bodyText(getString(R.string.equipment_intro), 14f))
    Equipment.entries.forEach { equipment ->
        val count = movementEquipment.values.count { tags -> EquipmentNeeds.of(tags).any { equipment in it } }
        val state = bodyText("", 13f)
        @Suppress("DEPRECATION") val toggle = Switch(context).apply { contentDescription = equipmentName(equipment) }
        fun bind() {
            val have = settings.equipment.has(equipment)
            state.text = if (have) "${equipmentHint(equipment)} · ${resources.getQuantityString(R.plurals.equipment_count, count, count)}"
            else getString(R.string.equipment_switched_off)
            state.setTextColor(if (have) AppColors.TEXT_SECONDARY else AppColors.ACTION)
            toggle.setOnCheckedChangeListener(null)
            toggle.isChecked = have
            toggle.setOnCheckedChangeListener { _, on ->
                settings.equipment = settings.equipment.with(equipment, on)
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
            setOnClickListener { toggle.toggle() }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(titleText(equipmentName(equipment), 16f))
                addView(state)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(toggle)
        }, topDp = 6)
    }
}
