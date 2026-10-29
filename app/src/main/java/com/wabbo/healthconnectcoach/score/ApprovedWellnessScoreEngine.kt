package com.wabbo.healthconnectcoach.score

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.ln
import kotlin.math.exp

/** Pure JVM score calculator. Adapters normalize Health Connect records into [NormalizedInput]. */
class ApprovedWellnessScoreEngine {
    companion object {
        const val SLEEP_ID = "sleep-extended-v1"
        const val BODY_BATTERY_ID = "body-battery-v1"
        const val SESSION_LOAD_ID = "session-load-v1"
        const val MODEL_VERSION = "health-coach-v2.2"
        const val WEEKLY_MODEL_VERSION = MODEL_VERSION+"/adaptive-3/weekly"
        private const val DAY = 86_400L
        fun halfUp(v: Double, digits: Int = 0): Double = BigDecimal.valueOf(v).setScale(digits, RoundingMode.HALF_UP).toDouble()
        fun clamp(v: Double, lo: Double = 0.0, hi: Double = 100.0) = min(hi, max(lo, v))
    }

    enum class Status { available, provisional, insufficient_data, permission_missing, stale, source_conflict }
    enum class PermissionState { granted, missing, unavailable }
    enum class RecordType { SLEEP_SESSION, HRV_RMSSD, RESTING_HEART_RATE, EXERCISE_SESSION, HEART_RATE, STEPS }
    data class HrCoverage(val coveredSeconds: Long = 0, val totalSeconds: Long = 0, val uniqueSamples: Int = 0) {
        val intervalCoverage: Double? get() = totalSeconds.takeIf { it > 0 }?.let { coveredSeconds.toDouble() / it }
    }
    data class Coverage(
        val available: Int,
        val expected: Int,
        val sources: Set<String> = emptySet(),
        val hrSeconds: Long = 0,
        val missingReasons: List<String> = emptyList(),
        val sourcesByType: Map<RecordType, Set<String>> = emptyMap(),
        val hrCoverage: HrCoverage = HrCoverage(),
        val windowCompleteness: Map<String, Boolean> = emptyMap(),
        val hrCoverageByWindow: Map<String, HrCoverage> = emptyMap(),
    )
    data class ScoreResult(val scoreId: String, val score: Int?, val status: Status, val asOf: Instant, val coverage: Coverage,
        val components: Map<String, Int> = emptyMap(), val rawIndex: Double? = null, val rating0to100: Int? = null,
        val provisional: Boolean = status == Status.provisional, val insufficientData: Boolean = status == Status.insufficient_data,
        val sourceConflict: Boolean = status == Status.source_conflict, val algorithmVersion: String = scoreId,
        val stale: Boolean = status == Status.stale)
    data class SleepStage(val start: Instant, val end: Instant, val type: String)
    data class SleepSession(
        val start: Instant,
        val end: Instant,
        val source: String,
        val id: String,
        val stages: List<SleepStage> = emptyList(),
        val localWakeDate: LocalDate? = null,
        val localMidpointMinute: Double? = null,
        val startZoneOffset: ZoneOffset? = null,
        val endZoneOffset: ZoneOffset? = null,
        val shortAwakenings: List<SleepStage> = emptyList(),
        val isMainSleep: Boolean? = null,
    )
    data class SleepNight(
        val date: LocalDate,
        val midpointMinute: Double,
        val source: String,
        val start: Instant? = null,
        val end: Instant? = null,
        val startZoneOffset: ZoneOffset? = null,
        val endZoneOffset: ZoneOffset? = null,
    )
    data class MetricSample(
        val at: Instant,
        val value: Double,
        val source: String,
        val id: String = "",
        val zoneOffset: ZoneOffset? = null,
        val recordStartZoneOffset: ZoneOffset? = null,
        val recordEndZoneOffset: ZoneOffset? = null,
        /** Explicit daily summary from the provider, not a made-up observation timestamp. */
        val dailyDate: LocalDate? = null,
    )
    data class BaselineDay(val date: LocalDate, val value: Double, val source: String)
    data class Workout(
        val start: Instant,
        val end: Instant,
        val source: String,
        val id: String,
        val heartRate: List<MetricSample> = emptyList(),
        val localStartDate: LocalDate? = null,
        val startZoneOffset: ZoneOffset? = null,
        val endZoneOffset: ZoneOffset? = null,
        val title: String = "Training",
    )
    data class Steps(
        val start: Instant,
        val end: Instant,
        val count: Double,
        val source: String,
        val id: String = "",
        val startZoneOffset: ZoneOffset? = null,
        val endZoneOffset: ZoneOffset? = null,
    )
    /** boundSources keys: sleep, hrv, rhr, exercise, heart_rate, steps. */
    data class NormalizedInput(val asOf: Instant, val zone: ZoneId = ZoneId.of("Europe/Berlin"),
        val sleeps: List<SleepSession> = emptyList(), val priorNights: List<SleepNight> = emptyList(),
        val hrv: List<MetricSample> = emptyList(), val rhr: List<MetricSample> = emptyList(),
        val hrvBaseline: List<BaselineDay> = emptyList(), val rhrBaseline: List<BaselineDay> = emptyList(),
        val workouts: List<Workout> = emptyList(), val steps: List<Steps> = emptyList(),
        val boundSources: Map<String, String?> = emptyMap(), val readComplete: Boolean = true,
        val historyComplete: Boolean = true, val sleepGoalMinutes: Double = 480.0,
        val permissions: Map<String, PermissionState> = emptyMap(),
        val windowCompleteness: Map<String, Boolean> = emptyMap(),
        val recordTypeWindowCompleteness: Map<RecordType, Map<String, Boolean>> = emptyMap(),
        val weeklyTargetLoad: Double? = null,
        val trainingHistoryStart: Instant? = null, val trainingHistoryComplete: Boolean = false,
        val personalProfile: PersonalScoreProfile = PersonalScoreProfile(),
        val weeklyGoalAdjustments: Map<String,Int> = emptyMap())

