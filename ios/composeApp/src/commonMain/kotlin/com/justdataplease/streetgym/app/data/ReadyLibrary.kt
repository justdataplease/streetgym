package com.justdataplease.streetgym.app.data

import com.justdataplease.streetgym.data.effort.Effort
import com.justdataplease.streetgym.data.model.TrainingProgram

/**
 * The Library's three filters (Android: readyEffort, readyGrade and readyFocus): how hard for the athlete, the grade
 * (1 Beginner to 5 Elite) and the focus (a group or a skill family, one of the workouts' focus tags); null lets all
 * through. Kept while the app runs.
 */
data class ReadyFilter(val effort: Effort? = null, val grade: Int? = null, val focus: String? = null) {
    /** Any filter on: the count line offers Clear filters. */
    val filtered: Boolean get() = effort != null || grade != null || focus != null
}

/** The Library's rules (android-streetgym, HomeActivity.renderReadyWorkouts and READY_FOCUS). */
object ReadyLibrary {
    /** The focus chips, in order: the Library's groups, then the skill families (tools/build_ready_workouts.py). */
    val FOCUS = listOf("problems", "full", "pull", "push", "legs", "core", "skills", "short", "mobility",
        "handstand", "front_lever", "back_lever", "planche", "flag", "muscleup", "lsit", "one_arm")

    /** The grades a chip can pick, Beginner to Elite. */
    val GRADES = (1..5).toList()

    /** The group whose heading opens with the doctor-first note. */
    const val PROBLEMS = "problems"

    /** The focus chips some workout of [ready] has, in [FOCUS]'s order. */
    fun focuses(ready: List<TrainingProgram>): List<String> = FOCUS.filter { focus -> ready.any { focus in it.focusTags } }

    /** The workouts [filter] lets through, in their order; [effortOf] rates one for the athlete. */
    fun shown(ready: List<TrainingProgram>, filter: ReadyFilter, effortOf: (TrainingProgram) -> Effort?): List<TrainingProgram> =
        ready.filter { workout ->
            (filter.grade == null || workout.grade == filter.grade) && (filter.focus == null || filter.focus in workout.focusTags) &&
                (filter.effort == null || effortOf(workout) == filter.effort)
        }

    /** [shown] under their group headings, the groups in the order their first workout comes. */
    fun groups(shown: List<TrainingProgram>): List<Pair<String?, List<TrainingProgram>>> = shown.groupBy { it.group }.toList()
}
