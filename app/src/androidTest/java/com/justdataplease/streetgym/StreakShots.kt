package com.justdataplease.streetgym

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import com.justdataplease.streetgym.data.AssetCurriculumRepository
import com.justdataplease.streetgym.data.progress.NextClass
import com.justdataplease.streetgym.data.progress.SharedPreferencesProgressRepository
import com.justdataplease.streetgym.data.progress.StreakDays
import com.justdataplease.streetgym.ui.AppSettings
import com.justdataplease.streetgym.ui.StreetTheme
import java.io.File
import java.util.TimeZone

/**
 * A review aid, not part of the suite: the streak on Today, its sheet and History's line.
 *   am instrument -w com.justdataplease.streetgym.debug.test/com.justdataplease.streetgym.StreakShots
 * Seeds a history of finished classes (a best run of 21 days, then 12 workout days with rest days between some of them:
 * up to today, up to yesterday so today may be the rest day, up to two days ago so today's workout is due, a run that
 * ended days ago, or nothing), opens the app and grabs the screen: Today in each theme, then the sheet a tap on the
 * flame opens and History behind its button. PNGs land in the app's external files under streak/. Restores the
 * preferences it touches. (The widget's flame is drawn by WidgetShots.)
 */
class StreakShots : Instrumentation() {
    private val log = StringBuilder()
    private val app: Context get() = targetContext
    private lateinit var out: File
    private lateinit var classes: List<String>

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = Bundle()
        val kept = PREFS.associateWith { app.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }
        val failure = runCatching {
            out = File(app.getExternalFilesDir(null), "streak").apply { deleteRecursively(); mkdirs() }
            classes = NextClass.ordered(AssetCurriculumRepository(app).load().getOrThrow()).map { it.id }
            // Days ago, newest last: the best run of 21, then the run each state needs.
            val best = (60 downTo 40).toList()
            // Twelve workout days, the last ones every other day: a rest day sits between them in this week's row.
            val run = listOf(16, 15, 14, 12, 11, 10, 8, 7, 6, 4, 2, 0)
            for (theme in StreetTheme.entries) {
                seed(theme, best + run)
                today("lit_${theme.key}", sheet = true, history = theme == StreetTheme.CONCRETE)
            }
            // Trained yesterday: today may be the rest day.
            seed(StreetTheme.CONCRETE, best + run.map { it + 1 })
            today("rest_concrete", sheet = true, history = false)
            seed(StreetTheme.WHEATPASTE, best + run.map { it + 1 })
            today("rest_wheatpaste", sheet = false, history = false)
            // Trained two days ago, rested yesterday: today's workout is due.
            seed(StreetTheme.CONCRETE, best + run.map { it + 2 })
            today("due_concrete", sheet = true, history = false)
            seed(StreetTheme.NIGHT_PARK, best + run.map { it + 2 })
            today("due_night_park", sheet = true, history = false)
            // Trained three days ago: two days missed, the streak ended.
            seed(StreetTheme.CONCRETE, best + run.map { it + 3 })
            today("ended_concrete", sheet = true, history = false)
            seed(StreetTheme.CONCRETE, best + (9 downTo 5))
            today("missed_concrete", sheet = true, history = true)
            seed(StreetTheme.CONCRETE, emptyList())
            today("none_concrete", sheet = true, history = true)
        }.exceptionOrNull()
        // Before finish(), which ends the process.
        kept.forEach { (name, values) -> restore(name, values) }
        if (failure == null) {
            result.putString("stream", "\nSTREAK OK\n$log")
            finish(Activity.RESULT_OK, result)
        } else {
            result.putString("stream", "\nSTREAK FAILED ${failure.stackTraceToString()}\n$log")
            finish(Activity.RESULT_CANCELED, result)
        }
    }

    /** A placed athlete in [theme] who finished one class on each of [daysAgo], at noon. */
    private fun seed(theme: StreetTheme, daysAgo: List<Int>) = runOnMainSync {
        val settings = AppSettings(app)
        settings.theme = theme
        settings.placementDone = true
        val progress = SharedPreferencesProgressRepository(app)
        progress.reset(0L)
        val now = System.currentTimeMillis()
        val zone = TimeZone.getDefault()
        val noon = StreakDays.epochDay(now, zone) * DAY + DAY / 2 - zone.getOffset(now)
        daysAgo.forEachIndexed { index, ago ->
            val at = minOf(noon - ago * DAY, now)
            progress.startWorkout(classes[index], at - 30 * 60_000L)
            progress.finishWorkout(at, effortRating = 7, points = 100)
        }
        // A class only marked complete yesterday: it must not count.
        if (daysAgo.isEmpty()) progress.completePrograms(listOf(classes[0]), now - DAY)
        val streak = StreakDays.snapshot(progress.sessionHistory()).streak
        log.append("${theme.key} ${daysAgo.size} days: $streak\n")
    }

    /** Opens the app on Today and grabs it; then the streak's sheet, and History from its button. */
    private fun today(name: String, sheet: Boolean, history: Boolean) {
        val activity = startActivitySync(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        var strip: View? = null
        val until = SystemClock.uptimeMillis() + 30_000
        while (strip == null && SystemClock.uptimeMillis() < until) {
            SystemClock.sleep(300)
            runOnMainSync { strip = find(activity.window.decorView) { it.isClickable && "${it.contentDescription}".contains("streak", true) } }
        }
        val found = checkNotNull(strip) { "Today never showed the streak ($name)" }
        var spoken = ""
        runOnMainSync { spoken = "${found.contentDescription}" }
        log.append("$name: \"$spoken\"\n")
        SystemClock.sleep(1200)
        shot("today_$name.png")
        if (sheet) {
            runOnMainSync { found.performClick() }
            SystemClock.sleep(1200)
            shot("sheet_$name.png")
            if (history) {
                val pressed = uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText("See history")
                    ?.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                log.append("$name: See history pressed=$pressed\n")
                SystemClock.sleep(1500)
                shot("history_$name.png")
            } else {
                uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                SystemClock.sleep(500)
            }
        }
        runOnMainSync { activity.finish() }
        SystemClock.sleep(800)
    }

    private fun find(view: View, test: (View) -> Boolean): View? {
        if (test(view)) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index), test)?.let { return it }
        return null
    }

    private fun shot(name: String) {
        File(out, name).outputStream().use { uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
        log.append("saved $name\n")
    }

    @Suppress("UNCHECKED_CAST")
    private fun restore(name: String, values: Map<String, Any?>) {
        val edit = app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
        values.forEach { (key, value) ->
            when (value) {
                is String -> edit.putString(key, value)
                is Boolean -> edit.putBoolean(key, value)
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
                is Float -> edit.putFloat(key, value)
                is Set<*> -> edit.putStringSet(key, value as Set<String>)
            }
        }
        edit.commit()
    }

    private companion object {
        const val DAY = 86_400_000L
        val PREFS = listOf("streetgym_progress", "streetgym_settings")
    }
}
