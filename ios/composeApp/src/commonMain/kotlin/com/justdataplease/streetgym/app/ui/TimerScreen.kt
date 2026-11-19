package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerColors
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.TimePickerLayoutType
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.Timing
import com.justdataplease.streetgym.app.data.TimingState
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.cancel
import com.justdataplease.streetgym.app.theme.Street
import com.justdataplease.streetgym.app.theme.StreetTheme
import kotlinx.coroutines.delay
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlin.time.Instant

/* Android's TimingPanel writes its words in the screen rather than its string files; these are the same words. */
internal val TIMER_TABS = listOf("Timer", "Stopwatch", "Alarm")
internal const val TIMER_COUNTING = "Counting down"
internal const val TIMER_UP = "Time’s up"
internal const val TIMER_PAUSED = "Paused"
internal const val TIMER_TAP = "Tap the time to change it"
internal const val TIMER_START = "Start"
internal const val TIMER_PAUSE = "Pause"
internal const val TIMER_RESUME = "Resume"
internal const val TIMER_RESET = "Reset"
internal const val TIMER_RUNNING = "Running"
internal const val TIMER_READY = "Ready"
internal const val TIMER_LAP = "Lap"
internal const val ALARM_OFF = "Turn off"
internal const val ALARM_ENABLE = "Enable alerts"
internal const val ALARM_SET = "Set alarm"
internal const val ALARM_ONCE = "One time"
internal const val ALERTS_OFF = "Background alerts are off. Enable alerts"
internal const val TIMER_DESCRIPTION = "Change timer duration"
internal const val ALARM_DESCRIPTION = "Change alarm time"
internal const val STOPWATCH_DESCRIPTION = "Stopwatch time"
internal const val PICK_HOURS = "Hours"
internal const val PICK_MINUTES = "Minutes"
internal const val PICK_SECONDS = "Seconds"
internal const val PICK_SET = "Set"
internal const val PICK_NONE = "Choose a duration"
/** The in-app alert's words (Android: HomeActivity.timingTick's dialog) and the alerts' own. */
internal const val RING_TIMER = "Time's up"
internal const val RING_ALARM = "Alarm"
internal const val RING_STOP = "Stop"
/** The timer's three presets. */
internal val TIMER_PRESETS = listOf(30_000L to "30 sec", 60_000L to "1 min", 180_000L to "3 min")

/**
 * The Timer tab (android-streetgym, ui/TimingPanel.kt): Timer, Stopwatch and Alarm, apart from any workout. The clocks
 * are kept on the phone and run on with another tab showing or the app closed ([Timing]); the screen redraws ten times
 * a second. Timer: the time in big even digits, a tap sets it; Start, Pause, Resume and Reset; three presets.
 * Stopwatch: hundredths, Lap and Reset, laps newest first. Alarm: the time on the phone's 12- or 24-hour clock, a tap
 * picks it; Set alarm or Turn off. With alerts off, a line under the Timer and Alarm asks to enable them. In landscape the
 * time and the buttons sit side by side. [onNote] shows a short note.
 */
@Composable
fun TimerScreen(model: AppModel, onNote: (String) -> Unit) {
    val timing = model.timing
    LaunchedEffect(timing) {
        timing.refreshAlerts()
        while (true) {
            timing.tick()
            delay(TIMER_TICK_MILLIS)
        }
    }
    val state = timing.state
    // Android: TimingPanel.build's landscape layout.
    val compact = isLandscape()
    var picking by remember { mutableStateOf(false) }
    Page {
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            TIMER_TABS.forEachIndexed { index, label ->
                if (index > 0) Spacer(Modifier.width(6.dp))
                TimerTab(label, index == state.tab, Modifier.weight(1f)) { if (index != state.tab) timing.selectTab(index) }
            }
        }
        TimerPanel(timing, state, compact) { picking = true }
        when (state.tab) {
            0 -> Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                TIMER_PRESETS.forEachIndexed { index, (millis, label) ->
                    if (index > 0) Spacer(Modifier.width(8.dp))
                    SecondaryButton(label, Modifier.weight(1f), enabled = !state.countdown.running, height = 48.dp, horizontalPadding = 0.dp) {
                        timing.preset(millis)
                    }
                }
            }
            1 -> if (state.stopwatch.laps.isNotEmpty()) StreetCard(Modifier.padding(top = 16.dp)) {
                state.stopwatch.laps.withIndex().reversed().forEach { (index, time) ->
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        BodyText("$TIMER_LAP ${index + 1}", 16f, Modifier.weight(1f))
                        TabularText(stopwatchText(time), TextStyle(color = Street.colors.text, fontSize = 16.sp, fontFamily = Street.type.body))
                    }
                }
            }
        }
        if (state.tab != 1 && !timing.alertsOn) {
            BodyText(ALERTS_OFF, 13f, Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(min = 48.dp)
                .clickable(role = Role.Button) { timing.enableAlerts() }.padding(horizontal = 8.dp, vertical = 12.dp))
        }
        // Android: TimingPanel's endAd.
        EndAd(model)
    }
    if (picking) when (state.tab) {
        0 -> DurationDialog(state.countdown.total, onClose = { picking = false }) { millis ->
            if (timing.setDuration(millis)) picking = false else onNote(PICK_NONE)
        }
        2 -> AlarmTimeDialog(state.alarmHour, state.alarmMinute, timing.hour24, onClose = { picking = false }) { hour, minute ->
            picking = false
            timing.setAlarmTime(hour, minute)
        }
        else -> picking = false
    }
}

