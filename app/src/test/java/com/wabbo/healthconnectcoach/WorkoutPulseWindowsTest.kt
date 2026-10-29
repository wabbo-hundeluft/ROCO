package com.wabbo.healthconnectcoach

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class WorkoutPulseWindowsTest {
    private fun t(second:Long)=Instant.ofEpochSecond(second)
    @Test fun overlappingWindowsAreMergedWithHalfOpenEdges() {
        val index=WorkoutPulseWindows(listOf(t(10) to t(20),t(15) to t(30),t(50) to t(60)))
        assertFalse(index.contains(t(9)));assertTrue(index.contains(t(10)));assertTrue(index.contains(t(29)))
        assertFalse(index.contains(t(30)));assertFalse(index.contains(t(49)));assertTrue(index.contains(t(50)));assertFalse(index.contains(t(60)))
    }
    @Test fun emptyOrInvalidWorkoutIntervalsNeverRetainAllDayPulse() {
        val index=WorkoutPulseWindows(listOf(t(20) to t(10),t(30) to t(30)))
        assertFalse(index.contains(t(20)));assertFalse(index.contains(t(0)))
    }
}
