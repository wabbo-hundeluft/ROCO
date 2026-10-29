package com.wabbo.healthconnectcoach

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable internal fun V2DayPicker(selected: LocalDate,today: LocalDate,days: List<DayInsight>,dismiss: ()->Unit,select: (LocalDate)->Unit) {
    val stored=days.map {it.date}.toSet()
    val dates=((0L..89L).map {today.minusDays(it)}+stored.filter {it<=today}).distinct().sortedDescending()
    V2DetailSheet(dismiss,title={RocoText("Deine Tage")},text={
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            item {RocoText("Öffne einen gespeicherten Tagesstand. Fehlende Daten werden nicht durch heutige Werte ersetzt.",fontSize=12.sp,color=V2Muted)}
            items(dates.size) {index->val date=dates[index]
                OutlinedButton(onClick={select(date);dismiss()},modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        RocoText(when(date) {today->"Heute";today.minusDays(1)->"Gestern";else->date.format(DateTimeFormatter.ofPattern("EEEE, d. MMM",Locale.getDefault()))})
                        RocoText(if(date in stored) "Gespeicherter Tagesstand" else "Noch kein gespeicherter Stand",fontSize=11.sp,color=V2Muted)
                    }
                    if(date==selected) V2Icon("check") else V2Icon("next")
                }
            }
        }
    },confirmButton={ClayButton(onClick=dismiss,secondary=true) {RocoText("Schließen")}})
}
