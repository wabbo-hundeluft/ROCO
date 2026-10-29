package com.wabbo.healthconnectcoach

import java.time.LocalDate
import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine as Engine

/** A chart uses one connector, and one metric origin or model version throughout. */
internal fun comparableTrendDays(days: List<DayInsight>,connector: String,through: LocalDate,key: String?=null,scoreId: String?=null): List<DayInsight> {
    require((key==null)!=(scoreId==null))
    val eligible=days.filter {it.date<=through&&it.sources["_connector"]==connector}
        .groupBy {it.date}.values.map {it.maxBy {day->day.calculatedAt}}.sortedBy {it.date}
    if(key!=null) {
        val definition=vitalDefinitions.first {it.key==key}
        val latest=eligible.lastOrNull {finiteVital(it,key)!=null} ?: return emptyList()
        return sameVitalDays(eligible,definition,latest)
    }
    fun score(day: DayInsight)=day.scores[scoreId]?.takeIf {it.score!=null && (scoreId!=Engine.SESSION_LOAD_ID||it.algorithmVersion.endsWith("/weekly"))}
    val version=eligible.lastOrNull {score(it)!=null}?.let {score(it)!!.algorithmVersion} ?: return emptyList()
    val profile=eligible.lastOrNull {score(it)!=null}?.let {score(it)?.components?.get("PROFILE_KEY")}
    return eligible.filter {score(it)?.algorithmVersion==version && score(it)?.components?.get("PROFILE_KEY")==profile}
}
