package com.justdataplease.streetgym.ui

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Typeface
import android.widget.TextView
import com.justdataplease.streetgym.R

/** How corners are cut: Fresh Court rounds them, both Concrete looks and Wheatpaste keep them square, Night Park chamfers them. */
enum class Corner { ROUND, SQUARE, CHAMFER }

/** The page background: flat, or the fine grain of concrete or poster paper. */
enum class Texture { NONE, CONCRETE, PAPER }

/** The colours behind [AppColors]. [ink] is the dark text put on light fills; [spot] only decorates (shadows, halftone, highlights). */
class Palette(
    val primary: Int,
    val primaryDark: Int,
    val background: Int,
    val surface: Int,
    val text: Int,
    val textSecondary: Int,
    val divider: Int,
    val infoSurface: Int,
    val accent: Int,
    val onAccent: Int,
    val action: Int,
    val success: Int,
    val warningSurface: Int,
    val border: Int,
    val ink: Int,
    val spot: Int,
    val heroText: Int,
    val heroKicker: Int,
    val mark: Int,
    val heart: Int,
)

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

/** Font files under assets/fonts; null keeps the system face, as Fresh Court always has. */
class ThemeFonts(
    val body: String?,
    val bold: String?,
    val display: String?,
    val numeral: String?,
    val label: String?,
    val timer: String?,
)

private const val ALBERT = "albert_sans_regular.ttf"
private const val ALBERT_BOLD = "albert_sans_bold.ttf"
private const val ALBERT_EXTRABOLD = "albert_sans_extrabold.ttf"

/**
 * The five looks picked in Settings, all free. Fresh Court is the default; Night Park is the only dark one. Concrete &
 * Trees is Concrete & Tape in the brand greens (the launcher icon's deep green and mint), so it shares its decor.
 */
