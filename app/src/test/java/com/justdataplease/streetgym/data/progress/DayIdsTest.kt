package com.justdataplease.streetgym.data.progress

import com.justdataplease.streetgym.data.CurriculumJsonParser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Day ids are frozen: an athlete's completed days, history, exams, placement and cloud copy all refer to them. A day's
 * id is its level and number ("sg:l02:p0045"), so a curriculum change edits days in place. Inserting, removing or
 * reordering days, or moving an exam, would shift every later day under the athletes already past it; this test fails
 * first.
 */
class DayIdsTest {
    @Test fun everyLevelKeepsItsHundredDaysAndTheirIds() {
        val levels = catalog.levels.sortedBy { it.number }
        assertEquals((1..10).toList(), levels.map { it.number })
        levels.forEach { level ->
            assertEquals("sg:l%02d".format(level.number), level.id)
            val days = level.programs.sortedBy { it.number }
            assertEquals(level.id, (1..100).map { "sg:l%02d:p%04d".format(level.number, it) }, days.map { it.id })
            assertEquals(level.id, (1..100).toList(), days.map { it.number })
        }
    }

    @Test fun examsStayOnTheirDays() {
        val exams = catalog.levels.sortedBy { it.number }
            .associate { level -> level.number to level.programs.filter { it.isExam }.map { it.number }.sorted() }
        assertEquals(FROZEN_EXAMS, exams)
    }

    private companion object {
        val catalog by lazy { CurriculumJsonParser.parse(File("src/main/assets/curriculum.json").readText()) }

        val FROZEN_EXAMS = mapOf(
            1 to listOf(20, 40, 60, 81, 100),
            2 to listOf(20, 40, 62, 84, 100),
            3 to listOf(20, 40, 64, 84, 100),
            4 to listOf(13, 34, 55, 76, 100),
            5 to listOf(13, 34, 55, 76, 100),
            6 to listOf(13, 34, 55, 76, 100),
            7 to listOf(13, 34, 55, 76, 100),
            8 to listOf(13, 34, 55, 76, 100),
            9 to listOf(13, 34, 55, 76, 100),
            10 to listOf(13, 34, 55, 76, 100),
        )
    }
}
