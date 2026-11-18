package com.justdataplease.streetgym.app.data

/** Focus (one set per screen) or List (every set of the class with its tick), as Android's AppSettings.WorkoutView. */
enum class WorkoutView(val key: String) { FOCUS("focus"), LIST("list") }

/**
 * The athlete's choices kept on the phone beside the progress (Android: AppSettings, the "streetgym_settings"
 * preferences), under the same names and values with the preferences' name in front, as [StoredProgress] keeps its own.
 */
class AppSettings(private val store: KeyValueStore) {
    /** How a class is trained; Focus until the athlete picks List. */
    var workoutView: WorkoutView
        get() = if (store.get(KEY_WORKOUT_VIEW) == WorkoutView.LIST.key) WorkoutView.LIST else WorkoutView.FOCUS
        set(value) = store.set(KEY_WORKOUT_VIEW, value.key)

    private companion object {
        const val KEY_WORKOUT_VIEW = "streetgym_settings.workout_view"
    }
}
