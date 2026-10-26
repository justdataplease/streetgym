package com.justdataplease.streetgym.ads

import android.content.Context
import android.content.SharedPreferences
import android.widget.FrameLayout
import com.google.android.ump.UserMessagingPlatform
import com.justdataplease.monetization.AdPalette
import com.justdataplease.monetization.NativeAdSlot
import com.justdataplease.streetgym.BuildConfig
import com.justdataplease.streetgym.billing.Entitlement
import com.justdataplease.streetgym.ui.AppColors

/** Removes existing cards immediately on purchase. Training never launches a consent popup. */
class ManagedNativePlacement(context: Context, private val training: Boolean = false) : FrameLayout(context),
    SharedPreferences.OnSharedPreferenceChangeListener {
    private val prefs = Entitlement.prefs(context)
    private var slot: NativeAdSlot? = null
    private val retry = Runnable { refresh() }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow(); prefs.registerOnSharedPreferenceChangeListener(this); refresh()
    }
    override fun onDetachedFromWindow() {
        prefs.unregisterOnSharedPreferenceChangeListener(this); removeCallbacks(retry)
        removeAllViews(); slot = null; super.onDetachedFromWindow()
    }
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) = refresh()
    private fun refresh() {
        removeCallbacks(retry)
        if (!isAttachedToWindow) return
        if (Entitlement.adFree(context)) { removeAllViews(); slot = null; return }
        if (training && !UserMessagingPlatform.getConsentInformation(context).canRequestAds()) {
            // The dashboard handles consent. Deferring here avoids interrupting an active set/dialog.
            removeAllViews(); slot = null; postDelayed(retry, 2000); return
        }
        if (slot == null) {
            slot = NativeAdSlot(context, BuildConfig.ADMOB_NATIVE_ID,
                AdPalette(AppColors.SURFACE, AppColors.TEXT, AppColors.TEXT_SECONDARY, AppColors.ACTION, AppColors.ON_ACCENT, AppColors.DIVIDER))
            addView(slot, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
    }
}
