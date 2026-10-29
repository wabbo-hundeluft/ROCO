package com.wabbo.healthconnectcoach

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

internal data class VitalDefinition(val key: String,val title: String,val unit: String,val decimals: Int,
    val low: Double,val high: Double,val explanation: String,val dailyTotal: Boolean=false)
internal val vitalDefinitions=listOf(
    VitalDefinition("hrv","Herzfrequenzvariabilität","ms",1,0.0,100.0,"Die HRV beschreibt Schwankungen zwischen Herzschlägen. Aussagekräftig ist vor allem der Vergleich mit deiner eigenen Basis."),
    VitalDefinition("rhr","Ruhepuls","bpm",0,40.0,90.0,"Dein Ruhepuls wird mit früheren Tagen derselben Quelle verglichen. Eine Abweichung allein erklärt noch keine Ursache."),
    VitalDefinition("spo2","Sauerstoffsättigung","%",1,90.0,100.0,"Die Quelle schätzt die Sauerstoffsättigung. Hier siehst du ihren Tageswert und deinen Verlauf, keine zusätzliche Diagnose."),
    VitalDefinition("respiratory_rate","Atemfrequenz","/min",1,10.0,25.0,"Atemzüge pro Minute als Tageswert deiner Quelle. Dein eigener Verlauf hilft, Unterschiede einzuordnen."),
    VitalDefinition("skin_delta","Hauttemperatur-Abweichung","°C",1,-.5,.5,"Abweichung von der Temperaturbasis deiner Quelle. Das ist keine absolute Körpertemperatur; die Basis kann sich im Verlauf verändern."),
    VitalDefinition("vo2max","Ausdauer-Schätzung","ml/kg/min",1,20.0,60.0,"VO₂max ist eine Ausdauer-Schätzung deiner Quelle. Sie wird nicht zwingend jeden Tag neu bestimmt; beachte das Datum."),
    VitalDefinition("steps","Schritte","Schritte",0,0.0,10_000.0,"Erfasste Schritte des Tages. Heute ist ein Zwischenstand; Training und Schritte bleiben eigene Ansichten.",true),
    VitalDefinition("distance_km","Distanz","km",2,0.0,10.0,"Erfasste Distanz des Tages. Heute ist ein Zwischenstand und wird nicht als vollständiger Tag bewertet.",true),
    VitalDefinition("active_kcal","Aktive Energie","kcal",0,0.0,1000.0,"Geschätzte aktive Energie deiner Quelle. Der heutige Tagesstand kann noch wachsen.",true),
    VitalDefinition("floors","Etagen","Etagen",0,0.0,20.0,"Erfasste gestiegene Etagen des Tages. Fehlende Daten werden nicht durch null ersetzt.",true),
)
internal fun metricOrigin(day: DayInsight,key: String): String?=day.sources[if(key in setOf("hrv","rhr","steps")) key else "_metric_$key"]
internal fun finiteVital(day: DayInsight,key: String)=day.metrics[key]?.takeIf {it.isFinite()}
internal fun formatVitalNumber(value: Double,definition: VitalDefinition): String =
    String.format(Locale.getDefault(),if(definition.key=="skin_delta") "%+.${definition.decimals}f" else "%,.${definition.decimals}f",if(value==0.0) 0.0 else value)
internal data class VitalReference(val median: Double,val low: Double,val high: Double,val days: Int)
internal fun sameVitalDays(days: List<DayInsight>,definition: VitalDefinition,current: DayInsight): List<DayInsight> {
    val origin=metricOrigin(current,definition.key) ?: return emptyList()
    val connector=current.sources["_connector"] ?: return emptyList()
    return days.filter {it.sources["_connector"]==connector&&metricOrigin(it,definition.key)==origin&&finiteVital(it,definition.key)!=null}
        .groupBy {it.date}.values.map {rows->rows.maxBy {it.calculatedAt}}.sortedBy {it.date}
}
internal fun vitalReference(days: List<DayInsight>,definition: VitalDefinition,current: DayInsight): VitalReference? {
    if(definition.dailyTotal) return null
    val values=sameVitalDays(days,definition,current).filter {it.date<current.date&&it.date>=current.date.minusDays(28)}.map {finiteVital(it,definition.key)!!}.sorted()
    if(values.size<7) return null
    fun percentile(p: Double): Double {val index=(values.size-1)*p;val lo=index.toInt();val hi=kotlin.math.ceil(index).toInt();return values[lo]+(values[hi]-values[lo])*(index-lo)}
    return VitalReference(percentile(.5),percentile(.1),percentile(.9),values.size)
}
/** Attach only measurements actually reread for the same connector, with their selected origin. */
internal fun enrichVitalDays(days: List<DayInsight>,connector: String,metrics: Map<LocalDate,Map<String,Double>>,origins: Map<String,String>): List<DayInsight> = days.map {day ->
    val values=metrics[day.date].orEmpty().filterValues {it.isFinite()}
    if(day.sources["_connector"]!=connector||values.isEmpty()) day else day.copy(metrics=day.metrics+values,
        sources=day.sources+values.keys.mapNotNull {key->origins[key]?.let {"_metric_$key" to it}}.toMap())
}

