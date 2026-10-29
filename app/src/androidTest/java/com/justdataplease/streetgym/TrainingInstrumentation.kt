package com.justdataplease.streetgym

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.AdapterView
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import com.justdataplease.streetgym.billing.Entitlement
import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.AssetCurriculumRepository
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.progress.Intensity
import com.justdataplease.streetgym.data.progress.SharedPreferencesProgressRepository
import com.justdataplease.streetgym.data.skills.SkillCurriculumReader
import com.justdataplease.streetgym.data.skills.SkillLevel
import com.justdataplease.streetgym.leaderboard.Scores
import com.justdataplease.streetgym.ui.AppColors
import com.justdataplease.streetgym.ui.AppSettings
import com.justdataplease.streetgym.ui.ExerciseAnimationView
import com.justdataplease.streetgym.ui.FocusWorkoutView
import com.justdataplease.streetgym.ui.StreetTheme
import com.justdataplease.streetgym.ui.HistoryView
import com.justdataplease.streetgym.ui.ParkSceneView
import com.justdataplease.streetgym.ui.WorkoutView
import com.justdataplease.streetgym.ui.setPrescription
import com.justdataplease.streetgym.ui.systemAnimationsEnabled
import com.justdataplease.streetgym.timer.*
import com.justdataplease.monetization.NativeAdSlot
import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.ParcelFileDescriptor
import android.widget.ScrollView

/**
 * Runs only through the test APK. Restores every preference it touches. Purchases are cached-ownership
 * fixtures, never a transaction. Only the free-tier checks let Google demo ad slots attach.
 */
import java.util.Locale

