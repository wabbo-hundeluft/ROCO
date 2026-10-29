package com.wabbo.healthconnectcoach

import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.feature.ExperimentalMindfulnessSessionApi
import androidx.health.connect.client.feature.ExperimentalPersonalHealthRecordApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalMindfulnessSessionApi::class, ExperimentalPersonalHealthRecordApi::class)
class HealthConnectPermissionSetTest {
    private val expectedStandardSuffixes = setOf(
        "READ_ACTIVE_CALORIES_BURNED",
        "READ_EXERCISE",
        "READ_DISTANCE",
        "READ_ELEVATION_GAINED",
        "READ_FLOORS_CLIMBED",
        "READ_PLANNED_EXERCISE",
        "READ_POWER",
        "READ_SPEED",
        "READ_STEPS",
        "READ_TOTAL_CALORIES_BURNED",
        "READ_VO2_MAX",
        "READ_WHEELCHAIR_PUSHES",
        "READ_BASAL_METABOLIC_RATE",
        "READ_BODY_FAT",
        "READ_BODY_WATER_MASS",
        "READ_BONE_MASS",
        "READ_HEIGHT",
        "READ_LEAN_BODY_MASS",
        "READ_WEIGHT",
        "READ_BASAL_BODY_TEMPERATURE",
        "READ_CERVICAL_MUCUS",
        "READ_INTERMENSTRUAL_BLEEDING",
        "READ_MENSTRUATION",
        "READ_OVULATION_TEST",
        "READ_SEXUAL_ACTIVITY",
        "READ_HYDRATION",
        "READ_NUTRITION",
        "READ_SLEEP",
        "READ_BLOOD_GLUCOSE",
        "READ_BLOOD_PRESSURE",
        "READ_BODY_TEMPERATURE",
        "READ_HEART_RATE",
        "READ_HEART_RATE_VARIABILITY",
        "READ_OXYGEN_SATURATION",
        "READ_RESPIRATORY_RATE",
        "READ_RESTING_HEART_RATE",
        "READ_SKIN_TEMPERATURE",
        "READ_MINDFULNESS",
    )

    @Test
    fun standardBulkSetIsTheExactConcrete38PermissionCatalog() {
        assertEquals(38, HealthConnectSetup.standardReadTypes.size)
        assertEquals(
            expectedStandardSuffixes,
            HealthConnectSetup.standardReadTypes.map { it.suffix }.toSet(),
        )
        assertEquals(
            expectedStandardSuffixes.mapTo(linkedSetOf()) { "android.permission.health.$it" },
            HealthConnectSetup.standardPermissions,
        )
        assertEquals(HealthConnectSetup.standardPermissions, HealthConnectPermissions.allAllowed)
        assertTrue(HealthConnectSetup.standardPermissions.none { it.contains("WRITE") })
        assertTrue(HealthConnectSetup.standardPermissions.none { it.contains("ALL") })
        assertFalse(HealthConnectSetup.standardReadTypes.any { it.suffix == "READ_MENSTRUAL_CYCLE_PHASE" })
    }

    @Test
    fun categoryToPermissionMapMatchesTheSevenProductCategories() {
        val actual = HealthDataCategory.values().associateWith { category ->
            HealthConnectSetup.standardReadTypes.filter { it.category == category }.map { it.suffix }.toSet()
        }
        assertEquals(
            mapOf(
                HealthDataCategory.ACTIVITY to setOf(
                    "READ_ACTIVE_CALORIES_BURNED", "READ_EXERCISE", "READ_DISTANCE", "READ_ELEVATION_GAINED",
                    "READ_FLOORS_CLIMBED", "READ_PLANNED_EXERCISE", "READ_POWER", "READ_SPEED", "READ_STEPS",
                    "READ_TOTAL_CALORIES_BURNED", "READ_VO2_MAX", "READ_WHEELCHAIR_PUSHES",
                ),
                HealthDataCategory.BODY to setOf(
                    "READ_BASAL_METABOLIC_RATE", "READ_BODY_FAT", "READ_BODY_WATER_MASS", "READ_BONE_MASS",
                    "READ_HEIGHT", "READ_LEAN_BODY_MASS", "READ_WEIGHT",
                ),
                HealthDataCategory.CYCLE to setOf(
                    "READ_BASAL_BODY_TEMPERATURE", "READ_CERVICAL_MUCUS", "READ_INTERMENSTRUAL_BLEEDING",
                    "READ_MENSTRUATION", "READ_OVULATION_TEST", "READ_SEXUAL_ACTIVITY",
                ),
                HealthDataCategory.NUTRITION to setOf("READ_HYDRATION", "READ_NUTRITION"),
                HealthDataCategory.SLEEP to setOf("READ_SLEEP"),
                HealthDataCategory.VITALS to setOf(
                    "READ_BLOOD_GLUCOSE", "READ_BLOOD_PRESSURE", "READ_BODY_TEMPERATURE", "READ_HEART_RATE",
                    "READ_HEART_RATE_VARIABILITY", "READ_OXYGEN_SATURATION", "READ_RESPIRATORY_RATE",
                    "READ_RESTING_HEART_RATE", "READ_SKIN_TEMPERATURE",
                ),
                HealthDataCategory.WELLNESS to setOf("READ_MINDFULNESS"),
            ),
            actual,
        )
    }

