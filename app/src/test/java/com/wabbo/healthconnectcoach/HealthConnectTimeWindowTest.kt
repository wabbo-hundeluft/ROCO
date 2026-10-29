package com.wabbo.healthconnectcoach

import java.time.Duration
import java.time.Instant
import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HealthConnectTimeWindowTest {
    private val now = Instant.parse("2026-10-24T12:00:00Z")

    @Test
    fun clampsLongHistoryToExactlyThirtyDays() {
        val result = HealthConnectTimeWindow.bound(
            requestedStart = now.minus(Duration.ofDays(365)),
            requestedEnd = now,
            now = now,
        )

        assertEquals(now.minus(Duration.ofDays(30)), result?.start)
        assertEquals(now, result?.end)
        assertEquals(true, result?.isClipped)
    }

    @Test
    fun historyGrantAllowsTheThirtyFiveDaySessionLoadWindow() {
        val result = HealthConnectTimeWindow.bound(
            requestedStart = now.minus(Duration.ofDays(35)),
            requestedEnd = now,
            now = now,
            historyGranted = true,
        )

        assertEquals(now.minus(Duration.ofDays(35)), result?.start)
        assertEquals(now, result?.end)
        assertEquals(false, result?.isClipped)
    }

    @Test
    fun noHistoryWindowIsMarkedIncompleteInsteadOfBecomingAFalseZeroWeek() {
        val result = HealthConnectTimeWindow.bound(
            requestedStart = now.minus(Duration.ofDays(35)),
            requestedEnd = now,
            now = now,
        )

        assertEquals(now.minus(Duration.ofDays(30)), result?.start)
        assertEquals(true, result?.isClipped)
    }

    @Test
    fun clampsFutureEndToNow() {
        val result = HealthConnectTimeWindow.bound(
            requestedStart = now.minus(Duration.ofDays(2)),
            requestedEnd = now.plus(Duration.ofDays(5)),
            now = now,
        )

        assertEquals(now.minus(Duration.ofDays(2)), result?.start)
        assertEquals(now, result?.end)
    }

    @Test
    fun rejectsInvertedOrEmptyRanges() {
        assertNull(HealthConnectTimeWindow.bound(now, now.minusSeconds(1), now))
        assertNull(HealthConnectTimeWindow.bound(now, now, now))
    }

    @Test
    fun successfulEmptyReadsCoverAllFourWindowsPerRequiredRecordType() {
        val coverage = SessionLoadReadCoverage.fromReads(
            asOf = now,
            exercise = read(days = 28),
            heartRate = read(days = 28),
            restingHeartRate = read(days = 35),
        )
        val complete = (0..3).associate { "W$it" to true }

        assertEquals(complete, coverage.getValue(ApprovedWellnessScoreEngine.RecordType.EXERCISE_SESSION))
        assertEquals(complete, coverage.getValue(ApprovedWellnessScoreEngine.RecordType.HEART_RATE))
        assertEquals(complete, coverage.getValue(ApprovedWellnessScoreEngine.RecordType.RESTING_HEART_RATE))
    }

    @Test
    fun missingHeartRateReadIsNotInferredCompleteFromExerciseAndRhrReads() {
        val coverage = SessionLoadReadCoverage.fromReads(
            asOf = now,
            exercise = read(days = 28),
            heartRate = null,
            restingHeartRate = read(days = 35),
        )
        val complete = (0..3).associate { "W$it" to true }
        val incomplete = (0..3).associate { "W$it" to false }

        assertEquals(complete, coverage.getValue(ApprovedWellnessScoreEngine.RecordType.EXERCISE_SESSION))
        assertEquals(incomplete, coverage.getValue(ApprovedWellnessScoreEngine.RecordType.HEART_RATE))
        assertEquals(complete, coverage.getValue(ApprovedWellnessScoreEngine.RecordType.RESTING_HEART_RATE))
    }

    @Test
    fun truncatedExerciseHeartRateAndRhrReadsOnlyInvalidateTheirUncoveredWindows() {
        val coverage = SessionLoadReadCoverage.fromReads(
            asOf = now,
            exercise = read(days = 27, clipped = true),
            heartRate = read(days = 20, clipped = true),
            restingHeartRate = read(days = 30, clipped = true),
        )

        assertEquals(mapOf("W0" to true, "W1" to true, "W2" to true, "W3" to false),
            coverage.getValue(ApprovedWellnessScoreEngine.RecordType.EXERCISE_SESSION))
        assertEquals(mapOf("W0" to true, "W1" to true, "W2" to false, "W3" to false),
            coverage.getValue(ApprovedWellnessScoreEngine.RecordType.HEART_RATE))
        assertEquals(mapOf("W0" to true, "W1" to true, "W2" to true, "W3" to false),
            coverage.getValue(ApprovedWellnessScoreEngine.RecordType.RESTING_HEART_RATE))
    }

    private fun read(days: Long, clipped: Boolean = false) = HealthConnectReadResult(
        records = emptyList<String>(),
        window = HealthConnectQueryWindow(now.minus(Duration.ofDays(days)), now, clipped),
    )
}
