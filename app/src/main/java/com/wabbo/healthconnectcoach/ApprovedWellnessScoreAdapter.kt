package com.wabbo.healthconnectcoach

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** In-memory results and source choices from one successful foreground Health Connect read. */
internal data class ApprovedWellnessScoreReadout(
    val scores: Map<String, ApprovedWellnessScoreEngine.ScoreResult>,
    val observedOrigins: Map<String, Set<String>>,
    /** Ephemeral normalization for this foreground operation; callers must not persist it. */
    val normalizedInput: ApprovedWellnessScoreEngine.NormalizedInput,
    /** Same-day real score calculations; never persisted or reconstructed from snapshots. */
    val batteryDayTrend: List<ScoreTrendPoint>,
)

internal data class ScoreTrendPoint(
    val at: Instant,
    val value: Int,
)

/** Adds only actual successful foreground-read scores to today's in-memory history. */
internal fun appendBatteryDayReadPoint(
    existing: List<ScoreTrendPoint>,
    current: ScoreTrendPoint?,
    dayStart: Instant,
    asOf: Instant,
): List<ScoreTrendPoint> {
    val points = existing.filter { !it.at.isBefore(dayStart) && !it.at.isAfter(asOf) }.toMutableList()
    if (current != null && !current.at.isBefore(dayStart) && !current.at.isAfter(asOf)) {
        points.removeAll { it.at == current.at }
        points += current
    }
    return points.sortedBy { it.at }.takeLast(200)
}

