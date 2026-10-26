package com.justdataplease.streetgym.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import com.justdataplease.streetgym.R
import kotlin.math.min

/*
 * The signature pieces of each theme, beyond what UiKit's shared helpers draw: the bottom bar, the Today header,
 * the level list and the focus progress strip. Fresh Court has none of them and looks exactly as it always did.
 */

private val active get() = AppColors.theme

/** The bottom bar's background in the active theme. */
fun Context.navBarBackground(): Drawable = when (active) {
    StreetTheme.CONCRETE, StreetTheme.CONCRETE_TREES -> roundedDrawable(AppColors.BORDER, 0f)
    StreetTheme.WHEATPASTE -> TopRuleDrawable(AppColors.BACKGROUND, AppColors.BORDER, dp(3).toFloat())
    StreetTheme.NIGHT_PARK -> roundedDrawable(AppColors.SURFACE, dp(24).toFloat())
    StreetTheme.FRESH_COURT -> roundedDrawable(AppColors.SURFACE, dp(24).toFloat(), AppColors.DIVIDER, dp(1))
}

/** A bottom-bar item's background, null for none. */
fun Context.navItemBackground(selected: Boolean): Drawable? = when (active) {
    StreetTheme.CONCRETE, StreetTheme.CONCRETE_TREES -> if (selected) roundedDrawable(AppColors.ACCENT, 0f) else null
    StreetTheme.NIGHT_PARK -> if (selected) TopRuleDrawable(Color.TRANSPARENT, AppColors.PRIMARY, dp(3).toFloat()) else null
    StreetTheme.WHEATPASTE -> null
    StreetTheme.FRESH_COURT -> roundedDrawable(if (selected) AppColors.INFO_SURFACE else AppColors.SURFACE, dp(16).toFloat())
}

/** The colour of a bottom-bar item's icon and label. */
fun navItemColor(selected: Boolean): Int = when (active) {
    StreetTheme.CONCRETE, StreetTheme.CONCRETE_TREES -> if (selected) AppColors.BORDER else 0xFFBDBAB3.toInt()
    StreetTheme.WHEATPASTE -> if (selected) AppColors.TEXT else AppColors.TEXT_SECONDARY
    else -> if (selected) AppColors.PRIMARY else AppColors.TEXT_SECONDARY
}

/** Type for a bottom-bar label; Wheatpaste marks the selected one with a highlighter swipe. */
fun TextView.styleNavLabel(selected: Boolean) {
    if (!AppType.custom) return
    typeface = if (active == StreetTheme.WHEATPASTE) AppType.label else AppType.bold
    isAllCaps = true
    textSize = 10f
    letterSpacing = 0.06f
    if (active == StreetTheme.WHEATPASTE && selected) {
        background = roundedDrawable(AppColors.SPOT, 0f)
        setPadding(context.dp(4), 0, context.dp(4), 0)
    }
}

/** The toolbar title in the theme's headline face. */
fun TextView.styleToolbarTitle() {
    if (!AppType.custom) return
    typeface = AppType.display
    isAllCaps = active.shape.upperTitles
    textSize = if (active == StreetTheme.WHEATPASTE) 20f else 25f
}

/**
 * The head of Today's day card: Concrete's tape strip and big class number, Night Park's stencilled overall number,
 * Wheatpaste's pasted poster. Null in Fresh Court.
 */
