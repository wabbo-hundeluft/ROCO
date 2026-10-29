package com.wabbo.healthconnectcoach

import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine as E
import com.wabbo.healthconnectcoach.score.PersonalScoreProfile as P
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class PersonalizedScoresTest {
    private val e=E()
    private val date=LocalDate.of(2026,10,27)
    private val wake=Instant.parse("2026-10-27T07:00:00Z")
    private fun night(hours: Long=8, end: Instant=wake): E.SleepSession {
        val start=end.minusSeconds(hours*3600)
        return E.SleepSession(start,end,"watch",end.toString(),listOf(
            E.SleepStage(start,start.plusSeconds(hours*1800),"LIGHT"),
            E.SleepStage(start.plusSeconds(hours*1800),start.plusSeconds(hours*2700),"DEEP"),
            E.SleepStage(start.plusSeconds(hours*2700),end,"REM")))
    }
    private fun input(profile: P=P(), hours: Long=8): E.NormalizedInput = E.NormalizedInput(
        wake.plusSeconds(4*3600),ZoneOffset.UTC,sleeps=listOf(night(hours)),
        priorNights=(1L..14).map {E.SleepNight(date.minusDays(it),180.0,"watch")},
        hrv=listOf(E.MetricSample(wake.minusSeconds(60),50.0,"watch")),
        rhr=listOf(E.MetricSample(wake.minusSeconds(60),60.0,"watch")),
        hrvBaseline=(1L..28).map {E.BaselineDay(date.minusDays(it),50.0,"watch")},
        rhrBaseline=(1L..28).map {E.BaselineDay(date.minusDays(it),60.0,"watch")},
        boundSources=listOf("sleep","hrv","rhr","exercise","heart_rate","steps").associateWith {"watch"},personalProfile=profile)
    private fun workout(pulse: Double=130.0): E.Workout {
        val start=wake.plusSeconds(3600)
        return E.Workout(start,start.plusSeconds(3600),"watch","w",(0..119).map {E.MetricSample(start.plusSeconds(it*30L),pulse,"watch")})
    }
    @Test fun missingProfileDoesNotInventAgeOrMaximum() {assertNull(P().maximumAt(date));assertEquals(420.0,P().sleepFloor(date),0.0)}
    @Test fun ageEstimateAndMeasuredOverrideAreExplicit() {
        assertEquals(187.0,P(1996).maximumAt(date)!!,0.001)
        assertEquals(195.0,P(1996,195).maximumAt(date)!!,0.0)
        assertNull(P(1996,195,true).maximumAt(date))
        assertNull(P(2011).maximumAt(date))
    }
    @Test fun historicalAgeUsesHistoricalDate() {assertEquals(190.5,P(1996).maximumAt(date.minusYears(5))!!,0.001)}
    @Test fun invalidAgeAndMaximumRejected() {
        listOf(P(2020),P(1800),P(measuredMaxHeartRate=20),P(measuredMaxHeartRate=300)).forEach {assertTrue(runCatching {it.validated(date)}.isFailure)}
    }
    @Test fun samePulseHasGreaterRelativeIntensityWithLowerMaximum() {
        val younger=e.activityLoad(workout(),input(P(2006))).weightedMinutes
        val older=e.activityLoad(workout(),input(P(1966))).weightedMinutes
        assertTrue(older>younger)
    }
    @Test fun intenseMinutesCountMoreThanLinearPulseIncrease() {
        val i=input(P(measuredMaxHeartRate=200))
        val moderate=e.activityLoad(workout(130.0),i).weightedMinutes
        val hard=e.activityLoad(workout(165.0),i).weightedMinutes
        assertEquals(2.25,hard/moderate,0.001)
    }
    @Test fun pulseLimitedAlwaysUsesDuration() {
        val load=e.activityLoad(workout(),input(P(1996,195,true)))
        assertTrue(load.fallback);assertEquals(60.0,load.weightedMinutes,0.0)
    }
    @Test fun missingPulseNeverBecomesHighIntensity() {
        val load=e.activityLoad(workout().copy(heartRate=emptyList()),input(P(1996)))
        assertTrue(load.fallback);assertEquals(60.0,load.weightedMinutes,0.0)
    }
    @Test fun weeklyPointsUseSamePersonalizedDoseAndCanExceed100() {
        val i=input(P(1996)).copy(workouts=listOf(workout()))
        val load=e.activityLoad(workout(),i).weightedMinutes
        val score=e.scoreWeeklyLoad(i,load/2)
        assertEquals(200,score.score);assertEquals(1,score.components["MAX_HR_ESTIMATED"])
        assertTrue(score.coverage.missingReasons.contains("max_hr_age_estimated"))
    }
    @Test fun energyDrainUsesSameDoseAsWeeklyLoad() {
        val i=input(P(1996)).copy(workouts=listOf(workout()))
        assertEquals(E.halfUp(e.activityLoad(workout(),i).weightedMinutes*.15).toInt(),e.scoreBodyBattery(i).components["TRAINING"])
    }
    @Test fun lowSleepGoalCannotMakeSixHoursPerfect() {
        val score=e.scoreSleep(input(hours=6).copy(sleepGoalMinutes=360.0))
        assertEquals(86,score.components["D"])
        assertEquals(420,score.components["SLEEP_GOAL_MIN"])
        assertEquals(480.0,e.effectiveSleepGoal(input(P(2011)).copy(sleepGoalMinutes=360.0)),0.0)
    }
    @Test fun veryShortNightCannotBeCompensatedByExcellentHrv() {
        val i=input(hours=3).copy(hrv=listOf(E.MetricSample(wake.minusSeconds(60),100.0,"watch")))
        assertTrue(e.scoreSleep(i).score!!<=53)
        assertTrue(e.scoreBodyBattery(i).components.getValue("START")<=53)
    }
    @Test fun observedPriorDeficitsReduceEnergyButMissingNightsDoNot() {
        val base=input()
        val poor=base.copy(sleeps=base.sleeps+(1L..3).map {night(4,wake.minusSeconds(it*86400))})
        assertEquals(12,e.scoreBodyBattery(poor).components["SLEEP_DEBT"])
        assertEquals(0,e.scoreBodyBattery(base).components["SLEEP_DEBT"])
        assertTrue(e.scoreBodyBattery(poor).score!!<e.scoreBodyBattery(base).score!!)
    }
    @Test fun futureAndOtherSourceNightsDoNotChangeDebt() {
        val base=input()
        val changed=base.copy(sleeps=base.sleeps+night(4,wake.plusSeconds(86400))+night(4,wake.minusSeconds(86400)).copy(source="other"))
        assertEquals(e.scoreBodyBattery(base).score,e.scoreBodyBattery(changed).score)
    }
    @Test fun profileNeverAppliesAnAgePenaltyToPersonalHrvBaseline() {
        assertEquals(e.scoreSleep(input(P(2006))).components["H"],e.scoreSleep(input(P(1966))).components["H"])
    }
    @Test fun invalidBaselineValuesAreIgnored() {
        val i=input();val baseline=i.hrvBaseline+E.BaselineDay(date.minusDays(2),Double.POSITIVE_INFINITY,"watch")
        assertEquals(e.scoreSleep(i).score,e.scoreSleep(i.copy(hrvBaseline=baseline)).score)
    }
    @Test fun pastWorkoutIsNotDeductedAgainFromObservedMorningRecovery() {
        val base=input()
        val past=workout().copy(start=wake.minusSeconds(24*3600),end=wake.minusSeconds(23*3600),heartRate=emptyList())
        assertEquals(e.scoreBodyBattery(base).score,e.scoreBodyBattery(base.copy(workouts=listOf(past))).score)
    }
    @Test fun alreadyClassifiedWakeIsNotPenalizedTwiceByShortAwakening() {
        val base=input();val s=base.sleeps.single();val stop=s.start.plusSeconds(600)
        val awake=E.SleepStage(s.start,stop,"AWAKE")
        val classified=s.copy(stages=listOf(awake)+s.stages.mapIndexed {idx,stage ->if(idx==0) stage.copy(start=stop) else stage})
        assertEquals(e.scoreSleep(base.copy(sleeps=listOf(classified))).score,
            e.scoreSleep(base.copy(sleeps=listOf(classified.copy(shortAwakenings=listOf(awake))))).score)
    }
    @Test fun minuteResolutionPulseCanContributeInsteadOfAlwaysFallingBack() {
        val w=workout().let {it.copy(heartRate=it.heartRate.filterIndexed {index,_->index%2==0})}
        assertFalse(e.activityLoad(w,input(P(1996))).fallback)
    }
    @Test fun missingPulsePermissionPreventsPersonalizedPulseWeighting() {
        val i=input(P(1996)).copy(permissions=mapOf("heart_rate" to E.PermissionState.missing))
        assertTrue(e.activityLoad(workout(),i).fallback)
    }
    @Test fun implausibleMaximumRelativeToRestingPulseUsesDuration() {
        val i=input(P(measuredMaxHeartRate=100)).let {it.copy(rhrBaseline=it.rhrBaseline.map {d->d.copy(value=90.0)})}
        assertTrue(e.activityLoad(workout(),i).fallback)
    }
    @Test fun corruptPulseCannotCrashOrGenerateExtremeDose() {
        val w=workout().let {it.copy(heartRate=it.heartRate.map {p->p.copy(value=Double.MAX_VALUE)})}
        val load=e.activityLoad(w,input());assertTrue(load.fallback);assertEquals(60.0,load.weightedMinutes,0.0)
    }
    @Test fun lowIntensityWorkoutCannotCreateAnUpwardJumpInEnergyCurve() {
        val start=wake.plusSeconds(55*60)
        val w=E.Workout(start,start.plusSeconds(10*60),"watch","low",(0..19).map {E.MetricSample(start.plusSeconds(it*30L),60.0,"watch")})
        val i=input(P(1996)).copy(asOf=w.end,workouts=listOf(w))
        val curve=e.batteryTimeline(i)
        assertTrue(curve.zipWithNext().all {it.second.second<=it.first.second})
        assertEquals(e.scoreBodyBattery(i).score,curve.last().second)
    }
    @Test fun displayedBaselineUsesExactlyTheSameMedianAsScore() {
        val i=input().copy(hrvBaseline=(1L..8).map {E.BaselineDay(date.minusDays(it),if(it<=4) 40.0 else 60.0,"watch")})
        val read=ApprovedWellnessScoreReadout(mapOf(E.SLEEP_ID to e.scoreSleep(i)),emptyMap(),i,emptyList())
        assertEquals(50.0,dayInsight(read).metrics["hrv_baseline"]!!,0.0)
        assertEquals(50,e.scoreSleep(i).components["H"])
    }
    @Test fun historicalReconstructionRetainsPersonalProfile() {
        val i=input(P(1996)).let {it.copy(sleeps=it.sleeps+night(end=wake.minusSeconds(86400)))}
        val read=ApprovedWellnessScoreReadout(emptyMap(),emptyMap(),i,emptyList())
        val past=historicalScoreReadout(read,wake.minusSeconds(86000),emptyMap(),true)
        assertEquals(i.personalProfile,past.normalizedInput.personalProfile)
        assertEquals(i.personalProfile.signature,past.scores[E.SLEEP_ID]?.components?.get("PROFILE_KEY"))
    }
    @Test fun scoreTrendsDoNotMixChangedPersonalProfiles() {
        val a=input(P(1996));val b=input(P(1966))
        fun day(i:E.NormalizedInput)=dayInsight(ApprovedWellnessScoreReadout(mapOf(E.SLEEP_ID to e.scoreSleep(i)),emptyMap(),i,emptyList())).let {it.copy(sources=it.sources+("_connector" to "test"))}
        val old=day(a).copy(date=date.minusDays(1));val current=day(b)
        assertEquals(listOf(current),comparableTrendDays(listOf(old,current),"test",date,scoreId=E.SLEEP_ID))
        assertEquals(listOf(current),comparableSleepHistory(listOf(old,current),current))
    }
    @Test fun weeklyAdjustmentAppliesOnceInSharedPipelineAndHistoricalReconstruction() {
        val week=date.with(DayOfWeek.MONDAY).toString()
        val goals=TrainingGoals(weeklyLoadAdjustments=mapOf(week to 125))
        val i=input(P(1996)).copy(workouts=listOf(workout()))
        val base=ApprovedWellnessScoreReadout(emptyMap(),emptyMap(),i,emptyList())
        val read=base.withV2Goals(goals)
        val without=base.withV2Goals(goals.copy(weeklyLoadAdjustments=emptyMap()))
        val score=read.scores.getValue(E.SESSION_LOAD_ID)
        assertEquals(without.scores.getValue(E.SESSION_LOAD_ID).rawIndex!!/1.25,score.rawIndex!!,.001)
        val reconstructed=historicalScoreReadout(read,read.normalizedInput.asOf,emptyMap(),true)
        assertEquals(score.score,reconstructed.scores[E.SESSION_LOAD_ID]?.score)
        assertEquals(125,reconstructed.scores[E.SESSION_LOAD_ID]?.components?.get("GOAL_PERCENT"))
    }
    @Test fun currentVitalsRemainVisibleWithoutMainSleepButDoNotInventEnergy() {
        val i=input().copy(sleeps=emptyList())
        val read=ApprovedWellnessScoreReadout(mapOf(E.BODY_BATTERY_ID to e.scoreBodyBattery(i)),emptyMap(),i,emptyList())
        assertEquals(50.0,dayInsight(read).metrics["hrv"]!!,0.0)
        assertNull(read.scores[E.BODY_BATTERY_ID]?.score)
    }
}
