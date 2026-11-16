package com.justdataplease.streetgym.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.SizeF
import com.justdataplease.streetgym.billing.Entitlement
import com.justdataplease.streetgym.billing.LevelAccess
import com.justdataplease.streetgym.data.AssetCurriculumRepository
import com.justdataplease.streetgym.data.model.TrainingExercise
import com.justdataplease.streetgym.data.progress.NextClass
import com.justdataplease.streetgym.data.progress.Placement
import com.justdataplease.streetgym.data.progress.SharedPreferencesProgressRepository
import com.justdataplease.streetgym.data.progress.Streak
import com.justdataplease.streetgym.data.progress.StreakDays
import com.justdataplease.streetgym.ui.AppSettings
import com.justdataplease.streetgym.ui.AppType
import com.justdataplease.streetgym.ui.HomeActivity
import com.justdataplease.streetgym.ui.MotionClip
import com.justdataplease.streetgym.ui.systemAnimationsEnabled
import java.util.TimeZone
import java.util.concurrent.Executors

/**
 * The "Next class" home-screen widget: the next class in the sequence (never a day or a date), its lead movement
 * playing, the streak's flame, and a tap that opens the class. The app keeps it in step through [watch]: finishing or
 * skipping a class, a reset, a level replay, a purchase or a new theme redraws it. The flame is for today, so the
 * widget is also redrawn as a new day starts ([ACTION_NEW_DAY], an alarm that never wakes the phone) and when the
 * clock or the time zone is changed. The daily update from the provider info only tidies up.
 */
class NextClassWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_NEW_DAY, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                watch(context)
                WidgetUpdates.run(context, null, force = true, pending = goAsync())
            }
            else -> super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        watch(context)
        WidgetUpdates.run(context, appWidgetIds, force = true, pending = goAsync())
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        WidgetUpdates.run(context, intArrayOf(appWidgetId), force = true, pending = goAsync())
    }

    /** The last widget is gone: nothing to redraw at midnight. */
    override fun onDisabled(context: Context) = WidgetUpdates.cancelNewDay(context)

    companion object {
        const val ACTION_OPEN = "com.justdataplease.streetgym.action.OPEN_NEXT_CLASS"
        const val ACTION_NEW_DAY = "com.justdataplease.streetgym.action.WIDGET_NEW_DAY"
        const val EXTRA_PROGRAM = "next_class_program"

        /** True for a tap on the widget (not a relaunch from Recents, which replays the old intent). */
        fun opened(intent: Intent?): Boolean =
            intent?.action == ACTION_OPEN && (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0

        /** The class a widget tap asks for; null for the app's home. */
        fun programToOpen(intent: Intent?): String? = intent?.takeIf(::opened)?.getStringExtra(EXTRA_PROGRAM)

        /** Keeps placed widgets in step while the app's process lives. Call from the main thread; once is enough. */
        fun watch(context: Context) = WidgetUpdates.watch(context)

        /** Redraws every placed widget now, whatever changed. */
        fun update(context: Context) = WidgetUpdates.run(context, null, force = true, pending = null)
    }
}

/**
 * The next class as Today works it out ([NextClass]), with the tier's lock and the first-run placement, for the widget.
 * Null when the curriculum can't be read.
 */
internal object NextClassState {
    fun card(context: Context): NextClassCard? {
        val catalog = HomeActivity.loadedCatalog ?: AssetCurriculumRepository(context).load().getOrNull() ?: return null
        val ordered = NextClass.ordered(catalog)
        val progress = SharedPreferencesProgressRepository(context)
        val completed = progress.completedProgramIds()
        val passed = AppSettings(context).let { NextClass.passed(ordered, completed, it.skillSwitches, it.equipment) }
        // The flip-book plays a step that moves: a hold's drawings would all be the same pose.
        val moves = { exercise: TrainingExercise ->
            MotionClip.moves(context, exercise.movementId, exercise.name.resolve(), exercise.category, exercise.equipment)
        }
        val next = NextClass.of(ordered, passed, progress.levelReplay()) ?: return NextClassCard.allDone(ordered.lastOrNull(), moves)
        val locked = LevelAccess.locked(next.levelNumber, Entitlement.tier(context))
        // A new athlete first answers the placement on Today, so the tap goes there instead of to Level 1, class 1.
        val placement = Placement.shouldAsk(AppSettings(context).placementDone, completed.size, progress.activeWorkout() != null)
        return NextClassCard.of(next, locked, opens = !placement, moves = moves)
    }

