package com.justdataplease.streetgym.data.skills

import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/** One entry of the curriculum movement library, as far as skills need it. */
data class SkillMovement(
    val id: String,
    val name: String,
    val category: String,
    val patterns: Set<String>,
    val regressionId: String?,
)

/** One prescribed exercise in a session. Readiness checks are single test sets. */
data class SkillStep(val movementId: String, val sets: Int, val readinessCheck: Boolean = false)

/** One scheduled session, in the order the program assigns them. */
data class SkillProgram(
    val id: String,
    val level: Int,
    val week: Int?,
    val day: Int?,
    val steps: List<SkillStep>,
)

enum class SkillGroup { PULL, PUSH, LEGS, CORE }

/**
 * A ladder of skills. [ladder] is ordered easiest to hardest, as a coach teaches it; [foundations] are the basic
 * strength movements that count a little toward the family; [tags] let new movements whose regression sits in this
 * ladder join it automatically.
 */
data class SkillFamilySpec(
    val id: String,
    val name: String,
    val group: SkillGroup,
    val ladder: List<String>,
    val foundations: List<String>,
    val tags: Set<String>,
)

data class Skill(
    val id: String,
    val name: String,
    val rung: Int,
    /** Points needed: what the schedule delivers toward this skill before it first asks for it. */
    val threshold: Int,
    /** First session that prescribes the skill, or null when the program never does. */
    val expected: SkillProgram?,
    val beyondProgram: Boolean,
    /** 1 (beginner) to 5 (elite): the level where the program first teaches it, never below the rung beneath it. */
    val difficulty: Int,
    internal val weights: Map<String, Double>,
)

data class SkillFamily(val spec: SkillFamilySpec, val skills: List<Skill>)

data class SkillStatus(
    val skill: Skill,
    val points: Int,
    val unlocked: Boolean,
    /** Contributing movements with their share of this skill's 100 points, largest first. */
    val sources: List<Pair<String, Int>>,
) {
    /** Skill points on a 0-100 scale: 100 is unlocked. */
    val score: Int get() = if (unlocked) 100 else (points * 100 / skill.threshold).coerceIn(0, 99)
}

data class SkillProgress(val families: List<Pair<SkillFamily, List<SkillStatus>>>) {
    val statuses: List<SkillStatus> get() = families.flatMap { it.second }
    val unlockedCount: Int get() = statuses.count { it.unlocked }
    val totalCount: Int get() = statuses.size

    /**
     * A group's score, 0-100: the average skill score of the skills the program teaches in it. Finishing the
     * program brings every group to 100; skills the program never prescribes are shown but not counted.
     */
    fun groupScore(group: SkillGroup): Int {
        val taught = families.filter { (family, _) -> family.spec.group == group }
            .flatMap { it.second }.filter { it.skill.expected != null }
        return if (taught.isEmpty()) 0 else taught.sumOf { it.score } / taught.size
    }

    /** Locked skills with some points, closest to unlocking first. */
    fun closest(limit: Int): List<SkillStatus> =
        statuses.filter { !it.unlocked && it.points > 0 }.sortedByDescending { it.score }.take(limit)
}

/**
 * Skills built from the curriculum itself. Every completed set earns points; points flow up the ladder; and each
 * target equals what the schedule delivers before it first prescribes the skill, so following the program brings a
 * skill to 100 on the day the program introduces it.
 */
