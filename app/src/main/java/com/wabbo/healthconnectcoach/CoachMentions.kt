package com.wabbo.healthconnectcoach

import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine as E
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

internal data class CoachMention(val id: String,val label: String,val aliases: String,val icon: String,val scoreId: String?=null,val metric: String?=null)
internal val coachMentions=listOf(
    CoachMention("hrv","HRV","Herzfrequenzvariabilität","heart",metric="hrv"),
    CoachMention("rhr","Ruheherzfrequenz","Ruhepuls Herzfrequenz","heart",metric="rhr"),
    CoachMention("load","Trainingsbelastung","Wochenbelastung Training Last","training",scoreId=E.SESSION_LOAD_ID),
    CoachMention("energy","Körperenergie","Body Battery Akku Tagesform","battery",scoreId=E.BODY_BATTERY_ID),
    CoachMention("sleep","Schlaf","Schlafscore Schlafphasen Nacht","moon",scoreId=E.SLEEP_ID),
    CoachMention("spo2","Sauerstoffsättigung","SpO2 Sauerstoff","heart",metric="spo2"),
    CoachMention("respiratory_rate","Atemfrequenz","Atmung","heart",metric="respiratory_rate"),
    CoachMention("skin_delta","Hauttemperatur","Temperatur Abweichung","heart",metric="skin_delta"),
    CoachMention("vo2max","Ausdauer","VO2max Fitness","training",metric="vo2max"),
    CoachMention("steps","Schritte","Bewegung","training",metric="steps"),
    CoachMention("distance_km","Distanz","Kilometer Strecke","training",metric="distance_km"),
    CoachMention("active_kcal","Aktive Energie","Kalorien kcal","training",metric="active_kcal"))
internal data class MentionQuery(val start: Int,val end: Int,val query: String)
internal fun mentionQuery(text: String,caret: Int): MentionQuery? {
    val end=caret.coerceIn(0,text.length);val start=text.lastIndexOf('@',end-1)
    if(start<0||start>0&&!text[start-1].isWhitespace()&&text[start-1] !in "([{,:;") return null
    val query=text.substring(start+1,end)
    if(query.length>32||query.any {it=='\n'||it=='@'}||query.count {it==' '}>1) return null
    if(coachMentions.any {text.substring(start,end).startsWith("@"+it.label+" ")}) return null
    return MentionQuery(start,end,query)
}
internal fun matchingMentions(query: String)=coachMentions.filter {query.isBlank()||(it.label+" "+it.aliases).contains(query.trim(),true)}
internal fun insertMention(text: String,query: MentionQuery,mention: CoachMention): Pair<String,Int> {
    val token="@${mention.label} "
    return text.replaceRange(query.start,query.end,token) to (query.start+token.length)
}
internal fun selectedMentionIds(text: String,ids: List<String>)=coachMentions.filter {it.id in ids&&text.contains("@"+it.label)}.map {it.id}

/** Explicit selection supplies an immediate dated packet; more saved detail remains available through read_app_data. */
internal fun coachMentionContext(data: CoachDataAccess?,ids: List<String>,date: LocalDate,zone: ZoneId=ZoneId.systemDefault()): String {
    val selected=coachMentions.filter {it.id in ids};if(selected.isEmpty()) return ""
    val root=JSONObject().put("selectedDate",date.toString()).put("scope","Explizit angehängte persönliche App-Daten. Fehlende Werte nicht erfinden; Zeitstempel und Quellen beachten. Details/weitere Tage bei Bedarf mit read_app_data lesen.")
    val items=JSONArray()
    selected.forEach {m->
        val rows=data?.days.orEmpty().filter {it.date<=date}.sortedBy {it.date}
        val current=rows.lastOrNull {d->if(m.metric!=null) finiteVital(d,m.metric)!=null else d.scores[m.scoreId]?.score!=null}
        val packet=JSONObject().put("id",m.id).put("name",m.label).put("available",current!=null)
        if(current!=null) {
            packet.put("date",current.date.toString()).put("calculatedAt",current.calculatedAt.toString()).put("connector",current.sources["_connector"])
            if(m.metric!=null) {
                val def=vitalDefinitions.first {it.key==m.metric}
                packet.put("value",finiteVital(current,m.metric)).put("unit",def.unit).put("origin",metricOrigin(current,m.metric)).put("explanation",def.explanation)
                val matching=sameVitalDays(rows,def,current).filter {it.date>=current.date.minusDays(28)}
                packet.put("dailyValues",JSONArray(matching.map {JSONArray().put(it.date.toString()).put(finiteVital(it,m.metric))}))
                vitalReference(rows,def,current)?.let {packet.put("personalBaseline",JSONObject().put("median",it.median).put("low",it.low).put("high",it.high).put("days",it.days))}
            } else {
                val s=current.scores.getValue(m.scoreId!!)
                packet.put("value",s.score).put("asOf",s.asOf.toString()).put("stale",s.stale).put("model",s.algorithmVersion).put("components",JSONObject(s.components)).put("missing",JSONArray(s.coverage.missingReasons))
                packet.put("dailyValues",JSONArray(rows.filter {it.date>=date.minusDays(28)&&it.sources["_connector"]==current.sources["_connector"]}.mapNotNull {d->d.scores[m.scoreId]?.takeIf {it.algorithmVersion==s.algorithmVersion&&!it.stale&&it.score!=null}?.let {JSONArray().put(d.date.toString()).put(it.score)}}))
                when(m.id) {
                    "load"-> {packet.put("goal",weeklyTargetValue(s)).put("pointsMeaning","100 entspricht dem adaptiven Wochenziel; keine Minuten und keine Wochensummen aus täglichen Snapshots addieren.")
                        packet.put("activities",JSONArray(rows.filter {it.date in current.date.with(java.time.DayOfWeek.MONDAY)..current.date&&it.sources["_connector"]==current.sources["_connector"]}.flatMap {it.activities}.distinctBy {it.id+it.start}.takeLast(30).map {a->JSONObject().put("title",a.title).put("start",a.start.toString()).put("minutes",a.minutes).put("points",a.loadPoints).put("meanPulse",a.meanPulse).put("peakPulse",a.peakPulse)}))}
                    "sleep"->packet.put("sleepStart",current.sleepStart?.toString()).put("sleepEnd",current.sleepEnd?.toString()).put("sleepMetrics",JSONObject(current.metrics.filterKeys {it.startsWith("sleep")||it.startsWith("stage_")||it in setOf("hrv","rhr","efficiency","time_in_bed")})).put("phaseMinutes",JSONObject(current.sleepBands.groupBy {it.stage}.mapValues {(_,bands)->bands.sumOf {java.time.Duration.between(it.start,it.end).toMinutes()}}))
                    "energy"->packet.put("energyCurve",JSONArray(current.energyCurve.takeLast(48).map {JSONArray().put(it.first.toString()).put(it.second)}))
                }
            }
        }
        if(m.id=="energy") data?.displayScores?.get(E.BODY_BATTERY_ID)?.takeIf {it.stale}?.let {packet.put("displayedCachedValue",it.score).put("displayedCachedAsOf",it.asOf.toString())}
        items.put(packet)
    }
    return root.put("attachments",items).toString()
}