fun Context.todayHeader(levelNumber: Int, levelName: String, classInLevel: Int, levelSize: Int, overall: Int, total: Int): View? {
    val levelLine = getString(R.string.theme_level_line, levelNumber, levelName)
    return when (active) {
        StreetTheme.FRESH_COURT -> null
        StreetTheme.CONCRETE, StreetTheme.CONCRETE_TREES -> LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addWithMargins(TextView(context).apply {
                text = levelLine.uppercase()
                setTextColor(AppColors.contentOn(AppColors.ACCENT))
                textSize = 12f
                typeface = AppType.label
                letterSpacing = 0.14f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                // The tape runs 8 dp past the card on each side and tilts, so the text needs more than the 24 dp it
                // used to have to clear the clipped, lowered left end and the edges.
                setPadding(dp(32), dp(9), dp(28), dp(9))
                background = roundedDrawable(AppColors.ACCENT, 0f, AppColors.BORDER, dp(2))
                rotation = -2f
            }, leftDp = -24, rightDp = -24, topDp = 2, bottomDp = 12)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM
                addView(bigNumber(classInLevel.toString(), 84f, AppColors.TEXT))
                addWithMargins(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(context).apply {
                        text = getString(R.string.theme_class).uppercase()
                        setTextColor(AppColors.TEXT)
                        textSize = 11f
                        typeface = AppType.label
                        letterSpacing = 0.16f
                    })
                    addView(bigNumber("/ $levelSize", 24f, AppColors.TEXT_SECONDARY))
                }, width = ViewGroup.LayoutParams.WRAP_CONTENT, leftDp = 10, bottomDp = 6)
            })
            contentDescription = getString(R.string.theme_class_of, classInLevel, levelSize, levelLine)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        StreetTheme.NIGHT_PARK -> LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = getString(R.string.theme_class).uppercase()
                setTextColor(AppColors.TEXT_SECONDARY)
                textSize = 11f
                typeface = AppType.bold
                letterSpacing = 0.22f
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM
                addView(bigNumber(overall.toString(), 80f, AppColors.PRIMARY))
                addWithMargins(bigNumber("/$total", 22f, AppColors.TEXT_SECONDARY, AppType.display),
                    width = ViewGroup.LayoutParams.WRAP_CONTENT, leftDp = 6, bottomDp = 8)
            })
            addWithMargins(TextView(context).apply {
                text = getString(R.string.theme_level_chip, levelNumber, levelName, classInLevel).uppercase()
                setTextColor(AppColors.TEXT)
                textSize = 14f
                typeface = AppType.display
                letterSpacing = 0.08f
                maxLines = 1
                setPadding(dp(12), dp(6), dp(12), dp(6))
                background = roundedDrawable(AppColors.INFO_SURFACE, dp(12).toFloat())
            }, width = ViewGroup.LayoutParams.WRAP_CONTENT, topDp = 8, bottomDp = 12)
            contentDescription = getString(R.string.theme_class_of, overall, total, levelLine)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        StreetTheme.WHEATPASTE -> LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            val dots = dp(96)
            background = LayerDrawable(arrayOf(roundedDrawable(AppColors.PRIMARY_DARK, 0f),
                HalftoneDrawable(AppColors.SPOT, dp(8).toFloat()))).apply {
                setLayerSize(1, dots, dots)
                setLayerGravity(1, Gravity.END or Gravity.BOTTOM)
                setLayerInset(1, 0, 0, -dots / 4, -dots / 3)
            }
            rotation = -1.5f
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(monoText(levelLine.uppercase()), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(monoText("$classInLevel/$levelSize"))
            })
            addWithMargins(TextView(context).apply {
                text = getString(R.string.theme_class_number, classInLevel).uppercase()
                setTextColor(AppColors.HERO_TEXT)
                textSize = 34f
                typeface = AppType.display
                includeFontPadding = false
            }, topDp = 8)
            contentDescription = getString(R.string.theme_class_of, classInLevel, levelSize, levelLine)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
    }
}

