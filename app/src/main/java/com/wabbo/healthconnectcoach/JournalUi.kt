package com.wabbo.healthconnectcoach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable internal fun JournalHomeCard(journal: WellnessJournal,today: LocalDate,clock: LocalTime,robot: String,ready: Boolean,
    open: (LocalDate)->Unit) {
    val pending=journalPromptDate(journal,today,clock)
    if(!ready) return
    if(pending!=null) Surface(color=Color(0xFFFFE8AA),shape=androidx.compose.foundation.shape.RoundedCornerShape(25.dp),modifier=Modifier.fillMaxWidth().testTag("journal-home-prompt")) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(15.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                RobotCompanion(robot,false,Modifier.size(70.dp),reactionKey=pending.hashCode())
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    RocoText("Ein kleiner Check-in für dich",fontSize=21.sp,lineHeight=27.sp,fontWeight=FontWeight.ExtraBold)
                    RocoText((if(pending==today) "Heute" else "Gestern")+" · "+if(journal.activeIds.size==1) "1 kurze Frage" else "${journal.activeIds.size} kurze Fragen",style=MaterialTheme.typography.bodySmall)
                }
            }
            RocoText("Entdecke, welche Gewohnheiten mit deiner Erholung zusammenhängen. Ja und Nein zählen gleich viel.",style=MaterialTheme.typography.bodyMedium)
            ClayButton(onClick={open(pending)}) {V2Icon("edit",tint=Color.White);RocoText("Logbuch ausfüllen")}
        }
    } else V2ClaySurface(onClick={open(today)},onClickLabel=RocoStrings.translate("Logbuch und Muster öffnen"),modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            V2Icon("check_circle",tint=V2Green)
            Column(Modifier.weight(1f)) {RocoText("Dein Logbuch",fontWeight=FontWeight.Bold);RocoText("Check-in erledigt · Muster entdecken",style=MaterialTheme.typography.bodySmall,color=V2Muted)}
            RocoText("${journalStreak(journal,today)} Tage",fontWeight=FontWeight.Bold,color=V2Load);V2Icon("next")
        }
    }
}

