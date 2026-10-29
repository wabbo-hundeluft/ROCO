package com.wabbo.healthconnectcoach

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.*
import java.time.format.DateTimeFormatter

@Composable internal fun CoachMemoryScreen(memory:CoachMemory,today:LocalDate,robot:String,ready:Boolean,saving:Boolean,error:String?,back:()->Unit,
    save:(CoachMemory,()->Unit)->Unit) {
    var editing by rememberSaveable {mutableStateOf(false)}
    var entryId by rememberSaveable {mutableStateOf<String?>(null)}
    var text by rememberSaveable {mutableStateOf("")}
    var category by rememberSaveable {mutableStateOf(MemoryCategory.CONTEXT.name)}
    var expiry by rememberSaveable {mutableStateOf(0)}
    var originalExpiry by rememberSaveable {mutableStateOf<String?>(null)}
    var validation by rememberSaveable {mutableStateOf<String?>(null)}
    V2PageHeader("Coach-Gedächtnis",back=back)
    V2RobotNote(robot,"Hier siehst du, was du mir dauerhaft mitgeben möchtest. Du entscheidest, was bleibt und was ich beim Coaching verwenden darf.",mood=RobotMood.CURIOUS)
    error?.let {RocoText(it,color=MaterialTheme.colorScheme.error)}
    if(!ready) RocoText("Dein Gedächtnis wird geöffnet …")
    else {
        V2Card {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {RocoText("Beim Coaching berücksichtigen",style=MaterialTheme.typography.titleMedium);RocoText("Zusätzlich ist die Gesundheitsfreigabe unter KI-Coach erforderlich.",style=MaterialTheme.typography.bodySmall,color=V2Muted)}
                Switch(memory.enabled,{enabled->save(memory.copy(enabled=enabled)) {}},enabled=!saving)
            }
            RocoText("${memory.active(today).size} aktiv · lokal verschlüsselt",color=V2Muted)
        }
        if(editing) V2Card(if(entryId==null) "Neue Erinnerung" else "Erinnerung bearbeiten") {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {MemoryCategory.entries.forEach {c->FilterChip(selected=category==c.name,onClick={category=c.name},enabled=!saving,label={RocoText(c.label)})}}
            OutlinedTextField(value=text,onValueChange={text=it.take(500)},label={RocoText("Was soll dein Coach wissen?")},enabled=!saving,modifier=Modifier.fillMaxWidth(),minLines=3,maxLines=6)
            RocoText("${text.length}/500 · Beschreibe eigene Angaben statt Anweisungen an die KI.",style=MaterialTheme.typography.bodySmall,color=V2Muted)
            RocoText("Gültigkeit",style=MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Dauerhaft",7 to "7 Tage",30 to "30 Tage").forEach {(days,label)->FilterChip(selected=expiry==days,onClick={expiry=days},enabled=!saving,label={RocoText(label)})}
                if(originalExpiry!=null) FilterChip(selected=expiry== -1,onClick={expiry= -1},enabled=!saving,label={RocoText("Bisheriges Ablaufdatum")})
            }
            validation?.let {RocoText(it,color=MaterialTheme.colorScheme.error)}
            ClayButton(enabled=!saving,onClick={
                val next=runCatching {memory.put(CoachMemoryEntry(id=entryId ?: java.util.UUID.randomUUID().toString(),text=text,category=MemoryCategory.valueOf(category),updatedAt=Instant.now(),expiresOn=when {expiry== -1->originalExpiry?.let(LocalDate::parse);expiry==0->null;else->today.plusDays(expiry.toLong())}))}
                next.onSuccess {save(it) {editing=false;validation=null}}.onFailure {validation=it.message}
            }) {RocoText(if(saving) "Wird gespeichert …" else "Erinnerung speichern")}
            TextButton(onClick={editing=false;validation=null},enabled=!saving) {RocoText("Abbrechen")}
        } else ClayButton(enabled=!saving&&memory.entries.size<40,onClick={entryId=null;text="";category=MemoryCategory.CONTEXT.name;expiry=0;originalExpiry=null;validation=null;editing=true}) {V2Icon("add",tint=Color.White);RocoText("Erinnerung hinzufügen")}
        if(memory.entries.isEmpty()&&!editing) V2Card("Du bestimmst, was ich mir merke") {
            RocoText("Zum Beispiel: Ich fahre gern Fahrrad. Oder: Bitte erkläre mir Fachbegriffe. Es wird nichts heimlich aus deinen Chats übernommen.",color=V2Muted)
        }
        memory.entries.sortedByDescending {it.updatedAt}.forEach {entry ->
            val expired=entry.expiresOn?.let {it<today}==true
            V2Card(entry.category.label,if(expired) "Abgelaufen · wird nicht verwendet" else entry.expiresOn?.let {"Gültig bis "+it.format(DateTimeFormatter.ofPattern("d. MMM yyyy"))} ?: "Dauerhaft") {
                RocoVerbatimText(entry.text,style=MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    TextButton(enabled=!saving,onClick={entryId=entry.id;text=entry.text;category=entry.category.name;originalExpiry=entry.expiresOn?.toString();expiry=if(originalExpiry==null) 0 else -1;validation=null;editing=true}) {RocoText("Bearbeiten")}
                    TextButton(enabled=!saving,onClick={save(memory.copy(entries=memory.entries.filterNot {it.id==entry.id})) {if(entryId==entry.id) editing=false}}) {RocoText("Entfernen")}
                }
            }
        }
    }
}
