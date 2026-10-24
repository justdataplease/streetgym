package com.justdataplease.streetgym.ui

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.graphics.Typeface
import android.os.SystemClock
import android.text.format.DateFormat
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.TextView
import com.justdataplease.streetgym.timer.*
import java.util.Date
import java.util.Locale

/** A branded, independent timing tool. No workout state is changed here. */
class TimingPanel(private val host: Activity, private val askNotifications: () -> Unit,
    private val askExact: () -> Unit) : ScrollView(host) {
    private val store = TimingStore(host)
    private val page = host.verticalPage()
    private val content = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
    private val tabs = mutableListOf<Button>()
    private var shownTab = -1
    private lateinit var digits: TextView
    private lateinit var detail: TextView
    private lateinit var primary: Button
    private lateinit var secondary: Button
    private var presets = listOf<Button>()
    private var lapsView: LinearLayout? = null
    private var renderedLaps: List<Long> = emptyList()
    private var alertsHint: TextView? = null

    init {
        isFillViewport = true
        val strip = LinearLayout(host)
        listOf("Timer", "Stopwatch", "Alarm").forEachIndexed { index, label ->
            val button = host.secondaryButton(label) {
                store.save(store.load().copy(tab = index)); refresh(store.load())
            }.apply { textSize = 13f; setPadding(host.dp(4), 0, host.dp(4), 0) }
            tabs += button
            strip.addView(button, LinearLayout.LayoutParams(0, host.dp(48), 1f).apply {
                if (index > 0) leftMargin = host.dp(6)
            })
        }
        page.addWithMargins(strip, bottomDp = 16)
        page.addView(content)
        addView(page)
        refresh(store.load())
    }

    fun refresh(state: TimingState) {
        if (shownTab != state.tab) build(state.tab)
        val now = SystemClock.elapsedRealtime()
        when (shownTab) {
            0 -> {
                val left = state.countdown.remaining(now)
                digits.showText(clockText(left, roundUp = true))
                detail.showText(when {
                    state.countdown.running -> "Counting down"
                    left == 0L -> "Time’s up"
                    left != state.countdown.total -> "Paused"
                    else -> "Tap the time to change it"
                })
                primary.showText(if (state.countdown.running) "Pause" else if (left < state.countdown.total && left > 0) "Resume" else "Start")
                secondary.isEnabled = left != state.countdown.total || state.countdown.running
                presets.forEach { it.isEnabled = !state.countdown.running }
                digits.isEnabled = !state.countdown.running
            }
            1 -> {
                val elapsed = state.stopwatch.elapsed(now)
                digits.showText(clockText(elapsed) + String.format(Locale.US, ".%02d", elapsed / 10 % 100))
                detail.showText(if (state.stopwatch.running) "Running" else if (elapsed > 0) "Paused" else "Ready")
                primary.showText(if (state.stopwatch.running) "Pause" else if (elapsed > 0) "Resume" else "Start")
                secondary.showText(if (state.stopwatch.running) "Lap" else "Reset")
                secondary.isEnabled = state.stopwatch.running || elapsed > 0
                lapsView?.visibility = if (state.stopwatch.laps.isEmpty()) GONE else VISIBLE
                if (renderedLaps != state.stopwatch.laps) {
                    renderedLaps = state.stopwatch.laps
                    lapsView?.removeAllViews()
                    state.stopwatch.laps.withIndex().reversed().forEach { (index, time) ->
                        lapsView?.addWithMargins(host.bodyText("Lap ${index + 1}     ${clockText(time)}.${(time / 10 % 100).toString().padStart(2, '0')}", 16f), topDp = 12)
                    }
                }
            }
            2 -> {
                val time = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, state.alarmHour); set(java.util.Calendar.MINUTE, state.alarmMinute) }
                digits.showText(DateFormat.getTimeFormat(host).format(time.time))
                detail.showText(if (state.alarmAt > 0) "${DateFormat.format("EEE, d MMM", Date(state.alarmAt))} · One time"
                    else "Tap the time to change it")
                primary.showText(when {
                    state.alarmAt > 0 -> "Turn off"
                    !TimingAlerts.notifications(host) -> "Enable alerts"
                    !TimingAlerts.exact(host) -> "Enable alarms"
                    else -> "Set alarm"
                })
            }
        }
        alertsHint?.apply {
            showText(when {
                !TimingAlerts.notifications(host) -> "Background alerts are off. Enable alerts"
                !TimingAlerts.exact(host) -> "For on-time background alerts, enable alarms"
                else -> ""
            })
            visibility = if (text.isEmpty()) GONE else VISIBLE
        }
    }

    private fun TextView.showText(value: String) { if (text.toString() != value) text = value }

    private fun build(tab: Int) {
        shownTab = tab; content.removeAllViews(); renderedLaps = emptyList(); lapsView = null; presets = emptyList(); alertsHint = null
        tabs.forEachIndexed { index, view ->
            view.background = roundedDrawable(if (index == tab) AppColors.PRIMARY else AppColors.INFO_SURFACE, host.dp(16).toFloat())
            view.setTextColor(if (index == tab) AppColors.ON_ACCENT else AppColors.TEXT)
            view.isSelected = index == tab
        }
        val panel = host.card()
        val compact = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        digits = host.titleText("", if (tab == 0) 60f else 48f).apply {
            gravity = Gravity.CENTER; typeface = Typeface.MONOSPACE; maxLines = 1
            if (android.os.Build.VERSION.SDK_INT >= 26) setAutoSizeTextTypeUniformWithConfiguration(
                24, if (tab == 0) 60 else 48, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            setPadding(0, host.dp(if (compact) 4 else 24), 0, host.dp(if (compact) 4 else 12))
            contentDescription = when (tab) { 0 -> "Change timer duration"; 2 -> "Change alarm time"; else -> "Stopwatch time" }
            if (tab != 1) { isClickable = true; isFocusable = true; setOnClickListener { if (tab == 0) durationPicker() else alarmPicker() } }
        }
        detail = host.bodyText("", 13f).apply { gravity = Gravity.CENTER }
        val readout = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            addView(digits); addWithMargins(detail, bottomDp = if (compact) 0 else 24)
        }
        if (compact) {
            panel.orientation = LinearLayout.HORIZONTAL; panel.gravity = Gravity.CENTER_VERTICAL
            panel.addView(readout, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        } else panel.addView(readout)
        primary = host.primaryButton("Start") { primaryAction() }
        secondary = host.secondaryButton("Reset") {
            if (shownTab == 0) {
                TimingAlerts.stop(host)
                TimingAlerts.update(host) { it.copy(countdown = it.countdown.reset()) }
            } else TimingAlerts.update(host) {
                it.copy(stopwatch = if (it.stopwatch.running) it.stopwatch.lap(SystemClock.elapsedRealtime()) else Stopwatch())
            }
            refresh(store.load())
        }
        val actions = LinearLayout(host)
        if (tab != 2) actions.addView(secondary, LinearLayout.LayoutParams(0, host.dp(54), 1f).apply { rightMargin = host.dp(10) })
        actions.addView(primary, LinearLayout.LayoutParams(0, host.dp(54), 1f))
        if (compact) panel.addView(actions, LinearLayout.LayoutParams(host.dp(240), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = host.dp(16)
        }) else panel.addView(actions)
        content.addView(panel)
        if (tab == 0) {
            val row = LinearLayout(host)
            presets = listOf(30_000L to "30 sec", 60_000L to "1 min", 180_000L to "3 min").mapIndexed { index, (time, label) ->
                host.secondaryButton(label) { TimingAlerts.update(host) { it.copy(countdown = Countdown(time)) }; refresh(store.load()) }
                    .apply { setPadding(0, 0, 0, 0); row.addView(this, LinearLayout.LayoutParams(0, host.dp(48), 1f).apply { if (index > 0) leftMargin = host.dp(8) }) }
            }
            content.addWithMargins(row, topDp = 12)
        }
        if (tab == 1) {
            lapsView = host.card()
            content.addWithMargins(lapsView!!, topDp = 16)
        } else {
            alertsHint = host.bodyText("", 13f).apply {
                minHeight = host.dp(48); setPadding(host.dp(8), host.dp(12), host.dp(8), host.dp(12))
                isClickable = true; isFocusable = true
                setOnClickListener { if (!TimingAlerts.notifications(host)) askNotifications() else askExact() }
            }
            content.addWithMargins(alertsHint!!, topDp = 8)
        }
    }

    private fun primaryAction() {
        val now = SystemClock.elapsedRealtime()
        when (shownTab) {
            0 -> {
                TimingAlerts.update(host) { it.copy(countdown = if (it.countdown.running) it.countdown.pause(now) else it.countdown.start(now), timerRinging = false) }
            }
            1 -> TimingAlerts.update(host) { it.copy(stopwatch = it.stopwatch.toggle(now)) }
            2 -> {
                val state = store.load()
                when {
                    state.alarmAt > 0 -> TimingAlerts.update(host) { it.copy(alarmAt = 0) }
                    !TimingAlerts.notifications(host) -> askNotifications()
                    !TimingAlerts.exact(host) -> askExact()
                    else -> TimingAlerts.update(host) { it.copy(alarmAt = nextAlarm(it.alarmHour, it.alarmMinute, System.currentTimeMillis()), alarmRinging = false) }
                }
            }
        }
        refresh(store.load())
    }

    private fun durationPicker() {
        val total = store.load().countdown.total / 1000
        val row = LinearLayout(host).apply { setPadding(host.dp(12), 0, host.dp(12), 0) }
        val pickers = listOf("Hours" to 23, "Minutes" to 59, "Seconds" to 59).mapIndexed { i, (label, limit) ->
            val picker = NumberPicker(host).apply {
                minValue = 0; maxValue = limit
                value = when (i) { 0 -> (total / 3600).toInt(); 1 -> (total / 60 % 60).toInt(); else -> (total % 60).toInt() }
                contentDescription = label
            }
            row.addView(LinearLayout(host).apply { orientation = LinearLayout.VERTICAL
                addView(host.bodyText(label, 12f).apply { gravity = Gravity.CENTER }); addView(picker)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            picker
        }
        val dialog = AlertDialog.Builder(host).setTitle("Timer").setView(row).setNegativeButton("Cancel", null)
            .setPositiveButton("Set", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            row.clearFocus()
            val duration = (pickers[0].value * 3600L + pickers[1].value * 60L + pickers[2].value) * 1000
            if (duration > 0) {
                TimingAlerts.update(host) { it.copy(countdown = Countdown(duration), timerRinging = false) }
                refresh(store.load()); dialog.dismiss()
            } else android.widget.Toast.makeText(host, "Choose a duration", android.widget.Toast.LENGTH_SHORT).show()
        } }
        dialog.show()
    }

    private fun alarmPicker() {
        val state = store.load()
        TimePickerDialog(host, { _, hour, minute ->
            TimingAlerts.update(host) { it.copy(alarmHour = hour, alarmMinute = minute,
                alarmAt = if (it.alarmAt > 0) nextAlarm(hour, minute, System.currentTimeMillis()) else 0) }
            refresh(store.load())
        }, state.alarmHour, state.alarmMinute, DateFormat.is24HourFormat(host)).show()
    }
}
