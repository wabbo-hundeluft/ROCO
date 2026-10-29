package com.wabbo.healthconnectcoach

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine as Engine
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable internal fun V2ScoreDetails(id: String, score: Engine.ScoreResult?, day: DayInsight?, days: List<DayInsight>, robot: String,
    back: ()->Unit, source: ()->Unit, discuss: ()->Unit, onActivity: (ActivityInsight)->Unit) {
    var sleepTab by rememberSaveable(id) {mutableStateOf("Nacht")}
    V2PageHeader(scoreTitle(id),(day?.date ?: score?.asOf?.atZone(ZoneId.systemDefault())?.toLocalDate())?.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM",Locale.getDefault())),back,info=source)
    if(score?.status!=Engine.Status.available) {
        RocoText(when(score?.status) {Engine.Status.stale->"Gespeicherter Stand · ${score.asOf.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d. MMM · HH:mm",Locale.getDefault()))}";Engine.Status.provisional->"Vorläufige Einordnung · einzelne Bausteine fehlen oder sind angenähert";else->"Noch kein berechenbarer Wert"},style=MaterialTheme.typography.bodySmall,color=V2Muted)
    }
    when(id) {
        Engine.BODY_BATTERY_ID -> {
            V2DetailNumber(score?.score,"/100",batteryLabel(score?.score),V2Green)
            V2RobotNote(robot,score?.components?.get("START")?.let {start ->"Dein Modellakku startete bei $start. Wachzeit und Aktivität haben bisher ${score.components["USED"] ?: 0} Punkte verbraucht."} ?: "Dein Akku entsteht aus Schlaf, HRV und Ruhepuls. Aktivität reduziert anschließend den Modellwert.",mood=robotScoreMood(Engine.BODY_BATTERY_ID,score?.score))
            V2Card("Dein Tag in Energie",gap=12.dp) {
                RocoText("Rekonstruierter Modellverlauf: Der Verbrauch wird innerhalb erfasster Trainings und Schrittintervalle gleichmäßig verteilt. Keine kontinuierliche Energiemessung.",style=MaterialTheme.typography.bodySmall,color=V2Muted)
                V2LineChart(day?.energyCurve.orEmpty().map {V2PlotPoint(it.first,it.second.toDouble())},"Pkt.",intraday=true)
                day?.activities.orEmpty().filter {it.start.atZone(ZoneId.systemDefault()).toLocalDate()==day?.date}.forEach {entry ->V2EnergyEvent(entry) {onActivity(entry)}}
            }
            V2Section("So ergibt sich dein Akku") {
                V2Card(contentPadding=PaddingValues(horizontal=22.dp,vertical=5.dp),gap=0.dp) {
                    val parts=score?.components.orEmpty()
                    val remaining=if(parts["USED"]!=null&&parts["TRAINING"]!=null) (parts.getValue("USED")-parts.getValue("TRAINING")).coerceAtLeast(0) else null
                    listOf("Startbasis am Morgen" to parts["START"],"Training heute" to parts["TRAINING"],"Übriger Modellverbrauch" to remaining,"Jetzt verfügbar" to score?.score).forEachIndexed {index,(label,value)->
                        Row(Modifier.fillMaxWidth().padding(vertical=15.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            RocoText(label,Modifier.weight(1f),fontSize=13.sp,lineHeight=20.sp,fontWeight=if(index==3) FontWeight(750) else FontWeight.Normal,color=Color(0xFF6B7660))
                            RocoText(value?.let {(if(index==0) "+" else if(index in 1..2) "−" else "")+it} ?: "–",fontSize=if(index==3) 27.sp else 17.sp,lineHeight=if(index==3) 33.sp else 24.sp,fontWeight=FontWeight(650),color=when(index) {0->Color(0xFF65945D);3->Color(0xFF3E4B34);else->Color(0xFF9E7D4C)})
                        }
                        if(index<3) HorizontalDivider(color=Color(0xFFEEF0E8))
                    }
                    var showCalculation by rememberSaveable {mutableStateOf(false)}
                    TextButton(onClick={showCalculation=!showCalculation}) {RocoText(if(showCalculation) "Rechenweg schließen" else "Rechenweg und Grenzen",fontSize=12.sp)}
                    if(showCalculation) {
                        RocoText("Erholungsbasis: ${parts["RECOVERY"] ?: "–"} · beobachtetes Schlafdefizit: ${parts["SLEEP_DEBT"] ?: "–"} · Wachzeit: ${parts["AWAKE"] ?: "–"} · Schritte außerhalb von Trainings: ${parts["STEPS"] ?: "–"}",fontSize=12.sp,lineHeight=19.sp,color=V2Muted)
                        RocoText("Die Wachzeit ist eine Modellannahme: 2 Punkte pro Stunde, nach 12 Stunden zusätzlich 1. Kein gemessener Stresswert. Verbrauch wird nicht durch bloßen Zeitablauf zurückgebucht. Teilwerte sind gerundet; bei 0 bleibt der Akku leer.",Modifier.padding(vertical=12.dp),fontSize=12.sp,lineHeight=19.sp,color=V2Muted)
                    }
                }
            }
            V2Section("Deine Startbasis") {V2Card(gap=0.dp) {
                V2Factor("Schlafdauer",durationLabel(day?.metrics?.get("sleep_minutes")),"Vergleich mit deinem gewählten Schlafziel: ${durationLabel(day?.metrics?.get("sleep_goal"))}.",score?.components?.get("D"),baseline="Dein Ziel: ${durationLabel(day?.metrics?.get("sleep_goal"))}",tint=V2Sleep)
                V2Factor("HRV",day?.metrics?.get("hrv")?.let {"${it.roundToInt()} ms"} ?: "–",vitalExplanation(day,"hrv"),score?.components?.get("H"),baseline=vitalBaselineLabel(day,"hrv"))
                V2Factor("Ruhepuls",day?.metrics?.get("rhr")?.let {"${it.roundToInt()} bpm"} ?: "–",vitalExplanation(day,"rhr"),score?.components?.get("P"),baseline=vitalBaselineLabel(day,"rhr"),tint=V2Load)
            }}
        }
        Engine.SESSION_LOAD_ID -> {
            val weekly=score?.takeIf {it.algorithmVersion.endsWith("/weekly")}
            val value=weekly?.score
            V2DetailNumber(value,"Punkte",when {value==null->"Ziel und Datengrundlage prüfen";value>100->"Über deinem Wochenziel";value==100->"Wochenziel erreicht";else->"Dein Fortschritt diese Woche"},V2Load)
            V2RobotNote(robot,when {value==null->"Dein Ziel lernt aus vollständig gelesenen früheren Trainingswochen. Bis genug Verlauf vorliegt, dient dein bestätigter Plan als Startpunkt.";value>100->"Du liegst über deinem persönlichen Wochenziel. Mehr ist kein Bonus — halte Raum für Erholung frei.";else->"Jedes erfasste Training trägt bei. Ruhetage fügen keine neue Belastung hinzu und gehören zu deinem Plan."},mood=robotScoreMood(Engine.SESSION_LOAD_ID,value))
            V2Card("Dein Weg zum Wochenziel","Montag bis zum Stand dieses Werts. Punkte gewichten Dauer und Puls; Minuten bleiben eine eigene Größe. 100 Punkte entsprechen deinem persönlichen Ziel; Werte darüber zeigen zusätzlichen Umfang.") {
                LinearProgressIndicator(progress={(value ?: 0).coerceIn(0,100)/100f},modifier=Modifier.fillMaxWidth(),color=if(value!=null&&value>100) V2Load else V2Green)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {RocoText("0");RocoText("Ziel 100")}
                if(value!=null&&value>100) RocoText("+${value-100} Punkte über dem Ziel",fontWeight=FontWeight.Bold,color=V2Load)
                RocoText("${weekly?.components?.get("WEEK_MIN") ?: 0} erfasste Trainingsminuten · ${weekly?.components?.get("WEEK_LOAD") ?: 0} gewichtete Belastungseinheiten",style=MaterialTheme.typography.bodySmall,color=V2Muted)
                if(day?.reconstructed==true) RocoText("Rückblick: aus damaligen Trainings und dem vor dieser Woche verfügbaren Verlauf rekonstruiert. Bei kurzer Basis bleibt das Ziel vorläufig.",style=MaterialTheme.typography.bodySmall,color=V2Muted)
            }
            weekly?.components?.get("MAX_HR")?.let { maximum ->
                V2Card("Deine persönliche Pulsintensität") {
                    RocoText("Maximalpuls: $maximum bpm · " + if(weekly.components["MAX_HR_ESTIMATED"]==1) "aus dem Geburtsjahr geschätzt" else "von dir als gemessen angegeben")
                    RocoText("Der Abstand zwischen Ruhepuls und Maximalpuls bestimmt die Intensität. Hohe Intensität zählt stärker. Dieselbe Berechnung gilt für frühere Wochen und heutige Trainings.", color=V2Muted)
                }
            }
            V2Section("Was beigetragen hat") {
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    if(day?.activities.isNullOrEmpty()) V2Card {RocoText("Noch kein erfasstes Training in dieser Kalenderwoche.",color=V2Muted)}
                    day?.activities.orEmpty().forEach {entry ->V2ClaySurface(onClick={onActivity(entry)},onClickLabel="${entry.title} erklären",modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp)) {
                        Row(Modifier.padding(20.dp),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=Alignment.CenterVertically) {
                            V2Icon("training",tint=V2Load)
                            Column(Modifier.weight(1f)) {RocoVerbatimText(entry.title,style=MaterialTheme.typography.titleMedium);RocoText("${entry.start.atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("EEE d.M.",Locale.getDefault()))} · ${durationLabel(entry.minutes)}",style=MaterialTheme.typography.bodySmall,color=V2Muted)}
                            RocoText(entry.loadPoints?.let {String.format(Locale.getDefault(),"%.1f Pkt.",it)} ?: "–",fontWeight=FontWeight.Bold);V2Icon("next")
                        }
                    } }
                }
            }
            V2Card("So werden daraus Punkte") {
                val units=weekly?.components?.get("WEEK_LOAD_X100")?.div(100.0) ?: weekly?.components?.get("WEEK_LOAD")?.toDouble()
                val target=weekly?.let(::weeklyTargetValue)
                RocoText(if(units!=null&&target!=null) "${String.format(Locale.getDefault(),"%.1f",units)} ÷ ${String.format(Locale.getDefault(),"%.1f",target)} × 100 ≈ ${value ?: "–"} Punkte" else "Erfasste gewichtete Last ÷ persönliches Wochenziel × 100",style=MaterialTheme.typography.titleMedium)
                RocoText("Trainingsminuten beschreiben Zeit. Deine Last gewichtet zusätzlich den gemessenen Puls; Punkte zeigen den Anteil am persönlichen Ziel. Gleiche Zahlen können zufällig entstehen, es ist keine 1:1-Übernahme von Cardio-Minuten.",style=MaterialTheme.typography.bodySmall,color=V2Muted)
                weekly?.components?.get("GOAL_PERCENT")?.takeIf {it!=100}?.let {RocoText("Diese Woche bewusst auf $it % der automatischen Basis angepasst.",color=V2Load)}
                ClayButton(onClick={discuss()},secondary=true) {RocoText("Wochenziel mit $robot besprechen")}
            }
            V2Card("Dein Ziel lernt mit dir",if(weekly?.components?.get("ADAPTIVE")==1) "Aus ${weekly.components["HISTORY_WEEKS"]} abgeschlossenen Trainingswochen" else "Lernphase · dein bestätigter Plan dient als Startziel") {
                val target=weekly?.let(::weeklyTargetValue)
                RocoText("100 Punkte",style=MaterialTheme.typography.headlineMedium)
                RocoText(target?.let {"entsprechen diese Woche ${String.format(Locale.getDefault(),"%.1f",it)} gewichteten Belastungseinheiten"} ?: "Noch keine ausreichende Zielgrundlage",color=V2Muted)
                if(weekly?.components?.get("ADAPTIVE")==1) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Column {RocoText("Längerfristige Last",style=MaterialTheme.typography.bodySmall);RocoText(weekly.components["BASE_WEEKLY"]?.toString() ?: "–",style=MaterialTheme.typography.titleLarge)};Column {RocoText("Kurzfristige Last",style=MaterialTheme.typography.bodySmall);RocoText(weekly.components["ACUTE_WEEKLY"]?.toString() ?: "–",style=MaterialTheme.typography.titleLarge)}}
                    val monday=(day?.date ?: LocalDate.now()).with(java.time.DayOfWeek.MONDAY)
                    val history=(4 downTo 1).mapNotNull {index ->weekly.components["PREV_WEEK_$index"]?.let {V2PlotPoint(monday.minusWeeks(index.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant(),it.toDouble())}}
                    V2LineChart(history,"Last",V2Load,0.0,maxOf(100.0,(target ?: 100.0)*1.1))
                    RocoText(if((weekly.components["HISTORY_WEEKS"] ?: 0)>=6) "Ein langsamer Lastverlauf mit 42-Tage-Zeitkonstante und ein schneller mit 7 Tagen bilden den Hintergrund. Das Ziel wird zu Wochenbeginn aus früheren Wochen abgeleitet. Dein heutiges Training verschiebt das Ziel dieser Woche nicht." else "Die ersten drei vollständig beobachteten Wochen bilden eine vorläufige Basis. Bereits diese Lernphase berücksichtigt den kurz- und langfristigen Lastverlauf; mit mehr Historie wird die Basis breiter. Neue Trainings verändern das Ziel dieser Woche nicht.",style=MaterialTheme.typography.bodySmall,color=V2Muted)
                    RocoText("Mehrere Wochen regelmäßiger höherer Last können das Ziel erhöhen. Weniger Training kann es senken. Änderungen werden pro Woche auf höchstens 10 % geglättet; das ist eine Modellregel, keine medizinisch belegte Sicherheitsgrenze.",style=MaterialTheme.typography.bodySmall,color=V2Muted)
                }
                RocoText("Die internen Einheiten gewichten Trainingsdauer und Puls. Das sind weder deine tatsächlichen Minuten noch Googles Kardio-Minuten. Sichtbar zählen Belastungspunkte: 100 × Wochenlast ÷ persönliches Ziel.",style=MaterialTheme.typography.bodySmall,color=V2Muted)
            }
            V2Card("Wie die Punkte entstehen") {RocoText("Ein Pulsabstand von 40 Schlägen pro Minute über dem persönlichen Ruhepuls zählt im Modell ungefähr wie eine ungewichtete Trainingsminute. Dein bestätigter Plan enthält entsprechend gewichtete Zielminuten. Fehlen ausreichende Pulsdaten, zählt die reine Trainingsdauer als gekennzeichnete Näherung.",style=MaterialTheme.typography.bodyMedium,color=V2Muted)}
        }
        else -> {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(top=7.dp,bottom=6.dp)) {
                if(maxWidth<300.dp||LocalDensity.current.fontScale>1.3f) Column(verticalArrangement=Arrangement.spacedBy(18.dp)) {V2SleepRing(score?.score);V2SleepDuration(day)}
                else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceAround) {V2SleepRing(score?.score);V2SleepDuration(day)}
            }
            V2MetricSegments(listOf("Nacht","Benchmarks","Verlauf"),sleepTab) {sleepTab=it}
            when(sleepTab) {
                "Benchmarks" -> SleepBenchmarks(day,days)
                "Verlauf" -> V2Card("Deine letzten Nächte","Gleiche Schlafquelle und Score-Version; fehlende Nächte bleiben Lücken.") {
                    val selected=day?.date ?: LocalDate.now()
                    val history=comparableSleepHistory(days,day).associateBy {it.date}
                    V2LineChart((6 downTo 0).map {offset ->val date=selected.minusDays(offset.toLong());V2PlotPoint(date.atStartOfDay(ZoneId.systemDefault()).toInstant(),history[date]?.scores?.get(Engine.SLEEP_ID)?.score?.toDouble())},"Pkt.",V2Sleep)
                }
                else -> {
                    V2RobotNote(robot,sleepOverviewNote(day),mood=robotScoreMood(Engine.SLEEP_ID,score?.score))
                    V2Card(gap=12.dp) {
                        FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            RocoText("Deine Schlafphasen",style=MaterialTheme.typography.titleMedium)
                            day?.sleepStart?.let {RocoText("${timeLabel(it)}–${day.sleepEnd?.let(::timeLabel) ?: "–"}",fontSize=11.sp,lineHeight=21.sp,color=V2Muted)}
                        }
                        V2SleepPhases(day)
                    }
                    V2Section("Was deinen Score prägt") {V2Card(gap=0.dp) {
                        V2Factor("Schlafdauer",durationLabel(day?.metrics?.get("sleep_minutes")),"Dein Schlafziel: ${durationLabel(day?.metrics?.get("sleep_goal"))}. Dieser Baustein zählt 30 % vor Umverteilung fehlender Bausteine. Bei sehr kurzer Nacht begrenzt zusätzlich die Schlafmenge den Gesamtscore; gute Einzelwerte gleichen fehlenden Schlaf nicht aus.",score?.components?.get("D"),baseline="Dein Ziel: ${durationLabel(day?.metrics?.get("sleep_goal"))}",tint=V2Sleep)
                        V2Factor("Effizienz & Unterbrechungen",day?.metrics?.get("efficiency")?.let {"${it.roundToInt()} % geschlafen"} ?: "Keine vollständige Nacht","20 % Gewicht. Vergleicht tatsächliche Schlafzeit mit der erfassten Nacht und berücksichtigt von Google gemeldete kurze Unterbrechungen. Das ist kein WHOOP-Sleep-Stress-Wert.",score?.components?.get("E"))
                        V2Factor("Regelmäßigkeit",score?.components?.get("R")?.let {"$it /100"} ?: "Lernphase","Vergleicht die Mitte deiner Nacht mit früheren Nächten. Mindestens fünf frühere Nächte sind nötig. Gewicht: 10 %.",score?.components?.get("R"))
                        V2Factor("Schlafphasen",score?.components?.get("Q")?.let {"$it /100"} ?: "Keine vollständige Verteilung","10 % Gewicht. Tief- und REM-Anteil werden möglichst mit deiner eigenen Quelle verglichen. In der Lernphase gelten breite Modellkorridore von 10–25 % Tief- und 15–30 % REM-Schlaf. Das sind bewusst grobe Modellregeln, keine klinischen Normwerte.",score?.components?.get("Q"))
                        V2Factor("Nächtliche HRV",day?.metrics?.get("hrv")?.let {"${it.roundToInt()} ms"} ?: "–",vitalExplanation(day,"hrv")+" Gewicht bei vollständigen Daten: 20 %.",score?.components?.get("H"),baseline=vitalBaselineLabel(day,"hrv"))
                        V2Factor("Ruhepuls",day?.metrics?.get("rhr")?.let {"${it.roundToInt()} bpm"} ?: "–",vitalExplanation(day,"rhr")+" Gewicht bei vollständigen Daten: 10 %.",score?.components?.get("P"),baseline=vitalBaselineLabel(day,"rhr"),tint=V2Load)
                        RocoText("Fehlende Bausteine werden nicht erfunden. Verfügbare Gewichte werden neu verteilt und das Ergebnis als vorläufig markiert.",style=MaterialTheme.typography.bodySmall,color=V2Muted)
                    }}
                }
            }
        }
    }
    if(score?.coverage?.missingReasons?.isNotEmpty()==true) V2Card("So sicher ist die Einordnung") {score.coverage.missingReasons.distinct().forEach {RocoText(scoreReasonLabel(it),style=MaterialTheme.typography.bodyMedium,color=V2Muted)}}
    ClayButton(onClick=discuss) {V2Icon("coach",tint=Color.White);RocoText("Mit $robot darüber sprechen")}
    TextButton(onClick=source) {RocoText("Datengrundlage ansehen");V2Icon("info",tint=V2Green)}
}

