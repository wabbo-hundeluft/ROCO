package com.wabbo.healthconnectcoach

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable internal fun VitalCheckHomeCard(days:List<DayInsight>,date:LocalDate,connector:String,open:()->Unit) {
    val items=vitalCheck(days,date,connector)
    val comparable=items.count {it.state in setOf(VitalCheckState.WITHIN,VitalCheckState.HIGHER,VitalCheckState.LOWER)}
    val outside=items.count {it.state in setOf(VitalCheckState.HIGHER,VitalCheckState.LOWER)}
    V2ClaySurface(onClick=open,onClickLabel=RocoStrings.translate("Vitalwerte-Check öffnen")) {
        Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {RocoText("Dein Vitalwerte-Check",style=MaterialTheme.typography.titleLarge);V2Icon("next")}
            RocoText(if(comparable==0) "Deine persönliche Basis lernt noch" else if(outside==0) "$comparable vergleichbare Werte in deinem Bereich" else "$outside von $comparable vergleichbaren Werten weichen ab",style=MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {items.forEach {item ->
                val tint=when(item.state) {VitalCheckState.WITHIN->V2Green;VitalCheckState.LOWER,VitalCheckState.HIGHER->V2Load;else->V2Muted}
                Canvas(Modifier.size(12.dp).semantics {contentDescription=RocoStrings.translate("${item.definition.title}: ${vitalCheckLabel(item.state)}")}) {drawCircle(tint)}
            }}
            val learning=items.count {it.state==VitalCheckState.LEARNING};val missing=items.count {it.state==VitalCheckState.MISSING}
            if(learning+missing>0) RocoText("$learning Vergleichsbereiche lernen noch · $missing Werte fehlen",style=MaterialTheme.typography.bodySmall,color=V2Muted)
            RocoText("HRV · Ruhepuls · Atmung · Sauerstoff · Hauttemperatur",style=MaterialTheme.typography.bodySmall,color=V2Muted)
        }
    }
}
@Composable internal fun VitalCheckScreen(days:List<DayInsight>,date:LocalDate,connector:String,robot:String,back:()->Unit,open:(String)->Unit) {
    V2PageHeader("Dein Vitalwerte-Check",date.toString()+" · "+connector,back)
    V2RobotNote(robot,"Wir vergleichen diese Werte mit deinem eigenen Verlauf. Eine Abweichung beschreibt einen Unterschied und erklärt für sich noch keine Ursache.",mood=RobotMood.CURIOUS)
    vitalCheck(days,date,connector).forEach {item ->
        V2ClaySurface(onClick={open(item.definition.key)},onClickLabel="${item.definition.title} und Verlauf öffnen") {
            Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                RocoText(item.definition.title,style=MaterialTheme.typography.titleMedium)
                RocoText(item.value?.let {formatVitalNumber(it,item.definition)+" "+item.definition.unit} ?: "–",style=MaterialTheme.typography.headlineMedium)
                RocoText(vitalCheckLabel(item.state),color=if(item.state in setOf(VitalCheckState.LOWER,VitalCheckState.HIGHER)) V2Load else V2Muted)
                if(item.low!=null&&item.high!=null&&item.value!=null) {
                    val label="Persönlicher Bereich ${formatVitalNumber(item.low,item.definition)} bis ${formatVitalNumber(item.high,item.definition)} ${item.definition.unit}"
                    Canvas(Modifier.fillMaxWidth().height(32.dp).semantics {contentDescription=label}) {
                        val range=(item.high-item.low).coerceAtLeast(.01)
                        val a=minOf(item.value,item.low-range*.3);val b=maxOf(item.value,item.high+range*.3)
                        fun x(v:Double)=((v-a)/(b-a)*size.width).toFloat().coerceIn(4.dp.toPx(),size.width-4.dp.toPx())
                        drawLine(V2Muted.copy(alpha=.15f),Offset(0f,size.height/2),Offset(size.width,size.height/2),6.dp.toPx(),StrokeCap.Round)
                        drawLine(V2Green.copy(alpha=.35f),Offset(x(item.low),size.height/2),Offset(x(item.high),size.height/2),12.dp.toPx(),StrokeCap.Round)
                        drawCircle(if(item.state==VitalCheckState.WITHIN) V2Green else V2Load,6.dp.toPx(),Offset(x(item.value),size.height/2))
                    }
                    RocoText(label,style=MaterialTheme.typography.bodySmall,color=V2Muted)
                    RocoText("${item.days} frühere Tage derselben Quelle · letzte 28 Tage",style=MaterialTheme.typography.bodySmall,color=V2Muted)
                } else RocoText(if(item.value==null) "Es wird kein älterer Tageswert als aktueller Wert eingesetzt." else "Für deinen persönlichen Vergleich brauchen wir mindestens sieben frühere Tage mit derselben Messherkunft.",style=MaterialTheme.typography.bodySmall,color=V2Muted)
            }
        }
    }
    RocoText("Die Bereiche beschreiben deine Messhistorie, keine medizinischen Normalwerte. Sie umfassen die mittleren 80 % und einen Mindestabstand gegen Messrauschen. Ein Wert im Bereich ist keine gesundheitliche Entwarnung.",style=MaterialTheme.typography.bodySmall,color=V2Muted)
}
