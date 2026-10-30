package com.justdataplease.streetgym.data.progress

import com.justdataplease.streetgym.billing.LevelAccess
import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.CurriculumJsonParser
import com.justdataplease.streetgym.data.model.LocalizedText
import com.justdataplease.streetgym.data.model.TrainingExercise
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.model.TrainingSet
import com.justdataplease.streetgym.data.progress.Placement.Start
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PlacementTest {
    /** The block starts with an exam every 20 classes: Levels 1 and 2 after each exam, Level 3 at day 1. */
    private val starts = listOf(Start(1), Start(1, 21), Start(1, 41), Start(1, 61), Start(1, 81),
        Start(2), Start(2, 21), Start(2, 41), Start(2, 61), Start(2, 81), Start(3))
    private fun start(pullUps: Int, pushUps: Int, squats: Int) = Placement.start(pullUps, pushUps, squats, starts)
    private val order = compareBy<Start>({ it.level }, { it.day })
    private fun grid() = (0..24).flatMap { u -> (0..60 step 5).flatMap { p -> (0..80 step 10).map { s -> Triple(u, p, s) } } }

    @Test fun theScoreWeighsPullUpsMostAndSquatsLeast() {
        assertEquals(0.0, Placement.score(0, 0, 0), 1e-9)
        assertEquals(1.0, Placement.score(20, 50, 75), 1e-9)
        assertEquals(1.0, Placement.score(100, 100, 100), 1e-9)
        assertEquals(0.5, Placement.score(20, 0, 0), 1e-9)
        assertEquals(0.35, Placement.score(0, 50, 0), 1e-9)
        assertEquals(0.15, Placement.score(0, 0, 75), 1e-9)
        assertEquals(1.0, Placement.Test.entries.sumOf { it.weight }, 1e-9)
    }

    @Test fun theOwnersCaseStartsAtLevelTwo() {
        // Ten pull-ups and thirty push-ups with squats never counted.
        assertEquals(Start(2), start(10, 30, 0))
        assertEquals(Start(2, 21), start(10, 30, 75))
    }

    @Test fun anchorsAlongTheProgram() {
        assertEquals(Start(1), start(0, 0, 0))
        assertEquals(Start(1, 21), start(0, 10, 20))
        assertEquals(Start(2), start(8, 25, 40))
        assertEquals(Start(2, 81), start(15, 50, 75))
        assertEquals(Start(3), start(20, 50, 75))
        assertEquals(Start(3), start(30, 60, 80))
    }

    @Test fun pullUpsAndPushUpsGateTheLevels() {
        // No pull-up: Level 1's first 41 classes at most, however many push-ups and squats.
        assertEquals(Start(1, 41), start(0, 20, 30))
        assertEquals(Start(1, 41), start(0, 100, 100))
        assertEquals(Start(1, 41), start(2, 100, 100))
        // Pull-ups alone don't open Level 2 without 20 push-ups, nor does a set of seven.
        assertEquals(Start(1, 81), start(40, 0, 0))
        assertEquals(Start(1, 81), start(7, 100, 100))
        // Level 3 takes fifteen pull-ups and 35 push-ups.
        assertEquals(Start(2, 81), start(14, 100, 100))
        assertEquals(Start(2, 81), start(20, 34, 100))
        every { u, p, s ->
            val (pull, push) = Placement.gate(start(u, p, s))
            assertTrue("$u/$p/$s", u >= pull && p >= push)
        }
    }

    @Test fun moreRepsNeverMoveTheStartBack() {
        every { u, p, s ->
            val here = start(u, p, s)
            assertTrue(order.compare(start(u + 1, p, s), here) >= 0)
            assertTrue(order.compare(start(u, p + 5, s), here) >= 0)
            assertTrue(order.compare(start(u, p, s + 10), here) >= 0)
        }
    }

    @Test fun theStartIsAlwaysABlockStartInTheFreeLevels() {
        assertEquals(3, Placement.MAX_LEVEL)
        every { u, p, s ->
            val at = start(u, p, s)
            assertTrue(at in starts)
            assertFalse(LevelAccess.locked(at.level, Tier.FREE))
        }
    }

    @Test fun theCardSaysWhatAFewMoreRepsWouldChange() {
        assertEquals(Placement.Better(Placement.Test.PULL_UPS, 4, Start(2, 21)), Placement.better(10, 30, 0, starts))
        assertNull(Placement.better(20, 50, 75, starts))
        every { u, p, s ->
            val better = Placement.better(u, p, s, starts) ?: return@every
            assertTrue("$u/$p/$s", order.compare(better.start, start(u, p, s)) > 0)
        }
    }

    @Test fun startsFollowTheExamsOfTheShippedProgram() {
        val catalog = CurriculumJsonParser.parse(File("src/main/assets/curriculum.json").readText())
        val programs = catalog.levels.sortedBy { it.number }.flatMap { level -> level.programs.sortedBy { it.number } }
        val shipped = Placement.starts(programs, TrainingProgram::levelNumber, TrainingProgram::number, TrainingProgram::isExam)
        val exams = programs.filter { it.isExam }.map { Start(it.levelNumber, it.number) }.toSet()
        assertEquals(listOf(Start(1), Start(2), Start(3)), shipped.filter { it.day == 1 })
        assertEquals(Start(3), shipped.last())
        shipped.filter { it.day > 1 }.forEach { assertTrue("$it", Start(it.level, it.day - 1) in exams) }
        assertTrue(shipped.size >= 9)
    }

    private fun every(check: (Int, Int, Int) -> Unit) = grid().forEach { (u, p, s) -> check(u, p, s) }

    @Test fun theSkillsStepSuggestsTheLevelAfterThePassedTest() {
        assertEquals(3..9, Placement.SKILL_TESTS)
        assertEquals(Start(4), Placement.skillStart(3))
        assertEquals(Start(7), Placement.skillStart(6))
        assertEquals(Start(10), Placement.skillStart(9))
    }

    @Test fun aStartPastThePlanStopsAtItsLastLevel() {
        assertEquals(3, Placement.lastOpenLevel(Tier.FREE))
        assertEquals(6, Placement.lastOpenLevel(Tier.PLUS))
        assertEquals(10, Placement.lastOpenLevel(Tier.PRO))
        assertEquals(Start(3), Placement.capped(Start(7), Tier.FREE))
        assertEquals(Start(6), Placement.capped(Start(7), Tier.PLUS))
        assertEquals(Start(7), Placement.capped(Start(7), Tier.PRO))
        assertEquals(Start(2, 21), Placement.capped(Start(2, 21), Tier.FREE))
        Tier.entries.forEach { tier ->
            Placement.SKILL_TESTS.forEach { passed ->
                assertFalse(LevelAccess.locked(Placement.capped(Placement.skillStart(passed), tier).level, tier))
            }
        }
    }

    @Test fun aStartAssumesTheExamJustBeforeIt() {
        val catalog = CurriculumJsonParser.parse(File("src/main/assets/curriculum.json").readText())
        val programs = catalog.levels.sortedBy { it.number }.flatMap { level -> level.programs.sortedBy { it.number } }
        fun assumed(start: Start) = Placement.assumed(programs, start, TrainingProgram::levelNumber, TrainingProgram::number, TrainingProgram::isExam)
            ?.let { Start(it.levelNumber, it.number) }
        assertNull(assumed(Start(1)))
        assertEquals(Start(1, 40), assumed(Start(1, 41)))
        assertEquals(Start(2, 100), assumed(Start(3)))
        // A level test is offered only to counts that pass its pull-ups and push-ups (the teacher's review 7).
        val levelThree = programs.first { it.levelNumber == 3 && it.number == 100 }
        val levelNine = programs.first { it.levelNumber == 9 && it.number == 100 }
        assertFalse(Placement.meets(levelThree, 0, 0))
        assertTrue(Placement.meets(levelThree, 30, 60))
        assertTrue(Placement.meets(levelNine, 20, 50))
        assertFalse(Placement.meets(levelNine, 10, 50))
        // Each skills-step start assumes the level test it came from, which tests every skill family of that level.
        Placement.SKILL_TESTS.forEach { passed ->
            assertEquals(Start(passed, 100), assumed(Placement.skillStart(passed)))
            assertTrue(programs.first { it.levelNumber == passed && it.number == 100 }.exercises.count { it.assessment } >= 12)
        }
    }

    @Test fun onlyANewAthleteIsAsked() {
        assertTrue("new athlete", Placement.shouldAsk(recorded = false, completedDays = 0, workoutActive = false))
        assertFalse("existing progress", Placement.shouldAsk(recorded = false, completedDays = 12, workoutActive = false))
        assertFalse("a first workout under way", Placement.shouldAsk(recorded = false, completedDays = 0, workoutActive = true))
        // answered and skipped both record placement as done
        assertFalse("already answered", Placement.shouldAsk(recorded = true, completedDays = 100, workoutActive = false))
        assertFalse("skipped", Placement.shouldAsk(recorded = true, completedDays = 0, workoutActive = false))
    }

    @Test fun aStartMarksEveryEarlierDay() {
        // three levels of fifty days: 1-1 .. 3-50
        val days = (1..3).flatMap { level -> (1..50).map { Triple("$level-$it", level, it) } }
        fun skipped(start: Start) = Placement.skipped(days, start, { it.second }, { it.third }).map { it.first }
        fun ids(level: Int, through: Int) = (1..through).map { "$level-$it" }
        assertEquals(emptyList<String>(), skipped(Start(1)))
        assertEquals(ids(1, 21), skipped(Start(1, 22)))
        assertEquals(ids(1, 40), skipped(Start(1, 41)))
        assertEquals(ids(1, 50), skipped(Start(2)))
        assertEquals(ids(1, 50) + ids(2, 50), skipped(Start(3)))
    }

    @Test fun theOpeningListsTheFirstMovementsWithTheirDose() {
        val day = program(
            exercise("Strict Pull-Up", set(reps = 4), set(reps = 4), set(reps = 4)),
            exercise("Parallel Bar Dip", set(reps = 6), set(reps = 8)),
            exercise("Strict Pull-Up", set(reps = 5)),
            exercise("L-Sit Hold", set(seconds = 10), set(seconds = 10)),
            exercise("Push-Up", set(max = true)),
        )
        assertEquals(listOf("strict pull-up 3×4", "parallel bar dip", "L-Sit hold 2×10 s"), Placement.opening(day))
        assertEquals(listOf("strict pull-up 3×4", "parallel bar dip", "L-Sit hold 2×10 s", "push-up 1×max"), Placement.opening(day, limit = 5))
    }

    @Test fun aCompoundRoundCountsEachNamedSet() {
        val day = program(exercise("Compound Exercise", set("Muscle Up", reps = 5), set("Bar Dips", reps = 12), set("Pull Up", reps = 6)))
        assertEquals(listOf("muscle up 1×5", "bar dips 1×12", "pull up 1×6"), Placement.opening(day))
    }

    private fun set(name: String? = null, reps: Int? = null, seconds: Int? = null, max: Boolean = false) =
        TrainingSet("s", 1, LocalizedText(name), LocalizedText.EMPTY, reps, max, seconds, 60)

    private fun exercise(name: String, vararg sets: TrainingSet) =
        TrainingExercise(name, null, 1, LocalizedText(name), LocalizedText.EMPTY, sets.toList())

    private fun program(vararg exercises: TrainingExercise) = TrainingProgram(
        "sg:l02:p0001", 1, 2, LocalizedText("Day one"), LocalizedText.EMPTY, true, false,
        1, 1, 40, exercises.toList(),
    )
}
