package com.justdataplease.streetgym.data.skills

import com.justdataplease.streetgym.data.model.TrainingProgram

/**
 * Each skill family's progression, in the order the classes teach it: every movement of its own line, with the rung it
 * leads to (an index into the family's ladder, [SkillBook.DEFAULT_FAMILIES]). The owner's skill switches (review 12):
 * an athlete can switch a family off, or off from one rung on, and every step of the line from there is left out of
 * their classes. Shared work (push-ups, pull-ups, dips, squats, rows, support holds, the hollow body and generic core,
 * leg and mobility drills) belongs to no line, so switching a skill off never takes the basics with it.
 */
object SkillProgressions {
    /** A step of a line: its movement and the ladder rung it leads to. */
    data class Step(val movementId: String, val rung: Int)

    /** The basics, always on: switching their family off starts after them. */
    val LOCKED: Set<String> = setOf("dead_hang", "pullup", "parallel_dip", "pushup", "squat", "hollow_body_hold")

    private fun line(vararg steps: Pair<String, Int>) = steps.map { (id, rung) -> Step(id, rung) }

    /** Each family's line, easiest first. Rungs are in it too, at their own index. */
    val LINES: Map<String, List<Step>> = mapOf(
        "pullup" to line("archer_pullup" to 2, "assisted_one_arm_pullup" to 3, "one_arm_pullup_negative" to 3, "one_arm_pullup" to 3),
        "muscleup" to line("low_bar_transition" to 0, "chest_to_bar" to 0, "band_muscleup" to 0, "negative_muscleup" to 0,
            "jump_muscleup" to 0, "pullover_lowbar" to 0, "strict_muscleup" to 0),
        "front_lever" to line("tuck_front_row" to 0, "tuck_front_hold" to 0, "advanced_tuck_front" to 1, "tuck_front_lever_row" to 1,
            "one_leg_front" to 2, "straddle_front" to 2, "full_front" to 3),
        "back_lever" to line("german_hang" to 0, "skin_cat_partial" to 0, "tuck_back_hold" to 0, "skin_the_cat" to 0,
            "advanced_tuck_back" to 1, "straddle_back" to 2, "full_back" to 3),
        "hefesto" to line("german_hang" to 0, "skin_cat_partial" to 0, "assisted_supinated_german_hang" to 0,
            "assisted_behind_bar_support" to 0, "supinated_german_hang" to 0, "assisted_korean_dip" to 0,
            "assisted_partial_hefesto" to 0, "assisted_hefesto" to 0, "hefesto" to 0),
        "pushup" to line("archer_pushup" to 1, "one_arm_pushup" to 1),
        "hspu" to line("pike_pushup" to 0, "elevated_pike_pushup" to 1, "wall_hspu_negative" to 1, "wall_hspu" to 1,
            "deficit_wall_hspu" to 2, "freestanding_hspu_negative" to 2, "freestanding_hspu" to 2),
        "planche" to line("frog_stand" to 0, "planche_lean" to 0, "pseudo_planche_pushup" to 0, "tuck_planche" to 1,
            "advanced_tuck_planche" to 2, "tuck_planche_pushup" to 2, "straddle_planche" to 3, "full_planche" to 4),
        "handstand" to line("pike_handstand_hold" to 0, "wall_handstand" to 0, "handstand_exit" to 0, "wall_kickup" to 1,
            "handstand_toe_pull" to 1, "handstand_balance" to 1, "freestanding_handstand" to 1, "straddle_handstand" to 2,
            "supported_handstand_shift" to 2, "handstand_weight_shift" to 2, "fingertip_one_arm_handstand" to 2,
            "one_arm_handstand_hover" to 2, "straddle_one_arm_handstand" to 2, "one_arm_handstand" to 2),
        "pistol" to line("assisted_pistol_squat" to 1, "pistol_squat" to 1),
        "shrimp" to line("assisted_partial_shrimp" to 0, "assisted_shrimp" to 0, "floating_shrimp" to 0,
            "assisted_held_shrimp" to 0, "shrimp_squat" to 0),
        "hanging_core" to line("hanging_leg_raise" to 0, "toes_to_bar" to 1),
        "lsit" to line("tuck_lsit" to 0, "one_leg_lsit" to 0, "lsit" to 0, "v_sit" to 1),
        "dragon_flag" to line("candlestick" to 1, "bent_dragon_negative" to 1, "one_leg_dragon_negative" to 1,
            "full_dragon_negative" to 1, "dragon_flag" to 1),
        "human_flag" to line("flag_ground_support" to 0, "assisted_tuck_flag" to 0, "vertical_flag" to 0, "tuck_flag" to 0,
            "straddle_flag" to 1, "flag_negative" to 2, "full_flag" to 2),
    )

