# ROCO — dein Gesundheitsbegleiter auf Android

Ein persönlicher Roboter hilft dir, **Körperenergie, Schlaf, HRV und Wochenbelastung** zu verstehen. ROCO verbindet visuelle Auswertungen mit einem Trainingsplan, einem Gewohnheiten-Logbuch und optionalem KI-Coaching.

[English README](README.md)

## APK herunterladen

Öffne **Releases** in diesem Repository und lade **ROCO-2.0.20.apk** herunter. Die Vorschau unterstützt Android 9 / API 28 oder neuer; verfügbare Gesundheitsdaten hängen von Gerät, Apps und Datenverbindung ab.

Die App heißt **ROCO**. Ihr aktuelles Paket lautet `com.wabbos.healthcoach.v2preview`; die APK ist mit einem Entwicklungszertifikat signiert. Ein Update über dieselbe Installation erhält lokale Daten. APKs mit anderem Paket oder anderer Signatur können sie nicht direkt aktualisieren. Die Prüfsumme steht in `SHA256SUMS.txt` bei der Veröffentlichung.

## Funktionen

- Dynamische Körperenergie mit Akkudarstellung und Tagesverlauf.
- Adaptive Wochenbelastung: 100 Punkte entsprechen deinem persönlichen Wochenziel. Einzelne Trainings zeigen ihren Beitrag.
- Schlafdetails, Schlafphasen, persönliche Vergleiche und Schlafplaner für die kommende Nacht.
- Gemeinsamer Vitalwerte-Check mit persönlichen Vergleichsbereichen.
- Eigene Trainingsziele, Planung und erledigte Einheiten.
- Logbuch mit vorgefertigten und eigenen Fragen, Check-in-Streak und lokalen Gewohnheiten-Mustern.
- Frühere Tage und interaktive Verläufe über 7, 30 und 90 Tage.
- Ein benennbarer Roboter mit programmierten, situationsbezogenen Animationen, morgendlichem Ausfahren und Schlafmodus.
- Neuer Chat mit großer Begrüßung und Möglichkeit, den vorherigen Chat fortzusetzen.

## Sprache und KI

Unter **Profil → Sprache → App-Sprache** wählst du Systemsprache, Deutsch, English, Français oder Español. Nicht unterstützte Systemsprachen verwenden Englisch.

KI ist optional. Trage eigene API-Keys für **Groq, Mistral, OpenRouter und Gemini** ein und wähle ein Modell. Bei Fehlern versucht ROCO konfigurierte Ersatzanbieter in dieser Reihenfolge. Jede Folgeanfrage erhält einen kurzen lokalen Rückblick auf den letzten Austausch. Kostenlose Kontingente und Modellzugang hängen vom jeweiligen Anbieter ab.

## Datenverbindung

ROCO bevorzugt eine eingerichtete Google-Health-Verbindung und nutzt Health Connect als Ersatz. Für den direkten Google-Zugang ist eine passende Google-Cloud-/OAuth-Konfiguration nötig; nicht jedes Konto hat automatisch Zugriff. Health Connect benötigt kompatible Software und tatsächlich importierte Daten deiner Uhr oder Apps.

Scores, Logbuch, Pläne, Chat und API-Einstellungen werden lokal authentifiziert verschlüsselt gespeichert. Für Google-Import und optionale KI-Anfragen wird eine Netzwerkverbindung verwendet. Den persönlichen Datenzugriff des Coachs steuerst du in den Einstellungen.

Die Scores sind eigene Wellness-Schätzungen und keine medizinischen Messungen oder Diagnosen. Fehlende Daten können einzelne Scores verhindern. Muster im Logbuch belegen keine Ursache. ROCO übernimmt keine proprietären WHOOP- oder Garmin-Algorithmen.

## Rückmeldungen

Dies ist eine frühe Vorschau. Nenne bei Fehlerberichten App-Version, Android-Version, Datenquelle und die Schritte zum Nachstellen. Poste keine API-Keys oder persönlichen Gesundheitsdaten in öffentlichen Issues.
