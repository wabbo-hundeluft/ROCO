/*
 * Standalone pure-Kotlin contract for the Health Coach v0.3 score engine.
 * This is an interface and data contract only: it contains no scoring logic,
 * Android/Health Connect imports, persistence, networking, or UI implementation.
 *
 * Android note: java.time is available natively from API 26; use core-library
 * desugaring when the app supports earlier Android versions.
 */
package com.example.healthcoach.score.contract

import java.time.LocalDate
import java.time.OffsetDateTime

enum class ScoreStatus {
    AVAILABLE,
    PROVISIONAL,
    INSUFFICIENT_DATA,
    PERMISSION_MISSING,
    STALE,
    SOURCE_CONFLICT,
}

data class ScoreCoverage(
    val availableComponents: Int,
    val expectedComponents: Int,
    val ratio: Double,
)

data class ScoreWindow(
    val start: OffsetDateTime? = null,
    val end: OffsetDateTime? = null,
    val meaning: String,
    val wakeDate: LocalDate? = null,
    val currentDay: LocalDate? = null,
    val endExclusive: Boolean? = null,
)

data class ScoreComponent(
    val id: String,
    val value0to100: Int,
    val weight: Double? = null,
    val estimated: Boolean = false,
    /** JSON-compatible scalar values only; training includes both heuristic flags. */
    val details: Map<String, Any?> = emptyMap(),
)

data class ScoreResult(
    val scoreId: String,
    /** Null for insufficient_data, permission_missing, stale, and source_conflict. */
    val value0to100: Int?,
    val status: ScoreStatus,
    val asOf: OffsetDateTime,
    val window: ScoreWindow,
    val components: List<ScoreComponent>,
    val dataSources: List<String>,
    val coverage: ScoreCoverage,
    val missingReasons: List<String>,
    val algorithmVersion: String = "hc-score-v0.3",
)

data class SleepStageMinutes(
    val stage: String,
    /** Adapter supplies normalized, non-overlapping minutes. */
    val minutes: Double,
)

data class SleepSession(
    val start: OffsetDateTime,
    val end: OffsetDateTime,
    val source: String,
    val recordId: String? = null,
    /** Null or empty means stages are absent; session duration is an estimated upper bound. */
    val stages: List<SleepStageMinutes>? = null,
    val startTimezoneInferred: Boolean = false,
    val endTimezoneInferred: Boolean = false,
    val midpointMinutes: Double? = null,
    val midpointTimezoneInferred: Boolean = false,
    val normalizationConflictReason: String? = null,
)

data class PriorSleepNight(
    val wakeDate: LocalDate,
    /** Local clock minutes in [0, 1440). */
    val midpointMinutes: Double,
    val source: String,
    val timezoneInferred: Boolean = false,
)

data class SleepRequest(
    val sessions: List<SleepSession>,
    val sleepGoalMinutes: Double = 480.0,
    val priorNights: List<PriorSleepNight> = emptyList(),
    /** Null means there is no explicit priority; unresolved source mixes conflict. */
    val primarySource: String? = null,
)

data class ReadinessMeasurement(
    val value: Double,
    /** Required to score current readiness. Null is ignored; never synthesize a time. */
    val measuredAt: OffsetDateTime?,
    val source: String,
    val recordId: String? = null,
    val timezoneInferred: Boolean = false,
)

data class ReadinessBaselineDay(
    val date: LocalDate,
    val value: Double,
    val source: String,
    val timezoneInferred: Boolean = false,
)

data class ReadinessRequest(
    val hrvRmssdMs: List<ReadinessMeasurement>,
    val restingHeartRateBpm: List<ReadinessMeasurement>,
    val hrvBaseline: List<ReadinessBaselineDay>,
    val restingHeartRateBaseline: List<ReadinessBaselineDay>,
    val primarySource: String? = null,
    val normalizationConflictReason: String? = null,
)

data class ExerciseSession(
    val start: OffsetDateTime,
    val end: OffsetDateTime,
    val source: String,
    val recordId: String? = null,
)

data class TrainingRequest(
    val sessions: List<ExerciseSession>,
    val primarySource: String? = null,
)

/**
 * Implement this interface in the Android/Kotlin application; this file itself
 * deliberately does not implement the algorithms. Preserve the Python reference
 * contract: circular sleep-midpoint median; every qualifying overlap and
 * unresolved source mix as SOURCE_CONFLICT; timestamp-only readiness; fresh
 * partial readiness as PROVISIONAL with a concrete reason; and inclusive v0.3
 * boundaries (180 minutes, 24 h, 48 h, 7 days, 5 prior sleep nights, 7 baseline days).
 *
 * Training may be AVAILABLE when its session inputs are fresh, but its component
 * details must include heuristicOnly=true and notBiologicalRecovery=true. UI
 * labels are “Zeit seit Training” or “Erholungsschätzung”, never biological
 * regeneration measurement language.
 */
interface HealthCoachScoreEngine {
    fun scoreSleep(
        request: SleepRequest,
        asOf: OffsetDateTime,
        permissionGranted: Boolean = true,
    ): ScoreResult

    fun scoreReadiness(
        request: ReadinessRequest,
        asOf: OffsetDateTime,
        permissionGranted: Boolean = true,
    ): ScoreResult

    fun scoreTrainingRecovery(
        request: TrainingRequest,
        asOf: OffsetDateTime,
        permissionGranted: Boolean = true,
    ): ScoreResult
}
