package com.justdataplease.streetgym.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.justdataplease.streetgym.app.data.Account
import com.justdataplease.streetgym.app.data.AccountUser
import com.justdataplease.streetgym.app.data.AppSetId
import com.justdataplease.streetgym.app.data.AppSetSource
import com.justdataplease.streetgym.app.data.CloudDocuments
import com.justdataplease.streetgym.app.data.CloudJson
import com.justdataplease.streetgym.app.data.CloudPlan
import com.justdataplease.streetgym.app.data.CloudWrite
import com.justdataplease.streetgym.app.data.LinkResult
import com.justdataplease.streetgym.app.data.SignInProvider
import com.justdataplease.streetgym.app.ui.AppleButton
import com.justdataplease.streetgym.json.JSONArray
import com.justdataplease.streetgym.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import platform.AuthenticationServices.ASAuthorizationAppleIDButton
import platform.AuthenticationServices.ASAuthorizationAppleIDButtonStyle
import platform.AuthenticationServices.ASAuthorizationAppleIDButtonTypeSignIn
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.currentLocale
import com.justdataplease.streetgym.app.resources.Res
import com.justdataplease.streetgym.app.resources.account_sign_in_apple
import org.jetbrains.compose.resources.stringResource
import platform.UIKit.UIDevice
import kotlin.concurrent.AtomicReference

/*
 * Firebase on iPhone (iosApp/StreetGym/FirebaseAccount.swift): Swift talks to Firebase Auth, Google Sign-In, Sign in
 * with Apple and Firestore, as Kotlin can't read their Swift APIs; Kotlin (common code) decides what to sign in, read
 * and write. Swift sees these as the protocols NativeAccount, NativeAccountListener, NativeCloud and NativeCloudListener;
 * every answer comes back on the main thread, but NativeCloudListener.decide, which Firestore runs on its own thread.
 * Without GoogleService-Info.plist Swift starts nothing and says so (configured(available = false)).
 */

/** What Swift's Firebase Auth does. Each request carries a number its answer comes back with. */
interface NativeAccount {
    /** Hands over the listener, which hears at once whether Firebase is configured and who is signed in. */
    fun startAccount(listener: NativeAccountListener)
    fun signInAnonymously(request: Long)
    /** Google ("google.com") or Apple ("apple.com"): the sign-in sheet, then link or sign in. */
    fun link(request: Long, provider: String)
    fun takeOver(request: Long)
    fun dropTakeOver()
    fun reauthenticate(request: Long)
    fun signOut()
    fun deleteUser(request: Long)
    fun deleteAnonymous(uid: String)
}

/** What Swift's Firebase Auth tells the app. */
interface NativeAccountListener {
    /** Firebase is configured ([available]); Google can sign in (its client id and URL type); Apple can. */
    fun configured(available: Boolean, google: Boolean, apple: Boolean)

    /** Whoever is signed in now; [uid] null for no one. [provider] "google.com", "apple.com" or null. */
    fun userChanged(uid: String?, anonymous: Boolean, email: String?, provider: String?)

    /** An anonymous sign-in or a take-over ended: the user's uid, or null. */
    fun signedIn(request: Long, uid: String?)

    /** A link ended: [outcome] "linked", "collision" (with the anonymous user's [uid]), "cancelled" or "failed". */
    fun linked(request: Long, outcome: String, uid: String?)

    /** A reauthentication or a deletion ended. */
    fun finished(request: Long, ok: Boolean)
}

/** What Swift's Firestore does: whole documents as JSON (common Kotlin's CloudJson). */
interface NativeCloud {
    /** Hands over the listener its answers go to. */
    fun attachCloud(listener: NativeCloudListener)

    /** Reads [paths] from the server. */
    fun read(request: Long, paths: List<String>)

    /** One batch: [writes] as CloudJson.encodeWrites writes it. */
    fun write(request: Long, writes: String)

    /** A transaction: reads [paths], asks [NativeCloudListener.decide], writes what it planned. */
    fun transaction(request: Long, paths: List<String>)
}

