package com.justdataplease.streetgym.data.progress

import com.justdataplease.streetgym.billing.LevelAccess
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.model.TrainingSet
import java.util.Locale

/**
 * First-launch placement: three questions about the athlete's best clean set decide where the program starts.
 * Each answer is a band of reps, kept as its lowest count. The start is the first row of [STARTS] that all three
 * answers reach, so the weakest answer decides.
 */
object Placement {
    /** Each question's bands, as their lowest counts. */
    enum class Question(val bands: List<Int>) {
        PUSH_UPS(listOf(0, 10, 20, 30)),
        PULL_UPS(listOf(0, 1, 5, 10)),
        SQUATS(listOf(0, 30, 50));

        /** "0–9", "10–19", "30+"; a band of one count is just that count ("0"). */
        fun label(band: Int): String {
            val low = bands[band]
            val high = bands.getOrNull(band + 1)?.minus(1) ?: return "$low+"
            return if (high == low) "$low" else "$low–$high"
        }
    }

    /** Where the program starts: a level and a day within it (its program number). */
    data class Start(val level: Int, val day: Int = 1)

    /** A start and the least of each answer it takes. */
    data class Row(val start: Start, val pushUps: Int, val pullUps: Int, val squats: Int)

    /**
     * Every start, best first. Level 1, day 41 opens the rebuilt Level 1 block for a fit adult still short on pull-ups
     * (0–4: five or more reach Level 2); day 22 is where full push-ups first appear.
     */
    val STARTS = listOf(
        Row(Start(3), pushUps = 30, pullUps = 10, squats = 50),
        Row(Start(2), pushUps = 20, pullUps = 5, squats = 30),
        Row(Start(1, 41), pushUps = 20, pullUps = 0, squats = 30),
        Row(Start(1, 22), pushUps = 10, pullUps = 0, squats = 30),
        Row(Start(1), pushUps = 0, pullUps = 0, squats = 0),
    )

    /**
     * The highest level a start reaches. Levels 4 and up need skills these counts can't measure, and Levels 1–3 are
     * the free ones, so a placement never lands on a paywall.
     */
    val MAX_LEVEL = LevelAccess.FIRST_PAID_LEVEL - 1

    /** The start for the lowest count of each band picked: the first row all three answers reach. */
    fun start(pushUps: Int, pullUps: Int, squats: Int): Start =
        STARTS.first { pushUps >= it.pushUps && pullUps >= it.pullUps && squats >= it.squats }.start

    /**
     * Only a new athlete is asked: nothing answered or skipped yet, no day completed and no workout under way. Anyone
     * with progress counts as placed, including athletes from before placement existed.
     */
    fun shouldAsk(recorded: Boolean, completedDays: Int, workoutActive: Boolean): Boolean =
        !recorded && completedDays == 0 && !workoutActive

    /** The days [start] marks complete: every day of the levels below it and the days before it in its own level. */
    fun <T> skipped(ordered: List<T>, start: Start, levelOf: (T) -> Int, dayOf: (T) -> Int): List<T> =
        ordered.filter { levelOf(it) < start.level || levelOf(it) == start.level && dayOf(it) < start.day }

    /**
     * The first [limit] movements of [program]'s main training, each with its dose ("strict pull-up 3×4"), for the
     * line on what a level opens with. A named set inside an exercise (a compound round) counts as its own movement.
     */
    fun opening(program: TrainingProgram, limit: Int = 3): List<String> {
        val seen = mutableSetOf<String>()
        return program.exercises.asSequence()
            .flatMap { exercise -> exercise.sets.groupBy { it.name.resolve().ifBlank { exercise.name.resolve() } }.entries }
            .filter { (name, _) -> name.isNotBlank() && seen.add(name.lowercase(Locale.ENGLISH)) }
            .take(limit)
            .map { (name, sets) -> listOf(inSentence(name), dose(sets)).filter(String::isNotBlank).joinToString(" ") }
            .toList()
    }

    /** "3×4", "2×20 s" or "3×max" when every set shares it; nothing when the sets differ. */
    private fun dose(sets: List<TrainingSet>): String {
        val reps = sets.map { it.repetitions }.distinct().singleOrNull()
        val seconds = sets.map { it.durationSeconds }.distinct().singleOrNull()
        return when {
            sets.all { it.repetitionsMax } -> "${sets.size}×max"
            reps != null -> "${sets.size}×$reps"
            seconds != null -> "${sets.size}×$seconds s"
            else -> ""
        }
    }

    /** "Strict Pull-Up" reads "strict pull-up" mid-sentence; a short capital such as the L of "L-Sit" stays. */
    private fun inSentence(name: String): String = name.split(' ').joinToString(" ") { word ->
        if (word.length > 1 && word[1].isLowerCase()) word.lowercase(Locale.ENGLISH) else word
    }
}
