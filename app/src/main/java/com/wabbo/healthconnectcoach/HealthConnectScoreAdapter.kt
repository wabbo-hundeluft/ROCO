package com.wabbo.healthconnectcoach

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import com.example.healthcoach.score.contract.ExerciseSession
import com.example.healthcoach.score.contract.HealthCoachScoreEngine
import com.example.healthcoach.score.contract.PriorSleepNight
import com.example.healthcoach.score.contract.ReadinessBaselineDay
import com.example.healthcoach.score.contract.ReadinessMeasurement
import com.example.healthcoach.score.contract.ReadinessRequest
import com.example.healthcoach.score.contract.SleepRequest
import com.example.healthcoach.score.contract.SleepSession
import com.example.healthcoach.score.contract.SleepStageMinutes
import com.example.healthcoach.score.contract.TrainingRequest
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

internal data class NormalizedTimestamp(
    val value: OffsetDateTime,
    val timezoneInferred: Boolean,
    val timezoneInferenceSource: String?,
)

/** Preserves the record instant; only a missing local offset is inferred for local-day grouping. */
internal fun normalizeHealthConnectTimestamp(
    instant: Instant,
    offset: ZoneOffset?,
    systemZone: ZoneId = ZoneId.systemDefault(),
): NormalizedTimestamp {
    val inferred = offset == null
    val resolvedOffset = offset ?: systemZone.rules.getOffset(instant)
    return NormalizedTimestamp(
        value = instant.atOffset(resolvedOffset),
        timezoneInferred = inferred,
        timezoneInferenceSource = if (inferred) "systemDefaultZoneId" else null,
    )
}

