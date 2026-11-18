package com.justdataplease.streetgym.app.data

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.justdataplease.streetgym.data.library.CustomDays
import com.justdataplease.streetgym.data.model.TrainingExercise
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.progress.ActiveWorkout
import com.justdataplease.streetgym.data.progress.Intensity
import com.justdataplease.streetgym.data.progress.WorkoutSession
import com.justdataplease.streetgym.data.skills.SkillBook
import com.justdataplease.streetgym.ui.SetMeasure
import com.justdataplease.streetgym.ui.WorkoutFlow
import com.justdataplease.streetgym.ui.WorkoutStep
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone

/** What the focus workout shows: the set at [index] of the class, the rest before it, or the summary at the end. */
sealed interface FocusPage {
    data class Set(val index: Int) : FocusPage
    data class Rest(val index: Int, val endsAt: Long) : FocusPage
    data object Summary : FocusPage
}

/** A timed set's countdown, [round] 1 or 2 (a per-side hold runs two). [cued]: its end was signalled. */
data class Hold(val key: String, val round: Int, val endsAt: Long, val cued: Boolean = false)

/**
 * The class in training as the focus screen and the list draw it, at [now]. [flow]: its sets the athlete trains, those
 * of switched-off skills and equipment left out ([leftOut]) until done anyway (Android: flow.without(leftOut)); the
 * focus view walks it and both views count it. [all]: every set of the class, as the list shows them. [skipped] and
 * [hold] last while the app runs. [restEndsAt]: when the rest that is running ends, null when none is (the list's rest
 * line shows it).
 */
class FocusState(
    val program: TrainingProgram,
    val flow: WorkoutFlow,
    val active: ActiveWorkout,
    val page: FocusPage,
    val skipped: Set<String>,
    val hold: Hold?,
    val now: Long,
    val restEndsAt: Long? = null,
    val all: WorkoutFlow = flow,
    val leftOut: Set<String> = emptySet(),
)

/**
 * What finishing a class saved. [streak]: the streak's days, when this class lit today's flame; else null. [exam]: an
 * exam class's targets, to tick the ones reached (Android: askExam after askEffortAndFinish); null for any other class.
 */
class Finished(val session: WorkoutSession, val points: Int, val streak: Int?, val exam: ExamQuestion? = null)

/**
 * "Exam: tick the targets you reached": the [exam] class's [tests], each with whether it looks reached (every set
 * ticked, none easier, no number short of the plan: WorkoutFlow.examTests), less those of switched-off skills or
 * equipment. [offerRepeat]: a block repeat may be offered for misses, which a repeated exam never does.
 */
class ExamQuestion(val exam: TrainingProgram, val tests: List<Pair<TrainingExercise, Boolean>>, val offerRepeat: Boolean)

/**
 * What a workout asks of the phone itself (Android: FLAG_KEEP_SCREEN_ON, onRestFinished's beep, TimingAlerts.tone and
 * vibrate, announceForAccessibility). The iPhone's is in iosMain; the desktop window stays silent; tests record calls.
 */
interface WorkoutDevice {
    /** The screen stays on while a workout shows. */
    fun keepScreenOn(on: Boolean) {}

    /** A rest ran out on screen with the rest timer sound on: the short beep. */
    fun restOver() {}

    /** A timed set ran out on screen: the beep when [sound] (the rest timer sound) is on, and a vibration. */
    fun holdOver(sound: Boolean) {}

    /** Says [text] to VoiceOver, as Android's focus view announces the rest and the timed sets. */
    fun announce(text: String) {}

    /**
     * The rest running ends at [at] (epoch ms): a notification with the beep then, saying [text], in case the app is in
     * the background by then (Android's rest timer beeps there too). Null cancels it; a new one replaces it.
     */
    fun restAlert(at: Long?, text: String = "") {}

