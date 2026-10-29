package com.wabbo.healthconnectcoach

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Week controls belong to the date, keeping the actual training list together. */
@Composable internal fun V2TrainingHeader(label: String,current: Boolean,canBack: Boolean,canNext: Boolean,
    previous: ()->Unit,next: ()->Unit,reset: ()->Unit,edit: ()->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            RocoText(label,Modifier.weight(1f),fontSize=11.sp,lineHeight=17.sp,fontWeight=FontWeight(750),color=Color(0xFF64764C))
            V2ClaySurface(previous,Modifier.width(40.dp).semantics {contentDescription=RocoStrings.translate("Vorherige Trainingswoche")},enabled=canBack,color=Color(0xFFEDF1E6),shape=RoundedCornerShape(13.dp),border=null,edgeHeight=3.dp) {Box(Modifier.height(40.dp),contentAlignment=Alignment.Center) {V2Icon("back",size=20.dp)}}
            V2ClaySurface(next,Modifier.width(40.dp).semantics {contentDescription=RocoStrings.translate("Nächste Trainingswoche")},enabled=canNext,color=Color(0xFFEDF1E6),shape=RoundedCornerShape(13.dp),border=null,edgeHeight=3.dp) {Box(Modifier.height(40.dp),contentAlignment=Alignment.Center) {V2Icon("next",size=20.dp)}}
        }
        V2PageHeader("Deine Trainingswoche.","Ein Plan, der zu deinem Alltag passt.",action=edit)
        if(!current) TextButton(onClick=reset,contentPadding=PaddingValues(0.dp)) {V2Icon("date",size=18.dp);Spacer(Modifier.width(7.dp));RocoText("Zur aktuellen Woche",fontSize=12.sp)}
    }
}
