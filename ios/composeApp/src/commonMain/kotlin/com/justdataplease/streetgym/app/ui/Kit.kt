package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.ic_lock
import com.justdataplease.streetgym.app.resources.unlock
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.justdataplease.streetgym.app.theme.Corner
import com.justdataplease.streetgym.app.theme.Street
import com.justdataplease.streetgym.app.theme.StreetTheme
import com.justdataplease.streetgym.app.theme.Texture
import kotlin.math.min

/*
 * The pieces every screen is built from, drawn as the Android app draws them (android-streetgym, ui/UiKit.kt and
 * ui/ThemeDrawables.kt): the same sizes in dp and sp, the same colours, the same corner and shadow rules per theme.
 */

/** How far Night Park cuts a corner of a shape with corner size [radius]. */
private const val CHAMFER = 0.58f

/** A theme shape's outline: square, or with the top-left and bottom-right corners cut (Night Park). */
private fun chamferPath(left: Float, top: Float, right: Float, bottom: Float, cut: Float): Path {
    val c = min(cut, min(right - left, bottom - top) / 2f)
    return Path().apply {
        moveTo(left + c, top)
        lineTo(right, top)
        lineTo(right, bottom - c)
        lineTo(right - c, bottom)
        lineTo(left, bottom)
        lineTo(left, top + c)
        close()
    }
}

/**
 * A filled shape in the theme's corner style, with an optional outline drawn inside its edge. Night Park cuts two
 * corners in proportion to [radius]; the poster themes keep it square. [dash] above 0 dashes the outline.
 */
fun DrawScope.streetShape(
    theme: StreetTheme,
    color: Color,
    radius: Float,
    stroke: Color? = null,
    strokeWidth: Float = 0f,
    topLeft: Offset = Offset.Zero,
    size: Size = this.size,
    dash: Float = 0f,
) {
    val outlined = stroke != null && strokeWidth > 0f
    val style = Stroke(strokeWidth, pathEffect = if (dash > 0f) PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.6f)) else null)
    val half = strokeWidth / 2f
    if (theme.shape.corner == Corner.CHAMFER) {
        val cut = radius * CHAMFER
        if (color.alpha > 0f) drawPath(chamferPath(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height, cut), color)
        if (outlined) drawPath(
            chamferPath(topLeft.x + half, topLeft.y + half, topLeft.x + size.width - half, topLeft.y + size.height - half, cut),
            stroke, style = style,
        )
    } else {
        if (color.alpha > 0f) drawRect(color, topLeft, size)
        if (outlined) drawRect(stroke, Offset(topLeft.x + half, topLeft.y + half),
            Size(size.width - strokeWidth, size.height - strokeWidth), style = style)
    }
}

/** [streetShape] behind a composable. */
fun Modifier.streetBackground(
    theme: StreetTheme,
    color: Color,
    radius: Dp,
    stroke: Color? = null,
    strokeWidth: Dp = 0.dp,
    dash: Dp = 0.dp,
): Modifier = drawBehind { streetShape(theme, color, radius.toPx(), stroke, strokeWidth.toPx(), dash = dash.toPx()) }

/** The outline [streetShape] fills, for clipping what is inside a card. */
fun streetOutline(theme: StreetTheme, radius: Dp): Shape = if (theme.shape.corner == Corner.SQUARE) RectangleShape else object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(chamferPath(0f, 0f, size.width, size.height, with(density) { radius.toPx() } * CHAMFER))
}

/** The page: flat, or concrete or paper grain, tiled from a small picture of two offset dot grids. */
fun Modifier.pageBackground(theme: StreetTheme): Modifier = drawWithCache {
    val brush = when (theme.shape.texture) {
        Texture.NONE -> SolidColor(theme.palette.background)
        else -> grain(theme.palette.background, density, theme.shape.texture)
    }
    onDrawBehind { drawRect(brush) }
}

