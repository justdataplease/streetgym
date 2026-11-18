package com.justdataplease.streetgym.app.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.albert_sans_bold
import com.justdataplease.streetgym.app.resources.albert_sans_extrabold
import com.justdataplease.streetgym.app.resources.albert_sans_regular
import com.justdataplease.streetgym.app.resources.anton_regular
import com.justdataplease.streetgym.app.resources.archivo_black_regular
import com.justdataplease.streetgym.app.resources.big_shoulders_black
import com.justdataplease.streetgym.app.resources.big_shoulders_stencil_black
import com.justdataplease.streetgym.app.resources.space_mono_bold
import com.justdataplease.streetgym.app.resources.theme_concrete
import com.justdataplease.streetgym.app.resources.theme_concrete_summary
import com.justdataplease.streetgym.app.resources.theme_night_park
import com.justdataplease.streetgym.app.resources.theme_night_park_summary
import com.justdataplease.streetgym.app.resources.theme_wheatpaste
import com.justdataplease.streetgym.app.resources.theme_wheatpaste_summary
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.FontResource
import org.jetbrains.compose.resources.StringResource

/*
 * The three looks, as the Android app defines them (android-streetgym, ui/StreetTheme.kt). Keep the names and numbers
 * the same on both sides: a colour changed there is changed here.
 */

/** How corners are cut: Concrete & Tape and Wheatpaste keep them square, Night Park chamfers them. */
enum class Corner { SQUARE, CHAMFER }

/** The page background: flat, or the fine grain of concrete or poster paper. */
enum class Texture { NONE, CONCRETE, PAPER }

/**
 * A theme's colours. [ink] is the dark text put on light fills; [spot] only decorates (shadows, halftone, highlights);
 * [palm] colours the movement guides' hands.
 */
class Palette(
    val primary: Color,
    val primaryDark: Color,
    val background: Color,
    val surface: Color,
    val text: Color,
    val textSecondary: Color,
    val divider: Color,
    val infoSurface: Color,
    val accent: Color,
    val onAccent: Color,
    val action: Color,
    val success: Color,
    val warningSurface: Color,
    val border: Color,
    val ink: Color,
    val spot: Color,
    val heroText: Color,
    val heroKicker: Color,
    val mark: Color,
    val heart: Color,
    val palm: Color,
) {
    /** White or the theme's ink, whichever reads better on [background]. */
    fun contentOn(background: Color): Color =
        if (contrast(Color.White, background) >= contrast(ink, background)) Color.White else ink
}

