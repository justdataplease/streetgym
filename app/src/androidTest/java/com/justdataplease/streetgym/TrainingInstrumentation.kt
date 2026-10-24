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
import android.widget.Switch
import android.widget.TextView
import com.justdataplease.streetgym.data.AssetCurriculumRepository
import com.justdataplease.streetgym.data.progress.SharedPreferencesProgressRepository
import com.justdataplease.streetgym.ui.AppColors
import com.justdataplease.streetgym.ui.AppSettings
import com.justdataplease.streetgym.timer.*
import com.justdataplease.monetization.NativeAdSlot

/** Runs only through the test APK. Restores every preference it touches. No network calls. */
class TrainingInstrumentation : Instrumentation() {
    private lateinit var activity: Activity
    private val basketball = false
    private val prefsNames = listOf("streetgym_progress", "street_basketball_progress",
        "street_basketball_skills", "street_basketball_plan", "streetgym_settings", "streetbasketball_settings", "streetgym_timing", "streetgym_ad_breaks", "streetgym_billing")
    private var checks = 0
    private fun preferences(name: String) = targetContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val originals = prefsNames.associateWith { preferences(it).all.toMap() }
        var failure: Throwable? = null
        try {
            prefsNames.forEach { check(preferences(it).edit().clear().commit()) }
            // Isolate workout/timing tests from the external ad network using test-only cached ownership.
            check(preferences("streetgym_billing").edit().putBoolean("owned", true).commit())
            if (!basketball) check(preferences("street_basketball_progress").edit().putString("legacy_marker", "kept").commit())
            val catalog = AssetCurriculumRepository(targetContext).load().getOrThrow()
            val programs = catalog.levels.flatMap { it.programs }
            check(catalog.levels.size == 6 && programs.size == if (basketball) 48 else 600)
            check(programs.map { it.id }.distinct().size == programs.size)
            check(programs.all { it.id.startsWith("bb:") == basketball && it.hasDetails })
            val assets = targetContext.assets.list("").orEmpty().toSet()
            check(("basketball_curriculum.json" in assets) == basketball)
            check(("curriculum.json" in assets) != basketball)
            check(("exercise_animations.json" in assets) != basketball)
            report("Own sport catalog, stable identities and packaged asset separation")

            val repository = SharedPreferencesProgressRepository(targetContext)
            launch()
            check(!AppSettings(targetContext).darkMode && !AppColors.darkMode)
            check(find { it.contentDescription == "Basketball" } == null)
            clickText(R.string.start_today)
            check(repository.activeWorkout()?.programId == programs.first().id)
            val first = checkNotNull(find { it is CheckBox && it.contentDescription?.toString()?.startsWith("Mark set ") == true })
            runOnMainSync { first.performClick() }; settle()
            val active = checkNotNull(repository.activeWorkout())
            check(active.completedSetIds.size == 1)
            report("Default light theme, dedicated navigation and first-session set saving")

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
            val monitor = addMonitor(MainActivity::class.java.name, null, false)
            val toggle = checkNotNull(find { it is Switch && it.text == targetContext.getString(R.string.dark_mode) })
            runOnMainSync { toggle.performClick() }
            activity = checkNotNull(waitForMonitorWithTimeout(monitor, 10000))
            removeMonitor(monitor); awaitNavigation()
            check(AppSettings(targetContext).darkMode && AppColors.darkMode)
            check(repository.activeWorkout() == active)
            check((find { it is Switch && it.text == targetContext.getString(R.string.dark_mode) } as Switch).isChecked)
            nav(R.string.nav_today); clickText(R.string.resume_workout)
            check((find { it is CheckBox && it.contentDescription?.toString()?.startsWith("Mark set ") == true } as CheckBox).isChecked)
            report("Dark-mode recreation keeps settings screen, active session and checked set")

            runOnMainSync { activity.finish() }; settle(); launch()
            check(AppSettings(targetContext).darkMode && AppColors.darkMode)
            check(repository.activeWorkout() == active)
            clickText(R.string.resume_workout)
            check((find { it is CheckBox && it.contentDescription?.toString()?.startsWith("Mark set ") == true } as CheckBox).isChecked)
            report("Cold activity restart retains dark preference and training progress")

            nav(R.string.nav_levels)
            clickLabel(catalog.levels.first().name.resolve())
            clickLabel(programs[2].name.resolve())
            val locked = find { it is TextView && it.text == targetContext.getString(R.string.assigned_later) }
            check(locked != null && !locked.isEnabled)
            check(repository.activeWorkout() == active)
            report("Future sessions remain locked after the sport split")

            nav(R.string.nav_today); clickText(R.string.resume_workout)
            clickText(R.string.finish_workout); clickDialog(R.string.finish); clickDialog(R.string.finish)
            check(repository.activeWorkout() == null)
            check(repository.completedProgramIds() == setOf(programs.first().id))
            check(repository.sessionHistory().size == 1)
            report("Normal finish flow saves history and advances the guided sequence")
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
            val lightMonitor=addMonitor(MainActivity::class.java.name,null,false)
            val darkToggle=checkNotNull(find { it is Switch && it.text == targetContext.getString(R.string.dark_mode) })
            runOnMainSync { darkToggle.performClick() }
            activity=checkNotNull(waitForMonitorWithTimeout(lightMonitor,10000));removeMonitor(lightMonitor);awaitNavigation()
            check(!AppSettings(targetContext).darkMode && !AppColors.darkMode)
            check(repository.completedProgramIds() == setOf(programs.first().id))
            report("Switching back to Fresh Court retains completed history")
            nav(R.string.nav_today)
            check(find { it is NativeAdSlot } == null)
            nav(R.string.nav_settings)
            check(find { it is TextView && it.text == "Ads removed" } != null)
            check(find { it is TextView && it.text == "Restore purchase" } != null)
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
            check(repository.sessionHistory().count { !it.manuallyCompleted } == 1)
            nav(R.string.nav_today); clickText(R.string.start_today)
            check(repository.activeWorkout()?.programId == catalog.levels[1].programs.first().id)
            report("Complete level preserves existing history without duplicates and unlocks the next level")

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
            await { timing.load().alarmRinging }
            check(timing.load().alarmRinging && timing.load().alarmAt == 0L)
            val notifications = targetContext.getSystemService(android.app.NotificationManager::class.java)
            check(notifications.activeNotifications.any { it.id == 711 })
            launch(); clickDialogLabel("Stop")
            check(!timing.load().alarmRinging && notifications.activeNotifications.none { it.id == 711 })
            report("Clock alarm schedules, delivers a background notification and stops from the app")
            runOnMainSync { activity.finish() }; settle()
            TimingAlerts.update(targetContext) { it.copy(countdown = Countdown(1500L).start(SystemClock.elapsedRealtime())) }
            await { timing.load().timerRinging }
            check(timing.load().timerRinging && !timing.load().countdown.running)
            val alert = checkNotNull(notifications.activeNotifications.firstOrNull { it.id == 710 })
            alert.notification.actions.first().actionIntent.send()
            settle()
            check(!timing.load().timerRinging && notifications.activeNotifications.none { it.id == 710 })
            launch()
            report("Countdown delivers in the background and its notification Stop action clears ringing")
        } catch (error: Throwable) { failure=error }
        finally {
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
            putString("stream",if(failure==null) "\nPASS: $checks training/theme integration checks.\n" else "\nFAIL: ${failure?.stackTraceToString()}\n")
        })
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
    private fun find(predicate:(View)->Boolean):View? { var result:View?=null;runOnMainSync { fun visit(view:View) { if(result!=null)return;if(predicate(view))result=view else if(view is ViewGroup)for(i in 0 until view.childCount)visit(view.getChildAt(i)) };visit(activity.window.decorView) };return result }
    private fun await(condition: () -> Boolean) {
        repeat(120) { if (condition()) return; SystemClock.sleep(100) }; error("Timed out awaiting Android delivery")
    }
    private fun settle() { SystemClock.sleep(150);runOnMainSync {} }
    private fun report(message:String) { checks++;sendStatus(0,Bundle().apply { putString("stream","\nPASS $checks: $message\n") }) }
}
