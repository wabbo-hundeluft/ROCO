package com.wabbo.healthconnectcoach

import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

/** A confirmed reference, not a second workout or an addition to measured minutes. */
internal data class RecordedTraining(val id: String, val connector: String, val title: String,
    val start: Instant, val end: Instant, val minutes: Double) {
    fun validated(): RecordedTraining {
        require(id.isNotBlank() && id.length<=1000 && connector in setOf("Google Health","Health Connect"))
        require(title.isNotBlank() && title.length<=200 && end>start && minutes.isFinite() && minutes>0 && minutes<=1440)
        return this
    }
}
internal fun encodeRecordedTraining(a: RecordedTraining)=JSONObject().put("id",a.id).put("connector",a.connector)
    .put("title",a.title).put("start",a.start.toString()).put("end",a.end.toString()).put("minutes",a.minutes)
internal fun decodeRecordedTraining(o: JSONObject)=RecordedTraining(o.getString("id"),o.getString("connector"),o.getString("title"),
    Instant.parse(o.getString("start")),Instant.parse(o.getString("end")),o.getDouble("minutes")).validated()

internal fun sameRecordedTraining(a: RecordedTraining,b: RecordedTraining): Boolean {
    if(a.connector==b.connector && a.id==b.id) return true
    // Reimports, changed provider IDs and connector fallback must not complete a second plan unit.
    val overlap=Duration.between(maxOf(a.start,b.start),minOf(a.end,b.end)).seconds.coerceAtLeast(0)
    val shorter=minOf(Duration.between(a.start,a.end).seconds,Duration.between(b.start,b.end).seconds)
    return shorter>0 && overlap.toDouble()/shorter>=.8
}
private fun sport(title: String): String? {
    val words=title.lowercase(Locale.ROOT).split(Regex("[^\\p{L}]+"))
    val types=mapOf("run" to setOf("run","running","joggen","laufen","lauf"),
        "walk" to setOf("walk","walking","spaziergang","gehen","wandern","hiking"),
        "cycle" to setOf("cycling","cycle","bike","radfahren","rad","fahrrad"),
        "swim" to setOf("swim","swimming","schwimmen"),
        "strength" to setOf("strength","kraft","krafttraining","weightlifting"),
        "yoga" to setOf("yoga","pilates"))
    return types.entries.firstOrNull {(_,aliases)->words.any {it in aliases}}?.key
}
internal fun recordedSuggestions(plan: TrainingPlan,sessionId: String,days: List<DayInsight>,connector: String,
    today: LocalDate,zone: ZoneId): List<RecordedTraining> {
    val session=plan.sessions.singleOrNull {it.id==sessionId} ?: return emptyList()
    if(session.completed || session.date>today) return emptyList()
    val used=plan.sessions.mapNotNull {it.recordedActivity}
    val expectedSport=sport(session.title)
    val rows=days.filter {it.sources["_connector"]==connector}
        .groupBy {it.date}.values.map {it.maxBy {day->day.calculatedAt}}.flatMap {day->day.activities.mapNotNull {a->
        val ref=RecordedTraining(a.id,connector,a.title,a.start,a.end,a.minutes)
        ref.takeIf {runCatching {it.validated()}.isSuccess && it.start.atZone(zone).toLocalDate()==session.date &&
            it.end.atZone(zone).toLocalDate()<=today && it.end<=day.calculatedAt && it.minutes/session.minutes in .5..3.0 &&
            (expectedSport==null || sport(it.title)==null || expectedSport==sport(it.title)) &&
            used.none {previous->sameRecordedTraining(previous,it)}}
    }}.sortedBy {abs(it.minutes-session.minutes)}
    val unique=mutableListOf<RecordedTraining>()
    rows.forEach {row->if(unique.none {sameRecordedTraining(it,row)}) unique+=row}
    return unique.take(5)
}
internal fun linkRecordedTraining(plan: TrainingPlan,sessionId: String,activity: RecordedTraining,
    days: List<DayInsight>,connector: String,today: LocalDate,zone: ZoneId): TrainingPlan {
    require(recordedSuggestions(plan,sessionId,days,connector,today,zone).any {it==activity}) {
        "Die Aufzeichnung ist nicht mehr verfügbar oder bereits zugeordnet. Bitte den Vorschlag erneut öffnen."
    }
    return plan.copy(revision=plan.revision+1,sessions=plan.sessions.map {if(it.id==sessionId) it.copy(completed=true,recordedActivity=activity) else it})
}

internal data class WeekTrainingSummary(val minutes: Double,val measuredDays: Int,val expectedDays: Int)
internal fun weekTrainingSummary(days: List<DayInsight>,at: LocalDate,today: LocalDate,connector: String): WeekTrainingSummary {
    val monday=at.with(java.time.DayOfWeek.MONDAY)
    val last=minOf(monday.plusDays(6),today)
    val expected=if(last<monday) 0 else Duration.between(monday.atStartOfDay(),last.plusDays(1).atStartOfDay()).toDays().toInt()
    val rows=days.filter {it.date in monday..last && it.sources["_connector"]==connector}
        .groupBy {it.date}.values.map {it.maxBy {day->day.calculatedAt}}
    val values=rows.mapNotNull {it.metrics["workout_minutes"]?.takeIf {m->m.isFinite()&&m>=0}}
    return WeekTrainingSummary(values.sum(),values.size,expected)
}
