package com.justdataplease.streetgym.ui

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Space
import android.widget.TextView
import com.justdataplease.streetgym.R
import kotlin.math.roundToInt

/** The active [StreetTheme]'s colours, set once per activity before any view is built. */
object AppColors {
    var theme: StreetTheme = StreetTheme.DEFAULT
        private set
    val darkMode: Boolean get() = theme.dark
    fun configure(theme: StreetTheme) { this.theme = theme }
    private val palette get() = theme.palette
    val PRIMARY: Int get() = palette.primary
    val PRIMARY_DARK: Int get() = palette.primaryDark
    val BACKGROUND: Int get() = palette.background
    val SURFACE: Int get() = palette.surface
    val TEXT: Int get() = palette.text
    val TEXT_SECONDARY: Int get() = palette.textSecondary
    val DIVIDER: Int get() = palette.divider
    val INFO_SURFACE: Int get() = palette.infoSurface
    val ACCENT: Int get() = palette.accent
    val NUMBER get() = PRIMARY
    val NUMBER_DARK get() = PRIMARY
    val ACTION get() = palette.action
    val ON_ACCENT get() = palette.onAccent
    val SUCCESS get() = palette.success
    val WARNING_SURFACE get() = palette.warningSurface
    /** Card outlines: ink in the poster themes. */
    val BORDER get() = palette.border
    /** Decoration only (offset shadows, halftone, highlights); never text. */
    val SPOT get() = palette.spot
    val HERO_TEXT get() = palette.heroText
    val HERO_KICKER get() = palette.heroKicker
    val MARK get() = palette.mark
    /** The filled heart on a saved day. */
    val HEART get() = palette.heart
    /** The line figure's hands in the movement guides: tape yellow in Concrete, each theme's light colour elsewhere. */
    val PALM get() = palette.palm

    /** White or the theme's ink, whichever reads better on [background]. */
    fun contentOn(background: Int): Int =
        if (contrast(Color.WHITE, background) >= contrast(palette.ink, background)) Color.WHITE else palette.ink

    /** WCAG contrast ratio between two colours, 1 to 21. */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(color: Int): Double {
        fun channel(value: Int): Double = (value / 255.0).let { if (it <= 0.03928) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
        return 0.2126 * channel(Color.red(color)) + 0.7152 * channel(Color.green(color)) + 0.0722 * channel(Color.blue(color))
    }
}

private val themeShape get() = AppColors.theme.shape

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

/** [color] moved [amount] (0..1) of the way to [towards], opaque. */
fun blendColor(color: Int, towards: Int, amount: Float): Int {
    val keep = 1f - amount
    return Color.rgb(
        (Color.red(color) * keep + Color.red(towards) * amount).roundToInt().coerceIn(0, 255),
        (Color.green(color) * keep + Color.green(towards) * amount).roundToInt().coerceIn(0, 255),
        (Color.blue(color) * keep + Color.blue(towards) * amount).roundToInt().coerceIn(0, 255),
    )
}

/** False when the system's animator duration scale is off; animated views then show a still frame. */
fun Context.systemAnimationsEnabled(): Boolean = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ValueAnimator.areAnimatorsEnabled()
    } else {
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
} catch (_: RuntimeException) {
    true
}

/**
 * A filled shape in the theme's corner style: Night Park cuts two corners in proportion to [radiusPx], and the
 * poster themes keep it square.
 */
fun roundedDrawable(
    color: Int,
    radiusPx: Float,
    strokeColor: Int? = null,
    strokeWidthPx: Int = 0,
): Drawable = when (themeShape.corner) {
    Corner.CHAMFER -> ChamferDrawable(color, radiusPx * 0.58f, strokeColor, strokeWidthPx)
    Corner.SQUARE -> gradient(color, 0f, strokeColor, strokeWidthPx)
}

private fun gradient(color: Int, radiusPx: Float, strokeColor: Int?, strokeWidthPx: Int, dashPx: Float = 0f) =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radiusPx
        if (strokeColor != null && strokeWidthPx > 0) setStroke(strokeWidthPx, strokeColor, dashPx, dashPx * 0.6f)
    }

/** A square outline of dashes, for what is still to come in the poster themes. */
fun Context.dashedDrawable(fill: Int, stroke: Int): Drawable = gradient(fill, 0f, stroke, dp(2), dp(7).toFloat())

