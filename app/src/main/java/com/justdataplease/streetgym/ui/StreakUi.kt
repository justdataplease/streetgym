package com.justdataplease.streetgym.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.justdataplease.streetgym.R
import com.justdataplease.streetgym.data.progress.Streak
import com.justdataplease.streetgym.data.progress.StreakDays
import java.text.DateFormatSymbols
import java.util.Locale
import kotlin.math.roundToInt

private const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

/** "12 day streak"; with none, "Streak ended" after two missed days or "No streak yet" before the first workout. */
fun Context.streakTitle(streak: Streak): String = when {
    streak.days > 0 -> getString(R.string.streak_days, streak.days)
    streak.best > 0 -> getString(R.string.streak_ended_title)
    else -> getString(R.string.streak_none_title)
}

/** What today still asks: done, a rest day if wanted, train to keep it, or finish a workout to start one. */
private fun Context.streakStatus(streak: Streak): String = getString(when {
    streak.litToday -> R.string.streak_lit
    streak.restToday -> R.string.streak_rest
    streak.days > 0 -> R.string.streak_unlit
    streak.best > 0 -> R.string.streak_ended
    else -> R.string.streak_none
})

/**
 * The streak on Today's line: the flame with its number, then this week's seven days. Tapping it opens
 * [showStreakSheet]. [roomy] false (a narrow phone or large type) draws smaller boxes, so the line still fits.
 */
fun Context.streakStrip(snapshot: StreakDays.Snapshot, locale: Locale, roomy: Boolean, onClick: () -> Unit): View =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(48)
        setPadding(dp(8), dp(2), 0, dp(2))
        isClickable = true
        isFocusable = true
        background = RippleDrawable(ColorStateList.valueOf(rippleColor()), null, ColorDrawable(Color.WHITE))
        contentDescription = "${streakTitle(snapshot.streak)}. ${streakStatus(snapshot.streak)}"
        addView(StreakFlameView(context, snapshot.streak, widthDp = if (roomy) 33 else 29, shadowDp = 2))
        addWithMargins(StreakWeekView(context, snapshot.week, locale, boxDp = if (roomy) 24 else 20, gapDp = if (roomy) 4 else 3),
            width = WRAP, leftDp = if (roomy) 9 else 7)
        setOnClickListener { onClick() }
    }

/**
 * What a tap on the flame opens: the streak, the best one, this week and the rule in two lines. [onHistory] adds
 * "See history", which opens the month's calendar; History's own line leaves it out.
 */
fun Context.showStreakSheet(snapshot: StreakDays.Snapshot, locale: Locale, onHistory: (() -> Unit)?) {
    val streak = snapshot.streak
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(20), dp(20), dp(4))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(StreakFlameView(context, streak, widthDp = 76, shadowDp = 4))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(titleText(streakTitle(streak), 24f))
                if (streak.best > 0) {
                    addWithMargins(bodyText(resources.getQuantityString(R.plurals.streak_best, streak.best, streak.best), 14f), topDp = 4)
                }
                addWithMargins(bodyText(streakStatus(streak), 14f).apply { setTextColor(AppColors.TEXT) }, topDp = 4)
            }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(16) })
        })
        addView(StreakWeekView(context, snapshot.week, locale, boxDp = 30, gapDp = 6), LinearLayout.LayoutParams(WRAP, WRAP).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(22)
        })
        addWithMargins(bodyText(getString(R.string.streak_rules), 14f), topDp = 18)
    }
    AlertDialog.Builder(this)
        .setView(ScrollView(this).apply { addView(content) })
        .apply { if (onHistory != null) setPositiveButton(R.string.streak_see_history) { _, _ -> onHistory() } }
        .setNegativeButton(R.string.skills_close, null)
        .show()
}

/**
 * History's line over the month: the flame, "12 DAY STREAK" on tape and the best streak; with no streak now, only the
 * best one. Null before the first workout. Tapping it opens [showStreakSheet].
 */
