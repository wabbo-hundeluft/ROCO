package com.wabbo.healthconnectcoach

import android.graphics.Paint
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

internal val V2Ink = Color(0xFF202820)
internal val V2Green = Color(0xFF22835D)
internal val V2Sleep = Color(0xFF557AA9)
internal val V2Load = Color(0xFFBB7342)
internal val V2Muted = Color(0xFF616B59)

@Composable internal fun V2Icon(name: String, modifier: Modifier = Modifier, tint: Color = V2Ink,size: Dp=24.dp) {
    if(name=="memory") {
        Canvas(modifier.size(size).clearAndSetSemantics {}) {
            val unit=this.size.width/24f;val stroke=Stroke(1.7f*unit)
            drawRoundRect(tint,Offset(4*unit,3*unit),Size(16*unit,18*unit),CornerRadius(3*unit),style=stroke)
            drawLine(tint,Offset(8*unit,3*unit),Offset(8*unit,21*unit),1.7f*unit,StrokeCap.Round)
            drawLine(tint,Offset(11*unit,9*unit),Offset(17*unit,9*unit),1.7f*unit,StrokeCap.Round)
            drawLine(tint,Offset(11*unit,13*unit),Offset(16*unit,13*unit),1.7f*unit,StrokeCap.Round)
        }
        return
    }
    val code = when(name) {
        "home" -> 0xe9b2; "training" -> 0xeb43; "trends" -> 0xf190; "coach" -> 0xe8af; "back" -> 0xe5c4
        "next" -> 0xe5cc; "close" -> 0xe5cd; "check" -> 0xe668; "edit" -> 0xf097; "undo" -> 0xe166; "send" -> 0xe163
        "sleep", "moon" -> 0xf159; "heart" -> 0xe87d; "walk" -> 0xe536; "refresh" -> 0xe5d5; "settings" -> 0xe8b8; "flame" -> 0xea0b; "info" -> 0xe88e
        "check_circle" -> 0xf0be; "battery" -> 0xe1a3; "forward" -> 0xe5c8; "up" -> 0xe5d8; "expand" -> 0xe5cf; "bolt" -> 0xea0b; "person" -> 0xf0d3; "cloud" -> 0xe2bf; "add" -> 0xe145; "date" -> 0xebcc; else -> 0xe88e
    }
    val iconSize=with(LocalDensity.current) {size.toSp()}
    RocoText(code.toChar().toString(), fontFamily = FontFamily(Font(R.font.material_symbols_rounded)), fontSize = iconSize, lineHeight = iconSize,
        color = tint, modifier = modifier.size(size).clearAndSetSemantics {})
}

@Composable internal fun V2Card(title: String? = null, subtitle: String? = null, contentPadding: PaddingValues=PaddingValues(22.dp),gap: Dp=16.dp, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(25.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE6E9DF)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(gap)) {
            if(title != null) RocoText(title, style = MaterialTheme.typography.titleMedium)
            if(subtitle != null) RocoText(subtitle, style = MaterialTheme.typography.bodySmall.copy(fontSize=11.sp,lineHeight=17.sp), color = Color(0xFF626F55))
            content()
        }
    }
}

@Composable internal fun V2Section(title: String, action: String? = null, onAction: () -> Unit = {}, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RocoText(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if(action != null) TextButton(onClick=onAction,modifier=Modifier.height(44.dp),contentPadding=PaddingValues(0.dp)) {RocoText(action,fontSize=12.sp,lineHeight=18.sp,fontWeight=FontWeight.Bold,color=Color(0xFF4A7256));V2Icon("next",tint=Color(0xFF4A7256),size=18.dp)}
        }
        content()
    }
}

@Composable internal fun V2Battery(value: Int?, modifier: Modifier = Modifier, dark: Boolean = false) {
    val fill = if(dark) Color(0xFFA0DCB3) else V2Green
    val scale=LocalDensity.current.fontScale.coerceAtLeast(1f)
    Box(modifier.size(112.dp*scale, 124.dp*scale).semantics { contentDescription = RocoStrings.translate("Körperenergie: ${value?.let { "$it von 100" } ?: "noch kein Wert"}") }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val body = Size(size.width * .82f, size.height * .86f); val origin = Offset(size.width * .09f, size.height * .11f)
            drawRoundRect(fill.copy(alpha = .5f), Offset(size.width * .36f, 0f), Size(size.width * .28f, size.height * .10f), CornerRadius(5.dp.toPx()))
            drawRoundRect(fill.copy(alpha = .13f), origin, body, CornerRadius(19.dp.toPx()))
            drawRoundRect(fill.copy(alpha = .75f), origin, body, CornerRadius(19.dp.toPx()), style = Stroke(3.dp.toPx()))
            if(value != null) {
                val inner = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(origin.x + 6.dp.toPx(), origin.y + 6.dp.toPx(), origin.x + body.width - 6.dp.toPx(), origin.y + body.height - 6.dp.toPx(), 12.dp.toPx(), 12.dp.toPx())) }
                drawContext.canvas.save(); drawContext.canvas.clipPath(inner)
                val level = (body.height - 12.dp.toPx()) * value.coerceIn(0,100) / 100f
                drawRect(fill.copy(alpha = .28f), Offset(origin.x + 6.dp.toPx(), origin.y + body.height - 6.dp.toPx() - level), Size(body.width - 12.dp.toPx(), level))
                drawContext.canvas.restore()
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 12.dp)) {
            RocoText(value?.toString() ?: "–", fontSize = 43.sp, fontWeight = FontWeight.Bold, color = if(dark) Color.White else V2Ink)
            RocoText("/100", style = MaterialTheme.typography.labelMedium, color = if(dark) Color(0xFFD7E3D2) else V2Muted)
        }
    }
}

