package com.justdataplease.streetgym.data.equipment

import com.justdataplease.streetgym.data.model.TrainingExercise

/**
 * What a training spot can lack (the owner, 2026-11-05: "for each exercise the equipment needed, and switch off what I
 * don't have, the same way as the skills"). The movement library tags every movement with what it uses; [EquipmentNeeds]
 * reads those tags as these seven. Everything else a tag names (a mat, the floor, something to hold, a towel, a spotter)
 * any spot has.
 */
enum class Equipment(val id: String) {
    PULLUP_BAR("pullup_bar"),
    LOW_BAR("low_bar"),
    PARALLEL_BARS("parallel_bars"),
    BOX("box"),
    WALL("wall"),
    POLE("pole"),
    BAND("band");

    companion object {
        fun of(id: String): Equipment? = entries.firstOrNull { it.id == id }
    }
}

object EquipmentNeeds {
    /** A library tag and the equipment that serves it: any one of them. */
    val TAGS: Map<String, Set<Equipment>> = mapOf(
        "high_bar" to setOf(Equipment.PULLUP_BAR),
        "rings_or_bar" to setOf(Equipment.PULLUP_BAR),
        "low_bar" to setOf(Equipment.LOW_BAR),
        "low_rings_or_bar" to setOf(Equipment.LOW_BAR),
        // The foot-assisted hang: a low bar, or a pull-up bar with the feet on a box.
        "low_bar_or_box" to setOf(Equipment.LOW_BAR, Equipment.PULLUP_BAR),
        "parallel_bars" to setOf(Equipment.PARALLEL_BARS),
        "box" to setOf(Equipment.BOX),
        "secure_box" to setOf(Equipment.BOX),
        "stable_step" to setOf(Equipment.BOX),
        "bench" to setOf(Equipment.BOX),
        // Incline push-ups and planks: hands on a box or bench, a low bar or the parallel bars.
        "secure_incline" to setOf(Equipment.BOX, Equipment.LOW_BAR, Equipment.PARALLEL_BARS),
        "wall" to setOf(Equipment.WALL),
        "stall_bars" to setOf(Equipment.POLE),
        "flag_pole" to setOf(Equipment.POLE),
        "resistance_band" to setOf(Equipment.BAND),
    )

    /** Tags any spot serves. Every library tag is in [TAGS] or here (EquipmentTest), so a new tag gets a decision. */
    val ANYWHERE: Set<String> = setOf(
        "mat", "mat_or_cushion", "support", "secure_support", "post", "doorway_or_post", "spotter", "foot_support",
        "parallettes_or_floor", "floor_or_parallettes", "parallettes_or_blocks", "parallel_bars_or_blocks", "secure_anchor",
        "secure_foot_anchor", "towel_or_band", "towel_or_sliders",
    )

    /** What [tags] need: one set per need, any one of its equipment serving it. */
    fun of(tags: Collection<String>): List<Set<Equipment>> = tags.mapNotNull(TAGS::get).distinct()
}

/**
 * The equipment the athlete switched off in Settings: what their spot lacks. A movement needing any of it is left out of
 * their classes, greyed like a switched-off skill ([com.justdataplease.streetgym.data.skills.SkillSwitches]).
 */
data class EquipmentSwitches(val off: Set<Equipment> = emptySet()) {
    val any: Boolean get() = off.isNotEmpty()

    fun has(equipment: Equipment): Boolean = equipment !in off

    /** The equipment the athlete lacks for a movement tagged [tags] (a need's first option: the box for an incline), or null. */
    fun missing(tags: Collection<String>): Equipment? {
        if (off.isEmpty()) return null
        return EquipmentNeeds.of(tags).firstOrNull { need -> need.all { it in off } }?.first()
    }

    fun isOff(exercise: TrainingExercise): Boolean = missing(exercise.equipment) != null

    fun with(equipment: Equipment, have: Boolean): EquipmentSwitches = EquipmentSwitches(if (have) off - equipment else off + equipment)

    companion object {
        /** "pullup_bar,wall", as kept in preferences. */
        fun parse(text: String?): EquipmentSwitches =
            EquipmentSwitches(text.orEmpty().split(',').mapNotNull { Equipment.of(it.trim()) }.toSet())

        fun format(switches: EquipmentSwitches): String = switches.off.sortedBy { it.ordinal }.joinToString(",") { it.id }
    }
}
