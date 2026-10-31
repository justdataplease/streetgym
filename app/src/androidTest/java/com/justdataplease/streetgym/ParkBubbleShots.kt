package com.justdataplease.streetgym

import android.app.Activity
import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import com.justdataplease.streetgym.ui.AppColors
import com.justdataplease.streetgym.ui.AppSettings
import com.justdataplease.streetgym.ui.AppType
import com.justdataplease.streetgym.ui.ParkSceneView
import com.justdataplease.streetgym.ui.StreetTheme
import java.io.File
import kotlin.math.roundToInt

/**
 * A review aid, not part of the suite: the Today hero's park greeting an athlete by name, in each [ParkSceneView.BubbleStyle].
 *   am instrument -w com.justdataplease.streetgym.debug.test/com.justdataplease.streetgym.ParkBubbleShots
 * For each theme and style: four moments of the greeting coming in, and one while it is read; then the longest name
 * and two more days' routines (other final skills) in Concrete. PNGs land in the app's external files under park/.
 */
class ParkBubbleShots : Instrumentation() {
    private val log = StringBuilder()

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = Bundle()
        val kept = AppColors.theme
        val failure = runCatching { shots() }.exceptionOrNull()
        AppColors.configure(kept)
        if (failure == null) {
            result.putString("stream", "\nPARK OK\n$log")
            finish(Activity.RESULT_OK, result)
        } else {
            result.putString("stream", "\nPARK FAILED ${failure.stackTraceToString()}\n$log")
            finish(Activity.RESULT_CANCELED, result)
        }
    }

    private fun shots() {
        val app = targetContext
        AppType.init(app)
        val out = File(app.getExternalFilesDir(null), "park").apply { deleteRecursively(); mkdirs() }
        val density = app.resources.displayMetrics.density
        // The hero card's park on a phone: the card's width inside the page, 180 dp tall.
        val width = (340 * density).roundToInt()
        val height = (180 * density).roundToInt()
        // Seconds after the greeting starts: popping in, nearly in, in, and settled while it's read.
        val moments = listOf(0.08f, 0.2f, 0.4f, 1.4f)
        for (theme in StreetTheme.entries) for (style in ParkSceneView.BubbleStyle.entries) runOnMainSync {
            AppColors.configure(theme)
            val view = scene(app, "Jason", style, width, height)
            val start = view.finaleAt - 0.35f
            moments.forEachIndexed { index, since ->
                view.showAt(start + since)
                save(view, width, height, File(out, "${theme.key}_${style.name.lowercase()}_$index.png"))
            }
            log.append("${theme.key} ${style.name}: finale at ${view.finaleAt}s, loop ${view.loopSeconds}s; ${view.contentDescription}\n")
        }
        runOnMainSync {
            AppColors.configure(StreetTheme.CONCRETE)
            for (style in ParkSceneView.BubbleStyle.entries) {
                val long = scene(app, "Alexandros_Papadopoulos", style, width, height)
                long.showAt(long.finaleAt + 1.2f)
                save(long, width, height, File(out, "long_${style.name.lowercase()}.png"))
                // Two more days: other final skills, the greeting placed around them.
                val other = scene(app, "Jason", style, width, height)
                for (day in 1..2) {
                    other.performLongClick()
                    other.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    other.layout(0, 0, width, height)
                    other.showAt(other.finaleAt + 1.2f)
                    save(other, width, height, File(out, "day${day}_${style.name.lowercase()}.png"))
                    log.append("day+$day: ${other.contentDescription}\n")
                }
            }
            AppColors.configure(AppSettings(app).theme)
        }
    }

    private fun scene(app: android.content.Context, name: String, style: ParkSceneView.BubbleStyle, width: Int, height: Int) =
        ParkSceneView(app).apply {
            username = name
            bubbleStyle = style
            measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            layout(0, 0, width, height)
        }

    /** The scene on the hero card's colour, as Today shows it. */
    private fun save(view: View, width: Int, height: Int, file: File) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply { drawColor(AppColors.PRIMARY_DARK); view.draw(this) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        log.append("saved ${file.name}\n")
    }
}
