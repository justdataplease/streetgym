package com.justdataplease.streetgym.ui

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import com.justdataplease.streetgym.R
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.timer.clockText
import java.util.Locale

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * Switches an exercise to its easier option and back, in both workout views: the sets ticked while it is on count
 * for the easier movement.
 */
fun Context.easierToggle(name: String, on: Boolean, onToggle: (Boolean) -> Unit): TextView = TextView(this).apply {
    text = getString(if (on) R.string.workout_easier_on else R.string.workout_easier_off, name)
    textSize = 13f
    typeface = Typeface.DEFAULT_BOLD
    setTextColor(if (on) AppColors.ON_ACCENT else AppColors.PRIMARY)
    maxLines = 2
    ellipsize = TextUtils.TruncateAt.END
    gravity = Gravity.CENTER_VERTICAL
    setPadding(dp(12), dp(6), dp(12), dp(6))
    background = RippleDrawable(
        ColorStateList.valueOf(0x22000000),
        roundedDrawable(if (on) AppColors.PRIMARY else AppColors.INFO_SURFACE, dp(16).toFloat()),
        null,
    )
    isClickable = true
    isFocusable = true
    contentDescription = getString(if (on) R.string.workout_easier_on_description else R.string.workout_easier_off_description, name)
    setOnClickListener { onToggle(!on) }
}

/** "Workout view: Focus · List", shown above Start on the overview and in Settings. */
fun Context.workoutViewSetting(settings: AppSettings): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    minimumHeight = dp(56)
    addView(TextView(context).apply {
        text = getString(R.string.workout_view)
        textSize = 15f
        setTextColor(AppColors.TEXT)
    }, LinearLayout.LayoutParams(0, WRAP, 1f))
    val holder = FrameLayout(context)
    fun bind() {
        val selected = if (settings.workoutView == WorkoutView.FOCUS) 0 else 1
        holder.removeAllViews()
        holder.addView(segmentedSwitch(
            listOf(getString(R.string.workout_view_focus), getString(R.string.workout_view_list)),
            selected,
        ) { index ->
            settings.workoutView = if (index == 0) WorkoutView.FOCUS else WorkoutView.LIST
            bind()
        }.apply { getChildAt(selected).isSelected = true })
    }
    bind()
    addView(holder)
}

/**
 * Focus workout: one set per screen, a full-screen rest after it and a summary that leads into
 * the finish flow. The position is always the first set that is neither checked nor skipped, so
 * switching views, rotation and restarts land on the same set; rest is the activity's timer.
 *
 * Every screen is strip, fill, info and controls. Controls are never weighted, the fill
 * (animation or countdown) takes the height that is left, and info is clipped from its bottom
 * before anything can push the controls off-screen, at any font scale and in both orientations.
 */
