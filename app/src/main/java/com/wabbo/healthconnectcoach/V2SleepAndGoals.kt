package com.wabbo.healthconnectcoach

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import java.time.Duration
import kotlin.math.roundToInt

@Composable internal fun TrainingGoalEditor(initial: TrainingGoals = TrainingGoals(), saveLabel: String = "Diese Ziele bestätigen", saving: Boolean=false, save: (TrainingGoals) -> Unit) {

    var duration by rememberSaveable(initial) { mutableIntStateOf(initial.sessionMinutes) }
    var days by rememberSaveable(initial,stateSaver=listSaver<Set<Int>,Int>(save={it.sorted()},restore={it.toSet()})) {mutableStateOf(initial.days)}
    var intensity by rememberSaveable(initial) { mutableDoubleStateOf(initial.intensity) }
    var sleep by rememberSaveable(initial) { mutableIntStateOf(initial.sleepMinutes) }
    val adjustmentWeek=java.time.LocalDate.now().with(java.time.DayOfWeek.MONDAY).toString()
    var adjustment by rememberSaveable(initial,adjustmentWeek) {mutableIntStateOf(initial.weeklyLoadAdjustments[adjustmentWeek] ?: 100)}
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        RocoText("Dein Alltag bestimmt den Plan.",style=MaterialTheme.typography.headlineSmall)
        RocoText("Das sind Vorschläge. Wähle, was zu dir passt. Erst deine Bestätigung legt Ziele fest.",color=V2Muted)
        V2Card("Deine Trainingstage") {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                listOf("Mo","Di","Mi","Do","Fr","Sa","So").forEachIndexed { index,label ->
                    val value=index+1
                    FilterChip(selected=value in days,onClick={days=if(value in days) days-value else days+value},label={RocoText(label)},modifier=Modifier.widthIn(min=48.dp).heightIn(min=48.dp))
                }
            }
            RocoText("${days.size} Einheiten · jeweils $duration Minuten",style=MaterialTheme.typography.titleMedium)
            Slider(value=duration.toFloat(),onValueChange={duration=(it/5).roundToInt()*5},valueRange=5f..120f)
            listOf(1.0 to "Leicht · Bewegung & Mobilität",1.5 to "Moderat · spürbar aktiv",2.0 to "Intensiv · anspruchsvoll").forEach { (value,label) ->
                Row(Modifier.fillMaxWidth().clickable { intensity=value },verticalAlignment=Alignment.CenterVertically) { RadioButton(selected=intensity==value,onClick={intensity=value});RocoText(label) }
            }
            RocoText("100 Belastungspunkte entsprechen deinem persönlichen Wochenziel. Bis genug Trainingshistorie vorliegt, dient dein bestätigter Plan als Ausgangspunkt. Tatsächlicher Puls gewichtet die Belastung; ohne ausreichenden Puls wird die Dauer als Näherung verwendet.",style=MaterialTheme.typography.bodySmall,color=V2Muted)
        }
        V2Card("Wochenbelastung · Ziel 100", "Nur diese Kalenderwoche. Die automatische Basis passt sich weiter an deinen Trainingsverlauf an.") {
            RocoText("$adjustment % der automatischen Basis",style=MaterialTheme.typography.titleLarge)
            Slider(value=adjustment.toFloat(),onValueChange={adjustment=(it/5).roundToInt()*5},valueRange=75f..125f,steps=9)
            RocoText("Höheres Ziel: mehr Training für dieselben 100 Belastungspunkte. Es gibt kein zusätzliches Cardio-Minuten-Ziel.",style=MaterialTheme.typography.bodySmall,color=V2Muted)
        }
        V2Card("Dein Schlafziel") {
            RocoText(durationLabel(sleep.toDouble()),style=MaterialTheme.typography.headlineMedium)
            Slider(value=sleep.toFloat(),onValueChange={sleep=(it/15).roundToInt()*15},valueRange=360f..660f)
        }
        ClayButton(onClick={save(initial.copy(sessionMinutes=duration,days=days,intensity=intensity,sleepMinutes=sleep).withWeeklyAdjustment(java.time.LocalDate.now(),adjustment))},enabled=days.isNotEmpty()&&!saving) {RocoText(if(saving) "Wird gespeichert …" else saveLabel)}
    }
}

