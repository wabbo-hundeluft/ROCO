package com.wabbo.healthconnectcoach

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Metadata
import com.example.healthcoach.score.contract.ScoreStatus
import com.wabbo.healthconnectcoach.score.HealthCoachScoreEngineImpl
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectScoreAdapterIntegrationTest {
    private val adapter = HealthConnectScoreAdapter(HealthCoachScoreEngineImpl())
    private val baseAsOf = OffsetDateTime.parse("2025-03-15T12:00:00+01:00")
    private val berlin = ZoneId.of("Europe/Berlin")
    private val plusOne = ZoneOffset.ofHours(1)
    private val source = "org.example.synthetic-wearable"

    @Test
    fun readinessRecordsNormalizeUnitsSourceIdsDailyMediansAndScore50() {
        val hrv = mutableListOf<HeartRateVariabilityRmssdRecord>()
        val rhr = mutableListOf<RestingHeartRateRecord>()
        for (daysAgo in 8L downTo 1L) {
            val date = baseAsOf.toLocalDate().minusDays(daysAgo)
            val isCurrent = daysAgo == 1L
            val hrvValues = if (isCurrent) listOf(40.0) else listOf(39.0, 41.0)
            hrvValues.forEachIndexed { index, value ->
                hrv += hrv(date, value, "$date-hrv-$index")
            }
            val rhrValues = if (isCurrent) listOf(60L) else listOf(59L, 61L)
            rhrValues.forEachIndexed { index, value ->
                rhr += rhr(date, value, "$date-rhr-$index")
            }
        }

        val request = adapter.normalizeReadiness(hrv, rhr, baseAsOf)
        assertEquals(40.0, request.hrvRmssdMs.last().value, 0.0)
        assertEquals(60.0, request.restingHeartRateBpm.last().value, 0.0)
        assertEquals("org.example.synthetic-wearable", request.hrvRmssdMs.first().source)
        assertEquals("2025-03-07-hrv-0", request.hrvRmssdMs.first().recordId)
        assertEquals(40.0, request.hrvBaseline.first { it.date == LocalDate.parse("2025-03-07") }.value, 0.0)
        assertEquals(60.0, request.restingHeartRateBaseline.first { it.date == LocalDate.parse("2025-03-07") }.value, 0.0)

        val score = adapter.scoreReadiness(hrv, rhr, baseAsOf)
        assertEquals(50, score.value0to100)
        assertEquals(ScoreStatus.AVAILABLE, score.status)
        assertEquals(listOf(50, 50), score.components.map { it.value0to100 })
        assertEquals(listOf("org.example.synthetic-wearable"), score.dataSources)
        score.components.forEach { assertEquals(false, it.details["timezoneInferred"]) }
    }

    @Test
    fun readinessRatiosProduce52AndUnresolvedSourcesConflict() {
        val hrv = hrvHistory { date, id -> hrv(date, 40.0, id) }.toMutableList()
        val rhr = rhrHistory { date, id -> rhr(date, 60L, id) }.toMutableList()
        hrv += hrv(LocalDate.parse("2025-03-14"), 48.0, "current-hrv", source)
        rhr += rhr(LocalDate.parse("2025-03-14"), 72L, "current-rhr", source)

        val higher = adapter.scoreReadiness(hrv, rhr, baseAsOf)
        assertEquals(52, higher.value0to100)
        assertEquals(listOf(60, 40), higher.components.map { it.value0to100 })
        assertEquals(ScoreStatus.AVAILABLE, higher.status)

        val sourceA = hrvHistory { date, id -> hrv(date, 40.0, id, "source-a") }.toMutableList()
        val sourceB = rhrHistory { date, id -> rhr(date, 60L, id, "source-b") }.toMutableList()
        sourceA += hrv(LocalDate.parse("2025-03-14"), 40.0, "current-a", "source-a")
        sourceB += rhr(LocalDate.parse("2025-03-14"), 60L, "current-b", "source-b")

        val conflict = adapter.scoreReadiness(sourceA, sourceB, baseAsOf)
        assertNull(conflict.value0to100)
        assertEquals(ScoreStatus.SOURCE_CONFLICT, conflict.status)
        assertTrue("readiness_metrics_use_different_sources_without_primary_source" in conflict.missingReasons)
    }

    @Test
    fun sleepStagesMapToAvailable100AndMissingStagesUseEstimatedFallback() {
        val current = sleepRecord(
            start = Instant.parse("2025-03-14T21:00:00Z"),
            end = Instant.parse("2025-03-15T05:00:00Z"),
            id = "sleep-current",
            source = source,
            startOffset = plusOne,
            endOffset = plusOne,
            stages = listOf(
                stage("2025-03-14T21:00:00Z", "2025-03-15T01:00:00Z", SleepSessionRecord.STAGE_TYPE_LIGHT),
                stage("2025-03-15T01:00:00Z", "2025-03-15T03:00:00Z", SleepSessionRecord.STAGE_TYPE_DEEP),
                stage("2025-03-15T03:00:00Z", "2025-03-15T05:00:00Z", SleepSessionRecord.STAGE_TYPE_REM),
            ),
        )
        val prior = (10..14).map { wakeDay ->
            val wakeDate = LocalDate.of(2025, 3, wakeDay)
            sleepRecord(
                start = wakeDate.minusDays(1).atTime(22, 0).atOffset(plusOne).toInstant(),
                end = wakeDate.atTime(6, 0).atOffset(plusOne).toInstant(),
                id = "sleep-prior-$wakeDay",
                source = source,
                startOffset = plusOne,
                endOffset = plusOne,
            )
        }
        val all = prior + current
        val normalized = adapter.normalizeSleep(all, baseAsOf)
        assertEquals(source, normalized.sessions.last().source)
        assertEquals("sleep-current", normalized.sessions.last().recordId)
        assertEquals(listOf("LIGHT", "DEEP", "REM"), normalized.sessions.last().stages!!.map { it.stage })
        assertEquals(listOf(240.0, 120.0, 120.0), normalized.sessions.last().stages!!.map { it.minutes })
        assertEquals(5, normalized.priorNights.size)

        val scored = adapter.scoreSleep(all, baseAsOf)
        assertEquals(100, scored.value0to100)
        assertEquals(ScoreStatus.AVAILABLE, scored.status)

        val noStages = sleepRecord(
            start = Instant.parse("2025-03-14T21:00:00Z"),
            end = Instant.parse("2025-03-15T05:00:00Z"),
            id = "sleep-no-stages",
            source = source,
            startOffset = plusOne,
            endOffset = plusOne,
        )
        val estimated = adapter.scoreSleep(listOf(noStages), baseAsOf)
        assertEquals(100, estimated.value0to100)
        assertEquals(ScoreStatus.PROVISIONAL, estimated.status)
        assertTrue(estimated.components.first().estimated)
    }

    @Test
    fun stageOverflowOrOverlapIsAConflictWithoutScoring() {
        val record = sleepRecord(
            start = Instant.parse("2025-03-14T21:00:00Z"),
            end = Instant.parse("2025-03-15T05:00:00Z"),
            id = "sleep-overflow",
            source = source,
            startOffset = plusOne,
            endOffset = plusOne,
        )
        SleepSessionRecord::class.java.getDeclaredField("stages").apply {
            isAccessible = true
            set(
                record,
                listOf(
                    stage("2025-03-14T21:00:00Z", "2025-03-15T02:00:00Z", SleepSessionRecord.STAGE_TYPE_LIGHT),
                    stage("2025-03-15T01:00:00Z", "2025-03-15T05:00:00Z", SleepSessionRecord.STAGE_TYPE_DEEP),
                ),
            )
        }
        val request = adapter.normalizeSleep(listOf(record), baseAsOf)
        assertEquals("sleep_stage_intervals_overlap", request.sessions.single().normalizationConflictReason)
        val score = adapter.scoreSleep(listOf(record), baseAsOf)
        assertNull(score.value0to100)
        assertEquals(ScoreStatus.SOURCE_CONFLICT, score.status)
    }

    @Test
    fun stageIntervalOutsideSessionIsAConflictWithoutScoring() {
        val record = sleepRecord(
            start = Instant.parse("2025-03-14T21:00:00Z"),
            end = Instant.parse("2025-03-15T05:00:00Z"),
            id = "sleep-stage-outside",
            source = source,
            startOffset = plusOne,
            endOffset = plusOne,
        )
        SleepSessionRecord::class.java.getDeclaredField("stages").apply {
            isAccessible = true
            set(
                record,
                listOf(stage("2025-03-14T20:59:00Z", "2025-03-14T21:30:00Z", SleepSessionRecord.STAGE_TYPE_LIGHT)),
            )
        }

        val result = adapter.scoreSleep(listOf(record), baseAsOf)
        assertNull(result.value0to100)
        assertEquals(ScoreStatus.SOURCE_CONFLICT, result.status)
        assertEquals("sleep_stage_interval_outside_session", result.missingReasons.single())
    }

    @Test
    fun trainingSessionsKeepEveryExerciseTypeAndUseInstantElapsedDuration() {
        val start = Instant.parse("2025-03-15T10:00:00Z")
        val end = Instant.parse("2025-03-15T11:00:00Z")
        val records = listOf(
            exercise(start, end, "yoga", ExerciseSessionRecord.EXERCISE_TYPE_YOGA, plusOne, plusOne),
            exercise(start, end, "other", ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT, plusOne, plusOne),
        )
        val normalized = adapter.normalizeTraining(records)
        assertEquals(2, normalized.sessions.size)
        assertEquals(source, normalized.sessions.first().source)
        assertEquals("yoga", normalized.sessions.first().recordId)

        val immediateAsOf = OffsetDateTime.parse("2025-03-15T12:00:00+01:00")
        val immediate = adapter.scoreTraining(records.take(1), immediateAsOf)
        assertEquals(0, immediate.value0to100)
        assertEquals(ScoreStatus.AVAILABLE, immediate.status)
        assertEquals(60.0, immediate.components.single().details["sessionDurationMinutes"] as Double, 0.0)
        assertEquals(true, immediate.components.single().details["durationUsesInstants"])
        val after27h = adapter.scoreTraining(records.take(1), immediateAsOf.plusHours(27))
        assertEquals(100, after27h.value0to100)
        assertEquals(ScoreStatus.INSUFFICIENT_DATA, adapter.scoreTraining(emptyList(), immediateAsOf).status)
    }

    @Test
    fun explicitOffsetWinsAndNullOffsetUsesSystemZoneAcrossSpringDstGap() {
        val explicit = normalizeHealthConnectTimestamp(
            Instant.parse("2025-03-30T00:30:00Z"),
            ZoneOffset.UTC,
            berlin,
        )
        assertEquals(Instant.parse("2025-03-30T00:30:00Z"), explicit.value.toInstant())
        assertEquals(ZoneOffset.UTC, explicit.value.offset)
        assertFalse(explicit.timezoneInferred)
        assertNull(explicit.timezoneInferenceSource)

        val beforeGap = normalizeHealthConnectTimestamp(Instant.parse("2025-03-30T00:59:59Z"), null, berlin)
        val afterGap = normalizeHealthConnectTimestamp(Instant.parse("2025-03-30T01:00:00Z"), null, berlin)
        assertEquals(OffsetDateTime.parse("2025-03-30T01:59:59+01:00"), beforeGap.value)
        assertEquals(OffsetDateTime.parse("2025-03-30T03:00:00+02:00"), afterGap.value)
        assertEquals("systemDefaultZoneId", afterGap.timezoneInferenceSource)
        assertTrue(beforeGap.timezoneInferred)
        assertTrue(afterGap.timezoneInferred)
    }

    @Test
    fun readinessWithNullOffsetsUsesLocalMar31AndMarksTimezoneInference() = withBerlin {
        val hrv = mutableListOf<HeartRateVariabilityRmssdRecord>()
        val rhr = mutableListOf<RestingHeartRateRecord>()
        for (day in 24..30) {
            val date = LocalDate.of(2025, 3, day)
            hrv += hrv(date, 40.0, "baseline-h-$day", source, null)
            rhr += rhr(date, 60L, "baseline-r-$day", source, null)
        }
        val instant = Instant.parse("2025-03-30T22:30:00Z")
        hrv += HeartRateVariabilityRmssdRecord(instant, null, 40.0, metadata("current-h", source))
        rhr += RestingHeartRateRecord(instant, null, 60L, metadata("current-r", source))
        val asOf = OffsetDateTime.parse("2025-04-01T12:00:00+02:00")

        val result = adapter.scoreReadiness(hrv, rhr, asOf)
        assertEquals(LocalDate.parse("2025-03-31"), result.window.currentDay)
        assertEquals(50, result.value0to100)
        assertEquals(ScoreStatus.AVAILABLE, result.status)
        result.components.forEach { component ->
            assertEquals(true, component.details["timezoneInferred"])
            assertEquals("systemDefaultZoneId", component.details["timezoneInferenceSource"])
        }

        val higherHrv = hrv.dropLast(1) + HeartRateVariabilityRmssdRecord(instant, null, 48.0, metadata("current-h-48", source))
        val higherRhr = rhr.dropLast(1) + RestingHeartRateRecord(instant, null, 72L, metadata("current-r-72", source))
        assertEquals(52, adapter.scoreReadiness(higherHrv, higherRhr, asOf).value0to100)
    }

    @Test
    fun springDstSleepAndWorkoutDurationsUseInstantsNotWallClock() = withBerlin {
        val sleep = sleepRecord(
            start = Instant.parse("2025-03-29T22:00:00Z"),
            end = Instant.parse("2025-03-30T05:00:00Z"),
            id = "spring-sleep",
            source = source,
            startOffset = null,
            endOffset = null,
        )
        val sleepAsOf = OffsetDateTime.parse("2025-03-30T12:00:00+02:00")
        val sleepScore = adapter.scoreSleep(listOf(sleep), sleepAsOf)
        assertEquals(88, sleepScore.value0to100)
        assertEquals(ScoreStatus.PROVISIONAL, sleepScore.status)
        assertEquals(LocalDate.parse("2025-03-30"), sleepScore.window.wakeDate)
        val sleepDetails = sleepScore.components.first().details
        assertEquals(420.0, sleepDetails["sleepMinutes"] as Double, 0.0)
        assertEquals(210.0, sleepDetails["midpointMinutes"] as Double, 0.0)
        assertEquals("03:30", sleepDetails["midpointLocalTime"])
        assertEquals(true, sleepDetails["timezoneInferred"])
        assertEquals("systemDefaultZoneId", sleepDetails["timezoneInferenceSource"])

        val explicitOffsetChangeSleep = sleepRecord(
            start = Instant.parse("2025-03-29T22:00:00Z"),
            end = Instant.parse("2025-03-30T05:00:00Z"),
            id = "spring-sleep-explicit-offset-change",
            source = source,
            startOffset = plusOne,
            endOffset = ZoneOffset.ofHours(2),
        )
        val explicitOffsetScore = adapter.scoreSleep(listOf(explicitOffsetChangeSleep), sleepAsOf)
        assertEquals("03:30", explicitOffsetScore.components.first().details["midpointLocalTime"])
        assertEquals(true, explicitOffsetScore.components.first().details["timezoneInferred"])
        assertEquals("systemDefaultZoneId", explicitOffsetScore.components.first().details["timezoneInferenceSource"])

        val workout = exercise(
            Instant.parse("2025-03-30T00:30:00Z"),
            Instant.parse("2025-03-30T01:30:00Z"),
            "spring-workout",
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            null,
            null,
        )
        val workoutAsOf = OffsetDateTime.parse("2025-03-30T03:30:00+02:00")
        val immediate = adapter.scoreTraining(listOf(workout), workoutAsOf)
        assertEquals(0, immediate.value0to100)
        assertEquals(60.0, immediate.components.single().details["sessionDurationMinutes"] as Double, 0.0)
        assertEquals(true, immediate.components.single().details["durationUsesInstants"])
        assertEquals(100, adapter.scoreTraining(listOf(workout), workoutAsOf.plusHours(27)).value0to100)
    }

    @Test
    fun maggusMidpointVectorsUseEuropeBerlinOffsetAtMidpointInstant() = withBerlin {
        val asOf = OffsetDateTime.parse("2025-03-30T12:00:00+02:00")
        val vectorA = sleepRecord(
            start = Instant.parse("2025-03-29T20:00:00Z"),
            end = Instant.parse("2025-03-30T02:00:00Z"),
            id = "maggus-vector-a",
            source = source,
            startOffset = plusOne,
            endOffset = ZoneOffset.ofHours(2),
        )
        val vectorB = sleepRecord(
            start = Instant.parse("2025-03-29T23:30:00Z"),
            end = Instant.parse("2025-03-30T02:30:00Z"),
            id = "maggus-vector-b",
            source = source,
            startOffset = plusOne,
            endOffset = ZoneOffset.ofHours(2),
        )

        val normalized = adapter.normalizeSleep(listOf(vectorA, vectorB), asOf).sessions.associateBy { it.recordId }
        assertEquals(OffsetDateTime.parse("2025-03-29T21:00:00+01:00"), normalized["maggus-vector-a"]!!.start)
        assertEquals(OffsetDateTime.parse("2025-03-30T04:00:00+02:00"), normalized["maggus-vector-a"]!!.end)
        assertEquals(OffsetDateTime.parse("2025-03-30T00:30:00+01:00"), normalized["maggus-vector-b"]!!.start)
        assertEquals(OffsetDateTime.parse("2025-03-30T04:30:00+02:00"), normalized["maggus-vector-b"]!!.end)
        assertTrue(normalized["maggus-vector-a"]!!.midpointTimezoneInferred)
        assertTrue(normalized["maggus-vector-b"]!!.midpointTimezoneInferred)

        val scoreA = adapter.scoreSleep(listOf(vectorA), asOf)
        assertEquals(360.0, scoreA.components.first().details["sleepMinutes"] as Double, 0.0)
        assertEquals(0.0, scoreA.components.first().details["midpointMinutes"] as Double, 0.0)
        assertEquals("00:00", scoreA.components.first().details["midpointLocalTime"])
        assertEquals(true, scoreA.components.first().details["timezoneInferred"])
        assertEquals("systemDefaultZoneId", scoreA.components.first().details["timezoneInferenceSource"])

        val scoreB = adapter.scoreSleep(listOf(vectorB), asOf)
        assertEquals(180.0, scoreB.components.first().details["sleepMinutes"] as Double, 0.0)
        assertEquals(180.0, scoreB.components.first().details["midpointMinutes"] as Double, 0.0)
        assertEquals("03:00", scoreB.components.first().details["midpointLocalTime"])
        assertEquals(true, scoreB.components.first().details["timezoneInferred"])
        assertEquals("systemDefaultZoneId", scoreB.components.first().details["timezoneInferenceSource"])
    }

    @Test
    fun autumnFoldKeepsBothInstantOccurrencesDistinct() {
        val first = normalizeHealthConnectTimestamp(Instant.parse("2025-10-26T00:30:00Z"), null, berlin)
        val second = normalizeHealthConnectTimestamp(Instant.parse("2025-10-26T01:30:00Z"), null, berlin)
        assertEquals(first.value.toLocalDateTime(), second.value.toLocalDateTime())
        assertEquals(ZoneOffset.ofHours(2), first.value.offset)
        assertEquals(ZoneOffset.ofHours(1), second.value.offset)
        assertTrue(first.value.toInstant().isBefore(second.value.toInstant()))
    }

    @Test
    fun duplicateRecordIdWithDifferentValuesCannotBeHiddenByNormalization() {
        val records = listOf(
            hrv(LocalDate.parse("2025-03-14"), 40.0, "same-id"),
            hrv(LocalDate.parse("2025-03-14"), 41.0, "same-id"),
        )
        val request = adapter.normalizeReadiness(records, emptyList(), baseAsOf)
        assertNotNull(request.normalizationConflictReason)
        assertEquals(ScoreStatus.SOURCE_CONFLICT, adapter.scoreReadiness(records, emptyList(), baseAsOf).status)
    }

    private fun hrvHistory(create: (LocalDate, String) -> HeartRateVariabilityRmssdRecord): List<HeartRateVariabilityRmssdRecord> =
        (8L downTo 2L).map { daysAgo -> create(baseAsOf.toLocalDate().minusDays(daysAgo), "hrv-$daysAgo") }

    private fun rhrHistory(create: (LocalDate, String) -> RestingHeartRateRecord): List<RestingHeartRateRecord> =
        (8L downTo 2L).map { daysAgo -> create(baseAsOf.toLocalDate().minusDays(daysAgo), "rhr-$daysAgo") }

    private fun hrv(
        date: LocalDate,
        value: Double,
        id: String,
        recordSource: String = source,
        offset: ZoneOffset? = plusOne,
    ) = HeartRateVariabilityRmssdRecord(
        date.atTime(8, 0).atOffset(offset ?: plusOne).toInstant(),
        offset,
        value,
        metadata(id, recordSource),
    )

    private fun rhr(
        date: LocalDate,
        value: Long,
        id: String,
        recordSource: String = source,
        offset: ZoneOffset? = plusOne,
    ) = RestingHeartRateRecord(
        date.atTime(8, 0).atOffset(offset ?: plusOne).toInstant(),
        offset,
        value,
        metadata(id, recordSource),
    )

    private fun sleepRecord(
        start: Instant,
        end: Instant,
        id: String,
        source: String,
        startOffset: ZoneOffset?,
        endOffset: ZoneOffset?,
        stages: List<SleepSessionRecord.Stage> = emptyList(),
    ) = SleepSessionRecord(
        startTime = start,
        startZoneOffset = startOffset,
        endTime = end,
        endZoneOffset = endOffset,
        metadata = metadata(id, source),
        stages = stages,
    )

    private fun exercise(
        start: Instant,
        end: Instant,
        id: String,
        type: Int,
        startOffset: ZoneOffset?,
        endOffset: ZoneOffset?,
    ) = ExerciseSessionRecord(
        startTime = start,
        startZoneOffset = startOffset,
        endTime = end,
        endZoneOffset = endOffset,
        metadata = metadata(id, source),
        exerciseType = type,
    )

    private fun stage(start: String, end: String, type: Int) = SleepSessionRecord.Stage(
        Instant.parse(start),
        Instant.parse(end),
        type,
    )

    private fun metadata(id: String, source: String): Metadata {
        val synthetic = Metadata.manualEntryWithId(id)
        Metadata::class.java.getDeclaredField("dataOrigin").apply {
            isAccessible = true
            set(synthetic, DataOrigin(source))
        }
        return synthetic
    }

    private fun <T> withBerlin(block: () -> T): T {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
            return block()
        } finally {
            TimeZone.setDefault(previous)
        }
    }
}