@SuppressLint("ViewConstructor")
class FocusWorkoutView(
    context: Context,
    private val program: TrainingProgram,
    private val flow: WorkoutFlow,
    private val sectionTitles: Map<String, String>,
    private val startedAtEpochMillis: Long,
    private val locale: Locale,
    private val host: Host,
) : FrameLayout(context) {

    /** What the activity owns: saved set checks, the rest timer, ad breaks and the finish flow. */
    interface Host {
        fun completedSets(): Set<String>
        fun setCompleted(key: String, completed: Boolean)
        fun restEndsAt(): Long?
        fun startRest(seconds: Int)
        fun addRest(seconds: Int)
        fun skipRest()
        fun sectionComplete()
        fun holdFinished()
        fun finish(completedSets: Int, totalSets: Int)
        /** Exercises done as their easier option ([WorkoutStep.exerciseKey]), credited to the easier movement. */
        fun easierExercises(): Set<String>
        fun setEasier(exerciseKey: String, easier: Boolean)
        /** What the athlete did in a set, by set key, where it differs from the plan ([ActiveWorkout.done]). */
        fun doneAmounts(): Map<String, Int>
        fun setDoneAmount(key: String, amount: Int?)
        /** How a set is logged now, done as planned or as its easier option. */
        fun measure(step: WorkoutStep): SetMeasure
        /** Sets of switched-off skills left out of this run (review 12), and doing them anyway. */
        fun leftOut(): Int = 0
        fun includeLeftOut() {}
    }

    private enum class Mode { SET, REST, SUMMARY }

    /** A timed set's countdown. Per-side holds run two rounds. */
    private data class Hold(val key: String, val round: Int, val endsAt: Long, val cued: Boolean = false)

    private val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    private var mode = Mode.SET
    private var position: Int? = null
    private var readout: TextView? = null
    private var announcedRest = 0L
    private var breakPending = false
    private val tick = object : Runnable {
        override fun run() {
            update()
            postDelayed(this, 250L)
        }
    }

    init {
        keepScreenOn = true
        setBackgroundColor(AppColors.BACKGROUND)
        if (session != startedAtEpochMillis) {
            session = startedAtEpochMillis
            skipped.clear()
            hold = null
        }
        render()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        removeCallbacks(tick)
        if (visibility == VISIBLE) post(tick)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    private fun render() {
        val completed = host.completedSets()
        val current = flow.firstIncomplete(completed, skipped)
        val restEnd = host.restEndsAt()
        position = current
        readout = null
        mode = when {
            current == null -> Mode.SUMMARY
            restEnd != null && restEnd > System.currentTimeMillis() -> Mode.REST
            else -> Mode.SET
        }
        removeAllViews()
        val screen = when {
            current == null -> summaryScreen(completed)
            mode == Mode.REST -> restScreen(current, completed)
            else -> setScreen(current, completed)
        }
        addView(screen, LayoutParams(MATCH, MATCH))
        screen.applyThemeType()
        // A section's ad break waits for its rest to end, as in the list view.
        if (mode != Mode.REST && breakPending) {
            breakPending = false
            host.sectionComplete()
        }
    }

    private fun update() {
        val now = System.currentTimeMillis()
        val index = position ?: return
        when (mode) {
            Mode.REST -> {
                val left = (host.restEndsAt() ?: now) - now
                if (left <= 0L) {
                    render()
                    announceForAccessibility(stepLabel(R.string.focus_rest_over, flow[index]))
                    return
                }
                showReadout(left)
                val seconds = (left + 999L) / 1_000L
                if (seconds % 15L == 0L && seconds != announcedRest) {
                    announcedRest = seconds
                    announceForAccessibility(context.getString(R.string.focus_rest_left, seconds.toInt()))
                }
            }
            Mode.SET -> {
                val step = flow[index]
                val current = hold?.takeIf { it.key == step.key } ?: return
                showReadout(current.endsAt - now)
                if (current.endsAt > now || current.cued) return
                hold = current.copy(cued = true)
                if (now - current.endsAt < 2_000L) host.holdFinished()
                render()
                announceForAccessibility(
                    if (current.round < rounds(step)) context.getString(R.string.focus_switch_sides)
                    else stepLabel(R.string.focus_time_up, step),
                )
            }
            Mode.SUMMARY -> Unit
        }
    }

    private fun showReadout(milliseconds: Long) {
        val text = clockText(milliseconds, roundUp = true)
        readout?.let { if (it.text.toString() != text) it.text = text }
    }

    private fun setScreen(index: Int, completed: Set<String>): View {
        val step = flow[index]
        val name = name(step)
        val notes = ExerciseNotes.parse(step.exercise.description.resolve(locale))
        val (prescription, cues) = prescription(step)
        val seconds = holdSeconds(step)
        val rounds = rounds(step)
        val now = System.currentTimeMillis()
        val current = hold?.takeIf { it.key == step.key }
        val running = current != null && current.endsAt > now
        // The countdown round the main button starts; null means it offers Done.
        val startRound = when {
            seconds == null -> null
            current == null -> 1
            !running && current.round < rounds -> current.round + 1
            else -> null
        }
        val side = if (seconds != null && rounds > 1) startRound ?: current?.round ?: 1 else null
        val variation = step.set.name.resolve(locale)
            .ifBlank { if (step.exercise.movementId == null) notes.description else "" }
        val cue = (listOf(variation) + cues).filter(String::isNotBlank).joinToString(" · ")
        val setLine = listOfNotNull(
            context.getString(R.string.focus_set_of, step.setNumber, step.setCount),
            side?.let { context.getString(R.string.focus_side, it) },
        ).joinToString(" · ")

        val animation = ExerciseAnimationView(context).bind(
            movementId = step.exercise.movementId,
            exerciseName = name,
            category = step.exercise.category,
            equipment = step.exercise.equipment,
            descriptor = step.set.name.resolve(locale).ifBlank { step.exercise.description.resolve(locale) },
        )
        val big = current?.let { clockText(it.endsAt - now, roundUp = true) } ?: prescription
        val info = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(context.titleText(name, if (landscape) 18f else 20f).apply {
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(0, WRAP, 1f))
                addView(textAction(context.getString(R.string.focus_details)) { showDetails(step) })
            })
            addView(context.bodyText(setLine, 14f).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            val dose = context.titleText(big, largeSize(big)).apply {
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                // Night Park stencils the dose in its light colour.
                if (AppColors.theme == StreetTheme.NIGHT_PARK) {
                    typeface = AppType.numeral
                    setTextColor(AppColors.PRIMARY)
                }
                if (current != null) typeface = AppType.timer(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD))
            }
            if (current != null) readout = dose
            addWithMargins(dose, topDp = 4)
            if (cue.isNotBlank()) addWithMargins(context.bodyText(cue, 14f).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, topDp = 2)
            if (step.credited) addWithMargins(textAction(context.setDoneLabel(host.measure(step), host.doneAmounts()[step.key])) {
                context.askSetDone(host.measure(step), host.doneAmounts()[step.key]) { amount ->
                    host.setDoneAmount(step.key, amount)
                    render()
                }
            }, width = WRAP, topDp = 2)
            val option = step.exercise.easier
                ?.takeIf { step.section in WorkoutFlow.CREDITED_SECTIONS && !step.exercise.readinessCheck }
            addWithMargins(if (option == null) easierChip(step, notes.easier) else context.easierToggle(
                option.name.resolve(locale), step.exerciseKey in host.easierExercises(),
            ) { on ->
                host.setEasier(step.exerciseKey, on)
                render()
            }, width = WRAP, topDp = 8)
        }

        val primary = if (startRound != null && seconds != null) {
            val label = if (startRound == 1) context.getString(R.string.focus_start, seconds)
            else context.getString(R.string.focus_start_side, startRound)
            context.primaryButton(label) { startHold(step, startRound, seconds) }.apply {
                contentDescription = context.getString(
                    R.string.focus_start_description, seconds, step.setNumber, step.setCount, name,
                )
            }
        } else {
            context.primaryButton(context.getString(R.string.focus_done)) {
                // A "max" set, a practice window or an easier option measured the other way has no number to count
                // until the athlete gives it.
                val measure = host.measure(step)
                if (step.credited && measure.planned == null && step.key !in host.doneAmounts()) {
                    context.askSetDone(measure, null) { amount ->
                        host.setDoneAmount(step.key, amount)
                        done(index)
                    }
                } else done(index)
            }.apply {
                contentDescription = stepLabel(R.string.focus_done_description, step)
            }
        }
        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(sideButton(context.getString(R.string.back), context.getString(R.string.focus_back_description)) {
                back(index)
            }.apply {
                isEnabled = flow.previous(index) != null
                alpha = if (isEnabled) 1f else 0.4f
            })
            addView(thumb(primary), LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(8), 0, dp(8), 0) })
            addView(sideButton(context.getString(R.string.focus_skip), stepLabel(R.string.focus_skip_description, step)) {
                skip(index)
            })
        }
        val title = sectionTitles[step.section].orEmpty()
        return screen(strip(title, context.getString(R.string.focus_set_of, index + 1, flow.size), completed, true), animation, info, controls)
    }

    private fun restScreen(index: Int, completed: Set<String>): View {
        val step = flow[index]
        val left = (host.restEndsAt() ?: 0L) - System.currentTimeMillis()
        val count = TextView(context).apply {
            text = clockText(left, roundUp = true)
            gravity = Gravity.CENTER
            setTextColor(AppColors.TEXT)
            typeface = AppType.timer(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD))
            maxLines = 1
            if (Build.VERSION.SDK_INT >= 26) {
                setAutoSizeTextTypeUniformWithConfiguration(32, 120, 2, TypedValue.COMPLEX_UNIT_SP)
            } else {
                textSize = 64f
            }
        }
        readout = count
        announcedRest = (left + 999L) / 1_000L
        val fill = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(context.sectionLabel(context.getString(R.string.focus_rest)).apply { gravity = Gravity.CENTER })
            addView(count, LinearLayout.LayoutParams(MATCH, 0, 1f))
        }
        val info = context.bodyText(context.getString(R.string.focus_rest_next, name(step), step.setNumber), 16f).apply {
            setTextColor(AppColors.TEXT)
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(sideButton(context.getString(R.string.focus_add_rest), context.getString(R.string.focus_add_rest_description)) {
                host.addRest(15)
                update()
            })
            addView(thumb(context.primaryButton(context.getString(R.string.focus_skip_rest)) {
                host.skipRest()
                render()
            }), LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(8) })
        }
        val title = sectionTitles[step.section].orEmpty()
        return screen(strip(title, context.getString(R.string.focus_set_of, index + 1, flow.size), completed, true), fill, info, controls)
    }

    private fun summaryScreen(completed: Set<String>): View {
        val done = flow.completedCount(completed)
        val missed = flow.keys.count { it in skipped && it !in completed }
        val minutes = ((System.currentTimeMillis() - startedAtEpochMillis) / 60_000L).coerceAtLeast(0L)
        val fill = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(context.titleText(context.getString(
                if (done == flow.size) R.string.focus_summary_complete else R.string.focus_summary_partial,
            ), 28f).apply { gravity = Gravity.CENTER })
            addWithMargins(context.bodyText(context.getString(R.string.started_minutes_ago, minutes), 15f).apply {
                gravity = Gravity.CENTER
            }, topDp = 6)
            if (missed > 0) addView(context.bodyText(
                resources.getQuantityString(R.plurals.focus_summary_skipped, missed, missed), 15f,
            ).apply { gravity = Gravity.CENTER })
            host.leftOut().takeIf { it > 0 }?.let { out ->
                addView(context.bodyText(resources.getQuantityString(R.plurals.focus_switched_off, out, out), 15f).apply {
                    gravity = Gravity.CENTER
                })
            }
        }
        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(thumb(context.primaryButton(context.getString(R.string.finish_workout)) { host.finish(done, flow.size) }))
            addWithMargins(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(context.secondaryButton(context.getString(R.string.back)) { back(null) }.apply {
                    contentDescription = context.getString(R.string.focus_back_description)
                    isEnabled = flow.size > 0
                }, LinearLayout.LayoutParams(0, WRAP, 1f))
                if (missed > 0) addView(context.secondaryButton(context.getString(R.string.focus_do_skipped)) {
                    skipped.clear()
                    render()
                }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(8) })
            }, topDp = 8)
            if (host.leftOut() > 0) addWithMargins(context.secondaryButton(context.getString(R.string.focus_do_switched_off)) {
                host.includeLeftOut()
            }, topDp = 8)
        }
        val sets = context.getString(R.string.active_sets_progress, done, flow.size)
        return screen(strip(context.displayName(program, locale), sets, completed, false), fill, null, controls)
    }

    /** Portrait: strip, fill, info, controls. Landscape: strip and fill left, info and controls right. */
    private fun screen(strip: View, fill: View, info: View?, controls: View): View =
        if (!landscape) LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(8))
            addView(strip)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(fill, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = dp(8) })
                info?.let { addView(it, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(10) }) }
            }, LinearLayout.LayoutParams(MATCH, 0, 1f))
            addView(controls, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(10) })
        } else LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(4), dp(16), dp(8))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(strip)
                addView(fill, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = dp(8) })
            }, LinearLayout.LayoutParams(0, MATCH, 5f))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(Space(context), LinearLayout.LayoutParams(MATCH, 0, 1f))
                    info?.let { addView(it, LinearLayout.LayoutParams(MATCH, WRAP)) }
                    addView(Space(context), LinearLayout.LayoutParams(MATCH, 0, 1f))
                }, LinearLayout.LayoutParams(MATCH, 0, 1f))
                addView(controls, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) })
            }, LinearLayout.LayoutParams(0, MATCH, 7f).apply { marginStart = dp(16) })
        }

    /** Section name and Finish, then the class progress bar and "Set 7 of 27". */
    private fun strip(title: String, position: String, completed: Set<String>, finish: Boolean): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(40)
                addView(context.sectionLabel(title).apply {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(4), 0, dp(4))
                }, LinearLayout.LayoutParams(0, WRAP, 1f))
                if (finish) addView(textAction(context.getString(R.string.finish)) {
                    host.finish(flow.completedCount(host.completedSets()), flow.size)
                })
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val bar = SetProgressView(context, BooleanArray(flow.size) { flow[it].key in completed }, this@FocusWorkoutView.position)
                addView(bar, LinearLayout.LayoutParams(0, dp(SetProgressView.heightDp), 1f))
                addView(context.bodyText(position, 13f).apply {
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                    setPadding(dp(10), 0, 0, 0)
                })
            })
        }

    private fun easierChip(step: WorkoutStep, easier: String): TextView {
        val option = easier.ifBlank { context.getString(R.string.focus_easier_generic) }
        return TextView(context).apply {
            text = context.getString(R.string.focus_easier, option)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(AppColors.PRIMARY)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = RippleDrawable(
                ColorStateList.valueOf(0x22000000),
                roundedDrawable(AppColors.INFO_SURFACE, dp(16).toFloat()),
                null,
            )
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(R.string.focus_easier_description, option)
            setOnClickListener { showDetails(step) }
        }
    }

    /** Bottom sheet: the movement description, tempo, safety, easier option and start criteria. */
    private fun showDetails(step: WorkoutStep) {
        val notes = ExerciseNotes.parse(step.exercise.description.resolve(locale))
        val (prescription, cues) = prescription(step)
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), dp(24))
            addView(context.bodyText(
                "${context.getString(R.string.focus_set_of, step.setNumber, step.setCount)} · $prescription", 14f,
            ))
            if (notes.description.isNotBlank()) addWithMargins(context.bodyText(notes.description, 15f).apply {
                setTextColor(AppColors.TEXT)
            }, topDp = 10)
            fun part(label: Int, text: String) {
                if (text.isBlank()) return
                addWithMargins(context.sectionLabel(context.getString(label)), topDp = 8)
                addView(context.bodyText(text, 14f))
            }
            part(R.string.focus_details_tempo, cues.joinToString(" · "))
            part(R.string.focus_details_safety, notes.safety)
            part(R.string.focus_details_easier, listOf(notes.easier, context.getString(R.string.readiness_note))
                .filter(String::isNotBlank).joinToString("\n\n"))
            part(R.string.focus_details_start, program.readiness.resolve(locale))
        }
        val sheet = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppColors.SURFACE)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(12), dp(8), dp(4))
                addView(context.titleText(name(step), 20f), LinearLayout.LayoutParams(0, WRAP, 1f))
                addView(textAction(context.getString(R.string.focus_close)) { dialog.dismiss() })
            })
            addView(ScrollView(context).apply { addView(body) }, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        sheet.applyThemeType()
        dialog.setContentView(sheet)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.BOTTOM)
            setLayout(MATCH, WRAP)
        }
    }

    /** "8 reps" or "30 s · per side"; the set's other notes (the tempo) come back as cues. */
    private fun prescription(step: WorkoutStep): Pair<String, List<String>> {
        val notes = SetNotes.parse(step.set.description.resolve(locale))
        val cues = notes.cues.toMutableList()
        val set = step.set
        val dose = when {
            set.repetitionsMax -> context.getString(R.string.maximum_repetitions)
            set.repetitions != null -> context.getString(R.string.repetitions_format, set.repetitions)
            set.durationSeconds != null -> context.getString(R.string.focus_seconds, set.durationSeconds)
            cues.isNotEmpty() -> cues.removeAt(0)
            else -> context.getString(R.string.set_number, step.setNumber)
        }
        return (listOf(dose) + notes.sides).joinToString(" · ") to cues
    }

    private fun holdSeconds(step: WorkoutStep): Int? =
        step.set.durationSeconds?.takeIf { it > 0 && step.set.repetitions == null && !step.set.repetitionsMax }

    private fun rounds(step: WorkoutStep): Int =
        if (SetNotes.parse(step.set.description.resolve(locale)).sides.isEmpty()) 1 else 2

    private fun startHold(step: WorkoutStep, round: Int, seconds: Int) {
        hold = Hold(step.key, round, System.currentTimeMillis() + seconds * 1_000L)
        render()
    }

    private fun done(index: Int) {
        val step = flow[index]
        hold = null
        skipped -= step.key
        host.setCompleted(step.key, true)
        val completed = host.completedSets()
        if (flow.isSectionComplete(step.section, completed)) breakPending = true
        val next = flow.firstIncomplete(completed, skipped)
        val rest = step.set.breakSeconds?.takeIf { it > 0 }
        if (next == null) host.skipRest() else rest?.let(host::startRest)
        render()
        if (mode == Mode.REST && next != null && rest != null) {
            announceForAccessibility(context.getString(
                R.string.focus_rest_started, rest, name(flow[next]), flow[next].setNumber,
            ))
        }
    }

    private fun skip(index: Int) {
        hold = null
        skipped += flow[index].key
        render()
    }

    /** Reopens the previous set (the last one from the summary), unchecking it if needed. */
    private fun back(index: Int?) {
        val target = (if (index == null) flow.steps.indices.lastOrNull() else flow.previous(index)) ?: return
        val key = flow[target].key
        hold = null
        skipped -= key
        host.setCompleted(key, false)
        render()
    }

    private fun largeSize(text: String): Float {
        val base = if (landscape) 30f else 36f
        return when {
            text.length <= 10 -> base
            text.length <= 16 -> base * 0.75f
            else -> base * 0.6f
        }
    }

    private fun thumb(button: Button): Button = button.apply {
        textSize = 16f
        minHeight = dp(64)
        setPadding(dp(12), 0, dp(12), 0)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    private fun sideButton(label: String, description: String, onClick: () -> Unit): Button =
        context.secondaryButton(label, onClick).apply {
            contentDescription = description
            minWidth = 0
            minimumWidth = 0
            minHeight = dp(56)
            setPadding(dp(14), 0, dp(14), 0)
            maxLines = 1
        }

    private fun textAction(label: String, onClick: () -> Unit): TextView = TextView(context).apply {
        text = label
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(AppColors.PRIMARY)
        gravity = Gravity.CENTER
        maxLines = 1
        minHeight = dp(40)
        setPadding(dp(12), 0, dp(12), 0)
        background = RippleDrawable(ColorStateList.valueOf(0x22000000), null, roundedDrawable(Color.WHITE, dp(12).toFloat()))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun name(step: WorkoutStep): String = context.displayName(step.exercise, locale)

    private fun stepLabel(resource: Int, step: WorkoutStep): String =
        context.getString(resource, step.setNumber, step.setCount, name(step))

    private fun dp(value: Int): Int = context.dp(value)

    private companion object {
        // Session-only: kept through rotation and view switches, gone after the process ends.
        val skipped = mutableSetOf<String>()
        var hold: Hold? = null
        var session = 0L
    }
}
