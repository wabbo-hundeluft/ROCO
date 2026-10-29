package com.wabbo.healthconnectcoach

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

private fun journalGroups(i: JournalInsight)=i.threshold?.let {("Mehr als ${String.format(Locale.getDefault(),"%.1f",it)} ${i.habit.unit}") to ("Bis ${String.format(Locale.getDefault(),"%.1f",it)} ${i.habit.unit}")} ?: ("Ja" to "Nein")
private fun journalQuality(i: JournalInsight)=when(i.quality) {"learning"->"Noch in der Lernphase";"overlapping"->"Noch nicht getrennt erklärbar";"early"->"Erste Tendenz · wenig Daten";"consistent"->"Wiederkehrender Zusammenhang";else->"Kein klares Muster"}
private fun effectLabel(v: Double?,unit: String)=v?.let {String.format(Locale.getDefault(),"%+.1f %s",it,unit)} ?: "Noch offen"
private fun valueUnit(i: JournalInsight)=if(i.outcome==JournalOutcome.HRV) "ms" else i.outcome.unit

@Composable internal fun JournalInsightsContent(insights: List<JournalInsight>,loading: Boolean,robot: String,discuss: (String)->Unit) {
    var outcomeText by rememberSaveable {mutableStateOf(JournalOutcome.MORNING_ENERGY.name)}
    var detailId by rememberSaveable {mutableStateOf<String?>(null)}
    val outcome=JournalOutcome.valueOf(outcomeText)
    RocoText("Was tut dir gut?",fontSize=28.sp,lineHeight=35.sp,fontWeight=FontWeight.ExtraBold)
    RocoText("Deine letzten 90 Tage. Lokal berechnete Zusammenhänge, keine Beweise für Ursachen.",color=V2Muted)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {JournalOutcome.entries.forEach {o->FilterChip(selected=o==outcome,onClick={outcomeText=o.name},label={RocoText(o.title,fontSize=11.sp)})}}
    if(outcome==JournalOutcome.MORNING_ENERGY) RocoText("Grundlage ist die modellierte Startenergie aus deinen Schlaf- und Erholungswerten, keine direkte Energiemessung.",fontSize=12.sp,color=V2Muted)
    if(loading) {LinearProgressIndicator(Modifier.fillMaxWidth(),color=V2Green);RocoText("Deine Muster werden lokal berechnet …",color=V2Muted)}
    insights.filter {it.outcome==outcome}.sortedWith(compareBy<JournalInsight> {if(it.quality=="consistent") 0 else if(it.effect!=null) 1 else 2}.thenByDescending {abs(it.effect ?: 0.0)}).forEach {i->
        V2ClaySurface(onClick={detailId=i.habit.id},modifier=Modifier.fillMaxWidth()) {Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {if(i.habit.custom) RocoVerbatimText(i.habit.title,Modifier.weight(1f),fontSize=18.sp,fontWeight=FontWeight.ExtraBold) else RocoText(i.habit.title,Modifier.weight(1f),fontSize=18.sp,fontWeight=FontWeight.ExtraBold);V2Icon("next")}
            RocoText(journalQuality(i),fontSize=12.sp,color=V2Muted)
            if(i.effect!=null) RocoText(effectLabel(i.effect,i.outcome.unit),fontSize=34.sp,fontWeight=FontWeight.ExtraBold,color=if(i.quality=="consistent") {if(i.helpful==true) V2Green else if(i.helpful==false) V2Load else V2Ink} else V2Muted)
            else Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {RocoText("${i.yesCount.coerceAtMost(5)} / 5",fontSize=25.sp,fontWeight=FontWeight.Bold);RocoText(if(i.threshold!=null) "höhere Mengen" else "Ja-Antworten",fontSize=11.sp,color=V2Muted)}
                Column(Modifier.weight(1f)) {RocoText("${i.noCount.coerceAtMost(5)} / 5",fontSize=25.sp,fontWeight=FontWeight.Bold);RocoText(if(i.threshold!=null) "niedrigere Mengen" else "Nein-Antworten",fontSize=11.sp,color=V2Muted)}
            }
            RocoText("${i.yesCount+i.noCount} passende Folgetage · ${i.outcome.title}",fontSize=12.sp,color=V2Muted)
        }}
    }
    RocoText("Ein Verhaltenstag wird mit der folgenden Nacht und dem nächsten Morgen verglichen. Fehlende oder übersprungene Antworten zählen nicht als Nein. Anbieter und Messherkünfte werden getrennt ausgewertet.",fontSize=12.sp,lineHeight=19.sp,color=V2Muted)
    insights.firstOrNull {it.habit.id==detailId&&it.outcome==outcome}?.let {i->JournalInsightSheet(i,robot,dismiss={detailId=null},discuss={detailId=null;discuss(it)})}
}

