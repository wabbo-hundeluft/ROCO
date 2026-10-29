package com.wabbo.healthconnectcoach

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class GoogleHealthFilterTest {
    @Test fun exerciseUsesTheCivilFieldRequiredByTheApi() {
        val filter=googleExerciseFilter(LocalDate.of(2026,10,24),LocalDate.of(2026,10,26))
        assertEquals("exercise.interval.civil_start_time >= \"2026-10-24\" AND exercise.interval.civil_start_time < \"2026-10-26\"",filter)
        assertFalse(filter.contains("interval.start_time"))
    }
    @Test fun emptyAndReversedExerciseWindowsAreRejected() {
        val date=LocalDate.of(2026,10,25)
        assertThrows(IllegalArgumentException::class.java) {googleExerciseFilter(date,date)}
        assertThrows(IllegalArgumentException::class.java) {googleExerciseFilter(date,date.minusDays(1))}
    }
}
