package com.justdataplease.streetgym.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.v2.runComposeUiTest
import com.justdataplease.streetgym.app.data.AccountStatus
import com.justdataplease.streetgym.app.data.AccountStore
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.AppSettings
import com.justdataplease.streetgym.app.data.CloudAccount
import com.justdataplease.streetgym.app.data.CloudJson
import com.justdataplease.streetgym.app.data.CloudTime
import com.justdataplease.streetgym.app.data.CloudWrite
import com.justdataplease.streetgym.app.data.Countries
import com.justdataplease.streetgym.app.data.DeleteField
import com.justdataplease.streetgym.app.data.LibraryStore
import com.justdataplease.streetgym.app.data.MemoryStore
import com.justdataplease.streetgym.app.data.PhoneServices
import com.justdataplease.streetgym.app.data.Plans
import com.justdataplease.streetgym.app.data.Profile
import com.justdataplease.streetgym.app.data.ProfileProblem
import com.justdataplease.streetgym.app.data.Profiles
import com.justdataplease.streetgym.app.data.Program
import com.justdataplease.streetgym.app.data.ServerTime
import com.justdataplease.streetgym.app.data.SignInProvider
import com.justdataplease.streetgym.app.data.StoredProgress
import com.justdataplease.streetgym.app.data.Usernames
import com.justdataplease.streetgym.app.data.loadProgram
import com.justdataplease.streetgym.app.ui.Screen
import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.progress.NextClass
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Settings › Profile and the account (Android: AccountSettingsView, Cloud, GoogleAccount, Profiles) through a fake
 * Firebase that checks firestore.rules: no Firebase, a username without sign-in, its rules and its claim, Connect
 * Google and Sign in with Apple linking the same user, an account that already has a user taking over, Delete
 * profile, Sign out, Delete account, and the documents each writes, field by field as Android writes them.
 */
@OptIn(ExperimentalTestApi::class)
class AccountTest {
    private var time = 20_775 * DAY + 12 * 3_600_000L

    private inner class Phone(
        tier: Tier = Tier.PLUS,
        val account: FakeAccount = FakeAccount(),
        server: MutableMap<String, Map<String, Any?>> = linkedMapOf(),
        val store: MemoryStore = MemoryStore(),
    ) {
        val cloud = FakeCloud({ account.user.value }, { time }, server)
        val model: AppModel = AppSettings(store).let { settings ->
            settings.placementDone = true
            AppModel(program, StoredProgress(store), now = { time }, zone = TimeZone.UTC, settings = settings, library = LibraryStore(store),
                plans = Plans(store, floor = tier), adStore = store, services = PhoneServices(account = account, documents = cloud, region = "GR"))
        }
        val state: CloudAccount get() = model.cloud

        /** A username picked without signing in, as Choose a username does. */
        fun pick(name: String, country: String = "GR"): String {
            var uid = ""
            state.pickUsername { uid = it }
            var problem: ProfileProblem? = ProfileProblem.FAILED
            state.saveProfile(uid, Profile(name, country)) { problem = it }
            assertNull(problem, cloud.refused)
            return uid
        }
    }

    @Test
    fun withoutFirebaseTheProfileStaysHidden() = runComposeUiTest {
        val store = MemoryStore()
        AppSettings(store).placementDone = true
        val model = AppModel(program, StoredProgress(store), now = { time }, zone = TimeZone.UTC, settings = AppSettings(store), adStore = store)
        assertFalse(model.cloud.available)
        setContent { StreetGymApp(model, PaddingValues(), startAt = Screen.Settings) }
        assertTrue(onAllNodesWithText("PROFILE").fetchSemanticsNodes().isEmpty())
        assertTrue(onAllNodesWithText("Choose a username", ignoreCase = true).fetchSemanticsNodes().isEmpty())
        // Your data and its switch show in every build (Android: shareProgressNote in Settings).
        onNodeWithText("YOUR DATA").performScrollTo().assertExists()
    }

    @Test
    fun aUsernameNeedsNoSignInAndClaimsItsName() {
        val phone = Phone()
        assertTrue(phone.state.available)
        assertNull(phone.state.user)
        val uid = phone.pick("Alexa_7")
        assertTrue(phone.state.user!!.anonymous)
        assertNull(phone.state.signedIn())
        // users/{uid} and usernames/{lower-case}, as Android's Profiles.save writes them.
        assertEquals(mapOf("username" to "Alexa_7", "country" to "GR", "visible" to true, "updated_at" to CloudTime(time)),
            phone.cloud.documents["users/$uid"])
        assertEquals(mapOf("uid" to uid), phone.cloud.documents["usernames/alexa_7"])
        // Kept on the phone under Android's names.
        assertEquals("""{"username":"Alexa_7","country":"GR","visible":true}""", phone.store.get("streetgym_account.profile_$uid"))
        assertEquals(Profile("Alexa_7", "GR"), phone.state.profile())

        // A new name frees the old one in the same transaction; another phone can then take it.
        phone.state.saveProfile(uid, Profile("Alexa8", "CY")) { assertNull(it) }
        assertNull(phone.cloud.documents["usernames/alexa_7"])
        assertEquals(mapOf("uid" to uid), phone.cloud.documents["usernames/alexa8"])
        assertEquals("Alexa8", phone.cloud.documents["users/$uid"]!!["username"])
    }

