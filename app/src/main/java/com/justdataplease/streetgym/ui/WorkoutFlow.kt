package com.justdataplease.streetgym.ui

import com.justdataplease.streetgym.data.model.TrainingExercise
import com.justdataplease.streetgym.data.model.TrainingSet
import com.justdataplease.streetgym.data.points.Points
import com.justdataplease.streetgym.data.skills.SkillStandard
import kotlin.math.roundToInt

/** One set of a class in training order, with the progress key both workout views save. */
data class WorkoutStep(
    val section: String,
    val exercise: TrainingExercise,
    val exerciseIndex: Int,
    val set: TrainingSet,
    val setIndex: Int,
    val key: String,
) {
    val setNumber: Int get() = set.number.takeIf { it > 0 } ?: setIndex + 1
    /** The exercise this set belongs to, as the easier-option choice is saved. */
    val exerciseKey: String get() = WorkoutFlow.exerciseKey(section, exercise, exerciseIndex)
    val setCount: Int get() = exercise.sets.size
    /** A set that counts toward skills, whose reps or seconds the athlete can log. */
    val credited: Boolean get() = section in WorkoutFlow.CREDITED_SECTIONS && !exercise.readinessCheck
    /** 2 for a set done on each side ("per side"), else 1. */
    val sides: Int get() = if (SetNotes.parse(set.description.resolve()).sides.isEmpty()) 1 else 2
}

/** A hold, measured in seconds; otherwise a set of reps. */
val TrainingSet.timed: Boolean get() = repetitions == null && !repetitionsMax && durationSeconds != null

/** The plan's reps or seconds, or null when it says "max" or gives no number. */
val TrainingSet.planned: Int? get() = if (timed) durationSeconds else repetitions?.takeIf { !repetitionsMax }

/**
 * How a set is logged and counted: [timed] in seconds, else reps; [planned] is the number that counts when the athlete
 * logs nothing, null when they have to give it. [window]: the plan's time is for attempts, so only the longest clean
 * hold counts.
 */
data class SetMeasure(val timed: Boolean, val planned: Int?, val window: Boolean = false)

/**
 * The ordered sets of one class (warm-up, practice, main, cooldown), free of Android types so the
 * list and focus views walk the same steps and save the same keys.
 */
class WorkoutFlow(val steps: List<WorkoutStep>) {
    val keys: List<String> = steps.map(WorkoutStep::key)
    val size: Int get() = steps.size

    operator fun get(index: Int): WorkoutStep = steps[index]

    /** The same class without the sets in [keys] (switched off, review 12); every other key stays as the full class saves it. */
    fun without(keys: Set<String>): WorkoutFlow = if (keys.isEmpty()) this else WorkoutFlow(steps.filter { it.key !in keys })

    fun keys(section: String): List<String> = steps.filter { it.section == section }.map(WorkoutStep::key)

    fun completedCount(completed: Set<String>): Int = keys.count { it in completed }

    /** The focus position: the first set that is neither checked nor skipped. */
    fun firstIncomplete(completed: Set<String>, skipped: Set<String> = emptySet()): Int? =
        steps.indexOfFirst { it.key !in completed && it.key !in skipped }.takeIf { it >= 0 }

    fun next(index: Int): Int? = (index + 1).takeIf { it in steps.indices }

    fun previous(index: Int): Int? = (index - 1).takeIf { it in steps.indices }

    fun isSectionComplete(section: String, completed: Set<String>): Boolean =
        keys(section).let { keys -> keys.isNotEmpty() && keys.all { it in completed } }

    /**
     * What the finished class credits toward skills: ticked sets per movement in the practice and main work. An
     * exercise done as its easier option ([easier] holds [exerciseKey]s) credits the easier movement; readiness checks
     * credit nothing; [movementOf] names the movement an exercise trains.
     */
    fun credits(completed: Set<String>, easier: Set<String>, movementOf: (TrainingExercise) -> String?): Map<String, Int> =
        credited(completed, easier, movementOf).groupingBy { it.second }.eachCount()

    /**
     * The same sets as [credits], each in its skill's measure ([measure]): what the athlete logged for it ([done], by set
     * key), else the plan's number; 0 when they have to give it and didn't. [standardOf] gives a skill's standard, and is
     * null for a movement that is no skill.
     */
    fun doses(
        completed: Set<String>,
        easier: Set<String>,
        done: Map<String, Int>,
        movementOf: (TrainingExercise) -> String?,
        standardOf: (String) -> SkillStandard?,
    ): Map<String, List<Int>> = credited(completed, easier, movementOf)
        .mapNotNull { (step, id) ->
            val standard = standardOf(id) ?: return@mapNotNull null
            id to (done[step.key] ?: measure(step, standard).planned ?: 0)
        }
        .groupBy({ it.first }, { it.second })

