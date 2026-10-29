package com.wabbo.healthconnectcoach

import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.feature.ExperimentalMindfulnessSessionApi
import androidx.health.connect.client.feature.ExperimentalPersonalHealthRecordApi
import androidx.health.connect.client.permission.HealthPermission

internal enum class HealthDataCategory(
    val title: String,
    val explanation: String,
    val sensitivity: String,
) {
    ACTIVITY(
        title = "Aktivität und Training",
        explanation = "Für eine getrennte Aktivitäts- und Trainingsübersicht.",
        sensitivity = "Aktivitätsdaten",
    ),
    BODY(
        title = "Körpermaße",
        explanation = "Für eine getrennte Körpermessungs- und Verlaufsansicht.",
        sensitivity = "Körperdaten · sensibel",
    ),
    CYCLE(
        title = "Zyklus und reproduktive Gesundheit",
        explanation = "Zyklus-, Fruchtbarkeits- und Sexualitätsangaben.",
        sensitivity = "Reproduktive Daten · besonders sensibel",
    ),
    NUTRITION(
        title = "Ernährung und Flüssigkeit",
        explanation = "Nur tatsächlich protokollierte Mahlzeiten und Trinkmengen.",
        sensitivity = "Ernährungsdaten · sensibel",
    ),
    SLEEP(
        title = "Schlaf",
        explanation = "Für eine getrennte Schlafübersicht und verfügbare Schlafstadien.",
        sensitivity = "Gesundheitsdaten · sensibel",
    ),
    VITALS(
        title = "Herz und Vitalwerte",
        explanation = "Für getrennte Verläufe einzelner Herz- und Vitalmesswerte.",
        sensitivity = "Gesundheitsdaten · besonders sensibel",
    ),
    WELLNESS(
        title = "Achtsamkeit",
        explanation = "Für einen getrennten Verlauf protokollierter Achtsamkeitssitzungen.",
        sensitivity = "Wellnessdaten · sensibel",
    ),
}

internal data class StandardReadType(
    val suffix: String,
    val title: String,
    val purpose: String,
    val category: HealthDataCategory,
    val featureGate: Int? = null,
) {
    val permission: String get() = "android.permission.health.$suffix"
}

internal data class MedicalReadType(
    val permission: String,
    val title: String,
    val purpose: String,
) {
    val suffix: String get() = permission.removePrefix("android.permission.health.")
}

internal enum class SetupPermissionStatus(val label: String) {
    GRANTED("Freigegeben"),
    NOT_GRANTED("Nicht freigegeben"),
    UNSUPPORTED("Nicht unterstützt"),
    UNKNOWN("Status nicht geprüft"),
}

