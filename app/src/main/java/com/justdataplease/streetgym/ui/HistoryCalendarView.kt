package com.justdataplease.streetgym.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.RippleDrawable
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.justdataplease.streetgym.R
import com.justdataplease.streetgym.data.progress.HistoryCalendar
import com.justdataplease.streetgym.data.progress.HistoryCalendar.DayMarks
import com.justdataplease.streetgym.data.progress.WorkoutSession
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.min

private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** The calendar's month and picked day while the app is open, so a day's overview and Back return to them. */
class HistoryCalendarState {
    /** The [HistoryCalendar] month shown, or null for the current one. */
    var month: Int? = null
    /** The picked day as yyyymmdd, or 0 for none. */
    var day: Int = 0
}

/**
 * Progress's History section: its label with a List | Calendar switch, then the newest 100 classes as [row]s or
 * [HistoryCalendarView]. The switch redraws this section only, so the page keeps its scroll position.
 */
fun Context.historySection(
    settings: AppSettings,
    history: List<WorkoutSession>,
    state: HistoryCalendarState,
    row: (WorkoutSession) -> View,
): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    val toggle = FrameLayout(context)
    addWithMargins(LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(sectionLabel(getString(R.string.history)), LinearLayout.LayoutParams(0, WRAP, 1f))
        addView(toggle)
    }, topDp = 4, bottomDp = 8)
    val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    addView(body)
    fun bind() {
        val view = settings.historyView
        toggle.removeAllViews()
        toggle.addView(segmentedSwitch(
            listOf(getString(R.string.history_view_list), getString(R.string.history_view_calendar)),
            view.ordinal,
        ) { index ->
            settings.historyView = HistoryView.entries[index]
            bind()
        }.apply { getChildAt(view.ordinal).isSelected = true })
        body.removeAllViews()
        when {
            view == HistoryView.CALENDAR -> body.addView(HistoryCalendarView(context, history, state, settings.selectedLocale(), row))
            history.isEmpty() -> body.addWithMargins(card().apply { addView(bodyText(getString(R.string.no_history))) }, bottomDp = 10)
            else -> history.take(100).forEach { body.addWithMargins(row(it), bottomDp = 8) }
        }
    }
    bind()
}

/**
 * History as a month. Trained days are filled in the accent, a repeat adds a ring under the day and one of the athlete's
 * own workouts a diamond, a day only marked complete (Complete day, Complete level, the placement) is pale, and today
 * is outlined. The arrows, or a sideways swipe, step between the month of the oldest entry and this one; tapping a day
 * lists its classes under the grid as [row]s.
 *
 * Days are local dates in the phone's time zone ([HistoryCalendar]). A month's marks are worked out once when it is
 * shown; the cells only draw them, with shared paints.
 */
