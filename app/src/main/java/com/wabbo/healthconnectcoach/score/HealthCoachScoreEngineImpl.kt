package com.wabbo.healthconnectcoach.score

import com.example.healthcoach.score.contract.ExerciseSession
import com.example.healthcoach.score.contract.HealthCoachScoreEngine
import com.example.healthcoach.score.contract.PriorSleepNight
import com.example.healthcoach.score.contract.ReadinessBaselineDay
import com.example.healthcoach.score.contract.ReadinessMeasurement
import com.example.healthcoach.score.contract.ReadinessRequest
import com.example.healthcoach.score.contract.ScoreComponent
import com.example.healthcoach.score.contract.ScoreCoverage
import com.example.healthcoach.score.contract.ScoreResult
import com.example.healthcoach.score.contract.ScoreStatus
import com.example.healthcoach.score.contract.ScoreWindow
import com.example.healthcoach.score.contract.SleepRequest
import com.example.healthcoach.score.contract.SleepSession
import com.example.healthcoach.score.contract.SleepStageMinutes
import com.example.healthcoach.score.contract.TrainingRequest
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.floor
import kotlin.math.min

/** Deterministic, local v0.3 score calculations. No Android, storage, or network APIs. */
class HealthCoachScoreEngineImpl : HealthCoachScoreEngine {
    override fun scoreSleep(
        request: SleepRequest,
        asOf: OffsetDateTime,
        permissionGranted: Boolean,
    ): ScoreResult {
        val cutoff = asOf.minusHours(24)
        val defaultWindow = ScoreWindow(
            start = cutoff,
            end = asOf,
            meaning = "session_end_within_last_24_hours",
        )
        val goal = request.sleepGoalMinutes.takeIf { it.isFinite() && it > 0.0 }
        if (!permissionGranted) {
            return result(
                scoreId = "sleep",
                value = null,
                status = ScoreStatus.PERMISSION_MISSING,
                asOf = asOf,
                window = defaultWindow,
                expectedComponents = 2,
                missingReasons = listOf("health_connect_sleep_permission_missing"),
            )
        }

        val (records, idConflict) = parseSleepSessions(request.sessions)
        val primarySource = request.primarySource.takeIf { !it.isNullOrEmpty() }
        val valid = records.filter { session ->
            !session.end.toInstant().isAfter(asOf.toInstant()) &&
                elapsedMinutes(session.start, session.end) >= SLEEP_MIN_SESSION_MINUTES
        }.filter { session -> primarySource == null || session.source == primarySource }
        val candidates = valid.filter {
            !it.end.toInstant().isBefore(cutoff.toInstant()) &&
                !it.end.toInstant().isAfter(asOf.toInstant())
        }
        val staleCandidates = valid.filter { it.end.toInstant().isBefore(cutoff.toInstant()) }

        if (idConflict) {
            return result(
                "sleep", null, ScoreStatus.SOURCE_CONFLICT, asOf, defaultWindow, 2,
                missingReasons = listOf("duplicate_record_id_has_conflicting_content"),
            )
        }
        val normalizationConflict = candidates.firstNotNullOfOrNull { it.normalizationConflictReason }
        if (normalizationConflict != null) {
            return result(
                scoreId = "sleep",
                value = null,
                status = ScoreStatus.SOURCE_CONFLICT,
                asOf = asOf,
                window = defaultWindow,
                expectedComponents = 2,
                dataSources = candidates.map { it.source },
                missingReasons = listOf(normalizationConflict),
            )
        }
        if (candidates.isEmpty()) {
            val stale = staleCandidates.isNotEmpty()
            return result(
                scoreId = "sleep",
                value = null,
                status = if (stale) ScoreStatus.STALE else ScoreStatus.INSUFFICIENT_DATA,
                asOf = asOf,
                window = defaultWindow,
                expectedComponents = 2,
                dataSources = staleCandidates.map { it.source },
                missingReasons = listOf(
                    if (stale) "latest_qualifying_sleep_ended_more_than_24_hours_ago"
                    else "no_qualifying_main_sleep_session",
                ),
            )
        }

        val sources = candidates.map { it.source }.toSet()
        val overlaps = candidates.indices.any { first ->
            (first + 1 until candidates.size).any { second ->
                overlaps(candidates[first].start, candidates[first].end, candidates[second].start, candidates[second].end)
            }
        }
        if (overlaps || (primarySource == null && sources.size > 1)) {
            return result(
                scoreId = "sleep",
                value = null,
                status = ScoreStatus.SOURCE_CONFLICT,
                asOf = asOf,
                window = defaultWindow,
                expectedComponents = 2,
                dataSources = sources.sorted(),
                missingReasons = listOf("overlapping_or_competing_sleep_sessions"),
            )
        }

        val selected = candidates.maxWithOrNull(
            compareBy<ParsedSleepSession> { elapsedMinutes(it.start, it.end) }
                .thenBy { it.end.toInstant() }
                .thenBy { it.recordId.orEmpty() },
        ) ?: error("A non-empty sleep candidate list must have a selected session.")
        val sessionMinutes = elapsedMinutes(selected.start, selected.end)
        val estimated = selected.stages.isNullOrEmpty()
        val missingReasons = mutableListOf<String>()
        val sleepMinutes: Double
        if (estimated) {
            sleepMinutes = sessionMinutes
            missingReasons += "sleep_stages_missing_session_duration_used_as_estimated_upper_bound"
        } else {
            var total = 0.0
            for (stageEntry in selected.stages.orEmpty()) {
                val stage = stageEntry.stage.uppercase()
                val minutes = stageEntry.minutes
                if (minutes.isFinite() && minutes > 0.0 && stage in SLEEPING_STAGES) {
                    total += minutes
                } else if (minutes.isFinite() && minutes > 0.0 && stage !in NON_SLEEP_STAGES) {
                    missingReasons += "unrecognized_sleep_stage_ignored"
                }
            }
            if (total <= 0.0) {
                return result(
                    scoreId = "sleep",
                    value = null,
                    status = ScoreStatus.INSUFFICIENT_DATA,
                    asOf = asOf,
                    window = defaultWindow,
                    expectedComponents = 2,
                    dataSources = listOf(selected.source),
                    missingReasons = missingReasons + "no_valid_sleep_stage_duration",
                )
            }
            if (total > sessionMinutes) {
                return result(
                    scoreId = "sleep",
                    value = null,
                    status = ScoreStatus.SOURCE_CONFLICT,
                    asOf = asOf,
                    window = defaultWindow,
                    expectedComponents = 2,
                    dataSources = listOf(selected.source),
                    missingReasons = listOf("sleep_stage_total_exceeds_session_duration"),
                )
            }
            sleepMinutes = total
        }

        if (goal == null) {
            return result(
                scoreId = "sleep",
                value = null,
                status = ScoreStatus.INSUFFICIENT_DATA,
                asOf = asOf,
                window = defaultWindow,
                expectedComponents = 2,
                dataSources = listOf(selected.source),
                missingReasons = missingReasons + "valid_editable_sleep_goal_required",
            )
        }

        val wakeDate = selected.end.toLocalDate()
        val midpointInstant = selected.start.toInstant().plusNanos(
            Duration.between(selected.start.toInstant(), selected.end.toInstant()).toNanos() / 2,
        )
        val localMidpoint = midpointInstant.atOffset(selected.end.offset)
        val computedMidpointMinutes = localMidpoint.hour * 60.0 + localMidpoint.minute + localMidpoint.second / 60.0 + localMidpoint.nano / 60_000_000_000.0
        val currentMidpoint = selected.midpointMinutes ?: computedMidpointMinutes
        val midpointLocalTime = selected.midpointMinutes?.let {
            LocalTime.ofNanoOfDay((it * 60_000_000_000.0).toLong())
        } ?: localMidpoint.toLocalTime()
        val durationScore = clamp(100.0 * min(sleepMinutes / goal, 1.0))
        val components = mutableListOf(
            component(
                id = "duration",
                value = durationScore,
                weight = 0.70,
                estimated = estimated,
                details = mapOf(
                    "sleepMinutes" to sleepMinutes,
                    "goalMinutes" to goal,
                    "goalIsEditable" to true,
                    "wakeDate" to wakeDate.toString(),
                    "midpointMinutes" to currentMidpoint,
                    "midpointLocalTime" to midpointLocalTime.toString(),
                    "timezoneInferred" to (selected.endTimezoneInferred || selected.midpointTimezoneInferred),
                ) + if (selected.endTimezoneInferred || selected.midpointTimezoneInferred) mapOf("timezoneInferenceSource" to "systemDefaultZoneId") else emptyMap(),
            ),
        )
        val selectedPrior = request.priorNights.asSequence()
            .filter { it.wakeDate < wakeDate && it.midpointMinutes.isFinite() && it.midpointMinutes >= 0.0 && it.midpointMinutes < 1440.0 }
            .map { PriorSleepNight(it.wakeDate, it.midpointMinutes, normalizeSource(it.source), it.timezoneInferred) }
            .filter { primarySource == null || it.source == primarySource }
            .sortedByDescending { it.wakeDate }
            .take(SLEEP_REGULARITY_LOOKBACK_NIGHTS)
            .toList()
        val priorSources = selectedPrior.map { it.source }.toSet()
        if (primarySource == null && priorSources.isNotEmpty() && priorSources != setOf(selected.source)) {
            return result(
                scoreId = "sleep",
                value = null,
                status = ScoreStatus.SOURCE_CONFLICT,
                asOf = asOf,
                window = defaultWindow,
                expectedComponents = 2,
                dataSources = uniqueSources(listOf(selected.source) + priorSources.sorted()),
                missingReasons = listOf("sleep_regularity_baseline_source_differs_or_is_mixed"),
            )
        }

        val perDate = linkedMapOf<LocalDate, Double>()
        for (night in selectedPrior) {
            val previous = perDate[night.wakeDate]
            if (previous != null && kotlin.math.abs(previous - night.midpointMinutes) > MIDPOINT_CONFLICT_TOLERANCE_MINUTES) {
                return result(
                    scoreId = "sleep",
                    value = null,
                    status = ScoreStatus.SOURCE_CONFLICT,
                    asOf = asOf,
                    window = defaultWindow,
                    expectedComponents = 2,
                    dataSources = listOf(selected.source),
                    missingReasons = listOf("conflicting_sleep_midpoints_for_same_wake_date"),
                )
            }
            perDate[night.wakeDate] = night.midpointMinutes
        }

        val baselineMidpoints = perDate.values.toList()
        val status: ScoreStatus
        val score: Double
        if (baselineMidpoints.size >= MIN_SLEEP_REGULARITY_NIGHTS) {
            val baselineMedian = circularMedian(baselineMidpoints)
            val deviation = circularDistance(currentMidpoint, baselineMedian)
            val regularityScore = 100.0 - 100.0 * min(deviation, REGULARITY_DEVIATION_CAP_MINUTES) / REGULARITY_DEVIATION_CAP_MINUTES
            val timezoneInferred = selected.endTimezoneInferred || selected.midpointTimezoneInferred || selectedPrior.any { it.timezoneInferred }
            components += component(
                id = "regularity",
                value = regularityScore,
                weight = 0.30,
                details = mapOf(
                    "currentMidpointMinutes" to currentMidpoint,
                    "baselineMedianMidpointMinutes" to baselineMedian,
                    "deviationMinutes" to deviation,
                    "baselineNights" to baselineMidpoints.size,
                    "timezoneInferred" to timezoneInferred,
                ) + if (timezoneInferred) mapOf("timezoneInferenceSource" to "systemDefaultZoneId") else emptyMap(),
            )
            score = 0.70 * durationScore + 0.30 * regularityScore
            status = if (estimated) ScoreStatus.PROVISIONAL else ScoreStatus.AVAILABLE
        } else {
            score = durationScore
            status = ScoreStatus.PROVISIONAL
            missingReasons += "sleep_regularity_requires_at_least_5_valid_prior_nights"
        }

        return result(
            scoreId = "sleep",
            value = score,
            status = status,
            asOf = asOf,
            window = ScoreWindow(
                start = cutoff,
                end = asOf,
                meaning = "main_sleep_ending_within_24_hours",
                wakeDate = wakeDate,
            ),
            components = components,
            dataSources = listOf(selected.source),
            expectedComponents = 2,
            missingReasons = missingReasons,
        )
    }