private const val TIMER_TICK_MILLIS = 100L

/** One of the three tabs: a secondary button filled in the theme's colour when chosen (Android: TimingPanel.build's strip). */
@Composable
private fun TimerTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val theme = Street.theme
    val palette = theme.palette
    Box(
        modifier.height(48.dp).streetBackground(theme, if (selected) palette.primary else palette.infoSurface, 16.dp)
            .clickable(role = Role.Tab, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Tab
                this.selected = selected
            }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label.uppercase(), style = TextStyle(color = if (selected) palette.onAccent else palette.text, fontSize = 13.sp,
            fontFamily = Street.type.label, letterSpacing = if (theme == StreetTheme.WHEATPASTE) 0.04.em else 0.1.em,
            textAlign = TextAlign.Center), maxLines = 1)
    }
}

/**
 * The card with the time, the line under it and the buttons; side by side when [compact] (landscape). A tap on the
 * Timer's or the Alarm's time opens its picker ([onPick]), not while the timer runs.
 */
@Composable
private fun TimerPanel(timing: Timing, state: TimingState, compact: Boolean, onPick: () -> Unit) {
    val tab = state.tab
    val now = timing.now
    val digits: String
    val detail: String
    val primary: String
    var secondary: String? = null
    var secondaryOn = false
    val onPrimary: () -> Unit
    var onSecondary: () -> Unit = {}
    when (tab) {
        0 -> {
            val left = state.countdown.remaining(now)
            digits = clockText(left, roundUp = true)
            detail = when {
                state.countdown.running -> TIMER_COUNTING
                left == 0L -> TIMER_UP
                left != state.countdown.total -> TIMER_PAUSED
                else -> TIMER_TAP
            }
            primary = if (state.countdown.running) TIMER_PAUSE else if (left < state.countdown.total && left > 0) TIMER_RESUME else TIMER_START
            secondary = TIMER_RESET
            secondaryOn = left != state.countdown.total || state.countdown.running
            onPrimary = timing::timerPrimary
            onSecondary = timing::timerReset
        }
        1 -> {
            val elapsed = state.stopwatch.elapsed(now)
            digits = stopwatchText(elapsed)
            detail = if (state.stopwatch.running) TIMER_RUNNING else if (elapsed > 0) TIMER_PAUSED else TIMER_READY
            primary = if (state.stopwatch.running) TIMER_PAUSE else if (elapsed > 0) TIMER_RESUME else TIMER_START
            secondary = if (state.stopwatch.running) TIMER_LAP else TIMER_RESET
            secondaryOn = state.stopwatch.running || elapsed > 0
            onPrimary = timing::stopwatchPrimary
            onSecondary = timing::stopwatchSecondary
        }
        else -> {
            digits = alarmClockText(state.alarmHour, state.alarmMinute, timing.hour24)
            detail = if (state.alarmAt > 0) "${alarmDay(state.alarmAt, timing)} · $ALARM_ONCE" else TIMER_TAP
            primary = when {
                state.alarmAt > 0 -> ALARM_OFF
                !timing.alertsOn -> ALARM_ENABLE
                else -> ALARM_SET
            }
            onPrimary = timing::alarmPrimary
        }
    }
    val pickable = tab != 1 && !(tab == 0 && state.countdown.running)
    val description = when (tab) { 0 -> TIMER_DESCRIPTION; 2 -> ALARM_DESCRIPTION; else -> STOPWATCH_DESCRIPTION }
    val readout: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier) {
            ClockReadout(
                digits, if (tab == 0) 60f else 48f, description,
                Modifier.padding(top = if (compact) 4.dp else 24.dp, bottom = if (compact) 4.dp else 12.dp),
                onClick = if (tab == 1) null else ({ if (pickable) onPick() }),
            )
            BodyText(detail, 13f, Modifier.fillMaxWidth().padding(bottom = if (compact) 0.dp else 24.dp), align = TextAlign.Center)
        }
    }
    val shadow = Street.shape.shadowDp.dp
    val actions: @Composable (Modifier) -> Unit = { modifier ->
        // Not baseline-aligned: the secondary matches the primary's face, above the hard shadow.
        Row(modifier) {
            secondary?.let {
                SecondaryButton(it, Modifier.weight(1f), enabled = secondaryOn, height = 54.dp - shadow, onClick = onSecondary)
                Spacer(Modifier.width(10.dp))
            }
            PrimaryButton(primary, Modifier.weight(1f), height = 54.dp, onClick = onPrimary)
        }
    }
    StreetCard {
        if (compact) Row(verticalAlignment = Alignment.CenterVertically) {
            readout(Modifier.weight(1f))
            actions(Modifier.padding(start = 16.dp).width(240.dp))
        } else {
            readout(Modifier.fillMaxWidth())
            actions(Modifier.fillMaxWidth())
        }
    }
}

