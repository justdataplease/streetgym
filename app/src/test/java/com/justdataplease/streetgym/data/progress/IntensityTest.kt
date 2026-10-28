package com.justdataplease.streetgym.data.progress

import com.justdataplease.streetgym.data.CurriculumJsonParser
import com.justdataplease.streetgym.data.model.TrainingProgram
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** "How strong do you feel?" on the shipped curriculum: strength reps and times change, sets and skill work never. */
class IntensityTest {
    @Test fun repsMoveByAQuarterAndNeverReachZero() {
        assertEquals(listOf(1, 2, 2, 3, 6, 8, 9, 15), listOf(1, 2, 3, 4, 8, 10, 12, 20).map(Intensity.EASIER::reps))
        assertEquals(listOf(1, 3, 4, 5, 10, 13, 15, 25), listOf(1, 2, 3, 4, 8, 10, 12, 20).map(Intensity.HARDER::reps))
        assertEquals(listOf(1, 8, 12), listOf(1, 8, 12).map(Intensity.NORMAL::reps))
    }

    @Test fun holdsFromTwentySecondsRoundToFive() {
        assertEquals(listOf(8, 11, 15, 20, 25, 45), listOf(10, 15, 20, 25, 30, 60).map(Intensity.EASIER::seconds))
        assertEquals(listOf(13, 19, 25, 30, 40, 75), listOf(10, 15, 20, 25, 30, 60).map(Intensity.HARDER::seconds))
    }

    @Test fun normalIsTheWrittenProgram() {
        assertSame(catalog, Intensity.NORMAL.scale(catalog))
        assertEquals(Intensity.NORMAL, Intensity.fromName(null))
        assertEquals(Intensity.HARDER, Intensity.fromName("HARDER"))
    }

    @Test fun onlyStrengthRepsAndTimesChange() {
        listOf(Intensity.EASIER, Intensity.HARDER).forEach { intensity ->
            val scaled = programs(intensity.scale(catalog))
            var changed = 0
            programs(catalog).zip(scaled).forEach { (written, now) ->
                assertEquals(written.id, now.id)
                // The warm-up, practice and cool-down keep their dose.
                assertEquals(written.warmup, now.warmup)
                assertEquals(written.practice, now.practice)
                assertEquals(written.cooldown, now.cooldown)
                written.exercises.zip(now.exercises).forEach { (before, after) ->
                    // Same sets, so skill points (which count sets) and ticks don't move.
                    assertEquals(before.sets.map { it.id }, after.sets.map { it.id })
                    if (written.isExam || !Intensity.scales(before)) assertEquals("${written.id} ${before.movementId}", before, after)
                    else before.sets.zip(after.sets).forEach { (a, b) ->
                        assertEquals(a.repetitions?.let(intensity::reps), b.repetitions)
                        assertEquals(a.durationSeconds?.let(intensity::seconds), b.durationSeconds)
                        assertEquals(a.repetitionsMax, b.repetitionsMax)
                        if (a != b) changed++
                    }
                }
            }
            assertTrue("$intensity changed $changed sets", changed > 1000)
        }
    }

    @Test fun skillWorkTestsAndExamsStayAsWritten() {
        val skill = setOf("skill_foundation", "skill", "advanced_skill", "mastery_skill", "skill_drill", "skill_strength")
        assertTrue(Intensity.STRENGTH.none(skill::contains))
        val scaled = programs(Intensity.EASIER.scale(catalog)).associateBy { it.id }
        programs(catalog).forEach { written ->
            val now = scaled.getValue(written.id)
            written.exercises.zip(now.exercises)
                .filter { (it, _) -> it.category in skill || it.readinessCheck || it.assessment || written.isExam }
                .forEach { (before, after) -> assertEquals(before, after) }
        }
        assertTrue(programs(catalog).any { it.isExam })
    }

    private fun programs(catalog: com.justdataplease.streetgym.data.model.CurriculumCatalog): List<TrainingProgram> =
        catalog.levels.sortedBy { it.number }.flatMap { level -> level.programs.sortedBy { it.number } }

    private companion object {
        val catalog by lazy { CurriculumJsonParser.parse(File("src/main/assets/curriculum.json").readText()) }
    }
}
