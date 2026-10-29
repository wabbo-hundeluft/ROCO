package com.wabbo.healthconnectcoach

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** All locally stored product data, never provider credentials or unrestricted network/file access. */
internal data class CoachDataAccess(val days: List<DayInsight>,val journal: WellnessJournal,val plan: TrainingPlan?,val today: LocalDate,val displayScores: Map<String,com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine.ScoreResult> = emptyMap(),val memory:CoachMemory=CoachMemory(enabled=false),val nightPlan:NightPlan?=null)
internal fun coachDataDescription(data: CoachDataAccess)=buildString {
    append("Du darfst bei Bedarf read_app_data für alle in der App gespeicherten Daten verwenden. ")
    append("Messwert-Tage: ${data.days.minOfOrNull {it.date}} bis ${data.days.maxOfOrNull {it.date}}. ")
    append("Aktuell angezeigte Scores (gespeicherte Werte behalten ihr ursprüngliches Datum): "+data.displayScores.values.map {mapOf("id" to it.scoreId,"value" to it.score,"asOf" to it.asOf,"stale" to it.stale)}+". ")
    append("Logbuch: ${data.journal.entries.size} Tage. Verhalten und folgende Nacht werden getrennt datiert. ")
    append("Aktive Fragen (Daten): "+data.journal.habits.filter {it.id in data.journal.activeIds}.map {mapOf("id" to it.id,"name" to it.title,"kind" to it.kind.name,"unit" to it.unit)})
    append(" Nutze summary für längere Zeiträume; Detaildaten sind seitenweise verfügbar. Lies Daten vor einer Planänderung, nicht zusammen mit Schreibaktionen im selben Werkzeugschritt. Erfinde keine Informationen außerhalb des verfügbaren Zeitraums.")
}
internal fun coachReadTool()=JSONObject().put("type","function").put("function",JSONObject()
    .put("name","read_app_data").put("description","Liest alle gespeicherten App-Daten in begrenzten Seiten: summary, current_scores, daily, activities, sleep_stages, energy_curve, journal, journal_questions, journal_insights, plan. Keine externen Rohdaten, keine Schlüssel. Beachtet tatsächliche Tage und Quellen. Für große Zeiträume summary zuerst nutzen; next_page bei Details beachten.")
    .put("parameters",JSONObject().put("type","object").put("additionalProperties",false).put("properties",JSONObject()
        .put("section",JSONObject().put("type","string").put("enum",JSONArray(listOf("summary","current_scores","daily","activities","sleep_stages","energy_curve","journal","journal_questions","journal_insights","plan"))))
        .put("from_date",JSONObject().put("type","string").put("description","Inklusive JJJJ-MM-TT"))
        .put("to_date",JSONObject().put("type","string").put("description","Inklusive JJJJ-MM-TT"))
        .put("page",JSONObject().put("type","integer").put("minimum",0))
        .put("habit_id",JSONObject().put("type","string").put("description","Leer für alle aktiven Fragen, sonst exakte Frage-ID für journal_insights.")))
        .put("required",JSONArray(listOf("section","from_date","to_date","page","habit_id")))))