/**
 * The clock in the theme's timer face, its digits in even widths, as large as fits up to [maxSize] sp (Android: the
 * readout's auto size, 24 sp up). [onClick] makes it a button that says [description].
 */
@Composable
private fun ClockReadout(text: String, maxSize: Float, description: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val fonts = Street.theme.fonts
    val family = Street.type.timer
    val color = Street.colors.text
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(
        modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .clearAndSetSemantics {
                // Android says only what a tap does; VoiceOver also hears the time.
                contentDescription = description
                stateDescription = text
                if (onClick != null) role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        val width = constraints.maxWidth.toFloat()
        // Even digits: the size depends only on where the digits are, so it holds still as the clock runs.
        val shape = text.map { if (it.isDigit()) '0' else it }.joinToString("")
        val size = remember(shape, width, family) {
            (maxSize.toInt() downTo MIN_CLOCK_SP).firstOrNull { points ->
                tabularWidth(measurer, shape, TextStyle(fontFamily = family, fontSize = points.sp)) <= width
            } ?: MIN_CLOCK_SP
        }
        TabularText(text, TextStyle(color = color, fontSize = size.sp, fontFamily = family),
            Modifier.fontPadding(size.sp, fonts.timerTop, fonts.timerBottom))
    }
}

private const val MIN_CLOCK_SP = 24

private fun tabularWidth(measurer: androidx.compose.ui.text.TextMeasurer, text: String, style: TextStyle): Float {
    val digit = DIGITS.maxOf { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width }.toFloat()
    return text.sumOf { if (it.isDigit()) digit.toDouble() else measurer.measure(it.toString(), style, softWrap = false, maxLines = 1).size.width.toDouble() }.toFloat()
}

private val DIGITS = (0..9).map(Int::toString)

/**
 * [text] with every digit drawn in the width of the widest one, centred in it (Android: TimingPanel.tabular): the timer
 * faces have proportional figures, and a running clock would shift.
 */
@Composable
fun TabularText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val digit = remember(style) { DIGITS.maxOf { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width } }
    val layouts = text.map { measurer.measure(it.toString(), style, softWrap = false, maxLines = 1) }
    val cells = text.mapIndexed { index, char -> if (char.isDigit()) digit else layouts[index].size.width }
    val height = layouts.maxOfOrNull { it.size.height } ?: 0
    with(density) {
        Canvas(modifier.width(cells.sum().toDp()).height(height.toDp())) {
            var x = 0f
            layouts.forEachIndexed { index, layout ->
                drawText(layout, topLeft = Offset(x + (cells[index] - layout.size.width) / 2f, 0f))
                x += cells[index]
            }
        }
    }
}

