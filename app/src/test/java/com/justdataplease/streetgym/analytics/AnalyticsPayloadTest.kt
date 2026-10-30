package com.justdataplease.streetgym.analytics

import com.justdataplease.streetgym.billing.Tier
import com.justdataplease.streetgym.data.library.CustomDay
import com.justdataplease.streetgym.data.library.CustomDays
import com.justdataplease.streetgym.data.library.CustomExercise
import com.justdataplease.streetgym.data.library.CustomSet
import com.justdataplease.streetgym.data.model.EasierOption
import com.justdataplease.streetgym.data.model.LocalizedText
import com.justdataplease.streetgym.data.model.TrainingExercise
import com.justdataplease.streetgym.data.model.TrainingProgram
import com.justdataplease.streetgym.data.model.TrainingSet
import com.justdataplease.streetgym.data.progress.ActiveWorkout
import com.justdataplease.streetgym.data.progress.Intensity
import com.justdataplease.streetgym.data.progress.WorkoutSession
import com.justdataplease.streetgym.data.skills.SkillCredit
import com.justdataplease.streetgym.data.skills.SkillCurriculumReader
import com.justdataplease.streetgym.data.skills.SkillLevel
import com.justdataplease.streetgym.ui.WorkoutFlow
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** What progress analytics sends: training progress only, every field one the rules allow, no name or typed text. */
class AnalyticsPayloadTest {
    private val snapshot = InstallSnapshot("2.6.9", 30, Tier.PLUS, debug = false, skills = mapOf("pullup" to listOf(1, 12, 9)),
        score = 87, switches = "planche:1", completedClasses = 12, trainedClasses = 10, markedClasses = 2, atLevel = 1, atDay = 13)

    private fun install(appSet: AppSetId? = AppSetId("6f1c2b0e-0000-4000-8000-000000000001", "developer"),
                        accountUid: String? = null, forget: Boolean = false, placement: PlacementRecord? = null) =
        AnalyticsPayload.install(snapshot, FIRST_SEEN, appSet, accountUid, forget, placement)

    @Test fun theInstallDocumentHoldsOnlyItsFields() {
        val document = install(accountUid = "googleUid42", placement = PlacementRecord(5, 20, null, 0, 1, 21))

        assertTrue(document.keys.toString(), AnalyticsPayload.INSTALL_FIELDS.containsAll(document.keys))
        assertEquals(mapOf("pullUps" to 5, "pushUps" to 20, "squats" to null, "skillTest" to 0, "startLevel" to 1, "startDay" to 21),
            document["placement"])
        assertEquals(1, document["v"])
        assertEquals("plus", document["tier"])
        assertEquals(false, document["debug"])
        assertEquals(true, document["shareProgress"])
        assertEquals(FIRST_SEEN, document["firstSeen"])
        assertEquals(87, document["score"])
        assertEquals(mapOf("pullup" to listOf(1, 12, 9)), document["skills"])
        assertEquals("planche:1", document["switches"])
        assertEquals("developer", document["appSetScope"])
        assertEquals("googleUid42", document["accountUid"])
        assertEquals(listOf(12, 10, 2, 1, 13), listOf("completedClasses", "trainedClasses", "markedClasses", "atLevel", "atDay").map(document::get))
        assertNoPersonalData(document)
    }

    @Test fun anAnonymousWriterOrAFailedAppSetIdLeavesTheirFieldsAsTheyWere() {
        val document = install(appSet = null)
        assertFalse("accountUid" in document)
        assertFalse("appSetId" in document)
        assertFalse("appSetScope" in document)
        // A deleted account comes off; a signed-in one wins.
        assertSame(AnalyticsPayload.Delete, install(forget = true)["accountUid"])
        assertEquals("other", install(accountUid = "other", forget = true)["accountUid"])
        // Not ready yet: no skills or score rather than zero.
        val early = AnalyticsPayload.install(snapshot.copy(skills = null, score = null), FIRST_SEEN, null, null, false, null)
        assertFalse("skills" in early || "score" in early)
        assertNull(early["placement"])
    }

    @Test fun textNeverPassesForAnId() {
        val odd = snapshot.copy(skills = mapOf("Anna's lever" to listOf(1, 1, 1), "pullup" to listOf(0, 3, 5)), switches = "Anna, Athens")
        val document = AnalyticsPayload.install(odd, FIRST_SEEN, null, null, false, null)
        assertEquals(setOf("pullup"), (document["skills"] as Map<*, *>).keys)
        assertEquals("", document["switches"])
    }

