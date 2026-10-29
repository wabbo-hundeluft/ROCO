package com.wabbo.healthconnectcoach

import com.wabbo.healthconnectcoach.score.AdaptiveWeeklyLoad as A
import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine as E
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import kotlin.math.roundToLong

class AdaptiveWeeklyLoadTest {
    private val monday=LocalDate.of(2026,10,26)
    private val zone=ZoneId.of("Europe/Berlin")
    private val now=monday.atTime(12,0).atZone(zone).toInstant()
    private val engine=E()
    private fun history(vararg weeks: Double): E.NormalizedInput {
        val start=monday.minusWeeks(weeks.size.toLong())
        val workouts=weeks.flatMapIndexed {week,value ->(0L..6).mapNotNull {day ->
            if(value==0.0) null else {
                val at=start.plusDays(week*7L+day).atTime(10,0).atZone(zone).toInstant()
                E.Workout(at,at.plusSeconds((value/7*60).roundToLong()),"watch","$week-$day")
            }
        }}
        return E.NormalizedInput(now,zone,workouts=workouts,boundSources=listOf("exercise","heart_rate","rhr","hrv").associateWith {"watch"},
            trainingHistoryStart=monday.minusDays(65).atStartOfDay(zone).toInstant(),trainingHistoryComplete=true)
    }
    private fun result(i: E.NormalizedInput)=A.evaluate(i,220.0) {engine.activityLoad(it,i)}
    @Test fun sufficientHistoryLearnsWithoutHardcodedOrUserEnteredTarget() {
        val i=history(220.0,220.0,220.0,220.0,220.0,220.0,220.0,220.0)
        val target=A.evaluate(i,null) {engine.activityLoad(it,i)}
        assertTrue(target.adaptive);assertEquals(8,target.completeWeeks)
        assertTrue(target.target!! in 215.0..225.0)
        assertNotNull(engine.scoreWeeklyLoad(i,null).score)
    }
    @Test fun repeatedHigherTrainingRaisesTheTargetGradually() {
        val r=result(history(70.0,70.0,70.0,77.0,85.0,94.0,103.0,113.0))
        assertTrue(r.target!!>70.0);assertTrue(r.target!!<113.0)
        assertTrue(r.target!!<=r.previousTarget!!*1.10+.01)
    }
    @Test fun lessTrainingLowersTheTargetWithoutAnAbruptCollapse() {
        val r=result(history(140.0,140.0,140.0,126.0,113.0,102.0,92.0,83.0))
        assertTrue(r.target!!<140.0);assertTrue(r.target!!>83.0)
        assertTrue(r.target!!>=r.previousTarget!!*.90-.01)
    }
    @Test fun oneExtremeWeekDoesNotImmediatelyDeclareMuchHigherCapacity() {
        val r=result(history(100.0,100.0,100.0,100.0,100.0,100.0,100.0,1000.0))
        assertTrue(r.fatigueHold);assertTrue(r.target!!<=r.previousTarget!!+.01)
    }
    @Test fun currentWeekWorkoutsNeverMoveTheGoalposts() {
        val i=history(120.0,120.0,120.0,120.0,120.0,120.0,120.0,120.0)
        val w=E.Workout(now.minusSeconds(5*3600),now.minusSeconds(3600),"watch","today")
        assertEquals(result(i).target,result(i.copy(workouts=i.workouts+w)).target)
    }
    @Test fun dailyReadWindowShiftWithinAWeekDoesNotChangeTheTarget() {
        val i=history(120.0,125.0,130.0,135.0,140.0,145.0,150.0,155.0)
        val sunday=i.copy(asOf=now.plusSeconds(6*86400),trainingHistoryStart=i.trainingHistoryStart!!.plusSeconds(6*86400))
        assertEquals(result(i).target,result(sunday).target)
    }
    @Test fun incompleteHistoryDoesNotBecomeWeeksOfZeroTraining() {
        val r=result(history(120.0,120.0,120.0,120.0,120.0,120.0,120.0,120.0).copy(trainingHistoryComplete=false))
        assertFalse(r.adaptive);assertEquals(220.0,r.target!!,.001);assertEquals("adaptive_history_incomplete",r.reason)
    }
    @Test fun standardHealthConnectWindowCanUseThreeCompleteWeeks() {
        val i=history(140.0,140.0,140.0,140.0,140.0,140.0,140.0,140.0).copy(trainingHistoryStart=now.minusSeconds(28*86400))
        val r=result(i);assertTrue(r.adaptive);assertEquals(3,r.completeWeeks)
    }
    @Test fun allZeroHistoryWithoutConfirmedStarterGoalDoesNotInventAnExerciseTarget() {
        val i=history(0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0)
        assertNull(A.evaluate(i,null) {engine.activityLoad(it,i)}.target)
    }
    @Test fun twentyTwoOutOfTwoHundredTwentyIsTenPointsButOtherTargetsWorkIdentically() {
        listOf(220.0 to 22L,330.0 to 33L,470.0 to 47L).forEach { (target,minutes) ->
            val at=now.minusSeconds(4*3600)
            val i=E.NormalizedInput(now,zone,workouts=listOf(E.Workout(at,at.plusSeconds(minutes*60),"watch","today")))
            assertEquals(10,engine.scoreWeeklyLoad(i,target).score)
        }
    }
    @Test fun lowRecoveryBeforeMondayCanReduceButNeverIncreaseTheRecommendation() {
        val i=history(140.0,140.0,140.0,140.0,140.0,140.0,140.0,140.0)
        val h=(1L..28).map {E.BaselineDay(monday.minusDays(it),if(it<=3) 30.0 else 50.0,"watch")}
        val r=(1L..28).map {E.BaselineDay(monday.minusDays(it),if(it<=3) 70.0 else 60.0,"watch")}
        val lowered=result(i.copy(hrvBaseline=h,rhrBaseline=r))
        assertTrue(lowered.recoveryHold);assertTrue(lowered.target!!<result(i).target!!)
    }
    @Test fun unknownHistoricalSourcesCannotSilentlyInflateTheLearnedTarget() {
        val i=history(120.0,120.0,120.0,120.0,120.0,120.0,120.0,120.0)
        val conflicting=i.copy(boundSources=emptyMap(),workouts=i.workouts+ i.workouts.first().copy(source="other"))
        assertFalse(result(conflicting).adaptive);assertEquals("adaptive_history_ambiguous",result(conflicting).reason)
    }
    @Test fun aWeekWithoutWearEvidenceIsNotTreatedAsConfirmedDetraining() {
        val i=history(120.0,120.0,120.0,120.0,120.0,120.0,120.0,0.0)
        val r=result(i)
        assertFalse(r.adaptive);assertEquals("adaptive_wear_coverage_missing",r.reason)
        assertEquals(220.0,r.target!!,.01)
    }
    @Test fun observedRestDaysCanFormARealLowTrainingWeek() {
        val i=history(120.0,120.0,120.0,120.0,120.0,120.0,120.0,0.0)
        val observed=(1L..56).map {E.BaselineDay(monday.minusDays(it),60.0,"watch")}
        val r=result(i.copy(rhrBaseline=observed))
        assertTrue(r.adaptive);assertTrue(r.target!!<120.0)
    }
    @Test fun individualTrainingSharesSumToTheWeekUsingTheSameTarget() {
        val original=history(140.0,140.0,140.0,140.0,140.0,140.0,140.0,140.0)
        val first=now.minusSeconds(5*3600)
        val sessions=listOf(E.Workout(first,first.plusSeconds(1200),"watch","a"),E.Workout(first.plusSeconds(3600),first.plusSeconds(5400),"watch","b"))
        val i=original.copy(workouts=original.workouts+sessions)
        val score=engine.scoreWeeklyLoad(i,null)
        val target=weeklyTargetValue(score)!!
        val contributions=sessions.sumOf {100*engine.activityLoad(it,i).weightedMinutes/target}
        assertEquals(score.rawIndex!!,contributions,.000001)
    }
    @Test fun currentWeekRecoveryChangesCannotRewriteMondaysTarget() {
        val i=history(140.0,140.0,140.0,140.0,140.0,140.0,140.0,140.0)
        val later=i.copy(hrvBaseline=listOf(E.BaselineDay(monday,10.0,"watch")),rhrBaseline=listOf(E.BaselineDay(monday,90.0,"watch")))
        assertEquals(result(i).target,result(later).target)
    }

