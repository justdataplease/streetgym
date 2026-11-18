package com.justdataplease.streetgym.app

import com.justdataplease.streetgym.app.data.AppSettings
import com.justdataplease.streetgym.app.data.Beep
import com.justdataplease.streetgym.app.data.WorkoutView
import com.justdataplease.streetgym.data.equipment.Equipment
import com.justdataplease.streetgym.data.equipment.EquipmentSwitches
import com.justdataplease.streetgym.data.skills.SkillSwitches
import com.justdataplease.streetgym.app.data.LocalDays
import com.justdataplease.streetgym.app.data.MemoryStore
import com.justdataplease.streetgym.app.data.StoredProgress
import com.justdataplease.streetgym.data.progress.ActiveWorkout
import com.justdataplease.streetgym.data.progress.Intensity
import com.justdataplease.streetgym.data.progress.LevelReplay
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
    fun eachTickKeepsItsTimeAndUntickingForgetsIt() {
        val progress = StoredProgress(MemoryStore())
        progress.startWorkout("sg:l01:p0001", startedAtEpochMillis = noon)
        progress.setCompleted(listOf("a", "b"), completed = true, at = noon + 60_000L)
        // A set ticked again keeps its first time; one ticked without a time gets the phone's, as on Android.
        progress.setCompleted(listOf("a"), completed = true, at = noon + 120_000L)
        progress.setCompleted("c", completed = true)
        progress.setCompleted(listOf("b"), completed = false, at = noon + 180_000L)

        val active = progress.activeWorkout()!!
        assertEquals(setOf("a", "c"), active.completedSetIds)
        assertEquals(noon + 60_000L, active.tickedAt["a"])
        assertTrue(active.tickedAt.getValue("c") > 0L)
        assertFalse("b" in active.tickedAt)
    }

    @Test
    fun theSettingsReadAsAndroidWritesThem() {
        val store = MemoryStore()
        val settings = AppSettings(store)
        // Unset: Android's defaults.
        assertTrue(settings.keepScreenAwake)
        assertTrue(settings.soundCues)
        assertEquals(WorkoutView.FOCUS, settings.workoutView)
        assertFalse(settings.skillSwitches.any)
        assertFalse(settings.equipment.any)
        store.set("streetgym_settings.keep_screen_awake", "false")
        store.set("streetgym_settings.sound_cues", "false")
        store.set("streetgym_settings.skill_switches", "handstand:2,planche:1")
        store.set("streetgym_settings.equipment_off", "pullup_bar,wall")
        assertFalse(settings.keepScreenAwake)
        assertFalse(settings.soundCues)
        assertEquals(SkillSwitches(mapOf("handstand" to 2, "planche" to 1)), settings.skillSwitches)
        assertEquals(EquipmentSwitches(setOf(Equipment.PULLUP_BAR, Equipment.WALL)), settings.equipment)
        settings.equipment = EquipmentSwitches()
        assertNull(store.get("streetgym_settings.equipment_off"))
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
    fun aLevelReplayIsKeptUnderAndroidsNamesAndCountsTheDaysRepeated() {
        val store = MemoryStore()
        val progress = StoredProgress(store)
        progress.completePrograms(listOf("a", "b", "c"), at = noon)
        assertNull(progress.levelReplay())

        progress.startLevelReplay(2, fromProgramId = "a")
        assertEquals("2", store.get("streetgym_progress.replay_level"))
        assertEquals("a", store.get("streetgym_progress.replay_from_program_id"))
        assertNull(store.get("streetgym_progress.replay_repeated_ids"))
        // Complete day on a day done before: no new history, but the replay moves on (Android: putRepeated).
        progress.completePrograms(listOf("a"), at = noon + 1)
        assertEquals(3, progress.sessionHistory().size)
        assertEquals("[\"a\"]", store.get("streetgym_progress.replay_repeated_ids"))
        // A class trained counts too; a repeat changes nothing.
        progress.startWorkout("b", startedAtEpochMillis = noon + 2)
        progress.finishWorkout(completedAtEpochMillis = noon + 3)
        progress.startWorkout("c", startedAtEpochMillis = noon + 4, repeat = true)
        progress.finishWorkout(completedAtEpochMillis = noon + 5)
        assertEquals(LevelReplay(2, setOf("a", "b"), "a"), StoredProgress(store).levelReplay())

        progress.endLevelReplay()
        assertNull(progress.levelReplay())
        assertNull(store.get("streetgym_progress.replay_level"))
        assertNull(store.get("streetgym_progress.replay_repeated_ids"))
        assertNull(store.get("streetgym_progress.replay_from_program_id"))
        // A level gone back to keeps no start class.
        progress.startLevelReplay(1)
        assertEquals(LevelReplay(1), progress.levelReplay())
    }

    @Test
    fun theBeepIsAndroidsToneAsAWaveFile() {
        val wav = Beep.wav()
        fun text(at: Int) = wav.copyOfRange(at, at + 4).decodeToString()
        fun int(at: Int) = (0 until 4).sumOf { (wav[at + it].toInt() and 0xFF) shl (8 * it) }
        assertEquals("RIFF", text(0))
        assertEquals("WAVE", text(8))
        assertEquals("fmt ", text(12))
        assertEquals("data", text(36))
        assertEquals(Beep.SAMPLE_RATE, int(24))
        val samples = Beep.SAMPLE_RATE * Beep.MILLIS / 1_000
        assertEquals(samples * 2, int(40))
        assertEquals(44 + samples * 2, wav.size)
        assertEquals(wav.size - 8, int(4))
        // It starts and ends quiet and is loud in between, never clipped.
        val pcm = (0 until samples).map { (wav[44 + 2 * it].toInt() and 0xFF) or (wav[45 + 2 * it].toInt() shl 8) }
        assertEquals(0, pcm.first())
        assertTrue(pcm.maxOf { kotlin.math.abs(it) } in 20_000..Short.MAX_VALUE.toInt())
        assertTrue(kotlin.math.abs(pcm.last()) < 2_000)
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