    @Test
    fun usernamesFollowAndroidsRules() {
        listOf("abc", "Alexa_7", "a.b_c", "ABCDEFGHIJKLMNOPQRST").forEach { assertTrue(Usernames.valid(it), it) }
        listOf("ab", "has space", "ÄÖÜ", "dash-name", "ABCDEFGHIJKLMNOPQRSTU", "").forEach { assertFalse(Usernames.valid(it), it) }
        assertEquals("alexa_7", Usernames.key("Alexa_7"))
        // The problems the dialog shows: not 3–20 letters, taken (unique ignoring case), and couldn't save.
        val phone = Phone()
        val other = Phone(server = phone.cloud.documents)
        other.pick("Runner")
        var uid = ""
        phone.state.pickUsername { uid = it }
        var problem: ProfileProblem? = null
        phone.state.saveProfile(uid, Profile("no", "GR")) { problem = it }
        assertEquals(ProfileProblem.INVALID, problem)
        // A name the engine's filter refuses (Android: Usernames.problem): nothing is claimed or saved.
        listOf("Malakas_7", "Sh1tHead", "admin", "Street_Gym").forEach { name ->
            problem = null
            phone.state.saveProfile(uid, Profile(name, "GR")) { problem = it }
            assertEquals(ProfileProblem.NOT_ALLOWED, problem, name)
            assertFalse(Usernames.allowed(name), name)
            assertNull(phone.cloud.documents["usernames/${Usernames.key(name)}"], name)
        }
        phone.state.saveProfile(uid, Profile("RUNNER", "GR")) { problem = it }
        assertEquals(ProfileProblem.TAKEN, problem)
        phone.cloud.online = false
        phone.state.saveProfile(uid, Profile("Fresh", "GR")) { problem = it }
        assertEquals(ProfileProblem.FAILED, problem)
        assertNull(phone.state.profile())
    }

    @Test
    fun aNameChosenBeforeTheFilterCanBeHiddenButNotSavedAgain() {
        val phone = Phone()
        val uid = phone.pick("Old_Name")
        // As if "Sh1t_Head" had been picked before the filter came: its claim and profile on the server and the phone.
        phone.cloud.documents.remove("usernames/old_name")
        phone.cloud.documents["usernames/sh1t_head"] = mapOf("uid" to uid)
        phone.cloud.documents["users/$uid"] = phone.cloud.documents["users/$uid"]!! + ("username" to "Sh1t_Head")
        AccountStore(phone.store).saveProfile(uid, Profile("Sh1t_Head", "GR"))
        // The switch saves without the dialog's checks, as Android's does, so the name can be taken off the boards.
        phone.state.setVisible(false)
        assertEquals(false, phone.cloud.documents["users/$uid"]!!["visible"])
        assertEquals(Profile("Sh1t_Head", "GR", visible = false), phone.state.profile())
        // Saving it from the dialog again is refused, as Android's dialog refuses it.
        var problem: ProfileProblem? = null
        phone.state.saveProfile(uid, Profile("Sh1t_Head", "CY")) { problem = it }
        assertEquals(ProfileProblem.NOT_ALLOWED, problem)
        assertEquals("GR", phone.cloud.documents["users/$uid"]!!["country"])
    }

    @Test
    fun theDialogSaysWhenANameIsntAllowed() = runComposeUiTest {
        val phone = Phone()
        setContent { StreetGymApp(phone.model, PaddingValues(), startAt = Screen.Settings) }
        onNodeWithText("CHOOSE A USERNAME").performClick()
        waitForIdle()
        onNodeWithContentDescription("Username (3–20 letters, digits, _ or .)").performTextInput("Admin")
        onNodeWithText("SAVE").performClick()
        waitForIdle()
        // Android's text, from its strings_account.xml.
        onNodeWithText("That username isn’t allowed. Try another.").assertExists()
        assertNull(phone.state.profile())
    }

    @Test
    fun offlineTheFirstUsernameSaysSo() {
        val phone = Phone()
        phone.account.online = false
        var opened = false
        phone.state.pickUsername { opened = true }
        assertFalse(opened)
        assertEquals(AccountStatus.OFFLINE, phone.state.status)
    }

