package com.wabbo.healthconnectcoach

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine as Engine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import org.json.JSONObject

@Composable
internal fun ProductExperience(
    scores: Map<String, Engine.ScoreResult>, days: List<DayInsight>, refreshing: Boolean,
    status: String, connected: Boolean, paused: Boolean, message: String?,
    onRefresh: () -> Unit, onConnect: () -> Unit, onHistory: () -> Unit,
    onPause: (Boolean) -> Unit, onDisconnect: () -> Unit, onDelete: () -> Unit,
    sourceBindings: Map<String, PrimarySourceBinding>, observedSources: Map<String, Set<String>>, onBind: (String, String) -> Unit,
    sourceName: String = "Health Connect", importStep: String = "Uhrendaten abrufen",manualRefreshAt: Instant?=null,
) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var today by remember {mutableStateOf(LocalDate.now())}
    val prefs = remember { context.getSharedPreferences("robot_companion", Context.MODE_PRIVATE) }
    var person by remember { mutableStateOf(prefs.getString("user_name", "").orEmpty()) }
    var robot by remember { mutableStateOf(prefs.getString("name", "Pip") ?: "Pip") }
    var onboarding by rememberSaveable { mutableStateOf(prefs.getInt("onboarding_version",0)<3) }
    val personalStore=remember {PersonalScoreProfileStore(SecureLocalDocument(context,"personal_score_profile"))}
    var personalProfile by remember {mutableStateOf(com.wabbo.healthconnectcoach.score.PersonalScoreProfile())}
    var personalReady by remember {mutableStateOf(false)}
    var personalEditor by rememberSaveable {mutableStateOf(false)}
    val memoryStore=remember {CoachMemoryStore(SecureLocalDocument(context,"coach_memory"))}
    val sleepStore=remember {SleepPlannerStore(SecureLocalDocument(context,"sleep_planner"))}
    var memory by remember {mutableStateOf(CoachMemory())};var memoryReady by remember {mutableStateOf(false)}
    var sleepSettings by remember {mutableStateOf<SleepPlannerSettings?>(null)};var sleepReady by remember {mutableStateOf(false)}
    var memoryError by remember {mutableStateOf<String?>(null)};var sleepError by remember {mutableStateOf<String?>(null)}
    var featureSaving by remember {mutableStateOf(false)}
    val planStore = remember { TrainingPlanStore(SecureLocalDocument(context,"training_plan")) }; val planMutex = remember { Mutex() }
    var plan by remember { mutableStateOf<TrainingPlan?>(null) };var undo by remember { mutableStateOf<TrainingPlan?>(null) }
    var planReady by remember { mutableStateOf(false) };var planError by remember { mutableStateOf<String?>(null) };var saving by remember { mutableStateOf(false) }
    var profileReturnTab by rememberSaveable {mutableStateOf("Heute")}
    var tab by rememberSaveable { mutableStateOf("Heute") };var route by rememberSaveable { mutableStateOf<String?>(null) }
    var followingToday by rememberSaveable {mutableStateOf(true)};var chosenDate by rememberSaveable { mutableStateOf(today.toString()) };var coachDate by rememberSaveable {mutableStateOf(today.toString())};var sourceDialog by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<PlannedSession?>(null) };var activity by remember { mutableStateOf<ActivityInsight?>(null) }
    var sessionEditor by rememberSaveable {mutableStateOf(false)};var editedSessionId by rememberSaveable {mutableStateOf<String?>(null)}
    val editedSession=plan?.sessions?.firstOrNull {it.id==editedSessionId}
    var goalEditor by rememberSaveable { mutableStateOf(false) };var toast by remember { mutableStateOf<String?>(null) };var award by remember { mutableStateOf(false) }
    val aiStore = remember { AiConfigurationStore(SecureLocalDocument(context,"ai_configuration")) }
    var ai by remember { mutableStateOf(AiConfiguration()) };var aiReady by remember { mutableStateOf(false) };var aiStatus by remember { mutableStateOf<String?>(null) };var aiSaving by remember { mutableStateOf(false) }
    val conversation = remember { CoachConversationState() };val snackbar = remember { SnackbarHostState() }
    val wellnessStore=remember {WellnessJournalStore(SecureLocalDocument(context,"wellness_journal"))}
    var wellnessJournal by remember {mutableStateOf(WellnessJournal())};var journalReady by remember {mutableStateOf(false)}
    var journalSaving by remember {mutableStateOf(false)};var journalError by remember {mutableStateOf<String?>(null)}
    var journalDate by rememberSaveable {mutableStateOf(today.toString())};var journalClock by remember {mutableStateOf(LocalTime.now())}
    var journalNotice by remember {mutableStateOf<String?>(null)};val journalMutex=remember {Mutex()}
    var journalInsightsLoading by remember {mutableStateOf(false)}
    val completedJournalEntries=wellnessJournal.entries.filter {it.completedAt!=null}
    val journalEvidenceKey=listOf(completedJournalEntries,wellnessJournal.activeIds,wellnessJournal.habits,days,today)
    var journalCalculatedKey by remember {mutableStateOf<List<Any>?>(null)}
    val journalResults by produceState<List<JournalInsight>>(emptyList(),completedJournalEntries,wellnessJournal.activeIds,wellnessJournal.habits,days,today) {
        journalInsightsLoading=true
        try {value=withContext(Dispatchers.Default) {journalInsights(wellnessJournal,days,today)};journalCalculatedKey=journalEvidenceKey}
        catch(e:CancellationException) {throw e} catch(_:Exception) {value=emptyList();journalError="Die Muster konnten nicht berechnet werden. Deine Einträge bleiben erhalten."}
        finally {journalInsightsLoading=false}
    }
    val chatStore=remember {CoachConversationStore(SecureLocalDocument(context,"coach_conversation"))};val chatMutex=remember {Mutex()};var chatReady by remember {mutableStateOf(false)}
    LaunchedEffect(Unit) {
        while(true) { kotlinx.coroutines.delay(60_000);today=LocalDate.now();journalClock=LocalTime.now() }
    }
    LaunchedEffect(today) { if(followingToday) chosenDate=today.toString() }
    val selectedDay = days.lastOrNull { it.date.toString()==chosenDate }
    val todayDay = days.lastOrNull { it.date==today }
    val selectedScores = if(chosenDate==today.toString()) scores else selectedDay?.scores.orEmpty()
    val energyScore=selectedScores[Engine.BODY_BATTERY_ID]
    val energyContextDay=if(energyScore?.stale==true&&energyScore.asOf!=selectedDay?.scores?.get(Engine.BODY_BATTERY_ID)?.asOf)
        days.lastOrNull {it.scores[Engine.BODY_BATTERY_ID]?.asOf==energyScore.asOf&&it.scores[Engine.BODY_BATTERY_ID]?.score==energyScore.score} else selectedDay
    val nightPlan=planTonight(sleepSettings ?: SleepPlannerSettings(),days,today,sourceName,ZoneId.systemDefault(),today.atTime(journalClock).atZone(ZoneId.systemDefault()).toInstant(),plan?.goals,personalProfile)
    val contextDay=if(route==Engine.BODY_BATTERY_ID) energyContextDay else if(tab=="Coach"&&route==null) days.lastOrNull {it.date.toString()==coachDate} else if(tab=="Heute"||route!=null) selectedDay else todayDay
    val source = contextDay?.sources?.get("_connector") ?: if(route==Engine.BODY_BATTERY_ID&&energyScore?.stale==true) "Lokaler gespeicherter Stand" else sourceName

    LaunchedEffect(Unit) {
        try {memory=withContext(Dispatchers.IO) {memoryStore.read()};memoryReady=true}
        catch(e:CancellationException) {throw e} catch(_:Exception) {memoryError="Dein Gedächtnis konnte nicht geöffnet werden. Es wird nicht überschrieben."}
        try {sleepSettings=withContext(Dispatchers.IO) {sleepStore.read()};sleepReady=true}
        catch(e:CancellationException) {throw e} catch(_:Exception) {sleepError="Dein Schlafplaner konnte nicht geöffnet werden. Gespeicherte Angaben bleiben erhalten."}
        try { personalProfile=withContext(Dispatchers.IO) {personalStore.read()};personalReady=true }
        catch(e:CancellationException) {throw e} catch(_:Exception) {planError="Deine persönlichen Angaben konnten nicht geöffnet werden. Sie wurden nicht überschrieben."}
        try {
            val loaded = withContext(Dispatchers.IO) {
                val original=planStore.read();val previous=planStore.previous()
                val extended=original?.let {TrainingPlanLogic.extend(it,today)}
                if(extended!=original&&extended!=null) planStore.save(extended,previous)
                extended to previous
            }; plan=loaded.first;undo=loaded.second;planReady=true
        } catch(e:CancellationException) { throw e } catch(_:Exception) { planError="Dein Trainingsplan konnte nicht geöffnet werden. Gespeicherte Daten wurden nicht überschrieben." }
        try { ai=withContext(Dispatchers.IO) { aiStore.read() };aiReady=true }
        catch(e:CancellationException) { throw e } catch(_:Exception) { aiStatus="KI-Einstellungen konnten nicht geöffnet werden." }
        try {wellnessJournal=withContext(Dispatchers.IO) {wellnessStore.read()};journalReady=true}
        catch(e:CancellationException) {throw e} catch(_:Exception) {journalError="Das Logbuch konnte nicht geöffnet werden. Gespeicherte Einträge wurden nicht überschrieben."}
        try {val loaded=withContext(Dispatchers.IO) {chatStore.readSnapshot()};conversation.messages=loaded.messages;conversation.previousMessages=loaded.previous;chatReady=true}
        catch(e:CancellationException) {throw e} catch(_:Exception) {toast="Der letzte Chat konnte nicht geöffnet werden."}
    }
    LaunchedEffect(conversation.messages,conversation.previousMessages,chatReady) {
        if(chatReady) {
            val snapshot=CoachConversationSnapshot(conversation.messages,conversation.previousMessages)
            try {chatMutex.withLock {withContext(Dispatchers.IO) {chatStore.saveSnapshot(snapshot)}}}
            catch(e:CancellationException) {throw e} catch(_:Exception) {toast="Der Chat konnte nicht lokal gespeichert werden."}
        }
    }
    fun saveMemory(next:CoachMemory,done:()->Unit) {
        if(featureSaving||!memoryReady) return
        featureSaving=true
        scope.launch {
            try {withContext(Dispatchers.IO) {memoryStore.save(next)};memory=next;memoryError=null;done();toast="Coach-Gedächtnis gespeichert."}
            catch(e:CancellationException) {throw e} catch(e:Exception) {memoryError=e.message ?: "Die Erinnerung konnte nicht gespeichert werden."}
            finally {featureSaving=false}
        }
    }
    fun saveSleep(next:SleepPlannerSettings,done:()->Unit) {
        if(featureSaving||!sleepReady) return
        featureSaving=true
        scope.launch {
            try {withContext(Dispatchers.IO) {sleepStore.save(next)};sleepSettings=next;sleepError=null;done();toast="Schlafplan gespeichert."}
            catch(e:CancellationException) {throw e} catch(_:Exception) {sleepError="Der Schlafplan konnte nicht gespeichert werden."}
            finally {featureSaving=false}
        }
    }
    fun saveJournal(next: WellnessJournal,onSaved: ()->Unit) {scope.launch {
        journalSaving=true;journalError=null
        try {journalMutex.withLock {
            check(journalReady) {"Dein Logbuch wird noch geöffnet."}
            val transition=prepareJournalSave(wellnessJournal,next,today)
            withContext(Dispatchers.IO) {wellnessStore.save(transition.journal)}
            wellnessJournal=transition.journal
            if(transition.celebrate) award=true
            journalNotice=if(transition.newCompletion) "Dein Check-in ist gespeichert." else "Logbuch gespeichert."
            onSaved()
        }} catch(e:CancellationException) {throw e} catch(e:Exception) {journalError=e.message ?: "Dein Logbuch konnte nicht gespeichert werden."}
        finally {journalSaving=false}
    }}
    LaunchedEffect(journalNotice) {journalNotice?.let {showAppNotice(snackbar,it,false);journalNotice=null}}
    suspend fun savePlan(next: TrainingPlan, expected: Long? = plan?.revision, keepUndo: Boolean = true, maintenance: Boolean = false) {
        planMutex.withLock {
            check(planReady) { "Der gespeicherte Plan ist noch nicht verfügbar." }
            val before=plan; check(before?.revision==expected) { "Dein Plan wurde inzwischen geändert. Bitte den Auftrag erneut stellen." }
            val updated=next.copy(revision=(before?.revision ?: 0)+1,celebrated=next.celebrated+before?.celebrated.orEmpty())
            withContext(Dispatchers.IO) { planStore.save(updated, if(maintenance) undo else if(keepUndo) before else null) }
            if(keepUndo&&!maintenance) undo=before
            plan=updated
            if(before?.goals!=updated.goals) onRefresh()
        }
    }
    fun mutate(next: TrainingPlan, text: String, onSaved: ()->Unit = {}) { val expected=plan?.revision;scope.launch {
        saving=true
        try { planError=null;savePlan(next,expected);toast=text;onSaved() }
        catch(e:CancellationException) { throw e } catch(e:Exception) { planError=e.message ?: "Plan konnte nicht gespeichert werden." }
        finally { saving=false }
    } }
    suspend fun restorePlan() {
        try { undo?.let { previous -> savePlan(TrainingPlanLogic.extend(previous,today),keepUndo=false);undo=null;toast="Planänderung rückgängig gemacht." } }
        catch(e:CancellationException) { throw e }
        catch(_:Exception) { planError="Die Planänderung konnte nicht rückgängig gemacht werden. Der aktuelle Plan bleibt erhalten." }
    }
    LaunchedEffect(today,planReady) {
        if(planReady) plan?.let {before ->
            try {val extended=TrainingPlanLogic.extend(before,today);if(extended!=before) savePlan(extended,before.revision,maintenance=true)}
            catch(e:CancellationException) {throw e} catch(_:Exception) {planError="Die nächsten Trainingswochen konnten nicht ergänzt werden. Dein vorhandener Plan bleibt erhalten."}
        }
    }
    LaunchedEffect(toast) { toast?.let { text ->
        val result=showAppNotice(snackbar,text,undo!=null)
        if(result==SnackbarResult.ActionPerformed) restorePlan()
        toast=null
    } }
    fun openDetail(id: String) {route=id}
    fun openProfile() {if(tab in listOf("Heute","Training","Trends","Coach")) profileReturnTab=tab;tab="Profil"}
    fun openCoach(question: String) {coachDate=if(route==Engine.BODY_BATTERY_ID&&energyScore?.stale==true) energyContextDay?.date?.toString() ?: chosenDate else chosenDate;conversation.question=question;route=null;tab="Coach" }
    fun replan(goals: TrainingGoals): TrainingPlan {
        val created=TrainingPlanLogic.create(goals,today)
        val past=plan?.sessions.orEmpty().filter { it.completed || it.date<today }
        val completedDates=past.filter { it.completed }.map { it.date }.toSet()
        return created.copy(sessions=(past+created.sessions.filter { it.date !in completedDates }).sortedBy { it.date },celebrated=plan?.celebrated.orEmpty())
    }
    BackHandler(route!=null || tab!="Heute" || goalEditor || personalEditor) { if(personalEditor) personalEditor=false else if(goalEditor) goalEditor=false else if(route?.startsWith("vital:")==true) route="vitals" else if(route!=null) route=null else if(tab=="Profil") tab=profileReturnTab else tab="Heute" }

    if(onboarding && planReady && personalReady) {
        V2Onboarding(person,robot,plan?.goals,connected,saving,planError,personalProfile) { name,buddy,goals,connect,profile -> scope.launch {
            saving=true
            try {
                withContext(Dispatchers.IO) {personalStore.save(profile)}
                personalProfile=profile
                if(goals!=null && (plan==null || plan?.goals!=goals)) savePlan(replan(goals))
                prefs.edit().putString("user_name",name).putString("name",buddy).putInt("onboarding_version",3).apply()
                person=name;robot=buddy;onboarding=false;planError=null
                if(connect) onConnect() else if(connected) onRefresh()
            } catch(e:CancellationException) { throw e } catch(e:Exception) { planError=e.message ?: "Dein Einstieg konnte nicht gespeichert werden." }
            finally { saving=false }
        } }
        return
    }

    V2InsetScaffold(tab,onSelect={chosen->tab=chosen;route=null;chosenDate=today.toString();followingToday=true;if(chosen=="Coach") coachDate=today.toString()},snackbar=snackbar,navigationVisible=route!="journal") {screenModifier,keyboardVisible->
        if(route=="journal") {
            JournalScreen(wellnessJournal,LocalDate.parse(journalDate),today,robot,journalResults,journalInsightsLoading,journalSaving,journalError,screenModifier,
                chooseDate={journalDate=it.toString()},back={route=null},discuss={openCoach(it)},save=::saveJournal)
        } else if(tab=="Coach" && route==null && !goalEditor) {
            CoachChatScreen(robot,person,ai,days.lastOrNull {it.date.toString()==coachDate},conversation,scope,screenModifier,
                keyboardVisible=keyboardVisible,chatReady=chatReady,openMemory={route="memory"},appData=CoachDataAccess(days,wellnessJournal,plan,today,scores,memory,nightPlan.takeIf {sleepSettings!=null&&coachDate==today.toString()}),plan=plan,planReady=planReady,planStatus=planError,onPlanUpdate={next,revision -> savePlan(next,revision);planError=null;toast=if(revision==null) "Dein Trainingsplan wurde übernommen." else "Dein Plan wurde angepasst."},onUndo={scope.launch { restorePlan() }},canUndo=undo!=null) {openProfile()}
        } else Column(screenModifier.verticalScroll(rememberScrollState()).padding(horizontal=24.dp,vertical=24.dp),verticalArrangement=Arrangement.spacedBy(if(route!=null) 24.dp else 25.dp)) {
            when {
                route=="memory" -> CoachMemoryScreen(memory,today,robot,memoryReady,featureSaving,memoryError,back={route=null},save=::saveMemory)
                route=="sleep-plan" -> SleepPlannerScreen(sleepSettings,days,today,sourceName,today.atTime(journalClock).atZone(ZoneId.systemDefault()).toInstant(),ZoneId.systemDefault(),plan?.goals,personalProfile,robot,sleepReady,featureSaving,sleepError,back={route=null},discuss=::openCoach,save=::saveSleep)
                route=="vital-check" -> VitalCheckScreen(days,LocalDate.parse(chosenDate),source,robot,back={route=null},open={route="vital:$it"})
                personalEditor -> {
                    V2PageHeader("Deine persönliche Basis",back={personalEditor=false})
                    planError?.let {RocoText(it,color=MaterialTheme.colorScheme.error)}
                    if(personalReady) PersonalScoreProfileEditor(personalProfile,saving) {next -> scope.launch {
                        saving=true
                        try {
                            withContext(Dispatchers.IO) {personalStore.save(next)}
                            personalProfile=next;personalEditor=false;planError=null;toast="Persönliche Angaben gespeichert.";onRefresh()
                        } catch(e:CancellationException) {throw e} catch(e:Exception) {planError=e.message ?: "Angaben konnten nicht gespeichert werden."}
                        finally {saving=false}
                    }}
                }
                goalEditor&&planReady -> {
                    V2PageHeader("Deine Ziele", "Gemeinsam festgelegt",back={goalEditor=false})
                    TrainingGoalEditor(plan?.goals ?: TrainingGoals(),saving=saving) {goals->mutate(replan(goals),"Neue Ziele gespeichert.") {goalEditor=false}}
                }
                route=="vitals" -> V2VitalsOverview(days,source,today,open={route="vital:$it"},back={route=null},through=LocalDate.parse(chosenDate))
                route?.startsWith("vital:")==true -> V2VitalDetails(route!!.removePrefix("vital:"),days,source,today,robot,back={route="vitals"},discuss=::openCoach,at=LocalDate.parse(chosenDate))
                route!=null -> {
                    val id=route!!
                    V2ScoreDetails(id,selectedScores[id],if(id==Engine.BODY_BATTERY_ID) energyContextDay else selectedDay,days,robot,back={route=null},source={sourceDialog=true},discuss={openCoach("Erkläre mir meinen ${if(id==Engine.BODY_BATTERY_ID) "Körperakku" else if(id==Engine.SLEEP_ID) "Schlafscore" else "Wochenbelastungswert"} anhand der tatsächlichen Daten.")},onActivity={activity=it})
                }
                tab=="Heute" -> V2TodayScreen(person,robot,selectedScores,days,LocalDate.parse(chosenDate),selectedDay?.sources?.get("_connector") ?: sourceName,connected,refreshing&&chosenDate==today.toString(),importStep,plan,
                    showSource={sourceDialog=true},onRefresh=onRefresh,openDetail=::openDetail,openProfile=::openProfile,
                    openTraining={tab="Training"},openGoals={goalEditor=true},openSession={session=it},onConnect=onConnect,
                    openAllVitals={route="vitals"},openVital={route="vital:$it"},
                    currentDate=today,selectDate={chosenDate=it.toString();followingToday=it==today},energyCacheSource=energyContextDay?.sources?.get("_connector"),
                    nightCard=({SleepPlannerHomeCard(nightPlan,sleepSettings!=null) {route="sleep-plan"}}),vitalsCheckCard=({VitalCheckHomeCard(days,LocalDate.parse(chosenDate),selectedDay?.sources?.get("_connector") ?: sourceName) {route="vital-check"}}),
                    journalProminent=journalReady&&journalPromptDate(wellnessJournal,today,journalClock)!=null,
                    journalCard=if(journalReady) ({JournalHomeCard(wellnessJournal,today,journalClock,robot,true) {journalDate=it.toString();route="journal"}}) else null,analysis={if(chosenDate==today.toString()) AutomaticDayAnalysis(ai,aiReady,todayDay,robot,refreshing,manualRefreshAt,plan,if(journalCalculatedKey==journalEvidenceKey) journalCoachSummary(journalResults) else null,memoryContext=coachMemoryContext(memory,today,ai.shareHealthSummary),nightContext=nightPlanContext(nightPlan.takeIf {sleepSettings!=null}))})
                tab=="Training" -> {
                    if(plan==null) {V2PageHeader("Deine Trainingswoche.","Ein Plan, der zu deinem Alltag passt.");V2WeekProgress(null,days,today,{goalEditor=true},sourceName)}
                    plan?.let { current ->
                        V2TrainingWeek(current,days,today,sourceName,edit={goalEditor=true},open={session=it})
                    }
                    V2RobotNote(robot,trainingDayGuidance(plan,todayDay,scores,today).message)
                    ClayButton(onClick={goalEditor=true}) { RocoText(if(plan==null) "Meinen Plan gemeinsam festlegen" else "Ziele und Trainingstage bearbeiten") }
                    if(plan!=null) ClayButton(onClick={editedSessionId=null;planError=null;sessionEditor=true},secondary=true) {RocoText("Weitere Einheit planen")}
                    ClayButton(onClick={openCoach("Hilf mir, meinen Trainingsplan passend anzupassen.")},secondary=true) { RocoText("Mit $robot den Plan anpassen") }
                }
                tab=="Trends" -> V2Trends(days,robot,sourceName,today=today,showSource={sourceDialog=true},discuss=::openCoach) { id,date -> chosenDate=date.toString();followingToday=date==today;route=id }
                else -> V2ProfileScreen(person,robot,source,editMemory={route="memory"},editPersonal={personalEditor=true},back={tab=profileReturnTab},showSource={sourceDialog=true},
                    editOnboarding={onboarding=true},editGoals={goalEditor=true},refresh=onRefresh,
                    connection={
                    V2Card("Deine Datenverbindung", "Aktueller Stand: $source") {
                        ClayButton(onClick=onConnect) { RocoText(if(connected) "Health Connect verwalten" else "Health Connect verbinden") }
                        TextButton(onClick=onHistory) { RocoText("Historische Daten freigeben") }
                        Row(verticalAlignment=Alignment.CenterVertically) { RocoText("Aktualisierung pausieren",Modifier.weight(1f));Switch(paused,onPause) }
                        TextButton(onClick={sourceDialog=true}) { RocoText("Datengrundlage und Quellen ansehen") }
                        if(connected) TextButton(onClick=onDisconnect) { RocoText("Health Connect trennen") }
                    }
                    GoogleHealthConnectionCard(onConnected=onRefresh)
                    },ai={
                    AiSettings(ai,aiReady,aiStatus,aiSaving) { config -> scope.launch {
                        aiSaving=true
                        try { val normalized=config.validated();withContext(Dispatchers.IO) {aiStore.save(normalized)};ai=normalized;aiStatus="Gespeichert" }
                        catch(e:CancellationException) {throw e} catch(e:Exception) {aiStatus=if(e is IllegalArgumentException) e.message else "Speichern fehlgeschlagen. Bitte erneut versuchen."} finally {aiSaving=false}
                    } }
                    },diagnostics={DiagnosticsPanel()},localData={
                    V2Card("Lokal auf deinem Gerät", "Scores, Pläne, Nachrichten und Einstellungen werden lokal verschlüsselt gespeichert.") { TextButton(onClick=onDelete) {RocoText("Lokale App-Daten löschen",color=MaterialTheme.colorScheme.error)} }
                    })
            }
            planError?.let { RocoText(it,color=MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(12.dp))
        }
    }
    if(sourceDialog) V2DetailSheet(onDismissRequest={sourceDialog=false},title={RocoText("Deine Datengrundlage")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        RocoText("Quelle dieses Standes: $source",fontWeight=FontWeight.Bold)
        if(chosenDate==today.toString()) {RocoText(status);message?.let {RocoText(it,style=MaterialTheme.typography.bodySmall)}} else RocoText("Gespeicherter Tagesstand vom $chosenDate")
        contextDay?.sleepEnd?.let { end ->RocoText("Verwendete Nacht: Aufgewacht am "+end.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d. MMM · HH:mm",Locale.getDefault()))) }
        contextDay?.sources?.get("_latest_imported_sleep_end")?.let { value ->
            RocoText("Neuester vom Anbieter gelieferter Hauptschlaf endet: "+Instant.parse(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d. MMM · HH:mm",Locale.getDefault())),style=MaterialTheme.typography.bodySmall)
        }
        RocoText("Tageswerte und frühere Vergleichstage werden getrennt betrachtet. Ein Google-Stand enthält keine ergänzten Health-Connect-Einzelwerte.")
        selectedScores.values.forEach { score -> RocoText(scoreTitle(score.scoreId),fontWeight=FontWeight.Bold);score.coverage.missingReasons.distinct().forEach {RocoText(scoreReasonLabel(it),style=MaterialTheme.typography.bodySmall)} }
        if(chosenDate==today.toString()&&source!="Google Health") observedSources.forEach { (type,origins) -> RocoText(ApprovedWellnessRecordTypes.settingsRows.toMap()[type] ?: type,fontWeight=FontWeight.Bold);origins.forEach { origin ->TextButton(onClick={onBind(type,origin)}) {RocoText((if(sourceBindings[type]?.packageName==origin) "✓ " else "")+origin)} } }
    }},confirmButton={ClayButton(onClick={sourceDialog=false}) {RocoText("Verstanden")}})
    session?.let { selected -> V2DetailSheet(onDismissRequest={session=null},startExpanded=true,title={RocoVerbatimText(selected.title)},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        RocoText("${selected.date.format(DateTimeFormatter.ofPattern("EEE, d. MMM",Locale.getDefault()))} · ${selected.minutes} Minuten")
        RocoText("Passe die Einheit deinem Befinden an. Beginne ruhig und plane Zeit zum Ausklingen ein.")
        val linked=selected.recordedActivity
        if(linked!=null) RocoText("Bestätigte Aufzeichnung: ${linked.title} · ${durationLabel(linked.minutes)} · ${linked.connector}",style=MaterialTheme.typography.bodySmall,color=V2Muted) else RocoText(if(selected.completed) "Manuell als erledigt bestätigt." else "Eine manuelle Bestätigung verändert keine Uhrendaten.",style=MaterialTheme.typography.bodySmall,color=V2Muted)
        if(!selected.completed) plan?.let {p ->
            val suggestions=recordedSuggestions(p,selected.id,days,sourceName,today,ZoneId.systemDefault())
            if(suggestions.isNotEmpty()) {
                RocoText("Passende Aufzeichnungen",style=MaterialTheme.typography.titleMedium)
                RocoText("Gleicher Starttag und ähnliche Dauer. Prüfe selbst, ob das dein geplantes Training war. Eine Aufzeichnung zählt für höchstens eine Einheit.",style=MaterialTheme.typography.bodySmall,color=V2Muted)
                suggestions.forEach {recorded ->
                    OutlinedButton(enabled=!saving,onClick={scope.launch {
                        saving=true
                        try {
                            val current=plan ?: error("Kein Plan verfügbar.")
                            val next=linkRecordedTraining(current,selected.id,recorded,days,sourceName,today,ZoneId.systemDefault())
                            savePlan(next,current.revision)
                            session=null;toast="Aufzeichnung zugeordnet. Sensorminuten bleiben unverändert."
                        } catch(e:CancellationException) {throw e} catch(e:Exception) {planError=e.message ?: "Die Zuordnung konnte nicht gespeichert werden.";session=null}
                        finally {saving=false}
                    }},modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)) {RocoVerbatimText("${recorded.title} · ${durationLabel(recorded.minutes)}");RocoText("${timeLabel(recorded.start)}–${timeLabel(recorded.end)} · Zuordnen",style=MaterialTheme.typography.bodySmall)}
                    }
                }
            }
        }
        planError?.let {RocoText(it,color=MaterialTheme.colorScheme.error)}
        if(!selected.completed&&selected.date>=today) TextButton(onClick={editedSessionId=selected.id;planError=null;sessionEditor=true;session=null}) {V2Icon("edit");RocoText("Datum, Dauer und Namen bearbeiten")}
    }},confirmButton={ClayButton(enabled=!saving&&selected.date<=today,onClick={plan?.let { p ->
        scope.launch {
            saving=true
            try {
                val next=TrainingPlanLogic.complete(p,selected.id,!selected.completed,today)
                savePlan(next,p.revision);session=null;toast=if(selected.completed) "Bestätigung zurückgenommen." else "Trainingstag geschafft."
            }
            catch(e:CancellationException) {throw e} catch(e:Exception) {planError=e.message ?: "Die Einheit konnte nicht gespeichert werden."}
            finally {saving=false}
        }
    }}) {RocoText(if(selected.completed) "Bestätigung zurücknehmen" else if(selected.date>today) "Bestätigen ab "+selected.date.format(DateTimeFormatter.ofPattern("d. MMM",Locale.getDefault())) else "Als erledigt bestätigen")}},dismissButton={ClayButton(secondary=true,onClick={session=null;openCoach("Verschiebe meine Einheit ${selected.title} vom ${selected.date} auf einen passenden anderen Tag. Frag mich nach dem neuen Datum.")}) {RocoText("Im Coach anpassen")}}) }
    activity?.let { entry -> V2DetailSheet(onDismissRequest={activity=null},title={RocoVerbatimText(entry.title)},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        RocoText("${timeLabel(entry.start)}–${timeLabel(entry.end)} · ${durationLabel(entry.minutes)}")
        RocoText("Beitrag: ${entry.loadPoints?.let { String.format(Locale.getDefault(),"%.1f",it) } ?: "–"} Belastungspunkte",fontWeight=FontWeight.Bold)
        RocoText("Durchschnitt ${entry.meanPulse?.roundToInt() ?: "–"} bpm · Spitze ${entry.peakPulse?.roundToInt() ?: "–"} bpm")
        V2LineChart(entry.pulse.map {V2PlotPoint(it.first,it.second)},"bpm",V2Load,40.0,160.0,intraday=true)
        RocoText(if(entry.fallback) "Die Belastung nutzt die Dauer als Näherung, weil Pulsabdeckung oder Ruhepuls-Basis nicht genügen." else "Die Belastung nutzt Trainingsdauer und den gemessenen Abstand zum persönlichen Ruhepuls.",style=MaterialTheme.typography.bodySmall,color=V2Muted)
    }},confirmButton={ClayButton(onClick={activity=null}) {RocoText("Fertig")}}) }
    if(sessionEditor&&planReady) V2SessionEditor(editedSession,plan?.goals?.sessionMinutes ?: 30,dismiss={sessionEditor=false},saving=saving,error=planError,defaultIntensity=plan?.goals?.intensity ?: 1.5,
        delete=if(editedSession==null) null else ({
            plan?.let {p->try {
                val next=applyPlanTool(p,"delete_session",JSONObject().put("session_id",editedSessionId),today).first
                mutate(next,"Einheit entfernt.") {sessionEditor=false}
            } catch(e:Exception) {planError=e.message}}
        })) {draft ->
        plan?.let {p ->
            try {
                var next=p
                val editing=editedSession
                check(editedSessionId==null||editing!=null) {"Diese Einheit ist nicht mehr im Plan. Bitte den Editor schließen und erneut öffnen."}
                if(editing==null) next=applyPlanTool(next,"create_session",JSONObject().put("date",draft.date.toString()).put("title",draft.title).put("minutes",draft.minutes),today).first
                else {
                    if(editing.date!=draft.date) next=applyPlanTool(next,"move_session",JSONObject().put("session_id",editing.id).put("date",draft.date.toString()),today).first
                    next=applyPlanTool(next,"update_session",JSONObject().put("session_id",editing.id).put("title",draft.title).put("minutes",draft.minutes),today).first
                }
                val changedId=editing?.id ?: next.sessions.single {row->p.sessions.none {it.id==row.id}}.id
                next=applyPlanTool(next,"set_session_intensity",JSONObject().put("session_id",changedId).put("intensity",draft.intensity),today).first
                mutate(next,"Einheit gespeichert.") {sessionEditor=false}
            } catch(e:Exception) {planError=e.message}
        }
    }
    if(award) V2DetailSheet(onDismissRequest={award=false},startExpanded=true,title={RocoText("Dein Logbuch-Streak wächst!")},text={Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(20.dp)) {StreakHandoff(robot,journalStreak(wellnessJournal,today));RocoText("$robot übergibt dir deine Flamme für deinen ehrlichen Tages-Check-in. Ja und Nein zählen gleich viel.")}},confirmButton={ClayButton(onClick={award=false}) {RocoText("Weiter so")}})
}

