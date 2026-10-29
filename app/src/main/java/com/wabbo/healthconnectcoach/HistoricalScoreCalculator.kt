package com.wabbo.healthconnectcoach

import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine as Engine
import java.time.Duration
import java.time.Instant

/** Reuse one normalization. Never rebuild all provider records for every historical day. */
internal fun historicalScoreReadout(current: ApprovedWellnessScoreReadout, at: Instant,
    windows: Map<Engine.RecordType, Map<String, Boolean>>, v2Model: Boolean = false): ApprovedWellnessScoreReadout {
    val original = current.normalizedInput
    require(at <= original.asOf)
    val sleeps = original.sleeps.filter { it.end <= at && it.end >= at.minusSeconds(35L * 86400) }
    val selected = sleeps.filter { (original.boundSources["sleep"] == null || it.source == original.boundSources["sleep"]) && Duration.between(it.start, it.end).toMinutes() >= 180 }
    val main = Engine().selectedMainSleep(original.copy(asOf=at,sleeps=sleeps))
    val wakeDate = main?.localWakeDate ?: main?.end?.atZone(original.zone)?.toLocalDate()
    val prior = if (wakeDate == null) emptyList() else selected.filter { (it.localWakeDate ?: it.end.atZone(original.zone).toLocalDate()) < wakeDate }
        .groupBy { it.localWakeDate ?: it.end.atZone(original.zone).toLocalDate() }.map { (date, nights) ->
            val night = nights.maxBy { Duration.between(it.start, it.end) }
            val minute = night.localMidpointMinute ?: night.start.plusSeconds(Duration.between(night.start, night.end).seconds / 2)
                .atZone(original.zone).let { it.hour * 60.0 + it.minute }
            Engine.SleepNight(date, minute, night.source, night.start, night.end, night.startZoneOffset, night.endZoneOffset)
        }.sortedBy { it.date }.takeLast(14)
    val input = original.copy(asOf = at, sleeps = sleeps, priorNights = prior,
        hrv = original.hrv.filter { it.at <= at }, rhr = original.rhr.filter { it.at <= at },
        hrvBaseline = original.hrvBaseline.filter { it.date < at.atZone(original.zone).toLocalDate() },
        rhrBaseline = original.rhrBaseline.filter { it.date < at.atZone(original.zone).toLocalDate() },
        workouts = original.workouts.filter { it.end <= at && it.start >= at.minusSeconds(65L * 86400) },
        steps = original.steps.filter { it.end <= at && it.start >= at.minusSeconds(86400) },
        recordTypeWindowCompleteness = windows)
    val engine = Engine()
    val scores = listOf(engine.scoreSleep(input), engine.scoreBodyBattery(input), if(v2Model) engine.scoreWeeklyLoad(input, input.weeklyTargetLoad) else engine.scoreSessionLoad(input)).associateBy { it.scoreId }
        .mapValues { (id, score) ->
            val duplicate = current.scores[id]?.takeIf { it.coverage.missingReasons.any { reason -> reason.startsWith("duplicate_") } }
            // Conflicting duplicate records were rejected by normalization. Do not turn them into valid historical data.
            duplicate?.copy(asOf = at) ?: score
        }
    return ApprovedWellnessScoreReadout(scores, current.observedOrigins, input, emptyList())
}