private fun grain(base: Color, density: Float, texture: Texture): Brush {
    val concrete = texture == Texture.CONCRETE
    val cell = ((if (concrete) 9f else 6f) * density).toInt().coerceAtLeast(4)
    val size = cell * 2
    val tile = ImageBitmap(size, size)
    val canvas = Canvas(tile)
    val paint = Paint().apply { isAntiAlias = true }
    paint.color = base
    canvas.drawRect(Rect(0f, 0f, size.toFloat(), size.toFloat()), paint)
    val radius = 0.7f * density
    paint.color = Color(18, 18, 18, if (concrete) 20 else 13)
    for (x in 0 until 2) for (y in 0 until 2) canvas.drawCircle(Offset(x * cell + cell * 0.3f, y * cell + cell * 0.4f), radius, paint)
    if (concrete) {
        paint.color = Color(255, 255, 255, 90)
        canvas.drawCircle(Offset(cell * 0.85f, cell * 1.2f), radius, paint)
        canvas.drawCircle(Offset(cell * 1.7f, cell * 0.15f), radius, paint)
        paint.color = Color(18, 18, 18, 14)
        canvas.drawCircle(Offset(cell * 1.35f, cell * 1.75f), radius * 1.3f, paint)
    }
    return ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
}

/** A card: the theme's surface, outline and corners, 16 dp of padding, and what is inside clipped to it. */
@Composable
fun StreetCard(
    modifier: Modifier = Modifier,
    fill: Color = Street.colors.surface,
    outlined: Boolean = true,
    padding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = Street.theme
    Column(
        modifier.fillMaxWidth()
            .clip(streetOutline(theme, 24.dp))
            .streetBackground(theme, fill, 24.dp, theme.palette.border, if (outlined) theme.shape.borderDp.dp else 0.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** The room Android leaves over and under a text of [size] in the display face. */
@Composable
fun Modifier.displayPadding(size: TextUnit): Modifier {
    val fonts = Street.theme.fonts
    return fontPadding(size, fonts.displayTop, fonts.displayBottom)
}

/** The room Android leaves over and under a text of [size] in a face whose padding is [top] and [bottom], in em. */
@Composable
fun Modifier.fontPadding(size: TextUnit, top: Float, bottom: Float): Modifier = with(LocalDensity.current) {
    padding(top = (size * top).toDp().coerceAtLeast(0.dp), bottom = (size * bottom).toDp().coerceAtLeast(0.dp))
}

/** A headline's face: the display face, the numeral face (Night Park's stencil) or the countdown face. */
enum class TitleFace { DISPLAY, NUMERAL, TIMER }

/** A headline in the theme's display face; the poster themes that shout set it in capitals. */
@Composable
fun TitleText(
    text: String,
    size: Float = 22f,
    modifier: Modifier = Modifier,
    color: Color = Street.colors.text,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign = TextAlign.Unspecified,
    face: TitleFace = TitleFace.DISPLAY,
) {
    val shape = Street.shape
    val fonts = Street.theme.fonts
    val type = Street.type
    val fontSize = (size * shape.titleScale).sp
    val family = when (face) {
        TitleFace.DISPLAY -> type.display
        TitleFace.NUMERAL -> type.numeral
        TitleFace.TIMER -> type.timer
    }
    val timer = face == TitleFace.TIMER
    BasicText(
        text = if (shape.upperTitles) text.uppercase() else text,
        modifier = modifier.fontPadding(fontSize, if (timer) fonts.timerTop else fonts.displayTop,
            if (timer) fonts.timerBottom else fonts.displayBottom),
        style = TextStyle(color = color, fontSize = fontSize, fontFamily = family, textAlign = align),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Running text in the body face. */
@Composable
fun BodyText(
    text: String,
    size: Float = 15f,
    modifier: Modifier = Modifier,
    color: Color = Street.colors.textSecondary,
    bold: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign = TextAlign.Unspecified,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(color = color, fontSize = size.sp, lineHeight = BODY_LINE.em,
            fontFamily = if (bold) Street.type.bold else Street.type.body, textAlign = align),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Running text with its own styles, such as Android's `<b>` in a string ([boldMarkup]). */
@Composable
fun BodyText(
    text: AnnotatedString,
    size: Float = 15f,
    modifier: Modifier = Modifier,
    color: Color = Street.colors.textSecondary,
    align: TextAlign = TextAlign.Unspecified,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(color = color, fontSize = size.sp, lineHeight = BODY_LINE.em, fontFamily = Street.type.body, textAlign = align),
    )
}

/**
 * Android's `<b>…</b>` in a string, which the build keeps as written (composeApp/build.gradle.kts), as bold runs of
 * [bold] in plain text; any other text is unchanged.
 */
fun boldMarkup(text: String, bold: SpanStyle): AnnotatedString = buildAnnotatedString {
    var rest = text
    while (true) {
        val open = rest.indexOf("<b>")
        val close = if (open < 0) -1 else rest.indexOf("</b>", open + 3)
        if (open < 0 || close < 0) {
            append(rest)
            break
        }
        append(rest.substring(0, open))
        withStyle(bold) { append(rest.substring(open + 3, close)) }
        rest = rest.substring(close + 4)
    }
}

/**
 * A quiet way out, as underlined bold text in the theme's text colour, centred and at least 48 dp tall: skipping the
 * strength test, or starting from scratch (Android: PlacementView.linkButton).
 */
@Composable
fun LinkButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = TextStyle(color = Street.colors.text, fontSize = 14.sp, fontFamily = Street.type.bold,
            textDecoration = TextDecoration.Underline, textAlign = TextAlign.Center))
    }
}

/** The body face's own line height (1.2 em) with Android's 1.12 line spacing on top. */
private const val BODY_LINE = 1.344f

/** A section's name over its content: small capitals in the label face. [padding] above and below it. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, padding: Dp = 8.dp, align: TextAlign = TextAlign.Unspecified) {
    val theme = Street.theme
    BasicText(
        text = text.uppercase(),
        modifier = modifier.padding(vertical = padding),
        style = TextStyle(
            color = if (theme == StreetTheme.NIGHT_PARK) theme.palette.primary else theme.palette.text,
            fontSize = 13.sp, fontFamily = Street.type.label, letterSpacing = theme.shape.labelSpacing.em, textAlign = align,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** A small filled label: a tier, a count. */
@Composable
fun Badge(text: String, modifier: Modifier = Modifier, color: Color = Street.colors.primary) {
    val theme = Street.theme
    BasicText(
        text = text.uppercase(),
        modifier = modifier.streetBackground(theme, color, 12.dp).padding(horizontal = 9.dp, vertical = 4.dp),
        style = TextStyle(color = theme.palette.onAccent, fontSize = 11.sp, fontFamily = Street.type.label,
            letterSpacing = 0.06.em, textAlign = TextAlign.Center),
        maxLines = 1,
    )
}

/**
 * A number in a filled box: an exercise's place in the class, a set's in the exercise, a level's number (Android:
 * numberCircle). [fill] and [color] change on Wheatpaste's blue poster.
 */
@Composable
fun NumberBox(
    number: String,
    box: Dp,
    textSize: Float,
    modifier: Modifier = Modifier,
    face: FontFamily = Street.type.numeral,
    radius: Dp = box * 0.55f,
    fill: Color = Street.colors.primary,
    color: Color = Street.colors.onAccent,
) {
    val theme = Street.theme
    Box(modifier.size(box).streetBackground(theme, fill, radius), contentAlignment = Alignment.Center) {
        BasicText(number, style = TextStyle(color = color, fontSize = textSize.sp, fontFamily = face), maxLines = 1)
    }
}

/**
 * The tag of a tier the athlete doesn't have: Android's badge in the secondary text colour with the lock before the
 * tier's name (Android: lockBadge). [fill] and [color] change on Wheatpaste's blue poster.
 */
@Composable
fun LockBadge(
    text: String,
    modifier: Modifier = Modifier,
    fill: Color = Street.colors.textSecondary,
    color: Color = Street.colors.onAccent,
    textSize: Float = 11f,
    padding: PaddingValues = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
    rim: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val theme = Street.theme
    Row(
        modifier.streetBackground(theme, fill, 12.dp, rim, if (rim != null) 2.dp else 0.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = text }
            .padding(padding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(Res.drawable.ic_lock), contentDescription = null, colorFilter = ColorFilter.tint(color),
            modifier = Modifier.size(12.dp))
        BasicText(
            text = text.uppercase(),
            modifier = Modifier.padding(start = 4.dp),
            style = TextStyle(color = color, fontSize = textSize.sp, fontFamily = Street.type.label, letterSpacing = 0.06.em,
                textAlign = TextAlign.Center),
            maxLines = 1,
        )
    }
}

/** Room above Unlock for its tilted sticker, and to its right (Android: STICKER_ROOM and STICKER_SIDE). */
private val STICKER_ROOM = 6.dp
private val STICKER_SIDE = 3.dp

/**
 * "Unlock" on the main button, as wide as its row, with the tier's tag stuck on its top-right corner, tilted like a
 * sticker (Android: unlockButton and tierButton, fill). Both open the offer.
 */
@Composable
fun UnlockButton(tierName: String, modifier: Modifier = Modifier, onClick: () -> Unit) =
    TierButton(stringResource(Res.string.unlock), tierName, modifier, onClick)

/**
 * [text] on the main button with the tier's tag stuck on its corner, like Unlock: My workouts' Create a workout on Free
 * (Android: tierButton). Both open the offer.
 */
@Composable
fun TierButton(text: String, tierName: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.fillMaxWidth()) {
        PrimaryButton(
            text,
            // The tilt lifts the tag's left corner above its box: the room above keeps the sticker whole.
            Modifier.padding(top = STICKER_ROOM + 12.dp, end = 14.dp + STICKER_SIDE),
            description = "$text $tierName",
            onClick = onClick,
        )
        LockBadge(
            tierName,
            Modifier.align(Alignment.TopEnd).padding(top = STICKER_ROOM, end = STICKER_SIDE)
                .graphicsLayer { rotationZ = 6f }.clearAndSetSemantics {},
            onClick = onClick,
        )
    }
}

/**
 * A switch row of Settings (Android: settingSwitch, the platform Material switch in the theme's accent): the label at
 * 15 sp, the switch on the right, at least 56 dp tall; a tap anywhere flips it.
 */
@Composable
fun SettingSwitch(label: String, checked: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    val theme = Street.theme
    val palette = theme.palette
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, Modifier.weight(1f), style = TextStyle(color = palette.text, fontSize = 15.sp, fontFamily = Street.type.body))
        SwitchMark(checked, Modifier.padding(start = 12.dp))
    }
}

