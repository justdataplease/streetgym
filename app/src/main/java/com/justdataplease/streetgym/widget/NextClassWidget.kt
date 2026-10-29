package com.justdataplease.streetgym.widget

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
import com.justdataplease.streetgym.data.progress.NextClass
import com.justdataplease.streetgym.data.progress.Placement
import com.justdataplease.streetgym.data.progress.SharedPreferencesProgressRepository
import com.justdataplease.streetgym.ui.AppSettings
import com.justdataplease.streetgym.ui.AppType
import com.justdataplease.streetgym.ui.HomeActivity
import com.justdataplease.streetgym.ui.systemAnimationsEnabled
import java.util.concurrent.Executors

/**
 * The "Next class" home-screen widget: the next class in the sequence (never a day or a date), its lead movement
 * playing, and a tap that opens the class. The app keeps it in step through [watch]: finishing or skipping a class,
 * a reset, a level replay, a purchase or a new theme redraws it. The daily update from the provider info only tidies up.
 */
class NextClassWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        watch(context)
        WidgetUpdates.run(context, appWidgetIds, force = true, pending = goAsync())
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        WidgetUpdates.run(context, intArrayOf(appWidgetId), force = true, pending = goAsync())
    }

    companion object {
        const val ACTION_OPEN = "com.justdataplease.streetgym.action.OPEN_NEXT_CLASS"
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
        val next = NextClass.of(ordered, completed, progress.levelReplay()) ?: return NextClassCard.allDone(ordered.lastOrNull())
        val locked = LevelAccess.locked(next.levelNumber, Entitlement.tier(context))
        // A new athlete first answers the placement on Today, so the tap goes there instead of to Level 1, class 1.
        val placement = Placement.shouldAsk(AppSettings(context).placementDone, completed.size, progress.activeWorkout() != null)
        return NextClassCard.of(next, locked, opens = !placement)
    }
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
        // The theme's faces load here, on the main thread, as the app's screens load them.
        val theme = AppSettings(appContext).theme
        AppType.init(appContext)
        val display = AppType.display(theme)
        worker.execute {
            try {
                val card = NextClassState.card(appContext) ?: return@execute
                val animate = appContext.systemAnimationsEnabled()
                val state = "${theme.key}|$animate|$card"
                if (!force && state == shown) return@execute
                val frames = WidgetRenderer.frames(appContext, card, theme, animate)
                for (id in targets) {
                    val options = manager.getAppWidgetOptions(id)
                    val built = WidgetRenderer.views(appContext, card, theme, frames, widthDp(options),
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
