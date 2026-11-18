package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = Street.theme
    Column(
        modifier.fillMaxWidth()
            .clip(streetOutline(theme, 24.dp))
            .streetBackground(theme, fill, 24.dp, theme.palette.border, if (outlined) theme.shape.borderDp.dp else 0.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        content = content,
    )
}

/** The room Android leaves over and under a text of [size] in the display face. */
@Composable
fun Modifier.displayPadding(size: TextUnit): Modifier {
    val fonts = Street.theme.fonts
    return with(LocalDensity.current) {
        padding(top = (size * fonts.displayTop).toDp().coerceAtLeast(0.dp), bottom = (size * fonts.displayBottom).toDp().coerceAtLeast(0.dp))
    }
}

/** A headline in the theme's display face; the poster themes that shout set it in capitals. */
@Composable
fun TitleText(
    text: String,
    size: Float = 22f,
    modifier: Modifier = Modifier,
    color: Color = Street.colors.text,
    maxLines: Int = Int.MAX_VALUE,
) {
    val shape = Street.shape
    val fontSize = (size * shape.titleScale).sp
    BasicText(
        text = if (shape.upperTitles) text.uppercase() else text,
        modifier = modifier.displayPadding(fontSize),
        style = TextStyle(color = color, fontSize = fontSize, fontFamily = Street.type.display),
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
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(color = color, fontSize = size.sp, lineHeight = BODY_LINE.em,
            fontFamily = if (bold) Street.type.bold else Street.type.body),
    )
}

/** The body face's own line height (1.2 em) with Android's 1.12 line spacing on top. */
private const val BODY_LINE = 1.344f

/** A section's name over its content: small capitals in the label face. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val theme = Street.theme
    BasicText(
        text = text.uppercase(),
        modifier = modifier.padding(vertical = 8.dp),
        style = TextStyle(
            color = if (theme == StreetTheme.NIGHT_PARK) theme.palette.primary else theme.palette.text,
            fontSize = 13.sp, fontFamily = Street.type.label, letterSpacing = theme.shape.labelSpacing.em,
        ),
        maxLines = 1,
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

/** A number in a filled box: an exercise's place in the class, a set's in the exercise. */
@Composable
fun NumberBox(number: String, box: Dp, textSize: Float, modifier: Modifier = Modifier, face: FontFamily = Street.type.numeral) {
    val theme = Street.theme
    Box(modifier.size(box).streetBackground(theme, theme.palette.primary, box * 0.55f), contentAlignment = Alignment.Center) {
        BasicText(number, style = TextStyle(color = theme.palette.onAccent, fontSize = textSize.sp, fontFamily = face), maxLines = 1)
    }
}

/**
 * The main action: the display face in capitals on the action colour. Concrete outlines the button in ink, and both
 * poster themes print a hard shadow down and right of it.
 */
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Street.colors.action,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val theme = Street.theme
    val palette = theme.palette
    val offset = theme.shape.shadowDp.dp
    val shadow = if (theme == StreetTheme.WHEATPASTE) palette.spot else palette.border
    Box(
        modifier.fillMaxWidth().heightIn(min = 52.dp)
            .drawBehind {
                val o = offset.toPx()
                val face = Size(size.width - o, size.height - o)
                if (o > 0f) streetShape(theme, shadow, 16.dp.toPx(), topLeft = Offset(o, o), size = face)
                if (theme == StreetTheme.CONCRETE) streetShape(theme, color, 16.dp.toPx(), palette.border, 2.dp.toPx(), size = face)
                else streetShape(theme, color, 16.dp.toPx(), size = face)
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(start = 18.dp, top = 12.dp, end = 18.dp + offset, bottom = 12.dp + offset),
        contentAlignment = Alignment.Center,
    ) {
        val fontSize = when (theme) { StreetTheme.CONCRETE -> 19.sp; StreetTheme.NIGHT_PARK -> 20.sp; StreetTheme.WHEATPASTE -> 15.sp }
        BasicText(
            text = text.uppercase(),
            modifier = Modifier.displayPadding(fontSize),
            style = TextStyle(
                color = palette.contentOn(color),
                fontSize = fontSize,
                fontFamily = Street.type.display,
                letterSpacing = if (theme == StreetTheme.WHEATPASTE) 0.02.em else 0.05.em,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

/** A second choice beside the main action: the label face in capitals, outlined, dashed in Wheatpaste. */
@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val theme = Street.theme
    val palette = theme.palette
    val face = when (theme) {
        StreetTheme.CONCRETE -> Modifier.streetBackground(theme, palette.surface, 16.dp, palette.border, 2.dp)
        StreetTheme.WHEATPASTE -> Modifier.streetBackground(theme, Color.Transparent, 16.dp, palette.border, 2.dp, dash = 7.dp)
        StreetTheme.NIGHT_PARK -> Modifier.streetBackground(theme, palette.infoSurface, 16.dp)
    }
    Box(
        modifier.fillMaxWidth().heightIn(min = 52.dp).then(face)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text.uppercase(),
            style = TextStyle(color = palette.text, fontSize = 13.sp, fontFamily = Street.type.label,
                letterSpacing = if (theme == StreetTheme.WHEATPASTE) 0.04.em else 0.1.em, textAlign = TextAlign.Center),
        )
    }
}

/** How much of something is done, as a bar. */
@Composable
fun ProgressBar(percent: Int, modifier: Modifier = Modifier) {
    val palette = Street.colors
    Box(modifier.fillMaxWidth().height(8.dp).drawBehind {
        val bar = 4.dp.toPx()
        val top = (size.height - bar) / 2f
        drawRect(palette.infoSurface, Offset(0f, top), Size(size.width, bar))
        drawRect(palette.primary, Offset(0f, top), Size(size.width * percent.coerceIn(0, 100) / 100f, bar))
    })
}

/** A row of options with one selected: Easier, Normal, Harder; the tabs of Training and Progress. */
@Composable
fun SegmentedSwitch(labels: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val theme = Street.theme
    val palette = theme.palette
    Row(modifier.streetBackground(theme, palette.infoSurface, 14.dp).padding(3.dp)) {
        labels.forEachIndexed { index, label ->
            val on = index == selected
            BasicText(
                text = label,
                modifier = Modifier
                    .then(if (on) Modifier.streetBackground(theme, palette.surface, 11.dp) else Modifier)
                    .clickable(role = Role.Tab) { if (!on) onSelect(index) }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                style = TextStyle(color = if (on) palette.primary else palette.textSecondary, fontSize = 14.sp,
                    fontFamily = Street.type.bold, textAlign = TextAlign.Center),
                maxLines = 1,
            )
        }
    }
}
