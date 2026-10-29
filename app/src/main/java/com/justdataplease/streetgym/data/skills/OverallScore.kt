package com.justdataplease.streetgym.data.skills

import kotlin.math.roundToInt

/**
 * One family's part of the overall score: its [best] skill, the one whose level earns the most, and the [points] it
 * earns.
 */
data class FamilyScore(val family: SkillFamily, val best: SkillStatus?, val points: Double) {
    val ceiling: Int get() = family.ceiling
}

/**
 * The overall score, 0 to 1000, weighted by how hard each skill is. Each family counts its best skill: a fifth of the
 * skill's value per level ([SKILL_VALUES]: a strict pull-up is 10, a full front lever 100, a full planche 200), so
 * Elite earns the whole value. The families' points add up and scale to 1000 against the sum of every family's top
 * value and the retired families' ([RETIRED_CEILINGS]). The app shows it out of 100 with one decimal (425 reads 42.5);
 * the leaderboard keeps the 1000 scale.
 */
class OverallScore private constructor(val families: List<FamilyScore>) {
    /**
     * M: every family's top value added up, with the retired families' while there are any. With none (round 11), Elite
     * of every family's top rung is the whole 1000.
     */
    val max: Int = scoreMax(families.map { it.family })
    val score: Int = scaled(families.sumOf { it.points }).coerceIn(0, MAX)
    val tier: Tier get() = Tier.of(score)

    /** Raw family values on the 1000 scale. */
    fun scaled(points: Double): Int = if (max == 0) 0 else (MAX * points / max).roundToInt()

    /** A group's points and its ceiling, both on the 1000 scale. */
    fun group(group: SkillGroup): Pair<Int, Int> = families.filter { it.family.spec.group == group }
        .let { scaled(it.sumOf { family -> family.points }) to scaled(it.sumOf { family -> family.ceiling.toDouble() }) }

    enum class Tier(val floor: Int) {
        BEGINNER(0), NOVICE(75), INTERMEDIATE(200), ADVANCED(425), ELITE(650), WORLD_CLASS(850);

        val next: Tier? get() = entries.getOrNull(ordinal + 1)

        companion object {
            fun of(score: Int): Tier = entries.last { score >= it.floor }
        }
    }

    companion object {
        const val MAX = 1000

        /** The score's M for [families]: their top values and the retired families' ([RETIRED_CEILINGS]). */
        fun scoreMax(families: List<SkillFamily>): Int = families.sumOf { it.ceiling } + RETIRED_CEILINGS.values.sum()

        /**
         * Families that left the belts but that the deployed leaderboard function still weighs (FAMILY_CEILINGS in
         * firebase/functions/boards.py): the function checks each overall against the sum of all its ceilings, so their
         * top values stay in [max] until it is redeployed without them. Then remove them here in the same release.
         * Round 9 retired the Nordic curl family (top value 24); round 11 needs a deploy for its new families anyway, so
         * the function drops it (score version 3) and nothing is retired.
         */
        val RETIRED_CEILINGS: Map<String, Int> = emptyMap()

        fun of(families: List<Pair<SkillFamily, List<SkillStatus>>>): OverallScore = OverallScore(families.map { (family, statuses) ->
            val best = statuses.map { it to points(it) }.filter { it.second > 0 }.maxByOrNull { it.second }
            FamilyScore(family, best?.first, best?.second ?: 0.0)
        })

        /** A fifth of the skill's value per level: Elite earns all of it, a skill not started nothing. */
        fun points(status: SkillStatus): Double =
            status.skill.value * status.level.ordinal.toDouble() / SkillLevel.ELITE.ordinal

        /**
         * What each rung is worth at Elite, anchored on a strict pull-up at 10. Lever and planche shapes step up about
         * 1.5 times each, with the biggest jumps from straddle to full planche and to the full one-arm pull-up; a back
         * lever shape is worth about half the same front lever shape, and the flag sits between them. Each family's
         * top value is its weight in the score, and must not exceed the leaderboard function's ceiling for it
         * (FAMILY_CEILINGS in boards.py; SkillBookTest checks they agree).
         *
         * Round 9 kept the values of the skills that stayed. Two exceptions: the parallel dip, now the dip family's
         * only rung, takes the family's old top value (12, the straight-bar dip's), as the deficit wall handstand push-up
         * took the freestanding negative's 55; and the two new skills, the squat and the hollow body, are small.
         *
         * Round 11 added three: the one-arm handstand at 110, the one-arm pull-up's value, which makes it the handstand
         * family's top; the Hefesto at 50, a full back lever's worth; and the shrimp squat at 14, a little above the
         * pistol.
         */
        val SKILL_VALUES: Map<String, Int> = mapOf(
            // Pull
            "dead_hang" to 2, "pullup" to 10, "archer_pullup" to 22, "one_arm_pullup" to 110,
            "strict_muscleup" to 32,
            "tuck_front_hold" to 15, "advanced_tuck_front" to 25, "straddle_front" to 65, "full_front" to 100,
            "tuck_back_hold" to 11, "advanced_tuck_back" to 17, "straddle_back" to 36, "full_back" to 50,
            "hefesto" to 50,
            // Push
            "parallel_dip" to 12,
            "pushup" to 5, "one_arm_pushup" to 24,
            "pike_pushup" to 7, "wall_hspu" to 24, "deficit_wall_hspu" to 55,
            "pseudo_planche_pushup" to 16, "tuck_planche" to 25, "advanced_tuck_planche" to 45,
            "straddle_planche" to 110, "full_planche" to 200,
            "wall_handstand" to 6, "freestanding_handstand" to 30, "one_arm_handstand" to 110,
            // Legs
            "squat" to 4, "pistol_squat" to 12, "shrimp_squat" to 14,
            // Core
            "hanging_leg_raise" to 6, "toes_to_bar" to 10,
            "lsit" to 10, "v_sit" to 30,
            "hollow_body_hold" to 3, "dragon_flag" to 18,
            "tuck_flag" to 15, "straddle_flag" to 35, "full_flag" to 60,
        )
    }
}