@Composable private fun V2SleepDuration(day: DayInsight?) {
    Column(verticalArrangement=Arrangement.spacedBy(7.dp)) {
        RocoText("Geschlafen",fontSize=11.sp,lineHeight=17.sp,color=Color(0xFF88977A))
        val minutes=day?.metrics?.get("sleep_minutes")?.takeIf {it.isFinite() && it>=0}?.roundToInt()
        RocoText(buildAnnotatedString {
            if(minutes==null) append("–") else {
                append((minutes/60).toString())
                withStyle(SpanStyle(fontSize=13.sp,fontWeight=FontWeight.Normal,letterSpacing=0.sp,color=Color(0xFF88977A))) {append(" h ")}
                append((minutes%60).toString())
                withStyle(SpanStyle(fontSize=13.sp,fontWeight=FontWeight.Normal,letterSpacing=0.sp,color=Color(0xFF88977A))) {append(" min")}
            }
        },fontSize=35.sp,lineHeight=39.sp,letterSpacing=(-1.3).sp,fontWeight=FontWeight(750))
        RocoText("Dein Ziel: ${durationLabel(day?.metrics?.get("sleep_goal"))}",fontSize=11.sp,lineHeight=17.sp,color=Color(0xFF626F55))
    }
}
internal fun vitalBaselineLabel(day: DayInsight?,key: String): String = day?.metrics?.get(key+"_baseline")?.let {
    "Deine Basis: ${String.format(Locale.getDefault(),"%.1f",it)} ${if(key=="hrv") "ms" else "bpm"}"
} ?: "Persönliche Basis lernt noch"

