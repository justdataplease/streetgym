package com.justdataplease.streetgym.analytics

import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.progress.MemoryPreferences
import org.junit.Assert.*
import org.junit.Test

/** When progress analytics writes and deletes: only with Firebase and the switch on, and off deletes what was shared. */
class AnalyticsTest {
    private val preferences = MemoryPreferences()
    private val backend = FakeBackend()
    private var sharing = true
    private var now = 1_761_800_000_000L
    private var appSet: AppSetId? = AppSetId("0b5e8f7c-1d2a-4c3b-9e8f-7a6b5c4d3e2f", "developer")
    private val snapshot = InstallSnapshot("2.6.9", 30, Tier.FREE, debug = false, skills = emptyMap(), score = 0,
        switches = "", completedClasses = 0, trainedClasses = 0, markedClasses = 0, atLevel = 1, atDay = 1)
    private val session = mapOf<String, Any?>("v" to 1, "programId" to "sg:l01:p0001", "startedAt" to now, "exercises" to emptyList<Any>())

    private fun analytics(backend: AnalyticsBackend? = this.backend) =
        Analytics(preferences, backend, { done -> done(appSet) }, { sharing }, { now })

    private fun Analytics.finish(id: String) = classFinished(id, session) { snapshot }

    @Test fun withoutFirebaseNothingHappensOrIsKept() {
        val analytics = analytics(backend = null)
        analytics.sync(force = true) { snapshot }
        analytics.finish("1761800000000")
        analytics.placed(PlacementRecord(3, 10, 20, 0, 1, 1))
        analytics.shareChanged(false)
        analytics.accountDeleted()
        assertTrue(preferences.all.toString(), preferences.all.isEmpty())
    }