internal suspend fun readCoachAppData(data: CoachDataAccess,args: JSONObject): JSONObject=withContext(Dispatchers.Default) {
    require(args.keys().asSequence().toSet()==setOf("section","from_date","to_date","page","habit_id")) {"Die Datenabfrage enthält ungültige Felder."}
    val from=LocalDate.parse(args.getString("from_date"));val to=LocalDate.parse(args.getString("to_date"));val section=args.getString("section")
    require(from>=data.today.minusDays(729)&&to>=from&&to<=if(section=="plan") data.today.plusDays(90) else data.today) {"Der angefragte Zeitraum ist nicht verfügbar."}
    val rawPage=args.get("page");require(rawPage is Number&&rawPage.toDouble()%1.0==0.0&&rawPage.toDouble() in 0.0..10000.0)
    val page=rawPage.toInt();val selected=data.days.filter {it.date in from..to}.sortedBy {it.date}
    fun scoreJson(s: com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine.ScoreResult)=JSONObject().put("id",s.scoreId).put("value",s.score ?: JSONObject.NULL).put("status",s.status.name).put("stale",s.stale).put("asOf",s.asOf.toString()).put("model",s.algorithmVersion).put("components",JSONObject(s.components)).put("missing",JSONArray(s.coverage.missingReasons))
    fun dayJson(d: DayInsight)=JSONObject().put("date",d.date.toString()).put("calculatedAt",d.calculatedAt.toString()).put("reconstructed",d.reconstructed)
        .put("scores",JSONArray(d.scores.values.map(::scoreJson))).put("metrics",JSONObject(d.metrics.filterValues {it.isFinite()})).put("sources",JSONObject(d.sources)).put("sleepStart",d.sleepStart?.toString()).put("sleepEnd",d.sleepEnd?.toString())
    val items: List<JSONObject> = when(section) {
        "summary"->selected.groupBy {it.sources["_connector"].orEmpty()}.map {(source,rows)->
            val metrics=JSONObject();rows.flatMap {it.metrics.keys}.distinct().forEach {key->
                val originKey=when {key.startsWith("hrv")->"hrv";key.startsWith("rhr")->"rhr";key.startsWith("sleep")||key.startsWith("stage_")||key in setOf("efficiency","time_in_bed")->"sleep";key=="workout_minutes"->"exercise";else->key}
                val streams=rows.mapNotNull {d->d.metrics[key]?.takeIf(Double::isFinite)?.let {Triple(d.date,d.sources[originKey].orEmpty(),it)}}.groupBy {it.second}
                if(streams.isNotEmpty()) metrics.put(key,JSONArray(streams.map {(origin,values)->JSONObject().put("origin",origin).put("mean",values.map {it.third}.average()).put("days",values.size)}))
            }
            val scoreGroups=rows.flatMap {it.scores.values}.filter {it.score!=null&&!it.stale}.groupBy {it.scoreId+"/"+it.algorithmVersion+"/"+it.coverage.sourcesByType.toSortedMap().mapValues {e->e.value.sorted()}}
            JSONObject().put("connector",source).put("availableDays",rows.size).put("metrics",metrics)
                .put("scoreMeansAreDailySnapshotsNotTotals",true).put("scores",JSONArray(scoreGroups.map {(id,values)->JSONObject().put("scoreAndModel",id).put("mean",values.map {it.score!!}.average()).put("days",values.size)}))
                .put("trainingMinutes",rows.flatMap {it.activities}.distinctBy {it.id+it.start}.sumOf {it.minutes})
        }
        "current_scores"->data.displayScores.values.map(::scoreJson)
        "daily"->selected.map(::dayJson)
        "activities"->selected.flatMap {d->d.activities.map {a->JSONObject().put("date",d.date.toString()).put("id",a.id).put("title",a.title).put("start",a.start.toString()).put("end",a.end.toString()).put("minutes",a.minutes).put("loadPoints",a.loadPoints ?: JSONObject.NULL).put("meanPulse",a.meanPulse ?: JSONObject.NULL).put("peakPulse",a.peakPulse ?: JSONObject.NULL).put("fallback",a.fallback).put("pulse",JSONArray(a.pulse.map {JSONArray().put(it.first.toString()).put(it.second)}))}}
        "sleep_stages"->selected.map {d->JSONObject().put("date",d.date.toString()).put("source",d.sources["sleep"]).put("start",d.sleepStart?.toString()).put("end",d.sleepEnd?.toString()).put("stages",JSONArray(d.sleepBands.map {JSONObject().put("start",it.start.toString()).put("end",it.end.toString()).put("stage",it.stage)}))}
        "energy_curve"->selected.map {d->JSONObject().put("date",d.date.toString()).put("points",JSONArray(d.energyCurve.map {JSONArray().put(it.first.toString()).put(it.second)}))}
        "journal"->data.journal.entries.filter {it.date in from..to}.sortedBy {it.date}.map {d->JSONObject().put("date",d.date.toString()).put("completed",d.completedAt!=null).put("answers",JSONArray(d.answers.map {(id,a)->val habit=data.journal.habits.find {it.id==id};JSONObject().put("id",id).put("question",habit?.question).put("unit",habit?.unit).put("yes",a.yes ?: JSONObject.NULL).put("amount",a.amount ?: JSONObject.NULL).put("skipped",a.skipped)}))}
        "journal_questions"->listOf(JSONObject().put("active",JSONArray(data.journal.activeIds)).put("questions",JSONArray(data.journal.habits.map {h->JSONObject().put("id",h.id).put("title",h.title).put("question",h.question).put("kind",h.kind.name).put("unit",h.unit).put("custom",h.custom)})))
        "journal_insights"->{
            val id=args.getString("habit_id");require(id.isBlank()||data.journal.habits.any {it.id==id}) {"Die Logbuch-Frage ist nicht vorhanden."}
            journalInsights(data.journal.copy(entries=data.journal.entries.filter {it.date in from..to}),data.days.filter {it.date in from..to.plusDays(1)},minOf(to.plusDays(1),data.today),habitIds=if(id.isBlank()) data.journal.activeIds.toSet() else setOf(id)).map {i->JSONObject()
                .put("habitId",i.habit.id).put("habit",i.habit.title).put("outcome",i.outcome.title).put("unit",i.outcome.unit)
                .put("yesOrHigherDays",i.yesCount).put("noOrLowerDays",i.noCount).put("quantityThreshold",i.threshold ?: JSONObject.NULL)
                .put("yesOrHigherMean",i.yesMean ?: JSONObject.NULL).put("noOrLowerMean",i.noMean ?: JSONObject.NULL).put("observedUnit",if(i.outcome==JournalOutcome.HRV) "ms" else i.outcome.unit).put("effect",i.effect ?: JSONObject.NULL).put("low",i.low ?: JSONObject.NULL).put("high",i.high ?: JSONObject.NULL)
                .put("quality",i.quality).put("analysisModel",JOURNAL_ANALYSIS_VERSION).put("controls",JSONArray(i.controls)).put("overlapping",JSONArray(i.overlapping)).put("source",i.source)
                .put("note","Eigene beobachtende lokale Analyse, kein Ursachebeweis. Verhaltenstag D ist Folgemesswert D+1 zugeordnet.")}
        }
        "plan"->data.plan?.sessions.orEmpty().filter {it.date in from..to}.sortedBy {it.date}.map {s->JSONObject().put("id",s.id).put("date",s.date.toString()).put("title",s.title).put("minutes",s.minutes).put("intensity",s.intensity).put("completed",s.completed).put("recordedActivity",s.recordedActivity?.title)}
        else->error("Diese Datenansicht wird nicht unterstützt.")
    }
    val offset=page*3;val result=items.drop(offset).take(3)
    JSONObject().put("section",section).put("from",from.toString()).put("to",to.toString()).put("items",JSONArray(result)).put("totalItems",items.size)
        .put("next_page",if(offset+result.size<items.size) page+1 else JSONObject.NULL).put("scope","Nur tatsächlich lokal gespeicherte Daten, keine erfundenen fehlenden Tage.")
}