    fun scoreSleep(i: NormalizedInput): ScoreResult {
        val n = i.asOf
        if (permissionMissing(i, "sleep")) return bad(SLEEP_ID, n, 4, Status.permission_missing, "sleep_permission_missing")
        val (main, conflict) = mainSleep(i)
        if (conflict) return bad(SLEEP_ID, n, 4, Status.source_conflict, "source_conflict")
        if (main == null && hasStaleMainSleep(i)) return bad(SLEEP_ID, n, 4, Status.stale, "main_sleep_stale")
        if (main == null && !i.readComplete) return bad(SLEEP_ID, n, 4, Status.insufficient_data, "sleep_read_incomplete")
        main ?: return bad(SLEEP_ID, n, 4, Status.insufficient_data, "main_sleep_missing")
        if (sleepMetricConflict(i, main)) return bad(SLEEP_ID, n, 4, Status.source_conflict, "source_conflict")
        if (!i.readComplete) return bad(SLEEP_ID, n, 4, Status.insufficient_data, "sleep_read_incomplete")
        val d = clamp(100 * sleepMinutes(main) / effectiveSleepGoal(i))
        val wake = wakeDate(main, i.zone)
        val prior = i.priorNights.filter { it.date in wake.minusDays(28)..wake.minusDays(1) && boundOk(i, "sleep", it.source) && (i.boundSources["sleep"] != null || it.source == main.source) }.groupBy { it.date }.values.map { it.last() }
        val r = if (prior.size >= 5) clamp(100 - 100 * min(circularDistance(midpoint(main, i.zone), circularMedian(prior.map { it.midpointMinute })), 120.0) / 120) else null
        val h = metricComponent(main, i, true, "hrv")
        val p = metricComponent(main, i, false, "rhr")
        val q = sleepComposition(main, i)
        val efficiency = sleepEfficiencyComponent(main)
        val values = listOf("D" to d, "R" to r, "E" to efficiency, "H" to h, "P" to p, "Q" to q)
        // Own wellness model; WHOOP's proprietary weights are not reproduced.
        val weights = mapOf("D" to .30, "E" to .20, "R" to .10, "Q" to .10, "H" to .20, "P" to .10)
        val present = values.filter { it.second != null }
        if (present.size < 2) return ok(SLEEP_ID, null, Status.insufficient_data, n, 6, setOf(main.source),
            listOf("insufficient_data", "regularity_missing") + metricMissingDetails(i, main, "hrv") + metricMissingDetails(i, main, "rhr"),
            mapOf("D" to halfUp(d).toInt()))
        val reasons = mutableListOf<String>()
        if (r == null) reasons += "regularity_missing"
        if (h == null) { reasons += optionalReason(i, "hrv", "HRV_missing"); reasons += metricMissingDetails(i, main, "hrv") }
        else if (usesDailyValue(i, main, "hrv")) reasons += "hrv_daily_value_used"
        if (p == null) { reasons += optionalReason(i, "rhr", "RHR_missing"); reasons += metricMissingDetails(i, main, "rhr") }
        else if (usesDailyValue(i, main, "rhr")) reasons += "rhr_daily_value_used"
        if (stageMode(main) != StageMode.COMPLETE) reasons += if (stageMode(main) == StageMode.MISSING) "sleep_stages_missing" else "sleep_stages_partial"
        if(efficiency == null) reasons += "sleep_efficiency_missing"
        val status = if (h != null && p != null && r != null && efficiency != null && reasons.isEmpty()) Status.available else Status.provisional
        val components = present.associate { it.first to halfUp(it.second!!).toInt() }
        // Good stages/HRV must not compensate away a severely short night.
        val combined = present.sumOf { weights[it.first]!! * it.second!! } / present.sumOf { weights[it.first]!! }
        return ok(SLEEP_ID, halfUp(min(combined, d + 15.0)).toInt(), status, n, 6,
            sourcesForSleep(i, main), reasons, components + profileComponents(i), sourcesByType = sourcesForSleepByType(i, main))
    }

    fun scoreBodyBattery(i: NormalizedInput): ScoreResult {
        val n = i.asOf
        if (permissionMissing(i, "sleep")) return bad(BODY_BATTERY_ID, n, 3, Status.permission_missing, "sleep_permission_missing")
        val (main, conflict) = mainSleep(i)
        if (conflict) return bad(BODY_BATTERY_ID, n, 3, Status.source_conflict, "source_conflict")
        if (main == null && hasStaleMainSleep(i)) return bad(BODY_BATTERY_ID, n, 3, Status.stale, "main_sleep_stale")
        if (main == null && !i.readComplete) return bad(BODY_BATTERY_ID, n, 3, Status.insufficient_data, "sleep_read_incomplete")
        main ?: return bad(BODY_BATTERY_ID, n, 3, Status.insufficient_data, "main_sleep_missing")
        if (sleepMetricConflict(i, main)) return bad(BODY_BATTERY_ID, n, 3, Status.source_conflict, "source_conflict")
        val d = clamp(100 * sleepMinutes(main) / effectiveSleepGoal(i))
        val h = metricComponent(main, i, true, "hrv")
        val p = metricComponent(main, i, false, "rhr")
        // A sleep-only estimate must not masquerade as nearly perfect recovery.
        if(h==null) return ok(BODY_BATTERY_ID,null,Status.insufficient_data,n,3,sourcesForSleep(i,main),
            listOf("energy_hrv_baseline_or_current_missing")+metricMissingDetails(i,main,"hrv"),mapOf("D" to halfUp(d).toInt()))
        val present = listOf("D" to d, "H" to h, "P" to p).filter { it.second != null }
        val sleepParts=scoreSleep(i).components
        val sleepWeights=mapOf("D" to .30,"E" to .20,"R" to .10,"Q" to .10)
        val availableSleep=sleepWeights.filterKeys { it in sleepParts }
        val sleepBasis=if(availableSleep.isEmpty()) d else availableSleep.entries.sumOf {it.value*sleepParts.getValue(it.key)} / availableSleep.values.sum()
        val recoverySignals=listOf(sleepBasis to .20,h to .50,p to .30).filter {it.first!=null}
        val recoveryBase=recoverySignals.sumOf {it.first!!*it.second}/recoverySignals.sumOf {it.second}
        // Morning physiology already reflects prior effort. Do not deduct the same
        // past workouts again as if their physiological response were unobserved.
        val priorLoad = 0.0
        val sleepDebt = recentSleepDebt(i, main)
        val startEnergy = halfUp(clamp(min(recoveryBase, d + 15.0)-priorLoad-sleepDebt)).toInt()
        var b = startEnergy.toDouble()
        val awakeUse=awakeEnergyUse(main.end,n)
        b -= awakeUse
        var trainingUse=0.0
        var stepUse=0.0
        var provisional = present.size < 3 || stageMode(main) != StageMode.COMPLETE
        val reasons = mutableListOf<String>()
        if (h == null) { reasons += optionalReason(i, "hrv", "HRV_missing"); reasons += metricMissingDetails(i, main, "hrv") }
        else if (usesDailyValue(i, main, "hrv")) reasons += "hrv_daily_value_used"
        if (p == null) { reasons += optionalReason(i, "rhr", "RHR_missing"); reasons += metricMissingDetails(i, main, "rhr") }
        else if (usesDailyValue(i, main, "rhr")) reasons += "rhr_daily_value_used"
        if (stageMode(main) == StageMode.MISSING) reasons += "sleep_stages_missing" else if (stageMode(main) == StageMode.PARTIAL) reasons += "sleep_stages_partial"
        if(i.personalProfile.pulseLimited) reasons += "pulse_weighting_disabled"
        else if(i.personalProfile.measuredMaxHeartRate==null && i.personalProfile.maximumAt(i.asOf.atZone(i.zone).toLocalDate())!=null) reasons += "max_hr_age_estimated"
        if (reasons.any { it.endsWith("_daily_value_used") || it in setOf("pulse_weighting_disabled","max_hr_age_estimated") }) provisional = true
        val ws = dedupeIdenticalWorkouts(i.workouts.filter { it.start >= main.end && it.end <= n && it.end > it.start })
        if (hasWorkoutOverlapConflict(ws)) return bad(BODY_BATTERY_ID, n, 3, Status.source_conflict, "overlapping_workout_sessions")
        if (sourceConflictForWorkouts(i, ws)) return bad(BODY_BATTERY_ID, n, 3, Status.source_conflict, "source_conflict")
        val validStepIntervals = i.steps.filter { it.count.isFinite() && it.count > 0.0 && it.end > it.start && it.end <= n }
        if (validStepIntervals.any { it.start < main.end && it.end > main.end }) {
            provisional = true
            reasons += "steps_wake_boundary_overlap"
        }
        val relevantSteps = validStepIntervals.filter { it.start >= main.end && it.start < n }
        val scoredSteps = relevantSteps.filter { step -> ws.none { step.start < it.end && step.end > it.start } }
        val stepSources = scoredSteps.filter { boundOk(i, "steps", it.source) }.map { it.source }.toSet()
        if (i.boundSources["steps"] == null && stepSources.size > 1) return bad(BODY_BATTERY_ID, n, 3, Status.source_conflict, "source_conflict")
        if (!i.readComplete) return bad(BODY_BATTERY_ID, n, 3, Status.insufficient_data, "sleep_read_incomplete")
        var hrSeconds = 0L
        var measuredHrCoverage = HrCoverage()
        val permissionReasons = dosePermissionReasons(i)
        if(ws.isNotEmpty()) {
            val doses=ws.map { workoutDose(it,i) }
            measuredHrCoverage=combineHrCoverage(doses.map {it.coverage})
            ws.zip(doses).forEach { (workout,dose) ->
                val usePulse=permissionReasons.isEmpty()&&dose.dose!=null
                val weighted=activityLoad(workout,i).weightedMinutes
                trainingUse += .15*weighted
                if(usePulse) hrSeconds+=dose.coverage.coveredSeconds
                else {provisional=true;reasons+="duration_fallback"}
            }
            if(permissionReasons.isNotEmpty()) {provisional=true;reasons+=permissionReasons}
        }
        b -= trainingUse
        if (permissionMissing(i, "steps")) { provisional = true; reasons += "steps_permission_missing" }
        relevantSteps.distinctBy {listOf(it.start,it.end,it.source,it.count)}.forEach { s ->
            if (boundOk(i, "steps", s.source) && s.start < s.end && ws.none { s.start < it.end && s.end > it.start }) stepUse += .7 * s.count / 1000.0
            else if (ws.any { s.start < it.end && s.end > it.start }) { provisional = true; reasons += "steps_workout_overlap" }
        }
        if (ws.isEmpty()) {
            provisional = true
            reasons += if (relevantSteps.isEmpty()) "activity_missing" else "workout_sessions_missing"
        }
        b -= stepUse
        val parts = mutableMapOf("D" to halfUp(d).toInt()); if (h != null) parts["H"] = halfUp(h).toInt(); if (p != null) parts["P"] = halfUp(p).toInt()
        parts["S"] = halfUp(sleepBasis).toInt()
        parts["RECOVERY"] = halfUp(recoveryBase).toInt()
        parts["PRIOR_LOAD"] = halfUp(priorLoad).toInt()
        parts["SLEEP_DEBT"] = halfUp(sleepDebt).toInt()
        parts["AWAKE"] = halfUp(awakeUse).toInt()
        parts["TRAINING"] = halfUp(trainingUse).toInt()
        parts["STEPS"] = halfUp(stepUse).toInt()
        parts["START"] = startEnergy
        parts["USED"] = startEnergy - halfUp(clamp(b)).toInt()
        val typedSources = sourcesForBatteryByType(i, main, ws, scoredSteps.filter { boundOk(i, "steps", it.source) })
        return ok(BODY_BATTERY_ID, halfUp(clamp(b)).toInt(), if (provisional) Status.provisional else Status.available, n, 3,
            typedSources.values.flatten().toSet(), reasons, parts + profileComponents(i), hrSeconds, sourcesByType = typedSources,
            hrCoverage = measuredHrCoverage,
            hrCoverageByWindow = if (ws.isEmpty()) emptyMap() else mapOf("since_wake" to measuredHrCoverage))
    }