    /**
     * Asks to show alerts (Android: HomeActivity.enableTimingNotifications, which only the Timer's "Enable alerts"
     * calls), then calls [done] with the answer. Until alerts are allowed, [restAlert] and the Timer's alerts schedule
     * nothing the phone will show.
     */
    fun askAlerts(done: (Boolean) -> Unit = {}) { done(true) }

    /** Whether the phone shows the app's alerts, to [result] (Android: TimingAlerts.notifications); maybe later. */
    fun alerts(result: (Alerts) -> Unit) { result(Alerts.ALLOWED) }

    /** Alerts turned off for the app: the phone's settings for them open (Android: ACTION_APP_NOTIFICATION_SETTINGS). */
    fun openAlertSettings() {}

    /**
     * The Timer runs out [inMillis] from now: an alert "Time’s up" with sound and Stop then, the app closed or not
     * (Android: TimingAlerts.schedule and notify). Null takes it away; a new one replaces it.
     */
    fun timerAlert(inMillis: Long?) {}

    /**
     * The alarm at [at] on the phone's wall clock: an alert "Alarm" with sound and Stop then, following a change of the
     * clock or the time zone. Null takes it away; a new one replaces it.
     */
    fun alarmAlert(at: LocalDateTime?) {}

    /** Stop: the Timer's and the alarm's alerts already shown go (Android: TimingAlerts.stop's cancel). */
    fun clearTimingAlerts() {}

    /** A timer or alarm rang with the app open and alerts off: the beep (Android: TimingAlerts.tone). */
    fun timingTone() {}

    /**
     * Where the app is ([com.justdataplease.streetgym.app.ui.Restore]), kept by the phone with its window, for when it
     * closes the app in the background (Android: onSaveInstanceState). Called on every change.
     */
    fun saveScreens(state: String) {}

    /**
     * The status bar's colour follows the theme: light words on a [dark] theme (Night Park), dark ones on the light
     * themes (Android: configureWindow's light status bar).
     */
    fun statusBar(dark: Boolean) {}

    /** Does nothing: previews, tests, and the desktop window. */
    object None : WorkoutDevice
}

/** What the focus view says to VoiceOver as the workout goes (Android: FocusWorkoutView.update and done). */
sealed interface Cue {
    /** A rest of [seconds] started; [next] is the set after it. */
    data class RestStarted(val seconds: Int, val next: WorkoutStep) : Cue
    /** Every 15 seconds of a rest: [seconds] left. */
    data class RestLeft(val seconds: Int) : Cue
    /** The rest ran out; [step] is the set now. */
    data class RestOver(val step: WorkoutStep) : Cue
    /** The timed set's countdown ran out. */
    data class TimeUp(val step: WorkoutStep) : Cue
    /** The first side of a per-side hold ran out. */
    data object SwitchSides : Cue
}

/** Counts the saves of the progress, so whatever read it is drawn again after one. */
@Stable
internal class Saves {
    var count by mutableIntStateOf(0)
        private set

    fun bump() { count++ }
}

/** A value that belongs to the class started at [session] (its start time): a new class starts without it. */
private data class Owned<T>(val session: Long, val value: T)

/**
 * Training a class (android-streetgym: HomeActivity's start, rest timer, finish and abandon, and FocusWorkoutView's
 * position, skips, timed sets and spoken cues). The position is always the first set neither ticked nor skipped, so
 * leaving the workout and coming back, or closing the app, lands on the same set.
 *
 * Ticks, logged amounts and easier options are saved at once ([StoredProgress], with the time of each tick). Skips, a
 * timed set's countdown and the rest last while the app runs, as on Android. Every time comes from [now], so tests step
 * it; [tick] is what the screen calls a few times a second. [sound] is the rest timer sound switch; [leftOutOfExam]
 * tells the exam targets of switched-off skills or equipment; [leftOut] the set keys of a class that the skill and
 * equipment switches leave out (Android: leftOutKeys), which the position, the ticks and the rest skip.
 */
