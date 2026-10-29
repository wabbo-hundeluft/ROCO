package com.wabbo.healthconnectcoach

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine as Engine
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable internal fun V2TodayScreen(person: String,robot: String,scores: Map<String,Engine.ScoreResult>,days: List<DayInsight>,
    today: LocalDate,sourceName: String,connected: Boolean,refreshing: Boolean,importStep: String,plan: TrainingPlan?,
    showSource: ()->Unit,onRefresh: ()->Unit,openDetail: (String)->Unit,openProfile: ()->Unit,openTraining: ()->Unit,
    openGoals: ()->Unit,openSession: (PlannedSession)->Unit,onConnect: ()->Unit,openAllVitals: ()->Unit,openVital: (String)->Unit,
    currentDate: LocalDate=today,selectDate: (LocalDate)->Unit={},energyCacheSource: String?=null,journalCard: (@Composable ()->Unit)?=null,journalProminent: Boolean=false,nightCard:(@Composable ()->Unit)?=null,vitalsCheckCard:(@Composable ()->Unit)?=null,analysis: @Composable ()->Unit = {}) {
    var dayPicker by rememberSaveable {mutableStateOf(false)}
    var roadVisible by remember {mutableStateOf(refreshing)}
    LaunchedEffect(refreshing) {if(refreshing) roadVisible=true else {kotlinx.coroutines.delay(1200);roadVisible=false}}
    val historical=today!=currentDate
    if(dayPicker) V2DayPicker(today,currentDate,days,dismiss={dayPicker=false},select=selectDate)
    val todayDay=days.lastOrNull {it.date==today}

                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) { Row(Modifier.clickable(onClickLabel=RocoStrings.translate("Tag auswählen")) {dayPicker=true}.semantics {contentDescription=RocoStrings.translate("Tag auswählen")},verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {RocoText(today.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM",Locale.getDefault())),color=V2Muted,style=MaterialTheme.typography.bodySmall);V2Icon("expand",size=16.dp,tint=V2Muted)};RocoText(if(historical) "Dein Rückblick." else if(person.isBlank()) "Hallo." else "Hallo, $person.",style=MaterialTheme.typography.headlineLarge) }
                        Surface(shape=CircleShape,color=Color(0xFFE3E9DD),modifier=Modifier.size(46.dp).clickable(onClickLabel=RocoStrings.translate("Profil öffnen")) {openProfile()}.semantics {contentDescription=RocoStrings.translate("Profil öffnen")}) { Box(contentAlignment=Alignment.Center) { RocoText(person.take(1).uppercase().ifBlank { "P" },fontWeight=FontWeight.Bold) } }
                    }
                    Row(Modifier.fillMaxWidth().heightIn(min=32.dp).v2TopOverlap(24.dp).clickable(onClickLabel=RocoStrings.translate("Datengrundlage öffnen"),onClick=showSource),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                        Box(Modifier.size(6.dp).background(if(connected) Color(0xFF4B9568) else V2Load,CircleShape))
                        RocoText("$sourceName · ${todayDay?.calculatedAt?.let {timeLabel(it)} ?: "noch kein gespeicherter Stand"}${if(scores.values.any {it.stale}) " · gespeichert" else ""}",fontSize=11.sp,lineHeight=17.sp,color=Color(0xFF596653))
                        V2Icon("next",tint=Color(0xFF596653),size=17.dp)
                    }
                    if(!historical&&journalProminent&&journalCard!=null) {
                        Box(Modifier.v2TopOverlap(23.dp)) {journalCard()}
                        V2ScoreCockpit(scores,refreshing,onRefresh,openDetail,historical,energyCacheSource)
                    } else Box(Modifier.v2TopOverlap(23.dp)) {V2ScoreCockpit(scores,refreshing,onRefresh,openDetail,historical,energyCacheSource)}
                    if(roadVisible) RobotLoadingRoad(robot,importStep,loading=refreshing)
                    else Box(Modifier.v2TopOverlap(4.dp)) {V2RobotNote(robot,if(historical) "Das ist dein gespeicherter Stand vom ${today.format(DateTimeFormatter.ofPattern("d. MMM",Locale.getDefault()))}. Öffne einen Score für die damaligen Details." else trainingDayGuidance(plan,todayDay,scores,today).let {it.title+". "+it.message},mood=if(historical) RobotMood.CURIOUS else robotScoreMood(Engine.BODY_BATTERY_ID,scores[Engine.BODY_BATTERY_ID]?.score))}
                    if(!historical&&!journalProminent) journalCard?.invoke()
                    analysis()
                    if(historical) V2Card("Dein Tagesrückblick") {
                        RocoText(if(todayDay==null) "Für diesen Tag ist noch kein Stand gespeichert. Heutige Werte werden hier nicht übernommen." else "Die drei großen Werte und ihre Details gehören zum ausgewählten Tag.",color=V2Muted)
                        ClayButton(onClick={selectDate(currentDate)},secondary=true) {RocoText("Zurück zu Heute")}
                    } else V2Section("Heute für dich", "Dein Plan", {openTraining()}) {
                        val next=plan?.sessions?.firstOrNull { !it.completed && it.date>=today }
                        if(next!=null) V2SessionCard(next,at=today) { openSession(next) }
                        else V2Card { RocoText(if(plan==null) "Lass uns deinen Trainingsplan gemeinsam festlegen." else "Heute darfst du Raum für Erholung lassen.");ClayButton(onClick={openGoals()},secondary=true) { RocoText(if(plan==null) "Meine Ziele besprechen" else "Plan anpassen") } }
                    }
                    if(!historical) V2WeekProgress(plan,days,today,{openGoals()},sourceName)
                    if(!historical) nightCard?.invoke()
                    V2Section("Deine Gesundheitswerte","Alle Werte",{openAllVitals()}) {
                        if(vitalsCheckCard!=null) vitalsCheckCard() else Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            listOf("hrv" to "HRV","rhr" to "Ruhepuls").forEach { (key,label) ->
                                V2VitalCard(key,label,todayDay,days,today,sourceName,Modifier.weight(1f)) {openVital(key)}
                            }
                        }
                    }
                    if(!connected) ClayButton(onClick=onConnect) { RocoText("Uhrendaten verbinden") }

}