    @Test
    fun plannedExerciseMindfulnessAndSkinTemperatureHaveExplicitFeatureGates() {
        assertEquals(
            mapOf(
                "READ_PLANNED_EXERCISE" to HealthConnectFeatures.FEATURE_PLANNED_EXERCISE,
                "READ_MINDFULNESS" to HealthConnectFeatures.FEATURE_MINDFULNESS_SESSION,
                "READ_SKIN_TEMPERATURE" to HealthConnectFeatures.FEATURE_SKIN_TEMPERATURE,
            ),
            HealthConnectSetup.standardReadTypes.filter { it.featureGate != null }
                .associate { it.suffix to requireNotNull(it.featureGate) },
        )
        val available = mapOf(
            HealthConnectFeatures.FEATURE_PLANNED_EXERCISE to HealthConnectFeatures.FEATURE_STATUS_AVAILABLE,
            HealthConnectFeatures.FEATURE_MINDFULNESS_SESSION to HealthConnectFeatures.FEATURE_STATUS_AVAILABLE,
            HealthConnectFeatures.FEATURE_SKIN_TEMPERATURE to HealthConnectFeatures.FEATURE_STATUS_AVAILABLE,
        )
        assertEquals(HealthConnectSetup.standardPermissions, HealthConnectSetup.availableStandardPermissions(true, available))
        val partialFeatures = available + (HealthConnectFeatures.FEATURE_PLANNED_EXERCISE to HealthConnectFeatures.FEATURE_STATUS_UNAVAILABLE) +
            (HealthConnectFeatures.FEATURE_MINDFULNESS_SESSION to null)
        val availablePermissions = HealthConnectSetup.availableStandardPermissions(true, partialFeatures)
        assertFalse("android.permission.health.READ_PLANNED_EXERCISE" in availablePermissions)
        assertFalse("android.permission.health.READ_MINDFULNESS" in availablePermissions)
        assertTrue("android.permission.health.READ_SKIN_TEMPERATURE" in availablePermissions)
    }

    @Test
    fun bundledRequestReconcilesPartialGrantAgainstFreshProviderState() {
        val requested = setOf(
            "android.permission.health.READ_SLEEP",
            "android.permission.health.READ_HEART_RATE",
            "android.permission.health.READ_WEIGHT",
        )
        val freshGranted = setOf("android.permission.health.READ_HEART_RATE")
        val result = HealthConnectSetup.requestResult(requested, freshGranted)
        assertEquals(freshGranted, result.granted)
        assertEquals(requested - freshGranted, result.denied)
        assertFalse(result.isFullyGranted)
        assertTrue(result.hasAnyGrant)
        assertFalse(hasAllGrantedPermissions(requested, freshGranted))
        assertTrue(
            hasAllGrantedPermissions(
                setOf("android.permission.health.READ_HEART_RATE"),
                freshGranted,
            ),
        )
    }

    @Test
    fun healthConnectReadsRequireEveryExactPermissionToBeGranted() {
        val requiredForReadiness = HealthConnectPermissions.readiness
        assertTrue(hasAllGrantedPermissions(requiredForReadiness, requiredForReadiness))
        assertFalse(
            hasAllGrantedPermissions(
                requiredForReadiness,
                setOf("android.permission.health.READ_HEART_RATE_VARIABILITY"),
            ),
        )
        assertFalse(
            hasAllGrantedPermissions(
                setOf("android.permission.health.READ_SLEEP"),
                emptySet(),
            ),
        )
    }

