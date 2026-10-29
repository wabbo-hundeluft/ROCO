package com.wabbo.healthconnectcoach

import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine as Engine

/** Anchor to the displayed night, rather than silently changing origin or model. */
internal fun comparableSleepHistory(days: List<DayInsight>,selected: DayInsight?): List<DayInsight> {
    selected ?: return emptyList()
    val connector=selected.sources["_connector"]?.takeIf {it.isNotBlank()} ?: return emptyList()
    val origin=selected.sources["sleep"]?.takeIf {it.isNotBlank()} ?: return emptyList()
    val version=selected.scores[Engine.SLEEP_ID]?.algorithmVersion ?: return emptyList()
    return days.filter {it.date<=selected.date&&it.sources["_connector"]==connector}
        .groupBy {it.date}.values.map {it.maxBy {day->day.calculatedAt}}
        .filter {it.sources["sleep"]==origin&&it.scores[Engine.SLEEP_ID]?.algorithmVersion==version &&
            it.scores[Engine.SLEEP_ID]?.components?.get("PROFILE_KEY")==selected.scores[Engine.SLEEP_ID]?.components?.get("PROFILE_KEY")}
        .sortedBy {it.date}
}