@Composable internal fun V2PageHeader(title: String, subtitle: String? = null, back: (() -> Unit)? = null,info: (() -> Unit)? = null,eyebrow: String?=null,action: (() -> Unit)?=null) {
    Row(modifier=if(back!=null) Modifier.v2HorizontalBleed(9.dp) else Modifier.fillMaxWidth(),verticalAlignment=if(back!=null) Alignment.CenterVertically else Alignment.Top,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        if(back!=null) IconButton(onClick=back,modifier=Modifier.semantics {contentDescription=RocoStrings.translate("Zurück")}) {V2Icon("back")}
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(if(back!=null) 4.dp else 9.dp)) {
            eyebrow?.let {V2Eyebrow(it)}
            RocoText(title,modifier=if(back==null) Modifier.widthIn(max=290.dp) else Modifier,fontSize=if(back!=null) 21.sp else 29.sp,lineHeight=if(back!=null) 25.sp else 35.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=if(back!=null) (-.65).sp else (-1.3).sp)
            if(subtitle!=null) RocoText(subtitle,fontSize=if(back!=null) 11.sp else 13.sp,lineHeight=if(back!=null) 17.sp else 22.sp,color=Color(0xFF626F55))
        }
        if(info!=null) IconButton(onClick=info,modifier=Modifier.semantics {contentDescription=RocoStrings.translate("Datengrundlage anzeigen")}) {V2Icon("info",size=22.dp)}
        if(action!=null) IconButton(onClick=action,modifier=Modifier.semantics {contentDescription=RocoStrings.translate("Trainingsplan bearbeiten")}) {V2Icon("edit")}
    }
}