    @Test fun anOwnDayGoesUpSetBySetWithoutItsNamesOrId() {
        val day = CustomDay("custom:0d9ab3a6-4e2f-4f7a-9c11-5a1e0b6f2c77", "Anna's Tuesday at Riverside Park", listOf(
            CustomExercise("pullup", "Pull-ups with John", "pullup", sets = listOf(CustomSet(8), CustomSet(8))),
            CustomExercise("own", "My secret move", null, sets = listOf(CustomSet(durationSeconds = 30))),
        ), updatedAt = 0L)
        val program = CustomDays.toProgram(day)
        val flow = WorkoutFlow.of(program.id, listOf("main" to program.exercises))
        val keys = flow.keys
        val active = ActiveWorkout(program.id, STARTED, completedSetIds = setOf(keys[0], keys[2]), done = mapOf(keys[0] to 10))
        val session = WorkoutSession(program.id, STARTED, STARTED + 1_800_000L, 1_800L, effortRating = 7, intensity = Intensity.HARDER)

        val document = AnalyticsPayload.session(session, program, flow, active, leftOut = emptySet())

        assertEquals(AnalyticsPayload.SESSION_FIELDS, document.keys)
        assertEquals("custom", document["programId"])
        assertEquals(true, document["custom"])
        assertEquals("harder", document["intensity"])
        assertEquals(7, document["effort"])
        assertEquals(STARTED, document["startedAt"])
        assertEquals(1_800L, document["durationSec"])
        assertEquals(listOf(3, 2, 0), listOf("plannedSets", "doneSets", "leftOutSets").map(document::get))
        val exercises = exercises(document)
        assertEquals(listOf("pullup", null), exercises.map { it["movementId"] })
        assertEquals(listOf(
            mapOf("planned" to 8, "max" to false, "timed" to false, "done" to 10, "ticked" to true, "off" to false),
            mapOf("planned" to 8, "max" to false, "timed" to false, "done" to null, "ticked" to false, "off" to false),
        ), exercises[0]["sets"])
        assertEquals(true, (exercises[1]["sets"] as List<*>).single().let { (it as Map<*, *>)["timed"] })
        assertNoPersonalData(document)
        val text = AnalyticsPayload.encode(document)
        listOf("Anna", "Riverside", "John", "secret", "0d9ab3a6").forEach { assertFalse(it, text.contains(it)) }
    }

    @Test fun aProgramClassKeepsItsSwitchesEasierOptionsAndTests() {
        val program = program("sg:l02:p0020", dayType = "exam_block",
            practice = listOf(exercise("p1", "tuck_front_hold", hold(10), hold(10))),
            main = listOf(
                exercise("m1", "archer_pullup", reps(4), reps(4), easier = "pullup", assessment = true),
                exercise("m2", "pushup", maxReps()),
            ))
        val flow = WorkoutFlow.of(program.id, listOf("practice" to program.practice, "main" to program.exercises))
        val keys = flow.keys
        val leftOut = setOf(keys[0], keys[1])
        val active = ActiveWorkout(program.id, STARTED, completedSetIds = setOf(keys[1], keys[2], keys[4]),
            easierExercises = setOf(flow[2].exerciseKey), done = mapOf(keys[4] to 25))
        val session = WorkoutSession(program.id, STARTED, STARTED + 3_000_000L, 3_000L, effortRating = 9)

        val document = AnalyticsPayload.session(session, program, flow, active, leftOut)

        assertEquals("sg:l02:p0020", document["programId"])
        assertEquals(listOf(2, 20, "exam_block", true, false, false, "normal"),
            listOf("level", "day", "dayType", "exam", "custom", "repeat", "intensity").map(document::get))
        // Switched off: out of the planned sets, even ticked.
        assertEquals(listOf(3, 2, 2), listOf("plannedSets", "doneSets", "leftOutSets").map(document::get))
        val exercises = exercises(document)
        assertEquals(listOf("practice", "main", "main"), exercises.map { it["section"] })
        assertEquals(listOf(null, "pullup", null), exercises.map { it["easier"] })
        assertEquals(listOf(false, true, false), exercises.map { it["test"] })
        val practice = sets(exercises[0])
        assertEquals(listOf(true, true), practice.map { it["off"] })
        assertEquals(listOf(10, 10), practice.map { it["planned"] })
        val max = sets(exercises[2]).single()
        assertEquals(mapOf("planned" to null, "max" to true, "timed" to false, "done" to 25, "ticked" to true, "off" to false), max)
        exercises.forEach { assertEquals(AnalyticsPayload.EXERCISE_FIELDS, it.keys) }
        exercises.flatMap(::sets).forEach { assertEquals(AnalyticsPayload.SET_FIELDS, it.keys) }
        assertNoPersonalData(document)
        assertEquals(STARTED.toString(), AnalyticsPayload.sessionId(session))
    }

    @Test fun anUnexpectedProgramIdIsSentAsUnknown() {
        val program = program("Anna's day", main = listOf(exercise("m1", "pushup", reps(5))))
        val flow = WorkoutFlow.of(program.id, listOf("main" to program.exercises))
        val document = AnalyticsPayload.session(WorkoutSession(program.id, STARTED, STARTED, 0L), program, flow, null, emptySet())
        assertEquals("unknown", document["programId"])
        assertEquals(0, document["doneSets"])
    }

