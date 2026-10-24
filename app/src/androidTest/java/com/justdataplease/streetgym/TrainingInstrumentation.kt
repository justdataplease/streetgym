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

/** Runs only through the test APK. Restores every preference it touches. No network calls. */
class TrainingInstrumentation : Instrumentation() {
    private lateinit var activity: Activity
    private val basketball = false
    private val prefsNames = listOf("streetgym_progress", "street_basketball_progress",
        "street_basketball_skills", "street_basketball_plan", "streetgym_settings", "streetbasketball_settings")
    private var checks = 0
    private fun preferences(name: String) = targetContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val originals = prefsNames.associateWith { preferences(it).all.toMap() }
        var failure: Throwable? = null
        try {
            prefsNames.forEach { check(preferences(it).edit().clear().commit()) }
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
            val first = checkNotNull(find { it is CheckBox })
            runOnMainSync { first.performClick() }; settle()
            val active = checkNotNull(repository.activeWorkout())
            check(active.completedSetIds.size == 1)
            report("Default light theme, dedicated navigation and first-session set saving")

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
            check((find { it is CheckBox } as CheckBox).isChecked)
            report("Dark-mode recreation keeps settings screen, active session and checked set")

            runOnMainSync { activity.finish() }; settle(); launch()
            check(AppSettings(targetContext).darkMode && AppColors.darkMode)
            check(repository.activeWorkout() == active)
            clickText(R.string.resume_workout)
            check((find { it is CheckBox } as CheckBox).isChecked)
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
        } catch (error: Throwable) { failure=error }
        finally {
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
    private fun clickDialog(resource:Int) {
        val label=targetContext.getString(resource)
        repeat(40) {
            val button=uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(label)?.firstOrNull { it.text?.toString()?.equals(label,true)==true && it.isClickable }
            if(button!=null) { check(button.performAction(AccessibilityNodeInfo.ACTION_CLICK));settle();return };SystemClock.sleep(100)
        }
        error("Missing dialog action: $label")
    }
    private fun find(predicate:(View)->Boolean):View? { var result:View?=null;runOnMainSync { fun visit(view:View) { if(result!=null)return;if(predicate(view))result=view else if(view is ViewGroup)for(i in 0 until view.childCount)visit(view.getChildAt(i)) };visit(activity.window.decorView) };return result }
    private fun settle() { SystemClock.sleep(150);runOnMainSync {} }
    private fun report(message:String) { checks++;sendStatus(0,Bundle().apply { putString("stream","\nPASS $checks: $message\n") }) }
}