@Composable internal fun V2SleepPhases(day: DayInsight?) {
    val stages=listOf("AWAKE" to "Wach","REM" to "REM","LIGHT" to "Leicht","DEEP" to "Tief")
    val colors=listOf(Color(0xFFC69767),Color(0xFFC2D6EB),Color(0xFF8CAFD7),Color(0xFF345579))
    var selected by remember(day?.calculatedAt) { mutableStateOf<SleepBand?>(null) }
    val start=day?.sleepStart;val end=day?.sleepEnd
    if(start==null || end==null || end<=start || day.sleepBands.isEmpty()) { RocoText("Deine Uhr hat noch keine Schlafphasen für diese Nacht geliefert.",color=V2Muted);return }
    val bands=day.sleepBands.filter { it.start>=start && it.end<=end && it.end>it.start }
    val total=Duration.between(start,end).toMillis().toDouble()
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Column(Modifier.width(42.dp).height(132.dp),verticalArrangement=Arrangement.SpaceAround) { stages.forEach { RocoText(it.second,style=MaterialTheme.typography.labelSmall,color=V2Muted) } }
        Canvas(Modifier.weight(1f).height(132.dp).pointerInput(bands) { detectTapGestures { p ->
            val at=start.plusMillis((p.x/size.width*total).toLong());selected=bands.firstOrNull { at>=it.start && at<it.end }
        } }.semantics { contentDescription=RocoStrings.translate("Schlafphasen zwischen ${timeLabel(start)} und ${timeLabel(end)}. Die Summen stehen direkt darunter.") }) {
            val row=size.height/4
            stages.forEachIndexed { index,(key,_) ->
                drawLine(Color(0xFFE5E9DF),Offset(0f,row*(index+.5f)),Offset(size.width,row*(index+.5f)),1.dp.toPx())
                bands.filter { if(key=="AWAKE") it.stage in setOf("AWAKE","AWAKE_IN_BED","OUT_OF_BED") else it.stage==key }.forEach { b ->
                    val x=(Duration.between(start,b.start).toMillis()/total*size.width).toFloat(); val width=(Duration.between(b.start,b.end).toMillis()/total*size.width).toFloat().coerceAtLeast(2f)
                    drawRoundRect(colors[index],Offset(x,row*index+row*.23f),Size(width,row*.54f),CornerRadius(3.dp.toPx()))
                    if(selected==b) drawRoundRect(V2Ink,Offset(x,row*index+row*.23f),Size(width,row*.54f),CornerRadius(3.dp.toPx()),style=Stroke(2.dp.toPx()))
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { RocoText(timeLabel(start),style=MaterialTheme.typography.bodySmall);RocoText(timeLabel(start.plusMillis((total/2).toLong())),style=MaterialTheme.typography.bodySmall);RocoText(timeLabel(end),style=MaterialTheme.typography.bodySmall) }
    selected?.let { RocoText("${stages.firstOrNull { s -> s.first==it.stage }?.second ?: "Wach"} · ${timeLabel(it.start)}–${timeLabel(it.end)} · ${durationLabel(Duration.between(it.start,it.end).seconds/60.0)}",color=V2Sleep) }
    listOf("LIGHT","DEEP","REM","AWAKE").chunked(2).forEach {keys->
        Row(horizontalArrangement=Arrangement.spacedBy(16.dp),modifier=Modifier.padding(top=12.dp)) {keys.forEach {key->
            val index=stages.indexOfFirst {it.first==key};val label=stages[index].second
            val minutes=if(key=="AWAKE") listOf("AWAKE","AWAKE_IN_BED","OUT_OF_BED").mapNotNull {day.metrics["stage_$it"]}.takeIf {it.isNotEmpty()}?.sum() else day.metrics["stage_$key"]
            Row(Modifier.weight(1f).clickable(onClickLabel=RocoStrings.translate("$label auswählen")) {selected=bands.firstOrNull {if(key=="AWAKE") it.stage in setOf("AWAKE","AWAKE_IN_BED","OUT_OF_BED") else it.stage==key}},horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Box(Modifier.padding(top=5.dp).size(7.dp).background(colors[index],CircleShape))
                Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {RocoText(label,fontSize=11.sp,lineHeight=17.sp,color=Color(0xFF5C6B51));RocoText(durationLabel(minutes),fontSize=15.sp,lineHeight=21.sp,fontWeight=FontWeight(750),color=Color(0xFF59684A))}
            }
        }}
    }
}

@Composable internal fun SleepBenchmarks(day: DayInsight?, days: List<DayInsight>) {
    val uri=LocalUriHandler.current
    RocoText("Dein üblicher Bereich",style=MaterialTheme.typography.titleLarge)
    RocoText("Vergleich mit deinen vorherigen 28 Tagen derselben Datenquelle. Mindestens 7 vollständige Nächte sind nötig. Bereiche sind persönliche Orientierung, keine medizinischen Grenzwerte.",color=V2Muted)
    val connector=day?.sources?.get("_connector")
    val prior=days.filter { day!=null && it.date<day.date && it.date>=day.date.minusDays(28) && it.sources["_connector"]==connector && it.sources["sleep"]==day.sources["sleep"] && it.metrics["sleep_minutes"]!=null }
    listOf("DEEP" to "Tiefschlaf","REM" to "REM-Schlaf","LIGHT" to "Leichtschlaf").forEach { (key,label) ->
        val current=day?.metrics?.get("stage_$key")?.let { m -> day.metrics["sleep_minutes"]?.takeIf { it>0 }?.let { 100*m/it } }
        val values=prior.mapNotNull { d -> d.metrics["stage_$key"]?.let { stage -> d.metrics["sleep_minutes"]?.takeIf { it>0 }?.let { 100*stage/it } } }.sorted()
        V2Card(label) {
            RocoText(current?.let { "${it.roundToInt()} % · ${durationLabel(day?.metrics?.get("stage_$key"))}" } ?: "Noch keine Phasendaten",style=MaterialTheme.typography.titleLarge)
            if(values.size>=7 && current!=null) {
                val low=values[((values.size-1)*.1).toInt()];val high=values[((values.size-1)*.9).toInt()]
                Canvas(Modifier.fillMaxWidth().height(42.dp).semantics { contentDescription=RocoStrings.translate("$label heute ${current.roundToInt()} Prozent, persönlicher Bereich ${low.roundToInt()} bis ${high.roundToInt()} Prozent.") }) {
                    drawRoundRect(Color(0xFFEEF2E9),Offset(0f,size.height*.35f),Size(size.width,size.height*.3f),CornerRadius(8.dp.toPx()))
                    drawRoundRect(Color(0xFFCBDCCB),Offset((low/100*size.width).toFloat(),size.height*.2f),Size(((high-low).coerceAtLeast(2.0)/100*size.width).toFloat(),size.height*.6f),CornerRadius(5.dp.toPx()))
                    val x=(current/100*size.width).toFloat().coerceIn(3.dp.toPx(),size.width-3.dp.toPx())
                    drawLine(V2Sleep,Offset(x,0f),Offset(x,size.height),4.dp.toPx())
                }
                RocoText("Dein Bereich: ${low.roundToInt()}–${high.roundToInt()} % · ${values.size} Nächte",color=V2Muted)
                RocoText(if(current in low..high) "Heute innerhalb deines üblichen Bereichs." else "Heute außerhalb deines üblichen Bereichs. Einzelne Nächte dürfen abweichen.")
            } else RocoText("Lernphase: ${values.size} von 7 Nächten. Dein persönlicher Bereich erscheint mit mehr vollständigen Phasendaten.",color=V2Muted)
        }
    }
    RocoText("Google vergleicht außerdem mit ähnlichen Alters- und Geschlechtsgruppen. Diese proprietären Vergleichsdaten werden nicht als eigene Normwerte erfunden.",style=MaterialTheme.typography.bodySmall,color=V2Muted)
    TextButton(onClick={uri.openUri("https://support.google.com/googlehealth/answer/14236712")}) { RocoText("So erklärt Google Schlaf-Benchmarks") }
}
