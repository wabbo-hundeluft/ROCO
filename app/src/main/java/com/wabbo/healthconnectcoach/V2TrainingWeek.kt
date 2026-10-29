package com.wabbo.healthconnectcoach

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable internal fun V2TrainingWeek(plan: TrainingPlan,days: List<DayInsight>,today: LocalDate,connector: String,
    edit: ()->Unit,open: (PlannedSession)->Unit) {
    var dateText by rememberSaveable(today.toString()) {mutableStateOf(today.toString())}
    val selected=LocalDate.parse(dateText);val monday=selected.with(DayOfWeek.MONDAY)
    val first=minOf(today.minusWeeks(6).with(DayOfWeek.MONDAY),plan.sessions.minOfOrNull {it.date} ?: today)
    val last=plan.scheduledThrough ?: today.plusDays(41)
    val label="${monday.format(DateTimeFormatter.ofPattern("d. MMM",Locale.getDefault()))} – ${monday.plusDays(6).format(DateTimeFormatter.ofPattern("d. MMM",Locale.getDefault()))}"
    V2TrainingHeader(label.uppercase(Locale.getDefault()),current=monday==today.with(DayOfWeek.MONDAY),canBack=monday>first,canNext=monday.plusWeeks(1)<=last,
        previous={dateText=selected.minusWeeks(1).toString()},next={dateText=minOf(selected.plusWeeks(1),last).toString()},
        reset={dateText=today.toString()},edit=edit)
    V2WeekProgress(plan,days,selected,edit,connector,today)
    val entries=TrainingPlanLogic.week(plan,selected)
    if(entries.isEmpty()) V2Card("Raum für Erholung") {RocoText("An diesen Tagen steht kein Training im Plan. Erholungstage unterbrechen deine Serie nicht.",color=V2Muted)}
    else Column(verticalArrangement=Arrangement.spacedBy(19.dp)) {entries.forEach {entry->
        Row(verticalAlignment=if(entry.completed) Alignment.CenterVertically else Alignment.Top,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.width(26.dp).padding(top=if(entry.completed) 0.dp else 13.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                RocoText(entry.date.format(DateTimeFormatter.ofPattern("EEE",Locale.getDefault())),fontSize=10.sp,lineHeight=15.sp,color=Color(0xFF6F875B))
                RocoText(entry.date.dayOfMonth.toString(),fontSize=20.sp,lineHeight=30.sp,fontWeight=FontWeight(650),color=Color(0xFF6F875B))
            }
            Column(Modifier.weight(1f)) {
                if(entry.completed) V2ClaySurface(onClick={open(entry)},onClickLabel="${entry.title} öffnen",color=Color(0xFFF0F3E9),edgeColor=Color(0xFFD7DECE),shape=RoundedCornerShape(19.dp),modifier=Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal=15.dp,vertical=17.dp),horizontalArrangement=Arrangement.spacedBy(11.dp),verticalAlignment=Alignment.CenterVertically) {
                        V2Icon("training",tint=Color(0xFF82956D),size=22.dp)
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {RocoVerbatimText(entry.title,fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight(750),color=Color(0xFF697B58));RocoText("${entry.minutes} min · ${if(entry.recordedActivity==null) "manuell bestätigt" else "Uhr zugeordnet"}",fontSize=11.sp,lineHeight=17.sp,color=Color(0xFF626C5B))}
                        Surface(shape=CircleShape,color=Color(0xFFDCE7D0),modifier=Modifier.size(23.dp)) {Box(contentAlignment=Alignment.Center) {V2Icon("check",size=17.dp,tint=Color(0xFF819B6C))}}
                    }
                } else {
                    V2SessionCard(entry,at=today) {open(entry)}
                    RocoText(if(entry.date==today) "Heute in deinem Tempo. Raum für Erholung." else "Geplant · ${intensityLabel(entry.intensity)}",Modifier.padding(start=7.dp,top=12.dp),fontSize=11.sp,lineHeight=17.sp,color=Color(0xFF626C5B))
                }
            }
        }
    }}
}