    fun scoreSessionLoad(i: NormalizedInput): ScoreResult {
        val n = i.asOf
        val windowCompleteness = sessionLoadWindowCompleteness(i)
        if (permissionMissing(i, "exercise")) return bad(SESSION_LOAD_ID, n, 4, Status.permission_missing, "exercise_permission_missing", windowCompleteness = windowCompleteness)
        val bounds = (0..4).map { n.minusSeconds(it * 7L * DAY) }
        val windows = (0..3).map { k ->
            dedupeIdenticalWorkouts(i.workouts.filter { it.start >= bounds[k + 1] && it.start < bounds[k] && it.end > it.start && it.end <= n })
        }
        val all = windows.flatten()
        if (hasWorkoutOverlapConflict(all)) return bad(SESSION_LOAD_ID, n, 4, Status.source_conflict, "overlapping_workout_sessions", windowCompleteness = windowCompleteness)
        if (sourceConflictForWorkouts(i, all)) return bad(SESSION_LOAD_ID, n, 4, Status.source_conflict, "source_conflict", windowCompleteness = windowCompleteness)
        val windowDoses = windows.map { window -> window.map { workoutDose(it, i) } }
        val allDoses = windowDoses.flatten()
        val hrCoverageByWindow = windowDoses.mapIndexed { index, doses -> "W$index" to combineHrCoverage(doses.map { it.coverage }) }.toMap()
        val hrCoverage = combineHrCoverage(allDoses.map { it.coverage })
        val typedSources = sourcesForWorkoutsByType(i, all)
        val dosePermissions = dosePermissionReasons(i)
        if (all.isNotEmpty() && dosePermissions.isNotEmpty()) return ok(SESSION_LOAD_ID, null, Status.permission_missing, n, 4,
            typedSources.values.flatten().toSet(), dosePermissions, sourcesByType = typedSources, windowCompleteness = windowCompleteness,
            hrCoverage = hrCoverage, hrCoverageByWindow = hrCoverageByWindow)
        if (windowCompleteness.values.any { !it }) return bad(SESSION_LOAD_ID, n, 4, Status.insufficient_data, "reference_window_incomplete",
            windowCompleteness = windowCompleteness, sourcesByType = typedSources, hrCoverage = hrCoverage, hrCoverageByWindow = hrCoverageByWindow)
        val fallback = allDoses.any { it.dose == null }
        val sums = if (fallback) windows.map { w -> w.sumOf { minutes(it.start, it.end) } }
            else windowDoses.map { it.sumDose() }
        val ref = median(sums.drop(1))
        if (ref <= 0.0) return bad(SESSION_LOAD_ID, n, 4, Status.insufficient_data, "Wref_missing_or_zero",
            windowCompleteness = windowCompleteness, sourcesByType = typedSources, hrCoverage = hrCoverage, hrCoverageByWindow = hrCoverageByWindow)
        val raw = 100 * sums[0] / ref
        val rating = halfUp(clamp(100 - .5 * max(0.0, raw - 100))).toInt()
        val reasons = buildList { if (fallback) add("duration_fallback"); if (sums[0] == 0.0) add("no_session_load") }
        val hrSeconds = if (fallback) 0L else hrCoverage.coveredSeconds
        return ok(SESSION_LOAD_ID, rating, if (fallback || sums[0] == 0.0) Status.provisional else Status.available, n, 4,
            typedSources.values.flatten().toSet(), reasons, emptyMap(), hrSeconds, raw, rating, sourcesByType = typedSources,
            hrCoverage = hrCoverage, windowCompleteness = windowCompleteness, hrCoverageByWindow = hrCoverageByWindow)
    }

