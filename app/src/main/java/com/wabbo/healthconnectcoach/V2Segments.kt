package com.wabbo.healthconnectcoach

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun V2MetricSegments(values: List<String>,selected: String,choose: (String)->Unit) {
    FlowRow(Modifier.fillMaxWidth().background(Color(0xFFEFF2E7),RoundedCornerShape(16.dp)).padding(5.dp),horizontalArrangement=Arrangement.spacedBy(5.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        values.forEach {value->
            Box(Modifier.weight(1f).widthIn(min=58.dp).clip(RoundedCornerShape(11.dp)).background(if(value==selected) Color.White else Color.Transparent)
                .selectable(value==selected,onClick={choose(value)},role=Role.Tab).padding(horizontal=4.dp,vertical=10.dp),contentAlignment=Alignment.Center) {
                RocoText(value,fontSize=11.sp,lineHeight=17.sp,fontWeight=FontWeight(650),color=if(value==selected) Color(0xFF4E6740) else Color(0xFF637551))
            }
        }
    }
}
@Composable internal fun V2PeriodSegments(period: Int,choose: (Int)->Unit) {
    Surface(shape=RoundedCornerShape(9.dp),color=Color.Transparent,border=BorderStroke(1.dp,Color(0xFFDCE4D0))) {
        Row(Modifier.padding(3.dp),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
            listOf(7,30,90).forEach {n->
                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(if(period==n) Color(0xFFDFE8D5) else Color.Transparent).selectable(period==n,onClick={choose(n)},role=Role.Tab).padding(horizontal=9.dp,vertical=8.dp),contentAlignment=Alignment.Center) {
                    RocoText("$n T",fontSize=10.sp,lineHeight=15.sp,color=if(period==n) Color(0xFF637E50) else Color(0xFF637551))
                }
            }
        }
    }
}
