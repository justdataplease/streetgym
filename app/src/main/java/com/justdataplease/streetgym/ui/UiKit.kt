package com.justdataplease.streetgym.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Space
import android.widget.TextView

object AppColors {
    var darkMode = false
        private set
    fun configure(dark: Boolean) { darkMode = dark }
    val PRIMARY: Int get() = if (darkMode) 0xFF8AF3BD.toInt() else 0xFF1E7056.toInt()
    val PRIMARY_DARK: Int get() = if (darkMode) 0xFF2A3E30.toInt() else 0xFF193F31.toInt()
    val BACKGROUND: Int get() = if (darkMode) 0xFF121917.toInt() else 0xFFF4F7F3.toInt()
    val SURFACE: Int get() = if (darkMode) 0xFF202B25.toInt() else 0xFFFFFFFF.toInt()
    val TEXT: Int get() = if (darkMode) 0xFFF4F8F2.toInt() else 0xFF182A22.toInt()
    val TEXT_SECONDARY: Int get() = if (darkMode) 0xFFA5B3A8.toInt() else 0xFF66756C.toInt()
    val DIVIDER: Int get() = if (darkMode) 0xFF39483E.toInt() else 0xFFE0E7E0.toInt()
    val INFO_SURFACE: Int get() = if (darkMode) 0xFF2F4035.toInt() else 0xFFE3F2E9.toInt()
    val ACCENT: Int get() = if (darkMode) 0xFF8AF3BD.toInt() else 0xFF69DFB4.toInt()
    val NUMBER get() = PRIMARY
    val NUMBER_DARK get() = PRIMARY
    val ACTION get() = PRIMARY
    val ACTION_LIGHT get() = ACCENT
    val ON_ACCENT get() = if (darkMode) BACKGROUND else Color.WHITE
    val SUCCESS get() = if (darkMode) 0xFF8AF3BD.toInt() else 0xFF1E7056.toInt()
    val WARNING_SURFACE get() = if (darkMode) 0xFF3B3023.toInt() else 0xFFFFF0D9.toInt()
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

fun roundedDrawable(
    color: Int,
    radiusPx: Float,
    strokeColor: Int? = null,
    strokeWidthPx: Int = 0,
): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.RECTANGLE
    setColor(color)
    cornerRadius = radiusPx
    if (strokeColor != null && strokeWidthPx > 0) setStroke(strokeWidthPx, strokeColor)
}

fun Context.verticalPage(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(20), dp(20), dp(20), dp(28))
}

fun Context.card(clickable: Boolean = false): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(16), dp(16), dp(16), dp(16))
    background = roundedDrawable(AppColors.SURFACE, dp(24).toFloat(), AppColors.DIVIDER, dp(1))
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
    textSize = sizeSp
    typeface = Typeface.DEFAULT_BOLD
}

fun Context.bodyText(text: CharSequence, sizeSp: Float = 15f): TextView = TextView(this).apply {
    this.text = text
    setTextColor(AppColors.TEXT_SECONDARY)
    textSize = sizeSp
    setLineSpacing(0f, 1.12f)
}

fun Context.sectionLabel(text: CharSequence): TextView = TextView(this).apply {
    this.text = text.toString().uppercase()
    setTextColor(AppColors.NUMBER_DARK)
    textSize = 13f
    typeface = Typeface.DEFAULT_BOLD
    letterSpacing = 0.08f
    setPadding(0, dp(8), 0, dp(8))
}

fun Context.numberCircle(number: String, contentDescriptionText: String): TextView = TextView(this).apply {
    text = number
    gravity = Gravity.CENTER
    setTextColor(AppColors.ON_ACCENT)
    textSize = 16f
    typeface = Typeface.DEFAULT_BOLD
    background = roundedDrawable(AppColors.NUMBER, dp(22).toFloat())
    contentDescription = contentDescriptionText
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
}

fun Context.badge(text: CharSequence, color: Int = AppColors.NUMBER): TextView = TextView(this).apply {
    this.text = text.toString().uppercase()
    setTextColor(AppColors.ON_ACCENT)
    textSize = 11f
    typeface = Typeface.DEFAULT_BOLD
    gravity = Gravity.CENTER
    letterSpacing = 0.06f
    setPadding(dp(9), dp(4), dp(9), dp(4))
    background = roundedDrawable(color, dp(12).toFloat())
}

fun Context.primaryButton(
    text: CharSequence,
    color: Int = AppColors.ACTION,
    onClick: () -> Unit,
): Button = Button(this).apply {
    this.text = text
    setTextColor(AppColors.ON_ACCENT)
    textSize = 14f
    typeface = Typeface.DEFAULT_BOLD
    isAllCaps = false
    minHeight = dp(52)
    setPadding(dp(18), dp(12), dp(18), dp(12))
    stateListAnimator = null
    background = RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), roundedDrawable(color, dp(16).toFloat()), null)
    setOnClickListener { onClick() }
}

fun Context.secondaryButton(text: CharSequence, onClick: () -> Unit): Button = Button(this).apply {
    this.text = text
    setTextColor(AppColors.NUMBER_DARK)
    textSize = 14f
    typeface = Typeface.DEFAULT_BOLD
    isAllCaps = false
    minHeight = dp(52)
    setPadding(dp(18), dp(12), dp(18), dp(12))
    stateListAnimator = null
    background = RippleDrawable(ColorStateList.valueOf(0x22000000), roundedDrawable(AppColors.INFO_SURFACE, dp(16).toFloat()), null)
    setOnClickListener { onClick() }
}

fun Context.roundIconButton(
    iconRes: Int,
    description: CharSequence,
    color: Int = AppColors.ACTION,
    onClick: () -> Unit,
): ImageButton = ImageButton(this).apply {
    setImageResource(iconRes)
    contentDescription = description
    background = roundedDrawable(color, dp(28).toFloat())
    setPadding(dp(16), dp(16), dp(16), dp(16))
    elevation = dp(6).toFloat()
    isFocusable = true
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
    if (bold) typeface = Typeface.DEFAULT_BOLD
}

private fun Context.selectableForeground() = TypedValue().let { value ->
    theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
    getDrawable(value.resourceId)
}
