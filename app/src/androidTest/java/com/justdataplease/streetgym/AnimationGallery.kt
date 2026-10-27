package com.justdataplease.streetgym

import android.app.Activity
import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import com.justdataplease.streetgym.ui.AppColors
import com.justdataplease.streetgym.ui.AppType
import com.justdataplease.streetgym.ui.ExerciseAnimationView
import com.justdataplease.streetgym.ui.StreetTheme
import com.justdataplease.streetgym.ui.blendColor
import org.json.JSONObject
import java.io.File
import kotlin.math.roundToInt

/**
 * A review aid, not part of the suite: draws every movement's guide (several moments, a second rep for movements that
 * alternate sides), its tall focus-screen guide and its badges into contact sheets, with the app's own renderer.
 *   am instrument -w -e theme night_park -e ids pullup,low_jack com.justdataplease.streetgym.debug.test/com.justdataplease.streetgym.AnimationGallery
 * Sheets land in the app's external files under gallery/<theme>/; "ids" defaults to every movement in the curriculum.
 */
class AnimationGallery : Instrumentation() {
    private var arguments: Bundle? = null

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        this.arguments = arguments
        start()
    }

    override fun onStart() {
        val result = Bundle()
        try {
            var written = emptyList<String>()
            runOnMainSync { written = render() }
            result.putString("stream", "\nGALLERY ${written.size} sheets\n${written.joinToString("\n")}\n")
            finish(Activity.RESULT_OK, result)
        } catch (failure: Throwable) {
            result.putString("stream", "\nGALLERY FAILED ${failure.stackTraceToString()}\n")
            finish(Activity.RESULT_CANCELED, result)
        }
    }

    private fun render(): List<String> {
        val context = targetContext
        val theme = StreetTheme.fromKey(arguments?.getString("theme")) ?: StreetTheme.DEFAULT
        AppColors.configure(theme)
        AppType.init(context)
        val library = JSONObject(context.assets.open("curriculum.json").bufferedReader().use { it.readText() })
            .getJSONArray("movement_library")
        val names = (0 until library.length()).associate { index ->
            library.getJSONObject(index).let { it.getString("id") to it.getJSONObject("name").getString("en") }
        }
        val ids = arguments?.getString("ids")?.split(',')?.map(String::trim)?.filter(String::isNotEmpty) ?: names.keys.toList()
        val density = context.resources.displayMetrics.density
        fun px(dp: Int) = (dp * density).roundToInt()
        val out = File(context.getExternalFilesDir(null), "gallery/${theme.key}").apply { deleteRecursively(); mkdirs() }

        val guideW = px(340)
        val guideH = px(168)
        val focusW = px(360)
        val focusH = px(330)
        val badge = px(64)
        val tile = px(52)
        val shrink = 0.42f
        val gap = px(6)
        val label = px(18)
        val phases = floatArrayOf(0f, 0.12f, 0.28f, 0.6f)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AppColors.TEXT; textSize = 13f * density * shrink * 1.6f }
        val rowW = ((phases.size + 1) * guideW + focusW * 0.5f) * shrink + badge + tile * 2 + gap * 10
        val rowH = (guideH * shrink).coerceAtLeast(focusH * 0.5f * shrink) + label + gap
        val written = ArrayList<String>()
        if (arguments?.getString("mode") == "focus") {
            // The focus screen's tall guide, side by side, to compare figure sizes across movements.
            val w = px(380)
            val h = px(330)
            val columns = 3
            val sheet = Bitmap.createBitmap(columns * (w / 2 + gap) + gap, ((ids.size + columns - 1) / columns) * (h / 2 + label + gap) + gap,
                Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheet)
            canvas.drawColor(AppColors.BACKGROUND)
            ids.forEachIndexed { index, id ->
                val x = gap + (index % columns) * (w / 2 + gap)
                val y = gap + (index / columns) * (h / 2 + label + gap)
                canvas.drawText(names[id] ?: id, x.toFloat(), y + label * 0.75f, text)
                val shot = snapshot(guide(id, names[id]), w, h) { it.hold(phases[2]) }
                canvas.drawBitmap(Bitmap.createScaledBitmap(shot, w / 2, h / 2, true), x.toFloat(), (y + label).toFloat(), null)
                shot.recycle()
            }
            val file = File(out, "focus.png")
            file.outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return listOf(file.absolutePath)
        }
        ids.chunked(10).forEachIndexed { sheetIndex, chunk ->
            val sheet = Bitmap.createBitmap(rowW.roundToInt(), (rowH * chunk.size + gap).roundToInt(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheet)
            canvas.drawColor(AppColors.BACKGROUND)
            chunk.forEachIndexed { row, id ->
                val top = gap + row * rowH
                canvas.drawText("${names[id] ?: id}  [$id]", gap.toFloat(), top + label * 0.75f, text)
                var x = gap.toFloat()
                val y = top + label
                fun place(bitmap: Bitmap, scale: Float) {
                    val w = (bitmap.width * scale).roundToInt()
                    val h = (bitmap.height * scale).roundToInt()
                    canvas.drawBitmap(Bitmap.createScaledBitmap(bitmap, w, h, true), x, y, null)
                    x += w + gap
                    bitmap.recycle()
                }
                // Guide moments, then the next rep (a mirrored side where the movement alternates).
                for (phase in phases) place(snapshot(guide(id, names[id]), guideW, guideH) { it.hold(phase) }, shrink)
                place(snapshot(guide(id, names[id]), guideW, guideH) { it.hold(phases[2], cycle = 1) }, shrink)
                place(snapshot(guide(id, names[id]), focusW, focusH) { it.hold(phases[2]) }, shrink * 0.5f)
                // A skill badge (still), and a picker tile (moving) at two moments.
                place(snapshot(badge(id, names[id], moving = false), badge, badge) {}, 1f)
                place(snapshot(badge(id, names[id], moving = true), tile, tile) { it.hold(phases[0]) }, 1f)
                place(snapshot(badge(id, names[id], moving = true), tile, tile) { it.hold(phases[2]) }, 1f)
            }
            val file = File(out, "sheet_%02d.png".format(sheetIndex + 1))
            file.outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
            sheet.recycle()
            written += file.absolutePath
        }
        return written
    }

    private fun guide(id: String, name: String?) = ExerciseAnimationView(targetContext).bind(id, name ?: id)

    private fun badge(id: String, name: String?, moving: Boolean) = guide(id, name).apply {
        setBadgeStyle(ExerciseAnimationView.BadgeStyle(
            AppColors.INFO_SURFACE, AppColors.PRIMARY, blendColor(AppColors.PRIMARY, AppColors.INFO_SURFACE, 0.5f),
            blendColor(AppColors.TEXT_SECONDARY, AppColors.INFO_SURFACE, 0.4f)), moving)
    }

    private fun snapshot(view: ExerciseAnimationView, width: Int, height: Int, setUp: (ExerciseAnimationView) -> Unit): Bitmap {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        setUp(view)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            Canvas(it).apply { drawColor(Color.TRANSPARENT); view.draw(this) }
        }
    }
}
