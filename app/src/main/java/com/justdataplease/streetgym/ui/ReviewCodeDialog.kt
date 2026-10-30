package com.justdataplease.streetgym.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.text.InputFilter
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.justdataplease.streetgym.R
import com.justdataplease.streetgym.billing.Entitlement
import com.justdataplease.streetgym.billing.ReviewAccess
import kotlin.math.abs

/** How long the About title is held before the review code dialog opens. */
const val REVIEW_HOLD_MILLIS = 2_000L

/**
 * Runs [action] once this view has been held for [millis] without the finger moving off; a hidden gesture, so nothing
 * shows until then. Scrolling the page cancels it.
 */
@SuppressLint("ClickableViewAccessibility")
fun View.onHeld(millis: Long, action: () -> Unit) {
    val slop = ViewConfiguration.get(context).scaledTouchSlop
    var downX = 0f
    var downY = 0f
    val fire = Runnable { performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); action() }
    setOnTouchListener { view, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; view.postDelayed(fire, millis) }
            MotionEvent.ACTION_MOVE -> if (abs(event.x - downX) > slop || abs(event.y - downY) > slop) view.removeCallbacks(fire)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.removeCallbacks(fire)
        }
        true
    }
}

/**
 * Play review access ([ReviewAccess]): one field for the code. The right code acts as Pro for 30 days and runs
 * [onUnlocked]; a wrong one says so and changes nothing, and the dialog stays open to try again.
 */
fun Activity.reviewCodeDialog(onUnlocked: () -> Unit) {
    val field = EditText(this).apply {
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        filters = arrayOf(InputFilter.LengthFilter(64))
        imeOptions = EditorInfo.IME_ACTION_DONE
        setTextColor(AppColors.TEXT)
        setHintTextColor(AppColors.TEXT_SECONDARY)
    }
    val dialog = AlertDialog.Builder(this).setTitle(R.string.review_code_title)
        .setView(LinearLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
        .setNegativeButton(R.string.cancel, null)
        .setPositiveButton(android.R.string.ok, null)
        .create()
    fun submit() {
        if (!Entitlement.redeemReviewCode(this, field.text.toString())) {
            Toast.makeText(this, R.string.review_code_wrong, Toast.LENGTH_SHORT).show()
            return
        }
        dialog.dismiss()
        Toast.makeText(this, resources.getQuantityString(R.plurals.review_code_unlocked, ReviewAccess.DAYS, ReviewAccess.DAYS),
            Toast.LENGTH_LONG).show()
        onUnlocked()
    }
    dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { submit() } }
    field.setOnEditorActionListener { _, action, _ -> (action == EditorInfo.IME_ACTION_DONE).also { if (it) submit() } }
    dialog.show()
}