    data class ActivityLoad(val weightedMinutes: Double, val minutes: Double, val fallback: Boolean, val coverage: HrCoverage)
    fun batteryTimeline(i: NormalizedInput): List<Pair<Instant, Int>> {
        val current=scoreBodyBattery(i)
        val main=mainSleep(i).first ?: return emptyList()
        if(current.score==null) return emptyList()
        val ws=dedupeIdenticalWorkouts(i.workouts.filter {it.start>=main.end&&it.end<=i.asOf&&it.end>it.start})
        val times=(listOf(main.end,i.asOf)+(1L..Duration.between(main.end,i.asOf).toHours()).map {main.end.plusSeconds(it*3600)}+
            ws.flatMap {listOf(it.start,it.end)}).distinct().sorted()
        val selected=if(times.size<=100) times else (0..98).map {times[it*(times.size-1)/99]}+i.asOf
        // Reconstruct within recorded intervals using each completed workout's final
        // dose. Re-evaluating sample sufficiency for every partial interval would
        // switch between duration and pulse and could make energy rise mid-workout.
        val start = current.components.getValue("START").toDouble()
        val loads=ws.map {it to activityLoad(it,i).weightedMinutes*.15}
        val steps=i.steps.filter {it.count.isFinite() && it.count>0 && it.end>it.start &&
            it.start>=main.end && it.end<=i.asOf && boundOk(i,"steps",it.source) &&
            ws.none {w -> it.start<w.end && it.end>w.start}}
            .distinctBy {listOf(it.start,it.end,it.source,it.count)}
        fun fraction(at: Instant,begin: Instant,end: Instant)=
            (minutes(begin,minOf(at,end))/minutes(begin,end)).coerceIn(0.0,1.0)
        return selected.map {at ->
            val training=loads.sumOf {(w,load)->load*fraction(at,w.start,w.end)}
            val stepUse=steps.sumOf {.7*it.count/1000.0*fraction(at,it.start,it.end)}
            at to halfUp(clamp(start-awakeEnergyUse(main.end,at)-training-stepUse)).toInt()
        }
    }
    private fun awakeEnergyUse(wake: Instant,at: Instant): Double {
        val hours=max(0.0,Duration.between(wake,at).toMillis()/3_600_000.0)
        return 2.0*hours+max(0.0,hours-12.0)
    }

    fun activityLoad(workout: Workout, input: NormalizedInput): ActivityLoad {
        val dose = workoutDose(workout, input)
        val duration = minutes(workout.start, workout.end)
        // One dose model for weekly progress, historical targets, activity cards and energy drain.
        return ActivityLoad(dose.dose?.div(40.0) ?: duration, duration, dose.dose == null, dose.coverage)
    }

    /** Monday-to-now progress towards the user's explicitly confirmed weekly load target. */
    fun scoreWeeklyLoad(i: NormalizedInput, target: Double?): ScoreResult {
        val version = WEEKLY_MODEL_VERSION
        fun unavailable(reason: String, status: Status = Status.insufficient_data) = ScoreResult(SESSION_LOAD_ID, null, status,
            i.asOf, Coverage(0, 1, missingReasons = listOf(reason)), algorithmVersion = version)
        val recommendation=AdaptiveWeeklyLoad.evaluate(i,target) {activityLoad(it,i)}
        val baseTarget=recommendation.target ?: return unavailable(recommendation.reason ?: "weekly_target_missing")
        val week=i.asOf.atZone(i.zone).toLocalDate().with(java.time.DayOfWeek.MONDAY).toString()
        val adjustment=i.weeklyGoalAdjustments[week]?.takeIf {it in 75..125} ?: 100
        val effectiveTarget=baseTarget*adjustment/100.0
        if (permissionMissing(i, "exercise")) return unavailable("exercise_permission_missing", Status.permission_missing)
        if (i.recordTypeWindowCompleteness[RecordType.EXERCISE_SESSION]?.get("W0") == false) return unavailable("weekly_exercise_read_incomplete")
        val monday = i.asOf.atZone(i.zone).toLocalDate().with(java.time.DayOfWeek.MONDAY).atStartOfDay(i.zone).toInstant()
        val workouts = dedupeIdenticalWorkouts(i.workouts.filter { it.end > monday && it.end <= i.asOf && it.end > it.start }
            .map { if (it.start < monday) it.copy(start = monday, heartRate = it.heartRate.filter { p -> p.at >= monday }) else it })
        if (hasWorkoutOverlapConflict(workouts) || (i.boundSources["exercise"]==null && workouts.map {it.source}.distinct().size>1)) return unavailable("overlapping_or_ambiguous_workouts", Status.source_conflict)
        val loads = workouts.map { activityLoad(it, i) }
        if(loads.any {!it.fallback} && sourceConflictForWorkouts(i,workouts)) return unavailable("overlapping_or_ambiguous_workouts",Status.source_conflict)
        val raw = 100.0 * loads.sumOf { it.weightedMinutes } / effectiveTarget
        val fallback = loads.any { it.fallback }
        val reasons=buildList {
            if(fallback) add("duration_fallback")
            if(i.personalProfile.pulseLimited) add("pulse_weighting_disabled")
            else if(i.personalProfile.measuredMaxHeartRate==null && i.personalProfile.maximumAt(i.asOf.atZone(i.zone).toLocalDate())!=null) add("max_hr_age_estimated")
            recommendation.reason?.let {add(it)}
            if(recommendation.durationFallback) add("adaptive_history_duration_fallback")
            if(recommendation.recoveryHold) add("adaptive_recovery_hold")
            if(recommendation.fatigueHold) add("adaptive_fatigue_hold")
        }
        val parts=mutableMapOf("WEEK_MIN" to halfUp(loads.sumOf {it.minutes}).toInt(),"WEEK_LOAD" to halfUp(loads.sumOf {it.weightedMinutes}).toInt(),"WEEK_LOAD_X100" to halfUp(loads.sumOf {it.weightedMinutes}*100).toInt(),
            "BASE_TARGET_X100" to halfUp(baseTarget*100).toInt(),"GOAL_PERCENT" to adjustment,
            "TARGET" to halfUp(effectiveTarget).toInt(),"TARGET_X100" to halfUp(effectiveTarget*100).toInt(),"ADAPTIVE" to if(recommendation.adaptive) 1 else 0,
            "BASE_WEEKLY" to halfUp(recommendation.chronicWeekly).toInt(),"ACUTE_WEEKLY" to halfUp(recommendation.acuteWeekly).toInt(),"HISTORY_WEEKS" to recommendation.completeWeeks)
        recommendation.previousTarget?.let {parts["PREVIOUS_TARGET"]=halfUp(it).toInt()}
        recommendation.pastWeeks.take(4).forEachIndexed {index,value ->parts["PREV_WEEK_${index+1}"]=halfUp(value).toInt()}
        val sources = sourcesForWorkoutsByType(i, workouts)
        return ScoreResult(SESSION_LOAD_ID, halfUp(raw).toInt(), if (reasons.isNotEmpty()||!recommendation.adaptive) Status.provisional else Status.available,
            i.asOf, Coverage(1, 1, sources.values.flatten().toSet(), missingReasons = reasons,
                sourcesByType = sources, hrCoverage = combineHrCoverage(loads.map { it.coverage }), windowCompleteness = mapOf("W0" to true)),
            parts + profileComponents(i),
            rawIndex = raw, algorithmVersion = version)
    }

    /** Compare complete detailed stages to previous nights of the same source; broad published ranges are only a fallback. */
    private fun sleepComposition(main: SleepSession, i: NormalizedInput): Double? {
        fun fractions(night: SleepSession): Pair<Double, Double>? {
            if (stageMode(night) != StageMode.COMPLETE || invalidStages(night)) return null
            val detailed = night.stages.filter { it.type in setOf("LIGHT", "DEEP", "REM") }
            val total = detailed.sumOf { minutes(it.start, it.end) }
            if (total < 180 || total < sleepMinutes(night) * .9) return null
            return detailed.filter { it.type == "DEEP" }.sumOf { minutes(it.start, it.end) } / total to
                detailed.filter { it.type == "REM" }.sumOf { minutes(it.start, it.end) } / total
        }
        val current = fractions(main) ?: return null
        val prior = i.sleeps.filter { it.source == main.source && it.end < main.start && it.end >= main.start.minusSeconds(28 * DAY) }
            .groupBy { wakeDate(it, i.zone) }.values.mapNotNull { nights -> fractions(nights.maxBy { minutes(it.start, it.end) }) }
        fun fit(value: Double, low: Double, high: Double): Double = clamp(100 - max(max(low - value, value - high), 0.0) * 200)
        val deepRange = if (prior.size >= 7) median(prior.map { it.first }).let { max(0.0, it - .05) to min(1.0, it + .05) } else .10 to .25
        val remRange = if (prior.size >= 7) median(prior.map { it.second }).let { max(0.0, it - .05) to min(1.0, it + .05) } else .15 to .30
        return (fit(current.first, deepRange.first, deepRange.second) + fit(current.second, remRange.first, remRange.second)) / 2
    }