@Composable internal fun V2ScoreCockpit(scores: Map<String,Engine.ScoreResult>, loading: Boolean, refresh: ()->Unit, details: (String)->Unit,historical: Boolean=false,energyCacheSource: String?=null) {
    val energy=scores[Engine.BODY_BATTERY_ID]
    val battery=energy?.score
    val load=scores[Engine.SESSION_LOAD_ID]?.takeIf {it.algorithmVersion.endsWith("/weekly")}?.score
    val sleep=scores[Engine.SLEEP_ID]?.score
    Surface(color=Color(0xFF202E25),contentColor=Color.White,shape=RoundedCornerShape(30.dp),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start=22.dp,end=22.dp,top=22.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                RocoText("DEIN TAG AUF EINEN BLICK",fontSize=10.sp,lineHeight=16.sp,letterSpacing=1.1.sp,fontWeight=FontWeight.SemiBold,color=Color(0xFFC1CDBC),modifier=Modifier.weight(1f))
                IconButton(onClick=refresh,enabled=!loading,modifier=Modifier.size(40.dp).semantics {contentDescription=RocoStrings.translate("Werte aktualisieren")}) {V2Icon("refresh",tint=Color(0xFFE1E9DD),size=21.dp)}
            }
            BoxWithConstraints(Modifier.fillMaxWidth().clickable(onClickLabel=RocoStrings.translate("Körperenergie erklären")) {details(Engine.BODY_BATTERY_ID)}.padding(top=14.dp,bottom=21.dp)) {
                if(maxWidth<300.dp||LocalDensity.current.fontScale>1.2f) Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {V2EnergyHeading(battery,historical,energy?.takeIf {it.stale}?.asOf,energyCacheSource);V2Battery(battery,dark=true)}
                else Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(20.dp)) {Column(Modifier.weight(1f)) {V2EnergyHeading(battery,historical,energy?.takeIf {it.stale}?.asOf,energyCacheSource)};V2Battery(battery,dark=true)}
            }
            HorizontalDivider(color=Color.White.copy(alpha=.125f))
            BoxWithConstraints {
                val stacked=maxWidth<290.dp||LocalDensity.current.fontScale>1.3f
                if(stacked) Column {
                    V2CockpitMetric("Wochenbelastung",load,"/100 Ziel","bolt",Color(0xFFC5A77E),Modifier.fillMaxWidth()) {details(Engine.SESSION_LOAD_ID)}
                    HorizontalDivider(color=Color.White.copy(alpha=.125f))
                    V2CockpitMetric("Schlaf",sleep,"/100","sleep",Color(0xFFA6BFDB),Modifier.fillMaxWidth()) {details(Engine.SLEEP_ID)}
                } else Row(Modifier.height(IntrinsicSize.Min)) {
                    V2CockpitMetric("Wochenbelastung",load,"/100 Ziel","bolt",Color(0xFFC5A77E),Modifier.weight(1f).padding(end=12.dp)) {details(Engine.SESSION_LOAD_ID)}
                    VerticalDivider(color=Color.White.copy(alpha=.125f))
                    V2CockpitMetric("Schlaf",sleep,"/100","sleep",Color(0xFFA6BFDB),Modifier.weight(1f).padding(start=20.dp)) {details(Engine.SLEEP_ID)}
                }
            }
        }
    }
}
@Composable private fun V2CockpitMetric(label: String,value: Int?,unit: String,icon: String,tint: Color,modifier: Modifier,open: ()->Unit) {
    Row(modifier.clickable(onClickLabel=RocoStrings.translate("$label erklären"),onClick=open).padding(top=18.dp,bottom=21.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        V2Icon(icon,tint=tint,size=21.dp)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            RocoText(label,fontSize=11.sp,lineHeight=16.sp,color=Color(0xFFD9E2D5))
            Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                RocoText(value?.toString() ?: "–",fontSize=27.sp,lineHeight=30.sp,fontWeight=FontWeight(650),letterSpacing=(-.6).sp,color=Color.White)
                RocoText(unit,fontSize=10.sp,lineHeight=16.sp,color=Color(0xFFB2BFB1))
            }
        }
        V2Icon("next",tint=Color(0xFF91A38E),size=17.dp)
    }
}