/** Converts one foreground Health Connect result to ephemeral score inputs and results. */
internal class HealthConnectScoreAdapter(
    private val engine: HealthCoachScoreEngine,
) {
    fun normalizeSleep(
        records: List<SleepSessionRecord>,
        asOf: OffsetDateTime,
        sleepGoalMinutes: Double = 480.0,
    ): SleepRequest {
        val sessions = records.map { record ->
            val start = normalizeHealthConnectTimestamp(record.startTime, record.startZoneOffset)
            val end = normalizeHealthConnectTimestamp(record.endTime, record.endZoneOffset)
            val midpoint = sleepMidpoint(record)
            val intervalConflict = sleepStageConflict(record)
            val stages = record.stages.map { stage ->
                SleepStageMinutes(
                    stage = sleepStageName(stage.stage),
                    minutes = Duration.between(stage.startTime, stage.endTime).toNanos() / NANOS_PER_MINUTE,
                )
            }.ifEmpty { null }
            SleepSession(
                start = start.value,
                end = end.value,
                source = record.metadata.dataOrigin.packageName,
                recordId = record.metadata.id,
                stages = stages,
                startTimezoneInferred = start.timezoneInferred,
                endTimezoneInferred = end.timezoneInferred,
                midpointMinutes = midpoint.minutes,
                midpointTimezoneInferred = midpoint.timezoneInferred,
                normalizationConflictReason = intervalConflict,
            )
        }
        val currentWakeDate = sessions.asSequence()
            .filter { Duration.between(it.start.toInstant(), it.end.toInstant()) >= Duration.ofMinutes(MIN_MAIN_SLEEP_MINUTES) }
            .filter { !it.end.toInstant().isBefore(asOf.minusHours(24).toInstant()) && !it.end.toInstant().isAfter(asOf.toInstant()) }
            .maxWithOrNull(
                compareBy<SleepSession> { Duration.between(it.start.toInstant(), it.end.toInstant()) }
                    .thenBy { it.end.toInstant() },
            )
            ?.end
            ?.toLocalDate()
        val priorNights = records.mapNotNull { record ->
            val start = normalizeHealthConnectTimestamp(record.startTime, record.startZoneOffset)
            val end = normalizeHealthConnectTimestamp(record.endTime, record.endZoneOffset)
            val duration = Duration.between(record.startTime, record.endTime)
            if (duration < Duration.ofMinutes(MIN_MAIN_SLEEP_MINUTES) || end.value.toInstant().isAfter(asOf.toInstant())) {
                return@mapNotNull null
            }
            if (currentWakeDate != null && !end.value.toLocalDate().isBefore(currentWakeDate)) return@mapNotNull null
            if (sleepStageConflict(record) != null) return@mapNotNull null
            val midpoint = sleepMidpoint(record)
            PriorSleepNight(
                wakeDate = end.value.toLocalDate(),
                midpointMinutes = midpoint.minutes,
                source = record.metadata.dataOrigin.packageName,
                timezoneInferred = midpoint.timezoneInferred,
            )
        }
        return SleepRequest(
            sessions = sessions,
            sleepGoalMinutes = sleepGoalMinutes,
            priorNights = priorNights,
            primarySource = null,
        )
    }

    fun scoreSleep(
        records: List<SleepSessionRecord>,
        asOf: OffsetDateTime,
        sleepGoalMinutes: Double = 480.0,
    ) = engine.scoreSleep(normalizeSleep(records, asOf, sleepGoalMinutes), asOf)

    fun normalizeReadiness(
        hrvRecords: List<HeartRateVariabilityRmssdRecord>,
        restingHeartRateRecords: List<RestingHeartRateRecord>,
        asOf: OffsetDateTime,
    ): ReadinessRequest {
        val hrv = deduplicateReadings(hrvRecords.map { record ->
            val timestamp = normalizeHealthConnectTimestamp(record.time, record.zoneOffset)
            ReadinessMeasurement(
                value = record.heartRateVariabilityMillis,
                measuredAt = timestamp.value,
                source = record.metadata.dataOrigin.packageName,
                recordId = record.metadata.id,
                timezoneInferred = timestamp.timezoneInferred,
            )
        })
        val rhr = deduplicateReadings(restingHeartRateRecords.map { record ->
            val timestamp = normalizeHealthConnectTimestamp(record.time, record.zoneOffset)
            ReadinessMeasurement(
                value = record.beatsPerMinute.toDouble(),
                measuredAt = timestamp.value,
                source = record.metadata.dataOrigin.packageName,
                recordId = record.metadata.id,
                timezoneInferred = timestamp.timezoneInferred,
            )
        })
        val conflict = when {
            hrv.conflict && rhr.conflict -> "duplicate_record_id_has_conflicting_hrv_and_resting_heart_rate_content"
            hrv.conflict -> "duplicate_record_id_has_conflicting_hrv_content"
            rhr.conflict -> "duplicate_record_id_has_conflicting_resting_heart_rate_content"
            else -> null
        }
        return ReadinessRequest(
            hrvRmssdMs = hrv.values,
            restingHeartRateBpm = rhr.values,
            hrvBaseline = baselineDays(hrv.values, asOf),
            restingHeartRateBaseline = baselineDays(rhr.values, asOf),
            primarySource = null,
            normalizationConflictReason = conflict,
        )
    }

    fun scoreReadiness(
        hrvRecords: List<HeartRateVariabilityRmssdRecord>,
        restingHeartRateRecords: List<RestingHeartRateRecord>,
        asOf: OffsetDateTime,
    ) = engine.scoreReadiness(normalizeReadiness(hrvRecords, restingHeartRateRecords, asOf), asOf)

    fun normalizeTraining(records: List<ExerciseSessionRecord>): TrainingRequest = TrainingRequest(
        sessions = records.map { record ->
            val start = normalizeHealthConnectTimestamp(record.startTime, record.startZoneOffset)
            val end = normalizeHealthConnectTimestamp(record.endTime, record.endZoneOffset)
            ExerciseSession(
                start = start.value,
                end = end.value,
                source = record.metadata.dataOrigin.packageName,
                recordId = record.metadata.id,
            )
        },
        primarySource = null,
    )

    fun scoreTraining(records: List<ExerciseSessionRecord>, asOf: OffsetDateTime) =
        engine.scoreTrainingRecovery(normalizeTraining(records), asOf)

    private fun sleepStageConflict(record: SleepSessionRecord): String? {
        val ordered = record.stages.sortedBy { it.startTime }
        var previousEnd: Instant? = null
        for (stage in ordered) {
            if (!stage.startTime.isBefore(stage.endTime)) return "sleep_stage_interval_not_positive"
            if (stage.startTime.isBefore(record.startTime) || stage.endTime.isAfter(record.endTime)) {
                return "sleep_stage_interval_outside_session"
            }
            if (previousEnd != null && stage.startTime.isBefore(previousEnd)) {
                return "sleep_stage_intervals_overlap"
            }
            previousEnd = stage.endTime
        }
        return null
    }

    private fun sleepMidpoint(record: SleepSessionRecord): SleepMidpoint {
        val duration = Duration.between(record.startTime, record.endTime)
        val midpointInstant = record.startTime.plusNanos(duration.toNanos() / 2)
        val stableExplicitOffset = record.startZoneOffset
            ?.takeIf { it == record.endZoneOffset }
        val midpoint = normalizeHealthConnectTimestamp(midpointInstant, stableExplicitOffset)
        return SleepMidpoint(
            minutes = midpoint.value.hour * 60.0 + midpoint.value.minute + midpoint.value.second / 60.0 + midpoint.value.nano / NANOS_PER_MINUTE,
            timezoneInferred = midpoint.timezoneInferred,
        )
    }

    private fun sleepStageName(stage: Int): String = when (stage) {
        SleepSessionRecord.STAGE_TYPE_SLEEPING -> "SLEEPING"
        SleepSessionRecord.STAGE_TYPE_LIGHT -> "LIGHT"
        SleepSessionRecord.STAGE_TYPE_DEEP -> "DEEP"
        SleepSessionRecord.STAGE_TYPE_REM -> "REM"
        SleepSessionRecord.STAGE_TYPE_AWAKE -> "AWAKE"
        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> "AWAKE_IN_BED"
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> "OUT_OF_BED"
        else -> "UNKNOWN"
    }

    private fun baselineDays(
        readings: List<ReadinessMeasurement>,
        asOf: OffsetDateTime,
    ): List<ReadinessBaselineDay> = readings.asSequence()
        .filter { it.value.isFinite() && it.value > 0.0 }
        .filter { it.measuredAt != null && !it.measuredAt.toInstant().isAfter(asOf.toInstant()) }
        .groupBy { reading -> reading.measuredAt!!.toLocalDate() to reading.source }
        .map { (dateAndSource, dailyReadings) ->
            val sorted = dailyReadings.map { it.value }.sorted()
            val middle = sorted.size / 2
            val median = if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
            ReadinessBaselineDay(
                date = dateAndSource.first,
                value = median,
                source = dateAndSource.second,
                timezoneInferred = dailyReadings.any { it.timezoneInferred },
            )
        }
        .sortedWith(compareBy<ReadinessBaselineDay> { it.date }.thenBy { it.source })

    private fun deduplicateReadings(readings: List<ReadinessMeasurement>): DeduplicatedReadings {
        val byId = linkedMapOf<String, ReadinessMeasurement>()
        val result = mutableListOf<ReadinessMeasurement>()
        var conflict = false
        for (reading in readings) {
            val id = reading.recordId.orEmpty()
            if (id.isEmpty()) {
                result += reading
                continue
            }
            val previous = byId[id]
            if (previous == null) {
                byId[id] = reading
                result += reading
            } else if (readingSignature(previous) != readingSignature(reading)) {
                conflict = true
            }
        }
        return DeduplicatedReadings(result, conflict)
    }

    private fun readingSignature(reading: ReadinessMeasurement) = listOf(
        reading.value,
        reading.measuredAt?.toInstant(),
        reading.measuredAt?.offset,
        reading.source,
    )

    private data class DeduplicatedReadings(
        val values: List<ReadinessMeasurement>,
        val conflict: Boolean,
    )

    private data class SleepMidpoint(
        val minutes: Double,
        val timezoneInferred: Boolean,
    )

    private companion object {
        const val MIN_MAIN_SLEEP_MINUTES = 180L
        const val NANOS_PER_MINUTE = 60_000_000_000.0
    }
}
