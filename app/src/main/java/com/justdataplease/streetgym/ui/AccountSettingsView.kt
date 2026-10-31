package com.justdataplease.streetgym.ui

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
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
 * Settings › Profile, at the top for every tier (the owner, 2026-10-31: "anyone can have a username… also the connect
 * to keep data will be there"). A username needs no sign-in: the first one signs in anonymously and the profile lives
 * under that user ([Cloud.profileUser]); the boards show Plus and Pro. Below it, Connect Google links that user, so the
 * username, the score and Pro's cloud progress follow to a new phone; once connected: cloud progress (Pro), sign out and
 * account deletion. Only create it when [Cloud.available]: a build without a Firebase configuration (README) shows
 * nothing here. [onChanged] runs after a connection or a profile change, so the score and sync catch up.
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
        val uid = Cloud.profileUid(activity)
        // The Google account only; the anonymous user holds a profile but keeps nothing beyond this phone.
        val google = Cloud.user(activity)
        val tier = Entitlement.tier(activity)
        val profile = uid?.let(store::profile)
        val status = context.bodyText("", 13f)

        if (uid == null || profile == null) {
            addView(context.bodyText(context.getString(R.string.profile_intro), 14f))
            addWithMargins(context.primaryButton(context.getString(R.string.account_choose_profile)) { pickUsername(status) }, topDp = 12)
        } else {
            addView(context.titleText("${Countries.flag(profile.country)} ${profile.username}", 18f))
            addWithMargins(context.bodyText(Countries.name(profile.country), 13f), topDp = 2)
            addWithMargins(Switch(context).apply {
                text = context.getString(R.string.account_visible)
                textSize = 15f
                setTextColor(AppColors.TEXT)
                isChecked = profile.visible
                minHeight = context.dp(56)
                setOnCheckedChangeListener { _, visible -> saveProfile(uid, profile.copy(visible = visible)) }
            }, topDp = 6)
            addWithMargins(context.secondaryButton(context.getString(R.string.account_edit_profile)) { editProfile(uid, profile) }, topDp = 4)
        }
        if (!tier.account) addWithMargins(context.bodyText(context.getString(R.string.profile_board_note), 12f), topDp = 8)

        if (google == null) {
            // Connect Google, when this build can (it needs the web client id).
            if (Cloud.signInAvailable(activity)) {
                addWithMargins(context.bodyText(context.getString(if (tier.cloud) R.string.account_intro else R.string.account_intro_plus), 14f), topDp = 18)
                addWithMargins(context.secondaryButton(context.getString(R.string.account_sign_in)) { connect(status) }, topDp = 10)
            } else if (profile != null) addWithMargins(context.bodyText(context.getString(R.string.profile_keep_note), 12f), topDp = 12)
            addWithMargins(status, topDp = 8)
            if (uid != null && profile != null) {
                addWithMargins(context.secondaryButton(context.getString(R.string.profile_delete)) { confirmDeleteProfile(uid) }, topDp = 8)
            }
            return
        }

        addWithMargins(context.bodyText(context.getString(R.string.profile_google, google.email.orEmpty()), 14f), topDp = 18)
        // Plus and Free keep their progress on the phone; Pro's syncs.
        if (tier.cloud) {
            status.text = if (store.lastSyncAt > 0) context.getString(R.string.account_synced,
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(store.lastSyncAt)))
                else context.getString(R.string.account_not_synced)
            addWithMargins(status, topDp = 8)
            addWithMargins(context.secondaryButton(context.getString(R.string.account_sync_now)) {
                status.setText(R.string.account_working)
                sync.sync(force = true) { result ->
                    if (result == CloudSync.Result.Failed) status.setText(R.string.account_sync_failed) else { render(); onChanged() }
                }
            }, topDp = 8)
        } else addWithMargins(context.bodyText(context.getString(R.string.account_plus_note), 13f), topDp = 8)
        addWithMargins(context.secondaryButton(context.getString(R.string.account_sign_out)) { GoogleAccount.signOut(activity) }, topDp = 8)
        addWithMargins(context.bodyText(context.getString(R.string.account_sign_out_note), 12f), topDp = 4)
        addWithMargins(context.secondaryButton(context.getString(R.string.account_delete)) { confirmDelete(google.uid) }, topDp = 12)
    }

    /** The first username: signs in anonymously when no one is signed in, then asks for the name and country. */
    private fun pickUsername(status: TextView) {
        if (busy) return
        Cloud.profileUser(activity)?.let { return editProfile(it.uid, null) }
        busy = true
        status.setText(R.string.account_working)
        Cloud.signInAnonymously(activity) { user ->
            busy = false
            if (user == null) status.setText(R.string.profile_offline)
            else { status.text = ""; editProfile(user.uid, null) }
        }
    }

    /**
     * Connect Google. A linked anonymous user keeps its uid, so the profile stays. A Google account with a user of its
     * own gave up this phone's profile ([GoogleAccount.Result.Signed.released]): its own profile loads, and when it has
     * none the released name is claimed again for it.
     */
    private fun connect(status: TextView) {
        if (busy) return
        busy = true
        status.setText(R.string.account_working)
        GoogleAccount.signIn(activity) { result ->
            when (result) {
                is GoogleAccount.Result.Signed -> Profiles.load(activity, result.user.uid) { profile ->
                    val reclaim = result.released?.takeIf { profile == null }
                    fun done() {
                        busy = false
                        sync.sync(force = true) { onChanged() }
                        render()
                    }
                    if (reclaim == null) done() else Profiles.save(activity, result.user.uid, reclaim) { done() }
                }
                GoogleAccount.Result.Cancelled -> { busy = false; status.text = "" }
                is GoogleAccount.Result.Failed -> { busy = false; status.setText(R.string.account_sign_in_failed) }
            }
        }
    }

    /** Username (unique) and country. */
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

    /** A profile kept without Google: its username, profile and score go, then (best effort) the anonymous user. */
    private fun confirmDeleteProfile(uid: String) {
        AlertDialog.Builder(activity).setTitle(R.string.profile_delete_title).setMessage(R.string.profile_delete_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                Profiles.release(activity, uid) { released, _ ->
                    if (!released) return@release Toast.makeText(activity, R.string.profile_delete_failed, Toast.LENGTH_LONG).show()
                    Cloud.profileUser(activity)?.takeIf { it.isAnonymous && it.uid == uid }?.delete()
                    Toast.makeText(activity, R.string.profile_deleted, Toast.LENGTH_LONG).show()
                    render()
                }
            }.show()
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
