package com.justdataplease.streetgym.app

import com.justdataplease.streetgym.app.data.LocalDays
import com.justdataplease.streetgym.app.data.MemoryStore
import com.justdataplease.streetgym.app.data.StoredProgress
import com.justdataplease.streetgym.data.progress.ActiveWorkout
import com.justdataplease.streetgym.data.progress.Intensity
import com.justdataplease.streetgym.data.progress.WorkoutSession
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoredProgressTest {
    private val day = 86_400_000L
    private val noon = 20_775 * day + 12 * 3_600_000L // 18 Nov 2026, 12:00 UTC

    @Test
    fun aFinishedClassIsCompletedAndInTheHistory() {
        val store = MemoryStore()
        val progress = StoredProgress(store)
        progress.startWorkout("sg:l01:p0001", startedAtEpochMillis = noon)
        progress.setCompleted("main:a-0:s1-0", completed = true)
        val session = progress.finishWorkout(completedAtEpochMillis = noon + 1_800_000L, effortRating = 6, points = 120)

        assertEquals(1_800L, session?.durationSeconds)
        assertNull(progress.activeWorkout())
        assertTrue(progress.isProgramCompleted("sg:l01:p0001"))
        // A second reader of the same store sees what the first saved.
        assertEquals(listOf(session), StoredProgress(store).sessionHistory())
    }

    @Test
    fun sessionsAreWrittenAsAndroidWritesThem() {
        val session = WorkoutSession("sg:l01:p0001", 1_000L, 61_000L, 60L, effortRating = 6, points = 120,
            credits = mapOf("pull_up" to 3, "dip" to 2), doses = mapOf("pull_up" to listOf(5, 5, 4)))
        val text = StoredProgress.encodeSessions(listOf(session))
        assertEquals(
            """[{"program_id":"sg:l01:p0001","started_at_epoch_millis":1000,"completed_at_epoch_millis":61000,""" +
                """"duration_seconds":60,"manually_completed":false,"repeat":false,"effort_rating":6,""" +
                """"credits":{"dip":2,"pull_up":3},"doses":{"pull_up":[5,5,4]},"points":120}]""",
            text,
        )
        assertEquals(listOf(session), StoredProgress.decodeSessions(text))
    }

    @Test
    fun aSessionSavedByAnOlderAndroidVersionReads() {
        val old = """[{"program_id":"sg:l01:p0002","started_at_epoch_millis":5,"completed_at_epoch_millis":9,"effort_rating":null}]"""
        assertEquals(listOf(WorkoutSession("sg:l01:p0002", 5L, 9L, 0L)), StoredProgress.decodeSessions(old))
    }

    @Test
    fun theClassInProgressKeepsItsTicksAndAmounts() {
        val active = ActiveWorkout("sg:l02:p0101", 7L, setOf("b", "a"), repeat = true, easierExercises = setOf("main:x-1"),
            intensity = Intensity.HARDER, done = mapOf("a" to 8), tickedAt = mapOf("a" to 70L, "b" to 90L))
        assertEquals(active, StoredProgress.decodeActive(StoredProgress.encode(active)))
    }

    @Test
    fun completeDayMovesOnButIsNotTraining() {
        val progress = StoredProgress(MemoryStore())
        progress.completePrograms(listOf("sg:l01:p0001"), at = noon)

        assertTrue(progress.isProgramCompleted("sg:l01:p0001"))
        assertTrue(progress.sessionHistory().single().manuallyCompleted)
        assertEquals(0, LocalDays.snapshot(progress.sessionHistory(), noon, TimeZone.UTC).streak.days)
    }

    @Test
    fun theStreakCountsLocalDaysWithOneRestDayBetween() {
        val progress = StoredProgress(MemoryStore())
        // Trained three days ago, two days ago and today: yesterday was the rest day.
        listOf(3, 2, 0).forEachIndexed { index, daysAgo ->
            progress.startWorkout("p$index", startedAtEpochMillis = noon - daysAgo * day - 1_000L)
            progress.finishWorkout(completedAtEpochMillis = noon - daysAgo * day)
        }
        val snapshot = LocalDays.snapshot(progress.sessionHistory(), noon, TimeZone.UTC)
        assertEquals(3, snapshot.streak.days)
        assertTrue(snapshot.streak.litToday)
        assertEquals(listOf(true, false, true), snapshot.week.take(3).map { it.trained })
        assertTrue(snapshot.week[1].rest)
    }

    @Test
    fun aDayTurnsAtLocalMidnight() {
        val athens = TimeZone.of("Europe/Athens") // UTC+2 in November
        val lateEvening = 20_775 * day + 21 * 3_600_000L + 59 * 60_000L // 21:59 UTC = 23:59 in Athens
        assertEquals(20_775L, LocalDays.epochDay(lateEvening, athens))
        assertEquals(20_776L, LocalDays.epochDay(lateEvening + 60_000L, athens))
        assertFalse(LocalDays.epochDay(lateEvening + 60_000L, TimeZone.UTC) == 20_776L)
    }
}
