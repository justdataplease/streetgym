package com.justdataplease.streetgym.ui

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.justdataplease.streetgym.R
import com.justdataplease.streetgym.account.AccountStore
import com.justdataplease.streetgym.account.Cloud
import com.justdataplease.streetgym.account.CloudSync
import com.justdataplease.streetgym.account.GoogleAccount
import com.justdataplease.streetgym.account.Profile
import com.justdataplease.streetgym.account.Profiles
import com.justdataplease.streetgym.analytics.Analytics
import com.justdataplease.streetgym.billing.Entitlement
import com.justdataplease.streetgym.leaderboard.Countries
import com.justdataplease.streetgym.leaderboard.Usernames
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * The Pro account in Settings: Sign in with Google, the leaderboard profile, cloud progress, sign out and account
 * deletion. Only create it when [Cloud.signInAvailable]: builds without a Firebase configuration (README) show
 * nothing here. [onChanged] runs after sign-in or a profile change, so the score and sync catch up.
 */
class AccountSettingsView(
    private val activity: Activity,
    private val sync: CloudSync,
    private val onChanged: () -> Unit,
) : LinearLayout(activity) {
    private val store = AccountStore(activity)
    private val auth = Cloud.auth(activity)
    private val listener = FirebaseAuth.AuthStateListener { post { render() } }
    private var busy = false

    init {
        orientation = VERTICAL
        setPadding(context.dp(16), context.dp(16), context.dp(16), context.dp(16))
        background = roundedDrawable(AppColors.SURFACE, context.dp(24).toFloat(), AppColors.DIVIDER, context.dp(1))
        render()
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); auth?.addAuthStateListener(listener) }
    override fun onDetachedFromWindow() { auth?.removeAuthStateListener(listener); super.onDetachedFromWindow() }

    private fun render() {
        removeAllViews()
        // The Google account only: the anonymous user progress analytics signs in with is no account.
        val user = Cloud.user(activity)
        addView(context.titleText(context.getString(R.string.account_title), 20f))
        val tier = Entitlement.tier(activity)
        if (!tier.account) {
            // Plus and Pro sign in; an account from a lapsed plan stays signed in, but nothing syncs or publishes.
            if (user != null) {
                addWithMargins(context.bodyText(context.getString(R.string.account_lapsed), 14f), topDp = 6)
                addWithMargins(context.secondaryButton(context.getString(R.string.account_sign_out)) { GoogleAccount.signOut(activity) }, topDp = 12)
            } else visibility = View.GONE
            return
        }
        visibility = View.VISIBLE
        val status = context.bodyText("", 13f)
        if (user == null) {
            addWithMargins(context.bodyText(context.getString(if (tier.cloud) R.string.account_intro else R.string.account_intro_plus), 14f), topDp = 6)
            addWithMargins(context.primaryButton(context.getString(R.string.account_sign_in)) {
                if (busy) return@primaryButton
                busy = true; status.setText(R.string.account_working)
                GoogleAccount.signIn(activity) { result ->
                    busy = false
                    when (result) {
                        is GoogleAccount.Result.Signed -> Profiles.load(activity, result.user.uid) { profile ->
                            sync.sync(force = true) { onChanged() }
                            render()
                            if (profile == null) editProfile(result.user.uid, null)
                        }
                        GoogleAccount.Result.Cancelled -> status.text = ""
                        is GoogleAccount.Result.Failed -> status.setText(R.string.account_sign_in_failed)
                    }
                }
            }, topDp = 12)
            addWithMargins(status, topDp = 8)
            return
        }
        addWithMargins(context.bodyText(user.email.orEmpty(), 14f), topDp = 4)
        val profile = store.profile(user.uid)
        if (profile == null) {
            addWithMargins(context.bodyText(context.getString(R.string.account_pick_name), 14f), topDp = 10)
            addWithMargins(context.primaryButton(context.getString(R.string.account_choose_profile)) { editProfile(user.uid, null) }, topDp = 10)
        } else {
            addWithMargins(context.titleText("${Countries.flag(profile.country)} ${profile.username}", 18f), topDp = 12)
            addWithMargins(context.bodyText(Countries.name(profile.country), 13f), topDp = 2)
            addWithMargins(Switch(context).apply {
                text = context.getString(R.string.account_visible)
                textSize = 15f
                setTextColor(AppColors.TEXT)
                isChecked = profile.visible
                minHeight = context.dp(56)
                setOnCheckedChangeListener { _, visible -> saveProfile(user.uid, profile.copy(visible = visible)) }
            }, topDp = 6)
            addWithMargins(context.secondaryButton(context.getString(R.string.account_edit_profile)) { editProfile(user.uid, profile) }, topDp = 4)
        }
        // Plus is on the leaderboard only; its progress stays on the phone.
        if (tier.cloud) {
            status.text = if (store.lastSyncAt > 0) context.getString(R.string.account_synced,
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(store.lastSyncAt)))
                else context.getString(R.string.account_not_synced)
            addWithMargins(status, topDp = 14)
            addWithMargins(context.secondaryButton(context.getString(R.string.account_sync_now)) {
                status.setText(R.string.account_working)
                sync.sync(force = true) { result ->
                    if (result == CloudSync.Result.Failed) status.setText(R.string.account_sync_failed) else { render(); onChanged() }
                }
            }, topDp = 8)
        } else addWithMargins(context.bodyText(context.getString(R.string.account_plus_note), 13f), topDp = 14)
        addWithMargins(context.secondaryButton(context.getString(R.string.account_sign_out)) { GoogleAccount.signOut(activity) }, topDp = 8)
        addWithMargins(context.bodyText(context.getString(R.string.account_sign_out_note), 12f), topDp = 4)
        addWithMargins(context.secondaryButton(context.getString(R.string.account_delete)) { confirmDelete(user.uid) }, topDp = 12)
    }

    /** Username (unique) and country; first shown right after the first sign-in. */
    private fun editProfile(uid: String, previous: Profile?) {
        val countries = Countries.all()
        val name = EditText(context).apply {
            hint = context.getString(R.string.profile_username)
            setText(previous?.username.orEmpty())
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setTextColor(AppColors.TEXT)
            setHintTextColor(AppColors.TEXT_SECONDARY)
        }
        val country = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item,
                countries.map { "${Countries.flag(it.first)} ${it.second}" })
            val current = previous?.country ?: Locale.getDefault().country
            setSelection(countries.indexOfFirst { it.first == current }.coerceAtLeast(0))
        }
        val content = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(context.dp(20), context.dp(8), context.dp(20), 0)
            addView(name)
            addWithMargins(context.bodyText(context.getString(R.string.profile_country), 13f), topDp = 12)
            addView(country)
        }
        val dialog = AlertDialog.Builder(activity).setTitle(R.string.profile_title).setView(content)
            .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.profile_save, null).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val username = name.text.toString().trim()
            if (!Usernames.valid(username)) { name.error = context.getString(R.string.profile_invalid); return@setOnClickListener }
            val profile = Profile(username, countries[country.selectedItemPosition].first, previous?.visible ?: true)
            saveProfile(uid, profile) { error -> if (error == null) dialog.dismiss() else name.error = error }
        }
    }

    /** [done] gets null once saved, else the reason to show. A saved profile republishes the score under it. */
    private fun saveProfile(uid: String, profile: Profile, done: (String?) -> Unit = {}) {
        Profiles.save(activity, uid, profile) { claim ->
            when (claim) {
                Profiles.Claim.Saved -> { store.savePublishedScore(uid, null, 0); render(); onChanged(); done(null) }
                Profiles.Claim.Taken -> done(context.getString(R.string.profile_taken))
                Profiles.Claim.Failed -> { render(); done(context.getString(R.string.profile_failed)) }
            }
        }
    }

    /**
     * Play requires in-app deletion: cloud progress, username, profile and score go, then the Firebase account, and
     * progress analytics drops the account's uid from this install's record. The profile is read again first, so a
     * username changed on another device is the one released.
     */
    private fun confirmDelete(uid: String) {
        AlertDialog.Builder(activity).setTitle(R.string.account_delete_title).setMessage(R.string.account_delete_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                GoogleAccount.reauthenticate(activity) { confirmed ->
                    val db = Cloud.firestore(activity)
                    if (!confirmed || db == null) return@reauthenticate failed()
                    Profiles.load(activity, uid) { profile ->
                        db.batch().apply {
                            delete(db.document("users/$uid/data/progress"))
                            delete(db.document("users/$uid/data/library"))
                            delete(db.document("scores/$uid"))
                            profile?.let { delete(db.document("usernames/${Usernames.key(it.username)}")) }
                            delete(db.document("users/$uid"))
                        }.commit().addOnCompleteListener(activity) { cleared ->
                            if (!cleared.isSuccessful) return@addOnCompleteListener failed()
                            Cloud.user(activity)?.delete()?.addOnCompleteListener(activity) { deleted ->
                                if (!deleted.isSuccessful) return@addOnCompleteListener failed()
                                store.clear()
                                Analytics.get(activity).accountDeleted()
                                Toast.makeText(activity, R.string.account_deleted, Toast.LENGTH_LONG).show()
                                render()
                            }
                        }
                    }
                }
            }.show()
    }

    private fun failed() = Toast.makeText(activity, R.string.account_delete_failed, Toast.LENGTH_LONG).show()
}
