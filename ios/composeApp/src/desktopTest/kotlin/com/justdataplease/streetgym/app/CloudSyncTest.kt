package com.justdataplease.streetgym.app

import com.justdataplease.streetgym.app.data.AppModel
import com.justdataplease.streetgym.app.data.AppSettings
import com.justdataplease.streetgym.app.data.CloudSync
import com.justdataplease.streetgym.app.data.CloudTime
import com.justdataplease.streetgym.app.data.FocusPage
import com.justdataplease.streetgym.app.data.LibraryStore
import com.justdataplease.streetgym.app.data.MemoryStore
import com.justdataplease.streetgym.app.data.PhoneServices
import com.justdataplease.streetgym.app.data.Plans
import com.justdataplease.streetgym.app.data.Profile
import com.justdataplease.streetgym.app.data.Program
import com.justdataplease.streetgym.app.data.SignInProvider
import com.justdataplease.streetgym.app.data.StoredProgress
import com.justdataplease.streetgym.app.data.loadProgram
import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.library.LibraryCodec
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.progress.ExamLog
import com.justdataplease.streetgym.data.progress.ExamResult
import com.justdataplease.streetgym.data.progress.NextClass
import com.justdataplease.streetgym.json.JSONObject
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pro's cloud backups and restore (Android: account/CloudSync.kt) through a fake Firestore that checks the rules: a
 * backup made with Android's fields, read back as the account's copy, restored on a new phone through the engine's
 * merge, both phones' progress kept, what Start over does to the copy, and who doesn't sync.
 */
class CloudSyncTest {
    private var time = 20_775 * DAY + 12 * 3_600_000L

    private inner class Phone(
        tier: Tier = Tier.PRO,
        server: MutableMap<String, Map<String, Any?>>,
        owners: MutableMap<Pair<SignInProvider, String>, String>,
        val store: MemoryStore = MemoryStore(),
    ) {
        val account = FakeAccount(owners = owners)
        val cloud = FakeCloud({ account.user.value }, { time }, server)
        val model = AppSettings(store).let { settings ->
            settings.placementDone = true
            AppModel(program, StoredProgress(store), now = { time }, zone = TimeZone.UTC, settings = settings, library = LibraryStore(store),
                plans = Plans(store, floor = tier), adStore = store, services = PhoneServices(account = account, documents = cloud))
        }

        fun signIn() = model.cloud.connect(SignInProvider.GOOGLE)

        fun sync(): CloudSync.Result {
            var result: CloudSync.Result? = null
            model.cloud.sync.sync(force = true) { result = it }
            return result!!
        }

        /** Trains today's class to its end and finishes it. */
        fun train() {
            model.start(model.today().next!!.id)
            while (true) {
                val index = (model.training.state()!!.page as? FocusPage.Set)?.index ?: break
                time += 30_000L
                model.training.done(index)
                model.training.skipRest()
            }
            model.training.finish(6)
        }
    }

    private val server = linkedMapOf<String, Map<String, Any?>>()
    private val owners = mutableMapOf<Pair<SignInProvider, String>, String>()

    @Test
    fun aBackupIsWrittenWithAndroidsFields() {
        val phone = Phone(server = server, owners = owners)
        phone.model.completeDay(ordered[0].id)
        phone.train()
        phone.model.progress.recordExam(ExamResult(ordered[19].id, time, listOf("pullup"), emptyList()))
        phone.model.setSaved(ordered[3].id, true)
        phone.signIn()
        val uid = phone.model.cloud.signedIn()!!.uid
        val progress = server["users/$uid/data/progress"]!!
        val local = phone.model.progress
        // completed as a sorted list, sessions and exams as the JSON each phone keeps, reset_at, the server's time.
        assertEquals(setOf("completed", "sessions", "exams", "reset_at", "updated_at"), progress.keys)
        assertEquals(local.completedProgramIds().sorted(), progress["completed"])
        assertEquals(phone.store.get("streetgym_progress.session_history"), progress["sessions"])
        assertEquals(local.examLog().encode(), progress["exams"])
        assertEquals(0L, progress["reset_at"])
        assertEquals(CloudTime(time), progress["updated_at"])
        val library = server["users/$uid/data/library"]!!
        assertEquals(setOf("library", "updated_at"), library.keys)
        assertEquals(LibraryCodec.encode(LibraryStore(phone.store).state()).toString(), library["library"])
        assertTrue(JSONObject(library["library"] as String).has("saved"))
        // The account's copy lists what was backed up: two classes and the saved day.
        assertEquals(2, local.sessionHistory().size)
        assertEquals(time, phone.model.cloud.store.lastSyncAt)
        // Nothing changed: the next sync reads and writes nothing new.
        val batches = phone.cloud.writes.size
        assertEquals(CloudSync.Result.Synced, phone.sync())
        assertEquals(batches, phone.cloud.writes.size)
    }

