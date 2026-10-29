package com.wabbo.healthconnectcoach

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

internal const val MAX_PLAN_SESSIONS = 10_000

internal data class TrainingGoals(
    val movementMinutes: Int = 150,
    val sessionMinutes: Int = 30,
    val days: Set<Int> = setOf(2, 4, 7),
    val intensity: Double = 1.5,
    val sleepMinutes: Int = 480,
    val weeklyLoadAdjustments: Map<String,Int> = emptyMap(),
) {
    val weeklyLoadTarget: Double get() = days.size * sessionMinutes * intensity
    fun validated(): TrainingGoals {
        require(movementMinutes in 15..1200 && sessionMinutes in 5..180 && days.isNotEmpty() && days.all { it in 1..7 }) { "Bitte wähle gültige Ziele und mindestens einen Trainingstag." }
        require(intensity in setOf(1.0, 1.5, 2.0) && sleepMinutes in 360..660) { "Bitte prüfe Intensität und Schlafziel." }
        require(weeklyLoadAdjustments.size<=52&&weeklyLoadAdjustments.all {(date,percent)->
            val monday=runCatching {LocalDate.parse(date)}.getOrNull()
            monday?.dayOfWeek==DayOfWeek.MONDAY&&percent in 75..125
        }) {"Die Wochenziel-Anpassung muss zwischen 75 und 125 Prozent liegen."}
        return this
    }
}

internal data class PlannedSession(val id: String, val date: LocalDate, val title: String,
    val minutes: Int, val intensity: Double, val completed: Boolean = false,
    val recordedActivity: RecordedTraining? = null)
internal data class TrainingPlan(val goals: TrainingGoals, val sessions: List<PlannedSession>,
    val revision: Long = 1, val celebrated: Set<String> = emptySet(), val scheduledThrough: LocalDate? = null)

/** All mutations are validated before persistence. A planned rest day never breaks the streak. */
internal object TrainingPlanLogic {
    fun create(goals: TrainingGoals, from: LocalDate): TrainingPlan {
        goals.validated()
        return TrainingPlan(goals, (0L..41L).map { from.plusDays(it) }.filter { it.dayOfWeek.value in goals.days }.map {
            PlannedSession(UUID.randomUUID().toString(), it, if (goals.intensity == 1.0) "Leicht bewegen & mobilisieren" else "Deine Trainingseinheit", goals.sessionMinutes, goals.intensity)
        },scheduledThrough=from.plusDays(41))
    }
    /** Extend only beyond the generated horizon: moved/deleted dates inside it stay intentional. */
    fun extend(plan: TrainingPlan, today: LocalDate): TrainingPlan {
        plan.goals.validated()
        val target=today.plusDays(41)
        // Older documents did not store the horizon. A far-future manually moved unit
        // must not suppress recurring planning for months.
        val first=plan.sessions.minOfOrNull {it.date}
        val known=plan.scheduledThrough ?: first?.let {minOf(plan.sessions.maxOf {s->s.date},it.plusDays(41))} ?: today.minusDays(1)
        if(known>=target) return plan
        val from=known.plusDays(1)
        require(ChronoUnit.DAYS.between(from,target)<=MAX_PLAN_SESSIONS) {"Der Planzeitraum ist zu groß. Bitte die Ziele erneut festlegen."}
        val occupied=plan.sessions.map {it.date}.toSet()
        val additions=generateSequence(from) {it.plusDays(1)}.takeWhile {it<=target}
            .filter {it.dayOfWeek.value in plan.goals.days&&it !in occupied}.map {
                PlannedSession(UUID.randomUUID().toString(),it,if(plan.goals.intensity==1.0) "Leicht bewegen & mobilisieren" else "Deine Trainingseinheit",plan.goals.sessionMinutes,plan.goals.intensity)
            }.toList()
        require(plan.sessions.size+additions.size<=MAX_PLAN_SESSIONS) {"Der Plan ist voll. Bitte ältere Einträge prüfen."}
        return plan.copy(revision=plan.revision+1,sessions=(plan.sessions+additions).sortedBy {it.date},scheduledThrough=target)
    }
    fun complete(plan: TrainingPlan, id: String, done: Boolean, today: LocalDate): TrainingPlan {
        val entry = plan.sessions.singleOrNull { it.id == id } ?: error("Die Einheit ist nicht mehr im Plan.")
        require(!entry.date.isAfter(today)) { "Eine zukünftige Einheit kann noch nicht als erledigt gelten." }
        return plan.copy(revision = plan.revision + 1, sessions = plan.sessions.map { if (it.id == id) it.copy(completed = done, recordedActivity = if(done) it.recordedActivity else null) else it })
    }
    fun move(plan: TrainingPlan, id: String, date: LocalDate, today: LocalDate): TrainingPlan {
        require(date in today..today.plusDays(90)) { "Das neue Datum muss innerhalb der nächsten 90 Tage liegen." }
        val item = plan.sessions.singleOrNull { it.id == id } ?: error("Einheit nicht gefunden.")
        require(!item.completed && item.date>=today) { "Erledigte oder vergangene Einheiten werden nicht nachträglich verschoben." }
        return plan.copy(revision = plan.revision + 1, sessions = plan.sessions.map { if (it.id == id) it.copy(date = date) else it }.sortedBy { it.date })
    }
    fun streak(plan: TrainingPlan, today: LocalDate): Int {
        val days = plan.sessions.filter { it.date <= today }.groupBy { it.date }.toSortedMap().entries.toList().asReversed()
        var count = 0
        for ((date, entries) in days) {
            if (entries.all { it.completed }) count++
            else if (date != today) break
        }
        return count
    }
    fun week(plan: TrainingPlan, at: LocalDate): List<PlannedSession> {
        val start = at.with(DayOfWeek.MONDAY)
        return plan.sessions.filter { it.date in start..start.plusDays(6) }
    }
}