@Composable internal fun JournalScreen(document: WellnessJournal,day: LocalDate,today: LocalDate,robot: String,
    insights: List<JournalInsight>,loadingInsights: Boolean,saving: Boolean,error: String?,modifier: Modifier=Modifier,
    chooseDate: (LocalDate)->Unit,back: ()->Unit,discuss: (String)->Unit,
    save: (WellnessJournal,()->Unit)->Unit) {
    var tab by rememberSaveable {mutableStateOf("Check-in")};var library by rememberSaveable {mutableStateOf(false)}
    Column(modifier.padding(horizontal=24.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            IconButton(onClick=back,modifier=Modifier.semantics {contentDescription=RocoStrings.translate("Logbuch schließen")}) {V2Icon("back")}
            Column(Modifier.weight(1f)) {RocoText("Dein Logbuch",fontSize=25.sp,fontWeight=FontWeight.ExtraBold);RocoText("${journalStreak(document,today)} "+if(journalStreak(document,today)==1) "Tag in Folge" else "Tage in Folge",fontSize=12.sp,color=V2Load)}
            IconButton(onClick={library=true},modifier=Modifier.semantics {contentDescription=RocoStrings.translate("Fragen auswählen")}) {V2Icon("settings")}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {listOf("Check-in","Muster","Kalender").forEach {label->
            V2ClaySurface(onClick={tab=label},color=if(tab==label) Color(0xFFDDECCB) else Color.White,edgeColor=Color(0xFFD2DCC6),modifier=Modifier.weight(1f)) {Box(Modifier.fillMaxWidth().padding(vertical=12.dp),contentAlignment=Alignment.Center) {RocoText(label,fontSize=12.sp,fontWeight=FontWeight.Bold)}}
        }}
        error?.let {RocoText(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        when(tab) {
            "Check-in"->JournalCheckIn(document,day,today,robot,saving,chooseDate,save,Modifier.weight(1f))
            "Kalender"->Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                StreakFlame(journalStreak(document,today))
                RocoText("Der Tag zählt, an dem deine Gewohnheiten stattgefunden haben. Morgens kannst du gestern ergänzen.",color=V2Muted)
                JournalCalendar(document,today) {chooseDate(it);tab="Check-in"}
            }
            else->Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(18.dp)) {JournalInsightsContent(insights,loadingInsights,robot,discuss)}
        }
    }
    if(library) JournalLibrarySheet(document,saving,error,dismiss={library=false},save={next->save(next) {library=false}})
}

private val answersSaver=Saver<Map<String,JournalAnswer>,String>(save={values->JSONObject().apply {values.forEach {(id,a)->put(id,JSONObject().put("yes",a.yes).put("amount",a.amount).put("skipped",a.skipped))}}.toString()},restore={text->
    val o=JSONObject(text);o.keys().asSequence().associateWith {id->o.getJSONObject(id).let {a->JournalAnswer(if(a.has("yes")) a.getBoolean("yes") else null,if(a.has("amount")) a.getDouble("amount") else null,a.optBoolean("skipped"))}}
})
@Composable private fun JournalCheckIn(document: WellnessJournal,date: LocalDate,today: LocalDate,robot: String,saving: Boolean,
    chooseDate: (LocalDate)->Unit,save: (WellnessJournal,()->Unit)->Unit,modifier: Modifier=Modifier) {
    val entry=document.entries.lastOrNull {it.date==date}
    val habits=document.activeIds.mapNotNull {id->document.habits.find {it.id==id}}
    var answers by rememberSaveable(date,document.revision,stateSaver=answersSaver) {mutableStateOf(entry?.answers.orEmpty())}
    var position by rememberSaveable(date) {mutableIntStateOf(0)}
    var localError by rememberSaveable(date) {mutableStateOf<String?>(null)}
    val index=position.coerceIn(0,habits.size);val answered=habits.count {it.id in answers}
    val habit=habits.getOrNull(index);val answer=habit?.let {answers[it.id]}
    var quantity by rememberSaveable(date,habit?.id,document.revision) {mutableStateOf(answer?.amount?.let {if(it%1==0.0) it.toInt().toString() else it.toString()}.orEmpty())}
    val number=quantity.replace(',','.').toDoubleOrNull()?.takeIf {it.isFinite()&&it in 0.0..10000.0}
    var justSaved by rememberSaveable(date) {mutableStateOf(false)}
    fun respond(value: JournalAnswer) {if(habit!=null) {answers=answers+(habit.id to value);localError=null;justSaved=false;position=index+1}}
    Column(modifier,verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick={chooseDate(date.minusDays(1))},enabled=date>today.minusDays(729),modifier=Modifier.semantics {contentDescription=RocoStrings.translate("Vorheriger Logbuch-Tag")}) {V2Icon("back")}
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                RocoText(when(date) {today->"Heute";today.minusDays(1)->"Gestern";else->date.format(DateTimeFormatter.ofPattern("EEEE",Locale.getDefault()))},fontWeight=FontWeight.Bold)
                RocoText(date.format(DateTimeFormatter.ofPattern("d. MMMM yyyy",Locale.getDefault())),style=MaterialTheme.typography.bodySmall,color=V2Muted)
            }
            IconButton(onClick={chooseDate(date.plusDays(1))},enabled=date<today,modifier=Modifier.semantics {contentDescription=RocoStrings.translate("Nächster Logbuch-Tag")}) {V2Icon("next")}
        }
        LinearProgressIndicator(progress={answered.toFloat()/habits.size.coerceAtLeast(1)},modifier=Modifier.fillMaxWidth().height(9.dp),color=Color(0xFF78AE44),trackColor=Color(0xFFE3EACF))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {RocoText("$answered von ${habits.size} bearbeitet",fontSize=12.sp,color=V2Muted);if(entry?.completedAt!=null) RocoText("Abgeschlossen",fontSize=12.sp,color=V2Green)}
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            if(index<habits.size) {
                val habit=habits[index]
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(15.dp)) {
                    RobotCompanion(robot,false,Modifier.size(74.dp),attentive=answer!=null,reactionKey=habit.id.hashCode(),allowSleep=false,event=if(answered>0) RobotEvent.ANSWERED else RobotEvent.NONE)
                    Column {RocoText(RocoStrings.translate(habit.category).uppercase(Locale.getDefault()),fontSize=10.sp,color=V2Muted);RocoText("Frage ${index+1} von ${habits.size}",fontWeight=FontWeight.Bold)}
                }
                if(habit.custom) RocoVerbatimText(habit.question,fontSize=27.sp,lineHeight=35.sp,fontWeight=FontWeight.ExtraBold,modifier=Modifier.fillMaxWidth())
                else RocoText(habit.question,fontSize=27.sp,lineHeight=35.sp,fontWeight=FontWeight.ExtraBold,modifier=Modifier.fillMaxWidth())
                RocoText("Gemeint ist ${if(date==today) "dein heutiger Tag" else "der ${date.format(DateTimeFormatter.ofPattern("d. MMMM",Locale.getDefault()))}"}. Nicht beantwortet ist niemals automatisch Nein.",fontSize=12.sp,lineHeight=19.sp,color=V2Muted)
                if(habit.kind==JournalQuestionKind.NUMBER) {
                    OutlinedTextField(quantity,{quantity=it.take(12);justSaved=false},label={RocoText(habit.unit.ifBlank {"Menge"})},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth(),isError=quantity.isNotBlank()&&number==null)
                    RocoText("0 ist eine bewusste Antwort ohne Menge. Für Muster werden deine höheren mit deinen niedrigeren Mengen verglichen.",fontSize=12.sp,color=V2Muted)
                }
            } else {
                RobotCompanion(robot,justSaved,Modifier.size(112.dp),attentive=!justSaved,reactionKey=answered+if(justSaved) 1000 else 0,mood=if(justSaved) RobotMood.CELEBRATE else RobotMood.PROUD,allowSleep=false,event=if(justSaved) RobotEvent.SUCCESS else RobotEvent.NONE)
                RocoText("Ehrlich eingecheckt.",fontSize=27.sp,fontWeight=FontWeight.ExtraBold)
                RocoText("Ja und Nein sind gleich wertvoll. Übersprungene Antworten gehen nicht in die Musteranalyse ein.",color=V2Muted)
                habits.forEachIndexed {i,h->V2ClaySurface(onClick={position=i;justSaved=false},modifier=Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(15.dp),verticalAlignment=Alignment.CenterVertically) {RocoText(h.title,Modifier.weight(1f),fontSize=13.sp);RocoText(answers[h.id]?.let {a->if(a.skipped) "Offen" else a.amount?.let {"$it ${h.unit}"} ?: if(a.yes==true) "Ja" else "Nein"} ?: "Fehlt",fontWeight=FontWeight.Bold);V2Icon("edit",size=17.dp)}
                }}
            }
        }
        localError?.let {RocoText(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        if(index==habits.size) ClayButton(onClick={try {val next=document.withEntry(date,answers,true,today,Instant.now());save(next) {localError=null;justSaved=true}} catch(e:IllegalArgumentException) {localError=e.message}},success=justSaved,enabled=!saving&&!justSaved&&habits.all {it.id in answers}&&habits.any {answers[it.id]?.skipped==false},modifier=Modifier.testTag("complete-journal")) {RocoText(if(saving) "Wird gespeichert …" else if(justSaved) "Gespeichert" else if(entry?.completedAt!=null) "Änderungen speichern" else "Check-in abschließen")}
        else {
            if(habit?.kind==JournalQuestionKind.BOOLEAN) Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                ClayButton(onClick={respond(JournalAnswer(yes=true))},secondary=answer?.yes!=true,enabled=!saving,modifier=Modifier.weight(1f)) {RocoText("Ja")}
                ClayButton(onClick={respond(JournalAnswer(yes=false))},secondary=answer?.yes!=false,enabled=!saving,modifier=Modifier.weight(1f)) {RocoText("Nein")}
            } else ClayButton(onClick={number?.let {respond(JournalAnswer(yes=it>0,amount=it))}},enabled=number!=null&&!saving) {RocoText("Weiter")}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick={respond(JournalAnswer(skipped=true))},enabled=!saving) {RocoText("Überspringen")}
                TextButton(onClick={position=habits.size},enabled=!saving) {RocoText("Übersicht")}
            }
        }
        if(entry?.completedAt==null) TextButton(onClick={try {save(document.withEntry(date,answers,false,today,Instant.now())) {localError=null}} catch(e:IllegalArgumentException) {localError=e.message}},enabled=answers.isNotEmpty()&&!saving) {RocoText("Als Entwurf speichern")}

    }
}

