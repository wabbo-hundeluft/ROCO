package com.wabbo.healthconnectcoach

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Metadata
import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovedWellnessScoreAdapterTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val offset = ZoneOffset.ofHours(1)
    private val asOf = Instant.parse("2025-03-15T12:00:00Z")
    private val source = "org.example.synthetic-wearable"

    @Test fun historicalCutoffExcludesFutureMeasurements() {
        val before = sleep(asOf.minusSeconds(36000), asOf.minusSeconds(7200), "past")
        val after = sleep(asOf.plusSeconds(36000), asOf.plusSeconds(72000), "future")
        val input = WellnessScoreRecords(listOf(before, after), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(listOf(before), input.through(asOf).sleepSessions)
        assertEquals(listOf(before, after), input.sleepSessions)
    }

    @Test
    fun sixRealHealthConnectRecordTypesNormalizeScoreAndAutoBindByType() {
        val wakeDate = LocalDate.parse("2025-03-15")
        val currentSleep = sleep(
            wakeDate.minusDays(1).atTime(22, 0).atOffset(offset).toInstant(),
            wakeDate.atTime(6, 0).atOffset(offset).toInstant(),
            "sleep-current",
        )
        val priorSleep = (1..5).map { daysAgo ->
            val date = wakeDate.minusDays(daysAgo.toLong())
            sleep(
                date.minusDays(1).atTime(22, 0).atOffset(offset).toInstant(),
                date.atTime(6, 0).atOffset(offset).toInstant(),
                "sleep-prior-$daysAgo",
            )
        }
        val hrv = mutableListOf(
            HeartRateVariabilityRmssdRecord(
                Instant.parse("2025-03-15T02:00:00Z"), offset, 50.0, metadata("hrv-current"),
            ),
        )
        val rhr = mutableListOf(
            RestingHeartRateRecord(
                Instant.parse("2025-03-15T02:00:00Z"), offset, 60L, metadata("rhr-current"),
            ),
        )
        for (daysAgo in 1..35) {
            val date = wakeDate.minusDays(daysAgo.toLong())
            hrv += HeartRateVariabilityRmssdRecord(
                date.atTime(3, 0).atOffset(offset).toInstant(), offset, 50.0, metadata("hrv-$daysAgo"),
            )
            rhr += RestingHeartRateRecord(
                date.atTime(8, 0).atOffset(offset).toInstant(), offset, 60L, metadata("rhr-$daysAgo"),
            )
        }

        val workouts = mutableListOf<ExerciseSessionRecord>()
        val heartRate = mutableListOf<HeartRateRecord>()
        for (week in 0..3) {
            val start = if (week == 0) asOf.minusSeconds(50 * 60L) else {
                val date = wakeDate.minusDays((week * 7 + 1).toLong())
                date.atTime(LocalTime.of(12, 10)).atOffset(offset).toInstant()
            }
            val end = start.plusSeconds(50 * 60L)
            workouts += ExerciseSessionRecord(
                startTime = start,
                startZoneOffset = offset,
                endTime = end,
                endZoneOffset = offset,
                metadata = metadata("exercise-$week"),
                exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            )
            val bpm = if (week == 0) 70L else 80L
            val samples = (0..100).map { index -> HeartRateRecord.Sample(start.plusSeconds(index * 30L), bpm) }
            heartRate += HeartRateRecord(start, offset, end, offset, samples, metadata("heart-$week"))
        }
        val steps = listOf(
            StepsRecord(
                startTime = asOf.minusSeconds(20 * 60L),
                startZoneOffset = offset,
                endTime = asOf.minusSeconds(10 * 60L),
                endZoneOffset = offset,
                count = 1_000L,
                metadata = metadata("steps-current"),
            ),
        )
        val records = WellnessScoreRecords(
            sleepSessions = priorSleep + currentSleep,
            hrvRmssd = hrv,
            restingHeartRate = rhr,
            exerciseSessions = workouts,
            heartRate = heartRate,
            steps = steps,
        )
        val store = PrimarySourceBindingStore(MemoryBindingPreferences())
        val permissions = listOf("sleep", "hrv", "rhr", "exercise", "heart_rate", "steps")
            .associateWith { ApprovedWellnessScoreEngine.PermissionState.granted }
        val result = ApprovedWellnessScoreAdapter().score(
            records = records,
            asOf = asOf,
            zone = zone,
            bindingStore = store,
            permissions = permissions,
            readComplete = true,
            historyComplete = true,
        )

        assertEquals(setOf(source), result.observedOrigins.values.flatten().toSet())
        assertEquals(6, store.allBindings().size)
        assertTrue(store.allBindings().all { it.packageName == source && it.status == PrimarySourceBindingStatus.AUTO_BOUND })
        assertEquals(setOf(ApprovedWellnessScoreEngine.SLEEP_ID, ApprovedWellnessScoreEngine.BODY_BATTERY_ID, ApprovedWellnessScoreEngine.SESSION_LOAD_ID), result.scores.keys)
        assertEquals(ApprovedWellnessScoreEngine.Status.available, result.scores.getValue(ApprovedWellnessScoreEngine.SLEEP_ID).status)
        assertEquals(ApprovedWellnessScoreEngine.Status.available, result.scores.getValue(ApprovedWellnessScoreEngine.SESSION_LOAD_ID).status)
        assertEquals(50.0, result.scores.getValue(ApprovedWellnessScoreEngine.SESSION_LOAD_ID).rawIndex!!, 0.01)
        assertEquals(100, result.scores.getValue(ApprovedWellnessScoreEngine.SESSION_LOAD_ID).rating0to100)
        assertNotNull(result.scores.getValue(ApprovedWellnessScoreEngine.BODY_BATTERY_ID).score)
        assertEquals(1, result.batteryDayTrend.size)
        assertEquals(asOf, result.batteryDayTrend.single().at)
        assertEquals(result.scores.getValue(ApprovedWellnessScoreEngine.BODY_BATTERY_ID).score, result.batteryDayTrend.single().value)
        assertTrue(result.batteryDayTrend.all { !it.at.isAfter(asOf) && it.value in 0..100 })
        val sleepCoverage = result.scores.getValue(ApprovedWellnessScoreEngine.SLEEP_ID).coverage
        assertEquals(setOf(source), sleepCoverage.sourcesByType[ApprovedWellnessScoreEngine.RecordType.SLEEP_SESSION])
        assertEquals(setOf(source), sleepCoverage.sourcesByType[ApprovedWellnessScoreEngine.RecordType.HRV_RMSSD])
        assertEquals(setOf(source), sleepCoverage.sourcesByType[ApprovedWellnessScoreEngine.RecordType.RESTING_HEART_RATE])
        val normalized = result.normalizedInput
        val normalizedCurrentSleep = normalized.sleeps.single { it.id == "sleep-current" }
        assertEquals(currentSleep.startTime, normalizedCurrentSleep.start)
        assertEquals(currentSleep.endTime, normalizedCurrentSleep.end)
        assertEquals(offset, normalizedCurrentSleep.startZoneOffset)
        assertEquals(offset, normalizedCurrentSleep.endZoneOffset)
        val normalizedPriorNight = normalized.priorNights.single { it.date == wakeDate.minusDays(1) }
        assertEquals(priorSleep.first().startTime, normalizedPriorNight.start)
        assertEquals(priorSleep.first().endTime, normalizedPriorNight.end)
        assertEquals(offset, normalizedPriorNight.startZoneOffset)
        assertEquals(offset, normalizedPriorNight.endZoneOffset)
        assertEquals(offset, normalized.hrv.single { it.id == "hrv-current" }.zoneOffset)
        assertEquals(offset, normalized.rhr.single { it.id == "rhr-current" }.zoneOffset)
        val normalizedWorkout = normalized.workouts.single { it.id == "exercise-0" }
        assertEquals(workouts.first().startTime, normalizedWorkout.start)
        assertEquals(workouts.first().endTime, normalizedWorkout.end)
        assertEquals(offset, normalizedWorkout.startZoneOffset)
        assertEquals(offset, normalizedWorkout.endZoneOffset)
        val normalizedHeartRateSample = normalizedWorkout.heartRate.first()
        assertEquals(heartRate.first().samples.first().time, normalizedHeartRateSample.at)
        assertEquals(offset, normalizedHeartRateSample.recordStartZoneOffset)
        assertEquals(offset, normalizedHeartRateSample.recordEndZoneOffset)
        val normalizedSteps = normalized.steps.single()
        assertEquals(steps.single().startTime, normalizedSteps.start)
        assertEquals(steps.single().endTime, normalizedSteps.end)
        assertEquals(offset, normalizedSteps.startZoneOffset)
        assertEquals(offset, normalizedSteps.endZoneOffset)
    }

    @Test
    fun adjacentSessionsAssignSharedHeartRateBoundaryOnlyToFollowingSession() {
        val firstStart = asOf.minusSeconds(3 * 60 * 60L)
        val sharedBoundary = firstStart.plusSeconds(600)
        val finalEnd = sharedBoundary.plusSeconds(600)
        val first = ExerciseSessionRecord(
            firstStart, offset, sharedBoundary, offset, metadata("boundary-exercise-a"),
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        )
        val second = ExerciseSessionRecord(
            sharedBoundary, offset, finalEnd, offset, metadata("boundary-exercise-b"),
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        )
        val heartRate = HeartRateRecord(
            firstStart,
            offset,
            finalEnd,
            offset,
            (0..40).map { index -> HeartRateRecord.Sample(firstStart.plusSeconds(index * 30L), 120L) },
            metadata("boundary-heart-rate"),
        )
        val sleepStart = firstStart.minusSeconds(8 * 60 * 60L)
        val sleepEnd = firstStart.minusSeconds(60 * 60L)
        val currentSleep = sleep(sleepStart, sleepEnd, "boundary-sleep")
        val sessionDate = firstStart.atZone(zone).toLocalDate()
        val rhr = (1..7).map { daysAgo ->
            val at = sessionDate.minusDays(daysAgo.toLong()).atTime(8, 0).atOffset(offset).toInstant()
            RestingHeartRateRecord(at, offset, 55L, metadata("boundary-rhr-$daysAgo"))
        }
        val normalized = ApprovedWellnessScoreAdapter().score(
            records = WellnessScoreRecords(listOf(currentSleep), emptyList(), rhr, listOf(first, second), listOf(heartRate), emptyList()),
            asOf = asOf,
            zone = zone,
            bindingStore = PrimarySourceBindingStore(MemoryBindingPreferences()),
            permissions = listOf("sleep", "rhr", "exercise", "heart_rate")
                .associateWith { ApprovedWellnessScoreEngine.PermissionState.granted },
            readComplete = true,
            historyComplete = true,
        ).normalizedInput

        val sessions = normalized.workouts.sortedBy { it.start }
        assertEquals(2, sessions.size)
        assertEquals(20, sessions[0].heartRate.size)
        assertEquals(20, sessions[1].heartRate.size)
        assertTrue(sessions[0].heartRate.none { it.at == sharedBoundary })
        assertEquals(1, sessions[1].heartRate.count { it.at == sharedBoundary })
        assertTrue(sessions[1].heartRate.none { it.at == finalEnd })

        // Test pulse mapping independently of readiness eligibility (this fixture has no HRV).
        val coverage=sessions.map {ApprovedWellnessScoreEngine().activityLoad(it,normalized).coverage}
        assertEquals(1_140L, coverage.sumOf {it.coveredSeconds})
        assertEquals(1_200L, coverage.sumOf {it.totalSeconds})
        assertEquals(40, coverage.sumOf {it.uniqueSamples})
    }

    @Test
    fun incompleteHistoryDoesNotClaimCompleteSessionLoadWindow() {
        val store = PrimarySourceBindingStore(MemoryBindingPreferences())
        val result = ApprovedWellnessScoreAdapter().score(
            records = WellnessScoreRecords(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
            asOf = asOf,
            zone = zone,
            bindingStore = store,
            permissions = mapOf("exercise" to ApprovedWellnessScoreEngine.PermissionState.granted),
            readComplete = true,
            historyComplete = false,
        )
        val load = result.scores.getValue(ApprovedWellnessScoreEngine.SESSION_LOAD_ID)
        assertEquals(ApprovedWellnessScoreEngine.Status.insufficient_data, load.status)
        assertTrue("reference_window_incomplete" in load.coverage.missingReasons)
    }

    @Test
    fun sessionLoadMissingDosePermissionPrecedesIncompleteTypedCoverageButGrantedEmptyReadsStayMissingData() {
        val exercise = ExerciseSessionRecord(
            startTime = asOf.minusSeconds(1_800),
            startZoneOffset = offset,
            endTime = asOf.minusSeconds(1_200),
            endZoneOffset = offset,
            metadata = metadata("permission-precedence-exercise"),
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        )
        val recordsWithWorkout = WellnessScoreRecords(
            emptyList(), emptyList(), emptyList(), listOf(exercise), emptyList(), emptyList(),
        )
        val completeExerciseRead = completedEmptyRead(28)
        val completeHeartRateRead = completedEmptyRead(28)
        val completeRhrRead = completedEmptyRead(35)
        val grantedPermissions = mapOf(
            "exercise" to ApprovedWellnessScoreEngine.PermissionState.granted,
            "heart_rate" to ApprovedWellnessScoreEngine.PermissionState.granted,
            "rhr" to ApprovedWellnessScoreEngine.PermissionState.granted,
        )

        listOf("heart_rate", "rhr").forEach { missingPermission ->
            val completeness = SessionLoadReadCoverage.fromReads(
                asOf = asOf,
                exercise = completeExerciseRead,
                heartRate = if (missingPermission == "heart_rate") null else completeHeartRateRead,
                restingHeartRate = if (missingPermission == "rhr") null else completeRhrRead,
            )
            val result = ApprovedWellnessScoreAdapter().score(
                records = recordsWithWorkout,
                asOf = asOf,
                zone = zone,
                bindingStore = PrimarySourceBindingStore(MemoryBindingPreferences()),
                permissions = grantedPermissions + (missingPermission to ApprovedWellnessScoreEngine.PermissionState.missing),
                readComplete = true,
                historyComplete = true,
                recordTypeWindowCompleteness = completeness,
            ).scores.getValue(ApprovedWellnessScoreEngine.SESSION_LOAD_ID)

            assertEquals(missingPermission, ApprovedWellnessScoreEngine.Status.permission_missing, result.status)
            assertTrue("${missingPermission}_permission_missing" in result.coverage.missingReasons)
            assertEquals((0..3).associate { "W$it" to false }, result.coverage.windowCompleteness)
        }

        val completeEmptyCoverage = SessionLoadReadCoverage.fromReads(
            asOf = asOf,
            exercise = completeExerciseRead,
            heartRate = completeHeartRateRead,
            restingHeartRate = completeRhrRead,
        )
        val emptyRecords = ApprovedWellnessScoreAdapter().score(
            records = WellnessScoreRecords(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
            asOf = asOf,
            zone = zone,
            bindingStore = PrimarySourceBindingStore(MemoryBindingPreferences()),
            permissions = grantedPermissions,
            readComplete = true,
            historyComplete = true,
            recordTypeWindowCompleteness = completeEmptyCoverage,
        ).scores.getValue(ApprovedWellnessScoreEngine.SESSION_LOAD_ID)

        assertEquals(ApprovedWellnessScoreEngine.Status.insufficient_data, emptyRecords.status)
        assertTrue("Wref_missing_or_zero" in emptyRecords.coverage.missingReasons)
        assertEquals((0..3).associate { "W$it" to true }, emptyRecords.coverage.windowCompleteness)
    }

    @Test
    fun configuredAppZoneOverridesRecordOffsetsForWakeDateAndBaseline() {
        val appAsOf = Instant.parse("2025-03-15T00:30:00Z")
        val start = Instant.parse("2025-03-14T15:30:00Z")
        val end = Instant.parse("2025-03-14T23:30:00Z")
        val current = SleepSessionRecord(
            startTime = start,
            startZoneOffset = ZoneOffset.UTC,
            endTime = end,
            endZoneOffset = ZoneOffset.UTC,
            metadata = metadata("zone-current-sleep"),
            stages = listOf(SleepSessionRecord.Stage(start, end, SleepSessionRecord.STAGE_TYPE_SLEEPING)),
        )
        val hrv = mutableListOf(
            HeartRateVariabilityRmssdRecord(Instant.parse("2025-03-14T20:00:00Z"), ZoneOffset.UTC, 50.0, metadata("zone-current-hrv")),
        )
        for (daysAgo in 1..7) {
            val date = LocalDate.parse("2025-03-15").minusDays(daysAgo.toLong())
            hrv += HeartRateVariabilityRmssdRecord(
                date.atTime(8, 0).atOffset(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC, 50.0, metadata("zone-hrv-$daysAgo"),
            )
        }
        val store = PrimarySourceBindingStore(MemoryBindingPreferences())
        val result = ApprovedWellnessScoreAdapter().score(
            records = WellnessScoreRecords(listOf(current), hrv, emptyList(), emptyList(), emptyList(), emptyList()),
            asOf = appAsOf,
            zone = zone,
            bindingStore = store,
            permissions = mapOf("sleep" to ApprovedWellnessScoreEngine.PermissionState.granted, "hrv" to ApprovedWellnessScoreEngine.PermissionState.granted),
            readComplete = true,
            historyComplete = true,
        )
        val sleepScore = result.scores.getValue(ApprovedWellnessScoreEngine.SLEEP_ID)
        assertEquals(50, sleepScore.components["H"])
        assertEquals(86, sleepScore.score)
    }

    @Test
    fun singleObservedOriginBindsEvenWhenTheReadWindowIsIncomplete() {
        val store = PrimarySourceBindingStore(MemoryBindingPreferences())
        val currentSleep = sleep(asOf.minusSeconds(8 * 3600L), asOf.minusSeconds(3600L), "partial-window-sleep")
        val result = ApprovedWellnessScoreAdapter().score(
            records = WellnessScoreRecords(listOf(currentSleep), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
            asOf = asOf,
            zone = zone,
            bindingStore = store,
            permissions = mapOf("sleep" to ApprovedWellnessScoreEngine.PermissionState.granted),
            readComplete = false,
            historyComplete = true,
        )
        assertEquals(PrimarySourceBindingStatus.AUTO_BOUND, store.get(ApprovedWellnessRecordTypes.SLEEP_SESSION)?.status)
        assertEquals(ApprovedWellnessScoreEngine.Status.insufficient_data, result.scores.getValue(ApprovedWellnessScoreEngine.SLEEP_ID).status)
    }

    @Test
    fun distinctRecordIdsWithContradictorySameTimestampValuesConflictBeforeAggregation() {
        val wakeDate = LocalDate.parse("2025-03-15")
        val currentSleep = sleep(
            wakeDate.minusDays(1).atTime(22, 0).atOffset(offset).toInstant(),
            wakeDate.atTime(6, 0).atOffset(offset).toInstant(),
            "same-time-current-sleep",
        )
        val currentTime = wakeDate.atTime(3, 0).atOffset(offset).toInstant()
        val hrv = mutableListOf(
            HeartRateVariabilityRmssdRecord(currentTime, offset, 50.0, metadata("same-time-hrv-a")),
            HeartRateVariabilityRmssdRecord(currentTime, offset, 70.0, metadata("same-time-hrv-b")),
        )
        for (daysAgo in 1..7) {
            val date = wakeDate.minusDays(daysAgo.toLong())
            hrv += HeartRateVariabilityRmssdRecord(date.atTime(3, 0).atOffset(offset).toInstant(), offset, 50.0, metadata("same-time-base-$daysAgo"))
        }
        val result = ApprovedWellnessScoreAdapter().score(
            records = WellnessScoreRecords(listOf(currentSleep), hrv, emptyList(), emptyList(), emptyList(), emptyList()),
            asOf = asOf,
            zone = zone,
            bindingStore = PrimarySourceBindingStore(MemoryBindingPreferences()),
            permissions = mapOf("sleep" to ApprovedWellnessScoreEngine.PermissionState.granted, "hrv" to ApprovedWellnessScoreEngine.PermissionState.granted),
            readComplete = true,
            historyComplete = true,
        )
        val sleepResult = result.scores.getValue(ApprovedWellnessScoreEngine.SLEEP_ID)
        assertEquals(ApprovedWellnessScoreEngine.Status.source_conflict, sleepResult.status)
        assertEquals(null, sleepResult.score)
        assertEquals(ApprovedWellnessScoreEngine.Status.source_conflict, result.scores.getValue(ApprovedWellnessScoreEngine.BODY_BATTERY_ID).status)
    }

    @Test
    fun explicitAndNullableHealthConnectOffsetsSurviveNormalizationAcrossDstBoundary() {
        val springForwardStart = Instant.parse("2025-03-30T00:30:00Z")
        val springForwardEnd = Instant.parse("2025-03-30T05:30:00Z")
        val currentSleep = SleepSessionRecord(
            startTime = springForwardStart,
            startZoneOffset = ZoneOffset.ofHours(1),
            endTime = springForwardEnd,
            endZoneOffset = ZoneOffset.ofHours(2),
            metadata = metadata("dst-sleep"),
            stages = listOf(SleepSessionRecord.Stage(springForwardStart, springForwardEnd, SleepSessionRecord.STAGE_TYPE_SLEEPING)),
        )
        val hrvAt = Instant.parse("2025-03-30T02:00:00Z")
        val rhrAt = Instant.parse("2025-03-30T03:00:00Z")
        val exerciseStart = Instant.parse("2025-03-30T05:40:00Z")
        val exerciseEnd = Instant.parse("2025-03-30T06:00:00Z")
        val exercise = ExerciseSessionRecord(
            startTime = exerciseStart,
            startZoneOffset = ZoneOffset.ofHours(2),
            endTime = exerciseEnd,
            endZoneOffset = ZoneOffset.ofHours(2),
            metadata = metadata("dst-exercise"),
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        )
        val heartRate = HeartRateRecord(
            exerciseStart,
            ZoneOffset.ofHours(2),
            exerciseEnd,
            ZoneOffset.ofHours(2),
            listOf(HeartRateRecord.Sample(exerciseStart, 120L), HeartRateRecord.Sample(exerciseEnd, 120L)),
            metadata("dst-hr"),
        )
        val stepsStart = Instant.parse("2025-03-30T06:05:00Z")
        val stepsEnd = Instant.parse("2025-03-30T06:15:00Z")
        val steps = StepsRecord(stepsStart, null, stepsEnd, null, 500L, metadata("dst-steps"))
        val result = ApprovedWellnessScoreAdapter().score(
            records = WellnessScoreRecords(
                listOf(currentSleep),
                listOf(HeartRateVariabilityRmssdRecord(hrvAt, null, 50.0, metadata("dst-hrv"))),
                listOf(RestingHeartRateRecord(rhrAt, null, 55L, metadata("dst-rhr"))),
                listOf(exercise),
                listOf(heartRate),
                listOf(steps),
            ),
            asOf = Instant.parse("2025-03-30T06:30:00Z"),
            zone = zone,
            bindingStore = PrimarySourceBindingStore(MemoryBindingPreferences()),
            permissions = listOf("sleep", "hrv", "rhr", "exercise", "heart_rate", "steps")
                .associateWith { ApprovedWellnessScoreEngine.PermissionState.granted },
            readComplete = true,
            historyComplete = true,
        )
        val normalized = result.normalizedInput
        val sleep = normalized.sleeps.single()
        assertEquals(springForwardStart, sleep.start)
        assertEquals(springForwardEnd, sleep.end)
        assertEquals(ZoneOffset.ofHours(1), sleep.startZoneOffset)
        assertEquals(ZoneOffset.ofHours(2), sleep.endZoneOffset)
        assertEquals(hrvAt, normalized.hrv.single().at)
        assertNull(normalized.hrv.single().zoneOffset)
        assertEquals(rhrAt, normalized.rhr.single().at)
        assertNull(normalized.rhr.single().zoneOffset)
        val normalizedWorkout = normalized.workouts.single()
        assertEquals(exerciseStart, normalizedWorkout.start)
        assertEquals(exerciseEnd, normalizedWorkout.end)
        assertEquals(ZoneOffset.ofHours(2), normalizedWorkout.startZoneOffset)
        assertEquals(ZoneOffset.ofHours(2), normalizedWorkout.endZoneOffset)
        val sample = normalizedWorkout.heartRate.first()
        assertEquals(exerciseStart, sample.at)
        assertNull(sample.zoneOffset)
        assertEquals(ZoneOffset.ofHours(2), sample.recordStartZoneOffset)
        assertEquals(ZoneOffset.ofHours(2), sample.recordEndZoneOffset)
        val normalizedSteps = normalized.steps.single()
        assertEquals(stepsStart, normalizedSteps.start)
        assertEquals(stepsEnd, normalizedSteps.end)
        assertNull(normalizedSteps.startZoneOffset)
        assertNull(normalizedSteps.endZoneOffset)
    }

    @Test
    fun batteryDayHistoryContainsOnlyActualSameDayReadsAndReplacesDuplicateTimestamps() {
        val dayStart = Instant.parse("2025-03-15T00:00:00Z")
        val yesterday = ScoreTrendPoint(dayStart.minusSeconds(60), 40)
        val first = ScoreTrendPoint(dayStart.plusSeconds(3_600), 55)
        val second = ScoreTrendPoint(dayStart.plusSeconds(7_200), 62)
        val future = ScoreTrendPoint(dayStart.plusSeconds(10_800), 70)

        val points = appendBatteryDayReadPoint(
            existing = listOf(yesterday, first, future),
            current = second,
            dayStart = dayStart,
            asOf = second.at,
        )
        assertEquals(listOf(first, second), points)

        val replacement = ScoreTrendPoint(second.at, 64)
        val updated = appendBatteryDayReadPoint(points, replacement, dayStart, second.at)
        assertEquals(listOf(first, replacement), updated)
        assertEquals(listOf(first), appendBatteryDayReadPoint(updated, null, dayStart, first.at))
    }

    private fun sleep(start: Instant, end: Instant, id: String) = SleepSessionRecord(
        startTime = start,
        startZoneOffset = offset,
        endTime = end,
        endZoneOffset = offset,
        metadata = metadata(id),
        stages = listOf(SleepSessionRecord.Stage(start, end, SleepSessionRecord.STAGE_TYPE_SLEEPING)),
    )

    private fun metadata(id: String): Metadata {
        val record = Metadata.manualEntryWithId(id)
        Metadata::class.java.getDeclaredField("dataOrigin").apply {
            isAccessible = true
            set(record, DataOrigin(source))
        }
        return record
    }

    private fun completedEmptyRead(days: Long) = HealthConnectReadResult(
        records = emptyList<String>(),
        window = HealthConnectQueryWindow(asOf.minus(Duration.ofDays(days)), asOf, isClipped = false),
    )

    private class MemoryBindingPreferences : PrimarySourceBindingPreferences {
        private val values = linkedMapOf<String, String>()
        override fun getString(key: String): String? = values[key]
        override fun keys(): Set<String> = values.keys.toSet()
        override fun putString(key: String, value: String) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
    }
}
