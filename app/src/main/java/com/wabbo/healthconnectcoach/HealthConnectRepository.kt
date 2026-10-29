package com.wabbo.healthconnectcoach

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.Lifecycle
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.reflect.KClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The only Health Connect data types approved for this app. */
object HealthConnectPermissions {
    val sleep: Set<String> = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class),
    )

    val hrv: Set<String> = setOf(
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
    )

    val restingHeartRate: Set<String> = setOf(
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
    )

    val readiness: Set<String> = hrv + restingHeartRate

    val trainingRecovery: Set<String> = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    )

    val heartRate: Set<String> = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
    )

    val steps: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
    )

    /** Exact app-declared standard read catalog; this is a concrete set, not a wildcard. */
    val allAllowed: Set<String> = HealthConnectSetup.standardPermissions
}

/** A bounded interval after applying the v0.3 limits and no-future-data limit. */
data class HealthConnectQueryWindow(
    val start: Instant,
    val end: Instant,
    /** True when the requested interval starts before the returned interval. */
    val isClipped: Boolean,
)

/** Pure time-window logic, kept separate so its privacy boundary can be unit-tested. */
internal object HealthConnectTimeWindow {
    val maximum: Duration = Duration.ofDays(30)
    val maximumWithHistory: Duration = Duration.ofDays(90)

    fun bound(
        requestedStart: Instant,
        requestedEnd: Instant,
        now: Instant,
        historyGranted: Boolean = false,
    ): HealthConnectQueryWindow? {
        if (requestedStart.isAfter(requestedEnd)) return null

        val end = minOf(requestedEnd, now)
        val maximumAllowed = if (historyGranted) maximumWithHistory else maximum
        val start = maxOf(requestedStart, end.minus(maximumAllowed))
        if (!start.isBefore(end)) return null

        return HealthConnectQueryWindow(
            start = start,
            end = end,
            isClipped = requestedStart.isBefore(start),
        )
    }
}

internal data class HealthConnectPage<T>(
    val records: List<T>,
    val nextPageToken: String?,
)

class HealthConnectReadCapacityException : IllegalStateException("Die Pulsdatenmenge ist zu groß. Der letzte gespeicherte Stand bleibt erhalten.")

class HealthConnectPermissionRevokedException : SecurityException(
    "The Health Connect permission was revoked during a read; accumulated records were discarded.",
)

class HealthConnectForegroundRequiredException : IllegalStateException(
    "Health Connect reads require the activity to remain at least STARTED.",
)

class HealthConnectAppDisconnectedException : IllegalStateException(
    "The app is disconnected; no Health Connect reads are allowed.",
)

/**
 * The page collector owns its temporary record buffer. It checks access both
 * immediately before and immediately after every provider call; any exception,
 * revocation, or lifecycle transition clears the buffer before propagating.
 */
internal suspend fun <T> collectHealthConnectPages(
    accumulator: MutableList<T> = mutableListOf(),
    beforeAndAfterPage: suspend () -> Unit,
    readPage: suspend (pageToken: String?) -> HealthConnectPage<T>,
): List<T> {
    val seenPageTokens = mutableSetOf<String>()
    var pageToken: String? = null
    try {
        while (true) {
            beforeAndAfterPage()
            val page = readPage(pageToken)
            beforeAndAfterPage()
            accumulator.addAll(page.records)
            val nextPageToken = page.nextPageToken ?: break
            check(seenPageTokens.add(nextPageToken)) {
                "Health Connect returned a repeated page token."
            }
            pageToken = nextPageToken
        }
        return accumulator.toList()
    } catch (failure: Throwable) {
        accumulator.clear()
        throw failure
    }
}

/** Results are held only within a single user-initiated foreground operation. */
data class ReadinessRecords(
    val hrvRmssdMs: List<HeartRateVariabilityRmssdRecord>,
    val restingHeartRate: List<RestingHeartRateRecord>,
)

/** The six non-medical record types used by the approved wellness score. */
data class WellnessScoreRecords(
    val sleepSessions: List<SleepSessionRecord>,
    val hrvRmssd: List<HeartRateVariabilityRmssdRecord>,
    val restingHeartRate: List<RestingHeartRateRecord>,
    val exerciseSessions: List<ExerciseSessionRecord>,
    val heartRate: List<HeartRateRecord>,
    val steps: List<StepsRecord>,
)