    /** Recorded sleep efficiency/disturbance proxy, not a continuous sleep-stress measurement. */
    private fun sleepEfficiencyComponent(s: SleepSession): Double? {
        if(stageMode(s)!=StageMode.COMPLETE||invalidStages(s)) return null
        val total=minutes(s.start,s.end)
        if(total<=0) return null
        val efficiency=sleepMinutes(s)/total
        var shortMinutes=0.0;var lastEnd=s.start
        s.shortAwakenings.filter {it.end>it.start&&it.start>=s.start&&it.end<=s.end}.sortedBy {it.start}.forEach {segment ->
            val begin=maxOf(lastEnd,segment.start)
            if(segment.end>begin) shortMinutes += s.stages.filter {it.type in SLEEP_TYPES}.sumOf {stage ->
                val a=maxOf(begin,stage.start);val b=minOf(segment.end,stage.end)
                if(b>a) minutes(a,b) else 0.0
            }
            lastEnd=maxOf(lastEnd,segment.end)
        }
        return clamp((efficiency-.70)/.30*100) * (1-min(.40,2*shortMinutes/total))
    }

    fun effectiveSleepGoal(i: NormalizedInput): Double = max(
        i.sleepGoalMinutes.takeIf {it.isFinite() && it > 0} ?: 480.0,
        i.personalProfile.sleepFloor(i.asOf.atZone(i.zone).toLocalDate()))

    private fun profileComponents(i: NormalizedInput): Map<String,Int> {
        val date = i.asOf.atZone(i.zone).toLocalDate()
        return buildMap {
            put("PROFILE_KEY", i.personalProfile.signature)
            put("SLEEP_GOAL_MIN", halfUp(effectiveSleepGoal(i)).toInt())
            i.personalProfile.maximumAt(date)?.let { put("MAX_HR", halfUp(it).toInt()) }
            put("MAX_HR_ESTIMATED", if(i.personalProfile.measuredMaxHeartRate == null && i.personalProfile.maximumAt(date) != null) 1 else 0)
        }
    }

    /** Observed deficits only; absent nights never become zero hours of sleep. */
    private fun recentSleepDebt(i: NormalizedInput, main: SleepSession): Double {
        val day = wakeDate(main,i.zone)
        val prior = i.sleeps.filter { it.source == main.source && it.isMainSleep != false &&
            wakeDate(it,i.zone) in day.minusDays(3)..day.minusDays(1) && it.end < main.start &&
            !invalidStages(it) && stageMode(it) == StageMode.COMPLETE }
            .groupBy {wakeDate(it,i.zone)}.values.map { nights -> nights.maxBy {minutes(it.start,it.end)} }
        return min(12.0, prior.sumOf { max(0.0,effectiveSleepGoal(i)-sleepMinutes(it))/60.0 * 2.0 })
    }

    private enum class StageMode { COMPLETE, PARTIAL, MISSING }
    fun selectedMainSleep(i: NormalizedInput): SleepSession? = mainSleep(i).let {if(it.second) null else it.first}

