package com.justdataplease.streetgym.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.text.TextPaint
import android.text.TextUtils
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.justdataplease.streetgym.MainActivity
import com.justdataplease.streetgym.R
import com.justdataplease.streetgym.ui.AppColors
import com.justdataplease.streetgym.ui.ExerciseAnimationView
import com.justdataplease.streetgym.ui.StreetTheme
import com.justdataplease.streetgym.ui.blendColor
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws the "Next class" widget (design A, the compact card) into [RemoteViews]. A widget can't run the movement
 * guides' animator, so the tile is a flip-book: [WidgetFrames.COUNT] drawings of the lead movement made with the app's
 * own [ExerciseAnimationView] (the exercise picker's moving badge, held at even steps through one loop), played by a
 * ViewFlipper. The tape and the headline are drawn in the theme's display face, which RemoteViews can't set; the two
 * small lines stay text in the system sans.
 */
internal object WidgetRenderer {
    /** One flip-book, shared by every placed widget. [bytes] is what its drawings hold. */
    class Frames(val bitmaps: List<Bitmap>, val intervalMs: Int) {
        val bytes: Int get() = bitmaps.sumOf { it.allocationByteCount }
    }

    /** The RemoteViews and the bytes of every drawing they carry (the host's bitmap limit counts these). */
    class Built(val views: RemoteViews, val bitmapBytes: Int)

    /** Which card: the launcher's pick by size ([AUTO]), or one of them (review tools). */
    enum class Layout { AUTO, FULL, COMPACT }

    /**
     * The lead movement's drawings at the tile's size, on the tile colour (opaque, so RGB 565 at half the memory).
     * [animate] false (system animations off) gives the clip's single still pose. Configures [AppColors] for [theme].
     */
    fun frames(context: Context, card: NextClassCard, theme: StreetTheme, animate: Boolean): Frames {
        AppColors.configure(theme)
        val density = context.resources.displayMetrics.density
        val side = (WidgetLayout.FIGURE_DP * min(density, WidgetLayout.MAX_FRAME_SCALE)).roundToInt()
        val surface = AppColors.INFO_SURFACE
        val view = ExerciseAnimationView(context).bind(card.movementId, card.movementName.ifBlank { card.role })
        // The exercise picker's moving tile: the figure in the theme's main colour on the info surface, framed on its
        // whole loop so it doesn't zoom from drawing to drawing.
        view.setBadgeStyle(ExerciseAnimationView.BadgeStyle(
            surface, AppColors.PRIMARY, blendColor(AppColors.PRIMARY, surface, 0.5f),
            blendColor(AppColors.TEXT_SECONDARY, surface, 0.4f)), moving = true)
        view.measure(View.MeasureSpec.makeMeasureSpec(side, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(side, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, side, side)
        val count = if (animate) WidgetFrames.COUNT else 1
        val start = view.loopStartPhase
        val bitmaps = List(count) { index ->
            if (animate) view.hold(WidgetFrames.phase(index, start))
            Bitmap.createBitmap(side, side, Bitmap.Config.RGB_565).also { bitmap ->
                Canvas(bitmap).apply { drawColor(surface); view.draw(this) }
            }
        }
        return Frames(bitmaps, WidgetFrames.intervalMs(view.loopDurationMs, count))
    }

    /**
     * The whole card for one placed widget [widthDp] wide and [heightDp] tall on a portrait home screen (0 when the
     * launcher hasn't said). From Android 12 the launcher picks the 96 dp card or the compact one for each orientation;
     * before that the portrait height decides. Both layouts share the same drawings, which the host holds once.
     */
    fun views(context: Context, card: NextClassCard, theme: StreetTheme, frames: Frames, widthDp: Int, heightDp: Int,
              display: Typeface, layout: Layout = Layout.AUTO): Built {
        val metrics = context.resources.displayMetrics
        val density = metrics.density
        val text = WidgetText.of(context, card)
        val tape = tape(text.tape, theme, display, density)
        val column = WidgetLayout.titleWidthDp(widthDp) * density
        val title = title(text.headline, theme, display, density, column)
        // "LEVEL 10 · CLASS 100" keeps its numbers: the tape steps aside first, then the line gets smaller.
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = WidgetLayout.META_SPACING
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, WidgetLayout.META_SP, metrics)
        }
        val fit = WidgetLayout.fitMeta(metaPaint.measureText(text.meta), column, tape.width + WidgetLayout.TAPE_GAP_DP * density)

        fun fill(views: RemoteViews, compact: Boolean): RemoteViews = views.apply {
            val palette = theme.palette
            val shadow = if (theme == StreetTheme.WHEATPASTE) palette.spot else palette.border
            setInt(R.id.widget_shadow, "setBackgroundColor", shadow)
            setInt(R.id.widget_outline, "setBackgroundColor", palette.border)
            setInt(R.id.widget_card, "setBackgroundColor", palette.surface)
            setInt(R.id.widget_tile, "setBackgroundColor", palette.border)
            setInt(R.id.widget_figure, "setBackgroundColor", palette.infoSurface)
            setInt(R.id.widget_action_outline, "setBackgroundColor", palette.border)
            setInt(R.id.widget_action, "setBackgroundColor", palette.action)
            setImageViewResource(R.id.widget_action, if (card.locked) R.drawable.ic_lock else R.drawable.widget_play)
            setInt(R.id.widget_action, "setColorFilter", AppColors.contentOn(palette.action))

            removeAllViews(R.id.widget_figure)
            for (bitmap in frames.bitmaps) {
                addView(R.id.widget_figure, RemoteViews(context.packageName, R.layout.widget_frame).apply {
                    setImageViewBitmap(R.id.widget_frame, bitmap)
                })
            }
            setInt(R.id.widget_figure, "setFlipInterval", frames.intervalMs)

            setImageViewBitmap(R.id.widget_tape, tape)
            setViewVisibility(R.id.widget_tape, if (fit.tape) View.VISIBLE else View.GONE)
            setImageViewBitmap(R.id.widget_title, title)
            setTextViewText(R.id.widget_meta, text.meta)
            setViewVisibility(R.id.widget_meta, if (text.meta.isEmpty()) View.GONE else View.VISIBLE)
            if (fit.scale < 1f) setTextViewTextSize(R.id.widget_meta, TypedValue.COMPLEX_UNIT_SP, WidgetLayout.META_SP * fit.scale)
            setTextColor(R.id.widget_meta, palette.textSecondary)
            if (!compact) {
                setTextViewText(R.id.widget_detail, text.detail)
                setViewVisibility(R.id.widget_detail, if (text.detail.isEmpty()) View.GONE else View.VISIBLE)
                setTextColor(R.id.widget_detail, palette.textSecondary)
            }
            setContentDescription(android.R.id.background, text.spoken)
            setOnClickPendingIntent(android.R.id.background, open(context, card))
        }

        val full = { fill(RemoteViews(context.packageName, R.layout.widget_next_class), compact = false) }
        val compact = { fill(RemoteViews(context.packageName, R.layout.widget_next_class_compact), compact = true) }
        val views = when {
            layout == Layout.FULL -> full()
            layout == Layout.COMPACT -> compact()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> RemoteViews(mapOf(
                SizeF(WidgetLayout.MIN_WIDTH_DP, WidgetLayout.CARD_HEIGHT_DP) to full(),
                SizeF(WidgetLayout.MIN_WIDTH_DP, WidgetLayout.MIN_HEIGHT_DP) to compact(),
            ))
            WidgetLayout.compact(heightDp) -> compact()
            else -> full()
        }
        return Built(views, frames.bytes + tape.allocationByteCount + title.allocationByteCount)
    }

    /** The tap: straight to the class (Today's card opens the same overview), or the app's home. */
    private fun open(context: Context, card: NextClassCard): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).setAction(NextClassWidget.ACTION_OPEN)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        card.programId?.let { intent.putExtra(NextClassWidget.EXTRA_PROGRAM, it) }
        return PendingIntent.getActivity(context, REQUEST_OPEN, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** The "NEXT" tape: the display face, letter-spaced, on the theme's accent. */
    private fun tape(label: String, theme: StreetTheme, face: Typeface, density: Float): Bitmap {
        val text = label.uppercase(Locale.ENGLISH)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face
            textSize = WidgetLayout.TAPE_TEXT_DP * density
            letterSpacing = theme.shape.labelSpacing
            color = AppColors.contentOn(theme.palette.accent)
        }
        val caps = capHeight(paint)
        val padX = WidgetLayout.TAPE_PAD_X_DP * density
        val width = ceil(paint.measureText(text) + padX * 2).toInt().coerceAtLeast(1)
        val height = ceil(WidgetLayout.TAPE_HEIGHT_DP * density).toInt()
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).apply {
                drawColor(theme.palette.accent)
                drawText(text, padX, (height + caps) / 2f, paint)
            }
        }
    }

    /** The class's role as the headline, in the theme's display face and case, cut with an ellipsis to [maxWidthPx]. */
    private fun title(label: String, theme: StreetTheme, face: Typeface, density: Float, maxWidthPx: Float): Bitmap {
        val upper = theme.shape.upperTitles
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face
            textSize = WidgetLayout.TITLE_DP * theme.shape.titleScale / WidgetLayout.TITLE_SCALE_BASE * density
            color = theme.palette.text
        }
        val shown = if (upper) label.uppercase(Locale.ENGLISH) else label
        val fitted = TextUtils.ellipsize(shown, paint, maxWidthPx, TextUtils.TruncateAt.END).toString().ifEmpty { shown }
        val bounds = Rect().also { paint.getTextBounds(fitted, 0, fitted.length, it) }
        val pad = paint.textSize * 0.1f
        // Capitals sit in a tight band; mixed case keeps room for descenders, so the line doesn't jump between classes.
        val top = min(bounds.top.toFloat(), -capHeight(paint)) - pad
        val bottom = (if (upper) max(bounds.bottom.toFloat(), 0f) else max(bounds.bottom.toFloat(), paint.fontMetrics.descent * 0.8f)) + pad
        val width = ceil(min(paint.measureText(fitted), maxWidthPx)).toInt().coerceAtLeast(1)
        val height = ceil(bottom - top).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).drawText(fitted, 0f, -top, paint)
        }
    }

    private fun capHeight(paint: Paint): Float = Rect().also { paint.getTextBounds("H", 0, 1, it) }.let { -it.top.toFloat() }

    private const val REQUEST_OPEN = 730
}

