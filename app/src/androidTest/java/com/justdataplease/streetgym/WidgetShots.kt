package com.justdataplease.streetgym

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.Instrumentation
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.ViewFlipper
import com.justdataplease.streetgym.data.AssetCurriculumRepository
import com.justdataplease.streetgym.data.progress.NextClass
import com.justdataplease.streetgym.data.progress.SharedPreferencesProgressRepository
import com.justdataplease.streetgym.ui.AppSettings
import com.justdataplease.streetgym.ui.AppType
import com.justdataplease.streetgym.ui.StreetTheme
import com.justdataplease.streetgym.widget.NextClassCard
import com.justdataplease.streetgym.widget.NextClassWidget
import com.justdataplease.streetgym.widget.WidgetRenderer
import java.io.File
import kotlin.math.roundToInt

/**
 * A review aid, not part of the suite: shows the "Next class" widget and saves what it looks like.
 *   adb shell appwidget grantbind --package com.justdataplease.streetgym.debug --user 0
 *   am instrument -w com.justdataplease.streetgym.debug.test/com.justdataplease.streetgym.WidgetShots
 * First the card for Level 4, class 20 (the design's example) is drawn straight from its RemoteViews in every theme.
 * Then the widget is bound to a real AppWidgetHost, updated by the provider through AppWidgetManager, and followed as
 * progress and the theme change (the app's own watcher redraws it), and finally shown in a window to catch it flipping.
 * PNGs land in the app's external files under widget/. Restores the preferences it touches.
 */
class WidgetShots : Instrumentation() {
    private val log = StringBuilder()
    private val app: Context get() = targetContext
    private val widthDp = 380
    private val heightDp = 116
    private fun px(dp: Int) = (dp * app.resources.displayMetrics.density).roundToInt()

