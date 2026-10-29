package com.wabbo.healthconnectcoach

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine as Engine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

private val LocalRobotName = staticCompositionLocalOf { "Pip" }

private val Ink = Color(0xFF171A18)
private val Violet = Color(0xFF427AC5)
private val Teal = Color(0xFF14805C)
private val Coral = Color(0xFFB16B22)
private data class ProductScore(val id: String, val name: String, val icon: String, val color: Color, val question: String)
private val productScores = listOf(
    ProductScore(Engine.BODY_BATTERY_ID, "Körperakku", "battery_charging_full", Teal, "Wie viel Energie ist heute drin?"),
    ProductScore(Engine.SESSION_LOAD_ID, "Belastung", "fitness_center", Coral, "Wie viel hast du trainiert?"),
    ProductScore(Engine.SLEEP_ID, "Schlaf", "bedtime", Violet, "Wie erholsam war deine Nacht?"),
)

/** Independent product shell. Legacy BlockD layouts are not used on this navigation path. */
@Composable
internal fun LegacyProductExperience(
    scores: Map<String, Engine.ScoreResult>, days: List<DayInsight>, refreshing: Boolean,
    status: String, connected: Boolean, paused: Boolean, message: String?,
    onRefresh: () -> Unit, onConnect: () -> Unit, onHistory: () -> Unit,
    onPause: (Boolean) -> Unit, onDisconnect: () -> Unit, onDelete: () -> Unit,
    sourceBindings: Map<String, PrimarySourceBinding>, observedSources: Map<String, Set<String>>,
    onBind: (String, String) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf("Heute") }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val context = LocalContext.current
    val robotPrefs = remember { context.getSharedPreferences("robot_companion", Context.MODE_PRIVATE) }
    var robotName by remember { mutableStateOf(robotPrefs.getString("name", "Pip") ?: "Pip") }
    var userName by remember { mutableStateOf(robotPrefs.getString("user_name", "").orEmpty()) }
    var naming by remember { mutableStateOf(robotPrefs.getInt("onboarding_version", 0) < 2) }
    var nameDraft by remember { mutableStateOf(robotName) }
    val aiStore = remember { AiConfigurationStore(SecureLocalDocument(context, "ai_configuration")) }
    var ai by remember { mutableStateOf(AiConfiguration()) }
    var aiReady by remember { mutableStateOf(false) }
    var aiStorageError by remember { mutableStateOf<String?>(null) }
    var aiSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val conversation = remember { CoachConversationState() }
    LaunchedEffect(aiStore) {
        try { ai = withContext(Dispatchers.IO) { aiStore.read() }; aiReady = true }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { aiStorageError = "KI-Einstellungen konnten nicht geöffnet werden. Gespeicherte Schlüssel wurden nicht überschrieben." }
    }
    val day = days.lastOrNull { it.date.toString() == selectedDate }
    val selectedScores = if (selectedDate == LocalDate.now().toString()) scores else day?.scores.orEmpty()
    BackHandler(detail != null || tab != "Heute") { if (detail != null) detail = null else tab = "Heute" }
    if (naming) {
        CompanionOnboarding(userName, robotName, connected, onComplete = { person, robot, connect ->
            userName = person; robotName = robot
            robotPrefs.edit().putString("name", robot).putString("user_name", person)
                .putBoolean("welcomed", true).putInt("onboarding_version", 2).apply()
            naming = false
            if (connect) onConnect()
        })
        return
    }
    CompositionLocalProvider(LocalRobotName provides robotName) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                listOf("Heute" to "home", "Verlauf" to "explore", "Coach" to "smart_toy", "Profil" to "settings").forEach { (label, icon) ->
                    NavigationBarItem(selected = tab == label, onClick = { tab = label; detail = null },
                        icon = { ProductIcon(icon, if (tab == label) Teal else Ink) }, label = { RocoText(label) })
                }
            }
        }) { insets ->
        if (tab == "Coach" && detail == null) {
            CoachChatScreen(robotName, userName, ai, day, conversation, scope, Modifier.fillMaxSize().padding(insets).imePadding()) { tab = "Profil" }
        } else {
        Column(Modifier.fillMaxSize().padding(insets).imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            when {
                detail != null -> {
                    val model = productScores.first { it.id == detail }
                    TextButton(onClick = { detail = null }) { RocoText("‹  Zurück") }
                    ScoreDeepDive(model, selectedScores[model.id], day, days, { selectedDate = it.toString() })
                    AiConversation(ai, day, true, "Erkläre meinen ${model.name} anhand der verfügbaren Werte.", onConfigure = { detail = null; tab = "Profil" })
                }
                tab == "Heute" -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            RocoText(if (userName.isBlank()) "Hallo!" else "Hallo, $userName!", style = MaterialTheme.typography.headlineLarge)
                        }
                        TextButton(onClick = onRefresh, enabled = !refreshing) { RocoText(if (refreshing) "Lädt …" else "↻") }
                    }
                    DateStrip(selectedDate, days, { selectedDate = it })
                    ScoreOverview(selectedScores) { detail = it }
                    if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Teal)
                    RocoText(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!connected) ProductPanel("Deine Pixel Watch verbinden", "Einmal Health Connect freigeben. Danach aktualisieren sich deine Werte beim Öffnen.") {
                        ClayButton(onClick = onConnect) { RocoText("Gesundheitsdaten verbinden") }
                    }
                    BuddyGuidance(selectedScores, refreshing)
                    ProductPanel("Dein Energieverlauf") {
                        HistoryChart(productScores.first(), days) { selectedDate = it.toString(); detail = Engine.BODY_BATTERY_ID }
                    }
                    WeekJourney()
                    ProductPanel("Dein Tag in Zahlen", "Tippe oben auf einen Score, um die Zusammenhänge zu verstehen.") {
                        MetricTiles(day)
                    }
                    if (selectedScores.values.any { it.status == Engine.Status.source_conflict }) ProductPanel("Welche Quelle soll zählen?", "Mehrere Apps liefern widersprüchliche Werte. Wähle im Profil pro Datentyp deine Hauptquelle.") {
                        TextButton(onClick = { tab = "Profil" }) { RocoText("Datenquellen auswählen →") }
                    }
                }
                tab == "Verlauf" -> {
                    RocoText("Dein Fortschritt", style = MaterialTheme.typography.headlineLarge)
                    RocoText("Muster erkennen. Deinen Rhythmus finden.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    productScores.forEach { model -> ProductPanel(model.name, model.question) {
                        HistoryChart(model, days) { selectedDate = it.toString(); detail = model.id }
                    } }
                    ProductPanel("Auch vor deinem ersten App-Tag", "Mit historischem Zugriff berechnen wir vergangene Tage aus vorhandenen Messungen. Fehlende Daten bleiben als Lücken sichtbar.") {
                        ClayButton(onClick = onHistory) { RocoText("Historische Daten freigeben") }
                        ClayButton(secondary = true, onClick = onRefresh, enabled = !refreshing) { RocoText("Verlauf aktualisieren") }
                    }
                }
                tab == "Coach" -> {
                    RocoText("Dein Coach", style = MaterialTheme.typography.headlineLarge)
                    BuddyGuidance(selectedScores, refreshing = false)
                    AiConversation(ai, day, false, "", onConfigure = { tab = "Profil" })
                    ProductPanel("Gesundheit verstehen", "Kurze Erklärungen, jederzeit und ohne KI verfügbar.") {
                        productScores.forEach { model -> TextButton(onClick = { detail = model.id }) { RocoText("${model.name}: ${model.question}") } }
                    }
                }
                else -> {
                    RocoText("Dein Profil", style = MaterialTheme.typography.headlineLarge)
                    ProductPanel("Dein Begleiter · $robotName") {
                        RobotCompanion(robotName, false, Modifier.size(100.dp))
                        RocoText("Dein Name: ${userName.ifBlank { "Noch offen" }}", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { naming = true }) { RocoText("Namen bearbeiten") }
                    }
                    ProductPanel("Pixel Watch & Health Connect", if (connected) "Verbunden · Daten bleiben auch offline sichtbar" else "Noch nicht verbunden") {
                        ClayButton(onClick = onConnect) { RocoText(if (connected) "Berechtigungen verwalten" else "Verbinden") }
                        TextButton(onClick = onHistory) { RocoText("Historische Daten freigeben") }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RocoText("Aktualisierung pausieren", Modifier.weight(1f)); Switch(checked = paused, onCheckedChange = onPause)
                        }
                        if (connected) TextButton(onClick = onDisconnect) { RocoText("Verbindung trennen") }
                        message?.let { RocoText(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    ProductPanel("Deine Datenquellen", "Bei mehreren Apps pro Datentyp eine Hauptquelle wählen. Verschiedene Datentypen dürfen aus verschiedenen Apps kommen.") {
                        val names = ApprovedWellnessRecordTypes.settingsRows.toMap() + mapOf("OxygenSaturationRecord" to "Sauerstoffsättigung", "RespiratoryRateRecord" to "Atemfrequenz", "SkinTemperatureRecord" to "Hauttemperatur", "ActiveCaloriesBurnedRecord" to "Aktive Energie", "DistanceRecord" to "Strecke", "FloorsClimbedRecord" to "Etagen", "Vo2MaxRecord" to "VO₂max")
                        if (observedSources.isEmpty()) RocoText("Nach der ersten Synchronisation erscheinen deine Quellen hier.")
                        observedSources.forEach { (type, origins) ->
                            RocoText(names[type] ?: type, fontWeight = FontWeight.SemiBold)
                            origins.forEach { origin ->
                                ClayButton(secondary = true, onClick = { onBind(type, origin) }, modifier = Modifier.fillMaxWidth()) {
                                    RocoText((if (sourceBindings[type]?.packageName == origin) "✓  " else "") + origin)
                                }
                            }
                        }
                    }
                    AiSettings(ai, aiReady, aiStorageError, aiSaving) { updated ->
                        scope.launch {
                            aiSaving = true; aiStorageError = null
                            try { withContext(Dispatchers.IO) { aiStore.save(updated) }; ai = updated; aiStorageError = "Gespeichert" }
                            catch (e: CancellationException) { throw e }
                            catch (_: Exception) { aiStorageError = "Speichern fehlgeschlagen. Bitte erneut versuchen." }
                            finally { aiSaving = false }
                        }
                    }
                    GoogleHealthConnectionCard()
                    DiagnosticsPanel()
                    ProductPanel("Deine Daten, deine Entscheidung", "Scores, Tagesübersichten und API-Keys werden verschlüsselt auf diesem Gerät gespeichert. KI erhält nur bei einer Anfrage die freigegebene Zusammenfassung.") {
                        TextButton(onClick = onDelete) { RocoText("Lokale App-Daten löschen", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
    }
}

@Composable private fun ProductIcon(name: String, color: Color) {
    RocoText(materialSymbolGlyph(name), fontFamily = FontFamily(Font(R.font.material_symbols_outlined)), fontSize = 24.sp,
        lineHeight = 24.sp, color = color, modifier = Modifier.size(26.dp))
}
@Composable private fun ProductPanel(title: String, subtitle: String = "", content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            RocoText(title, style = MaterialTheme.typography.titleLarge)
            if (subtitle.isNotEmpty()) RocoText(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}
@Composable private fun DateStrip(selected: String, days: List<DayInsight>, select: (String) -> Unit) {
    val dates = (days.map { it.date } + LocalDate.now()).distinct().sortedDescending()
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        dates.forEach { date -> FilterChip(selected = date.toString() == selected, onClick = { select(date.toString()) },
            label = { RocoText(if (date == LocalDate.now()) "Heute" else date.format(DateTimeFormatter.ofPattern("d. MMM", Locale.getDefault()))) }) }
    }
}
private fun visibleValue(model: ProductScore, score: Engine.ScoreResult?): String = if (model.id == Engine.SESSION_LOAD_ID)
    score?.rawIndex?.let { "${it.roundToInt()}%" } ?: "–" else score?.score?.toString() ?: "–"

@Composable private fun ScoreOverview(scores: Map<String, Engine.ScoreResult>, open: (String) -> Unit) {
    Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            val battery = productScores.first()
            Row(Modifier.fillMaxWidth().clickable { open(battery.id) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                ScoreDial(scores[battery.id]?.score?.div(100f), visibleValue(battery, scores[battery.id]), battery.color, Modifier.size(116.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RocoText("Körperakku", style = MaterialTheme.typography.titleLarge)
                    RocoText("Deine Energie heute", style = MaterialTheme.typography.bodyMedium)
                    RocoText("Entdecken →", style = MaterialTheme.typography.labelLarge, color = battery.color)
                }
            }
            HorizontalDivider(color = Color(0xFFEBEEE9))
            val spacious = LocalDensity.current.fontScale > 1.3f
            if (spacious) Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                productScores.drop(1).forEach { OverviewNumber(it, scores[it.id], Modifier.fillMaxWidth(), open) }
            } else Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                productScores.drop(1).forEach { OverviewNumber(it, scores[it.id], Modifier.weight(1f), open) }
            }
        }
    }
}
@Composable private fun OverviewNumber(model: ProductScore, score: Engine.ScoreResult?, modifier: Modifier, open: (String) -> Unit) {
    Column(modifier.clickable { open(model.id) }.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductIcon(model.icon, model.color); RocoText(model.name, style = MaterialTheme.typography.titleMedium)
        }
        RocoText(visibleValue(model, score), fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
        LinearProgressIndicator(progress = { ((if (model.id == Engine.SESSION_LOAD_ID) score?.rawIndex?.div(200) else score?.score?.div(100.0)) ?: 0.0).toFloat().coerceIn(0f, 1f) },
            color = model.color, trackColor = model.color.copy(alpha = .10f), modifier = Modifier.fillMaxWidth().height(7.dp))
        RocoText(if (model.id == Engine.SESSION_LOAD_ID) "deiner Trainingsbasis" else "von 100 · Details →", style = MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun ScoreTile(model: ProductScore, score: Engine.ScoreResult?, modifier: Modifier, open: (String) -> Unit) {
    Surface(modifier.clickable { open(model.id) }.semantics { contentDescription = RocoStrings.translate("${model.name}: ${visibleValue(model, score)}. Details öffnen") },
        shape = RoundedCornerShape(24.dp), color = model.color.copy(alpha = .09f)) {
        Column(Modifier.padding(horizontal = 7.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
            ScoreDial(if (model.id == Engine.SESSION_LOAD_ID) score?.rawIndex?.toFloat()?.div(200) else score?.score?.div(100f), visibleValue(model, score), model.color, Modifier.size(78.dp))
            RocoText(model.name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
            RocoText(if (model.id == Engine.SESSION_LOAD_ID) "deiner Basis" else "von 100", style = MaterialTheme.typography.labelSmall, color = model.color)
        }
    }
}
@Composable private fun ScoreDial(progress: Float?, label: String, color: Color, modifier: Modifier) {
    val animated by animateFloatAsState((progress ?: 0f).coerceIn(0f, 1f), label = "Score")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(5.dp)) {
            drawArc(color.copy(alpha = .12f), -90f, 360f, false, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
            if (progress != null) drawArc(color, -90f, 360f * animated, false, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
        }
        RocoText(label, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold, color = color)
    }
}

@Composable private fun BuddyGuidance(scores: Map<String, Engine.ScoreResult>, refreshing: Boolean) {
    var tapped by remember { mutableStateOf(false) }
    LaunchedEffect(tapped) { if (tapped) { delay(1600); tapped = false } }
    val battery = scores[Engine.BODY_BATTERY_ID]?.score
    val title = when { refreshing -> "Ich sortiere deine Werte …"; tapped -> "Wir gehen das gemeinsam an."; battery == null -> "Lernen wir deinen Rhythmus kennen."; battery < 40 -> "Auch Erholung bringt dich weiter."; else -> "Kleine Schritte. Gute Gewohnheiten." }
    Surface(color = Color(0xFFFFEBC2), shape = RoundedCornerShape(28.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            RobotCompanion(LocalRobotName.current, tapped || refreshing, Modifier.size(112.dp)) { tapped = true }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RocoText(title, style = MaterialTheme.typography.titleMedium, color = Ink)
                RocoText(if (battery != null && battery < 40) "Plane heute bewusst Pausen ein. Dein Wochenziel darf auch einen Erholungstag enthalten." else "Deine Werte helfen bei der Orientierung. Wie du dich fühlst, zählt genauso.", style = MaterialTheme.typography.bodyMedium, color = Ink)
            }
        }
    }
}
@Composable private fun WeekJourney() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("weekly_journey", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val today = LocalDate.now(); val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    var completed by remember { mutableStateOf(prefs.getStringSet("days", emptySet()).orEmpty().toSet()) }
    var goal by remember { mutableIntStateOf(prefs.getInt("goal", 3).coerceIn(2, 7)) }
    var error by remember { mutableStateOf<String?>(null) }
    val count = (0..6).count { monday.plusDays(it.toLong()).toString() in completed }
    ProductPanel("Deine Woche · $count von $goal Tagen", "Bewusste Bewegung oder geplante Erholung. Du entscheidest, was heute passt.") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (0..6).forEach { offset -> val date = monday.plusDays(offset.toLong()); val done = date.toString() in completed
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RocoText(date.format(DateTimeFormatter.ofPattern("EE",Locale.getDefault())), style=MaterialTheme.typography.labelSmall)
                    Surface(shape=RoundedCornerShape(10.dp),color=if(done) Teal else Color(0xFFE9EFEC),modifier=Modifier.size(30.dp)) {
                        Box(contentAlignment=Alignment.Center) { RocoText(if(done) "✓" else "·",color=if(done) Color.White else Ink) }
                    }
                }
            }
        }
        if (count >= goal) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RobotCompanion(LocalRobotName.current, true, Modifier.size(64.dp))
            RocoText("Wochenziel erreicht! Gut, dass du dir Zeit für dich genommen hast.", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        }
        LinearProgressIndicator(progress = { (count.toFloat()/goal).coerceIn(0f,1f) }, modifier=Modifier.fillMaxWidth(),color=Teal)
        ClayButton(onClick = { scope.launch {
            val next = if(today.toString() in completed) completed - today.toString() else completed + today.toString()
            val retained = next.filter { it >= today.minusDays(90).toString() }.toSet()
            if(withContext(Dispatchers.IO) { prefs.edit().putStringSet("days",retained).commit() }) completed=retained else error="Dein Check-in konnte nicht gespeichert werden."
        } }, modifier=Modifier.fillMaxWidth()) { RocoText(if(today.toString() in completed) "Heute abgehakt ✓ · Rückgängig" else "Mein Tagesziel geschafft") }
        Row(verticalAlignment=Alignment.CenterVertically) {
            RocoText("Wochenziel",Modifier.weight(1f)); TextButton(onClick={ if(goal>2) { goal--; prefs.edit().putInt("goal",goal).apply() } }) { RocoText("−") }
            RocoText("$goal"); TextButton(onClick={ if(goal<7) { goal++; prefs.edit().putInt("goal",goal).apply() } }) { RocoText("+") }
        }
        error?.let { RocoText(it,color=MaterialTheme.colorScheme.error) }
    }
}
@Composable private fun MetricTiles(day: DayInsight?) {
    val entries = listOf("Schritte" to day?.metrics?.get("steps")?.roundToInt()?.toString(), "Training" to day?.metrics?.get("workout_minutes")?.let { "${it.roundToInt()} min" },
        "Ruhepuls" to day?.metrics?.get("rhr")?.let { "${it.roundToInt()} bpm" }, "HRV" to day?.metrics?.get("hrv")?.let { "${it.roundToInt()} ms" })
    val optional = listOf(Triple("spo2", "Sauerstoff", "%"), Triple("respiratory_rate", "Atemfrequenz", "/min"), Triple("skin_delta", "Hauttemperatur Δ", "°C"), Triple("active_kcal", "Aktive Energie", "kcal"), Triple("distance_km", "Strecke", "km"), Triple("floors", "Etagen", ""), Triple("vo2max", "VO₂max", "ml/kg/min"))
        .mapNotNull { (key, name, unit) -> day?.metrics?.get(key)?.let { name to (String.format(Locale.getDefault(), "%.1f", it) + " " + unit) } }
    (entries + optional).chunked(2).forEach { pair -> Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { pair.forEach { (name,value) ->
        Surface(color=MaterialTheme.colorScheme.background,shape=RoundedCornerShape(18.dp),modifier=Modifier.weight(1f)) {
            Column(Modifier.padding(14.dp)) { RocoText(name,style=MaterialTheme.typography.labelMedium); RocoText(value ?: "Noch offen",style=MaterialTheme.typography.titleMedium) }
        }
    } } }
}

@Composable private fun ScoreDeepDive(model: ProductScore, score: Engine.ScoreResult?, day: DayInsight?, days: List<DayInsight>, select: (LocalDate)->Unit) {
    RocoText(model.name,style=MaterialTheme.typography.headlineLarge)
    RocoText(model.question,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(20.dp)) {
        RobotCompanion(LocalRobotName.current, false, Modifier.size(100.dp))
        RocoText("Ich zeige dir, was deine Werte bedeuten. Tippe die Bausteine an, wenn du mehr wissen möchtest.", Modifier.weight(1f), style=MaterialTheme.typography.bodyLarge)
    }
    ProductPanel(day?.date?.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM",Locale.getDefault())) ?: "Heute") {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(20.dp)) {
            ScoreDial(if(model.id==Engine.SESSION_LOAD_ID) score?.rawIndex?.toFloat()?.div(200) else score?.score?.div(100f),visibleValue(model,score),model.color,Modifier.size(116.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                RocoText(scoreState(score),style=MaterialTheme.typography.titleMedium)
                RocoText(if(model.id==Engine.SESSION_LOAD_ID) "100% entspricht deiner üblichen Wochenbelastung." else "Ein Hinweis für deinen Alltag, keine medizinische Messung.",style=MaterialTheme.typography.bodyMedium)
            }
        }
        if(day?.reconstructed==true) RocoText("Aus historischen Messungen berechnet",style=MaterialTheme.typography.labelMedium)
        score?.let { RocoText("Stand: "+it.asOf.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d. MMM · HH:mm",Locale.getDefault())),style=MaterialTheme.typography.bodySmall) }
    }
    if(model.id==Engine.SLEEP_ID) SleepTimeline(day)
    if(model.id==Engine.BODY_BATTERY_ID) EnergyBreakdown(score)
    if(model.id==Engine.SESSION_LOAD_ID) TrainingBreakdown(score, day)
    ProductPanel("Was steckt dahinter?", "Tippe auf einen Baustein für die Erklärung.") {
        val explanations = if(model.id==Engine.SESSION_LOAD_ID) mapOf("W0" to ("Diese Woche" to "Belastung der letzten sieben Tage. Trainingsdauer und Herzfrequenz fließen ein."), "W1" to ("Vorwoche" to "Vergleichsfenster 8–14 Tage vor dem Berechnungszeitpunkt."), "W2" to ("Vor zwei Wochen" to "Vergleichsfenster 15–21 Tage vorher."), "W3" to ("Vor drei Wochen" to "Vergleichsfenster 22–28 Tage vorher.")) else mapOf(
            "D" to ("Schlafdauer" to "Wie gut deine Schlafdauer zum Modell passt. Sie hat das größte Gewicht. Das Modell kennt deinen persönlichen Schlafbedarf noch nicht."),
            "R" to ("Regelmäßigkeit" to "Vergleicht deinen Schlafrhythmus mit vorherigen Nächten. Einzelne Abweichungen sind normal."),
            "H" to ("HRV" to "Die zeitlichen Unterschiede zwischen Herzschlägen, verglichen mit deiner eigenen Basis. Ein einzelner Wert ist keine Diagnose."),
            "P" to ("Ruhepuls" to "Dein Ruhepuls im Vergleich zu deiner eigenen Basis. Schlaf, Belastung und viele weitere Faktoren können ihn verändern."))
        explanations.forEach { (key,pair) -> if (model.id != Engine.BODY_BATTERY_ID || key != "R") ExplanationBar(pair.first,pair.second,score?.components?.get(key),model.color) }
        RocoText(when(model.id) {
            Engine.SLEEP_ID -> "Gewichtung: Dauer 50%, Rhythmus 20%, HRV und Ruhepuls je 15%. Fehlende Bausteine verändern die Gewichtung; mindestens zwei sind erforderlich."
            Engine.BODY_BATTERY_ID -> "Schlafdauer, HRV und Ruhepuls bilden die Startenergie. Bewegung und Training reduzieren den Modellwert; ihr Einfluss nimmt mit der Zeit ab."
            else -> "Die jüngste Trainingswoche wird mit dem Median der drei Vorwochen verglichen. Ein Wert über 100% bedeutet mehr als deine bisherige Basis — nicht automatisch besser."
        },style=MaterialTheme.typography.bodyMedium)
        if(model.id==Engine.SESSION_LOAD_ID) score?.rating0to100?.let { RocoText("Balance-Bewertung: $it / 100 · getrennt von der Belastung",style=MaterialTheme.typography.labelMedium) }
    }
    ProductPanel("Dein Verlauf", "Wähle einen Tag für seine Details.") { HistoryChart(model,days,select) }
    if(model.id!=Engine.SLEEP_ID) ProductPanel("Die Messwerte dazu") { MetricTiles(day) }
    ScoreDataStatus(score, day)
}
private fun scoreState(score: Engine.ScoreResult?):String = when(score?.status) {
    Engine.Status.available -> "Werte verfügbar"
    Engine.Status.provisional -> "Erste Einschätzung"
    Engine.Status.source_conflict -> "Quellen prüfen"
    Engine.Status.permission_missing -> "Freigabe fehlt"
    Engine.Status.stale -> "Letzter gespeicherter Stand"
    else -> "Noch nicht genug Daten"
}
@Composable private fun ExplanationBar(name:String, explanation:String, value:Int?, color:Color) {
    var expanded by rememberSaveable(name) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { expanded=!expanded }.padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row { RocoText(name,Modifier.weight(1f),fontWeight=FontWeight.SemiBold); RocoText(value?.toString() ?: "–",color=color); RocoText(if(expanded) "  −" else "  +") }
        LinearProgressIndicator(progress={ (value ?: 0).coerceIn(0,100)/100f },modifier=Modifier.fillMaxWidth(),color=color)
        if(expanded) RocoText(explanation,style=MaterialTheme.typography.bodyMedium)
    }
}
@Composable private fun SleepTimeline(day:DayInsight?) {
    ProductPanel("Deine Nacht",day?.metrics?.get("sleep_minutes")?.let { "${it.toInt()/60} h ${it.toInt()%60} min Schlaf" } ?: "Schlafphasen erscheinen, sobald deine Uhr sie liefert.") {
        val start=day?.sleepStart; val end=day?.sleepEnd
        val stages=listOf("AWAKE" to "Wach", "REM" to "REM", "LIGHT" to "Leicht", "DEEP" to "Tief")
        stages.forEachIndexed { index,(key,label) ->
            val color=listOf(Coral,Color(0xFFA5C9EE),Violet,Color(0xFF1E4275))[index]
            val bands=day?.sleepBands.orEmpty().filter { it.stage==key }
            RocoText("$label · ${day?.metrics?.get("stage_$key")?.roundToInt()?.let { "$it min" } ?: "keine Phasendaten"}",style=MaterialTheme.typography.labelMedium)
            Canvas(Modifier.fillMaxWidth().height(24.dp).background(color.copy(alpha=.07f),RoundedCornerShape(6.dp))) {
                if(start!=null && end!=null && end>start) {
                    val duration=Duration.between(start,end).toMillis().toFloat()
                    bands.forEach { band ->
                        val x=(Duration.between(start,band.start).toMillis()/duration).coerceIn(0f,1f)*size.width
                        val right=(Duration.between(start,band.end).toMillis()/duration).coerceIn(0f,1f)*size.width
                        drawRoundRect(color,Offset(x,0f),Size((right-x).coerceAtLeast(1f),size.height),androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
                    }
                }
            }
        }
        if(start!=null && end!=null) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            val format=DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
            RocoText(format.format(start),style=MaterialTheme.typography.bodySmall); RocoText(format.format(end),style=MaterialTheme.typography.bodySmall)
        }
        ExplanationBar("Warum wechseln die Phasen?","Leicht-, Tief- und REM-Schlaf wechseln sich nachts ab. Uhren schätzen die Phasen aus Sensorsignalen. Einzelne Nächte und kurze Wachphasen sind normal.",null,Violet)
    }
}
@Composable private fun HistoryChart(model:ProductScore,days:List<DayInsight>,select:(LocalDate)->Unit) {
    var period by rememberSaveable { mutableIntStateOf(7) }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf(7,30).forEach { FilterChip(selected=period==it,onClick={period=it},label={RocoText("$it Tage")}) } }
    val dates=(0 until period).map { LocalDate.now().minusDays((period-1-it).toLong()) }
    val points=dates.map { date -> days.lastOrNull { it.date==date }?.scores?.get(model.id)?.let { if(model.id==Engine.SESSION_LOAD_ID) it.rawIndex?.toFloat() else it.score?.toFloat() } }
    if(points.all { it==null }) { RocoText("Noch kein Verlauf. Verbinde deine Uhr oder importiere historische Daten.",style=MaterialTheme.typography.bodyMedium);return }
    val max=if(model.id==Engine.SESSION_LOAD_ID) maxOf(200f,points.filterNotNull().max()) else 100f
    Canvas(Modifier.fillMaxWidth().height(184.dp).pointerInput(dates,points) { detectTapGestures { point ->
        val i=(point.x/size.width*period).toInt().coerceIn(0,period-1);if(points[i]!=null)select(dates[i])
    } }.semantics { contentDescription=RocoStrings.translate("${model.name}, Verlauf über $period Tage. Einzelwerte folgen unter dem Diagramm.") }) {
        drawLine(model.color.copy(alpha=.15f),Offset(0f,size.height),Offset(size.width,size.height),1.dp.toPx())
        points.forEachIndexed { i,value -> if(value!=null) {
            val slot=size.width/period;val height=value.coerceAtLeast(0f)/max*(size.height-8.dp.toPx())
            drawRoundRect(model.color,Offset(slot*i+slot*.15f,size.height-height.coerceAtLeast(2f)),Size(slot*.7f,height.coerceAtLeast(2f)),androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
        } }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        dates.forEachIndexed { i,date -> if(points[i]!=null) AssistChip(onClick={select(date)},label={RocoText("${date.dayOfMonth}.${date.monthValue}. · ${points[i]?.roundToInt()}${if(model.id==Engine.SESSION_LOAD_ID) "%" else ""}")}) }
    }
}

@Composable internal fun AiSettings(config:AiConfiguration,ready:Boolean,status:String?,saving:Boolean,save:(AiConfiguration)->Unit) {
    var draft by remember(config) { mutableStateOf(config) }
    var editing by remember { mutableStateOf(CoachProvider.entries.all {config.keyFor(it).isBlank()}) }
    LaunchedEffect(config, status) { if (status == "Gespeichert") editing = false }
    LaunchedEffect(ready) { if (ready) editing = CoachProvider.entries.all {config.keyFor(it).isBlank()} }
    V2Section("Dein KI-Coach") {V2Card {
        if (!editing) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                V2Icon("check_circle",tint=Color(0xFF6B9555))
                Column {RocoText("Gespeichert",style=MaterialTheme.typography.titleMedium);RocoText("Eigene Keys · Modelle frei wählbar",style=MaterialTheme.typography.bodySmall,color=Color(0xFF637551))}
            }
            RocoText("Reihenfolge: Groq → Mistral → OpenRouter → Gemini",style=MaterialTheme.typography.bodySmall,color=V2Muted)
            CoachProvider.entries.filter {config.keyFor(it).isNotBlank()}.forEach {p->RocoText("${p.label} · ${config.modelFor(p)}",style=MaterialTheme.typography.bodySmall,color=V2Muted)}
            if(status=="Gespeichert") ClayButton(onClick={},enabled=false,success=true) {RocoText("Gespeichert")}
            ClayButton(onClick={draft=config;editing=true},secondary=true) {V2Icon("edit",size=19.dp);RocoText("Bearbeiten")}
        } else {
            RocoText("Groq zuerst. Nicht eingerichtete Anbieter werden übersprungen. Ein Ersatzaufruf nutzt nur deine konfigurierten Keys.",style=MaterialTheme.typography.bodySmall)
            CoachProvider.entries.forEach {provider->AiProviderFields(provider,draft) {draft=it}}
            Row(verticalAlignment=Alignment.CenterVertically) { RocoText("Coach-Zugriff auf Gesundheits- und Logbuchdaten",Modifier.weight(1f));Switch(draft.shareHealthSummary,{draft=draft.copy(shareHealthSummary=it)}) }
            Row(verticalAlignment=Alignment.CenterVertically) { RocoText("Stündliche Tagesanalyse · manuell aktualisierbar",Modifier.weight(1f));Switch(draft.autoAnalysis,{draft=draft.copy(autoAnalysis=it)}) }
            RocoText("Bei einer Anfrage erhalten die jeweils verwendeten Anbieter deine freigegebenen Scores, Messwerte, Verläufe und Logbuchdaten.",style=MaterialTheme.typography.bodySmall)
            ClayButton(onClick={save(draft)},enabled=ready && !saving) { RocoText(if(saving) "Wird gespeichert …" else if(status=="Gespeichert") "✓ Gespeichert" else "Speichern") }
            TextButton(onClick={save(AiConfiguration())},enabled=ready && !saving) { RocoText("API-Keys entfernen") }
            if(status!=null && status!="Gespeichert") RocoText(status,color=MaterialTheme.colorScheme.error)
        }
    }}
}
@Composable private fun AiConversation(config:AiConfiguration,day:DayInsight?,gemini:Boolean,initial:String,onConfigure:()->Unit) {
    val scope=rememberCoroutineScope()
    var question by rememberSaveable(initial) { mutableStateOf(initial) }
    var messages by remember(gemini,day?.date) { mutableStateOf<List<CoachMessage>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val configured=(if(gemini) config.geminiKey else config.routerKey).isNotBlank()
    ProductPanel(if(gemini) "Frag nach · Gemini" else "Dein Gespräch · OpenRouter", if(config.shareHealthSummary) "Mit deiner freigegebenen Tageszusammenfassung" else "Ohne Gesundheitszusammenfassung") {
        if(!configured) { RocoText("Hinterlege deinen eigenen API-Key, um persönliche Fragen zu stellen.");ClayButton(onClick=onConfigure) { RocoText("KI einrichten") } }
        else {
            messages.takeLast(16).forEach { message -> Surface(color=if(message.role=="user") Teal.copy(alpha=.08f) else MaterialTheme.colorScheme.background,shape=RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) { RocoText(if(message.role=="user") "Du" else "KI-Coach",style=MaterialTheme.typography.labelMedium,color=Teal);if(message.role=="user") RocoVerbatimText(message.text,style=MaterialTheme.typography.bodyMedium) else CoachMarkdown(message.text) }
            } }
            OutlinedTextField(question,{question=it.take(4000)},label={RocoText("Was möchtest du wissen?")},modifier=Modifier.fillMaxWidth(),minLines=2,maxLines=5,enabled=!busy)
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            ClayButton(onClick={
                val request=messages+CoachMessage("user",question.trim()); busy=true;error=null
                scope.launch {
                    try { val answer=askCoach(config,gemini,request,day);messages=request+CoachMessage("assistant",answer);question="" }
                    catch(e:CancellationException) { throw e }
                    catch(e:Exception) { error=if(e is IllegalStateException || e is IllegalArgumentException) e.message else "Verbindung fehlgeschlagen. Bitte prüfe dein Netz und versuche es erneut." }
                    finally { busy=false }
                }
            },enabled=!busy && question.isNotBlank()) { RocoText(if(gemini) "Erklärung anfragen" else "Senden") }
            error?.let { RocoText(it,color=MaterialTheme.colorScheme.error) }
            RocoText("KI-Antworten können Fehler enthalten. Keine medizinische Diagnose.",style=MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun EnergyBreakdown(score: Engine.ScoreResult?) {
    val parts=score?.components.orEmpty()
    val weights=mapOf("D" to .5,"H" to .25,"P" to .25)
    val present=weights.filterKeys { it in parts }
    val base=if(present.isEmpty()) null else present.entries.sumOf { parts.getValue(it.key)*it.value }/present.values.sum()
    ProductPanel("Von Erholung zu Tagesenergie", "Der Akku ist eine Modellschätzung aus Schlaf, Puls und Bewegung.") {
        if(base==null || score?.score==null) RocoText("Die Aufschlüsselung erscheint, sobald deine Startenergie berechenbar ist.")
        else {
            val remaining=score.score.toFloat();val used=(base-remaining).coerceAtLeast(0.0).toFloat()
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { RocoText("Startbasis · ${base.roundToInt()}");RocoText("Jetzt · ${score.score}",color=Teal,fontWeight=FontWeight.Bold) }
            Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                drawRoundRect(Teal.copy(alpha=.10f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(9.dp.toPx()))
                drawRoundRect(Teal, size=Size(size.width*remaining.coerceIn(0f,100f)/100,size.height),cornerRadius=androidx.compose.ui.geometry.CornerRadius(9.dp.toPx()))
                if(used>0) drawRect(Coral.copy(alpha=.5f),Offset(size.width*remaining/100,0f),Size(size.width*used.coerceAtMost(100-remaining)/100,size.height))
            }
            RocoText("Grün: verbleibende Energie · Koralle: modellierter Verbrauch seit dem Aufwachen",style=MaterialTheme.typography.bodySmall)
            ExplanationBar("Warum ist das eine Schätzung?","Deine Uhr misst keinen Akku. Wir verrechnen Erholungsmerkmale mit Trainingsdauer, verfügbarem Puls und Schritten. Fehlende Pulsdaten führen zu einer gekennzeichneten Näherung über die Dauer.",null,Teal)
        }
    }
}
@Composable private fun TrainingBreakdown(score:Engine.ScoreResult?, day:DayInsight?) {
    ProductPanel("Deine vier Trainingswochen", "Trainingszeit in rollierenden 7-Tage-Fenstern. Der Belastungsindex berücksichtigt zusätzlich Pulsdaten, wenn sie ausreichend vorliegen.") {
        val values=(0..3).map { day?.metrics?.get("week_${it}_minutes") }
        val max=values.filterNotNull().maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        values.forEachIndexed { i,value ->
            val complete=score?.coverage?.windowCompleteness?.get("W$i")==true
            Row { RocoText(if(i==0) "Letzte 7 Tage" else "${i*7+1}–${(i+1)*7} Tage zuvor",Modifier.weight(1f));RocoText(value?.let { "${it.roundToInt()} min" } ?: "–") }
            LinearProgressIndicator(progress={((value ?: 0.0)/max).toFloat()},modifier=Modifier.fillMaxWidth(),color=if(i==0) Coral else Coral.copy(alpha=.5f))
            if(!complete) RocoText("Zeitraum nicht vollständig gelesen",style=MaterialTheme.typography.bodySmall)
        }
        score?.coverage?.hrCoverage?.let { coverage ->
            if(coverage.totalSeconds>0) {
                val percent=(100.0*coverage.coveredSeconds/coverage.totalSeconds).roundToInt()
                RocoText("Pulsabdeckung während der Trainings: $percent%",style=MaterialTheme.typography.labelLarge)
                LinearProgressIndicator(progress={percent.coerceIn(0,100)/100f},modifier=Modifier.fillMaxWidth(),color=Teal)
            }
        }
        if("duration_fallback" in score?.coverage?.missingReasons.orEmpty()) RocoText("Für diesen Vergleich wird Trainingsdauer verwendet, weil vollständige Pulsdaten oder Ruhepuls-Basiswerte fehlen.",style=MaterialTheme.typography.bodyMedium)
    }
}