@SuppressLint("ViewConstructor")
class HistoryCalendarView(
    context: Context,
    history: List<WorkoutSession>,
    private val state: HistoryCalendarState,
    private val locale: Locale,
    private val row: (WorkoutSession) -> View,
) : LinearLayout(context) {
    private val zone = TimeZone.getDefault()
    private val byDay = HistoryCalendar.byDay(history, zone)
    private val today = HistoryCalendar.dayKey(System.currentTimeMillis(), zone)
    private val current = HistoryCalendar.monthOf(today)
    private val first = HistoryCalendar.firstMonth(byDay.keys, current)
    // The week starts where the phone's region starts it; names and dates follow the app's language.
    private val firstDayOfWeek = Calendar.getInstance(Locale.getDefault()).firstDayOfWeek
    private val paints = CalendarPaints(context)
    // Dates are formatted as plain calendar dates, at midnight UTC, so no time zone can move them.
    private val date = GregorianCalendar(TimeZone.getTimeZone("UTC"))
    private val dayFormat = format("MMMMd")
    private val titleFormat = format("MMMMyyyy")
    private val monthFormat = format("MMMM")
    private val panelFormat = format("EEEEMMMMd")
    private val title = context.titleText("", 17f).apply {
        gravity = Gravity.CENTER
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val count = context.bodyText("", 14f).apply { gravity = Gravity.CENTER }
    private val previous = arrow("‹", R.string.calendar_previous_month, -1)
    private val next = arrow("›", R.string.calendar_next_month, 1)
    private val weeks = List(6) { LinearLayout(context) }
    private val cells = List(42) { DayCell(context, paints) }
    private val panel = LinearLayout(context).apply { orientation = VERTICAL }
    private var month = (state.month ?: current).coerceIn(first, current)

    init {
        orientation = VERTICAL
        val calendarCard = context.card().apply {
            setPadding(dp(10), dp(8), dp(10), dp(14))
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(previous, LayoutParams(dp(48), dp(48)))
                addView(title, LayoutParams(0, WRAP, 1f))
                addView(next, LayoutParams(dp(48), dp(48)))
            })
            addWithMargins(count, bottomDp = 10)
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                val names = DateFormatSymbols.getInstance(locale).shortWeekdays
                for (column in 0 until 7) addView(TextView(context).apply {
                    text = names[HistoryCalendar.weekday(firstDayOfWeek, column)]
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(AppColors.TEXT_SECONDARY)
                    // Each day's description already names its date.
                    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LayoutParams(0, WRAP, 1f))
            })
            val grid = SwipeArea(context) { step -> showMonth(month + step) }
            weeks.forEachIndexed { index, week ->
                for (column in 0 until 7) {
                    val cell = cells[index * 7 + column]
                    cell.setOnClickListener { pick(cell.key) }
                    week.addView(cell, LayoutParams(0, dp(52), 1f))
                }
                grid.addView(week)
            }
            addWithMargins(grid, topDp = 4)
            addWithMargins(legend(), leftDp = 8, topDp = 12, rightDp = 8)
        }
        addView(calendarCard)
        addWithMargins(panel, topDp = 14)
        showMonth(month)
    }

    private fun showMonth(target: Int) {
        if (target !in first..current) return
        month = target
        state.month = target.takeIf { it != current }
        val year = HistoryCalendar.year(month)
        val number = HistoryCalendar.monthNumber(month)
        val grid = HistoryCalendar.grid(month, firstDayOfWeek)
        val marks = HistoryCalendar.monthMarks(byDay, month, grid.days)
        val trained = HistoryCalendar.trainedCount(marks)
        title.text = titleFormat.on(year, number, 1)
        count.text = if (month == current) {
            resources.getQuantityString(R.plurals.calendar_month_count_current, trained, trained)
        } else {
            resources.getQuantityString(R.plurals.calendar_month_count, trained, trained, monthFormat.on(year, number, 1))
        }
        previous.enable(month > first)
        next.enable(month < current)
        weeks.forEachIndexed { index, week -> week.visibility = if (index < grid.rows) VISIBLE else GONE }
        cells.forEachIndexed { index, cell ->
            val day = grid.dayAt(index)
            if (day == 0) {
                cell.bind(0, null, today = false, open = false, selected = false, description = null)
            } else {
                val key = HistoryCalendar.key(year, number, day)
                val open = key <= today
                cell.bind(key, marks[day], key == today, open, key == state.day,
                    if (open) describe(year, number, day, marks[day], key == today) else dayFormat.on(year, number, day))
            }
        }
        bindPanel()
    }

    private fun pick(key: Int) {
        if (key == 0) return
        state.day = key
        cells.forEach { it.isSelected = it.key == key }
        bindPanel()
    }

    /** The picked day's classes in the list's rows; a placement marks whole levels on one day, so many marks are one line. */
    private fun bindPanel() {
        panel.removeAllViews()
        val key = state.day.takeIf { it != 0 && HistoryCalendar.monthOf(it) == month }
        if (key == null) {
            panel.addView(context.bodyText(context.getString(if (byDay.isEmpty()) R.string.calendar_empty else R.string.calendar_pick_day), 14f))
            return
        }
        panel.addWithMargins(TextView(context).apply {
            text = panelFormat.on(key / 10_000, key / 100 % 100, key % 100)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(AppColors.TEXT)
        }, bottomDp = 8)
        val sessions = byDay[key].orEmpty()
        val (marked, trained) = sessions.partition(WorkoutSession::manuallyCompleted)
        trained.forEach { panel.addWithMargins(row(it), bottomDp = 8) }
        if (marked.size <= 3) {
            marked.forEach { panel.addWithMargins(row(it), bottomDp = 8) }
        } else {
            panel.addView(context.bodyText(resources.getQuantityString(R.plurals.calendar_marked_days, marked.size, marked.size), 14f))
        }
        if (sessions.isEmpty()) panel.addView(context.bodyText(context.getString(R.string.calendar_day_empty), 14f))
    }

    /** "October 25, today, 2 workouts, 1 repeat": what the cell shows, for TalkBack. */
    private fun describe(year: Int, month: Int, day: Int, marks: DayMarks?, isToday: Boolean): String = buildList {
        add(dayFormat.on(year, month, day))
        if (isToday) add(context.getString(R.string.calendar_today_description))
        val trained = marks?.trained ?: 0
        val marked = marks?.marked ?: 0
        if (trained > 0) add(plural(R.plurals.calendar_workouts_description, trained))
        else if (marked == 0) add(context.getString(R.string.calendar_no_workouts_description))
        marks?.repeats?.takeIf { it > 0 }?.let { add(plural(R.plurals.calendar_repeats_description, it)) }
        marks?.custom?.takeIf { it > 0 }?.let { add(plural(R.plurals.calendar_custom_description, it)) }
        if (marked > 0) add(plural(R.plurals.calendar_marked_description, marked))
    }.joinToString(", ")

    private fun plural(id: Int, quantity: Int) = resources.getQuantityString(id, quantity, quantity)

    private fun dp(value: Int) = context.dp(value)

    private fun legend(): LinearLayout = LinearLayout(context).apply {
        orientation = VERTICAL
        listOf(
            listOf(Marker.WORKOUT to R.string.calendar_legend_workout, Marker.REPEAT to R.string.calendar_legend_repeat),
            listOf(Marker.CUSTOM to R.string.calendar_legend_custom, Marker.MARKED to R.string.calendar_legend_marked),
        ).forEach { pair ->
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                pair.forEach { (marker, label) ->
                    addView(LinearLayout(context).apply {
                        orientation = HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        minimumHeight = dp(24)
                        addView(Swatch(context, paints, marker), LayoutParams(dp(16), dp(16)))
                        addWithMargins(context.bodyText(context.getString(label), 12f), width = WRAP, leftDp = 6)
                    }, LayoutParams(0, WRAP, 1f))
                }
            })
        }
    }

    private fun arrow(glyph: String, description: Int, step: Int) = TextView(context).apply {
        text = glyph
        gravity = Gravity.CENTER
        textSize = 30f
        setTextColor(AppColors.TEXT)
        contentDescription = context.getString(description)
        isClickable = true
        isFocusable = true
        background = RippleDrawable(ColorStateList.valueOf(paints.ripple), null, null)
        setOnClickListener { showMonth(month + step) }
    }

    private fun TextView.enable(on: Boolean) {
        isEnabled = on
        alpha = if (on) 1f else 0.25f
        // An arrow that has just reached the last month is still pressed: clear its ripple rather than leave it lit.
        if (!on) {
            isPressed = false
            jumpDrawablesToCurrentState()
        }
    }

    private fun format(skeleton: String) =
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale).apply { timeZone = date.timeZone }

    private fun SimpleDateFormat.on(year: Int, month: Int, day: Int): String {
        date.clear()
        date.set(year, month - 1, day)
        return format(date.time)
    }
}