@Stable
class Training internal constructor(
    private val progress: StoredProgress,
    private val skills: SkillBook?,
    private val grades: Map<String, Int>,
    private val now: () -> Long,
    private val zone: TimeZone,
    private val device: WorkoutDevice,
    private val saves: Saves,
    private val sound: () -> Boolean = { true },
    private val leftOutOfExam: (TrainingExercise) -> Boolean = { false },
    private val leftOut: (TrainingProgram, WorkoutFlow) -> Set<String> = { _, _ -> emptySet() },
    private val programAt: (String, Intensity) -> TrainingProgram?,
) {
    private var skips by mutableStateOf<Owned<Set<String>>?>(null)
    private var held by mutableStateOf<Owned<Hold>?>(null)
    private var rest by mutableStateOf<Owned<Long>?>(null)
    /** The last class opened, by id and intensity: the program never changes while the app runs. */
    private var opened: Triple<String, Intensity, Pair<TrainingProgram, WorkoutFlow>>? = null
    /** The seconds of rest last announced, so each 15-second mark is said once (Android: announcedRest). */
    private var announcedRest = 0L

    /**
     * Hears the [Cue]s while the focus view shows, as only Android's focus view announces them; null in the list.
     */
    var cues: ((Cue) -> Unit)? = null

    /**
     * Hears each class finished, with the class as it ran ([WorkoutFlow], every set), the workout as it was just before
     * it finished, the set keys the switches left out and how an exercise names its movement: progress analytics and
     * the cloud copy follow it (Android: askEffortAndFinish's cloudChanged and analytics.classFinished).
     */
    var finished: ((WorkoutSession, TrainingProgram, WorkoutFlow, ActiveWorkout, Set<String>, (TrainingExercise) -> String?) -> Unit)? = null

    /** The time [tick] last saw; reading it redraws a countdown as it runs. */
    var clock by mutableLongStateOf(now())
        private set

    /** The class in progress, as saved. */
    fun active(): ActiveWorkout? {
        saves.count
        return progress.activeWorkout()
    }

    /** The class in progress at the Easier, Normal or Harder it was started with. */
    fun program(): TrainingProgram? = active()?.let { open(it)?.first }

    /** What the focus screen shows now; null without a class in progress. */
    fun state(): FocusState? {
        clock
        val active = active() ?: return null
        val (program, all) = open(active) ?: return null
        val out = leftOut(program, all)
        val flow = all.without(out)
        val at = now()
        val skipped = skipped(active)
        val current = flow.firstIncomplete(active.completedSetIds, skipped)
        val restEnd = restEnd(active)
        val page = when {
            current == null -> FocusPage.Summary
            restEnd != null && restEnd > at -> FocusPage.Rest(current, restEnd)
            else -> FocusPage.Set(current)
        }
        return FocusState(program, flow, active, page, skipped, hold(active), at, restEnd?.takeIf { it > at }, all, out)
    }

    /**
     * Moves the clock on: a rest that ran out ends with its beep, and a timed set that ran out with its tone and
     * vibration; only one seen ending sounds, not one that ended while the app was away (a rest in the background had
     * its notification). The focus view's [cues] hear the rest's 15-second marks and the ends.
     */
    fun tick() {
        val at = now()
        clock = at
        val active = progress.activeWorkout() ?: return
        restEnd(active)?.let { end ->
            if (end > at) {
                val seconds = (end - at + 999L) / 1_000L
                if (seconds % REST_MARK_SECONDS == 0L && seconds != announcedRest) {
                    announcedRest = seconds
                    cues?.invoke(Cue.RestLeft(seconds.toInt()))
                }
            } else {
                rest = null
                if (at - end < CUE_WINDOW_MILLIS && sound()) device.restOver()
                live(active)?.let { flow ->
                    flow.firstIncomplete(active.completedSetIds, skipped(active))?.let { cues?.invoke(Cue.RestOver(flow[it])) }
                }
            }
        }
        val hold = hold(active) ?: return
        if (hold.endsAt > at || hold.cued) return
        held = Owned(active.startedAtEpochMillis, hold.copy(cued = true))
        if (at - hold.endsAt < CUE_WINDOW_MILLIS) device.holdOver(sound())
        val (_, flow) = current() ?: return
        val step = flow.steps.firstOrNull { it.key == hold.key } ?: return
        cues?.invoke(if (hold.round < step.sides) Cue.SwitchSides else Cue.TimeUp(step))
    }

    /** Starts round [round] of the timed set at [index], [seconds] long. */
    fun startHold(index: Int, round: Int, seconds: Int) {
        val (active, flow) = current() ?: return
        val at = now()
        held = Owned(active.startedAtEpochMillis, Hold(flow[index].key, round, at + seconds * 1_000L))
        clock = at
    }

    /** "Done · Next": ticks the set at [index] and starts the rest after it; after the class's last set there is none. */
    fun done(index: Int) {
        val (active, flow) = current() ?: return
        val step = flow[index]
        val at = now()
        val skipped = skipped(active) - step.key
        held = null
        skips = Owned(active.startedAtEpochMillis, skipped)
        progress.setCompleted(listOf(step.key), completed = true, at = at)
        // The section's ad break waits for the rest after it, as Android's focus view does.
        if (flow.isSectionComplete(step.section, active.completedSetIds + step.key)) sectionBreak = true
        val next = flow.firstIncomplete(active.completedSetIds + step.key, skipped)
        val pause = step.set.breakSeconds?.takeIf { it > 0 }
        if (next == null) rest = null
        else if (pause != null) {
            rest = Owned(active.startedAtEpochMillis, at + pause * 1_000L)
            announcedRest = pause.toLong()
            cues?.invoke(Cue.RestStarted(pause, flow[next]))
        }
        clock = at
        saves.bump()
    }

    /**
     * A set ticked or unticked in the list (Android: renderActive's set check). A tick starts the set's rest, or ends
     * the one running when it was the class's last set; unticking leaves the rest as it is. Focus follows, since its
     * position is the first set neither ticked nor skipped.
     */
    fun tick(key: String, completed: Boolean) {
        val (active, flow) = current() ?: return
        val at = now()
        progress.setCompleted(listOf(key), completed, at = at)
        if (completed) {
            val done = flow.completedCount(active.completedSetIds + key)
            val pause = flow.steps.firstOrNull { it.key == key }?.set?.breakSeconds?.takeIf { it > 0 }
            if (done >= flow.size) rest = null
            else if (pause != null) {
                rest = Owned(active.startedAtEpochMillis, at + pause * 1_000L)
                announcedRest = pause.toLong()
            }
        }
        clock = at
        saves.bump()
    }

    /**
     * Every set of [section] ticked or unticked at once (Android: the section's check box). With the whole class ticked
     * the rest ends.
     */
    fun tickSection(section: String, completed: Boolean) {
        val (active, flow) = current() ?: return
        val at = now()
        val keys = flow.keys(section)
        if (keys.isEmpty()) return
        progress.setCompleted(keys, completed, at = at)
        val ticked = if (completed) active.completedSetIds + keys else active.completedSetIds - keys.toSet()
        if (flow.completedCount(ticked) == flow.size) rest = null
        // Android's list: a section ticked whole with no rest running is a break for the full-screen ad.
        if (completed && !resting()) sectionBreak = true
        clock = at
        saves.bump()
    }

    /** Leaves the set at [index] for later: the summary offers the skipped sets again. */
    fun skip(index: Int) {
        val (active, flow) = current() ?: return
        held = null
        skips = Owned(active.startedAtEpochMillis, skipped(active) + flow[index].key)
        clock = now()
    }

    /** Reopens the set before [index] (from the summary, with null, the class's last set), unticking it. */
    fun back(index: Int?) {
        val (active, flow) = current() ?: return
        val target = (if (index == null) flow.steps.indices.lastOrNull() else flow.previous(index)) ?: return
        val key = flow[target].key
        held = null
        skips = Owned(active.startedAtEpochMillis, skipped(active) - key)
        progress.setCompleted(listOf(key), completed = false, at = now())
        clock = now()
        saves.bump()
    }

    /** "Do skipped sets": back to the first set skipped. */
    fun doSkipped() {
        skips = null
        clock = now()
    }

    /** "+15 s": more time on the rest that is running. */
    fun addRest(seconds: Int) {
        val running = rest ?: return
        rest = running.copy(value = running.value + seconds * 1_000L)
        clock = now()
    }

    fun skipRest() {
        rest = null
        clock = now()
    }

    /** What the athlete did in the set with [key], in its measure; null goes back to the plan. */
    fun setDone(key: String, amount: Int?) {
        progress.setDone(key, amount)
        saves.bump()
    }

    /** Switches an exercise to or from its easier option; what was logged for its sets was the other movement. */
    fun setEasier(exerciseKey: String, easier: Boolean) {
        val flow = progress.activeWorkout()?.let(::open)?.second ?: return
        progress.setEasier(exerciseKey, easier)
        val done = progress.activeWorkout()?.done.orEmpty()
        flow.steps.filter { it.exerciseKey == exerciseKey && it.key in done }.forEach { progress.setDone(it.key, null) }
        saves.bump()
    }

    /** How [step] is logged now, done as planned or as its easier option. */
    fun measure(step: WorkoutStep): SetMeasure {
        val easier = active()?.easierExercises.orEmpty()
        val id = WorkoutFlow.movement(step, easier, ::skillMovement)
        return WorkoutFlow.measure(step, id?.let { skills?.skill(it)?.standard })
    }

    /**
     * Finishes the class with the athlete's [effort] (RPE 1 to 10). Only the sets that ran their share of the time
     * count ([WorkoutFlow.counted]); they credit skills, doses and points as the engine counts them. A first class
     * finished today lights the streak.
     */
    fun finish(effort: Int): Finished? {
        val active = progress.activeWorkout() ?: return null
        val (program, flow) = open(active) ?: return null
        val at = now()
        val easier = active.easierExercises
        val counted = flow.counted(active.completedSetIds, active.tickedAt, active.startedAtEpochMillis)
        val book = skills
        val credits = if (book == null) emptyMap() else flow.credits(counted, easier, ::skillMovement)
        val doses = if (book == null) emptyMap() else flow.doses(counted, easier, active.done, ::skillMovement) { book.skill(it)?.standard }
        val points = flow.points(counted, easier, active.done, ::skillMovement, { id -> book?.skill(id)?.standard },
            { id -> id?.let(grades::get) ?: 1 })
        val litBefore = LocalDays.snapshot(progress.sessionHistory(), at, zone).streak.litToday
        val session = progress.finishWorkout(at, effort, credits, doses, points) ?: return null
        forget()
        saves.bump()
        finished?.invoke(session, program, flow, active, leftOut(program, flow)) { exercise -> exercise.movementId ?: skillMovement(exercise) }
        val streak = LocalDays.snapshot(progress.sessionHistory(), at, zone).streak.takeIf { !litBefore && it.litToday }
        // An exam's targets, less those of skills or equipment switched off; a repeated exam offers no block repeat.
        val exam = if (!program.isExam) null else flow.examTests(active.completedSetIds, easier, active.done)
            .filterNot { (exercise, _) -> leftOutOfExam(exercise) }
            .takeIf { it.isNotEmpty() }
            ?.let { ExamQuestion(program, it, offerRepeat = !session.repeat) }
        return Finished(session, points, streak?.days, exam)
    }

    /** "End session": the class in progress is dropped with its ticks; nothing is saved to the history. */
    fun abandon() {
        progress.abandonWorkout()
        forget()
        saves.bump()
    }

    /** The class in progress and the sets the athlete trains, for an action on them. */
    private fun current(): Pair<ActiveWorkout, WorkoutFlow>? {
        val active = progress.activeWorkout() ?: return null
        return live(active)?.let { active to it }
    }

    /** The class's sets less those the switches leave out. */
    private fun live(active: ActiveWorkout): WorkoutFlow? = open(active)?.let { (program, all) -> all.without(leftOut(program, all)) }

    /** Start over: the rest, the timed set and the skips go with the progress. */
    fun reset() = forget()

    private fun open(active: ActiveWorkout): Pair<TrainingProgram, WorkoutFlow>? {
        // An own workout can be edited while it is in progress: it is read again, and its sets follow the edit.
        val own = if (CustomDays.isCustom(active.programId)) programAt(active.programId, active.intensity) ?: return null else null
        opened?.takeIf { it.first == active.programId && it.second == active.intensity && (own == null || own == it.third.first) }
            ?.let { return it.third }
        val program = own ?: programAt(active.programId, active.intensity) ?: return null
        return (program to flow(program)).also { opened = Triple(active.programId, active.intensity, it) }
    }

    private fun forget() {
        sectionBreak = false
        rest = null
        held = null
        skips = null
        clock = now()
    }

    private fun skipped(active: ActiveWorkout): Set<String> =
        skips?.takeIf { it.session == active.startedAtEpochMillis }?.value.orEmpty()

    private fun hold(active: ActiveWorkout): Hold? = held?.takeIf { it.session == active.startedAtEpochMillis }?.value

    private fun restEnd(active: ActiveWorkout): Long? = rest?.takeIf { it.session == active.startedAtEpochMillis }?.value

    /** A rest runs now: no full-screen ad over it (Android: adBreakNow's restEndsAtEpochMillis). */
    fun resting(): Boolean {
        val active = progress.activeWorkout() ?: return false
        return restEnd(active)?.let { it > now() } == true
    }

    /**
     * A finished section waits for its ad break (Android: FocusWorkoutView.breakPending, and the list's section box):
     * true once, when the section is done and no rest runs, which then lets the break go.
     */
    fun takeSectionBreak(): Boolean {
        if (!sectionBreak || resting()) return false
        sectionBreak = false
        return true
    }

    /** A section was just finished, its break not yet taken. */
    private var sectionBreak = false

    /** The rest running and its class, kept for when the phone closes the app (Android: STATE_REST_END, STATE_REST_PROGRAM). */
    fun runningRest(): Pair<String, Long>? {
        val active = progress.activeWorkout() ?: return null
        return restEnd(active)?.let { active.programId to it }
    }

    /** A rest kept that way runs on, if its class is still the one in progress and the rest hasn't ended. */
    fun restoreRest(programId: String, end: Long) {
        val active = progress.activeWorkout() ?: return
        if (active.programId == programId && end > now()) rest = Owned(active.startedAtEpochMillis, end)
    }

    /** The skill movement [exercise] trains, when the skills know it (Android: HomeActivity.skillMovement). */
    private fun skillMovement(exercise: TrainingExercise): String? =
        skills?.movementOf(exercise.movementId, exercise.name.resolve(), exercise.description.resolve())

    companion object {
        /** A rest's or a timed set's end sounds only when it is seen within this long of it (Android: holdFinished). */
        private const val CUE_WINDOW_MILLIS = 2_000L

        /** The rest's time left is spoken at every multiple of this (Android: FocusWorkoutView.update). */
        private const val REST_MARK_SECONDS = 15L

        /** A class's sets in training order, with the keys Android saves. */
        fun flow(program: TrainingProgram): WorkoutFlow = WorkoutFlow.of(program.id, sections(program))

        /** Every class has the same four sections, in this order, under the keys both phones save. */
        fun sections(program: TrainingProgram): List<Pair<String, List<TrainingExercise>>> = listOf(
            WARM_UP to program.warmup,
            PRACTICE to program.practice,
            MAIN to program.exercises,
            COOL_DOWN to program.cooldown,
        )

        const val WARM_UP = "warmup"
        const val PRACTICE = "practice"
        const val MAIN = "main"
        const val COOL_DOWN = "cooldown"
    }
}
