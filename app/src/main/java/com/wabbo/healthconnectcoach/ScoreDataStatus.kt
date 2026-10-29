package com.wabbo.healthconnectcoach

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine as Engine

internal fun scoreReasonLabel(reason: String): String = when {
    reason.startsWith("hrv_baseline_days:") -> "HRV-Vergleich: ${reason.substringAfter(':')} von mindestens 7 früheren Tagen vorhanden."
    reason.startsWith("rhr_baseline_days:") -> "Ruhepuls-Vergleich: ${reason.substringAfter(':')} von mindestens 7 früheren Tagen vorhanden."
    reason.startsWith("duplicate_") -> "Eine Quelle liefert unterschiedliche Messungen unter derselben Datensatz-ID. Bitte prüfe die Datenquelle."
    else -> when(reason) {
        "hrv_daily_value_used" -> "HRV: Tageswert verwendet, weil kein Wert im Schlafintervall liegt."
        "rhr_daily_value_used" -> "Ruhepuls: Tageswert verwendet, weil kein Wert im Schlafintervall liegt."
        "hrv_current_missing" -> "Kein aktueller HRV-Wert der gewählten Quelle für diese Nacht oder den Aufwachtag."
        "rhr_current_missing" -> "Kein aktueller Ruhepuls der gewählten Quelle für diese Nacht oder den Aufwachtag."
        "hrv_source_unresolved", "rhr_source_unresolved" -> "Für diesen Messwert fehlt eine eindeutige Hauptquelle. Bitte im Profil auswählen."
        "HRV_missing" -> "Der HRV-Baustein ist noch nicht berechenbar."
        "RHR_missing" -> "Der Ruhepuls-Baustein ist noch nicht berechenbar."
        "regularity_missing" -> "Für den Schlafrhythmus fehlen noch Vergleichsnächte: mindestens 5 sind erforderlich."
        "main_sleep_missing" -> "Keine abgeschlossene Hauptnacht von mindestens 3 Stunden mit Aufwachdatum am ausgewählten Tag."
        "main_sleep_stale" -> "Die letzte passende Nacht liegt mehr als 24 Stunden zurück."
        "adaptive_history_missing", "adaptive_history_too_short", "adaptive_history_short" -> "Dein Belastungsziel lernt noch. Für die Anpassung werden abgeschlossene, vollständig gelesene Trainingswochen benötigt."
        "adaptive_wear_coverage_missing" -> "Für die automatische Anpassung fehlen ausreichend beobachtete Tage. Keine Trainingsaufzeichnung bedeutet nicht automatisch eine echte Trainingspause."
        "adaptive_history_incomplete" -> "Der frühere Trainingsverlauf ist unvollständig. Fehlende Daten werden nicht als Trainingspause gewertet."
        "adaptive_history_ambiguous" -> "Frühere Trainingsquellen oder Überlappungen sind nicht eindeutig; das automatische Ziel wird daraus nicht erhöht."
        "adaptive_training_baseline_missing" -> "Für ein automatisches Ziel fehlt noch eine regelmäßige Trainingsbasis. Dein bestätigter Plan kann als Startpunkt dienen."
        "adaptive_history_duration_fallback" -> "Ein Teil früherer Trainings nutzt mangels ausreichender Pulsdaten die Dauer als Näherung."
        "adaptive_recovery_hold" -> "HRV und Ruhepuls vor Wochenbeginn lagen gemeinsam ungünstiger als deine persönliche Basis. Das Ziel wurde zurückhaltender angesetzt."
        "adaptive_fatigue_hold" -> "Die kurzfristige Last lag deutlich über der längerfristigen. Das Ziel wurde deshalb nicht weiter angehoben."
        "energy_hrv_baseline_or_current_missing" -> "Für die Körperenergie fehlen eine aktuelle nächtliche HRV oder mindestens sieben persönliche Vergleichstage. Schlafdauer allein ersetzt keine Erholungsmessung."
        "new_day_or_model_pending" -> "Neue Tagesdaten oder aktualisiertes Score-Modell werden geladen. Frühere Werte findest du im Verlauf."
        "sleep_efficiency_missing" -> "Für die Schlaf-Effizienz fehlen vollständige Phasen."
        "source_conflict" -> "Mehrere oder widersprüchliche Messungen: Hauptquellen im Profil prüfen."
        "overlapping_workout_sessions" -> "Überlappende Trainings würden doppelt gezählt. Bitte die Trainingsquelle prüfen."
        "reference_window_incomplete" -> "Für die relative Belastung fehlen vollständige Vergleichswochen. Gib historischen Zugriff im Profil frei: Die ältesten Ruhepuls-Basiswerte können mehr als 30 Tage zurückliegen."
        "Wref_missing_or_zero" -> "In den drei Vorwochen fehlt eine Trainingsbasis. Heutige Trainingszeit ist trotzdem als Messwert verfügbar."
        "sleep_read_incomplete" -> "Die benötigten Daten wurden noch nicht vollständig gelesen."
        "sleep_stages_missing" -> "Keine Schlafphasen geliefert; die Schlafsitzungsdauer dient als Näherung."
        "sleep_stages_partial" -> "Schlafphasen sind nur teilweise vorhanden."
        "max_hr_age_estimated" -> "Die Pulsintensität nutzt einen aus dem Geburtsjahr geschätzten Maximalpuls. Ein gemessener Wert lässt sich im Profil eintragen."
        "pulse_weighting_disabled" -> "Pulsgewichtung im Profil ausgeschaltet. Trainingsdauer dient als Näherung."
        "duration_fallback" -> "Trainingsdauer verwendet, weil Pulsdaten oder die Ruhepuls-Basis nicht ausreichen."
        "insufficient_data" -> "Für den zusammengesetzten Schlafscore braucht es mindestens zwei berechenbare Bausteine."
        "no_session_load" -> "In der aktuellen Trainingswoche wurden keine Einheiten gefunden."
        "weekly_target_missing" -> "Für ein automatisches Wochenziel fehlt noch genügend Trainingsverlauf. Du kannst gemeinsam mit dem Coach einen Startplan festlegen."
        "weekly_exercise_read_incomplete" -> "Die Trainings dieser Kalenderwoche wurden noch nicht vollständig gelesen."
        "overlapping_or_ambiguous_workouts" -> "Überlappende oder nicht eindeutig zugeordnete Trainings würden die Belastung verfälschen. Bitte die Trainingsquelle prüfen."
        "restored_local_score_snapshot" -> "Letzter lokal gespeicherter Stand; die Aktualisierung steht noch aus."
        "steps_workout_overlap" -> "Schritte während Trainings werden nicht zusätzlich vom Akku abgezogen."
        "steps_wake_boundary_overlap" -> "Ein Schrittintervall überschneidet das Aufwachen und wurde nicht vollständig zugeordnet."
        else -> if(reason.endsWith("_permission_missing")) "Ein benötigtes Leserecht fehlt. Bitte Health Connect im Profil prüfen." else "Datenhinweis: ${reason.replace('_',' ')}"
    }
}