    private fun mainSleep(i: NormalizedInput): Pair<SleepSession?, Boolean> {
        val b = i.boundSources["sleep"]
        val today=localDate(i.asOf,i.zone)
        val xs = i.sleeps.filter { (b == null || it.source == b) && it.isMainSleep != false &&
            it.end <= i.asOf && wakeDate(it,i.zone)==today && minutes(it.start, it.end) >= 180 }
            .distinctBy { listOf(it.start, it.end, it.source, it.startZoneOffset, it.endZoneOffset, it.stages) }
        if (b == null && xs.map { it.source }.toSet().size > 1) return null to true
        if (xs.any { invalidStages(it) } || overlap(xs)) return null to true
        return xs.maxWithOrNull(compareBy<SleepSession> { minutes(it.start, it.end) }.thenBy { it.end }) to false
    }
    private fun metricComponent(s: SleepSession, i: NormalizedInput, higher: Boolean, key: String): Double? {
        if (permissionMissing(i, key)) return null
        val x = recoveryMetricValues(i,s,key).map {it.value}
        val b = recoveryBaselineValues(i,s,key)
        if (x.isEmpty() || b.size < 7) return null
        // Personal median is 50, not 100. MAD limits outlier influence; minimum
        // dispersion avoids magnifying small measurement noise into extreme scores.
        val reference=if(higher) b.map(::ln) else b
        val center=if(higher) ln(median(b)) else median(b)
        val mad=median(reference.map {abs(it-center)})*1.4826
        val scale=max(mad,if(higher) .10 else 3.0)
        val value=if(higher) ln(median(x)) else median(x)
        return clamp(50.0 + (if(higher) 25.0 else -25.0)*(value-center)/scale)
    }
    /** Shared by calculations and explanations so displayed comparisons use identical samples. */
    fun recoveryMetricValues(i: NormalizedInput, sleep: SleepSession, key: String): List<MetricSample> {
        if(permissionMissing(i,key)) return emptyList()
        val values=if(key=="hrv") i.hrv else i.rhr
        val wake=wakeDate(sleep,i.zone)
        val relevant=values.filter {it.value.isFinite() && it.value>0 && it.at<=i.asOf &&
            (it.at in sleep.start..sleep.end || (it.dailyDate ?: localDate(it.at,i.zone))==wake)}
        val source=i.boundSources[key] ?: relevant.map {it.source}.distinct().singleOrNull() ?: return emptyList()
        return metricValuesForSleep(i,sleep,values,source)
    }
    fun recoveryBaselineValues(i: NormalizedInput, sleep: SleepSession, key: String): List<Double> {
        val source=i.boundSources[key] ?: recoveryMetricValues(i,sleep,key).map {it.source}.distinct().singleOrNull() ?: return emptyList()
        val wake=wakeDate(sleep,i.zone)
        val days=if(key=="hrv") i.hrvBaseline else i.rhrBaseline
        return days.filter {it.source==source && it.value.isFinite() && it.value>0 && it.date in wake.minusDays(28)..wake.minusDays(1)}
            .groupBy {it.date}.values.map {median(it.map {day->day.value})}.sorted()
    }
    private fun metricValuesForSleep(i: NormalizedInput, sleep: SleepSession, values: List<MetricSample>, source: String): List<MetricSample> {
        val selected = values.filter { it.source == source && it.value.isFinite() && it.value > 0 && it.at <= i.asOf }
        val overnight = selected.filter { it.dailyDate == null && it.at in sleep.start..sleep.end }
        // Some watch exporters store one daily statistic with a daytime timestamp.
        return overnight.ifEmpty { selected.filter { (it.dailyDate ?: localDate(it.at, i.zone)) == wakeDate(sleep, i.zone) } }
    }
    private fun usesDailyValue(i: NormalizedInput, sleep: SleepSession, key: String): Boolean {
        val values = if (key == "hrv") i.hrv else i.rhr
        val source = i.boundSources[key] ?: values.filter {it.at<=i.asOf && (it.at in sleep.start..sleep.end || (it.dailyDate ?: localDate(it.at,i.zone))==wakeDate(sleep,i.zone))}.map { it.source }.distinct().singleOrNull() ?: return false
        return values.none { it.dailyDate == null && it.source == source && it.at in sleep.start..sleep.end && it.at <= i.asOf && it.value > 0 } &&
            metricValuesForSleep(i, sleep, values, source).isNotEmpty()
    }
    private fun metricMissingDetails(i: NormalizedInput, sleep: SleepSession, key: String): List<String> {
        if (permissionMissing(i, key)) return listOf("${key}_permission_missing")
        val values = if (key == "hrv") i.hrv else i.rhr
        val days = if (key == "hrv") i.hrvBaseline else i.rhrBaseline
        val source = i.boundSources[key] ?: values.map { it.source }.distinct().singleOrNull()
            ?: return listOf("${key}_source_unresolved")
        val wake = wakeDate(sleep, i.zone)
        val count = days.filter { it.source == source && it.value.isFinite() && it.value > 0 && it.date in wake.minusDays(28)..wake.minusDays(1) }.map { it.date }.distinct().size
        return buildList {
            if (metricValuesForSleep(i, sleep, values, source).isEmpty()) add("${key}_current_missing")
            if (count < 7) add("${key}_baseline_days:$count")
        }
    }
    private fun sleepMetricConflict(i: NormalizedInput, sleep: SleepSession): Boolean {
        val wake = wakeDate(sleep, i.zone)
        val lowerBaselineDate = wake.minusDays(28)
        val upperBaselineDate = wake.minusDays(1)
        val positiveHrv = i.hrv.filter { it.value.isFinite() && it.value > 0.0 }
        val positiveRhr = i.rhr.filter { it.value.isFinite() && it.value > 0.0 }
        val hrvInSleep = positiveHrv.filter { it.at <= i.asOf && (it.at in sleep.start..sleep.end || (it.dailyDate ?: localDate(it.at, i.zone)) == wake) }
        val rhrInSleep = positiveRhr.filter { it.at <= i.asOf && (it.at in sleep.start..sleep.end || (it.dailyDate ?: localDate(it.at, i.zone)) == wake) }
        val hrvDays = i.hrvBaseline.filter { it.value.isFinite() && it.value > 0.0 && it.date in lowerBaselineDate..upperBaselineDate }
        val rhrDays = i.rhrBaseline.filter { it.value.isFinite() && it.value > 0.0 && it.date in lowerBaselineDate..upperBaselineDate }
        val hrvRelevant = positiveHrv.filter { it.at <= i.asOf && (it.at in sleep.start..sleep.end || localDate(it.at, i.zone) in lowerBaselineDate..wake) }
        val rhrRelevant = positiveRhr.filter { it.at <= i.asOf && (it.at in sleep.start..sleep.end || localDate(it.at, i.zone) in lowerBaselineDate..wake) }
        return metricConflict(i, "hrv", hrvInSleep, hrvDays, hrvRelevant) || metricConflict(i, "rhr", rhrInSleep, rhrDays, rhrRelevant)
    }
    private fun metricConflict(i: NormalizedInput, key: String, current: List<MetricSample>, baseline: List<BaselineDay>, relevant: List<MetricSample>): Boolean {
        val bound = i.boundSources[key]
        val selectedSource=bound ?: current.map {it.source}.distinct().singleOrNull()
        val considered = if (selectedSource == null) relevant else relevant.filter { it.source == selectedSource }
        if (hasSameTimestampValueConflict(considered)) return true
        if (bound != null) {
            // Preserve the R1 priority rule: a foreign current RHR cannot become a
            // partial score when the selected RHR source has no current value.
            return key == "rhr" && current.any { it.source != bound } && current.none { it.source == bound }
        }
        // An old exporter outside today's chosen metric source must not invalidate today's value.
        return current.map { it.source }.toSet().size > 1
    }
    private data class DoseComputation(val dose: Double?, val coverage: HrCoverage)
    private fun workoutDose(w: Workout, i: NormalizedInput): DoseComputation {
        val source = i.boundSources["heart_rate"] ?: w.source
        val candidates = w.heartRate.filter { it.source == source && it.value.isFinite() && it.value in 20.0..400.0 && it.at >= w.start && it.at < w.end }
        val uniqueSampleCount = candidates.map { it.at }.toSet().size
        val samples = dedupe(candidates) ?: return DoseComputation(null, HrCoverage(0, max(0, Duration.between(w.start, w.end).seconds), uniqueSampleCount))
        val total = Duration.between(w.start, w.end).seconds
        if (total <= 0) return DoseComputation(null, HrCoverage(uniqueSamples = samples.size))
        var covered = 0L; var invalid = false
        samples.zipWithNext().forEach { (a, b) ->
            val sec = Duration.between(a.at, b.at).seconds
            if (sec > 120) invalid = true
            if (sec <= 60) covered += sec
        }
        val coverage = HrCoverage(covered, total, samples.size)
        if (i.personalProfile.pulseLimited || dosePermissionReasons(i).isNotEmpty()) return DoseComputation(null, coverage)
        val r = workoutBaseline(i, w)
        val maximum = i.personalProfile.maximumAt(w.start.atZone(i.zone).toLocalDate())
        if(r!=null && maximum!=null && maximum<=r+20) return DoseComputation(null, coverage)
        val enoughSamples = samples.size >= 10 && Duration.between(samples.firstOrNull()?.at ?: w.start, samples.lastOrNull()?.at ?: w.start).seconds >= 300
        val dose = if (r != null && enoughSamples && !invalid && covered.toDouble() / total >= .70) {
            samples.zipWithNext().sumOf { (a, b) ->
                val sec = Duration.between(a.at, b.at).seconds
                if (sec <= 60) {
                    fun intensity(hr: Double): Double {
                        if (maximum == null || maximum <= r + 20) return max(0.0, hr - r)
                        val reserve = ((hr - r) / (maximum - r)).coerceIn(0.0, 1.0)
                        // ROCO units: 50% HR reserve = 1 unit/min, 100% = 4.
                        // Smooth nonlinear weighting, not proprietary WHOOP strain or clinical TRIMP.
                        return 40.0 * 4.0 * reserve * reserve
                    }
                    (intensity(a.value) + intensity(b.value)) / 2 * sec / 60.0
                } else 0.0
            }
        } else null
        return DoseComputation(dose, coverage)
    }
    private fun workoutBaseline(i: NormalizedInput, w: Workout): Double? {
        val bound = i.boundSources["rhr"]
        val source = bound ?: w.source; val day = w.localStartDate ?: localDate(w.start, i.zone)
        val x = i.rhrBaseline.filter { it.source == source && it.value.isFinite() && it.value > 0 && it.date >= day.minusDays(7) && it.date < day }.groupBy { it.date }.values.map { median(it.map { q -> q.value }) }
        return x.takeIf { it.size >= 3 }?.let(::median)
    }
    private fun sourceConflictForWorkouts(i: NormalizedInput, ws: List<Workout>): Boolean {
        // Without a contributing workout no pulse or training source can block the score.
        if (ws.isEmpty()) return false
        val ex = ws.map { it.source }.toSet(); val be = i.boundSources["exercise"]
        if ((be == null && ex.size > 1) || (be != null && ex.any { it != be })) return true
        // Selected origins belong to record types, not to a single vendor. A workout
        // from Strava may use pulse and resting pulse from a Pixel/Fitbit watch.
        if (listOf("exercise", "heart_rate", "rhr").all { i.boundSources[it] != null }) {
            // Only each contributing workout's seven-day baseline is relevant here.
            // Unrelated older raw pulse records must not invalidate today's battery.
            val usedBaselineSamples = i.rhr.filter { sample ->
                boundOk(i, "rhr", sample.source) && sample.at <= i.asOf && ws.any { workout ->
                    val day = workout.localStartDate ?: localDate(workout.start, i.zone)
                    localDate(sample.at, i.zone) in day.minusDays(7)..day.minusDays(1)
                }
            }
            if (hasSameTimestampValueConflict(usedBaselineSamples)) return true
            return ws.any { workout ->
                hasSameTimestampValueConflict(workout.heartRate.filter {
                    boundOk(i, "heart_rate", it.source) && it.at >= workout.start && it.at < workout.end
                })
            }
        }
        if (ws.any { w -> i.boundSources["rhr"]?.let { it != w.source } == true }) return true
        val currentRhr = currentRhrForWorkoutCheck(i)
        if (hasSameTimestampValueConflict(currentRhr)) return true
        val currentRhrSources = currentRhr.map { it.source }.toSet()
        if (ws.any { workout -> currentRhrSources.any { it != workout.source } }) return true
        if (ws.any { w ->
                val day = w.localStartDate ?: localDate(w.start, i.zone)
                val baselineSources = i.rhrBaseline.filter { it.value > 0 && it.date in day.minusDays(7)..day.minusDays(1) }.map { it.source }.toSet()
                val baselineSamples = i.rhr.filter {
                    it.value.isFinite() && it.value > 0.0 && localDate(it.at, i.zone) in day.minusDays(7)..day.minusDays(1)
                }
                baselineSources.any { it != w.source } || hasSameTimestampValueConflict(baselineSamples)
            }) return true
        val hr = ws.flatMap { w -> w.heartRate.filter { it.at >= w.start && it.at < w.end } }.map { it.source }.toSet(); val bh = i.boundSources["heart_rate"]
        if (bh != null && ws.any { it.source != bh }) return true
        if ((bh == null && hr.size > 1) || (bh != null && hr.any { it != bh })) return true
        return ws.any { w ->
            w.heartRate.any { it.at >= w.start && it.at < w.end && it.source != w.source } ||
                hasSameTimestampValueConflict(w.heartRate.filter { it.at >= w.start && it.at < w.end })
        }
    }
    private fun dedupeIdenticalWorkouts(workouts: List<Workout>): List<Workout> =
        workouts.distinctBy(::workoutSignature)

