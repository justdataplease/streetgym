package com.justdataplease.streetgym.data.skills

import com.justdataplease.streetgym.data.progress.Intensity
import kotlin.math.max
import kotlin.math.min
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

/** One scheduled session, in the order the program assigns them. [minutes] is its planned length, when it has one. */
data class SkillProgram(
    val id: String,
    val level: Int,
    val week: Int?,
    val day: Int?,
    val steps: List<SkillStep>,
    val minutes: Int? = null,
)

/**
 * One finished class as skills credit it. [sets]: the sets ticked per movement, with a set done as the easier option
 * credited to the easier movement and readiness checks left out; null for a class saved before ticks were kept, or a
 * day the placement test placed past, whose prescription counts. [seconds]: how long the class took. [factor]: the
 * day's Easier (0.75), Normal or Harder (1.25), applied to strength movements, whose reps it scaled. [minutes]: the
 * planned length of a class outside the program (the athlete's own day), when known.
 */
data class SkillCredit(
    val programId: String,
    val sets: Map<String, Int>? = null,
    val seconds: Long = Long.MAX_VALUE,
    val factor: Double = 1.0,
    val minutes: Int? = null,
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
    /** Prep target: the weighted points the schedule delivers toward this skill up to the class that first asks for it. */
    val threshold: Int,
    /** First session that prescribes the skill, or null when the program never does. */
    val expected: SkillProgram?,
    val beyondProgram: Boolean,
    /** 1 (beginner) to 5 (elite): a coach's grade of the movement, never below the rung beneath it. */
    val difficulty: Int,
    /** What achieving the rung is worth in the overall score ([OverallScore.SKILL_VALUES]). */
    val value: Int,
    /** How the rung is tested, for rungs worth [OverallScore.TEST_GATE] or more; null when training alone achieves it. */
    val test: SkillTest?,
    internal val weights: Map<String, Double>,
)

data class SkillFamily(val spec: SkillFamilySpec, val skills: List<Skill>) {
    /** The family's top value: what holding its hardest rung is worth. */
    val ceiling: Int get() = skills.maxOfOrNull { it.value } ?: 0
}

data class SkillStatus(
    val skill: Skill,
    /** 0-50: weighted points from this rung, the rungs below it and its foundations, against its target. */
    val prep: Int,
    /** Credited classes that trained this rung itself; the first [SkillBook.PRACTICE_SESSIONS] earn practice points. */
    val sessions: Int,
    /** A logged test pass, or "Yes" on the rung's readiness check. */
    val verified: Boolean,
    /** Verified, an easy rung fully trained, or below a harder rung of the family that is achieved. */
    val achieved: Boolean,
    /** Movements that earned its prep points, with their share of its 100 points, largest first. */
    val sources: List<Pair<String, Int>>,
) {
    /** 0-50: 25 for each of the first two classes that trained the rung itself. */
    val practice: Int get() = SkillBook.PRACTICE_POINTS * min(sessions, SkillBook.PRACTICE_SESSIONS)

    /** The rung's own work, 0-100: prep plus practice, with nothing from harder rungs. */
    val own: Int get() = prep + practice

    /** Skill points on a 0-100 scale: 100 is trained or achieved. */
    val score: Int get() = if (achieved) 100 else own

    /** Fully trained but not achieved: a rung worth [OverallScore.TEST_GATE] or more waits for its test. */
    val trained: Boolean get() = !achieved && own >= 100
}

data class SkillProgress(val families: List<Pair<SkillFamily, List<SkillStatus>>>) {
    /** The weighted overall score, from the same statuses. */
    val overall: OverallScore by lazy { OverallScore.of(families) }
    val statuses: List<SkillStatus> get() = families.flatMap { it.second }
    val achievedCount: Int get() = statuses.count { it.achieved }
    val totalCount: Int get() = statuses.size

    /** Rungs the athlete has trained themselves but not achieved, closest first. */
    fun closest(limit: Int): List<SkillStatus> =
        statuses.filter { !it.achieved && it.sessions > 0 }.sortedByDescending { it.own }.take(limit)
}

