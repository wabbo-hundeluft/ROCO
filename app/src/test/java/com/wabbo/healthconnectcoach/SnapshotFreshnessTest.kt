package com.wabbo.healthconnectcoach

import com.example.healthcoach.score.contract.ScoreCoverage
import com.example.healthcoach.score.contract.ScoreResult
import com.example.healthcoach.score.contract.ScoreStatus
import com.example.healthcoach.score.contract.ScoreWindow
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SnapshotFreshnessTest {
    private fun snapshot() = FeatureReadSnapshot(
        recordCount = 7,
        readAt = LocalDateTime.parse("2026-10-24T12:00:00"),
        score = ScoreResult(
            scoreId = "sleep",
            value0to100 = 84,
            status = ScoreStatus.AVAILABLE,
            asOf = OffsetDateTime.parse("2026-10-24T11:45:00Z"),
            window = ScoreWindow(meaning = "synthetic"),
            components = emptyList(),
            dataSources = emptyList(),
            coverage = ScoreCoverage(availableComponents = 0, expectedComponents = 0, ratio = 0.0),
            missingReasons = emptyList(),
            algorithmVersion = "hc-score-v0.3",
        ),
    )

    @Test
    fun emptyReadMarksRetainedScoreStaleWithoutChangingOriginalTimestamps() {
        assertOldSnapshotIsRetainedAndStale()
    }

    @Test
    fun readErrorMarksRetainedScoreStaleWithoutChangingOriginalTimestamps() {
        assertOldSnapshotIsRetainedAndStale()
    }

    @Test
    fun healthConnectUnavailableMarksEveryRetainedSnapshotUnverifiableWithoutChangingScoresOrTimestamps() {
        assertEarlyRefreshFailureMarksSnapshotsUnverifiable()
    }

    @Test
    fun permissionCheckFailureMarksEveryRetainedSnapshotUnverifiableWithoutChangingScoresOrTimestamps() {
        assertEarlyRefreshFailureMarksSnapshotsUnverifiable()
    }

    @Test
    fun earlyRefreshFailureWithNoSnapshotsLeavesTheEmptyMapUnchanged() {
        val emptySnapshots = emptyMap<String, FeatureReadSnapshot>()

        assertSame(emptySnapshots, emptySnapshots.markSnapshotsFreshnessUnverifiable())
    }

    private fun assertOldSnapshotIsRetainedAndStale() {
        val previous = snapshot()
        assertNull(snapshotFreshnessLabel(previous))
        val originalCalculatedAt = previous.score.asOf
        val originalLastSuccessfulReadAt = previous.readAt
        val originalValue = previous.score.value0to100
        val originalStatus = previous.score.status

        val updated = mapOf("sleep" to previous).markFeatureSnapshotStale("sleep")
        val retained = requireNotNull(updated["sleep"])

        assertEquals(SnapshotFreshness.STALE, retained.freshness)
        assertEquals("Veraltet · letzter erfolgreicher Health-Connect-Read am 24.10.2026, 12:00.", snapshotFreshnessLabel(retained))
        assertSame(previous.score, retained.score)
        assertEquals(originalValue, retained.score.value0to100)
        assertEquals(originalStatus, retained.score.status)
        assertEquals(originalCalculatedAt, retained.score.asOf)
        assertEquals(originalLastSuccessfulReadAt, retained.readAt)
        assertEquals(previous.recordCount, retained.recordCount)
    }

    private fun assertEarlyRefreshFailureMarksSnapshotsUnverifiable() {
        val sleepSnapshot = snapshot()
        val readinessSnapshot = snapshot().copy(
            readAt = LocalDateTime.parse("2026-10-23T08:30:00"),
            score = sleepSnapshot.score.copy(
                scoreId = "readiness",
                value0to100 = 61,
                status = ScoreStatus.PROVISIONAL,
                asOf = OffsetDateTime.parse("2026-10-23T08:00:00Z"),
            ),
        )
        val previous = mapOf("sleep" to sleepSnapshot, "readiness" to readinessSnapshot)

        val updated = previous.markSnapshotsFreshnessUnverifiable()

        assertEquals(previous.keys, updated.keys)
        for ((featureId, original) in previous) {
            val retained = requireNotNull(updated[featureId])
            assertEquals(SnapshotFreshness.FRESHNESS_UNVERIFIABLE, retained.freshness)
            assertEquals(
                "Veraltet · Freshness nicht prüfbar · letzter erfolgreicher Health-Connect-Read am ${original.readAt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm", Locale.GERMANY))}.",
                snapshotFreshnessLabel(retained),
            )
            assertSame(original.score, retained.score)
            assertEquals(original.score.value0to100, retained.score.value0to100)
            assertEquals(original.score.status, retained.score.status)
            assertEquals(original.score.asOf, retained.score.asOf)
            assertEquals(original.readAt, retained.readAt)
            assertEquals(original.recordCount, retained.recordCount)
        }
    }
}