@Composable internal fun V2SleepRing(value: Int?, modifier: Modifier = Modifier) {
    val scale=LocalDensity.current.fontScale.coerceAtLeast(1f)
    Box(modifier.size(116.dp*scale).semantics { contentDescription = RocoStrings.translate("Schlafscore ${value ?: "fehlt"} von 100") }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val origin=Offset(size.width*.06f,size.height*.06f);val arc=Size(size.width*.88f,size.height*.88f)
            val stroke=size.width*.055f
            drawArc(V2Sleep.copy(alpha=.13f),-90f,360f,false,topLeft=origin,size=arc,style=Stroke(stroke))
            if(value!=null) drawArc(V2Sleep,-90f,value.coerceIn(0,100)*3.6f,false,topLeft=origin,size=arc,style=Stroke(stroke,cap=StrokeCap.Round))
        }
        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {RocoText(value?.toString() ?: "–",fontSize=34.sp,lineHeight=39.sp,letterSpacing=(-1.5).sp,fontWeight=FontWeight(750));RocoText("Schlafscore",fontSize=10.sp,lineHeight=12.sp,color=Color(0xFF70766C))}
    }
}

@Composable internal fun V2RobotNote(name: String, text: String, reactionKey: Int = 0,mood: RobotMood?=null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(15.dp)) {
        RobotCompanion(name, false, Modifier.size(85.dp), reactionKey = 31*reactionKey+text.hashCode(),mood=mood,event=RobotEvent.CONTENT_CHANGED)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            RocoText("$name erklärt", style = MaterialTheme.typography.labelMedium.copy(fontSize=11.sp,lineHeight=17.sp,fontWeight=FontWeight(750)), color = Color(0xFF78613A))
            RocoText(text, style = MaterialTheme.typography.bodyMedium.copy(fontSize=13.sp,lineHeight=21.45.sp), color = Color(0xFF46513E))
        }
    }
}

internal data class V2PlotPoint(val at: Instant, val value: Double?,val label: String?=null,val samples: Int?=null,val expected: Int?=null,val joinable: Boolean=true)

