package com.justdataplease.streetgym.app.data

import com.justdataplease.streetgym.data.equipment.EquipmentSwitches
import com.justdataplease.streetgym.data.progress.Intensity
import com.justdataplease.streetgym.data.skills.SkillSwitches
import kotlinx.datetime.TimeZone

/** Focus (one set per screen) or List (every set of the class with its tick), as Android's AppSettings.WorkoutView. */
enum class WorkoutView(val key: String) { FOCUS("focus"), LIST("list") }

/**
 * The athlete's choices kept on the phone beside the progress (Android: AppSettings, the "streetgym_settings"
 * preferences), under the same names and values with the preferences' name in front, as [StoredProgress] keeps its own.
 * A switch is "true" or "false"; a day is its number of days since 1970 as text.
 */
class AppSettings(private val store: KeyValueStore) {
    /** How a class is trained; Focus until the athlete picks List. */
    var workoutView: WorkoutView
        get() = if (store.get(KEY_WORKOUT_VIEW) == WorkoutView.LIST.key) WorkoutView.LIST else WorkoutView.FOCUS
        set(value) = store.set(KEY_WORKOUT_VIEW, value.key)

    /** Keep screen awake: on until switched off. The focus view keeps the screen on whatever it says, as on Android. */
    var keepScreenAwake: Boolean
        get() = flag(KEY_KEEP_SCREEN_AWAKE, default = true)
        set(value) = store.set(KEY_KEEP_SCREEN_AWAKE, value.toString())

    /** Rest timer sound: the beep at the end of a rest and of a timed set; on until switched off. */
    var soundCues: Boolean
        get() = flag(KEY_SOUND_CUES, default = true)
        set(value) = store.set(KEY_SOUND_CUES, value.toString())

    /** The skills the athlete switched off, or off from a rung on (Android: AppSettings.skillSwitches). */
    var skillSwitches: SkillSwitches
        get() = SkillSwitches.parse(store.get(KEY_SKILL_SWITCHES))
        set(value) = store.set(KEY_SKILL_SWITCHES, SkillSwitches.format(value).ifEmpty { null })

    /** The equipment the athlete's spot lacks, switched off in Settings; all on by default. */
    var equipment: EquipmentSwitches
        get() = EquipmentSwitches.parse(store.get(KEY_EQUIPMENT_OFF))
        set(value) = store.set(KEY_EQUIPMENT_OFF, EquipmentSwitches.format(value).ifEmpty { null })

    /** "How do you feel today?": the pick for today's classes, Normal again the next local day. */
    fun intensityToday(now: Long, zone: TimeZone): Intensity =
        if (store.get(KEY_INTENSITY_DAY)?.toLongOrNull() == LocalDays.epochDay(now, zone)) Intensity.fromName(store.get(KEY_INTENSITY))
        else Intensity.NORMAL

    fun setIntensityToday(value: Intensity, now: Long, zone: TimeZone) {
        store.set(KEY_INTENSITY, value.name)
        store.set(KEY_INTENSITY_DAY, LocalDays.epochDay(now, zone).toString())
    }

    private fun flag(key: String, default: Boolean): Boolean = store.get(key)?.toBooleanStrictOrNull() ?: default

    private companion object {
        const val KEY_WORKOUT_VIEW = "streetgym_settings.workout_view"
        const val KEY_KEEP_SCREEN_AWAKE = "streetgym_settings.keep_screen_awake"
        const val KEY_SOUND_CUES = "streetgym_settings.sound_cues"
        const val KEY_SKILL_SWITCHES = "streetgym_settings.skill_switches"
        const val KEY_EQUIPMENT_OFF = "streetgym_settings.equipment_off"
        const val KEY_INTENSITY = "streetgym_settings.intensity"
        const val KEY_INTENSITY_DAY = "streetgym_settings.intensity_day"
    }
}
