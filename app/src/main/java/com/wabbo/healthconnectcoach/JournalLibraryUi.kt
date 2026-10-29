package com.wabbo.healthconnectcoach

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun JournalLibrarySheet(document: WellnessJournal,saving: Boolean,error: String?,dismiss: ()->Unit,save: (WellnessJournal)->Unit) {
    var active by remember(document.revision) {mutableStateOf(document.activeIds)}
    var search by rememberSaveable {mutableStateOf("")};var category by rememberSaveable {mutableStateOf("Alle")}
    var custom by rememberSaveable {mutableStateOf(false)};var title by rememberSaveable {mutableStateOf("")};var question by rememberSaveable {mutableStateOf("")};var unit by rememberSaveable {mutableStateOf("")};var numeric by rememberSaveable {mutableStateOf(false)}
    var note by remember {mutableStateOf<String?>(null)}
    V2DetailSheet(dismiss,title={RocoText("Deine Fragen")},startExpanded=true,text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        RocoText("Wähle bis zu zehn Fragen. Fang klein an; Ja, Nein und bewusstes Überspringen bleiben gleichberechtigt.",color=V2Muted)
        RocoText("${active.size} / 10 ausgewählt",fontWeight=FontWeight.Bold)
        OutlinedTextField(search,{search=it.take(80)},label={RocoText("Fragen suchen")},singleLine=true,modifier=Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {(listOf("Alle","Eigene")+document.habits.map {it.category}).distinct().forEach {c->FilterChip(selected=category==c,onClick={category=c},label={RocoText(c,fontSize=11.sp)})}}
        ClayButton(onClick={custom=!custom},secondary=true) {V2Icon("add");RocoText(if(custom) "Eigene Frage schließen" else "Eigene Frage erstellen")}
        if(custom) V2Card("Was möchtest du beobachten?") {
            OutlinedTextField(title,{title=it.take(60)},label={RocoText("Kurzer Name (optional)")},singleLine=true,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(question,{question=it.take(180)},label={RocoText("Deine tägliche Frage")},modifier=Modifier.fillMaxWidth())
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {FilterChip(selected=!numeric,onClick={numeric=false},label={RocoText("Ja / Nein")});FilterChip(selected=numeric,onClick={numeric=true},label={RocoText("Menge / Zahl")})}
            if(numeric) OutlinedTextField(unit,{unit=it.take(24)},label={RocoText("Einheit, z. B. Minuten")},singleLine=true,modifier=Modifier.fillMaxWidth())
            RocoText("Eine neue Bedeutung bekommt eine neue Frage. Frühere Antworten werden nicht nachträglich umgedeutet.",fontSize=12.sp,color=V2Muted)
            ClayButton(onClick={try {
                save(prepareJournalLibraryUpdate(document,active,true,title,question,numeric,unit))
            } catch(e:IllegalArgumentException) {note=e.message}},enabled=!saving&&question.isNotBlank()&&(!numeric||unit.isNotBlank())) {RocoText("Eigene Frage hinzufügen")}
        }
        document.habits.filter {(category=="Alle"||category=="Eigene"&&it.custom||it.category==category)&&(search.isBlank()||(it.title+" "+it.question).contains(search,true))}.forEach {habit->
            val selected=habit.id in active
            V2ClaySurface(onClick={if(selected) active=active-habit.id else if(active.size<10) active=active+habit.id else note="Zehn Fragen reichen für einen übersichtlichen Check-in. Entferne zuerst eine andere."},enabled=!saving,color=if(selected) Color(0xFFE1EDCD) else Color.White,modifier=Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    V2Icon(if(selected) "check_circle" else "add",tint=if(selected) V2Green else V2Muted)
                    Column(Modifier.weight(1f)) {if(habit.custom) { RocoVerbatimText(habit.title,fontWeight=FontWeight.Bold,fontSize=14.sp); RocoVerbatimText(habit.question,fontSize=12.sp,color=V2Muted); RocoText("Eigene Frage",fontSize=10.sp,color=V2Load) } else { RocoText(habit.title,fontWeight=FontWeight.Bold,fontSize=14.sp); RocoText(habit.question,fontSize=12.sp,color=V2Muted) }}
                }
            }
        }
    }},confirmButton={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        (note ?: error)?.let {RocoText(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp)}
        ClayButton(onClick={try {save(prepareJournalLibraryUpdate(document,active,custom,title,question,numeric,unit))} catch(e:IllegalArgumentException) {note=e.message}},enabled=active.isNotEmpty()&&!saving) {RocoText(if(saving) "Wird gespeichert …" else "Fragen übernehmen")}
    }})
}