@Composable private fun V2EnergyHeading(battery: Int?,historical: Boolean=false,savedAt: Instant?=null,cacheSource: String?=null) {
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        RocoText("Körperenergie",style=MaterialTheme.typography.headlineSmall,color=Color.White)
        RocoText(if(savedAt!=null) (cacheSource ?: "Lokaler Stand")+" · "+savedAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d. MMM · HH:mm",Locale.getDefault())) else if(historical) "Dein gespeicherter Tagesakku" else "Dein Akku für heute",fontSize=13.sp,lineHeight=19.5.sp,fontWeight=FontWeight(450),color=Color(0xFFC3D0C1))
        RocoText(batteryLabel(battery),style=MaterialTheme.typography.bodySmall,color=Color(0xFFD0DDC9))
    }
}

@Composable internal fun V2SessionCard(entry: PlannedSession,at: LocalDate=LocalDate.now(),open: ()->Unit) {
    V2ClaySurface(onClick=open,onClickLabel="${entry.title} öffnen",modifier=Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal=17.dp,vertical=19.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(13.dp)) {
                Surface(color=Color(0xFFEDF1E7),shape=RoundedCornerShape(14.dp),modifier=Modifier.size(43.dp,45.dp)) {Box(contentAlignment=Alignment.Center) {V2Icon(if(entry.completed) "check" else "training",tint=Color(0xFF62754B),size=22.dp)}}
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    RocoText(if(entry.date==at) "HEUTE" else entry.date.format(DateTimeFormatter.ofPattern("EEE, d. MMM",Locale.getDefault())).uppercase(Locale.getDefault()),fontSize=10.sp,lineHeight=15.sp,letterSpacing=.7.sp,fontWeight=FontWeight(750),color=Color(0xFF5E704B))
                    RocoVerbatimText(entry.title,style=MaterialTheme.typography.titleMedium)
                    RocoText("${entry.minutes} min · ${if(entry.completed) (if(entry.recordedActivity!=null) "Aufzeichnung zugeordnet" else "manuell bestätigt") else intensityLabel(entry.intensity)}",fontSize=12.sp,lineHeight=18.sp,color=Color(0xFF626C5B))
                }
                V2Icon("next",tint=Color(0xFF919A89),size=20.dp)
            }
        }
}

internal fun batteryLabel(value: Int?): String = when {value==null->"Noch kein Wert";value>=75->"Gut aufgeladen";value>=40->"Noch Energie für deinen Tag";else->"Zeit für Erholung"}
internal fun scoreTitle(id: String): String = when(id) {Engine.BODY_BATTERY_ID->"Deine Körperenergie";Engine.SLEEP_ID->"Deine Nacht";else->"Deine Wochenbelastung"}
internal fun vitalComparison(day: DayInsight?, key: String): String {
    val value=day?.metrics?.get(key) ?: return "Noch keine Tagesmessung"
    val base=day.metrics[key+"_baseline"] ?: return "${day.metrics[key+"_baseline_days"]?.roundToInt() ?: 0} von 7 Basistagen"
    return if(key=="hrv") "${if(value>=base) "+" else ""}${((value/base-1)*100).roundToInt()} % zur Basis" else "${if(value>=base) "+" else ""}${(value-base).roundToInt()} bpm zur Basis"
}
