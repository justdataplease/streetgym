package com.justdataplease.streetgym.data.progress

/**
 * The training streak: how many days in a row the athlete finished a workout. A day is a local calendar day counted from
 * 1970-01-01, which each phone works out from its own time zone, so everything here is plain arithmetic.
 *
 * [days] is the run that ends today, or the one that ended yesterday while today is still open ([litToday] false): the
 * number is kept until the day is over. A missed day ends the run and the next workout starts at 1. [best] is the
 * longest run ever, the current one included.
 */
data class Streak(val days: Int, val best: Int, val litToday: Boolean) {
    /** One day of the week row. A day after today is [open] false: it can't have been trained yet. */
    data class Day(val epochDay: Long, val trained: Boolean, val today: Boolean, val open: Boolean)

    companion object {
        val NONE = Streak(0, 0, false)

        /**
         * A finished workout counts: the day's class, a repeat, a Library workout or one of the athlete's own, as History
         * fills its days. A day only marked complete (Complete day, Complete level, the placement) was not trained.
         */
        fun counts(session: WorkoutSession): Boolean = !session.manuallyCompleted && session.completedAtEpochMillis > 0L

        /** The streak on local day [today] from the days trained. A day after [today] (a clock set back) counts for [best] only. */
        fun of(trainedDays: Collection<Long>, today: Long): Streak {
            val days = trainedDays.toSet()
            if (days.isEmpty()) return NONE
            val lit = today in days
            var current = 0
            var day = if (lit) today else today - 1
            while (day in days) {
                current++
                day--
            }
            var best = 0
            var run = 0
            var previous = 0L
            for (trained in days.sorted()) {
                run = if (run > 0 && trained == previous + 1) run + 1 else 1
                if (run > best) best = run
                previous = trained
            }
            return Streak(current, best, lit)
        }

        /**
         * The seven days of [today]'s week, starting on [firstWeekday] (1 Monday to 7 Sunday, as the phone's region
         * starts its weeks).
         */
        fun week(trainedDays: Collection<Long>, today: Long, firstWeekday: Int = MONDAY): List<Day> {
            val days = trainedDays.toSet()
            val start = today - (weekday(today) - firstWeekday).mod(DAYS_IN_WEEK)
            return List(DAYS_IN_WEEK) { index ->
                val day = start + index
                Day(day, trained = day <= today && day in days, today = day == today, open = day <= today)
            }
        }

        /** The weekday of local day [epochDay], 1 Monday to 7 Sunday; 1970-01-01 was a Thursday. */
        fun weekday(epochDay: Long): Int = (epochDay + THURSDAY - 1).mod(DAYS_IN_WEEK) + 1

        const val MONDAY = 1
        private const val THURSDAY = 4
        private const val DAYS_IN_WEEK = 7
    }
}
