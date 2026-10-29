package com.wabbo.healthconnectcoach.score

import com.example.healthcoach.score.contract.ExerciseSession
import com.example.healthcoach.score.contract.PriorSleepNight
import com.example.healthcoach.score.contract.ReadinessBaselineDay
import com.example.healthcoach.score.contract.ReadinessMeasurement
import com.example.healthcoach.score.contract.ReadinessRequest
import com.example.healthcoach.score.contract.ScoreResult
import com.example.healthcoach.score.contract.ScoreStatus
import com.example.healthcoach.score.contract.SleepRequest
import com.example.healthcoach.score.contract.SleepSession
import com.example.healthcoach.score.contract.SleepStageMinutes
import com.example.healthcoach.score.contract.TrainingRequest
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthCoachScoreEngineTest {
    private val engine = HealthCoachScoreEngineImpl()
    private val now = OffsetDateTime.parse("2025-03-15T12:00:00Z")
    private val fullSleepStages = listOf(SleepStageMinutes("SLEEPING", 480.0))

    private fun session(
        start: OffsetDateTime = OffsetDateTime.parse("2025-03-14T22:00:00Z"),
        end: OffsetDateTime = OffsetDateTime.parse("2025-03-15T06:00:00Z"),
        stages: List<SleepStageMinutes>? = fullSleepStages,
        source: String = "sleep-device",
        recordId: String? = "sleep-1",
    ) = SleepSession(start, end, source, recordId, stages)

    private fun priorNights(
        count: Int = 6,
        midpoint: Double = 120.0,
        source: String = "sleep-device",
    ): List<PriorSleepNight> = (count downTo 1).map { offset ->
        PriorSleepNight(now.toLocalDate().minusDays(offset.toLong()), midpoint, source)
    }

    private fun sleepRequest(
        sessions: List<SleepSession> = listOf(session()),
        goal: Double = 480.0,
        prior: List<PriorSleepNight> = emptyList(),
        primarySource: String? = null,
    ) = SleepRequest(sessions, goal, prior, primarySource)

    private fun readinessHistory(
        count: Int = 7,
        hrv: Double = 40.0,
        rhr: Double = 60.0,
        source: String = "wearable",
    ): Pair<List<ReadinessBaselineDay>, List<ReadinessBaselineDay>> {
        val firstDay = now.toLocalDate().minusDays((count + 1).toLong())
        val days = (0 until count).map { firstDay.plusDays(it.toLong()) }
        return days.map { ReadinessBaselineDay(it, hrv, source) } to
            days.map { ReadinessBaselineDay(it, rhr, source) }
    }

    private fun readinessRequest(
        hrv: Double = 40.0,
        rhr: Double = 60.0,
        hrvTime: OffsetDateTime? = OffsetDateTime.parse("2025-03-14T08:00:00Z"),
        rhrTime: OffsetDateTime? = OffsetDateTime.parse("2025-03-14T08:00:00Z"),
        source: String = "wearable",
        historyCount: Int = 7,
        hrvHistory: List<ReadinessBaselineDay>? = null,
        rhrHistory: List<ReadinessBaselineDay>? = null,
    ): ReadinessRequest {
        val (defaultHrvHistory, defaultRhrHistory) = readinessHistory(count = historyCount, source = source)
        return ReadinessRequest(
            hrvRmssdMs = listOf(ReadinessMeasurement(hrv, hrvTime, source, "h1")),
            restingHeartRateBpm = listOf(ReadinessMeasurement(rhr, rhrTime, source, "r1")),
            hrvBaseline = hrvHistory ?: defaultHrvHistory,
            restingHeartRateBaseline = rhrHistory ?: defaultRhrHistory,
        )
    }

    private fun assertContract(result: ScoreResult, scoreId: String, expectedComponents: Int) {
        assertEquals(scoreId, result.scoreId)
        assertTrue(
            result.status in setOf(
                ScoreStatus.AVAILABLE,
                ScoreStatus.PROVISIONAL,
                ScoreStatus.INSUFFICIENT_DATA,
                ScoreStatus.PERMISSION_MISSING,
                ScoreStatus.STALE,
                ScoreStatus.SOURCE_CONFLICT,
            ),
        )
        assertNotNull(result.asOf)
        assertNotNull(result.window)
        assertNotNull(result.components)
        assertNotNull(result.dataSources)
        assertNotNull(result.coverage)
        assertNotNull(result.missingReasons)
        assertEquals(HealthCoachScoreEngineImpl.ALGORITHM_VERSION, result.algorithmVersion)
        assertEquals(expectedComponents, result.coverage.expectedComponents)
        result.value0to100?.let { assertTrue(it in 0..100) }
    }

    @Test
    fun testDuration480Of480WithRegularBaselineIs100() {
        val result = engine.scoreSleep(sleepRequest(prior = priorNights()), now)
        assertEquals(100, result.value0to100)
        assertEquals(ScoreStatus.AVAILABLE, result.status)
        assertEquals(listOf("duration", "regularity"), result.components.map { it.id })
        assertEquals(100, result.components[0].value0to100)
        assertEquals(100, result.components[1].value0to100)
        assertContract(result, "sleep", 2)
    }

    @Test
    fun testDuration360Of480WithoutRegularityIs75AndProvisional() {
        val result = engine.scoreSleep(
            sleepRequest(sessions = listOf(session(stages = listOf(SleepStageMinutes("LIGHT", 360.0))))),
            now,
        )
        assertEquals(75, result.value0to100)
        assertEquals(ScoreStatus.PROVISIONAL, result.status)
        assertEquals(75, result.components[0].value0to100)
        assertTrue("sleep_regularity_requires_at_least_5_valid_prior_nights" in result.missingReasons)
    }

    @Test
    fun testSleepFallbackUsesSessionDurationAndMarksEstimate() {
        val result = engine.scoreSleep(sleepRequest(sessions = listOf(session(stages = null))), now)
        assertEquals(100, result.value0to100)
        assertEquals(ScoreStatus.PROVISIONAL, result.status)
        assertTrue(result.components.first().estimated)
        assertTrue("sleep_stages_missing_session_duration_used_as_estimated_upper_bound" in result.missingReasons)
    }

    @Test
    fun testShortSessionDoesNotCountAsMainSleep() {
        val end = OffsetDateTime.parse("2025-03-15T06:00:00Z")
        val result = engine.scoreSleep(
            sleepRequest(sessions = listOf(session(start = end.minusHours(2), end = end, stages = listOf(SleepStageMinutes("SLEEPING", 120.0))))),
            now,
        )
        assertNull(result.value0to100)
        assertEquals(ScoreStatus.INSUFFICIENT_DATA, result.status)
    }

    @Test
    fun testStaleSleepIsNotScored() {
        val end = now.minusHours(25)
        val result = engine.scoreSleep(
            sleepRequest(sessions = listOf(session(start = end.minusHours(8), end = end))),
            now,
        )
        assertNull(result.value0to100)
        assertEquals(ScoreStatus.STALE, result.status)
    }

    @Test
    fun testOverlappingCompetingSessionsAreSourceConflict() {
        val a = session(recordId = "a")
        val b = session(recordId = "b", source = "other-device")
        val competing = engine.scoreSleep(sleepRequest(sessions = listOf(a, b)), now)
        assertNull(competing.value0to100)
        assertEquals(ScoreStatus.SOURCE_CONFLICT, competing.status)

        val sameSourceOverlap = session(recordId = "c")
        assertEquals(
            ScoreStatus.SOURCE_CONFLICT,
            engine.scoreSleep(sleepRequest(sessions = listOf(a, sameSourceOverlap)), now).status,
        )

        val earlierEnd = OffsetDateTime.parse("2025-03-14T20:00:00Z")
        val separateA = session(
            start = earlierEnd.minusHours(8), end = earlierEnd, source = "device-a", recordId = "d",
        )
        val separateB = session(source = "device-b", recordId = "e")
        assertEquals(
            ScoreStatus.SOURCE_CONFLICT,
            engine.scoreSleep(sleepRequest(sessions = listOf(separateA, separateB)), now).status,
        )
    }

    @Test
    fun testRegularityUsesCircularClockDistanceAcrossMidnight() {
        val currentEnd = OffsetDateTime.parse("2025-03-15T06:00:00Z")
        val currentStart = OffsetDateTime.parse("2025-03-14T22:00:00Z")
        val nearMidnight = engine.scoreSleep(
            sleepRequest(
                sessions = listOf(session(start = currentStart, end = currentEnd)),
                prior = priorNights(midpoint = 1435.0),
            ),
            now,
        )
        assertEquals(0, nearMidnight.components[1].value0to100)

        val midnightNights = (6 downTo 1).mapIndexed { index, offset ->
            val midpoint = listOf(1435.0, 1435.0, 1435.0, 5.0, 5.0, 5.0)[index]
            PriorSleepNight(now.toLocalDate().minusDays(offset.toLong()), midpoint, "sleep-device")
        }
        val current = session(
            start = OffsetDateTime.parse("2025-03-14T20:00:00Z"),
            end = OffsetDateTime.parse("2025-03-15T04:00:00Z"),
        )
        val midnightResult = engine.scoreSleep(sleepRequest(sessions = listOf(current), prior = midnightNights), now)
        val regularity = midnightResult.components[1]
        assertTrue((regularity.details["deviationMinutes"] as Double) <= 5.0)
        assertTrue(regularity.value0to100 >= 96)
    }

    @Test
    fun testSleepPermissionMissingReturnsNull() {
        val result = engine.scoreSleep(sleepRequest(sessions = emptyList()), now, permissionGranted = false)
        assertNull(result.value0to100)
        assertEquals(ScoreStatus.PERMISSION_MISSING, result.status)
    }

    @Test
    fun testEqualReadinessValuesToBaselineScore50() {
        val result = engine.scoreReadiness(readinessRequest(), now)
        assertEquals(50, result.value0to100)
        assertEquals(ScoreStatus.AVAILABLE, result.status)
        assertEquals(listOf(50, 50), result.components.map { it.value0to100 })
        assertContract(result, "readiness", 2)
    }

    @Test
    fun testHrvAndRestingPulseTwentyPercentHigherFollowV03Formulas() {
        val result = engine.scoreReadiness(readinessRequest(hrv = 48.0, rhr = 72.0), now)
        assertEquals(60, result.components[0].value0to100)
        assertEquals(40, result.components[1].value0to100)
        assertEquals(52, result.value0to100)
    }

    @Test
    fun testOneAvailableMetricIsProvisionalAndRenormalized() {
        val full = readinessRequest()
        val hrvOnly = full.copy(restingHeartRateBpm = emptyList())
        val result = engine.scoreReadiness(hrvOnly, now)
        assertEquals(50, result.value0to100)
        assertEquals(ScoreStatus.PROVISIONAL, result.status)
        assertEquals(0.5, result.coverage.ratio, 0.0)

        val untimestampedRhr = full.copy(
            restingHeartRateBpm = listOf(ReadinessMeasurement(60.0, null, "wearable", "r1")),
        )
        val partial = engine.scoreReadiness(untimestampedRhr, now)
        assertEquals(ScoreStatus.PROVISIONAL, partial.status)
        assertEquals(50, partial.value0to100)
        assertTrue("missing_timestamp_for_current_resting_heart_rate_bpm" in partial.missingReasons)

        val noTimestamps = full.copy(
            hrvRmssdMs = listOf(ReadinessMeasurement(40.0, null, "wearable", "h1")),
            restingHeartRateBpm = listOf(ReadinessMeasurement(60.0, null, "wearable", "r1")),
        )
        val absent = engine.scoreReadiness(noTimestamps, now)
        assertEquals(ScoreStatus.INSUFFICIENT_DATA, absent.status)
        assertNull(absent.value0to100)
        assertTrue("readiness_measurement_timestamp_missing" in absent.missingReasons)
    }

    @Test
    fun testSixBaselineDaysAreInsufficient() {
        val result = engine.scoreReadiness(readinessRequest(historyCount = 6), now)
        assertNull(result.value0to100)
        assertEquals(ScoreStatus.INSUFFICIENT_DATA, result.status)
        assertTrue(result.missingReasons.any { it.contains("baseline_requires_7_valid_days") })
    }

    @Test
    fun testMeasurementsOlderThan48HoursAreStale() {
        val old = now.minusHours(50)
        val result = engine.scoreReadiness(readinessRequest(hrvTime = old, rhrTime = old), now)
        assertNull(result.value0to100)
        assertEquals(ScoreStatus.STALE, result.status)
    }

    @Test
    fun testMixedBaselineSourceIsConflict() {
        val (hrvHistory, rhrHistory) = readinessHistory()
        val mixed = hrvHistory.toMutableList().also {
            it[0] = it[0].copy(source = "other-wearable")
        }
        val result = engine.scoreReadiness(
            readinessRequest(hrvHistory = mixed, rhrHistory = rhrHistory),
            now,
        )
        assertNull(result.value0to100)
        assertEquals(ScoreStatus.SOURCE_CONFLICT, result.status)
    }

    @Test
    fun testDuplicateRecordIdWithDifferentCurrentValuesIsConflict() {
        val full = readinessRequest()
        val conflict = full.copy(
            hrvRmssdMs = full.hrvRmssdMs + ReadinessMeasurement(
                45.0, full.hrvRmssdMs.first().measuredAt, "wearable", "h1",
            ),
        )
        val result = engine.scoreReadiness(conflict, now)
        assertNull(result.value0to100)
        assertEquals(ScoreStatus.SOURCE_CONFLICT, result.status)
    }

    @Test
    fun testRespiratoryRateAndCombinedDayScoreAreNotPartOfV03() {
        val result = engine.scoreReadiness(readinessRequest(), now)
        assertEquals(listOf("hrv_rmssd_ms", "resting_heart_rate_bpm"), result.components.map { it.id })
        assertFalse(result.components.any { it.id == "respiratory_rate_per_min" })
    }

    @Test
    fun testReadinessPermissionMissingReturnsNull() {
        val result = engine.scoreReadiness(readinessRequest(), now, permissionGranted = false)
        assertNull(result.value0to100)
        assertEquals(ScoreStatus.PERMISSION_MISSING, result.status)
    }

    @Test
    fun testImmediateTrainingScoreIsZeroAndReaches100AtAssumedRestTime() {
        val session = ExerciseSession(now.minusMinutes(60), now, "watch", "w1")
        val immediate = engine.scoreTrainingRecovery(TrainingRequest(listOf(session)), now)
        assertEquals(0, immediate.value0to100)
        assertEquals(ScoreStatus.AVAILABLE, immediate.status)

        val after = now.plusHours(27)
        val full = engine.scoreTrainingRecovery(TrainingRequest(listOf(session)), after)
        assertEquals(100, full.value0to100)
        assertEquals(27.0, full.components[0].details["assumedRestHours"] as Double, 0.0)
    }

    @Test
    fun testNoTrainingSessionWithinSevenDaysIsNot100() {
        val oldEnd = now.minusDays(8)
        val session = ExerciseSession(oldEnd.minusMinutes(30), oldEnd, "watch")
        val result = engine.scoreTrainingRecovery(TrainingRequest(listOf(session)), now)
        assertNull(result.value0to100)
        assertEquals(ScoreStatus.INSUFFICIENT_DATA, result.status)
        assertTrue("no_current_training_recorded_in_last_7_days" in result.missingReasons)
    }

    @Test
    fun testTrainingResultIsExplicitlyACoarseHeuristic() {
        val end = now.minusHours(4)
        val result = engine.scoreTrainingRecovery(
            TrainingRequest(listOf(ExerciseSession(end.minusMinutes(40), end, "watch"))),
            now,
        )
        val details = result.components[0].details
        assertEquals(true, details["heuristicOnly"])
        assertEquals(true, details["notBiologicalRecovery"])
        assertEquals("Zeit seit Training", details["displayLabel"])
        assertEquals("Erholungsschätzung", details["scoreLabel"])
        assertContract(result, "training_recovery_estimate", 1)
    }

    @Test
    fun testMedianAggregationFlagsSpreadConflict() {
        val result = HealthCoachScoreAggregation.aggregateSamples(
            samples = listOf(60.0, 72.0, null),
            disagreementThreshold = 8.0,
            minimum = 20.0,
            maximum = 220.0,
        )
        assertEquals(66.0, result.value!!, 0.0)
        assertTrue(result.conflict)
        assertEquals(2, result.acceptedCount)
        assertEquals(1, result.rejectedCount)
    }

    @Test
    fun testSyntheticJsonExampleVector() {
        val syntheticSleep = session(
            start = OffsetDateTime.parse("2025-03-14T22:00:00Z"),
            end = OffsetDateTime.parse("2025-03-15T06:00:00Z"),
            stages = listOf(
                SleepStageMinutes("LIGHT", 210.0),
                SleepStageMinutes("DEEP", 90.0),
                SleepStageMinutes("REM", 60.0),
            ),
            source = "synthetic-wearable",
            recordId = "synthetic-sleep-1",
        )
        val syntheticPrior = (6 downTo 1).map { offset ->
            PriorSleepNight(now.toLocalDate().minusDays(offset.toLong()), 120.0, "synthetic-wearable")
        }
        val sleep = engine.scoreSleep(sleepRequest(sessions = listOf(syntheticSleep), prior = syntheticPrior), now)
        val (hrvHistory, rhrHistory) = readinessHistory(hrv = 40.0, rhr = 60.0, source = "synthetic-wearable")
        val readiness = engine.scoreReadiness(
            readinessRequest(
                hrv = 48.0,
                rhr = 72.0,
                source = "synthetic-wearable",
                hrvHistory = hrvHistory,
                rhrHistory = rhrHistory,
            ),
            now,
        )
        val training = engine.scoreTrainingRecovery(TrainingRequest(emptyList()), now)
        assertEquals(listOf("sleep", "readiness", "training_recovery_estimate"), listOf(sleep.scoreId, readiness.scoreId, training.scoreId))
        assertEquals(HealthCoachScoreEngineImpl.ALGORITHM_VERSION, sleep.algorithmVersion)
        assertNull(training.value0to100)
        assertEquals(ScoreStatus.INSUFFICIENT_DATA, training.status)
    }

    @Test
    fun testFivePriorNightsAreEnoughForSleepRegularity() {
        val result = engine.scoreSleep(sleepRequest(prior = priorNights(count = 5)), now)
        assertEquals(ScoreStatus.AVAILABLE, result.status)
        assertEquals(2, result.components.size)
    }

    @Test
    fun testSleepEndingExactly24HoursAgoIsIncluded() {
        val end = now.minusHours(24)
        val result = engine.scoreSleep(
            sleepRequest(sessions = listOf(session(start = end.minusHours(8), end = end))),
            now,
        )
        assertEquals(100, result.value0to100)
        assertFalse(result.status == ScoreStatus.STALE)
    }

    @Test
    fun testExactThreeHourSleepSessionQualifiesAndRoundsHalfUp() {
        val end = OffsetDateTime.parse("2025-03-15T06:00:00Z")
        val result = engine.scoreSleep(
            sleepRequest(
                sessions = listOf(session(
                    start = end.minusHours(3),
                    end = end,
                    stages = listOf(SleepStageMinutes("SLEEPING", 180.0)),
                )),
            ),
            now,
        )
        assertEquals(38, result.value0to100)
    }

    @Test
    fun testDuplicateSleepRecordIdIsNotCountedTwice() {
        val one = session()
        val result = engine.scoreSleep(sleepRequest(sessions = listOf(one, one)), now)
        assertEquals(100, result.value0to100)
        assertEquals(ScoreStatus.PROVISIONAL, result.status)
    }

    @Test
    fun testDuplicateSleepRecordIdWithDifferentStagesIsConflict() {
        val first = session(stages = listOf(SleepStageMinutes("SLEEPING", 480.0)))
        val second = session(stages = listOf(SleepStageMinutes("SLEEPING", 360.0)))
        val result = engine.scoreSleep(sleepRequest(sessions = listOf(first, second)), now)
        assertNull(result.value0to100)
        assertEquals(ScoreStatus.SOURCE_CONFLICT, result.status)
    }

    @Test
    fun testFreshReadinessSignalRemainsProvisionalWhenOtherIsStale() {
        val later = OffsetDateTime.parse("2025-03-16T01:00:00Z")
        val current = readinessRequest(
            hrvTime = OffsetDateTime.parse("2025-03-14T08:00:00Z"),
            rhrTime = OffsetDateTime.parse("2025-03-14T00:00:00Z"),
        )
        val result = engine.scoreReadiness(current, later)
        assertEquals(ScoreStatus.PROVISIONAL, result.status)
        assertEquals(50, result.value0to100)
        assertTrue(result.missingReasons.any { it.contains("stale_current_resting_heart_rate_bpm") })
    }

    @Test
    fun testReadinessMeasurementAtExactly48HoursIsNotStale() {
        val time = OffsetDateTime.parse("2025-03-14T12:00:00Z")
        val later = OffsetDateTime.parse("2025-03-16T12:00:00Z")
        val result = engine.scoreReadiness(readinessRequest(hrvTime = time, rhrTime = time), later)
        assertEquals(ScoreStatus.AVAILABLE, result.status)
        assertEquals(50, result.value0to100)
    }

    @Test
    fun testTrainingRestTimeIsCappedAt36Hours() {
        val end = now.minusHours(36)
        val session = ExerciseSession(end.minusMinutes(120), end, "watch")
        val result = engine.scoreTrainingRecovery(TrainingRequest(listOf(session)), now)
        assertEquals(100, result.value0to100)
        assertEquals(36.0, result.components[0].details["assumedRestHours"] as Double, 0.0)
    }

    @Test
    fun testTrainingSessionEndingExactlySevenDaysAgoIsIncluded() {
        val end = now.minusDays(7)
        val session = ExerciseSession(end.minusMinutes(60), end, "watch")
        val result = engine.scoreTrainingRecovery(TrainingRequest(listOf(session)), now)
        assertEquals(ScoreStatus.AVAILABLE, result.status)
        assertEquals(100, result.value0to100)
    }
}