/**
 * Material's switch as Android draws it in the theme's accent: a 34 x 14 dp track, half see-through, and a 20 dp thumb
 * in the accent when on, pale when off, in a 48 x 40 dp box. It only draws; whatever holds it takes the tap.
 */
@Composable
fun SwitchMark(checked: Boolean, modifier: Modifier = Modifier) {
    val theme = Street.theme
    val accent = theme.palette.primary
    val track = if (checked) accent.copy(alpha = 0.5f) else (if (theme.dark) Color.White else Color.Black).copy(alpha = 0.3f)
    val thumb = if (checked) accent else if (theme.dark) Color(0xFFBDBDBD) else Color(0xFFF1F1F1)
    Canvas(modifier.size(48.dp, 40.dp)) {
        val trackWidth = 34.dp.toPx()
        val trackHeight = 14.dp.toPx()
        val left = (size.width - trackWidth) / 2f
        val middle = size.height / 2f
        drawRoundRect(track, Offset(left, middle - trackHeight / 2f), Size(trackWidth, trackHeight),
            androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f))
        val radius = 10.dp.toPx()
        val centre = Offset(if (checked) left + trackWidth - radius + 3.dp.toPx() else left + radius - 3.dp.toPx(), middle)
        // The thumb's soft shadow, as Material lifts it.
        drawCircle(Color.Black.copy(alpha = 0.18f), radius + 0.8.dp.toPx(), centre + Offset(0f, 1.dp.toPx()))
        drawCircle(thumb, radius, centre)
    }
}

