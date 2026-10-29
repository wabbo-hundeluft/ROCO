package com.wabbo.healthconnectcoach

import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine as E
import java.time.LocalDate
import java.time.ZoneId

internal data class TrainingDayGuidance(val kind: String,val title: String,val message: String)

/** Transparent wellness guidance, not a diagnosis or an automatic plan mutation. */
internal fun trainingDayGuidance(plan: TrainingPlan?,day: DayInsight?,scores: Map<String,E.ScoreResult>,today: LocalDate,zone: ZoneId=ZoneId.systemDefault()): TrainingDayGuidance {
    val sessions=plan?.sessions.orEmpty().filter {it.date==today}
    val measured=day?.takeIf {it.date==today}?.activities.orEmpty().any {it.end.atZone(zone).toLocalDate()==today}
    fun fresh(id: String)=scores[id]?.takeIf {!it.stale&&it.asOf.atZone(zone).toLocalDate()==today}?.score
    val energy=fresh(E.BODY_BATTERY_ID);val sleep=fresh(E.SLEEP_ID)
    val weekly=scores[E.SESSION_LOAD_ID]?.takeIf {!it.stale&&it.algorithmVersion==E.WEEKLY_MODEL_VERSION&&it.asOf.atZone(zone).toLocalDate().with(java.time.DayOfWeek.MONDAY)==today.with(java.time.DayOfWeek.MONDAY)}?.score
    if(sessions.isNotEmpty()&&sessions.all {it.completed}) return TrainingDayGuidance("completed","Für heute geschafft","Deine geplanten Einheiten sind für heute erledigt. Jetzt darf Erholung im Mittelpunkt stehen.")
    if(energy!=null&&energy<=30||sleep!=null&&sleep<55||weekly!=null&&weekly>100) return TrainingDayGuidance("recovery","Heute eher Erholung",when {
        energy!=null&&energy<=30->"Dein aktueller Modellakku ist niedrig. Geh es heute locker an; eine geplante Einheit können wir bei Bedarf gemeinsam verschieben."
        sleep!=null&&sleep<55->"Dein aktueller Schlafscore spricht für einen ruhigeren Tag. Locker bewegen ist eine Option; zusätzliches hartes Training ist heute kein Muss."
        else->"Du liegst über deinem Wochenziel. Heute hat Erholung Vorrang; weitere Belastung ist kein Bonus."
    })
    if(plan==null) return if(measured) TrainingDayGuidance("active_recorded","Heute schon aktiv","Deine Uhr hat heute Bewegung aufgezeichnet. Lass uns passende Trainingstage und Erholung gemeinsam festlegen.") else TrainingDayGuidance("unplanned","Dein Rhythmus ist noch offen","Lass uns zuerst besprechen, welche Tage zu Training und welche zur Erholung passen. Ich erfinde dafür keine Ziele.")
    if(sessions.isEmpty()) return TrainingDayGuidance("rest","Heute ist ein Ruhetag","Heute steht kein Training in deinem Plan. Erholung gehört dazu; ein lockerer Spaziergang bleibt eine freiwillige Option.")
    if(measured) return TrainingDayGuidance("active_recorded","Heute schon aktiv","Ein Training wurde heute erfasst. Prüfe, ob du die Aufzeichnung deiner geplanten Einheit zuordnen möchtest; danach darfst du auch abschalten.")
    val pending=sessions.filter {!it.completed}
    return TrainingDayGuidance("active","Heute ist ein aktiver Tag","Heute geplant: ${pending.joinToString {it.title}} · ${pending.sumOf {it.minutes}} Minuten. "+if(energy==null) "Das ist eine planeigene Orientierung; eine frische Energieeinschätzung fehlt noch." else "Bleib bei deinem vereinbarten Tempo und passe es an dein Befinden an.")
}
