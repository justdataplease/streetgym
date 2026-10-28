package com.justdataplease.streetgym.data.progress

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.justdataplease.streetgym.data.skills.SkillCurriculumReader
import com.justdataplease.streetgym.data.skills.SkillGroup
import com.justdataplease.streetgym.leaderboard.Scores
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Repeating a completed day, on the real repository and the shipped curriculum: history only, nothing it counts toward. */
class RepeatDayTest {
    private data class Day(val id: String, val level: Int)

    private val preferences = MemoryPreferences()
    private val repository = SharedPreferencesProgressRepository(PreferencesContext(preferences))

    private fun train(day: Day, at: Long, repeat: Boolean = false, effort: Int = 7): WorkoutSession? {
        repository.startWorkout(day.id, at, repeat)
        repository.setCompleted("${day.id}:first-set", true)
        return repository.finishWorkout(at + 30 * MINUTE, effort)
    }
    private fun today() = DayAssignment.next(days, repository.completedProgramIds(), repository.levelReplay(),
        Day::id, Day::level)
    private fun skills() = book.progress(Scores.credits(repository.sessionHistory()), repository.skillTests().passed.keys)
    private fun levelProgress(level: Int) = days.count { it.level == level && it.id in repository.completedProgramIds() }

    @Test fun aRepeatChangesNoCompletedDayTodayLevelProgressOrSkillPoints() {
        days.take(12).forEachIndexed { index, day -> train(day, index * DAY) }
        val completed = repository.completedProgramIds()
        val today = today()
        val points = skills()
        val level = levelProgress(1)

        train(days[3], 20 * DAY, repeat = true, effort = 9)

        assertEquals(completed, repository.completedProgramIds())
        assertEquals(today, today())
        assertEquals(days[12], today())
        assertEquals(level, levelProgress(1))
        assertEquals(points, skills())
        assertEquals(points.achievedCount, skills().achievedCount)
        assertEquals(points.overall.score, skills().overall.score)
        SkillGroup.entries.forEach { assertEquals(it.name, points.overall.group(it), skills().overall.group(it)) }
    }

    @Test fun historyKeepsTheRepeatFlaggedWithItsOwnRating() {
        train(days[0], 0L)
        repository.startWorkout(days[0].id, DAY, repeat = true)
        repository.setCompleted("set", true)
        assertEquals(ActiveWorkout(days[0].id, DAY, setOf("set"), repeat = true), repository.activeWorkout())
        val session = repository.finishWorkout(DAY + 40 * MINUTE, effortRating = 9)

        assertNull(repository.activeWorkout())
        assertTrue(session!!.repeat)
        val history = repository.sessionHistory()
        assertEquals(2, history.size)
        assertEquals(WorkoutSession(days[0].id, DAY, DAY + 40 * MINUTE, 40 * 60L, 9, repeat = true), history.first())
        assertFalse(history.last().repeat)
        assertEquals(1, history.count { !it.repeat })
    }

    @Test fun aRepeatLeavesALevelReplayWhereItWas() {
        val one = days.filter { it.level == 1 }
        val two = days.filter { it.level == 2 }
        repository.completePrograms(one.map(Day::id), at = 0L)
        train(two[0], DAY); train(two[1], 2 * DAY)
        repository.startLevelReplay(1)
        // A normal class on Today's replayed day moves the replay on, as before.
        train(one[0], 3 * DAY)
        val replay = repository.levelReplay()
        assertEquals(LevelReplay(1, setOf(one[0].id)), replay)
        assertEquals(one[1], today())
        val completed = repository.completedProgramIds()

        // Repeating another day, or even Today's own replayed day, moves nothing.
        train(one[40], 4 * DAY, repeat = true)
        train(one[1], 5 * DAY, repeat = true)

        assertEquals(replay, repository.levelReplay())
        assertEquals(one[1], today())
        assertEquals(completed, repository.completedProgramIds())
        assertEquals(2, repository.sessionHistory().count { it.repeat })
    }

    @Test fun onlyACompletedDayCanBeRepeated() {
        assertThrows(IllegalArgumentException::class.java) { repository.startWorkout(days[0].id, 0L, repeat = true) }
        train(days[0], 0L)
        assertThrows(IllegalArgumentException::class.java) { repository.startWorkout(days[1].id, DAY, repeat = true) }
        assertNull(repository.activeWorkout())
    }

    @Test fun completingOrEndingAroundARepeatCountsOnlyTheDaysCompleted() {
        train(days[0], 0L)
        repository.startWorkout(days[0].id, DAY, repeat = true)
        repository.setCompleted("set", true)
        // "Complete day" on Today: the repeat in progress is of a day already done, so it keeps running.
        repository.completePrograms(listOf(days[0].id, days[1].id), at = DAY)
        assertEquals(ActiveWorkout(days[0].id, DAY, setOf("set"), repeat = true), repository.activeWorkout())
        repository.abandonWorkout()

        assertEquals(setOf(days[0].id, days[1].id), repository.completedProgramIds())
        assertEquals(2, repository.sessionHistory().size)
        assertTrue(repository.sessionHistory().none { it.repeat })
    }

