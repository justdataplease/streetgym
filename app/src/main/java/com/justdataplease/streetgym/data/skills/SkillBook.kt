package com.justdataplease.streetgym.data.skills

/** One entry of the curriculum movement library, as far as skills need it. [counts]: what a set must be to count. */
data class SkillMovement(
    val id: String,
    val name: String,
    val category: String,
    val patterns: Set<String>,
    val regressionId: String?,
    val counts: String? = null,
)

/**
 * One prescribed exercise in a session: [sets] sets of [reps] reps or [seconds] seconds each, null when the session
 * doesn't say (or says "max"). Readiness checks are single test sets.
 */
data class SkillStep(
    val movementId: String,
    val sets: Int,
    val readinessCheck: Boolean = false,
    val reps: Int? = null,
    val seconds: Int? = null,
)

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
 * credited to the easier movement and readiness checks left out; null for a class saved before ticks were kept, whose
 * prescription counts. [doses]: each of those sets in its skill's measure, reps or seconds held (0 when unknown); null
 * for a class saved before doses were kept, whose prescribed doses count. [seconds]: how long the class took.
 * [minutes]: the planned length of a class outside the program (the athlete's own day), when known.
 */
data class SkillCredit(
    val programId: String,
    val sets: Map<String, Int>? = null,
    val seconds: Long = Long.MAX_VALUE,
    val minutes: Int? = null,
    val doses: Map<String, List<Int>>? = null,
)

enum class SkillGroup { PULL, PUSH, LEGS, CORE }

/**
 * A ladder of skills. [ladder] is ordered easiest to hardest, as a coach teaches it; [foundations] are the family's
 * movements that earn no belt and never become rungs: basic strength work, assisted, supported, partial or negative
 * progressions (the owner's call: a skill is something done on one's own, such as a long dead hang; the progressions and
 * the negatives are steps toward it), the shapes that left the belts in round 9 and the drills the classes add around
 * them; [tags] let new movements whose regression sits in this ladder join it automatically.
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
    /** First session that prescribes the skill, or null when the program never does. */
    val expected: SkillProgram?,
    /** Past what the program trains: only offered as a readiness check, or never scheduled. */
    val beyondProgram: Boolean,
    /** What the skill is worth in the overall score at Elite ([OverallScore.SKILL_VALUES]). */
    val value: Int,
    /** Its measure, difficulty and the targets of its levels. */
    val standard: SkillStandard,
    /** What a set must be to count toward its levels: the shape, range and timing a judge would ask for. */
    val counts: String? = null,
) {
    /** How hard the movement is: 1 easy to 5 extreme. */
    val difficulty: Int get() = standard.grade
}

data class SkillFamily(val spec: SkillFamilySpec, val skills: List<Skill>) {
    /** The family's top value: what Elite of its hardest rung is worth. */
    val ceiling: Int get() = skills.maxOfOrNull { it.value } ?: 0
}

/**
 * Where an athlete stands on one skill. [sets]: its ticked sets. [best]: its best set, in reps or seconds (0 when none
 * is known). [atTargets]: its sets at each target, Beginner to Elite. [level]: the level those reach. A harder rung
 * says nothing of an easier one's target, so every level is earned on the skill itself (the coaching review, 29 October
 * 2026: no more carried Master).
 */
data class SkillStatus(
    val skill: Skill,
    val sets: Int,
    val best: Int,
    val atTargets: List<Int>,
    val level: SkillLevel,
) {
    val next: SkillLevel? get() = level.next
    val started: Boolean get() = sets > 0 || level > SkillLevel.NONE

    /** Sets done at [level]'s target. */
    fun setsAt(level: SkillLevel): Int = atTargets.getOrNull(level.ordinal - SkillLevel.BEGINNER.ordinal) ?: 0

    /** Sets at its target still needed for the next level, 0 at Elite. */
    val setsToGo: Int get() = next?.let { (it.sets - setsAt(it)).coerceAtLeast(0) } ?: 0

    /** 0-100: the way to the next level. */
    val progress: Int get() = next?.let { (setsAt(it) * 100 / it.sets).coerceAtMost(100) } ?: 100
}

