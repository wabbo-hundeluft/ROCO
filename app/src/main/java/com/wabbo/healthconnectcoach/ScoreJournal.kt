package com.wabbo.healthconnectcoach

import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine as Engine
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class SleepBand(val start: Instant, val end: Instant, val stage: String)
internal data class ActivityInsight(val id: String, val title: String, val start: Instant, val end: Instant,
    val minutes: Double, val loadPoints: Double?, val meanPulse: Double?, val peakPulse: Double?,
    val pulse: List<Pair<Instant, Double>>, val fallback: Boolean)
internal data class DayInsight(
    val date: LocalDate,
    val calculatedAt: Instant,
    val reconstructed: Boolean,
    val scores: Map<String, Engine.ScoreResult>,
    val sleepStart: Instant? = null,
    val sleepEnd: Instant? = null,
    val sleepBands: List<SleepBand> = emptyList(),
    val metrics: Map<String, Double> = emptyMap(),
    val sources: Map<String, String> = emptyMap(),
    val activities: List<ActivityInsight> = emptyList(),
    val energyCurve: List<Pair<Instant, Int>> = emptyList(),
)

internal fun dayInsight(readout: ApprovedWellnessScoreReadout, reconstructed: Boolean = false): DayInsight {
    val input = readout.normalizedInput
    val today = input.asOf.atZone(input.zone).toLocalDate()
    val dayStart = today.atStartOfDay(input.zone).toInstant()
    val sleep = Engine().selectedMainSleep(input)
        ?.takeUnless { readout.scores[Engine.SLEEP_ID]?.status == Engine.Status.source_conflict }
    val metrics = linkedMapOf<String, Double>(
        "read_sleep_count" to input.sleeps.size.toDouble(),
        "read_hrv_count" to input.hrv.size.toDouble(),
        "read_rhr_count" to input.rhr.size.toDouble(),
    )
    sleep?.let {
        metrics["time_in_bed"] = Duration.between(it.start, it.end).seconds / 60.0
        val bands = it.stages.filter { stage -> stage.end > stage.start && stage.start >= it.start && stage.end <= it.end }
        if (bands.isNotEmpty()) {
            val known = bands.filter { b -> b.type in setOf("LIGHT", "DEEP", "REM", "SLEEPING") }
            if (known.isNotEmpty()) metrics["sleep_minutes"] = known.sumOf { b -> Duration.between(b.start, b.end).seconds / 60.0 }
            bands.groupBy { b -> b.type }.forEach { (type, entries) ->
                metrics["stage_$type"] = entries.sumOf { b -> Duration.between(b.start, b.end).seconds / 60.0 }
            }
            metrics["sleep_minutes"]?.let { minutes -> metrics["efficiency"] = (100 * minutes / metrics.getValue("time_in_bed")).coerceIn(0.0, 100.0) }
        }
    }
    fun median(values: List<Double>): Double {val v=values.sorted();return if(v.size%2==0) (v[v.size/2-1]+v[v.size/2])/2 else v[v.size/2]}
    listOf("hrv", "rhr").forEach { key ->
        if(sleep!=null) {
            val engine=Engine()
            val values=engine.recoveryMetricValues(input,sleep,key).map {it.value}
            if(values.isNotEmpty()) metrics[key]=median(values)
            val baseline=engine.recoveryBaselineValues(input,sleep,key)
            metrics[key+"_baseline_days"]=baseline.size.toDouble()
            if(baseline.size>=7) {
                metrics[key+"_baseline"]=median(baseline)
                metrics[key+"_low"]=baseline[(baseline.size*.1).toInt()]
                metrics[key+"_high"]=baseline[((baseline.size-1)*.9).toInt()]
            }
        } else {
            // Vitals remain useful when no main sleep exists, without inventing an energy score.
            val samples=if(key=="hrv") input.hrv else input.rhr
            val values=samples.filter {it.at<=input.asOf && (it.dailyDate ?: it.at.atZone(input.zone).toLocalDate())==today &&
                it.value.isFinite() && it.value>0 && (input.boundSources[key]==null || it.source==input.boundSources[key])}
            if(values.isNotEmpty() && values.map {it.source}.distinct().size==1 && input.permissions[key] !in setOf(Engine.PermissionState.missing,Engine.PermissionState.unavailable)) {
                metrics[key]=median(values.map {it.value})
                val baseline=(if(key=="hrv") input.hrvBaseline else input.rhrBaseline)
                    .filter {it.source==values.first().source && it.value.isFinite() && it.value>0 && it.date in today.minusDays(28)..today.minusDays(1)}
                    .groupBy {it.date}.values.map {median(it.map {d->d.value})}.sorted()
                metrics[key+"_baseline_days"]=baseline.size.toDouble()
                if(baseline.size>=7) {
                    metrics[key+"_baseline"]=median(baseline)
                    metrics[key+"_low"]=baseline[(baseline.size*.1).toInt()]
                    metrics[key+"_high"]=baseline[((baseline.size-1)*.9).toInt()]
                }
            }
        }
    }
    val workouts = input.workouts.filter { it.start >= dayStart && it.end <= input.asOf }
        .distinctBy { listOf(it.start, it.end, it.source) }
    metrics["workout_minutes"] = workouts.sumOf { Duration.between(it.start, it.end).seconds / 60.0 }
    metrics["workout_count"] = workouts.size.toDouble()
    (0..3).forEach { index ->
        val end = input.asOf.minusSeconds(index * 7L * 86400)
        val start = end.minusSeconds(7L * 86400)
        metrics["week_${index}_minutes"] = input.workouts.filter { it.start >= start && it.start < end && it.end <= input.asOf }
            .distinctBy { listOf(it.start, it.end, it.source) }.sumOf { Duration.between(it.start, it.end).seconds / 60.0 }
    }
    val steps = input.steps.filter { it.start >= dayStart && it.end <= input.asOf &&
        (input.boundSources["steps"] == null || it.source == input.boundSources["steps"]) }
    if (steps.isNotEmpty() && steps.map { it.source }.distinct().size == 1) metrics["steps"] = steps.distinctBy { listOf(it.start, it.end, it.count, it.source) }.sumOf { it.count }
    sleep?.let { metrics["short_awakenings_minutes"] = it.shortAwakenings.sumOf {s -> Duration.between(s.start,s.end).seconds/60.0} }
    val naps=input.sleeps.filter {it.isMainSleep==false && it.end<=input.asOf && it.end.atZone(input.zone).toLocalDate()==today && it.end>it.start &&
        (input.boundSources["sleep"]==null || it.source==input.boundSources["sleep"])}.distinctBy {listOf(it.start,it.end,it.source)}
    if(naps.map {it.source}.distinct().size<=1) metrics["nap_minutes"]=naps.sumOf {nap ->
        val stages=nap.stages.filter {it.type in setOf("SLEEPING","LIGHT","DEEP","REM") && it.start>=nap.start&&it.end<=nap.end&&it.end>it.start}
        if(stages.isNotEmpty()) stages.sumOf {Duration.between(it.start,it.end).seconds/60.0} else Duration.between(nap.start,nap.end).seconds/60.0
    }
    metrics["sleep_goal"] = Engine().effectiveSleepGoal(input)
    val engine = Engine()
    val monday = today.with(java.time.DayOfWeek.MONDAY).atStartOfDay(input.zone).toInstant()
    val activities = input.workouts.filter { it.start >= monday && it.end <= input.asOf }.distinctBy { listOf(it.start, it.end, it.source) }.map { w ->
        val load = engine.activityLoad(w, input)
        val pulse = w.heartRate.filter { it.value.isFinite() && it.value > 0 && it.at in w.start..w.end && (input.boundSources["heart_rate"] == null || it.source == input.boundSources["heart_rate"]) }.sortedBy { it.at }
        val grouped = pulse.groupBy { it.at.epochSecond / 60 }.values.map { p -> p.first().at to p.map { it.value }.average() }
        val stride = (grouped.size / 80).coerceAtLeast(1)
        ActivityInsight(w.id, w.title, w.start, w.end, load.minutes, readout.scores[Engine.SESSION_LOAD_ID]?.let {weeklyTargetValue(it)}?.let { 100 * load.weightedMinutes / it },
            pulse.takeIf { it.isNotEmpty() }?.map { it.value }?.average(), pulse.maxOfOrNull { it.value }, grouped.filterIndexed { idx, _ -> idx % stride == 0 }.take(100), load.fallback)
    }
    return DayInsight(today, input.asOf, reconstructed, readout.scores, sleep?.start, sleep?.end,
        sleep?.stages.orEmpty().map { SleepBand(it.start, it.end, it.type) }, metrics,
        input.boundSources.mapNotNull { (k, v) -> v?.let { k to it } }.toMap() +
            input.sleeps.filter {it.end<=input.asOf&&it.isMainSleep!=false}.maxByOrNull {it.end}?.let {mapOf("_latest_imported_sleep_end" to it.end.toString())}.orEmpty(), activities, engine.batteryTimeline(input))
}

