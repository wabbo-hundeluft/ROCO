package com.wabbo.healthconnectcoach

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable internal fun V2PlanProposal(plan: TrainingPlan,enabled: Boolean,alreadyPlanned: Boolean,accept: ()->Unit,discard: ()->Unit) {
    HorizontalDivider()
    RocoText("Dein Planvorschlag",style=MaterialTheme.typography.titleLarge)
    RocoText("Noch nicht als Trainingsplan gespeichert",style=MaterialTheme.typography.bodySmall,color=V2Muted)
    RocoText(plan.goals.days.sorted().joinToString(" · ") {DayOfWeek.of(it).getDisplayName(TextStyle.FULL,Locale.getDefault())},fontWeight=FontWeight.Bold)
    RocoText("${plan.goals.sessionMinutes} Minuten je Einheit · ${intensityLabel(plan.goals.intensity)}")
    RocoText("Wochenbelastung: Ziel 100 Punkte · Schlafziel ${durationLabel(plan.goals.sleepMinutes.toDouble())}")
    RocoText("100 Belastungspunkte sind dein persönliches Wochenziel. Es lernt später aus deiner Trainingshistorie; der Plan dient zunächst als Ausgangspunkt.",style=MaterialTheme.typography.bodySmall,color=V2Muted)
    if(alreadyPlanned) RocoText("Du hast inzwischen einen bestätigten Plan. Bitte passe ihn im Coach oder unter Training an; dieser Vorschlag überschreibt ihn nicht.",color=V2Muted)
    else ClayButton(onClick=accept,enabled=enabled) {RocoText("Diesen Plan übernehmen")}
    TextButton(onClick=discard,enabled=enabled,modifier=Modifier.fillMaxWidth()) {RocoText("Vorschlag verwerfen")}
}
