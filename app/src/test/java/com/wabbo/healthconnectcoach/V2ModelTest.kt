package com.wabbo.healthconnectcoach

import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine as E
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class V2ModelTest {
    private val now=Instant.parse("2026-10-25T12:00:00Z")
    private val engine=E()
    private fun workout(start: String, minutes: Long, id: String="a")=E.Workout(Instant.parse(start),Instant.parse(start).plusSeconds(minutes*60),"watch",id)
    private fun input(vararg workouts: E.Workout)=E.NormalizedInput(now,ZoneOffset.UTC,workouts=workouts.toList(),
        boundSources=mapOf("exercise" to "watch","heart_rate" to "watch","rhr" to "watch"))
    @Test fun noConfirmedGoalNeverCreatesAnArbitraryWeeklyValue() {val score=engine.scoreWeeklyLoad(input(),null);assertNull(score.score);assertTrue("weekly_target_missing" in score.coverage.missingReasons)}
    @Test fun zeroActivityWithCompleteReadIsZeroRatherThanMissing() {assertEquals(0,engine.scoreWeeklyLoad(input(),60.0).score)}
    @Test fun calendarWeekExcludesPreviousSundayAndKeepsRestDayProgress() {
        val i=input(workout("2026-10-18T10:00:00Z",120),workout("2026-10-19T10:00:00Z",30,"b"))
        val score=engine.scoreWeeklyLoad(i,60.0);assertEquals(50,score.score);assertEquals(30,score.components["WEEK_MIN"]);assertTrue("duration_fallback" in score.coverage.missingReasons)
    }
    @Test fun crossingMondayWorkoutOnlyCountsThePartInsideThisWeek() {assertEquals(50,engine.scoreWeeklyLoad(input(workout("2026-10-18T23:50:00Z",20)),20.0).score)}
    @Test fun targetAndExcessAreNotClampedToOneHundred() {assertEquals(100,engine.scoreWeeklyLoad(input(workout("2026-10-20T10:00:00Z",60)),60.0).score);assertEquals(150,engine.scoreWeeklyLoad(input(workout("2026-10-20T10:00:00Z",90)),60.0).score)}
    @Test fun identicalExportedWorkoutsCountOnce() {val w=workout("2026-10-20T10:00:00Z",30);assertEquals(50,engine.scoreWeeklyLoad(input(w,w.copy(id="copy")),60.0).score)}
    @Test fun overlappingDistinctWorkoutsCannotSilentlyDoubleTheLoad() {val score=engine.scoreWeeklyLoad(input(workout("2026-10-20T10:00:00Z",30),workout("2026-10-20T10:15:00Z",30,"b")),60.0);assertNull(score.score);assertEquals(E.Status.source_conflict,score.status)}
    @Test fun incompleteCurrentExerciseReadDoesNotBecomeZero() {val i=input().copy(recordTypeWindowCompleteness=mapOf(E.RecordType.EXERCISE_SESSION to mapOf("W0" to false)));assertNull(engine.scoreWeeklyLoad(i,60.0).score)}
    @Test fun excessWeeklyLoadSurvivesSnapshotRoundTrip() {val score=engine.scoreWeeklyLoad(input(workout("2026-10-20T10:00:00Z",90)),60.0);val saved=scoreSnapshotCandidate(score,now);assertNotNull(saved);assertEquals(150,saved!!.approvedScoreSnapshotOrNull()!!.score.score)}

    private fun sleepInput(deep: Long, rem: Long, resting: Double=55.0): E.NormalizedInput {
        val start=Instant.parse("2026-10-24T22:00:00Z");val end=start.plusSeconds(8*3600)
        val light=480-deep-rem
        val stages=listOf(E.SleepStage(start,start.plusSeconds(light*60),"LIGHT"),E.SleepStage(start.plusSeconds(light*60),start.plusSeconds((light+deep)*60),"DEEP"),E.SleepStage(start.plusSeconds((light+deep)*60),end,"REM"))
        val date=LocalDate.of(2026,10,25)
        return E.NormalizedInput(now,ZoneOffset.UTC,sleeps=listOf(E.SleepSession(start,end,"watch","night",stages)),
            priorNights=(1L..7L).map {E.SleepNight(date.minusDays(it),120.0,"watch")},
            hrv=listOf(E.MetricSample(end.minusSeconds(3600),50.0,"watch")),rhr=listOf(E.MetricSample(end.minusSeconds(3600),resting,"watch")),
            hrvBaseline=(1L..8L).map {E.BaselineDay(date.minusDays(it),50.0,"watch")},rhrBaseline=(1L..8L).map {E.BaselineDay(date.minusDays(it),55.0,"watch")},
            boundSources=mapOf("sleep" to "watch","hrv" to "watch","rhr" to "watch"))
    }
    @Test fun detailedStagesHaveTheirOwnScoreComponentAndCorrectCoverage() {val score=engine.scoreSleep(sleepInput(90,120));assertNotNull(score.components["Q"]);assertEquals(6,score.coverage.expected);assertEquals(6,score.coverage.available)}
    @Test fun sleepDistributionChangesTheScoreEvenWithIdenticalDuration() {assertTrue(engine.scoreSleep(sleepInput(90,120)).score!!>engine.scoreSleep(sleepInput(10,10)).score!!)}
    @Test fun restingPulseStillInfluencesTheSleepScore() {assertTrue(engine.scoreSleep(sleepInput(90,120,70.0)).score!!<engine.scoreSleep(sleepInput(90,120,55.0)).score!!)}
    @Test fun batteryCoverageCountsSignalsRatherThanExplanationMetadata() {val i=sleepInput(90,120).copy(hrv=emptyList(),rhr=emptyList());val r=engine.scoreBodyBattery(i);assertEquals(1,r.coverage.available);assertNull(r.score);assertTrue("energy_hrv_baseline_or_current_missing" in r.coverage.missingReasons)}
    @Test fun unusedHistoricalRawPulseConflictsCannotSuppressTodaysBattery() {
        val original=sleepInput(90,120)
        val old=now.minusSeconds(40*86400)
        val input=original.copy(rhr=original.rhr+listOf(E.MetricSample(old,55.0,"watch"),E.MetricSample(old,57.0,"watch")),
            boundSources=original.boundSources+mapOf("exercise" to "watch","heart_rate" to "watch"))
        assertEquals(engine.scoreBodyBattery(original).score,engine.scoreBodyBattery(input).score)
        val walk=workout("2026-10-25T10:00:00Z",30)
        assertNotNull(engine.scoreBodyBattery(input.copy(workouts=listOf(walk))).score)
    }
    @Test fun mergingDifferentConnectorsNeverBorrowsMissingIndividualScores() {
        val date=LocalDate.of(2026,10,25);val old=DayInsight(date,now,false,mapOf(E.SLEEP_ID to E.ScoreResult(E.SLEEP_ID,90,E.Status.available,now,E.Coverage(4,4))),sources=mapOf("_connector" to "Google Health"))
        val fresh=old.copy(sources=mapOf("_connector" to "Health Connect"),scores=mapOf(E.SLEEP_ID to E.ScoreResult(E.SLEEP_ID,null,E.Status.insufficient_data,now,E.Coverage(0,4))))
        assertNull(mergeDays(listOf(old),listOf(fresh)).single().scores[E.SLEEP_ID]!!.score)
    }
}