@OptIn(ExperimentalMindfulnessSessionApi::class, ExperimentalPersonalHealthRecordApi::class)
internal object HealthConnectSetup {
    val standardReadTypes: List<StandardReadType> = listOf(
        StandardReadType("READ_ACTIVE_CALORIES_BURNED", "Aktive Kalorien", "Aktivitätskalorien im Verlauf anzeigen.", HealthDataCategory.ACTIVITY),
        StandardReadType("READ_EXERCISE", "Trainingseinheiten und Radfahrkadenz", "Trainingseinheiten und verfügbare Kadenzdaten getrennt anzeigen.", HealthDataCategory.ACTIVITY),
        StandardReadType("READ_DISTANCE", "Distanz", "Zurückgelegte Strecken getrennt anzeigen.", HealthDataCategory.ACTIVITY),
        StandardReadType("READ_ELEVATION_GAINED", "Höhenmeter", "Aufstiege und Höhenmeter getrennt anzeigen.", HealthDataCategory.ACTIVITY),
        StandardReadType("READ_FLOORS_CLIMBED", "Etagen", "Etagenaufstiege getrennt anzeigen.", HealthDataCategory.ACTIVITY),
        StandardReadType("READ_PLANNED_EXERCISE", "Geplante Trainingseinheiten", "Geplante Einheiten getrennt von absolvierten Trainings anzeigen.", HealthDataCategory.ACTIVITY, HealthConnectFeatures.FEATURE_PLANNED_EXERCISE),
        StandardReadType("READ_POWER", "Leistung", "Leistungswerte aus kompatiblen Trainingsquellen getrennt anzeigen.", HealthDataCategory.ACTIVITY),
        StandardReadType("READ_SPEED", "Geschwindigkeit", "Geschwindigkeitswerte aus kompatiblen Trainingsquellen getrennt anzeigen.", HealthDataCategory.ACTIVITY),
        StandardReadType("READ_STEPS", "Schritte und Schrittfrequenz", "Schrittzahl und verfügbare Schrittfrequenz getrennt anzeigen.", HealthDataCategory.ACTIVITY),
        StandardReadType("READ_TOTAL_CALORIES_BURNED", "Gesamtkalorien", "Gesamtkalorien getrennt anzeigen.", HealthDataCategory.ACTIVITY),
        StandardReadType("READ_VO2_MAX", "VO₂max", "Vorhandene VO₂max-Messwerte getrennt anzeigen.", HealthDataCategory.ACTIVITY),
        StandardReadType("READ_WHEELCHAIR_PUSHES", "Rollstuhl-Schübe", "Erfasste Rollstuhl-Schübe getrennt anzeigen.", HealthDataCategory.ACTIVITY),
        StandardReadType("READ_BASAL_METABOLIC_RATE", "Grundumsatz", "Grundumsatz-Messwerte getrennt anzeigen.", HealthDataCategory.BODY),
        StandardReadType("READ_BODY_FAT", "Körperfett", "Körperfettmessungen getrennt anzeigen.", HealthDataCategory.BODY),
        StandardReadType("READ_BODY_WATER_MASS", "Körperwassermasse", "Körperwassermessungen getrennt anzeigen.", HealthDataCategory.BODY),
        StandardReadType("READ_BONE_MASS", "Knochenmasse", "Knochenmassmessungen getrennt anzeigen.", HealthDataCategory.BODY),
        StandardReadType("READ_HEIGHT", "Körpergröße", "Körpergrößenmessungen getrennt anzeigen.", HealthDataCategory.BODY),
        StandardReadType("READ_LEAN_BODY_MASS", "Magere Körpermasse", "Messungen der mageren Körpermasse getrennt anzeigen.", HealthDataCategory.BODY),
        StandardReadType("READ_WEIGHT", "Gewicht", "Gewichtsmessungen getrennt anzeigen.", HealthDataCategory.BODY),
        StandardReadType("READ_BASAL_BODY_TEMPERATURE", "Basaltemperatur", "Basaltemperatur im Zyklusverlauf getrennt anzeigen.", HealthDataCategory.CYCLE),
        StandardReadType("READ_CERVICAL_MUCUS", "Zervixschleim", "Zervixschleim-Beobachtungen getrennt anzeigen.", HealthDataCategory.CYCLE),
        StandardReadType("READ_INTERMENSTRUAL_BLEEDING", "Zwischenblutungen", "Einträge zu Zwischenblutungen getrennt anzeigen.", HealthDataCategory.CYCLE),
        StandardReadType("READ_MENSTRUATION", "Menstruation", "Menstruations-Einträge getrennt anzeigen.", HealthDataCategory.CYCLE),
        StandardReadType("READ_OVULATION_TEST", "Ovulationstests", "Ovulationstest-Ergebnisse getrennt anzeigen.", HealthDataCategory.CYCLE),
        StandardReadType("READ_SEXUAL_ACTIVITY", "Sexuelle Aktivität", "Einträge zu sexueller Aktivität getrennt anzeigen.", HealthDataCategory.CYCLE),
        StandardReadType("READ_HYDRATION", "Flüssigkeitsaufnahme", "Protokollierte Trinkmengen getrennt anzeigen; fehlende Einträge werden nicht als zu geringe Aufnahme gedeutet.", HealthDataCategory.NUTRITION),
        StandardReadType("READ_NUTRITION", "Ernährung", "Protokollierte Nährstoff- und Mahlzeiteneinträge getrennt anzeigen; fehlende Einträge werden nicht bewertet.", HealthDataCategory.NUTRITION),
        StandardReadType("READ_SLEEP", "Schlafsitzungen", "Schlafsitzungen und verfügbare Schlafstadien getrennt anzeigen.", HealthDataCategory.SLEEP),
        StandardReadType("READ_BLOOD_GLUCOSE", "Blutzucker", "Blutzuckermessungen getrennt anzeigen.", HealthDataCategory.VITALS),
        StandardReadType("READ_BLOOD_PRESSURE", "Blutdruck", "Blutdruckmessungen getrennt anzeigen.", HealthDataCategory.VITALS),
        StandardReadType("READ_BODY_TEMPERATURE", "Körpertemperatur", "Körpertemperaturmessungen getrennt anzeigen.", HealthDataCategory.VITALS),
        StandardReadType("READ_HEART_RATE", "Herzfrequenz", "Herzfrequenz-Messungen getrennt anzeigen.", HealthDataCategory.VITALS),
        StandardReadType("READ_HEART_RATE_VARIABILITY", "Herzfrequenzvariabilität (HRV)", "HRV-Messungen getrennt anzeigen.", HealthDataCategory.VITALS),
        StandardReadType("READ_OXYGEN_SATURATION", "Sauerstoffsättigung", "Sauerstoffsättigung getrennt anzeigen.", HealthDataCategory.VITALS),
        StandardReadType("READ_RESPIRATORY_RATE", "Atemfrequenz", "Atemfrequenzmessungen getrennt anzeigen.", HealthDataCategory.VITALS),
        StandardReadType("READ_RESTING_HEART_RATE", "Ruhepuls", "Ruhepuls-Messungen getrennt anzeigen.", HealthDataCategory.VITALS),
        StandardReadType("READ_SKIN_TEMPERATURE", "Hauttemperatur", "Hauttemperatur-Verläufe aus kompatiblen Geräten getrennt anzeigen.", HealthDataCategory.VITALS, HealthConnectFeatures.FEATURE_SKIN_TEMPERATURE),
        StandardReadType("READ_MINDFULNESS", "Achtsamkeitssitzungen", "Protokollierte Achtsamkeitssitzungen getrennt anzeigen.", HealthDataCategory.WELLNESS, HealthConnectFeatures.FEATURE_MINDFULNESS_SESSION),
    )