/** A switch on its own, at the end of a row whose other part does something else (Android: a Switch beside a tappable row). */
@Composable
fun SwitchToggle(checked: Boolean, description: String, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    SwitchMark(
        checked,
        modifier.toggleable(checked, role = Role.Switch, onValueChange = onChange).semantics { contentDescription = description },
    )
}

/**
 * The main action: the display face in capitals on the action colour. Concrete outlines the button in ink, and both
 * poster themes print a hard shadow down and right of it. [thumb]: the focus workout's (Android: FocusWorkoutView.thumb),
 * 16 sp on one line, at least 64 dp tall, the label centred on the whole button with its shadow. [icon] sits at the
 * start, as Android's compound drawable does, with the label centred in the rest.
 */
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Street.colors.action,
    enabled: Boolean = true,
    thumb: Boolean = false,
    icon: Painter? = null,
    description: String? = null,
    onClick: () -> Unit,
) {
    val theme = Street.theme
    val palette = theme.palette
    val offset = theme.shape.shadowDp.dp
    val shadow = if (theme == StreetTheme.WHEATPASTE) palette.spot else palette.border
    val padding = if (thumb) PaddingValues(horizontal = 12.dp)
        else PaddingValues(start = 18.dp, top = 12.dp, end = 18.dp + offset, bottom = 12.dp + offset)
    Row(
        modifier.fillMaxWidth().heightIn(min = if (thumb) 64.dp else 52.dp)
            .drawBehind {
                val o = offset.toPx()
                val face = Size(size.width - o, size.height - o)
                if (o > 0f) streetShape(theme, shadow, 16.dp.toPx(), topLeft = Offset(o, o), size = face)
                if (theme == StreetTheme.CONCRETE) streetShape(theme, color, 16.dp.toPx(), palette.border, 2.dp.toPx(), size = face)
                else streetShape(theme, color, 16.dp.toPx(), size = face)
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(padding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
        }
        val fontSize = when {
            thumb -> 16.sp
            theme == StreetTheme.CONCRETE -> 19.sp
            theme == StreetTheme.NIGHT_PARK -> 20.sp
            else -> 15.sp
        }
        BasicText(
            text = text.uppercase(),
            modifier = Modifier.weight(1f).displayPadding(fontSize),
            style = TextStyle(
                color = palette.contentOn(color),
                fontSize = fontSize,
                fontFamily = Street.type.display,
                letterSpacing = if (theme == StreetTheme.WHEATPASTE) 0.02.em else 0.05.em,
                textAlign = TextAlign.Center,
            ),
            maxLines = if (thumb) 1 else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A second choice beside the main action: the label face in capitals, outlined, dashed in Wheatpaste. [side]: the
 * focus workout's Back, Skip and +15 s (Android: FocusWorkoutView.sideButton), as wide as the label, 56 dp tall.
 */
@Composable
fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    side: Boolean = false,
    description: String? = null,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val theme = Street.theme
    val palette = theme.palette
    val face = when (theme) {
        StreetTheme.CONCRETE -> Modifier.streetBackground(theme, palette.surface, 16.dp, palette.border, 2.dp)
        StreetTheme.WHEATPASTE -> Modifier.streetBackground(theme, Color.Transparent, 16.dp, palette.border, 2.dp, dash = 7.dp)
        StreetTheme.NIGHT_PARK -> Modifier.streetBackground(theme, palette.infoSurface, 16.dp)
    }
    // [compact]: the builder's Up, Down, Sets and Remove, 44 dp tall with 10 dp at the sides (Android: renderEditDay).
    val size = when {
        compact -> Modifier.height(44.dp)
        side -> Modifier.heightIn(min = 56.dp)
        else -> Modifier.fillMaxWidth().heightIn(min = 52.dp)
    }
    Box(
        modifier.then(size).then(face)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(horizontal = if (compact) 10.dp else if (side) 14.dp else 18.dp, vertical = if (side || compact) 0.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text.uppercase(),
            style = TextStyle(color = palette.text, fontSize = 13.sp, fontFamily = Street.type.label,
                letterSpacing = if (theme == StreetTheme.WHEATPASTE) 0.04.em else 0.1.em, textAlign = TextAlign.Center),
            maxLines = if (side || compact) 1 else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A plain action in words: bold, in the theme's primary colour, at least 40 dp tall (Android: FocusWorkoutView.textAction). */
@Composable
fun TextAction(text: String, modifier: Modifier = Modifier, description: String? = null, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 40.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = TextStyle(color = Street.colors.primary, fontSize = 14.sp, fontFamily = Street.type.bold,
            textAlign = TextAlign.Center), maxLines = 1)
    }
}

/**
 * A small filled choice: the easier option of an exercise (Android: easierChip and easierToggle). [on] fills it in the
 * primary colour.
 */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    on: Boolean = false,
    maxLines: Int = 1,
    description: String? = null,
    onClick: () -> Unit,
) {
    val theme = Street.theme
    val palette = theme.palette
    BasicText(
        text = text,
        modifier = modifier.streetBackground(theme, if (on) palette.primary else palette.infoSurface, 16.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        style = TextStyle(color = if (on) palette.onAccent else palette.primary, fontSize = 13.sp, fontFamily = Street.type.bold),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * How much of something is done, as a 4 dp bar centred in [height] (Android: horizontalProgress). The track is the
 * info colour at Material's disabled alpha, as Android's progress drawable draws it (0.26 light, 0.3 dark: measured on
 * the emulator, levels.png). [track] and [bar] change on Wheatpaste's blue poster.
 */
@Composable
fun ProgressBar(
    percent: Int,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    track: Color = Street.colors.infoSurface.copy(alpha = if (Street.theme.dark) 0.3f else 0.26f),
    bar: Color = Street.colors.primary,
) {
    Box(modifier.fillMaxWidth().height(height).drawBehind {
        val thick = 4.dp.toPx()
        val top = (size.height - thick) / 2f
        drawRect(track, Offset(0f, top), Size(size.width, thick))
        drawRect(bar, Offset(0f, top), Size(size.width * percent.coerceIn(0, 100) / 100f, thick))
    })
}

/** [part] of [whole] in whole percent, 0 to 100 (Android: percentage). */
fun percentage(part: Int, whole: Int): Int = if (whole <= 0) 0 else (part * 100 / whole).coerceIn(0, 100)

/**
 * Android's check box (the platform Material one, tinted): an 18 dp rounded square, a 2 dp frame when unticked and
 * filled with the tick cut out when ticked, 16 dp in from the left of its [box] (48 dp in a row, 32 dp in a dialog's
 * list) as Android draws its 32 dp button there. It only draws; the row around it takes the tap, as Android's rows pass
 * theirs to the box.
 */
@Composable
fun CheckMark(checked: Boolean, tint: Color, modifier: Modifier = Modifier, box: Dp = 48.dp) {
    Box(modifier.size(box).drawBehind {
        val unit = 1.dp.toPx()
        val center = Offset(16.dp.toPx(), size.height / 2f)
        fun point(x: Float, y: Float) = Offset(center.x + x * unit, center.y + y * unit)
        val corner = androidx.compose.ui.geometry.CornerRadius(2f * unit)
        val box = androidx.compose.ui.geometry.RoundRect(point(-9f, -9f).x, point(-9f, -9f).y, point(9f, 9f).x, point(9f, 9f).y, corner)
        val path = Path().apply {
            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
            addRoundRect(box)
            if (checked) {
                // The tick, cut out of the box (btn_checkbox_checked_mtrl).
                moveTo(point(-2f, 5f).x, point(-2f, 5f).y)
                lineTo(point(-7f, 0f).x, point(-7f, 0f).y)
                lineTo(point(-5.586f, -1.414f).x, point(-5.586f, -1.414f).y)
                lineTo(point(-2f, 2.172f).x, point(-2f, 2.172f).y)
                lineTo(point(5.586f, -5.414f).x, point(5.586f, -5.414f).y)
                lineTo(point(7f, -4f).x, point(7f, -4f).y)
                close()
            } else {
                addRect(Rect(point(-7f, -7f), point(7f, 7f)))
            }
        }
        drawPath(path, tint)
    })
}

/**
 * A row of options with one selected: Easier, Normal, Harder; the tabs of Training and Progress. [compact] narrows
 * each option, as Training's four tabs are (Android: segmentedSwitch).
 */
@Composable
fun SegmentedSwitch(labels: List<String>, selected: Int, modifier: Modifier = Modifier, compact: Boolean = false, onSelect: (Int) -> Unit) {
    val theme = Street.theme
    val palette = theme.palette
    Row(modifier.streetBackground(theme, palette.infoSurface, 14.dp).padding(3.dp)) {
        labels.forEachIndexed { index, label ->
            val on = index == selected
            BasicText(
                text = label,
                modifier = Modifier
                    .then(if (on) Modifier.streetBackground(theme, palette.surface, 11.dp) else Modifier)
                    .selectable(on, role = Role.Tab) { if (!on) onSelect(index) }
                    .padding(horizontal = if (compact) 11.dp else 18.dp, vertical = 8.dp),
                style = TextStyle(color = if (on) palette.primary else palette.textSecondary, fontSize = 14.sp,
                    fontFamily = Street.type.bold, textAlign = TextAlign.Center),
                maxLines = 1,
            )
        }
    }
}