/** The stopwatch's time: "01:05.42", hundredths after the seconds. */
fun stopwatchText(elapsed: Long): String = clockText(elapsed) + "." + (elapsed / 10 % 100).toString().padStart(2, '0')

/** The alarm's time as the phone writes times: "07:00" on a 24-hour clock, "7:00 AM" on a 12-hour one (Android: DateFormat.getTimeFormat). */
fun alarmClockText(hour: Int, minute: Int, hour24: Boolean): String {
    val minutes = minute.toString().padStart(2, '0')
    if (hour24) return "${hour.toString().padStart(2, '0')}:$minutes"
    val twelve = if (hour % 12 == 0) 12 else hour % 12
    return "$twelve:$minutes ${if (hour < 12) "AM" else "PM"}"
}

private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val MONTHS_SHORT = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** The alarm's day, "Wed, 5 Nov" (Android: DateFormat.format("EEE, d MMM")), on the phone's wall clock. */
fun alarmDay(alarmAt: Long, timing: Timing): String {
    val date = Instant.fromEpochMilliseconds(alarmAt).toLocalDateTime(timing.zoneNow()).date
    return "${WEEKDAYS[date.dayOfWeek.ordinal]}, ${date.day} ${MONTHS_SHORT[date.month.ordinal]}"
}

/**
 * "Timer": hours, minutes and seconds on three wheels, Cancel and Set (Android: TimingPanel.durationPicker's
 * NumberPickers). [onSet] gets the duration in milliseconds.
 */
@Composable
internal fun DurationDialog(total: Long, onClose: () -> Unit, onSet: (Long) -> Unit) {
    val seconds = total / 1_000
    var hours by remember { mutableIntStateOf((seconds / 3_600).toInt().coerceIn(0, 23)) }
    var minutes by remember { mutableIntStateOf((seconds / 60 % 60).toInt()) }
    var secs by remember { mutableIntStateOf((seconds % 60).toInt()) }
    StreetDialog(
        title = TIMER_TABS[0],
        onDismiss = onClose,
        confirm = DialogAction(PICK_SET) { onSet((hours * 3_600L + minutes * 60L + secs) * 1_000L) },
        dismiss = DialogAction(stringResource(Res.string.cancel), onClose),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            WheelColumn(PICK_HOURS, hours, 23, Modifier.weight(1f)) { hours = it }
            WheelColumn(PICK_MINUTES, minutes, 59, Modifier.weight(1f)) { minutes = it }
            WheelColumn(PICK_SECONDS, secs, 59, Modifier.weight(1f)) { secs = it }
        }
    }
}

/** Android's TimePickerDialog's words in its keyboard mode (the framework's time_picker_prompt_label). */
internal const val PICK_TYPE_TIME = "Type in time"
/** What its toggle says to a screen reader (the framework's time_picker_text_input_mode_description and radial one). */
internal const val PICK_TEXT_MODE = "Switch to text input mode for the time input."
internal const val PICK_CLOCK_MODE = "Switch to clock mode for the time input."