@Composable private fun JournalCalendar(document: WellnessJournal,today: LocalDate,select: (LocalDate)->Unit) {
    var monthText by rememberSaveable {mutableStateOf(today.withDayOfMonth(1).toString())}
    val month=LocalDate.parse(monthText);val offset=(month.dayOfWeek.value-1);val days=month.lengthOfMonth()
    V2Card {
        Row(verticalAlignment=Alignment.CenterVertically) {IconButton(onClick={monthText=month.minusMonths(1).toString()},enabled=month>today.minusMonths(23).withDayOfMonth(1)) {V2Icon("back")};RocoText(month.format(DateTimeFormatter.ofPattern("MMMM yyyy",Locale.getDefault())),Modifier.weight(1f),fontWeight=FontWeight.Bold);IconButton(onClick={monthText=month.plusMonths(1).toString()},enabled=month<today.withDayOfMonth(1)) {V2Icon("next")}}
        Row {listOf("M","D","M","D","F","S","S").forEach {RocoText(it,Modifier.weight(1f),color=V2Muted,fontSize=11.sp)}}
        (0 until ((offset+days+6)/7)).forEach {week->Row(horizontalArrangement=Arrangement.spacedBy(3.dp)) {(0..6).forEach {weekday->
            val number=week*7+weekday-offset+1
            if(number !in 1..days) Spacer(Modifier.weight(1f).height(44.dp)) else {
                val date=month.withDayOfMonth(number);val entry=document.entries.lastOrNull {it.date==date}
                V2ClaySurface(onClick={select(date)},enabled=date in today.minusDays(729)..today,color=if(entry?.completedAt!=null) Color(0xFFDDECCB) else Color(0xFFF5F6EE),modifier=Modifier.weight(1f).height(44.dp),shape=CircleShape) {
                    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {RocoText(number.toString(),fontSize=13.sp,fontWeight=if(date==today) FontWeight.ExtraBold else FontWeight.Normal)}
                }
            }
        }}}
        RocoText("Grün: abgeschlossen. Einträge beziehen sich auf den Verhaltenstag; die Auswertung nutzt die folgende Nacht.",fontSize=12.sp,color=V2Muted)
    }
}
