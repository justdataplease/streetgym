package com.justdataplease.streetgym.app

import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.FocusPage
import com.justdataplease.streetgym.app.data.KeyValueStore
import com.justdataplease.streetgym.app.data.MemoryStore
import com.justdataplease.streetgym.app.data.Program
import com.justdataplease.streetgym.app.data.Start
import com.justdataplease.streetgym.app.data.StoredProgress
import com.justdataplease.streetgym.app.data.Training
import com.justdataplease.streetgym.app.data.WorkoutDevice
import com.justdataplease.streetgym.app.data.loadProgram
import com.justdataplease.streetgym.app.ui.clockText
import com.justdataplease.streetgym.app.ui.pointsText
import com.justdataplease.streetgym.data.model.TrainingExercise
import com.justdataplease.streetgym.data.progress.Intensity
import com.justdataplease.streetgym.data.progress.NextClass
import com.justdataplease.streetgym.ui.WorkoutFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Training a class over the real program, with a clock the tests step: starting it, ticking its sets, the rest between
 * them, finishing (points, skills and the streak as the engine counts them), ending it, and coming back to it after the
 * app was closed.
 */
class TrainingTest {
    /** 18 Nov 2026, 12:00 UTC. */
    private val noon = 20_775 * 86_400_000L + 12 * 3_600_000L

    private class Clock(var time: Long) {
        fun step(millis: Long) { time += millis }
    }

    /** Counts what the class asked of the phone. */
    private class Phone : WorkoutDevice {
        var rests = 0
        var holds = 0
        override fun restOver() { rests++ }
        override fun holdOver() { holds++ }
    }

    private fun model(clock: Clock, store: KeyValueStore = MemoryStore(), phone: WorkoutDevice = Phone()) =
        AppModel(program, StoredProgress(store), now = { clock.time }, zone = TimeZone.UTC, device = phone)

    private fun page(model: AppModel) = model.training.state()?.page

    /** Ticks the sets from the current one up to [until] (not included), a set every [every] ms, skipping each rest. */
    private fun tickUpTo(model: AppModel, clock: Clock, until: Int, every: Long = 30_000L) {
        val training = model.training
        while (true) {
            val index = (page(model) as? FocusPage.Set)?.index ?: return
            if (index >= until) return
            clock.step(every)
            training.done(index)
            training.skipRest()
        }
    }

    @Test
    fun startingTodaysClassOpensItsFirstSet() {
        val clock = Clock(noon)
        val model = model(clock)
        val first = model.today().next!!

        assertEquals(Start.STARTED, model.start(first.id))
        val state = model.training.state()!!
        assertEquals(FocusPage.Set(0), state.page)
        assertEquals(first.id, state.active.programId)
        assertEquals(noon, state.active.startedAtEpochMillis)
        assertTrue(model.today().resume)
        assertEquals(state.flow.keys, WorkoutFlow.of(first.id, Training.sections(first)).keys)

        // Asking again resumes it; a class not reached yet does not start.
        clock.step(60_000L)
        assertEquals(Start.STARTED, model.start(first.id))
        assertEquals(noon, model.training.active()!!.startedAtEpochMillis)
        assertEquals(Start.LOCKED, model.start(NextClass.ordered(program.catalog)[1].id))
    }

    @Test
    fun setsAreTickedInOrderEachWithTheTimeItWasTicked() {
        val clock = Clock(noon)
        val model = model(clock)
        val first = model.today().next!!
        model.start(first.id)
        val flow = model.training.state()!!.flow

        tickUpTo(model, clock, until = 3)

        val active = model.training.active()!!
        assertEquals(flow.keys.take(3).toSet(), active.completedSetIds)
        assertEquals(flow.keys.take(3).mapIndexed { index, key -> key to noon + (index + 1) * 30_000L }.toMap(), active.tickedAt)
        assertEquals(FocusPage.Set(3), page(model))
        // A second reader of the store sees the same ticks: they are saved at once.
        assertEquals(active, StoredProgress.decodeActive(StoredProgress.encode(active)))
    }