data class SkillProgress(val families: List<Pair<SkillFamily, List<SkillStatus>>>) {
    /** The weighted overall score, from the same statuses. */
    val overall: OverallScore by lazy { OverallScore.of(families) }
    val statuses: List<SkillStatus> get() = families.flatMap { it.second }

    /** Levels earned across every skill, out of [totalLevels]. */
    val levels: Int get() = statuses.sumOf { it.level.ordinal }
    val totalLevels: Int get() = statuses.size * SkillLevel.ELITE.ordinal

    /** Skills the athlete has started and can still raise, closest to their next level first. */
    fun closest(limit: Int): List<SkillStatus> = statuses.filter { it.started && it.next != null }
        .sortedWith(compareByDescending<SkillStatus> { it.progress }.thenByDescending { it.sets }).take(limit)
}

/**
 * Skills built from the curriculum, each with five levels ([SkillLevel]): each level takes its sets of the skill itself
 * at its target ([SkillStandard]), reps for movements and seconds for holds. A set done as the easier option
 * counts for the easier skill.
 */
class SkillBook private constructor(
    val families: List<SkillFamily>,
    private val movements: Map<String, SkillMovement>,
    private val programs: Map<String, SkillProgram>,
) {
    private val skills: Map<String, Skill> = families.flatMap { it.skills }.associateBy { it.id }

    /** The last result: Skills redraws on every tab and filter tap with the same input. */
    @Volatile private var last: Pair<List<SkillCredit>, SkillProgress>? = null

    /** The movement a workout exercise credits: its own id when skills know it, else the source workout's name. */
    fun movementOf(movementId: String?, name: String, description: String): String? =
        movementId?.takeIf { it in movements }
            ?: SkillCurriculumReader.authenticMovement(name, description)?.takeIf { it in movements }

    /** The skill with [id], if it is a rung of a ladder. */
    fun skill(id: String): Skill? = skills[id]

    fun progress(credits: List<SkillCredit>): SkillProgress {
        val key = credits.toList()
        last?.takeIf { it.first == key }?.let { return it.second }
        return compute(key).also { last = key to it }
    }

    private fun compute(credits: List<SkillCredit>): SkillProgress {
        val doses = HashMap<String, MutableList<Int>>()
        for (credit in credits) {
            val program = programs[credit.programId]
            // A class outside the program (the athlete's own day) counts its ticks as they are.
            if (program == null && credit.sets == null) continue
            // A class counts once it ran at least [MIN_SHARE_PERCENT] of its planned length.
            val minutes = program?.minutes ?: credit.minutes
            if (minutes != null && credit.seconds < minutes * 60L * MIN_SHARE_PERCENT / 100) continue
            val ticked = if (program == null) credit.sets.orEmpty().filter { (id, count) -> count > 0 && id in movements }
                else creditedSets(credit, program)
            for ((id, count) in ticked) {
                // Each set as logged, else as prescribed, else unknown (0, a set short of every target).
                val known = credit.doses?.get(id) ?: program?.let { prescribedDoses(it, id) }.orEmpty()
                doses.getOrPut(id, ::mutableListOf) += List(count) { known.getOrElse(it) { 0 } }
            }
        }
        val result = families.map { family ->
            family to family.skills.map { skill ->
                val sets = doses[skill.id].orEmpty()
                val atTargets = skill.standard.targets.map { target -> sets.count { it >= target } }
                SkillStatus(skill, sets.size, sets.maxOrNull() ?: 0, atTargets, levelOf(atTargets))
            }
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
            .mapValues { (id, count) -> minOf(count, prescribed[id] ?: total) }
    }

    /**
     * What [program] prescribes for the skill [movementId], set by set, in the skill's measure; nothing for a practice
     * window, whose time says nothing of the hold.
     */
    private fun prescribedDoses(program: SkillProgram, movementId: String): List<Int> {
        val standard = skills[movementId]?.standard?.takeUnless { it.window } ?: return emptyList()
        return program.steps.filter { it.movementId == movementId && !it.readinessCheck }
            .flatMap { step -> List(step.sets) { (if (standard.seconds) step.seconds else step.reps) ?: 0 } }
    }

    companion object {
        /** A class counts toward skills when it took at least this share of its planned minutes. */
        const val MIN_SHARE_PERCENT = 20

        /** The level [atTargets] reach: the sets at each target, Beginner up, each level standing on the one below. */
        fun levelOf(atTargets: List<Int>): SkillLevel =
            SkillLevel.entries[SkillLevel.entries.drop(1).zip(atTargets).takeWhile { (level, sets) -> sets >= level.sets }.size]

        fun build(
            movements: Map<String, SkillMovement>,
            schedule: List<SkillProgram>,
            specs: List<SkillFamilySpec> = DEFAULT_FAMILIES,
            values: Map<String, Int> = OverallScore.SKILL_VALUES,
            standards: (String) -> SkillStandard = SkillStandards::of,
        ): SkillBook {
            val ladders = resolveLadders(movements, specs)
            val families = specs.mapNotNull { spec ->
                val ladder = ladders[spec.id].orEmpty()
                if (ladder.isEmpty()) return@mapNotNull null
                SkillFamily(spec, ladder.mapIndexed { rung, id ->
                    val trained = schedule.firstOrNull { program -> program.steps.any { it.movementId == id && !it.readinessCheck } }
                    val checked = schedule.firstOrNull { program -> program.steps.any { it.movementId == id && it.readinessCheck } }
                    Skill(id, movements.getValue(id).name, rung, trained ?: checked, trained == null, values[id] ?: 0, standards(id),
                        movements.getValue(id).counts)
                })
            }
            return SkillBook(families, movements, schedule.associateBy { it.id })
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
         * The 37 belted skills in 14 families (round 9). The skills that left the belts stay as foundations, so they
         * can't rejoin a ladder by their regression, and so do the drills the round-9 classes add (the tuck front raise,
         * the one-arm hang and the like). The Nordic curl family left with its two rungs; its leaderboard weight stays
         * in [OverallScore.RETIRED_CEILINGS] until the board function is redeployed.
         */
        val DEFAULT_FAMILIES = listOf(
            SkillFamilySpec("pullup", "Pull-up", SkillGroup.PULL,
                listOf("dead_hang", "pullup", "archer_pullup", "one_arm_pullup"),
                listOf("inverted_row", "scapular_pull", "foot_assisted_hang", "active_hang", "assisted_pullup", "chinup",
                    "eccentric_pullup", "typewriter_pullup", "one_arm_hang", "assisted_one_arm_pullup",
                    "one_arm_pullup_negative"),
                setOf("vertical_pull")),
            SkillFamilySpec("muscleup", "Muscle-up", SkillGroup.PULL,
                listOf("strict_muscleup"),
                listOf("pullup", "parallel_dip", "straight_bar_support", "chest_to_bar", "straight_bar_dip",
                    "pullover_lowbar", "low_bar_transition", "jump_muscleup", "band_muscleup", "negative_muscleup"),
                setOf("muscle_up")),
            SkillFamilySpec("front_lever", "Front lever", SkillGroup.PULL,
                listOf("tuck_front_hold", "advanced_tuck_front", "straddle_front", "full_front"),
                listOf("inverted_row", "feet_elevated_row", "scapular_pull", "hollow_body_hold", "tuck_front_row",
                    "tuck_front_raise", "one_leg_front", "front_lever_raise", "front_lever_negative"),
                setOf("front_lever")),
            SkillFamilySpec("back_lever", "Back lever", SkillGroup.PULL,
                listOf("tuck_back_hold", "advanced_tuck_back", "straddle_back", "full_back"),
                listOf("active_hang", "arch_hold", "chinup", "skin_cat_partial", "german_hang", "tuck_back_entry",
                    "one_leg_back"),
                setOf("back_lever")),
            SkillFamilySpec("dip", "Dip", SkillGroup.PUSH,
                listOf("parallel_dip"),
                listOf("foot_assisted_support", "support_hold", "scapular_dip", "close_pushup", "assisted_dip",
                    "eccentric_dip", "straight_bar_support", "straight_bar_dip"),
                setOf("bar_support")),
            SkillFamilySpec("pushup", "Push-up", SkillGroup.PUSH,
                listOf("pushup", "one_arm_pushup"),
                listOf("plank", "scapular_pushup", "wall_pushup", "incline_pushup", "pause_pushup", "close_pushup",
                    "archer_pushup", "incline_one_arm_pushup", "reverse_wrist_pushup"),
                setOf("horizontal_push")),
            SkillFamilySpec("hspu", "Handstand push-up", SkillGroup.PUSH,
                listOf("pike_pushup", "wall_hspu", "deficit_wall_hspu"),
                listOf("pushup", "elevated_pike_pushup", "wall_hspu_negative", "freestanding_hspu_negative",
                    "freestanding_hspu"),
                setOf("overhead_press")),
            SkillFamilySpec("planche", "Planche", SkillGroup.PUSH,
                listOf("pseudo_planche_pushup", "tuck_planche", "advanced_tuck_planche", "straddle_planche", "full_planche"),
                listOf("support_hold", "straight_bar_support", "elevated_plank", "planche_lean", "frog_stand",
                    "tuck_planche_pushup", "straddle_planche_negative"),
                setOf("planche")),
            SkillFamilySpec("handstand", "Handstand", SkillGroup.PUSH,
                listOf("wall_handstand", "freestanding_handstand"),
                listOf("pike_pushup", "wall_slide", "overhead_line", "pike_handstand_hold", "wall_kickup",
                    "handstand_exit", "handstand_toe_pull", "handstand_balance", "wall_handstand_shrug",
                    "tuck_press_handstand"),
                setOf("handstand")),
            // The squat leads to the pistol; the hamstring work of the old Nordic curl family stays here as movements.
            SkillFamilySpec("pistol", "Pistol squat", SkillGroup.LEGS,
                listOf("squat", "pistol_squat"),
                listOf("box_squat", "reverse_lunge", "step_up", "cossack_squat", "deep_squat_hold", "bulgarian_split_squat",
                    "split_squat", "assisted_pistol_squat", "glute_bridge", "single_leg_bridge", "single_leg_rdl",
                    "sliding_leg_curl", "nordic_negative", "nordic_curl"),
                setOf("single_leg_squat")),
            SkillFamilySpec("hanging_core", "Toes to bar", SkillGroup.CORE,
                listOf("hanging_leg_raise", "toes_to_bar"),
                listOf("lying_leg_raise", "hollow_hold", "seated_pike_lift", "knee_raise"), setOf("hanging_core")),
            SkillFamilySpec("lsit", "L-sit", SkillGroup.CORE,
                listOf("lsit", "v_sit"),
                listOf("support_hold", "knee_raise", "seated_pike_lift", "tuck_lsit", "one_leg_lsit", "tuck_v_sit"),
                setOf("l_sit")),
            // The hollow body is the dragon flag's base shape: the same straight line, lying on the floor.
            SkillFamilySpec("dragon_flag", "Dragon flag", SkillGroup.CORE,
                listOf("hollow_body_hold", "dragon_flag"),
                listOf("hollow_hold", "lying_leg_raise", "candlestick", "bent_dragon_negative",
                    "one_leg_dragon_negative", "full_dragon_negative"), setOf("dragon_flag")),
            SkillFamilySpec("human_flag", "Human flag", SkillGroup.CORE,
                listOf("tuck_flag", "straddle_flag", "full_flag"),
                listOf("straight_side_plank", "pullup", "pike_pushup", "kneeling_side_plank", "flag_ground_support",
                    "assisted_tuck_flag", "flag_negative"),
                setOf("human_flag")),
        )
    }
}