/** WCAG contrast ratio between two colours, 1 to 21. */
fun contrast(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/** [color] moved [amount] (0..1) of the way to [towards], channel by channel, opaque. */
fun blendColor(color: Color, towards: Color, amount: Float): Color {
    val keep = 1f - amount
    return Color(
        red = (color.red * keep + towards.red * amount).coerceIn(0f, 1f),
        green = (color.green * keep + towards.green * amount).coerceIn(0f, 1f),
        blue = (color.blue * keep + towards.blue * amount).coerceIn(0f, 1f),
    )
}

/** Shapes and type treatment: card outline and the hard offset shadow under primary buttons, in dp. */
class ThemeShape(
    val corner: Corner,
    val borderDp: Int,
    val shadowDp: Int,
    val dashedSecondary: Boolean,
    val upperTitles: Boolean,
    val titleScale: Float,
    val labelSpacing: Float,
    val texture: Texture,
)

/**
 * A theme's font files. [displayTop] and [displayBottom]: the room Android leaves over and under a text in the display
 * face (its font padding, in em, from the font file), which the screens' spacing counts on.
 */
class ThemeFonts(
    val body: FontResource,
    val bold: FontResource,
    val display: FontResource,
    val numeral: FontResource,
    val label: FontResource,
    val timer: FontResource,
    val displayTop: Float,
    val displayBottom: Float,
)

/** The three looks picked in Settings, all free. Concrete & Tape is the default and Night Park the dark one. */
enum class StreetTheme(
    val key: String,
    val dark: Boolean,
    val label: StringResource,
    val summary: StringResource,
    val palette: Palette,
    val shape: ThemeShape,
    val fonts: ThemeFonts,
) {
    CONCRETE(
        "concrete", false, Res.string.theme_concrete, Res.string.theme_concrete_summary,
        Palette(
            primary = Color(0xFF121212), primaryDark = Color(0xFF1B1B1A), background = Color(0xFFD9D7D2),
            surface = Color(0xFFF5F4F0), text = Color(0xFF121212), textSecondary = Color(0xFF4A4945),
            divider = Color(0xFF9C9992), infoSurface = Color(0xFFE9E7E2), accent = Color(0xFFFFD60A),
            onAccent = Color(0xFFF5F4F0), action = Color(0xFFFFD60A), success = Color(0xFF121212),
            warningSurface = Color(0xFFFFF1A6), border = Color(0xFF121212), ink = Color(0xFF121212),
            spot = Color(0xFFFFD60A), heroText = Color(0xFFF5F4F0), heroKicker = Color(0xFFFFD60A),
            mark = Color(0xFF121212), heart = Color(0xFFD7263D), palm = Color(0xFFFFD60A),
        ),
        ThemeShape(Corner.SQUARE, borderDp = 2, shadowDp = 4, dashedSecondary = false, upperTitles = true,
            titleScale = 1.15f, labelSpacing = 0.14f, texture = Texture.CONCRETE),
        ThemeFonts(Res.font.albert_sans_regular, Res.font.albert_sans_bold, Res.font.anton_regular, Res.font.anton_regular,
            Res.font.albert_sans_extrabold, Res.font.anton_regular, displayTop = 0.227f, displayBottom = 0f),
    ),
    NIGHT_PARK(
        "night_park", true, Res.string.theme_night_park, Res.string.theme_night_park_summary,
        Palette(
            primary = Color(0xFFFF8A2A), primaryDark = Color(0xFF1B1D21), background = Color(0xFF0F1012),
            surface = Color(0xFF17191C), text = Color(0xFFF2EEE6), textSecondary = Color(0xFF9A9EA5),
            divider = Color(0xFF2A2D31), infoSurface = Color(0xFF22252A), accent = Color(0xFFFF8A2A),
            onAccent = Color(0xFF140B03), action = Color(0xFFFF8A2A), success = Color(0xFFFF8A2A),
            warningSurface = Color(0xFF33281C), border = Color(0xFF2A2D31), ink = Color(0xFF140B03),
            spot = Color(0xFFFF8A2A), heroText = Color(0xFFF2EEE6), heroKicker = Color(0xFFFF8A2A),
            mark = Color(0xFFFF8A2A), heart = Color(0xFFFF7D8F), palm = Color(0xFFFF8A2A),
        ),
        ThemeShape(Corner.CHAMFER, borderDp = 0, shadowDp = 0, dashedSecondary = false, upperTitles = true,
            titleScale = 1.15f, labelSpacing = 0.2f, texture = Texture.NONE),
        ThemeFonts(Res.font.albert_sans_regular, Res.font.albert_sans_bold, Res.font.big_shoulders_black,
            Res.font.big_shoulders_stencil_black, Res.font.albert_sans_bold, Res.font.big_shoulders_stencil_black,
            displayTop = 0.24f, displayBottom = 0.015f),
    ),
    WHEATPASTE(
        "wheatpaste", false, Res.string.theme_wheatpaste, Res.string.theme_wheatpaste_summary,
        Palette(
            primary = Color(0xFF1F4FD1), primaryDark = Color(0xFF1F4FD1), background = Color(0xFFF1ECE2),
            surface = Color(0xFFFBF8F2), text = Color(0xFF1C1B1A), textSecondary = Color(0xFF5B5650),
            divider = Color(0xFFC9C1B3), infoSurface = Color(0xFFE8E1D4), accent = Color(0xFFFF4FA0),
            onAccent = Color(0xFFFFFFFF), action = Color(0xFF1C1B1A), success = Color(0xFF1F4FD1),
            warningSurface = Color(0xFFFFE1EE), border = Color(0xFF1C1B1A), ink = Color(0xFF1C1B1A),
            spot = Color(0xFFFF4FA0), heroText = Color(0xFFFFFFFF), heroKicker = Color(0xFFFFFFFF),
            mark = Color(0xFF1F4FD1), heart = Color(0xFFE0245E), palm = Color(0xFF1F4FD1),
        ),
        ThemeShape(Corner.SQUARE, borderDp = 2, shadowDp = 5, dashedSecondary = true, upperTitles = false,
            titleScale = 0.95f, labelSpacing = 0.06f, texture = Texture.PAPER),
        ThemeFonts(Res.font.albert_sans_regular, Res.font.albert_sans_bold, Res.font.archivo_black_regular,
            Res.font.archivo_black_regular, Res.font.space_mono_bold, Res.font.space_mono_bold,
            displayTop = 0.079f, displayBottom = 0.057f),
    );

    companion object {
        val DEFAULT = CONCRETE

        /** Looks that were dropped open as the one they were a copy of. */
        private val RETIRED = mapOf("concrete_trees" to "concrete", "green_park" to "night_park")

        fun fromKey(key: String?): StreetTheme? = (RETIRED[key] ?: key).let { found -> entries.firstOrNull { it.key == found } }
    }
}

/** The active theme's faces, loaded. */
class StreetType(
    val body: FontFamily,
    val bold: FontFamily,
    val display: FontFamily,
    val numeral: FontFamily,
    val label: FontFamily,
    val timer: FontFamily,
)

@Composable
fun streetType(theme: StreetTheme): StreetType {
    val fonts = theme.fonts
    val body = Font(fonts.body)
    val bold = Font(fonts.bold)
    val display = Font(fonts.display)
    val numeral = Font(fonts.numeral)
    val label = Font(fonts.label)
    val timer = Font(fonts.timer)
    return remember(body, bold, display, numeral, label, timer) {
        StreetType(FontFamily(body), FontFamily(bold), FontFamily(display), FontFamily(numeral), FontFamily(label), FontFamily(timer))
    }
}

val LocalStreetTheme = staticCompositionLocalOf { StreetTheme.DEFAULT }
val LocalStreetType = staticCompositionLocalOf<StreetType> { error("StreetType is set by StreetGymTheme") }

/** The active theme, where a screen is drawn. */
object Street {
    val theme: StreetTheme @Composable @ReadOnlyComposable get() = LocalStreetTheme.current
    val colors: Palette @Composable @ReadOnlyComposable get() = LocalStreetTheme.current.palette
    val shape: ThemeShape @Composable @ReadOnlyComposable get() = LocalStreetTheme.current.shape
    val type: StreetType @Composable @ReadOnlyComposable get() = LocalStreetType.current
}