fun Context.streakLine(snapshot: StreakDays.Snapshot, locale: Locale): View? {
    val streak = snapshot.streak
    if (streak.best <= 0) return null
    return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        minimumHeight = dp(40)
        isClickable = true
        isFocusable = true
        background = RippleDrawable(ColorStateList.valueOf(rippleColor()), null, ColorDrawable(Color.WHITE))
        if (streak.days > 0) {
            contentDescription = "${streakTitle(streak)}. ${resources.getQuantityString(R.plurals.streak_best, streak.best, streak.best)}"
            addView(StreakFlameView(context, streak, widthDp = 18, shadowDp = 2, numbered = false))
            addWithMargins(TextView(context).apply {
                text = streakTitle(streak).uppercase(Locale.ENGLISH)
                textSize = 13f
                typeface = AppType.display
                letterSpacing = AppColors.theme.shape.labelSpacing
                setTextColor(AppColors.contentOn(AppColors.ACCENT))
                setBackgroundColor(AppColors.ACCENT)
                setPadding(dp(8), dp(3), dp(8), dp(3))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, width = WRAP, leftDp = 8)
            addWithMargins(bodyText(getString(R.string.streak_best_short, streak.best), 13f).apply {
                typeface = AppType.bold
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, width = WRAP, leftDp = 8)
        } else {
            addView(bodyText(resources.getQuantityString(R.plurals.streak_best_only, streak.best, streak.best), 13f),
                LinearLayout.LayoutParams(WRAP, WRAP))
        }
        setOnClickListener { showStreakSheet(snapshot, locale, onHistory = null) }
    }
}

private fun rippleColor(): Int = if (AppColors.darkMode) 0x22FFFFFF else 0x22000000

/**
 * This week as seven boxes with the weekday's letter: a trained day filled in the accent, a rest day the streak
 * carried on over in a pale accent, today outlined and underlined, the others faint. The days are spoken by whatever
 * holds the row.
 */
@SuppressLint("ViewConstructor")
class StreakWeekView(
    context: Context,
    private val days: List<Streak.Day>,
    locale: Locale,
    boxDp: Int,
    gapDp: Int,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val box = boxDp * density
    private val gap = gapDp * density
    private val stroke = 2f * density
    private val underlineGap = 4f * density
    private val underline = 3f * density
    private val letters = DateFormatSymbols.getInstance(locale).shortWeekdays.let { names ->
        days.map { names[StreakDays.calendarDay(Streak.weekday(it.epochDay))].take(1).uppercase(locale) }
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = (boxDp * 0.3f + 4f) * density
        // The body's bold in every theme: a label face's M and W (Wheatpaste's mono) don't read this small.
        typeface = AppType.bold
    }
    private val textOffset = -(text.descent() + text.ascent()) / 2f
    private val outlined = AppColors.theme.shape.borderDp > 0
    private val faint = blendColor(AppColors.TEXT_SECONDARY, AppColors.BACKGROUND, 0.45f)
    private val pale = blendColor(AppColors.ACCENT, AppColors.BACKGROUND, 0.62f)
    private val onAccent = AppColors.contentOn(AppColors.ACCENT)

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val count = days.size
        setMeasuredDimension((box * count + gap * (count - 1)).roundToInt(), (box + underlineGap + underline).roundToInt())
    }

    override fun onDraw(canvas: Canvas) {
        val inset = stroke / 2f
        days.forEachIndexed { index, day ->
            val left = index * (box + gap)
            val right = left + box
            when {
                day.trained -> {
                    fill.color = AppColors.ACCENT
                    canvas.drawRect(left, 0f, right, box, fill)
                    if (outlined) {
                        line.color = AppColors.BORDER
                        canvas.drawRect(left + inset, inset, right - inset, box - inset, line)
                    }
                    text.color = onAccent
                }
                day.rest -> {
                    fill.color = pale
                    canvas.drawRect(left, 0f, right, box, fill)
                    line.color = faint
                    canvas.drawRect(left + inset, inset, right - inset, box - inset, line)
                    text.color = AppColors.TEXT_SECONDARY
                }
                day.today -> {
                    fill.color = AppColors.SURFACE
                    canvas.drawRect(left, 0f, right, box, fill)
                    line.color = AppColors.TEXT
                    canvas.drawRect(left + inset, inset, right - inset, box - inset, line)
                    text.color = AppColors.TEXT
                }
                else -> {
                    line.color = faint
                    canvas.drawRect(left + inset, inset, right - inset, box - inset, line)
                    text.color = faint
                }
            }
            canvas.drawText(letters[index], left + box / 2f, box / 2f + textOffset, text)
            if (day.today) {
                fill.color = AppColors.TEXT
                canvas.drawRect(left, box + underlineGap, right, box + underlineGap + underline, fill)
            }
        }
    }
}
