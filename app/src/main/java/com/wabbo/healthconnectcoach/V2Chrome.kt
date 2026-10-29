package com.wabbo.healthconnectcoach

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun V2BottomNavigation(selected: String,onSelect: (String)->Unit,insets: WindowInsets=WindowInsets.navigationBars) {
    Row(Modifier.fillMaxWidth().background(Color(0xFFFAFBF6)).drawBehind {drawLine(Color(0xFFE3E7DD),Offset.Zero,Offset(size.width,0f),1.dp.toPx())}
        .windowInsetsPadding(insets).padding(start=20.dp,end=20.dp,top=9.dp,bottom=18.dp),horizontalArrangement=Arrangement.spacedBy(2.dp)) {
        listOf("Heute" to "home","Training" to "training","Trends" to "trends","Coach" to "coach").forEach {(label,icon)->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(15.dp)).background(if(selected==label) Color(0xFFE5ECDD) else Color.Transparent)
                .selectable(selected==label,onClick={onSelect(label)},role=Role.Tab).heightIn(min=56.dp).padding(vertical=7.dp),
                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                V2Icon(icon,tint=if(selected==label) Color(0xFF3D5C37) else Color(0xFF616E54),size=22.dp)
                RocoText(label,fontSize=11.sp,lineHeight=16.sp,fontWeight=FontWeight(650),color=if(selected==label) Color(0xFF3D5C37) else Color(0xFF616E54))
            }
        }
    }
}

@Composable internal fun V2Eyebrow(text: String,modifier: Modifier=Modifier) {
    RocoText(text,modifier=modifier,fontSize=10.sp,lineHeight=14.sp,letterSpacing=1.2.sp,fontWeight=FontWeight(750),color=Color(0xFF64764C))
}
