package com.justdataplease.streetgym.ui

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.Toast
import com.justdataplease.streetgym.R

/** Android's build-number trick on the Settings version line: [TAPS] taps ask for the passcode. */
class VersionTaps {
    private var taps = 0

    /** What one more tap shows: nothing at first, a countdown from [COUNTDOWN_FROM], then the passcode on every tap. */
    fun tap(): Step {
        taps = minOf(taps + 1, TAPS)
        val remaining = TAPS - taps
        return when {
            remaining == 0 -> Step.Passcode
            taps >= COUNTDOWN_FROM -> Step.Countdown(remaining)
            else -> Step.Quiet
        }
    }

    sealed interface Step {
        data object Quiet : Step
        data class Countdown(val remaining: Int) : Step
        data object Passcode : Step
    }

    companion object {
        const val TAPS = 10
        const val COUNTDOWN_FROM = 4
        private const val PASSCODE = "0002"

        fun accepts(code: String): Boolean = code.trim() == PASSCODE
    }
}

/**
 * The hidden developer settings behind the version line ([VersionTaps]). Once the passcode is accepted a single tap
 * opens them, until they are hidden again. They live on this device only and never sync.
 */
class DeveloperMenu(private val context: Context, private val settings: AppSettings) {
    private val taps = VersionTaps()
    private var toast: Toast? = null

    fun onVersionTapped() {
        if (settings.developerUnlocked) {
            open()
            return
        }
        when (val step = taps.tap()) {
            VersionTaps.Step.Quiet -> Unit
            is VersionTaps.Step.Countdown ->
                show(context.resources.getQuantityString(R.plurals.developer_taps_left, step.remaining, step.remaining))
            VersionTaps.Step.Passcode -> askPasscode()
        }
    }

    private fun askPasscode() {
        val field = EditText(context).apply {
            hint = context.getString(R.string.developer_passcode_hint)
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setTextColor(AppColors.TEXT)
        }
        AlertDialog.Builder(context).setTitle(R.string.developer_title)
            .setMessage(R.string.developer_passcode_message)
            .setView(padded(field))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.developer_unlock) { _, _ ->
                if (VersionTaps.accepts(field.text.toString())) {
                    settings.developerUnlocked = true
                    open()
                } else {
                    show(context.getString(R.string.developer_wrong_passcode))
                }
            }.show()
    }

    private fun open() {
        val inspect = Switch(context).apply {
            text = context.getString(R.string.developer_inspect_all)
            textSize = 15f
            setTextColor(AppColors.TEXT)
            isChecked = settings.inspectAllDays
            minHeight = context.dp(56)
            setOnCheckedChangeListener { _, value -> settings.inspectAllDays = value }
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(inspect)
            addView(context.bodyText(context.getString(R.string.developer_inspect_note), 13f))
        }
        AlertDialog.Builder(context).setTitle(R.string.developer_title)
            .setView(padded(content))
            .setNeutralButton(R.string.developer_hide) { _, _ ->
                settings.inspectAllDays = false
                settings.developerUnlocked = false
            }
            .setPositiveButton(R.string.developer_done, null)
            .show()
    }

    private fun padded(view: android.view.View) = LinearLayout(context).apply {
        setPadding(context.dp(20), context.dp(8), context.dp(20), 0)
        addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /** One toast at a time, as fast taps would otherwise queue a stack of them. */
    private fun show(message: String) {
        toast?.cancel()
        toast = Toast.makeText(context, message, Toast.LENGTH_SHORT).also(Toast::show)
    }
}