/** Full daily score breakdowns, never API credentials or raw pulse samples. */
internal class ScoreJournal(private val document: SecureLocalDocument) {
    fun read(): List<DayInsight> = document.read()?.let { decodeDays(it) }.orEmpty()
    fun save(days: List<DayInsight>) { document.write(encodeDays(days.sortedBy { it.date }.takeLast(90))) }
}

internal fun mergeDays(old: List<DayInsight>, fresh: List<DayInsight>): List<DayInsight> {
    val result = old.associateBy { it.date }.toMutableMap()
    fresh.forEach { next ->
        val prior = result[next.date]
        val scores = next.scores.mapValues { (id, score) ->
            val previous = prior?.scores?.get(id)
            if (canReuseCachedScore(score, previous) && prior?.sources?.get("_connector") == next.sources["_connector"]) previous!!.copy(status = Engine.Status.stale, stale = true, coverage = previous.coverage.copy(missingReasons = (score.coverage.missingReasons + previous.coverage.missingReasons).distinct()))
            else score
        }
        result[next.date] = next.copy(scores = scores)
    }
    return result.values.sortedBy { it.date }.takeLast(90)
}

private fun encodeDays(days: List<DayInsight>): String = JSONArray().apply {
    days.forEach { day -> put(JSONObject().apply {
        put("date", day.date.toString()); put("at", day.calculatedAt.toString()); put("reconstructed", day.reconstructed)
        put("sleepStart", day.sleepStart?.toString()); put("sleepEnd", day.sleepEnd?.toString())
        put("metrics", JSONObject(day.metrics)); put("sources", JSONObject(day.sources))
        put("energy", JSONArray().apply { day.energyCurve.forEach { put(JSONArray().put(it.first.toString()).put(it.second)) } })
        put("activities", JSONArray().apply { day.activities.forEach { a -> put(JSONObject().put("id", a.id).put("title", a.title).put("start", a.start.toString()).put("end", a.end.toString())
            .put("minutes", a.minutes).put("load", a.loadPoints ?: JSONObject.NULL).put("mean", a.meanPulse ?: JSONObject.NULL).put("peak", a.peakPulse ?: JSONObject.NULL).put("fallback", a.fallback)
            .put("pulse", JSONArray().apply { a.pulse.forEach { put(JSONArray().put(it.first.toString()).put(it.second)) } })) } })
        put("bands", JSONArray().apply { day.sleepBands.forEach { put(JSONObject().put("s", it.start.toString()).put("e", it.end.toString()).put("t", it.stage)) } })
        put("scores", JSONArray().apply { day.scores.values.forEach { score -> put(JSONObject().apply {
            put("id", score.scoreId); put("value", score.score ?: JSONObject.NULL); put("status", score.status.name)
            put("at", score.asOf.toString()); put("components", JSONObject(score.components))
            put("raw", score.rawIndex ?: JSONObject.NULL); put("rating", score.rating0to100 ?: JSONObject.NULL)
            put("available", score.coverage.available); put("expected", score.coverage.expected)
            put("missing", JSONArray(score.coverage.missingReasons)); put("sources", JSONArray(score.coverage.sources.toList()))
            put("hrCovered", score.coverage.hrCoverage.coveredSeconds); put("hrTotal", score.coverage.hrCoverage.totalSeconds)
            put("hrSamples", score.coverage.hrCoverage.uniqueSamples)
            put("windows", JSONObject(score.coverage.windowCompleteness))
            put("version", score.algorithmVersion)
            put("hrSeconds", score.coverage.hrSeconds)
            put("sourcesByType", JSONObject().apply { score.coverage.sourcesByType.forEach { (type, sources) -> put(type.name, JSONArray(sources.toList())) } })
            put("hrWindows", JSONObject().apply { score.coverage.hrCoverageByWindow.forEach { (name, coverage) ->
                put(name, JSONObject().put("covered", coverage.coveredSeconds).put("total", coverage.totalSeconds).put("samples", coverage.uniqueSamples))
            } })
        }) } })
    }) }
}.toString()