/** Maps only the six PO-approved local Health Connect record types into pure score inputs. */
internal class ApprovedWellnessScoreAdapter(
    private val engine: ApprovedWellnessScoreEngine = ApprovedWellnessScoreEngine(),
) {
    fun score(
        records: WellnessScoreRecords,
        asOf: Instant,
        zone: ZoneId,
        bindingStore: PrimarySourceBindingStore,
        permissions: Map<String, ApprovedWellnessScoreEngine.PermissionState>,
        readComplete: Boolean,
        historyComplete: Boolean,
        windowCompleteness: Map<String, Boolean> = emptyMap(),
        recordTypeWindowCompleteness: Map<ApprovedWellnessScoreEngine.RecordType, Map<String, Boolean>> = emptyMap(),
        v2Model: Boolean = false,
        goals: TrainingGoals? = null,
        trainingHistoryStart: Instant? = null,
        trainingHistoryComplete: Boolean = false,
        personalProfile: com.wabbo.healthconnectcoach.score.PersonalScoreProfile = com.wabbo.healthconnectcoach.score.PersonalScoreProfile(),
    ): ApprovedWellnessScoreReadout {
        // Historical calculations must never consume a later day's records.
        val eligibleRecords = records.through(asOf)
        val sleep = dedupeById(eligibleRecords.sleepSessions, { it.metadata.id }, ::sleepSignature)
        val hrv = dedupeById(eligibleRecords.hrvRmssd, { it.metadata.id }, ::hrvSignature)
        val rhr = dedupeById(eligibleRecords.restingHeartRate, { it.metadata.id }, ::rhrSignature)
        val exercise = dedupeById(eligibleRecords.exerciseSessions, { it.metadata.id }, ::exerciseSignature)
        val heartRate = dedupeById(eligibleRecords.heartRate, { it.metadata.id }, ::heartRateSignature)
        val steps = dedupeById(eligibleRecords.steps, { it.metadata.id }, ::stepsSignature)

        val observed = linkedMapOf(
            ApprovedWellnessRecordTypes.SLEEP_SESSION to origins(eligibleRecords.sleepSessions) { it.metadata.dataOrigin },
            ApprovedWellnessRecordTypes.HRV_RMSSD to origins(eligibleRecords.hrvRmssd) { it.metadata.dataOrigin },
            ApprovedWellnessRecordTypes.RESTING_HEART_RATE to origins(eligibleRecords.restingHeartRate) { it.metadata.dataOrigin },
            ApprovedWellnessRecordTypes.EXERCISE_SESSION to origins(eligibleRecords.exerciseSessions) { it.metadata.dataOrigin },
            ApprovedWellnessRecordTypes.HEART_RATE to origins(eligibleRecords.heartRate) { it.metadata.dataOrigin },
            ApprovedWellnessRecordTypes.STEPS to origins(eligibleRecords.steps) { it.metadata.dataOrigin },
        )
        val originsByType = mapOf(
            ApprovedWellnessRecordTypes.SLEEP_SESSION to eligibleRecords.sleepSessions.map { it.metadata.dataOrigin },
            ApprovedWellnessRecordTypes.HRV_RMSSD to eligibleRecords.hrvRmssd.map { it.metadata.dataOrigin },
            ApprovedWellnessRecordTypes.RESTING_HEART_RATE to eligibleRecords.restingHeartRate.map { it.metadata.dataOrigin },
            ApprovedWellnessRecordTypes.EXERCISE_SESSION to eligibleRecords.exerciseSessions.map { it.metadata.dataOrigin },
            ApprovedWellnessRecordTypes.HEART_RATE to eligibleRecords.heartRate.map { it.metadata.dataOrigin },
            ApprovedWellnessRecordTypes.STEPS to eligibleRecords.steps.map { it.metadata.dataOrigin },
        )
        originsByType.forEach { (recordType, sourceOrigins) ->
            if (permissions[enginePermissionKey(recordType)] == ApprovedWellnessScoreEngine.PermissionState.granted) {
                bindingStore.autoBind(recordType, sourceOrigins)
            }
        }

        val boundSources = mapOf(
            "sleep" to bindingStore.get(ApprovedWellnessRecordTypes.SLEEP_SESSION)?.packageName,
            "hrv" to bindingStore.get(ApprovedWellnessRecordTypes.HRV_RMSSD)?.packageName,
            "rhr" to bindingStore.get(ApprovedWellnessRecordTypes.RESTING_HEART_RATE)?.packageName,
            "exercise" to bindingStore.get(ApprovedWellnessRecordTypes.EXERCISE_SESSION)?.packageName,
            "heart_rate" to bindingStore.get(ApprovedWellnessRecordTypes.HEART_RATE)?.packageName,
            "steps" to bindingStore.get(ApprovedWellnessRecordTypes.STEPS)?.packageName,
        )
        val sleepRecords = sleep.records.map { record ->
            ApprovedWellnessScoreEngine.SleepSession(
                start = record.startTime,
                end = record.endTime,
                source = record.metadata.dataOrigin.packageName,
                id = record.metadata.id,
                localWakeDate = localDate(record.endTime, zone),
                localMidpointMinute = midpointMinute(record.startTime, record.endTime, zone),
                startZoneOffset = record.startZoneOffset,
                endZoneOffset = record.endZoneOffset,
                stages = record.stages.map { stage ->
                    ApprovedWellnessScoreEngine.SleepStage(
                        start = stage.startTime,
                        end = stage.endTime,
                        type = sleepStageName(stage.stage),
                    )
                },
            )
        }
        val mainSleep = sleepRecords.asSequence()
            .filter { boundSources["sleep"] == null || it.source == boundSources["sleep"] }
            .filter { it.end in asOf.minusSeconds(86_400)..asOf }
            .filter { it.end.isAfter(it.start) && java.time.Duration.between(it.start, it.end) >= java.time.Duration.ofMinutes(180) }
            .maxWithOrNull(compareBy<ApprovedWellnessScoreEngine.SleepSession> { java.time.Duration.between(it.start, it.end) }.thenBy { it.end })
        val wakeDate = mainSleep?.localWakeDate
        val priorNights = if (wakeDate == null) {
            emptyList()
        } else {
            sleepRecords.asSequence()
                .filter { boundSources["sleep"] == null || it.source == boundSources["sleep"] }
                .filter { it.end.isBefore(asOf) && it.localWakeDate!!.isBefore(wakeDate) }
                .filter { it.end.isAfter(it.start) && java.time.Duration.between(it.start, it.end) >= java.time.Duration.ofMinutes(180) }
                .groupBy { it.localWakeDate!! }
                .mapNotNull { (date, nights) ->
                    val night = nights.maxByOrNull { java.time.Duration.between(it.start, it.end) } ?: return@mapNotNull null
                    ApprovedWellnessScoreEngine.SleepNight(
                        date = date,
                        midpointMinute = night.localMidpointMinute!!,
                        source = night.source,
                        start = night.start,
                        end = night.end,
                        startZoneOffset = night.startZoneOffset,
                        endZoneOffset = night.endZoneOffset,
                    )
                }
                .sortedBy { it.date }
                .takeLast(14)
                .toList()
        }
        val hrvSamples = hrv.records.mapNotNull { record ->
            if (!record.heartRateVariabilityMillis.isFinite() || record.heartRateVariabilityMillis <= 0.0) return@mapNotNull null
            ApprovedWellnessScoreEngine.MetricSample(
                at = record.time,
                value = record.heartRateVariabilityMillis,
                source = record.metadata.dataOrigin.packageName,
                id = record.metadata.id,
                zoneOffset = record.zoneOffset,
            )
        }
        val rhrSamples = rhr.records.mapNotNull { record ->
            val value = record.beatsPerMinute.toDouble()
            if (!value.isFinite() || value <= 0.0) return@mapNotNull null
            ApprovedWellnessScoreEngine.MetricSample(
                at = record.time,
                value = value,
                source = record.metadata.dataOrigin.packageName,
                id = record.metadata.id,
                zoneOffset = record.zoneOffset,
            )
        }
        val hrvBaseline = baselineDays(hrv.records.map { record ->
            Triple(record.time, record.heartRateVariabilityMillis, record.metadata.dataOrigin.packageName)
        }, zone)
        val rhrBaseline = baselineDays(rhr.records.map { record ->
            Triple(record.time, record.beatsPerMinute.toDouble(), record.metadata.dataOrigin.packageName)
        }, zone)
        val allHeartRateSamples = heartRate.records.flatMap { record ->
            record.samples.mapNotNull { sample ->
                val value = sample.beatsPerMinute.toDouble()
                if (!value.isFinite() || value <= 0.0) return@mapNotNull null
                ApprovedWellnessScoreEngine.MetricSample(
                    at = sample.time,
                    value = value,
                    source = record.metadata.dataOrigin.packageName,
                    id = record.metadata.id,
                    recordStartZoneOffset = record.startZoneOffset,
                    recordEndZoneOffset = record.endZoneOffset,
                )
            }
        }
        val selectedExerciseSource = boundSources["exercise"]
        val workouts = exercise.records
            .filter { selectedExerciseSource == null || it.metadata.dataOrigin.packageName == selectedExerciseSource }
            .map { record ->
            val start = record.startTime
            val end = record.endTime
            ApprovedWellnessScoreEngine.Workout(
                start = start,
                end = end,
                source = record.metadata.dataOrigin.packageName,
                id = record.metadata.id,
                heartRate = allHeartRateSamples.filter { it.at >= start && it.at < end },
                title = record.title?.takeIf { it.isNotBlank() } ?: "Training",
                localStartDate = localDate(start, zone),
                startZoneOffset = record.startZoneOffset,
                endZoneOffset = record.endZoneOffset,
            )
        }
        val stepsList = steps.records.filter { it.count > 0 }.map { record ->
            ApprovedWellnessScoreEngine.Steps(
                start = record.startTime,
                end = record.endTime,
                count = record.count.toDouble(),
                source = record.metadata.dataOrigin.packageName,
                id = record.metadata.id,
                startZoneOffset = record.startZoneOffset,
                endZoneOffset = record.endZoneOffset,
            )
        }
        val input = ApprovedWellnessScoreEngine.NormalizedInput(
            asOf = asOf,
            zone = zone,
            sleeps = sleepRecords,
            priorNights = priorNights,
            hrv = hrvSamples,
            rhr = rhrSamples,
            hrvBaseline = hrvBaseline,
            rhrBaseline = rhrBaseline,
            workouts = workouts,
            steps = stepsList,
            boundSources = boundSources,
            readComplete = readComplete,
            historyComplete = historyComplete,
            recordTypeWindowCompleteness = recordTypeWindowCompleteness,
            permissions = permissions,
            sleepGoalMinutes = goals?.sleepMinutes?.toDouble() ?: 480.0,
            weeklyTargetLoad = goals?.weeklyLoadTarget,
            personalProfile = personalProfile,
            weeklyGoalAdjustments = goals?.weeklyLoadAdjustments.orEmpty(),
            trainingHistoryStart = if(v2Model) trainingHistoryStart else null,
            trainingHistoryComplete = trainingHistoryComplete,
            windowCompleteness = (0..3).associate { index ->
                val key = "W$index"
                key to (windowCompleteness[key] ?: if (index == 0) readComplete else historyComplete)
            },
        )
        val scores = linkedMapOf(
            ApprovedWellnessScoreEngine.SLEEP_ID to engine.scoreSleep(input),
            ApprovedWellnessScoreEngine.BODY_BATTERY_ID to engine.scoreBodyBattery(input),
            ApprovedWellnessScoreEngine.SESSION_LOAD_ID to if (v2Model) engine.scoreWeeklyLoad(input, input.weeklyTargetLoad) else engine.scoreSessionLoad(input),
        )
        if (sleep.conflict) {
            scores[ApprovedWellnessScoreEngine.SLEEP_ID] = conflict(ApprovedWellnessScoreEngine.SLEEP_ID, asOf, 4, "duplicate_sleep_record_id_conflict")
            scores[ApprovedWellnessScoreEngine.BODY_BATTERY_ID] = conflict(ApprovedWellnessScoreEngine.BODY_BATTERY_ID, asOf, 3, "duplicate_sleep_record_id_conflict")
        }
        if (hrv.conflict) {
            scores[ApprovedWellnessScoreEngine.SLEEP_ID] = conflict(ApprovedWellnessScoreEngine.SLEEP_ID, asOf, 4, "duplicate_hrv_record_id_conflict")
            scores[ApprovedWellnessScoreEngine.BODY_BATTERY_ID] = conflict(ApprovedWellnessScoreEngine.BODY_BATTERY_ID, asOf, 3, "duplicate_hrv_record_id_conflict")
        }
        if (rhr.conflict) {
            scores[ApprovedWellnessScoreEngine.SLEEP_ID] = conflict(ApprovedWellnessScoreEngine.SLEEP_ID, asOf, 4, "duplicate_rhr_record_id_conflict")
            scores[ApprovedWellnessScoreEngine.BODY_BATTERY_ID] = conflict(ApprovedWellnessScoreEngine.BODY_BATTERY_ID, asOf, 3, "duplicate_rhr_record_id_conflict")
            scores[ApprovedWellnessScoreEngine.SESSION_LOAD_ID] = conflict(ApprovedWellnessScoreEngine.SESSION_LOAD_ID, asOf, 4, "duplicate_rhr_record_id_conflict")
        }
        if (exercise.conflict) {
            scores[ApprovedWellnessScoreEngine.BODY_BATTERY_ID] = conflict(ApprovedWellnessScoreEngine.BODY_BATTERY_ID, asOf, 3, "duplicate_exercise_record_id_conflict")
            scores[ApprovedWellnessScoreEngine.SESSION_LOAD_ID] = conflict(ApprovedWellnessScoreEngine.SESSION_LOAD_ID, asOf, 4, "duplicate_exercise_record_id_conflict")
        }
        if (heartRate.conflict) {
            scores[ApprovedWellnessScoreEngine.BODY_BATTERY_ID] = conflict(ApprovedWellnessScoreEngine.BODY_BATTERY_ID, asOf, 3, "duplicate_heart_rate_record_id_conflict")
            scores[ApprovedWellnessScoreEngine.SESSION_LOAD_ID] = conflict(ApprovedWellnessScoreEngine.SESSION_LOAD_ID, asOf, 4, "duplicate_heart_rate_record_id_conflict")
        }
        if (steps.conflict) {
            scores[ApprovedWellnessScoreEngine.BODY_BATTERY_ID] = conflict(ApprovedWellnessScoreEngine.BODY_BATTERY_ID, asOf, 3, "duplicate_steps_record_id_conflict")
        }
        return ApprovedWellnessScoreReadout(
            scores = scores,
            observedOrigins = observed,
            normalizedInput = input,
            batteryDayTrend = scores[ApprovedWellnessScoreEngine.BODY_BATTERY_ID]
                ?.let { score -> score.score?.let { value -> listOf(ScoreTrendPoint(score.asOf, value)) } }
                .orEmpty(),
        )
    }

    private fun <T> origins(records: List<T>, origin: (T) -> DataOrigin): Set<String> =
        records.map { origin(it).packageName }.toSet()

    private fun enginePermissionKey(recordType: String): String = when (recordType) {
        ApprovedWellnessRecordTypes.SLEEP_SESSION -> "sleep"
        ApprovedWellnessRecordTypes.HRV_RMSSD -> "hrv"
        ApprovedWellnessRecordTypes.RESTING_HEART_RATE -> "rhr"
        ApprovedWellnessRecordTypes.EXERCISE_SESSION -> "exercise"
        ApprovedWellnessRecordTypes.HEART_RATE -> "heart_rate"
        ApprovedWellnessRecordTypes.STEPS -> "steps"
        else -> error("Unapproved Health Connect record type: $recordType")
    }

    private fun baselineDays(values: List<Triple<Instant, Double, String>>, zone: ZoneId): List<ApprovedWellnessScoreEngine.BaselineDay> =
        values.asSequence()
            .filter { (_, value, _) -> value.isFinite() && value > 0.0 }
            .groupBy { (at, _, source) -> source to at }
            .values
            .map { it.first() }
            .groupBy { (at, _, source) -> localDate(at, zone) to source }
            .map { (dateAndSource, entries) ->
                ApprovedWellnessScoreEngine.BaselineDay(
                    date = dateAndSource.first,
                    value = median(entries.map { it.second }),
                    source = dateAndSource.second,
                )
            }
            .sortedWith(compareBy<ApprovedWellnessScoreEngine.BaselineDay> { it.date }.thenBy { it.source })
            .toList()

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
    }

    private fun localDate(instant: Instant, zone: ZoneId): LocalDate = instant.atZone(zone).toLocalDate()

    private fun midpointMinute(start: Instant, end: Instant, zone: ZoneId): Double {
        val midpoint = start.plusNanos(java.time.Duration.between(start, end).toNanos() / 2)
        val local = midpoint.atZone(zone)
        val fractionalMinute = local.minute + local.second / 60.0 + local.nano / 60_000_000_000.0
        return (ApprovedWellnessScoreEngine.halfUp(local.hour * 60.0 + fractionalMinute).toInt() % 1440).toDouble()
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

    private fun sleepSignature(record: SleepSessionRecord) = listOf(
        record.startTime, record.endTime, record.startZoneOffset, record.endZoneOffset,
        record.stages.sortedBy { it.startTime }.map { listOf(it.startTime, it.endTime, it.stage) },
    )

    private fun hrvSignature(record: HeartRateVariabilityRmssdRecord) = listOf(record.time, record.zoneOffset, record.heartRateVariabilityMillis)
    private fun rhrSignature(record: RestingHeartRateRecord) = listOf(record.time, record.zoneOffset, record.beatsPerMinute)
    private fun exerciseSignature(record: ExerciseSessionRecord) = listOf(record.startTime, record.endTime, record.startZoneOffset, record.endZoneOffset)
    private fun heartRateSignature(record: HeartRateRecord) = listOf(record.startZoneOffset, record.endZoneOffset, record.samples.map { it.time to it.beatsPerMinute })
    private fun stepsSignature(record: StepsRecord) = listOf(record.startTime, record.endTime, record.startZoneOffset, record.endZoneOffset, record.count)

    private fun <T> dedupeById(records: List<T>, id: (T) -> String, signature: (T) -> Any): Deduped<T> {
        val unique = linkedMapOf<String, T>()
        val conflicts = mutableSetOf<String>()
        val noId = mutableListOf<T>()
        for (record in records) {
            val key = id(record)
            if (key.isBlank()) {
                noId += record
                continue
            }
            val previous = unique[key]
            if (previous == null) unique[key] = record
            else if (signature(previous) != signature(record)) conflicts += key
        }
        return Deduped(noId + unique.values, conflicts.isNotEmpty())
    }

    private data class Deduped<T>(val records: List<T>, val conflict: Boolean)

    private fun conflict(id: String, asOf: Instant, expected: Int, reason: String) =
        ApprovedWellnessScoreEngine.ScoreResult(
            scoreId = id,
            score = null,
            status = ApprovedWellnessScoreEngine.Status.source_conflict,
            asOf = asOf,
            coverage = ApprovedWellnessScoreEngine.Coverage(0, expected, missingReasons = listOf(reason)),
        )
}

internal fun WellnessScoreRecords.through(at: Instant): WellnessScoreRecords = WellnessScoreRecords(
    sleepSessions = sleepSessions.filter { it.endTime <= at },
    hrvRmssd = hrvRmssd.filter { it.time <= at },
    restingHeartRate = restingHeartRate.filter { it.time <= at },
    exerciseSessions = exerciseSessions.filter { it.endTime <= at },
    heartRate = heartRate.filter { it.endTime <= at },
    steps = steps.filter { it.endTime <= at },
)
