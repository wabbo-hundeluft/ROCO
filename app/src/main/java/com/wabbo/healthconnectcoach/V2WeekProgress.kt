package com.wabbo.healthconnectcoach

import androidx.compose.foundation.BorderStroke
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
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToInt

@Composable internal fun V2WeekProgress(plan: TrainingPlan?,days: List<DayInsight>,at: LocalDate,edit: ()->Unit,connector: String,today: LocalDate=LocalDate.now()) {
    var showBasis by rememberSaveable(at.toString()) {mutableStateOf(false)}
    V2Card {
        val monday=at.with(DayOfWeek.MONDAY)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.Top) {
            Column(Modifier.weight(1f).widthIn(max=220.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {V2Eyebrow("DEINE WOCHE");RocoText("Kleine Schritte. Echter Fortschritt.",fontSize=14.sp,lineHeight=21.sp,fontWeight=FontWeight(750))}
            Surface(color=Color(0xFFF1F3EB),shape=RoundedCornerShape(7.dp)) {RocoText("KW ${at.get(WeekFields.ISO.weekOfWeekBasedYear())}",Modifier.padding(horizontal=7.dp,vertical=5.dp),fontSize=10.sp,lineHeight=14.sp,color=Color(0xFF64764C))}
        }
        if(plan==null) {RocoText("Deine Wochenziele stehen fest, sobald du sie bestätigt hast.",color=V2Muted);ClayButton(onClick=edit) {RocoText("Gemeinsam Ziele festlegen")}}
        else {
            val summary=weekTrainingSummary(days,at,today,connector)
            val week=TrainingPlanLogic.week(plan,at)
            val load=days.filter {it.date in monday..monday.plusDays(6)&&it.date<=today&&it.sources["_connector"]==connector}.maxByOrNull {it.date}?.scores?.get(com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine.SESSION_LOAD_ID)?.takeIf {it.algorithmVersion==com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine.WEEKLY_MODEL_VERSION}
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom) {
                RocoText("Wochenbelastung",Modifier.weight(1f),fontSize=12.sp,lineHeight=18.sp)
                RocoText(load?.score?.let {"$it / 100 Punkte"} ?: "Noch kein Wert",fontSize=15.sp,lineHeight=21.sp,fontWeight=FontWeight(750))
            }
            load?.score?.let {value->LinearProgressIndicator(progress={value.coerceIn(0,100)/100f},modifier=Modifier.fillMaxWidth().height(7.dp),color=if(value>100) V2Load else Color(0xFF779C6A),trackColor=Color(0xFFEDF0E6))}
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom) {RocoText("Geplante Einheiten",Modifier.weight(1f),fontSize=12.sp,lineHeight=18.sp);RocoText("${week.count {it.completed}} / ${week.size}",fontSize=15.sp,lineHeight=21.sp,fontWeight=FontWeight(750))}
            Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                (0L..6L).forEach {offset->
                    val date=monday.plusDays(offset);val entries=week.filter {it.date==date};val complete=entries.isNotEmpty()&&entries.all {it.completed}
                    val label=when {complete->"erledigt";entries.isEmpty()->"Erholung";date<today->"offen";else->"geplant"}
                    Surface(shape=CircleShape,color=if(complete) Color(0xFFDCEBD1) else Color(0xFFF2F4ED),border=if(date==today) BorderStroke(1.5.dp,Color(0xFF83956B)) else null,modifier=Modifier.size(29.dp).semantics {contentDescription=RocoStrings.translate("${date.format(DateTimeFormatter.ofPattern("EEEE d. MMM",Locale.getDefault()))}: $label")}) {
                        Box(contentAlignment=Alignment.Center) {if(complete) V2Icon("check",tint=Color(0xFF4E7145),size=15.dp) else RocoText(date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW,Locale.getDefault()),fontSize=10.sp,color=Color(0xFF62734D))}
                    }
                }
            }
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick={showBasis=!showBasis},contentPadding=PaddingValues(0.dp)) {RocoText(if(showBasis) "Erklärung schließen" else "So zählt dein Fortschritt",fontSize=11.sp)}
                TextButton(onClick=edit,contentPadding=PaddingValues(0.dp)) {RocoText("Ziele bearbeiten",fontSize=12.sp);V2Icon("next",tint=V2Green,size=17.dp)}
            }
            if(showBasis) {
                RocoText("${summary.minutes.roundToInt()} erfasste Trainingsminuten sind Information, kein zweites Wochenziel. Bestätigungen ergänzen keine Sensorminuten.",fontSize=11.sp,lineHeight=17.sp,color=Color(0xFF616B59))
                RocoText(if(summary.expectedDays==0) "Künftige Woche · aktuelle Zielvorgabe" else "$connector · ${summary.measuredDays}/${summary.expectedDays} bisherige Tagesstände · aktuelle Zielvorgabe",fontSize=11.sp,lineHeight=17.sp,color=Color(0xFF616B59))
            }
        }
    }
}