    override fun scoreReadiness(
        request: ReadinessRequest,
        asOf: OffsetDateTime,
        permissionGranted: Boolean,
    ): ScoreResult {
        val emptyWindow = ScoreWindow(meaning = "latest_complete_local_day_and_previous_28_days")
        if (!permissionGranted) {
            return result(
                scoreId = "readiness",
                value = null,
                status = ScoreStatus.PERMISSION_MISSING,
                asOf = asOf,
                window = emptyWindow,
                expectedComponents = 2,
                missingReasons = listOf("health_connect_hrv_or_resting_heart_rate_permission_missing"),
            )
        }
        if (request.normalizationConflictReason != null) {
            return result(
                scoreId = "readiness",
                value = null,
                status = ScoreStatus.SOURCE_CONFLICT,
                asOf = asOf,
                window = emptyWindow,
                expectedComponents = 2,
                dataSources = uniqueSources((request.hrvRmssdMs + request.restingHeartRateBpm).map { it.source }),
                missingReasons = listOf(request.normalizationConflictReason),
            )
        }

        val hrvRows = validReadings(request.hrvRmssdMs)
        val rhrRows = validReadings(request.restingHeartRateBpm)
        val allRows = hrvRows + rhrRows
        val eligibleDays = allRows.asSequence()
            .filter { it.measuredAt != null && !it.measuredAt.toInstant().isAfter(asOf.toInstant()) }
            .mapNotNull { it.measuredAt?.toLocalDate() }
            .filter { it < asOf.toLocalDate() }
            .toList()
        if (eligibleDays.isEmpty()) {
            val staleRows = allRows.filter { it.measuredAt != null && isOlderThan(it.measuredAt, asOf, READINESS_STALE_HOURS) }
            val missingReasons = mutableListOf<String>()
            if (staleRows.isNotEmpty()) missingReasons += "readiness_measurements_older_than_48_hours"
            if (allRows.any { it.measuredAt == null }) missingReasons += "readiness_measurement_timestamp_missing"
            missingReasons += "no_measurements_for_a_complete_local_day"
            return result(
                scoreId = "readiness",
                value = null,
                status = if (staleRows.isNotEmpty()) ScoreStatus.STALE else ScoreStatus.INSUFFICIENT_DATA,
                asOf = asOf,
                window = emptyWindow,
                expectedComponents = 2,
                dataSources = staleRows.map { it.source },
                missingReasons = missingReasons,
            )
        }

        val targetDay = eligibleDays.maxOrNull() ?: error("An eligible date must exist.")
        val startDay = targetDay.minusDays(READINESS_BASELINE_DAYS.toLong())
        val window = ScoreWindow(
            start = startDay.atStartOfDay().atOffset(asOf.offset),
            end = targetDay.atStartOfDay().atOffset(asOf.offset),
            meaning = "prior_28_complete_local_days",
            currentDay = targetDay,
            endExclusive = true,
        )
        val primarySource = request.primarySource.takeIf { !it.isNullOrEmpty() }
        val components = mutableListOf<ScoreComponent>()
        val rawValues = mutableMapOf<String, Double>()
        val dataSources = mutableListOf<String>()
        val missingReasons = mutableListOf<String>()
        var staleMetricFound = false
        var conflictReason: String? = null

        val readinessMetrics = listOf(
            MetricSpec(HRV_ID, "ms", request.hrvRmssdMs, request.hrvBaseline),
            MetricSpec(RHR_ID, "bpm", request.restingHeartRateBpm, request.restingHeartRateBaseline),
        )
        for (metric in readinessMetrics) {
            val metricRows = validReadings(metric.current).filter { row ->
                row.measuredAt != null && row.measuredAt.toLocalDate() == targetDay && !row.measuredAt.toInstant().isAfter(asOf.toInstant())
            }.filter { row -> primarySource == null || row.source == primarySource }

            if (metricRows.isEmpty()) {
                val timestampedRows = validReadings(metric.current).filter { row ->
                    row.measuredAt != null && !row.measuredAt.toInstant().isAfter(asOf.toInstant())
                }
                val staleRows = timestampedRows.filter { isOlderThan(it.measuredAt!!, asOf, READINESS_STALE_HOURS) }
                when {
                    staleRows.isNotEmpty() -> {
                        staleMetricFound = true
                        missingReasons += "stale_current_${metric.id}_older_than_48_hours"
                    }
                    validReadings(metric.current).any { it.measuredAt == null } ->
                        missingReasons += "missing_timestamp_for_current_${metric.id}"
                    else -> missingReasons += "missing_current_${metric.id}_on_latest_complete_day"
                }
                continue
            }

            val sources = metricRows.map { it.source }.toSet()
            if (sources.size > 1) {
                conflictReason = "multiple_current_sources_for_${metric.id}"
                break
            }
            val source = sources.first()
            val seenIds = linkedMapOf<String, ReadingSignature>()
            for (row in metricRows) {
                val recordId = row.recordId.orEmpty()
                if (recordId.isEmpty()) continue
                val signature = ReadingSignature(row.value, row.measuredAt!!.toInstant(), row.measuredAt.offset, row.source)
                val previous = seenIds[recordId]
                if (previous != null && previous != signature) {
                    conflictReason = "duplicate_record_id_has_conflicting_${metric.id}_content"
                    break
                }
                seenIds[recordId] = signature
            }
            if (conflictReason != null) break

            if (metricRows.any { isOlderThan(it.measuredAt!!, asOf, READINESS_STALE_HOURS) }) {
                staleMetricFound = true
                missingReasons += "stale_current_${metric.id}_older_than_48_hours"
                continue
            }

            val seenRecordIds = mutableSetOf<String>()
            val uniqueRows = metricRows.filter { row ->
                val id = row.recordId.orEmpty()
                id.isEmpty() || seenRecordIds.add(id)
            }
            val currentValue = median(uniqueRows.map { it.value })
            val historyRows = validBaseline(metric.baseline).filter { row ->
                row.date >= startDay && row.date < targetDay && (primarySource == null || row.source == primarySource)
            }
            val historySources = historyRows.map { it.source }.toSet()
            if (historySources.isNotEmpty() && historySources != setOf(source)) {
                conflictReason = "${metric.id}_baseline_source_differs_or_is_mixed"
                break
            }
            val dailyValues = historyRows.groupBy { it.date }
                .toSortedMap()
                .values
                .map { dailyRows -> median(dailyRows.map { it.value }) }
            dataSources += source
            val baselineCount = dailyValues.size
            if (baselineCount < MIN_READINESS_BASELINE_DAYS) {
                missingReasons += "baseline_requires_7_valid_days_${metric.id}"
                continue
            }
            val baseline = median(dailyValues)
            val timezoneInferred = metricRows.any { it.timezoneInferred } || historyRows.any { it.timezoneInferred }
            val rawComponentValue = if (metric.id == HRV_ID) {
                clamp(50.0 + 50.0 * (currentValue / baseline - 1.0))
            } else {
                clamp(50.0 - 50.0 * (currentValue / baseline - 1.0))
            }
            val weight = if (metric.id == HRV_ID) 0.60 else 0.40
            rawValues[metric.id] = rawComponentValue
            components += component(
                id = metric.id,
                value = rawComponentValue,
                weight = weight,
                details = mapOf(
                    "currentMedian" to currentValue,
                    "baselineMedian" to baseline,
                    "baselineDays" to baselineCount,
                    "unit" to metric.unit,
                    "timezoneInferred" to timezoneInferred,
                ) + if (timezoneInferred) mapOf("timezoneInferenceSource" to "systemDefaultZoneId") else emptyMap(),
            )
        }

        if (conflictReason != null) {
            return result(
                scoreId = "readiness",
                value = null,
                status = ScoreStatus.SOURCE_CONFLICT,
                asOf = asOf,
                window = window,
                expectedComponents = 2,
                dataSources = dataSources,
                missingReasons = listOf(conflictReason),
            )
        }
        if (primarySource == null && components.size == 2 && dataSources.distinct().size > 1) {
            return result(
                scoreId = "readiness",
                value = null,
                status = ScoreStatus.SOURCE_CONFLICT,
                asOf = asOf,
                window = window,
                expectedComponents = 2,
                dataSources = dataSources,
                missingReasons = listOf("readiness_metrics_use_different_sources_without_primary_source"),
            )
        }
        if (components.isEmpty()) {
            return result(
                scoreId = "readiness",
                value = null,
                status = if (staleMetricFound) ScoreStatus.STALE else ScoreStatus.INSUFFICIENT_DATA,
                asOf = asOf,
                window = window,
                expectedComponents = 2,
                dataSources = dataSources,
                missingReasons = missingReasons.ifEmpty { listOf("no_current_metric_with_7_day_baseline") },
            )
        }
        val score: Double
        var status: ScoreStatus
        if (components.size == 2) {
            score = 0.60 * (rawValues[HRV_ID] ?: 0.0) + 0.40 * (rawValues[RHR_ID] ?: 0.0)
            status = ScoreStatus.AVAILABLE
        } else {
            score = components.single().value0to100.toDouble()
            status = ScoreStatus.PROVISIONAL
        }
        if (staleMetricFound) status = ScoreStatus.PROVISIONAL

        return result(
            scoreId = "readiness",
            value = score,
            status = status,
            asOf = asOf,
            window = window,
            components = components,
            dataSources = dataSources,
            expectedComponents = 2,
            missingReasons = missingReasons,
        )
    }

