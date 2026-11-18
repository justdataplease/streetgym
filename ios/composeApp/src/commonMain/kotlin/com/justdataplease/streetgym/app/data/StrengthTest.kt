package com.justdataplease.streetgym.app.data

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.progress.Placement

/**
 * What the strength test says for one athlete's counts (Android: PlacementView.placementCard's renderResult).
 * [counted]: the start the counts give. [tests]: the level tests (each level's last class, Level 3's to Level 9's)
 * whose pull-ups and push-ups the counts pass, which the skills step offers. [skill]: the level test picked there, 0
 * for none (a pick the counts no longer pass is dropped). [wanted]: the skills step's start, or [counted]. [start]:
 * [wanted] held to the last level the tier opens. [opening]: the first movements of the start's class, with their dose.
 * [assumed]: the exam just before the start, whose targets it takes for granted. [better]: what a few more reps alone
 * would change, without a picked test.
 */
class StrengthResult(
    val counted: Placement.Start,
    val tests: List<TrainingProgram>,
    val skill: Int,
    val wanted: Placement.Start,
    val start: Placement.Start,
    val opening: List<String>,
    val assumed: TrainingProgram?,
    val better: Placement.Better?,
) {
    /** The start is Level 1, day 1: "Start from scratch" and the no-points note are left out. */
    val fromScratch: Boolean get() = start == SCRATCH

    companion object {
        /** Level 1, day 1: where skipping the test, or starting from scratch, begins. */
        val SCRATCH = Placement.Start(1)
    }
}

/**
 * The first launch's strength test over the program in training order (Android: PlacementView and
 * HomeActivity.startPlacement), every rule from the engine's [Placement].
 */
class StrengthTest(private val written: List<TrainingProgram>) {
    /** Where a placement may start: the block starts of Levels 1 to 3. */
    val starts: List<Placement.Start> =
        Placement.starts(written, TrainingProgram::levelNumber, TrainingProgram::number, TrainingProgram::isExam)

    /** The level tests whose pull-ups and push-ups [pullUps] and [pushUps] pass. */
    fun tests(pullUps: Int, pushUps: Int): List<TrainingProgram> = Placement.SKILL_TESTS.mapNotNull { number ->
        written.filter { it.levelNumber == number }.maxByOrNull { it.number }
            ?.takeIf { it.isExam && Placement.meets(it, pullUps, pushUps) }
    }

    /** The start for these counts, with [skill] the level test picked (0 for none), held to what [tier] opens. */
    fun result(pullUps: Int, pushUps: Int, squats: Int, skill: Int, tier: Tier): StrengthResult {
        val counted = Placement.start(pullUps, pushUps, squats, starts)
        val tests = tests(pullUps, pushUps)
        val picked = skill.takeIf { level -> level > 0 && tests.any { it.levelNumber == level } } ?: 0
        val wanted = if (picked > 0) Placement.skillStart(picked) else counted
        val start = Placement.capped(wanted, tier)
        val opening = written.firstOrNull { it.levelNumber == start.level && it.number == start.day }
            ?.let(Placement::opening).orEmpty()
        val assumed = Placement.assumed(written, start, TrainingProgram::levelNumber, TrainingProgram::number, TrainingProgram::isExam)
        val better = if (picked == 0) Placement.better(pullUps, pushUps, squats, starts) else null
        return StrengthResult(counted, tests, picked, wanted, start, opening, assumed, better)
    }

    /** The classes [start] marks complete: every class before it (Android: startPlacement). */
    fun skipped(start: Placement.Start): List<String> =
        Placement.skipped(written, start, TrainingProgram::levelNumber, TrainingProgram::number).map(TrainingProgram::id)

    /** The class [start] opens on. */
    fun programAt(start: Placement.Start): TrainingProgram? =
        written.firstOrNull { it.levelNumber == start.level && it.number == start.day }
}

/**
 * The strength test's answers while the app runs, so Today can redraw without losing them (Android: placementAnswers,
 * placementSkill, placementShown). A count of -1 is empty; [skill] 0 is no level test picked; [shown]: Calculate was
 * tapped since the last change.
 */
@Stable
class PlacementForm {
    val counts = mutableStateListOf(-1, -1, -1)
    var skill by mutableIntStateOf(0)
    var shown by mutableStateOf(false)

    /** Every count is in. */
    val ready: Boolean get() = counts.none { it < 0 }

    /** A count typed, or stepped with − and +: the start hides until Calculate is tapped again. */
    fun set(index: Int, count: Int) {
        counts[index] = count
        shown = false
    }

    /** Calculate: shows the start once every count is in. */
    fun calculate(): Boolean {
        if (!ready) return false
        shown = true
        return true
    }

    fun clear() {
        for (index in counts.indices) counts[index] = -1
        skill = 0
        shown = false
    }
}