    private var arguments: Bundle? = null

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        this.arguments = arguments
        start()
    }

    override fun onStart() {
        val result = Bundle()
        val kept = PREFS.associateWith { app.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }
        val failure = runCatching {
            val out = File(app.getExternalFilesDir(null), "widget").apply { deleteRecursively(); mkdirs() }
            direct(out)
            hosted(out)
            if (arguments?.getString("launcher") != "false") launcher(out)
        }.exceptionOrNull()
        // Before finish(), which ends the process.
        kept.forEach { (name, values) -> restore(name, values) }
        if (failure == null) {
            result.putString("stream", "\nWIDGET OK\n$log")
            finish(Activity.RESULT_OK, result)
        } else {
            result.putString("stream", "\nWIDGET FAILED ${failure.stackTraceToString()}\n$log")
            finish(Activity.RESULT_CANCELED, result)
        }
    }

    /** The design's example in each theme, two of its drawings, the compact card, and the picker's preview image. */
    private fun direct(out: File) {
        val catalog = AssetCurriculumRepository(app).load().getOrThrow()
        val sample = NextClass.ordered(catalog).first { it.levelNumber == 4 && it.number == 20 }
        val card = NextClassCard.of(sample, locked = false)
        log.append("sample: $card\n")
        for (theme in StreetTheme.entries) runOnMainSync {
            AppType.init(app)
            val frames = WidgetRenderer.frames(app, card, theme, animate = true)
            val built = WidgetRenderer.views(app, card, theme, frames, widthDp, heightDp, AppType.display(theme), WidgetRenderer.Layout.FULL)
            log.append("${theme.key}: ${frames.bitmaps.size} drawings of ${frames.bitmaps[0].width}x${frames.bitmaps[0].height} " +
                "${frames.bitmaps[0].config}, flip every ${frames.intervalMs} ms; drawings ${frames.bytes} B, " +
                "whole card ${built.bitmapBytes} B\n")
            val root = applied(built)
            val flipper = root.findViewById<ViewFlipper>(R.id.widget_figure)
            save(root, px(widthDp), px(heightDp), WALL, File(out, "card_${theme.key}.png"))
            for (frame in listOf(0, 8)) {
                flipper.displayedChild = frame
                save(root, px(widthDp), px(heightDp), WALL, File(out, "card_${theme.key}_frame%02d.png".format(frame)))
                val drawing = frames.bitmaps[frame]
                File(out, "tile_${theme.key}_frame%02d.png".format(frame)).outputStream().use {
                    Bitmap.createScaledBitmap(drawing, drawing.width * 2, drawing.height * 2, false).compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            if (theme == StreetTheme.CONCRETE) {
                flipper.displayedChild = 4
                save(root, px(widthDp), px(100), Color.TRANSPARENT, File(out, "widget_preview.png"))
            }
            // A landscape home screen's 62 dp cell: the compact card.
            val compact = applied(WidgetRenderer.views(app, card, theme, frames, 624, 62, AppType.display(theme),
                WidgetRenderer.Layout.COMPACT))
            save(compact, px(624), px(62), WALL, File(out, "compact_${theme.key}.png"))
        }
        // The other kinds of card: an exam, a level beyond the tier, the end of the sequence, the longest line in a
        // three-cell widget.
        val ordered = NextClass.ordered(catalog)
        val exam = ordered.first { it.levelNumber == 4 && it.isExam }
        val others = listOf(
            Triple("exam", NextClassCard.of(exam, locked = false), widthDp),
            Triple("locked", NextClassCard.of(ordered.first { it.levelNumber == 6 }, locked = true), widthDp),
            Triple("all_done", NextClassCard.allDone(ordered.last()), widthDp),
            Triple("narrow", NextClassCard.of(ordered.last(), locked = false), 280),
        )
        for ((name, other, width) in others) runOnMainSync {
            val theme = if (name == "locked") StreetTheme.NIGHT_PARK else StreetTheme.CONCRETE
            val frames = WidgetRenderer.frames(app, other, theme, animate = true)
            val built = WidgetRenderer.views(app, other, theme, frames, width, heightDp, AppType.display(theme),
                WidgetRenderer.Layout.FULL)
            val root = applied(built)
            save(root, px(width), px(heightDp), WALL, File(out, "other_${name}_${theme.key}.png"))
            log.append("$name: $other\n")
        }
    }

    /** The RemoteViews applied as a host applies them, flip-book held on drawing 4. */
    private fun applied(built: WidgetRenderer.Built): FrameLayout {
        val root = FrameLayout(app)
        root.addView(built.views.apply(app, root))
        root.findViewById<ViewFlipper>(R.id.widget_figure).apply { stopFlipping(); displayedChild = 4 }
        return root
    }

    /** The widget in a real AppWidgetHost, kept up to date by the app as progress and the theme change. */
    private fun hosted(out: File) {
        val manager = AppWidgetManager.getInstance(app)
        val host = AppWidgetHost(app, HOST_ID)
        var id = 0
        runOnMainSync { host.startListening(); id = host.allocateAppWidgetId() }
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
        }
        if (!manager.bindAppWidgetIdIfAllowed(id, ComponentName(app, NextClassWidget::class.java), options)) {
            log.append("HOST bind refused: run adb shell appwidget grantbind --package ${app.packageName} --user 0\n")
            host.deleteAppWidgetId(id)
            return
        }
        try {
            lateinit var hostView: AppWidgetHostView
            val settings = AppSettings(app)
            val progress = SharedPreferencesProgressRepository(app)
            runOnMainSync {
                progress.reset(System.currentTimeMillis())
                settings.theme = StreetTheme.CONCRETE
                hostView = host.createView(app, id, manager.getAppWidgetInfo(id))
                NextClassWidget.watch(app)
                NextClassWidget.update(app)
            }
            await("a new athlete's first class", hostView) { meta(it) == "LEVEL 1 · CLASS 1" }
            snap(hostView, File(out, "host_concrete_start.png"))

            // Skipping every class before Level 4, class 20: the watcher redraws the widget by itself.
            val catalog = AssetCurriculumRepository(app).load().getOrThrow()
            val ordered = NextClass.ordered(catalog)
            val before = ordered.takeWhile { !(it.levelNumber == 4 && it.number == 20) }.map { it.id }
            runOnMainSync { progress.completePrograms(before) }
            await("Level 4, class 20 after the skips", hostView) { meta(it) == "LEVEL 4 · CLASS 20" }
            snap(hostView, File(out, "host_concrete_level4.png"))

            // One more class done: the next one shows.
            runOnMainSync { progress.completePrograms(listOf(ordered[before.size].id)) }
            await("class 21 once class 20 is done", hostView) { meta(it) == "LEVEL 4 · CLASS 21" }
            snap(hostView, File(out, "host_concrete_level4_next.png"))

            // A new theme: the watcher redraws in Night Park's colours.
            val night = StreetTheme.NIGHT_PARK.palette.surface
            runOnMainSync { settings.theme = StreetTheme.NIGHT_PARK }
            await("Night Park", hostView) { surface(it) == night }
            snap(hostView, File(out, "host_night_park_level4_next.png"))
            runOnMainSync { progress.markProgramIncomplete(ordered[before.size].id) }
            await("back to class 20 in Night Park", hostView) { meta(it) == "LEVEL 4 · CLASS 20" && surface(it) == night }
            snap(hostView, File(out, "host_night_park_level4.png"))

            playing(hostView, out)
        } finally {
            runOnMainSync { host.deleteAppWidgetId(id); host.stopListening() }
        }
    }

    /** Puts the hosted widget in a window, where its ViewFlipper runs on its own, and grabs the screen twice. */
    private fun playing(hostView: AppWidgetHostView, out: File) {
        val intent = Intent(Intent.ACTION_MAIN).setClassName(app, MainActivity::class.java.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = startActivitySync(intent)
        waitForIdleSync()
        val stage = FrameLayout(app).apply { setBackgroundColor(WALL) }
        runOnMainSync {
            (hostView.parent as? ViewGroup)?.removeView(hostView)
            stage.addView(hostView, FrameLayout.LayoutParams(px(widthDp), px(heightDp)).apply { topMargin = px(160); leftMargin = px(16) })
            (activity.window.decorView as ViewGroup).addView(stage, ViewGroup.LayoutParams(-1, -1))
        }
        SystemClock.sleep(1500)
        val seen = ArrayList<Int>()
        for (shot in 0 until 2) {
            var child = -1
            var flipping = false
            runOnMainSync {
                val flipper = hostView.findViewById<ViewFlipper>(R.id.widget_figure)
                child = flipper.displayedChild
                flipping = flipper.isFlipping
            }
            seen += child
            val screen = uiAutomation.takeScreenshot()
            val location = IntArray(2)
            runOnMainSync { hostView.getLocationOnScreen(location) }
            val crop = Bitmap.createBitmap(screen, location[0], location[1], px(widthDp), px(heightDp))
            File(out, "playing_night_park_%d.png".format(shot)).outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
            log.append("playing shot $shot: drawing $child of the flip-book, flipping=$flipping\n")
            SystemClock.sleep(330)
        }
        log.append("PLAYING ${if (seen.distinct().size > 1) "moves" else "STILL"}\n")
        runOnMainSync { (stage.parent as? ViewGroup)?.removeView(stage); activity.finish() }
    }

    /**
     * Pins the widget on the emulator's own launcher, as Android lets an app in the foreground ask (the launcher's
     * "Add to home screen" is pressed through accessibility, not by hand), then grabs the home screen twice.
     */
    private fun launcher(out: File) {
        val manager = AppWidgetManager.getInstance(app)
        val provider = ComponentName(app, NextClassWidget::class.java)
        if (!manager.isRequestPinAppWidgetSupported) { log.append("LAUNCHER can't pin widgets\n"); return }
        val ordered = NextClass.ordered(AssetCurriculumRepository(app).load().getOrThrow())
        runOnMainSync {
            AppSettings(app).theme = StreetTheme.CONCRETE
            SharedPreferencesProgressRepository(app).completePrograms(ordered.takeWhile { !(it.levelNumber == 4 && it.number == 20) }.map { it.id })
        }
        val activity = startActivitySync(Intent(Intent.ACTION_MAIN).setClassName(app, MainActivity::class.java.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitForIdleSync()
        var asked = false
        runOnMainSync { asked = manager.requestPinAppWidget(provider, null, null) }
        var clicked = false
        val until = SystemClock.uptimeMillis() + 15_000
        while (asked && !clicked && SystemClock.uptimeMillis() < until) {
            val root = uiAutomation.rootInActiveWindow
            val button = root?.let { window ->
                listOf("Add to home screen", "Add").firstNotNullOfOrNull { label ->
                    window.findAccessibilityNodeInfosByText(label).firstOrNull { it.text?.toString().equals(label, ignoreCase = true) }
                }
            }
            clicked = button?.let { node -> generateSequence(node) { it.parent }.firstOrNull { it.isClickable } }
                ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            if (!clicked) SystemClock.sleep(300)
        }
        log.append("LAUNCHER asked=$asked added=$clicked\n")
        uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        SystemClock.sleep(5000)
        for (id in manager.getAppWidgetIds(provider)) {
            val options = manager.getAppWidgetOptions(id)
            log.append("LAUNCHER widget $id: width ${options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)}–" +
                "${options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)} dp, height " +
                "${options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)}–${options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)} dp\n")
        }
        for (shot in 0 until 2) {
            val screen = uiAutomation.takeScreenshot()
            File(out, "launcher_concrete_%d.png".format(shot)).outputStream().use { screen.compress(Bitmap.CompressFormat.PNG, 100, it) }
            SystemClock.sleep(330)
        }
        // A tap on the placed widget, as a finger would (input tap on its meta line): first with the app still open
        // behind the launcher, then with it closed. Both should land on the class's overview.
        tap(out, "launcher_tapped_open.png")
        runOnMainSync { activity.finish() }
        SystemClock.sleep(1500)
        uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        SystemClock.sleep(2000)
        tap(out, "launcher_tapped_closed.png")
        uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    /**
     * Taps the placed widget as a finger would (input tap). The Pixel launcher keeps widget contents out of the
     * accessibility tree, so the card is found by its node when there is one, else on the screen by its surface colour.
     */
    private fun tap(out: File, name: String) {
        uiAutomation.serviceInfo = uiAutomation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        fun walk(node: AccessibilityNodeInfo): Sequence<AccessibilityNodeInfo> =
            sequenceOf(node) + (0 until node.childCount).asSequence().mapNotNull(node::getChild).flatMap(::walk)
        val display = app.resources.displayMetrics
        val screen = Rect(0, 0, display.widthPixels, display.heightPixels)
        val bounds = Rect()
        // The launcher may still be sliding home: look again until the card sits on the screen.
        for (attempt in 0 until 10) {
            val roots = uiAutomation.windows.mapNotNull { it.root } + listOfNotNull(uiAutomation.rootInActiveWindow)
            val node = roots.asSequence().flatMap(::walk).firstOrNull {
                "${it.text} ${it.contentDescription}".let { words -> "CLASS 20" in words.uppercase() || "Next class" in words }
            }
            bounds.setEmpty()
            if (node != null) node.getBoundsInScreen(bounds) else cardOnScreen(bounds)
            log.append("LAUNCHER windows ${roots.map { it.packageName }.distinct()}; widget by " +
                "${if (node != null) "node" else "colour"} at $bounds\n")
            if (!bounds.isEmpty && screen.contains(bounds)) break
            // On another home-screen page: swipe to it.
            val y = screen.height() / 2
            if (bounds.left >= screen.right) uiAutomation.executeShellCommand("input swipe ${screen.width() * 9 / 10} $y ${screen.width() / 10} $y 300").close()
            if (bounds.right <= 0 && bounds.left < 0) uiAutomation.executeShellCommand("input swipe ${screen.width() / 10} $y ${screen.width() * 9 / 10} $y 300").close()
            SystemClock.sleep(900)
        }
        if (bounds.isEmpty || !screen.contains(bounds)) return
        uiAutomation.executeShellCommand("input tap ${bounds.centerX()} ${bounds.centerY()}").close()
        SystemClock.sleep(4000)
        waitForIdleSync()
        uiAutomation.takeScreenshot()?.let { screen ->
            File(out, name).outputStream().use { screen.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        log.append("LAUNCHER tapped -> $name\n")
    }

    /** The bounds of Concrete's card surface on the screen (empty when it isn't there). */
    private fun cardOnScreen(bounds: Rect) {
        val screen = uiAutomation.takeScreenshot()?.copy(Bitmap.Config.ARGB_8888, false) ?: return
        val surface = StreetTheme.CONCRETE.palette.surface or 0xFF000000.toInt()
        var left = Int.MAX_VALUE; var top = Int.MAX_VALUE; var right = -1; var bottom = -1
        val row = IntArray(screen.width)
        for (y in 0 until screen.height step 2) {
            screen.getPixels(row, 0, screen.width, 0, y, screen.width, 1)
            for (x in row.indices step 2) if (row[x] == surface) {
                left = minOf(left, x); right = maxOf(right, x); top = minOf(top, y); bottom = maxOf(bottom, y)
            }
        }
        if (right > left && bottom > top) bounds.set(left, top, right, bottom)
    }

    private fun await(what: String, view: View, test: (View) -> Boolean) {
        val until = SystemClock.uptimeMillis() + 30_000
        var ok = false
        while (!ok && SystemClock.uptimeMillis() < until) {
            runOnMainSync { ok = runCatching { test(view) }.getOrDefault(false) }
            if (!ok) SystemClock.sleep(200)
        }
        var shown = ""
        runOnMainSync { shown = meta(view).orEmpty() }
        log.append("HOST $what: ${if (ok) "shown" else "NOT SHOWN (meta \"$shown\")"}\n")
        check(ok) { "The hosted widget never showed $what" }
    }

    private fun meta(view: View): String? = view.findViewById<TextView>(R.id.widget_meta)?.text?.toString()

    private fun surface(view: View): Int? = (view.findViewById<View>(R.id.widget_card)?.background as? ColorDrawable)?.color

    private fun snap(view: View, file: File) {
        runOnMainSync {
            val stopped = view.findViewById<ViewFlipper>(R.id.widget_figure)
            stopped?.stopFlipping()
            save(view, px(widthDp), px(heightDp), WALL, file)
        }
    }

    private fun save(view: View, width: Int, height: Int, background: Int, file: File) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply { drawColor(background); view.draw(this) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        log.append("saved ${file.name}\n")
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
        const val HOST_ID = 7301
        /** A dark home-screen wallpaper, as in the design's mock-up. */
        val WALL = Color.rgb(0x2B, 0x2E, 0x31)
        val PREFS = listOf("streetgym_progress", "streetgym_settings")
    }
}