/** What Swift's Firestore tells the app. */
interface NativeCloudListener {
    /** {"path": fields or null}, or null when the read failed. */
    fun readDone(request: Long, documents: String?)
    fun writeDone(request: Long, ok: Boolean)

    /**
     * Inside a transaction, on Firestore's thread: the documents read, as in [readDone]; returns
     * {"writes": [...], "outcome": "..."}. May run more than once.
     */
    fun decide(request: Long, documents: String): String

    /** The transaction's outcome, or null when it failed. */
    fun transactionDone(request: Long, outcome: String?)
}

/** The app's [Account] over Swift's [NativeAccount]. */
internal class IosAccount(private val native: NativeAccount) : Account, NativeAccountListener {
    override var available = false
        private set
    override var providers: Set<SignInProvider> = emptySet()
        private set
    override val user = MutableStateFlow<AccountUser?>(null)
    private var next = 0L
    private val signing = mutableMapOf<Long, (AccountUser?) -> Unit>()
    private val linking = mutableMapOf<Long, (LinkResult) -> Unit>()
    private val finishing = mutableMapOf<Long, (Boolean) -> Unit>()

    init {
        native.startAccount(this)
    }

    override fun signInAnonymously(done: (AccountUser?) -> Unit) {
        if (!available) return done(null)
        val request = ++next
        signing[request] = done
        native.signInAnonymously(request)
    }

    override fun link(provider: SignInProvider, done: (LinkResult) -> Unit) {
        if (!available || provider !in providers) return done(LinkResult.Failed("unavailable"))
        val request = ++next
        linking[request] = done
        native.link(request, provider.id)
    }

    override fun takeOver(done: (AccountUser?) -> Unit) {
        val request = ++next
        signing[request] = done
        native.takeOver(request)
    }

    override fun dropTakeOver() = native.dropTakeOver()

    override fun reauthenticate(done: (Boolean) -> Unit) = finish(done, native::reauthenticate)

    override fun signOut() = native.signOut()

    override fun deleteUser(done: (Boolean) -> Unit) = finish(done, native::deleteUser)

    override fun deleteAnonymous(uid: String) = native.deleteAnonymous(uid)

    private fun finish(done: (Boolean) -> Unit, call: (Long) -> Unit) {
        if (!available) return done(false)
        val request = ++next
        finishing[request] = done
        call(request)
    }

    override fun configured(available: Boolean, google: Boolean, apple: Boolean) {
        this.available = available
        providers = buildSet {
            if (available && google) add(SignInProvider.GOOGLE)
            if (available && apple) add(SignInProvider.APPLE)
        }
    }

    override fun userChanged(uid: String?, anonymous: Boolean, email: String?, provider: String?) {
        user.value = uid?.let { AccountUser(it, anonymous, email?.takeIf(String::isNotBlank), SignInProvider.of(provider)) }
    }

    override fun signedIn(request: Long, uid: String?) {
        signing.remove(request)?.invoke(uid?.let { user.value?.takeIf { current -> current.uid == it } ?: AccountUser(it, anonymous = true) })
    }

    override fun linked(request: Long, outcome: String, uid: String?) {
        val done = linking.remove(request) ?: return
        done(when (outcome) {
            "linked" -> user.value?.takeIf { uid == null || it.uid == uid }?.let(LinkResult::Linked) ?: LinkResult.Failed("no user")
            "collision" -> uid?.let(LinkResult::Collision) ?: LinkResult.Failed("collision")
            "cancelled" -> LinkResult.Cancelled
            else -> LinkResult.Failed(outcome)
        })
    }

    override fun finished(request: Long, ok: Boolean) {
        finishing.remove(request)?.invoke(ok)
    }
}

/** The app's [CloudDocuments] over Swift's [NativeCloud]. */
internal class IosCloud(private val native: NativeCloud) : CloudDocuments, NativeCloudListener {
    private var next = 0L
    private val reading = mutableMapOf<Long, (Map<String, Map<String, Any?>?>?) -> Unit>()
    private val writing = mutableMapOf<Long, (Boolean) -> Unit>()
    private val ending = mutableMapOf<Long, (String?) -> Unit>()
    /** Each transaction's planner, which Firestore's thread reads. */
    private val planners = AtomicReference<Map<Long, (Map<String, Map<String, Any?>?>) -> CloudPlan>>(emptyMap())

