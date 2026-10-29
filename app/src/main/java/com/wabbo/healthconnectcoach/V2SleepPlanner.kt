package com.wabbo.healthconnectcoach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.wabbo.healthconnectcoach.score.PersonalScoreProfile

private fun clockText(time:ZonedDateTime)=time.format(DateTimeFormatter.ofPattern("HH:mm"))
@Composable internal fun SleepPlannerHomeCard(plan:NightPlan,configured:Boolean,open:()->Unit) {
    V2ClaySurface(onClick=open,onClickLabel=RocoStrings.translate("Schlafplaner öffnen"),color=Color(0xFFF0EDF7),edgeColor=Color(0xFFD9D1E8)) {
        Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {V2Icon("sleep",tint=V2Sleep);RocoText("Deine nächste Nacht",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);V2Icon("next",tint=V2Sleep)}
            if(configured) {RocoText("${clockText(plan.bedtime)} ins Bett",fontSize=30.sp,color=V2Ink);RocoText("${clockText(plan.wake)} aufstehen · ${durationLabel(plan.sleepMinutes.toDouble())} Schlaf vorgeschlagen",color=V2Muted)}
            else {RocoText("Wann möchtest du aufstehen?",style=MaterialTheme.typography.titleMedium);RocoText("Plane deine Bettzeit mit deinem Schlafziel und den vorhandenen Daten.",color=V2Muted)}
        }
    }
}
@Composable internal fun SleepPlannerScreen(settings:SleepPlannerSettings?,days:List<DayInsight>,today:LocalDate,connector:String,now:Instant,zone:ZoneId,
    goals:TrainingGoals?,profile:PersonalScoreProfile,robot:String,ready:Boolean,saving:Boolean,error:String?,back:()->Unit,
    discuss:(String)->Unit,save:(SleepPlannerSettings,()->Unit)->Unit) {
    var editing by rememberSaveable(settings) {mutableStateOf(settings==null)}
    var time by rememberSaveable(settings) {mutableStateOf((settings ?: SleepPlannerSettings()).let {"%02d:%02d".format(it.wakeMinute/60,it.wakeMinute%60)})}
    var windDown by rememberSaveable(settings) {mutableIntStateOf(settings?.windDownMinutes ?: 30)}
    var validation by rememberSaveable {mutableStateOf<String?>(null)}
    fun parsed():SleepPlannerSettings {
        require(time.matches(Regex("[0-2][0-9]:[0-5][0-9]"))) {"Bitte die Aufstehzeit als HH:mm eingeben, zum Beispiel 07:00."}
        val parts=time.split(':');return SleepPlannerSettings(parts[0].toInt()*60+parts[1].toInt(),windDown).validated()
    }
    val proposal=runCatching {parsed()}.getOrNull() ?: settings ?: SleepPlannerSettings()
    val plan=planTonight(if(editing) proposal else settings ?: proposal,days,today,connector,zone,now,goals,profile)
    V2PageHeader("Deine nächste Nacht",plan.wake.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM",Locale.getDefault())),back)
    error?.let {RocoText(it,color=MaterialTheme.colorScheme.error)}
    if(!ready) {RocoText("Dein Schlafplaner wird geöffnet …");return}
    V2RobotNote(robot,if(plan.windowStarted&&settings!=null) "Das vorgeschlagene Bettzeitfenster hat bereits begonnen. Wir verschieben deine Aufstehzeit nicht automatisch. Plane für heute so viel Ruhe ein, wie dein Alltag zulässt." else "Lass uns deiner Erholung Platz geben. Das ist eine Planungshilfe aus deinen Angaben und erfassten Daten.",mood=RobotMood.SLEEPING)
    if(editing) V2Card("Deine Aufstehzeit") {
        V2LabeledInput(time,{time=it.take(5)},"Aufstehen · HH:mm",!saving)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf("06:30","07:00","08:00").forEach {v->FilterChip(selected=time==v,onClick={time=v},enabled=!saving,label={RocoText(v)})}}
        RocoText("$windDown Minuten Abendroutine",style=MaterialTheme.typography.titleMedium)
        Slider(value=windDown.toFloat(),onValueChange={windDown=(it/5).toInt()*5},valueRange=0f..90f,steps=17,enabled=!saving)
        validation?.let {RocoText(it,color=MaterialTheme.colorScheme.error)}
        ClayButton(enabled=!saving,onClick={runCatching {parsed()}.onSuccess {save(it) {editing=false;validation=null}}.onFailure {validation=it.message}}) {RocoText(if(saving) "Wird gespeichert …" else "Schlafplan speichern")}
        if(settings!=null) TextButton(enabled=!saving,onClick={editing=false;validation=null}) {RocoText("Abbrechen")}
    } else TextButton(onClick={editing=true}) {RocoText("Aufstehzeit bearbeiten")}
    V2Card(if(settings==null) "Dein Vorschaufenster" else "Dein Bettzeitfenster") {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Column {RocoText("INS BETT",style=MaterialTheme.typography.labelMedium,color=V2Muted);RocoText(clockText(plan.bedtime),fontSize=36.sp)}
            V2Icon("sleep",tint=V2Sleep,size=30.dp)
            Column(horizontalAlignment=Alignment.End) {RocoText("AUFSTEHEN",style=MaterialTheme.typography.labelMedium,color=V2Muted);RocoText(clockText(plan.wake),fontSize=36.sp)}
        }
        RocoText("Ab ${clockText(plan.windDown)} herunterfahren · ${durationLabel(plan.bedMinutes.toDouble())} Zeit im Bett",color=V2Muted)
        RocoText("${durationLabel(plan.sleepMinutes.toDouble())} Schlaf als Vorschlag",style=MaterialTheme.typography.titleLarge)
        RocoText("Das stellt keinen Wecker. Die Zeitplanung wird lokal berechnet.",style=MaterialTheme.typography.bodySmall,color=V2Muted)
    }
    V2Card("So entsteht dein Vorschlag", "${plan.source} · ${plan.observedNights} beobachtete Nächte im letzten Dreitagesfenster") {
        listOf("Dein Schlafziel" to plan.baseMinutes,"Beobachtetes Defizit" to plan.debtMinutes,"Heutiges Training" to plan.trainingMinutes,"Gutschrift für Nickerchen" to -plan.napCreditMinutes).forEach {(label,minutes)->
            Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {RocoText(label,Modifier.weight(1f));RocoText((if(minutes>0&&label!="Dein Schlafziel") "+" else if(minutes<0) "−" else "")+durationLabel(kotlin.math.abs(minutes).toDouble()))}
                Box(Modifier.fillMaxWidth().height(6.dp).background(Color(0xFFE5DFEC),RoundedCornerShape(4.dp))) {Box(Modifier.fillMaxWidth((kotlin.math.abs(minutes)/plan.baseMinutes.toFloat()).coerceIn(0f,1f)).fillMaxHeight().background(V2Sleep,RoundedCornerShape(4.dp)))}
            }
        }
        RocoText(if(plan.efficiencyAssumed) "Für Wachzeit im Bett planen wir vorläufig mit 90 % Schlafeffizienz. Das ist eine Annahme, bis mindestens fünf frühere Nächte derselben Quelle vorliegen." else "Deine frühere Schlafeffizienz von ${(plan.efficiency*100).toInt()} % bestimmt den Zeitpuffer im Bett, begrenzt auf maximal 90 Minuten.",style=MaterialTheme.typography.bodySmall,color=V2Muted)
        RocoText("Zusatzbedarf: halbes beobachtetes Defizit, maximal 60 Minuten; ein Viertel heutiger Trainingsminuten, maximal 30; halbe erfasste Nickerchenzeit, maximal 45 Minuten Gutschrift. Fehlende Nächte zählen nicht als null Stunden. Die Schlaf-Untergrenze bleibt erhalten. Eigene Planungsregeln, keine Messung deines tatsächlichen Schlafbedarfs.",style=MaterialTheme.typography.bodySmall,color=V2Muted)
    }
    ClayButton(onClick={discuss("Besprich meinen Schlafplan für die nächste Nacht. Lokal vorgeschlagen: ${plan.sleepMinutes} Minuten Schlaf, Bettzeit ${plan.bedtime}, Aufstehen ${plan.wake}, beobachtetes Defizit-Zuschlag ${plan.debtMinutes} Minuten. Kennzeichne diesen Plan als Schätzung und berücksichtige meine gespeicherten Angaben.")},secondary=true) {RocoText("Mit $robot besprechen")}
}
