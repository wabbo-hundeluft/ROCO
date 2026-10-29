package com.wabbo.healthconnectcoach

import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine as E
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class DayAnalysisCacheTest {
    private val date=LocalDate.of(2026,10,26)
    private val now=Instant.parse("2026-10-26T12:00:00Z")
    private val config=AiConfiguration(geminiKey="test-key",shareHealthSummary=true)
    private val score=E.ScoreResult(E.SLEEP_ID,75,E.Status.available,now,E.Coverage(4,4),components=mapOf("D" to 80),algorithmVersion=E.MODEL_VERSION+"/sleep")
    private fun day()=DayInsight(date,now,false,mapOf(E.SLEEP_ID to score),metrics=mapOf("hrv" to 40.0,"rhr" to 55.0),sources=mapOf("_connector" to "Google Health","hrv" to "watch"))
    @Test fun reservesBeforeNetworkAndSurvivesRestartWithoutDuplicateAutomaticRequest() {
        val reserved=DayAnalysisCache(date).reserve("facts-a",false)
        val restored=decodeAnalysisCache(encodeAnalysisCache(reserved),date)
        assertEquals(1,restored.attempts)
        assertFalse(restored.mayRequest("facts-a",false))
        assertTrue(restored.mayRequest("facts-a",true))
    }
    @Test fun cachesSeparateFactSnapshotsAndOnlyMatchesRequestedOne() {
        var cache=DayAnalysisCache(date).reserve("a",false).finish(DayAnalysisEntry("a","first",now))
        cache=cache.reserve("b",false).finish(DayAnalysisEntry("b","second",now))
        assertEquals("first",cache.matching("a")?.text)
        assertNull(cache.matching("c"))
        assertFalse(cache.mayRequest("a",true))
        assertEquals(cache,decodeAnalysisCache(encodeAnalysisCache(cache),date))
    }
    @Test fun failedAttemptsStillConsumeDailyBudgetAndDoNotReuseOtherAnswer() {
        var cache=DayAnalysisCache(date).reserve("a",false).finish(DayAnalysisEntry("a","old",now))
        cache=cache.reserve("b",false)
        repeat(MAX_DAILY_ANALYSES-2) {cache=cache.reserve("b",true)}
        assertFalse(cache.mayRequest("b",true));assertFalse(cache.mayRequest("c",false))
        assertNull(cache.matching("b"))
    }
    @Test fun dayChangeResetsAttemptsAndAnswers() {
        val cache=DayAnalysisCache(date).reserve("a",false).finish(DayAnalysisEntry("a","yesterday",now))
        assertEquals(DayAnalysisCache(date.plusDays(1)),decodeAnalysisCache(encodeAnalysisCache(cache),date.plusDays(1)))
    }
    @Test fun legacyCacheRetainsBudgetButCannotMasqueradeAsCurrentFacts() {
        val old="""{"date":"2026-10-26","count":2,"fingerprint":"old","text":"old answer"}"""
        val cache=decodeAnalysisCache(old,date)
        assertEquals(2,cache.attempts);assertTrue(cache.entries.isEmpty())
    }
    @Test fun sameFactsWithChangedCalculationTimesReuseFingerprint() {
        val first=day()
        val refreshed=first.copy(calculatedAt=now.plusSeconds(3600),scores=mapOf(E.SLEEP_ID to score.copy(asOf=now.plusSeconds(3600))))
        assertEquals(analysisFingerprint(first,config,"Pip"),analysisFingerprint(refreshed,config,"Pip"))
    }
    @Test fun changedVitalOrOriginInvalidatesExplanationEvenWhenScoreSame() {
        val d=day();val hash=analysisFingerprint(d,config,"Pip")
        assertNotEquals(hash,analysisFingerprint(d.copy(metrics=d.metrics+("hrv" to 25.0)),config,"Pip"))
        assertNotEquals(hash,analysisFingerprint(d.copy(sources=d.sources+("hrv" to "other-watch")),config,"Pip"))
    }
    @Test fun keyModelAndRobotChangesCannotReusePreviousText() {
        val hash=analysisFingerprint(day(),config,"Pip")
        assertNotEquals(hash,analysisFingerprint(day(),config.copy(geminiKey="other"),"Pip"))
        assertNotEquals(hash,analysisFingerprint(day(),config.copy(geminiModel="different"),"Pip"))
        assertNotEquals(hash,analysisFingerprint(day(),config,"Bob"))
    }
    @Test fun canonicalOrderAndReadCountsDoNotTriggerAnotherRequest() {
        val d=day();val reordered=d.copy(metrics=linkedMapOf("rhr" to 55.0,"hrv" to 40.0,"read_hrv_count" to 900.0),sources=d.sources.toList().reversed().toMap())
        assertEquals(analysisFingerprint(d,config,"Pip"),analysisFingerprint(reordered,config,"Pip"))
    }
    @Test fun encodedCacheNeverContainsCredential() {
        val fingerprint=analysisFingerprint(day(),config,"Pip")
        val cache=DayAnalysisCache(date).reserve(fingerprint,false)
        assertFalse(encodeAnalysisCache(cache).contains(config.geminiKey))
        assertEquals(64,fingerprint.length)
    }
}