    @Test
    fun aNewPhoneRestoresAndBothKeepEverything() {
        val first = Phone(server = server, owners = owners)
        first.train()
        first.model.progress.recordExam(ExamResult(ordered[19].id, time, emptyList(), listOf("pullup")))
        first.model.setSaved(ordered[5].id, true)
        first.signIn()
        time += 60 * 60_000L

        // The new phone has its own class already; signing in with the same Google account merges both ways.
        val second = Phone(server = server, owners = owners)
        second.model.completeDay(ordered[0].id)
        second.signIn()
        assertEquals(first.model.cloud.signedIn()!!.uid, second.model.cloud.signedIn()!!.uid)
        val restored = second.model.progress
        assertTrue(restored.isProgramCompleted(ordered[0].id))
        assertEquals(first.model.progress.sessionHistory().first(), restored.sessionHistory().first { !it.manuallyCompleted })
        assertEquals(ExamLog.decode(first.model.progress.examLog().encode()), restored.examLog())
        assertTrue(LibraryStore(second.store).isSaved(ordered[5].id))
        // The first phone catches up with the second's marked day; nothing is lost on either.
        assertEquals(CloudSync.Result.Synced, first.sync())
        assertEquals(restored.completedProgramIds(), first.model.progress.completedProgramIds())
        assertEquals(2, first.model.progress.sessionHistory().size)
    }

    @Test
    fun startOverTravelsAsItsTimeAndKeepsAClassInProgress() {
        val first = Phone(server = server, owners = owners)
        first.train()
        first.signIn()
        val second = Phone(server = server, owners = owners)
        second.signIn()
        assertEquals(1, second.model.progress.sessionHistory().size)

        // Start over on the first phone: the copy keeps only what came after, with the reset's time.
        time += 60_000L
        first.model.reset()
        time += 4_000L
        first.model.cloud.tick()
        val uid = first.model.cloud.signedIn()!!.uid
        val copy = server["users/$uid/data/progress"]!!
        assertEquals(first.model.progress.resetAt(), copy["reset_at"])
        assertEquals(emptyList<String>(), copy["completed"])

        // The other phone, mid-class, starts over too at its next sync, and its class carries on.
        second.model.start(second.model.today().next!!.id)
        assertEquals(CloudSync.Result.Synced, second.sync())
        assertTrue(second.model.progress.sessionHistory().isEmpty())
        assertTrue(second.model.progress.completedProgramIds().isEmpty())
        assertNotNull(second.model.progress.activeWorkout())
        assertEquals(first.model.progress.resetAt(), second.model.progress.resetAt())
    }

    @Test
    fun onlyProSignedInSyncsAndOpeningCatchesUpEverySixHours() {
        val plus = Phone(Tier.PLUS, server, owners)
        plus.signIn()
        assertEquals(CloudSync.Result.Skipped, plus.sync())
        assertTrue(server.keys.none { it.contains("/data/") })

        val anonymous = Phone(Tier.PRO, server, owners)
        var uid = ""
        anonymous.model.cloud.pickUsername { uid = it }
        anonymous.model.cloud.saveProfile(uid, Profile("NoAccount", "GR"))
        assertEquals(CloudSync.Result.Skipped, anonymous.sync())

        val pro = Phone(Tier.PRO, linkedMapOf(), mutableMapOf())
        pro.signIn()
        val synced = pro.model.cloud.store.lastSyncAt
        // Opening the app within six hours reads nothing; after them it catches up.
        var result: CloudSync.Result? = null
        time += 60 * 60_000L
        pro.model.cloud.sync.sync { result = it }
        assertEquals(CloudSync.Result.Skipped, result)
        time += CloudSync.MIN_INTERVAL_MILLIS
        pro.model.cloud.sync.sync { result = it }
        assertEquals(CloudSync.Result.Synced, result)
        assertTrue(pro.model.cloud.store.lastSyncAt > synced)
    }

    @Test
    fun aChangeSyncsOnceItSettlesAndOfflineSaysSo() {
        val phone = Phone(server = server, owners = owners)
        phone.signIn()
        val uid = phone.model.cloud.signedIn()!!.uid
        phone.model.completeDay(ordered[0].id)
        phone.model.cloud.tick()
        assertFalse(ordered[0].id in (server["users/$uid/data/progress"]!!["completed"] as List<*>))
        time += 3_000L
        phone.model.cloud.tick()
        assertTrue(ordered[0].id in (server["users/$uid/data/progress"]!!["completed"] as List<*>))

        phone.cloud.online = false
        phone.model.cloud.syncNow()
        assertEquals(com.justdataplease.streetgym.app.data.AccountStatus.SYNC_FAILED, phone.model.cloud.status)
        // Everything stays on the phone.
        assertTrue(phone.model.isCompleted(ordered[0].id))
        phone.cloud.online = true
        phone.model.cloud.syncNow()
        assertNull(phone.model.cloud.status)
    }

    private companion object {
        const val DAY = 86_400_000L
        val program: Program by lazy { runBlocking { loadProgram() } }
        val ordered: List<TrainingProgram> by lazy { NextClass.ordered(program.catalog) }
    }
}