/** [face] over a hard copy of itself in [shadowColor], [offsetPx] down and right, the poster themes' printed shadow. */
fun shadowed(face: Drawable, shadow: Drawable, offsetPx: Int): Drawable =
    if (offsetPx <= 0) face else LayerDrawable(arrayOf(shadow, face)).apply {
        setLayerInset(0, offsetPx, offsetPx, 0, 0)
        setLayerInset(1, 0, 0, offsetPx, offsetPx)
    }

/** The page: flat, or concrete or paper grain. */
fun Context.pageBackground(): Drawable = when (val texture = themeShape.texture) {
    Texture.NONE -> ColorDrawable(AppColors.BACKGROUND)
    else -> GrainDrawable(AppColors.BACKGROUND, resources.displayMetrics.density, texture)
}

fun Context.verticalPage(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(20), dp(20), dp(20), dp(28))
}

fun Context.card(clickable: Boolean = false): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(16), dp(16), dp(16), dp(16))
    background = roundedDrawable(AppColors.SURFACE, dp(24).toFloat(), AppColors.BORDER, dp(themeShape.borderDp))
    clipToOutline = true
    elevation = 0f
    if (clickable) {
        isClickable = true
        isFocusable = true
        foreground = selectableForeground()
    }
}

fun Context.titleText(text: CharSequence, sizeSp: Float = 22f): TextView = TextView(this).apply {
    this.text = text
    setTextColor(AppColors.TEXT)
    textSize = sizeSp * themeShape.titleScale
    typeface = AppType.display
    isAllCaps = themeShape.upperTitles
}

fun Context.bodyText(text: CharSequence, sizeSp: Float = 15f): TextView = TextView(this).apply {
    this.text = text
    setTextColor(AppColors.TEXT_SECONDARY)
    textSize = sizeSp
    setLineSpacing(0f, 1.12f)
    if (AppType.custom) typeface = AppType.body
}

fun Context.sectionLabel(text: CharSequence): TextView = TextView(this).apply {
    this.text = text.toString().uppercase()
    setTextColor(if (AppColors.theme == StreetTheme.NIGHT_PARK) AppColors.PRIMARY else if (AppType.custom) AppColors.TEXT else AppColors.NUMBER_DARK)
    textSize = 13f
    typeface = AppType.label
    letterSpacing = themeShape.labelSpacing
    setPadding(0, dp(8), 0, dp(8))
}

fun Context.numberCircle(number: String, contentDescriptionText: String): TextView = TextView(this).apply {
    text = number
    gravity = Gravity.CENTER
    setTextColor(AppColors.ON_ACCENT)
    textSize = if (AppType.custom) 20f else 16f
    typeface = AppType.numeral
    background = roundedDrawable(AppColors.NUMBER, dp(22).toFloat())
    contentDescription = contentDescriptionText
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
}

fun Context.badge(text: CharSequence, color: Int = AppColors.NUMBER): TextView = TextView(this).apply {
    this.text = text.toString().uppercase()
    setTextColor(AppColors.ON_ACCENT)
    textSize = 11f
    typeface = AppType.label
    gravity = Gravity.CENTER
    letterSpacing = 0.06f
    setPadding(dp(9), dp(4), dp(9), dp(4))
    background = roundedDrawable(color, dp(12).toFloat())
}

/** Marks content of a tier the athlete doesn't have (Plus by default). */
fun Context.lockBadge(text: CharSequence = getString(R.string.tier_plus)): TextView = badge(text, AppColors.TEXT_SECONDARY).apply {
    val icon = getDrawable(R.drawable.ic_lock)?.mutate()?.apply { setTint(AppColors.ON_ACCENT); setBounds(0, 0, dp(12), dp(12)) }
    setCompoundDrawablesRelative(icon, null, null, null)
    compoundDrawablePadding = dp(4)
    contentDescription = text
}