    @Test
    fun connectingGoogleOrAppleKeepsTheSameUserAndProfile() {
        for (provider in SignInProvider.entries) {
            val phone = Phone()
            val uid = phone.pick("Linker${provider.ordinal}")
            phone.state.connect(provider)
            val account = phone.state.signedIn()!!
            assertEquals(uid, account.uid)
            assertEquals(provider, account.provider)
            assertEquals(phone.account.identities[provider], account.email)
            assertEquals("Linker${provider.ordinal}", phone.state.profile()!!.username)
            assertNull(phone.state.status)
            // Plus keeps its progress on the phone: no cloud copy.
            assertTrue(phone.cloud.documents.keys.none { it.contains("/data/") })
        }
    }

    @Test
    fun anAccountWithAUserOfItsOwnTakesOverAndClaimsTheNameAgain() {
        val server = linkedMapOf<String, Map<String, Any?>>()
        val owners = mutableMapOf<Pair<SignInProvider, String>, String>()
        // The athlete signed in with Google on another phone before and never picked a name there.
        val before = Phone(account = FakeAccount(owners = owners), server = server)
        before.state.connect(SignInProvider.GOOGLE)
        val owner = before.state.signedIn()!!.uid
        val phone = Phone(account = FakeAccount(owners = owners), server = server)
        val anonymous = phone.pick("Taker")
        phone.state.connect(SignInProvider.GOOGLE)
        assertEquals(owner, phone.state.signedIn()!!.uid)
        // This phone's profile was released and the anonymous user deleted; the name was claimed again for the account.
        assertTrue(anonymous in phone.account.deletedUsers)
        assertNull(server["users/$anonymous"])
        assertEquals(mapOf("uid" to owner), server["usernames/taker"])
        assertEquals("Taker", server["users/$owner"]!!["username"])
        assertEquals("Taker", phone.state.profile()!!.username)
    }

    @Test
    fun cancellingTheSheetChangesNothing() {
        val phone = Phone()
        val uid = phone.pick("Stays")
        phone.account.cancel = true
        phone.state.connect(SignInProvider.APPLE)
        assertNull(phone.state.status)
        assertFalse(phone.state.busy)
        assertEquals(uid, phone.state.user!!.uid)
        assertTrue(phone.state.user!!.anonymous)
    }

    @Test
    fun deletingTheProfileFreesTheName() {
        val phone = Phone()
        val uid = phone.pick("Gone")
        var deleted: Boolean? = null
        phone.state.deleteProfile { deleted = it }
        assertEquals(true, deleted)
        assertNull(phone.cloud.documents["users/$uid"])
        assertNull(phone.cloud.documents["usernames/gone"])
        assertNull(phone.store.get("streetgym_account.profile_$uid"))
        assertTrue(uid in phone.account.deletedUsers)
        assertNull(phone.state.user)
        // The released documents, in Android's order: the score, the claim, the profile.
        assertEquals(listOf("scores/$uid", "usernames/gone", "users/$uid"), Profiles.releasePaths(uid, Profile("Gone", "GR")))
    }

    @Test
    fun signingOutKeepsEverythingOnThePhone() {
        val phone = Phone(Tier.PRO)
        phone.pick("Leaver")
        phone.state.connect(SignInProvider.GOOGLE)
        phone.model.completeDay(ordered.first().id)
        phone.state.signOut()
        assertNull(phone.state.user)
        assertTrue(phone.model.isCompleted(ordered.first().id))
    }

    @Test
    fun deletingTheAccountDeletesItsDocumentsAndItsUser() {
        val phone = Phone(Tier.PRO)
        val uid = phone.pick("Deleter")
        phone.state.connect(SignInProvider.APPLE)
        phone.model.completeDay(ordered.first().id)
        time += 4_000L
        phone.state.tick()
        phone.state.publishScore()
        assertTrue(phone.cloud.documents.keys.containsAll(listOf("users/$uid/data/progress", "users/$uid/data/library", "users/$uid")))
        var deleted: Boolean? = null
        phone.state.deleteAccount { deleted = it }
        assertEquals(true, deleted)
        assertEquals(listOf("users/$uid/data/progress", "users/$uid/data/library", "scores/$uid", "usernames/deleter", "users/$uid"),
            phone.cloud.writes.last().map(CloudWrite::path))
        assertTrue(phone.cloud.documents.keys.none { it.contains(uid) || it == "usernames/deleter" })
        assertTrue(uid in phone.account.deletedUsers)
        assertNull(phone.state.user)
        assertNull(AccountStore(phone.store).profile(uid))
        assertEquals(0L, AccountStore(phone.store).lastSyncAt)
        // Everything on the phone stays.
        assertTrue(phone.model.isCompleted(ordered.first().id))
    }

