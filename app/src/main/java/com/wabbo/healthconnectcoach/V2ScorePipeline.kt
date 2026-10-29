package com.wabbo.healthconnectcoach

import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine as Engine

/** One calculation contract for both connectors, live reads and historical reconstruction. */
internal fun ApprovedWellnessScoreReadout.withV2Goals(goals: TrainingGoals?, personalProfile: com.wabbo.healthconnectcoach.score.PersonalScoreProfile = normalizedInput.personalProfile): ApprovedWellnessScoreReadout {
    val input = normalizedInput.copy(sleepGoalMinutes = goals?.sleepMinutes?.toDouble() ?: 480.0, weeklyTargetLoad = goals?.weeklyLoadTarget, personalProfile = personalProfile, weeklyGoalAdjustments = goals?.weeklyLoadAdjustments.orEmpty())
    val engine = Engine()
    val next = listOf(engine.scoreSleep(input), engine.scoreBodyBattery(input), engine.scoreWeeklyLoad(input, goals?.weeklyLoadTarget)).associateBy { it.scoreId }
    return copy(normalizedInput = input, scores = next.mapValues { (id, result) ->
        scores[id]?.takeIf { it.coverage.missingReasons.any { reason -> reason.startsWith("duplicate_") } } ?: result
    })
}

internal fun weeklyGuidance(score: Engine.ScoreResult?, plan: TrainingPlan?, today: java.time.LocalDate): String {
    if (plan == null && score?.components?.get("ADAPTIVE")!=1) return "Lass uns zuerst deine Trainingstage und dein Wochenziel gemeinsam festlegen."
    val value = score?.score ?: return "Für die Wochenbelastung fehlen noch Daten. Dein Trainingsplan bleibt sichtbar."
    val todayPlanned = plan?.sessions.orEmpty().any { it.date == today }
    return when {
        value > 100 -> "$value Belastungspunkte: Du liegst über deinem persönlichen Wochenziel von 100. Mehr ist jetzt kein Bonus. Plane Erholung ein."
        value == 100 -> "Dein Wochenziel ist erreicht: 100 Punkte. Gut gemacht — Erholung gehört jetzt dazu."
        !todayPlanned -> "$value von 100 Belastungspunkten diese Woche. Heute ist kein Training geplant. Genieße deinen Freiraum."
        value >= 80 -> "Schon $value von 100 Punkten. Du näherst dich deinem Wochenziel. Bleib bei deinem Plan."
        else -> "$value von 100 Belastungspunkten erreicht. Deine nächste geplante Einheit bringt dich weiter — in deinem Tempo."
    }
}

/** Cached values never cross dates or formula revisions as if they were current. */
internal fun canReuseCachedScore(fresh: Engine.ScoreResult?, cached: Engine.ScoreResult?): Boolean =
    fresh?.score==null && cached?.score!=null && fresh?.algorithmVersion==cached.algorithmVersion &&
        fresh?.asOf?.atZone(java.time.ZoneId.systemDefault())?.toLocalDate()==cached.asOf.atZone(java.time.ZoneId.systemDefault()).toLocalDate() &&
        fresh?.coverage?.missingReasons.orEmpty().none {it in setOf("weekly_target_missing","main_sleep_missing","main_sleep_stale")}

internal fun scoreForDisplayDay(score: Engine.ScoreResult, day: java.time.LocalDate, zone: java.time.ZoneId): Engine.ScoreResult =
    if(score.asOf.atZone(zone).toLocalDate()==day && score.algorithmVersion.startsWith(Engine.MODEL_VERSION+"/") &&
        (score.scoreId!=Engine.SESSION_LOAD_ID||score.algorithmVersion==Engine.WEEKLY_MODEL_VERSION)) score
    else score.copy(score=null,status=Engine.Status.insufficient_data,stale=true,
        coverage=score.coverage.copy(missingReasons=score.coverage.missingReasons+"new_day_or_model_pending"))

internal fun weeklyTargetValue(score: Engine.ScoreResult): Double? =
    (score.components["TARGET_X100"]?.div(100.0) ?: score.components["TARGET"]?.toDouble())?.takeIf {it>0}


/** An explicit adjustment belongs to a calendar week; measured load and adaptive base stay intact. */
internal fun adjustWeeklyLoadGoal(score: Engine.ScoreResult,percent: Int): Engine.ScoreResult {
    require(percent in 75..125)
    val base=weeklyTargetValue(score) ?: return score
    val raw=score.rawIndex ?: return score
    if(score.score==null) return score
    val target=base*percent/100.0
    return score.copy(score=Engine.halfUp(raw*100/percent).toInt(),rawIndex=raw*100/percent,
        components=score.components+mapOf("BASE_TARGET_X100" to Engine.halfUp(base*100).toInt(),"GOAL_PERCENT" to percent,
            "TARGET_X100" to Engine.halfUp(target*100).toInt(),"TARGET" to Engine.halfUp(target).toInt()))
}
