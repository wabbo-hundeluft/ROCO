package com.wabbo.healthconnectcoach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun PrimarySourceBindingsSettingsCard(
    bindings: Map<String, PrimarySourceBinding>,
    observedOrigins: Map<String, Set<String>>,
    observationsAvailable: Boolean,
    permissionGrantedByRecordType: Map<String, Boolean>,
    permissionStatusKnown: Boolean,
    locallyConnected: Boolean,
    readsPaused: Boolean,
    onRefreshSources: () -> Unit,
    onBindSource: (recordType: String, packageName: String) -> Unit,
    onRemoveBinding: (recordType: String) -> Unit,
) {
    DetailCard(title = "Primärquellen für Scores") {
        RocoText(
            "Je Datentyp wird genau eine Health-Connect-Quelle stabil gebunden. Quellenwechsel erfolgen nur hier ausdrücklich; andere Quellen ersetzen fehlende Werte nicht. Schlaf, HRV, Ruhepuls, Training, Herzfrequenz und Schritte werden lokal verarbeitet.",
            style = MaterialTheme.typography.bodySmall,
        )
        RocoText(
            "Gespeichert werden ausschließlich Record-Typ, Paketname der Quelle und Bindungsstatus – keine Messwerte, Record-IDs oder Rohdaten.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = locallyConnected && !readsPaused,
            onClick = onRefreshSources,
        ) {
            RocoText("Erlaubte Score-Datenquellen jetzt aktualisieren")
        }
        if (readsPaused) {
            RocoText("Lesen ist pausiert; Aktualisieren startet keinen Health-Connect-Read.", style = MaterialTheme.typography.bodySmall)
        }
        ApprovedWellnessRecordTypes.settingsRows.forEach { (recordType, title) ->
            val binding = bindings[recordType]
            val candidates = observedOrigins[recordType].orEmpty().sorted()
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                RocoText(title, style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                RocoText(
                    when {
                        binding != null -> "Gebunden: ${binding.packageName} · ${if (binding.status == PrimarySourceBindingStatus.AUTO_BOUND) "automatisch" else "von Ihnen gewählt"}"
                        !permissionStatusKnown -> "Der aktuelle Leserechtsstatus ist nicht bestätigt; Quelle und Daten dürfen nicht als frisch verifiziert gelten."
                        permissionGrantedByRecordType[recordType] != true -> "Für diesen Typ fehlt ein bestätigtes Leserecht; dieser Typ wird nicht gelesen."
                        candidates.size > 1 -> "Quellenkonflikt: mehrere Origins erkannt; bitte eine auswählen."
                        candidates.size == 1 -> "Erkannte Quelle: ${candidates.single()}"
                        observationsAvailable -> "Im letzten erfolgreichen Fenster keine Quelle mit Records beobachtet."
                        else -> "Noch keine Quelle in einem erfolgreichen Vordergrund-Read beobachtet."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (binding == null && candidates.size > 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (binding != null && (!permissionStatusKnown || permissionGrantedByRecordType[recordType] != true)) {
                    RocoText(
                        if (permissionStatusKnown) "Die Bindung bleibt gespeichert, aber ohne aktuelles Leserecht erfolgt kein Read."
                        else "Die Bindung bleibt gespeichert; der aktuelle Berechtigungsstatus konnte nicht bestätigt werden.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (binding != null && candidates.none { it == binding.packageName }) {
                    RocoText(
                        "Die gebundene Quelle fehlt im letzten Read; es wird nicht still auf eine andere Quelle gewechselt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                candidates.filter { it != binding?.packageName }.forEach { packageName ->
                    TextButton(
                        modifier = Modifier.align(Alignment.Start),
                        onClick = { onBindSource(recordType, packageName) },
                    ) {
                        RocoText("Als Primärquelle wählen: $packageName")
                    }
                }
                if (binding != null) {
                    TextButton(
                        modifier = Modifier.align(Alignment.Start),
                        onClick = { onRemoveBinding(recordType) },
                    ) {
                        RocoText("Bindung für diesen Typ lösen")
                    }
                }
            }
        }
    }
}