    @Test
    fun deletingAskesForTheAccountAgainFirst() {
        val phone = Phone(Tier.PRO)
        phone.pick("Careful")
        phone.state.connect(SignInProvider.GOOGLE)
        phone.account.reauthenticates = false
        var deleted: Boolean? = null
        phone.state.deleteAccount { deleted = it }
        assertEquals(false, deleted)
        assertNotEquals(null, phone.cloud.documents["usernames/careful"])
        assertNotEquals(null, phone.state.signedIn())
    }

    @Test
    fun theSettingsCardFollowsTheAccount() = runComposeUiTest {
        val phone = Phone()
        setContent { StreetGymApp(phone.model, PaddingValues(), startAt = Screen.Settings) }
        onNodeWithText("Pick a username for the leaderboard. No sign-in needed.").assertExists()
        onNodeWithText("CHOOSE A USERNAME").performClick()
        waitForIdle()
        onNodeWithText("Your leaderboard profile").assertExists()
        // The phone's region is the first country.
        onNodeWithContentDescription("${Countries.flag("GR")} Greece").assertExists()
        onNodeWithContentDescription("Username (3–20 letters, digits, _ or .)").performTextInput("x")
        onNodeWithText("SAVE").performClick()
        waitForIdle()
        onNodeWithText("Use 3–20 letters, digits, _ or .").assertExists()
        onNodeWithContentDescription("Username (3–20 letters, digits, _ or .)").performTextInput("yz_Athlete")
        onNodeWithText("SAVE").performClick()
        waitForIdle()
        onNodeWithText("${Countries.flag("GR")} xyz_Athlete", ignoreCase = true).assertExists()
        onNodeWithText("Show me on the leaderboard").assertExists()
        // Both sign-ins are offered on Plus, Apple's beside Google's.
        onNodeWithText("Connect Google or Apple to keep your username on a new phone. Cloud backups and restore are part of Pro.").assertExists()
        onNodeWithContentDescription("Sign in with Apple").assertExists()
        onNodeWithText("CONNECT GOOGLE").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Google: athlete@gmail.com").performScrollTo().assertExists()
        onNodeWithText("Your progress stays on this phone. Cloud backups and restore are part of Pro.").assertExists()
        onNodeWithText("SIGN OUT").assertExists()
        onNodeWithText("DELETE ACCOUNT").performScrollTo().performClick()
        waitForIdle()
        onNodeWithText("Delete your account?").assertExists()
    }

    @Test
    fun theParkGreetsTheUsername() {
        val phone = Phone()
        assertNull(phone.model.greetedName())
        phone.pick("Greeted")
        assertEquals("Greeted", phone.model.greetedName())
    }

    @Test
    fun documentsCrossToSwiftAsJson() {
        val writes = listOf(
            CloudWrite.Put("scores/u1", mapOf("v" to 4, "points" to 1200L, "visible" to true, "updated_at" to ServerTime,
                "list" to listOf("a", null), "gone" to DeleteField, "at" to CloudTime(5L)), mergeFields = listOf("v", "gone")),
            CloudWrite.Remove("users/u1"),
        )
        assertEquals("""[{"path":"scores/u1","op":"set","fields":{"v":4,"points":1200,"visible":true,"updated_at":{"${'$'}server":"time"},""" +
            """"list":["a",null],"gone":{"${'$'}server":"delete"},"at":{"${'$'}time":5}},"merge":["v","gone"]},{"path":"users/u1","op":"delete"}]""",
            CloudJson.encodeWrites(writes))
        val read = CloudJson.decodeDocuments("""{"users/u1":{"username":"A","updated_at":{"${'$'}time":42},"n":[1,2]},"users/u2":null}""")!!
        assertEquals(mapOf("username" to "A", "updated_at" to CloudTime(42), "n" to listOf(1, 2)), read["users/u1"])
        assertTrue("users/u2" in read && read["users/u2"] == null)
        assertNull(CloudJson.decodeDocuments(null))
    }

    @Test
    fun countriesAreAndroidsWithTheirFlags() {
        assertEquals("🇬🇷", Countries.flag("GR"))
        assertEquals("Greece", Countries.name("GR"))
        assertTrue(Countries.valid("CY") && !Countries.valid("XX"))
        val all = Countries.all()
        assertEquals(all.sortedBy { it.second }, all)
        assertTrue(all.size > 240)
    }

    private companion object {
        const val DAY = 86_400_000L
        val program: Program by lazy { runBlocking { loadProgram() } }
        val ordered: List<TrainingProgram> by lazy { NextClass.ordered(program.catalog) }
    }
}