    @Test fun aClassSurvivesThePhonesQueue() {
        val program = program("sg:l01:p0001", main = listOf(exercise("m1", "pushup", reps(5), maxReps())))
        val flow = WorkoutFlow.of(program.id, listOf("main" to program.exercises))
        val document = AnalyticsPayload.session(WorkoutSession(program.id, STARTED, STARTED + 60_000L, 60L, effortRating = 5),
            program, flow, ActiveWorkout(program.id, STARTED, setOf(flow.keys[0])), emptySet())
        val decoded = AnalyticsPayload.decode(AnalyticsPayload.encode(document))!!
        // JSON reads small numbers back as Int: the same values all the same.
        assertEquals(numbersAsLong(document), numbersAsLong(decoded))
        assertEquals(STARTED, (decoded["startedAt"] as Number).toLong())
        assertNull(AnalyticsPayload.decode("not json"))
        // The placement too.
        val placement = PlacementRecord(null, 12, 30, 4, 5, 1)
        assertEquals(placement, PlacementRecord.decode(placement.encode()))
        assertNull(PlacementRecord.decode(null))
    }

    @Test fun skillsAreTheStartedOnesWithLevelSetsAndBest() {
        val progress = book.progress(listOf(SkillCredit("sg:l01:p0001", sets = mapOf("pullup" to 12), seconds = 1_800L,
            doses = mapOf("pullup" to List(12) { 9 }))))
        val skills = AnalyticsPayload.skills(progress)

        assertTrue(skills.isNotEmpty())
        assertTrue(skills.size < progress.statuses.size)
        skills.forEach { (id, value) ->
            assertTrue(id, AnalyticsPayload.ID.matches(id))
            assertEquals(3, value.size)
            assertTrue(value[0] in SkillLevel.NONE.ordinal..SkillLevel.ELITE.ordinal)
        }
        // As Skills shows it.
        val pullUp = progress.statuses.first { it.skill.id == "pullup" }
        assertTrue(pullUp.sets > 0)
        assertEquals(listOf(pullUp.level.ordinal, pullUp.sets, pullUp.best), skills["pullup"])
        assertEquals(9, pullUp.best)
        assertEquals(progress.statuses.filter { it.started }.map { it.skill.id }.toSet(), skills.keys)
    }

    private companion object {
        const val FIRST_SEEN = 1_761_800_000_000L
        const val STARTED = 1_761_900_000_000L
        val book by lazy { SkillCurriculumReader.read(File("src/main/assets/curriculum.json").readText()) }

        /** No key a person could be found by, and every text an id or a code: no spaces, no typed words. */
        val PERSONAL_KEYS = setOf("name", "email", "username", "country", "location", "latitude", "longitude", "lat", "lng",
            "ip", "locale", "timezone", "phone", "androidId", "deviceId", "notes", "text", "title")
        val CODE = Regex("^[A-Za-z0-9_:.,\\-]*$")

        fun assertNoPersonalData(value: Any?, path: String = "") {
            when (value) {
                is Map<*, *> -> value.forEach { (key, item) ->
                    assertFalse("$path.$key", PERSONAL_KEYS.any { it.equals(key as String, ignoreCase = true) })
                    assertNoPersonalData(item, "$path.$key")
                }
                is List<*> -> value.forEachIndexed { index, item -> assertNoPersonalData(item, "$path[$index]") }
                is String -> assertTrue("$path = $value", CODE.matches(value))
            }
        }

        fun numbersAsLong(value: Any?): Any? = when (value) {
            is Number -> value.toLong()
            is Map<*, *> -> value.mapValues { numbersAsLong(it.value) }
            is List<*> -> value.map(::numbersAsLong)
            else -> value
        }

        @Suppress("UNCHECKED_CAST")
        fun exercises(document: Map<String, Any?>) = document["exercises"] as List<Map<String, Any?>>

        @Suppress("UNCHECKED_CAST")
        fun sets(exercise: Map<String, Any?>) = exercise["sets"] as List<Map<String, Any?>>

        fun reps(count: Int) = Triple(count, false, null as Int?)
        fun maxReps() = Triple(null as Int?, true, null as Int?)
        fun hold(seconds: Int) = Triple(null as Int?, false, seconds)

        fun exercise(id: String, movement: String, vararg sets: Triple<Int?, Boolean, Int?>, easier: String? = null,
                     assessment: Boolean = false) = TrainingExercise(
            id = id, movementId = movement, number = 1, name = LocalizedText("Name of $movement"),
            description = LocalizedText("Coach's words"),
            sets = sets.mapIndexed { index, (reps, max, seconds) ->
                TrainingSet("$id-s$index", index + 1, LocalizedText.EMPTY, LocalizedText("Slow and strict"), reps, max, seconds, 90)
            },
            easier = easier?.let { EasierOption(it, LocalizedText("Easier $it")) }, assessment = assessment,
        )

        fun program(id: String, dayType: String? = null, practice: List<TrainingExercise> = emptyList(),
                    main: List<TrainingExercise>) = TrainingProgram(
            id = id, number = id.takeLast(4).toIntOrNull() ?: 0, levelNumber = id.substringAfter(":l", "").take(2).toIntOrNull() ?: 0,
            name = LocalizedText("Block 1 Exam"), description = LocalizedText("A class"),
            hasDetails = true, proPlaceholder = false, week = null, day = null, estimatedMinutes = 40, exercises = main,
            dayType = dayType, practice = practice,
        )
    }
}
