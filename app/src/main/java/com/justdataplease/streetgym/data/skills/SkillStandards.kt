package com.justdataplease.streetgym.data.skills

/**
 * An athlete's level in one skill, like belts: each takes [sets] sets at its target or more ([SkillStandard.target]),
 * and a set over a higher target counts for the lower ones too, so 30 sets at the Elite target earn all five. A level
 * held on a harder rung holds on the easier rungs of its ladder ([SkillStatus.carriedBy]). Beginner comes quickly (10
 * sets), so a new athlete earns a first badge in the first weeks; Intermediate takes 20 and the rest 30 each. Elite is
 * ultra hard but possible.
 */
enum class SkillLevel(val sets: Int) {
    NONE(0), BEGINNER(10), INTERMEDIATE(20), ADVANCED(30), MASTER(30), ELITE(30);

    val next: SkillLevel? get() = entries.getOrNull(ordinal + 1)
}

/**
 * A skill's standard. [seconds]: its sets are holds measured in seconds, else clean reps (negatives are progressions,
 * not skills). [perSide]: one side at a time, the weaker side counting. [grade]: how hard the movement is, 1 (easy) to
 * 5 (extreme), as a street-workout coach grades it. [targets]: the reps or seconds a
 * set needs for Beginner, Intermediate, Advanced, Master and Elite. [window]: the program's time is a practice window of
 * attempts and resets, so a set counts the longest clean hold the athlete logs, never the window (no skill is one since
 * round 9, when the balance attempts left the belts).
 */
data class SkillStandard(
    val seconds: Boolean,
    val grade: Int,
    val targets: List<Int>,
    val perSide: Boolean = false,
    val window: Boolean = false,
) {
    fun target(level: SkillLevel): Int? = targets.getOrNull(level.ordinal - SkillLevel.BEGINNER.ordinal)
}

/**
 * Every belted skill's standard: 37 skills (curriculum round 9, the owner's choice of 29 October 2026). A target is a
 * working set, what a trained athlete does for one set inside a class with one or two reps in reserve, never a single
 * all-out max: the pull-up asks 3, 6, 10, 15 and 20 reps, the push-up 6, 12, 20, 30 and 40, the dip 4, 10, 15, 20 and
 * 25, and the dead hang 30 s to 90 s. The rarest rungs stay realistic, so their top is reachable inside the program: the
 * one-arm pull-up asks 1, 1, 2, 2 and 3 reps a side, the full planche 2, 2, 3, 3 and 4 s, the strict muscle-up 1 to 5
 * reps. Where two levels share a target they differ only by their sets: Beginner takes 10 and Intermediate 20, while
 * Advanced and Master, at 30 each, come together. Each skill's Elite target is reached once inside the program, by the
 * end of Level 10, or the program gives its Elite through a harder rung of its ladder (the tuck and advanced tuck back
 * levers, through the full back lever); its 30 Elite sets come from repeated blocks and the athlete's own training. A
 * progression peaks while it is still trained, never as a record beside the harder rung. Everything else in the
 * classes (chin-ups, knee raises, Nordic curls, the one-leg levers, balance attempts and the like) is a progression or
 * a movement and earns no belt. Grades: 1 easy (the dead hang, push-up, squat and hollow body), 2 moderate (the first
 * strict benchmarks, pull-up and dip), 3 hard, 4 very hard (the strict muscle-up, full back lever and full flag), 5
 * extreme (the full front lever, straddle planche and, the hardest of all, the full planche and the one-arm pull-up).
 */
object SkillStandards {
    private fun reps(grade: Int, vararg targets: Int) = SkillStandard(false, grade, targets.toList())
    private fun repsSide(grade: Int, vararg targets: Int) = SkillStandard(false, grade, targets.toList(), perSide = true)
    private fun hold(grade: Int, vararg targets: Int) = SkillStandard(true, grade, targets.toList())
    private fun holdSide(grade: Int, vararg targets: Int) = SkillStandard(true, grade, targets.toList(), perSide = true)

    val ALL: Map<String, SkillStandard> = mapOf(
        // Pull-up
        "dead_hang" to hold(1, 30, 45, 60, 75, 90),
        "pullup" to reps(2, 3, 6, 10, 15, 20),
        "archer_pullup" to repsSide(3, 2, 3, 4, 6, 8),
        "one_arm_pullup" to repsSide(5, 1, 1, 1, 2, 3),
        // Muscle-up
        "strict_muscleup" to reps(4, 1, 2, 3, 4, 5),
        // Front lever
        "tuck_front_hold" to hold(2, 5, 8, 12, 16, 20),
        "advanced_tuck_front" to hold(3, 5, 8, 10, 12, 15),
        "straddle_front" to hold(4, 3, 5, 6, 8, 10),
        "full_front" to hold(5, 2, 3, 4, 6, 8),
        // Back lever
        "tuck_back_hold" to hold(2, 5, 8, 12, 16, 20),
        "advanced_tuck_back" to hold(3, 5, 8, 10, 15, 20),
        "straddle_back" to hold(4, 3, 5, 8, 10, 12),
        "full_back" to hold(4, 3, 5, 8, 10, 12),
        // Dip
        "parallel_dip" to reps(2, 4, 10, 15, 20, 25),
        // Push-up
        "pushup" to reps(1, 6, 12, 20, 30, 40),
        "one_arm_pushup" to repsSide(4, 1, 3, 5, 7, 10),
        // Handstand push-up
        "pike_pushup" to reps(2, 6, 8, 10, 12, 15),
        "wall_hspu" to reps(4, 2, 4, 6, 8, 10),
        "deficit_wall_hspu" to reps(4, 2, 3, 5, 6, 8),
        // Planche
        "pseudo_planche_pushup" to reps(3, 5, 8, 10, 12, 15),
        "tuck_planche" to hold(3, 5, 8, 10, 12, 15),
        "advanced_tuck_planche" to hold(4, 3, 5, 8, 10, 12),
        "straddle_planche" to hold(5, 2, 3, 4, 5, 6),
        "full_planche" to hold(5, 2, 2, 2, 3, 4),
        // Handstand: the freestanding hold is a plain hold, its longest clean balance.
        "wall_handstand" to hold(2, 20, 30, 45, 60, 75),
        "freestanding_handstand" to hold(4, 5, 10, 20, 30, 45),
        // Legs (the pistol squat family)
        "squat" to reps(1, 15, 20, 25, 30, 40),
        "pistol_squat" to repsSide(3, 2, 4, 6, 8, 10),
        // Toes to bar
        "hanging_leg_raise" to reps(2, 5, 8, 10, 12, 15),
        "toes_to_bar" to reps(3, 3, 6, 8, 12, 15),
        // L-sit
        "lsit" to hold(3, 5, 10, 15, 20, 30),
        "v_sit" to hold(4, 2, 3, 4, 5, 6),
        // Dragon flag: the hollow body is its first rung.
        "hollow_body_hold" to hold(1, 20, 30, 40, 50, 60),
        "dragon_flag" to reps(4, 1, 3, 5, 6, 8),
        // Human flag
        "tuck_flag" to holdSide(3, 5, 8, 10, 12, 15),
        "straddle_flag" to holdSide(4, 2, 4, 6, 8, 10),
        "full_flag" to holdSide(4, 2, 3, 4, 5, 6),
    )

    /** A rung without its own standard (one that joined a ladder by its regression): moderate, five to thirty reps. */
    val DEFAULT = reps(2, 5, 10, 15, 20, 30)

    fun of(skillId: String): SkillStandard = ALL[skillId] ?: DEFAULT
}
