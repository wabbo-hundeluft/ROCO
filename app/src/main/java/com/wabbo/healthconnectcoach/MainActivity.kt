package com.wabbo.healthconnectcoach

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.feature.ExperimentalPersonalHealthRecordApi
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.healthcoach.score.contract.ScoreResult
import com.example.healthcoach.score.contract.ScoreComponent
import com.example.healthcoach.score.contract.ScoreStatus
import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine
import com.wabbo.healthconnectcoach.ui.theme.HealthCoachTheme
import com.wabbo.healthconnectcoach.ui.theme.LocalHealthCoachCategoryColors
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class FeaturePermissionRequest(
    val id: String,
    val title: String,
    val subtitle: String,
    val explanation: String,
    val period: String,
    val inputs: String,
    val permissions: Set<String>,
)

private enum class PermissionRequestKind {
    FEATURE,
    STANDARD_BULK,
    HISTORY,
    MEDICAL,
}

private val featurePermissionRequests = listOf(
    FeaturePermissionRequest(
        id = "sleep",
        title = "Schlaf",
        subtitle = "Schlafdauer und Regelmäßigkeit",
        explanation = "Schlafsitzungen und optionale Schlafstadien lesen.",
        period = "Letzter Hauptschlaf, der innerhalb der vergangenen 24 Stunden endete; Abfrage höchstens 30 Tage.",
        inputs = "Schlafsitzungen und Schlafstadien aus Health Connect.",
        permissions = HealthConnectPermissions.sleep,
    ),
    FeaturePermissionRequest(
        id = "readiness",
        title = "Tagesform",
        subtitle = "Bereitschaft",
        explanation = "HRV RMSSD und Ruhepuls für die Tagesform lesen.",
        period = "Jüngster vollständiger lokaler Messtag; persönliche Baseline aus den vorherigen 28 Tagen.",
        inputs = "HRV RMSSD in Millisekunden und Ruhepuls in bpm aus Health Connect.",
        permissions = HealthConnectPermissions.readiness,
    ),
    FeaturePermissionRequest(
        id = "training",
        title = "Zeit seit Training",
        subtitle = "Erholungsschätzung",
        explanation = "Übungssitzungen für eine lokale Zeit-seit-Training-Schätzung lesen.",
        period = "Jüngste Übungssitzung innerhalb der vergangenen 7 Tage; Abfrage höchstens 30 Tage.",
        inputs = "Übungssitzungen aus Health Connect.",
        permissions = HealthConnectPermissions.trainingRecovery,
    ),
)

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(RocoLocales.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window,window.decorView).isAppearanceLightStatusBars = true
        WindowCompat.getInsetsController(window,window.decorView).isAppearanceLightNavigationBars = true
        setContent {
            var appearanceModeName by rememberSaveable { mutableStateOf(AppearanceMode.SYSTEM.name) }
            val appearanceMode = AppearanceMode.valueOf(appearanceModeName)
            val useDarkTheme = when (appearanceMode) {
                AppearanceMode.SYSTEM -> isSystemInDarkTheme()
                AppearanceMode.LIGHT -> false
                AppearanceMode.DARK -> true
            }
            HealthCoachTheme(darkTheme = false) {
                HealthCoachHome(
                    activity = this,
                    appearanceMode = appearanceMode,
                    onAppearanceModeChange = { appearanceModeName = it.name },
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalPersonalHealthRecordApi::class)
private fun HealthCoachHome(
    activity: MainActivity,
    appearanceMode: AppearanceMode,
    onAppearanceModeChange: (AppearanceMode) -> Unit,
) {
    val sdkStatus = remember(activity) { HealthConnectClient.getSdkStatus(activity) }
    val client = remember(activity, sdkStatus) {
        if (sdkStatus == HealthConnectClient.SDK_AVAILABLE) {
            runCatching { HealthConnectClient.getOrCreate(activity) }.getOrNull()
        } else {
            null
        }
    }
    val preferences = remember(activity) {
        activity.getSharedPreferences(LOCAL_ACCESS_PREFERENCES, Context.MODE_PRIVATE)
    }
    val accessStore = remember(preferences) { SharedPreferencesLocalAccessStore(preferences) }
    val accessController = remember(accessStore) { LocalAccessController(accessStore) }
    val sourceBindingStore = remember(activity) { createPrimarySourceBindingStore(activity) }
    val snapshotStore = remember(activity) { createEncryptedScoreSnapshotStore(activity) }
    val journal = remember(activity) { ScoreJournal(SecureLocalDocument(activity, "score_journal")) }
    var journalDays by remember { mutableStateOf<List<DayInsight>>(emptyList()) }
    var productGeneration by remember { mutableIntStateOf(0) }
    var pendingUserRefresh by remember {mutableStateOf(false)}
    var effectiveProvider by remember { mutableStateOf("Health Connect") }
    var importStep by remember { mutableStateOf("Uhrendaten abrufen") }
    val scope = rememberCoroutineScope()
    val readMutex = remember(snapshotStore) { kotlinx.coroutines.sync.Mutex() }

    var locallyConnected by remember { mutableStateOf(accessController.isConnected()) }
    var readsPaused by remember { mutableStateOf(preferences.getBoolean(LOCAL_READ_PAUSED, false)) }
    var requestedPermissions by remember { mutableStateOf(emptySet<String>()) }
    var permissionRequestKind by remember { mutableStateOf<PermissionRequestKind?>(null) }
    var enableLocalAfterGrant by remember { mutableStateOf(false) }
    var setupGrantedPermissions by remember { mutableStateOf(emptySet<String>()) }
    var setupPermissionsChecked by remember { mutableStateOf(false) }
    var setupPermissionCheckFailed by remember { mutableStateOf(false) }
    var selectedStandardPermissions by remember {
        mutableStateOf(
            HealthConnectPermissions.sleep + HealthConnectPermissions.readiness +
                HealthConnectPermissions.trainingRecovery + HealthConnectPermissions.heartRate +
                HealthConnectPermissions.steps,
        )
    }
    var selectedMedicalPermissions by remember { mutableStateOf(HealthConnectSetup.medicalPermissions) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    var readMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var deletionResult by remember { mutableStateOf<LocalAppDataDeletionResult?>(null) }
    var selectedTab by remember { mutableStateOf(BlockDTab.TODAY) }
    var scoreDetailId by remember { mutableStateOf<String?>(null) }
    var healthConnectSettingsOpen by remember { mutableStateOf(false) }
    var sourceBindings by remember(sourceBindingStore) {
        mutableStateOf(sourceBindingStore.allBindings().associateBy { it.recordType })
    }
    var observedSourceOrigins by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    var sourceObservationsAvailable by remember { mutableStateOf(false) }
    var snapshots by remember { mutableStateOf<Map<String, FeatureReadSnapshot>>(emptyMap()) }
    var approvedScores by remember { mutableStateOf<Map<String, ApprovedWellnessScoreEngine.ScoreResult>>(emptyMap()) }
    var offlineApprovedScores by remember { mutableStateOf<Map<String, ApprovedWellnessScoreEngine.ScoreResult>>(emptyMap()) }
    var offlineScoreReadAt by remember { mutableStateOf<Map<String, Instant>>(emptyMap()) }
    var scoreTrendHistory by remember { mutableStateOf<Map<String, List<ScoreTrendPoint>>>(emptyMap()) }
    var batteryDayTrend by remember { mutableStateOf<List<ScoreTrendPoint>>(emptyList()) }
    var snapshotStoreReady by remember { mutableStateOf(false) }
    var permissionsVerified by remember { mutableStateOf(false) }
    var permissionCheckFailed by remember { mutableStateOf(false) }
    var resumeGeneration by remember { mutableIntStateOf(0) }
    var homeRefreshing by remember { mutableStateOf(false) }
    var lastRefreshAttempt by remember {mutableStateOf(preferences.getLong(LAST_SCORE_REFRESH_ATTEMPT,0L).takeIf {it>0}?.let(Instant::ofEpochMilli))}
    var manualRefreshAt by remember {mutableStateOf<Instant?>(null)}
    var activeReadJob by remember { mutableStateOf<Job?>(null) }
    val setupFeatureStatuses = remember(client, sdkStatus, resumeGeneration) {
        (HealthConnectSetup.featureGates + setOf(
            HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY,
            HealthConnectFeatures.FEATURE_PERSONAL_HEALTH_RECORD,
        )).associateWith { feature ->
            client?.let { runCatching { it.features.getFeatureStatus(feature) }.getOrNull() }
        }
    }

    suspend fun clearPersistedScores() {
        snapshots = emptyMap()
        offlineApprovedScores = emptyMap()
        offlineScoreReadAt = emptyMap()
        scoreTrendHistory = emptyMap()
        batteryDayTrend = emptyList()
        withContext(Dispatchers.IO) { snapshotStore.clear() }
    }

    suspend fun rereadActualPermissions(): Set<String>? {
        val currentClient = client
        if (sdkStatus != HealthConnectClient.SDK_AVAILABLE || currentClient == null) {
            setupGrantedPermissions = emptySet()
            setupPermissionsChecked = true
            setupPermissionCheckFailed = false
            return null
        }
        return try {
            currentClient.permissionController.getGrantedPermissions().also { actualGranted ->
                setupGrantedPermissions = actualGranted
                setupPermissionsChecked = true
                setupPermissionCheckFailed = false
            }
        } catch (_: Exception) {
            setupPermissionsChecked = false
            setupPermissionCheckFailed = true
            null
        }
    }

    suspend fun readApprovedScores(currentClient: HealthConnectClient, grantedBeforeRead: Set<String>) {
        if (
            !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            !accessController.isConnected() ||
            readsPaused
        ) return

        val sleepGranted = hasAllGrantedPermissions(HealthConnectPermissions.sleep, grantedBeforeRead)
        val hrvGranted = hasAllGrantedPermissions(HealthConnectPermissions.hrv, grantedBeforeRead)
        val rhrGranted = hasAllGrantedPermissions(HealthConnectPermissions.restingHeartRate, grantedBeforeRead)
        val exerciseGranted = hasAllGrantedPermissions(HealthConnectPermissions.trainingRecovery, grantedBeforeRead)
        val heartRateGranted = hasAllGrantedPermissions(HealthConnectPermissions.heartRate, grantedBeforeRead)
        val stepsGranted = hasAllGrantedPermissions(HealthConnectPermissions.steps, grantedBeforeRead)
        val asOf = Instant.now()
        val goals = withContext(Dispatchers.IO) { TrainingPlanStore(SecureLocalDocument(activity, "training_plan")).read()?.goals }
        val personalProfile = withContext(Dispatchers.IO) { PersonalScoreProfileStore(SecureLocalDocument(activity, "personal_score_profile")).read() }
        val calculationZone = ZoneId.systemDefault()
        val repository = HealthConnectRepository(
            client = currentClient,
            lifecycle = activity.lifecycle,
            isAppConnected = accessController::isConnected,
            clock = Clock.systemUTC(),
        )
        val historicalAccess = HealthConnectSetup.historyPermission in grantedBeforeRead
        val standardStart = asOf.minus(Duration.ofDays(if (historicalAccess) 65 else 29))
        val loadStart = asOf.minus(Duration.ofDays(if (historicalAccess) 65 else 28))
        val historyStart = asOf.minus(Duration.ofDays(if (historicalAccess) 65 else 35))
        val sleepRead = if (sleepGranted) repository.readSleepSessionsWithWindow(standardStart, asOf) else null
        val hrvRead = if (hrvGranted) repository.readHrvRmssdWithWindow(standardStart, asOf) else null
        val rhrRead = if (rhrGranted) repository.readRestingHeartRateWithWindow(historyStart, asOf) else null
        val exerciseRead = if (exerciseGranted) repository.readExerciseSessionsWithWindow(loadStart, asOf) else null
        val heartRateRead = if (heartRateGranted) try {
            repository.readHeartRateWithWindow(loadStart, asOf, exerciseRead?.records.orEmpty().map { it.startTime to it.endTime })
        } catch (_: HealthConnectReadCapacityException) {
            permissionMessage = "Sehr viele Trainings-Pulsdaten. Schlaf und Akku werden weiter berechnet; für Belastung bleibt der letzte Stand erhalten."
            null
        } else null
        val stepsRead = if (stepsGranted) repository.readStepsWithWindow(standardStart, asOf) else null
        val sessionLoadReadCompleteness = SessionLoadReadCoverage.fromReads(
            asOf = asOf,
            exercise = exerciseRead,
            heartRate = heartRateRead,
            restingHeartRate = rhrRead,
        )

        val readComplete =
            (sleepRead == null || !sleepRead.isIncomplete) &&
                (hrvRead == null || !hrvRead.isIncomplete) &&
                (rhrRead == null || Duration.between(rhrRead.window.start, rhrRead.window.end) >= Duration.ofDays(28)) &&
                (exerciseRead == null || !exerciseRead.isIncomplete) &&
                (heartRateRead == null || !heartRateRead.isIncomplete) &&
                (stepsRead == null || !stepsRead.isIncomplete)
        val historyComplete =
            (rhrRead == null || !rhrRead.isIncomplete) &&
                (exerciseRead == null || !exerciseRead.isIncomplete) &&
                (heartRateRead == null || !heartRateRead.isIncomplete)
        val permissionStates = mapOf(
            "sleep" to if (sleepGranted) ApprovedWellnessScoreEngine.PermissionState.granted else ApprovedWellnessScoreEngine.PermissionState.missing,
            "hrv" to if (hrvGranted) ApprovedWellnessScoreEngine.PermissionState.granted else ApprovedWellnessScoreEngine.PermissionState.missing,
            "rhr" to if (rhrGranted) ApprovedWellnessScoreEngine.PermissionState.granted else ApprovedWellnessScoreEngine.PermissionState.missing,
            "exercise" to if (exerciseGranted) ApprovedWellnessScoreEngine.PermissionState.granted else ApprovedWellnessScoreEngine.PermissionState.missing,
            "heart_rate" to if (heartRateGranted) ApprovedWellnessScoreEngine.PermissionState.granted else ApprovedWellnessScoreEngine.PermissionState.missing,
            "steps" to if (stepsGranted) ApprovedWellnessScoreEngine.PermissionState.granted else ApprovedWellnessScoreEngine.PermissionState.missing,
        )
        val wellnessRecords = WellnessScoreRecords(
            sleepSessions = sleepRead?.records.orEmpty(),
            hrvRmssd = hrvRead?.records.orEmpty(),
            restingHeartRate = rhrRead?.records.orEmpty(),
            exerciseSessions = exerciseRead?.records.orEmpty(),
            heartRate = heartRateRead?.records.orEmpty(),
            steps = stepsRead?.records.orEmpty(),
        )
        val watchVitals = WatchVitals.read(repository, asOf.minus(Duration.ofDays(30)), asOf, calculationZone, grantedBeforeRead, sourceBindingStore)
        val allScoreReadPermissions = HealthConnectPermissions.sleep +
            HealthConnectPermissions.readiness +
            HealthConnectPermissions.trainingRecovery +
            HealthConnectPermissions.heartRate +
            HealthConnectPermissions.steps
        val previouslyGrantedScoreRights = grantedBeforeRead.intersect(allScoreReadPermissions + WatchVitals.permissions + HealthConnectSetup.historyPermission)
        val grantedAfterRead = currentClient.permissionController.getGrantedPermissions()
        if (!grantedAfterRead.containsAll(previouslyGrantedScoreRights)) {
            approvedScores = emptyMap()
            readMessage = "Während des Reads wurde ein Health-Connect-Recht widerrufen; neue Werte wurden verworfen. Der letzte gespeicherte Score bleibt sichtbar."
            return
        }
        if (
            !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            !accessController.isConnected() ||
            readsPaused
        ) return

        val readout = withContext(Dispatchers.Default) {
            ApprovedWellnessScoreAdapter().score(
                records = wellnessRecords,
                asOf = asOf,
                zone = calculationZone,
                bindingStore = sourceBindingStore,
                permissions = permissionStates,
                readComplete = readComplete,
                historyComplete = historyComplete,
                recordTypeWindowCompleteness = sessionLoadReadCompleteness,
                v2Model = true,
                goals = goals,
                personalProfile = personalProfile,
                trainingHistoryStart = exerciseRead?.window?.start,
                trainingHistoryComplete = exerciseRead != null,
            )
        }
        observedSourceOrigins = readout.observedOrigins + watchVitals.origins
        sourceBindings = sourceBindingStore.allBindings().associateBy { it.recordType }
        sourceObservationsAvailable = sleepRead != null || hrvRead != null || rhrRead != null ||
            exerciseRead != null || heartRateRead != null || stepsRead != null
        approvedScores = readout.scores
        val persistedSnapshots = try {
            withContext(Dispatchers.IO) {
                readout.scores.values.mapNotNull { score -> snapshotStore.upsertEligibleScore(score, asOf) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        val restoredSnapshots = persistedSnapshots.mapNotNull { it.approvedScoreSnapshotOrNull() }
        if (restoredSnapshots.isNotEmpty()) {
            offlineApprovedScores = offlineApprovedScores + restoredSnapshots.associate { it.score.scoreId to it.score }
            offlineScoreReadAt = offlineScoreReadAt + restoredSnapshots.associate { it.score.scoreId to it.lastSuccessfulReadAt }
        }
        effectiveProvider = "Health Connect"
        val currentInsight = withContext(Dispatchers.Default) { dayInsight(readout).let { it.copy(sources = it.sources + ("_connector" to "Health Connect")) } }
        journalDays = mergeDays(journalDays, listOf(currentInsight.copy(metrics = currentInsight.metrics + watchVitals.values[currentInsight.date].orEmpty())))
        try { withContext(Dispatchers.IO) { journal.save(journalDays) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { permissionMessage = "Der Tagesstand konnte nicht gespeichert werden." }
        val freshDays = withContext(Dispatchers.Default) {
            val currentDate = asOf.atZone(calculationZone).toLocalDate()
            val older = (1L..29L).mapNotNull { offset ->
                kotlinx.coroutines.yield()
                val date = currentDate.minusDays(offset)
                // Refresh recent days, and reconstruct older dates once. No future records enter a past score.
                if (offset > 2 && journalDays.any { it.date == date && it.scores.values.all { score -> score.algorithmVersion.startsWith(ApprovedWellnessScoreEngine.MODEL_VERSION) && score.components["PROFILE_KEY"] == personalProfile.signature } &&
                    it.scores[ApprovedWellnessScoreEngine.SESSION_LOAD_ID]?.algorithmVersion == ApprovedWellnessScoreEngine.WEEKLY_MODEL_VERSION &&
                    it.scores[ApprovedWellnessScoreEngine.SESSION_LOAD_ID]?.components?.get("GOAL_PERCENT")== (goals?.weeklyLoadAdjustments?.get(date.with(java.time.DayOfWeek.MONDAY).toString()) ?: 100) }) return@mapNotNull null
                val end = date.plusDays(1).atStartOfDay(calculationZone).toInstant().minusNanos(1)
                val historical = historicalScoreReadout(readout, end,
                    SessionLoadReadCoverage.fromReads(end, exerciseRead, heartRateRead, rhrRead), v2Model = true)
                dayInsight(historical, reconstructed = true)
            }
            older + dayInsight(readout)
        }
        val enrichedDays = freshDays.map { day -> day.copy(metrics = day.metrics + watchVitals.values[day.date].orEmpty(), sources = day.sources + ("_connector" to "Health Connect")) }
        journalDays = mergeDays(journalDays, enrichedDays)
        journalDays=enrichVitalDays(journalDays,"Health Connect",watchVitals.values,watchVitals.selectedSources)
        if (watchVitals.errors.isNotEmpty()) permissionMessage = watchVitals.errors.joinToString(" · ")
        try { withContext(Dispatchers.IO) { journal.save(journalDays) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { permissionMessage = "Die neuen Tagesdaten konnten nicht gespeichert werden. Sie sind nur bis zum Schließen verfügbar." }
        val cutoff = asOf.minusSeconds(Duration.ofDays(30).seconds)
        val nextHistory = scoreTrendHistory.toMutableMap()
        readout.scores.forEach { (scoreId, score) ->
            val value = if (scoreId == ApprovedWellnessScoreEngine.SESSION_LOAD_ID) score.rating0to100 ?: score.score else score.score
            if (value != null) {
                val point = ScoreTrendPoint(score.asOf, value)
                nextHistory[scoreId] = (nextHistory[scoreId].orEmpty().filter { !it.at.isBefore(cutoff) } + point)
                    .distinctBy { it.at }
                    .takeLast(200)
            }
        }
        scoreTrendHistory = nextHistory
        val todayStart = asOf.atZone(calculationZone).toLocalDate().atStartOfDay(calculationZone).toInstant()
        batteryDayTrend = appendBatteryDayReadPoint(
            existing = batteryDayTrend,
            current = readout.batteryDayTrend.singleOrNull(),
            dayStart = todayStart,
            asOf = asOf,
        )
        val statusSummary = readout.scores.values.joinToString { "${it.scoreId}: ${it.status.name}" }
        readMessage = if (sourceObservationsAvailable) {
            "Neue lokale Scores aktualisiert (${statusSummary}); freigegebene Score- und Uhrendaten wurden verarbeitet."
        } else {
            "Neue lokale Scores: passende Leserechte fehlen; es wurden keine Health-Connect-Records gelesen (${statusSummary})."
        }
    }

    suspend fun readGoogleScores() {
        importStep = "Google-Freigabe prüfen"
        val token = GoogleHealthSession.accessToken(activity)
        val goals = withContext(Dispatchers.IO) { TrainingPlanStore(SecureLocalDocument(activity, "training_plan")).read()?.goals }
        val personalProfile = withContext(Dispatchers.IO) { PersonalScoreProfileStore(SecureLocalDocument(activity, "personal_score_profile")).read() }
        val now = Instant.now()
        val imported = GoogleHealthRepository(token) { step -> scope.launch { importStep = "Google Health · $step" } }.read(now, ZoneId.systemDefault(), goals, personalProfile)
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || readsPaused || !GoogleHealthSession.enabled(activity)) return
        val readout = imported.readout
        importStep = "Heutige Scores berechnen"
        val current = withContext(Dispatchers.Default) { dayInsight(readout).let {
            it.copy(metrics=it.metrics+imported.extraMetrics[it.date].orEmpty(),sources=it.sources+("_connector" to "Google Health"))
        } }
        // Today is published before historical reconstruction, which may be cancelled on pause.
        journalDays = mergeDays(journalDays,listOf(current))
        approvedScores = readout.scores
        effectiveProvider = "Google Health"
        permissionMessage = null
        permissionCheckFailed = false
        try {
            withContext(Dispatchers.IO) {
                journal.save(journalDays)
                readout.scores.values.forEach { snapshotStore.upsertEligibleScore(it,now) }
            }
            offlineApprovedScores = offlineApprovedScores + readout.scores.filterValues {it.score!=null}
        } catch(e:CancellationException) {throw e}
        catch(_:Exception) {permissionMessage="Die neuen Google-Tageswerte sind sichtbar, konnten aber noch nicht lokal gespeichert werden."}
        importStep = "Frühere Tage ergänzen"
        var historyFailed=false
        val previous = try { withContext(Dispatchers.Default) {
            val today=now.atZone(ZoneId.systemDefault()).toLocalDate()
            (1L..29L).mapNotNull {offset ->
                kotlinx.coroutines.yield()
                val date=today.minusDays(offset)
                if(offset>2&&journalDays.any {it.date==date&&it.sources["_connector"]=="Google Health"&&
                    it.scores.values.all {score ->score.algorithmVersion.startsWith(ApprovedWellnessScoreEngine.MODEL_VERSION+"/") && score.components["PROFILE_KEY"] == personalProfile.signature}&&
                    it.scores[ApprovedWellnessScoreEngine.SESSION_LOAD_ID]?.algorithmVersion==ApprovedWellnessScoreEngine.WEEKLY_MODEL_VERSION &&
                    it.scores[ApprovedWellnessScoreEngine.SESSION_LOAD_ID]?.components?.get("GOAL_PERCENT")== (goals?.weeklyLoadAdjustments?.get(date.with(java.time.DayOfWeek.MONDAY).toString()) ?: 100)}) return@mapNotNull null
                val at=date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().minusNanos(1)
                val past=historicalScoreReadout(readout,at,readout.normalizedInput.recordTypeWindowCompleteness,v2Model=true)
                dayInsight(past,true).let {it.copy(metrics=it.metrics+imported.extraMetrics[date].orEmpty(),sources=it.sources+("_connector" to "Google Health"))}
            }
        } } catch(e:CancellationException) {throw e}
        catch(_:Exception) {historyFailed=true;emptyList()}
        if(previous.isNotEmpty()) {
            journalDays=mergeDays(journalDays,previous)
            try {withContext(Dispatchers.IO) {journal.save(journalDays)}}
            catch(e:CancellationException) {throw e}
            catch(_:Exception) {permissionMessage="Die Tageswerte sind verfügbar; der ergänzte Verlauf konnte nicht gespeichert werden."}
        }
        journalDays=enrichVitalDays(journalDays,"Google Health",imported.extraMetrics,
            imported.extraMetrics.values.flatMap {it.keys}.distinct().associateWith {"Google Health · Google-/Fitbit-Uhren"})
        try {withContext(Dispatchers.IO) {journal.save(journalDays)}}
        catch(e:CancellationException) {throw e} catch(_:Exception) {permissionMessage="Die zusätzlichen Uhrwerte konnten nicht lokal gespeichert werden."}
        readMessage = "Google Health · ${imported.count} Messwerte aus Google-/Pixel-Watch-Quellen verarbeitet. Health Connect wurde für diesen Stand nicht gelesen."+
            (if(historyFailed) " Frühere Tage konnten nicht vollständig ergänzt werden." else "")+
            imported.optionalErrors.takeIf {it.isNotEmpty()}?.joinToString(prefix=" Zusätzliche Werte: ").orEmpty()
    }

    suspend fun refreshScoreSnapshots(fromPullRefresh: Boolean) {
        if (!readMutex.tryLock()) return
        try {
            if (!snapshotStoreReady || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
            if (readsPaused) return
            val startedAt=Instant.now()
            val changedModel=preferences.getString("last_refresh_score_model",null)!=ApprovedWellnessScoreEngine.MODEL_VERSION
            if(!automaticRefreshDue(lastRefreshAttempt,startedAt,fromPullRefresh||changedModel)) return
            lastRefreshAttempt=startedAt
            val remembered=withContext(Dispatchers.IO) {preferences.edit().putLong(LAST_SCORE_REFRESH_ATTEMPT,startedAt.toEpochMilli()).putString("last_refresh_score_model",ApprovedWellnessScoreEngine.MODEL_VERSION).commit()}
            if(!remembered) {readMessage="Die Aktualisierungszeit konnte nicht gespeichert werden. Dein letzter Stand bleibt sichtbar.";return}
            homeRefreshing=true
            if(fromPullRefresh) {readMessage=null;manualRefreshAt=startedAt}
            if(!GoogleHealthSession.enabled(activity) && effectiveProvider=="Google Health") {effectiveProvider="Health Connect";approvedScores=emptyMap()}
            if (GoogleHealthSession.enabled(activity)) {
                try {
                    readGoogleScores()
                    return
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    permissionMessage = (if(failure is IllegalStateException) failure.message else "Google-Import fehlgeschlagen.") + " Vollständiger Ersatz über Health Connect wird versucht."
                    effectiveProvider = "Health Connect"
                    importStep = "Health Connect als Ersatz laden"
                }
            }
            if (!locallyConnected || !accessController.isConnected()) {
                permissionsVerified = false
                approvedScores = emptyMap()
                if (fromPullRefresh) readMessage = "Health Connect ist getrennt; der letzte lokal gespeicherte Score bleibt sichtbar."
                return
            }
            val currentClient = client
            if (sdkStatus != HealthConnectClient.SDK_AVAILABLE || currentClient == null) {
                permissionCheckFailed = true
                setupGrantedPermissions = emptySet()
                setupPermissionsChecked = true
                setupPermissionCheckFailed = false
                snapshots = snapshots.markSnapshotsFreshnessUnverifiable()
                if (fromPullRefresh) readMessage = "Health Connect ist auf diesem Gerät nicht verfügbar; es wurden keine Daten gelesen."
                return
            }

            val granted = try {
                currentClient.permissionController.getGrantedPermissions()
            } catch (_: Exception) {
                permissionCheckFailed = true
                setupPermissionsChecked = false
                setupPermissionCheckFailed = true
                snapshots = snapshots.markSnapshotsFreshnessUnverifiable()
                if (fromPullRefresh) readMessage = "Health-Connect-Rechte konnten nicht geprüft werden; es wurden keine Daten gelesen und der letzte lokale Stand bleibt erhalten."
                return
            }
            permissionCheckFailed = false
            setupGrantedPermissions = granted
            setupPermissionsChecked = true
            setupPermissionCheckFailed = false
            permissionsVerified = true

            if (readsPaused) {
                if (fromPullRefresh) readMessage = "Lesen ist pausiert; der letzte lokale Snapshot bleibt sichtbar eingefroren."
                return
            }
            if (!shouldReadScoresOnResume(accessController.isConnected(), readsPaused)) return

            // The approved pipeline is the sole dashboard score reader. Legacy snapshot
            // decoding and deletion remain in place for compatibility with existing data.
            try {
                readApprovedScores(currentClient, granted)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: HealthConnectPermissionRevokedException) {
                approvedScores = emptyMap()
                permissionCheckFailed = false
                readMessage = "Health-Connect-Recht wurde während des Score-Reads widerrufen; neue Scores wurden entfernt."
            } catch (_: HealthConnectForegroundRequiredException) {
                readMessage = "Der neue Score-Read wurde beim Verlassen des Vordergrunds abgebrochen; der letzte Arbeitsspeicherstand bleibt erhalten."
            } catch (_: HealthConnectAppDisconnectedException) {
                locallyConnected = false
                accessController.disconnect()
                approvedScores = emptyMap()
                permissionsVerified = false
                readMessage = "Health Connect ist getrennt. Deine gespeicherten Scores und Quellen bleiben erhalten."
            } catch (_: Exception) {
                readMessage = "Der neue lokale Wellness-Score-Read ist fehlgeschlagen; der letzte Arbeitsspeicherstand bleibt erhalten."
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            permissionCheckFailed = true
            if (fromPullRefresh) readMessage = "Health-Connect-Rechte konnten nicht geprüft werden; der letzte gültige lokale Stand bleibt erhalten."
        } finally {
            homeRefreshing = false
            readMutex.unlock()
        }
    }

    val requestSnapshotRead: (Boolean) -> Unit = { fromPullRefresh ->
        if (activeReadJob?.isActive != true) {
            activeReadJob = scope.launch {
                var requested=fromPullRefresh
                do {pendingUserRefresh=false;refreshScoreSnapshots(requested);requested=pendingUserRefresh} while(requested)
            }
        } else if(fromPullRefresh) pendingUserRefresh=true
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { _ ->
        val requestKind = permissionRequestKind
        val requested = requestedPermissions
        scope.launch {
            val actualGranted = rereadActualPermissions()
            val result = actualGranted?.let { HealthConnectSetup.requestResult(requested, it) }
            val fullyGranted = result?.isFullyGranted == true
            val shouldConnectLocally = when (requestKind) {
                PermissionRequestKind.FEATURE -> fullyGranted
                PermissionRequestKind.STANDARD_BULK -> result?.hasAnyGrant == true
                PermissionRequestKind.HISTORY, PermissionRequestKind.MEDICAL, null -> false
            }
            if (shouldConnectLocally && enableLocalAfterGrant) {
                accessController.connect()
                locallyConnected = true
            }
            permissionMessage = when {
                actualGranted == null -> "Berechtigungsstatus konnte nach dem Systemdialog nicht erneut geprüft werden; es wurden dadurch keine weiteren Daten gelesen."
                requestKind == PermissionRequestKind.HISTORY && HealthConnectSetup.historyPermission in actualGranted ->
                    "Historienfreigabe aktiv. Vergangene Tage werden aus deinen Messungen berechnet."
                requestKind == PermissionRequestKind.HISTORY ->
                    "Die separate Historienfreigabe wurde nicht erteilt oder inzwischen widerrufen."
                requestKind == PermissionRequestKind.MEDICAL && result != null ->
                    "Medical-Rechte erneut geprüft: ${result.granted.size} von ${result.requested.size} ausgewählten Einzelrechten freigegeben. Medizinische Records wurden nicht ausgelesen."
                result == null -> "Keine Berechtigungsanfrage erkannt."
                requestKind == PermissionRequestKind.STANDARD_BULK ->
                    "Standardrechte erneut geprüft: ${result.granted.size} von ${result.requested.size} ausgewählten Einzelrechten freigegeben. Nicht freigegebene Typen bleiben gesperrt."
                fullyGranted -> "Alle angefragten Einzelrechte wurden erneut bestätigt."
                result.hasAnyGrant -> "Ein Teil der angefragten Rechte ist aktiv; der Status wird je Typ angezeigt."
                else -> "Keine der angefragten Berechtigungen ist aktuell freigegeben."
            }
            permissionRequestKind = null
            enableLocalAfterGrant = false
            if (
                (shouldConnectLocally || (requestKind == PermissionRequestKind.HISTORY && locallyConnected)) &&
                result?.hasAnyGrant == true &&
                activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            ) {
                activeReadJob?.cancelAndJoin()
                requestSnapshotRead(false)
            }
        }
    }

    val requestFeaturePermissions: (FeaturePermissionRequest) -> Unit = { feature ->
        permissionRequestKind = PermissionRequestKind.FEATURE
        enableLocalAfterGrant = !locallyConnected
        requestedPermissions = feature.permissions
        permissionLauncher.launch(feature.permissions)
    }

    val requestStandardSetup: () -> Unit = {
        val availablePermissions = HealthConnectSetup.availableStandardPermissions(
            sdkAvailable = sdkStatus == HealthConnectClient.SDK_AVAILABLE,
            featureStatuses = setupFeatureStatuses,
        )
        val scorePermissions = HealthConnectPermissions.sleep + HealthConnectPermissions.readiness +
            HealthConnectPermissions.trainingRecovery + HealthConnectPermissions.heartRate +
            HealthConnectPermissions.steps
        val selectedAvailablePermissions = (scorePermissions + WatchVitals.permissions).intersect(availablePermissions) +
            if (HealthConnectSetup.historySupported(sdkStatus == HealthConnectClient.SDK_AVAILABLE, setupFeatureStatuses)) setOf(HealthConnectSetup.historyPermission) else emptySet()
        if (selectedAvailablePermissions.isEmpty()) {
            permissionMessage = "Es ist kein ausgewähltes Standard-Leserecht auf diesem Gerät verfügbar."
        } else {
            permissionRequestKind = PermissionRequestKind.STANDARD_BULK
            enableLocalAfterGrant = !locallyConnected
            requestedPermissions = selectedAvailablePermissions
            permissionLauncher.launch(selectedAvailablePermissions)
        }
    }

    val requestHistoryPermission: () -> Unit = {
        if (HealthConnectSetup.historySupported(sdkStatus == HealthConnectClient.SDK_AVAILABLE, setupFeatureStatuses)) {
            permissionRequestKind = PermissionRequestKind.HISTORY
            enableLocalAfterGrant = false
            requestedPermissions = setOf(HealthConnectSetup.historyPermission)
            permissionLauncher.launch(setOf(HealthConnectSetup.historyPermission))
        } else {
            permissionMessage = "Health Connect meldet die Historienfunktion auf diesem Gerät als nicht verfügbar; es wurde kein History-Recht angefragt."
        }
    }

    val requestMedicalPermissions: () -> Unit = {
        if (HealthConnectSetup.medicalFeatureAvailable(sdkStatus == HealthConnectClient.SDK_AVAILABLE, setupFeatureStatuses)) {
            val selected = selectedMedicalPermissions.intersect(HealthConnectSetup.medicalPermissions)
            if (selected.isEmpty()) {
                permissionMessage = "Wählen Sie mindestens ein medizinisches Einzelrecht für den separaten Health-Records-Rechtebildschirm aus."
            } else {
                permissionRequestKind = PermissionRequestKind.MEDICAL
                enableLocalAfterGrant = false
                requestedPermissions = selected
                permissionLauncher.launch(selected)
            }
        } else {
            permissionMessage = "FEATURE_PERSONAL_HEALTH_RECORD ist auf diesem Gerät nicht verfügbar; es wurde kein Medical-Recht angefragt."
        }
    }

    DisposableEffect(activity.lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    permissionsVerified = false
                    permissionCheckFailed = false
                    resumeGeneration += 1
                }
                Lifecycle.Event.ON_PAUSE -> activeReadJob?.cancel()
                else -> Unit
            }
        }
        activity.lifecycle.addObserver(observer)
        if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            permissionsVerified = false
            permissionCheckFailed = false
            resumeGeneration += 1
        }
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(snapshotStore) {
        try {
            journalDays = withContext(Dispatchers.IO) { journal.read() }
            if(GoogleHealthSession.enabled(activity)) effectiveProvider=journalDays.lastOrNull()?.sources?.get("_connector") ?: "Google Health"
            if(lastRefreshAttempt==null) lastRefreshAttempt=journalDays.map {it.calculatedAt}.filter {it<=Instant.now()}.maxOrNull()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { permissionMessage = "Der Tagesverlauf konnte nicht geöffnet werden. Der letzte Score wird separat geladen." }
        try {
            run {
                val restored = withContext(Dispatchers.IO) { snapshotStore.readAll() }
                snapshots = restored.mapNotNull { persisted ->
                    val featureId = persisted.featureIdOrNull() ?: return@mapNotNull null
                    featureId to FeatureReadSnapshot(
                        recordCount = 0,
                        readAt = LocalDateTime.ofInstant(persisted.lastSuccessfulReadAt, ZoneId.systemDefault()),
                        score = persisted.toScoreResult(),
                        scoreOnlySnapshot = true,
                        freshness = SnapshotFreshness.STALE,
                    )
                }.toMap()
                val restoredApproved = restored.mapNotNull { it.approvedScoreSnapshotOrNull() }
                offlineApprovedScores = restoredApproved.associate { it.score.scoreId to it.score }
                offlineScoreReadAt = restoredApproved.associate { it.score.scoreId to it.lastSuccessfulReadAt }
                scoreTrendHistory = emptyMap()
                batteryDayTrend = emptyList()
            }
        } catch (_: Exception) {
            snapshots = emptyMap()
            offlineApprovedScores = emptyMap()
            offlineScoreReadAt = emptyMap()
            scoreTrendHistory = emptyMap()
            batteryDayTrend = emptyList()
            readMessage = "Der lokale Score-Snapshot konnte nicht authentifiziert oder gelesen werden."
            try {
                withContext(Dispatchers.IO) { snapshotStore.clear() }
            } catch (_: Exception) {
                readMessage = "Der lokale Score-Snapshot war unlesbar und konnte nicht vollständig gelöscht werden."
            }
        } finally {
            snapshotStoreReady = true
        }
    }

    LaunchedEffect(resumeGeneration, selectedTab, client) {
        if (selectedTab == BlockDTab.SETTINGS) {
            rereadActualPermissions()
            sourceBindings = sourceBindingStore.allBindings().associateBy { it.recordType }
        }
    }

    LaunchedEffect(resumeGeneration, snapshotStoreReady) {
        if (resumeGeneration > 0 && snapshotStoreReady) requestSnapshotRead(false)
    }

    val latestRead = approvedScores.values.maxByOrNull { it.asOf }?.asOf?.atZone(ZoneId.systemDefault())?.toLocalDateTime()
    val lastReadHint = latestRead?.let { "Zuletzt berechnet um ${formatTime(it)}" }
    LaunchedEffect(snapshotStoreReady, resumeGeneration) {
        while(true) {
            kotlinx.coroutines.delay(60_000)
            if(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)&&snapshotStoreReady) requestSnapshotRead(false)
        }
    }

    val latestSnapshotRead = offlineScoreReadAt.values.maxOrNull()?.let {
        DateTimeFormatter.ofPattern("d. MMM · HH:mm", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
            .format(it)
    }
    val dataHint = when {
        !locallyConnected -> "Health Connect getrennt · ${latestSnapshotRead?.let { "letzter Stand $it" } ?: "noch kein gespeicherter Score"}"
        !snapshotStoreReady -> "Lokaler Status wird geladen"
        !permissionsVerified && permissionCheckFailed -> "Health-Connect-Rechte nicht prüfbar · ${latestSnapshotRead?.let { "letzter Snapshot $it" } ?: lastReadHint ?: "kein freigegebener Stand"}"
        !permissionsVerified -> "Health-Connect-Rechte werden geprüft · ${latestSnapshotRead?.let { "letzter Snapshot $it" } ?: lastReadHint ?: "kein freigegebener Stand"}"
        readsPaused -> "Lesen pausiert · ${latestSnapshotRead?.let { "letzter Stand $it" } ?: lastReadHint ?: "kein freigegebener Stand"}"
        latestRead == null -> "Health Connect · ${latestSnapshotRead?.let { "gespeicherter Stand $it" } ?: "noch kein freigegebener Score-Read"}"
        else -> "Health Connect · $lastReadHint"
    }

    BackHandler(enabled = scoreDetailId != null || (selectedTab == BlockDTab.SETTINGS && healthConnectSettingsOpen)) {
        if (scoreDetailId != null) scoreDetailId = null else healthConnectSettingsOpen = false
    }

    val liveAccess = (permissionsVerified && locallyConnected && !permissionCheckFailed) || (effectiveProvider == "Google Health" && approvedScores.isNotEmpty())
    val lastStored = journalDays.lastOrNull { it.date == java.time.LocalDate.now() }?.scores.orEmpty()
    val compatibleSaved = if (journalDays.lastOrNull { it.date == LocalDate.now() }?.sources?.get("_connector") == effectiveProvider) lastStored else emptyMap()
    val unfilteredDisplay = if (liveAccess) approvedScores + compatibleSaved.filterKeys { canReuseCachedScore(approvedScores[it],compatibleSaved[it]) }.mapValues { (id, saved) ->
        saved.copy(coverage = saved.coverage.copy(missingReasons = approvedScores[id]?.coverage?.missingReasons.orEmpty() + saved.coverage.missingReasons))
    }
        else (if (lastStored.isNotEmpty()) lastStored else offlineApprovedScores).mapValues { (_, score) -> score.copy(status = ApprovedWellnessScoreEngine.Status.stale, stale = true) }
    val dayDisplay=unfilteredDisplay.mapValues {(_,score)->scoreForDisplayDay(score,LocalDate.now(),ZoneId.systemDefault())}
    val savedEnergy=journalDays.mapNotNull {it.scores[ApprovedWellnessScoreEngine.BODY_BATTERY_ID]}+
        listOfNotNull(offlineApprovedScores[ApprovedWellnessScoreEngine.BODY_BATTERY_ID])
    val displayed=withLastSavedEnergy(dayDisplay,savedEnergy,Instant.now())
    androidx.compose.runtime.key(productGeneration) {
        RobotCircadianHost { ProductExperience(
            scores = displayed, days = journalDays, refreshing = homeRefreshing,
            sourceName = effectiveProvider, importStep = importStep,manualRefreshAt=manualRefreshAt,
            status = when {
                homeRefreshing -> "Deine Uhrendaten werden aktualisiert …"
                !locallyConnected && !GoogleHealthSession.enabled(activity) -> "Offline · dein gespeicherter Stand bleibt verfügbar"
                readsPaused -> "Aktualisierung pausiert"
                permissionCheckFailed && effectiveProvider != "Google Health" -> "Zugriff derzeit nicht möglich · gespeicherter Stand"
                else -> journalDays.lastOrNull()?.calculatedAt?.atZone(ZoneId.systemDefault())?.let {
                    RocoStrings.translate("Zuletzt aktualisiert")+" ${it.format(DateTimeFormatter.ofPattern("d. MMM · HH:mm", Locale.getDefault()))}"
                } ?: "Bereit für deine ersten Messungen"
            }, connected = locallyConnected || GoogleHealthSession.enabled(activity), paused = readsPaused, message = permissionMessage ?: readMessage,
            onRefresh = { requestSnapshotRead(true) }, onConnect = requestStandardSetup, onHistory = requestHistoryPermission,
            onPause = { paused ->
                if (paused) activeReadJob?.cancel()
                preferences.edit().putBoolean(LOCAL_READ_PAUSED, paused).apply()
                readsPaused = paused
                if (!paused) requestSnapshotRead(true)
            },
            onDisconnect = {
                activeReadJob?.cancel()
                try { accessController.disconnect(); locallyConnected = false; permissionsVerified = false; approvedScores = emptyMap() }
                catch (_: Exception) { permissionMessage = "Verbindung konnte nicht getrennt werden." }
            },
            onDelete = { deletionResult = null; showDeleteConfirmation = true },
            sourceBindings = sourceBindings, observedSources = observedSourceOrigins,
            onBind = { type, origin ->
                scope.launch {
                    try {
                        activeReadJob?.cancelAndJoin()
                        withContext(Dispatchers.IO) { sourceBindingStore.bind(type, origin) }
                        sourceBindings = sourceBindingStore.allBindings().associateBy { it.recordType }
                        requestSnapshotRead(true)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { permissionMessage = "Die Datenquelle konnte nicht gespeichert werden." }
                }
            },
        )
        }
    }

    val eraseLocalData: () -> Unit = {
        activeReadJob?.cancel()
        scope.launch {
        val result = withContext(Dispatchers.IO) { LocalAppDataEraser.erase(activity, accessController, snapshotStore, sourceBindingStore) }
        journalDays = emptyList()
        productGeneration++
        locallyConnected = false
        readsPaused = preferences.getBoolean(LOCAL_READ_PAUSED, false)
        permissionsVerified = false
        snapshots = emptyMap()
        approvedScores = emptyMap()
        offlineApprovedScores = emptyMap()
        offlineScoreReadAt = emptyMap()
        scoreTrendHistory = emptyMap()
        batteryDayTrend = emptyList()
        sourceBindings = sourceBindingStore.allBindings().associateBy { it.recordType }
        observedSourceOrigins = emptyMap()
        sourceObservationsAvailable = false
        readMessage = null
        deletionResult = result
        permissionMessage = result.userMessage
        if (result.isComplete) showDeleteConfirmation = false
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = {
                RocoText(
                    when (deletionResult?.status) {
                        LocalAppDataDeletionStatus.SUCCESS -> "Löschung abgeschlossen"
                        LocalAppDataDeletionStatus.PARTIAL_FAILURE -> "Löschung teilweise fehlgeschlagen"
                        LocalAppDataDeletionStatus.FAILURE -> "Löschen fehlgeschlagen"
                        null -> "Lokale App-Daten löschen?"
                    },
                )
            },
            text = {
                RocoText(
                    deletionResult?.userMessage
                        ?: "Gelöscht werden nur lokale Dateien, Einstellungen und temporäre App-Daten. Health-Connect-Quelldaten und Berechtigungen bleiben unverändert.",
                )
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.heightIn(min = 48.dp),
                    onClick = when (deletionResult?.status) {
                        null -> eraseLocalData
                        LocalAppDataDeletionStatus.SUCCESS -> ({ showDeleteConfirmation = false })
                        LocalAppDataDeletionStatus.PARTIAL_FAILURE,
                        LocalAppDataDeletionStatus.FAILURE -> eraseLocalData
                    },
                ) {
                    RocoText(
                        when (deletionResult?.status) {
                            null -> "Löschen"
                            LocalAppDataDeletionStatus.SUCCESS -> "Fertig"
                            LocalAppDataDeletionStatus.PARTIAL_FAILURE,
                            LocalAppDataDeletionStatus.FAILURE -> "Erneut versuchen"
                        },
                    )
                }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.heightIn(min = 48.dp),
                    onClick = { showDeleteConfirmation = false },
                ) {
                    RocoText(if (deletionResult == null) "Abbrechen" else "Schließen")
                }
            },
        )
    }
}

@Composable
@OptIn(ExperimentalPersonalHealthRecordApi::class)
private fun SettingsScreen(
    locallyConnected: Boolean,
    sdkAvailable: Boolean,
    featureStatuses: Map<Int, Int?>,
    grantedPermissions: Set<String>,
    sourceBindings: Map<String, PrimarySourceBinding>,
    observedSourceOrigins: Map<String, Set<String>>,
    sourceObservationsAvailable: Boolean,
    permissionStatusKnown: Boolean,
    permissionStatusCheckFailed: Boolean,
    selectedStandardPermissions: Set<String>,
    selectedMedicalPermissions: Set<String>,
    readsPaused: Boolean,
    permissionMessage: String?,
    onBack: () -> Unit,
    onStandardSelectionChanged: (Set<String>) -> Unit,
    onMedicalSelectionChanged: (Set<String>) -> Unit,
    onRequestStandardSetup: () -> Unit,
    onRequestHistoryPermission: () -> Unit,
    onRequestMedicalPermissions: () -> Unit,
    onRefreshSources: () -> Unit,
    onBindSource: (recordType: String, packageName: String) -> Unit,
    onRemoveSourceBinding: (recordType: String) -> Unit,
    onPauseChanged: (Boolean) -> Unit,
    onDisconnect: () -> Unit,
    onDelete: () -> Unit,
) {
    val availableStandardPermissions = HealthConnectSetup.availableStandardPermissions(sdkAvailable, featureStatuses)
    val selectedAvailablePermissions = selectedStandardPermissions.intersect(availableStandardPermissions)
    val sourcePermissionStatusKnown = permissionStatusKnown && !permissionStatusCheckFailed
    val sourcePermissionGrantedByRecordType = mapOf(
        ApprovedWellnessRecordTypes.SLEEP_SESSION to hasAllGrantedPermissions(HealthConnectPermissions.sleep, grantedPermissions),
        ApprovedWellnessRecordTypes.HRV_RMSSD to hasAllGrantedPermissions(HealthConnectPermissions.hrv, grantedPermissions),
        ApprovedWellnessRecordTypes.RESTING_HEART_RATE to hasAllGrantedPermissions(HealthConnectPermissions.restingHeartRate, grantedPermissions),
        ApprovedWellnessRecordTypes.EXERCISE_SESSION to hasAllGrantedPermissions(HealthConnectPermissions.trainingRecovery, grantedPermissions),
        ApprovedWellnessRecordTypes.HEART_RATE to hasAllGrantedPermissions(HealthConnectPermissions.heartRate, grantedPermissions),
        ApprovedWellnessRecordTypes.STEPS to hasAllGrantedPermissions(HealthConnectPermissions.steps, grantedPermissions),
    )
    val historyStatus = HealthConnectSetup.historyStatus(
        sdkAvailable = sdkAvailable,
        featureStatuses = featureStatuses,
        grantedPermissions = grantedPermissions,
        statusKnown = permissionStatusKnown,
    )
    val phrFeatureStatus = featureStatuses[HealthConnectFeatures.FEATURE_PERSONAL_HEALTH_RECORD]
    Box(Modifier.fillMaxSize()) {
        MintRadialBackdrop(Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        TextButton(modifier = Modifier.heightIn(min = 48.dp), onClick = onBack) { RocoText("← Einstellungen") }
        RocoText("Health Connect einrichten", style = MaterialTheme.typography.headlineMedium)
        StatusPill(
            text = if (locallyConnected) "App-Zugriff aktiviert · Rechte werden vor jedem Lesen erneut geprüft." else "Health Connect nicht verbunden",
            connected = locallyConnected,
            compact = false,
        )
        DetailCard(title = "Lesen im Vordergrund") {
            RocoText(
                "Health-Connect-Reads laufen nur im Vordergrund, etwa beim Öffnen oder Aktualisieren. Medical-/PHR-Records werden in diesem UI-Block weder gelesen noch angezeigt.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RocoText("Health-Connect-Rechte", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        DetailCard(title = "Deine drei Scores verbinden") {
            RocoText(
                "Einmal im Android-Dialog freigeben: Schlaf, HRV, Ruhepuls, Training, Puls und Schritte. ROCO fragt keine anderen Datentypen an.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RocoText(
                if (permissionStatusCheckFailed || !permissionStatusKnown) "Rechtestatus wird geprüft"
                else "${selectedAvailablePermissions.count { it in grantedPermissions }} von ${selectedAvailablePermissions.size} nötigen Freigaben aktiv",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (permissionStatusCheckFailed) {
                RocoText("Der aktuelle Rechte-Status konnte nicht vom Health-Connect-Provider gelesen werden.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } else if (!permissionStatusKnown && sdkAvailable) {
                RocoText("Berechtigungsstatus wird geprüft …", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                enabled = selectedAvailablePermissions.isNotEmpty(),
                shape = RoundedCornerShape(14.dp),
                onClick = onRequestStandardSetup,
            ) {
                RocoText(if (locallyConnected) "Health-Connect-Rechte prüfen" else "Mit Health Connect verbinden")
            }
            RocoText("Du kannst einzelne Rechte jederzeit direkt in Android ändern.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        var showAdvanced by rememberSaveable { mutableStateOf(false) }
        TextButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.heightIn(min = 48.dp)) {
            RocoText(if (showAdvanced) "⌃ Weniger Einstellungen" else "⌄ Erweiterte Einstellungen")
        }
        if (showAdvanced) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PrimarySourceBindingsSettingsCard(
            bindings = sourceBindings,
            observedOrigins = observedSourceOrigins,
            observationsAvailable = sourceObservationsAvailable,
            permissionGrantedByRecordType = sourcePermissionGrantedByRecordType,
            permissionStatusKnown = sourcePermissionStatusKnown,
            locallyConnected = locallyConnected,
            readsPaused = readsPaused,
            onRefreshSources = onRefreshSources,
            onBindSource = onBindSource,
            onRemoveBinding = onRemoveSourceBinding,
        )
        DetailCard(title = "Medical-/PHR-Status") {
            val phrStatus = when {
                !sdkAvailable -> "Plattform nicht verfügbar"
                phrFeatureStatus == null -> "Verfügbarkeit nicht prüfbar"
                phrFeatureStatus == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE -> "Feature verfügbar"
                else -> "Feature nicht verfügbar"
            }
            RocoText("Status: $phrStatus", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            RocoText("Keine Medical-/PHR-Records werden in Block D gelesen oder angezeigt.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DetailCard(title = "Vordergrund-Reads") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    RocoText("Lesen pausieren", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    RocoText(
                        "Stoppt neue Health-Connect-Reads und friert den letzten Snapshot sichtbar ein.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = readsPaused, onCheckedChange = onPauseChanged)
            }
        }
        DetailCard(title = "Zugriff und Daten") {
            RocoText("Trennen stoppt neue Reads. Der letzte Score bleibt lokal gespeichert. Health-Connect-Daten und Rechte bleiben unverändert.", style = MaterialTheme.typography.bodySmall)
            Button(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp),
                onClick = onDisconnect,
            ) {
                RocoText("Health-Connect-Zugriff trennen")
            }
            RocoText("Lokale App-Daten löschen entfernt App-Dateien und Einstellungen. Daten und Rechte in Health Connect bleiben unverändert.", style = MaterialTheme.typography.bodySmall)
            Button(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp),
                onClick = onDelete,
            ) {
                RocoText("Lokale App-Daten löschen")
            }
        }
        }
        permissionMessage?.let {
            RocoText(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RocoText(
            "Die Kategorienfarben haben keine Bewertungsbedeutung. Status wird ausgeschrieben; dynamische Systemfarben sind deaktiviert.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        }
    }
}

@Composable
private fun StandardPermissionRow(
    type: StandardReadType,
    status: SetupPermissionStatus,
    selected: Boolean,
    enabled: Boolean,
    onSelectionChanged: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = selected, enabled = enabled, onCheckedChange = onSelectionChanged)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            RocoText(type.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            RocoText(type.purpose, style = MaterialTheme.typography.bodySmall)
            RocoText(
                "${type.category.sensitivity} · Zugriff: ${status.label}${if (!enabled) " · Feature auf diesem Gerät nicht verfügbar" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = if (status == SetupPermissionStatus.UNSUPPORTED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun DetailCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RocoText(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

private fun featureSnapshotStatus(snapshot: FeatureReadSnapshot?, locallyConnected: Boolean): String = when {
    !locallyConnected -> "Health Connect nicht verbunden"
    snapshot == null -> "Noch nicht abgefragt"
    snapshot.scoreOnlySnapshot -> "Letzter lokaler Score · ${scoreStatusLabel(snapshot.score.status)}"
    else -> scoreStatusLabel(snapshot.score.status)
}

private fun formatScoreHeadline(score: ScoreResult): String =
    "${score.value0to100?.let { "$it / 100" } ?: "Kein Score"} · ${scoreStatusLabel(score.status)}"

private fun scoreStatusLabel(status: ScoreStatus): String = when (status) {
    ScoreStatus.AVAILABLE -> "Verfügbar"
    ScoreStatus.PROVISIONAL -> "Vorläufig"
    ScoreStatus.INSUFFICIENT_DATA -> "Zu wenig Daten"
    ScoreStatus.PERMISSION_MISSING -> "Berechtigung fehlt"
    ScoreStatus.STALE -> "Veraltet"
    ScoreStatus.SOURCE_CONFLICT -> "Quellenkonflikt"
}

private fun componentName(id: String): String = when (id) {
    "duration" -> "Schlafdauer"
    "regularity" -> "Regelmäßigkeit"
    "hrv_rmssd_ms" -> "HRV RMSSD"
    "resting_heart_rate_bpm" -> "Ruhepuls"
    "time_since_last_workout" -> "Zeit seit Training"
    else -> id
}

private fun componentDetails(component: ScoreComponent): String? {
    val details = component.details
    fun number(key: String): Double? = (details[key] as? Number)?.toDouble()
    fun display(value: Double?): String = value?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "–"
    return when (component.id) {
        "hrv_rmssd_ms", "resting_heart_rate_bpm" ->
            "Aktuell ${display(number("currentMedian"))} ${details["unit"]} · Baseline ${display(number("baselineMedian"))} ${details["unit"]} · ${details["baselineDays"] ?: 0} Baseline-Tage"
        "duration" ->
            "${display(number("sleepMinutes"))} von ${display(number("goalMinutes"))} Schlafminuten · Mittelpunkt ${details["midpointLocalTime"] ?: "–"} (${display(number("midpointMinutes"))} min)"
        "time_since_last_workout" ->
            "Sitzung ${display(number("sessionDurationMinutes"))} min · seit Ende ${display(number("hoursSinceSessionEnd"))} h · angenommene Ruhezeit ${display(number("assumedRestHours"))} h; reine Heuristik"
        else -> null
    }
}

private fun humanizeReason(reason: String): String = when (reason) {
    "no_qualifying_main_sleep_session" -> "Keine geeignete Hauptschlafsitzung gefunden."
    "sleep_stages_missing_session_duration_used_as_estimated_upper_bound" -> "Schlafstadien fehlen; die Sitzungsdauer ist eine Schätzung der oberen Grenze."
    "no_measurements_for_a_complete_local_day" -> "Keine Messungen für einen vollständigen lokalen Tag."
    "no_current_training_recorded_in_last_7_days" -> "Kein Training in den letzten sieben Tagen aufgezeichnet."
    "readiness_metrics_use_different_sources_without_primary_source" -> "HRV und Ruhepuls stammen aus unterschiedlichen Quellen."
    else -> reason.replace('_', ' ')
}

private fun formatDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.getDefault()))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

private fun formatTime(dateTime: LocalDateTime): String =
    dateTime.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))

private const val LOCAL_ACCESS_PREFERENCES = "health_coach_local_access"
private const val LOCAL_READ_PAUSED = "health_coach_reads_paused"