    override fun scoreTrainingRecovery(
        request: TrainingRequest,
        asOf: OffsetDateTime,
        permissionGranted: Boolean,
    ): ScoreResult {
        val cutoff = asOf.minusDays(TRAINING_LOOKBACK_DAYS.toLong())
        val window = ScoreWindow(
            start = cutoff,
            end = asOf,
            meaning = "workout_ending_within_last_7_days",
        )
        if (!permissionGranted) {
            return result(
                scoreId = "training_recovery_estimate",
                value = null,
                status = ScoreStatus.PERMISSION_MISSING,
                asOf = asOf,
                window = window,
                expectedComponents = 1,
                missingReasons = listOf("health_connect_exercise_permission_missing"),
            )
        }

        val (records, idConflict) = parseExerciseSessions(request.sessions)
        val primarySource = request.primarySource.takeIf { !it.isNullOrEmpty() }
        val valid = records.filter { session ->
            !session.end.toInstant().isAfter(asOf.toInstant()) &&
                !session.end.toInstant().isBefore(cutoff.toInstant()) &&
                session.end.toInstant().isAfter(session.start.toInstant())
        }.filter { session -> primarySource == null || session.source == primarySource }

        if (idConflict) {
            return result(
                scoreId = "training_recovery_estimate",
                value = null,
                status = ScoreStatus.SOURCE_CONFLICT,
                asOf = asOf,
                window = window,
                expectedComponents = 1,
                missingReasons = listOf("duplicate_record_id_has_conflicting_content"),
            )
        }
        if (valid.isEmpty()) {
            return result(
                scoreId = "training_recovery_estimate",
                value = null,
                status = ScoreStatus.INSUFFICIENT_DATA,
                asOf = asOf,
                window = window,
                expectedComponents = 1,
                dataSources = records.map { it.source },
                missingReasons = listOf("no_current_training_recorded_in_last_7_days"),
            )
        }
        val sources = valid.map { it.source }.toSet()
        if (primarySource == null && sources.size > 1) {
            return result(
                scoreId = "training_recovery_estimate",
                value = null,
                status = ScoreStatus.SOURCE_CONFLICT,
                asOf = asOf,
                window = window,
                expectedComponents = 1,
                dataSources = sources.sorted(),
                missingReasons = listOf("training_source_is_mixed_or_unresolved"),
            )
        }

        var selected = valid.first()
        for (session in valid.drop(1)) {
            if (session.end.toInstant().isAfter(selected.end.toInstant())) selected = session
        }
        val durationMinutes = elapsedMinutes(selected.start, selected.end)
        val hoursSince = elapsedHours(selected.end, asOf)
        val assumedRestHours = clamp(12.0 + 0.25 * durationMinutes, 12.0, 36.0)
        val value = 100.0 * min(hoursSince / assumedRestHours, 1.0)
        val trainingComponent = component(
            id = "time_since_last_workout",
            value = value,
            weight = 1.0,
            details = mapOf(
                "displayLabel" to "Zeit seit Training",
                "scoreLabel" to "Erholungsschätzung",
                "sessionDurationMinutes" to durationMinutes,
                "hoursSinceSessionEnd" to hoursSince,
                "assumedRestHours" to assumedRestHours,
                "durationUsesInstants" to true,
                "heuristicOnly" to true,
                "notBiologicalRecovery" to true,
            ),
        )
        return result(
            scoreId = "training_recovery_estimate",
            value = value,
            status = ScoreStatus.AVAILABLE,
            asOf = asOf,
            window = window,
            components = listOf(trainingComponent),
            dataSources = listOf(selected.source),
            expectedComponents = 1,
        )
    }

