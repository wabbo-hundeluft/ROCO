package com.wabbo.healthconnectcoach

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class TrendAggregationTest {
    private val zone=ZoneOffset.UTC
    private fun points(values:List<Double?>,start:LocalDate=LocalDate.of(2026,10,20))=values.mapIndexed {i,v->V2PlotPoint(start.plusDays(i.toLong()).atStartOfDay(zone).toInstant(),v)}
    @Test fun ninetyDaysBecomeThirtyActualThreeDayMeans() {
        val result=aggregateTrendPoints(points((1..90).map {it.toDouble()}),90,zone=zone)
        assertEquals(30,result.size);assertEquals(2.0,result.first().value!!,0.001);assertEquals(89.0,result.last().value!!,0.001);assertEquals(3,result.first().samples)
    }
    @Test fun missingValuesStayExplicitAndDoNotJoinAcrossPartialGroups() {
        val result=aggregateTrendPoints(points(listOf(3.0,null,9.0,null,null,null)),90,zone=zone)
        assertEquals(6.0,result.first().value!!,0.001);assertEquals(2,result.first().samples);assertFalse(result.first().joinable);assertNull(result.last().value)
        assertTrue(result.first().label!!.contains("2/3"))
    }
    @Test fun weeklyLoadNeverAveragesAcrossMondayReset() {
        val result=aggregateTrendPoints(points(listOf(90.0,100.0,0.0),LocalDate.of(2026,10,24)),90,weeklyReset=true,zone=zone)
        assertEquals(2,result.size);assertEquals(95.0,result.first().value!!,0.001);assertEquals(0.0,result.last().value!!,0.001)
    }
    @Test fun shortWindowsRemainDailyAndGapsDoNotBecomeConsecutiveDays() {
        val original=points(listOf(1.0,2.0,3.0));assertEquals(original,aggregateTrendPoints(original,30,zone=zone))
        assertEquals(2,aggregateTrendPoints(listOf(original.first(),original.last()),90,zone=zone).size)
    }
}
