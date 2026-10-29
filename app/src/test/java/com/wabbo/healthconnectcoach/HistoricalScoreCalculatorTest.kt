package com.wabbo.healthconnectcoach

import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine as E
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class HistoricalScoreCalculatorTest {
    private val now=Instant.parse("2026-10-25T12:00:00Z")
    private val past=Instant.parse("2026-10-23T21:59:59Z")
    private val zone=ZoneId.of("Europe/Berlin")
    @Test fun historicalNormalizationNeverConsumesLaterMetricsOrFutureBaselines() {
        val selected=E.NormalizedInput(now,zone,
            hrv=listOf(E.MetricSample(past.minusSeconds(100),45.0,"watch"),E.MetricSample(now,99.0,"watch")),
            rhr=listOf(E.MetricSample(now,60.0,"watch")),
            hrvBaseline=listOf(E.BaselineDay(now.atZone(zone).toLocalDate(),99.0,"watch")))
        val current=ApprovedWellnessScoreReadout(emptyMap(),emptyMap(),selected,emptyList())
        val historical=historicalScoreReadout(current,past,emptyMap()).normalizedInput
        assertEquals(past,historical.asOf)
        assertEquals(1,historical.hrv.size)
        assertTrue(historical.rhr.isEmpty());assertTrue(historical.hrvBaseline.isEmpty())
        assertEquals(2,current.normalizedInput.hrv.size)
    }
    @Test fun earlierReferenceWindowsAreNotInferredFromTodaysCompleteness() {
        val current=ApprovedWellnessScoreReadout(emptyMap(),emptyMap(),E.NormalizedInput(now,zone),emptyList())
        val windows=mapOf(E.RecordType.EXERCISE_SESSION to mapOf("W0" to true,"W1" to false,"W2" to false,"W3" to false))
        val pastInput=historicalScoreReadout(current,past,windows).normalizedInput
        assertEquals(windows,pastInput.recordTypeWindowCompleteness)
    }
}
