package com.justdataplease.streetgym.ui

import android.content.Context
import java.util.Locale

enum class WorkoutView(val key: String) { FOCUS("focus"), LIST("list") }

/** Progress → History as the list of classes or the month calendar ([historySection]). */
enum class HistoryView(val key: String) { LIST("list"), CALENDAR("calendar") }

class AppSettings(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /** The look picked in Settings. Before themes there was only a dark switch; dark then means Night Park. */
    var theme: StreetTheme
        get() = StreetTheme.fromKey(preferences.getString(KEY_THEME, null))
            ?: if (preferences.getBoolean(KEY_LEGACY_DARK_MODE, false)) StreetTheme.NIGHT_PARK else StreetTheme.DEFAULT
        set(value) = preferences.edit().putString(KEY_THEME, value.key).remove(KEY_LEGACY_DARK_MODE).apply()

    var keepScreenAwake: Boolean
        get() = preferences.getBoolean(KEY_KEEP_SCREEN_AWAKE, true)
        set(value) = preferences.edit().putBoolean(KEY_KEEP_SCREEN_AWAKE, value).apply()

    var soundCues: Boolean
        get() = preferences.getBoolean(KEY_SOUND_CUES, true)
        set(value) = preferences.edit().putBoolean(KEY_SOUND_CUES, value).apply()

    /** The History section's last choice, kept on this device; the list until the calendar is picked. */
    var historyView: HistoryView
        get() = if (preferences.getString(KEY_HISTORY_VIEW, null) == HistoryView.CALENDAR.key) HistoryView.CALENDAR else HistoryView.LIST
        set(value) = preferences.edit().putString(KEY_HISTORY_VIEW, value.key).apply()

    var workoutView: WorkoutView
        get() = if (preferences.getString(KEY_WORKOUT_VIEW, null) == WorkoutView.LIST.key) WorkoutView.LIST else WorkoutView.FOCUS
        set(value) = preferences.edit().putString(KEY_WORKOUT_VIEW, value.key).apply()

    /** The first-launch placement was answered or skipped ([com.justdataplease.streetgym.data.progress.Placement]). */
    var placementDone: Boolean
        get() = preferences.getBoolean(KEY_PLACEMENT_DONE, false)
        set(value) = preferences.edit().putBoolean(KEY_PLACEMENT_DONE, value).apply()

    /** The developer settings' passcode was accepted ([DeveloperMenu]); one tap on the version then opens them. */
    var developerUnlocked: Boolean
        get() = preferences.getBoolean(KEY_DEVELOPER_UNLOCKED, false)
        set(value) = preferences.edit().putBoolean(KEY_DEVELOPER_UNLOCKED, value).apply()

    /** Developer setting: days not reached yet, paid levels included, open read-only to inspect them. */
    var inspectAllDays: Boolean
        get() = preferences.getBoolean(KEY_INSPECT_ALL_DAYS, false)
        set(value) = preferences.edit().putBoolean(KEY_INSPECT_ALL_DAYS, value).apply()

    fun selectedLocale(): Locale = Locale.ENGLISH

    companion object {
        private const val KEY_DEVELOPER_UNLOCKED = "developer_unlocked"
        private const val KEY_INSPECT_ALL_DAYS = "inspect_all_days"
        private const val KEY_HISTORY_VIEW = "history_view"
        private const val PREFERENCES_NAME = "streetgym_settings"
        private const val KEY_THEME = "theme"
        private const val KEY_LEGACY_DARK_MODE = "dark_mode"
        private const val KEY_KEEP_SCREEN_AWAKE = "keep_screen_awake"
        private const val KEY_SOUND_CUES = "sound_cues"
        private const val KEY_WORKOUT_VIEW = "workout_view"
        private const val KEY_PLACEMENT_DONE = "placement_done"
    }
}