/**
 * The alarm's time as Android's TimePickerDialog shows it in the app's theme (Theme.Material's TimePicker, in its clock
 * mode): the time picked on a clock face, on the phone's 12- or 24-hour clock, its time beside the face in landscape;
 * the keyboard at the bottom left switches to typing the time ("Type in time") and back, as Android 8 and later offer;
 * Cancel and OK. Drawn with Material 3's TimePicker and TimeInput in the app's dialog, in the theme's colours, the
 * numbers in Roboto. [onSet] gets the hour (0 to 23) and the minute.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlarmTimeDialog(
    hour: Int,
    minute: Int,
    hour24: Boolean,
    typingFirst: Boolean = false,
    onClose: () -> Unit,
    onSet: (Int, Int) -> Unit,
) {
    val state = rememberTimePickerState(hour, minute, hour24)
    var typing by remember { mutableStateOf(typingFirst) }
    val landscape = isLandscape()
    val palette = Street.colors
    StreetDialog(
        title = null,
        onDismiss = onClose,
        confirm = DialogAction(OK) { onSet(state.hour, state.minute) },
        dismiss = DialogAction(stringResource(Res.string.cancel), onClose),
        leading = {
            StreetMaterial {
                CompositionLocalProvider(LocalContentColor provides palette.textSecondary) {
                    // Material 3 1.9.0 draws the keyboard on the clock face, as Android, but names it for the clock
                    // mode: Android's words for what the button does.
                    val words = if (typing) PICK_CLOCK_MODE else PICK_TEXT_MODE
                    TimePickerDialogDefaults.DisplayModeToggle(
                        onDisplayModeChange = { typing = !typing },
                        displayMode = if (typing) TimePickerDisplayMode.Input else TimePickerDisplayMode.Picker,
                        modifier = Modifier.clearAndSetSemantics {
                            contentDescription = words
                            role = Role.Button
                            onClick { typing = !typing; true }
                        },
                    )
                }
            }
        },
    ) {
        StreetMaterial {
            val colors = timePickerColors()
            Column(Modifier.fillMaxWidth().padding(start = 12.dp, top = 18.dp, end = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (typing) {
                    BasicText(PICK_TYPE_TIME, Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 16.dp),
                        style = TextStyle(color = palette.textSecondary, fontSize = 14.sp, fontFamily = Street.type.system))
                    TimeInput(state, colors = colors)
                } else TimePicker(state, colors = colors,
                    layoutType = if (landscape) TimePickerLayoutType.Horizontal else TimePickerLayoutType.Vertical)
            }
        }
    }
}

/**
 * The clock face in the theme's colours, as Theme.Material colours Android's: the face on the theme's pale surface, the
 * hand and the picked number in its accent (colorAccent), the time and AM/PM picked in it too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun timePickerColors(): TimePickerColors {
    val palette = Street.colors
    return TimePickerDefaults.colors(
        clockDialColor = palette.infoSurface,
        clockDialSelectedContentColor = palette.onAccent,
        clockDialUnselectedContentColor = palette.text,
        selectorColor = palette.primary,
        containerColor = palette.surface,
        periodSelectorBorderColor = palette.divider,
        periodSelectorSelectedContainerColor = palette.primary,
        periodSelectorUnselectedContainerColor = Color.Transparent,
        periodSelectorSelectedContentColor = palette.onAccent,
        periodSelectorUnselectedContentColor = palette.textSecondary,
        timeSelectorSelectedContainerColor = palette.primary,
        timeSelectorUnselectedContainerColor = palette.infoSurface,
        timeSelectorSelectedContentColor = palette.onAccent,
        timeSelectorUnselectedContentColor = palette.text,
    )
}

/**
 * Material 3 in the app's theme, for its pieces the app uses (the alarm's clock face): the theme's colours and every
 * Material type style in Roboto Regular, as Android's framework time picker draws under the themes' fontFamily "sans".
 */
@Composable
internal fun StreetMaterial(content: @Composable () -> Unit) {
    val theme = Street.theme
    val palette = theme.palette
    val system = Street.type.system
    val scheme = remember(theme) {
        if (theme.dark) darkColorScheme(
            primary = palette.primary, onPrimary = palette.onAccent, primaryContainer = palette.primary, onPrimaryContainer = palette.onAccent,
            surface = palette.surface, onSurface = palette.text, onSurfaceVariant = palette.textSecondary,
            surfaceContainerHighest = palette.infoSurface, background = palette.background, onBackground = palette.text,
            outline = palette.divider, error = palette.heart,
        ) else lightColorScheme(
            primary = palette.primary, onPrimary = palette.onAccent, primaryContainer = palette.primary, onPrimaryContainer = palette.onAccent,
            surface = palette.surface, onSurface = palette.text, onSurfaceVariant = palette.textSecondary,
            surfaceContainerHighest = palette.infoSurface, background = palette.background, onBackground = palette.text,
            outline = palette.divider, error = palette.heart,
        )
    }
    val typography = remember(system) {
        val base = Typography()
        Typography(
            displayLarge = base.displayLarge.copy(fontFamily = system, fontWeight = FontWeight.Normal), displayMedium = base.displayMedium.copy(fontFamily = system, fontWeight = FontWeight.Normal),
            displaySmall = base.displaySmall.copy(fontFamily = system, fontWeight = FontWeight.Normal), headlineLarge = base.headlineLarge.copy(fontFamily = system, fontWeight = FontWeight.Normal),
            headlineMedium = base.headlineMedium.copy(fontFamily = system, fontWeight = FontWeight.Normal), headlineSmall = base.headlineSmall.copy(fontFamily = system, fontWeight = FontWeight.Normal),
            titleLarge = base.titleLarge.copy(fontFamily = system, fontWeight = FontWeight.Normal), titleMedium = base.titleMedium.copy(fontFamily = system, fontWeight = FontWeight.Normal),
            titleSmall = base.titleSmall.copy(fontFamily = system, fontWeight = FontWeight.Normal), bodyLarge = base.bodyLarge.copy(fontFamily = system, fontWeight = FontWeight.Normal),
            bodyMedium = base.bodyMedium.copy(fontFamily = system, fontWeight = FontWeight.Normal), bodySmall = base.bodySmall.copy(fontFamily = system, fontWeight = FontWeight.Normal),
            labelLarge = base.labelLarge.copy(fontFamily = system, fontWeight = FontWeight.Normal), labelMedium = base.labelMedium.copy(fontFamily = system, fontWeight = FontWeight.Normal),
            labelSmall = base.labelSmall.copy(fontFamily = system, fontWeight = FontWeight.Normal),
        )
    }
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}

