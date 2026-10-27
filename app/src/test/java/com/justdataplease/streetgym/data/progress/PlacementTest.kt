package com.justdataplease.streetgym.data.progress

import com.justdataplease.streetgym.billing.LevelAccess
import com.justdataplease.streetgym.data.model.ContentOrigin
import com.justdataplease.streetgym.data.model.LocalizedText
import com.justdataplease.streetgym.data.model.TrainingExercise
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.model.TrainingSet
import com.justdataplease.streetgym.data.progress.Placement.Question
import com.justdataplease.streetgym.data.progress.Placement.Start
import org.junit.Assert.*
import org.junit.Test

class PlacementTest {
    private val push = Question.PUSH_UPS.bands
    private val pull = Question.PULL_UPS.bands
    private val squat = Question.SQUATS.bands
    private fun every(): List<Triple<Int, Int, Int>> =
        push.flatMap { p -> pull.flatMap { u -> squat.map { s -> Triple(p, u, s) } } }
    private fun start(pushUps: Int, pullUps: Int, squats: Int) = Placement.start(pushUps, pullUps, squats)
    private val order = compareBy<Start>({ it.level }, { it.day })

    @Test fun eachQuestionOffersItsBands() {
        assertEquals(listOf("0–9", "10–19", "20–29", "30+"), push.indices.map(Question.PUSH_UPS::label))
        assertEquals(listOf("0", "1–4", "5–9", "10+"), pull.indices.map(Question.PULL_UPS::label))
        assertEquals(listOf("0–29", "30–49", "50+"), squat.indices.map(Question.SQUATS::label))
    }

    @Test fun theStartsFromBestToFirst() {
        assertEquals(listOf(Start(2, 71), Start(2), Start(1, 41), Start(1, 22), Start(1)), Placement.STARTS.map { it.start })
        // Each row's own minimums reach exactly that row.
        Placement.STARTS.forEach { row -> assertEquals(row.start, start(row.pushUps, row.pullUps, row.squats)) }
    }

    @Test fun theLevelTwoTailTakesEveryTopBand() {
        // Round 6: the strongest answers meet Level 3's German hangs, tuck levers and negatives first.
        assertEquals(Start(2, 71), start(30, 10, 50))
        // one band short on any question drops to Level 2
        assertEquals(Start(2), start(20, 10, 50))
        assertEquals(Start(2), start(30, 5, 50))
        assertEquals(Start(2), start(30, 10, 30))
    }

    @Test fun levelTwoDayOneTakesTwentyFiveAndThirty() {
        assertEquals(Start(2), start(20, 5, 30))
        assertEquals(Start(2), start(30, 5, 30))
        // short on push-ups: full push-ups but not enough for Level 1's later block
        assertEquals(Start(1, 22), start(10, 5, 30))
        // short on pull-ups: the fit adult who can't pull yet
        assertEquals(Start(1, 41), start(20, 1, 30))
        // short on squats
        assertEquals(Start(1), start(20, 5, 0))
    }

    @Test fun levelOneDayFortyOneIsTheFitAdultShortOnPullUps() {
        assertEquals(Start(1, 41), start(20, 0, 30))
        assertEquals(Start(1, 41), start(20, 1, 30))
        assertEquals(Start(1, 41), start(30, 1, 50))
        assertEquals(Start(1, 41), start(30, 0, 50))
        // pull-ups 5+ is Level 2
        assertEquals(Start(2), start(20, 5, 30))
        assertEquals(Start(1, 22), start(10, 1, 30))
        assertEquals(Start(1), start(20, 1, 0))
    }

    @Test fun levelOneDayTwentyTwoTakesTenPushUpsAndThirtySquats() {
        assertEquals(Start(1, 22), start(10, 0, 30))
        // pull-ups don't matter here
        assertEquals(Start(1, 22), start(10, 10, 50))
        assertEquals(Start(1, 22), start(10, 5, 50))
        assertEquals(Start(1), start(0, 0, 30))
        assertEquals(Start(1), start(10, 0, 0))
    }

    @Test fun levelOneDayOneIsEveryoneElse() {
        assertEquals(Start(1), start(0, 0, 0))
        assertEquals(Start(1), start(0, 10, 50))
        assertEquals(Start(1), start(30, 10, 0))
        assertEquals(Start(1), start(30, 0, 0))
    }

    @Test fun everyAnswerFollowsTheTable() {
        every().forEach { (p, u, s) ->
            val expected = when {
                p >= 30 && u >= 10 && s >= 50 -> Start(2, 71)
                p >= 20 && u >= 5 && s >= 30 -> Start(2)
                p >= 20 && u <= 4 && s >= 30 -> Start(1, 41)
                p >= 10 && s >= 30 -> Start(1, 22)
                else -> Start(1)
            }
            assertEquals("$p/$u/$s", expected, start(p, u, s))
        }
    }

    @Test fun aBetterAnswerNeverMovesTheStartBack() {
        every().forEach { (p, u, s) ->
            val here = start(p, u, s)
            push.filter { it > p }.forEach { assertTrue(order.compare(start(it, u, s), here) >= 0) }
            pull.filter { it > u }.forEach { assertTrue(order.compare(start(p, it, s), here) >= 0) }
            squat.filter { it > s }.forEach { assertTrue(order.compare(start(p, u, it), here) >= 0) }
        }
    }

    @Test fun theStartStopsAtLevelThreeAndNeverAtAPaywall() {
        assertEquals(3, Placement.MAX_LEVEL)
        assertEquals(Start(2, 71), start(500, 100, 1000))
        every().forEach { (p, u, s) ->
            val level = start(p, u, s).level
            assertTrue(level in 1..Placement.MAX_LEVEL)
            assertFalse(LevelAccess.locked(level, owned = false))
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
        "sg:l02:p0001", 1, 2, LocalizedText("Day one"), LocalizedText.EMPTY, ContentOrigin.UNKNOWN, true, false,
        1, 1, 40, exercises.toList(),
    )
}
