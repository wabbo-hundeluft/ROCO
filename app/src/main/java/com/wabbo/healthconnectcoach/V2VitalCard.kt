package com.wabbo.healthconnectcoach

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable internal fun V2VitalCard(key: String,label: String,day: DayInsight?,days: List<DayInsight>,today: LocalDate,connector: String,modifier: Modifier=Modifier,open: ()->Unit) {
    val value=day?.takeIf {it.sources["_connector"]==connector}?.let {finiteVital(it,key)}
    val history=comparableTrendDays(days,connector,today,key=key).associateBy {it.date}
    val values=(6 downTo 0).map {history[today.minusDays(it.toLong())]?.let {finiteVital(it,key)}}
    val tint=if(key=="rhr") V2Load else V2Green
    V2ClaySurface(onClick=open,onClickLabel=RocoStrings.translate("$label und Verlauf öffnen"),modifier=modifier) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
            RocoText(label,fontSize=11.sp,lineHeight=17.sp,color=Color(0xFF687361))
            RocoText(value?.let {"${it.roundToInt()} ${if(key=="hrv") "ms" else "bpm"}"} ?: "–",fontSize=26.sp,lineHeight=32.sp,letterSpacing=(-.7).sp,fontWeight=FontWeight(750))
            Canvas(Modifier.fillMaxWidth().height(32.dp).clearAndSetSemantics {}) {
                val valid=values.filterNotNull();val min=valid.minOrNull() ?: 0.0;val range=((valid.maxOrNull() ?: 1.0)-min).coerceAtLeast(1.0)
                fun at(i: Int,v: Double)=Offset(i/6f*size.width,(size.height-3.dp.toPx()-(v-min)/range*(size.height-6.dp.toPx())).toFloat())
                values.zipWithNext().forEachIndexed {i,(a,b)->if(a!=null&&b!=null) drawLine(tint,at(i,a),at(i+1,b),1.8.dp.toPx(),StrokeCap.Round)}
                values.forEachIndexed {i,v->if(v!=null) drawCircle(tint,1.8.dp.toPx(),at(i,v))}
            }
            RocoText(vitalComparison(day?.takeIf {it.sources["_connector"]==connector},key),fontSize=10.sp,lineHeight=16.sp,color=Color(0xFF66815C))
        }
    }
}