    @Test
    fun theRestCountsDownWithTheClockAndEndsWithItsCue() {
        val clock = Clock(noon)
        val phone = Phone()
        val model = model(clock, phone = phone)
        val training = model.training
        model.start(model.today().next!!.id)
        val flow = training.state()!!.flow
        val rested = flow.steps.indices.first { (flow[it].set.breakSeconds ?: 0) > 0 && flow.next(it) != null }
        val pause = flow[rested].set.breakSeconds!! * 1_000L

        tickUpTo(model, clock, until = rested)
        clock.step(20_000L)
        training.done(rested)
        val started = clock.time
        assertEquals(FocusPage.Rest(rested + 1, started + pause), page(model))
        assertEquals(clockText(pause, roundUp = true), clockText(started + pause - clock.time, roundUp = true))

        // A millisecond before its end the rest still runs, showing its last second.
        clock.step(pause - 1)
        training.tick()
        assertTrue(page(model) is FocusPage.Rest)
        assertEquals("00:01", clockText(started + pause - clock.time, roundUp = true))
        assertEquals(0, phone.rests)

        // +15 s, then the end: the next set, and one cue.
        training.addRest(15)
        assertEquals(FocusPage.Rest(rested + 1, started + pause + 15_000L), page(model))
        clock.step(15_000L)
        training.tick()
        assertTrue(page(model) is FocusPage.Rest)
        clock.step(1L)
        training.tick()
        assertEquals(FocusPage.Set(rested + 1), page(model))
        assertEquals(1, phone.rests)
        clock.step(5_000L)
        training.tick()
        assertEquals(1, phone.rests)

        // Skip rest ends it at once, without the cue.
        val again = flow.steps.indices.first { it > rested && (flow[it].set.breakSeconds ?: 0) > 0 && flow.next(it) != null }
        tickUpTo(model, clock, until = again)
        training.done(again)
        assertTrue(page(model) is FocusPage.Rest)
        training.skipRest()
        assertEquals(FocusPage.Set(again + 1), page(model))
        assertEquals(1, phone.rests)
    }

    @Test
    fun aTimedSetCountsDownAndCuesItsEndOnce() {
        val clock = Clock(noon)
        val phone = Phone()
        val model = model(clock, phone = phone)
        val training = model.training
        model.start(model.today().next!!.id)
        val flow = training.state()!!.flow
        val timed = flow.steps.indices.first { flow[it].set.repetitions == null && !flow[it].set.repetitionsMax && (flow[it].set.durationSeconds ?: 0) > 0 }
        val seconds = flow[timed].set.durationSeconds!!

        tickUpTo(model, clock, until = timed)
        training.startHold(timed, round = 1, seconds = seconds)
        val hold = training.state()!!.hold!!
        assertEquals(flow[timed].key, hold.key)
        assertEquals(clock.time + seconds * 1_000L, hold.endsAt)

        clock.step(seconds * 1_000L - 500L)
        training.tick()
        assertEquals(0, phone.holds)
        clock.step(500L)
        training.tick()
        assertEquals(1, phone.holds)
        assertTrue(training.state()!!.hold!!.cued)
        training.tick()
        assertEquals(1, phone.holds)

        // A countdown that ended while the screen was away ends without a sound.
        training.startHold(timed, round = 1, seconds = seconds)
        clock.step(seconds * 1_000L + 5_000L)
        training.tick()
        assertEquals(1, phone.holds)
        assertTrue(training.state()!!.hold!!.cued)

        // Done · Next drops the countdown.
        training.done(timed)
        assertNull(training.state()!!.hold)
    }

