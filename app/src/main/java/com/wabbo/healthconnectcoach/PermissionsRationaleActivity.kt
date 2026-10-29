package com.wabbo.healthconnectcoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class PermissionsRationaleActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(RocoLocales.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        RocoText("Warum Health-Connect-Zugriff?", style = MaterialTheme.typography.headlineSmall)
                        RocoText(
                            "ROCO fragt nur das Leserecht für die gewählte Funktion an: Schlafsitzungen, HRV RMSSD, " +
                                "Ruhepuls oder Übungssitzungen. Daten werden nur nach Ihrer Aktion im sichtbaren Vordergrund " +
                                "für höchstens 30 Tage gelesen und nicht an einen Server gesendet."
                        )
                        RocoText(
                            "Die App schreibt nichts in Health Connect und liest nicht im Hintergrund. Sie können den App-Zugriff " +
                                "hier trennen oder Health-Connect-Rechte in den Android-Einstellungen jederzeit widerrufen. " +
                                "Lokale App-Daten zu löschen entfernt keine Quelldaten aus Health Connect."
                        )
                        Button(onClick = { finish() }) {
                            RocoText("Schließen")
                        }
                    }
                }
            }
        }
    }
}