    private data class ParsedSleepSession(
        val start: OffsetDateTime,
        val end: OffsetDateTime,
        val source: String,
        val recordId: String?,
        val stages: List<SleepStageMinutes>?,
        val startTimezoneInferred: Boolean,
        val endTimezoneInferred: Boolean,
        val midpointMinutes: Double?,
        val midpointTimezoneInferred: Boolean,
        val normalizationConflictReason: String?,
    )

    private data class SleepSignature(
        val start: OffsetDateTime,
        val end: OffsetDateTime,
        val source: String,
        val stages: List<SleepStageMinutes>?,
        val startTimezoneInferred: Boolean,
        val endTimezoneInferred: Boolean,
        val midpointMinutes: Double?,
        val midpointTimezoneInferred: Boolean,
        val normalizationConflictReason: String?,
    )

    private data class ParsedExerciseSession(
        val start: OffsetDateTime,
        val end: OffsetDateTime,
        val source: String,
        val recordId: String?,
    )

    private data class ExerciseSignature(
        val start: Instant,
        val end: Instant,
        val source: String,
    )

    private data class ParsedReading(
        val value: Double,
        val measuredAt: OffsetDateTime?,
        val source: String,
        val recordId: String?,
        val timezoneInferred: Boolean,
    )

    private data class ReadingSignature(
        val value: Double,
        val measuredAt: Instant,
        val offset: java.time.ZoneOffset,
        val source: String,
    )