    @Test fun theInstallIdIsARandomCodeKeptUntilTheAppsDataIsCleared() {
        val id = analytics().installId
        assertTrue(id, Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$").matches(id))
        // Another start of the app.
        assertEquals(id, analytics().installId)
        // Cleared data: a new code.
        assertNotEquals(id, Analytics(MemoryPreferences(), backend, { it(null) }, { true }).installId)
        analytics().sync { snapshot }
        assertEquals(id, backend.writes.single().installId)
        assertEquals(now, backend.writes.single().install["firstSeen"])
    }

    @Test fun theInstallDocumentGoesUpAtMostEveryTenMinutesWhenTheAppOpens() {
        val analytics = analytics()
        analytics.sync { snapshot }
        now += 9 * MINUTE
        analytics.sync { snapshot }
        assertEquals(1, backend.writes.size)
        analytics.sync(force = true) { snapshot.copy(completedClasses = 1) }
        assertEquals(2, backend.writes.size)
        assertEquals(1, backend.writes.last().install["completedClasses"])
        now += 10 * MINUTE
        analytics.sync { snapshot }
        assertEquals(3, backend.writes.size)
        assertTrue(backend.writes.all { it.sessions.isEmpty() && it.install["shareProgress"] == true })
    }

    @Test fun aFinishedClassGoesUpOnceWithTheInstallDocument() {
        val analytics = analytics()
        analytics.finish("1761800000000")

        assertEquals(setOf("1761800000000"), backend.writes.single().sessions.keys)
        assertEquals("sg:l01:p0001", backend.writes.single().sessions.values.single()["programId"])
        assertEquals(setOf("1761800000000"), analytics.uploaded())
        assertTrue(analytics.pending().isEmpty())
        now += 11 * MINUTE
        analytics.sync { snapshot }
        assertTrue(backend.writes.last().sessions.isEmpty())
    }

    @Test fun aClassTheServerDidNotConfirmWaitsOnThePhoneForTheNextStart() {
        backend.ok = false
        analytics().finish("1761800000000")
        assertEquals(setOf("1761800000000"), analytics().pending())
        assertTrue(analytics().uploaded().isEmpty())

        backend.ok = true
        now += 11 * MINUTE
        val restarted = analytics()
        restarted.sync { snapshot }
        assertEquals(setOf("1761800000000"), backend.writes.last().sessions.keys)
        assertEquals(setOf("1761800000000"), restarted.uploaded())
        assertTrue(restarted.pending().isEmpty())
    }

    @Test fun nobodySignedInWritesNothingAndKeepsTheClass() {
        backend.writer = null
        val analytics = analytics()
        analytics.finish("1761800000000")
        assertTrue(backend.writes.isEmpty())
        assertEquals(setOf("1761800000000"), analytics.pending())
    }

    @Test fun noAppSetIdStillWritesWithoutOne() {
        appSet = null
        analytics().sync { snapshot }
        val install = backend.writes.single().install
        assertFalse("appSetId" in install || "appSetScope" in install)
        appSet = AppSetId("0b5e8f7c-1d2a-4c3b-9e8f-7a6b5c4d3e2f", "app")
        analytics().sync(force = true) { snapshot }
        assertEquals("app", backend.writes.last().install["appSetScope"])
    }

    @Test fun switchedOffNothingGoesUp() {
        sharing = false
        val analytics = analytics()
        analytics.sync(force = true) { snapshot }
        analytics.finish("1761800000000")
        assertTrue(backend.log.isEmpty())
        assertTrue(analytics.pending().isEmpty())
        // Never shared: switching off has nothing to delete.
        analytics.shareChanged(false)
        assertTrue(backend.log.isEmpty())
        assertFalse(analytics.deletePending)
    }

    @Test fun switchingOffDeletesTheInstallAndEveryClassItWrote() {
        val analytics = analytics()
        analytics.finish("1761800000000")
        backend.ok = false
        analytics.finish("1761800600000")
        backend.ok = true

        sharing = false
        analytics.shareChanged(false)

        // The class the server never confirmed may have landed all the same: it goes too.
        assertEquals(listOf("write [1761800000000]", "write [1761800600000]",
            "delete ${analytics.installId} [1761800000000, 1761800600000]"), backend.log)
        assertFalse(analytics.deletePending)
        assertTrue(analytics.uploaded().isEmpty() && analytics.pending().isEmpty())
        analytics.sync(force = true) { snapshot }
        analytics.finish("1761801200000")
        assertEquals(3, backend.log.size)
    }

    @Test fun aDeletionNotConfirmedIsRetriedBeforeAnythingIsWrittenAgain() {
        val analytics = analytics()
        analytics.finish("1761800000000")
        backend.deleted = false
        sharing = false
        analytics.shareChanged(false)
        assertTrue(analytics.deletePending)
        // Retried when the app opens, even switched off.
        analytics.sync { snapshot }
        assertEquals(2, backend.log.count { it.startsWith("delete") })

        backend.deleted = true
        sharing = true
        analytics.shareChanged(true)
        analytics.finish("1761801200000")

        assertEquals(listOf("delete ${analytics.installId} [1761800000000]", "write [1761801200000]"), backend.log.takeLast(2))
        assertFalse(analytics.deletePending)
        assertEquals(setOf("1761801200000"), analytics.uploaded())
    }

    @Test fun switchingBackOnWritesAtTheNextSync() {
        val analytics = analytics()
        analytics.sync { snapshot }
        sharing = false
        analytics.shareChanged(false)
        sharing = true
        analytics.shareChanged(true)
        analytics.sync { snapshot }
        assertEquals(listOf("write []", "delete ${analytics.installId} []", "write []"), backend.log)
    }

    @Test fun theAccountsUidIsOnlyAGoogleAccountsAndGoesWithIt() {
        val analytics = analytics()
        backend.writer = Writer("googleUid42")
        analytics.sync { snapshot }
        assertEquals("googleUid42", backend.writes.last().install["accountUid"])
        // Signed out: the anonymous user leaves the uid as it was.
        backend.writer = Writer(null)
        analytics.sync(force = true) { snapshot }
        assertFalse("accountUid" in backend.writes.last().install)
        // The account deleted: it comes off at the next sync, once.
        analytics.accountDeleted()
        analytics.sync { snapshot }
        assertSame(AnalyticsPayload.Delete, backend.writes.last().install["accountUid"])
        analytics.sync(force = true) { snapshot }
        assertFalse("accountUid" in backend.writes.last().install)
    }

    @Test fun thePlacementGoesWithEveryInstallDocument() {
        val analytics = analytics()
        analytics.placed(PlacementRecord(4, 18, null, 0, 1, 21))
        analytics.sync { snapshot }
        assertEquals(mapOf("pullUps" to 4, "pushUps" to 18, "squats" to null, "skillTest" to 0, "startLevel" to 1, "startDay" to 21),
            backend.writes.single().install["placement"])
    }

    private class Write(val installId: String, val install: Map<String, Any?>, val sessions: Map<String, Map<String, Any?>>)

    private class FakeBackend : AnalyticsBackend {
        var writer: Writer? = Writer(null)
        var ok = true
        var deleted = true
        val writes = mutableListOf<Write>()
        val log = mutableListOf<String>()

        override fun signIn(done: (Writer?) -> Unit) = done(writer)

        override fun write(installId: String, install: Map<String, Any?>, sessions: Map<String, Map<String, Any?>>, done: (Boolean) -> Unit) {
            writes += Write(installId, install, sessions)
            log += "write ${sessions.keys.sorted()}"
            done(ok)
        }

        override fun delete(installId: String, sessionIds: Collection<String>, done: (Boolean) -> Unit) {
            log += "delete $installId ${sessionIds.sorted()}"
            done(deleted)
        }
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