enum class StreetTheme(
    val key: String,
    val dark: Boolean,
    val label: Int,
    val summary: Int,
    val style: Int,
    val palette: Palette,
    val shape: ThemeShape,
    val fonts: ThemeFonts,
) {
    CONCRETE(
        "concrete", false, R.string.theme_concrete, R.string.theme_concrete_summary, R.style.Theme_StreetConcrete,
        Palette(
            primary = 0xFF121212.toInt(), primaryDark = 0xFF1B1B1A.toInt(), background = 0xFFD9D7D2.toInt(),
            surface = 0xFFF5F4F0.toInt(), text = 0xFF121212.toInt(), textSecondary = 0xFF4A4945.toInt(),
            divider = 0xFF9C9992.toInt(), infoSurface = 0xFFE9E7E2.toInt(), accent = 0xFFFFD60A.toInt(),
            onAccent = 0xFFF5F4F0.toInt(), action = 0xFFFFD60A.toInt(), success = 0xFF121212.toInt(),
            warningSurface = 0xFFFFF1A6.toInt(), border = 0xFF121212.toInt(), ink = 0xFF121212.toInt(),
            spot = 0xFFFFD60A.toInt(), heroText = 0xFFF5F4F0.toInt(), heroKicker = 0xFFFFD60A.toInt(),
            mark = 0xFF121212.toInt(), heart = 0xFFD7263D.toInt(),
        ),
        ThemeShape(Corner.SQUARE, borderDp = 2, shadowDp = 4, dashedSecondary = false, upperTitles = true,
            titleScale = 1.15f, labelSpacing = 0.14f, texture = Texture.CONCRETE),
        ThemeFonts(ALBERT, ALBERT_BOLD, "anton_regular.ttf", "anton_regular.ttf", ALBERT_EXTRABOLD, "anton_regular.ttf"),
    ),
    CONCRETE_TREES(
        "concrete_trees", false, R.string.theme_concrete_trees, R.string.theme_concrete_trees_summary, R.style.Theme_StreetTrees,
        Palette(
            primary = 0xFF1E7056.toInt(), primaryDark = 0xFF193F31.toInt(), background = 0xFFD9D7D2.toInt(),
            surface = 0xFFF5F4F0.toInt(), text = 0xFF121212.toInt(), textSecondary = 0xFF4A4945.toInt(),
            divider = 0xFF9C9992.toInt(), infoSurface = 0xFFE9E7E2.toInt(), accent = 0xFF69DFB4.toInt(),
            onAccent = 0xFFF5F4F0.toInt(), action = 0xFF69DFB4.toInt(), success = 0xFF1E7056.toInt(),
            warningSurface = 0xFFD5F3E6.toInt(), border = 0xFF121212.toInt(), ink = 0xFF121212.toInt(),
            spot = 0xFF69DFB4.toInt(), heroText = 0xFFF5F4F0.toInt(), heroKicker = 0xFF69DFB4.toInt(),
            mark = 0xFF1E7056.toInt(), heart = 0xFFD7263D.toInt(),
        ),
        ThemeShape(Corner.SQUARE, borderDp = 2, shadowDp = 4, dashedSecondary = false, upperTitles = true,
            titleScale = 1.15f, labelSpacing = 0.14f, texture = Texture.CONCRETE),
        ThemeFonts(ALBERT, ALBERT_BOLD, "anton_regular.ttf", "anton_regular.ttf", ALBERT_EXTRABOLD, "anton_regular.ttf"),
    ),
    NIGHT_PARK(
        "night_park", true, R.string.theme_night_park, R.string.theme_night_park_summary, R.style.Theme_StreetNight,
        Palette(
            primary = 0xFFFF8A2A.toInt(), primaryDark = 0xFF1B1D21.toInt(), background = 0xFF0F1012.toInt(),
            surface = 0xFF17191C.toInt(), text = 0xFFF2EEE6.toInt(), textSecondary = 0xFF9A9EA5.toInt(),
            divider = 0xFF2A2D31.toInt(), infoSurface = 0xFF22252A.toInt(), accent = 0xFFFF8A2A.toInt(),
            onAccent = 0xFF140B03.toInt(), action = 0xFFFF8A2A.toInt(), success = 0xFFFF8A2A.toInt(),
            warningSurface = 0xFF33281C.toInt(), border = 0xFF2A2D31.toInt(), ink = 0xFF140B03.toInt(),
            spot = 0xFFFF8A2A.toInt(), heroText = 0xFFF2EEE6.toInt(), heroKicker = 0xFFFF8A2A.toInt(),
            mark = 0xFFFF8A2A.toInt(), heart = 0xFFFF7D8F.toInt(),
        ),
        ThemeShape(Corner.CHAMFER, borderDp = 0, shadowDp = 0, dashedSecondary = false, upperTitles = true,
            titleScale = 1.15f, labelSpacing = 0.2f, texture = Texture.NONE),
        ThemeFonts(ALBERT, ALBERT_BOLD, "big_shoulders_black.ttf", "big_shoulders_stencil_black.ttf", ALBERT_BOLD,
            "big_shoulders_stencil_black.ttf"),
    ),
    WHEATPASTE(
        "wheatpaste", false, R.string.theme_wheatpaste, R.string.theme_wheatpaste_summary, R.style.Theme_StreetPaste,
        Palette(
            primary = 0xFF1F4FD1.toInt(), primaryDark = 0xFF1F4FD1.toInt(), background = 0xFFF1ECE2.toInt(),
            surface = 0xFFFBF8F2.toInt(), text = 0xFF1C1B1A.toInt(), textSecondary = 0xFF5B5650.toInt(),
            divider = 0xFFC9C1B3.toInt(), infoSurface = 0xFFE8E1D4.toInt(), accent = 0xFFFF4FA0.toInt(),
            onAccent = 0xFFFFFFFF.toInt(), action = 0xFF1C1B1A.toInt(), success = 0xFF1F4FD1.toInt(),
            warningSurface = 0xFFFFE1EE.toInt(), border = 0xFF1C1B1A.toInt(), ink = 0xFF1C1B1A.toInt(),
            spot = 0xFFFF4FA0.toInt(), heroText = 0xFFFFFFFF.toInt(), heroKicker = 0xFFFFFFFF.toInt(),
            mark = 0xFF1F4FD1.toInt(), heart = 0xFFE0245E.toInt(),
        ),
        ThemeShape(Corner.SQUARE, borderDp = 2, shadowDp = 5, dashedSecondary = true, upperTitles = false,
            titleScale = 0.95f, labelSpacing = 0.06f, texture = Texture.PAPER),
        ThemeFonts(ALBERT, ALBERT_BOLD, "archivo_black_regular.ttf", "archivo_black_regular.ttf", "space_mono_bold.ttf",
            "space_mono_bold.ttf"),
    ),
    FRESH_COURT(
        "fresh_court", false, R.string.theme_fresh_court, R.string.theme_fresh_court_summary, R.style.Theme_StreetGym,
        Palette(
            primary = 0xFF1E7056.toInt(), primaryDark = 0xFF193F31.toInt(), background = 0xFFF4F7F3.toInt(),
            surface = 0xFFFFFFFF.toInt(), text = 0xFF182A22.toInt(), textSecondary = 0xFF66756C.toInt(),
            divider = 0xFFE0E7E0.toInt(), infoSurface = 0xFFE3F2E9.toInt(), accent = 0xFF69DFB4.toInt(),
            onAccent = 0xFFFFFFFF.toInt(), action = 0xFF1E7056.toInt(), success = 0xFF1E7056.toInt(),
            warningSurface = 0xFFFFF0D9.toInt(), border = 0xFFE0E7E0.toInt(), ink = 0xFF182A22.toInt(),
            spot = 0xFF69DFB4.toInt(), heroText = 0xFFFFFFFF.toInt(), heroKicker = 0xFF69DFB4.toInt(),
            mark = 0xFF1E7056.toInt(), heart = 0xFFE0455A.toInt(),
        ),
        ThemeShape(Corner.ROUND, borderDp = 1, shadowDp = 0, dashedSecondary = false, upperTitles = false,
            titleScale = 1f, labelSpacing = 0.08f, texture = Texture.NONE),
        ThemeFonts(null, null, null, null, null, null),
    );

    /** Both Concrete looks: the same tape, boxes and outlined buttons. */
    val concrete: Boolean get() = this == CONCRETE || this == CONCRETE_TREES

    companion object {
        val DEFAULT = FRESH_COURT
        fun fromKey(key: String?): StreetTheme? = entries.firstOrNull { it.key == key }
    }
}

