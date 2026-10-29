package com.wabbo.healthconnectcoach

import com.example.healthcoach.score.contract.ScoreResult
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime
import java.util.Locale

internal enum class SnapshotFreshness {
    CURRENT,
    STALE,
    FRESHNESS_UNVERIFIABLE,
}

internal data class FeatureReadSnapshot(
    val recordCount: Int,
    val readAt: LocalDateTime,
    val score: ScoreResult,
    val scoreOnlySnapshot: Boolean = false,
    val freshness: SnapshotFreshness = SnapshotFreshness.CURRENT,
)

internal fun Map<String, FeatureReadSnapshot>.markFeatureSnapshotStale(
    featureId: String,
): Map<String, FeatureReadSnapshot> {
    val previous = this[featureId] ?: return this
    if (previous.freshness == SnapshotFreshness.STALE) return this
    return this + (featureId to previous.copy(freshness = SnapshotFreshness.STALE))
}

internal fun Map<String, FeatureReadSnapshot>.markSnapshotsFreshnessUnverifiable(): Map<String, FeatureReadSnapshot> {
    if (isEmpty() || values.all { it.freshness == SnapshotFreshness.FRESHNESS_UNVERIFIABLE }) return this
    return mapValues { (_, snapshot) ->
        if (snapshot.freshness == SnapshotFreshness.FRESHNESS_UNVERIFIABLE) {
            snapshot
        } else {
            snapshot.copy(freshness = SnapshotFreshness.FRESHNESS_UNVERIFIABLE)
        }
    }
}

internal fun snapshotFreshnessLabel(snapshot: FeatureReadSnapshot): String? =
    when (snapshot.freshness) {
        SnapshotFreshness.CURRENT -> null
        SnapshotFreshness.STALE ->
            "Veraltet · letzter erfolgreicher Health-Connect-Read am ${snapshot.readAt.format(readAtFormat())}."
        SnapshotFreshness.FRESHNESS_UNVERIFIABLE ->
            "Veraltet · Freshness nicht prüfbar · letzter erfolgreicher Health-Connect-Read am ${snapshot.readAt.format(readAtFormat())}."
    }

private fun readAtFormat() = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm", Locale.getDefault())