    @Test fun threeWeekLearningWindowRespondsToRecentModerateIncreaseAndDecrease() {
        val stable=history(100.0,100.0,100.0).copy(trainingHistoryStart=now.minusSeconds(28*86400))
        val up=history(100.0,100.0,115.0).copy(trainingHistoryStart=now.minusSeconds(28*86400))
        val down=history(100.0,100.0,70.0).copy(trainingHistoryStart=now.minusSeconds(28*86400))
        assertTrue(result(up).target!!>result(stable).target!!)
        assertTrue(result(down).target!!<result(stable).target!!)
        assertTrue(result(up).target!!<=110.01);assertTrue(result(down).target!!>=89.99)
    }
    @Test fun pointsUseTheTargetAsDenominatorEvenWhenAnEqualityIsCoincidental() {
        val start=now.minusSeconds(7200)
        val i=E.NormalizedInput(now,zone,workouts=listOf(E.Workout(start,start.plusSeconds(20*60),"watch","ride")))
        for(target in listOf(83.7,100.0,220.0,473.2)) {
            val score=engine.scoreWeeklyLoad(i,target)
            assertEquals(100.0*20.0/target,score.rawIndex!!,.000001)
            assertEquals(target,weeklyTargetValue(score)!!,.005)
        }
    }
}