/** Metadata and records for a read whose requested interval may have been clipped. */
data class HealthConnectReadResult<T>(
    val records: List<T>,
    val window: HealthConnectQueryWindow,
) {
    val isIncomplete: Boolean get() = window.isClipped
}

/**
 * Read-only Health Connect adapter. It reads the activity's Lifecycle directly
 * before and after each provider page and rechecks the exact grant for that page.
 * Results remain in memory; this class never persists, logs, or transmits records.
 */
class HealthConnectRepository(
    private val client: HealthConnectClient,
    private val lifecycle: Lifecycle,
    private val isAppConnected: () -> Boolean,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun readSleepSessions(start: Instant, end: Instant): List<SleepSessionRecord> =
        readRecords(SleepSessionRecord::class, start, end).records

    suspend fun readSleepSessionsWithWindow(start: Instant, end: Instant): HealthConnectReadResult<SleepSessionRecord> =
        readRecords(SleepSessionRecord::class, start, end)

    suspend fun readHrvRmssd(start: Instant, end: Instant): List<HeartRateVariabilityRmssdRecord> =
        readRecords(HeartRateVariabilityRmssdRecord::class, start, end).records

    suspend fun readHrvRmssdWithWindow(start: Instant, end: Instant): HealthConnectReadResult<HeartRateVariabilityRmssdRecord> =
        readRecords(HeartRateVariabilityRmssdRecord::class, start, end)

    suspend fun readRestingHeartRate(start: Instant, end: Instant): List<RestingHeartRateRecord> =
        readRecords(RestingHeartRateRecord::class, start, end).records

    suspend fun readRestingHeartRateWithWindow(start: Instant, end: Instant): HealthConnectReadResult<RestingHeartRateRecord> =
        readRecords(RestingHeartRateRecord::class, start, end)

    suspend fun readHeartRate(start: Instant, end: Instant): List<HeartRateRecord> =
        readRecords(HeartRateRecord::class, start, end).records

    suspend fun readHeartRateWithWindow(start: Instant, end: Instant, workoutWindows: List<Pair<Instant, Instant>>? = null): HealthConnectReadResult<HeartRateRecord> {
        val index = workoutWindows?.let(::WorkoutPulseWindows)
        return readRecords(HeartRateRecord::class, start, end) { record ->
            if (index == null) record else {
                val relevant = record.samples.filter { index.contains(it.time) }
                if (relevant.isEmpty()) null else HeartRateRecord(record.startTime, record.startZoneOffset,
                    record.endTime, record.endZoneOffset, relevant, record.metadata)
            }
        }
    }

    suspend fun readSteps(start: Instant, end: Instant): List<StepsRecord> =
        readRecords(StepsRecord::class, start, end).records

    suspend fun readStepsWithWindow(start: Instant, end: Instant): HealthConnectReadResult<StepsRecord> =
        readRecords(StepsRecord::class, start, end)

    suspend fun readReadinessRecords(start: Instant, end: Instant): ReadinessRecords {
        val hrv = readHrvRmssd(start, end).toMutableList()
        var restingHeartRate = mutableListOf<RestingHeartRateRecord>()
        try {
            restingHeartRate = readRestingHeartRate(start, end).toMutableList()
            return ReadinessRecords(hrvRmssdMs = hrv.toList(), restingHeartRate = restingHeartRate.toList())
        } catch (failure: Throwable) {
            hrv.clear()
            restingHeartRate.clear()
            throw failure
        }
    }

    suspend fun readExerciseSessions(start: Instant, end: Instant): List<ExerciseSessionRecord> =
        readRecords(ExerciseSessionRecord::class, start, end).records

    suspend fun readExerciseSessionsWithWindow(start: Instant, end: Instant): HealthConnectReadResult<ExerciseSessionRecord> =
        readRecords(ExerciseSessionRecord::class, start, end)

    /** Reads exactly the six approved score inputs; a failure discards all partial lists. */
    suspend fun readWellnessScoreRecords(start: Instant, end: Instant): WellnessScoreRecords {
        val sleep = mutableListOf<SleepSessionRecord>()
        val hrv = mutableListOf<HeartRateVariabilityRmssdRecord>()
        val resting = mutableListOf<RestingHeartRateRecord>()
        val exercise = mutableListOf<ExerciseSessionRecord>()
        val heartRate = mutableListOf<HeartRateRecord>()
        val steps = mutableListOf<StepsRecord>()
        try {
            sleep.addAll(readSleepSessions(start, end))
            hrv.addAll(readHrvRmssd(start, end))
            resting.addAll(readRestingHeartRate(start, end))
            exercise.addAll(readExerciseSessions(start, end))
            heartRate.addAll(readHeartRate(start, end))
            steps.addAll(readSteps(start, end))
            return WellnessScoreRecords(sleep.toList(), hrv.toList(), resting.toList(), exercise.toList(), heartRate.toList(), steps.toList())
        } catch (failure: Throwable) {
            sleep.clear(); hrv.clear(); resting.clear(); exercise.clear(); heartRate.clear(); steps.clear()
            throw failure
        }
    }

    internal suspend fun <T : Record> readAdditional(type: KClass<T>, start: Instant, end: Instant): HealthConnectReadResult<T> =
        readRecords(type, start, end)

    private suspend fun <T : Record> readRecords(
        recordType: KClass<T>,
        requestedStart: Instant,
        requestedEnd: Instant,
        transform: (T) -> T? = { it },
    ): HealthConnectReadResult<T> {
        val requiredPermission = HealthPermission.getReadPermission(recordType)
        ensureReadAllowed(requiredPermission)
        val now = clock.instant()
        val needsHistory = requestedStart.isBefore(minOf(requestedEnd, now).minus(HealthConnectTimeWindow.maximum))
        val historyGranted = needsHistory && hasGrantedPermission(HealthConnectSetup.historyPermission)
        val window = HealthConnectTimeWindow.bound(requestedStart, requestedEnd, now, historyGranted)
            ?: return HealthConnectReadResult(emptyList(), HealthConnectQueryWindow(requestedStart, requestedEnd, false))
        val timeFilter = TimeRangeFilter.between(window.start, window.end)
        val permissions = if (needsHistory) setOf(requiredPermission, HealthConnectSetup.historyPermission) else setOf(requiredPermission)
        val temporaryRecords = mutableListOf<T>()
        var retainedPulseSamples = 0

        val records = collectHealthConnectPages(
            accumulator = temporaryRecords,
            beforeAndAfterPage = { ensureReadAllowed(permissions, historyRequired = historyGranted) },
            readPage = { pageToken -> withContext(Dispatchers.Default) {
                val response = client.readRecords(
                    ReadRecordsRequest(
                        recordType = recordType,
                        timeRangeFilter = timeFilter,
                        pageToken = pageToken,
                        pageSize = 250,
                    ),
                )
                val retained = response.records.mapNotNull(transform)
                retainedPulseSamples += retained.sumOf { (it as? HeartRateRecord)?.samples?.size ?: 0 }
                if (retainedPulseSamples > 200_000) throw HealthConnectReadCapacityException()
                HealthConnectPage(retained, response.pageToken)
            } },
        )
        return HealthConnectReadResult(records, window)
    }

    private suspend fun ensureReadAllowed(requiredPermission: String) =
        ensureReadAllowed(setOf(requiredPermission), historyRequired = false)

    private suspend fun ensureReadAllowed(requiredPermissions: Set<String>, historyRequired: Boolean) {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) throw HealthConnectForegroundRequiredException()
        if (!isAppConnected()) throw HealthConnectAppDisconnectedException()
        val granted = client.permissionController.getGrantedPermissions()
        val required = if (historyRequired) requiredPermissions else requiredPermissions - HealthConnectSetup.historyPermission
        if (!hasAllGrantedPermissions(required, granted)) throw HealthConnectPermissionRevokedException()
    }

    private suspend fun hasGrantedPermission(permission: String): Boolean =
        permission in client.permissionController.getGrantedPermissions()
}