    /** Steps that need another line too: the freestanding handstand push-up needs the freestanding handstand. */
    val REQUIRES: Map<String, Pair<String, Int>> = mapOf(
        "freestanding_hspu_negative" to ("handstand" to 1),
        "freestanding_hspu" to ("handstand" to 1),
    )

    /** The first rung a family can be switched off from: the one after its locked basics. */
    fun firstSwitchable(family: SkillFamilySpec): Int = family.ladder.indexOfFirst { it !in LOCKED }.coerceAtLeast(0)

    /** Families with something to switch off (the dip's line is all basics). */
    fun switchable(families: List<SkillFamilySpec>): List<SkillFamilySpec> = families.filter { LINES[it.id].orEmpty().isNotEmpty() }
}

/**
 * The athlete's switches: per family, the first ladder rung left out of their classes ([offFrom]); a family missing
 * from the map is all on. A movement is off when every line it belongs to is off at its step (the German hang stays
 * while the back lever or the Hefesto is on), or a line it needs is off.
 */
data class SkillSwitches(val offFrom: Map<String, Int> = emptyMap()) {
    val any: Boolean get() = offFrom.isNotEmpty()

    /** Whether [family]'s rung at [rung] is switched off. */
    fun isRungOff(family: String, rung: Int): Boolean = offFrom[family]?.let { rung >= it } ?: false

    /** Whether the movement is left out of the athlete's classes. */
    fun isOff(movementId: String): Boolean {
        if (offFrom.isEmpty()) return false
        val homes = HOMES[movementId].orEmpty()
        val own = homes.isNotEmpty() && homes.all { (family, rung) -> isRungOff(family, rung) }
        val needs = SkillProgressions.REQUIRES[movementId]?.let { (family, rung) -> isRungOff(family, rung) } ?: false
        return own || needs
    }

    /** The family that switches the movement off, to name in the class ("You've switched off the Handstand"). */
    fun offFamily(movementId: String): String? {
        if (!isOff(movementId)) return null
        return HOMES[movementId].orEmpty().firstOrNull { (family, rung) -> isRungOff(family, rung) }?.first
            ?: SkillProgressions.REQUIRES[movementId]?.first
    }

    /** Switches [family] off from [rung] on (null: all on). */
    fun with(family: String, rung: Int?): SkillSwitches =
        SkillSwitches(if (rung == null) offFrom - family else offFrom + (family to rung))

    companion object {
        /** Every movement's lines: (family, the rung its step leads to). */
        val HOMES: Map<String, List<Pair<String, Int>>> = SkillProgressions.LINES.flatMap { (family, steps) ->
            steps.map { it.movementId to (family to it.rung) }
        }.groupBy({ it.first }, { it.second })

        /** "family:rung,family:rung", as kept in preferences. */
        fun parse(text: String?): SkillSwitches = SkillSwitches(text.orEmpty().split(',').mapNotNull { part ->
            val (family, rung) = part.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            rung.toIntOrNull()?.let { family to it }
        }.toMap())

        fun format(switches: SkillSwitches): String = switches.offFrom.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }
    }
}

/**
 * A class with nothing left to train: every exercise of its practice and main work is switched off. It counts as passed
 * (the sequence moves on) and earns nothing; warm-up and cool-down don't keep it.
 */
fun TrainingProgram.switchedOff(switches: SkillSwitches): Boolean {
    if (!switches.any) return false
    val work = (practice + exercises).filterNot { it.readinessCheck }
    return work.isNotEmpty() && work.all { exercise -> exercise.movementId?.let(switches::isOff) == true }
}

/**
 * The skills the athlete trains: each family with its switched-off rungs left out (a family all off goes), so the
 * overall score is out of what they train. The leaderboard keeps the whole scale ([SkillProgress.overall]).
 */
fun SkillProgress.trained(switches: SkillSwitches): SkillProgress = if (!switches.any) this else SkillProgress(
    families.mapNotNull { (family, statuses) ->
        val on = statuses.filter { status -> !switches.isRungOff(family.spec.id, status.skill.rung) }
        if (on.isEmpty()) null else SkillFamily(family.spec, on.map { it.skill }) to on
    },
)