    val standardPermissions: Set<String> = standardReadTypes.mapTo(linkedSetOf()) { it.permission }

    val historyPermission: String = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY

    val medicalReadTypes: List<MedicalReadType> = listOf(
        MedicalReadType(HealthPermission.PERMISSION_READ_MEDICAL_DATA_ALLERGIES_INTOLERANCES, "Allergien und Unverträglichkeiten", "Allergie- und Unverträglichkeitseinträge getrennt anzeigen."),
        MedicalReadType(HealthPermission.PERMISSION_READ_MEDICAL_DATA_CONDITIONS, "Erkrankungen", "Medizinische Diagnosen und Erkrankungseinträge getrennt anzeigen."),
        MedicalReadType(HealthPermission.PERMISSION_READ_MEDICAL_DATA_LABORATORY_RESULTS, "Laborergebnisse", "Medizinische Laborwerte getrennt anzeigen."),
        MedicalReadType(HealthPermission.PERMISSION_READ_MEDICAL_DATA_MEDICATIONS, "Medikamente", "Medikamenteninformationen aus Medical Records getrennt anzeigen."),
        MedicalReadType(HealthPermission.PERMISSION_READ_MEDICAL_DATA_PERSONAL_DETAILS, "Persönliche Angaben", "Persönliche Angaben aus dem medizinischen Datensatz getrennt anzeigen."),
        MedicalReadType(HealthPermission.PERMISSION_READ_MEDICAL_DATA_PRACTITIONER_DETAILS, "Behandlerangaben", "Angaben zu medizinischen Behandlern getrennt anzeigen."),
        MedicalReadType(HealthPermission.PERMISSION_READ_MEDICAL_DATA_PREGNANCY, "Schwangerschaft", "Schwangerschaftsinformationen besonders geschützt getrennt anzeigen."),
        MedicalReadType(HealthPermission.PERMISSION_READ_MEDICAL_DATA_PROCEDURES, "Eingriffe", "Medizinische Verfahren und Eingriffe getrennt anzeigen."),
        MedicalReadType(HealthPermission.PERMISSION_READ_MEDICAL_DATA_SOCIAL_HISTORY, "Sozialanamnese", "Medizinische Sozialanamnese getrennt anzeigen."),
        MedicalReadType(HealthPermission.PERMISSION_READ_MEDICAL_DATA_VACCINES, "Impfungen", "Impfungsinformationen getrennt anzeigen."),
        MedicalReadType(HealthPermission.PERMISSION_READ_MEDICAL_DATA_VISITS, "Besuche", "Medizinische Besuche getrennt anzeigen."),
        MedicalReadType(HealthPermission.PERMISSION_READ_MEDICAL_DATA_VITAL_SIGNS, "Medizinische Vitalzeichen", "In Medical Records gespeicherte Vitalzeichen getrennt anzeigen."),
    )

    val medicalPermissions: Set<String> = medicalReadTypes.mapTo(linkedSetOf()) { it.permission }

    val featureGates: Set<Int> = setOf(
        HealthConnectFeatures.FEATURE_PLANNED_EXERCISE,
        HealthConnectFeatures.FEATURE_MINDFULNESS_SESSION,
        HealthConnectFeatures.FEATURE_SKIN_TEMPERATURE,
    )

