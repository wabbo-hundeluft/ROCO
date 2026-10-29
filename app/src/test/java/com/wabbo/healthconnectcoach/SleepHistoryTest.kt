package com.wabbo.healthconnectcoach

import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine as E
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SleepHistoryTest {
    private val now=Instant.parse("2026-10-26T10:00:00Z")
    private val score=E.ScoreResult(E.SLEEP_ID,76,E.Status.available,now,E.Coverage(4,4),algorithmVersion="same-model")
    private val day=DayInsight(LocalDate.of(2026,10,26),now,false,mapOf(E.SLEEP_ID to score),sources=mapOf("_connector" to "Google Health","sleep" to "watch"))
    @Test fun sourceAndModelChangesAreNotJoined() {
        val good=day.copy(date=day.date.minusDays(1))
        val otherConnector=day.copy(date=day.date.minusDays(2),sources=day.sources+("_connector" to "Health Connect"))
        val otherOrigin=day.copy(date=day.date.minusDays(3),sources=day.sources+("sleep" to "other-watch"))
        val otherModel=day.copy(date=day.date.minusDays(4),scores=mapOf(E.SLEEP_ID to score.copy(algorithmVersion="old-model")))
        assertEquals(listOf(good,day),comparableSleepHistory(listOf(otherConnector,otherOrigin,otherModel,day,good),day))
    }
    @Test fun newestDayCannotResurrectAnOlderMatchingSnapshot() {
        val old=day.copy(date=day.date.minusDays(1),calculatedAt=now.minusSeconds(10))
        val latest=old.copy(calculatedAt=now,sources=day.sources+("sleep" to "other-watch"))
        assertEquals(listOf(day),comparableSleepHistory(listOf(old,latest,day),day))
    }
    @Test fun futureAndUnknownOriginAreNotUsed() {
        assertEquals(listOf(day),comparableSleepHistory(listOf(day,day.copy(date=day.date.plusDays(1))),day))
        assertTrue(comparableSleepHistory(listOf(day),day.copy(sources=emptyMap())).isEmpty())
    }
    @Test fun overviewOnlyClaimsGoalReachedWithRealDurationAndGoal() {
        assertTrue(sleepOverviewNote(day).startsWith("Die Dauer"))
        assertTrue(sleepOverviewNote(day.copy(metrics=mapOf("sleep_minutes" to 480.0,"sleep_goal" to 480.0))).contains("erreicht"))
        assertTrue(sleepOverviewNote(day.copy(metrics=mapOf("sleep_minutes" to 420.0,"sleep_goal" to 480.0))).contains("kürzer"))
        assertTrue(sleepOverviewNote(day.copy(metrics=mapOf("sleep_minutes" to Double.NaN,"sleep_goal" to 480.0))).startsWith("Die Dauer"))
    }
}