@Composable internal fun ScoreDataStatus(score: Engine.ScoreResult?, day: DayInsight?) {
    Surface(color=Color.White,shape=RoundedCornerShape(28.dp),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            RocoText("Deine Datengrundlage",style=MaterialTheme.typography.titleLarge)
            if(score==null) RocoText("Verbinde Health Connect und aktualisiere die Daten, um den Berechnungsstatus zu sehen.")
            else {
                RocoText("${score.coverage.available} von ${score.coverage.expected} Bausteinen oder Zeitfenstern verfügbar",style=MaterialTheme.typography.bodyLarge)
                LinearProgressIndicator(progress={if(score.coverage.expected>0) score.coverage.available.toFloat()/score.coverage.expected else 0f},modifier=Modifier.fillMaxWidth(),color=Color(0xFF14805C))
                score.coverage.missingReasons.distinct().forEach { RocoText(scoreReasonLabel(it),style=MaterialTheme.typography.bodyMedium) }
                if(score.coverage.missingReasons.isEmpty()) RocoText("Alle benötigten Bausteine für diese Berechnung sind vorhanden.")
                day?.metrics?.let { metrics ->
                    val seen=listOf("read_sleep_count" to "Schlafsitzungen", "read_hrv_count" to "HRV-Werte", "read_rhr_count" to "Ruhepulswerte")
                    seen.forEach { (key,label) -> metrics[key]?.let { RocoText("$label gelesen: ${it.toInt()}",style=MaterialTheme.typography.bodySmall) } }
                }
            }
        }
    }
}
