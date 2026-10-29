package com.wabbo.healthconnectcoach

import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine as Engine
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class TrendDataTest {
    private val date=LocalDate.of(2026,10,26)
    private val now=Instant.parse("2026-10-26T20:00:00Z")
    private fun day(at: LocalDate=date,connector: String="Google Health",origin: String="watch",value: Double=40.0,calculated: Instant=now)=DayInsight(at,calculated,false,emptyMap(),metrics=mapOf("hrv" to value),sources=mapOf("_connector" to connector,"hrv" to origin))
    @Test fun metricsNeverMixConnectorsOrChangedOrigins() {
        val rows=listOf(day(date.minusDays(3),"Health Connect"),day(date.minusDays(2),origin="old-watch"),day(date.minusDays(1)),day())
        assertEquals(listOf(date.minusDays(1),date),comparableTrendDays(rows,"Google Health",date,key="hrv").map {it.date})
    }
    @Test fun futureAndDuplicateDayRowsDoNotChangeSelectedSource() {
        val rows=listOf(day(value=20.0,calculated=now.minusSeconds(1)),day(),day(date.plusDays(1),origin="other"))
        assertEquals(listOf(40.0),comparableTrendDays(rows,"Google Health",date,key="hrv").map {it.metrics["hrv"]})
    }
    @Test fun historicalSelectionUsesOnlyItsOwnPast() {
        val rows=listOf(day(date.minusDays(1),origin="old"),day(origin="new"))
        assertEquals("old",comparableTrendDays(rows,"Google Health",date.minusDays(1),key="hrv").single().sources["hrv"])
    }
    @Test fun unknownOriginsAndNonFiniteValuesCannotFormComparableCurve() {
        assertTrue(comparableTrendDays(listOf(day().copy(sources=mapOf("_connector" to "Google Health"))),"Google Health",date,key="hrv").isEmpty())
        assertTrue(comparableTrendDays(listOf(day(value=Double.NaN)),"Google Health",date,key="hrv").isEmpty())
    }
    @Test fun scoreChartsKeepOneModelAndWeeklyUnits() {
        val score=Engine().scoreWeeklyLoad(Engine.NormalizedInput(now,ZoneOffset.UTC),60.0)
        val rows=listOf(day(date.minusDays(2)).copy(scores=mapOf(Engine.SESSION_LOAD_ID to score.copy(algorithmVersion="old/weekly"))),
            day(date.minusDays(1)).copy(scores=mapOf(Engine.SESSION_LOAD_ID to score)),
            day().copy(scores=mapOf(Engine.SESSION_LOAD_ID to score.copy(algorithmVersion="daily"))))
        assertEquals(listOf(date.minusDays(1)),comparableTrendDays(rows,"Google Health",date,scoreId=Engine.SESSION_LOAD_ID).map {it.date})
    }
}
