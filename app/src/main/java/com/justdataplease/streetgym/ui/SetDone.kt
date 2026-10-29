package com.justdataplease.streetgym.ui

import android.app.AlertDialog
import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.FrameLayout
import com.justdataplease.streetgym.R

/**
 * "Did 12 reps" or "Held 45 s" for a set logged by the athlete; "Did more or fewer?" while it follows the plan, or
 * what to log when there is no plan's number to follow ([SetMeasure]).
 */
fun Context.setDoneLabel(measure: SetMeasure, done: Int?): String = when {
    done == null -> getString(when {
        measure.window -> R.string.set_done_log_window
        measure.planned != null -> R.string.set_done_ask
        measure.timed -> R.string.set_done_log_seconds
        else -> R.string.set_done_log_reps
    })
    measure.timed -> getString(R.string.set_done_seconds, done)
    else -> resources.getQuantityString(R.plurals.set_done_reps, done, done)
}

/**
 * What the athlete did in a set, in its [measure], starting from [done] or the plan: skill levels count sets at a
 * target, so a set done bigger than planned counts as done. [onDone] gets the number, or null for "As planned".
 */
fun Context.askSetDone(measure: SetMeasure, done: Int?, onDone: (Int?) -> Unit) {
    val field = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER
        filters = arrayOf(InputFilter.LengthFilter(3))
        (done ?: measure.planned)?.let { setText(it.toString()); selectAll() }
        hint = getString(if (measure.timed) R.string.set_done_seconds_hint else R.string.set_done_reps_hint)
        contentDescription = hint
        gravity = Gravity.CENTER
    }
    val dialog = AlertDialog.Builder(this)
        .setTitle(R.string.set_done_title)
        .setMessage(when {
            measure.window -> R.string.set_done_message_window
            measure.timed -> R.string.set_done_message_hold
            else -> R.string.set_done_message_reps
        })
        .setView(FrameLayout(this).apply {
            setPadding(dp(24), dp(4), dp(24), 0)
            addView(field)
        })
        .setPositiveButton(R.string.set_done_save) { _, _ -> field.text.toString().toIntOrNull()?.let(onDone) }
        .setNegativeButton(R.string.cancel, null)
    if (measure.planned != null) dialog.setNeutralButton(R.string.set_done_plan) { _, _ -> onDone(null) }
    dialog.show()
}
