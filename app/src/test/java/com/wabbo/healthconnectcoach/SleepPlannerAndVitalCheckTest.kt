package com.wabbo.healthconnectcoach

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class SleepPlannerAndVitalCheckTest {
    private val today=LocalDate.of(2026,10,28)
    private val zone=ZoneId.of("Europe/Berlin")
    private val now=today.atTime(18,0).atZone(zone).toInstant()
    private fun day(date:LocalDate=today,hrv:Double=50.0,source:String="watch",sleep:Double=480.0)=DayInsight(date,date.atTime(12,0).atZone(zone).toInstant(),false,emptyMap(),
        metrics=mapOf("hrv" to hrv,"rhr" to 60.0,"spo2" to 98.0,"respiratory_rate" to 14.0,"skin_delta" to .1,"sleep_minutes" to sleep,"time_in_bed" to sleep/.9),
        sources=mapOf("_connector" to "Google Health","sleep" to source,"hrv" to source,"rhr" to source,"_metric_spo2" to source,"_metric_respiratory_rate" to source,"_metric_skin_delta" to source))
    private fun plan(days:List<DayInsight> = emptyList(),settings:SleepPlannerSettings=SleepPlannerSettings())=planTonight(settings,days,today,"Google Health",zone,now)
    @Test fun noMeasurementsStillProducesExplicitAssumedPlan() {
        val p=plan();assertEquals(480,p.sleepMinutes);assertEquals(0,p.observedNights);assertTrue(p.efficiencyAssumed)
        assertEquals(today.plusDays(1),p.wake.toLocalDate());assertEquals(7,p.wake.hour)
        assertTrue(p.bedtime<p.wake);assertTrue(p.windDown<p.bedtime)
    }
    @Test fun missingNightsNeverBecomeSleepDebt() {assertEquals(0,plan(listOf(day(today.minusDays(4),sleep=240.0))).debtMinutes)}
    @Test fun onlyObservedRecentDeficitAndSameSourceCount() {
        val p=plan(listOf(day(),day(today.minusDays(1),sleep=360.0),day(today.minusDays(2),source="other",sleep=180.0)))
        assertEquals(60,p.debtMinutes);assertEquals(2,p.observedNights)
    }
    @Test fun oldConnectorAndFutureDataDoNotAffectTonight() {
        val bad=day().copy(sources=day().sources+("_connector" to "Health Connect"),metrics=day().metrics+("sleep_minutes" to 240.0))
        assertEquals(plan(),plan(listOf(bad,day(today.plusDays(1),sleep=240.0))))
    }
    @Test fun napsAndTrainingChangeRecommendationWithinBounds() {
        val w=ActivityInsight("w","Rad",now.minusSeconds(7200),now.minusSeconds(3600),60.0,null,null,null,emptyList(),true)
        assertEquals(15,plan(listOf(day().copy(activities=listOf(w)))).trainingMinutes)
        val p=plan(listOf(day().copy(metrics=day().metrics+("nap_minutes" to 180.0))))
        assertEquals(45,p.napCreditMinutes);assertEquals(435,p.sleepMinutes)
    }
    @Test fun recentEfficiencyNeedsFiveEarlierNights() {
        val days=(1L..5).map {day(today.minusDays(it)).copy(metrics=mapOf("sleep_minutes" to 480.0,"time_in_bed" to 600.0))}
        val p=plan(days+day());assertFalse(p.efficiencyAssumed);assertEquals(.8,p.efficiency,.0001)
        assertEquals(570,p.bedMinutes) // Bound the estimated wake-time buffer to 90 minutes.
    }
    @Test fun plannerRespectsTimeZoneChangeAndDstDuration() {
        val date=LocalDate.of(2026,10,24);val at=date.atTime(20,0).atZone(zone).toInstant()
        val p=planTonight(SleepPlannerSettings(),emptyList(),date,"Google Health",zone,at)
        assertEquals(p.bedMinutes.toLong(),Duration.between(p.bedtime,p.wake).toMinutes())
    }
    @Test fun justAfterMidnightUsesUpcomingMorning() {
        val p=planTonight(SleepPlannerSettings(),emptyList(),today,"Google Health",zone,today.atTime(0,30).atZone(zone).toInstant())
        assertEquals(today,p.wake.toLocalDate());assertTrue(p.windowStarted)
    }
    @Test fun invalidSettingsRejected() {assertTrue(runCatching {plan(settings=SleepPlannerSettings(1440))}.isFailure)}
    @Test fun noCurrentVitalsNeverBorrowYesterdaysValues() {
        assertTrue(vitalCheck(listOf(day(today.minusDays(1))),today,"Google Health").all {it.state==VitalCheckState.MISSING})
    }
    @Test fun allFiveSignalsUseOwnEarlierRange() {
        val history=(1L..14).map {day(today.minusDays(it))}
        val values=vitalCheck(history+day(hrv=20.0),today,"Google Health")
        assertEquals(5,values.size);assertEquals(VitalCheckState.LOWER,values.first().state)
        assertEquals(4,values.count {it.state==VitalCheckState.WITHIN})
    }
    @Test fun otherSourceDoesNotProvideBaseline() {
        val history=(1L..14).map {day(today.minusDays(it),source="other")}
        assertTrue(vitalCheck(history+day(),today,"Google Health").all {it.state==VitalCheckState.LEARNING})
    }
    @Test fun futureExtremeValueDoesNotContaminateComparison() {
        val history=(1L..14).map {day(today.minusDays(it))}
        assertEquals(vitalCheck(history+day(),today,"Google Health"),vitalCheck(history+day()+day(today.plusDays(1),hrv=999.0),today,"Google Health"))
    }
    @Test fun stableBaselineToleratesMeasurementNoise() {
        val history=(1L..14).map {day(today.minusDays(it))}
        assertEquals(VitalCheckState.WITHIN,vitalCheck(history+day(hrv=52.0),today,"Google Health").first().state)
    }
}