    override fun read(paths: List<String>, done: (Map<String, Map<String, Any?>?>?) -> Unit) {
        val request = ++next
        reading[request] = done
        native.read(request, paths)
    }

    override fun write(writes: List<CloudWrite>, done: (Boolean) -> Unit) {
        if (writes.isEmpty()) return done(true)
        val request = ++next
        writing[request] = done
        native.write(request, CloudJson.encodeWrites(writes))
    }

    override fun transaction(paths: List<String>, decide: (Map<String, Map<String, Any?>?>) -> CloudPlan, done: (String?) -> Unit) {
        val request = ++next
        ending[request] = done
        while (true) {
            val now = planners.value
            if (planners.compareAndSet(now, now + (request to decide))) break
        }
        native.transaction(request, paths)
    }

    override fun readDone(request: Long, documents: String?) {
        reading.remove(request)?.invoke(CloudJson.decodeDocuments(documents))
    }

    override fun writeDone(request: Long, ok: Boolean) {
        writing.remove(request)?.invoke(ok)
    }

    override fun decide(request: Long, documents: String): String {
        val plan = planners.value[request]?.let { decide -> CloudJson.decodeDocuments(documents)?.let(decide) }
            ?: CloudPlan(emptyList(), "")
        return JSONObject().put("writes", JSONArray(CloudJson.encodeWrites(plan.writes))).put("outcome", plan.outcome).toString()
    }

    override fun transactionDone(request: Long, outcome: String?) {
        while (true) {
            val now = planners.value
            if (planners.compareAndSet(now, now - request)) break
        }
        ending.remove(request)?.invoke(outcome?.takeIf(String::isNotEmpty))
    }
}

/**
 * The install's chain id on iPhone: the identifier for vendor, the same for every app of the developer on this phone
 * until the last of them is deleted (Android: the App Set ID's developer scope). It can be missing just after a restart,
 * before the phone is unlocked; then the install document keeps the one it had.
 */
internal object VendorId : AppSetSource {
    override fun fetch(done: (AppSetId?) -> Unit) =
        done(UIDevice.currentDevice.identifierForVendor?.UUIDString?.lowercase()?.let { AppSetId(it, "developer") })
}

/** The phone's country, two capitals, for the leaderboard's own board and the profile's first pick (Android: Locale's country). */
internal fun phoneRegion(): String? =
    (NSLocale.currentLocale.objectForKey(NSLocaleCountryCode) as? String)?.uppercase()?.takeIf { it.length == 2 }

/**
 * Apple's own Sign in with Apple button (AuthenticationServices), black, or white on Night Park, as wide as the card.
 * Compose takes the tap over it (the button itself is drawn only), so VoiceOver hears it as one button.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal object IosAppleButton : AppleButton {
    @Composable
    override fun Draw(dark: Boolean, modifier: Modifier, onClick: () -> Unit) {
        val label = stringResource(Res.string.account_sign_in_apple)
        Box(modifier.fillMaxWidth().height(52.dp)) {
            key(dark) {
                UIKitView(
                    factory = {
                        ASAuthorizationAppleIDButton(authorizationButtonType = ASAuthorizationAppleIDButtonTypeSignIn,
                            authorizationButtonStyle = if (dark) ASAuthorizationAppleIDButtonStyle.ASAuthorizationAppleIDButtonStyleWhite else ASAuthorizationAppleIDButtonStyle.ASAuthorizationAppleIDButtonStyleBlack)
                    },
                    modifier = Modifier.matchParentSize(),
                    properties = UIKitInteropProperties(interactionMode = null, isNativeAccessibilityEnabled = false),
                )
            }
            Box(Modifier.matchParentSize().clickable(onClick = onClick).semantics {
                contentDescription = label
                role = Role.Button
            })
        }
    }
}
