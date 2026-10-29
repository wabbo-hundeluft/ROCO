package com.wabbo.healthconnectcoach

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class SessionDraft(val date: LocalDate,val title: String,val minutes: Int,val intensity: Double)

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun V2SessionEditor(initial: PlannedSession?, defaultMinutes: Int, dismiss: ()->Unit,saving: Boolean=false,error: String?=null,defaultIntensity: Double=1.5,delete: (() -> Unit)?=null, save: (SessionDraft)->Unit) {
    var title by rememberSaveable(initial?.id) {mutableStateOf(initial?.title ?: "Meine Trainingseinheit")}
    var minutes by rememberSaveable(initial?.id) {mutableStateOf((initial?.minutes ?: defaultMinutes).toString())}
    var dateText by rememberSaveable(initial?.id) {mutableStateOf((initial?.date ?: LocalDate.now()).toString())}
    val date=LocalDate.parse(dateText)
    var intensity by rememberSaveable(initial?.id) {mutableDoubleStateOf(initial?.intensity ?: defaultIntensity)}
    var deleting by rememberSaveable {mutableStateOf(false)}
    var picking by rememberSaveable {mutableStateOf(false)}
    V2DetailSheet(onDismissRequest={if(!saving) dismiss()},startExpanded=true,title={RocoText(if(initial==null) "Training planen" else "Einheit bearbeiten")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            OutlinedTextField(title,{title=it.take(100)},label={RocoText("Name der Einheit")},singleLine=true,enabled=!saving)
            OutlinedTextField(minutes,{minutes=it.filter(Char::isDigit).take(3)},label={RocoText("Minuten")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,enabled=!saving)
            OutlinedButton(onClick={picking=true},enabled=!saving,modifier=Modifier.fillMaxWidth()) {V2Icon("date");RocoText(date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM",Locale.getDefault())))}
            RocoText("Intensität dieser Einheit",style=MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf(1.0,1.5,2.0).forEach {value->
                FilterChip(selected=intensity==value,onClick={intensity=value},enabled=!saving,label={RocoText(intensityLabel(value))},modifier=Modifier.heightIn(min=48.dp))
            }}
            error?.let {RocoText(it,color=MaterialTheme.colorScheme.error)}
            if(delete!=null) TextButton(onClick={deleting=true},enabled=!saving) {RocoText("Einheit aus dem Plan entfernen",color=MaterialTheme.colorScheme.error)}
            RocoText("Dein Wochenziel bleibt bestehen. Wenn du den Zielumfang ändern möchtest, passe deine Ziele an.",style=MaterialTheme.typography.bodySmall,color=V2Muted)
        }
    },confirmButton={ClayButton(enabled=!saving&&title.isNotBlank()&&(minutes.toIntOrNull() ?: 0) in 5..180&&date>=LocalDate.now(),onClick={save(SessionDraft(date,title.trim(),minutes.toInt(),intensity))}) {RocoText(if(saving) "Wird gespeichert …" else "Speichern")}},dismissButton={ClayButton(onClick=dismiss,enabled=!saving,secondary=true) {RocoText("Abbrechen")}})
    if(deleting&&delete!=null) AlertDialog(onDismissRequest={if(!saving) deleting=false},title={RocoText("Einheit entfernen?")},text={RocoText("${initial?.title ?: title} wird aus dem Plan entfernt. Uhr-Aufzeichnungen bleiben erhalten. Du kannst die Planänderung anschließend rückgängig machen.")},confirmButton={TextButton(enabled=!saving,onClick={deleting=false;delete()}) {RocoText("Entfernen")}},dismissButton={TextButton(enabled=!saving,onClick={deleting=false}) {RocoText("Behalten")}})
    if(picking) {
        val state=rememberDatePickerState(initialSelectedDateMillis=date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),selectableDates=object:SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean=Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() in LocalDate.now()..LocalDate.now().plusDays(90)
        })
        DatePickerDialog(onDismissRequest={picking=false},confirmButton={TextButton(onClick={state.selectedDateMillis?.let {dateText=Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()};picking=false}) {RocoText("Übernehmen")}},dismissButton={TextButton(onClick={picking=false}) {RocoText("Abbrechen")}}) {DatePicker(state)}
    }
}
