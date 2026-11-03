package com.justdataplease.streetgym.data.skills

/**
 * An athlete's level in one skill, like belts: each takes [sets] sets at its target or more ([SkillStandard.target]),
 * and a set over a higher target counts for the lower ones too, so 30 sets at the Elite target earn all five. A set on a
 * harder rung counts for the easier rungs of its ladder, and a level held on a harder rung holds on them
 * ([SkillStatus.carriedBy]). Each level takes five sets more than the one below (review 11, the owner): Beginner 10, so a
 * new athlete earns a first badge in the first weeks, then 15, 20, 25 and Elite 30. The classes take every skill to
 * Master; Elite is ultra hard but possible.
 */
enum class SkillLevel(val sets: Int) {
    NONE(0), BEGINNER(10), INTERMEDIATE(15), ADVANCED(20), MASTER(25), ELITE(30);

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
 * Every belted skill's standard: 40 skills (curriculum round 11, which added the one-arm handstand, the shrimp squat and
 * the Hefesto to round 9's 37). A target is a working set, what a trained athlete does for one set inside a class with
 * one or two reps in reserve, never a single all-out max: the pull-up asks 3, 6, 10, 15 and 20 reps, the push-up 6, 12,
 * 20, 30 and 40, the dip 4, 10, 15, 20 and 25, and the dead hang 30 s to 90 s. The rarest rungs stay realistic, so their
 * top is reachable inside the program: the one-arm pull-up asks 1, 1, 1, 1 and 2 reps a side, the Hefesto 1, 1, 1, 1 and
 * 2, the full planche 2, 3, 4, 5 and 6 s, the one-arm handstand 3 to 7 s a side and the strict muscle-up 1 to 7 reps.
 * 2.9.0 (three coach reviews): no two belts of a skill share a target where a step fits (the one-arm push-up 1 to 5, the
 * full flag 2 to 6 s), the squat's Elite is 50 and the Hefesto's 2, as the one-arm pull-up's.
 * Round 11 starts the hardest statics at 3 s, the plan's minimum hold for a full or straddle shape. Where two levels share
 * a target they differ only by their sets. Review 11 (the owner) fits the belts to the classes: every skill reaches
 * Master inside the program; the basics, the core ladder and the easier rungs (through the harder rung's sets) finish
 * Elite there too, and each other ladder's top rung gets about 24 of its 30 Elite sets, the rest from a repeated block or
 * the athlete's own training. A progression peaks while it is still trained, never as a record beside the harder rung.
 * Everything else in the classes (chin-ups, knee raises,
 * Nordic curls, the one-leg levers, balance attempts, assisted shrimp squats and Hefestos and the like) is a progression
 * or a movement and earns no belt. Grades: 1 easy (the dead hang, push-up, squat and hollow body), 2 moderate (the first
 * strict benchmarks, pull-up and dip), 3 hard (the shrimp squat among them), 4 very hard (the strict muscle-up, full
 * back lever and full flag), 5 extreme (the full front lever, straddle planche, Hefesto, one-arm handstand and, the
 * hardest of all, the full planche and the one-arm pull-up).
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
        "one_arm_pullup" to repsSide(5, 1, 1, 1, 1, 2),
        // Muscle-up
        "strict_muscleup" to reps(4, 1, 2, 4, 6, 7),
        // Hefesto: from a supinated German hang behind the bar to straight-arm support above it.
        "hefesto" to reps(5, 1, 1, 1, 1, 2),
        // Front lever
        "tuck_front_hold" to hold(2, 5, 8, 12, 16, 20),
        "advanced_tuck_front" to hold(3, 5, 8, 10, 12, 15),
        "straddle_front" to hold(4, 3, 5, 7, 10, 12),
        "full_front" to hold(5, 3, 4, 5, 7, 10),
        // Back lever
        "tuck_back_hold" to hold(2, 5, 8, 12, 16, 20),
        "advanced_tuck_back" to hold(3, 5, 8, 10, 15, 20),
        "straddle_back" to hold(4, 3, 5, 8, 10, 12),
        "full_back" to hold(4, 3, 5, 8, 12, 14),
        // Dip
        "parallel_dip" to reps(2, 4, 10, 15, 20, 25),
        // Push-up
        "pushup" to reps(1, 6, 12, 20, 30, 40),
        "one_arm_pushup" to repsSide(4, 1, 2, 3, 4, 5),
        // Handstand push-up
        "pike_pushup" to reps(2, 6, 8, 10, 12, 15),
        "wall_hspu" to reps(4, 2, 4, 6, 8, 10),
        "deficit_wall_hspu" to reps(4, 2, 3, 5, 6, 7),
        // Planche
        "pseudo_planche_pushup" to reps(3, 5, 8, 10, 12, 15),
        "tuck_planche" to hold(3, 5, 8, 10, 12, 15),
        "advanced_tuck_planche" to hold(4, 5, 6, 8, 10, 12),
        "straddle_planche" to hold(5, 3, 4, 5, 6, 8),
        "full_planche" to hold(5, 2, 3, 4, 5, 6),
        // Handstand: the freestanding hold is a plain hold, its longest clean balance; the one-arm handstand is held on
        // each arm, the weaker side counting.
        "wall_handstand" to hold(2, 20, 30, 45, 60, 75),
        "freestanding_handstand" to hold(4, 5, 15, 30, 45, 60),
        "one_arm_handstand" to holdSide(5, 3, 4, 5, 6, 7),
        // Legs: the pistol squat family, and the shrimp squat (rear foot held) as a family of its own
        "squat" to reps(1, 15, 20, 30, 40, 50),
        "pistol_squat" to repsSide(3, 2, 4, 6, 8, 10),
        "shrimp_squat" to repsSide(3, 2, 3, 5, 7, 8),
        // Toes to bar
        "hanging_leg_raise" to reps(2, 5, 8, 10, 12, 15),
        "toes_to_bar" to reps(3, 3, 6, 8, 12, 15),
        // L-sit
        "lsit" to hold(3, 5, 10, 15, 20, 30),
        "v_sit" to hold(4, 3, 4, 5, 6, 7),
        // Dragon flag: the hollow body is its first rung.
        "hollow_body_hold" to hold(1, 20, 30, 40, 50, 60),
        "dragon_flag" to reps(4, 1, 3, 5, 6, 8),
        // Human flag
        "tuck_flag" to holdSide(3, 5, 8, 10, 12, 15),
        "straddle_flag" to holdSide(4, 3, 4, 6, 8, 10),
        "full_flag" to holdSide(4, 2, 3, 4, 5, 6),
    )

    /** A rung without its own standard (one that joined a ladder by its regression): moderate, five to thirty reps. */
    val DEFAULT = reps(2, 5, 10, 15, 20, 30)

    fun of(skillId: String): SkillStandard = ALL[skillId] ?: DEFAULT
}