    private data class MetricSpec(
        val id: String,
        val unit: String,
        val current: List<ReadinessMeasurement>,
        val baseline: List<ReadinessBaselineDay>,
    )

    private fun parseSleepSessions(inputs: List<SleepSession>): Pair<List<ParsedSleepSession>, Boolean> {
        val records = mutableListOf<ParsedSleepSession>()
        val signatures = mutableMapOf<String, SleepSignature>()
        var conflict = false
        for (input in inputs) {
            if (!input.end.toInstant().isAfter(input.start.toInstant())) continue
            val source = normalizeSource(input.source)
            val recordId = input.recordId?.takeIf { it.isNotEmpty() }
            val parsed = ParsedSleepSession(
                input.start,
                input.end,
                source,
                recordId,
                input.stages,
                input.startTimezoneInferred,
                input.endTimezoneInferred,
                input.midpointMinutes,
                input.midpointTimezoneInferred,
                input.normalizationConflictReason,
            )
            if (recordId != null) {
                val signature = SleepSignature(
                    input.start,
                    input.end,
                    source,
                    input.stages,
                    input.startTimezoneInferred,
                    input.endTimezoneInferred,
                    input.midpointMinutes,
                    input.midpointTimezoneInferred,
                    input.normalizationConflictReason,
                )
                val old = signatures[recordId]
                if (old != null) {
                    if (old != signature) conflict = true
                    continue
                }
                signatures[recordId] = signature
            }
            records += parsed
        }
        return records to conflict
    }

