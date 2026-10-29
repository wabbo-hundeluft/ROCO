package com.wabbo.healthconnectcoach

import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine as Engine
import java.time.Duration
import java.time.Instant

internal const val LAST_SCORE_REFRESH_ATTEMPT="last_score_refresh_attempt"
internal val AUTOMATIC_REFRESH_INTERVAL: Duration=Duration.ofHours(1)

internal fun automaticRefreshDue(lastAttempt: Instant?,now: Instant,manual: Boolean=false): Boolean =
    manual||lastAttempt==null||lastAttempt>now||!now.isBefore(lastAttempt.plus(AUTOMATIC_REFRESH_INTERVAL))

/** Presentation only: retain its original timestamp/model; never write an old value as today's score. */
internal fun withLastSavedEnergy(current: Map<String,Engine.ScoreResult>,saved: List<Engine.ScoreResult>,now: Instant): Map<String,Engine.ScoreResult> {
    if(current[Engine.BODY_BATTERY_ID]?.score!=null) return current
    val last=saved.filter {it.scoreId==Engine.BODY_BATTERY_ID&&it.score!=null&&it.algorithmVersion.startsWith(Engine.MODEL_VERSION+"/")&&it.asOf<=now}.maxByOrNull {it.asOf} ?: return current
    return current+(Engine.BODY_BATTERY_ID to last.copy(status=Engine.Status.stale,stale=true,
        coverage=last.coverage.copy(missingReasons=(last.coverage.missingReasons+"saved_energy_pending_refresh").distinct())))
}
