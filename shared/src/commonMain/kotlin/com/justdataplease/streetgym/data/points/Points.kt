package com.justdataplease.streetgym.data.points

import com.justdataplease.streetgym.data.progress.WorkoutSession
import com.justdataplease.streetgym.data.skills.SkillStatus

/**
 * Points (the owner, 2.10.0: "the more points, the overall score increases, even when doing custom workouts"), the one
 * number on Skills and the leaderboard. Every counted set earns its reps times its movement's difficulty (1 easy to 5
 * extreme), a hold a rep for every [SECONDS_PER_REP] seconds; each rank a skill reaches (its chevrons) adds [CHEVRON]
 * times the skill's difficulty. Program classes, repeats, ready workouts and the athlete's own all count, once each
 * exercise ran its share of the time (WorkoutFlow.counted); "Complete day" and the placement earn nothing.
 */
object Points {
    /** A hold counts a rep for every this many seconds; a rep takes about as long when a set is timed. */
    const val SECONDS_PER_REP = 3
    /** Points per rank a skill reaches, times its difficulty: a push-up's rank is 100, a full planche's 500. */
    const val CHEVRON = 100
    /** A logged number counts up to this many times the plan's, so a typo can't buy the leaderboard. */
    const val LOGGED_CAP = 2
    /** The most a set without a planned number counts: a "max" set of reps, or a hold. */
    const val MAX_REPS = 50
    const val MAX_SECONDS = 180

    /**
     * An exercise counts once its ticks took at least this share of its planned time (the owner: 30%). The androidTest
     * sets it to 0 for its scripted classes, which tick in milliseconds.
     */
    @kotlin.concurrent.Volatile var timeShare = 0.3

    /** One set: [amount] reps, or seconds held when [timed], on [sides] sides, at [difficulty] 1 to 5. */
    fun set(amount: Int, timed: Boolean, sides: Int, difficulty: Int): Double =
        (if (timed) amount.toDouble() / SECONDS_PER_REP else amount.toDouble()) * sides * difficulty.coerceIn(1, 5)

    /** What the ranks are worth: every rank held on a skill, times its difficulty. */
    fun chevrons(statuses: List<SkillStatus>): Int = statuses.sumOf { it.level.ordinal * CHEVRON * it.skill.difficulty }

    /** What the classes earned; a class saved before points earns none, and "Complete day" never does. */
    fun classes(sessions: List<WorkoutSession>): Int = sessions.filterNot { it.manuallyCompleted }.sumOf { it.points ?: 0 }

    /** The UTC month a time falls in, as the leaderboard keys it ("2026-10"). */
    fun month(epochMillis: Long): String = UtcDate.of(epochMillis).let {
        "${it.year.toString().padStart(4, '0')}-${it.month.toString().padStart(2, '0')}"
    }

    /** When the UTC month of [epochMillis] began. */
    fun monthStart(epochMillis: Long): Long = UtcDate.of(epochMillis).let { UtcDate.startOfDay(it.year, it.month, 1) }
}

/**
 * A UTC calendar date from epoch milliseconds and back, by the proleptic Gregorian rules (Howard Hinnant's civil-days
 * algorithms), so the engine needs no platform calendar.
 */
internal data class UtcDate(val year: Int, val month: Int, val day: Int) {
    companion object {
        private const val DAY_MILLIS = 86_400_000L

        fun of(epochMillis: Long): UtcDate {
            val days = epochMillis.floorDiv(DAY_MILLIS) + 719_468
            val era = days.floorDiv(146_097L)
            val dayOfEra = days - era * 146_097
            val yearOfEra = (dayOfEra - dayOfEra / 1_460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
            val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
            val shifted = (5 * dayOfYear + 2) / 153
            val day = (dayOfYear - (153 * shifted + 2) / 5 + 1).toInt()
            val month = (if (shifted < 10) shifted + 3 else shifted - 9).toInt()
            val year = (yearOfEra + era * 400 + if (month <= 2) 1 else 0).toInt()
            return UtcDate(year, month, day)
        }

        /** Midnight UTC at the start of [year]-[month]-[day], in epoch milliseconds. */
        fun startOfDay(year: Int, month: Int, day: Int): Long {
            val y = (if (month <= 2) year - 1 else year).toLong()
            val era = y.floorDiv(400L)
            val yearOfEra = y - era * 400
            val dayOfYear = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
            val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
            return (era * 146_097 + dayOfEra - 719_468) * DAY_MILLIS
        }
    }
}

/** All-time points and this month's, as Skills shows the one and the leaderboard ranks both. */
data class PointsTotal(val all: Int, val month: Int, val monthKey: String)