    @Test
    fun finishingSavesTheClassMovesTheSequenceOnAndLightsTheStreak() {
        val clock = Clock(noon)
        val store = MemoryStore()
        val model = model(clock, store)
        val training = model.training
        val first = model.today().next!!
        model.start(first.id)
        val flow = training.state()!!.flow

        // Each set ticked after its planned time, so every exercise runs its share of the time and counts.
        while (true) {
            val index = (page(model) as? FocusPage.Set)?.index ?: break
            clock.step((WorkoutFlow.plannedSeconds(flow[index]) * 1_000).toLong() + 1_000L)
            training.done(index)
            training.skipRest()
        }
        assertEquals(FocusPage.Summary, page(model))
        val active = training.active()!!

        val finished = assertNotNull(training.finish(effort = 6))

        // What the engine counts for the same ticks.
        val skills = program.skills!!
        val movement = { exercise: TrainingExercise ->
            skills.movementOf(exercise.movementId, exercise.name.resolve(), exercise.description.resolve())
        }
        val counted = flow.counted(active.completedSetIds, active.tickedAt, active.startedAtEpochMillis)
        val credits = flow.credits(counted, active.easierExercises, movement)
        val points = flow.points(counted, active.easierExercises, active.done, movement, { skills.skill(it)?.standard },
            { id -> id?.let(program.catalog.movementGrades::get) ?: 1 })
        assertEquals(flow.keys.toSet(), counted)
        assertTrue(points > 0)
        assertEquals(points, finished.points)

        val session = finished.session
        assertEquals(first.id, session.programId)
        assertEquals(6, session.effortRating)
        assertEquals(points, session.points)
        assertEquals(credits.filterValues { it > 0 }, session.credits)
        assertTrue(session.credits!!.isNotEmpty())
        assertEquals((clock.time - noon) / 1_000L, session.durationSeconds)

        // The class is done, the sequence offers the next one, and the first class of the day lit the flame.
        assertNull(training.active())
        assertNull(training.state())
        assertTrue(model.isCompleted(first.id))
        val today = model.today()
        assertEquals(1, today.done)
        assertEquals(2, today.next?.number)
        assertFalse(today.resume)
        assertEquals(1, finished.streak)
        assertEquals(1, today.streak.streak.days)
        assertTrue(today.streak.streak.litToday)
        // Saved for good: a new reader of the store has the same history.
        assertEquals(listOf(session), StoredProgress(store).sessionHistory())

        // A second class the same day earns its points, but the flame was already lit.
        val second = today.next!!
        assertEquals(Start.STARTED, model.start(second.id))
        clock.step(60_000L)
        training.done(0)
        assertNull(training.finish(effort = 5)!!.streak)
        assertEquals(1, model.today().streak.streak.days)
    }

    @Test
    fun setsTickedTooFastEarnNothingButTheClassIsDone() {
        val clock = Clock(noon)
        val model = model(clock)
        val training = model.training
        val first = model.today().next!!
        model.start(first.id)
        tickUpTo(model, clock, until = Int.MAX_VALUE, every = 1_000L)
        assertEquals(FocusPage.Summary, page(model))

        val finished = training.finish(effort = 3)!!

        // The engine's rule: an exercise counts once its ticks took 30% of its planned time.
        assertEquals(0, finished.points)
        assertEquals(emptyMap<String, Int>(), finished.session.credits)
        assertTrue(model.isCompleted(first.id))
        // It was still a workout: the streak counts it.
        assertEquals(1, finished.streak)
    }

    @Test
    fun endingASessionDropsItsTicksAndKeepsTheSequence() {
        val clock = Clock(noon)
        val model = model(clock)
        val training = model.training
        val first = model.today().next!!
        model.start(first.id)
        tickUpTo(model, clock, until = 2)
        training.skip(2)

        training.abandon()

        assertNull(training.active())
        assertNull(training.state())
        assertFalse(model.isCompleted(first.id))
        assertTrue(model.progress.sessionHistory().isEmpty())
        assertEquals(first.id, model.today().next?.id)
        assertFalse(model.today().resume)
        assertEquals(0, model.today().streak.streak.days)

        // Starting it again starts clean: no ticks, no skips.
        clock.step(60_000L)
        model.start(first.id)
        assertEquals(FocusPage.Set(0), page(model))
        assertTrue(training.active()!!.completedSetIds.isEmpty())
    }

