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
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider

/** Sign in with Google (Pro only) through Credential Manager, then Firebase Auth. */
object GoogleAccount {
    // Activity.getMainExecutor needs API 28; the app supports 23.
    private val main = java.util.concurrent.Executor { android.os.Handler(android.os.Looper.getMainLooper()).post(it) }

    sealed interface Result {
        data class Signed(val user: FirebaseUser) : Result
        data object Cancelled : Result
        data class Failed(val reason: String) : Result
    }

    fun signIn(activity: Activity, onResult: (Result) -> Unit) {
        googleCredential(activity) { credential, failure ->
            val auth = Cloud.auth(activity)
            if (credential == null || auth == null) { onResult(failure ?: Result.Failed("unavailable")); return@googleCredential }
            auth.signInWithCredential(credential).addOnCompleteListener(activity) { task ->
                val user = if (task.isSuccessful) task.result?.user else null
                onResult(if (user != null) Result.Signed(user) else Result.Failed(task.exception?.message ?: "sign-in"))
            }
        }
    }

    /** Asks for the Google account again (Firebase needs a recent sign-in to delete an account). */
    fun reauthenticate(activity: Activity, onResult: (Boolean) -> Unit) {
        val user = Cloud.auth(activity)?.currentUser ?: return onResult(false)
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
