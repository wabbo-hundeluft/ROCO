package com.wabbo.healthconnectcoach

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class VitalDetailsTest {
    private val today=LocalDate.of(2026,10,26)
    private val temperature=vitalDefinitions.first {it.key=="skin_delta"}
    private val oxygen=vitalDefinitions.first {it.key=="spo2"}
    private fun day(date: LocalDate,value: Double,connector: String="Google Health",origin: String?="watch")=DayInsight(date,date.atStartOfDay(ZoneOffset.UTC).toInstant(),false,emptyMap(),
        metrics=mapOf("spo2" to value),sources=mapOf("_connector" to connector)+origin?.let {mapOf("_metric_spo2" to it)}.orEmpty())
    @Test fun smallNegativeTemperatureDeviationsKeepTheirSignAndDecimalPlace() {
        assertEquals("-0,3",formatVitalNumber(-.3,temperature))
        assertEquals("+0,3",formatVitalNumber(.3,temperature))
        assertFalse(formatVitalNumber(-.3,temperature).contains("0,0"))
    }
    @Test fun theAxisContainsNegativeOutliersAndPersonalBands() {
        val bounds=vitalChartBounds(listOf(-2.0,-.1),-.5,.5,-2.5 to .8)
        assertTrue(bounds.first<=-2.5);assertTrue(bounds.second>=.8)
    }
    @Test fun aFlatOrEmptySeriesNeverProducesAZeroHeightAxis() {
        listOf(emptyList(),listOf(0.0),listOf(Double.NaN,Double.POSITIVE_INFINITY)).forEach {
            val bounds=vitalChartBounds(it,0.0,0.0)
            assertTrue(bounds.first.isFinite()&&bounds.second.isFinite());assertTrue(bounds.second>bounds.first)
        }
    }
    @Test fun personalReferenceExcludesTodayOtherOriginsAndTheOtherConnector() {
        val current=day(today,95.0)
        val valid=(1L..7).map {day(today.minusDays(it),97.0)}
        val mixed=valid+current+day(today.minusDays(8),10.0,origin="other")+day(today.minusDays(9),20.0,connector="Health Connect")
        val ref=vitalReference(mixed,oxygen,current)!!
        assertEquals(7,ref.days);assertEquals(97.0,ref.median,0.0)
    }
    @Test fun duplicateDaysDoNotPretendThereAreSevenIndependentComparisons() {
        val rows=List(7) {day(today.minusDays(1),97.0)}
        assertNull(vitalReference(rows,oxygen,day(today,96.0)))
    }
    @Test fun unknownLegacyOriginsAreNotUsedToBuildAConfidentPersonalBaseline() {
        val rows=(1L..14).map {day(today.minusDays(it),97.0,origin=null)}
        assertNull(vitalReference(rows,oxygen,day(today,96.0)))
    }
    @Test fun incompleteDailyTotalsDoNotGetGradedAgainstCompleteDays() {
        val definition=vitalDefinitions.first {it.key=="distance_km"}
        val rows=(1L..14).map {day(today.minusDays(it),97.0).copy(metrics=mapOf("distance_km" to 10.0),sources=mapOf("_connector" to "Google Health","_metric_distance_km" to "watch"))}
        assertNull(vitalReference(rows,definition,rows.first().copy(date=today)))
    }
    @Test fun rereadingVitalsUpdatesOnlyTheMatchingConnectorAndStoresOrigin() {
        val google=day(today,97.0);val hc=day(today.minusDays(1),98.0,connector="Health Connect")
        val fresh=enrichVitalDays(listOf(google,hc),"Google Health",mapOf(today to mapOf("spo2" to 96.5),hc.date to mapOf("spo2" to 10.0)),mapOf("spo2" to "new-watch"))
        assertEquals(96.5,fresh.first().metrics["spo2"]!!,0.0);assertEquals("new-watch",metricOrigin(fresh.first(),"spo2"))
        assertEquals(hc,fresh.last())
    }
    @Test fun invalidValuesAreNotDisplayedAsRealMeasurements() {
        assertNull(finiteVital(day(today,Double.NaN),"spo2"))
    }
}
