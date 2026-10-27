package com.justdataplease.streetgym.data.skills

import kotlin.math.roundToInt

/** How a rung is tested: hold for [amount] seconds, do [amount] clean reps, or lower one rep over [amount] seconds. */
data class SkillTest(val kind: Kind, val amount: Int, val perSide: Boolean = false) {
    enum class Kind { HOLD, REPS, NEGATIVE }
}

/**
 * One family's part of the overall score. [base]: its hardest achieved rung. [next]: the rung of the smallest value
 * above it, which training earns partial credit toward. [points]: the base value plus that partial credit.
 */
data class FamilyScore(val family: SkillFamily, val base: SkillStatus?, val next: SkillStatus?, val points: Double) {
    val ceiling: Int get() = family.ceiling
}

/**
 * The overall score, 0 to 1000, weighted by how hard each skill is. Each family counts only its hardest achieved
 * rung, at that rung's value ([SKILL_VALUES]: a strict pull-up is 10, a full front lever 100, a full planche 200),
 * plus half the gap to the next rung in proportion to how far the athlete has trained it. The families' values add
 * up and scale to 1000 against the sum of every family's top value. Training alone never reaches the next value:
 * rungs worth [TEST_GATE] or more count only after a passed test.
 */
class OverallScore private constructor(val families: List<FamilyScore>) {
    /** M: every family's top value added up; 1000 overall means every family at its top rung. */
    val max: Int = families.sumOf { it.ceiling }
    val score: Int = scaled(families.sumOf { it.points }).coerceIn(0, MAX)
    val tier: Tier get() = Tier.of(score)

    /** Raw family values on the 1000 scale. */
    fun scaled(points: Double): Int = if (max == 0) 0 else (MAX * points / max).roundToInt()

    /** A group's points and its ceiling, both on the 1000 scale. */
    fun group(group: SkillGroup): Pair<Int, Int> = families.filter { it.family.spec.group == group }
        .let { scaled(it.sumOf { family -> family.points }) to scaled(it.sumOf { family -> family.ceiling.toDouble() }) }

    /** What achieving [skill] adds to the overall score now: nothing once its family holds it or a harder rung. */
    fun gain(skill: Skill): Int {
        val family = families.firstOrNull { it.family.skills.contains(skill) } ?: return 0
        return (scaled(skill.value.toDouble()) - scaled(family.points)).coerceAtLeast(0)
    }

    /** The trained rung whose test would add the most, or null when no trained rung waits for a test. */
    fun bestTest(): Pair<SkillStatus, Int>? = families.mapNotNull { it.next }.filter { it.trained }
        .map { it to gain(it.skill) }.filter { it.second > 0 }.maxByOrNull { it.second }

    enum class Tier(val floor: Int) {
        BEGINNER(0), NOVICE(75), INTERMEDIATE(200), ADVANCED(425), ELITE(650), WORLD_CLASS(850);

        val next: Tier? get() = entries.getOrNull(ordinal + 1)

        companion object {
            fun of(score: Int): Tier = entries.last { score >= it.floor }
        }
    }

