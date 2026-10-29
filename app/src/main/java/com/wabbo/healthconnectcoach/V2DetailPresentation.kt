package com.wabbo.healthconnectcoach

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun V2EnergyEvent(entry: ActivityInsight,open: ()->Unit) {
    V2ClaySurface(open,color=Color(0xFFF5F1E9),edgeColor=Color(0xFFE5DDCB),shape=RoundedCornerShape(13.dp),border=null,
        onClickLabel="${entry.title} erklären",edgeHeight=3.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            V2Icon("walk",tint=Color(0xFF9E7D4C),size=20.dp)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                RocoVerbatimText(entry.title,fontSize=12.sp,lineHeight=17.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=Color(0xFF78613A))
                RocoText(timeLabel(entry.start),fontSize=10.sp,lineHeight=14.sp,color=V2Muted)
            }
            RocoText(durationLabel(entry.minutes),fontSize=12.sp,lineHeight=18.sp,color=Color(0xFF78613A))
            V2Icon("next",tint=Color(0xFF9E7D4C),size=17.dp)
        }
    }
}

internal fun sleepOverviewNote(day: DayInsight?): String {
    val minutes=day?.metrics?.get("sleep_minutes")?.takeIf {it.isFinite()&&it>=0}
    val goal=day?.metrics?.get("sleep_goal")?.takeIf {it.isFinite()&&it>0}
    val lead=when {
        minutes==null||goal==null -> "Die Dauer ist nur ein Teil deiner Nacht."
        minutes>=goal -> "Du hast dein Schlafziel erreicht."
        else -> "Deine Nacht war kürzer als dein Schlafziel."
    }
    return "$lead Phasen, HRV und Ruhepuls helfen dir, sie genauer zu verstehen."
}