/** Time-proportional x-axis; missing observations explicitly break the line. Selection also works without tapping the canvas. */
@Composable internal fun V2LineChart(points: List<V2PlotPoint>, unit: String, color: Color = V2Green,
    low: Double = 0.0, high: Double = 100.0, baseline: Pair<Double, Double>? = null, intraday: Boolean = false, decimals: Int = if(unit=="ms") 1 else 0) {
    var chooseValue by remember(points) {mutableStateOf(false)}
    val context=LocalContext.current
    val chartTypeface=remember(context) {context.resources.getFont(R.font.manrope_regular)}
    val plotted=points.map {it.copy(value=it.value?.takeIf {v->v.isFinite()})}
    var selected by remember(plotted) { mutableIntStateOf((plotted.lastIndex).coerceAtLeast(0)) }
    val valid = plotted.filter { it.value?.isFinite()==true }
    if(valid.isEmpty()) { RocoText("Noch keine Messpunkte in diesem Zeitraum.", color = V2Muted); return }
    val bounds=vitalChartBounds(valid.map {it.value!!},low,high,baseline)
    val lower=bounds.first;val upper=bounds.second
    fun number(value: Double)=String.format(Locale.getDefault(),"%.${decimals.coerceIn(0,3)}f",value)
    val format = remember(intraday) { DateTimeFormatter.ofPattern(if(intraday) "HH:mm" else "dd.MM.", Locale.getDefault()).withZone(ZoneId.systemDefault()) }
    val range = (plotted.last().at.toEpochMilli() - plotted.first().at.toEpochMilli()).toDouble().coerceAtLeast(1.0)
    val leftMargin=maxOf(37f,maxOf(number(lower).length,number(upper).length)*6f*LocalDensity.current.fontScale+9f).dp
    val current = plotted.getOrNull(selected)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RocoText(current?.label ?: current?.at?.let(format::format) ?: "", style = MaterialTheme.typography.bodySmall, color = V2Muted, modifier = Modifier.weight(1f))
            RocoText(current?.value?.let {"${number(it)} $unit"} ?: "Keine Daten",fontSize=19.sp,lineHeight=26.sp,fontWeight=FontWeight(750),color=Color(0xFF3E4C34))
            IconButton(onClick={chooseValue=true},modifier=Modifier.size(32.dp).semantics {contentDescription=RocoStrings.translate("Werte auswählen")}) {V2Icon("expand",tint=V2Muted,size=17.dp)}
        }
        BoxWithConstraints(Modifier.fillMaxWidth().v2HorizontalBleed(5.dp)) {Canvas(Modifier.fillMaxWidth().height(maxWidth*(172f/350)).pointerInput(plotted) { detectTapGestures { p ->
            val left = leftMargin.toPx(); val plot = size.width - left - 14.dp.toPx()
            selected = plotted.indices.minByOrNull { abs(left + (plotted[it].at.toEpochMilli() - plotted.first().at.toEpochMilli()) / range * plot - p.x) } ?: 0
        } }.semantics {
            customActions=listOf(CustomAccessibilityAction("Vorheriger Wert") {selected=(selected-1).coerceAtLeast(0);true},CustomAccessibilityAction("Nächster Wert") {selected=(selected+1).coerceAtMost(plotted.lastIndex);true})
            contentDescription = RocoStrings.translate("Verlauf mit Zeitachse und Skala von ${number(lower)} bis ${number(upper)} $unit. Werte über die Auswahlliste lesen.") }) {
            val left = leftMargin.toPx(); val right = size.width - 14.dp.toPx(); val top = 12.dp.toPx(); val bottom = size.height - 29.dp.toPx()
            fun x(at: Instant) = left + ((at.toEpochMilli() - plotted.first().at.toEpochMilli()) / range).toFloat() * (right-left)
            fun y(v: Double) = bottom - ((v-lower)/(upper-lower)).toFloat() * (bottom-top)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11.sp.toPx(); this.color = android.graphics.Color.rgb(94,106,83);typeface=chartTypeface;fontFeatureSettings="tnum" }
            baseline?.let { (lo,hi) -> drawRect(Color(0xFFDCE9E0), Offset(left,y(hi)), Size(right-left,(y(lo)-y(hi)).coerceAtLeast(0f))) }
            listOf(lower,(lower+upper)/2,upper).forEach { tick ->
                drawLine(Color(0xFFE2E6DF), Offset(left,y(tick)),Offset(right,y(tick)),1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(),4.dp.toPx())))
                paint.textAlign = Paint.Align.RIGHT; drawContext.canvas.nativeCanvas.drawText(number(tick),left-7.dp.toPx(),y(tick)+4.dp.toPx(),paint)
            }
            if(low == 0.0 && upper > 100 && unit == "Pkt.") drawLine(V2Load.copy(alpha=.65f),Offset(left,y(100.0)),Offset(right,y(100.0)),2.dp.toPx())
            current?.value?.let {drawLine(color.copy(alpha=.2f),Offset(x(current.at),top),Offset(x(current.at),bottom),1.dp.toPx())}
            plotted.zipWithNext().forEach { (a,b) -> if(a.value != null && b.value != null && a.joinable && b.joinable) drawLine(color,Offset(x(a.at),y(a.value)),Offset(x(b.at),y(b.value)),2.7.dp.toPx(),StrokeCap.Round) }
            plotted.forEachIndexed {index,p->p.value?.let {v->drawCircle(if(index==selected) color else Color.White,if(index==selected) 5.dp.toPx() else 3.dp.toPx(),Offset(x(p.at),y(v)));if(index!=selected) drawCircle(color,3.dp.toPx(),Offset(x(p.at),y(v)),style=Stroke(2.dp.toPx()))}}
            listOf(0,plotted.size/3,plotted.size*2/3,plotted.lastIndex).distinct().forEach { index ->
                paint.textAlign = if(index==0) Paint.Align.LEFT else if(index==plotted.lastIndex) Paint.Align.RIGHT else Paint.Align.CENTER
                drawContext.canvas.nativeCanvas.drawText(format.format(plotted[index].at),x(plotted[index].at),size.height-5.dp.toPx(),paint)
            }
        }
        }
        if(chooseValue) V2DetailSheet(onDismissRequest={chooseValue=false},title={RocoText("Wert auswählen")},text={
            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {items(plotted.size) {index->
                val point=plotted[index]
                TextButton(onClick={selected=index;chooseValue=false},modifier=Modifier.fillMaxWidth()) {RocoText(point.label ?: format.format(point.at),Modifier.weight(1f));RocoText(point.value?.let {"${number(it)} $unit"} ?: "Keine Daten")}
            }}
        },confirmButton={ClayButton(onClick={chooseValue=false},secondary=true) {RocoText("Schließen")}})
    }
}