@Composable private fun V2DetailNumber(value: Int?,unit: String,label: String,tint: Color) {
    Column(Modifier.fillMaxWidth().v2TopOverlap(16.dp).padding(top=5.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement=Arrangement.Center,verticalArrangement=Arrangement.spacedBy(4.dp)) {
            RocoText(value?.toString() ?: "–",Modifier.alignByBaseline().testTag("detail-score"),fontSize=if(LocalDensity.current.fontScale>1.3f) 64.sp else 79.sp,lineHeight=if(LocalDensity.current.fontScale>1.3f) 68.sp else 83.sp,fontWeight=FontWeight.Bold,letterSpacing=(-4.5).sp)
            RocoText(unit,Modifier.alignByBaseline().padding(start=9.dp),fontSize=17.sp,lineHeight=24.sp,fontWeight=FontWeight(450),color=Color(0xFF949C8B))
        }
        Surface(color=if(tint==V2Load) Color(0xFFF3E9DC) else Color(0xFFE3EFDB),shape=RoundedCornerShape(20.dp)) {RocoText(label,Modifier.padding(horizontal=12.dp,vertical=5.dp),fontSize=11.sp,lineHeight=16.sp,fontWeight=FontWeight(650),color=if(tint==V2Load) Color(0xFF9E713E) else Color(0xFF5C7A4D))}
    }
}