/**
 * One wheel of a picker, as Android's NumberPicker: [label] over it, the value between two rules in the theme's colour,
 * the values before and after it faded; a drag or a tap on a neighbour turns it, wrapping round unless [wrap] is off.
 * VoiceOver adjusts it by swiping up and down.
 */
@Composable
private fun WheelColumn(
    name: String,
    value: Int,
    max: Int,
    modifier: Modifier = Modifier,
    label: Boolean = true,
    min: Int = 0,
    pad: Boolean = true,
    wrap: Boolean = true,
    names: List<String>? = null,
    onChange: (Int) -> Unit,
) {
    val palette = Street.colors
    val count = max - min + 1
    fun step(by: Int): Int? {
        val next = value + by
        return when {
            next in min..max -> next
            !wrap -> null
            else -> ((next - min) % count + count) % count + min
        }
    }
    fun text(number: Int?): String = when {
        number == null -> ""
        names != null -> names[number - min]
        pad -> number.toString().padStart(2, '0')
        else -> number.toString()
    }
    val row = 44.dp
    val stepPx = with(LocalDensity.current) { row.toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (label) BodyText(name, 12f, Modifier.fillMaxWidth(), align = TextAlign.Center)
        Column(
            Modifier.fillMaxWidth()
                .pointerInput(value) {
                    detectVerticalDragGestures(onDragEnd = { drag = 0f }) { _, amount ->
                        drag += amount
                        val steps = (drag / stepPx).toInt()
                        if (steps != 0) {
                            drag -= steps * stepPx
                            step(-steps)?.let(onChange)
                        }
                    }
                }
                .semantics(mergeDescendants = true) {
                    contentDescription = name
                    stateDescription = text(value)
                    progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), min.toFloat()..max.toFloat(), count - 2)
                    setProgress { target ->
                        val next = target.roundToInt().coerceIn(min, max)
                        if (next != value) onChange(next)
                        true
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WheelValue(text(step(-1)), row, faded = true) { step(-1)?.let(onChange) }
            Box(Modifier.width(56.dp).height(2.dp).background(palette.primary))
            WheelValue(text(value), row, faded = false) {}
            Box(Modifier.width(56.dp).height(2.dp).background(palette.primary))
            WheelValue(text(step(1)), row, faded = true) { step(1)?.let(onChange) }
        }
    }
}

/** A wheel's number in Roboto, as Android's NumberPicker draws it in its dialog's system face. */
@Composable
private fun WheelValue(text: String, height: Dp, faded: Boolean, onTap: () -> Unit) {
    val palette = Street.colors
    Box(Modifier.fillMaxWidth().height(height).pointerInput(text) { detectTapGestures { onTap() } }, contentAlignment = Alignment.Center) {
        BasicText(text, style = TextStyle(color = if (faded) palette.textSecondary.copy(alpha = 0.6f) else palette.text,
            fontSize = if (faded) 16.sp else 20.sp, fontFamily = Street.type.system, textAlign = TextAlign.Center), maxLines = 1)
    }
}

/** The in-app alert while a timer or alarm rings (Android: timingTick's dialog): "Time's up" or "Alarm", and Stop. */
@Composable
fun RingingDialog(alarm: Boolean, onStop: () -> Unit) {
    StreetDialog(title = if (alarm) RING_ALARM else RING_TIMER, onDismiss = onStop, confirm = DialogAction(RING_STOP, onStop))
}
