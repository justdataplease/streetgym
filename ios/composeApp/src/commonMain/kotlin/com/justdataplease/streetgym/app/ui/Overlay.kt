package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justdataplease.streetgym.app.theme.Street
import com.justdataplease.streetgym.app.theme.StreetTheme
import kotlinx.coroutines.delay

/*
 * What covers the whole screen, the title bar and the safe areas included: Android's AlertDialogs in the app's theme
 * (styles.xml, StreetDialog.*), the focus workout's bottom sheet, and the short note (Toast) after an action.
 */

/** The layers over the screen; [Shell] draws them over everything, and a screen adds one with [Modal]. */
@Stable
class Overlay {
    internal val layers = mutableStateListOf<Layer>()

    /** A dialog or sheet is up: the screen behind it is hidden from VoiceOver, as behind a system dialog. */
    val shown: Boolean get() = layers.isNotEmpty()
}

internal class Layer(val content: @Composable () -> Unit)

val LocalOverlay = staticCompositionLocalOf { Overlay() }

/** The room the phone needs around the screen (the Dynamic Island, the home indicator), for what the overlay draws. */
val LocalSafeArea = staticCompositionLocalOf { PaddingValues() }

/** Shows [content] over the whole screen for as long as this is in the composition. */
@Composable
fun Modal(content: @Composable () -> Unit) {
    val overlay = LocalOverlay.current
    val latest = rememberUpdatedState(content)
    DisposableEffect(overlay) {
        val layer = Layer { latest.value() }
        overlay.layers.add(layer)
        onDispose { overlay.layers.remove(layer) }
    }
}

/** The overlay's layers, the latest on top. */
@Composable
internal fun OverlayLayers(overlay: Overlay) {
    overlay.layers.forEach { layer -> key(layer) { layer.content() } }
}

/** Android dims the screen behind a dialog to 60%; a tap on the dimmed part closes it. */
private const val DIM = 0.6f

@Composable
private fun Scrim(onDismiss: () -> Unit, alignment: Alignment, content: @Composable BoxScope.() -> Unit) {
    val dismiss = rememberUpdatedState(onDismiss)
    // Plain taps, not a button: VoiceOver should not offer the dim as something to press.
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = DIM))
            .pointerInput(Unit) { detectTapGestures { dismiss.value() } },
        contentAlignment = alignment,
        content = content,
    )
}

/** Takes the taps on a dialog's own area, so they don't reach the dim behind it and close it. */
private fun Modifier.keepTaps(): Modifier = pointerInput(Unit) { detectTapGestures { } }

/** One of a dialog's buttons: its label and what it does. */
class DialogAction(val label: String, val onClick: () -> Unit)

/**
 * A question over the screen, as Android's AlertDialog shows it in the app's theme: the surface with Concrete's and
 * Wheatpaste's ink outline or Night Park's round corners, 95% of the screen wide; the title, the message, any [content],
 * then the buttons in words in the theme's colour, [neutral] at the start, [dismiss] and [confirm] at the end.
 * Android draws its dialogs in the system face; here they keep the app's own. [cancelable] false keeps it up when the
 * dim is tapped (Android: setCancelable(false)).
 */
