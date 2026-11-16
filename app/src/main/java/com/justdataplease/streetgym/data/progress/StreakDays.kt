package com.justdataplease.streetgym.data.progress

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

/**
 * The [Streak] from this phone's history and clock. A day is the local date in the phone's time zone, as History's
 * calendar splits them ([HistoryCalendar.dayKey]), so the flame and the calendar's filled days always agree.
 */
object StreakDays {
    /** The streak now, with the seven days of this week for the week row. */
    class Snapshot(val streak: Streak, val week: List<Streak.Day>)

    /** The local calendar day of [millis] in [zone], as days since 1970-01-01; it turns at local midnight. */
    fun epochDay(millis: Long, zone: TimeZone): Long = (millis + zone.getOffset(millis)).floorDiv(DAY_MILLIS)

    /** The local days with a workout finished ([Streak.counts]). */
    fun trained(sessions: List<WorkoutSession>, zone: TimeZone): Set<Long> =
        sessions.filter(Streak::counts).mapTo(HashSet()) { epochDay(it.completedAtEpochMillis, zone) }

    /** [firstDayOfWeek] is [Calendar.SUNDAY] to [Calendar.SATURDAY]: where the phone's region starts its weeks. */
    fun snapshot(
        sessions: List<WorkoutSession>,
        now: Long = System.currentTimeMillis(),
        zone: TimeZone = TimeZone.getDefault(),
        firstDayOfWeek: Int = Calendar.getInstance(Locale.getDefault()).firstDayOfWeek,
    ): Snapshot {
        val days = trained(sessions, zone)
        val today = epochDay(now, zone)
        return Snapshot(Streak.of(days, today), Streak.week(days, today, isoWeekday(firstDayOfWeek)))
    }

    /** [Calendar.SUNDAY]..[Calendar.SATURDAY] as [Streak]'s 1 Monday to 7 Sunday. */
    fun isoWeekday(calendarDay: Int): Int = (calendarDay + 5) % 7 + 1

    /** [Streak]'s 1 Monday to 7 Sunday as [Calendar.SUNDAY]..[Calendar.SATURDAY]. */
    fun calendarDay(isoWeekday: Int): Int = isoWeekday % 7 + 1

    /** The first instant of the local day after [now]: when an unlit flame's day is over. */
    fun nextMidnight(now: Long, zone: TimeZone): Long = GregorianCalendar(zone).apply {
        timeInMillis = now
        add(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private const val DAY_MILLIS = 86_400_000L
}