private fun Context.bigNumber(value: String, sizeSp: Float, color: Int, face: Typeface = AppType.numeral): TextView =
    TextView(this).apply {
        text = value
        setTextColor(color)
        textSize = sizeSp
        typeface = face
        includeFontPadding = false
        maxLines = 1
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

private fun Context.monoText(value: String): TextView = TextView(this).apply {
    text = value
    setTextColor(AppColors.HERO_TEXT)
    textSize = 12f
    typeface = AppType.label
    maxLines = 1
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
}

enum class LevelState { DONE, CURRENT, UPCOMING }

/** True when level cards sit on Night Park's route line, which joins them without gaps. */
val levelsOnRoute: Boolean get() = active == StreetTheme.NIGHT_PARK

/**
 * Dresses a level card for its [state]: Concrete taps the current level in tape yellow and dashes what's to come,
 * Night Park strings the levels on a route, Wheatpaste pastes them up as tilted posters with a stamp on finished ones.
 */
fun Context.decorateLevel(card: LinearLayout, state: LevelState, index: Int, first: Boolean, last: Boolean): View {
    val radius = dp(24).toFloat()
    when (active) {
        StreetTheme.FRESH_COURT -> Unit
        StreetTheme.CONCRETE, StreetTheme.CONCRETE_TREES -> when (state) {
            LevelState.CURRENT -> {
                val offset = dp(4)
                card.background = shadowed(roundedDrawable(AppColors.ACCENT, 0f, AppColors.BORDER, dp(2)),
                    roundedDrawable(AppColors.BORDER, 0f), offset)
                card.setPadding(dp(16), dp(16), dp(16) + offset, dp(16) + offset)
            }
            LevelState.UPCOMING -> card.background = dashedDrawable(Color.TRANSPARENT, AppColors.BORDER)
            LevelState.DONE -> Unit
        }
        StreetTheme.NIGHT_PARK -> {
            card.background = when (state) {
                LevelState.CURRENT -> roundedDrawable(AppColors.SURFACE, radius, AppColors.PRIMARY, dp(2))
                LevelState.UPCOMING -> roundedDrawable(AppColors.BACKGROUND, radius, AppColors.DIVIDER, dp(1))
                LevelState.DONE -> card.background
            }
            return LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp(12))
                addView(RouteRail(context, state, first, last), LinearLayout.LayoutParams(dp(30), ViewGroup.LayoutParams.MATCH_PARENT))
                addView(card, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(10) })
            }
        }
        StreetTheme.WHEATPASTE -> {
            card.rotation = if (index % 2 == 0) -1f else 1f
            when (state) {
                LevelState.CURRENT -> {
                    card.background = roundedDrawable(AppColors.PRIMARY_DARK, 0f)
                    onPoster(card)
                }
                LevelState.UPCOMING -> card.background = dashedDrawable(Color.TRANSPARENT, AppColors.BORDER)
                LevelState.DONE -> card.foreground = LayerDrawable(listOfNotNull(
                    StampDrawable(getString(R.string.theme_stamp_done), AppColors.PRIMARY, resources.displayMetrics.density, AppType.label),
                    card.foreground,
                ).toTypedArray())
            }
        }
    }
    return card
}

/** Recolours a card's content for the blue poster: white text, white plates with blue type, a white progress bar. */
private fun onPoster(view: View) {
    when (view) {
        is ProgressBar -> {
            view.progressTintList = ColorStateList.valueOf(AppColors.HERO_TEXT)
            view.progressBackgroundTintList = ColorStateList.valueOf(0x55FFFFFF)
        }
        is TextView -> if (view.background != null) {
            view.background = roundedDrawable(AppColors.HERO_TEXT, 0f)
            view.setTextColor(AppColors.PRIMARY_DARK)
            view.compoundDrawablesRelative.filterNotNull().forEach { it.setTint(AppColors.PRIMARY_DARK) }
        } else view.setTextColor(AppColors.HERO_TEXT)
        is ViewGroup -> for (index in 0 until view.childCount) onPoster(view.getChildAt(index))
    }
}