fun Context.primaryButton(
    text: CharSequence,
    color: Int = AppColors.ACTION,
    onClick: () -> Unit,
): Button = Button(this).apply {
    this.text = text
    val content = AppColors.contentOn(color)
    setTextColor(content)
    isAllCaps = false
    minHeight = dp(52)
    stateListAnimator = null
    val theme = AppColors.theme
    // The display face in capitals; Concrete outlines the button in ink, and both poster themes print a hard shadow.
    val radius = dp(16).toFloat()
    val offset = dp(themeShape.shadowDp)
    val face = if (theme == StreetTheme.CONCRETE) roundedDrawable(color, radius, AppColors.BORDER, dp(2)) else roundedDrawable(color, radius)
    val shadow = roundedDrawable(if (theme == StreetTheme.WHEATPASTE) AppColors.SPOT else AppColors.BORDER, radius)
    textSize = when (theme) { StreetTheme.CONCRETE -> 19f; StreetTheme.NIGHT_PARK -> 20f; StreetTheme.WHEATPASTE -> 15f }
    typeface = AppType.display
    isAllCaps = true
    letterSpacing = if (theme == StreetTheme.WHEATPASTE) 0.02f else 0.05f
    setPadding(dp(18), dp(12), dp(18) + offset, dp(12) + offset)
    val ripple = if (content == Color.WHITE) 0x22FFFFFF else 0x22000000
    background = RippleDrawable(ColorStateList.valueOf(ripple), shadowed(face, shadow, offset), null)
    setOnClickListener { onClick() }
}

fun Context.secondaryButton(text: CharSequence, onClick: () -> Unit): Button = Button(this).apply {
    this.text = text
    isAllCaps = false
    minHeight = dp(52)
    setPadding(dp(18), dp(12), dp(18), dp(12))
    stateListAnimator = null
    val radius = dp(16).toFloat()
    val theme = AppColors.theme
    setTextColor(AppColors.TEXT)
    textSize = 13f
    typeface = AppType.label
    isAllCaps = true
    letterSpacing = if (theme == StreetTheme.WHEATPASTE) 0.04f else 0.1f
    val face = when (theme) {
        StreetTheme.CONCRETE -> roundedDrawable(AppColors.SURFACE, radius, AppColors.BORDER, dp(2))
        StreetTheme.WHEATPASTE -> dashedDrawable(Color.TRANSPARENT, AppColors.BORDER)
        StreetTheme.NIGHT_PARK -> roundedDrawable(AppColors.INFO_SURFACE, radius)
    }
    val ripple = if (AppColors.darkMode) 0x22FFFFFF else 0x22000000
    background = RippleDrawable(ColorStateList.valueOf(ripple), face, roundedDrawable(Color.WHITE, radius))
    setOnClickListener { onClick() }
}

fun Context.horizontalProgress(percent: Int): ProgressBar = ProgressBar(
    this,
    null,
    android.R.attr.progressBarStyleHorizontal,
).apply {
    max = 100
    progress = percent.coerceIn(0, 100)
    progressTintList = ColorStateList.valueOf(AppColors.PRIMARY)
    progressBackgroundTintList = ColorStateList.valueOf(AppColors.INFO_SURFACE)
    minimumHeight = dp(8)
}

fun Context.spacer(heightDp: Int): Space = Space(this).apply {
    layoutParams = LinearLayout.LayoutParams(1, dp(heightDp))
}

fun ViewGroup.addWithMargins(
    view: View,
    width: Int = ViewGroup.LayoutParams.MATCH_PARENT,
    height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    leftDp: Int = 0,
    topDp: Int = 0,
    rightDp: Int = 0,
    bottomDp: Int = 0,
) {
    val context = view.context
    addView(
        view,
        LinearLayout.LayoutParams(width, height).apply {
            setMargins(context.dp(leftDp), context.dp(topDp), context.dp(rightDp), context.dp(bottomDp))
        },
    )
}

fun Context.oneLineText(text: CharSequence, bold: Boolean = false): TextView = TextView(this).apply {
    this.text = text
    setTextColor(AppColors.TEXT)
    textSize = 16f
    maxLines = 1
    ellipsize = TextUtils.TruncateAt.END
    typeface = if (bold) AppType.bold else AppType.body
}

/** Gives every text in this tree that kept the system face the theme's body or bold face (built screens, rows, sheets). */
fun View.applyThemeType() {
    if (!AppType.custom) return
    when (this) {
        is TextView -> AppType.restyle(this)
        is ViewGroup -> for (index in 0 until childCount) getChildAt(index).applyThemeType()
    }
}

internal fun Context.selectableForeground() = TypedValue().let { value ->
    theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
    getDrawable(value.resourceId)
}
