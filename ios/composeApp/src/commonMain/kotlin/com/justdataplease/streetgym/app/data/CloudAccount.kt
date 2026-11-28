package com.justdataplease.streetgym.app.data

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.justdataplease.streetgym.billing.Tier

/** The line under Settings › Profile's buttons while something runs or after it failed (Android: its status TextView). */
enum class AccountStatus { WORKING, OFFLINE, SIGN_IN_FAILED, SYNC_FAILED }

/** Why a profile wasn't saved (Android: Usernames.problem and saveProfile's reasons). */
enum class ProfileProblem { INVALID, NOT_ALLOWED, TAKEN, FAILED }

/**
 * The account, the profile, Pro's cloud copy, the score on the leaderboard and progress analytics, as the screens use
 * them (android-streetgym: account/Cloud.kt, ui/AccountSettingsView.kt, and HomeActivity's cloudChanged, afterSync,
 * publishScore and syncAnalytics), over the phone's [account] and [docs]. Everything is kept under Android's names in
 * [store]. The screens read [user], [revision] and [status], which change as the account does.
 *
 * Anyone may pick a username (an anonymous user holds it); Connect Google, or Sign in with Apple on iPhone, links that
 * user, so the username, the score and Pro's cloud copy follow to a new phone. [tier] gates what Android gates: the
 * boards and the score for Plus and Pro ([Tier.account]), the cloud copy for Pro ([Tier.cloud]). [score] is the
 * athlete's points now (null before the skills are read); [refresh] redraws the screens after a sync brought progress.
 */