@Composable internal fun V2VitalsOverview(days: List<DayInsight>,connector: String,today: LocalDate,open: (String)->Unit,back: ()->Unit,through: LocalDate=today) {
    V2PageHeader("Deine Messwerte","Jeder Wert mit Datum und eigener Quelle.",back)
    vitalDefinitions.forEach {definition ->
        val latest=days.filter {it.date<=through&&it.sources["_connector"]==connector&&finiteVital(it,definition.key)!=null}.maxByOrNull {it.date}
        V2Card {
            Column(Modifier.fillMaxWidth().clickable(onClickLabel=RocoStrings.translate("${definition.title} und Verlauf öffnen")) {open(definition.key)},verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {RocoText(definition.title,style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f));V2Icon("next")}
                RocoText(latest?.let {formatVitalNumber(finiteVital(it,definition.key)!!,definition)} ?: "–",style=MaterialTheme.typography.headlineMedium)
                RocoText(definition.unit,color=V2Muted)
                RocoText(latest?.let {"Tageswert vom "+it.date.format(DateTimeFormatter.ofPattern("d. MMM",Locale.getDefault()))+if(it.date==today) " · heute" else if(it.date==through) " · ausgewählter Tag" else " · letzter vorhandener Stand"} ?: "Noch kein Wert aus $connector",style=MaterialTheme.typography.bodySmall,color=V2Muted)
            }
        }
    }
}

@Composable internal fun V2VitalDetails(key: String,days: List<DayInsight>,connector: String,today: LocalDate,robot: String,back: ()->Unit,discuss: (String)->Unit, at: LocalDate=today) {
    val definition=vitalDefinitions.first {it.key==key}
    var period by rememberSaveable(key) {mutableIntStateOf(7)}
    val latest=days.filter {it.date<=at&&it.sources["_connector"]==connector&&finiteVital(it,key)!=null}.maxByOrNull {it.date}
    val matching=latest?.let {sameVitalDays(days,definition,it)}.orEmpty().associateBy {it.date}
    val points=(period-1 downTo 0).map {offset->val date=at.minusDays(offset.toLong());V2PlotPoint(date.atStartOfDay(ZoneId.systemDefault()).toInstant(),matching[date]?.let {finiteVital(it,key)})}
    val reference=latest?.let {vitalReference(days,definition,it)}
    V2PageHeader(definition.title,latest?.let {"Tageswert vom "+it.date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM",Locale.getDefault()))} ?: "Noch keine Messung",back)
    RocoText(latest?.let {formatVitalNumber(finiteVital(it,key)!!,definition)} ?: "–",style=MaterialTheme.typography.displayLarge)
    RocoText(definition.unit,color=V2Muted)
    V2RobotNote(robot,definition.explanation)
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {listOf(7,30,90).forEach {n->FilterChip(selected=period==n,onClick={period=n},label={RocoText("$n Tage")})}}
    V2Card("Dein Verlauf", "$connector · gleiche Messquelle · fehlende Tage bleiben Lücken") {
        if(period==90) RocoText("Dreitägige Mittelwerte · gültige Tage je Gruppe werden bei der Auswahl angezeigt.",style=MaterialTheme.typography.bodySmall,color=V2Muted)
        V2LineChart(aggregateTrendPoints(points,period),definition.unit,V2Green,definition.low,definition.high,reference?.let {it.low to it.high},decimals=definition.decimals)
        if(definition.dailyTotal) RocoText("Heute ist ein Zwischenstand. Ein niedrigerer Stand als an abgeschlossenen Tagen ist keine Bewertung deines Tages.",style=MaterialTheme.typography.bodySmall,color=V2Muted)
    }
    if(reference!=null&&latest!=null) V2Card("Verglichen mit deiner Basis","${reference.days} frühere Tage derselben Quelle") {
        val value=finiteVital(latest,key)!!;val delta=value-reference.median
        RocoText("Basis ${formatVitalNumber(reference.median,definition)} ${definition.unit}",fontWeight=FontWeight.Bold)
        RocoText("${if(delta>=0) "+" else "−"}${formatVitalNumber(abs(delta),definition.copy(key="delta"))} ${definition.unit} zur Basis")
        RocoText("Persönlicher Bereich: ${formatVitalNumber(reference.low,definition)} bis ${formatVitalNumber(reference.high,definition)} ${definition.unit}",style=MaterialTheme.typography.bodySmall,color=V2Muted)
        RocoText("Schattiert sind die mittleren 80 % deiner früheren Tageswerte. Dieser Vergleich beschreibt deinen Verlauf; höher ist nicht automatisch besser.",style=MaterialTheme.typography.bodySmall,color=V2Muted)
    } else if(!definition.dailyTotal) V2Card("Deine Basis lernt noch") {RocoText("Für einen persönlichen Bereich brauchen wir mindestens sieben frühere Tage mit eindeutig derselben Quelle. Unbekannte alte Quellen werden nicht vermischt.",color=V2Muted)}
    RocoText("Quelle: $connector${latest?.let {metricOrigin(it,key)}?.let {" · $it"}.orEmpty()}",style=MaterialTheme.typography.bodySmall,color=V2Muted)
    ClayButton(onClick={discuss("Erkläre mir Bedeutung und Grenzen von ${definition.title}. Trenne Messwerte, persönliche Vergleiche und Annahmen. Wenn dir passende persönliche Daten fehlen, sage das klar.")}) {V2Icon("coach");RocoText("Mit $robot verstehen")}
}