private enum class Marker { WORKOUT, REPEAT, CUSTOM, MARKED }

/** Paints, sizes and colours shared by every cell of one calendar, made once so that drawing allocates nothing. */
private class CalendarPaints(context: Context) {
    private val density = context.resources.displayMetrics.density
    val disc = Paint(Paint.ANTI_ALIAS_FLAG)
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        // Follows the font scale, up to where a number still fits its disc.
        textSize = min(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, context.resources.displayMetrics), 18f * density)
    }
    /** From a disc's centre to the baseline that centres a number on it. */
    val textOffset = -(text.descent() + text.ascent()) / 2f
    val area = RectF()
    val discRadius = 15f * density
    val discY = 21f * density
    val todayGap = 3f * density
    val markerY = 45f * density
    val markerSize = 3.2f * density
    val markerGap = 5.5f * density
    val corner = 14f * density
    val inset = 2f * density
    val numbers = Array(32) { it.toString() }

    val trained = AppColors.PRIMARY
    val onTrained = AppColors.ON_ACCENT
    val marked = blendColor(AppColors.PRIMARY, AppColors.SURFACE, 0.78f)
    val repeat = AppColors.PRIMARY
    val custom = AppColors.TEXT
    val today = AppColors.TEXT
    val selected = AppColors.INFO_SURFACE
    val number = AppColors.TEXT
    val faint = AppColors.TEXT_SECONDARY
    val future = blendColor(AppColors.TEXT_SECONDARY, AppColors.SURFACE, 0.55f)
    val ripple = Color.argb(0x33, Color.red(AppColors.PRIMARY), Color.green(AppColors.PRIMARY), Color.blue(AppColors.PRIMARY))

    fun diamond(canvas: Canvas, x: Float, y: Float, color: Int) {
        disc.color = color
        area.set(x - markerSize * 0.9f, y - markerSize * 0.9f, x + markerSize * 0.9f, y + markerSize * 0.9f)
        canvas.save()
        canvas.rotate(45f, x, y)
        canvas.drawRect(area, disc)
        canvas.restore()
    }
}