    @Test fun aFinishedClassKeepsItsTicksAndEasierOptions() {
        repository.startWorkout(days[0].id, 0L)
        repository.setEasier("main:e1-0", true)
        repository.setEasier("main:e2-1", true)
        repository.setEasier("main:e2-1", false)
        assertEquals(setOf("main:e1-0"), repository.activeWorkout()!!.easierExercises)
        val session = repository.finishWorkout(30 * MINUTE, 7, credits = mapOf("pushup" to 2, "squat" to 0))
        assertEquals(mapOf("pushup" to 2), session!!.credits)
        assertEquals(mapOf("pushup" to 2), repository.sessionHistory().single().credits)
    }

    @Test fun skillTestsAreKeptAndMergedWithTheCloudCopy() {
        repository.recordSkillTest("lsit", passed = true, at = 10)
        repository.recordSkillTest("tuck_planche", passed = false, at = 20)
        assertEquals(setOf("lsit"), repository.skillTests().passed.keys)
        val cloud = SkillTestLog().record("dragon_flag", true, 5).encode()
        val merged = repository.mergeCloud(emptyList(), null, cloud)
        assertEquals(setOf("lsit", "dragon_flag"), repository.skillTests().passed.keys)
        assertEquals(repository.skillTests(), SkillTestLog.decode(merged.tests))
    }

    @Test fun dataSavedBeforeRepeatsReadsAsNormalSessions() {
        preferences.edit()
            .putStringSet("completed_program_ids", setOf(days[0].id))
            .putString("session_history", """[{"program_id":"${days[0].id}","started_at_epoch_millis":1,""" +
                """"completed_at_epoch_millis":2,"duration_seconds":0,"manually_completed":false,"effort_rating":6}]""")
            .putString("active_workout", """{"program_id":"${days[1].id}","started_at_epoch_millis":3,"completed_set_ids":["s"]}""")
            .apply()

        val old = repository.sessionHistory().single()
        assertFalse(old.repeat)
        // Saved before ticks were kept: its prescription counts.
        assertNull(old.credits)
        assertTrue(repository.activeWorkout()!!.easierExercises.isEmpty())
        assertEquals(6, old.effortRating)
        assertFalse(repository.activeWorkout()!!.repeat)
        repository.finishWorkout(4L, 7)
        assertEquals(setOf(days[0].id, days[1].id), repository.completedProgramIds())
        assertEquals(days[2], today())
        assertTrue(repository.sessionHistory().none { it.repeat })
    }

    private companion object {
        const val MINUTE = 60_000L
        const val DAY = 24 * 60 * MINUTE
        val curriculum: String by lazy { File("src/main/assets/curriculum.json").readText() }
        val book by lazy { SkillCurriculumReader.read(curriculum) }
        /** The whole sequence in order, as Today walks it. */
        val days: List<Day> by lazy {
            val levels = JSONObject(curriculum).getJSONArray("levels")
            (0 until levels.length()).map(levels::getJSONObject).sortedBy { it.getInt("number") }.flatMap { level ->
                val programs = level.getJSONArray("programs")
                (0 until programs.length()).map(programs::getJSONObject).sortedBy { it.optInt("number") }
                    .map { Day(it.getString("id"), level.getInt("number")) }
            }
        }
    }
}

internal class PreferencesContext(private val preferences: SharedPreferences) : ContextWrapper(null) {
    override fun getApplicationContext(): Context = this
    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = preferences
}

/** SharedPreferences in memory, as much as the repository uses: every edit lands at once. */
internal class MemoryPreferences : SharedPreferences {
    private val stored = HashMap<String, Any>()

    override fun getAll(): Map<String, *> = HashMap(stored)
    override fun getString(key: String?, defValue: String?): String? = stored[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (stored[key] as? Set<String>)?.toMutableSet() ?: defValues
    override fun getInt(key: String?, defValue: Int): Int = stored[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = stored[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = stored[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = stored[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = key in stored
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val changes = LinkedHashMap<String, Any?>()
        private var clear = false

        private fun put(key: String?, value: Any?): SharedPreferences.Editor {
            changes[checkNotNull(key)] = value
            return this
        }
        override fun putString(key: String?, value: String?) = put(key, value)
        override fun putStringSet(key: String?, values: MutableSet<String>?) = put(key, values?.toSet())
        override fun putInt(key: String?, value: Int) = put(key, value)
        override fun putLong(key: String?, value: Long) = put(key, value)
        override fun putFloat(key: String?, value: Float) = put(key, value)
        override fun putBoolean(key: String?, value: Boolean) = put(key, value)
        override fun remove(key: String?) = put(key, null)
        override fun clear(): SharedPreferences.Editor {
            clear = true
            return this
        }
        override fun commit(): Boolean {
            if (clear) stored.clear()
            changes.forEach { (key, value) -> if (value == null) stored.remove(key) else stored[key] = value }
            return true
        }
        override fun apply() {
            commit()
        }
    }
}
