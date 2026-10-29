package com.wabbo.healthconnectcoach

import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine as E
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class TrainingSeriesAndGuidanceTest {
    private val today=LocalDate.of(2026,10,26)
    private val now=today.atTime(12,0).toInstant(ZoneOffset.UTC)
    private val plan=TrainingPlan(TrainingGoals(),emptyList())
    @Test fun weekdaySeriesCreatesAllTenCorrectDatesAcrossTwoWeeks() {
        val next=scheduleTrainingSeries(plan,today,today.plusDays(11),(1..5).toSet(),"Fahrradfahren",30,1.5,true,today).first
        assertEquals(10,next.sessions.size);assertEquals(10,next.sessions.map {it.date}.distinct().size)
        assertTrue(next.sessions.all {it.date.dayOfWeek.value in 1..5&&it.title=="Fahrradfahren"})
        assertTrue(next.sessions.any {it.date==today.plusDays(11)})
    }
    @Test fun retryingTheSameSeriesIsIdempotentAndCompletedEntriesSurviveReplacement() {
        val done=PlannedSession("done",today,"Spaziergang",20,1.0,true)
        val first=scheduleTrainingSeries(plan.copy(sessions=listOf(done)),today,today.plusDays(4),(1..5).toSet(),"Rad",30,1.5,true,today).first
        val again=scheduleTrainingSeries(first,today,today.plusDays(4),(1..5).toSet(),"Rad",30,1.5,true,today).first
        assertEquals(first,again);assertEquals(done,again.sessions.single {it.id=="done"})
        assertEquals(5,again.sessions.count {!it.completed})
    }
    @Test fun invalidOrPastSeriesCannotMutateAPlan() {
        assertThrows(IllegalArgumentException::class.java) {scheduleTrainingSeries(plan,today.minusDays(1),today,setOf(1),"Rad",30,1.5,true,today)}
        assertThrows(IllegalArgumentException::class.java) {scheduleTrainingSeries(plan,today,today.plusDays(91),setOf(1),"Rad",30,1.5,true,today)}
        assertTrue(plan.sessions.isEmpty())
    }
    @Test fun consciousWeeklyAdjustmentChangesRatioNotMeasuredLoadAndSurvivesStorage() {
        val base=E.ScoreResult(E.SESSION_LOAD_ID,50,E.Status.available,now,E.Coverage(1,1),components=mapOf("TARGET_X100" to 22000,"WEEK_LOAD_X100" to 11000),rawIndex=50.0,algorithmVersion=E.WEEKLY_MODEL_VERSION)
        val adjusted=adjustWeeklyLoadGoal(base,110)
        assertEquals(242.0,weeklyTargetValue(adjusted)!!,.001);assertEquals(45,adjusted.score)
        assertEquals(11000,adjusted.components["WEEK_LOAD_X100"]);assertEquals(22000,adjusted.components["BASE_TARGET_X100"])
        val goals=plan.goals.withWeeklyAdjustment(today,110)
        assertEquals(goals,decodeTrainingGoals(encodeTrainingGoals(goals)))
        assertTrue(goals.withWeeklyAdjustment(today,100).weeklyLoadAdjustments.isEmpty())
    }
    @Test fun adjustmentAppliesOnlyToItsOwnWeekIncludingHistoricalReconstruction() {
        val goals=plan.goals.withWeeklyAdjustment(today,125)
        fun target(at: Instant)=weeklyTargetValue(ApprovedWellnessScoreReadout(emptyMap(),emptyMap(),E.NormalizedInput(at,ZoneOffset.UTC),emptyList()).withV2Goals(goals).scores.getValue(E.SESSION_LOAD_ID))!!
        assertEquals(goals.weeklyLoadTarget*1.25,target(now),.01)
        assertEquals(goals.weeklyLoadTarget,target(now.minusSeconds(7*86400)),.01)
    }
    @Test fun completedTrainingIsNotAnInstructionToTrainAgainAndStaleBatteryDoesNotForceRest() {
        val planned=plan.copy(sessions=listOf(PlannedSession("today",today,"Rad",30,1.5,true)))
        assertEquals("completed",trainingDayGuidance(planned,null,emptyMap(),today,ZoneOffset.UTC).kind)
        val stale=E.ScoreResult(E.BODY_BATTERY_ID,5,E.Status.stale,now.minusSeconds(86400),E.Coverage(4,4),stale=true)
        assertEquals("active",trainingDayGuidance(planned.copy(sessions=planned.sessions.map {it.copy(completed=false)}),null,mapOf(E.BODY_BATTERY_ID to stale),today,ZoneOffset.UTC).kind)
        assertEquals("rest",trainingDayGuidance(plan,null,emptyMap(),today,ZoneOffset.UTC).kind)
    }
    @Test fun freshLowEnergySuggestsRecoveryWithoutDeletingScheduledTraining() {
        val planned=plan.copy(sessions=listOf(PlannedSession("today",today,"Rad",30,1.5)))
        val score=E.ScoreResult(E.BODY_BATTERY_ID,20,E.Status.available,now,E.Coverage(4,4))
        assertEquals("recovery",trainingDayGuidance(planned,null,mapOf(E.BODY_BATTERY_ID to score),today,ZoneOffset.UTC).kind)
        assertEquals(1,planned.sessions.size);assertFalse(planned.sessions.single().completed)
    }
    @Test fun seriesAndAdjustmentAreAvailableAsValidatedPlanTools() {
        val args=JSONObject().put("start_date",today.toString()).put("end_date",today.plusDays(4).toString()).put("weekdays",org.json.JSONArray(listOf(1,2,3,4,5))).put("title","Rad").put("minutes",30).put("intensity",1.5).put("replace_existing",true)
        assertEquals(5,applyPlanTool(plan,"schedule_training_series",args,today).first.sessions.size)
        assertThrows(IllegalArgumentException::class.java) {applyPlanTool(plan,"set_weekly_load_adjustment",JSONObject().put("percent",200),today)}
    }
}
