package com.justdataplease.streetgym.account

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore

/**
 * The Firebase project behind the profile and Google account, the leaderboard and progress analytics. A build without a Firebase
 * configuration (README, "Pro account") has no default app: then [available] is false, every account feature stays
 * hidden and nothing is uploaded, so the app runs exactly as before.
 */
object Cloud {
    @Volatile private var appCheckInstalled = false
    @Volatile private var anonymousSignIn: Task<AuthResult>? = null

    fun available(context: Context): Boolean =
        runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

    /** Google sign-in also needs the web OAuth client id; without it the app offers no sign-in. */
    fun webClientId(context: Context): String? = context.resources
        .getIdentifier("default_web_client_id", "string", context.packageName)
        .takeIf { it != 0 }?.let(context::getString)?.takeIf(String::isNotBlank)

    fun signInAvailable(context: Context): Boolean = available(context) && webClientId(context) != null

    /** Installs App Check once (Play Integrity in release, the debug provider in debug) before the first read. */
    fun firestore(context: Context): FirebaseFirestore? {
        if (!available(context)) return null
        if (!appCheckInstalled) synchronized(this) {
            if (!appCheckInstalled) {
                runCatching { FirebaseAppCheck.getInstance().installAppCheckProviderFactory(AppCheckFactory.get()) }
                appCheckInstalled = true
            }
        }
        return runCatching { FirebaseFirestore.getInstance() }.getOrNull()
    }

    fun auth(context: Context): FirebaseAuth? = if (available(context)) runCatching { FirebaseAuth.getInstance() }.getOrNull() else null

    /**
     * The Google account signed in, for what needs one: cloud progress (Pro) and the account a progress record names.
     * The anonymous user ([signInAnonymously]) is no account.
     */
    fun user(context: Context): FirebaseUser? = account(auth(context)?.currentUser, FirebaseUser::isAnonymous)

    fun uid(context: Context): String? = user(context)?.uid

    /**
     * Whoever holds the profile, the username and the score: the Google account, else the anonymous user (the owner,
     * 2026-10-31: anyone picks a username, Google is optional). Connecting Google links that anonymous user, so its
     * uid, username and score stay.
     */
    fun profileUser(context: Context): FirebaseUser? = auth(context)?.currentUser

    fun profileUid(context: Context): String? = profileUser(context)?.uid

    /** [user] unless it is [anonymous]. */
    fun <U : Any> account(user: U?, anonymous: (U) -> Boolean): U? = user?.takeUnless(anonymous)

    /**
     * Whoever is signed in, else a new anonymous user, for progress analytics or a username picked without Google;
     * [done] gets null when no one could sign in (offline, or Anonymous sign-in off in the console). One anonymous
     * sign-in at a time.
     */
    fun signInAnonymously(context: Context, done: (FirebaseUser?) -> Unit) {
        val auth = auth(context) ?: return done(null)
        auth.currentUser?.let { return done(it) }
        val task = anonymousSignIn?.takeUnless { it.isComplete }
            ?: runCatching { auth.signInAnonymously() }.getOrNull()?.also { anonymousSignIn = it }
            ?: return done(null)
        task.addOnCompleteListener { done(if (it.isSuccessful) auth.currentUser else null) }
    }

    /**
     * Runs [block] once no anonymous sign-in is under way: one finishing after a Google sign-in would replace the account.
     */
    fun afterAnonymousSignIn(block: () -> Unit) {
        val pending = anonymousSignIn
        if (pending == null || pending.isComplete) block() else pending.addOnCompleteListener { block() }
    }
}