/** The active theme's faces. Fresh Court keeps the system font, so it gets nothing from here. */
object AppType {
    private var assets: AssetManager? = null
    private val cache = HashMap<String, Typeface>()
    private val systemRegular by lazy {
        setOf(Typeface.DEFAULT, Typeface.SANS_SERIF, Typeface.create("sans", Typeface.NORMAL),
            Typeface.create("sans-serif", Typeface.NORMAL))
    }
    private val systemBold by lazy {
        setOf(Typeface.DEFAULT_BOLD, Typeface.create(Typeface.DEFAULT, Typeface.BOLD),
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD), Typeface.create("sans", Typeface.BOLD),
            Typeface.create("sans-serif", Typeface.BOLD))
    }

    fun init(context: Context) { assets = context.applicationContext.assets }

    private val fonts get() = AppColors.theme.fonts

    /** True when the theme has its own faces. */
    val custom: Boolean get() = fonts.body != null

    val body: Typeface get() = of(AppColors.theme, fonts.body, Typeface.DEFAULT)
    val bold: Typeface get() = of(AppColors.theme, fonts.bold, Typeface.DEFAULT_BOLD)
    val display: Typeface get() = display(AppColors.theme)
    val numeral: Typeface get() = of(AppColors.theme, fonts.numeral, Typeface.DEFAULT_BOLD)
    val label: Typeface get() = of(AppColors.theme, fonts.label, Typeface.DEFAULT_BOLD)

    /** Countdown digits; [fallback] is what the screen used before themes. */
    fun timer(fallback: Typeface): Typeface = of(AppColors.theme, fonts.timer, fallback)

    /** Any theme's headline face, for the Settings picker that shows each name in its own type. */
    fun display(theme: StreetTheme): Typeface = of(theme, theme.fonts.display, Typeface.DEFAULT_BOLD)

    private fun of(theme: StreetTheme, file: String?, fallback: Typeface): Typeface {
        val manager = assets ?: return fallback
        if (file == null || theme.fonts.body == null) return fallback
        return cache.getOrPut(file) { runCatching { Typeface.createFromAsset(manager, "fonts/$file") }.getOrDefault(fallback) }
    }

    /** Swaps a system face a view kept for the theme's body or bold face; set faces (display, timers) stay. */
    fun restyle(view: TextView) {
        if (!custom) return
        val face = view.typeface
        when {
            face == null || face in systemRegular -> view.typeface = body
            face in systemBold -> view.typeface = bold
        }
    }
}