    companion object {
        const val MAX = 1000
        /** Rungs worth this much or more are achieved only by a passed test, never by attendance. */
        const val TEST_GATE = 15
        /** Training a family's next rung earns at most this share of the gap to it. */
        const val PARTIAL = 0.5

        fun of(families: List<Pair<SkillFamily, List<SkillStatus>>>): OverallScore = OverallScore(families.map { (family, statuses) ->
            val base = statuses.filter { it.achieved }.maxByOrNull { it.skill.value }
            val floor = base?.skill?.value ?: 0
            val above = statuses.filter { it.skill.value > floor }
            val step = above.minOfOrNull { it.skill.value }
            // Rungs sharing the smallest value above: the one trained furthest counts.
            val next = above.filter { it.skill.value == step }.maxByOrNull { it.own }
            val partial = next?.let { PARTIAL * it.own / 100.0 * (it.skill.value - floor) } ?: 0.0
            FamilyScore(family, base, next, floor + partial)
        })

        /**
         * What achieving each rung is worth, anchored on a strict pull-up at 10. Lever and planche shapes step up about
         * 1.5 times each, with the biggest jumps from straddle to full planche and to the full one-arm pull-up; a back
         * lever shape is worth about half the same front lever shape, and the flag sits between them. Each family's
         * top value is its weight in the score.
         */
        val SKILL_VALUES: Map<String, Int> = mapOf(
            "assisted_pullup" to 3, "eccentric_pullup" to 5, "chinup" to 8, "pullup" to 10, "archer_pullup" to 22,
            "typewriter_pullup" to 30, "assisted_one_arm_pullup" to 45, "one_arm_pullup_negative" to 70, "one_arm_pullup" to 110,
            "straight_bar_support" to 2, "low_bar_transition" to 5, "jump_muscleup" to 9, "chest_to_bar" to 14,
            "negative_muscleup" to 16, "band_muscleup" to 18, "strict_muscleup" to 32,
            "tuck_front_row" to 6, "tuck_front_hold" to 15, "advanced_tuck_front" to 25, "one_leg_front" to 40,
            "straddle_front" to 65, "full_front" to 100,
            "skin_cat_partial" to 3, "german_hang" to 5, "tuck_back_entry" to 7, "tuck_back_hold" to 11,
            "advanced_tuck_back" to 17, "one_leg_back" to 25, "straddle_back" to 36, "full_back" to 50,
            "support_hold" to 2, "assisted_dip" to 3, "eccentric_dip" to 4, "parallel_dip" to 8, "straight_bar_dip" to 12,
            "wall_pushup" to 1, "incline_pushup" to 2, "pushup" to 5, "pause_pushup" to 6, "close_pushup" to 7,
            "archer_pushup" to 14, "one_arm_pushup" to 24,
            "pike_pushup" to 7, "elevated_pike_pushup" to 11, "wall_hspu_negative" to 15, "wall_hspu" to 24,
            "deficit_wall_hspu" to 35, "freestanding_hspu_negative" to 55,
            "elevated_plank" to 2, "planche_lean" to 6, "frog_stand" to 6, "pseudo_planche_pushup" to 16, "tuck_planche" to 25,
            "advanced_tuck_planche" to 45, "straddle_planche" to 110, "full_planche" to 200,
            "overhead_line" to 1, "pike_handstand_hold" to 3, "wall_handstand" to 6, "wall_kickup" to 6, "handstand_exit" to 6,
            "handstand_toe_pull" to 10, "handstand_balance" to 20, "freestanding_handstand" to 30,
            "split_squat" to 2, "assisted_pistol_squat" to 6, "pistol_squat" to 12,
            "sliding_leg_curl" to 4, "nordic_negative" to 12, "nordic_curl" to 24,
            "knee_raise" to 2, "hanging_leg_raise" to 6, "toes_to_bar" to 10,
            "tuck_lsit" to 3, "one_leg_lsit" to 6, "lsit" to 10, "v_sit" to 30,
            "candlestick" to 2, "bent_dragon_negative" to 5, "one_leg_dragon_negative" to 9, "full_dragon_negative" to 12,
            "dragon_flag" to 18,
            "kneeling_side_plank" to 1, "flag_ground_support" to 4, "assisted_tuck_flag" to 8, "tuck_flag" to 15,
            "straddle_flag" to 35, "full_flag" to 60,
        )

        private fun hold(seconds: Int, perSide: Boolean = false) = SkillTest(SkillTest.Kind.HOLD, seconds, perSide)
        private fun reps(count: Int, perSide: Boolean = false) = SkillTest(SkillTest.Kind.REPS, count, perSide)

        /**
         * The standard a gated rung is tested at, as judges credit statics: held still with a clean line, 10 s for tuck
         * shapes, 6 s for advanced tuck and one-leg shapes and 3 s for straddle and full shapes. Dynamic rungs take
         * three clean reps, the strict muscle-up and nordic curl one; one-arm work one per side, negatives lowered
         * over at least 4 s (3 s for the freestanding handstand push-up).
         */
        val SKILL_TESTS: Map<String, SkillTest> = mapOf(
            "archer_pullup" to reps(3, perSide = true), "typewriter_pullup" to reps(3),
            "assisted_one_arm_pullup" to reps(1, perSide = true),
            "one_arm_pullup_negative" to SkillTest(SkillTest.Kind.NEGATIVE, 4, perSide = true),
            "one_arm_pullup" to reps(1, perSide = true),
            "negative_muscleup" to reps(3), "band_muscleup" to reps(3), "strict_muscleup" to reps(1),
            "tuck_front_hold" to hold(10), "advanced_tuck_front" to hold(6), "one_leg_front" to hold(6, perSide = true),
            "straddle_front" to hold(3), "full_front" to hold(3),
            "advanced_tuck_back" to hold(6), "one_leg_back" to hold(6, perSide = true), "straddle_back" to hold(3),
            "full_back" to hold(3),
            "one_arm_pushup" to reps(1, perSide = true),
            "wall_hspu_negative" to reps(3), "wall_hspu" to reps(3), "deficit_wall_hspu" to reps(3),
            "freestanding_hspu_negative" to SkillTest(SkillTest.Kind.NEGATIVE, 3),
            "pseudo_planche_pushup" to reps(3), "tuck_planche" to hold(10), "advanced_tuck_planche" to hold(6),
            "straddle_planche" to hold(3), "full_planche" to hold(3),
            "handstand_balance" to hold(10), "freestanding_handstand" to hold(30),
            "nordic_curl" to reps(1), "v_sit" to hold(5), "dragon_flag" to reps(3),
            "tuck_flag" to hold(10, perSide = true), "straddle_flag" to hold(3, perSide = true),
            "full_flag" to hold(3, perSide = true),
        )
    }
}
