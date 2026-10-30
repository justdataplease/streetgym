package com.justdataplease.streetgym.data.progress

import com.justdataplease.streetgym.billing.LevelAccess
import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.model.TrainingSet
import java.util.Locale

/**
 * First-launch placement, taken like an exam: one set each of strict pull-ups, push-ups and squats, as many as the
 * athlete can with good form. A weighted [score] (pull-ups count most, squats least) picks one of the block starts of
 * Levels 1–3 ([starts]), and the [gate]s keep pull-ups honest: Level 2 needs eight and Level 3 fifteen. An athlete the
 * counts place at Level 3 can go on with the skills step ([skillStart]): the hardest level test they would pass today.
 * The days before the start give no skill levels, like "Complete day". It's only a guide: the athlete can start from
 * scratch, or complete levels themselves, whenever they think they should.
 */
object Placement {
    /** The tests, in the card's order. [full] is the count that earns all of a test's [weight]. */
    enum class Test(val weight: Double, val full: Int) {
        PULL_UPS(0.5, 20),
        PUSH_UPS(0.35, 50),
        SQUATS(0.15, 75),
    }

    /** Where the program starts: a level and a day within it (its program number). */
    data class Start(val level: Int, val day: Int = 1)

    /** One test that, alone and [more] reps higher, would move the start on to [start]. */
    data class Better(val test: Test, val more: Int, val start: Start)

    /**
     * The highest level a start reaches. Levels 4 and up need skills these counts can't measure, and Levels 1–3 are
     * the free ones, so a placement never lands on a paywall.
     */
    val MAX_LEVEL = LevelAccess.FIRST_PAID_LEVEL - 1

    /** 0 to 1: each test's share of its [Test.full] count, weighted. */
    fun score(pullUps: Int, pushUps: Int, squats: Int): Double = listOf(
        Test.PULL_UPS to pullUps, Test.PUSH_UPS to pushUps, Test.SQUATS to squats,
    ).sumOf { (test, count) -> test.weight * count.coerceIn(0, test.full) / test.full }

    /** The least pull-ups and push-ups a start takes: Level 3 fifteen and 35, Level 2 eight and 20, Level 1 past day 41 three pull-ups. */
    fun gate(start: Start): Pair<Int, Int> = when {
        start.level >= 3 -> 15 to 35
        start.level == 2 -> 8 to 20
        start.day > 41 -> 3 to 0
        else -> 0 to 0
    }

    /**
     * Where a placement may start, in program order: the first class of Levels 1 and 2 and the class after each of
     * their exams, so the athlete joins a block fresh; Level 3 only at its first class, as three counts don't measure
     * the tendons its levers and muscle-ups load.
     */
    fun <T> starts(ordered: List<T>, levelOf: (T) -> Int, dayOf: (T) -> Int, isExam: (T) -> Boolean): List<Start> {
        val days = ordered.filter { levelOf(it) <= MAX_LEVEL }.map { Start(levelOf(it), dayOf(it)) }.toSet()
        val afterExams = ordered.filter { levelOf(it) < MAX_LEVEL && isExam(it) }
            .map { Start(levelOf(it), dayOf(it) + 1) }.filter { it in days }
        return ((1..MAX_LEVEL).map { Start(it) }.filter { it in days } + afterExams)
            .distinct().sortedWith(compareBy({ it.level }, { it.day }))
    }

    /**
     * The start for these counts: the score picks a place along [starts] (a perfect score the last), and the gates
     * hold it back to the furthest start the pull-ups and push-ups reach.
     */
    fun start(pullUps: Int, pushUps: Int, squats: Int, starts: List<Start>): Start {
        val picked = (score(pullUps, pushUps, squats) * starts.size).toInt().coerceIn(0, starts.lastIndex)
        return starts.take(picked + 1).last { gate(it).let { (pull, push) -> pullUps >= pull && pushUps >= push } }
    }

    /**
     * What would move the start on soonest: the first test, in the card's order, that a few more reps alone would
     * take to a later start, with the fewest reps that do. Null at the last start, or when no single test would.
     */
    fun better(pullUps: Int, pushUps: Int, squats: Int, starts: List<Start>): Better? {
        val here = starts.indexOf(start(pullUps, pushUps, squats, starts))
        return Test.entries.firstNotNullOfOrNull { test ->
            (1..MAX_MORE).firstNotNullOfOrNull { more ->
                val moved = when (test) {
                    Test.PULL_UPS -> start(pullUps + more, pushUps, squats, starts)
                    Test.PUSH_UPS -> start(pullUps, pushUps + more, squats, starts)
                    Test.SQUATS -> start(pullUps, pushUps, squats + more, starts)
                }
                Better(test, more, moved).takeIf { starts.indexOf(moved) > here }
            }
        }
    }

    /** A hint names at most this many more reps. */
    private const val MAX_MORE = 15

    const val LAST_LEVEL = 10

    /**
     * The level tests the skills step offers (review 6): passing the Level N test, every target with good form, suggests
     * Level N + 1. The counts reach Level 3, so the step starts at its test and ends at Level 9's.
     */
    val SKILL_TESTS: IntRange = MAX_LEVEL until LAST_LEVEL

    fun skillStart(passed: Int): Start = Start(passed.coerceIn(SKILL_TESTS) + 1)

    /** The last level [tier] opens: Level 3 on Free, 5 on Plus, 10 on Pro. */
    fun lastOpenLevel(tier: Tier): Int = (1..LAST_LEVEL).last { !LevelAccess.locked(it, tier) }

    /** [start], or the first class of the last level [tier] opens when [start] needs a plan the athlete doesn't have. */
    fun capped(start: Start, tier: Tier): Start = if (LevelAccess.locked(start.level, tier)) Start(lastOpenLevel(tier)) else start

    /** The exam just before [start], whose targets the start assumes the athlete can already do; null at Level 1, day 1. */
    fun <T> assumed(ordered: List<T>, start: Start, levelOf: (T) -> Int, dayOf: (T) -> Int, isExam: (T) -> Boolean): T? =
        skipped(ordered, start, levelOf, dayOf).lastOrNull(isExam)

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