/** Night Park's route: a line through every level with a station beside each card, solid as far as the athlete has come. */
private class RouteRail(context: Context, private val state: LevelState, private val first: Boolean, private val last: Boolean) :
    View(context) {
    private val density = resources.displayMetrics.density
    private val reached = AppColors.TEXT
    private val ahead = blendColor(AppColors.TEXT_SECONDARY, AppColors.BACKGROUND, 0.45f)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f * density; style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dashes = DashPathEffect(floatArrayOf(6f * density, 6f * density), 0f)
    private val check = Path()

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onDraw(canvas: Canvas) {
        val x = width / 2f
        val y = 38f * density
        if (!first) segment(canvas, x, 0f, y, solid = state != LevelState.UPCOMING)
        if (!last) segment(canvas, x, y, height.toFloat(), solid = state == LevelState.DONE)
        when (state) {
            LevelState.DONE -> {
                fill.color = reached
                canvas.drawCircle(x, y, 11f * density, fill)
                line.pathEffect = null
                line.color = AppColors.BACKGROUND
                line.strokeWidth = 2.6f * density
                check.reset()
                check.moveTo(x - 5f * density, y)
                check.lineTo(x - 1.5f * density, y + 3.5f * density)
                check.lineTo(x + 5f * density, y - 3.5f * density)
                canvas.drawPath(check, line)
                line.strokeWidth = 2f * density
            }
            LevelState.CURRENT -> {
                fill.color = AppColors.BACKGROUND
                canvas.drawCircle(x, y, 12f * density, fill)
                line.pathEffect = null
                line.color = AppColors.PRIMARY
                line.strokeWidth = 4f * density
                canvas.drawCircle(x, y, 10f * density, line)
                line.strokeWidth = 2f * density
                fill.color = AppColors.PRIMARY
                canvas.drawCircle(x, y, 4f * density, fill)
            }
            LevelState.UPCOMING -> {
                fill.color = AppColors.BACKGROUND
                canvas.drawCircle(x, y, 8f * density, fill)
                line.pathEffect = null
                line.color = ahead
                canvas.drawCircle(x, y, 8f * density, line)
            }
        }
    }

    private fun segment(canvas: Canvas, x: Float, from: Float, to: Float, solid: Boolean) {
        line.color = if (solid) reached else ahead
        line.pathEffect = if (solid) null else dashes
        canvas.drawLine(x, from, x, to, line)
    }
}

/**
 * The focus workout's class progress: Concrete's row of set boxes, Night Park's ticked track and Wheatpaste's tally of
 * finished sets. [completed] holds one flag per set in class order; [current] is the set on screen.
 */
class SetProgressView(context: Context, private val completed: BooleanArray, private val current: Int?) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onDraw(canvas: Canvas) {
        when (AppColors.theme) {
            StreetTheme.CONCRETE, StreetTheme.CONCRETE_TREES -> boxes(canvas)
            StreetTheme.WHEATPASTE -> tally(canvas)
            else -> track(canvas)
        }
    }

    private fun boxes(canvas: Canvas) {
        val count = completed.size
        if (count == 0) return
        val gap = if (count > 40) density else 2f * density
        val cell = (width - gap * (count - 1)) / count
        val stroke = 1.5f * density
        for (index in 0 until count) {
            val left = index * (cell + gap)
            box.set(left + stroke / 2, stroke / 2, left + cell - stroke / 2, height - stroke / 2)
            paint.style = Paint.Style.FILL
            paint.color = when {
                completed[index] -> AppColors.BORDER
                index == current -> AppColors.ACCENT
                else -> Color.TRANSPARENT
            }
            canvas.drawRect(box, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            paint.color = AppColors.BORDER
            canvas.drawRect(box, paint)
        }
    }

    private fun track(canvas: Canvas) {
        val count = completed.size
        if (count == 0) return
        val mid = height / 2f
        val thick = 4f * density
        paint.style = Paint.Style.FILL
        paint.color = AppColors.DIVIDER
        canvas.drawRect(0f, mid - thick / 2, width.toFloat(), mid + thick / 2, paint)
        val gap = if (count <= 60) 2f * density else 0f
        val cell = (width - gap * (count - 1)) / count
        paint.color = AppColors.PRIMARY
        for (index in 0 until count) if (completed[index]) {
            val left = index * (cell + gap)
            canvas.drawRect(left, mid - thick / 2, left + cell, mid + thick / 2, paint)
        }
        current?.takeIf { it in 0 until count }?.let { index ->
            val center = index * (cell + gap) + cell / 2
            paint.color = AppColors.TEXT
            canvas.drawRect(center - 1.5f * density, 0f, center + 1.5f * density, height.toFloat(), paint)
        }
    }

    private fun tally(canvas: Canvas) {
        val done = completed.count { it }
        val marks = done + if (current != null) 1 else 0
        if (marks == 0) return
        val groups = (marks + 4) / 5
        // Strokes 6 dp apart and 10 dp between groups, squeezed when a long class would run off the strip.
        val natural = groups * (4 * 6f + 10f) * density
        val scale = min(1f, width / natural)
        val pitch = 6f * density * scale
        val groupGap = 10f * density * scale
        val top = 1.5f * density
        val bottom = height - 1.5f * density
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 2.4f * density
        var x = 2f * density
        for (mark in 0 until marks) {
            val inGroup = mark % 5
            paint.color = if (mark < done) AppColors.TEXT else AppColors.SPOT
            if (inGroup == 4) {
                canvas.drawLine(x - 4 * pitch - 2f * density, bottom - (bottom - top) * 0.25f, x + 2f * density, top + (bottom - top) * 0.3f, paint)
                x += groupGap
            } else {
                canvas.drawLine(x, top, x, bottom, paint)
                x += pitch
            }
        }
    }

    companion object {
        /** How tall the strip is in the active theme, in dp. */
        val heightDp: Int get() = when (AppColors.theme) {
            StreetTheme.CONCRETE, StreetTheme.CONCRETE_TREES -> 10
            StreetTheme.NIGHT_PARK -> 12
            StreetTheme.WHEATPASTE -> 22
            StreetTheme.FRESH_COURT -> 4
        }
    }
}

