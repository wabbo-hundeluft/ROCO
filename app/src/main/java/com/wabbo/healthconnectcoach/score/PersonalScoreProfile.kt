package com.wabbo.healthconnectcoach.score

import java.time.LocalDate

/** Optional user-declared inputs. No sex/weight multiplier is inferred for recovery. */
data class PersonalScoreProfile(
    val birthYear: Int? = null,
    val measuredMaxHeartRate: Int? = null,
    val pulseLimited: Boolean = false,
) {
    fun validated(today: LocalDate = LocalDate.now()): PersonalScoreProfile {
        require(birthYear == null || birthYear in (today.year - 100)..(today.year - 13)) { "Bitte ein Geburtsjahr für ein Alter von 13 bis 100 Jahren eingeben oder das Feld leer lassen." }
        require(measuredMaxHeartRate == null || measuredMaxHeartRate in 100..230) { "Bitte einen gemessenen Maximalpuls von 100 bis 230 eingeben oder das Feld leer lassen." }
        return this
    }
    fun ageAt(date: LocalDate): Int? = birthYear?.let { date.year - it }?.takeIf { it in 13..100 }
    fun maximumAt(date: LocalDate): Double? {
        if (pulseLimited) return null
        measuredMaxHeartRate?.takeIf { it in 100..230 }?.let { return it.toDouble() }
        // Tanaka et al., 2001: population estimate for adults, not an individual measurement.
        return ageAt(date)?.takeIf { it >= 18 }?.let { 208.0 - .7 * it }
    }
    fun sleepFloor(date: LocalDate): Double = if (ageAt(date)?.let { it < 18 } == true) 480.0 else 420.0
    val signature: Int get() = ((birthYear ?: 0) * 251 + (measuredMaxHeartRate ?: 0)) * 2 + if (pulseLimited) 1 else 0
}
