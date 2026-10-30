package com.justdataplease.streetgym.data.progress

import com.justdataplease.streetgym.data.CurriculumJsonParser
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ExamLogTest {
    private val first = ExamResult("sg:l04:p0013", 1_000L, listOf("pullup", "straight_bar_dip"), listOf("tuck_planche"))
    private val retake = ExamResult("sg:l04:p0013", 5_000L, listOf("pullup", "straight_bar_dip", "tuck_planche"), emptyList())

    @Test fun anExamPassesWhenNothingWasMissed() {
        assertFalse(first.passed)
        assertTrue(retake.passed)
    }

    @Test fun onlyTheHeadlineOrThreeMissesOfferARepeat() {
        assertFalse(first.offersRepeat(headline = "pullup"))
        assertTrue(first.offersRepeat(headline = "tuck_planche"))
        assertFalse(first.offersRepeat(headline = null))
        val three = first.copy(missed = listOf("tuck_planche", "pushup", "squat"))
        assertTrue(three.offersRepeat(headline = "pullup"))
        assertFalse(retake.offersRepeat(headline = "tuck_planche"))
    }

    @Test fun everyShippedExamNamesAHeadlineItTests() {
        val catalog = CurriculumJsonParser.parse(File("src/main/assets/curriculum.json").readText())
        val exams = catalog.levels.flatMap { it.programs }.filter { it.isExam && it.dayType != "exam_level_full_battle" }
        assertEquals(49, exams.size)
        exams.forEach { exam ->
            val tests = exam.exercises.filter { it.assessment }.map { it.movementId }
            assertTrue(exam.id, exam.headline != null && exam.headline in tests)
            // From Level 4 an exam tests every skill family the block trained: twelve tests or more (review 6).
            if (exam.levelNumber >= 4) assertTrue("${exam.id}: ${tests.size}", tests.size in 12..15)
        }
    }

    @Test fun theLatestAttemptCounts() {
        val log = ExamLog().record(retake).record(first)
        assertEquals(retake, log.latest("sg:l04:p0013"))
        assertNull(log.latest("sg:l04:p0034"))
        assertEquals(listOf(first, retake), log.results)
    }

    @Test fun encodingRoundTrips() {
        val log = ExamLog().record(first).record(retake)
        assertEquals(log, ExamLog.decode(log.encode()))
        assertEquals(ExamLog(), ExamLog.decode(null))
        assertEquals(ExamLog(), ExamLog.decode("not json"))
    }

    @Test fun aMergeKeepsEveryAttemptOnce() {
        val phone = ExamLog().record(first)
        val cloud = ExamLog().record(first).record(retake)
        assertEquals(listOf(first, retake), phone.merge(cloud).results)
        assertEquals(phone.merge(cloud), cloud.merge(phone))
    }
}
