package com.wabbo.healthconnectcoach

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wabbo.healthconnectcoach.score.PersonalScoreProfile
import java.time.LocalDate

@Composable internal fun PersonalScoreProfileEditor(initial: PersonalScoreProfile, saving: Boolean = false,
    label: String = "Angaben speichern", save: (PersonalScoreProfile)->Unit) {
    var year by rememberSaveable(initial) {mutableStateOf(initial.birthYear?.toString().orEmpty())}
    var maximum by rememberSaveable(initial) {mutableStateOf(initial.measuredMaxHeartRate?.toString().orEmpty())}
    var limited by rememberSaveable(initial) {mutableStateOf(initial.pulseLimited)}
    var error by rememberSaveable {mutableStateOf<String?>(null)}
    Column(verticalArrangement=Arrangement.spacedBy(18.dp)) {
        RocoText("Dein Körper. Deine Basis.", style=MaterialTheme.typography.headlineSmall)
        RocoText("Alle Angaben sind freiwillig und werden lokal verschlüsselt gespeichert. HRV und Ruhepuls vergleichen wir mit deinem eigenen Verlauf.", color=V2Muted)
        V2LabeledInput(year,{year=it.filter(Char::isDigit).take(4)},"Geburtsjahr · optional",!saving,KeyboardOptions(keyboardType=KeyboardType.Number))
        RocoText("Das ungefähre Alter unterstützt die Schätzung deiner Pulsintensität. Jugendliche erhalten eine Schlaf-Untergrenze von 8 Stunden, Erwachsene von 7 Stunden. Dein höheres Schlafziel bleibt erhalten.",style=MaterialTheme.typography.bodySmall,color=V2Muted)
        V2LabeledInput(maximum,{maximum=it.filter(Char::isDigit).take(3)},"Gemessener Maximalpuls in bpm · optional",!saving,KeyboardOptions(keyboardType=KeyboardType.Number))
        RocoText("Nur einen bereits zuverlässig gemessenen Wert eintragen, keinen Maximaltest für die App durchführen. Ohne diesen Wert nutzen wir bei Erwachsenen 208 − 0,7 × Alter als grobe Schätzung. Ohne Alter bleibt die bisherige Pulsabstand-Näherung erhalten.",style=MaterialTheme.typography.bodySmall,color=V2Muted)
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Switch(checked=limited,onCheckedChange={limited=it},enabled=!saving)
            RocoText("Pulsgewichtung ausschalten, z. B. bei Medikamenten, die meinen Puls verändern. Dann zählt Trainingsdauer als Näherung.",Modifier.weight(1f))
        }
        error?.let {RocoText(it,color=MaterialTheme.colorScheme.error)}
        ClayButton(enabled=!saving,onClick={
            val result=runCatching {PersonalScoreProfile(year.takeIf {it.isNotBlank()}?.toInt(),maximum.takeIf {it.isNotBlank()}?.toInt(),limited).validated(LocalDate.now())}
            result.onSuccess {error=null;save(it)}.onFailure {error=it.message ?: "Bitte Angaben prüfen."}
        }) {RocoText(if(saving) "Wird gespeichert …" else label)}
    }
}
