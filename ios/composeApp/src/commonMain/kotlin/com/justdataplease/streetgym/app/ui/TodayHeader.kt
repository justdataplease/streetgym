package com.justdataplease.streetgym.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.theme_class
import com.justdataplease.streetgym.app.resources.theme_class_number
import com.justdataplease.streetgym.app.resources.theme_class_of
import com.justdataplease.streetgym.app.resources.theme_level_chip
import com.justdataplease.streetgym.app.resources.theme_level_line
import com.justdataplease.streetgym.app.theme.Street
import com.justdataplease.streetgym.app.theme.StreetTheme
import org.jetbrains.compose.resources.stringResource

/**
 * The head of Today's day card (android-streetgym, ui/ThemeDecor.kt): Concrete's tape strip and big class number,
 * Night Park's stencilled class number, Wheatpaste's pasted poster. Every theme counts the class within its level.
 */
@Composable
fun TodayHeader(levelNumber: Int, levelName: String, classInLevel: Int, levelSize: Int, modifier: Modifier = Modifier) {
    val theme = Street.theme
    val palette = theme.palette
    val type = Street.type
    val levelLine = stringResource(Res.string.theme_level_line, levelNumber, levelName)
    val spoken = stringResource(Res.string.theme_class_of, classInLevel, levelSize, levelLine)
    val classLabel = stringResource(Res.string.theme_class).uppercase()
    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken }) {
        when (theme) {
            StreetTheme.CONCRETE -> {
                // The tape runs 8 dp past the card on each side and tilts; the card clips its ends.
                BasicText(
                    text = levelLine.uppercase(),
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                        .bleed(24.dp)
                        .graphicsLayer { rotationZ = -2f }
                        .streetBackground(theme, palette.accent, 0.dp, palette.border, 2.dp)
                        .padding(start = 32.dp, top = 9.dp, end = 28.dp, bottom = 9.dp),
                    style = TextStyle(color = palette.contentOn(palette.accent), fontSize = 12.sp, fontFamily = type.label,
                        letterSpacing = 0.14.em),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    BigNumber(classInLevel.toString(), 84f, palette.text, type.numeral)
                    Column(Modifier.padding(start = 10.dp, bottom = 6.dp)) {
                        BasicText(classLabel, style = TextStyle(color = palette.text, fontSize = 11.sp, fontFamily = type.label,
                            letterSpacing = 0.16.em))
                        BigNumber("/ $levelSize", 24f, palette.textSecondary, type.numeral)
                    }
                }
            }
            StreetTheme.NIGHT_PARK -> {
                BasicText(classLabel, style = TextStyle(color = palette.textSecondary, fontSize = 11.sp, fontFamily = type.bold,
                    letterSpacing = 0.22.em))
                Row(verticalAlignment = Alignment.Bottom) {
                    BigNumber(classInLevel.toString(), 80f, palette.primary, type.numeral)
                    BigNumber("/$levelSize", 22f, palette.textSecondary, type.display, Modifier.padding(start = 6.dp, bottom = 8.dp))
                }
                BasicText(
                    text = stringResource(Res.string.theme_level_chip, levelNumber, levelName).uppercase(),
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                        .streetBackground(theme, palette.infoSurface, 12.dp)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    style = TextStyle(color = palette.text, fontSize = 14.sp, fontFamily = type.display, letterSpacing = 0.08.em),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StreetTheme.WHEATPASTE -> Column(
                Modifier.fillMaxWidth()
                    .graphicsLayer { rotationZ = -1.5f }
                    .clipToBounds()
                    .drawBehind {
                        drawRect(palette.primaryDark)
                        // A disc of dots let into the bottom corner, a quarter of it off the edge.
                        val disc = 96.dp.toPx()
                        halftone(palette.spot, 8.dp.toPx(), Offset(size.width - disc * 0.75f, size.height - disc * 2f / 3f), disc)
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Row {
                    MonoText(levelLine.uppercase(), Modifier.weight(1f))
                    MonoText("$classInLevel/$levelSize")
                }
                BasicText(
                    text = stringResource(Res.string.theme_class_number, classInLevel).uppercase(),
                    modifier = Modifier.padding(top = 8.dp),
                    style = TextStyle(color = palette.heroText, fontSize = 34.sp, fontFamily = type.display),
                )
            }
        }
    }
}

@Composable
private fun BigNumber(value: String, size: Float, color: Color, face: FontFamily, modifier: Modifier = Modifier) {
    BasicText(value, modifier, style = TextStyle(color = color, fontSize = size.sp, fontFamily = face), maxLines = 1)
}

@Composable
private fun MonoText(value: String, modifier: Modifier = Modifier) {
    BasicText(value, modifier, style = TextStyle(color = Street.colors.heroText, fontSize = 12.sp, fontFamily = Street.type.label),
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Wider than its place by [by] on each side, centred on it. */
private fun Modifier.bleed(by: Dp): Modifier = layout { measurable, constraints ->
    val width = constraints.maxWidth + by.roundToPx() * 2
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-by.roundToPx(), 0) }
}

/** Wheatpaste's halftone: dots of [color] on a [pitch] grid, filling the circle inside a square [box] at [topLeft]. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.halftone(color: Color, pitch: Float, topLeft: Offset, box: Float) {
    val centre = Offset(topLeft.x + box / 2f, topLeft.y + box / 2f)
    val radius = box / 2f
    val dot = pitch * 0.32f
    var y = topLeft.y + pitch / 2f
    while (y < topLeft.y + box) {
        var x = topLeft.x + pitch / 2f
        while (x < topLeft.x + box) {
            val dx = x - centre.x
            val dy = y - centre.y
            if (dx * dx + dy * dy <= radius * radius) drawCircle(color, dot, Offset(x, y))
            x += pitch
        }
        y += pitch
    }
}