    /**
     * The ticked sets that count, for points and ranks alike (the owner, 2.10.0: "it only counts if 30% of the time
     * passes for each exercise"): an exercise's sets count once its ticks took at least [share] of their planned time
     * ([plannedSeconds]). The time from one tick to the next goes to the set ticked; sets ticked together share it by
     * their planned time. A class started before ticks were timed ([tickedAt] empty) counts every tick, and so does a
     * set ticked before then.
     */
    fun counted(completed: Set<String>, tickedAt: Map<String, Long>, startedAt: Long, share: Double = Points.timeShare): Set<String> {
        if (tickedAt.isEmpty() || share <= 0.0) return completed
        val byKey = steps.associateBy(WorkoutStep::key)
        val spent = HashMap<String, Double>()
        var previous = startedAt
        tickedAt.filterKeys { it in completed && it in byKey }.entries.groupBy({ it.value }, { it.key }).toSortedMap()
            .forEach { (at, keys) ->
                val elapsed = (at - previous).coerceAtLeast(0L) / 1_000.0
                previous = maxOf(previous, at)
                val planned = keys.map { plannedSeconds(byKey.getValue(it)) }
                val total = planned.sum()
                keys.forEachIndexed { index, key ->
                    val part = if (total > 0) elapsed * planned[index] / total else elapsed / keys.size
                    spent.merge(byKey.getValue(key).exerciseKey, part, Double::plus)
                }
            }
        val ticked = steps.filter { it.key in completed }
        val needed = ticked.filter { it.key in tickedAt }.groupBy(WorkoutStep::exerciseKey)
            .mapValues { (_, sets) -> sets.sumOf(::plannedSeconds) * share }
        return ticked.filter { step -> step.key !in tickedAt || (spent[step.exerciseKey] ?: 0.0) >= (needed[step.exerciseKey] ?: 0.0) }
            .map(WorkoutStep::key).toSet()
    }

    /**
     * What the [counted] practice and main sets earn ([Points]): the reps or seconds the athlete logged ([done]), else the
     * plan's (a logged number up to twice the plan), times the difficulty of the movement done ([difficultyOf], the easier
     * option's when they chose it). [movementOf] and [standardOf] give the measure a skill is logged in, as [doses] does.
     */
    fun points(
        counted: Set<String>,
        easier: Set<String>,
        done: Map<String, Int>,
        movementOf: (TrainingExercise) -> String?,
        standardOf: (String) -> SkillStandard?,
        difficultyOf: (String?) -> Int,
    ): Int = steps.filter { it.credited && it.key in counted }.sumOf { step ->
        val measure = measure(step, movement(step, easier, movementOf)?.let(standardOf))
        val logged = done[step.key]
        val (amount, timed) = when {
            logged != null -> logged to measure.timed
            measure.planned != null -> measure.planned to measure.timed
            else -> (step.set.planned ?: 0) to step.set.timed
        }
        val cap = measure.planned?.let { it * Points.LOGGED_CAP } ?: if (timed) Points.MAX_SECONDS else Points.MAX_REPS
        val moved = step.exercise.easier?.takeIf { step.exerciseKey in easier }?.movementId ?: step.exercise.movementId
        Points.set(amount.coerceIn(0, cap), timed, step.sides, difficultyOf(moved))
    }.roundToInt()

    /** The ticked sets that train skills, each with the movement it credits. */
    private fun credited(completed: Set<String>, easier: Set<String>, movementOf: (TrainingExercise) -> String?) = steps
        .filter { it.credited && it.key in completed }
        .mapNotNull { step -> movement(step, easier, movementOf)?.let { step to it } }

    /**
     * An exam class's targets (its assessment sets in the main work), each with whether it looks reached: every set
     * ticked, none done as the easier option ([easier]) and no logged number ([done]) short of the plan.
     */
    fun examTests(completed: Set<String>, easier: Set<String>, done: Map<String, Int>): List<Pair<TrainingExercise, Boolean>> = steps
        .filter { it.section == "main" && it.exercise.assessment }
        .groupBy(WorkoutStep::exercise)
        .map { (exercise, sets) ->
            exercise to sets.all { step ->
                step.key in completed && step.exerciseKey !in easier &&
                    done[step.key].let { it == null || it >= (step.set.planned ?: 0) }
            }
        }