    private fun parseExerciseSessions(inputs: List<ExerciseSession>): Pair<List<ParsedExerciseSession>, Boolean> {
        val records = mutableListOf<ParsedExerciseSession>()
        val signatures = mutableMapOf<String, ExerciseSignature>()
        var conflict = false
        for (input in inputs) {
            if (!input.end.toInstant().isAfter(input.start.toInstant())) continue
            val source = normalizeSource(input.source)
            val recordId = input.recordId?.takeIf { it.isNotEmpty() }
            val parsed = ParsedExerciseSession(input.start, input.end, source, recordId)
            if (recordId != null) {
                val signature = ExerciseSignature(input.start.toInstant(), input.end.toInstant(), source)
                val old = signatures[recordId]
                if (old != null) {
                    if (old != signature) conflict = true
                    continue
                }
                signatures[recordId] = signature
            }
            records += parsed
        }
        return records to conflict
    }

    private fun validReadings(inputs: List<ReadinessMeasurement>): List<ParsedReading> = inputs.asSequence()
        .filter { it.value.isFinite() && it.value > 0.0 }
        .map { ParsedReading(it.value, it.measuredAt, normalizeSource(it.source), it.recordId?.takeIf(String::isNotEmpty), it.timezoneInferred) }
        .toList()

    private fun validBaseline(inputs: List<ReadinessBaselineDay>): List<ReadinessBaselineDay> = inputs.filter {
        it.value.isFinite() && it.value > 0.0
    }.map { it.copy(source = normalizeSource(it.source)) }