class TrainingInstrumentation : Instrumentation() {
    private lateinit var activity: Activity
    private val basketball = false
    private val prefsNames = listOf("streetgym_progress", "street_basketball_progress",
        "street_basketball_skills", "street_basketball_plan", "streetgym_settings", "streetbasketball_settings", "streetgym_timing", "streetgym_ad_breaks", "streetgym_billing",
        "streetgym_library", "streetgym_level_names", "streetgym_account", "streetgym_boards", "streetgym_parks")
    private var checks = 0
    private fun preferences(name: String) = targetContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val originals = prefsNames.associateWith { preferences(it).all.toMap() }
        var failure: Throwable? = null
        try {
            prefsNames.forEach { check(preferences(it).edit().clear().commit()) }
            // Isolate workout/timing tests from the external ad network using test-only cached ownership.
            check(preferences(Entitlement.PREFS).edit().putBoolean(Entitlement.PLUS_OWNED, true).commit())
            if (!basketball) check(preferences("street_basketball_progress").edit().putString("legacy_marker", "kept").commit())
            val catalog = AssetCurriculumRepository(targetContext).load().getOrThrow()
            val programs = catalog.levels.flatMap { it.programs }
            check(catalog.levels.size == (if (basketball) 7 else 10) && programs.size == if (basketball) 48 else 1000)
            check(programs.map { it.id }.distinct().size == programs.size)
            check(programs.all { it.id.startsWith("bb:") == basketball && it.hasDetails })
            val assets = targetContext.assets.list("").orEmpty().toSet()
            check(("basketball_curriculum.json" in assets) == basketball)
            check(("curriculum.json" in assets) != basketball)
            check(("exercise_animations.json" in assets) != basketball)
            report("Own sport catalog, stable identities and packaged asset separation")

            // Focus is the default; the list scenarios below run in the List view.
            check(AppSettings(targetContext).workoutView == WorkoutView.FOCUS)
            AppSettings(targetContext).workoutView = WorkoutView.LIST
            // These scenarios train an athlete past the first-launch placement; the last scenario is the first launch.
            AppSettings(targetContext).placementDone = true
            val repository = SharedPreferencesProgressRepository(targetContext)
            launch()
            check(AppSettings(targetContext).theme == StreetTheme.CONCRETE && AppColors.theme == StreetTheme.CONCRETE && !AppColors.darkMode)
            check(find { it.contentDescription == "Basketball" } == null)
            checkParkScenePlayback()
            clickText(R.string.start_today)
            check(repository.activeWorkout()?.programId == programs.first().id)
            val first = checkNotNull(find { it is CheckBox && it.contentDescription?.toString()?.startsWith("Mark set ") == true })
            runOnMainSync { first.performClick() }; settle()
            val active = checkNotNull(repository.activeWorkout())
            check(active.completedSetIds.size == 1)
            report("Default Concrete & Tape theme, dedicated navigation and first-session set saving")

            val scroller = checkNotNull(find { it is android.widget.ScrollView }) as android.widget.ScrollView
            runOnMainSync { scroller.scrollTo(0, targetContext.resources.displayMetrics.density.times(600).toInt()) }; settle()
            val scrollY = scroller.scrollY
            check(scrollY > 0)
            runOnMainSync { first.performClick() }; settle()
            check(repository.activeWorkout()?.completedSetIds?.isEmpty() == true)
            check(scroller === find { it is android.widget.ScrollView } && scroller.scrollY == scrollY)
            runOnMainSync { first.performClick() }; settle()
            check(repository.activeWorkout()?.completedSetIds?.size == 1 && scroller.scrollY == scrollY)
            report("Individual set checks preserve the same scroll view and exact position")
            val warmup = checkNotNull(find { it is CheckBox && it.contentDescription == "Complete all Warm-up sets" })
            val warmupSets = programs.first().warmup.sumOf { it.sets.size }
            runOnMainSync { warmup.performClick() }; settle()
            check(repository.activeWorkout()?.completedSetIds?.size == warmupSets)
            check(scroller.scrollY == scrollY)
            runOnMainSync { warmup.performClick() }; settle()
            check(repository.activeWorkout()?.completedSetIds?.isEmpty() == true && scroller.scrollY == scrollY)
            runOnMainSync { first.performClick() }; settle()
            check(repository.activeWorkout() == active)
            report("Whole-section completion and undo preserve other sets and scroll position")


            nav(R.string.nav_settings)
            check(themeRow(StreetTheme.CONCRETE).isSelected && StreetTheme.entries.size == 3)
            pickTheme(StreetTheme.NIGHT_PARK)
            check(AppSettings(targetContext).theme == StreetTheme.NIGHT_PARK && AppColors.darkMode)
            check(repository.activeWorkout() == active)
            check(themeRow(StreetTheme.NIGHT_PARK).isSelected && !themeRow(StreetTheme.CONCRETE).isSelected)
            nav(R.string.nav_today); clickText(R.string.resume_workout)
            check((find { it is CheckBox && it.contentDescription?.toString()?.startsWith("Mark set ") == true } as CheckBox).isChecked)
            report("Picking Night Park recreates on the settings screen and keeps the active session and checked set")

            runOnMainSync { activity.finish() }; settle(); launch()
            check(AppSettings(targetContext).theme == StreetTheme.NIGHT_PARK && AppColors.darkMode)
            check(repository.activeWorkout() == active)
            clickText(R.string.resume_workout)
            check((find { it is CheckBox && it.contentDescription?.toString()?.startsWith("Mark set ") == true } as CheckBox).isChecked)
            report("Cold activity restart retains the picked theme and training progress")

            nav(R.string.nav_levels)
            clickLabel(catalog.levels.first().name.resolve())
            clickLabel(programs[2].name.resolve())
            val locked = find { it is TextView && it.text == targetContext.getString(R.string.assigned_later) }
            check(locked != null && !locked.isEnabled)
            check(repository.activeWorkout() == active)
            report("Future sessions remain locked after the sport split")

            val versionLine = targetContext.getString(R.string.version_format,
                targetContext.packageManager.getPackageInfo(targetContext.packageName, 0).versionName)
            nav(R.string.nav_settings)
            repeat(9) { clickLabel(versionLine) }
            check(!AppSettings(targetContext).developerUnlocked)
            clickLabel(versionLine)
            check(AppSettings(targetContext).developerUnlocked && !AppSettings(targetContext).inspectAllDays)
            clickDialog(R.string.developer_inspect_all); clickDialog(R.string.developer_done)
            check(AppSettings(targetContext).inspectAllDays)
            nav(R.string.nav_levels)
            clickLabel(catalog.levels.first().name.resolve())
            clickLabel(programs[2].name.resolve())
            check(shown(R.string.inspect_only_note) && find { it is ExerciseAnimationView } != null)
            check(!shown(R.string.start_workout) && !shown(R.string.assigned_later) && !shown("Complete day"))
            check(find { it is TextView && it.text == targetContext.getString(R.string.inspect_only) && !it.isEnabled } != null)
            check(repository.activeWorkout() == active && repository.completedProgramIds().isEmpty())
            nav(R.string.nav_settings)
            clickLabel(versionLine); clickDialog(R.string.developer_hide)
            check(!AppSettings(targetContext).developerUnlocked && !AppSettings(targetContext).inspectAllDays)
            nav(R.string.nav_levels)
            clickLabel(catalog.levels.first().name.resolve())
            clickLabel(programs[2].name.resolve())
            check(find { it is TextView && it.text == targetContext.getString(R.string.assigned_later) && !it.isEnabled } != null)
            check(!shown(R.string.inspect_only_note))
            report("Ten taps on the version open developer settings; Inspect all days shows a future day read-only")

            nav(R.string.nav_today); clickText(R.string.resume_workout)
            clickText(R.string.finish_workout); clickDialog(R.string.finish); clickDialog(R.string.finish)
            check(repository.activeWorkout() == null)
            check(repository.completedProgramIds() == setOf(programs.first().id))
            check(repository.sessionHistory().size == 1)
            report("Normal finish flow saves history and advances the guided sequence")

            val firstName = programs.first().name.resolve()
            val monthCount = targetContext.resources.getQuantityString(R.plurals.calendar_month_count_current, 1, 1)
            nav(R.string.nav_progress); clickText(R.string.progress_tab_history)
            check(AppSettings(targetContext).historyView == HistoryView.LIST && shown(firstName) && !shown(monthCount))
            clickText(R.string.history_view_calendar)
            check(AppSettings(targetContext).historyView == HistoryView.CALENDAR && shown(monthCount) && !shown(firstName))
            val todayParts = setOf(targetContext.getString(R.string.calendar_today_description),
                targetContext.resources.getQuantityString(R.plurals.calendar_workouts_description, 1, 1))
            val todayCell = checkNotNull(find { it.contentDescription?.split(", ")?.containsAll(todayParts) == true }) { "Today is not marked" }
            runOnMainSync { todayCell.performClick() }; settle()
            check(todayCell.isSelected && shown(firstName))
            clickLabel(firstName)
            check(shown(R.string.workout_overview) && shown(R.string.repeat_day))
            nav(R.string.nav_progress)
            check(shown(monthCount) && shown(firstName))
            clickText(R.string.history_view_list)
            check(AppSettings(targetContext).historyView == HistoryView.LIST && !shown(monthCount) && shown(firstName))
            report("History calendar marks today's workout, lists it under the day, opens its overview and returns to the list")

            val skillBook = targetContext.assets.open("curriculum.json").bufferedReader().use { SkillCurriculumReader.read(it.readText()) }
            fun skills() = skillBook.progress(Scores.credits(repository.sessionHistory()))
            val repeatDay = programs.first()
            val repeatCompleted = repository.completedProgramIds()
            val repeatSkills = skills()
            nav(R.string.nav_levels); clickLabel(catalog.levels.first().name.resolve()); clickLabel(repeatDay.name.resolve())
            check(shown(R.string.repeat_note) && !shown(R.string.workout_completed))
            clickText(R.string.repeat_day)
            check(repository.activeWorkout()?.let { it.programId == repeatDay.id && it.repeat } == true)
            check(shown(R.string.repeat_active_title) && shown(R.string.repeat_note) && !shown("Complete day"))
            // "Did more or fewer?" logs what the set really was, and ticks it.
            clickText(R.string.set_done_ask)
            setDialogText("15")
            clickDialog(R.string.set_done_save)
            check(repository.activeWorkout()?.let { it.done.values.toList() == listOf(15) && it.completedSetIds.size == 1 } == true)
            check(shown(targetContext.resources.getQuantityString(R.plurals.set_done_reps, 15, 15)) ||
                shown(targetContext.getString(R.string.set_done_seconds, 15)))
            nav(R.string.nav_levels); clickLabel(catalog.levels.first().name.resolve()); clickLabel(programs[1].name.resolve())
            clickText(R.string.start_workout); clickDialog(R.string.cancel)
            check(repository.activeWorkout()?.let { it.repeat && it.completedSetIds.size == 1 } == true)
            nav(R.string.nav_today)
            check(shown(R.string.repeat_note) && shown(repeatDay.name.resolve()) && !shown("Complete day"))
            clickText(R.string.resume_workout)
            clickText(R.string.finish_workout); clickDialog(R.string.finish); clickDialog(R.string.finish)
            check(repository.activeWorkout() == null && repository.levelReplay() == null)
            check(repository.completedProgramIds() == repeatCompleted)
            // The repeat's ticked set counts like any class's, with its reps; nothing goes down.
            check(skills().statuses.zip(repeatSkills.statuses).all { (now, before) -> now.sets >= before.sets && now.level >= before.level })
            check(repository.sessionHistory().first { it.repeat }.credits?.values?.sum() == 1)
            val repeatHistory = repository.sessionHistory()
            check(repeatHistory.size == 2 && repeatHistory.count { !it.repeat } == 1)
            check(repeatHistory.first().let { it.repeat && it.programId == repeatDay.id && it.effortRating != null })
            nav(R.string.nav_today)
            check(shown(programs[1].name.resolve()) && shown(R.string.start_today) && !shown(R.string.repeat_note))
            nav(R.string.nav_progress); clickText(R.string.progress_tab_history)
            check(shown(targetContext.getString(R.string.workouts_completed_format, 1, programs.size)))
            check(find { it is TextView && it.text.startsWith(targetContext.getString(R.string.history_repeat, "")) } != null)
            report("Repeating a completed day asks before replacing a workout, logs a set done bigger than planned that counts toward skills, and leaves progress and Today unchanged")
            if (basketball) {
                nav(R.string.nav_levels); clickLabel(catalog.levels.first().name.resolve()); clickLabel(programs.first().name.resolve())
                clickText(R.string.start_workout)
                check(repository.activeWorkout()?.programId == programs.first().id && repository.activeWorkout()?.completedSetIds?.isEmpty() == true)
                nav(R.string.nav_progress)
                val skill = checkNotNull(find { it is CheckBox })
                runOnMainSync { skill.performClick() }; settle()
                check("l1:c1" in preferences("street_basketball_skills").getStringSet("passed", emptySet()).orEmpty())
                nav(R.string.nav_today); nav(R.string.nav_progress)
                check((find { it is CheckBox } as CheckBox).isChecked)
                report("Basketball repeat sessions and persistent skill checks")
            } else {
                check(preferences("street_basketball_progress").getString("legacy_marker", null) == "kept")
                report("Legacy basketball preferences remain untouched in the gymnastics app")
            }
            nav(R.string.nav_settings)
            pickTheme(StreetTheme.WHEATPASTE)
            check(AppSettings(targetContext).theme == StreetTheme.WHEATPASTE && !AppColors.darkMode)
            check(repository.completedProgramIds() == setOf(programs.first().id))
            report("Switching to Wheatpaste retains completed history")
            nav(R.string.nav_today)
            check(find { it is NativeAdSlot } == null)
            nav(R.string.nav_settings)
            check(find { it is TextView && it.text == targetContext.getString(R.string.plus_owned) } != null)
            check(find { it is TextView && it.text == targetContext.getString(R.string.restore_purchase) } != null)
            report("Cached paid entitlement hides ad slots and offers purchase restoration")

            nav(R.string.nav_today); clickLabel("Complete day"); clickDialogLabel("Complete")
            check(repository.completedProgramIds() == programs.take(2).map { it.id }.toSet())
            check(repository.sessionHistory().count { it.manuallyCompleted } == 1)
            clickText(R.string.start_today)
            check(repository.activeWorkout()?.programId == programs[2].id)
            report("Complete day records explicit completion and immediately unlocks the next workout")
            nav(R.string.nav_levels); clickLabel(catalog.levels.first().name.resolve())
            clickLabel("Complete level"); clickDialogLabel("Complete")
            check(repository.activeWorkout() == null)
            check(repository.completedProgramIds() == catalog.levels.first().programs.map { it.id }.toSet())
            val historySize = repository.sessionHistory().size
            repository.completePrograms(catalog.levels.first().programs.map { it.id })
            check(repository.sessionHistory().size == historySize)
            check(repository.sessionHistory().count { !it.manuallyCompleted && !it.repeat } == 1)
            nav(R.string.nav_today); clickText(R.string.start_today)
            check(repository.activeWorkout()?.programId == catalog.levels[1].programs.first().id)
            report("Complete level preserves existing history without duplicates and unlocks the next level")

            val focusProgram = catalog.levels[1].programs.first()
            nav(R.string.nav_levels); clickLabel(catalog.levels[1].name.resolve()); clickLabel(focusProgram.name.resolve())
            clickText(R.string.workout_view_focus)
            check(AppSettings(targetContext).workoutView == WorkoutView.FOCUS)
            check((find { it is TextView && it.text == targetContext.getString(R.string.workout_view_focus) } as TextView).isSelected)
            clickText(R.string.resume_workout)
            check(checkNotNull(find { it is FocusWorkoutView }).keepScreenOn)
            check(find { it is CheckBox } == null && find { it is NativeAdSlot } == null)
            check(find { it.contentDescription?.toString()?.let { label -> label.startsWith("Complete set ") || label.startsWith("Start the ") } == true } != null)
            checkDoneVisible()
            report("Focus is picked on the overview and opens one set per screen, screen on, no ad cards")

            var restSeen = false
            var focusDone = 0
            while (focusDone < 3 || !restSeen) {
                check(focusDone < 12) { "No rest screen after $focusDone sets" }
                completeFocusSet(); focusDone++
                if (hasText(R.string.focus_skip_rest)) {
                    if (!restSeen) {
                        check(find { it is TextView && it.text.startsWith("Next: ") && it.text.contains(" · set ") } != null)
                        val before = readoutSeconds()
                        clickText(R.string.focus_add_rest)
                        check(readoutSeconds() > before + 10)
                        checkDoneVisible()
                    }
                    restSeen = true
                    clickText(R.string.focus_skip_rest)
                    check(!hasText(R.string.focus_skip_rest))
                }
            }
            check(repository.activeWorkout()?.completedSetIds?.size == focusDone)
            report("Done · Next saves each set; the rest screen adds 15 s and Skip rest returns to the next set")

            val position = focusPosition()
            check(position.size == 2)
            val focusChecked = checkNotNull(repository.activeWorkout()).completedSetIds
            clickDescription(R.string.switch_to_list)
            check(AppSettings(targetContext).workoutView == WorkoutView.LIST && find { it is FocusWorkoutView } == null)
            val boxes = findAll { it is CheckBox && it.contentDescription?.toString()?.startsWith("Mark set ") == true }
            check(boxes.count { (it as CheckBox).isChecked } == focusChecked.size)
            clickDescription(R.string.switch_to_focus)
            check(find { it is FocusWorkoutView } != null && focusPosition() == position)
            check(repository.activeWorkout()?.completedSetIds == focusChecked)
            report("List shows the same checked sets and switching back keeps the focus position")

            rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            check(focusPosition() == position); checkDoneVisible(); checkTwoPanes()
            rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            check(focusPosition() == position); checkDoneVisible()
            runOnMainSync { activity.finish() }; settle(); launch(); clickText(R.string.resume_workout)
            check(find { it is FocusWorkoutView } != null && focusPosition() == position)
            report("Rotation and a cold restart keep the focus position")

            displayFontScale = shell("settings get system font_scale").trim()
            reconfigure("wm size 720x1280", "wm density 320", "settings put system font_scale 1.3")
            check(activity.resources.configuration.fontScale > 1.29f && activity.resources.configuration.screenWidthDp <= 360)
            checkDoneVisible()
            rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            checkDoneVisible(); checkTwoPanes()
            rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            checkDoneVisible()
            var smallRest = false
            repeat(6) {
                if (!smallRest) {
                    completeFocusSet()
                    if (hasText(R.string.focus_skip_rest)) { checkDoneVisible(); clickText(R.string.focus_skip_rest); smallRest = true }
                }
            }
            check(smallRest)
            restoreDisplay()
            report("At font scale 1.3 on a 360 x 640 dp phone, Done stays fully visible without scrolling in both orientations")

            var holdStart: TextView? = null
            var skips = 0
            while (holdStart == null) {
                check(skips++ < 60 && !hasText(R.string.finish_workout)) { "No timed set of 30 s or less" }
                val start = find { it is TextView && (Regex("Start (\\d+) s").matchEntire(it.text)?.groupValues?.get(1)?.toInt() ?: 99) <= 30 }
                if (start != null) holdStart = start as TextView else clickText(R.string.focus_skip)
            }
            val hold = checkNotNull(holdStart)
            val holdSeconds = Regex("\\d+").find(hold.text)!!.value.toInt()
            val beforeHold = checkNotNull(repository.activeWorkout()).completedSetIds.size
            runOnMainSync { hold.performClick() }; settle()
            check(hasText(R.string.focus_done))
            val firstReading = readoutSeconds()
            // A busy emulator can lag a tick behind; the countdown must still start near the set's time and run.
            check(firstReading in (holdSeconds - 2)..holdSeconds)
            awaitFor(4000) { readoutSeconds() < firstReading }
            val sideTwo = targetContext.getString(R.string.focus_start_side, 2)
            awaitFor(holdSeconds * 1000L + 4000L) { readoutSeconds() == 0 }
            if (find { it is TextView && it.text.toString() == sideTwo } != null) {
                clickLabel(sideTwo)
                awaitFor(holdSeconds * 1000L + 4000L) { readoutSeconds() == 0 && find { it is TextView && it.text.toString() == sideTwo } == null }
            }
            check(hasText(R.string.focus_done))
            clickText(R.string.focus_done)
            check(repository.activeWorkout()?.completedSetIds?.size == beforeHold + 1)
            if (hasText(R.string.focus_skip_rest)) clickText(R.string.focus_skip_rest)
            report("A timed set counts down from Start, ends with the timer cue and then offers Done")

            nav(R.string.nav_settings); pickTheme(StreetTheme.NIGHT_PARK)
            check(AppSettings(targetContext).theme == StreetTheme.NIGHT_PARK && AppColors.darkMode)
            nav(R.string.nav_today); clickText(R.string.resume_workout)
            val night = checkNotNull(find { it is FocusWorkoutView })
            check((night.background as ColorDrawable).color == AppColors.BACKGROUND)
            checkDoneVisible()
            var focusSteps = 0
            while (true) {
                check(focusSteps++ < 200) { "Focus workout did not reach the summary" }
                when {
                    hasText(R.string.focus_do_skipped) -> clickText(R.string.focus_do_skipped)
                    hasText(R.string.finish_workout) -> break
                    hasText(R.string.focus_skip_rest) -> clickText(R.string.focus_skip_rest)
                    else -> completeFocusSet()
                }
            }
            check(hasText(R.string.focus_summary_complete) && find { it is NativeAdSlot } == null)
            check(uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText("Close ×").isNullOrEmpty())
            report("Night Park focus run reaches the summary, and section ends show no ads for an owner")

            val focusHistoryBefore = repository.sessionHistory().size
            clickText(R.string.finish_workout); clickDialog(R.string.finish)
            check(repository.activeWorkout() == null && focusProgram.id in repository.completedProgramIds())
            val saved = repository.sessionHistory()
            check(saved.size == focusHistoryBefore + 1 && saved.first().programId == focusProgram.id && saved.first().effortRating != null)
            // The scenarios after this one run in Wheatpaste, so all four themes carry part of the suite.
            nav(R.string.nav_settings); pickTheme(StreetTheme.WHEATPASTE)
            check(AppSettings(targetContext).theme == StreetTheme.WHEATPASTE && !AppColors.darkMode)
            report("The focus summary leads into the effort rating and saves history")

            val billing = preferences(Entitlement.PREFS)
            // The tier scenarios check sets and Finish in the list view; the focus runs above left Focus picked.
            AppSettings(targetContext).workoutView = WorkoutView.LIST
            val levelFour = catalog.levels[3].programs.minBy { it.number }
            val paywall = targetContext.getString(R.string.paywall_level, 4)
            repository.completePrograms((catalog.levels[1].programs + catalog.levels[2].programs).map { it.id })
            nav(R.string.nav_today)
            check(!shown(paywall))
            clickText(R.string.start_today)
            check(repository.activeWorkout()?.programId == levelFour.id)
            val levelFourSet = checkNotNull(find { it is CheckBox && it.contentDescription?.toString()?.startsWith("Mark set ") == true })
            runOnMainSync { levelFourSet.performClick() }; settle()
            await { repository.activeWorkout()?.completedSetIds?.size == 1 }
            val lockedActive = checkNotNull(repository.activeWorkout())
            check(lockedActive.completedSetIds.size == 1)
            check(find { it is NativeAdSlot } == null)
            nav(R.string.nav_today); check(find { it is NativeAdSlot } == null)
            report("Owned user starts Level 4 and every ad slot stays empty")

            check(billing.edit().clear().commit())
            // Billing replies arrive on their own schedule under load: poll instead of a fixed settle.
            await { Entitlement.tier(targetContext) == Tier.PRO }
            check(Entitlement.tier(targetContext) == Tier.PRO && Entitlement.adFree(targetContext))
            nav(R.string.nav_today)
            check(shown(R.string.resume_workout) && !shown(paywall) && find { it is NativeAdSlot } == null)
            nav(R.string.nav_settings)
            check(shown(R.string.pro_active) && shown(R.string.tier_current_pro))
            clickText(R.string.tier_free)
            await { Entitlement.tier(targetContext) == Tier.FREE && shown(R.string.tier_current_free) }
            check(Entitlement.tier(targetContext) == Tier.FREE && !Entitlement.adFree(targetContext))
            check(!shown(R.string.pro_active) && shown(R.string.tier_current_free))
            report("Debug builds get Pro by default and Simulate tier drops them to Free")

            nav(R.string.nav_today)
            await { shown(paywall) }
            check(shown(paywall) && shown(R.string.restore_purchase))
            check(!shown(R.string.start_today) && !shown(R.string.resume_workout) && !shown("Complete day"))
            check(find { it is NativeAdSlot } != null)
            check(repository.activeWorkout() == lockedActive)
            nav(R.string.nav_levels)
            // Levels 1-3 are free; Levels 4 and 5 carry the Plus badge and 6 and 7 the Pro one.
            fun badges(tier: Int) = findAll { it is TextView && it.text.toString() == targetContext.getString(tier).uppercase() }.size
            check(badges(R.string.tier_plus) == 2 && badges(R.string.tier_pro) == catalog.levels.size - 5)
            clickLabel(catalog.levels[3].name.resolve())
            check(shown(paywall) && !shown("Complete level"))
            nav(R.string.nav_today); clickLabel(levelFour.name.resolve())
            check(shown(paywall) && !shown(R.string.start_workout) && !shown(R.string.resume_workout))
            check(repository.activeWorkout() == lockedActive)
            report("Free tier: Today, Training and Overview show the paywall, Complete level is hidden and ads show")

            check(billing.edit().putBoolean(Entitlement.PLUS_OWNED, true).commit())
            await { Entitlement.tier(targetContext) == Tier.PLUS }
            nav(R.string.nav_today); await { shown(R.string.resume_workout) }; clickText(R.string.resume_workout)
            check(shown(R.string.finish_workout))
            val completedBefore = repository.completedProgramIds()
            val historyBefore = repository.sessionHistory()
            check(billing.edit().putBoolean(Entitlement.PLUS_OWNED, false).commit())
            await { shown(paywall) }
            check(shown(paywall) && !shown(R.string.finish_workout))
            check(repository.activeWorkout() == lockedActive)
            check(repository.completedProgramIds() == completedBefore && repository.sessionHistory() == historyBefore)
            check(billing.edit().clear().putBoolean(Entitlement.PLUS_OWNED, true).commit())
            await { !shown(paywall) }
            check(!shown(paywall) && find { it is NativeAdSlot } == null)
            report("A refunded cache returns an active Level 4 class to Today's paywall and keeps its sets and history")

            // The clear above also dropped the simulated tier, so this debug build is Pro again: simulate Plus.
            Entitlement.setSimulatedTier(targetContext, Tier.PLUS)
            await { Entitlement.tier(targetContext) == Tier.PLUS }
            nav(R.string.nav_levels)
            check(badges(R.string.tier_plus) == 0 && badges(R.string.tier_pro) == catalog.levels.size - 5)
            clickLabel(catalog.levels[5].name.resolve())
            // The Plus block is built but hidden.
            val plusOffer = find { it is TextView && it.text.toString() == targetContext.getString(R.string.plus_title) && it.isShown }
            check(shown(targetContext.getString(R.string.paywall_level_pro, 6)) && plusOffer == null && !shown("Complete level"))
            Entitlement.setSimulatedTier(targetContext, null)
            report("Plus opens Levels 4 and 5; Levels 6 and 7 offer Pro only")

            nav(R.string.nav_timer); clickLabel("30 sec"); clickLabel("Start")
            val timing = TimingStore(targetContext)
            check(timing.load().countdown.running)
            clickLabel("Pause"); val remaining = timing.load().countdown.left
            SystemClock.sleep(250)
            check(timing.load().countdown.remaining(SystemClock.elapsedRealtime()) == remaining)
            clickLabel("Resume")
            runOnMainSync { activity.finish() }; settle(); launch(); nav(R.string.nav_timer)
            check(timing.load().countdown.running && timing.load().countdown.remaining(SystemClock.elapsedRealtime()) < remaining)
            clickLabel("Pause"); clickLabel("Reset")
            check(!timing.load().countdown.running && timing.load().countdown.left == 30_000L)
            report("Timer start, pause, resume, reset and restart persistence")
            clickLabel("Stopwatch"); clickLabel("Start"); SystemClock.sleep(250); clickLabel("Lap"); clickLabel("Pause")
            val watch = timing.load().stopwatch
            check(!watch.running && watch.banked > 0 && watch.laps.size == 1)
            runOnMainSync { activity.finish() }; settle(); launch(); nav(R.string.nav_timer)
            check(timing.load().stopwatch == watch && timing.load().tab == 1)
            clickLabel("Reset"); check(timing.load().stopwatch == Stopwatch())
            report("Stopwatch laps and paused time survive navigation and restart")
            val rotationMonitor = addMonitor(MainActivity::class.java.name, null, false)
            runOnMainSync { activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            activity = checkNotNull(waitForMonitorWithTimeout(rotationMonitor, 10000))
            removeMonitor(rotationMonitor); awaitNavigation()
            check(find { it is TextView && it.text == "Stopwatch" } != null)
            check(timing.load().tab == 1)
            val portraitMonitor = addMonitor(MainActivity::class.java.name, null, false)
            runOnMainSync { activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            activity = checkNotNull(waitForMonitorWithTimeout(portraitMonitor, 10000))
            removeMonitor(portraitMonitor); awaitNavigation()
            check(find { it is TextView && it.text == "Stopwatch" } != null)
            report("Timer tab and mode stay selected through landscape and portrait recreation")

            check(TimingAlerts.notifications(targetContext) && TimingAlerts.exact(targetContext))
            clickLabel("Alarm"); clickLabel("Set alarm"); check(timing.load().alarmAt > System.currentTimeMillis())
            clickLabel("Turn off"); check(timing.load().alarmAt == 0L)
            runOnMainSync { activity.finish() }; settle()
            TimingAlerts.update(targetContext) { it.copy(alarmAt = System.currentTimeMillis() + 1500L) }
            val notifications = targetContext.getSystemService(android.app.NotificationManager::class.java)
            // The alert saves its ringing state, then posts the notification: wait for both.
            await { timing.load().alarmRinging && notifications.activeNotifications.any { it.id == 711 } }
            check(timing.load().alarmRinging && timing.load().alarmAt == 0L)
            check(notifications.activeNotifications.any { it.id == 711 })
            launch(); clickDialogLabel("Stop")
            check(!timing.load().alarmRinging && notifications.activeNotifications.none { it.id == 711 })
            report("Clock alarm schedules, delivers a background notification and stops from the app")
            runOnMainSync { activity.finish() }; settle()
            TimingAlerts.update(targetContext) { it.copy(countdown = Countdown(1500L).start(SystemClock.elapsedRealtime())) }
            await { timing.load().timerRinging && notifications.activeNotifications.any { it.id == 710 } }
            check(timing.load().timerRinging && !timing.load().countdown.running)
            val alert = checkNotNull(notifications.activeNotifications.firstOrNull { it.id == 710 })
            alert.notification.actions.first().actionIntent.send()
            settle()
            check(!timing.load().timerRinging && notifications.activeNotifications.none { it.id == 710 })
            launch()
            report("Countdown delivers in the background and its notification Stop action clears ringing")

            runOnMainSync { activity.finish() }; settle()
            check(preferences("streetgym_progress").edit().clear().commit())
            check(preferences("streetgym_settings").edit().remove("placement_done").commit())
            launch()
            check(shown(R.string.placement_title) && !shown(R.string.start_today))
            clickText(R.string.placement_skip)
            check(AppSettings(targetContext).placementDone && repository.completedProgramIds().isEmpty())
            check(!shown(R.string.placement_title) && shown(R.string.start_today) && shown(programs.first().name.resolve()))
            nav(R.string.nav_levels); clickLabel(catalog.levels.first().name.resolve())
            check(shown(R.string.placement_level_one_hint) && shown("Complete level"))
            AppSettings(targetContext).placementDone = false
            nav(R.string.nav_today)
            check(shown(R.string.placement_title))
            val levelOneDays = catalog.levels.first().programs
            val levelOne = levelOneDays.map { it.id }.toSet()
            val dayFortyOne = levelOneDays.first { it.number == 41 }
            val levelTwo = catalog.levels[1]
            val levelTwoFirst = levelTwo.programs.minBy { it.number }
            val atDayFortyOne = targetContext.getString(R.string.placement_level_day, 1, 41)
            val atLevelTwo = targetContext.getString(R.string.level_number, 2)
            // Placed days are done but give no skills at all, like Complete day.
            fun placedForNothing(days: Int) = repository.sessionHistory().let { it.size == days && it.all { session -> session.placed } } &&
                Scores.credits(repository.sessionHistory()).isEmpty() && Scores.markedComplete(repository.sessionHistory()) == days &&
                skills().statuses.all { it.sets == 0 && it.level == SkillLevel.NONE }
            fun enter(test: Int, count: Int) {
                val field = checkNotNull(find { it is EditText && it.contentDescription == targetContext.getString(test) }) as EditText
                runOnMainSync { field.setText(count.toString()) }; settle()
            }
            // A fit adult who can't pull up yet starts inside Level 1, at day 41.
            enter(R.string.placement_pull_ups, 0); enter(R.string.placement_push_ups, 20)
            check(shown(R.string.placement_skip) && shown(R.string.placement_answer_all) &&
                !shown(targetContext.getString(R.string.placement_confirm, atDayFortyOne)))
            enter(R.string.placement_squats, 30)
            check(shown(targetContext.getString(R.string.placement_result_day, atDayFortyOne)))
            check(shown(R.string.placement_scratch) && shown(R.string.placement_no_credit) && !shown(R.string.placement_skip) &&
                !shown(R.string.placement_answer_all))
            enter(R.string.placement_pull_ups, 10); enter(R.string.placement_push_ups, 30)
            check(shown(targetContext.getString(R.string.placement_result, 2, levelTwo.name.resolve())))
            check(shown(targetContext.getString(R.string.placement_confirm, atLevelTwo)))
            enter(R.string.placement_pull_ups, 0); enter(R.string.placement_push_ups, 20)
            clickLabel(targetContext.getString(R.string.placement_confirm, atDayFortyOne))
            check(AppSettings(targetContext).placementDone &&
                repository.completedProgramIds() == levelOneDays.filter { it.number < 41 }.map { it.id }.toSet())
            check(placedForNothing(40))
            check(!shown(R.string.placement_title) && shown(dayFortyOne.name.resolve()) && shown(R.string.start_today))
            nav(R.string.nav_levels)
            check(shown(targetContext.getString(R.string.level_progress_format, 40, 100)))
            clickLabel(catalog.levels.first().name.resolve())
            check(shown(R.string.placement_level_one_hint) && shown("Complete level") && shown(R.string.complete_level_no_points))
            // A new athlete again. The owner's case, ten pull-ups and thirty push-ups with squats never counted, starts at
            // Level 2, and the card says what four more pull-ups would change; the whole of Level 1 is placed past.
            check(preferences("streetgym_progress").edit().clear().commit())
            AppSettings(targetContext).placementDone = false
            nav(R.string.nav_today)
            check(shown(R.string.placement_title))
            enter(R.string.placement_pull_ups, 10); enter(R.string.placement_push_ups, 30); enter(R.string.placement_squats, 0)
            check(shown(targetContext.getString(R.string.placement_better,
                targetContext.resources.getQuantityString(R.plurals.placement_more_pull_ups, 4, 4),
                targetContext.getString(R.string.placement_level_day, 2, 21))))
            clickLabel(targetContext.getString(R.string.placement_confirm, atLevelTwo))
            check(AppSettings(targetContext).placementDone && repository.completedProgramIds() == levelOne)
            check(placedForNothing(levelOne.size))
            check(!shown(R.string.placement_title) && shown(levelTwoFirst.name.resolve()) && shown(R.string.start_today))
            // Harder for today, from Today's card: the day's strength reps go up a quarter and its overview says so; the
            // overview sets it back.
            check(AppSettings(targetContext).intensityToday() == Intensity.NORMAL && shown(R.string.complete_day_no_points))
            clickText(R.string.intensity_harder)
            check(AppSettings(targetContext).intensityToday() == Intensity.HARDER)
            clickLabel(levelTwoFirst.name.resolve())
            check(find { it is TextView && it.text.toString().endsWith(" • " + targetContext.getString(R.string.intensity_harder)) } != null)
            // Level 2 opens with its easy re-entry class (round 9): push-ups at half the dose, no pulling.
            fun pushUps(day: TrainingProgram) = checkNotNull(day.exercises.firstOrNull { it.movementId == "pushup" }).sets.first()
            val harder = targetContext.setPrescription(pushUps(Intensity.HARDER.scale(levelTwoFirst)), Locale.ENGLISH)
            check(harder != targetContext.setPrescription(pushUps(levelTwoFirst), Locale.ENGLISH) && shown(harder))
            clickText(R.string.intensity_normal)
            check(AppSettings(targetContext).intensityToday() == Intensity.NORMAL &&
                shown(targetContext.setPrescription(pushUps(levelTwoFirst), Locale.ENGLISH)))
            nav(R.string.nav_levels)
            check(levelOne.size == 100 && shown(targetContext.getString(R.string.level_progress_format, 100, 100)))
            clickLabel(catalog.levels.first().name.resolve())
            check(!shown(R.string.placement_level_one_hint) && !shown("Complete level"))
            // Saved before placement existed: progress alone counts as placed.
            check(preferences("streetgym_settings").edit().remove("placement_done").commit())
            runOnMainSync { activity.finish() }; settle(); launch()
            check(!shown(R.string.placement_title) && AppSettings(targetContext).placementDone)
            clickText(R.string.start_today)
            check(repository.activeWorkout()?.programId == levelTwoFirst.id)
            report("First launch is a placement test: the weighted counts and pull-up gates pick a block start, a skip keeps Level 1 and its hint, placed days give no skills, Harder for today scales strength reps, and placement never returns")

            // Settings' Reset progress, with Level 1 complete and a Level 2 class in progress.
            nav(R.string.nav_settings)
            clickText(R.string.reset_button); clickDialog(R.string.reset_confirm)
            check(repository.completedProgramIds().isEmpty() && repository.sessionHistory().isEmpty())
            check(repository.activeWorkout() == null && repository.resetAt() > 0)
            check(!AppSettings(targetContext).placementDone && shown(R.string.placement_title))
            report("Reset progress in Settings erases finished classes, history and the class in progress, and asks where to start again")
        } catch (error: Throwable) { failure=error }
        finally {
            if (displayFontScale != null) runCatching { restoreDisplay() }
            TimingAlerts.stop(targetContext)
            TimingAlerts.update(targetContext) { TimingState() }
            if (::activity.isInitialized) { runOnMainSync { activity.finish() };settle() }
            originals.forEach { (name,values) ->
                val edit=preferences(name).edit().clear()
                values.forEach { (key,value) -> when(value) {
                    is String -> edit.putString(key,value)
                    is Boolean -> edit.putBoolean(key,value)
                    is Int -> edit.putInt(key,value)
                    is Long -> edit.putLong(key,value)
                    is Float -> edit.putFloat(key,value)
                    is Set<*> -> edit.putStringSet(key,value.filterIsInstance<String>().toSet())
                } }
                check(edit.commit())
            }
        }
        finish(if(failure==null) Activity.RESULT_OK else Activity.RESULT_CANCELED,Bundle().apply {
            putString("stream",if(failure==null) "\nPASS: $checks training/theme integration checks.\n" else "\nFAIL: ${failure.stackTraceToString()}\n")
        })
    }
    /** Exercise the real Canvas view and navigation: the choreography tests alone cannot catch skipped playback. */
    private fun checkParkScenePlayback() {
        check(targetContext.systemAnimationsEnabled()) { "Enable system animations for the park playback checks" }
        // Read the Canvas clock without adding test controls to the production view.
        val clock = ParkSceneView::class.java.getDeclaredField("clock").apply { isAccessible = true }
        fun scene() = checkNotNull(find { it is ParkSceneView }) as ParkSceneView
        fun time(view: ParkSceneView): Float {
            var seconds = 0f
            runOnMainSync { seconds = clock.getFloat(view) }
            return seconds
        }
        val first = scene()
        runOnMainSync { first.performClick() }
        SystemClock.sleep(400)
        check(time(first) in 0.1f..2f) { "Opening park scene did not start playing" }

        nav(R.string.nav_settings)
        nav(R.string.nav_today)
        val returned = scene()
        check(returned !== first)
        check(time(returned) < 2f) { "Returning to Today skipped to the final held skill" }
        report("Returning to Today starts the three-exercise routine from the beginning")

        var previous = time(returned)
        var repeated = false
        val deadline = SystemClock.uptimeMillis() + 45_000L
        while (!repeated && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(100)
            val current = time(returned)
            repeated = previous > 10f && current < 1f
            previous = current
        }
        check(repeated) { "The three-exercise routine stayed in its final hold instead of repeating" }
        val restarted = time(returned)
        SystemClock.sleep(300)
        check(time(returned) > restarted) { "The second routine did not animate" }
        report("The complete routine repeats automatically and continues animating on its second pass")

        runOnMainSync { returned.visibility = View.INVISIBLE }
        val paused = time(returned)
        SystemClock.sleep(250)
        check(time(returned) == paused) { "The hidden park kept animating" }
        runOnMainSync { returned.visibility = View.VISIBLE }
        SystemClock.sleep(250)
        check(time(returned) in 0.1f..1f) { "Making the park visible did not restart the routine" }
        report("The park pauses off-screen and starts the full routine when visible again")
    }

    private fun launch() { activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));awaitNavigation() }
    private fun awaitNavigation() { repeat(100) { if(find { it.contentDescription==targetContext.getString(R.string.nav_today) }!=null) return;SystemClock.sleep(100) };error("Training app did not load") }
    private fun nav(resource:Int) { val view=checkNotNull(find { it.contentDescription==targetContext.getString(resource) });runOnMainSync { view.performClick() };settle() }
    private fun clickText(resource:Int)=clickLabel(targetContext.getString(resource))
    private fun clickLabel(label:String) {
        var view=checkNotNull(find { it is TextView && it.text.toString()==label }) { "Missing: $label" }
        while(!view.isClickable && view.parent is View) {
            val parent=view.parent
            if(parent is AdapterView<*>) { val row=view;runOnMainSync { val position=parent.getPositionForView(row);parent.performItemClick(row,position,parent.getItemIdAtPosition(position)) };settle();return }
            view=parent as View
        }
        check(view.isClickable);val target=view;runOnMainSync { target.performClick() };settle()
    }
    private fun clickDialog(resource:Int) = clickDialogLabel(targetContext.getString(resource))
    private fun clickDialogLabel(label: String) {
        repeat(40) {
            val button=uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(label)?.firstOrNull { it.text?.toString()?.equals(label,true)==true && it.isClickable }
            if(button!=null) { check(button.performAction(AccessibilityNodeInfo.ACTION_CLICK));settle();return };SystemClock.sleep(100)
        }
        val labels = mutableListOf<String>()
        fun read(node: AccessibilityNodeInfo?) {
            if (node == null) return
            node.text?.toString()?.let(labels::add)
            for (i in 0 until node.childCount) read(node.getChild(i))
        }
        read(uiAutomation.rootInActiveWindow)
        error("Missing dialog action: $label. Visible text: $labels")
    }
    /** Types into the text field of the dialog in front. */
    private fun setDialogText(text: String) {
        fun editable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.isEditable) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let(::editable)?.let { return it }
            return null
        }
        repeat(40) {
            val field = uiAutomation.rootInActiveWindow?.let(::editable)
            if (field != null) {
                check(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
                    Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }))
                settle(); return
            }
            SystemClock.sleep(100)
        }
        error("No text field in the dialog")
    }
    private fun shown(label: String) = find { it is TextView && it.text.toString() == label } != null
    private fun shown(resource: Int) = shown(targetContext.getString(resource))
    private fun findAll(predicate: (View) -> Boolean): List<View> {
        val result = mutableListOf<View>()
        runOnMainSync { fun visit(view: View) { if (predicate(view)) result += view; if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i)) }; visit(activity.window.decorView) }
        return result
    }
    private fun find(predicate:(View)->Boolean):View? { var result:View?=null;runOnMainSync { fun visit(view:View) { if(result!=null)return;if(predicate(view))result=view else if(view is ViewGroup)for(i in 0 until view.childCount)visit(view.getChildAt(i)) };visit(activity.window.decorView) };return result }
    private fun await(condition: () -> Boolean) {
        repeat(120) { if (condition()) return; SystemClock.sleep(100) }; error("Timed out awaiting Android delivery")
    }
    private fun settle() { SystemClock.sleep(150);runOnMainSync {} }
    private fun report(message:String) { checks++;sendStatus(0,Bundle().apply { putString("stream","\nPASS $checks: $message\n") }) }

    // Focus workout helpers.
    private var displayFontScale: String? = null
    private val startPattern = Regex("Start \\d+ s|Start side \\d+")
    private val clockPattern = Regex("\\d{2}:\\d{2}")
    private fun hasText(resource: Int) = targetContext.getString(resource).let { label -> find { it is TextView && it.text.toString() == label } != null }
    private fun focusPosition() = findAll { it is TextView && it.text.startsWith("Set ") }.map { (it as TextView).text.toString() }
    private fun readoutSeconds(): Int {
        val text = (checkNotNull(find { it is TextView && clockPattern.matches(it.text) }) {
            val shown = findAll { it is TextView && it.isShown }.joinToString(" | ") { (it as TextView).text }
            "Missing countdown (destroyed=${activity.isDestroyed}): $shown"
        } as TextView).text.toString()
        return text.substringBefore(':').toInt() * 60 + text.substringAfter(':').toInt()
    }
    /** Starts a timed set first when needed; Done is offered while it counts down. */
    private fun completeFocusSet() {
        find { it is TextView && startPattern.matches(it.text) }?.let { start -> runOnMainSync { start.performClick() }; settle() }
        clickText(R.string.focus_done)
    }
    private fun clickDescription(resource: Int) {
        val view = checkNotNull(find { it.contentDescription == targetContext.getString(resource) })
        runOnMainSync { view.performClick() }; settle()
    }
    private fun checkDoneVisible() {
        val labels = setOf(targetContext.getString(R.string.focus_done), targetContext.getString(R.string.focus_skip_rest))
        val done = checkNotNull(find { it is TextView && (it.text.toString() in labels || startPattern.matches(it.text)) }) { "Missing Done" }
        var visible = false
        runOnMainSync {
            val rect = Rect()
            visible = done.isShown && done.getGlobalVisibleRect(rect) && rect.width() == done.width && rect.height() == done.height
        }
        check(visible) { "Done is clipped" }
        check(find { it is ScrollView } == null)
    }
    private fun checkTwoPanes() {
        val animation = checkNotNull(find { it is ExerciseAnimationView })
        val done = checkNotNull(find { it is TextView && (it.text.toString() == targetContext.getString(R.string.focus_done) || startPattern.matches(it.text)) })
        var sideBySide = false
        runOnMainSync {
            val a = IntArray(2).also(animation::getLocationOnScreen)
            val d = IntArray(2).also(done::getLocationOnScreen)
            sideBySide = a[0] + animation.width <= d[0]
        }
        check(sideBySide) { "Landscape focus is not two panes" }
    }
    private fun rotate(orientation: Int) {
        val monitor = addMonitor(MainActivity::class.java.name, null, false)
        runOnMainSync { activity.requestedOrientation = orientation }
        activity = checkNotNull(waitForMonitorWithTimeout(monitor, 10000))
        removeMonitor(monitor); awaitNavigation()
    }
    /** Taps [theme]'s row in Settings and follows the activity through the recreation. */
    private fun pickTheme(theme: StreetTheme) {
        val monitor = addMonitor(MainActivity::class.java.name, null, false)
        val row = themeRow(theme)
        runOnMainSync { row.performClick() }
        activity = checkNotNull(waitForMonitorWithTimeout(monitor, 10000))
        removeMonitor(monitor); awaitNavigation()
    }
    private fun themeRow(theme: StreetTheme): View {
        val name = targetContext.getString(theme.label)
        return checkNotNull(find { it.isClickable && it.contentDescription?.toString()?.startsWith("$name, ") == true }) { "No $name row" }
    }
    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    /** Runs display commands and follows the activity through every recreation they cause. */
    private fun reconfigure(vararg commands: String) {
        val monitor = addMonitor(MainActivity::class.java.name, null, false)
        commands.forEach { shell(it) }
        var next = monitor.waitForActivityWithTimeout(5000)
        while (next != null) { activity = next; next = monitor.waitForActivityWithTimeout(2000) }
        removeMonitor(monitor); awaitNavigation()
    }
    private fun restoreDisplay() {
        val scale = displayFontScale?.toFloatOrNull() ?: 1f
        displayFontScale = null
        reconfigure("settings put system font_scale $scale", "wm density reset", "wm size reset")
    }
    private fun awaitFor(millis: Long, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + millis
        while (SystemClock.uptimeMillis() < end) { if (condition()) return; SystemClock.sleep(200) }
        error("Timed out after $millis ms")
    }
}