    fun availableStandardPermissions(
        sdkAvailable: Boolean,
        featureStatuses: Map<Int, Int?>,
    ): Set<String> = standardReadTypes.asSequence()
        .filter { isSupported(it, sdkAvailable, featureStatuses) }
        .map { it.permission }
        .toCollection(linkedSetOf())

    fun isSupported(
        type: StandardReadType,
        sdkAvailable: Boolean,
        featureStatuses: Map<Int, Int?>,
    ): Boolean {
        if (!sdkAvailable) return false
        val gate = type.featureGate ?: return true
        return featureStatuses[gate] == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }

    fun status(
        type: StandardReadType,
        sdkAvailable: Boolean,
        featureStatuses: Map<Int, Int?>,
        grantedPermissions: Set<String>,
        statusKnown: Boolean,
    ): SetupPermissionStatus {
        if (!sdkAvailable) return SetupPermissionStatus.UNSUPPORTED
        val gate = type.featureGate
        if (gate != null) {
            val featureStatus = featureStatuses[gate]
            if (featureStatus == null) return SetupPermissionStatus.UNKNOWN
            if (featureStatus != HealthConnectFeatures.FEATURE_STATUS_AVAILABLE) {
                return SetupPermissionStatus.UNSUPPORTED
            }
        }
        if (!statusKnown) return SetupPermissionStatus.UNKNOWN
        return if (type.permission in grantedPermissions) {
            SetupPermissionStatus.GRANTED
        } else {
            SetupPermissionStatus.NOT_GRANTED
        }
    }

    fun historySupported(sdkAvailable: Boolean, featureStatuses: Map<Int, Int?>): Boolean =
        sdkAvailable && featureStatuses[HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY] ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE

    fun medicalFeatureAvailable(sdkAvailable: Boolean, featureStatuses: Map<Int, Int?>): Boolean =
        sdkAvailable && featureStatuses[HealthConnectFeatures.FEATURE_PERSONAL_HEALTH_RECORD] ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE

    fun medicalStatus(
        type: MedicalReadType,
        sdkAvailable: Boolean,
        featureStatuses: Map<Int, Int?>,
        grantedPermissions: Set<String>,
        statusKnown: Boolean,
    ): SetupPermissionStatus = when {
        !sdkAvailable -> SetupPermissionStatus.UNSUPPORTED
        featureStatuses[HealthConnectFeatures.FEATURE_PERSONAL_HEALTH_RECORD] == null -> SetupPermissionStatus.UNKNOWN
        !medicalFeatureAvailable(sdkAvailable, featureStatuses) -> SetupPermissionStatus.UNSUPPORTED
        !statusKnown -> SetupPermissionStatus.UNKNOWN
        type.permission in grantedPermissions -> SetupPermissionStatus.GRANTED
        else -> SetupPermissionStatus.NOT_GRANTED
    }

    fun historyStatus(
        sdkAvailable: Boolean,
        featureStatuses: Map<Int, Int?>,
        grantedPermissions: Set<String>,
        statusKnown: Boolean,
    ): SetupPermissionStatus = when {
        !sdkAvailable -> SetupPermissionStatus.UNSUPPORTED
        featureStatuses[HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY] == null -> SetupPermissionStatus.UNKNOWN
        !historySupported(sdkAvailable, featureStatuses) -> SetupPermissionStatus.UNSUPPORTED
        !statusKnown -> SetupPermissionStatus.UNKNOWN
        historyPermission in grantedPermissions -> SetupPermissionStatus.GRANTED
        else -> SetupPermissionStatus.NOT_GRANTED
    }

    fun requestResult(requestedPermissions: Set<String>, currentGrantedPermissions: Set<String>): PermissionRequestResult =
        PermissionRequestResult(
            requested = requestedPermissions,
            granted = requestedPermissions.intersect(currentGrantedPermissions),
            denied = requestedPermissions - currentGrantedPermissions,
        )
}

internal data class PermissionRequestResult(
    val requested: Set<String>,
    val granted: Set<String>,
    val denied: Set<String>,
) {
    val hasAnyGrant: Boolean get() = granted.isNotEmpty()
    val isFullyGranted: Boolean get() = requested.isNotEmpty() && denied.isEmpty()
}

internal fun hasAllGrantedPermissions(requiredPermissions: Set<String>, currentGrantedPermissions: Set<String>): Boolean =
    currentGrantedPermissions.containsAll(requiredPermissions)
