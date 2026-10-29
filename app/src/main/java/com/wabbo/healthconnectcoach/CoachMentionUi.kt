package com.wabbo.healthconnectcoach

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun CoachMentionPicker(query: String,data: CoachDataAccess?,date: java.time.LocalDate,enabled: Boolean,compact: Boolean=false,choose: (CoachMention)->Unit) {
    Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(22.dp),color=androidx.compose.ui.graphics.Color.White,tonalElevation=2.dp) {
        Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            RocoText("Persönliche Daten anhängen",fontWeight=FontWeight.Bold,fontSize=14.sp)
            RocoText(if(enabled) "Datierte Werte, Basis und Verlauf für Pip." else "Gesundheitsdaten zuerst im Profil freigeben.",fontSize=11.sp,color=V2Muted)
            val options=matchingMentions(query)
            if(options.isEmpty()) RocoText("Kein passender Wert. Probiere HRV, Schlaf oder Akku.",fontSize=12.sp)
            LazyColumn(Modifier.heightIn(max=if(compact) 96.dp else 184.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {items(options,key={it.id}) {item->
                val available=data?.days.orEmpty().any {it.date<=date&&(if(item.metric!=null) finiteVital(it,item.metric)!=null else it.scores[item.scoreId]?.score!=null)}
                TextButton(onClick={choose(item)},enabled=enabled,modifier=Modifier.fillMaxWidth(),contentPadding=PaddingValues(horizontal=8.dp,vertical=6.dp)) {
                    V2Icon(item.icon,size=19.dp);Spacer(Modifier.width(10.dp));RocoText(item.label,Modifier.weight(1f),fontSize=13.sp)
                    RocoText(if(available) "Anhängen +" else "Keine Daten",fontSize=10.sp,color=V2Muted)
                }
            }}
        }
    }
}
