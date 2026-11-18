package com.justdataplease.streetgym.app

import com.justdataplease.streetgym.app.data.Analytics
import com.justdataplease.streetgym.app.data.AnalyticsPayload
import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.AppSetId
import com.justdataplease.streetgym.app.data.AppSetSource
import com.justdataplease.streetgym.app.data.AppSettings
import com.justdataplease.streetgym.app.data.CloudTime
import com.justdataplease.streetgym.app.data.FocusPage
import com.justdataplease.streetgym.app.data.LibraryStore
import com.justdataplease.streetgym.app.data.MemoryStore
import com.justdataplease.streetgym.app.data.PhoneServices
import com.justdataplease.streetgym.app.data.Plans
import com.justdataplease.streetgym.app.data.Program
import com.justdataplease.streetgym.app.data.SignInProvider
import com.justdataplease.streetgym.app.data.StoredProgress
import com.justdataplease.streetgym.app.data.loadProgram
import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.progress.NextClass
import com.justdataplease.streetgym.data.progress.Placement
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Progress analytics behind "Share my progress" (Android: analytics/Analytics.kt, AnalyticsPayload, FirestoreAnalytics)
 * through a fake Firestore that checks the rules: nothing at all while it's off, the install's record and each finished
 * class with Android's exact fields, at most every ten minutes, switching off deleting what was shared, and a deleted
 * account's uid taken off the record.
 */
class AnalyticsTest {
    private var time = 20_775 * DAY + 12 * 3_600_000L

    private inner class Phone(tier: Tier = Tier.FREE, share: Boolean = true, val store: MemoryStore = MemoryStore()) {
        val account = FakeAccount()
        val cloud = FakeCloud({ account.user.value }, { time })
        val model = AppSettings(store).let { settings ->
            settings.shareProgress = share
            AppModel(program, StoredProgress(store), now = { time }, zone = TimeZone.UTC, settings = settings, library = LibraryStore(store),
                plans = Plans(store, floor = tier), adStore = store,
                services = PhoneServices(account = account, documents = cloud, appSet = AppSetSource { it(AppSetId(VENDOR, "developer")) },
                    versionCode = 7)).apply { version = "1.0" }
        }

        val install: String get() = store.get(Analytics.KEY_INSTALL_ID)!!

        fun train() {
            model.start(model.today().next!!.id)
            while (true) {
                val index = (model.training.state()!!.page as? FocusPage.Set)?.index ?: break
                time += 30_000L
                model.training.done(index)
                model.training.skipRest()
            }
            model.training.finish(7)
        }
    }

    @Test
    fun switchedOffNothingIsWrittenAtAll() {
        val phone = Phone(share = false)
        phone.model.startPlacement(Placement.Start(1, 1))
        phone.train()
        phone.model.opened()
        assertTrue(phone.cloud.documents.isEmpty())
        assertTrue(phone.cloud.writes.isEmpty())
        assertNull(phone.account.user.value)
    }

