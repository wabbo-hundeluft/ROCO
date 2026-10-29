package com.wabbo.healthconnectcoach

import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine as Engine
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ScoreJournalTest {
    private val date = LocalDate.of(2026, 10, 25)
    private fun day(date: LocalDate, value: Int?) = DayInsight(date, date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(), false,
        mapOf(Engine.SLEEP_ID to Engine.ScoreResult(Engine.SLEEP_ID, value,
            if (value == null) Engine.Status.insufficient_data else Engine.Status.available,
            Instant.parse("2026-10-25T06:00:00Z"), Engine.Coverage(if(value == null) 0 else 4,4))))
    @Test fun failedRefreshPreservesLastValueWithItsOriginalTimestampAndMarksItStale() {
        val previous = day(date, 81)
        val merged = mergeDays(listOf(previous), listOf(day(date,null))).single().scores.getValue(Engine.SLEEP_ID)
        assertEquals(81, merged.score)
        assertEquals(Engine.Status.stale, merged.status)
        assertEquals(previous.scores.getValue(Engine.SLEEP_ID).asOf,merged.asOf)
    }
    @Test fun successfulRefreshReplacesPreviousScoreWithoutDuplicatingDate() {
        val merged=mergeDays(listOf(day(date,81)),listOf(day(date,86)))
        assertEquals(1,merged.size);assertEquals(86,merged.single().scores.getValue(Engine.SLEEP_ID).score)
        assertEquals(Engine.Status.available,merged.single().scores.getValue(Engine.SLEEP_ID).status)
    }
    @Test fun retentionKeepsLastNinetyDistinctDays() {
        val values=(0L..100L).map { day(date.minusDays(it),80) }
        val merged=mergeDays(values,emptyList())
        assertEquals(90,merged.size);assertEquals(date.minusDays(89),merged.first().date);assertEquals(date,merged.last().date)
    }
    @Test fun unknownValueNeverBorrowsScoreFromAnotherDate() {
        val merged=mergeDays(listOf(day(date.minusDays(1),90)),listOf(day(date,null)))
        assertNull(merged.last().scores.getValue(Engine.SLEEP_ID).score)
    }
}