    @Test
    fun anUnfinishedClassIsOfferedAgainAfterTheAppIsClosed() {
        val clock = Clock(noon)
        val store = MemoryStore()
        val before = model(clock, store)
        val first = before.today().next!!
        before.intensity = Intensity.HARDER
        before.start(first.id)
        tickUpTo(before, clock, until = 3)
        before.training.skip(3)
        assertEquals(FocusPage.Set(4), page(before))
        val saved = before.training.active()!!

        // A new start of the app: the store read again from the same saved texts.
        clock.step(10 * 60_000L)
        val after = model(clock, store)
        val today = after.today()
        assertTrue(today.resume)
        assertEquals(first.id, today.next?.id)
        assertEquals(saved, after.training.active())
        assertEquals(Intensity.HARDER, after.intensity)
        // The ticks are back; the skip lasted only while the app ran, as on Android.
        assertEquals(FocusPage.Set(3), page(after))
        assertEquals(after.program(first.id), after.training.program())
        assertEquals(Start.STARTED, after.start(first.id))
        assertEquals(noon, after.training.active()!!.startedAtEpochMillis)
    }

    @Test
    fun skipBackAndTheSkippedSetsFromTheSummary() {
        val clock = Clock(noon)
        val model = model(clock)
        val training = model.training
        model.start(model.today().next!!.id)
        val flow = training.state()!!.flow
        val last = flow.size - 1

        training.skip(0)
        assertEquals(FocusPage.Set(1), page(model))
        training.back(1)
        assertEquals(FocusPage.Set(0), page(model))

        // Everything but the last set, which is skipped: the summary, with the last set to do.
        tickUpTo(model, clock, until = last)
        training.skip(last)
        val summary = training.state()!!
        assertEquals(FocusPage.Summary, summary.page)
        assertEquals(setOf(flow[last].key), summary.skipped)
        assertEquals(last, flow.completedCount(summary.active.completedSetIds))
        training.doSkipped()
        assertEquals(FocusPage.Set(last), page(model))

        // Back from the summary reopens the last set, unticked.
        training.done(last)
        assertEquals(FocusPage.Summary, page(model))
        training.back(null)
        assertEquals(FocusPage.Set(last), page(model))
        assertFalse(flow[last].key in training.active()!!.completedSetIds)
    }

    @Test
    fun theEasierOptionForgetsWhatWasLoggedForTheOtherMovement() {
        val clock = Clock(noon)
        val model = model(clock)
        val training = model.training
        // The first class with an easier option in its credited work, made today's.
        val ordered = NextClass.ordered(program.catalog)
        val target = ordered.first { candidate -> (candidate.practice + candidate.exercises).any { it.easier != null && !it.readinessCheck } }
        model.progress.completePrograms(ordered.takeWhile { it.id != target.id }.map { it.id }, at = noon)
        assertEquals(Start.STARTED, model.start(target.id))
        val flow = training.state()!!.flow
        val step = flow.steps.first { it.credited && it.exercise.easier != null }

        training.setDone(step.key, 5)
        assertEquals(5, training.active()!!.done[step.key])
        training.setEasier(step.exerciseKey, true)
        assertTrue(step.exerciseKey in training.active()!!.easierExercises)
        assertNull(training.active()!!.done[step.key])
        training.setEasier(step.exerciseKey, false)
        assertFalse(step.exerciseKey in training.active()!!.easierExercises)
    }

    @Test
    fun changingTheIntensityDuringAClassChangesItsReps() {
        val clock = Clock(noon)
        val model = model(clock)
        val first = model.today().next!!
        model.start(first.id)
        model.intensity = Intensity.HARDER
        assertEquals(Intensity.HARDER, model.training.active()!!.intensity)
        assertEquals(Intensity.HARDER.scale(NextClass.ordered(program.catalog).first()), model.training.program())
    }

    @Test
    fun countdownsAndPointsAsTheyAreShown() {
        assertEquals("00:00", clockText(0L))
        assertEquals("00:00", clockText(-5_000L, roundUp = true))
        assertEquals("00:01", clockText(1L, roundUp = true))
        assertEquals("00:00", clockText(999L))
        assertEquals("01:30", clockText(90_000L))
        assertEquals("1:02:05", clockText(3_725_000L))
        assertEquals("0", pointsText(0))
        assertEquals("999", pointsText(999))
        assertEquals("12,480", pointsText(12_480))
        assertEquals("1,234,567", pointsText(1_234_567))
    }

    private companion object {
        val program: Program by lazy { runBlocking { loadProgram() } }
    }
}