    /** The streak today, as Today's line shows it. */
    fun streak(context: Context): Streak = StreakDays.snapshot(SharedPreferencesProgressRepository(context).sessionHistory()).streak
}

/** Builds and pushes the widget off the main thread; preference changes come in bursts and are batched. */
private object WidgetUpdates {
    private const val TAG = "NextClassWidget"
    private const val SETTLE_MS = 600L
    /** The app's preference files (their names are fixed, or progress would be lost): progress, settings, the tier. */
    private val WATCHED = listOf("streetgym_progress", "streetgym_settings", Entitlement.PREFS)

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, TAG).apply { isDaemon = true } }
    private var app: Context? = null
    /** What the placed widgets show now; a change that leaves it the same (a set ticked mid-class) draws nothing. */
    @Volatile private var shown: String? = null
    private val settle = Runnable { app?.let { run(it, null, force = false, pending = null) } }
    // Held here for the process: SharedPreferences keeps its listeners weakly.
    private val changed = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        main.removeCallbacks(settle)
        main.postDelayed(settle, SETTLE_MS)
    }

    fun watch(context: Context) {
        if (app != null) return
        val appContext = context.applicationContext
        app = appContext
        WATCHED.forEach { appContext.getSharedPreferences(it, Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(changed) }
    }

    /** Main thread. [ids] null means every placed widget; [force] draws even when nothing they show has changed. */
    fun run(context: Context, ids: IntArray?, force: Boolean, pending: BroadcastReceiver.PendingResult?) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val every = manager.getAppWidgetIds(ComponentName(appContext, NextClassWidget::class.java))
        val targets = ids ?: every
        if (targets.isEmpty()) { pending?.finish(); return }
        if (every.isNotEmpty()) scheduleNewDay(appContext)
        // The theme's faces load here, on the main thread, as the app's screens load them.
        val theme = AppSettings(appContext).theme
        AppType.init(appContext)
        val display = AppType.display(theme)
        worker.execute {
            try {
                val card = NextClassState.card(appContext) ?: return@execute
                val streak = NextClassState.streak(appContext)
                val animate = appContext.systemAnimationsEnabled()
                val state = "${theme.key}|$animate|$card|$streak"
                if (!force && state == shown) return@execute
                val frames = WidgetRenderer.frames(appContext, card, theme, animate)
                for (id in targets) {
                    val options = manager.getAppWidgetOptions(id)
                    val built = WidgetRenderer.views(appContext, card, streak, theme, frames, widthDp(options),
                        options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) ?: 0, display)
                    manager.updateAppWidget(id, built.views)
                }
                if (targets.toSet() == every.toSet()) shown = state
            } catch (failure: RuntimeException) {
                Log.w(TAG, "Widget update failed", failure)
            } finally {
                pending?.finish()
            }
        }
    }

    /**
     * Asks for one redraw as the next local day starts, when a lit flame goes unlit and a missed day puts it out. Every
     * redraw asks again, so the chain goes on while a widget is placed. Not an exact alarm (no permission): any time
     * in the day's first minutes, and not while the phone sleeps, since nobody is looking at the home screen then.
     */
    private fun scheduleNewDay(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val midnight = StreakDays.nextMidnight(System.currentTimeMillis(), TimeZone.getDefault())
        runCatching { alarms.setWindow(AlarmManager.RTC, midnight, NEW_DAY_WINDOW_MS, newDay(context)) }
            .onFailure { Log.w(TAG, "No new-day redraw", it) }
    }

    fun cancelNewDay(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(newDay(context.applicationContext))
    }

    private fun newDay(context: Context): PendingIntent = PendingIntent.getBroadcast(context, REQUEST_NEW_DAY,
        Intent(context, NextClassWidget::class.java).setAction(NextClassWidget.ACTION_NEW_DAY),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** The shortest window Android 12+ allows. */
    private const val NEW_DAY_WINDOW_MS = 10 * 60 * 1000L
    private const val REQUEST_NEW_DAY = 731

    /**
     * The narrowest width the launcher shows this widget at (portrait on a phone), in dp; 0 when it hasn't said. The
     * height that goes with it is OPTION_APPWIDGET_MAX_HEIGHT, the portrait one.
     */
    private fun widthDp(options: Bundle?): Int {
        if (options == null) return 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            sizes?.minOfOrNull { it.width }?.takeIf { it > 0f }?.let { return it.toInt() }
        }
        return options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
    }
}