private fun decodeDays(text: String): List<DayInsight> {
    val array = JSONArray(text)
    require(array.length() <= 90)
    return (0 until array.length()).map { index ->
        val row = array.getJSONObject(index)
        val scores = row.getJSONArray("scores")
        DayInsight(LocalDate.parse(row.getString("date")), Instant.parse(row.getString("at")), row.getBoolean("reconstructed"),
            (0 until scores.length()).associate { i ->
                val s = scores.getJSONObject(i)
                val components = s.getJSONObject("components")
                val reasons = s.getJSONArray("missing")
                val sources = s.getJSONArray("sources")
                val windows = s.optJSONObject("windows") ?: JSONObject()
                val id = s.getString("id")
                id to Engine.ScoreResult(id, if (s.isNull("value")) null else s.getInt("value"), Engine.Status.valueOf(s.getString("status")),
                    Instant.parse(s.getString("at")), Engine.Coverage(s.getInt("available"), s.getInt("expected"),
                        sources = (0 until sources.length()).map { sources.getString(it) }.toSet(),
                        missingReasons = (0 until reasons.length()).map { reasons.getString(it) },
                        hrSeconds = s.optLong("hrSeconds"),
                        sourcesByType = (s.optJSONObject("sourcesByType") ?: JSONObject()).let { types -> types.keys().asSequence().associate { name ->
                            Engine.RecordType.valueOf(name) to types.getJSONArray(name).let { values -> (0 until values.length()).map { values.getString(it) }.toSet() }
                        } },
                        hrCoverageByWindow = (s.optJSONObject("hrWindows") ?: JSONObject()).let { windows -> windows.keys().asSequence().associateWith { name ->
                            windows.getJSONObject(name).let { c -> Engine.HrCoverage(c.getLong("covered"), c.getLong("total"), c.getInt("samples")) }
                        } },
                        hrCoverage = Engine.HrCoverage(s.optLong("hrCovered"), s.optLong("hrTotal"), s.optInt("hrSamples")),
                        windowCompleteness = windows.keys().asSequence().associateWith { windows.getBoolean(it) }),
                    components.keys().asSequence().associateWith { components.getInt(it) },
                    if (s.isNull("raw")) null else s.getDouble("raw"), if (s.isNull("rating")) null else s.getInt("rating"), algorithmVersion = s.optString("version", id))
            },
            row.optString("sleepStart").takeIf { it.isNotEmpty() }?.let(Instant::parse),
            row.optString("sleepEnd").takeIf { it.isNotEmpty() }?.let(Instant::parse),
            row.getJSONArray("bands").let { bands -> (0 until bands.length()).map { bands.getJSONObject(it).let { b -> SleepBand(Instant.parse(b.getString("s")), Instant.parse(b.getString("e")), b.getString("t")) } } },
            row.getJSONObject("metrics").let { m -> m.keys().asSequence().associateWith { m.getDouble(it) } },
            row.getJSONObject("sources").let { m -> m.keys().asSequence().associateWith { m.getString(it) } },
            row.optJSONArray("activities")?.let { list -> (0 until list.length()).map { list.getJSONObject(it).let { a ->
                ActivityInsight(a.getString("id"), a.getString("title"), Instant.parse(a.getString("start")), Instant.parse(a.getString("end")), a.getDouble("minutes"),
                    if(a.isNull("load")) null else a.getDouble("load"), if(a.isNull("mean")) null else a.getDouble("mean"), if(a.isNull("peak")) null else a.getDouble("peak"),
                    a.getJSONArray("pulse").let { p -> (0 until p.length()).map { idx -> p.getJSONArray(idx).let { point -> Instant.parse(point.getString(0)) to point.getDouble(1) } } }, a.getBoolean("fallback"))
            } } }.orEmpty(),
            row.optJSONArray("energy")?.let { p -> (0 until p.length()).map { p.getJSONArray(it).let { q -> Instant.parse(q.getString(0)) to q.getInt(1) } } }.orEmpty())
    }
}
