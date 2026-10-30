package com.justdataplease.streetgym.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import com.justdataplease.streetgym.R

/**
 * The progress-sharing note (the owner, 2026-10-30): one plain line on what the app keeps to improve the program, beside a
 * switch that is on until the athlete switches it off. It closes the first-launch placement under a rule, and sits in a
 * card in Settings › Your data ([inSettings]).
 */
fun Context.shareProgressNote(settings: AppSettings, inSettings: Boolean = false): View {
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(bodyText(getText(R.string.share_body), 12.5f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        @Suppress("DEPRECATION") val toggle = Switch(context).apply {
            contentDescription = getString(R.string.share_switch)
            isChecked = settings.shareProgress
            setOnCheckedChangeListener { _, on -> settings.shareProgress = on }
        }
        addView(toggle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { marginStart = dp(14) })
    }
    if (inSettings) return card().apply { addView(row) }
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(View(context).apply { setBackgroundColor(AppColors.DIVIDER) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(2)))
        addWithMargins(row, topDp = 12)
    }
}
