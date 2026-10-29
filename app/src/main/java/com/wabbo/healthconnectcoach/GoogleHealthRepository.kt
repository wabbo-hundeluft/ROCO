package com.wabbo.healthconnectcoach

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine as Engine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.time.*
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Account tokens stay in memory. AuthorizationClient refreshes the grant without storing a refresh token. */
internal object GoogleHealthSession {
    @Volatile private var token: String? = null
    @Volatile private var expires: Instant = Instant.EPOCH
    fun enabled(context: Context) = context.getSharedPreferences("google_health_connection", Context.MODE_PRIVATE).getBoolean("preferred", false)
    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("google_health_connection", Context.MODE_PRIVATE).edit().putBoolean("preferred", enabled).apply()
        if (!enabled) { token = null; expires = Instant.EPOCH }
    }
    fun accept(value: String) { token = value; expires = Instant.now().plusSeconds(2700) }
    suspend fun accessToken(context: Context): String {
        token?.takeIf { Instant.now() < expires }?.let { return it }
        return suspendCancellableCoroutine { continuation ->
            val request = AuthorizationRequest.builder().setRequestedScopes(googleHealthReadScopes.map(::Scope)).build()
            Identity.getAuthorizationClient(context).authorize(request).addOnSuccessListener { result ->
                if (!continuation.isActive) return@addOnSuccessListener
                val fresh = result.accessToken
                if (result.hasResolution() || fresh.isNullOrBlank()) continuation.resumeWithException(IllegalStateException("Bitte Google Health im Profil erneut freigeben."))
                else { accept(fresh); continuation.resume(fresh) }
            }.addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Die Google-Freigabe ist momentan nicht erreichbar.")) }
        }
    }
}

internal data class GoogleHealthImport(val readout: ApprovedWellnessScoreReadout, val count: Int,
    val extraMetrics: Map<LocalDate,Map<String,Double>> = emptyMap(), val optionalErrors: List<String> = emptyList())

