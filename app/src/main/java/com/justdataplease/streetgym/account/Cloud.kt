package com.justdataplease.streetgym.account

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * The Firebase project behind the Pro account and the leaderboard. A build without a Firebase configuration
 * (README, "Pro account") has no default app: then [available] is false and every account feature stays hidden,
 * so the app runs exactly as before.
 */
object Cloud {
    @Volatile private var appCheckInstalled = false

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

    fun uid(context: Context): String? = auth(context)?.currentUser?.uid
}
