package com.wabbo.healthconnectcoach

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScoreTrendInteractionTest {
    @Test
    fun tapSelectsNearestActualTimestampPosition() {
        val start = Instant.parse("2026-10-20T00:00:00Z")
        val points = listOf(
            ScoreTrendPoint(start, 40),
            ScoreTrendPoint(start.plusSeconds(86_400), 55),
            ScoreTrendPoint(start.plusSeconds(259_200), 62),
        )

        assertEquals(1, nearestTrendPointIndex(points, tapX = 55f, left = 0f, right = 100f, start = start, spanMillis = 259_200_000f))
    }

    @Test
    fun displayedDeltaUsesPreviousRealPointAndHasNoSyntheticBaseline() {
        val points = listOf(
            ScoreTrendPoint(Instant.parse("2026-10-20T00:00:00Z"), 40),
            ScoreTrendPoint(Instant.parse("2026-10-23T00:00:00Z"), 62),
        )

        assertEquals(22, trendPointDelta(points, 1))
        assertNull(trendPointDelta(points, 0))
        assertNull(trendPointDelta(points, 2))
        assertNull(nearestTrendPointIndex(emptyList(), 50f, 0f, 100f, points.first().at, 1f))
    }
}