/**
 * Skills built from the curriculum itself. Credited sets earn points that flow up the ladder: a rung's prep (up to 50)
 * fills as the schedule's lead-up to it is trained, and its practice (25 a class, up to 50) comes only from classes
 * that trained the rung itself. So following the program fills prep by the class that introduces a rung, and the
 * second class that trains it brings it to 100. Rungs worth [OverallScore.TEST_GATE] or more also need a passed test.
 */
class SkillBook private constructor(
    val families: List<SkillFamily>,
    private val movements: Map<String, SkillMovement>,
    private val programs: Map<String, SkillProgram>,
    private val grades: Map<String, Int>,
) {
    /** The last result: Skills redraws on every tab and filter tap with the same input, and the walk isn't free. */
    @Volatile private var last: Pair<Pair<List<SkillCredit>, Set<String>>, SkillProgress>? = null

    /** The movement a workout exercise credits: its own id when skills know it, else the source workout's name. */
    fun movementOf(movementId: String?, name: String, description: String): String? =
        movementId?.takeIf { it in movements }
            ?: SkillCurriculumReader.authenticMovement(name, description)?.takeIf { it in movements }

    fun progress(credits: List<SkillCredit>, verified: Set<String> = emptySet()): SkillProgress {
        val key = credits.toList() to verified.toSet()
        last?.takeIf { it.first == key }?.let { return it.second }
        return compute(key.first, key.second).also { last = key to it }
    }

    private fun compute(credits: List<SkillCredit>, verified: Set<String>): SkillProgress {
        val classes = credits.mapNotNull { credit ->
            val program = programs[credit.programId]
            // A class outside the program (the athlete's own day) counts its ticks as they are.
            if (program == null && credit.sets == null) return@mapNotNull null
            // A class counts once it ran at least [MIN_SHARE_PERCENT] of its planned length.
            val minutes = program?.minutes ?: credit.minutes
            if (minutes != null && credit.seconds < minutes * 60L * MIN_SHARE_PERCENT / 100) return@mapNotNull null
            val sets = if (program == null) credit.sets.orEmpty().filter { (id, count) -> count > 0 && id in movements }
                else creditedSets(credit, program)
            sets to credit.factor
        }
        val result = families.map { family ->
            val own = family.skills.map { skill ->
                val parts = HashMap<String, Double>()
                var sessions = 0
                for ((sets, factor) in classes) {
                    for ((id, count) in sets) {
                        val weight = skill.weights[id] ?: continue
                        parts[id] = (parts[id] ?: 0.0) + count * setPoints(id) * weight * scaled(id, factor)
                    }
                    if ((sets[skill.id] ?: 0) > 0) sessions++
                }
                val points = parts.values.sum()
                // The target is the schedule's delivery rounded down; the margin absorbs summing in another order.
                val prep = (PREP_POINTS * (points + 1e-6) / skill.threshold).toInt().coerceIn(0, PREP_POINTS)
                val sources = parts.entries.sortedByDescending { it.value }.map { (id, value) ->
                    (movements[id]?.name ?: id) to (value * PREP_POINTS / skill.threshold).roundToInt().coerceAtMost(PREP_POINTS)
                }
                val tested = skill.id in verified
                SkillStatus(skill, prep, sessions, tested, achievedAlone(skill, prep, sessions, tested), sources)
            }
            // Achieving a rung also achieves every easier rung of the family, by value rather than ladder position.
            val statuses = own.map { status ->
                status.copy(achieved = status.achieved || own.any { it.achieved && it.skill.value > status.skill.value })
            }
            family to statuses
        }
        return SkillProgress(result)
    }

    /** Credited sets per movement: the ticks up to the class's prescription, or the prescription for an older class. */
    private fun creditedSets(credit: SkillCredit, program: SkillProgram): Map<String, Int> {
        val prescribed = HashMap<String, Int>()
        for (step in program.steps) if (!step.readinessCheck) prescribed[step.movementId] = (prescribed[step.movementId] ?: 0) + step.sets
        val ticked = credit.sets ?: return prescribed
        // An easier option isn't in the prescription; it is credited up to the class's whole prescription.
        val total = prescribed.values.sum()
        return ticked.filter { (id, count) -> count > 0 && id in movements }
            .mapValues { (id, count) -> min(count, prescribed[id] ?: total) }
    }

    private fun setPoints(movementId: String): Double = setPoints(movements[movementId], grades)

    /** A day's factor reaches the movements whose reps it scaled ([Intensity]); skill work keeps its points. */
    private fun scaled(movementId: String, factor: Double): Double =
        if (movements[movementId]?.category in Intensity.STRENGTH) factor else 1.0

    companion object {
        const val POINTS_PER_SET = 10.0
        const val PREP_POINTS = 50
        const val PRACTICE_POINTS = 25
        const val PRACTICE_SESSIONS = 2
        /** A class counts toward skills when it took at least this share of its planned minutes. */
        const val MIN_SHARE_PERCENT = 20
        private const val BEYOND_STEP = 1.6
        private const val MIN_THRESHOLD = 40

        private val TIERS = mapOf(
            "strength" to 1.25, "skill_foundation" to 1.25, "skill_drill" to 1.25, "power" to 1.25,
            "advanced_fundamental" to 1.5, "skill" to 1.5, "skill_strength" to 1.5,
            "advanced_strength" to 2.0, "advanced_skill" to 2.0, "mastery_skill" to 2.5,
        )

        /** A rung's set is worth more the harder its grade: 10 at grade 1 up to 20 at grade 5. Other movements go by category. */
        private fun setPoints(movement: SkillMovement?, grades: Map<String, Int>): Double =
            grades[movement?.id]?.let { POINTS_PER_SET * (1 + 0.25 * (it - 1)) }
                ?: POINTS_PER_SET * (TIERS[movement?.category] ?: 1.0)

        /** Achieved by the rung's own work: a passed test, or training alone for a rung below the test gate. */
        private fun achievedAlone(skill: Skill, prep: Int, sessions: Int, verified: Boolean): Boolean =
            verified || (skill.value < OverallScore.TEST_GATE &&
                prep + PRACTICE_POINTS * min(sessions, PRACTICE_SESSIONS) >= 100)

        fun build(
            movements: Map<String, SkillMovement>,
            schedule: List<SkillProgram>,
            specs: List<SkillFamilySpec> = DEFAULT_FAMILIES,
            grades: Map<String, Int> = DEFAULT_GRADES,
            values: Map<String, Int> = OverallScore.SKILL_VALUES,
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
                    val (raw, expected, beyond) = calibrate(id, weights, movements, schedule, previous, grades)
                    // A trained skill's prep is full on the class that first prescribes it, or a little later for
                    // the very first classes.
                    val threshold = if (expected != null) maxOf(raw, MIN_THRESHOLD) else raw
                    previous = threshold
                    val above = ladder.drop(rung + 1).firstNotNullOfOrNull(grades::get) ?: 5
                    previousDifficulty = maxOf(previousDifficulty,
                        grades[id] ?: estimate(movements.getValue(id), expected, previousDifficulty, above))
                    val value = values[id] ?: 0
                    val test = OverallScore.SKILL_TESTS[id]?.takeIf { value >= OverallScore.TEST_GATE }
                    Skill(id, movements.getValue(id).name, rung, threshold, expected, beyond, previousDifficulty, value, test, weights)
                }
                SkillFamily(spec, skills)
            }
            return SkillBook(families, movements, schedule.associateBy { it.id }, grades)
        }

        /**
         * Difficulty for a rung without a grade (one that joined its ladder by its regression): the level that first
         * teaches it, at least Intermediate for advanced categories, and kept between the rung [below] and the next
         * graded rung [above]. When the program never teaches it, it is one step past the rung below. Where a class
         * first schedules a movement is a poor grade on its own: the program saves regressions for deload days and
         * leaves some rungs out, so a negative dip would be Elite and a wall push-up would make push-ups Advanced.
         */
        internal fun estimate(movement: SkillMovement, expected: SkillProgram?, below: Int, above: Int): Int {
            val byLevel = when (expected?.level) {
                null -> below + 1
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
            return maxOf(byLevel, floor).coerceIn(below, maxOf(below, above))
        }

        /** Target for one skill: points the schedule delivers before (and on) the first session that prescribes it. */
        private fun calibrate(
            id: String,
            weights: Map<String, Double>,
            movements: Map<String, SkillMovement>,
            schedule: List<SkillProgram>,
            previous: Int,
            grades: Map<String, Int>,
        ): Triple<Int, SkillProgram?, Boolean> {
            var delivered = 0.0
            var firstCheck: Pair<SkillProgram, Double>? = null
            for (program in schedule) {
                for (step in program.steps) {
                    if (step.readinessCheck) continue
                    val weight = weights[step.movementId] ?: continue
                    delivered += step.sets * setPoints(movements[step.movementId], grades) * weight
                }
                if (program.steps.any { it.movementId == id && !it.readinessCheck }) {
                    return Triple(delivered.toInt(), program, false)
                }
                if (firstCheck == null && program.steps.any { it.movementId == id && it.readinessCheck }) {
                    firstCheck = program to delivered
                }
            }
            // Only offered as a readiness check: prep is the lead-up to where the program first tests it.
            firstCheck?.let { (program, points) -> return Triple(points.toInt(), program, true) }
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
            // A family's foundations count toward it as they are: they never join a ladder as a rung.
            val foundationIds = specs.flatMap { it.foundations }.toSet()
            var changed = true
            while (changed) {
                changed = false
                for (movement in movements.values) {
                    if (movement.id in assigned || movement.id in foundationIds) continue
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

        /**
         * 1 Beginner, 2 Novice, 3 Intermediate, 4 Advanced, 5 Elite: how a street-workout coach grades each rung,
         * ladder by ladder. Novice is the first strict benchmark (pull-up, dip); Elite is kept for the full front
         * lever, the straddle and full planche and the full one-arm pull-up.
         */
        val DEFAULT_GRADES = mapOf(
            "assisted_pullup" to 1, "eccentric_pullup" to 1, "chinup" to 2, "pullup" to 2, "archer_pullup" to 3,
            "typewriter_pullup" to 4, "assisted_one_arm_pullup" to 4, "one_arm_pullup_negative" to 4, "one_arm_pullup" to 5,
            "straight_bar_support" to 1, "low_bar_transition" to 2, "pullover_lowbar" to 2, "chest_to_bar" to 3,
            "jump_muscleup" to 3, "negative_muscleup" to 3, "band_muscleup" to 3, "strict_muscleup" to 4,
            "tuck_front_row" to 1, "tuck_front_hold" to 2, "advanced_tuck_front" to 3, "one_leg_front" to 4,
            "straddle_front" to 4, "full_front" to 5,
            "skin_cat_partial" to 1, "german_hang" to 2, "tuck_back_entry" to 2, "tuck_back_hold" to 2,
            "advanced_tuck_back" to 3, "one_leg_back" to 3, "straddle_back" to 4, "full_back" to 4,
            "support_hold" to 1, "assisted_dip" to 1, "eccentric_dip" to 1, "parallel_dip" to 2, "straight_bar_dip" to 3,
            "wall_pushup" to 1, "incline_pushup" to 1, "pushup" to 1, "pause_pushup" to 2,
            "close_pushup" to 2, "archer_pushup" to 3, "one_arm_pushup" to 4,
            "pike_pushup" to 2, "elevated_pike_pushup" to 2, "wall_hspu_negative" to 3, "wall_hspu" to 4,
            "deficit_wall_hspu" to 4, "freestanding_hspu_negative" to 4,
            "elevated_plank" to 1, "planche_lean" to 2, "frog_stand" to 2, "pseudo_planche_pushup" to 3, "tuck_planche" to 3,
            "advanced_tuck_planche" to 4, "straddle_planche" to 5, "full_planche" to 5,
            "overhead_line" to 1, "pike_handstand_hold" to 1, "wall_handstand" to 2, "wall_kickup" to 2,
            "handstand_exit" to 2, "handstand_toe_pull" to 3, "handstand_balance" to 3, "freestanding_handstand" to 4,
            "split_squat" to 1, "bulgarian_split_squat" to 2, "assisted_pistol_squat" to 2, "pistol_squat" to 3,
            "sliding_leg_curl" to 2, "nordic_negative" to 3, "nordic_curl" to 4,
            "knee_raise" to 1, "hanging_leg_raise" to 2, "toes_to_bar" to 3,
            "tuck_lsit" to 1, "one_leg_lsit" to 2, "lsit" to 3, "v_sit" to 4,
            "candlestick" to 1, "bent_dragon_negative" to 2, "one_leg_dragon_negative" to 3, "full_dragon_negative" to 3,
            "dragon_flag" to 4,
            "kneeling_side_plank" to 1, "flag_ground_support" to 2, "assisted_tuck_flag" to 2, "tuck_flag" to 3,
            "straddle_flag" to 4, "full_flag" to 4,
        )

        val DEFAULT_FAMILIES = listOf(
            SkillFamilySpec("pullup", "Pull-up", SkillGroup.PULL,
                listOf("assisted_pullup", "eccentric_pullup", "chinup", "pullup", "archer_pullup", "typewriter_pullup",
                    "assisted_one_arm_pullup", "one_arm_pullup_negative", "one_arm_pullup"),
                listOf("inverted_row", "active_hang", "scapular_pull"), setOf("vertical_pull")),
            SkillFamilySpec("muscleup", "Muscle-up", SkillGroup.PULL,
                listOf("straight_bar_support", "low_bar_transition", "jump_muscleup", "chest_to_bar", "negative_muscleup",
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
                listOf("wall_pushup", "incline_pushup", "pushup", "pause_pushup", "close_pushup", "archer_pushup", "one_arm_pushup"),
                listOf("plank", "scapular_pushup"), setOf("horizontal_push")),
            SkillFamilySpec("hspu", "Handstand push-up", SkillGroup.PUSH,
                listOf("pike_pushup", "elevated_pike_pushup", "wall_hspu_negative", "wall_hspu", "deficit_wall_hspu",
                    "freestanding_hspu_negative"),
                listOf("pushup"), setOf("overhead_press")),
            SkillFamilySpec("planche", "Planche", SkillGroup.PUSH,
                listOf("elevated_plank", "planche_lean", "frog_stand", "pseudo_planche_pushup", "tuck_planche",
                    "advanced_tuck_planche", "straddle_planche", "full_planche"),
                listOf("support_hold", "straight_bar_support"), setOf("planche")),
            SkillFamilySpec("handstand", "Handstand", SkillGroup.PUSH,
                listOf("overhead_line", "pike_handstand_hold", "wall_handstand", "wall_kickup", "handstand_exit",
                    "handstand_toe_pull", "handstand_balance", "freestanding_handstand"),
                listOf("pike_pushup", "wall_slide"), setOf("handstand")),
            SkillFamilySpec("pistol", "Pistol squat", SkillGroup.LEGS,
                listOf("split_squat", "assisted_pistol_squat", "pistol_squat"),
                listOf("squat", "box_squat", "reverse_lunge", "step_up", "cossack_squat", "deep_squat_hold", "bulgarian_split_squat"),
                setOf("single_leg_squat")),
            SkillFamilySpec("nordic", "Nordic curl", SkillGroup.LEGS,
                listOf("sliding_leg_curl", "nordic_negative", "nordic_curl"),
                listOf("glute_bridge", "single_leg_bridge", "single_leg_rdl"), setOf("knee_flexion")),
            SkillFamilySpec("hanging_core", "Toes to bar", SkillGroup.CORE,
                listOf("knee_raise", "hanging_leg_raise", "toes_to_bar"),
                listOf("lying_leg_raise", "hollow_hold", "seated_pike_lift"), setOf("hanging_core")),
            SkillFamilySpec("lsit", "L-sit", SkillGroup.CORE,
                listOf("tuck_lsit", "one_leg_lsit", "lsit", "v_sit"),
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
