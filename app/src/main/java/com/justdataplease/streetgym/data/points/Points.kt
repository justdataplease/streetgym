package com.justdataplease.streetgym.data.points

import com.justdataplease.streetgym.data.progress.WorkoutSession
import com.justdataplease.streetgym.data.skills.SkillStatus
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

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
    @Volatile var timeShare = 0.3

    /** One set: [amount] reps, or seconds held when [timed], on [sides] sides, at [difficulty] 1 to 5. */
    fun set(amount: Int, timed: Boolean, sides: Int, difficulty: Int): Double =
        (if (timed) amount.toDouble() / SECONDS_PER_REP else amount.toDouble()) * sides * difficulty.coerceIn(1, 5)

    /** What the ranks are worth: every rank held on a skill, times its difficulty. */
    fun chevrons(statuses: List<SkillStatus>): Int = statuses.sumOf { it.level.ordinal * CHEVRON * it.skill.difficulty }

    /** What the classes earned; a class saved before points earns none, and "Complete day" never does. */
    fun classes(sessions: List<WorkoutSession>): Int = sessions.filterNot { it.manuallyCompleted }.sumOf { it.points ?: 0 }

    /** The UTC month a time falls in, as the leaderboard keys it ("2026-10"). */
    fun month(epochMillis: Long): String = utc(epochMillis).let {
        String.format(Locale.ROOT, "%04d-%02d", it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1)
    }

    /** When the UTC month of [epochMillis] began. */
    fun monthStart(epochMillis: Long): Long = utc(epochMillis).apply {
        set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun utc(epochMillis: Long): Calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT).apply { timeInMillis = epochMillis }
}

/** All-time points and this month's, as Skills shows the one and the leaderboard ranks both. */
data class PointsTotal(val all: Int, val month: Int, val monthKey: String)
