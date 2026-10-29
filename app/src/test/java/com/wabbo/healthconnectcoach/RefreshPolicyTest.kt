package com.wabbo.healthconnectcoach

import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine as E
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class RefreshPolicyTest {
    private val now=Instant.parse("2026-10-26T15:00:00Z")
    private fun battery(at: Instant=now.minusSeconds(86400),value: Int?=36,version: String=E.MODEL_VERSION+"/energy")=E.ScoreResult(E.BODY_BATTERY_ID,value,E.Status.available,at,E.Coverage(4,4),algorithmVersion=version)
    @Test fun automaticRefreshWaitsExactlyAnHourWhileManualIsImmediate() {
        assertTrue(automaticRefreshDue(null,now))
        assertFalse(automaticRefreshDue(now.minusSeconds(3599),now))
        assertTrue(automaticRefreshDue(now.minusSeconds(3600),now))
        assertTrue(automaticRefreshDue(now,now,true))
    }
    @Test fun clockCorrectionDoesNotBlockTheAppUntilOldFutureTime() {assertTrue(automaticRefreshDue(now.plusSeconds(3600),now))}
    @Test fun missingEnergyUsesLatestSavedValueWithItsOriginalDateAndModel() {
        val older=battery();val latest=battery(now.minusSeconds(10),44)
        val current=mapOf(E.BODY_BATTERY_ID to battery(now,null))
        val shown=withLastSavedEnergy(current,listOf(older,latest),now).getValue(E.BODY_BATTERY_ID)
        assertEquals(44,shown.score);assertEquals(latest.asOf,shown.asOf);assertEquals(latest.algorithmVersion,shown.algorithmVersion)
        assertTrue(shown.stale);assertEquals(E.Status.stale,shown.status);assertTrue(shown.coverage.missingReasons.contains("saved_energy_pending_refresh"))
        assertNull(current.getValue(E.BODY_BATTERY_ID).score);assertFalse(latest.stale)
    }
    @Test fun freshZeroEnergyIsNotReplacedByAHigherOldValue() {
        val current=mapOf(E.BODY_BATTERY_ID to battery(now,0));assertEquals(current,withLastSavedEnergy(current,listOf(battery(value=90)),now))
    }
    @Test fun futureOrIncompatibleModelCannotBeUsedAsFallback() {
        assertTrue(withLastSavedEnergy(emptyMap(),listOf(battery(now.plusSeconds(5)),battery(version="old/energy")),now).isEmpty())
    }
    @Test fun energyFallbackNeverSubstitutesOldSleepOrWeeklyScores() {
        val sleep=battery().copy(scoreId=E.SLEEP_ID)
        val result=withLastSavedEnergy(emptyMap(),listOf(sleep,battery()),now)
        assertEquals(setOf(E.BODY_BATTERY_ID),result.keys)
    }
    @Test fun analysisThrottleTimestampSurvivesCacheRoundTrip() {
        val cache=DayAnalysisCache(java.time.LocalDate.of(2026,10,26),lastRequestedAt=now)
        val restored=decodeAnalysisCache(encodeAnalysisCache(cache),cache.date)
        assertEquals(now,restored.lastRequestedAt)
        assertFalse(automaticRefreshDue(restored.lastRequestedAt,now.plusSeconds(3599)))
        assertTrue(automaticRefreshDue(restored.lastRequestedAt,now.plusSeconds(3600)))
    }
}
