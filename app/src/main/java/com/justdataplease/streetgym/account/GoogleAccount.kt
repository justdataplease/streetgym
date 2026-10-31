package com.justdataplease.streetgym.account

import android.app.Activity
import android.os.CancellationSignal
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider

/** Connect Google (any tier, to keep the username and Pro's progress) through Credential Manager, then Firebase Auth. */
object GoogleAccount {
    // Activity.getMainExecutor needs API 28; the app supports 23.
    private val main = java.util.concurrent.Executor { android.os.Handler(android.os.Looper.getMainLooper()).post(it) }

    sealed interface Result {
        /** [released]: this phone's profile, given up because the Google account had a user of its own. */
        data class Signed(val user: FirebaseUser, val released: Profile? = null) : Result
        data object Cancelled : Result
        data class Failed(val reason: String) : Result
    }

    /**
     * Connects Google (the owner, 2026-10-31: "the connect to keep data"). The anonymous user that holds this phone's
     * profile, or only its progress analytics, is linked to the account, so its uid, username and score stay. When that
     * Google account already has a user (from another phone), this phone's profile is released ([Profiles.release]) and
     * the account signs in instead; the caller may claim the released name again for an account that has no profile.
     */
    fun signIn(activity: Activity, onResult: (Result) -> Unit) {
        googleCredential(activity) { credential, failure ->
            val auth = Cloud.auth(activity)
            if (credential == null || auth == null) { onResult(failure ?: Result.Failed("unavailable")); return@googleCredential }
            Cloud.afterAnonymousSignIn {
                val anonymous = auth.currentUser?.takeIf(FirebaseUser::isAnonymous)
                if (anonymous == null) return@afterAnonymousSignIn signInWith(activity, auth, credential, null, onResult)
                anonymous.linkWithCredential(credential).addOnCompleteListener(activity) { task ->
                    val user = if (task.isSuccessful) task.result?.user else null
                    val collision = task.exception as? FirebaseAuthUserCollisionException
                    when {
                        user != null -> onResult(Result.Signed(user))
                        collision != null -> Profiles.release(activity, anonymous.uid) { released, profile ->
                            if (!released) return@release onResult(Result.Failed("release"))
                            // Best effort: nothing is left on the anonymous user, and signing in replaces it anyway.
                            anonymous.delete().addOnCompleteListener(activity) {
                                signInWith(activity, auth, collision.updatedCredential ?: credential, profile, onResult)
                            }
                        }
                        else -> onResult(Result.Failed(task.exception?.message ?: "link"))
                    }
                }
            }
        }
    }

    private fun signInWith(activity: Activity, auth: FirebaseAuth, credential: AuthCredential, released: Profile?, onResult: (Result) -> Unit) {
        auth.signInWithCredential(credential).addOnCompleteListener(activity) { task ->
            val user = if (task.isSuccessful) task.result?.user else null
            onResult(if (user != null) Result.Signed(user, released) else Result.Failed(task.exception?.message ?: "sign-in"))
        }
    }

    /** Asks for the Google account again (Firebase needs a recent sign-in to delete an account). */
    fun reauthenticate(activity: Activity, onResult: (Boolean) -> Unit) {
        val user = Cloud.user(activity) ?: return onResult(false)
        googleCredential(activity) { credential, _ ->
            if (credential == null) onResult(false)
            else user.reauthenticate(credential).addOnCompleteListener(activity) { onResult(it.isSuccessful) }
        }
    }

    fun signOut(activity: Activity) {
        Cloud.auth(activity)?.signOut()
        runCatching {
            CredentialManager.create(activity).clearCredentialStateAsync(
                androidx.credentials.ClearCredentialStateRequest(), CancellationSignal(), main,
                object : CredentialManagerCallback<Void?, androidx.credentials.exceptions.ClearCredentialException> {
                    override fun onResult(result: Void?) = Unit
                    override fun onError(e: androidx.credentials.exceptions.ClearCredentialException) = Unit
                })
        }
    }

    private fun googleCredential(activity: Activity, done: (AuthCredential?, Result?) -> Unit) {
        val clientId = Cloud.webClientId(activity) ?: return done(null, Result.Failed("no web client id"))
        val request = GetCredentialRequest.Builder().addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build()).build()
        runCatching {
            CredentialManager.create(activity).getCredentialAsync(activity, request, CancellationSignal(), main,
                object : CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
                    override fun onResult(result: GetCredentialResponse) {
                        val credential = result.credential
                        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                            val token = runCatching { GoogleIdTokenCredential.createFrom(credential.data).idToken }.getOrNull()
                            if (token != null) done(GoogleAuthProvider.getCredential(token, null), null)
                            else done(null, Result.Failed("bad token"))
                        } else done(null, Result.Failed("unexpected credential"))
                    }

                    override fun onError(e: GetCredentialException) =
                        done(null, if (e is GetCredentialCancellationException) Result.Cancelled else Result.Failed(e.type))
                })
        }.onFailure { done(null, Result.Failed(it.message ?: "credential manager")) }
    }
}
