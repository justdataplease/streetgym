package com.justdataplease.streetgym.app.ui

import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.data.library.CustomDays
import com.justdataplease.streetgym.data.library.LibraryCodec
import com.justdataplease.streetgym.json.JSONArray
import com.justdataplease.streetgym.json.JSONObject

/** Where the shell was: its screens (the first a tab), Training's and Progress's tabs, and the list open in My workouts. */
data class Screens(val stack: List<Screen>, val trainingTab: Int, val workoutList: String?, val progressTab: Int)

/**
 * What comes back when the phone closed the app in the background and it opens again (Android: onSaveInstanceState and
 * its restore): the same screens and tabs, the builder's unsaved workout, the strength test's counts and the rest that
 * was running. The phone keeps this text with the app's window (SwiftUI's scene storage), and drops it when the athlete
 * closes the app themselves, as Android does.
 */
object Restore {
    fun encode(screens: Screens, model: AppModel): String {
        val o = JSONObject()
        o.put("stack", JSONArray().apply { screens.stack.forEach { put(screenJson(it)) } })
        o.put("training_tab", screens.trainingTab)
        screens.workoutList?.let { o.put("workout_list", it) }
        o.put("progress_tab", screens.progressTab)
        model.draft?.let { o.put("draft_day", LibraryCodec.encodeDay(it)) }
        val form = model.placementForm
        o.put("placement_answers", JSONArray().apply { form.counts.forEach { put(it) } })
        o.put("placement_skill", form.skill)
        o.put("placement_shown", form.shown)
        model.training.runningRest()?.let { (program, end) ->
            o.put("rest_program", program)
            o.put("rest_end_epoch_millis", end)
        }
        return o.toString()
    }

    /**
     * Puts the builder's workout, the strength test's counts and the rest back into [model] and returns the screens;
     * those that can't open any more are left out, as Android's isValidScreen does. Null for nothing usable.
     */
    fun decode(text: String?, model: AppModel): Screens? {
        if (text.isNullOrBlank()) return null
        return runCatching {
            val o = JSONObject(text)
            o.optJSONObject("draft_day")?.let(LibraryCodec::decodeDay)?.let { model.draft = it }
            o.optJSONArray("placement_answers")?.takeIf { it.length() == model.placementForm.counts.size }?.let { answers ->
                for (index in 0 until answers.length()) model.placementForm.counts[index] = answers.optInt(index, -1)
                model.placementForm.skill = o.optInt("placement_skill")
                model.placementForm.shown = o.optBoolean("placement_shown")
            }
            if (o.has("rest_program")) model.training.restoreRest(o.getString("rest_program"), o.optLong("rest_end_epoch_millis"))
            val all = o.optJSONArray("stack") ?: JSONArray()
            val stack = (0 until all.length()).mapNotNull { all.optJSONObject(it)?.let(::screenOf) }
            val usable = stack.takeWhile { valid(it, model) }
            val first = usable.firstOrNull()?.takeIf { !it.nested } ?: return@runCatching null
            Screens(listOf(first) + usable.drop(1), o.optInt("training_tab").coerceIn(0, 3), o.optString("workout_list").ifEmpty { null },
                o.optInt("progress_tab").coerceIn(0, 2))
        }.getOrNull()
    }

    private fun valid(screen: Screen, model: AppModel): Boolean = when (screen) {
        is Screen.Level -> model.level(screen.number) != null
        is Screen.Class -> model.programById(screen.programId) != null
        is Screen.Workout -> model.training.active()?.programId == screen.programId && model.programById(screen.programId) != null
        is Screen.EditDay -> model.draft?.id == screen.dayId || CustomDays.isCustom(screen.dayId) && model.customDay(screen.dayId) != null
        else -> true
    }

    private fun screenJson(screen: Screen): JSONObject = JSONObject().apply {
        when (screen) {
            Screen.Today -> put("screen", "today")
            Screen.Training -> put("screen", "training")
            Screen.Progress -> put("screen", "progress")
            Screen.Timer -> put("screen", "timer")
            Screen.Settings -> put("screen", "settings")
            is Screen.Level -> put("screen", "level").put("level", screen.number)
            is Screen.Class -> put("screen", "class").put("program", screen.programId)
            is Screen.Workout -> put("screen", "workout").put("program", screen.programId)
            is Screen.EditDay -> put("screen", "edit").put("program", screen.dayId)
        }
    }

    private fun screenOf(o: JSONObject): Screen? = when (o.optString("screen")) {
        "today" -> Screen.Today
        "training" -> Screen.Training
        "progress" -> Screen.Progress
        "timer" -> Screen.Timer
        "settings" -> Screen.Settings
        "level" -> Screen.Level(o.optInt("level"))
        "class" -> o.optString("program").ifEmpty { null }?.let(Screen::Class)
        "workout" -> o.optString("program").ifEmpty { null }?.let { Screen.Workout(it) }
        "edit" -> o.optString("program").ifEmpty { null }?.let(Screen::EditDay)
        else -> null
    }
}
