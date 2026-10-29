package com.wabbo.healthconnectcoach

/** Stable Health Connect record-type keys; persisted bindings contain no record payload. */
internal object ApprovedWellnessRecordTypes {
    const val SLEEP_SESSION = "SleepSessionRecord"
    const val HRV_RMSSD = "HeartRateVariabilityRmssdRecord"
    const val RESTING_HEART_RATE = "RestingHeartRateRecord"
    const val EXERCISE_SESSION = "ExerciseSessionRecord"
    const val HEART_RATE = "HeartRateRecord"
    const val STEPS = "StepsRecord"

    val settingsRows = listOf(
        SLEEP_SESSION to "Schlafsitzungen und Stadien",
        HRV_RMSSD to "HRV RMSSD",
        RESTING_HEART_RATE to "Ruhepuls",
        EXERCISE_SESSION to "Trainingseinheiten",
        HEART_RATE to "Herzfrequenz in Trainingseinheiten",
        STEPS to "Schritte außerhalb des Trainings",
    )
}
