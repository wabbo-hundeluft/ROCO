package com.wabbo.healthconnectcoach

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import org.json.JSONObject
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable internal fun V2Onboarding(initialPerson: String, initialRobot: String, initialGoals: TrainingGoals?, connected: Boolean,
    saving: Boolean, error: String?, initialProfile: com.wabbo.healthconnectcoach.score.PersonalScoreProfile = com.wabbo.healthconnectcoach.score.PersonalScoreProfile(), complete: (String,String,TrainingGoals?,Boolean,com.wabbo.healthconnectcoach.score.PersonalScoreProfile)->Unit) {
    val focus=LocalFocusManager.current
    var step by rememberSaveable { mutableIntStateOf(0) };var person by rememberSaveable {mutableStateOf(initialPerson)};var robot by rememberSaveable {mutableStateOf(initialRobot)}
    var goals by rememberSaveable(stateSaver=Saver<TrainingGoals,String>(save={encodeTrainingGoals(it).toString()},restore={decodeTrainingGoals(JSONObject(it))})) {mutableStateOf(initialGoals ?: TrainingGoals())}
    var profile by rememberSaveable(stateSaver=Saver<com.wabbo.healthconnectcoach.score.PersonalScoreProfile,String>(save={encodePersonalScoreProfile(it).toString()},restore={decodePersonalScoreProfile(JSONObject(it))})) {mutableStateOf(initialProfile)}
    var confirmed by rememberSaveable {mutableStateOf(initialGoals!=null)}
    val keyboardOpen=WindowInsets.ime.getBottom(LocalDensity.current)>0
    val firstSteps=step<2
    val scroll=rememberScrollState()
    LaunchedEffect(step) {scroll.scrollTo(0)}
    val robotReaction=step*1000+(if(step==1) robot.length else person.length)/4

    Scaffold(containerColor=MaterialTheme.colorScheme.background,contentWindowInsets=WindowInsets.systemBars.union(WindowInsets.displayCutout)) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
        val compact=keyboardOpen||maxHeight<640.dp
        val heroSize by animateDpAsState(if(compact) 92.dp else if(firstSteps) 176.dp else if(step in 2..3) 125.dp else 190.dp,tween(180),label="onboarding-robot-space")
        Column(Modifier.fillMaxSize().padding(horizontal=28.dp,vertical=if(compact) 12.dp else 24.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {(0..4).forEach { index ->Box(Modifier.weight(1f).height(4.dp).background(if(index<=step) V2Green else Color(0xFFE1E7DA),RoundedCornerShape(3.dp)))}}
            // Keep the welcome robot outside the auto-scrolling form, even with the keyboard up.
            if(firstSteps) {
                Spacer(Modifier.height(if(compact) 6.dp else 14.dp))
                RobotCompanion(robot.ifBlank {"Dein Coach"},false,Modifier.align(Alignment.CenterHorizontally).size(heroSize),
                    reactionKey=robotReaction,attentive=if(step==1) robot.isNotBlank() else person.isNotBlank(),allowSleep=false,event=RobotEvent.INPUT)
            }
            Column(Modifier.weight(1f).verticalScroll(scroll).padding(top=if(firstSteps) 8.dp else 24.dp,bottom=if(compact) 12.dp else 24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(if(compact) 12.dp else 24.dp)) {
                if(!firstSteps) RobotCompanion(robot.ifBlank {"Dein Coach"},false,Modifier.size(heroSize),reactionKey=step,mood=if(step==4) RobotMood.CELEBRATE else RobotMood.CURIOUS,allowSleep=false)
                if(!compact||!firstSteps) V2Eyebrow(listOf("WIR LERNEN UNS KENNEN","DEIN TEAM","DEINE PERSÖNLICHE BASIS","DEIN RHYTHMUS","BEREIT FÜR DEINEN TAG")[step])
                RocoText(when(step){0->"Hallo! Wie darf ich dich nennen?";1->"Und wie soll ich heißen?";2->"Was sollte ich über dich wissen?";3->"Was passt in deinen Alltag?";else->"Unser Team ist bereit."},style=MaterialTheme.typography.headlineLarge.copy(fontSize=if(compact) 26.sp else 29.sp,lineHeight=if(compact) 32.sp else 36.sp,letterSpacing=(-1).sp),textAlign=TextAlign.Center)
                when(step) {
                    0 -> {if(!compact) RocoText("Ich begleite dich durch Bewegung, Schlaf und Erholung. Schritt für Schritt.",textAlign=TextAlign.Center,fontSize=13.sp,lineHeight=24.sp,color=Color(0xFF58664E));V2LabeledInput(person,{person=it.take(32)},"Dein Vorname",enabled=!saving)}
                    1 -> {if(!compact) RocoText("Du gibst deinem persönlichen Coach einen Namen.",textAlign=TextAlign.Center,fontSize=13.sp,lineHeight=24.sp,color=Color(0xFF58664E));V2LabeledInput(robot,{robot=it.take(24)},"Name deines Roboters",enabled=!saving)}
                    2 -> { PersonalScoreProfileEditor(profile,saving,"Weiter") {profile=it;step=3} }
                    3 -> {
                        TrainingGoalEditor(goals,"Diese Ziele passen zu mir") {goals=it;confirmed=true;step=4}
                        TextButton(onClick={confirmed=false;step=4}) {RocoText("Erst meine Gesundheitsdaten ansehen")}
                    }
                    else -> {
                        if(confirmed) V2Card("Dein Rhythmus") {RocoText("${goals.days.size} Trainingstage · ${goals.sessionMinutes} Minuten je Einheit");RocoText("Schlafziel: ${durationLabel(goals.sleepMinutes.toDouble())}")}
                        else V2Card("In deinem Tempo") {RocoText("Deinen Trainingsplan legst du später gemeinsam mit mir fest. Schlaf und Energie kannst du schon vorher ansehen.")}
                        listOf("Heute" to "Energie, Wochenbelastung und Schlaf auf einen Blick.","Training" to "Dein bestätigter Plan mit Raum für Erholung.","Trends & Coach" to "Werte vergleichen und gemeinsam verstehen.").forEach { (title,text) -> V2Card(title) {RocoText(text,color=V2Muted)} }
                        RocoText("Health Connect verbindet die Daten deiner Uhr. Einen direkten Google-Health-Zugang kannst du anschließend im Profil aktivieren.",color=V2Muted)
                    }
                }
            }
            error?.let {RocoText(it,color=MaterialTheme.colorScheme.error)}
            if(step !in 2..3) ClayButton(onClick={focus.clearFocus();if(step<4) step++ else complete(person.trim(),robot.trim(),goals.takeIf {confirmed},!connected,profile)},enabled=!saving&&person.trim().isNotEmpty()&&(step==0||robot.trim().isNotEmpty())) {RocoText(if(saving) "Wird gespeichert …" else if(step==4) {if(connected) "Zeig mir meinen Tag" else "Uhrendaten verbinden"} else "Weiter");V2Icon("forward",tint=if(saving) Color(0xFFA2AB98) else Color.White,size=20.dp)}
            if(step==4&&!connected) TextButton(onClick={complete(person.trim(),robot.trim(),goals.takeIf {confirmed},false,profile)},enabled=!saving,modifier=Modifier.fillMaxWidth()) {RocoText("Später verbinden")}
            if(step>0) TextButton(onClick={focus.clearFocus();step--},enabled=!saving,modifier=Modifier.fillMaxWidth()) {RocoText("Zurück")}
        }
        }
    }
}

