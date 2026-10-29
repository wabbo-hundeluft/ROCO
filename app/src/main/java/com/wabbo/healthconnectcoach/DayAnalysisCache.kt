package com.wabbo.healthconnectcoach

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate

internal const val MAX_DAILY_ANALYSES=24

internal data class DayAnalysisEntry(val fingerprint: String,val text: String,val at: Instant)
internal data class DayAnalysisCache(val date: LocalDate,val attempts: Int=0,
    val attempted: Set<String> = emptySet(),val entries: List<DayAnalysisEntry> = emptyList(),val lastRequestedAt: Instant?=null) {
    fun matching(fingerprint: String)=entries.lastOrNull {it.fingerprint==fingerprint}
    fun mayRequest(fingerprint: String,explicitRetry: Boolean): Boolean =
        matching(fingerprint)==null && attempts<MAX_DAILY_ANALYSES && (explicitRetry || fingerprint !in attempted)
    fun reserve(fingerprint: String,explicitRetry: Boolean): DayAnalysisCache {
        check(mayRequest(fingerprint,explicitRetry))
        return copy(attempts=attempts+1,attempted=attempted+fingerprint)
    }
    fun finish(entry: DayAnalysisEntry): DayAnalysisCache {
        require(entry.fingerprint in attempted && entry.text.isNotBlank())
        return copy(entries=(entries.filterNot {it.fingerprint==entry.fingerprint}+entry).takeLast(3))
    }
}
internal fun analysisFingerprint(day: DayInsight,config: AiConfiguration,robot: String,plan: TrainingPlan?=null,journalContext: String?=null,memoryContext:String?=null,nightContext:String?=null): String {
    val data=JSONObject().put("schema",2).put("policy","adaptive-weekly-goal-v2").put("promptVersion",COACH_PROMPT_VERSION).put("prompt",COACH_SYSTEM_PROMPT).put("journalContext",journalContext).put("memoryContext",memoryContext).put("nightContext",nightContext).put("planRevision",plan?.revision ?: 0).put("date",day.date.toString()).put("robot",robot)
        .put("providers",JSONArray().apply {CoachProvider.entries.filter {config.keyFor(it).isNotBlank()}.forEach {p->put(JSONObject().put("provider",p.name).put("model",config.modelFor(p).trim()).put("key",config.keyFor(p).trim()))}})
        .put("sources",JSONObject(day.sources.toSortedMap()))
        .put("metrics",JSONObject(day.metrics.filterKeys {!it.startsWith("read_")}.toSortedMap()))
        .put("scores",JSONArray().apply {day.scores.toSortedMap().forEach {(id,s)->put(JSONObject()
            .put("id",id).put("value",s.score ?: JSONObject.NULL).put("status",s.status.name)
            .put("version",s.algorithmVersion).put("components",JSONObject(s.components.toSortedMap()))
            .put("raw",s.rawIndex ?: JSONObject.NULL).put("missing",JSONArray(s.coverage.missingReasons.sorted())))}})
    // JSONObject insertion order is unspecified; canonicalize recursively before hashing.
    fun canonical(value: Any?): String=when(value) {
        is JSONObject->value.keys().asSequence().toList().sorted().joinToString(prefix="{",postfix="}") {JSONObject.quote(it)+":"+canonical(value.get(it))}
        is JSONArray->(0 until value.length()).joinToString(prefix="[",postfix="]") {canonical(value.get(it))}
        null,JSONObject.NULL->"null"
        is String->JSONObject.quote(value)
        else->value.toString()
    }
    return MessageDigest.getInstance("SHA-256").digest(canonical(data).toByteArray(Charsets.UTF_8)).joinToString("") {"%02x".format(it)}
}
internal fun decodeAnalysisCache(text: String?,date: LocalDate): DayAnalysisCache {
    if(text==null) return DayAnalysisCache(date)
    val o=JSONObject(text)
    if(o.optString("date")!=date.toString()) return DayAnalysisCache(date)
    val attempts=o.optInt("count",0).coerceIn(0,MAX_DAILY_ANALYSES)
    val rows=o.optJSONArray("entries")
    // Older fingerprints omitted several health facts. Retain the day's request budget,
    // but do not reuse those texts as if they described the new complete fingerprint.
    if(o.optInt("schema")!=2 || rows==null) return DayAnalysisCache(date,attempts)
    val attempted=o.optJSONArray("attempted") ?: JSONArray()
    val entries=(0 until minOf(rows.length(),3)).map {rows.getJSONObject(it).let {r->
        DayAnalysisEntry(r.getString("fingerprint"),r.getString("text"),Instant.parse(r.getString("at")))
    }}
    return DayAnalysisCache(date,attempts,(0 until minOf(attempted.length(),MAX_DAILY_ANALYSES)).map {attempted.getString(it)}.toSet(),entries,o.optString("lastRequestedAt").takeIf {it.isNotBlank()}?.let(Instant::parse))
}
internal fun encodeAnalysisCache(cache: DayAnalysisCache)=JSONObject().put("schema",2).put("date",cache.date.toString())
    .put("count",cache.attempts).put("attempted",JSONArray(cache.attempted.toList())).put("lastRequestedAt",cache.lastRequestedAt?.toString())
    .put("entries",JSONArray().apply {cache.entries.forEach {e->put(JSONObject().put("fingerprint",e.fingerprint).put("text",e.text).put("at",e.at.toString()))}}).toString()