@Composable private fun JournalInsightSheet(i: JournalInsight,robot: String,dismiss: ()->Unit,discuss: (String)->Unit) {
    val groups=journalGroups(i)
    V2DetailSheet(dismiss,title={if(i.habit.custom) RocoVerbatimText(i.habit.title) else RocoText(i.habit.title)},startExpanded=true,text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        RocoText(i.outcome.title,style=MaterialTheme.typography.titleMedium)
        RocoText(journalQuality(i),color=V2Muted)
        RocoText(effectLabel(i.effect,i.outcome.unit),fontSize=39.sp,fontWeight=FontWeight.ExtraBold)
        RocoText(if(i.threshold==null) "Geschätzter Unterschied nach Ja-Tagen gegenüber Nein-Tagen." else "Geschätzter Unterschied nach Tagen mit höheren gegenüber niedrigeren Mengen. Die Grenze ist dein persönlicher Median.",color=V2Muted)
        if(i.effect!=null) JournalEffectRange(i)
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {listOf(Triple(groups.first,i.yesMean,i.yesCount),Triple(groups.second,i.noMean,i.noCount)).forEach {(label,value,count)->
            Surface(color=Color(0xFFF0F4E8),shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp),modifier=Modifier.weight(1f)) {Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                RocoText(label,fontSize=12.sp,color=V2Muted);RocoText(value?.let {String.format(Locale.getDefault(),"%.1f",it)} ?: "–",fontSize=26.sp,fontWeight=FontWeight.ExtraBold);RocoText("${valueUnit(i)} · $count Tage",fontSize=12.sp,color=V2Muted)
            }}
        }}
        if(i.overlapping.isNotEmpty()) V2Card("Diese Dinge treten oft gemeinsam auf") {RocoText(i.overlapping.joinToString()+". Die App kann die einzelnen Beiträge noch nicht verlässlich trennen.",color=V2Muted)}
        JournalComparisonPlot(i)
        var method by remember {mutableStateOf(false)}
        TextButton(onClick={method=!method}) {RocoText(if(method) "Rechenweg schließen" else "Wie entsteht diese Einordnung?")}
        if(method) {
            RocoText("Eigene lokale Analyse: Mindestens fünf passende Tage je Vergleichsgruppe. Tage ohne passenden Folgemesswert werden ausgelassen. Messquellen und Score-Versionen werden getrennt gehalten. Mengen werden am persönlichen Median geteilt.",fontSize=12.sp,color=V2Muted)
            RocoText("Die Schätzung berücksichtigt, soweit ausreichend erfasst: ${i.controls.joinToString().ifBlank {"noch keine zusätzlichen Vergleichsfaktoren"}}. Fehlende Kontrollwerte werden nur bei mindestens 80 % Abdeckung über den Median ergänzt. Extremwerte werden für die Schätzung begrenzt.",fontSize=12.sp,color=V2Muted)
            RocoText("Die Spanne kommt aus 96 Wiederholungen mit ganzen Wochenblöcken. Sie zeigt Streuung, keine Wahrscheinlichkeit für eine Ursache. Erst ab 28 passenden Tagen und vier Wochen sowie derselben Richtung in beiden Zeitabschnitten kann ein wiederkehrendes Muster markiert werden. Sehr kleine Unterschiede werden nicht hervorgehoben. Unbeobachtete Einflüsse und Zufall bleiben möglich.",fontSize=12.sp,color=V2Muted)
            RocoText("Datenstrom: ${i.source.ifBlank {"Noch keine passende Biometrie"}} · $JOURNAL_ANALYSIS_VERSION",fontSize=10.sp,color=V2Muted)
        }
    }},confirmButton={ClayButton(onClick={discuss("Besprich bitte mein Logbuch-Muster zu „${i.habit.title}“ und ${i.outcome.title}. Nutze die lokalen Datenwerkzeuge, erläutere Unsicherheit und schlage ein kleines sinnvolles Alltagsexperiment vor, ohne einen Ursachebeweis zu behaupten.")}) {RocoText("Mit $robot besprechen")}})
}
@Composable private fun JournalEffectRange(i: JournalInsight) {
    val limit=max(1.0,listOfNotNull(i.effect,i.low,i.high).maxOf {abs(it)}*1.1)
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(56.dp).semantics {contentDescription=RocoStrings.translate("Unterschied ${effectLabel(i.effect,i.outcome.unit)}. Streuung von ${effectLabel(i.low,i.outcome.unit)} bis ${effectLabel(i.high,i.outcome.unit)}.")}) {
            fun x(v: Double)=((v+limit)/(2*limit)*size.width).toFloat()
            drawLine(Color(0xFFCDD6C4),Offset(0f,size.height/2),Offset(size.width,size.height/2),2.dp.toPx())
            drawLine(V2Muted,Offset(x(0.0),6.dp.toPx()),Offset(x(0.0),size.height-6.dp.toPx()),1.dp.toPx())
            if(i.low!=null&&i.high!=null) drawLine(Color(0xFFACC996),Offset(x(i.low),size.height/2),Offset(x(i.high),size.height/2),9.dp.toPx())
            i.effect?.let {drawCircle(V2Green,6.dp.toPx(),Offset(x(it),size.height/2))}
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {RocoText("−${String.format(Locale.getDefault(),"%.0f",limit)}",fontSize=11.sp);RocoText("0",fontSize=11.sp);RocoText("+${String.format(Locale.getDefault(),"%.0f",limit)}",fontSize=11.sp)}
        RocoText(if(i.low==null) "Für eine Streuungsspanne fehlen noch ausreichend Wochen." else "Bootstrap-Spanne: ${effectLabel(i.low,i.outcome.unit)} bis ${effectLabel(i.high,i.outcome.unit)}",fontSize=12.sp,color=V2Muted)
    }
}
@Composable private fun JournalComparisonPlot(i: JournalInsight) {
    val points=i.comparisons;if(points.size<2) return
    var selected by remember(i) {mutableIntStateOf(points.lastIndex)}
    val first=points.first().date.toEpochDay();val span=(points.last().date.toEpochDay()-first).coerceAtLeast(1)
    val lo=points.minOf {it.value};val hi=points.maxOf {it.value};val pad=max((hi-lo)*.15,1.0);val bottom=lo-pad;val top=hi+pad
    val choice=points[selected.coerceIn(points.indices)]
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        RocoText("Die passenden Folgetage",fontWeight=FontWeight.Bold)
        RocoText(choice.date.format(DateTimeFormatter.ofPattern("d. MMM",Locale.getDefault()))+" · "+(if(choice.yes) journalGroups(i).first else journalGroups(i).second)+" · "+String.format(Locale.getDefault(),"%.1f %s",choice.value,valueUnit(i)),fontSize=12.sp,color=V2Muted)
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Column(Modifier.width(37.dp).height(145.dp),verticalArrangement=Arrangement.SpaceBetween) {listOf(top,(top+bottom)/2,bottom).forEach {RocoText(String.format(Locale.getDefault(),"%.0f",it),fontSize=10.sp,color=V2Muted)}}
            Canvas(Modifier.weight(1f).height(145.dp).pointerInput(points) {detectTapGestures {p->selected=points.indices.minBy {abs((points[it].date.toEpochDay()-first).toFloat()/span*size.width-p.x)}}}.semantics {contentDescription=RocoStrings.translate("Tagespunkte: Grün für ${journalGroups(i).first}, blau für ${journalGroups(i).second}. Tippe einen Tag für seinen Wert.")}) {
                fun x(k: Int)=((points[k].date.toEpochDay()-first).toFloat()/span*size.width).coerceIn(3.dp.toPx(),size.width-3.dp.toPx())
                fun y(v: Double)=((top-v)/(top-bottom)*size.height).toFloat().coerceIn(3.dp.toPx(),size.height-3.dp.toPx())
                (0..2).forEach {n->drawLine(Color(0xFFE0E6D9),Offset(0f,size.height*n/2),Offset(size.width,size.height*n/2),1.dp.toPx())}
                points.indices.forEach {k->drawCircle(if(points[k].yes) V2Green else V2Sleep,if(k==selected) 5.dp.toPx() else 3.dp.toPx(),Offset(x(k),y(points[k].value)))}
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {RocoText(points.first().date.format(DateTimeFormatter.ofPattern("d. MMM",Locale.getDefault())),fontSize=10.sp,color=V2Muted);RocoText(points.last().date.format(DateTimeFormatter.ofPattern("d. MMM",Locale.getDefault())),fontSize=10.sp,color=V2Muted)}
    }
}