    private fun result(
        scoreId: String,
        value: Double?,
        status: ScoreStatus,
        asOf: OffsetDateTime,
        window: ScoreWindow,
        expectedComponents: Int,
        components: List<ScoreComponent> = emptyList(),
        dataSources: List<String> = emptyList(),
        missingReasons: List<String> = emptyList(),
    ): ScoreResult {
        val uniqueReasons = missingReasons.distinct()
        return ScoreResult(
            scoreId = scoreId,
            value0to100 = value?.let(::roundScore),
            status = status,
            asOf = asOf,
            window = window,
            components = components,
            dataSources = uniqueSources(dataSources),
            coverage = ScoreCoverage(
                availableComponents = components.size,
                expectedComponents = expectedComponents,
                ratio = if (expectedComponents == 0) 0.0 else components.size.toDouble() / expectedComponents,
            ),
            missingReasons = uniqueReasons,
            algorithmVersion = ALGORITHM_VERSION,
        )
    }

    private fun component(
        id: String,
        value: Double,
        weight: Double? = null,
        estimated: Boolean = false,
        details: Map<String, Any?> = emptyMap(),
    ) = ScoreComponent(
        id = id,
        value0to100 = roundScore(value),
        weight = weight,
        estimated = estimated,
        details = details,
    )

    private fun circularMedian(values: List<Double>): Double {
        val candidates = values.map { moduloDay(it) }.distinct().sorted()
        return candidates.minWithOrNull(
            compareBy<Double> { candidate -> values.sumOf { circularDistance(candidate, it) } }
                .thenBy { it },
        ) ?: 0.0
    }