/** The card's words, from the string resources; English is the only language the app ships. */
internal class WidgetText(val tape: String, val meta: String, val headline: String, val detail: String, val spoken: String) {
    companion object {
        fun of(context: Context, card: NextClassCard): WidgetText {
            val minutes = card.minutes?.let { context.getString(R.string.widget_minutes, it) }
            if (card.kind == NextClassCard.Kind.ALL_DONE) {
                val headline = context.getString(R.string.all_complete_title)
                val detail = context.getString(R.string.widget_all_done_detail)
                return WidgetText(context.getString(R.string.widget_done), "", headline, detail,
                    context.getString(R.string.widget_open_app, "$headline, $detail"))
            }
            val meta = context.getString(R.string.widget_level_class, card.levelNumber, card.classNumber)
            val first = when (card.kind) {
                NextClassCard.Kind.EXAM -> card.targets.takeIf { it > 0 }
                    ?.let { context.resources.getQuantityString(R.plurals.widget_targets, it, it) }
                else -> card.detail
            }
            val parts = listOfNotNull(first?.takeIf(String::isNotBlank), minutes)
            val detail = parts.joinToString(NextClassCard.SEPARATOR)
            val headline = card.role.ifBlank { card.movementName }.ifBlank { context.getString(R.string.app_name) }
            // Spoken with commas, not the dots TalkBack would read out.
            val summary = (listOf(context.getString(R.string.widget_level_class_spoken, card.levelNumber, card.classNumber), headline) +
                parts + listOfNotNull(context.getString(R.string.widget_locked).takeIf { card.locked })).joinToString(", ")
            val spoken = if (card.programId != null) context.getString(R.string.widget_open_class, summary)
                else context.getString(R.string.widget_open_app, summary)
            return WidgetText(context.getString(R.string.widget_next), meta.uppercase(Locale.ENGLISH), headline, detail, spoken)
        }
    }
}