@Stable
class CloudAccount(
    val account: Account,
    private val docs: CloudDocuments?,
    store: KeyValueStore,
    progress: StoredProgress,
    library: LibraryStore,
    private val tier: () -> Tier,
    private val now: () -> Long,
    private val score: () -> Score?,
    private val refresh: () -> Unit,
    sharing: () -> Boolean = { false },
    appSet: AppSetSource = AppSetSource.None,
) {
    val store = AccountStore(store)
    val profiles = Profiles(docs, this.store)
    val boards = LeaderboardRepository(docs, store, now)
    val blocked = BlockedNames(store)
    val sync = CloudSync(docs, { signedIn()?.uid }, progress, library, this.store, profiles, tier, now)
    val analytics = Analytics(store, docs?.let { CloudAnalyticsBackend(it, ::signInAnonymously) }, appSet, sharing, now)

    /** Firebase is in this build (Android: Cloud.available): Settings › Profile and the boards show only then. */
    val available: Boolean get() = account.available && docs != null

    /** Whoever is signed in, the anonymous user included (Android: Cloud.profileUser). */
    var user by mutableStateOf(account.user.value)
        private set

    /** Bumped when the profile, the last sync or the blocked names change, so what shows them is drawn again. */
    var revision by mutableIntStateOf(0)
        private set

    var status by mutableStateOf<AccountStatus?>(null)

    /** An account action is under way; another tap waits (Android: busy). */
    var busy = false
        private set

    /** The account signed in (Google or Apple), never the anonymous user (Android: Cloud.user). */
    fun signedIn(): AccountUser? = user?.takeUnless(AccountUser::anonymous)

    /** The cached profile of whoever holds one now. */
    fun profile(): Profile? {
        revision
        return user?.uid?.let(store::profile)
    }

    /** The user Firebase reports now; called as it changes and after every action. */
    fun refreshUser() {
        user = account.user.value
        revision++
    }

    /** Follows Firebase's user while the app runs (Android: the AuthStateListener). */
    suspend fun follow() {
        account.user.collect { value ->
            if (value != user) {
                user = value
                revision++
            }
        }
    }

    // ---- Anonymous sign-in, one at a time (Android: Cloud.signInAnonymously, afterAnonymousSignIn)

    private var signingIn: MutableList<(AccountUser?) -> Unit>? = null

    /** Whoever is signed in, else a new anonymous user; [done] gets null when no one could sign in. */
    fun signInAnonymously(done: (AccountUser?) -> Unit) {
        account.user.value?.let { return done(it) }
        signingIn?.let { it += done; return }
        val waiting = mutableListOf(done)
        signingIn = waiting
        account.signInAnonymously { signed ->
            signingIn = null
            refreshUser()
            waiting.forEach { it(signed) }
        }
    }

    /** Runs [block] once no anonymous sign-in is under way: one finishing after an account's sign-in would replace it. */
    private fun afterAnonymousSignIn(block: () -> Unit) {
        signingIn?.let { it += { block() } } ?: block()
    }

    // ---- Settings › Profile (Android: AccountSettingsView)

    /** "Choose a username": signs in anonymously when no one is, then [edit] opens the profile for that user. */
    fun pickUsername(edit: (uid: String) -> Unit) {
        if (busy) return
        account.user.value?.let { return edit(it.uid) }
        busy = true
        status = AccountStatus.WORKING
        signInAnonymously { signed ->
            busy = false
            if (signed == null) status = AccountStatus.OFFLINE
            else {
                status = null
                edit(signed.uid)
            }
        }
    }

    /**
     * Connect Google or Sign in with Apple. A linked anonymous user keeps its uid, so the profile stays. An account with
     * a user of its own takes over: this phone's profile is released first, the account's own profile loads, and when it
     * has none the released name is claimed again for it (Android: connect and GoogleAccount.signIn).
     */
    fun connect(provider: SignInProvider) {
        if (busy) return
        busy = true
        status = AccountStatus.WORKING
        afterAnonymousSignIn {
            account.link(provider) { result ->
                when (result) {
                    is LinkResult.Linked -> signed(result.user, null)
                    is LinkResult.Collision -> profiles.release(result.anonymousUid) { released, profile ->
                        if (!released) {
                            account.dropTakeOver()
                            return@release failed(AccountStatus.SIGN_IN_FAILED)
                        }
                        account.takeOver { user -> if (user == null) failed(AccountStatus.SIGN_IN_FAILED) else signed(user, profile) }
                    }
                    LinkResult.Cancelled -> {
                        busy = false
                        status = null
                    }
                    is LinkResult.Failed -> failed(AccountStatus.SIGN_IN_FAILED)
                }
            }
        }
    }

    private fun signed(user: AccountUser, released: Profile?) {
        profiles.load(user.uid) { profile ->
            val reclaim = released?.takeIf { profile == null }
            fun done() {
                busy = false
                status = null
                refreshUser()
                sync.sync(force = true) { afterSync(it) }
            }
            if (reclaim == null) done() else profiles.save(user.uid, reclaim) { done() }
        }
    }

    private fun failed(why: AccountStatus) {
        busy = false
        status = why
        refreshUser()
    }

    /**
     * Saves [profile] for [uid]; [done] gets null once saved, else the problem to show: the format, a name the
     * engine's filter refuses, taken, or couldn't save. A saved profile republishes the score under it (Android:
     * editProfile's Save, Usernames.problem, and saveProfile).
     */
    fun saveProfile(uid: String, profile: Profile, done: (ProfileProblem?) -> Unit = {}) {
        if (!Usernames.valid(profile.username)) return done(ProfileProblem.INVALID)
        if (!Usernames.allowed(profile.username)) return done(ProfileProblem.NOT_ALLOWED)
        save(uid, profile, done)
    }

    /** Android's saveProfile: the claim and what follows it, without the dialog's checks. */
    private fun save(uid: String, profile: Profile, done: (ProfileProblem?) -> Unit) {
        profiles.save(uid, profile) { claim ->
            when (claim) {
                Profiles.Claim.Saved -> {
                    store.savePublishedScore(uid, null, 0)
                    revision++
                    // Hidden: the score document goes now. Android forgets the published score first, so its
                    // publishScore finds nothing to take off and the name stays on the boards (reported to the owner).
                    if (profile.visible) publishScore() else boards.unpublish(uid) {}
                    done(null)
                }
                Profiles.Claim.Taken -> done(ProfileProblem.TAKEN)
                Profiles.Claim.Failed -> {
                    revision++
                    done(ProfileProblem.FAILED)
                }
            }
        }
    }

    /**
     * "Show me on the leaderboard": the profile saved with [visible]. Android's switch saves without the dialog's
     * checks, so a name chosen before the filter can still be hidden; the iPhone's does the same.
     */
    fun setVisible(visible: Boolean) {
        val uid = user?.uid ?: return
        val profile = store.profile(uid) ?: return
        save(uid, profile.copy(visible = visible)) {}
    }

    /** "Delete profile" of a profile kept without an account: its username, profile and score go, then the anonymous user. */
    fun deleteProfile(done: (Boolean) -> Unit) {
        val uid = user?.uid ?: return done(false)
        profiles.release(uid) { released, _ ->
            if (released) account.deleteAnonymous(uid)
            refreshUser()
            done(released)
        }
    }

    /**
     * "Delete account" (Apple 5.1.1(v), Android: confirmDelete): the account is asked for again, then the cloud copy,
     * the My workouts copy, the score, the username and the profile go, then the account itself, and analytics takes its
     * uid off this install's record. The profile is read again first, so a name changed on another phone is the one freed.
     */
    fun deleteAccount(done: (Boolean) -> Unit) {
        val uid = signedIn()?.uid ?: return done(false)
        account.reauthenticate { confirmed ->
            val docs = docs
            if (!confirmed || docs == null) return@reauthenticate done(false)
            profiles.load(uid) { profile ->
                docs.write(deletePaths(uid, profile).map(CloudWrite::Remove)) { cleared ->
                    if (!cleared) return@write done(false)
                    account.deleteUser { deleted ->
                        if (deleted) {
                            store.clear()
                            analytics.accountDeleted()
                        }
                        refreshUser()
                        done(deleted)
                    }
                }
            }
        }
    }

    /** "Sync now" (Pro): the merge both ways at once; a failure says so under the button. */
    fun syncNow() {
        status = AccountStatus.WORKING
        sync.sync(force = true) { result ->
            if (result == CloudSync.Result.Failed) status = AccountStatus.SYNC_FAILED
            else {
                status = null
                revision++
                afterSync(result)
            }
        }
    }

    /** "Sign out": everything on this phone stays. */
    fun signOut() {
        account.signOut()
        status = null
        refreshUser()
    }

    // ---- Blocking names on the leaderboard (Apple 1.2; Android has no block)

    /** [username] is left out of every board on this phone. */
    fun block(username: String) {
        blocked.block(username)
        revision++
    }

    /** Every blocked name shows again. */
    fun unblockAll() {
        blocked.clear()
        revision++
    }

    // ---- Keeping the cloud in step (Android: HomeActivity's cloudChanged, afterSync, publishScore, onResume)

    private var changedAt: Long? = null

    /**
     * Something to save changed on the phone (a finished class, a marked day, an exam, a reset, My workouts): the sync
     * and the score follow [SETTLE_MILLIS] later, once for a burst of changes; [tick] runs them.
     */
    fun changed() {
        if (!tier().account || user == null) return
        changedAt = now()
    }

    /** Called about every second while the app runs: a change that settled goes up. */
    fun tick() {
        val at = changedAt ?: return
        if (now() - at < SETTLE_MILLIS) return
        changedAt = null
        sync.sync(force = true) { afterSync(it) }
    }

    /** The app came to the screen: Pro catches up with other phones at most every six hours. */
    fun resumed() {
        refreshUser()
        sync.sync { afterSync(it) }
    }

    /** Another phone's progress may have arrived: the score follows, and the screens are drawn again. */
    private fun afterSync(result: CloudSync.Result) {
        publishScore()
        if (result == CloudSync.Result.Synced) {
            revision++
            refresh()
        }
    }

    /**
     * Plus or Pro with a visible profile: the score goes to the leaderboard when it changed, at most every ten minutes
     * ([Scores.shouldPublish]). A hidden profile takes its score off.
     */
    fun publishScore() {
        if (!tier().account) return
        val uid = user?.uid ?: return
        val profile = store.profile(uid) ?: return
        val published = store.publishedScore(uid)
        if (!profile.visible) {
            if (published != null) boards.unpublish(uid) { ok -> if (ok) store.savePublishedScore(uid, null, 0) }
            return
        }
        val current = score() ?: return
        val at = now()
        if (!Scores.shouldPublish(published?.first, published?.second ?: 0, current, at)) return
        boards.publish(uid, profile, current) { ok -> if (ok) store.savePublishedScore(uid, current, at) }
    }

    /** Start over: the score goes up again from zero, at once rather than after the usual ten minutes (Android: confirmReset). */
    fun progressReset() {
        user?.uid?.let { store.savePublishedScore(it, null, 0) }
        changed()
    }

    companion object {
        const val SETTLE_MILLIS = 3_000L

        /** What "Delete account" deletes, in Android's order; the username's claim only when there is a profile. */
        fun deletePaths(uid: String, profile: Profile?): List<String> = listOfNotNull(
            CloudSync.progressPath(uid), CloudSync.libraryPath(uid), "scores/$uid",
            profile?.let { Profiles.claim(Usernames.key(it.username)) }, Profiles.user(uid),
        )
    }
}