@Composable private fun V2Factor(title: String,value: String,explanation: String,component: Int?,baseline: String?=null,tint: Color=V2Green) {
    var expanded by rememberSaveable(title) {mutableStateOf(false)}
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable(onClickLabel=RocoStrings.translate("$title erklären")) {expanded=!expanded}.padding(vertical=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                RocoText(title,fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight(750))
                RocoText(baseline ?: component?.let {"Modellbeitrag: $it /100"} ?: "Datenbasis noch unvollständig",fontSize=11.sp,lineHeight=17.sp,color=Color(0xFF657456))
            }
            RocoText(value,Modifier.widthIn(max=145.dp),fontSize=18.sp,lineHeight=25.sp,letterSpacing=(-.4).sp,fontWeight=FontWeight.Bold,color=tint)
            V2Icon(if(expanded) "close" else "add",tint=Color(0xFF9AA78B),size=19.dp)
        }
        if(expanded) {
            component?.let {LinearProgressIndicator(progress={it.coerceIn(0,100)/100f},modifier=Modifier.fillMaxWidth(),color=tint)}
            RocoText(explanation,Modifier.padding(bottom=16.dp,top=10.dp),fontSize=13.sp,lineHeight=23.sp,color=Color(0xFF56634C))
        }
        HorizontalDivider(color=Color(0xFFECEFE5))
    }
}