    @Test
    fun historyIsASeparateFeatureGatedRightAndRevocationDisablesIt() {
        val available = mapOf(
            HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY to HealthConnectFeatures.FEATURE_STATUS_AVAILABLE,
        )
        val unavailable = mapOf(
            HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY to HealthConnectFeatures.FEATURE_STATUS_UNAVAILABLE,
        )
        val historyGrant = setOf(HealthConnectSetup.historyPermission)
        assertFalse(HealthConnectSetup.historyPermission in HealthConnectSetup.standardPermissions)
        assertFalse(HealthConnectSetup.historySupported(true, unavailable))
        assertEquals(
            SetupPermissionStatus.UNSUPPORTED,
            HealthConnectSetup.historyStatus(true, unavailable, historyGrant, statusKnown = true),
        )
        assertTrue(HealthConnectSetup.historySupported(true, available))
        assertEquals(
            SetupPermissionStatus.NOT_GRANTED,
            HealthConnectSetup.historyStatus(true, available, emptySet(), statusKnown = true),
        )
        assertEquals(
            SetupPermissionStatus.GRANTED,
            HealthConnectSetup.historyStatus(true, available, historyGrant, statusKnown = true),
        )
        assertEquals(
            SetupPermissionStatus.NOT_GRANTED,
            HealthConnectSetup.historyStatus(true, available, emptySet(), statusKnown = true),
        )
    }

    @Test
    fun medicalPermissionsAreTwelveGranularRightsAndNeverPartOfStandardBatch() {
        assertEquals(12, HealthConnectSetup.medicalReadTypes.size)
        assertEquals(12, HealthConnectSetup.medicalPermissions.size)
        assertTrue(HealthConnectSetup.standardPermissions.intersect(HealthConnectSetup.medicalPermissions).isEmpty())
        assertEquals(
            setOf(
                "READ_MEDICAL_DATA_ALLERGIES_INTOLERANCES", "READ_MEDICAL_DATA_CONDITIONS",
                "READ_MEDICAL_DATA_LABORATORY_RESULTS", "READ_MEDICAL_DATA_MEDICATIONS",
                "READ_MEDICAL_DATA_PERSONAL_DETAILS", "READ_MEDICAL_DATA_PRACTITIONER_DETAILS",
                "READ_MEDICAL_DATA_PREGNANCY", "READ_MEDICAL_DATA_PROCEDURES",
                "READ_MEDICAL_DATA_SOCIAL_HISTORY", "READ_MEDICAL_DATA_VACCINES",
                "READ_MEDICAL_DATA_VISITS", "READ_MEDICAL_DATA_VITAL_SIGNS",
            ),
            HealthConnectSetup.medicalReadTypes.map { it.suffix }.toSet(),
        )
        assertTrue(HealthConnectSetup.medicalPermissions.all { it.startsWith("android.permission.health.READ_MEDICAL_DATA_") })
        assertTrue(
            HealthConnectSetup.medicalFeatureAvailable(
                sdkAvailable = true,
                featureStatuses = mapOf(
                    HealthConnectFeatures.FEATURE_PERSONAL_HEALTH_RECORD to HealthConnectFeatures.FEATURE_STATUS_AVAILABLE,
                ),
            ),
        )
        assertFalse(
            HealthConnectSetup.medicalFeatureAvailable(
                sdkAvailable = true,
                featureStatuses = mapOf(
                    HealthConnectFeatures.FEATURE_PERSONAL_HEALTH_RECORD to HealthConnectFeatures.FEATURE_STATUS_UNAVAILABLE,
                ),
            ),
        )
        val phrAvailable = mapOf(
            HealthConnectFeatures.FEATURE_PERSONAL_HEALTH_RECORD to HealthConnectFeatures.FEATURE_STATUS_AVAILABLE,
        )
        val medicalType = HealthConnectSetup.medicalReadTypes.first()
        assertEquals(
            SetupPermissionStatus.NOT_GRANTED,
            HealthConnectSetup.medicalStatus(medicalType, true, phrAvailable, emptySet(), statusKnown = true),
        )
        assertEquals(
            SetupPermissionStatus.GRANTED,
            HealthConnectSetup.medicalStatus(medicalType, true, phrAvailable, setOf(medicalType.permission), statusKnown = true),
        )
        assertEquals(
            SetupPermissionStatus.UNSUPPORTED,
            HealthConnectSetup.medicalStatus(
                medicalType,
                true,
                mapOf(HealthConnectFeatures.FEATURE_PERSONAL_HEALTH_RECORD to HealthConnectFeatures.FEATURE_STATUS_UNAVAILABLE),
                emptySet(),
                statusKnown = true,
            ),
        )
    }

    @Test
    fun existingScoreFeaturesKeepTheirOriginalNarrowPermissionSets() {
        assertEquals(setOf("android.permission.health.READ_SLEEP"), HealthConnectPermissions.sleep)
        assertEquals(
            setOf(
                "android.permission.health.READ_HEART_RATE_VARIABILITY",
                "android.permission.health.READ_RESTING_HEART_RATE",
            ),
            HealthConnectPermissions.readiness,
        )
        assertEquals(setOf("android.permission.health.READ_EXERCISE"), HealthConnectPermissions.trainingRecovery)
    }
}