    private fun hasWorkoutOverlapConflict(workouts: List<Workout>): Boolean {
        val sorted = workouts.sortedBy { it.start }
        for (leftIndex in sorted.indices) {
            val left = sorted[leftIndex]
            for (rightIndex in leftIndex + 1 until sorted.size) {
                val right = sorted[rightIndex]
                if (!right.start.isBefore(left.end)) break
                if (left.start.isBefore(right.end) && right.start.isBefore(left.end)) return true
            }
        }
        return false
    }

    private fun workoutSignature(workout: Workout) = listOf(
        workout.start,
        workout.end,
        workout.source,
        workout.startZoneOffset,
        workout.endZoneOffset,
        workout.heartRate.sortedBy { it.at }.map { listOf(it.at, it.value, it.source, it.zoneOffset, it.recordStartZoneOffset, it.recordEndZoneOffset) },
    )
    private fun dosePermissionReasons(i: NormalizedInput): List<String> = buildList {
        if (permissionMissing(i, "heart_rate")) add("heart_rate_permission_missing")
        if (permissionMissing(i, "rhr")) add("rhr_permission_missing")
    }
    private fun sleepMinutes(s: SleepSession): Double = when (stageMode(s)) { StageMode.MISSING -> minutes(s.start, s.end); else -> s.stages.filter { it.type in SLEEP_TYPES }.sumOf { minutes(it.start, it.end) } }
    private fun stageMode(s: SleepSession): StageMode {
        if (s.stages.isEmpty() || s.stages.none { it.type in KNOWN_STAGE_TYPES }) return StageMode.MISSING
        val classifiedSeconds = s.stages.filter { it.type in KNOWN_STAGE_TYPES }.sumOf { Duration.between(it.start, it.end).toMillis() }
        return if (classifiedSeconds >= Duration.between(s.start, s.end).toMillis()) StageMode.COMPLETE else StageMode.PARTIAL
    }
    private fun invalidStages(s: SleepSession) = s.stages.any { it.start < s.start || it.end > s.end || it.end <= it.start } || s.stages.indices.any { x -> (x + 1 until s.stages.size).any { y -> s.stages[x].start < s.stages[y].end && s.stages[y].start < s.stages[x].end } }
    private fun overlap(x: List<SleepSession>) = x.indices.any { a -> (a + 1 until x.size).any { b -> x[a].start < x[b].end && x[b].start < x[a].end } }
    private fun wakeDate(s: SleepSession, z: ZoneId) = s.localWakeDate ?: localDate(s.end, z)
    private fun midpoint(s: SleepSession, z: ZoneId) = s.localMidpointMinute?.let { (halfUp(it).toInt() % 1440).toDouble() }
        ?: localMinute(s.start.plusNanos(Duration.between(s.start, s.end).toNanos() / 2), z)
    private fun circularDistance(a: Double, b: Double) = min(abs(a - b), 1440 - abs(a - b))
    private fun circularMedian(x: List<Double>) = x.map { it % 1440 }.minWithOrNull(compareBy<Double> { c -> x.sumOf { circularDistance(c, it) } }.thenBy { it }) ?: 0.0
    private fun median(x: List<Double>): Double { val s = x.sorted(); return if (s.isEmpty()) 0.0 else if (s.size % 2 == 1) s[s.size / 2] else s[s.size / 2 - 1] / 2 + s[s.size / 2] / 2 }
    private fun minutes(a: Instant, b: Instant) = Duration.between(a, b).toMillis() / 60_000.0
    private fun localDate(x: Instant, z: ZoneId) = x.atZone(z).toLocalDate()
    private fun localMinute(x: Instant, z: ZoneId): Double { val q = x.atZone(z); return ((q.hour * 60 + q.minute + if (q.second * 1000 + q.nano / 1_000_000 >= 30_000) 1 else 0) % 1440).toDouble() }
    private fun decay(end: Instant, now: Instant) = max(0.0, 1 - max(0.0, Duration.between(end, now).toMillis() / 3_600_000.0) / 12)
    private fun dedupe(xs: List<MetricSample>): List<MetricSample>? = xs.groupBy { it.at }.map { (_, v) -> if (v.map { it.value }.distinct().size > 1) null else v.first() }.let { if (it.any { x -> x == null }) null else it.filterNotNull().sortedBy { it.at } }
    private fun hasSameTimestampValueConflict(xs: List<MetricSample>): Boolean =
        xs.groupBy { it.source to it.at }.values.any { samples -> samples.map { it.value }.distinct().size > 1 }
    private fun permissionMissing(i: NormalizedInput, key: String) = i.permissions[key] == PermissionState.missing || i.permissions[key] == PermissionState.unavailable
    private fun optionalReason(i: NormalizedInput, key: String, fallback: String) = if (permissionMissing(i, key)) "${key}_permission_missing" else fallback
    private fun boundOk(i: NormalizedInput, key: String, source: String) = i.boundSources[key] == null || i.boundSources[key] == source
    private fun sourcesForSleep(i: NormalizedInput, main: SleepSession) = sourcesForSleepByType(i, main).values.flatten().toSet()
    private fun sourcesForSleepByType(i: NormalizedInput, main: SleepSession): Map<RecordType, Set<String>> {
        val wake = wakeDate(main, i.zone)
        val lower = wake.minusDays(28)
        val upper = wake.minusDays(1)
        val sleepSources = setOf(main.source) + i.priorNights.filter { it.date in lower..upper && boundOk(i, "sleep", it.source) }.map { it.source }
        val hrvSources = i.hrv.filter { boundOk(i, "hrv", it.source) && (it.at in main.start..main.end || localDate(it.at, i.zone) in lower..upper) }.map { it.source } +
            i.hrvBaseline.filter { boundOk(i, "hrv", it.source) && it.date in lower..upper }.map { it.source }
        val rhrSources = i.rhr.filter { boundOk(i, "rhr", it.source) && (it.at in main.start..main.end || localDate(it.at, i.zone) in lower..upper) }.map { it.source } +
            i.rhrBaseline.filter { boundOk(i, "rhr", it.source) && it.date in lower..upper }.map { it.source }
        return mapOf(
            RecordType.SLEEP_SESSION to sleepSources,
            RecordType.HRV_RMSSD to hrvSources.toSet(),
            RecordType.RESTING_HEART_RATE to rhrSources.toSet(),
        ).filterValues { it.isNotEmpty() }
    }
    private fun sourcesForBatteryByType(i: NormalizedInput, main: SleepSession, workouts: List<Workout>, steps: List<Steps>): Map<RecordType, Set<String>> {
        val sources = sourcesForSleepByType(i, main).toMutableMap()
        if (workouts.isNotEmpty()) {
            sources[RecordType.EXERCISE_SESSION] = workouts.map { it.source }.toSet()
            val heartRateSources = workouts.flatMap { it.heartRate }.filter { boundOk(i, "heart_rate", it.source) }.map { it.source }.toSet()
            if (heartRateSources.isNotEmpty()) sources[RecordType.HEART_RATE] = heartRateSources
            val rhrSources = workouts.flatMap { workout ->
                val date = workout.localStartDate ?: localDate(workout.start, i.zone)
                i.rhrBaseline.filter { it.date in date.minusDays(7)..date.minusDays(1) && boundOk(i, "rhr", it.source) }.map { it.source }
            }.toSet()
            if (rhrSources.isNotEmpty()) sources[RecordType.RESTING_HEART_RATE] = sources[RecordType.RESTING_HEART_RATE].orEmpty() + rhrSources
        }
        if (steps.isNotEmpty()) sources[RecordType.STEPS] = steps.map { it.source }.toSet()
        return sources.filterValues { it.isNotEmpty() }
    }
    private fun sourcesForWorkoutsByType(i: NormalizedInput, workouts: List<Workout>): Map<RecordType, Set<String>> {
        val exerciseSources = workouts.map { it.source }.toSet()
        val heartRateSources = workouts.flatMap { it.heartRate }.filter { boundOk(i, "heart_rate", it.source) }.map { it.source }.toSet()
        val baselineSources = workouts.flatMap { workout ->
            val date = workout.localStartDate ?: localDate(workout.start, i.zone)
            i.rhrBaseline.filter { it.date in date.minusDays(7)..date.minusDays(1) && boundOk(i, "rhr", it.source) }.map { it.source }
        }.toSet()
        val currentRhrSources = currentRhrForWorkoutCheck(i).filter { boundOk(i, "rhr", it.source) }.map { it.source }.toSet()
        return mapOf(
            RecordType.EXERCISE_SESSION to exerciseSources,
            RecordType.HEART_RATE to heartRateSources,
            RecordType.RESTING_HEART_RATE to (baselineSources + currentRhrSources),
        ).filterValues { it.isNotEmpty() }
    }
    private fun sessionLoadWindowCompleteness(i: NormalizedInput): Map<String, Boolean> = (0..3).associate { index ->
        val key = "W$index"
        val defaultComplete = if (index == 0) i.readComplete else i.historyComplete
        val requiredRecordTypes = listOf(RecordType.EXERCISE_SESSION, RecordType.HEART_RATE, RecordType.RESTING_HEART_RATE)
        val typedComplete = if (i.recordTypeWindowCompleteness.isEmpty()) null else
            requiredRecordTypes.all { recordType -> i.recordTypeWindowCompleteness[recordType]?.get(key) == true }
        key to (!permissionMissing(i, "exercise") && (typedComplete ?: (i.windowCompleteness[key] ?: defaultComplete)))
    }
    private fun hasStaleMainSleep(i: NormalizedInput): Boolean {
        val bound = i.boundSources["sleep"]
        return i.sleeps.any { sleep ->
            (bound == null || sleep.source == bound) && sleep.end.isBefore(i.asOf.minusSeconds(DAY)) &&
                !sleep.end.isAfter(i.asOf) && minutes(sleep.start, sleep.end) >= 180
        }
    }
    private fun currentRhrForWorkoutCheck(i: NormalizedInput): List<MetricSample> {
        val main = mainSleep(i).first
        val start = main?.start ?: i.asOf.minusSeconds(DAY)
        val end = main?.end ?: i.asOf
        return i.rhr.filter { it.value.isFinite() && it.value > 0.0 && it.at in start..end && it.at <= i.asOf }
    }
    private fun combineHrCoverage(coverages: List<HrCoverage>) = HrCoverage(
        coveredSeconds = coverages.sumOf { it.coveredSeconds },
        totalSeconds = coverages.sumOf { it.totalSeconds },
        uniqueSamples = coverages.sumOf { it.uniqueSamples },
    )
    private fun List<DoseComputation>.sumDose() = sumOf { it.dose!! }
    private fun bad(
        id: String,
        n: Instant,
        e: Int,
        s: Status,
        r: String,
        windowCompleteness: Map<String, Boolean> = emptyMap(),
        sourcesByType: Map<RecordType, Set<String>> = emptyMap(),
        hrCoverage: HrCoverage = HrCoverage(),
        hrCoverageByWindow: Map<String, HrCoverage> = emptyMap(),
    ) = ok(id, null, s, n, e, emptySet(), listOf(r), sourcesByType = sourcesByType,
        windowCompleteness = windowCompleteness, hrCoverage = hrCoverage, hrCoverageByWindow = hrCoverageByWindow)
    private fun ok(
        id: String,
        score: Int?,
        s: Status,
        n: Instant,
        e: Int,
        src: Set<String>,
        reasons: List<String>,
        c: Map<String, Int> = emptyMap(),
        hr: Long = 0,
        raw: Double? = null,
        rating: Int? = null,
        sourcesByType: Map<RecordType, Set<String>> = emptyMap(),
        hrCoverage: HrCoverage = HrCoverage(),
        windowCompleteness: Map<String, Boolean> = emptyMap(),
        hrCoverageByWindow: Map<String, HrCoverage> = emptyMap(),
    ): ScoreResult {
        val typedSources = sourcesByType.filterValues { it.isNotEmpty() }
        val allSources = typedSources.values.flatten().toSet().ifEmpty { src }
        val available = when(id) { SESSION_LOAD_ID -> windowCompleteness.count { it.value }; BODY_BATTERY_ID -> c.keys.count { it in setOf("D","H","P") }; SLEEP_ID -> c.keys.count {it in setOf("D","E","R","Q","H","P")}; else -> c.size }
        return ScoreResult(
            id, score, s, n,
            Coverage(available, e, allSources, hr, reasons, typedSources, hrCoverage, windowCompleteness, hrCoverageByWindow),
            c, raw, rating, algorithmVersion = MODEL_VERSION + "/" + id,
        )
    }
    private val SLEEP_TYPES = setOf("SLEEPING", "LIGHT", "DEEP", "REM")
    private val KNOWN_STAGE_TYPES = SLEEP_TYPES + setOf("AWAKE", "AWAKE_IN_BED", "OUT_OF_BED")
}
