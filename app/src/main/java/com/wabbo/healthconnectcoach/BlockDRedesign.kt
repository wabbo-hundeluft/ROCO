package com.wabbo.healthconnectcoach

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthcoach.score.contract.ScoreComponent
import com.example.healthcoach.score.contract.ScoreResult
import com.example.healthcoach.score.contract.ScoreStatus
import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine
import com.wabbo.healthconnectcoach.ui.theme.HealthCoachTheme
import com.wabbo.healthconnectcoach.ui.theme.LocalHealthCoachCategoryColors
import com.wabbo.healthconnectcoach.ui.theme.IbmPlexSans
import com.wabbo.healthconnectcoach.ui.theme.IbmPlexSerif
import com.wabbo.healthconnectcoach.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

internal enum class BlockDTab(val title: String, val icon: String) {
    TODAY("Heute", "home"),
    EXPLORE("Entdecken", "explore"),
    COACH("Coach", "smart_toy"),
    SETTINGS("Einstellungen", "settings"),
}

internal enum class AppearanceMode(val title: String) {
    SYSTEM("System"),
    LIGHT("Hell"),
    DARK("Dunkel"),
}

private val MaterialSymbols = FontFamily(Font(R.font.material_symbols_outlined))
private const val SleepScoreId = "sleep-extended-v1"
private const val BatteryScoreId = "body-battery-v1"
private const val SessionLoadScoreId = "session-load-v1"

private data class ScoreDisplay(
    val id: String,
    val title: String,
    val subtitle: String,
    val symbol: String,
    val tone: Color,
)