    @Test
    fun theInstallsRecordAndAClassGoUpWithAndroidsFields() {
        val phone = Phone()
        phone.model.placementForm.counts[0] = 8
        phone.model.placementForm.counts[1] = 25
        phone.model.placementForm.counts[2] = 40
        phone.model.startPlacement(Placement.Start(1, 1))
        // The strength test's answer writes the record at once, signed in anonymously.
        assertTrue(phone.account.user.value!!.anonymous)
        val id = phone.install
        assertTrue(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$").matches(id))
        val record = phone.cloud.documents["analytics/$id"]!!
        assertEquals(mapOf(
            "v" to 1, "appSetId" to VENDOR, "appSetScope" to "developer", "firstSeen" to CloudTime(time), "versionName" to "1.0",
            "versionCode" to 7, "tier" to "free", "debug" to false, "shareProgress" to true,
            "placement" to mapOf("pullUps" to 8, "pushUps" to 25, "squats" to 40, "skillTest" to 0, "startLevel" to 1, "startDay" to 1),
            "skills" to emptyMap<String, List<Int>>(), "switches" to "", "completedClasses" to 0, "trainedClasses" to 0,
            "markedClasses" to 0, "atLevel" to 1, "atDay" to 1, "lastSeen" to CloudTime(time),
        ), record)
        assertNull(phone.cloud.refused)

        // A finished class: its document under the install, by the millisecond it started, every set with it.
        val started = time
        phone.train()
        val session = phone.cloud.documents["analytics/$id/sessions/$started"]!!
        assertEquals(AnalyticsPayload.SESSION_FIELDS + "uploadedAt", session.keys)
        assertEquals(ordered[0].id, session["programId"])
        assertEquals(1, session["level"])
        assertEquals(1, session["day"])
        assertEquals("normal", session["intensity"])
        assertEquals(7, session["effort"])
        assertEquals(CloudTime(started), session["startedAt"])
        assertEquals(CloudTime(time), session["uploadedAt"])
        assertEquals(session["plannedSets"], session["doneSets"])
        @Suppress("UNCHECKED_CAST")
        val exercises = session["exercises"] as List<Map<String, Any?>>
        assertTrue(exercises.all { it.keys == AnalyticsPayload.EXERCISE_FIELDS })
        assertTrue(exercises.flatMap { it["sets"] as List<Map<String, Any?>> }.all { it.keys == AnalyticsPayload.SET_FIELDS && it["ticked"] == true })
        assertEquals(setOf(started.toString()), phone.model.cloud.analytics.uploaded())
        assertTrue(phone.model.cloud.analytics.pending().isEmpty())
        // The record moved on with it.
        assertEquals(1, phone.cloud.documents["analytics/$id"]!!["trainedClasses"])
        assertNull(phone.cloud.refused)
    }

    @Test
    fun openingTheAppWritesTheRecordAtMostEveryTenMinutes() {
        val phone = Phone()
        phone.model.startPlacement(Placement.Start(1, 1))
        val writes = phone.cloud.writes.size
        time += 5 * 60_000L
        phone.model.opened()
        assertEquals(writes, phone.cloud.writes.size)
        time += 5 * 60_000L
        phone.model.opened()
        assertEquals(writes + 1, phone.cloud.writes.size)
    }

    @Test
    fun switchingOffDeletesWhatWasShared() {
        val phone = Phone()
        phone.model.startPlacement(Placement.Start(1, 1))
        phone.train()
        val id = phone.install
        assertTrue(phone.cloud.documents.keys.any { it.startsWith("analytics/$id/sessions/") })
        phone.model.shareProgress = false
        assertTrue(phone.cloud.documents.keys.none { it.startsWith("analytics/") })
        assertFalse(phone.model.cloud.analytics.deletePending)
        // Off, another class writes nothing.
        phone.train()
        assertTrue(phone.cloud.documents.keys.none { it.startsWith("analytics/") })
        // Offline, the deletion waits and is retried when the app opens.
        phone.model.shareProgress = true
        time += 11 * 60_000L
        phone.model.opened()
        assertTrue("analytics/$id" in phone.cloud.documents)
        phone.cloud.online = false
        phone.model.shareProgress = false
        assertTrue(phone.model.cloud.analytics.deletePending)
        phone.cloud.online = true
        phone.model.opened()
        assertFalse(phone.model.cloud.analytics.deletePending)
        assertTrue(phone.cloud.documents.keys.none { it.startsWith("analytics/") })
    }

    @Test
    fun aDeletedAccountsUidComesOffTheRecord() {
        val phone = Phone(Tier.PRO)
        phone.model.startPlacement(Placement.Start(1, 1))
        phone.model.cloud.connect(SignInProvider.GOOGLE)
        val uid = phone.model.cloud.signedIn()!!.uid
        time += 11 * 60_000L
        phone.model.opened()
        val id = phone.install
        assertEquals(uid, phone.cloud.documents["analytics/$id"]!!["accountUid"])
        phone.model.cloud.deleteAccount {}
        // The next write runs as a new anonymous user and removes the field.
        time += 11 * 60_000L
        phone.model.opened()
        assertFalse("accountUid" in phone.cloud.documents["analytics/$id"]!!)
        assertNull(phone.cloud.refused)
    }

    private companion object {
        const val DAY = 86_400_000L
        const val VENDOR = "4f0d1f3c-8b2e-4a59-9d55-0c3b9e2f7a10"
        val program: Program by lazy { runBlocking { loadProgram() } }
        val ordered: List<TrainingProgram> by lazy { NextClass.ordered(program.catalog) }
    }
}
