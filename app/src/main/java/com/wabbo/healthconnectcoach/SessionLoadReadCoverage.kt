package com.wabbo.healthconnectcoach

import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine
import java.time.Duration
import java.time.Instant

/** Per-record-type read coverage for the four seven-day session-load windows. */
internal object SessionLoadReadCoverage {
    private val week = Duration.ofDays(7)

    /**
     * A completed empty read is still complete coverage; a null result means that
     * this record type was not successfully read. Each window is checked against
     * the actual returned interval rather than another type's requested interval.
     */
    fun fromReads(
        asOf: Instant,
        exercise: HealthConnectReadResult<*>?,
        heartRate: HealthConnectReadResult<*>?,
        restingHeartRate: HealthConnectReadResult<*>?,
    ): Map<ApprovedWellnessScoreEngine.RecordType, Map<String, Boolean>> = mapOf(
        ApprovedWellnessScoreEngine.RecordType.EXERCISE_SESSION to windowCoverage(asOf, exercise, baselineDays = 0),
        ApprovedWellnessScoreEngine.RecordType.HEART_RATE to windowCoverage(asOf, heartRate, baselineDays = 0),
        ApprovedWellnessScoreEngine.RecordType.RESTING_HEART_RATE to windowCoverage(asOf, restingHeartRate, baselineDays = 1),
    )

    private fun windowCoverage(
        asOf: Instant,
        read: HealthConnectReadResult<*>?,
        baselineDays: Int,
    ): Map<String, Boolean> = (0..3).associate { index ->
        val windowEnd = asOf.minus(week.multipliedBy(index.toLong()))
        val windowStart = asOf.minus(week.multipliedBy(index + 1L + baselineDays))
        "W$index" to (read != null &&
            !read.window.start.isAfter(windowStart) &&
            !read.window.end.isBefore(windowEnd))
    }
}