/** One day of the grid, drawn from its [DayMarks]; a cell before day 1 or after the last draws nothing. */
@SuppressLint("ViewConstructor")
private class DayCell(context: Context, private val paints: CalendarPaints) : View(context) {
    /** yyyymmdd, or 0 for an empty cell. */
    var key = 0
        private set
    private var marks: DayMarks? = null
    private var today = false

    init {
        background = RippleDrawable(ColorStateList.valueOf(paints.ripple), null,
            roundedDrawable(Color.WHITE, paints.corner))
    }

    fun bind(key: Int, marks: DayMarks?, today: Boolean, open: Boolean, selected: Boolean, description: String?) {
        this.key = key
        this.marks = marks
        this.today = today
        isEnabled = open
        isClickable = open
        isFocusable = key != 0
        importantForAccessibility = if (key == 0) IMPORTANT_FOR_ACCESSIBILITY_NO else IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = description
        isSelected = selected
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (key == 0) return
        val p = paints
        val x = width / 2f
        val y = p.discY
        val radius = min(p.discRadius, x - p.todayGap - p.line.strokeWidth)
        if (isSelected) {
            p.area.set(p.inset, p.inset / 2f, width - p.inset, height - p.inset / 2f)
            p.disc.color = p.selected
            canvas.drawRoundRect(p.area, p.corner, p.corner, p.disc)
            p.line.color = p.trained
            canvas.drawRoundRect(p.area, p.corner, p.corner, p.line)
        }
        val marks = marks
        val trained = marks != null && marks.trained > 0
        val markedOnly = marks != null && !trained && marks.marked > 0
        if (trained || markedOnly) {
            p.disc.color = if (trained) p.trained else p.marked
            canvas.drawCircle(x, y, radius, p.disc)
        }
        if (today) {
            p.line.color = p.today
            canvas.drawCircle(x, y, radius + p.todayGap, p.line)
        }
        p.text.color = when {
            trained -> p.onTrained
            markedOnly -> p.faint
            !isEnabled -> p.future
            else -> p.number
        }
        p.text.typeface = if (trained || today) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        canvas.drawText(p.numbers[key % 100], x, y + p.textOffset, p.text)
        if (marks == null) return
        val repeat = marks.repeats > 0
        val custom = marks.custom > 0
        val offset = if (repeat && custom) p.markerGap else 0f
        if (repeat) {
            p.line.color = p.repeat
            canvas.drawCircle(x - offset, p.markerY, p.markerSize, p.line)
        }
        if (custom) p.diamond(canvas, x + offset, p.markerY, p.custom)
    }
}

/** A legend sample: the same shapes the cells draw. */
@SuppressLint("ViewConstructor")
private class Swatch(context: Context, private val paints: CalendarPaints, private val marker: Marker) : View(context) {
    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        val p = paints
        val x = width / 2f
        val y = height / 2f
        when (marker) {
            Marker.WORKOUT, Marker.MARKED -> {
                p.disc.color = if (marker == Marker.WORKOUT) p.trained else p.marked
                canvas.drawCircle(x, y, min(x, y) - p.inset / 2f, p.disc)
            }
            Marker.REPEAT -> {
                p.line.color = p.repeat
                canvas.drawCircle(x, y, p.markerSize, p.line)
            }
            Marker.CUSTOM -> p.diamond(canvas, x, y, p.custom)
        }
    }
}

/** The weeks; a sideways swipe steps the month like the arrows ([step] gets -1 or 1). Taps still reach the days. */
@SuppressLint("ViewConstructor")
private class SwipeArea(context: Context, private val step: (Int) -> Unit) : LinearLayout(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val distance = context.dp(56)
    private var downX = 0f
    private var downY = 0f
    private var swiping = false

    init {
        orientation = VERTICAL
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean = track(event)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        track(event)
        if (event.actionMasked == MotionEvent.ACTION_UP && swiping) {
            swiping = false
            val dx = event.x - downX
            if (abs(dx) >= distance) step(if (dx < 0) 1 else -1)
        }
        return true
    }

    /** True once the gesture is a sideways drag; from then on the page's ScrollView leaves it to this view. */
    private fun track(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                swiping = false
            }
            MotionEvent.ACTION_MOVE -> if (!swiping) {
                val dx = abs(event.x - downX)
                val dy = abs(event.y - downY)
                if (dx > slop && dx > dy * 2) {
                    swiping = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_CANCEL -> swiping = false
        }
        return swiping
    }
}