    private fun circularDistance(first: Double, second: Double): Double {
        val difference = kotlin.math.abs(moduloDay(first) - moduloDay(second))
        return min(difference, 1440.0 - difference)
    }

    private fun moduloDay(value: Double): Double {
        val result = value % 1440.0
        return if (result < 0.0) result + 1440.0 else result
    }

    private fun overlaps(aStart: OffsetDateTime, aEnd: OffsetDateTime, bStart: OffsetDateTime, bEnd: OffsetDateTime): Boolean {
        val laterStart = maxOf(aStart.toInstant(), bStart.toInstant())
        val earlierEnd = minOf(aEnd.toInstant(), bEnd.toInstant())
        return laterStart.isBefore(earlierEnd)
    }

    private fun elapsedMinutes(start: OffsetDateTime, end: OffsetDateTime): Double =
        Duration.between(start.toInstant(), end.toInstant()).toNanos() / NANOS_PER_MINUTE

    private fun elapsedHours(start: OffsetDateTime, end: OffsetDateTime): Double =
        Duration.between(start.toInstant(), end.toInstant()).toNanos() / NANOS_PER_HOUR

    private fun isOlderThan(measuredAt: OffsetDateTime, asOf: OffsetDateTime, hours: Long): Boolean =
        Duration.between(measuredAt.toInstant(), asOf.toInstant()) > Duration.ofHours(hours)

    private fun median(values: List<Double>): Double {
        require(values.isNotEmpty()) { "Median requires at least one value." }
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
    }

    private fun clamp(value: Double, low: Double = 0.0, high: Double = 100.0): Double = value.coerceIn(low, high)

    private fun roundScore(value: Double): Int = floor(clamp(value) + 0.5).toInt()

    private fun normalizeSource(source: String): String = if (source.isEmpty()) "unknown" else source

    private fun uniqueSources(sources: List<String>): List<String> =
        sources.filter { it.isNotEmpty() }.distinct()

    companion object {
        const val ALGORITHM_VERSION = "hc-score-v0.3"
        private const val SLEEP_MIN_SESSION_MINUTES = 180.0
        private const val DEFAULT_SLEEP_GOAL_MINUTES = 480.0
        private const val SLEEP_REGULARITY_LOOKBACK_NIGHTS = 14
        private const val MIN_SLEEP_REGULARITY_NIGHTS = 5
        private const val READINESS_BASELINE_DAYS = 28
        private const val MIN_READINESS_BASELINE_DAYS = 7
        private const val TRAINING_LOOKBACK_DAYS = 7
        private const val READINESS_STALE_HOURS = 48L
        private const val MIDPOINT_CONFLICT_TOLERANCE_MINUTES = 0.01
        private const val REGULARITY_DEVIATION_CAP_MINUTES = 120.0
        private const val NANOS_PER_MINUTE = 60_000_000_000.0
        private const val NANOS_PER_HOUR = 3_600_000_000_000.0
        private const val HRV_ID = "hrv_rmssd_ms"
        private const val RHR_ID = "resting_heart_rate_bpm"
        private val SLEEPING_STAGES = setOf("SLEEPING", "LIGHT", "DEEP", "REM")
        private val NON_SLEEP_STAGES = setOf("AWAKE", "AWAKE_IN_BED", "OUT_OF_BED", "UNKNOWN")
    }
}

/** Adapter-level robust median helper; a conflict must never be silently scored. */
data class AggregatedValue(
    val value: Double?,
    val conflict: Boolean,
    val acceptedCount: Int,
    val rejectedCount: Int,
)

object HealthCoachScoreAggregation {
    fun aggregateSamples(
        samples: List<Double?>,
        disagreementThreshold: Double,
        minimum: Double = Double.NEGATIVE_INFINITY,
        maximum: Double = Double.POSITIVE_INFINITY,
    ): AggregatedValue {
        require(disagreementThreshold.isFinite() && disagreementThreshold >= 0.0) {
            "disagreementThreshold must be finite and non-negative."
        }
        val accepted = mutableListOf<Double>()
        var rejected = 0
        for (sample in samples) {
            if (sample == null || !sample.isFinite() || sample < minimum || sample > maximum) {
                rejected += 1
            } else {
                accepted += sample
            }
        }
        if (accepted.isEmpty()) return AggregatedValue(null, false, 0, rejected)
        val sorted = accepted.sorted()
        val middle = sorted.size / 2
        val median = if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
        return AggregatedValue(
            value = median,
            conflict = sorted.last() - sorted.first() > disagreementThreshold,
            acceptedCount = accepted.size,
            rejectedCount = rejected,
        )
    }
}