/** Official v4 reconcile endpoints, one connector and Google wearable family for the entire snapshot. */
internal class GoogleHealthRepository(private val token: String, private val progress: (String) -> Unit = {}) {
    private val count = AtomicInteger(0)
    private suspend fun dailyRollup(type: String, from: LocalDate, until: LocalDate, consume: (LocalDate,JSONObject)->Unit) {
        fun civil(date: LocalDate)=JSONObject().put("date",JSONObject().put("year",date.year).put("month",date.monthValue).put("day",date.dayOfMonth))
            .put("time",JSONObject().put("hours",0).put("minutes",0).put("seconds",0))
        var next="";val seen=mutableSetOf<String>()
        do {
            coroutineContext.ensureActive()
            val body=JSONObject().put("windowSizeDays",1).put("pageSize",200).put("dataSourceFamily","users/me/dataSourceFamilies/google-wearables")
                .put("range",JSONObject().put("start",civil(from)).put("end",civil(until)))
            if(next.isNotEmpty()) body.put("pageToken",next)
            val c=URL("https://health.googleapis.com/v4/users/me/dataTypes/$type/dataPoints:dailyRollUp").openConnection() as HttpsURLConnection
            val response=try {
                c.requestMethod="POST";c.doOutput=true;c.instanceFollowRedirects=false;c.connectTimeout=15000;c.readTimeout=25000
                c.setRequestProperty("Authorization","Bearer $token");c.setRequestProperty("Content-Type","application/json; charset=utf-8")
                c.outputStream.use {it.write(body.toString().toByteArray(Charsets.UTF_8))}
                check(c.responseCode in 200..299) {"Zusätzlicher Google-Wert $type nicht verfügbar (HTTP ${c.responseCode})."}
                val text=c.inputStream.bufferedReader().use {input ->val buffer=CharArray(4096);val out=StringBuilder();while(true) {coroutineContext.ensureActive();val n=input.read(buffer);if(n<0)break;out.append(buffer,0,n);check(out.length<=1_048_576)};out.toString()}
                JSONObject(text)
            } finally {c.disconnect()}
            val rows=response.optJSONArray("rollupDataPoints") ?: JSONArray()
            repeat(rows.length()) {index ->val row=rows.getJSONObject(index);consume(googleDate(row.getJSONObject("civilStartTime").getJSONObject("date")),row)}
            next=response.optString("nextPageToken");check(next.isEmpty()||seen.add(next)) {"Wiederholte Google-Summenseite."};check(seen.size<=10)
        } while(next.isNotEmpty())
    }
    private suspend fun points(type: String, filter: String, consume: (JSONObject) -> Unit) {
        var next = ""; val seen = mutableSetOf<String>(); var pages = 0
        do {
            coroutineContext.ensureActive()
            check(++pages <= 150) { "Zu viele Google-Datenseiten. Bitte einen kürzeren Zeitraum verwenden." }
            val query = linkedMapOf("filter" to filter, "pageSize" to if(type in setOf("sleep", "exercise")) "25" else "10000",
                "dataSourceFamily" to "users/me/dataSourceFamilies/google-wearables")
            if (next.isNotEmpty()) query["pageToken"] = next
            val encoded = query.entries.joinToString("&") { URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8") }
            val c = URL("https://health.googleapis.com/v4/users/me/dataTypes/$type/dataPoints:reconcile?$encoded").openConnection() as HttpsURLConnection
            val response = try {
                c.connectTimeout = 15000; c.readTimeout = 25000; c.instanceFollowRedirects = false
                c.setRequestProperty("Authorization", "Bearer $token")
                check(c.responseCode in 200..299) { when(c.responseCode) {
                    401 -> "Google-Zugang abgelaufen. Bitte erneut verbinden."
                    403 -> "Google hat die Datenfreigabe für $type abgelehnt."
                    429 -> "Google-Anfragelimit erreicht. Später erneut versuchen."
                    else -> "Google-Datenimport für $type fehlgeschlagen (HTTP ${c.responseCode})."
                } }
                val bytes = c.inputStream.use { input ->
                    val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                    while(true) { coroutineContext.ensureActive(); val n = input.read(buffer); if(n < 0) break; out.write(buffer, 0, n); check(out.size() <= 8_388_608) { "Google-Datenseite zu groß." } }; out.toByteArray()
                }
                JSONObject(bytes.toString(Charsets.UTF_8))
            } finally { c.disconnect() }
            val rows = response.optJSONArray("dataPoints") ?: JSONArray()
            repeat(rows.length()) { consume(rows.getJSONObject(it)); check(count.incrementAndGet() <= 250_000) { "Zu viele Google-Messwerte. Letzter gespeicherter Stand bleibt erhalten." } }
            next = response.optString("nextPageToken")
            check(next.isEmpty() || seen.add(next)) { "Google hat eine wiederholte Datenseite geliefert." }
        } while(next.isNotEmpty())
    }

    suspend fun read(asOf: Instant, zone: ZoneId, goals: TrainingGoals?, personalProfile: com.wabbo.healthconnectcoach.score.PersonalScoreProfile = com.wabbo.healthconnectcoach.score.PersonalScoreProfile()): GoogleHealthImport = withContext(Dispatchers.IO) {
        val today = asOf.atZone(zone).toLocalDate(); val from = today.minusDays(65); val nextDay = today.plusDays(1)
        val start = from.atStartOfDay(zone).toInstant()
        val source = "Google Health · Pixel/Fitbit"
        fun intervalFilter(type: String, field: String = "interval.start_time", begin: Instant = start, end: Instant = asOf) =
            "$type.$field >= \"$begin\" AND $type.$field < \"$end\""
        fun dailyFilter(type: String) = "$type.date >= \"$from\" AND $type.date < \"$nextDay\""
        val sleeps = mutableListOf<Engine.SleepSession>(); val hrv = mutableListOf<Engine.MetricSample>(); val rhr = mutableListOf<Engine.MetricSample>()
        val workouts = mutableListOf<Engine.Workout>(); val steps = mutableListOf<Engine.Steps>()
        val extra=mutableMapOf<LocalDate,MutableMap<String,Double>>();val optionalErrors=mutableListOf<String>()
        fun extraValue(date: LocalDate,key: String,value: Double) {if(date<=today&&value.isFinite()) extra.getOrPut(date) {mutableMapOf()}[key]=value}
        progress("Nächte und Schlafphasen laden")
        points("sleep", intervalFilter("sleep", "interval.end_time")) { row -> row.optJSONObject("sleep")?.let { sleep ->
            val interval = sleep.getJSONObject("interval"); val begin = Instant.parse(interval.getString("startTime")); val end = Instant.parse(interval.getString("endTime"))
            if (end <= asOf && end > begin) {
                val stages = sleep.optJSONArray("stages") ?: JSONArray()
                sleeps += Engine.SleepSession(begin, end, source, row.optString("dataPointName", "sleep:$begin:$end"),
                    (0 until stages.length()).map { stages.getJSONObject(it).let { s -> Engine.SleepStage(Instant.parse(s.getString("startTime")), Instant.parse(s.getString("endTime")), when(s.getString("type")) { "ASLEEP" -> "SLEEPING"; "RESTLESS" -> "AWAKE_IN_BED"; else -> s.getString("type") }) } },
                    localWakeDate = end.atZone(zone).toLocalDate(), localMidpointMinute = begin.plusSeconds(Duration.between(begin, end).seconds / 2).atZone(zone).let { it.hour * 60.0 + it.minute },
                    shortAwakenings = (sleep.optJSONArray("shortAwakenings") ?: JSONArray()).let { rows -> (0 until rows.length()).map { rows.getJSONObject(it).let { r -> Engine.SleepStage(Instant.parse(r.getString("startTime")),Instant.parse(r.getString("endTime")),"AWAKE") } } },
                    isMainSleep=sleep.optJSONObject("metadata")?.let {m -> if(m.optBoolean("nap")) false else if(m.has("mainSleep")) m.getBoolean("mainSleep") else null })
            }
        } }
        progress("HRV und Ruhepuls einordnen")
        fun daily(row: JSONObject, key: String, field: String, target: MutableList<Engine.MetricSample>) {
            row.optJSONObject(key)?.let { metric ->
                val date = googleDate(metric.getJSONObject("date")); val value = metric.optDouble(field, Double.NaN)
                if (date <= today && value.isFinite() && value > 0) target += Engine.MetricSample(date.atStartOfDay(zone).toInstant(), value, source, "$key:$date", dailyDate = date)
            }
        }
        points("daily-heart-rate-variability", dailyFilter("daily_heart_rate_variability")) { daily(it, "dailyHeartRateVariability", "averageHeartRateVariabilityMilliseconds", hrv) }
        points("daily-resting-heart-rate", dailyFilter("daily_resting_heart_rate")) { daily(it, "dailyRestingHeartRate", "beatsPerMinute", rhr) }
        progress("Trainings und Bewegung laden")
        points("exercise", googleExerciseFilter(from, nextDay)) { row -> row.optJSONObject("exercise")?.let { ex ->
            val interval = ex.getJSONObject("interval"); val begin = Instant.parse(interval.getString("startTime")); val end = Instant.parse(interval.getString("endTime"))
            if (end > begin && end <= asOf) workouts += Engine.Workout(begin, end, source, row.optString("dataPointName", "$begin:$end"),
                localStartDate = begin.atZone(zone).toLocalDate(), title = ex.optString("displayName").ifBlank { googleExerciseName(ex.optString("exerciseType")) })
        } }
        points("steps", intervalFilter("steps", begin = today.minusDays(35).atStartOfDay(zone).toInstant())) { row -> row.optJSONObject("steps")?.let { value ->
            val interval = value.getJSONObject("interval"); val begin = Instant.parse(interval.getString("startTime")); val end = Instant.parse(interval.getString("endTime")); val n = value.optDouble("count")
            if (n.isFinite() && n >= 0 && end <= asOf && end > begin) steps += Engine.Steps(begin, end, n, source, "$begin:$end")
        } }
        val recent = workouts.distinctBy { it.start to it.end }.filter { it.end >= start }
        check(recent.size <= 150) { "Zu viele Trainings im aktuellen Import. Der gespeicherte Stand bleibt verfügbar." }
        val permits=Semaphore(3);val completedPulse=AtomicInteger(0)
        val enriched = coroutineScope {workouts.distinctBy { it.start to it.end }.map { w -> async { permits.withPermit {
            if (w !in recent) w else {
                progress("Trainingspuls zuordnen · ${completedPulse.get()}/${recent.size}")
                val pulse = mutableListOf<Engine.MetricSample>()
                points("heart-rate", intervalFilter("heart_rate", "sample_time.physical_time", w.start, w.end)) { row -> row.optJSONObject("heartRate")?.let { value ->
                    val at = Instant.parse(value.getJSONObject("sampleTime").getString("physicalTime")); val bpm = value.optDouble("beatsPerMinute")
                    if(bpm.isFinite() && bpm > 0 && at >= w.start && at < w.end) pulse += Engine.MetricSample(at, bpm, source)
                } }
                completedPulse.incrementAndGet()
                w.copy(heartRate = pulse)
            }
        } } }.awaitAll() }
        progress("Weitere Uhrendaten einordnen")
        suspend fun optional(name: String, read: suspend ()->Unit) {
            try {read()} catch(e:CancellationException) {throw e} catch(_:Exception) {optionalErrors+="$name derzeit nicht verfügbar"}
        }
        optional("Sauerstoff") {points("daily-oxygen-saturation",dailyFilter("daily_oxygen_saturation")) {r ->r.optJSONObject("dailyOxygenSaturation")?.let {v ->val value=v.optDouble("averagePercentage",Double.NaN);if(value>0) extraValue(googleDate(v.getJSONObject("date")),"spo2",value)}}}
        optional("Atemfrequenz") {points("daily-respiratory-rate",dailyFilter("daily_respiratory_rate")) {r ->r.optJSONObject("dailyRespiratoryRate")?.let {v ->val value=v.optDouble("breathsPerMinute",Double.NaN);if(value>0) extraValue(googleDate(v.getJSONObject("date")),"respiratory_rate",value)}}}
        optional("Hauttemperatur") {points("daily-sleep-temperature-derivations",dailyFilter("daily_sleep_temperature_derivations")) {r ->r.optJSONObject("dailySleepTemperatureDerivations")?.let {v ->extraValue(googleDate(v.getJSONObject("date")),"skin_delta",v.optDouble("nightlyTemperatureCelsius",Double.NaN)-v.optDouble("baselineTemperatureCelsius",Double.NaN))}}}
        optional("VO₂max") {points("daily-vo2-max",dailyFilter("daily_vo2_max")) {r ->r.optJSONObject("dailyVo2Max")?.let {v ->val value=v.optDouble("vo2Max",Double.NaN);if(value>0) extraValue(googleDate(v.getJSONObject("date")),"vo2max",value)}}}
        listOf(Triple("distance","distance","millimetersSum"),Triple("active-energy-burned","activeEnergyBurned","kcalSum"),Triple("floors","floors","countSum")).forEach { (type,field,sum) ->
            optional(type) {dailyRollup(type,today.minusDays(30),nextDay) {date,row ->row.optJSONObject(field)?.let {v ->extraValue(date,when(type) {"distance"->"distance_km";"floors"->"floors";else->"active_kcal"},v.optDouble(sum,Double.NaN)/(if(type=="distance") 1_000_000 else 1))}}}
        }
        val bindings = listOf("sleep", "hrv", "rhr", "exercise", "heart_rate", "steps").associateWith { source }
        fun baseline(samples: List<Engine.MetricSample>) = samples.groupBy { it.dailyDate ?: it.at.atZone(zone).toLocalDate() }.map { (date, entries) ->
            val v = entries.map { it.value }.sorted(); Engine.BaselineDay(date, if(v.size % 2 == 0) (v[v.size/2-1] + v[v.size/2])/2 else v[v.size/2], source)
        }
        fun dailyMedian(samples: List<Engine.MetricSample>) = samples.groupBy { it.dailyDate }.values.map { entries ->
            val sorted = entries.map { it.value }.sorted(); entries.first().copy(value = if(sorted.size % 2 == 0) (sorted[sorted.size/2-1]+sorted[sorted.size/2])/2 else sorted[sorted.size/2])
        }
        val input = Engine.NormalizedInput(asOf, zone, sleeps.distinctBy { it.start to it.end }, hrv = dailyMedian(hrv), rhr = dailyMedian(rhr), hrvBaseline = baseline(hrv), rhrBaseline = baseline(rhr),
            workouts = enriched, steps = steps.distinctBy { it.start to it.end }, boundSources = bindings, permissions = bindings.mapValues { Engine.PermissionState.granted },
            trainingHistoryStart=start,trainingHistoryComplete=true,
            recordTypeWindowCompleteness = Engine.RecordType.entries.associateWith { (0..3).associate { "W$it" to true } })
        // Historical normalizer computes previous-night references once and excludes future observations.
        val base = ApprovedWellnessScoreReadout(emptyMap(), emptyMap(), input, emptyList())
        val normalized = historicalScoreReadout(base, asOf, input.recordTypeWindowCompleteness).withV2Goals(goals, personalProfile)
        GoogleHealthImport(normalized.copy(normalizedInput = normalized.normalizedInput.copy(sleeps = input.sleeps, workouts = input.workouts, steps = input.steps)), count.get(),extra,optionalErrors)
    }
}

internal fun googleDate(value: JSONObject): LocalDate = LocalDate.of(value.getInt("year"), value.getInt("month"), value.getInt("day"))
internal fun googleExerciseName(type: String): String = when(type) {
    "WALKING", "WALK" -> "Spaziergang"; "RUNNING", "RUN" -> "Laufen"; "BIKING", "CYCLING" -> "Radfahren"; "STRENGTH_TRAINING" -> "Krafttraining"; "YOGA" -> "Yoga"; else -> "Training"
}

/** Exercise supports civil_start_time only; physical start_time is rejected with HTTP 400.
 * Date boundaries also avoid inventing offsets for sessions recorded while travelling.
 * Actual returned instants are still checked against asOf before entering a score.
 */
internal fun googleExerciseFilter(from: LocalDate, until: LocalDate): String {
    require(until > from)
    return "exercise.interval.civil_start_time >= \"$from\" AND exercise.interval.civil_start_time < \"$until\""
}
