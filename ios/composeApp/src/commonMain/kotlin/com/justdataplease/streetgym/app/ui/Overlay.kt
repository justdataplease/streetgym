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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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
 * Wheatpaste's ink outline or Night Park's round corners, 95% of the screen wide, or 65% in landscape (Theme.Material's
 * windowMinWidthMinor and windowMinWidthMajor); the title, the message, any [content], then the buttons in words in the
 * theme's colour, [neutral] (or [leading], drawn in its place) at the start, [dismiss] and [confirm] at the end. As on
 * Android the dialog's own words are in the system face, Roboto Regular (the app's themes set fontFamily "sans", which
 * outranks Theme.Material's medium title and buttons); what [content] draws keeps the face it picks.
 * [cancelable] false keeps it up when the dim is tapped (Android: setCancelable(false)).
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
    leading: (@Composable () -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Modal {
        val theme = Street.theme
        val palette = theme.palette
        val system = Street.type.system
        Scrim(if (cancelable) onDismiss else ({}), Alignment.Center) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(LocalSafeArea.current), contentAlignment = Alignment.Center) {
                val card = if (theme == StreetTheme.NIGHT_PARK) Modifier.clip(RoundedCornerShape(24.dp)).background(palette.surface)
                    else Modifier.background(palette.surface).border(2.dp, palette.border)
                // Android measures the share against the whole screen; the safe area's sides are a few points of it.
                val share = if (maxWidth > maxHeight) LANDSCAPE_DIALOG else PORTRAIT_DIALOG
                Column(
                    Modifier.fillMaxWidth(share).heightIn(max = maxHeight - 32.dp).then(card).keepTaps().padding(8.dp),
                ) {
                    if (title != null) BasicText(
                        title,
                        Modifier.padding(start = 24.dp, top = 18.dp, end = 24.dp, bottom = 8.dp),
                        style = TextStyle(color = palette.text, fontSize = 20.sp, fontFamily = system),
                    )
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        if (message != null) BasicText(
                            message,
                            Modifier.padding(start = 24.dp, top = if (title == null) 18.dp else 4.dp, end = 24.dp, bottom = 8.dp),
                            style = TextStyle(color = palette.textSecondary, fontSize = 16.sp, fontFamily = system),
                        )
                        content?.invoke(this)
                    }
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        if (leading != null) leading() else neutral?.let { DialogButton(it) }
                        Spacer(Modifier.weight(1f))
                        dismiss?.let { DialogButton(it) }
                        DialogButton(confirm)
                    }
                }
            }
        }
    }
}

/** How much of the screen's width an AlertDialog takes (Theme.Material.Dialog: windowMinWidthMinor 95%, windowMinWidthMajor 65%). */
private const val PORTRAIT_DIALOG = 0.95f
private const val LANDSCAPE_DIALOG = 0.65f

/** A dialog's button: its label in capitals, Roboto Regular 14 sp, in the theme's colour (Theme.Material's ButtonBar.AlertDialog). */
@Composable
private fun DialogButton(action: DialogAction) {
    Box(
        Modifier.widthIn(min = 64.dp).heightIn(min = 48.dp).clickable(role = Role.Button, onClick = action.onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(action.label.uppercase(), style = TextStyle(color = Street.colors.primary, fontSize = 14.sp,
            fontFamily = Street.type.system, textAlign = TextAlign.Center), maxLines = 1)
    }
}

/** A row of a single-choice list in a dialog: the radio ring, filled when [selected], then [label] in Roboto, as Android's list item. */
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
            fontFamily = Street.type.system), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A row of a multiple-choice list in a dialog (Android: setMultiChoiceItems): the check box, ticked when [checked],
 * then [label] in Roboto, which may run over two lines.
 */
@Composable
fun CheckRow(label: String, checked: Boolean, enabled: Boolean = true, size: Float = 18f, onChange: (Boolean) -> Unit) {
    val palette = Street.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).alpha(if (enabled) 1f else 0.7f)
            .toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)
            .padding(start = 20.dp, end = 24.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckMark(checked, if (checked) palette.primary else palette.textSecondary, box = 32.dp)
        BasicText(label, Modifier.padding(start = 20.dp), style = TextStyle(color = palette.text, fontSize = size.sp,
            fontFamily = Street.type.system))
    }
}

/**
 * A number of up to three digits in Roboto, centred over a rule in the theme's colour, with [hint] while it is empty; it
 * takes the focus, so the phone shows its number pad (Android: askSetDone's EditText, in its dialog's system face).
 */
@Composable
fun NumberField(value: TextFieldValue, hint: String, modifier: Modifier = Modifier, onChange: (TextFieldValue) -> Unit) {
    val palette = Street.colors
    val focus = remember { FocusRequester() }
    val style = TextStyle(color = palette.text, fontSize = 18.sp, fontFamily = Street.type.system, textAlign = TextAlign.Center)
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
 * A line of text to type, as Android's EditText draws it: [hint] while empty, a rule under it in the secondary colour,
 * thicker and in the theme's colour while it has the focus, and [error] under it (Android: setError). [maxLength] caps
 * it; [digits] takes digits only, on the number keyboard; [capitalize] starts sentences in capitals; [focusFirst] takes
 * the focus when it shows. [imeAction] runs [onAction]. [family]: the theme's body face on a screen, Roboto in a dialog
 * (Android restyles a screen's fields, never a dialog's).
 */
@Composable
fun EntryField(
    value: TextFieldValue,
    hint: String,
    modifier: Modifier = Modifier,
    size: Float = 18f,
    maxLength: Int = Int.MAX_VALUE,
    digits: Boolean = false,
    capitalize: Boolean = false,
    error: String? = null,
    focusFirst: Boolean = false,
    imeAction: ImeAction = ImeAction.Default,
    onAction: () -> Unit = {},
    family: FontFamily = Street.type.body,
    onChange: (TextFieldValue) -> Unit,
) {
    val palette = Street.colors
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val style = TextStyle(color = palette.text, fontSize = size.sp, fontFamily = family)
    Column(modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = { next -> if (next.text.length <= maxLength && (!digits || next.text.all(Char::isDigit))) onChange(next) },
            modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = hint },
            textStyle = style,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = if (capitalize) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
                keyboardType = if (digits) KeyboardType.Number else KeyboardType.Text,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(onAny = { onAction() }),
            cursorBrush = SolidColor(palette.primary),
            decorationBox = { field ->
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).drawBehind {
                        val rule = (if (focused) 2 else 1).dp.toPx()
                        drawRect(if (focused) palette.primary else palette.textSecondary, Offset(0f, this.size.height - rule - 4.dp.toPx()),
                            androidx.compose.ui.geometry.Size(this.size.width, rule))
                    }.padding(start = 4.dp, top = 10.dp, end = 4.dp, bottom = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.text.isEmpty()) BasicText(hint, Modifier.fillMaxWidth(), style = style.copy(color = palette.textSecondary),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    field()
                }
            },
        )
        if (error != null) BasicText(error, Modifier.padding(start = 4.dp, top = 2.dp),
            style = TextStyle(color = palette.heart, fontSize = 13.sp, fontFamily = family))
    }
    if (focusFirst) LaunchedEffect(focus) { runCatching { focus.requestFocus() } }
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
 * taps. Drawn in the theme's text colour with the surface's on it, so it stands out in every theme; its words in
 * Roboto, as Android's toast is in the system face.
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
            style = TextStyle(color = palette.surface, fontSize = 14.sp, fontFamily = Street.type.system, textAlign = TextAlign.Center),
        )
    }
}