internal fun vitalExplanation(day: DayInsight?, key: String): String {
    val base=day?.metrics?.get(key+"_baseline")
    return if(base==null) "Für einen persönlichen Vergleich brauchen wir mindestens sieben gültige frühere Tage derselben Quelle. " else
        "Deine Basis: ${String.format(Locale.getDefault(),"%.1f",base)} ${if(key=="hrv") "ms" else "bpm"}, aus ${day.metrics[key+"_baseline_days"]?.roundToInt()} früheren Tagen. ${vitalComparison(day,key)}. Einzelne Unterschiede erklären noch keine Ursache. Im persönlichen Vergleich bedeutet 50 Punkte: im Bereich deiner Basis; 100 ist kein Standardwert. "
}

@Composable internal fun V2Trends(days: List<DayInsight>,robot: String,connector: String,today: LocalDate=LocalDate.now(),showSource: ()->Unit={},discuss: (String)->Unit={},open: (String,LocalDate)->Unit) {
    var metric by rememberSaveable {mutableStateOf("HRV")};var period by rememberSaveable {mutableIntStateOf(7)};var showDays by rememberSaveable {mutableStateOf(false)}
    val id=when(metric) {"Energie"->Engine.BODY_BATTERY_ID;"Belastung"->Engine.SESSION_LOAD_ID;else->Engine.SLEEP_ID}
    val key=if(metric=="HRV") "hrv" else if(metric=="Ruhepuls") "rhr" else null
    val unit=when(metric) {"HRV"->"ms";"Ruhepuls"->"bpm";else->"Pkt."}
    val history=comparableTrendDays(days,connector,today,key=key,scoreId=id.takeIf {key==null})
    val byDate=history.associateBy {it.date}
    val points=(period-1 downTo 0).map {offset->val date=today.minusDays(offset.toLong());val d=byDate[date];V2PlotPoint(date.atStartOfDay(ZoneId.systemDefault()).toInstant(),if(key!=null) d?.metrics?.get(key) else d?.scores?.get(id)?.score?.toDouble())}
    val latest=history.lastOrNull()
    val reference=key?.let {k->latest?.let {vitalReference(history,vitalDefinitions.first {it.key==k},it)}}
    val baseline=reference?.let {it.low to it.high}
    V2PageHeader("Werte verstehen.","Vergleiche dich mit deiner eigenen Basis.",eyebrow="DEINE PERSÖNLICHEN MUSTER")
    V2MetricSegments(listOf("HRV","Ruhepuls","Energie","Belastung","Schlaf"),metric) {metric=it}
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {RocoText(if(metric=="HRV") "Herzratenvariabilität" else metric,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);V2PeriodSegments(period) {period=it}}
    V2Card {
        if(period==90) RocoText(if(metric=="Belastung") "Mittelwerte aus bis zu drei Tagen · Kalenderwochen bleiben getrennt." else "Dreitägige Mittelwerte · nur vorhandene Werte; unvollständige Gruppen bleiben erkennbar.",fontSize=11.sp,color=V2Muted)
        V2LineChart(aggregateTrendPoints(points,period,weeklyReset=metric=="Belastung"),unit,if(metric=="Belastung") V2Load else if(metric=="Schlaf"||metric=="HRV") V2Sleep else V2Green,
            low=if(metric=="HRV") 30.0 else if(metric=="Ruhepuls") 40.0 else 0.0,high=if(metric=="HRV") 70.0 else if(metric=="Ruhepuls") 80.0 else 100.0,baseline=baseline)
        RocoText(if(baseline!=null) "Persönlicher Bereich · ${reference.days} frühere gültige Tage" else if(metric=="Belastung") "Wochenstand · 100 = Ziel · neuer Beginn am Montag" else "Nur vorhandene Datenpunkte · Lücken bleiben offen",fontSize=11.sp,lineHeight=17.sp,color=Color(0xFF5E6D51))
    }
    Surface(color=Color(0xFFF0F3E8),shape=RoundedCornerShape(25.dp),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            V2Eyebrow("DAS FÄLLT AUF")
            val value=latest?.let {if(key!=null) finiteVital(it,key) else it.scores[id]?.score?.toDouble()}
            val inRange=value!=null&&reference!=null&&value in reference.low..reference.high
            RocoText(if(value==null) "Dein Verlauf beginnt hier." else if(reference!=null) {if(inRange) "Im persönlichen Bereich." else "Außerhalb deiner üblichen Spanne."} else "Deine Werte im Zusammenhang.",fontSize=20.sp,lineHeight=27.sp,fontWeight=FontWeight(750))
            val text=if(value==null) "Für diese Ansicht liegt noch kein vergleichbarer Wert vor. Es werden keine Punkte ergänzt." else if(reference!=null) {
                val delta=value-reference.median
                val percent=reference.median.takeIf {it!=0.0}?.let {100*delta/it}
                "${String.format(Locale.getDefault(),"%.1f",value)} $unit gegenüber ${String.format(Locale.getDefault(),"%.1f",reference.median)} $unit als Basis"+(percent?.let {" · ${String.format(Locale.getDefault(),"%+.0f",it)} %"} ?: "")+". Der Unterschied allein erklärt noch keine Ursache."
            } else if(key!=null) "Deine Basis lernt noch. Für einen persönlichen Bereich fehlen ausreichend frühere Tage derselben Messquelle." else if(metric=="Belastung") "100 Punkte entsprechen deinem persönlichen Wochenziel. Vergleiche Kalenderwochen; der Montag beginnt bei null neu." else "Vergleiche die einzelnen Tagesstände. Unterschiedliche Uhrzeiten und Datenlücken können den Vergleich beeinflussen."
            RocoText(text,fontSize=13.sp,lineHeight=24.sp,color=Color(0xFF536348))
            RocoText("$connector${latest?.let {" · Stand "+it.date.format(DateTimeFormatter.ofPattern("d. MMM",Locale.getDefault()))}.orEmpty()}",fontSize=11.sp,lineHeight=17.sp,color=Color(0xFF5E6D51))
            TextButton(onClick=showSource,contentPadding=PaddingValues(0.dp)) {RocoText("Datengrundlage ansehen",fontSize=12.sp);V2Icon("forward",tint=V2Green,size=18.dp)}
        }
    }
    V2RobotNote(robot,"Ich erkläre dir, was sich verändert hat – und wie sicher wir das aus den vorhandenen Daten ableiten können.")
    ClayButton(onClick={discuss("Hilf mir, meinen $metric-Verlauf zu verstehen. Wenn dir passende historische Daten fehlen, sage das deutlich.")},secondary=true) {RocoText("Diesen Verlauf mit $robot besprechen")}
    TextButton(onClick={showDays=!showDays}) {RocoText(if(showDays) "Einzelne Tage schließen" else "Einzelne Tage öffnen")}
    if(showDays) {
        val visible=history.filter {it.date>=today.minusDays(period-1L)}.asReversed()
        if(visible.isEmpty()) RocoText("Noch keine vergleichbaren Tage in diesem Zeitraum.",color=V2Muted)
        visible.forEach {day->TextButton(onClick={open(if(key!=null) "vital:$key" else id,day.date)},modifier=Modifier.fillMaxWidth()) {RocoText(day.date.format(DateTimeFormatter.ofPattern("EEE, d. MMM",Locale.getDefault())),Modifier.weight(1f));RocoText((if(key!=null) finiteVital(day,key) else day.scores[id]?.score?.toDouble())?.let {String.format(Locale.getDefault(),if(key=="hrv") "%.1f %s" else "%.0f %s",it,unit)} ?: "–");V2Icon("next")}}
    }
}
