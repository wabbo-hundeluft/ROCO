package com.wabbo.healthconnectcoach.score

import com.example.healthcoach.score.contract.PriorSleepNight
import com.example.healthcoach.score.contract.ReadinessBaselineDay
import com.example.healthcoach.score.contract.ReadinessMeasurement
import com.example.healthcoach.score.contract.ReadinessRequest
import com.example.healthcoach.score.contract.ScoreStatus
import com.example.healthcoach.score.contract.ExerciseSession
import com.example.healthcoach.score.contract.SleepRequest
import com.example.healthcoach.score.contract.SleepSession
import com.example.healthcoach.score.contract.SleepStageMinutes
import com.example.healthcoach.score.contract.TrainingRequest
import java.time.LocalDate
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthCoachScoreEngineSourceAndOverflowTest {
    private val engine = HealthCoachScoreEngineImpl()
    private val now = OffsetDateTime.parse("2025-03-15T12:00:00Z")

    @Test
    fun anyStageTotalAboveSessionDurationIsSourceConflict() {
        val end = OffsetDateTime.parse("2025-03-15T06:00:00Z")
        val request = SleepRequest(
            sessions = listOf(
                SleepSession(
                    start = end.minusHours(8),
                    end = end,
                    source = "sleep-device",
                    recordId = "stage-overflow",
                    stages = listOf(SleepStageMinutes("SLEEPING", 480.000001)),
                ),
            ),
        )

        val result = engine.scoreSleep(request, now)

        assertNull(result.value0to100)
        assertEquals(ScoreStatus.SOURCE_CONFLICT, result.status)
        assertTrue("sleep_stage_total_exceeds_session_duration" in result.missingReasons)
    }

    @Test
    fun primarySleepSourceIsFilteredBeforeTheFourteenNightLookbackCap() {
        val wakeDate = now.toLocalDate()
        val otherSourceNights = (1L..15L).map { daysAgo ->
            PriorSleepNight(wakeDate.minusDays(daysAgo), 600.0, "other-device")
        }
        val primarySourceNights = (16L..20L).map { daysAgo ->
            PriorSleepNight(wakeDate.minusDays(daysAgo), 120.0, "sleep-device")
        }
        val sessionEnd = OffsetDateTime.parse("2025-03-15T06:00:00Z")
        val request = SleepRequest(
            sessions = listOf(
                SleepSession(
                    start = OffsetDateTime.parse("2025-03-14T22:00:00Z"),
                    end = sessionEnd,
                    source = "sleep-device",
                    recordId = "sleep-main",
                    stages = listOf(SleepStageMinutes("SLEEPING", 480.0)),
                ),
            ),
            priorNights = otherSourceNights + primarySourceNights,
            primarySource = "sleep-device",
        )

        val result = engine.scoreSleep(request, now)

        assertEquals(ScoreStatus.AVAILABLE, result.status)
        assertEquals(2, result.components.size)
        assertEquals(5, result.components[1].details["baselineNights"])
    }

    @Test
    fun unresolvedDifferentSourcesCannotBeCombinedIntoReadinessScore() {
        val currentDay = LocalDate.parse("2025-03-14")
        val hrvSource = "wearable-oura"
        val rhrSource = "watch-apple"
        val hrvBaseline = (1L..7L).map { daysAgo ->
            ReadinessBaselineDay(currentDay.minusDays(daysAgo), 40.0, hrvSource)
        }
        val rhrBaseline = (1L..7L).map { daysAgo ->
            ReadinessBaselineDay(currentDay.minusDays(daysAgo), 60.0, rhrSource)
        }
        val request = ReadinessRequest(
            hrvRmssdMs = listOf(
                ReadinessMeasurement(
                    40.0,
                    OffsetDateTime.parse("2025-03-14T08:00:00Z"),
                    hrvSource,
                    "hrv-1",
                ),
            ),
            restingHeartRateBpm = listOf(
                ReadinessMeasurement(
                    60.0,
                    OffsetDateTime.parse("2025-03-14T08:00:00Z"),
                    rhrSource,
                    "rhr-1",
                ),
            ),
            hrvBaseline = hrvBaseline,
            restingHeartRateBaseline = rhrBaseline,
        )

        val result = engine.scoreReadiness(request, now)

        assertNull(result.value0to100)
        assertEquals(ScoreStatus.SOURCE_CONFLICT, result.status)
        assertTrue("readiness_metrics_use_different_sources_without_primary_source" in result.missingReasons)
    }

    @Test
    fun readinessSourceConflictWinsOverPartialScoreWhenHrvBaselineHasOnlySixDays() {
        val currentDay = LocalDate.parse("2025-03-14")
        val hrvSource = "wearable-oura"
        val rhrSource = "watch-apple"
        val hrvBaseline = (1L..6L).map { daysAgo ->
            ReadinessBaselineDay(currentDay.minusDays(daysAgo), 40.0, hrvSource)
        }
        val rhrBaseline = (1L..7L).map { daysAgo ->
            ReadinessBaselineDay(
                currentDay.minusDays(daysAgo),
                60.0,
                if (daysAgo == 3L) "foreign-rhr-source" else rhrSource,
            )
        }
        val request = ReadinessRequest(
            hrvRmssdMs = listOf(
                ReadinessMeasurement(40.0, OffsetDateTime.parse("2025-03-14T08:00:00Z"), hrvSource, "hrv-current"),
            ),
            restingHeartRateBpm = listOf(
                ReadinessMeasurement(60.0, OffsetDateTime.parse("2025-03-14T08:00:00Z"), rhrSource, "rhr-current"),
            ),
            hrvBaseline = hrvBaseline,
            restingHeartRateBaseline = rhrBaseline,
        )

        val result = engine.scoreReadiness(request, now)

        assertNull(result.value0to100)
        assertEquals(ScoreStatus.SOURCE_CONFLICT, result.status)
        assertTrue(result.missingReasons.single().contains("resting_heart_rate_bpm_baseline_source_differs_or_is_mixed"))
    }

    @Test
    fun trainingSourceConflictDoesNotSelectOneOriginSilently() {
        val firstEnd = now.minusHours(2)
        val secondEnd = now.minusHours(5)
        val request = TrainingRequest(
            sessions = listOf(
                ExerciseSession(firstEnd.minusMinutes(30), firstEnd, "watch-one", "workout-one"),
                ExerciseSession(secondEnd.minusMinutes(45), secondEnd, "watch-two", "workout-two"),
            ),
        )

        val result = engine.scoreTrainingRecovery(request, now)

        assertNull(result.value0to100)
        assertEquals(ScoreStatus.SOURCE_CONFLICT, result.status)
        assertTrue("training_source_is_mixed_or_unresolved" in result.missingReasons)
    }
}
