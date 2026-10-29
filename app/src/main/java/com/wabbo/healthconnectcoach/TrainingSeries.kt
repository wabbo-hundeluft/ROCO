package com.wabbo.healthconnectcoach

import java.time.LocalDate
import java.util.UUID

internal fun scheduleTrainingSeries(plan: TrainingPlan,start: LocalDate,end: LocalDate,weekdays: Set<Int>,title: String,
    minutes: Int,intensity: Double,replaceExisting: Boolean,today: LocalDate): Pair<TrainingPlan,String> {
    require(start in today..today.plusDays(90)&&end in start..today.plusDays(90)) {"Bitte einen Zeitraum innerhalb der nächsten 90 Tage wählen."}
    require(weekdays.isNotEmpty()&&weekdays.all {it in 1..7}) {"Bitte gültige Trainingstage wählen."}
    require(title.trim().length in 1..100&&minutes in 5..180&&intensity in setOf(1.0,1.5,2.0)) {"Bitte Titel, Dauer und Intensität prüfen."}
    val dates=generateSequence(start) {it.plusDays(1)}.takeWhile {it<=end}.filter {it.dayOfWeek.value in weekdays}.toList()
    require(dates.isNotEmpty()) {"Keiner der gewählten Wochentage liegt im Zeitraum."}
    fun matches(s: PlannedSession,d: LocalDate)=s.date==d&&s.title==title.trim()&&s.minutes==minutes&&s.intensity==intensity
    val entries=dates.mapNotNull {d->
        if(plan.sessions.any {it.completed&&matches(it,d)}) null
        else plan.sessions.firstOrNull {!it.completed&&matches(it,d)} ?: PlannedSession(UUID.randomUUID().toString(),d,title.trim(),minutes,intensity)
    }
    val ids=entries.map {it.id}.toSet()
    val retained=plan.sessions.filter {it.completed||(!replaceExisting||it.date !in dates)&&it.id !in ids}
    val sessions=(retained+entries).sortedWith(compareBy<PlannedSession> {it.date}.thenBy {it.id})
    require(sessions.size<=MAX_PLAN_SESSIONS) {"Der Plan ist voll. Bitte zuerst ältere Einträge prüfen."}
    if(sessions==plan.sessions.sortedWith(compareBy<PlannedSession> {it.date}.thenBy {it.id})) return plan to "Die Trainingsserie ist bereits so eingeplant."
    return plan.copy(revision=plan.revision+1,sessions=sessions) to "${entries.size} Termine für ${title.trim()} vom $start bis $end eingeplant. Erledigte Einheiten bleiben erhalten."
}