/** Settings' theme choice: a row per theme with a small swatch and its name in its own headline face. */
fun Context.themePicker(selected: StreetTheme, onPick: (StreetTheme) -> Unit): LinearLayout = card().apply {
    setPadding(dp(16), dp(6), dp(16), dp(6))
    StreetTheme.entries.forEachIndexed { index, option ->
        if (index > 0) addView(View(context).apply { setBackgroundColor(AppColors.DIVIDER) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
        val name = getString(option.label)
        val summary = getString(option.summary)
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(68)
            setPadding(0, dp(8), 0, dp(8))
            isClickable = true
            isFocusable = true
            isSelected = option == selected
            foreground = selectableForeground()
            contentDescription = if (option == selected) "$name, $summary, ${getString(R.string.theme_selected)}" else "$name, $summary"
            setOnClickListener { if (option != selected) onPick(option) }
            addView(ThemeSwatch(context, option), LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = name
                    setTextColor(AppColors.TEXT)
                    textSize = 17f
                    typeface = AppType.display(option)
                    maxLines = 1
                })
                addView(bodyText(summary, 13f))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(14) })
            addView(RadioButton(context).apply {
                isChecked = option == selected
                isClickable = false
                isFocusable = false
                buttonTintList = ColorStateList.valueOf(if (AppColors.theme.concrete) AppColors.TEXT else AppColors.PRIMARY)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
        })
    }
}

/** A thumbnail of [option]: its page, a card on it in its own corner style and its action colour. */
private class ThemeSwatch(context: Context, private val option: StreetTheme) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val area = RectF()

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onDraw(canvas: Canvas) {
        val colors = option.palette
        val w = width.toFloat()
        val h = height.toFloat()
        area.set(0f, 0f, w, h)
        shape(area, 10f)
        paint.style = Paint.Style.FILL
        paint.color = colors.background
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density
        paint.color = AppColors.DIVIDER
        canvas.drawPath(path, paint)
        area.set(w * 0.18f, h * 0.18f, w * 0.82f, h * 0.62f)
        shape(area, 6f)
        paint.style = Paint.Style.FILL
        paint.color = colors.surface
        canvas.drawPath(path, paint)
        if (option.shape.borderDp >= 2) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f * density
            paint.color = colors.border
            canvas.drawPath(path, paint)
        }
        area.set(w * 0.18f, h * 0.7f, w * 0.82f, h * 0.84f)
        shape(area, 4f)
        paint.style = Paint.Style.FILL
        paint.color = if (option == StreetTheme.WHEATPASTE) colors.spot else colors.action
        canvas.drawPath(path, paint)
    }

    private fun shape(box: RectF, radiusDp: Float) {
        path.reset()
        val r = radiusDp * density
        when (option.shape.corner) {
            Corner.ROUND -> path.addRoundRect(box, r, r, Path.Direction.CW)
            Corner.SQUARE -> path.addRect(box, Path.Direction.CW)
            Corner.CHAMFER -> {
                val cut = min(r * 0.8f, min(box.width(), box.height()) / 2)
                path.moveTo(box.left + cut, box.top)
                path.lineTo(box.right, box.top)
                path.lineTo(box.right, box.bottom - cut)
                path.lineTo(box.right - cut, box.bottom)
                path.lineTo(box.left, box.bottom)
                path.lineTo(box.left, box.top + cut)
                path.close()
            }
        }
    }
}