@Composable
internal fun BlockDBottomNavigation(selected: BlockDTab, onSelect: (BlockDTab) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    val twoRows = fontScale >= 1.3f
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant),
        ) {
            val rows = if (twoRows) {
                listOf(BlockDTab.values().take(2), BlockDTab.values().drop(2))
            } else {
                listOf(BlockDTab.values().toList())
            }
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = if (twoRows) 62.dp else 76.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    row.forEach { tab ->
                        val isSelected = tab == selected
                        val fg = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        val bg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .padding(horizontal = 3.dp, vertical = 4.dp)
                                .background(bg, RoundedCornerShape(18.dp))
                                .clickable(
                                    role = Role.Tab,
                                    onClick = { onSelect(tab) },
                                )
                                .semantics(mergeDescendants = true) {
                                    this.selected = isSelected
                                    contentDescription = "${tab.title}, ${if (isSelected) "ausgewählt" else "Tab"}"
                                }
                                .padding(horizontal = if (twoRows) 10.dp else 2.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (twoRows) {
                                MaterialSymbol(tab.icon, tint = fg, size = 21.sp)
                                Spacer(Modifier.width(8.dp))
                                RocoText(tab.title, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 2)
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    MaterialSymbol(tab.icon, tint = fg, size = 22.sp)
                                    RocoText(tab.title, color = fg, style = MaterialTheme.typography.labelSmall, maxLines = 2, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TodayRedesignScreen(
    scores: Map<String, ApprovedWellnessScoreEngine.ScoreResult>,
    batteryDayTrend: List<ScoreTrendPoint>,
    scoreHistory: Map<String, List<ScoreTrendPoint>>,
    snapshotReadAt: Map<String, Instant>,
    dataHint: String,
    locallyConnected: Boolean,
    readsPaused: Boolean,
    permissionsVerified: Boolean,
    permissionCheckFailed: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenScore: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val categoryColors = LocalHealthCoachCategoryColors.current
    val scoreItems = listOf(
        ScoreDisplay(BatteryScoreId, "Akku", "Erholung · Bewegung", "battery_charging_full", categoryColors.readiness),
        ScoreDisplay(SessionLoadScoreId, "Belastung", "Training", "fitness_center", categoryColors.training),
        ScoreDisplay(SleepScoreId, "Schlaf", "Dauer · Rhythmus", "bedtime", categoryColors.sleep),
    )
    var selectedScoreId by remember { mutableStateOf(BatteryScoreId) }
    val selected = scoreItems.first { it.id == selectedScoreId }
    val score = scores[selectedScoreId]
    val scoreValue = displayValue(selectedScoreId, score)
    val state = stateHint(locallyConnected, readsPaused, permissionsVerified, permissionCheckFailed)
    Box(modifier = Modifier.fillMaxSize()) {
        MintRadialBackdrop(Modifier.fillMaxSize())
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HealthBuddy(isRefreshing = isRefreshing, modifier = Modifier.size(48.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    RocoText("Dein Tag", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                    RocoText(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.getDefault()).format(java.time.LocalDate.now()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onRefresh, enabled = !isRefreshing, modifier = Modifier.heightIn(min = 48.dp)) {
                    MaterialSymbol(if (isRefreshing) "hourglass_top" else "sync", tint = MaterialTheme.colorScheme.primary, size = 20.sp)
                    Spacer(Modifier.width(4.dp))
                    RocoText(if (isRefreshing) "Lädt" else "Neu")
                }
            }
            StatusPill(text = dataHint, connected = locallyConnected && permissionsVerified && !permissionCheckFailed, compact = true)

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val compact = LocalDensity.current.fontScale >= 1.25f || maxWidth < 340.dp
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        scoreItems.forEach { item ->
                            ScoreSelectorCard(item, scores[item.id], item.id == selectedScoreId) {
                                onOpenScore(item.id)
                            }
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        scoreItems.forEach { item ->
                            Box(Modifier.weight(1f)) {
                                ScoreSelectorCard(item, scores[item.id], item.id == selectedScoreId) {
                                    onOpenScore(item.id)
                                }
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpenScore(selectedScoreId) },
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(color = selected.tone.copy(alpha = 0.2f), shape = RoundedCornerShape(18.dp), modifier = Modifier.size(44.dp)) {
                            Box(contentAlignment = Alignment.Center) { MaterialSymbol(selected.symbol, tint = selected.tone, size = 24.sp) }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            RocoText(selected.title, style = MaterialTheme.typography.titleLarge)
                            RocoText(selected.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        RocoText("Details  ›", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ScoreRing(scoreValue, score?.let(::scoreStatusLabel) ?: state, selected.tone, 92.dp, 25.sp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            RocoText(scoreValue?.let { "$it / 100" } ?: "Noch kein Wert", style = MaterialTheme.typography.headlineSmall)
                            RocoText(score?.let(::scoreStatusLabel) ?: state, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (score?.status == ApprovedWellnessScoreEngine.Status.stale) {
                                RocoText("Letzter Stand · ${formatInstant(snapshotReadAt[selectedScoreId] ?: score.asOf)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                    if (selectedScoreId == BatteryScoreId && batteryDayTrend.isNotEmpty()) {
                        BatteryDayTrendChart(points = batteryDayTrend, isSnapshot = score?.status == ApprovedWellnessScoreEngine.Status.stale)
                    } else {
                        val trend = scoreHistory[selectedScoreId].orEmpty()
                        if (trend.isNotEmpty()) {
                            RocoText("Dein Verlauf", style = MaterialTheme.typography.titleSmall)
                            ScoreTrendPlot(points = trend, periodDays = 7, intraday = false)
                        } else {
                            RocoText("Mit neuen Messungen entsteht hier dein Verlauf.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (score != null && score.components.isNotEmpty()) ComponentBars(score.components)
                    RocoText("Tippe für Erklärung und Trends", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }

            if (!locallyConnected || !permissionsVerified || permissionCheckFailed) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        RocoText("Verbinde deine Gesundheitsdaten", style = MaterialTheme.typography.titleMedium)
                        RocoText(
                            "Deine letzten Scores bleiben auf diesem Gerät gespeichert und werden beim Öffnen neu berechnet, sobald Health Connect verfügbar ist.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onOpenSettings, modifier = Modifier.heightIn(min = 48.dp)) { RocoText("Health Connect prüfen") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyCheckInProgress(
    items: List<ScoreDisplay>,
    scores: Map<String, ApprovedWellnessScoreEngine.ScoreResult>,
) {
    val readyCount = items.count { displayValue(it.id, scores[it.id]) != null }
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MaterialSymbol(if (readyCount == items.size) "celebration" else "auto_awesome", tint = MaterialTheme.colorScheme.tertiary, size = 19.sp)
            RocoText("Dein Check-in", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            items.forEach { item ->
                MaterialSymbol(
                    if (displayValue(item.id, scores[item.id]) != null) "check_circle" else "radio_button_unchecked",
                    tint = if (displayValue(item.id, scores[item.id]) != null) item.tone else MaterialTheme.colorScheme.outlineVariant,
                    size = 18.sp,
                )
            }
            RocoText("$readyCount/3", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ScoreSelectorCard(
    display: ScoreDisplay,
    score: ApprovedWellnessScoreEngine.ScoreResult?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val value = displayValue(display.id, score)
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    Card(
        modifier = Modifier.fillMaxWidth()
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.selected = selected
                contentDescription = "${display.title}, ${value?.let { "$it von 100" } ?: "kein Wert"}${if (selected) ", ausgewählt" else ""}"
            },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = background),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 2.dp else 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            ScoreRing(value, score?.let(::scoreStatusLabel) ?: "Noch keine Daten", display.tone, 58.dp, 17.sp, Modifier.clearAndSetSemantics { })
            RocoText(display.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, textAlign = TextAlign.Center)
            RocoText(value?.let { "$it" } ?: "—", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HealthBuddy(isRefreshing: Boolean, modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(if (isRefreshing) 1.1f else 1f, animationSpec = tween(durationMillis = 360), label = "buddy-bounce")
    val mint = MaterialTheme.colorScheme.primary
    val coral = MaterialTheme.colorScheme.tertiary
    val ink = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier = modifier.graphicsLayer(scaleX = scale, scaleY = scale).semantics { contentDescription=RocoStrings.translate("Dein ROCO Maskottchen") }) {
        val unit = size.minDimension
        val cx = size.width / 2f
        val cy = size.height / 2f
        val bodyW = unit * 0.72f
        val bodyH = unit * 0.78f
        val left = cx - bodyW / 2f
        val top = cy - bodyH / 2f
        drawRoundRect(
            color = mint,
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(bodyW, bodyH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(unit * 0.27f),
        )
        // Tiny rounded arms and feet make the battery character feel friendly.
        drawRoundRect(mint, Offset(left - unit * 0.09f, cy + unit * 0.03f), androidx.compose.ui.geometry.Size(unit * 0.17f, unit * 0.28f), androidx.compose.ui.geometry.CornerRadius(unit * 0.09f))
        drawRoundRect(mint, Offset(left + bodyW - unit * 0.08f, cy + unit * 0.03f), androidx.compose.ui.geometry.Size(unit * 0.17f, unit * 0.28f), androidx.compose.ui.geometry.CornerRadius(unit * 0.09f))
        drawRoundRect(mint, Offset(cx - unit * 0.23f, top + bodyH - unit * 0.01f), androidx.compose.ui.geometry.Size(unit * 0.15f, unit * 0.15f), androidx.compose.ui.geometry.CornerRadius(unit * 0.07f))
        drawRoundRect(mint, Offset(cx + unit * 0.08f, top + bodyH - unit * 0.01f), androidx.compose.ui.geometry.Size(unit * 0.15f, unit * 0.15f), androidx.compose.ui.geometry.CornerRadius(unit * 0.07f))
        drawRoundRect(ink.copy(alpha = 0.85f), Offset(cx - unit * 0.15f, cy - unit * 0.11f), androidx.compose.ui.geometry.Size(unit * 0.055f, unit * 0.1f), androidx.compose.ui.geometry.CornerRadius(unit * 0.03f))
        drawRoundRect(ink.copy(alpha = 0.85f), Offset(cx + unit * 0.095f, cy - unit * 0.11f), androidx.compose.ui.geometry.Size(unit * 0.055f, unit * 0.1f), androidx.compose.ui.geometry.CornerRadius(unit * 0.03f))
        drawArc(
            color = ink.copy(alpha = 0.82f),
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(cx - unit * 0.14f, cy + unit * 0.04f),
            size = androidx.compose.ui.geometry.Size(unit * 0.28f, unit * 0.18f),
            style = Stroke(width = unit * 0.035f, cap = StrokeCap.Round),
        )
        drawCircle(coral, radius = unit * 0.055f, center = Offset(cx, cy + unit * 0.25f))
        drawCircle(Color.White.copy(alpha = 0.42f), radius = unit * 0.07f, center = Offset(cx - unit * 0.18f, top + unit * 0.13f))
    }
}

@Composable
private fun ScoreCard(
    display: ScoreDisplay,
    score: ApprovedWellnessScoreEngine.ScoreResult?,
    snapshotReadAt: Instant?,
    state: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.heightIn(min = 144.dp).clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = scoreDescription(display.title, score, state) + ". Details öffnen." },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ScoreRing(
                value = displayValue(display.id, score),
                status = score?.let(::scoreStatusLabel) ?: state,
                tint = display.tone,
                size = 72.dp,
                centerFont = 19.sp,
                modifier = Modifier.clearAndSetSemantics { },
            )
            RocoText(display.title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
            RocoText(
                text = score?.let(::scoreStatusLabel) ?: state,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            if (score?.status == ApprovedWellnessScoreEngine.Status.stale) {
                RocoText(
                    "Gespeicherter Stand · ${formatInstant(snapshotReadAt ?: score.asOf)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun ScoreRing(value: Int?, status: String, tint: Color, size: androidx.compose.ui.unit.Dp, centerFont: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = modifier.size(size).semantics {
            if (value != null) progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(value.toFloat(), 0f..100f)
            contentDescription = "${value?.let { "$it von 100" } ?: "nicht verfügbar"}, $status"
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(2.dp)) {
            drawArc(track, -90f, 360f, false, style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round))
            value?.let {
                drawArc(tint, -90f, (min(it, 100).coerceAtLeast(0) * 3.6f), false, style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        RocoText(value?.toString() ?: "—", fontFamily = IbmPlexSans, fontWeight = FontWeight.SemiBold, fontSize = centerFont, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

@Composable
internal fun ScoreDetailRedesignScreen(
    scoreId: String,
    score: ApprovedWellnessScoreEngine.ScoreResult?,
    scoreHistory: List<ScoreTrendPoint>,
    snapshotReadAt: Instant?,
    locallyConnected: Boolean,
    readsPaused: Boolean,
    permissionsVerified: Boolean,
    permissionCheckFailed: Boolean,
    grantedPermissions: Set<String>,
    observedSourceOrigins: Map<String, Set<String>>,
    sourceObservationsAvailable: Boolean,
    onBack: () -> Unit,
) {
    val display = scoreDisplay(scoreId)
    var selectedPeriod by remember { mutableIntStateOf(7) }
    var showCalculationExplanation by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        MintRadialBackdrop(Modifier.fillMaxSize())
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                    MaterialSymbol("arrow_back", tint = MaterialTheme.colorScheme.primary, size = 22.sp)
                    Spacer(Modifier.width(5.dp))
                    RocoText("Zurück")
                }
                Spacer(Modifier.weight(1f))
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RocoText(display.title, style = MaterialTheme.typography.headlineMedium)
                RocoText(display.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        ScoreRing(displayValue(scoreId, score), score?.let(::scoreStatusLabel) ?: stateHint(locallyConnected, readsPaused, permissionsVerified, permissionCheckFailed), display.tone, 104.dp, 28.sp)
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
                            RocoText(displayValue(scoreId, score)?.let { "$it / 100" } ?: "—", style = MaterialTheme.typography.headlineSmall)
                            RocoText(score?.let(::scoreStatusLabel) ?: stateHint(locallyConnected, readsPaused, permissionsVerified, permissionCheckFailed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            score?.let {
                                RocoText(
                                    if (it.status == ApprovedWellnessScoreEngine.Status.stale) {
                                        "Veralteter Stand · zuletzt gelesen ${formatInstant(snapshotReadAt ?: it.asOf)} · berechnet ${formatInstant(it.asOf)}"
                                    } else {
                                        "Berechnet ${formatInstant(it.asOf)}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    RocoText(
                        if (score == null) "Noch kein Wert. Es wird keine Beispielzahl eingesetzt."
                        else if (score.status == ApprovedWellnessScoreEngine.Status.stale) {
                            "Letzter Score lokal gespeichert. Für einen aktuellen Wert Health Connect wieder verbinden."
                        } else "Der Scorewert wird lokal verschlüsselt gespeichert. Rohdaten und Messreihen bleiben ausschließlich in Health Connect.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            ScoreTrendCard(
                title = display.title,
                selectedPeriod = selectedPeriod,
                points = scoreHistory,
                isSnapshot = score?.status == ApprovedWellnessScoreEngine.Status.stale,
                onPeriodChange = { selectedPeriod = it },
            )

            if (score != null && score.components.isNotEmpty()) {
                SectionCard(title = "Berechnete Bestandteile", subtitle = "Nur Bestandteile aus diesem echten Score-Ergebnis.") {
                    ComponentBars(score.components)
                }
            }

            if (scoreId == SessionLoadScoreId) {
                score?.let { SessionLoadWindows(it) } ?: SectionCard(title = "W0–W3", subtitle = "Fensterabdeckung erscheint nach einem erfolgreichen Score-Read.") {
                    RocoText("Keine Fensterdaten im Arbeitsspeicher.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (scoreId == BatteryScoreId || scoreId == SessionLoadScoreId) {
                score?.let { HeartRateCoverage(it) } ?: SectionCard(title = "Herzfrequenz-Abdeckung", subtitle = "Keine Abdeckungsdaten im Arbeitsspeicher.") {
                    RocoText("HR-Abdeckung wird erst nach einem echten Read gezeigt.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            SectionCard(title = "Quelle je Recordtyp", subtitle = "Quellnamen bleiben verborgen; die Ansicht zeigt nur Nutzung und Abdeckung.") {
                relevantRecordTypes(scoreId).forEach { recordType ->
                    SourceTypeRow(recordType, score, grantedPermissions, permissionsVerified, observedSourceOrigins, sourceObservationsAvailable)
                }
            }

            score?.let {
                val missing = safeMissingReasons(it.coverage.missingReasons)
                if (missing.isNotEmpty()) {
                    SectionCard(title = "Datenstatus", subtitle = "Fehlende Daten sind kein Nullwert.") {
                        missing.forEach { reason -> RocoText("• $reason", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                SectionCard(title = "Berechnungsinfo", subtitle = "Version ${it.algorithmVersion}") {
                    RocoText("Coverage ${it.coverage.available} von ${it.coverage.expected} Bestandteilen oder Fenstern.", style = MaterialTheme.typography.bodyMedium)
                    if (scoreId == SessionLoadScoreId && it.rating0to100 != null) {
                        RocoText("Bewertung: ${it.rating0to100} von 100", style = MaterialTheme.typography.bodyMedium)
                    }
                    RocoText("Der Rohindex wird in dieser Ansicht nicht ausgegeben.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SectionCard(title = "So wird der Score berechnet", subtitle = "Kurz erklärt · standardmäßig geschlossen") {
                TextButton(
                    onClick = { showCalculationExplanation = !showCalculationExplanation },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                        contentDescription = if (showCalculationExplanation) "Berechnungserklärung schließen" else "Berechnungserklärung öffnen"
                        selected = showCalculationExplanation
                    },
                ) {
                    RocoText(if (showCalculationExplanation) "Erklärung schließen" else "Erklärung öffnen")
                    Spacer(Modifier.width(6.dp))
                    MaterialSymbol(if (showCalculationExplanation) "expand_less" else "expand_more", tint = MaterialTheme.colorScheme.primary, size = 20.sp)
                }
                if (showCalculationExplanation) {
                    RocoText(calculationExplanation(scoreId), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ScoreTrendCard(
    title: String,
    selectedPeriod: Int,
    points: List<ScoreTrendPoint>,
    isSnapshot: Boolean,
    showPeriodSelector: Boolean = true,
    onPeriodChange: (Int) -> Unit,
) {
    val latest = points.maxOfOrNull { it.at }
    val visiblePoints = if (latest == null) emptyList() else points.filter {
        !it.at.isBefore(latest.minus(java.time.Duration.ofDays(selectedPeriod.toLong())))
    }
    SectionCard(title = "Verlauf · $title", subtitle = "$selectedPeriod Tage · nur echte Scoreberechnungen aus dieser App-Sitzung") {
        if (showPeriodSelector) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 30).forEach { period ->
                    FilterPill("$period T", selected = selectedPeriod == period, enabled = true, contentDescription=RocoStrings.translate("$period-Tage-Zeitraum"), modifier = Modifier.widthIn(min = 72.dp)) { onPeriodChange(period) }
                }
            }
        }
        if (visiblePoints.isEmpty()) {
            RocoText(
                if (isSnapshot) "Der Offline-Snapshot enthält nur den letzten Scorewert und Originalzeitpunkt; eine Zeitreihe oder Offlinekurve wird nicht gespeichert."
                else "Für diesen Zeitraum liegen keine echten Scorepunkte im Arbeitsspeicher vor. Es werden keine Werte ergänzt oder interpoliert.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ScoreTrendPlot(points = visiblePoints, periodDays = selectedPeriod, intraday = false)
            RocoText(
                "${visiblePoints.size} echte Score${if (visiblePoints.size == 1) "berechnung" else "berechnungen"} · nur tatsächliche Zeitpunkte, keine Nullauffüllung",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BatteryDayTrendChart(points: List<ScoreTrendPoint>, isSnapshot: Boolean) {
    val todayPoints = points.sortedBy { it.at }
    SectionCard(title = "Tagesform im Verlauf", subtitle = "Heutige echte Messpunkte · Sitzungsspeicher") {
        if (todayPoints.isEmpty()) {
            RocoText(
                if (isSnapshot) "Der veraltete Stand enthält nur den letzten Einzelwert; Tagespunkte und Offline-Verläufe werden nicht gespeichert."
                else "Nach einem erfolgreichen Live-Read erscheint ein echter Scorepunkt. Es werden keine stündlichen Schätzwerte oder Offline-Verläufe gezeigt.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ScoreTrendPlot(points = todayPoints, periodDays = 1, intraday = true)
            RocoText("${todayPoints.size} echte Live-Scorepunkte · nur tatsächliche Read-Zeitpunkte, keine Stundenauffüllung", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ScoreTrendPlot(points: List<ScoreTrendPoint>, periodDays: Int, intraday: Boolean) {
    val sorted = remember(points) { points.sortedBy { it.at } }
    var selectedIndex by remember(sorted) { mutableIntStateOf(sorted.lastIndex) }
    val selectedPoint = sorted.getOrNull(selectedIndex.coerceIn(0, sorted.lastIndex))
    val latest = sorted.last().at
    val zone = ZoneId.systemDefault()
    val start = if (intraday) latest.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
    else latest.minus(java.time.Duration.ofDays(periodDays.toLong()))
    val end = latest
    val spanMillis = (end.toEpochMilli() - start.toEpochMilli()).coerceAtLeast(1L).toFloat()
    val tint = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        Modifier.fillMaxWidth().height(168.dp)
            .pointerInput(sorted, start, spanMillis) {
                detectTapGestures { tap ->
                    val left = 8.dp.toPx()
                    val right = (size.width - 8.dp.toPx()).coerceAtLeast(left + 1f)
                    selectedIndex = nearestTrendPointIndex(sorted, tap.x, left, right, start, spanMillis) ?: selectedIndex
                }
            }
            .semantics {
            val minValue = sorted.minOf { it.value }
            val maxValue = sorted.maxOf { it.value }
            val selected = selectedPoint
            contentDescription = "Score-Verlauf mit ${sorted.size} echten Punkten, Wertebereich $minValue bis $maxValue von 100. " +
                (selected?.let { "Ausgewählt: ${it.value} von 100 am ${formatTrendTime(it.at, zone)}." } ?: "") +
                " Fehlende Zeitpunkte werden nicht ergänzt."
            customActions = listOf(
                CustomAccessibilityAction("Vorherigen Datenpunkt auswählen") {
                    selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                    true
                },
                CustomAccessibilityAction("Nächsten Datenpunkt auswählen") {
                    selectedIndex = (selectedIndex + 1).coerceAtMost(sorted.lastIndex)
                    true
                },
            )
        },
    ) {
        val left = 8.dp.toPx()
        val right = (size.width - 8.dp.toPx()).coerceAtLeast(left + 1f)
        val top = 10.dp.toPx()
        val bottom = (size.height - 12.dp.toPx()).coerceAtLeast(top + 1f)
        listOf(0f, 50f, 100f).forEach { value ->
            val y = bottom - (value / 100f) * (bottom - top)
            drawLine(grid.copy(alpha = 0.7f), Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
        }
        val centers = sorted.map { point ->
            val xFraction = ((point.at.toEpochMilli() - start.toEpochMilli()).toFloat() / spanMillis).coerceIn(0f, 1f)
            val yFraction = point.value.coerceIn(0, 100) / 100f
            Offset(left + (right - left) * xFraction, bottom - (bottom - top) * yFraction)
        }
        val maxConnectionGapMinutes = when {
            intraday -> 90L
            periodDays <= 7 -> 2L * 24L * 60L
            else -> 7L * 24L * 60L
        }
        // Connect only nearby real reads; wider gaps remain visible instead of being bridged.
        for (index in 0 until centers.lastIndex) {
            val fromTime = sorted[index].at
            val toTime = sorted[index + 1].at
            if (java.time.Duration.between(fromTime, toTime).toMinutes() <= maxConnectionGapMinutes) {
                drawLine(
                    tint.copy(alpha = 0.7f),
                    centers[index],
                    centers[index + 1],
                    strokeWidth = (if (intraday) 3.dp else 2.dp).toPx(),
                )
            }
        }
        centers.forEachIndexed { index, center ->
            if (index == selectedIndex) {
                drawLine(grid, Offset(center.x, top), Offset(center.x, bottom), strokeWidth = 1.dp.toPx())
                drawCircle(tint, radius = 10.dp.toPx(), center = center, style = Stroke(width = 2.dp.toPx()))
            }
            drawCircle(track, radius = 8.dp.toPx(), center = center)
            drawCircle(tint, radius = if (index == selectedIndex) 6.dp.toPx() else 5.dp.toPx(), center = center)
        }
    }
    selectedPoint?.let { selected ->
        val change = trendPointDelta(sorted, selectedIndex)
        RocoText(
            text = buildString {
                append("${selected.value}/100 · ${formatTrendTime(selected.at, zone)}")
                if (change != null) append(" · ${if (change > 0) "+" else ""}$change zum vorherigen Punkt")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        RocoText(if (intraday) "00:00" else "−$periodDays T", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        RocoText(if (intraday) formatInstant(latest) else "letzter Punkt", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatTrendTime(at: Instant, zone: ZoneId): String =
    DateTimeFormatter.ofPattern("d. MMM · HH:mm", Locale.getDefault()).withZone(zone).format(at)

internal fun nearestTrendPointIndex(
    points: List<ScoreTrendPoint>,
    tapX: Float,
    left: Float,
    right: Float,
    start: Instant,
    spanMillis: Float,
): Int? {
    if (points.isEmpty() || spanMillis <= 0f) return null
    return points.indices.minByOrNull { index ->
        val fraction = ((points[index].at.toEpochMilli() - start.toEpochMilli()).toFloat() / spanMillis).coerceIn(0f, 1f)
        kotlin.math.abs(left + (right - left) * fraction - tapX)
    }
}

internal fun trendPointDelta(points: List<ScoreTrendPoint>, selectedIndex: Int): Int? {
    val current = points.getOrNull(selectedIndex) ?: return null
    val previous = points.getOrNull(selectedIndex - 1) ?: return null
    return current.value - previous.value
}

@Composable
private fun ComponentBars(components: Map<String, Int>) {
    val labels = mapOf("D" to "Dauer", "R" to "Regelmäßigkeit", "H" to "HRV", "P" to "Ruhepuls", "A" to "Aktivität", "activity" to "Aktivität")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        components.entries.sortedBy { listOf("D", "R", "H", "P", "A", "activity").indexOf(it.key).let { index -> if (index < 0) Int.MAX_VALUE else index } }.forEach { (key, value) ->
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    RocoText(labels[key] ?: "Bestandteil", style = MaterialTheme.typography.bodyMedium)
                    RocoText("$value von 100", style = MaterialTheme.typography.labelLarge)
                }
                ThinProgress(
                    progress = value.coerceIn(0, 100) / 100f,
                    description = "${labels[key] ?: "Bestandteil"} Scorewert $value von 100",
                )
            }
        }
    }
}

@Composable
private fun ThinProgress(progress: Float, description: String) {
    val tint = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.fillMaxWidth().height(10.dp).semantics {
        progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(progress, 0f..1f)
        contentDescription = description
    }) {
        drawRoundRect(track, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
        drawRoundRect(tint, size = androidx.compose.ui.geometry.Size(size.width * progress, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
    }
}

@Composable
private fun SessionLoadWindows(score: ApprovedWellnessScoreEngine.ScoreResult) {
    SectionCard(title = "Session-Load-Fenster W0–W3", subtitle = "W0 = letzte 7 Tage · W1–W3 = die drei Referenzwochen. Abdeckung bleibt je Fenster getrennt.") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (0..3).forEach { index ->
                val key = "W$index"
                val complete = score.coverage.windowCompleteness[key]
                val hr = score.coverage.hrCoverageByWindow[key]
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            RocoText("$key · ${if (index == 0) "letzte 7 Tage" else "Referenzwoche $index"}", style = MaterialTheme.typography.titleSmall)
                            RocoText(when (complete) { true -> "Vollständig"; false -> "Unvollständig"; null -> "Nicht geprüft" }, style = MaterialTheme.typography.labelLarge, color = if (complete == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (complete != null) {
                            RocoText("Abfragefenster", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ThinProgress(
                                progress = if (complete) 1f else 0f,
                                description = "$key Health-Connect-Abfrage ${if (complete) "vollständig" else "unvollständig"}",
                            )
                        }
                        if (hr != null) {
                            RocoText("HR-Coverage · ${formatCoverage(hr)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (hr.totalSeconds > 0L) {
                                ThinProgress(
                                    progress = (hr.intervalCoverage ?: 0.0).toFloat().coerceIn(0f, 1f),
                                    description = "$key Herzfrequenz-Coverage ${((hr.intervalCoverage ?: 0.0) * 100).toInt()} Prozent",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeartRateCoverage(score: ApprovedWellnessScoreEngine.ScoreResult) {
    val coverage = score.coverage.hrCoverage
    SectionCard(title = "Herzfrequenz-Abdeckung", subtitle = "Aggregierte Abdeckung; keine einzelnen Messpunkte.") {
        if (coverage.totalSeconds <= 0L) {
            RocoText("Für diesen Score liegt keine berechenbare HR-Abdeckungsdauer vor.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            RocoText(formatCoverage(coverage), style = MaterialTheme.typography.titleMedium)
            ThinProgress(
                progress = (coverage.intervalCoverage ?: 0.0).toFloat().coerceIn(0f, 1f),
                description = "Herzfrequenz-Coverage ${((coverage.intervalCoverage ?: 0.0) * 100).toInt()} Prozent",
            )
        }
    }
}

private fun formatCoverage(coverage: ApprovedWellnessScoreEngine.HrCoverage): String {
    val ratio = coverage.intervalCoverage?.let { "${(it * 100).toInt()} %" } ?: "nicht berechenbar"
    val covered = coverage.coveredSeconds / 60
    val total = coverage.totalSeconds / 60
    return "$ratio · $covered von $total Minuten · ${coverage.uniqueSamples} echte Stichproben"
}

private data class RecordTypeUi(val key: ApprovedWellnessScoreEngine.RecordType, val label: String, val permission: Set<String>)

private fun relevantRecordTypes(scoreId: String): List<RecordTypeUi> {
    val all = listOf(
        RecordTypeUi(ApprovedWellnessScoreEngine.RecordType.SLEEP_SESSION, "Schlafsitzung", HealthConnectPermissions.sleep),
        RecordTypeUi(ApprovedWellnessScoreEngine.RecordType.HRV_RMSSD, "HRV (RMSSD)", HealthConnectPermissions.hrv),
        RecordTypeUi(ApprovedWellnessScoreEngine.RecordType.RESTING_HEART_RATE, "Ruhepuls", HealthConnectPermissions.restingHeartRate),
        RecordTypeUi(ApprovedWellnessScoreEngine.RecordType.EXERCISE_SESSION, "Trainingseinheit", HealthConnectPermissions.trainingRecovery),
        RecordTypeUi(ApprovedWellnessScoreEngine.RecordType.HEART_RATE, "Herzfrequenz", HealthConnectPermissions.heartRate),
        RecordTypeUi(ApprovedWellnessScoreEngine.RecordType.STEPS, "Schritte", HealthConnectPermissions.steps),
    )
    val wanted = when (scoreId) {
        SleepScoreId -> setOf(ApprovedWellnessScoreEngine.RecordType.SLEEP_SESSION, ApprovedWellnessScoreEngine.RecordType.HRV_RMSSD, ApprovedWellnessScoreEngine.RecordType.RESTING_HEART_RATE)
        BatteryScoreId -> setOf(ApprovedWellnessScoreEngine.RecordType.SLEEP_SESSION, ApprovedWellnessScoreEngine.RecordType.HRV_RMSSD, ApprovedWellnessScoreEngine.RecordType.RESTING_HEART_RATE, ApprovedWellnessScoreEngine.RecordType.EXERCISE_SESSION, ApprovedWellnessScoreEngine.RecordType.HEART_RATE, ApprovedWellnessScoreEngine.RecordType.STEPS)
        else -> setOf(ApprovedWellnessScoreEngine.RecordType.EXERCISE_SESSION, ApprovedWellnessScoreEngine.RecordType.HEART_RATE, ApprovedWellnessScoreEngine.RecordType.RESTING_HEART_RATE)
    }
    return all.filter { it.key in wanted }
}

@Composable
private fun SourceTypeRow(
    type: RecordTypeUi,
    score: ApprovedWellnessScoreEngine.ScoreResult?,
    grantedPermissions: Set<String>,
    permissionsVerified: Boolean,
    observedSourceOrigins: Map<String, Set<String>>,
    sourceObservationsAvailable: Boolean,
) {
    val typeName = when (type.key) {
        ApprovedWellnessScoreEngine.RecordType.SLEEP_SESSION -> ApprovedWellnessRecordTypes.SLEEP_SESSION
        ApprovedWellnessScoreEngine.RecordType.HRV_RMSSD -> ApprovedWellnessRecordTypes.HRV_RMSSD
        ApprovedWellnessScoreEngine.RecordType.RESTING_HEART_RATE -> ApprovedWellnessRecordTypes.RESTING_HEART_RATE
        ApprovedWellnessScoreEngine.RecordType.EXERCISE_SESSION -> ApprovedWellnessRecordTypes.EXERCISE_SESSION
        ApprovedWellnessScoreEngine.RecordType.HEART_RATE -> ApprovedWellnessRecordTypes.HEART_RATE
        ApprovedWellnessScoreEngine.RecordType.STEPS -> ApprovedWellnessRecordTypes.STEPS
    }
    val observed = if (sourceObservationsAvailable) observedSourceOrigins[typeName].orEmpty().size else null
    val used = score?.coverage?.sourcesByType?.get(type.key).orEmpty().size
    val accessStatus = when {
        !permissionsVerified -> "Leserecht wird geprüft"
        !grantedPermissions.containsAll(type.permission) -> "Leserecht fehlt · keine Quelle gelesen"
        observed != null && observed > 0 -> "${observed} Quelle${if (observed == 1) "" else "n"} im Read beobachtet"
        score != null && sourceObservationsAvailable -> "Im Read kein Record dieses Typs beobachtet"
        score != null -> "Quellenread nicht beobachtet"
        else -> "Noch nicht gelesen"
    }
    val scoreCoverage = when {
        used > 0 -> "Score-Coverage: verwendet · $used erkannte Quelle${if (used == 1) "" else "n"}"
        observed != null && observed > 0 -> "Score-Coverage: beobachtet, aber nicht verwendet"
        score != null && sourceObservationsAvailable -> "Score-Coverage: kein verwendeter Record"
        else -> "Score-Coverage: noch nicht ermittelt"
    }
    val hrCoverage = score?.coverage?.hrCoverage
    Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            RocoText(type.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            RocoText(accessStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 190.dp))
        }
        RocoText(scoreCoverage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (type.key == ApprovedWellnessScoreEngine.RecordType.HEART_RATE && hrCoverage != null && hrCoverage.totalSeconds > 0L) {
            RocoText("HR-Coverage: ${formatCoverage(hrCoverage)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ExploreRedesignScreen(
    scores: Map<String, ApprovedWellnessScoreEngine.ScoreResult>,
    scoreHistory: Map<String, List<ScoreTrendPoint>>,
    snapshotReadAt: Map<String, Instant>,
    dataHint: String,
    locallyConnected: Boolean,
    permissionsVerified: Boolean,
    permissionCheckFailed: Boolean,
    readsPaused: Boolean,
    grantedPermissions: Set<String>,
    observedSourceOrigins: Map<String, Set<String>>,
    sourceObservationsAvailable: Boolean,
    medicalFeatureAvailable: Boolean?,
    historyStatusText: String,
    onOpenScore: (String) -> Unit,
) {
    var selectedPeriod by remember { mutableIntStateOf(30) }
    val fontScale = LocalDensity.current.fontScale
    val scoreState = stateHint(locallyConnected, readsPaused, permissionsVerified, permissionCheckFailed)
    val categories = listOf(
        CategoryCardModel("Schlaf · erweitert", "Schlafscore", "bedtime", SleepScoreId, CategoryKind.SCORE),
        CategoryCardModel("Tagesform", "Score", "battery_charging_full", BatteryScoreId, CategoryKind.SCORE),
        CategoryCardModel("Session-Kreislauflast", "Score", "fitness_center", SessionLoadScoreId, CategoryKind.SCORE),
        CategoryCardModel("Herz & HRV", "Quellenstatus", "monitor_heart", BatteryScoreId, CategoryKind.HEART),
        CategoryCardModel("Bewegung", "Quellenstatus", "directions_run", BatteryScoreId, CategoryKind.MOVEMENT),
        CategoryCardModel("Medical / PHR", "Nur Status & Verfügbarkeit", "health_and_safety", null, CategoryKind.MEDICAL),
    )
    val orderedCategories = categories.sortedBy { item ->
        categoryPriority(item, scores, grantedPermissions, permissionsVerified, observedSourceOrigins, sourceObservationsAvailable)
    }
    Box(Modifier.fillMaxSize()) {
        MintRadialBackdrop(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                RocoText("Entdecken", style = MaterialTheme.typography.headlineMedium)
                RocoText("Verläufe nur aus echten Datenpunkten.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                StatusPill(dataHint, locallyConnected && permissionsVerified && !permissionCheckFailed, compact = true)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(7, 30, 90, 365, -1).forEach { days ->
                    val available = days == 7 || days == 30
                    val label = when (days) { 7 -> "7 T"; 30 -> "30 T"; 90 -> "90 T"; 365 -> "1 Jahr"; else -> "Alles" }
                    FilterPill(
                        label = label,
                        selected = selectedPeriod == days,
                        enabled = available,
                        contentDescription = if (available) "$label Zeitraum" else "$label deaktiviert: $historyStatusText",
                        modifier = Modifier.weight(1f),
                        onClick = { selectedPeriod = days },
                    )
                }
            }
            RocoText(historyStatusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(SleepScoreId, BatteryScoreId, SessionLoadScoreId).forEach { id ->
                ScoreTrendCard(
                    title = scoreDisplay(id).title,
                    selectedPeriod = selectedPeriod,
                    points = scoreHistory[id].orEmpty(),
                    isSnapshot = scores[id]?.status == ApprovedWellnessScoreEngine.Status.stale,
                    showPeriodSelector = false,
                    onPeriodChange = { selectedPeriod = it },
                )
            }
            RocoText("Bereiche", style = MaterialTheme.typography.titleLarge)
            if (fontScale >= 1.3f) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    orderedCategories.forEach { item ->
                        CategoryCard(item, scores, item.scoreId?.let { snapshotReadAt[it] }, scoreState, grantedPermissions, permissionsVerified, observedSourceOrigins, sourceObservationsAvailable, medicalFeatureAvailable, Modifier.fillMaxWidth()) { item.scoreId?.let(onOpenScore) }
                    }
                }
            } else {
                orderedCategories.chunked(2).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        rowItems.forEach { item ->
                            CategoryCard(item, scores, item.scoreId?.let { snapshotReadAt[it] }, scoreState, grantedPermissions, permissionsVerified, observedSourceOrigins, sourceObservationsAvailable, medicalFeatureAvailable, Modifier.weight(1f)) { item.scoreId?.let(onOpenScore) }
                        }
                        if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private enum class CategoryKind { SCORE, HEART, MOVEMENT, MEDICAL }
private data class CategoryCardModel(val title: String, val label: String, val symbol: String, val scoreId: String?, val kind: CategoryKind)

private fun categoryPriority(
    model: CategoryCardModel,
    scores: Map<String, ApprovedWellnessScoreEngine.ScoreResult>,
    grantedPermissions: Set<String>,
    permissionsVerified: Boolean,
    observedSourceOrigins: Map<String, Set<String>>,
    sourceObservationsAvailable: Boolean,
): Int {
    if (model.kind == CategoryKind.MEDICAL) return 4
    if (!permissionsVerified) return 3
    return when (model.kind) {
        CategoryKind.SCORE -> when (scores[model.scoreId]?.status) {
            ApprovedWellnessScoreEngine.Status.available -> 0
            ApprovedWellnessScoreEngine.Status.provisional -> 1
            ApprovedWellnessScoreEngine.Status.insufficient_data,
            ApprovedWellnessScoreEngine.Status.stale,
            ApprovedWellnessScoreEngine.Status.source_conflict -> 2
            ApprovedWellnessScoreEngine.Status.permission_missing, null -> 3
        }
        CategoryKind.HEART -> {
            val hasComponents = scores.values.any { "H" in it.components || "P" in it.components }
            val hasObservation = listOf(ApprovedWellnessRecordTypes.HRV_RMSSD, ApprovedWellnessRecordTypes.RESTING_HEART_RATE).any { !observedSourceOrigins[it].isNullOrEmpty() }
            when {
                !grantedPermissions.containsAll(HealthConnectPermissions.hrv) && !grantedPermissions.containsAll(HealthConnectPermissions.restingHeartRate) -> 3
                hasComponents -> 0
                sourceObservationsAvailable && hasObservation -> 1
                else -> 2
            }
        }
        CategoryKind.MOVEMENT -> {
            val used = listOf(ApprovedWellnessScoreEngine.RecordType.EXERCISE_SESSION, ApprovedWellnessScoreEngine.RecordType.STEPS).any { type -> scores.values.any { !it.coverage.sourcesByType[type].isNullOrEmpty() } }
            val hasObservation = listOf(ApprovedWellnessRecordTypes.EXERCISE_SESSION, ApprovedWellnessRecordTypes.STEPS).any { !observedSourceOrigins[it].isNullOrEmpty() }
            when {
                !grantedPermissions.containsAll(HealthConnectPermissions.trainingRecovery) && !grantedPermissions.containsAll(HealthConnectPermissions.steps) -> 3
                used -> 0
                sourceObservationsAvailable && hasObservation -> 1
                else -> 2
            }
        }
        CategoryKind.MEDICAL -> 4
    }
}

@Composable
private fun CategoryCard(
    model: CategoryCardModel,
    scores: Map<String, ApprovedWellnessScoreEngine.ScoreResult>,
    snapshotReadAt: Instant?,
    state: String,
    grantedPermissions: Set<String>,
    permissionsVerified: Boolean,
    observedSourceOrigins: Map<String, Set<String>>,
    sourceObservationsAvailable: Boolean,
    medicalAvailable: Boolean?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val score = model.scoreId?.let(scores::get)
    val value = if (model.kind == CategoryKind.SCORE) score?.let { displayValue(it.scoreId, it) } else null
    val scoreStatus = score?.let(::scoreStatusLabel)
    val status = when (model.kind) {
        CategoryKind.MEDICAL -> when (medicalAvailable) {
            true -> "Feature verfügbar · keine Records gelesen"
            false -> "Feature nicht verfügbar · keine Records gelesen"
            null -> "Status nicht geprüft · keine Records gelesen"
        }
        CategoryKind.SCORE -> value?.let { "$it / 100 · $scoreStatus" } ?: scoreStatus ?: state
        CategoryKind.HEART -> {
            val hrvUsed = scores.values.any { "H" in it.components }
            val rhrUsed = scores.values.any { "P" in it.components }
            val hrvObserved = !observedSourceOrigins[ApprovedWellnessRecordTypes.HRV_RMSSD].isNullOrEmpty()
            val rhrObserved = !observedSourceOrigins[ApprovedWellnessRecordTypes.RESTING_HEART_RATE].isNullOrEmpty()
            val missingPermissions = listOf(HealthConnectPermissions.hrv, HealthConnectPermissions.restingHeartRate).count { !grantedPermissions.containsAll(it) }
            when {
                !permissionsVerified -> state
                missingPermissions == 2 -> "HRV- und Ruhepulsrechte fehlen"
                missingPermissions == 1 -> "Teilweise Berechtigung fehlt"
                hrvUsed && rhrUsed -> "HRV und Ruhepuls in Scores verwendet"
                hrvUsed -> "HRV verwendet · Ruhepuls fehlt"
                rhrUsed -> "Ruhepuls verwendet · HRV fehlt"
                sourceObservationsAvailable && (hrvObserved || rhrObserved) -> "Quelle beobachtet · kein Scorebeitrag"
                sourceObservationsAvailable -> "Keine Quelle im Read beobachtet"
                else -> "Quellenstatus unbekannt"
            }
        }
        CategoryKind.MOVEMENT -> {
            val exerciseUsed = scores.values.any { !it.coverage.sourcesByType[ApprovedWellnessScoreEngine.RecordType.EXERCISE_SESSION].isNullOrEmpty() }
            val stepsUsed = scores.values.any { !it.coverage.sourcesByType[ApprovedWellnessScoreEngine.RecordType.STEPS].isNullOrEmpty() }
            val exerciseObserved = !observedSourceOrigins[ApprovedWellnessRecordTypes.EXERCISE_SESSION].isNullOrEmpty()
            val stepsObserved = !observedSourceOrigins[ApprovedWellnessRecordTypes.STEPS].isNullOrEmpty()
            val hasAnyPermission = grantedPermissions.containsAll(HealthConnectPermissions.trainingRecovery) || grantedPermissions.containsAll(HealthConnectPermissions.steps)
            val hasAllPermissions = grantedPermissions.containsAll(HealthConnectPermissions.trainingRecovery) && grantedPermissions.containsAll(HealthConnectPermissions.steps)
            when {
                !permissionsVerified -> state
                !hasAnyPermission -> "Berechtigung fehlt"
                !hasAllPermissions -> "Teilweise Berechtigung fehlt"
                exerciseUsed || stepsUsed -> "Bewegungsquelle in Score verwendet"
                sourceObservationsAvailable && (exerciseObserved || stepsObserved) -> "Quelle beobachtet · kein aktueller Scorebeitrag"
                sourceObservationsAvailable -> "Keine Quelle im Read beobachtet"
                else -> "Quellenstatus unbekannt"
            }
        }
    }
    Card(
        modifier = modifier.heightIn(min = 132.dp).then(if (model.scoreId != null) Modifier.clickable(role = Role.Button, onClick = onClick).semantics(mergeDescendants = true) { contentDescription = "${model.title}, $status. Details öffnen." } else Modifier.semantics(mergeDescendants = true) { contentDescription = "${model.title}. $status" }),
        shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MaterialSymbol(model.symbol, tint = MaterialTheme.colorScheme.primary, size = 25.sp)
            RocoText(model.title, style = MaterialTheme.typography.titleSmall)
            RocoText(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (score?.status == ApprovedWellnessScoreEngine.Status.stale) {
                RocoText("Veralteter Stand · gelesen ${formatInstant(snapshotReadAt ?: score.asOf)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun CoachRedesignScreen(
    scores: Map<String, ApprovedWellnessScoreEngine.ScoreResult>,
    onOpenScore: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var selectedScoreId by remember { mutableStateOf(BatteryScoreId) }
    val scoreIds = listOf(BatteryScoreId, SleepScoreId, SessionLoadScoreId)
    val selectedDisplay = scoreDisplay(selectedScoreId)
    val selectedScore = scores[selectedScoreId]
    Box(Modifier.fillMaxSize()) {
        MintRadialBackdrop(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                RocoText("Coach", style = MaterialTheme.typography.headlineMedium)
                RocoText("Deine Scores · lokal und verständlich erklärt", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                scoreIds.forEach { scoreId ->
                    val display = scoreDisplay(scoreId)
                    val label = when (scoreId) {
                        BatteryScoreId -> "Tagesform"
                        SleepScoreId -> "Schlaf"
                        else -> "Belastung"
                    }
                    FilterPill(
                        label = label,
                        selected = selectedScoreId == scoreId,
                        enabled = true,
                        contentDescription = "${display.title} erklären",
                        modifier = Modifier.weight(1f),
                    ) { selectedScoreId = scoreId }
                }
            }
            SectionCard(
                title = selectedDisplay.title,
                subtitle = selectedScore?.let(::scoreStatusLabel) ?: "Noch keine aktuellen Messwerte",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ScoreRing(
                        value = selectedScore?.let { displayValue(selectedScoreId, it) },
                        status = selectedScore?.let(::scoreStatusLabel) ?: "Nicht verfügbar",
                        tint = selectedDisplay.tone,
                        size = 88.dp,
                        centerFont = 25.sp,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
                        RocoText(
                            selectedScore?.let { displayValue(selectedScoreId, it)?.let { value -> "$value / 100" } } ?: "—",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        RocoText("${selectedScore?.let { "Berechnet ${formatInstant(it.asOf)}" } ?: "Werte erscheinen nach dem nächsten Health-Connect-Read"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                RocoText(calculationExplanation(selectedScoreId), style = MaterialTheme.typography.bodyMedium)
                if (selectedScore?.components?.isNotEmpty() == true) {
                    RocoText("Bestandteile", style = MaterialTheme.typography.titleSmall)
                    ComponentBars(selectedScore.components)
                }
                selectedScore?.let { score ->
                    val missingReasons = safeMissingReasons(score.coverage.missingReasons)
                    if (missingReasons.isNotEmpty()) {
                        RocoText("Datenlücken", style = MaterialTheme.typography.titleSmall)
                        missingReasons.take(3).forEach { reason ->
                            RocoText(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                TextButton(onClick = { onOpenScore(selectedScoreId) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    RocoText("Score-Details öffnen")
                }
            }
            if (scores.isEmpty()) {
                SectionCard(title = "Noch keine Daten", subtitle = "Du bestimmst, welche Messwerte freigegeben werden.") {
                    RocoText("Verbinde Health Connect und starte einen Lesevorgang, damit deine Scores hier erscheinen.", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onOpenSettings, modifier = Modifier.heightIn(min = 48.dp)) { RocoText("Health Connect einrichten") }
                }
            }
            RocoText("Erklärungen werden auf diesem Gerät erstellt. Gesundheitsdaten werden nicht an einen KI-Dienst gesendet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SettingsLandingScreen(
    locallyConnected: Boolean,
    permissionsVerified: Boolean,
    permissionCheckFailed: Boolean,
    readsPaused: Boolean,
    appearanceMode: AppearanceMode,
    onOpenHealthConnect: () -> Unit,
    onAppearanceModeChange: (AppearanceMode) -> Unit,
) {
    val hcStatus = when {
        !locallyConnected -> "Nicht verbunden"
        permissionCheckFailed -> "Rechtestatus nicht prüfbar"
        !permissionsVerified -> "Rechte werden geprüft"
        readsPaused -> "Lesen pausiert"
        else -> "Zugriff aktiv"
    }
    Box(Modifier.fillMaxSize()) {
        MintRadialBackdrop(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
            RocoText("Einstellungen", style = MaterialTheme.typography.headlineMedium)
            SettingGroupTitle("Health Connect")
            SettingRow("Health Connect einrichten", hcStatus, "health_metrics", onOpenHealthConnect)
            SettingGroupTitle("KI-Dienste")
            SectionCard(title = "OpenRouter · Chat", subtitle = "Deaktiviert · Offline-Vorschau") {
                RocoText("Keine Schlüssel werden gelesen oder geändert; es werden keine Chat-Anfragen gesendet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SectionCard(title = "Gemini · Erklärungen", subtitle = "Deaktiviert · Offline-Vorschau") {
                RocoText("Keine Schlüssel werden gelesen oder geändert; es werden keine Erklärungs-Anfragen gesendet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SettingGroupTitle("Darstellung")
            SectionCard(title = "Farbschema", subtitle = "Systemeinstellung wird respektiert; große Schrift bleibt systemkonform.") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    AppearanceMode.values().forEach { mode ->
                        FilterPill(mode.title, selected = appearanceMode == mode, enabled = true, contentDescription = "Farbschema ${mode.title}", modifier = Modifier.weight(1f)) { onAppearanceModeChange(mode) }
                    }
                }
            }
            SettingGroupTitle("Bedienung")
            SectionCard(title = "Zugänglichkeit", subtitle = "TalkBack, Touchziele und Schriftgröße") {
                RocoText("Systemschriftgröße wird übernommen. Bedienelemente haben mindestens 48 dp Hitfläche; bei großer Schrift ordnet sich die Tab-Leiste neu.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SettingGroupTitle("Über")
            SectionCard(title = "ROCO", subtitle = "Lokale Wellness-Anzeige") {
                RocoText("Version 0.1.0 · keine medizinische Bewertung", style = MaterialTheme.typography.bodyMedium)
                RocoText("Die App zeigt nur freigegebene Score-IDs und deren tatsächlichen Datenstatus.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingGroupTitle(title: String) {
    RocoText(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 5.dp))
}

@Composable
private fun SettingRow(title: String, status: String, icon: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp).clickable(role = Role.Button, onClick = onClick).semantics(mergeDescendants = true) { contentDescription=RocoStrings.translate("$title. $status. Öffnen.") },
        shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MaterialSymbol(icon, tint = MaterialTheme.colorScheme.primary, size = 25.sp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                RocoText(title, style = MaterialTheme.typography.titleSmall)
                RocoText(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MaterialSymbol("chevron_right", tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 22.sp)
        }
    }
}

@Composable
private fun SectionCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RocoText(title, style = MaterialTheme.typography.titleMedium)
                RocoText(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, enabled: Boolean, contentDescription: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val fg = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = modifier.heightIn(min = 48.dp).semantics(mergeDescendants = true) {
            this.contentDescription = contentDescription
            this.selected = selected
            if (!enabled) this.disabled()
        }.then(if (enabled) Modifier.clickable(role = Role.RadioButton, onClick = onClick) else Modifier),
        color = bg,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            RocoText(label, style = MaterialTheme.typography.labelLarge, color = fg, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable
internal fun StatusPill(text: String, connected: Boolean, compact: Boolean, modifier: Modifier = Modifier) {
    val bg = if (connected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    Surface(color = bg, shape = RoundedCornerShape(18.dp), modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription=RocoStrings.translate("Health-Connect-Status: $text") }) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = if (compact) 10.dp else 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MaterialSymbol(if (connected) "check_circle" else "info", tint = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, size = 20.sp)
            RocoText(text, style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun MaterialSymbol(name: String, tint: Color, size: androidx.compose.ui.unit.TextUnit) {
    RocoText(
        text = materialSymbolGlyph(name),
        fontFamily = MaterialSymbols,
        fontWeight = FontWeight.Normal,
        fontSize = size,
        lineHeight = size,
        color = tint,
        maxLines = 1,
        modifier = Modifier.size(size.value.dp).clearAndSetSemantics { },
        style = TextStyle(fontFeatureSettings = "'liga' 1"),
    )
}

@Composable
internal fun MintRadialBackdrop(modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.background))
}

private fun displayValue(id: String, result: ApprovedWellnessScoreEngine.ScoreResult?): Int? = when (id) {
    SessionLoadScoreId -> result?.rating0to100 ?: result?.score
    else -> result?.score
}

@Composable
private fun scoreDisplay(id: String): ScoreDisplay {
    val colors = LocalHealthCoachCategoryColors.current
    return when (id) {
        SleepScoreId -> ScoreDisplay(id, "Erweiterter Schlafscore", "Schlafdauer · Regelmäßigkeit · HRV · Ruhepuls", "bedtime", colors.sleep)
        BatteryScoreId -> ScoreDisplay(id, "Tagesform", "Lokale Basis · Aktivität · Erholung", "battery_charging_full", colors.readiness)
        else -> ScoreDisplay(id, "Session-Kreislauflast", "7 Tage gegenüber drei Referenzwochen", "fitness_center", colors.training)
    }
}

private fun calculationExplanation(id: String): String = when (id) {
    SleepScoreId -> "Der erweiterte Schlafscore ordnet nur die vorhandenen freigegebenen Komponenten ein: Schlafdauer, Regelmäßigkeit, HRV und Ruhepuls. Fehlende Eingaben bleiben als fehlend erkennbar."
    BatteryScoreId -> "Die Tagesform ist eine lokale Wellness-Heuristik aus freigegebenen Schlaf-, Erholungs- und Aktivitätsdaten. Sie ist kein medizinischer Messwert."
    else -> "Die Session-Kreislauflast vergleicht W0 (jüngste sieben Tage) mit den Referenzfenstern W1–W3. Lücken und HR-Abdeckung bleiben pro Fenster sichtbar."
}

private fun scoreStatusLabel(score: ApprovedWellnessScoreEngine.ScoreResult): String = when (score.status) {
    ApprovedWellnessScoreEngine.Status.available -> "Verfügbar"
    ApprovedWellnessScoreEngine.Status.provisional -> "Vorläufig"
    ApprovedWellnessScoreEngine.Status.insufficient_data -> "Zu wenig Daten"
    ApprovedWellnessScoreEngine.Status.permission_missing -> "Berechtigung fehlt"
    ApprovedWellnessScoreEngine.Status.stale -> "Veraltet"
    ApprovedWellnessScoreEngine.Status.source_conflict -> "Quellenkonflikt"
}

private fun stateHint(connected: Boolean, paused: Boolean, verified: Boolean, failed: Boolean): String = when {
    !connected -> "App-Zugriff getrennt"
    failed -> "Berechtigungsstatus nicht prüfbar"
    !verified -> "Health-Connect-Rechte werden geprüft"
    paused -> "Lesen pausiert · Arbeitsspeicherstand eingefroren"
    else -> "Noch keine ausreichenden Messungen"
}

private fun scoreDescription(title: String, score: ApprovedWellnessScoreEngine.ScoreResult?, fallback: String): String {
    val value = score?.let { displayValue(it.scoreId, it) }
    return "$title, ${value?.let { "$it von 100" } ?: "nicht verfügbar"}, ${score?.let(::scoreStatusLabel) ?: fallback}"
}

private fun safeMissingReasons(reasons: List<String>): List<String> {
    val explanations = mapOf(
        "sleep_permission_missing" to "Schlafleserecht fehlt; Health Connect hat keine Schlafrecords für diesen Score geliefert.",
        "hrv_permission_missing" to "HRV-Leserecht fehlt; die HRV-Komponente konnte nicht berechnet werden.",
        "rhr_permission_missing" to "Ruhepuls-Leserecht fehlt; die Ruhepuls-Komponente konnte nicht berechnet werden.",
        "exercise_permission_missing" to "Trainingsleserecht fehlt; Trainingsrecords konnten nicht ausgewertet werden.",
        "heart_rate_permission_missing" to "Herzfrequenz-Leserecht fehlt; eine HR-basierte Trainingsdosis ist nicht berechenbar.",
        "steps_permission_missing" to "Schritt-Leserecht fehlt; Schrittaktivität konnte nicht berücksichtigt werden.",
        "HRV_missing" to "HRV fehlt: keine passende aktuelle Messung mit mindestens sieben gültigen Baseline-Tagen.",
        "RHR_missing" to "Ruhepuls fehlt: keine passende aktuelle Messung mit mindestens sieben gültigen Baseline-Tagen.",
        "regularity_missing" to "Regelmäßigkeit fehlt: weniger als fünf gültige vorherige Schlafnächte.",
        "sleep_stages_missing" to "Schlafstadien fehlen; für die Dauer wurde nur die Sitzungsdauer als obere Schätzung verwendet.",
        "sleep_stages_partial" to "Schlafstadien decken nicht die gesamte Sitzung ab; die Dauerkomponente ist vorläufig.",
        "main_sleep_missing" to "Keine geeignete Hauptschlafsitzung von mindestens drei Stunden im letzten 24-Stunden-Fenster.",
        "main_sleep_stale" to "Die letzte geeignete Hauptschlafsitzung endete vor mehr als 24 Stunden.",
        "sleep_read_incomplete" to "Der Schlaf-Read wurde nicht vollständig abgeschlossen; der Zeitbereich ist nicht vollständig geprüft.",
        "duration_fallback" to "Mindestens eine Trainingseinheit hatte keine ausreichende Herzfrequenz-Coverage; ihre Dauer wurde als begrenzter Ersatz verwendet.",
        "steps_wake_boundary_overlap" to "Ein Schrittintervall überlappt den Aufwachzeitpunkt; die Zuordnung zur Tagesaktivität ist vorläufig.",
        "steps_workout_overlap" to "Schrittintervalle überlappen eine Trainingseinheit und wurden nicht zusätzlich als Aktivität gezählt.",
        "activity_missing" to "Seit dem Aufwachen liegen weder verwertbare Trainings- noch Schrittintervalle vor.",
        "workout_sessions_missing" to "Seit dem Aufwachen liegt keine verwertbare Trainingseinheit vor.",
        "reference_window_incomplete" to "Mindestens eines der Fenster W0–W3 wurde nicht vollständig aus Health Connect gelesen.",
        "Wref_missing_or_zero" to "Die drei Referenzwochen W1–W3 enthalten keine berechenbare Session-Last.",
        "no_session_load" to "Im jüngsten Fenster W0 wurde keine Session-Last erfasst.",
        "source_conflict" to "Mehrere nicht eindeutig gebundene Datenquellen widersprechen sich.",
        "overlapping_workout_sessions" to "Trainingseinheiten überlappen sich zeitlich; eine eindeutige Lastzuordnung ist nicht möglich.",
    )
    return reasons.mapNotNull(explanations::get).distinct()
}

private fun formatInstant(instant: Instant): String = DateTimeFormatter.ofPattern("d. MMM · HH:mm", Locale.getDefault()).withZone(ZoneId.systemDefault()).format(instant)

private data class PreviewScreen(val title: String)
private class BlockDPreviewProvider : PreviewParameterProvider<PreviewScreen> {
    override val values = sequenceOf(PreviewScreen("Heute"), PreviewScreen("Details"), PreviewScreen("Entdecken"), PreviewScreen("Coach"), PreviewScreen("Einstellungen"))
}

@Composable
private fun BlockDPreviewScene(page: PreviewScreen, dark: Boolean) {
    HealthCoachTheme(darkTheme = dark) {
        val emptyScores = emptyMap<String, ApprovedWellnessScoreEngine.ScoreResult>()
        val selected = when (page.title) {
            "Heute", "Details" -> BlockDTab.TODAY
            "Entdecken" -> BlockDTab.EXPLORE
            "Coach" -> BlockDTab.COACH
            else -> BlockDTab.SETTINGS
        }
        Scaffold(bottomBar = { BlockDBottomNavigation(selected) {} }) { contentPadding ->
            Box(Modifier.fillMaxSize().padding(contentPadding)) {
                when (page.title) {
                    "Heute" -> TodayRedesignScreen(emptyScores, emptyList(), emptyMap(), emptyMap(), "Noch kein freigegebener Score-Read", false, false, false, false, false, {}, {}, {})
            "Details" -> ScoreDetailRedesignScreen(BatteryScoreId, null, emptyList(), null, false, false, false, false, emptySet(), emptyMap(), false, {})
                    "Entdecken" -> ExploreRedesignScreen(
                        scores = emptyScores,
                        scoreHistory = emptyMap(),
                        snapshotReadAt = emptyMap(),
                        dataHint = "Health Connect · noch kein freigegebener Score-Read",
                        locallyConnected = false,
                        permissionsVerified = false,
                        permissionCheckFailed = false,
                        readsPaused = false,
                        grantedPermissions = emptySet(),
                        observedSourceOrigins = emptyMap(),
                        sourceObservationsAvailable = false,
                        medicalFeatureAvailable = null,
                        historyStatusText = "History nicht freigegeben · maximal 30 Tage",
                        onOpenScore = {},
                    )
                    "Coach" -> CoachRedesignScreen(emptyScores, {}, {})
                    else -> SettingsLandingScreen(false, false, false, false, AppearanceMode.SYSTEM, {}, {})
                }
            }
        }
    }
}

@Preview(name = "360 dp · 1× · hell", widthDp = 360, heightDp = 820, fontScale = 1f, showBackground = true)
@Composable
private fun BlockDPreviewLight1x(@PreviewParameter(BlockDPreviewProvider::class) page: PreviewScreen) = BlockDPreviewScene(page, false)

@Preview(name = "360 dp · 1,3× · hell", widthDp = 360, heightDp = 820, fontScale = 1.3f, showBackground = true)
@Composable
private fun BlockDPreviewLight13x(@PreviewParameter(BlockDPreviewProvider::class) page: PreviewScreen) = BlockDPreviewScene(page, false)

@Preview(name = "360 dp · 2× · hell", widthDp = 360, heightDp = 820, fontScale = 2f, showBackground = true)
@Composable
private fun BlockDPreviewLight2x(@PreviewParameter(BlockDPreviewProvider::class) page: PreviewScreen) = BlockDPreviewScene(page, false)

@Preview(name = "360 dp · 1× · dunkel", widthDp = 360, heightDp = 820, fontScale = 1f, showBackground = true)
@Composable
private fun BlockDPreviewDark1x(@PreviewParameter(BlockDPreviewProvider::class) page: PreviewScreen) = BlockDPreviewScene(page, true)

@Preview(name = "360 dp · 2× · dunkel", widthDp = 360, heightDp = 820, fontScale = 2f, showBackground = true)
@Composable
private fun BlockDPreviewDark2x(@PreviewParameter(BlockDPreviewProvider::class) page: PreviewScreen) = BlockDPreviewScene(page, true)

@Preview(name = "360 dp · 1,3× · dunkel", widthDp = 360, heightDp = 820, fontScale = 1.3f, showBackground = true)
@Composable
private fun BlockDPreviewDark13x(@PreviewParameter(BlockDPreviewProvider::class) page: PreviewScreen) = BlockDPreviewScene(page, true)
