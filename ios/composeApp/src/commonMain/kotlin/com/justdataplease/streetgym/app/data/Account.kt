package com.justdataplease.streetgym.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Who is signed in (Android: FirebaseUser): a Google or Apple account, or the [anonymous] user a username without
 * sign-in made (Android: Cloud.signInAnonymously). [email] as the account gives it; [provider] the account's sign-in.
 */
data class AccountUser(val uid: String, val anonymous: Boolean, val email: String? = null, val provider: SignInProvider? = null)

/** The sign-ins the iPhone offers: Google, as on Android, and Sign in with Apple beside it (Apple 4.8). */
enum class SignInProvider(val id: String) {
    GOOGLE("google.com"),
    APPLE("apple.com");

    companion object {
        fun of(id: String?): SignInProvider? = entries.firstOrNull { it.id == id }
    }
}

/** How connecting an account ended (Android: GoogleAccount.Result, before a collision is settled). */
sealed interface LinkResult {
    /** The account is linked to the user who was signed in (anonymous), or signed in when no one was. */
    data class Linked(val user: AccountUser) : LinkResult

    /**
     * The account already has a user of its own, from another phone; [anonymousUid] holds this phone's profile. The
     * phone keeps the account's credential for [Account.takeOver] (Android: FirebaseAuthUserCollisionException).
     */
    data class Collision(val anonymousUid: String) : LinkResult

    data object Cancelled : LinkResult
    data class Failed(val reason: String) : LinkResult
}

/**
 * Firebase Auth as the app needs it (Android: account/Cloud.kt and GoogleAccount.kt): on iPhone Swift does the signing
 * in (iosApp/StreetGym/FirebaseAccount.swift, through iosMain's IosAccount), [None] where the build has no Firebase,
 * a fake on the desktop and in tests. Every answer comes back on the main thread.
 */
interface Account {
    /** The build has a Firebase configuration (Android: Cloud.available); without it every account feature stays hidden. */
    val available: Boolean

    /** The sign-ins this build can offer: Google needs its client id and the URL type; Apple its capability. */
    val providers: Set<SignInProvider>

    /** Whoever is signed in, the anonymous user included, as it changes (Android: an AuthStateListener). */
    val user: StateFlow<AccountUser?>

    /** Signs in a new anonymous user; [done] gets null when no one could sign in (offline, or turned off in the console). */
    fun signInAnonymously(done: (AccountUser?) -> Unit)

    /** Asks the athlete for [provider]'s account, then links it to the anonymous user signed in, or signs in with it. */
    fun link(provider: SignInProvider, done: (LinkResult) -> Unit)

    /** After a [LinkResult.Collision]: deletes the anonymous user (best effort) and signs in with the account it kept. */
    fun takeOver(done: (AccountUser?) -> Unit)

    /** After a [LinkResult.Collision] that isn't taken over: forgets the account's credential. */
    fun dropTakeOver()

    /** Asks for the account again: deleting it needs a recent sign-in. */
    fun reauthenticate(done: (Boolean) -> Unit)

    fun signOut()

    /** Deletes the signed-in account's user (after [reauthenticate]); for Sign in with Apple, its token is revoked first. */
    fun deleteUser(done: (Boolean) -> Unit)

    /** Deletes the anonymous user [uid] when it is the one signed in; best effort (Android: confirmDeleteProfile). */
    fun deleteAnonymous(uid: String)

    /** No Firebase in this build: nothing signs in, and the account's features stay hidden. */
    object None : Account {
        override val available = false
        override val providers = emptySet<SignInProvider>()
        override val user: StateFlow<AccountUser?> = MutableStateFlow(null)
        override fun signInAnonymously(done: (AccountUser?) -> Unit) = done(null)
        override fun link(provider: SignInProvider, done: (LinkResult) -> Unit) = done(LinkResult.Failed("unavailable"))
        override fun takeOver(done: (AccountUser?) -> Unit) = done(null)
        override fun dropTakeOver() {}
        override fun reauthenticate(done: (Boolean) -> Unit) = done(false)
        override fun signOut() {}
        override fun deleteUser(done: (Boolean) -> Unit) = done(false)
        override fun deleteAnonymous(uid: String) {}
    }
}