/** Requests are reserved on disk before sending; only a matching fact snapshot is displayed. */
@Composable internal fun AutomaticDayAnalysis(config: AiConfiguration, ready: Boolean, day: DayInsight?, robot: String, refreshing: Boolean,manualRefreshAt: java.time.Instant?=null,plan: TrainingPlan?=null,journalContext: String?=null,memoryContext:String?=null,nightContext:String?=null) {
    val context=LocalContext.current;val document=remember {SecureLocalDocument(context,"day_analysis")}
    var answer by remember {mutableStateOf<DayAnalysisEntry?>(null)};var note by remember {mutableStateOf<String?>(null)};var busy by remember {mutableStateOf(false)}
    var retry by remember {mutableIntStateOf(0)};var retryFor by remember {mutableStateOf<String?>(null)};var retryAllowed by remember {mutableStateOf(false)};var expanded by remember {mutableStateOf(false)}
    val fingerprint=remember(day,config,robot,plan?.revision,journalContext,memoryContext,nightContext) {day?.let {analysisFingerprint(it,config,robot,plan,journalContext,memoryContext,nightContext)}}
    val enabled=ready&&config.autoAnalysis&&config.shareHealthSummary&&CoachProvider.entries.any {config.keyFor(it).isNotBlank()}&&day!=null&&day.date==LocalDate.now()&&
        day.scores.values.any {it.score!=null}&&day.scores.values.all {it.algorithmVersion.startsWith(com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine.MODEL_VERSION+"/")}
    LaunchedEffect(enabled,fingerprint,refreshing,retry,manualRefreshAt) {
        answer=null;note=null;retryAllowed=false;busy=false
        if(!enabled||day==null||fingerprint==null) return@LaunchedEffect
        val explicitRetry=retryFor==fingerprint;retryFor=null
        var mayRetry=true
        try {
            var cache=withContext(Dispatchers.IO) {decodeAnalysisCache(document.read(),day.date)}
            cache.matching(fingerprint)?.let {answer=it;return@LaunchedEffect}
            if(refreshing) return@LaunchedEffect
            val lastAnalysisAt=cache.lastRequestedAt
            val manual=explicitRetry||(manualRefreshAt!=null&&(lastAnalysisAt==null||manualRefreshAt>lastAnalysisAt))
            if(!automaticRefreshDue(cache.lastRequestedAt,java.time.Instant.now(),manual)) return@LaunchedEffect
            if(!cache.mayRequest(fingerprint,explicitRetry||manual||automaticRefreshDue(lastAnalysisAt,java.time.Instant.now()))) {
                retryAllowed=cache.attempts<MAX_DAILY_ANALYSES
                note=if(retryAllowed) "Die letzte Anfrage wurde nicht abgeschlossen. Du kannst sie erneut versuchen." else "Für heute wurden bereits 24 Tagesanalysen angefragt. Du kannst im Coach weiterfragen."
                return@LaunchedEffect
            }
            busy=true
            cache=cache.reserve(fingerprint,explicitRetry||manual||automaticRefreshDue(lastAnalysisAt,java.time.Instant.now())).copy(lastRequestedAt=java.time.Instant.now())
            mayRetry=cache.attempts<MAX_DAILY_ANALYSES
            withContext(Dispatchers.IO) {document.write(encodeAnalysisCache(cache))}
            val text=askConfiguredTextCoach(config,listOf(CoachMessage("user","Sage zuerst, ob heute laut Plan und aktuellen Daten ein aktiver Tag oder ein Erholungstag sinnvoll ist. Erkläre dann in höchstens 100 Wörtern: ein konkreter Vergleich mit meiner persönlichen Basis, Wochenfortschritt und eine vorsichtige Einordnung. Nutze kurze Absätze und **fette Kennzahlen**, erfinde keine Ursachen.")),day,robot,plan,journalContext,memoryContext,nightContext)
            val entry=DayAnalysisEntry(fingerprint,text,day.calculatedAt)
            cache=cache.finish(entry)
            withContext(Dispatchers.IO) {document.write(encodeAnalysisCache(cache))};answer=entry
        } catch(e:CancellationException) {throw e} catch(e:Exception) {
            note=if(e is IllegalStateException||e is IllegalArgumentException) e.message else "Die Tagesanalyse ist gerade nicht erreichbar."
            retryAllowed=mayRetry
        } finally {busy=false}
    }
    val visibleAnswer=answer?.takeIf {it.fingerprint==fingerprint}
    if(enabled&&(busy||visibleAnswer!=null||note!=null)) V2Card("$robot erkennt Zusammenhänge",if(busy) "Deine Tagesanalyse wird erstellt …" else "KI · auf Basis deiner freigegebenen Werte") {
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth(),color=V2Green)
        visibleAnswer?.let {entry->
            RocoText("Datenstand: "+entry.at.atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("d. MMM · HH:mm",java.util.Locale.getDefault())),style=MaterialTheme.typography.bodySmall,color=V2Muted)
            if(expanded) CoachMarkdown(entry.text) else RocoVerbatimText(entry.text.lineSequence().firstOrNull()?.replace("**","").orEmpty(),maxLines=3)
            TextButton(onClick={expanded=!expanded}) {RocoText(if(expanded) "Weniger anzeigen" else "Analyse lesen")}
        }
        note?.let {RocoText(it,style=MaterialTheme.typography.bodySmall,color=V2Muted)}
        if(!busy&&visibleAnswer==null&&retryAllowed) TextButton(onClick={retryFor=fingerprint;retry++}) {RocoText("Erneut versuchen")}
    }
}