    companion object {
        /** [sections] are (key, exercises) pairs in class order; empty sections add no steps. */
        fun of(programId: String, sections: List<Pair<String, List<TrainingExercise>>>): WorkoutFlow = WorkoutFlow(
            sections.flatMap { (section, exercises) ->
                exercises.flatMapIndexed { exerciseIndex, exercise ->
                    exercise.sets.mapIndexed { setIndex, set ->
                        WorkoutStep(
                            section, exercise, exerciseIndex, set, setIndex,
                            setKey(programId, section, exercise, exerciseIndex, set, setIndex),
                        )
                    }
                }
            },
        )

        /** The sections whose sets train skills: warm-up and cooldown only prepare and recover. */
        val CREDITED_SECTIONS = setOf("practice", "main")

        /**
         * How long a set is planned to take: its work (a hold as written, reps at [Points.SECONDS_PER_REP] each, a "max"
         * set as ten, both sides when per side) and the rest before it.
         */
        fun plannedSeconds(step: WorkoutStep): Double {
            val work = if (step.set.timed) step.set.durationSeconds ?: 0 else (step.set.planned ?: 10) * Points.SECONDS_PER_REP
            val rest = if (step.setIndex > 0) step.exercise.sets[step.setIndex - 1].breakSeconds ?: 0 else 0
            return (work * step.sides + rest).toDouble()
        }

        /** The movement [step] trains: its easier option when [easier] holds its exercise, else what [movementOf] names. */
        fun movement(step: WorkoutStep, easier: Set<String>, movementOf: (TrainingExercise) -> String?): String? =
            step.exercise.easier?.takeIf { step.exerciseKey in easier }?.movementId ?: movementOf(step.exercise)

        /**
         * How [step] is logged and counted toward the skill it trains ([standard], null for a movement that is no skill):
         * the plan's number, unless the time is a practice window or the skill is measured the other way (an easier
         * option done for reps in place of a hold, or the reverse), when only the athlete's own number counts.
         */
        fun measure(step: WorkoutStep, standard: SkillStandard?): SetMeasure = when {
            standard == null -> SetMeasure(step.set.timed, step.set.planned)
            standard.window -> SetMeasure(timed = true, planned = null, window = true)
            standard.seconds != step.set.timed -> SetMeasure(standard.seconds, planned = null)
            else -> SetMeasure(step.set.timed, step.set.planned)
        }

        fun exerciseKey(section: String, exercise: TrainingExercise, exerciseIndex: Int): String =
            "$section:${exercise.id}-$exerciseIndex"

        fun setKey(
            programId: String,
            section: String,
            exercise: TrainingExercise,
            exerciseIndex: Int,
            set: TrainingSet,
            setIndex: Int,
        ): String = buildString {
            append(programId).append(':').append(section).append(':')
            append(exercise.id).append('-').append(exerciseIndex).append(':')
            append(set.id).append('-').append(setIndex)
        }
    }
}

/** A movement's notes as the parser joins them: "description; Safety: …; Regression: …". */
data class ExerciseNotes(val description: String, val safety: String, val easier: String) {
    companion object {
        private const val SAFETY = "Safety:"
        private const val REGRESSION = "Regression:"

        fun parse(text: String): ExerciseNotes {
            val parts = text.split("; ").map(String::trim).filter(String::isNotBlank)
            fun tagged(tag: String) = parts.filter { it.startsWith(tag) }
                .joinToString(" ") { it.removePrefix(tag).trim() }
            return ExerciseNotes(
                description = parts.filterNot { it.startsWith(SAFETY) || it.startsWith(REGRESSION) }.joinToString("; "),
                safety = tagged(SAFETY),
                easier = tagged(REGRESSION),
            )
        }
    }
}

/** A set's notes split into the side qualifier ("per side") and cues such as the tempo. */
data class SetNotes(val sides: List<String>, val cues: List<String>) {
    companion object {
        private val SIDE_NOTES = setOf("per side")

        fun parse(text: String): SetNotes {
            val (sides, cues) = text.split("; ").map(String::trim).filter(String::isNotBlank)
                .partition { it in SIDE_NOTES }
            return SetNotes(sides, cues)
        }
    }
}
