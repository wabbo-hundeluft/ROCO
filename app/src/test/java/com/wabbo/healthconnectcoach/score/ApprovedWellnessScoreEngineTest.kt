package com.wabbo.healthconnectcoach.score

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovedWellnessScoreEngineTest {
    private data class NamedVector(val id: String, val testName: String)
    private val vectorRegistryVersion = "hc-score-v1-block-c.1"
    private val namedVectors = listOf(
        NamedVector("S1", "s1Full94Coverage4of4"), NamedVector("S2", "s2PartialExact70"),
        NamedVector("S3", "s3StagesMissingFallback"), NamedVector("S4", "s4CorrectedSeparateMetricSources"),
        NamedVector("S5", "s5BoundSourceMissingDoesNotSwitch"), NamedVector("S6", "s6SleepOverlapConflict"),
        NamedVector("S7", "s7RegularityCircularAndBoundary"), NamedVector("S8", "s8BaselineSixOrZeroMissingAndCap"),
        NamedVector("B1", "b1StartAndDAlone"), NamedVector("B2", "b2DynamicHrAndDecay"),
        NamedVector("B3", "b3StepsDecayAndProvisional"), NamedVector("B4", "b4DurationFallbackButConflictNeverFallback"),
        NamedVector("B5", "b5HrExactThresholdsAndGaps"), NamedVector("B6", "b6HalfOpenStepIntervals"),
        NamedVector("B7", "b7NoSleepBasis"), NamedVector("B8", "bodyBatteryKeepsMeasuredSessionsWhenOneNeedsDurationFallback"),
        NamedVector("L1", "l1RatingsAndRawIndex"), NamedVector("L2", "l2RealHrDose"),
        NamedVector("L3", "l3GlobalDurationFallback"), NamedVector("L4", "l4MissingOrZeroReference"),
        NamedVector("L5", "l5ZeroCurrentPositiveReferenceIsProvisional"), NamedVector("L6", "l6TrainingSourceConflictBeforeFallback"),
        NamedVector("R1", "r1ConflictPriorityOverIncompleteBaseline"),
    )
    private val e = ApprovedWellnessScoreEngine()
    private val now = Instant.parse("2025-03-15T11:00:00Z")
    private val zone = ZoneId.of("Europe/Berlin")
    private val day: LocalDate get() = now.atZone(zone).toLocalDate()
    private fun sl(src:String="watch-A", staged:Boolean=true) = ApprovedWellnessScoreEngine.SleepSession(now.minus(8,ChronoUnit.HOURS),now.minus(1,ChronoUnit.HOURS),src,"sleep-1",if(staged) listOf(ApprovedWellnessScoreEngine.SleepStage(now.minus(8,ChronoUnit.HOURS),now.minus(1,ChronoUnit.HOURS),"SLEEPING")) else emptyList())
    private fun nights(src:String="watch-A", n:Int=5, minute:Double=450.0)=(1..n).map{ApprovedWellnessScoreEngine.SleepNight(day.minusDays(it.toLong()),minute,src)}
    private fun metric(v:Double,src:String="watch-A")=listOf(ApprovedWellnessScoreEngine.MetricSample(now.minus(4,ChronoUnit.HOURS),v,src))
    private fun baseline(v:Double,src:String="watch-A",n:Int=7)=(1..n).map{ApprovedWellnessScoreEngine.BaselineDay(day.minusDays(it.toLong()),v,src)}
    private fun base(hrv:Double=50.0,rhr:Double=55.0,src:String="watch-A")=ApprovedWellnessScoreEngine.NormalizedInput(now,zone,listOf(sl(src)),nights(src),metric(hrv,src),metric(rhr,src),baseline(50.0,src),baseline(55.0,src),boundSources=mapOf("sleep" to src,"hrv" to src,"rhr" to src))
    private fun workout(start:Instant=now.minus(30,ChronoUnit.MINUTES),end:Instant=now,src:String="watch-A",samples:Int=61,step:Long=30):ApprovedWellnessScoreEngine.Workout {
        val hr=(0 until samples).map{ApprovedWellnessScoreEngine.MetricSample(start.plusSeconds(it*step),120.0,src,"hr-$it")}
        return ApprovedWellnessScoreEngine.Workout(start,end,src,"w-${start.epochSecond}",hr)
    }
    private fun doseWorkout(dose: Double, start: Instant, src: String = "watch-A"): ApprovedWellnessScoreEngine.Workout {
        val end = start.plusSeconds(600)
        val bpm = 55.0 + dose / 10.0
        val hr = (0..20).map { ApprovedWellnessScoreEngine.MetricSample(start.plusSeconds(it * 30L), bpm, src, "dose-${start.epochSecond}-$it") }
        return ApprovedWellnessScoreEngine.Workout(start, end, src, "dose-${start.epochSecond}", hr)
    }
    private fun doseInput(current: Double, prior: List<Double>): ApprovedWellnessScoreEngine.NormalizedInput {
        val starts = listOf(1L, 8L, 15L, 22L).map { now.minus(it, ChronoUnit.DAYS) }
        val ws = listOf(current) + prior
        val baselines = starts.flatMap { start -> (1..7).map { d -> ApprovedWellnessScoreEngine.BaselineDay(start.atZone(zone).toLocalDate().minusDays(d.toLong()), 55.0, "watch-A") } }
        return base().copy(workouts = ws.mapIndexed { ix, dose -> doseWorkout(dose, starts[ix]) }, rhrBaseline = baselines)
    }
    private fun fourWindowInput(
        foreignWorkoutWindow: Int? = null,
        foreignHrWindow: Int? = null,
        foreignBaselineWindow: Int? = null,
        foreignCurrentRhr: Boolean = false,
        omitHr: Boolean = false,
    ): ApprovedWellnessScoreEngine.NormalizedInput {
        val starts = listOf(1L, 8L, 15L, 22L).map { now.minus(it, ChronoUnit.DAYS) }
        val sessions = starts.mapIndexed { index, start ->
            val source = if (index == foreignWorkoutWindow) "watch-B" else "watch-A"
            val hrSource = if (index == foreignHrWindow) "watch-B" else "watch-A"
            val session = doseWorkout(1000.0, start).copy(id = "window-$index", source = source)
            session.copy(heartRate = if (omitHr) emptyList() else session.heartRate.map { it.copy(source = hrSource) })
        }
        val baselines = starts.flatMapIndexed { index, start ->
            val source = if (index == foreignBaselineWindow) "watch-B" else "watch-A"
            (1..7).map { daysAgo -> ApprovedWellnessScoreEngine.BaselineDay(start.atZone(zone).toLocalDate().minusDays(daysAgo.toLong()), 55.0, source) }
        }
        return base().copy(
            rhr = metric(55.0, if (foreignCurrentRhr) "watch-B" else "watch-A"),
            workouts = sessions,
            rhrBaseline = baselines,
            boundSources = base().boundSources + mapOf("exercise" to "watch-A", "heart_rate" to "watch-A", "rhr" to "watch-A"),
        )
    }
    private fun assertStatus(r:ApprovedWellnessScoreEngine.ScoreResult,s:ApprovedWellnessScoreEngine.Status)=assertEquals(s,r.status)

    @Test fun namedVectorRegistryHas22PoCasesPlusMaggusB8(){
        assertEquals("hc-score-v1-block-c.1", vectorRegistryVersion)
        assertEquals(23, namedVectors.size)
        assertEquals(23, namedVectors.map { it.id }.toSet().size)
        assertEquals(22, namedVectors.count { it.id != "B8" })
        assertEquals("bodyBatteryKeepsMeasuredSessionsWhenOneNeedsDurationFallback", namedVectors.single { it.id == "B8" }.testName)
    }

    @Test fun dailyExportedHrvAndRestingPulseAreUsableAndExplicitlyProvisional() {
        val daily=base().copy(hrv=listOf(ApprovedWellnessScoreEngine.MetricSample(now,50.0,"watch-A")),
            rhr=listOf(ApprovedWellnessScoreEngine.MetricSample(now,55.0,"watch-A")))
        val score=e.scoreSleep(daily)
        assertEquals(79,score.score)
        assertStatus(score,ApprovedWellnessScoreEngine.Status.provisional)
        assertTrue("hrv_daily_value_used" in score.coverage.missingReasons)
        assertTrue("rhr_daily_value_used" in score.coverage.missingReasons)
        assertTrue(e.scoreBodyBattery(daily).score!=null)
    }
    @Test fun sourceFromOlderNightsDoesNotBlockUnambiguousCurrentSleep() {
        val input=base().copy(boundSources=base().boundSources-mapOf("sleep" to "watch-A").keys,
            sleeps=base().sleeps+sl("old-watch").copy(start=sl().start.minusSeconds(3*86400),end=sl().end.minusSeconds(3*86400),stages=emptyList()))
        assertTrue(e.scoreSleep(input).score!=null)
    }
    @Test fun presentCurrentMetricsDoNotInventMissingBaselineDays() {
        val input=base().copy(hrvBaseline=baseline(50.0).take(2),rhrBaseline=baseline(55.0).take(3))
        val score=e.scoreSleep(input)
        assertTrue("hrv_baseline_days:2" in score.coverage.missingReasons)
        assertTrue("rhr_baseline_days:3" in score.coverage.missingReasons)
    }

    @Test fun s1Full94Coverage4of4(){val r=e.scoreSleep(base());assertEquals(79,r.score);assertStatus(r,ApprovedWellnessScoreEngine.Status.available);assertEquals(5,r.coverage.available);assertEquals(6,r.coverage.expected)}
    @Test fun s2PartialExact70(){val r=e.scoreSleep(base(hrv=40.0).copy(sleepGoalMinutes=560.0,priorNights=nights(n=5,minute=510.0),rhr=emptyList()));assertEquals(59,r.score);assertStatus(r,ApprovedWellnessScoreEngine.Status.provisional);assertEquals(4,r.coverage.available);assertTrue("RHR_missing" in r.coverage.missingReasons)}
    @Test fun s3StagesMissingFallback(){val r=e.scoreSleep(base().copy(sleepGoalMinutes=420.0,sleeps=listOf(sl(staged=false))));assertEquals(79,r.score);assertStatus(r,ApprovedWellnessScoreEngine.Status.provisional);assertTrue("sleep_stages_missing" in r.coverage.missingReasons)}
    @Test fun sleepPartialStagesWithUnknownGapsNeverBecomeAvailable(){
        val sleep = sl()
        val split = sleep.start.plusSeconds(4 * 60 * 60L)
        val partial = sleep.copy(stages = listOf(
            ApprovedWellnessScoreEngine.SleepStage(sleep.start, split, "SLEEPING"),
            ApprovedWellnessScoreEngine.SleepStage(split, sleep.end, "UNKNOWN"),
        ))
        val result = e.scoreSleep(base().copy(sleeps = listOf(partial)))
        assertEquals(57, result.score)
        assertStatus(result, ApprovedWellnessScoreEngine.Status.provisional)
        assertTrue("sleep_stages_partial" in result.coverage.missingReasons)
        assertEquals(50, result.components["D"])
    }
    @Test fun sleepOnlyUnknownStagesUseTheMissingStagesUpperBoundFallback(){
        val sleep = sl().copy(stages = listOf(ApprovedWellnessScoreEngine.SleepStage(sl().start, sl().end, "UNKNOWN")))
        val result = e.scoreSleep(base().copy(sleepGoalMinutes = 420.0, sleeps = listOf(sleep)))
        assertEquals(79, result.score)
        assertStatus(result, ApprovedWellnessScoreEngine.Status.provisional)
        assertTrue("sleep_stages_missing" in result.coverage.missingReasons)
    }
    @Test fun s4CorrectedSeparateMetricSources(){
        val r=e.scoreSleep(base().copy(hrv=metric(50.0,"origin-B"),rhr=metric(55.0,"origin-C"),hrvBaseline=baseline(50.0,"origin-B"),rhrBaseline=baseline(55.0,"origin-C"),boundSources=mapOf("sleep" to "watch-A","hrv" to "origin-B","rhr" to "origin-C")))
        assertEquals(79,r.score); assertStatus(r,ApprovedWellnessScoreEngine.Status.available)
        assertEquals(setOf("watch-A","origin-B","origin-C"),r.coverage.sources)
        assertEquals(setOf("watch-A"), r.coverage.sourcesByType[ApprovedWellnessScoreEngine.RecordType.SLEEP_SESSION])
        assertEquals(setOf("origin-B"), r.coverage.sourcesByType[ApprovedWellnessScoreEngine.RecordType.HRV_RMSSD])
        assertEquals(setOf("origin-C"), r.coverage.sourcesByType[ApprovedWellnessScoreEngine.RecordType.RESTING_HEART_RATE])
    }
    @Test fun contradictorySameTimestampCurrentSleepMetricsAreSourceConflicts(){
        val hrv = metric(50.0) + metric(70.0)
        val rhr = metric(55.0) + metric(65.0)
        listOf(base().copy(hrv = hrv), base().copy(rhr = rhr)).forEach { input ->
            val result = e.scoreSleep(input)
            assertNull(result.score); assertStatus(result, ApprovedWellnessScoreEngine.Status.source_conflict)
        }
    }
    @Test fun contradictorySameTimestampBaselineSamplesConflictBeforeMedian(){
        val at = day.minusDays(1).atTime(3, 0).atZone(zone).toInstant()
        val samples = listOf(
            ApprovedWellnessScoreEngine.MetricSample(at, 50.0, "watch-A", "baseline-a"),
            ApprovedWellnessScoreEngine.MetricSample(at, 80.0, "watch-A", "baseline-b"),
        )
        val result = e.scoreSleep(base().copy(hrv = metric(50.0) + samples))
        assertNull(result.score); assertStatus(result, ApprovedWellnessScoreEngine.Status.source_conflict)
    }
    @Test fun differentSleepMetricTimestampsRemainValidForMedian(){
        val at = now.minus(5, ChronoUnit.HOURS)
        val result = e.scoreSleep(base().copy(hrv = metric(40.0) + listOf(ApprovedWellnessScoreEngine.MetricSample(at, 60.0, "watch-A"))))
        assertEquals(79, result.score); assertStatus(result, ApprovedWellnessScoreEngine.Status.available)
    }
    @Test fun s5BoundSourceMissingDoesNotSwitch(){val r=e.scoreSleep(base().copy(sleepGoalMinutes=560.0,priorNights=nights(n=5,minute=510.0),hrv=metric(50.0,"origin-B"),hrvBaseline=baseline(50.0,"origin-B"),boundSources=mapOf("sleep" to "watch-A","hrv" to "watch-A","rhr" to "watch-A")));assertEquals(75,r.score);assertStatus(r,ApprovedWellnessScoreEngine.Status.provisional);assertTrue(r.coverage.missingReasons.any{"HRV_missing"==it})}
    @Test fun s6SleepOverlapConflict(){val a=sl();val r=e.scoreSleep(base().copy(sleeps=listOf(a,a.copy(id="sleep-2",start=a.start.plusSeconds(3600)))));assertNull(r.score);assertStatus(r,ApprovedWellnessScoreEngine.Status.source_conflict)}
    @Test fun s7RegularityCircularAndBoundary(){
        assertEquals(100, e.scoreSleep(base().copy(priorNights = nights(n = 5, minute = 450.0))).components["R"])
        assertEquals(0, e.scoreSleep(base().copy(priorNights = nights(n = 5, minute = 570.0))).components["R"])
        assertEquals(0, e.scoreSleep(base().copy(priorNights = nights(n = 5, minute = 571.0))).components["R"])
        val fourNights = e.scoreSleep(base().copy(priorNights = nights(n = 4, minute = 450.0)))
        assertNull(fourNights.components["R"]); assertEquals(4, fourNights.coverage.available)
    }
    @Test fun s8BaselineSixOrZeroMissingAndCap(){
        assertEquals(0, e.scoreSleep(base(hrv = 30.0)).components["H"])
        assertEquals(0, e.scoreSleep(base(hrv = 10.0)).components["H"])
        assertNull(e.scoreSleep(base().copy(hrvBaseline = baseline(50.0, n = 6))).components["H"])
        assertNull(e.scoreSleep(base().copy(hrvBaseline = baseline(0.0))).components["H"])
        assertEquals(0, e.scoreSleep(base().copy(rhr = metric(77.0), rhrBaseline = baseline(70.0))).components["P"])
    }

    @Test fun b1StartAndDAlone(){assertEquals(57,e.scoreBodyBattery(base()).score);val r=e.scoreBodyBattery(base().copy(hrv=emptyList(),rhr=emptyList()));assertNull(r.score);assertStatus(r,ApprovedWellnessScoreEngine.Status.insufficient_data);assertEquals(1,r.coverage.available);assertEquals(3,r.coverage.expected)}
    @Test fun b2TrainingDoesNotRefundEnergyWithPassingHours(){
        val input=base().copy(rhr=metric(60.0),rhrBaseline=baseline(60.0),workouts=listOf(workout()))
        val current=e.scoreBodyBattery(input)
        val six=e.scoreBodyBattery(input.copy(asOf=now.plus(6,ChronoUnit.HOURS)))
        val twelve=e.scoreBodyBattery(input.copy(asOf=now.plus(10,ChronoUnit.HOURS)))
        assertTrue(current.score!!>six.score!!);assertTrue(six.score!!>twelve.score!!)
        assertEquals(current.components["TRAINING"],twelve.components["TRAINING"])
    }
    @Test fun b3StepsDecayAndProvisional(){
        val s = ApprovedWellnessScoreEngine.Steps(now.minus(1, ChronoUnit.HOURS), now, 5000.0, "watch-A")
        val r = e.scoreBodyBattery(base().copy(steps = listOf(s)))
        assertEquals(54, r.score); assertStatus(r, ApprovedWellnessScoreEngine.Status.provisional)
        val nonPositive = ApprovedWellnessScoreEngine.Steps(now.minus(1, ChronoUnit.HOURS), now, -5000.0, "watch-A")
        assertEquals(57, e.scoreBodyBattery(base().copy(steps = listOf(nonPositive))).score)
    }
    @Test fun b4DurationFallbackButConflictNeverFallback(){val w=workout().copy(heartRate=emptyList());val r=e.scoreBodyBattery(base().copy(workouts=listOf(w)));assertEquals(53,r.score);assertTrue("duration_fallback" in r.coverage.missingReasons);val conflict=e.scoreBodyBattery(base().copy(workouts=listOf(w.copy(source="origin-B")),boundSources=mapOf("sleep" to "watch-A","hrv" to "watch-A","rhr" to "watch-A","exercise" to "watch-A")));assertNull(conflict.score);assertStatus(conflict,ApprovedWellnessScoreEngine.Status.source_conflict)}
    @Test fun bodyBatteryKeepsMeasuredSessionsWhenOneNeedsDurationFallback(){
        val validDose = workout(now.minus(60, ChronoUnit.MINUTES), now.minus(30, ChronoUnit.MINUTES))
        val insufficientDose = workout(now.minus(30, ChronoUnit.MINUTES), now).copy(heartRate = emptyList())
        val input = base().copy(
            workouts = listOf(validDose, insufficientDose),
            boundSources = base().boundSources + mapOf("exercise" to "watch-A", "heart_rate" to "watch-A"),
        )
        val result = e.scoreBodyBattery(input)
        // Per-session fallback keeps the measured pulse dose of the other workout.
        assertEquals(45, result.score); assertEquals(1770L, result.coverage.hrSeconds)
        assertEquals(1770L, result.coverage.hrCoverage.coveredSeconds)
        assertEquals(3600L, result.coverage.hrCoverage.totalSeconds)
        assertEquals(60, result.coverage.hrCoverage.uniqueSamples)
        assertEquals(88, result.components["D"])
        assertEquals(50, result.components["H"])
        assertEquals(50, result.components["P"])
        assertTrue("duration_fallback" in result.coverage.missingReasons); assertStatus(result,ApprovedWellnessScoreEngine.Status.provisional)
    }
    @Test fun b5HrExactThresholdsAndGaps(){
        fun trace(times: List<Long>, duration: Long = 600L): ApprovedWellnessScoreEngine.Workout {
            val start = now.minusSeconds(duration)
            return ApprovedWellnessScoreEngine.Workout(start, start.plusSeconds(duration), "watch-A", "threshold", times.mapIndexed { ix, t -> ApprovedWellnessScoreEngine.MetricSample(start.plusSeconds(t), 120.0, "watch-A", "t-$ix") })
        }
        val sleepEnd = now.minus(2, ChronoUnit.HOURS)
        val sleepStart = sleepEnd.minus(7, ChronoUnit.HOURS)
        val night = sl().copy(start = sleepStart, end = sleepEnd, stages = listOf(ApprovedWellnessScoreEngine.SleepStage(sleepStart, sleepEnd, "SLEEPING")))
        fun score(times: List<Long>, duration: Long = 600L) = e.scoreBodyBattery(base().copy(sleeps = listOf(night), workouts = listOf(trace(times, duration))))
        val exact70 = score((0L..420L step 30L) + 540L)
        assertEquals(420L, exact70.coverage.hrSeconds)
        assertEquals(420L, exact70.coverage.hrCoverage.coveredSeconds)
        assertEquals(600L, exact70.coverage.hrCoverage.totalSeconds)
        assertEquals(16, exact70.coverage.hrCoverage.uniqueSamples)
        assertEquals(.70, exact70.coverage.hrCoverage.intervalCoverage!!, .0001)
        assertStatus(exact70, ApprovedWellnessScoreEngine.Status.available)
        val below70Times = (0L..4170L step 30L) + 4194L
        val below70 = score(below70Times, 6000L)
        assertEquals(0L, below70.coverage.hrSeconds)
        assertTrue("duration_fallback" in below70.coverage.missingReasons)
        assertStatus(below70, ApprovedWellnessScoreEngine.Status.provisional)
        val exact30 = score((0L..600L step 30L).toList(), 600L)
        assertEquals(570L, exact30.coverage.hrSeconds)
        assertEquals(570L, exact30.coverage.hrCoverage.coveredSeconds)
        assertEquals(20, exact30.coverage.hrCoverage.uniqueSamples)
        val gap31 = score((0L..240L step 30L) + (271L..541L step 30L))
        assertEquals(541L, gap31.coverage.hrSeconds) // Minute-resolution integration includes this 31-second interval.
        val gap120Times = (0L..240L step 30L) + (360L..540L step 30L)
        assertEquals(420L, score(gap120Times).coverage.hrSeconds)
        val gap121Times = (0L..240L step 30L) + (361L..541L step 30L)
        val gap121 = score(gap121Times)
        assertEquals(0L, gap121.coverage.hrSeconds)
        assertStatus(gap121, ApprovedWellnessScoreEngine.Status.provisional)
        assertStatus(score(listOf(0L, 30L, 60L, 90L, 120L, 150L, 180L, 210L, 300L), 300L), ApprovedWellnessScoreEngine.Status.provisional)
        assertStatus(score((0L..300L step 30L).toList(), 330L), ApprovedWellnessScoreEngine.Status.available)
        assertStatus(score((0L..240L step 30L) + 299L, 299L), ApprovedWellnessScoreEngine.Status.provisional)
    }
    @Test fun b6HalfOpenStepIntervals(){val w=workout();val touching=ApprovedWellnessScoreEngine.Steps(w.end,w.end.plusSeconds(60),5000.0,"watch-A");val overlap=ApprovedWellnessScoreEngine.Steps(w.start.plusSeconds(1),w.end.plusSeconds(1),5000.0,"watch-A");assertTrue(e.scoreBodyBattery(base().copy(workouts=listOf(w),steps=listOf(touching))).score!!<94);assertEquals(e.scoreBodyBattery(base().copy(workouts=listOf(w),steps=listOf(overlap))).score,e.scoreBodyBattery(base().copy(workouts=listOf(w))).score)}
    @Test fun b7NoSleepBasis(){val r=e.scoreBodyBattery(base().copy(sleeps=emptyList()));assertNull(r.score);assertStatus(r,ApprovedWellnessScoreEngine.Status.insufficient_data)}
    @Test fun contradictorySameTimestampWorkoutHrIsConflictNotDurationFallback(){
        val workout = workout()
        val conflictingSample = workout.heartRate.first().copy(value = 130.0, id = "same-time-different-value")
        val input = base().copy(workouts = listOf(workout.copy(heartRate = workout.heartRate + conflictingSample)))
        val battery = e.scoreBodyBattery(input)
        assertNull(battery.score); assertStatus(battery, ApprovedWellnessScoreEngine.Status.source_conflict)
        assertTrue("duration_fallback" !in battery.coverage.missingReasons)
        val load = e.scoreSessionLoad(input)
        assertNull(load.score); assertStatus(load, ApprovedWellnessScoreEngine.Status.source_conflict)
    }
    @Test fun identicalSameTimestampWorkoutHrDuplicatesDeduplicate(){
        val workout = workout()
        val duplicate = workout.heartRate.first().copy(id = "identical-duplicate")
        val original = e.scoreBodyBattery(base().copy(workouts = listOf(workout)))
        val repeated = e.scoreBodyBattery(base().copy(workouts = listOf(workout.copy(heartRate = workout.heartRate + duplicate))))
        assertEquals(original.score, repeated.score)
        assertEquals(60, repeated.coverage.hrCoverage.uniqueSamples)
    }
    @Test fun foreignCurrentRhrConflictsBeforeWorkoutDurationFallback(){
        val workout = workout().copy(heartRate = emptyList())
        val input = base().copy(
            rhr = metric(55.0, "watch-B"),
            workouts = listOf(workout),
            boundSources = base().boundSources + mapOf("exercise" to "watch-A", "heart_rate" to "watch-A"),
        )
        val result = e.scoreBodyBattery(input)
        assertNull(result.score); assertStatus(result, ApprovedWellnessScoreEngine.Status.source_conflict)
        assertTrue("duration_fallback" !in result.coverage.missingReasons)
    }
    @Test fun contradictorySameTimestampRhrBaselineSamplesConflictBeforeWorkoutFallback(){
        val at = now.minus(2, ChronoUnit.DAYS).atZone(zone).withHour(3).withMinute(0).withSecond(0).withNano(0).toInstant()
        val rawBaselineConflict = listOf(
            ApprovedWellnessScoreEngine.MetricSample(at, 55.0, "watch-A", "rhr-baseline-a"),
            ApprovedWellnessScoreEngine.MetricSample(at, 70.0, "watch-A", "rhr-baseline-b"),
        )
        val input = fourWindowInput(omitHr = true).let { it.copy(rhr = it.rhr + rawBaselineConflict) }
        val result = e.scoreSessionLoad(input)
        assertNull(result.score); assertStatus(result, ApprovedWellnessScoreEngine.Status.source_conflict)
        assertTrue("duration_fallback" !in result.coverage.missingReasons)
    }

    @Test fun l1RatingsAndRawIndex(){
        listOf(500.0 to 50.0, 1000.0 to 100.0, 1200.0 to 120.0, 2000.0 to 200.0, 3000.0 to 300.0).forEach { (dose, raw) ->
            val r = e.scoreSessionLoad(doseInput(dose, listOf(1000.0, 1000.0, 1000.0)))
            assertEquals(raw, r.rawIndex!!, 0.01)
            assertEquals(if (raw <= 100.0) 100 else if (raw == 120.0) 90 else if (raw == 200.0) 50 else 0, r.rating0to100)
            assertStatus(r, ApprovedWellnessScoreEngine.Status.available)
        }
    }
    @Test fun l2RealHrDose(){val r=e.scoreSessionLoad(doseInput(1200.0, listOf(1000.0, 1000.0, 1000.0)));assertEquals(120.0,r.rawIndex!!,0.01);assertEquals(ApprovedWellnessScoreEngine.MODEL_VERSION+"/session-load-v1",r.algorithmVersion)}
    @Test fun l3GlobalDurationFallback(){
        val starts = listOf(1L, 8L, 15L, 22L).map { now.minus(it, ChronoUnit.DAYS) }
        val durations = listOf(60L, 50L, 50L, 50L)
        val ws = starts.mapIndexed { ix, start ->
            ApprovedWellnessScoreEngine.Workout(start, start.plusSeconds(durations[ix] * 60), "watch-A", "duration-$ix")
        }
        val baselines = starts.flatMap { start -> (1..7).map { d -> ApprovedWellnessScoreEngine.BaselineDay(start.atZone(zone).toLocalDate().minusDays(d.toLong()), 55.0, "watch-A") } }
        val r = e.scoreSessionLoad(base().copy(workouts = ws, rhrBaseline = baselines))
        assertEquals(120.0, r.rawIndex!!, 0.01); assertEquals(90, r.rating0to100)
        assertTrue("duration_fallback" in r.coverage.missingReasons); assertStatus(r, ApprovedWellnessScoreEngine.Status.provisional)
    }
    @Test fun l4MissingOrZeroReference(){
        val zero = e.scoreSessionLoad(base())
        assertNull(zero.score); assertStatus(zero, ApprovedWellnessScoreEngine.Status.insufficient_data)
        val incomplete = e.scoreSessionLoad(base().copy(workouts = listOf(workout()), historyComplete = false))
        assertNull(incomplete.rawIndex); assertTrue("reference_window_incomplete" in incomplete.coverage.missingReasons)
    }
    @Test fun l5ZeroCurrentPositiveReferenceIsProvisional(){
        val starts = listOf(8L, 15L, 22L).map { now.minus(it, ChronoUnit.DAYS) }
        val prior = starts.mapIndexed { ix, start -> doseWorkout(1000.0, start).copy(id = "prior-$ix") }
        val baselines = starts.flatMap { start -> (1..7).map { d -> ApprovedWellnessScoreEngine.BaselineDay(start.atZone(zone).toLocalDate().minusDays(d.toLong()), 55.0, "watch-A") } }
        val r = e.scoreSessionLoad(base().copy(workouts = prior, rhrBaseline = baselines))
        assertEquals(0.0, r.rawIndex!!, 0.01); assertEquals(100, r.rating0to100)
        assertTrue("no_session_load" in r.coverage.missingReasons); assertTrue("duration_fallback" !in r.coverage.missingReasons)
        assertStatus(r, ApprovedWellnessScoreEngine.Status.provisional)
    }
    @Test fun l6TrainingSourceConflictBeforeFallback(){val w=workout();val r=e.scoreSessionLoad(base().copy(workouts=listOf(w,w.copy(id="w2",source="origin-B")),boundSources=emptyMap()));assertNull(r.score);assertStatus(r,ApprovedWellnessScoreEngine.Status.source_conflict)}
    @Test fun r1ConflictPriorityOverIncompleteBaseline(){
        val conflicting = base().copy(hrvBaseline = baseline(50.0, n = 6), rhr = metric(55.0, "origin-B"))
        val r = e.scoreSleep(conflicting)
        assertNull(r.score); assertStatus(r, ApprovedWellnessScoreEngine.Status.source_conflict)
        val incomplete = e.scoreSleep(conflicting.copy(readComplete = false))
        assertNull(incomplete.score); assertStatus(incomplete, ApprovedWellnessScoreEngine.Status.source_conflict)
        val battery = e.scoreBodyBattery(conflicting.copy(readComplete = false))
        assertNull(battery.score); assertStatus(battery, ApprovedWellnessScoreEngine.Status.source_conflict)
        val a = ApprovedWellnessScoreEngine.Workout(now.minus(60, ChronoUnit.MINUTES), now.minus(40, ChronoUnit.MINUTES), "watch-A", "a")
        val b = ApprovedWellnessScoreEngine.Workout(now.minus(30, ChronoUnit.MINUTES), now.minus(10, ChronoUnit.MINUTES), "origin-B", "b")
        val load = e.scoreSessionLoad(base().copy(workouts = listOf(a, b), readComplete = false))
        assertNull(load.score); assertStatus(load, ApprovedWellnessScoreEngine.Status.source_conflict)
    }
    @Test fun independentUnboundSameTypeWorkoutOriginConflict(){
        val a = workout()
        val b = workout(start=now.minus(2,ChronoUnit.HOURS),end=now.minus(90,ChronoUnit.MINUTES),src="origin-B")
        val r = e.scoreSessionLoad(base().copy(workouts=listOf(a,b),boundSources=emptyMap()))
        assertNull(r.score); assertStatus(r,ApprovedWellnessScoreEngine.Status.source_conflict)
    }
    @Test fun bodyBatteryStepsConflictAndBoundSelection(){
        val a = ApprovedWellnessScoreEngine.Steps(now.minus(1, ChronoUnit.HOURS), now, 1000.0, "watch-A")
        val b = a.copy(source = "watch-B", count = 9000.0)
        assertStatus(e.scoreBodyBattery(base().copy(steps = listOf(a, b), boundSources = emptyMap())), ApprovedWellnessScoreEngine.Status.source_conflict)
        val selected = e.scoreBodyBattery(base().copy(steps = listOf(a, b), boundSources = mapOf("steps" to "watch-A")))
        assertTrue(selected.coverage.sources.contains("watch-A")); assertTrue(!selected.coverage.sources.contains("watch-B")); assertEquals(56, selected.score)
    }
    @Test fun workoutRhrBaselineConflictPrecedesFallback(){
        val w = workout().copy(heartRate = emptyList())
        val foreign = baseline(55.0, "watch-B")
        val r = e.scoreBodyBattery(base().copy(workouts = listOf(w), rhrBaseline = foreign))
        assertStatus(r, ApprovedWellnessScoreEngine.Status.source_conflict); assertNull(r.score)
    }
    @Test fun dosePermissionsAreNotDurationFallback(){
        val w = workout().copy(heartRate = emptyList())
        val missing = base().copy(workouts = listOf(w), permissions = mapOf("heart_rate" to ApprovedWellnessScoreEngine.PermissionState.missing))
        val b = e.scoreBodyBattery(missing)
        assertStatus(b, ApprovedWellnessScoreEngine.Status.provisional); assertTrue("heart_rate_permission_missing" in b.coverage.missingReasons); assertTrue("duration_fallback" in b.coverage.missingReasons);assertEquals(0L,b.coverage.hrSeconds)
        val l = e.scoreSessionLoad(missing.copy(workouts = listOf(w, workout(now.minus(8, ChronoUnit.DAYS), now.minus(8, ChronoUnit.DAYS).plusSeconds(600)), workout(now.minus(15, ChronoUnit.DAYS), now.minus(15, ChronoUnit.DAYS).plusSeconds(600)), workout(now.minus(22, ChronoUnit.DAYS), now.minus(22, ChronoUnit.DAYS).plusSeconds(600)))))
        assertStatus(l, ApprovedWellnessScoreEngine.Status.permission_missing); assertTrue("heart_rate_permission_missing" in l.coverage.missingReasons)
    }
    @Test fun separatelyBoundHeartRateWithNoSamplesUsesExplicitDurationFallback(){
        val w = workout().copy(heartRate = emptyList())
        val input = base().copy(
            workouts = listOf(w),
            boundSources = mapOf("sleep" to "watch-A", "hrv" to "watch-A", "rhr" to "watch-A", "exercise" to "watch-A", "heart_rate" to "watch-B"),
        )
        val result = e.scoreBodyBattery(input)
        assertTrue(result.score != null); assertStatus(result, ApprovedWellnessScoreEngine.Status.provisional)
        assertTrue("duration_fallback" in result.coverage.missingReasons)
    }
    @Test fun nonIdenticalOverlappingWorkoutsConflictBeforeFallbackAndExactDuplicatesCountOnce(){
        val a = ApprovedWellnessScoreEngine.Workout(now.minus(60, ChronoUnit.MINUTES), now.minus(30, ChronoUnit.MINUTES), "watch-A", "overlap-a")
        val b = ApprovedWellnessScoreEngine.Workout(now.minus(45, ChronoUnit.MINUTES), now.minus(15, ChronoUnit.MINUTES), "watch-A", "overlap-b")
        val conflictInput = base().copy(workouts = listOf(a, b))
        val batteryConflict = e.scoreBodyBattery(conflictInput)
        assertNull(batteryConflict.score); assertStatus(batteryConflict, ApprovedWellnessScoreEngine.Status.source_conflict)
        val loadConflict = e.scoreSessionLoad(conflictInput)
        assertNull(loadConflict.score); assertStatus(loadConflict, ApprovedWellnessScoreEngine.Status.source_conflict)

        val one = e.scoreBodyBattery(base().copy(workouts = listOf(a)))
        val duplicate = e.scoreBodyBattery(base().copy(workouts = listOf(a, a.copy(id = "duplicate-id"))))
        assertEquals(one.score, duplicate.score)
    }
    @Test fun staleMainSleepReturnsNullWithExplicitStaleStatus(){
        val staleSleep = sl().copy(start = now.minus(32, ChronoUnit.HOURS), end = now.minus(25, ChronoUnit.HOURS))
        val input = base().copy(sleeps = listOf(staleSleep))
        listOf(e.scoreSleep(input), e.scoreBodyBattery(input)).forEach { result ->
            assertNull(result.score)
            assertStatus(result, ApprovedWellnessScoreEngine.Status.stale)
            assertTrue(result.stale)
            assertTrue("main_sleep_stale" in result.coverage.missingReasons)
        }
    }
    @Test fun sessionLoadCoverageNeverCountsIncompleteHistoryAsFourOfFour(){
        val historyMissing = e.scoreSessionLoad(base().copy(historyComplete = false))
        assertNull(historyMissing.rawIndex)
        assertEquals(1, historyMissing.coverage.available)
        assertEquals(4, historyMissing.coverage.expected)
        assertEquals(mapOf("W0" to true, "W1" to false, "W2" to false, "W3" to false), historyMissing.coverage.windowCompleteness)
        assertEquals(setOf("W0", "W1", "W2", "W3"), historyMissing.coverage.hrCoverageByWindow.keys)
        assertTrue("reference_window_incomplete" in historyMissing.coverage.missingReasons)

        val oneWindowMissing = e.scoreSessionLoad(base().copy(windowCompleteness = mapOf("W0" to true, "W1" to false, "W2" to true, "W3" to true)))
        assertNull(oneWindowMissing.rawIndex)
        assertEquals(3, oneWindowMissing.coverage.available)
        assertEquals(false, oneWindowMissing.coverage.windowCompleteness["W1"])
        assertEquals(0.0, oneWindowMissing.coverage.hrCoverageByWindow.getValue("W0").intervalCoverage ?: 0.0, 0.0)
    }
    @Test fun sessionLoadRequiresEachRecordTypeToCoverEachWindowIndependently(){
        val windows = (0..3).associate { "W$it" to true }
        val types = listOf(
            ApprovedWellnessScoreEngine.RecordType.EXERCISE_SESSION,
            ApprovedWellnessScoreEngine.RecordType.HEART_RATE,
            ApprovedWellnessScoreEngine.RecordType.RESTING_HEART_RATE,
        )
        val fullTypeCoverage = types.associateWith { windows }
        val fullInput = fourWindowInput().copy(
            readComplete = false,
            historyComplete = false,
            windowCompleteness = windows,
            recordTypeWindowCompleteness = fullTypeCoverage,
        )
        val complete = e.scoreSessionLoad(fullInput)
        assertEquals(100, complete.score)
        assertStatus(complete, ApprovedWellnessScoreEngine.Status.available)
        assertEquals(windows, complete.coverage.windowCompleteness)

        types.forEach { missingType ->
            val typeCoverage = fullTypeCoverage + (missingType to (windows + ("W2" to false)))
            val result = e.scoreSessionLoad(fullInput.copy(recordTypeWindowCompleteness = typeCoverage))
            assertNull("$missingType W2 must not be inferred complete", result.score)
            assertNull(result.rawIndex)
            assertEquals(mapOf("W0" to true, "W1" to true, "W2" to false, "W3" to true), result.coverage.windowCompleteness)
            assertEquals(3, result.coverage.available)
            assertTrue("reference_window_incomplete" in result.coverage.missingReasons)
        }
    }
    @Test fun selectedOriginsAreHonoredAcrossAllTrainingWindows() {
        for (window in 0..3) {
            val foreignWorkout = e.scoreSessionLoad(fourWindowInput(foreignWorkoutWindow = window))
            assertNull(foreignWorkout.score)
            assertStatus(foreignWorkout, ApprovedWellnessScoreEngine.Status.source_conflict)
            listOf(fourWindowInput(foreignHrWindow = window), fourWindowInput(foreignBaselineWindow = window, omitHr = true)).forEach { input ->
                val result = e.scoreSessionLoad(input)
                assertStatus(result, ApprovedWellnessScoreEngine.Status.provisional)
                assertTrue("duration_fallback" in result.coverage.missingReasons)
                assertEquals(100.0, result.rawIndex!!, 0.01)
            }
        }
    }
    @Test fun explicitCrossAppBindingsProduceTheSameLoadAsSingleAppData() {
        val original = fourWindowInput()
        val mixed = original.copy(
            workouts = original.workouts.map { w -> w.copy(source = "training-app", heartRate = w.heartRate.map { it.copy(source = "pixel-watch") }) },
            boundSources = original.boundSources + mapOf("exercise" to "training-app", "heart_rate" to "pixel-watch"),
        )
        assertEquals(e.scoreSessionLoad(original).rawIndex, e.scoreSessionLoad(mixed).rawIndex)
        assertStatus(e.scoreSessionLoad(mixed), ApprovedWellnessScoreEngine.Status.available)
        val conflicting = mixed.copy(workouts = mixed.workouts.mapIndexed { index, w ->
            if (index == 0) w.copy(heartRate = w.heartRate + w.heartRate.first().copy(value = 200.0)) else w
        })
        assertStatus(e.scoreSessionLoad(conflicting), ApprovedWellnessScoreEngine.Status.source_conflict)
    }
}