class SkillBook private constructor(
    val families: List<SkillFamily>,
    private val movements: Map<String, SkillMovement>,
    private val programs: Map<String, SkillProgram>,
) {
    /** The last result: Skills redraws on every tab and filter tap with the same days, and the walk isn't free. */
    @Volatile private var last: Pair<Set<String>, SkillProgress>? = null

    fun progress(completedProgramIds: Set<String>): SkillProgress {
        last?.takeIf { it.first == completedProgramIds }?.let { return it.second }
        return compute(completedProgramIds).also { last = completedProgramIds.toSet() to it }
    }

    private fun compute(completedProgramIds: Set<String>): SkillProgress {
        val done = completedProgramIds.mapNotNull(programs::get)
        val result = families.map { family ->
            val raw = family.skills.map { skill ->
                val parts = HashMap<String, Double>()
                for (program in done) for (step in program.steps) {
                    val weight = skill.weights[step.movementId] ?: continue
                    parts[step.movementId] = (parts[step.movementId] ?: 0.0) + step.sets * setPoints(step.movementId) * weight
                }
                val points = parts.values.sum().roundToInt()
                val sources = parts.entries.sortedByDescending { it.value }.map { (id, value) ->
                    (movements[id]?.name ?: id) to (value * 100 / skill.threshold).roundToInt().coerceAtMost(100)
                }
                SkillStatus(skill, points, points >= skill.threshold, sources)
            }
            // Unlocking a harder rung also unlocks the easier ones below it.
            var higherUnlocked = false
            val statuses = raw.asReversed().map { status ->
                val unlocked = status.unlocked || higherUnlocked
                higherUnlocked = unlocked
                status.copy(unlocked = unlocked)
            }.asReversed()
            family to statuses
        }
        return SkillProgress(result)
    }

    private fun setPoints(movementId: String): Double = setPoints(movements[movementId])

    companion object {
        const val POINTS_PER_SET = 10.0
        private const val BEYOND_STEP = 1.6
        private const val MIN_THRESHOLD = 40

        private val TIERS = mapOf(
            "strength" to 1.25, "skill_foundation" to 1.25, "skill_drill" to 1.25, "power" to 1.25,
            "advanced_fundamental" to 1.5, "skill" to 1.5, "skill_strength" to 1.5,
            "advanced_strength" to 2.0, "advanced_skill" to 2.0, "mastery_skill" to 2.5,
        )

        private fun setPoints(movement: SkillMovement?): Double =
            POINTS_PER_SET * (TIERS[movement?.category] ?: 1.0)

        fun build(
            movements: Map<String, SkillMovement>,
            schedule: List<SkillProgram>,
            specs: List<SkillFamilySpec> = DEFAULT_FAMILIES,
        ): SkillBook {
            val ladders = resolveLadders(movements, specs)
            val families = specs.mapNotNull { spec ->
                val ladder = ladders[spec.id].orEmpty()
                if (ladder.isEmpty()) return@mapNotNull null
                var previous = 0
                var previousDifficulty = 1
                val skills = ladder.mapIndexed { rung, id ->
                    val weights = HashMap<String, Double>()
                    weights[id] = 1.0
                    for (k in 1..rung) weights[ladder[rung - k]] = max(0.5.pow(k), 0.125)
                    for (base in spec.foundations) {
                        if (base in movements && base !in weights) weights[base] = if (rung < 2) 0.25 else 0.125
                    }
                    val (raw, expected, beyond) = calibrate(id, weights, movements, schedule, previous)
                    // A readiness check completes the skill with its own test set; a trained skill reaches 100 on
                    // the class that first prescribes it, or a little later for the very first classes.
                    val threshold = when {
                        expected != null && beyond -> raw.coerceAtLeast(1)
                        expected != null -> maxOf(raw, MIN_THRESHOLD)
                        else -> raw
                    }
                    previous = threshold
                    previousDifficulty = maxOf(previousDifficulty, difficulty(movements.getValue(id), expected))
                    Skill(id, movements.getValue(id).name, rung, threshold, expected, beyond, previousDifficulty, weights)
                }
                SkillFamily(spec, skills)
            }
            return SkillBook(families, movements, schedule.associateBy { it.id })
        }

        /** Beginner (1) to elite (5) by the level that first teaches a movement; advanced categories start higher. */
        internal fun difficulty(movement: SkillMovement, expected: SkillProgram?): Int {
            val byLevel = when (expected?.level) {
                null -> 5
                1 -> 1
                2 -> 2
                3 -> 3
                4, 5 -> 4
                else -> 5
            }
            val floor = when (movement.category) {
                "mastery_skill" -> 5
                "advanced_skill", "advanced_strength", "advanced_fundamental" -> 3
                else -> 1
            }
            return maxOf(byLevel, floor)
        }

        /** Target for one skill: points the schedule delivers before (and on) the first session that prescribes it. */
        private fun calibrate(
            id: String,
            weights: Map<String, Double>,
            movements: Map<String, SkillMovement>,
            schedule: List<SkillProgram>,
            previous: Int,
        ): Triple<Int, SkillProgram?, Boolean> {
            var delivered = 0.0
            var firstCheck: Pair<SkillProgram, Double>? = null
            for (program in schedule) {
                for (step in program.steps) {
                    if (step.readinessCheck) continue
                    val weight = weights[step.movementId] ?: continue
                    delivered += step.sets * setPoints(movements[step.movementId]) * weight
                }
                if (program.steps.any { it.movementId == id && !it.readinessCheck }) {
                    return Triple(delivered.roundToInt(), program, false)
                }
                if (firstCheck == null && program.steps.any { it.movementId == id && it.readinessCheck }) {
                    firstCheck = program to delivered + setPoints(movements[id])
                }
            }
            // Only offered as a readiness check: target where the program first tests it.
            firstCheck?.let { (program, points) -> return Triple(points.roundToInt(), program, true) }
            val base = if (previous > 0) previous else 200
            return Triple((base * BEYOND_STEP).roundToInt(), null, true)
        }

        /** Curated ladders, filtered to movements that exist, plus new rungs that regress into a ladder. */
        private fun resolveLadders(
            movements: Map<String, SkillMovement>,
            specs: List<SkillFamilySpec>,
        ): Map<String, List<String>> {
            val ladders = specs.associate { spec -> spec.id to spec.ladder.filter(movements::containsKey).toMutableList() }
            val assigned = ladders.values.flatten().toMutableSet()
            var changed = true
            while (changed) {
                changed = false
                for (movement in movements.values) {
                    if (movement.id in assigned) continue
                    val regression = movement.regressionId ?: continue
                    val spec = specs.firstOrNull { spec ->
                        regression in ladders.getValue(spec.id) && movement.patterns.any(spec.tags::contains)
                    } ?: continue
                    val ladder = ladders.getValue(spec.id)
                    ladder.add(ladder.indexOf(regression) + 1, movement.id)
                    assigned += movement.id
                    changed = true
                }
            }
            return ladders
        }

        val DEFAULT_FAMILIES = listOf(
            SkillFamilySpec("pullup", "Pull-up", SkillGroup.PULL,
                listOf("assisted_pullup", "eccentric_pullup", "chinup", "pullup", "archer_pullup", "typewriter_pullup",
                    "assisted_one_arm_pullup", "one_arm_pullup_negative"),
                listOf("inverted_row", "active_hang", "scapular_pull"), setOf("vertical_pull")),
            SkillFamilySpec("muscleup", "Muscle-up", SkillGroup.PULL,
                listOf("straight_bar_support", "low_bar_transition", "chest_to_bar", "jump_muscleup", "negative_muscleup",
                    "band_muscleup", "strict_muscleup"),
                listOf("pullup", "parallel_dip", "straight_bar_dip", "pullover_lowbar"), setOf("muscle_up")),
            SkillFamilySpec("front_lever", "Front lever", SkillGroup.PULL,
                listOf("tuck_front_row", "tuck_front_hold", "advanced_tuck_front", "one_leg_front", "straddle_front", "full_front"),
                listOf("inverted_row", "feet_elevated_row", "scapular_pull", "hollow_body_hold"), setOf("front_lever")),
            SkillFamilySpec("back_lever", "Back lever", SkillGroup.PULL,
                listOf("skin_cat_partial", "german_hang", "tuck_back_entry", "tuck_back_hold", "advanced_tuck_back",
                    "one_leg_back", "straddle_back", "full_back"),
                listOf("active_hang", "arch_hold", "chinup"), setOf("back_lever")),
            SkillFamilySpec("dip", "Dip", SkillGroup.PUSH,
                listOf("support_hold", "assisted_dip", "eccentric_dip", "parallel_dip", "straight_bar_dip"),
                listOf("foot_assisted_support", "close_pushup"), setOf("bar_support")),
            SkillFamilySpec("pushup", "Push-up", SkillGroup.PUSH,
                listOf("wall_pushup", "incline_pushup", "pushup", "pause_pushup", "close_pushup", "archer_pushup"),
                listOf("plank", "scapular_pushup"), setOf("horizontal_push")),
            SkillFamilySpec("hspu", "Handstand push-up", SkillGroup.PUSH,
                listOf("pike_pushup", "elevated_pike_pushup", "wall_hspu_negative", "wall_hspu", "deficit_wall_hspu",
                    "freestanding_hspu_negative"),
                listOf("pushup"), setOf("overhead_press")),
            SkillFamilySpec("planche", "Planche", SkillGroup.PUSH,
                listOf("elevated_plank", "planche_lean", "frog_stand", "tuck_planche", "advanced_tuck_planche",
                    "straddle_planche", "full_planche"),
                listOf("pseudo_planche_pushup", "support_hold", "straight_bar_support"), setOf("planche")),
            SkillFamilySpec("handstand", "Handstand", SkillGroup.PUSH,
                listOf("overhead_line", "pike_handstand_hold", "wall_handstand", "handstand_toe_pull", "handstand_exit",
                    "handstand_balance"),
                listOf("pike_pushup", "wall_slide"), setOf("handstand")),
            SkillFamilySpec("pistol", "Pistol squat", SkillGroup.LEGS,
                listOf("split_squat", "bulgarian_split_squat", "assisted_pistol_squat", "pistol_squat"),
                listOf("squat", "box_squat", "reverse_lunge", "step_up", "cossack_squat", "deep_squat_hold"), setOf("single_leg_squat")),
            SkillFamilySpec("nordic", "Nordic curl", SkillGroup.LEGS,
                listOf("sliding_leg_curl", "nordic_negative", "nordic_curl"),
                listOf("glute_bridge", "single_leg_bridge", "single_leg_rdl"), setOf("knee_flexion")),
            SkillFamilySpec("hanging_core", "Toes to bar", SkillGroup.CORE,
                listOf("knee_raise", "hanging_leg_raise", "toes_to_bar"),
                listOf("lying_leg_raise", "hollow_hold", "seated_pike_lift"), setOf("hanging_core")),
            SkillFamilySpec("lsit", "L-sit", SkillGroup.CORE,
                listOf("tuck_lsit", "one_leg_lsit", "lsit"),
                listOf("support_hold", "knee_raise", "seated_pike_lift"), setOf("l_sit")),
            SkillFamilySpec("dragon_flag", "Dragon flag", SkillGroup.CORE,
                listOf("candlestick", "bent_dragon_negative", "one_leg_dragon_negative", "full_dragon_negative", "dragon_flag"),
                listOf("hollow_body_hold", "lying_leg_raise"), setOf("dragon_flag")),
            SkillFamilySpec("human_flag", "Human flag", SkillGroup.CORE,
                listOf("kneeling_side_plank", "flag_ground_support", "assisted_tuck_flag", "tuck_flag", "straddle_flag", "full_flag"),
                listOf("straight_side_plank", "pullup", "pike_pushup"), setOf("human_flag")),
        )
    }
}
