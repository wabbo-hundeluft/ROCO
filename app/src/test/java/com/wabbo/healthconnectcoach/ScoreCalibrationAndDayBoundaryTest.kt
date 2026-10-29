package com.wabbo.healthconnectcoach

import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine as E
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ScoreCalibrationAndDayBoundaryTest {
    private val engine=E()
    private val day=LocalDate.of(2026,10,26)
    private val zone=ZoneId.of("Europe/Berlin")
    private val wake=Instant.parse("2026-10-26T06:00:00Z")
    private fun night(end: Instant=wake,hours: Long=8,id: String="today"): E.SleepSession {
        val start=end.minusSeconds(hours*3600)
        return E.SleepSession(start,end,"watch",id,listOf(
            E.SleepStage(start,start.plusSeconds(hours*1800),"LIGHT"),
            E.SleepStage(start.plusSeconds(hours*1800),start.plusSeconds(hours*2700),"DEEP"),
            E.SleepStage(start.plusSeconds(hours*2700),end,"REM")))
    }
    private fun input(at: Instant=wake.plusSeconds(2*3600),hrv: Double=50.0)=E.NormalizedInput(at,zone,
        sleeps=listOf(night()),priorNights=(1L..14).map {E.SleepNight(day.minusDays(it),180.0,"watch")},
        hrv=listOf(E.MetricSample(wake.minusSeconds(3600),hrv,"watch")),rhr=listOf(E.MetricSample(wake.minusSeconds(3600),60.0,"watch")),
        hrvBaseline=(1L..14).map {E.BaselineDay(day.minusDays(it),50.0,"watch")},
        rhrBaseline=(1L..14).map {E.BaselineDay(day.minusDays(it),60.0,"watch")},
        boundSources=listOf("sleep","hrv","rhr","exercise","heart_rate","steps").associateWith {"watch"})
    @Test fun newerShorterNightWinsOverLongerYesterdayWithinTwentyFourHours() {
        val yesterday=night(Instant.parse("2026-10-25T09:00:00Z"),10,"yesterday")
        val i=input().copy(sleeps=listOf(yesterday,night()))
        assertEquals("today",engine.selectedMainSleep(i)?.id)
        val read=ApprovedWellnessScoreReadout(mapOf(E.SLEEP_ID to engine.scoreSleep(i)),emptyMap(),i,emptyList())
        assertEquals(wake,dayInsight(read).sleepEnd)
    }
    @Test fun absentTodaysNightCannotBorrowYesterdaysEvenWithinTwentyFourHours() {
        val i=input().copy(sleeps=listOf(night(Instant.parse("2026-10-25T09:00:00Z"),10,"yesterday")))
        assertNull(engine.selectedMainSleep(i));assertNull(engine.scoreSleep(i).score);assertNull(engine.scoreBodyBattery(i).score)
    }
    @Test fun localWakeDateHandlesUtcDateBoundary() {
        val end=Instant.parse("2026-10-25T23:30:00Z")
        assertEquals(day,end.atZone(zone).toLocalDate())
        assertNotNull(engine.selectedMainSleep(input(end.plusSeconds(60)).copy(sleeps=listOf(night(end)))) )
    }
    @Test fun providerLabelledNapIsNotChosenAsMainSleep() {
        val i=input().copy(sleeps=listOf(night().copy(isMainSleep=false)))
        assertNull(engine.selectedMainSleep(i))
    }
    @Test fun personalMedianIsNeutralAndNotAutomaticPerfectRecovery() {
        val score=engine.scoreSleep(input())
        assertEquals(50,score.components["H"]);assertEquals(50,score.components["P"])
        assertEquals(85,score.score)
    }
    @Test fun absoluteHrvThirtyFiveIsNotAutomaticallyBadButADropFromSeventyMatters() {
        val i=input(hrv=35.0)
        val normal=i.copy(hrvBaseline=i.hrvBaseline.map {it.copy(value=35.0)})
        val suppressed=i.copy(hrvBaseline=i.hrvBaseline.map {it.copy(value=70.0)})
        assertEquals(50,engine.scoreSleep(normal).components["H"])
        assertTrue(engine.scoreSleep(normal).score!!>engine.scoreSleep(suppressed).score!!)
    }
    @Test fun fourteenHoursAwakeDoNotLeaveBatteryNearlyFull() {
        val morning=engine.scoreBodyBattery(input(wake))
        val evening=engine.scoreBodyBattery(input(wake.plusSeconds(14*3600)))
        assertEquals(60,morning.score);assertEquals(30,evening.score)
        assertEquals(30,evening.components["AWAKE"])
    }
    @Test fun earlierTrainingIsNotAutomaticallyRefundedTwelveHoursLater() {
        val w=E.Workout(wake.plusSeconds(600),wake.plusSeconds(4200),"watch","workout")
        val i=input(wake.plusSeconds(2*3600)).copy(workouts=listOf(w))
        val later=i.copy(asOf=wake.plusSeconds(14*3600))
        assertEquals(engine.scoreBodyBattery(i).components["TRAINING"],engine.scoreBodyBattery(later).components["TRAINING"])
        assertTrue(engine.scoreBodyBattery(later).score!!<engine.scoreBodyBattery(i).score!!)
    }
    @Test fun shortAwakeningsReduceQualityWithoutChangingReportedSleepDuration() {
        val i=input();val s=i.sleeps.single()
        val disturbed=s.copy(shortAwakenings=listOf(E.SleepStage(s.start.plusSeconds(7200),s.start.plusSeconds(8400),"AWAKE")))
        val a=engine.scoreSleep(i);val b=engine.scoreSleep(i.copy(sleeps=listOf(disturbed)))
        assertEquals(a.components["D"],b.components["D"]);assertTrue(b.score!!<a.score!!)
    }
    @Test fun chartAndCurrentValueUseTheSameCalculation() {
        val i=input(wake.plusSeconds(14*3600))
        val line=engine.batteryTimeline(i)
        assertEquals(engine.scoreBodyBattery(i).score,line.last().second)
        assertTrue(line.zipWithNext().all {it.first.second>=it.second.second})
    }
    @Test fun yesterdayOrOldFormulaNeverAppearsAsTodaysCurrentScore() {
        val score=engine.scoreSleep(input())
        assertNull(scoreForDisplayDay(score.copy(asOf=score.asOf.minusSeconds(86400)),day,zone).score)
        assertNull(scoreForDisplayDay(score.copy(algorithmVersion="health-coach-v2/"+score.scoreId),day,zone).score)
        assertEquals(score.score,scoreForDisplayDay(score,day,zone).score)
    }
}