internal class TrainingPlanStore(private val document: SecureLocalDocument) {
    fun read(): TrainingPlan? = document.read()?.let { decodePlan(JSONObject(it)) }
    fun previous(): TrainingPlan? = document.read()?.let { JSONObject(it).optJSONObject("previous")?.let(::decodePlan) }
    fun save(plan: TrainingPlan, previous: TrainingPlan? = null) { plan.goals.validated(); require(plan.sessions.size<=MAX_PLAN_SESSIONS); document.write(encodePlan(plan).apply { if(previous != null) put("previous", encodePlan(previous)) }.toString()) }
}

internal fun encodeTrainingGoals(goals: TrainingGoals)=JSONObject().put("movement",goals.movementMinutes)
    .put("duration",goals.sessionMinutes).put("intensity",goals.intensity).put("sleep",goals.sleepMinutes)
    .put("days",JSONArray(goals.days.sorted())).put("weeklyLoadAdjustments",JSONObject(goals.weeklyLoadAdjustments))
internal fun decodeTrainingGoals(g: JSONObject): TrainingGoals {
    val days=g.getJSONArray("days")
    return TrainingGoals(g.getInt("movement"),g.getInt("duration"),(0 until days.length()).map {days.getInt(it)}.toSet(),
        g.getDouble("intensity"),g.optInt("sleep",480),g.optJSONObject("weeklyLoadAdjustments")?.let {obj->obj.keys().asSequence().associateWith {obj.getInt(it)}}.orEmpty()).validated()
}

internal fun encodePlan(plan: TrainingPlan): JSONObject = JSONObject().apply {
    put("scheduledThrough",plan.scheduledThrough?.toString() ?: JSONObject.NULL)
    put("revision", plan.revision); put("goals",encodeTrainingGoals(plan.goals))
    put("celebrated", JSONArray(plan.celebrated.toList()))
    put("sessions", JSONArray().apply { plan.sessions.forEach { s -> put(JSONObject().put("id", s.id).put("date", s.date.toString())
        .put("title", s.title).put("minutes", s.minutes).put("intensity", s.intensity).put("done", s.completed)
        .apply {s.recordedActivity?.let {put("recordedActivity",encodeRecordedTraining(it))}}) } })
}
internal fun decodePlan(o: JSONObject): TrainingPlan {
    val goals=decodeTrainingGoals(o.getJSONObject("goals"))
    val rows = o.getJSONArray("sessions"); require(rows.length() <= MAX_PLAN_SESSIONS)
    val sessions = (0 until rows.length()).map { rows.getJSONObject(it).let { s ->
        PlannedSession(s.getString("id"), LocalDate.parse(s.getString("date")), s.getString("title").take(100), s.getInt("minutes"), s.getDouble("intensity"), s.getBoolean("done"),
            s.optJSONObject("recordedActivity")?.let(::decodeRecordedTraining))
    } }
    require(sessions.all { it.minutes in 5..180 && it.intensity in setOf(1.0, 1.5, 2.0) && (it.recordedActivity==null||it.completed) } && sessions.map { it.id }.distinct().size == sessions.size)
    val celebrated = o.optJSONArray("celebrated") ?: JSONArray()
    return TrainingPlan(goals, sessions.sortedBy { it.date }, o.getLong("revision"), (0 until celebrated.length()).map { celebrated.getString(it) }.toSet(),
        o.optString("scheduledThrough").takeIf {it.isNotBlank()&&it!="null"}?.let(LocalDate::parse))
}


internal fun TrainingGoals.withWeeklyAdjustment(today: LocalDate,percent: Int): TrainingGoals {
    require(percent in 75..125)
    val key=today.with(DayOfWeek.MONDAY).toString()
    val next=if(percent==100) weeklyLoadAdjustments-key else weeklyLoadAdjustments+(key to percent)
    return copy(weeklyLoadAdjustments=next.toSortedMap().entries.toList().takeLast(52).associate {it.key to it.value}).validated()
}
