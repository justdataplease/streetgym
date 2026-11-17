package com.justdataplease.streetgym.data.progress

/**
 * The training streak: how many workout days the athlete has strung together. A day is a local calendar day counted
 * from 1970-01-01, which each phone works out from its own time zone, so everything here is plain arithmetic.
 *
 * One rest day between two workouts keeps the streak (train, rest, train). Two missed days in a row end it and the next
 * workout starts at 1. Rest days are not counted: [days] is the number of days trained in the run, and it only goes up
 * with a workout.
 *
 * The run is still alive on a day not trained yet ([litToday] false). After a workout yesterday, today may be the rest
 * day ([restToday]); after a rest day yesterday, today's workout is due and the number is kept until the day is over.
 * [best] is the longest run ever, the current one included.
 */
data class Streak(val days: Int, val best: Int, val litToday: Boolean, val restToday: Boolean = false) {
    /**
     * One day of the week row. A day after today is [open] false: it can't have been trained yet. [rest] is a day off
     * that the streak carried on over: the day before was trained, and the day after was too, or is today.
     */
    data class Day(val epochDay: Long, val trained: Boolean, val today: Boolean, val open: Boolean, val rest: Boolean = false)

    companion object {
        val NONE = Streak(0, 0, false)

        /**
         * A finished workout counts: the day's class, a repeat, a Library workout or one of the athlete's own, as History
         * fills its days. A day only marked complete (Complete day, Complete level, the placement) was not trained.
         */
        fun counts(session: WorkoutSession): Boolean = !session.manuallyCompleted && session.completedAtEpochMillis > 0L

        /** The streak on local day [today] from the days trained. A day after [today] (a clock set back) counts for [best] only. */
        fun of(trainedDays: Collection<Long>, today: Long): Streak {
            if (trainedDays.isEmpty()) return NONE
            var best = 0
            var run = 0
            var previous = 0L
            // The run that ends on the last day trained up to today, and that day.
            var current = 0
            var last = Long.MIN_VALUE
            for (trained in trainedDays.toSet().sorted()) {
                run = if (run > 0 && trained - previous <= REACH) run + 1 else 1
                if (run > best) best = run
                previous = trained
                if (trained <= today) {
                    current = run
                    last = trained
                }
            }
            if (last == Long.MIN_VALUE || today - last > REACH) return Streak(0, best, false)
            return Streak(current, best, litToday = last == today, restToday = today - last == 1L)
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
                val trained = day <= today && day in days
                val rest = !trained && day < today && day - 1 in days && (day + 1 in days || day + 1 == today)
                Day(day, trained, today = day == today, open = day <= today, rest = rest)
            }
        }

        /** The weekday of local day [epochDay], 1 Monday to 7 Sunday; 1970-01-01 was a Thursday. */
        fun weekday(epochDay: Long): Int = (epochDay + THURSDAY - 1).mod(DAYS_IN_WEEK) + 1

        const val MONDAY = 1

        /** The furthest the next workout day can be from the last one: one rest day between them. */
        private const val REACH = 2L
        private const val THURSDAY = 4
        private const val DAYS_IN_WEEK = 7
    }
}