@Composable
fun StreetDialog(
    title: String?,
    onDismiss: () -> Unit,
    confirm: DialogAction,
    dismiss: DialogAction? = null,
    neutral: DialogAction? = null,
    message: String? = null,
    cancelable: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Modal {
        val theme = Street.theme
        val palette = theme.palette
        Scrim(if (cancelable) onDismiss else ({}), Alignment.Center) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(LocalSafeArea.current), contentAlignment = Alignment.Center) {
                val card = if (theme == StreetTheme.NIGHT_PARK) Modifier.clip(RoundedCornerShape(24.dp)).background(palette.surface)
                    else Modifier.background(palette.surface).border(2.dp, palette.border)
                Column(
                    Modifier.fillMaxWidth(0.95f).heightIn(max = maxHeight - 32.dp).then(card).keepTaps().padding(8.dp),
                ) {
                    if (title != null) BasicText(
                        title,
                        Modifier.padding(start = 24.dp, top = 18.dp, end = 24.dp, bottom = 8.dp),
                        style = TextStyle(color = palette.text, fontSize = 20.sp, fontFamily = Street.type.bold),
                    )
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        if (message != null) BasicText(
                            message,
                            Modifier.padding(start = 24.dp, top = if (title == null) 18.dp else 4.dp, end = 24.dp, bottom = 8.dp),
                            style = TextStyle(color = palette.textSecondary, fontSize = 16.sp, fontFamily = Street.type.body),
                        )
                        content?.invoke(this)
                    }
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        neutral?.let { DialogButton(it) }
                        Spacer(Modifier.weight(1f))
                        dismiss?.let { DialogButton(it) }
                        DialogButton(confirm)
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogButton(action: DialogAction) {
    Box(
        Modifier.widthIn(min = 64.dp).heightIn(min = 48.dp).clickable(role = Role.Button, onClick = action.onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(action.label.uppercase(), style = TextStyle(color = Street.colors.primary, fontSize = 14.sp,
            fontFamily = Street.type.bold, textAlign = TextAlign.Center), maxLines = 1)
    }
}

/** A row of a single-choice list in a dialog: the radio ring, filled when [selected], then [label]. */
@Composable
fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    val palette = Street.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, role = Role.RadioButton, onClick = onSelect)
            .padding(start = 20.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ring = if (selected) palette.primary else palette.textSecondary
        Canvas(Modifier.size(32.dp)) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            drawCircle(ring, radius = 9.dp.toPx(), center = centre, style = Stroke(2.dp.toPx()))
            if (selected) drawCircle(ring, radius = 5.dp.toPx(), center = centre)
        }
        BasicText(label, Modifier.padding(start = 20.dp), style = TextStyle(color = palette.text, fontSize = 18.sp,
            fontFamily = Street.type.body), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A row of a multiple-choice list in a dialog (Android: setMultiChoiceItems): the check box, ticked when [checked],
 * then [label], which may run over two lines.
 */
@Composable
fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val palette = Street.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Checkbox, onValueChange = onChange)
            .padding(start = 20.dp, end = 24.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckMark(checked, if (checked) palette.primary else palette.textSecondary, box = 32.dp)
        BasicText(label, Modifier.padding(start = 20.dp), style = TextStyle(color = palette.text, fontSize = 18.sp,
            fontFamily = Street.type.body))
    }
}

/**
 * A number of up to three digits, centred over a rule in the theme's colour, with [hint] while it is empty; it takes
 * the focus, so the phone shows its number pad (Android: askSetDone's EditText).
 */
@Composable
fun NumberField(value: TextFieldValue, hint: String, modifier: Modifier = Modifier, onChange: (TextFieldValue) -> Unit) {
    val palette = Street.colors
    val focus = remember { FocusRequester() }
    val style = TextStyle(color = palette.text, fontSize = 18.sp, fontFamily = Street.type.body, textAlign = TextAlign.Center)
    BasicTextField(
        value = value,
        onValueChange = { next -> if (next.text.length <= 3 && next.text.all(Char::isDigit)) onChange(next) },
        modifier = modifier.fillMaxWidth().focusRequester(focus),
        textStyle = style,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        cursorBrush = SolidColor(palette.primary),
        decorationBox = { field ->
            Box(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).drawBehind {
                    val rule = 2.dp.toPx()
                    drawRect(palette.primary, Offset(0f, size.height - rule - 4.dp.toPx()),
                        androidx.compose.ui.geometry.Size(size.width, rule))
                }.padding(top = 10.dp, bottom = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (value.text.isEmpty()) BasicText(hint, Modifier.fillMaxWidth(), style = style.copy(color = palette.textSecondary))
                field()
            }
        },
    )
    LaunchedEffect(focus) { runCatching { focus.requestFocus() } }
}

/**
 * A sheet from the bottom over the dimmed screen, full width on the surface colour, reaching under the home indicator
 * (Android: a Dialog at the bottom, FocusWorkoutView.showDetails).
 */
@Composable
fun BottomSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Modal {
        val safe = LocalSafeArea.current
        Scrim(onDismiss, Alignment.BottomCenter) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(top = safe.calculateTopPadding()), contentAlignment = Alignment.BottomCenter) {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = maxHeight).background(Street.colors.surface).keepTaps()
                        .padding(bottom = safe.calculateBottomPadding()),
                    content = content,
                )
            }
        }
    }
}

/** How long a note stays, as Android's Toast.LENGTH_SHORT. */
private const val NOTE_MILLIS = 2_000L

/**
 * A short note at the bottom of the screen after an action, gone after two seconds (Android: Toast). It takes no
 * taps. Drawn in the theme's text colour with the surface's on it, so it stands out in every theme.
 */
@Composable
fun Note(text: String, onGone: () -> Unit) {
    val theme = Street.theme
    val palette = theme.palette
    val latest = rememberUpdatedState(onGone)
    LaunchedEffect(text) {
        delay(NOTE_MILLIS)
        latest.value()
    }
    Box(Modifier.fillMaxSize().padding(LocalSafeArea.current).padding(start = 24.dp, end = 24.dp, bottom = 64.dp),
        contentAlignment = Alignment.BottomCenter) {
        BasicText(
            text,
            Modifier.streetBackground(theme, palette.text, 24.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            style = TextStyle(color = palette.surface, fontSize = 14.sp, fontFamily = Street.type.body, textAlign = TextAlign.Center),
        )
    }
}
