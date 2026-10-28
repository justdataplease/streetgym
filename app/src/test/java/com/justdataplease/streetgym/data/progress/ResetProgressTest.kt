package com.justdataplease.streetgym.data.progress

import org.junit.Assert.*
import org.junit.Test

/** Settings' Reset progress on the real repository, and a reset arriving from another of the athlete's devices. */
class ResetProgressTest {
    private val repository = SharedPreferencesProgressRepository(PreferencesContext(MemoryPreferences()))

    private fun train(day: String, at: Long) {
        repository.startWorkout(day, at, repeat = false)
        repository.setCompleted("$day:first-set", true)
        repository.finishWorkout(at + 30 * MINUTE, 7)
    }

    @Test fun aResetErasesEverythingTrainedAndRemembersWhen() {
        train("sg:l01:p0001", 0)
        train("sg:l01:p0002", DAY)
        repository.completePrograms(listOf("sg:l01:p0003"), 2 * DAY)
        repository.recordSkillTest("pullup", passed = true, at = 2 * DAY)
        repository.recordExam(ExamResult("sg:l01:p0020", 2 * DAY, listOf("pullup"), emptyList()))
        repository.startLevelReplay(1)
        repository.startWorkout("sg:l01:p0004", 3 * DAY, repeat = false)

        repository.reset(4 * DAY)

        assertTrue(repository.completedProgramIds().isEmpty())
        assertTrue(repository.sessionHistory().isEmpty())
        assertEquals(SkillTestLog(), repository.skillTests())
        assertEquals(ExamLog(), repository.examLog())
        assertNull(repository.levelReplay())
        assertNull(repository.activeWorkout())
        assertEquals(4 * DAY, repository.resetAt())
    }

    @Test fun aResetFromAnotherDeviceLeavesTheClassInProgress() {
        train("sg:l01:p0001", 0)
        repository.startWorkout("sg:l01:p0002", DAY, repeat = false)
        val active = repository.activeWorkout()

        repository.reset(2 * DAY, keepActiveWorkout = true)

        assertEquals(active, repository.activeWorkout())
        assertTrue(repository.completedProgramIds().isEmpty())
        assertTrue(repository.sessionHistory().isEmpty())
    }

    @Test fun classesAfterAResetCountAgain() {
        train("sg:l01:p0001", 0)
        repository.reset(DAY)
        train("sg:l01:p0001", 2 * DAY)

        assertEquals(setOf("sg:l01:p0001"), repository.completedProgramIds())
        assertEquals(1, repository.sessionHistory().size)
        assertEquals(DAY, repository.resetAt())
    }

    private companion object {
        const val MINUTE = 60_000L
        const val DAY = 24 * 60 * MINUTE
    }
}