@Composable internal fun StreakFlame(count: Int, celebrating: Boolean = false) {
    val progress = remember(count) { Animatable(if(celebrating) 0f else 1f) }
    val motion=productMotionEnabled()
    LaunchedEffect(count,celebrating,motion) {if(celebrating&&motion) progress.animateTo(1f,tween(1300,easing=FastOutSlowInEasing)) else progress.snapTo(1f)}
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.semantics { contentDescription=RocoStrings.translate("$count abgeschlossene Logbuch-Tage in Folge.") }) {
        Canvas(Modifier.size(42.dp,54.dp)) {
            val p = progress.value; val w = size.width; val h = size.height
            val path = Path().apply { moveTo(w*.54f,h*(.05f+.12f*(1-p))); cubicTo(w*.76f,h*.4f,w*.63f,h*.35f,w*.82f,h*.49f); cubicTo(w*1.1f,h*.86f,w*.75f,h,w*.48f,h); cubicTo(w*.1f,h,w*.01f,h*.66f,w*.24f,h*.39f); cubicTo(w*.28f,h*.59f,w*.42f,h*.46f,w*.54f,h*(.05f+.12f*(1-p))); close() }
            drawPath(path, Color(0xFFE38C3E).copy(alpha = .5f+.5f*p)); drawOval(Color(0xFFFFD58A), Offset(w*.34f,h*.58f),Size(w*.34f,h*.35f))
        }
        Column { RocoText("$count Logbuch-Tage",style=MaterialTheme.typography.titleMedium); RocoText("ehrlich eingecheckt",style=MaterialTheme.typography.bodySmall,color=V2Muted) }
    }
}

@Composable internal fun StreakHandoff(robot: String, count: Int) {
    val travel=remember {Animatable(0f)}
    val motion=productMotionEnabled()
    LaunchedEffect(motion) {if(motion) {kotlinx.coroutines.delay(250);travel.animateTo(1f,tween(1300,easing=FastOutSlowInEasing))} else travel.snapTo(1f)}
    Box(Modifier.fillMaxWidth().height(190.dp).semantics {contentDescription=RocoStrings.translate("$robot übergibt dir die Flamme für $count abgeschlossene Logbuch-Tage in Folge.")}) {
        RobotCompanion(robot,true,Modifier.align(Alignment.CenterStart).size(140.dp))
        Column(Modifier.align(Alignment.CenterEnd).width(125.dp).padding(top=65.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            RocoText(count.toString(),fontSize=34.sp,fontWeight=FontWeight.Bold)
            RocoText("Logbuch-Tage",style=MaterialTheme.typography.labelMedium)
        }
        Canvas(Modifier.fillMaxSize()) {
            val p=travel.value;val x=size.width*(.34f+.45f*p);val y=size.height*(.43f-.16f*sin(p*Math.PI).toFloat())
            val w=27.dp.toPx();val h=43.dp.toPx()
            val flame=Path().apply {moveTo(x,y-h*.8f);cubicTo(x+w*.2f,y-h*.2f,x+w*.6f,y-h*.4f,x+w*.45f,y);cubicTo(x+w*.45f,y+h*.5f,x-w*.5f,y+h*.5f,x-w*.45f,y);cubicTo(x-w*.5f,y-h*.3f,x-w*.1f,y-h*.4f,x,y-h*.8f);close()}
            drawPath(flame,Color(0xFFE38C3E));drawOval(Color(0xFFFFD58A),Offset(x-w*.17f,y-h*.05f),Size(w*.34f,h*.3f))
        }
    }
}

internal fun durationLabel(minutes: Double?): String = minutes?.roundToInt()?.let { if(it>=60) "${it/60} h ${it%60} min" else "$it min" } ?: "–"
internal fun timeLabel(at: Instant): String = DateTimeFormatter.ofPattern("HH:mm",Locale.getDefault()).withZone(ZoneId.systemDefault()).format(at)

/** Finite, nonzero axis range; negative measurements and personal bands must stay inside it. */
internal fun vitalChartBounds(values: List<Double>,low: Double,high: Double,band: Pair<Double,Double>?=null): Pair<Double,Double> {
    val finite=(values+listOfNotNull(band?.first,band?.second)).filter {it.isFinite()}
    val minimum=minOf(low,finite.minOrNull() ?: low)
    val maximum=maxOf(high,finite.maxOrNull() ?: high)
    val padding=maxOf((maximum-minimum)*.05,.001)
    return (if(minimum<low) minimum-padding else minimum) to
        (if(maximum>high) maximum+padding else maxOf(maximum,minimum+padding))
}
