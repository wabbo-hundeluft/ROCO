package com.wabbo.healthconnectcoach

import android.app.Application
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine as E
import com.wabbo.healthconnectcoach.ui.theme.HealthCoachTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.*
import java.util.TimeZone

/** Synthetic presentation fixtures only; no user data, Health Connect, GIS or AI calls. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[36],qualifiers="de-rDE-w427dp-h852dp-mdpi",application=Application::class)
class NativeVisualTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var previousZone: TimeZone
    @Before fun prepare() {
        previousZone=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Settings.Global.putFloat(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,0f)
        compose.mainClock.autoAdvance=false
    }
    @After fun restoreZone() {TimeZone.setDefault(previousZone)}
    private val date=LocalDate.of(2026,10,25)
    private val now=date.atTime(14,0).toInstant(ZoneOffset.UTC)
    private fun score(id: String,n: Int): E.ScoreResult {
        val parts=when(id) {
            E.BODY_BATTERY_ID->mapOf("START" to 91,"USED" to 20,"TRAINING" to 14,"AWAKE" to 6,"STEPS" to 0,"RECOVERY" to 91,"PRIOR_LOAD" to 0,"D" to 90,"H" to 80,"P" to 75)
            E.SLEEP_ID->mapOf("D" to 90,"E" to 84,"R" to 84,"Q" to 80,"H" to 78,"P" to 86)
            else->mapOf("WEEK_MIN" to 112,"WEEK_LOAD" to 63,"TARGET_X100" to 15000,"ADAPTIVE" to 0)
        }
        return E.ScoreResult(id,n,E.Status.available,now,E.Coverage(4,4),components=parts,algorithmVersion=if(id==E.SESSION_LOAD_ID) E.WEEKLY_MODEL_VERSION else E.MODEL_VERSION+"/visual")
    }
    private val scores=mapOf(E.BODY_BATTERY_ID to score(E.BODY_BATTERY_ID,71),E.SESSION_LOAD_ID to score(E.SESSION_LOAD_ID,42),E.SLEEP_ID to score(E.SLEEP_ID,86))
    private val start=date.minusDays(1).atTime(23,5).toInstant(ZoneOffset.UTC)
    private val sleepBands=run {
        var at=start
        listOf("LIGHT" to 38,"DEEP" to 42,"LIGHT" to 54,"REM" to 26,"AWAKE" to 8,"LIGHT" to 46,"DEEP" to 32,"LIGHT" to 48,"REM" to 42,"AWAKE" to 7,"LIGHT" to 40,"DEEP" to 18,"LIGHT" to 44,"REM" to 62,"AWAKE" to 8).map {(type,minutes)->val band=SleepBand(at,at.plusSeconds(minutes*60L),type);at=band.end;band}
    }
    private val walkStart=date.atTime(11,10).toInstant(ZoneOffset.UTC)
    private val walk=ActivityInsight("walk","Zügiger Spaziergang",walkStart,walkStart.plusSeconds(38*60),38.0,16.0,130.0,143.0,listOf(105,122,132,140,135,137,143,130,125).mapIndexed {i,v->walkStart.plusSeconds(i*285L) to v.toDouble()},false)
    private val day=DayInsight(date,now,false,scores,sleepStart=start,sleepEnd=start.plusSeconds(515*60),sleepBands=sleepBands,
        metrics=mapOf("hrv" to 52.0,"rhr" to 57.0,"hrv_baseline" to 48.0,"rhr_baseline" to 55.0,"hrv_baseline_days" to 26.0,"rhr_baseline_days" to 26.0,"workout_minutes" to 38.0,"sleep_minutes" to 492.0,"sleep_goal" to 480.0,"efficiency" to 95.5,"stage_LIGHT" to 270.0,"stage_DEEP" to 92.0,"stage_REM" to 130.0,"stage_AWAKE" to 23.0),
        sources=mapOf("_connector" to "Google Health","hrv" to "watch","rhr" to "watch","sleep" to "watch"),activities=listOf(walk),
        energyCurve=listOf(460 to 91,540 to 89,630 to 85,690 to 77,720 to 71,780 to 71,840 to 71).map {(minute,value)->date.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(minute*60L) to value})
    private val allDays=(26 downTo 1).map {offset->
        val d=date.minusDays(offset.toLong());val recent=listOf(47.0,49.0,44.0,51.0,48.0,50.0)
        day.copy(date=d,calculatedAt=d.atTime(14,0).toInstant(ZoneOffset.UTC),metrics=day.metrics+mapOf("hrv" to if(offset<=6) recent[6-offset] else listOf(44.0,48.0,52.0)[offset%3],"workout_minutes" to if(d==date.minusDays(5)) 45.0 else if(d==date.minusDays(3)) 29.0 else 0.0))
    }+day
    private val plan=TrainingPlanLogic.create(TrainingGoals(days=setOf(2,4,7),sessionMinutes=20,intensity=1.0),date).let {p->p.copy(sessions=listOf(PlannedSession("tue",date.minusDays(5),"Kraft & Stabilität",45,1.5,true),PlannedSession("thu",date.minusDays(3),"Locker Rad fahren",29,1.0,true))+p.sessions.map {it.copy(title="Beweglich bleiben")})}
    private fun capture(name: String) {
        compose.waitForIdle()
        compose.mainClock.autoAdvance=false
        compose.mainClock.advanceTimeBy(2000)
        val directory=File("build/outputs/native-visual");directory.mkdirs()
        compose.onRoot().captureToImage().asAndroidBitmap().let {image->File(directory,"$name.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}}
        File(directory,"$name.tree.txt").writeText(compose.onRoot().printToString())
        compose.mainClock.autoAdvance=true
    }
    private fun frame(tab: String="Heute",width: Int=427,height: Int=852,fontScale: Float=1f,content: @Composable ColumnScope.()->Unit) {
        compose.setContent {HealthCoachTheme(darkTheme=false) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,fontScale), androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                Column(Modifier.requiredSize(width.dp,height.dp).background(MaterialTheme.colorScheme.background)) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=24.dp,vertical=24.dp),verticalArrangement=Arrangement.spacedBy(25.dp),content=content)
                    V2BottomNavigation(tab,{},insets=WindowInsets(0,0,0,0))
                }
            }
        }}
    }
    private fun today(width: Int=427,height: Int=852,fontScale: Float=1f,onDetail: (String)->Unit={})=frame(width=width,height=height,fontScale=fontScale) {
        V2TodayScreen("Noah","Pip",scores,allDays,date,"Google Health",true,false,"Uhrendaten abrufen",plan,{}, {},onDetail,{},{},{},{},{},{},{})
    }
    @Test fun bundledSymbolsContainCriticalNavigationAndActionGlyphs() {
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val paint=android.graphics.Paint().apply {typeface=context.resources.getFont(R.font.material_symbols_rounded)}
        listOf(0xe9b2,0xf190,0xe8af,0xe5d5,0xe163,0xe668,0xf159,0xe87d).forEach {assertTrue("Missing symbol ${it.toString(16)}",paint.hasGlyph(it.toChar().toString()))}
    }
    @Test fun todayScreenshotAndScoreNavigation() {var opened: String?=null;today(onDetail={opened=it});capture("today");compose.onNodeWithText("Körperenergie").performClick();assertEquals(E.BODY_BATTERY_ID,opened)}
    @Test fun energyDetailsAndPointPicker() {
        frame {V2ScoreDetails(E.BODY_BATTERY_ID,scores[E.BODY_BATTERY_ID],day,allDays,"Pip",{},{},{},{})}
        capture("energy")
        compose.onNodeWithContentDescription("Werte auswählen").performClick()
        compose.onNodeWithText("91 Pkt.").performClick()
        compose.onNodeWithText("91 Pkt.").assertIsDisplayed()
    }
    @Test fun sleepAndBenchmarkTab() {
        frame {V2ScoreDetails(E.SLEEP_ID,scores[E.SLEEP_ID],day,allDays,"Pip",{},{},{},{})};capture("sleep")
        compose.onNodeWithText("Benchmarks").performClick();compose.onNodeWithText("Dein üblicher Bereich").assertIsDisplayed();capture("sleep-benchmarks")
    }
    @Test fun weeklyLoadDetails() {frame {V2ScoreDetails(E.SESSION_LOAD_ID,scores[E.SESSION_LOAD_ID],day,allDays,"Pip",{},{},{},{})};capture("load")}
    @Test fun trainingTimeline() {frame("Training") {V2TrainingWeek(plan,allDays,date,"Google Health",{},{})};compose.onNodeWithText("Kraft & Stabilität").assertExists();capture("training")
        compose.onNodeWithContentDescription("Nächste Trainingswoche").performClick()
        compose.onNodeWithText("Zur aktuellen Woche").assertIsDisplayed()
        compose.onNodeWithText("Kraft & Stabilität").assertDoesNotExist()
        capture("training-next")
        compose.onNodeWithText("Zur aktuellen Woche").performClick()
        compose.onNodeWithText("Kraft & Stabilität").assertExists()
    }
    @Test fun trendsAndActualPeriodSelection() {
        frame("Trends") {V2Trends(allDays,"Pip","Google Health",today=date) {_,_->}};capture("trends")
        compose.onNodeWithText("30 T").performClick();compose.onNodeWithText("30 T").assertIsSelected();capture("trends-30")
        compose.onNodeWithText("90 T").performClick();compose.onNodeWithText("90 T").assertIsSelected();capture("trends-90")
    }
    @Test fun coachWelcomeAndDraft() {
        compose.setContent {HealthCoachTheme(darkTheme=false) {val scope=rememberCoroutineScope();Column(Modifier.requiredSize(427.dp,852.dp).background(MaterialTheme.colorScheme.background)) {androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onBackground) {CoachChatScreen("Pip","Noah",AiConfiguration(routerKey="test-only"),day,remember {CoachConversationState()},scope,Modifier.weight(1f),plan=plan,configure={})};V2BottomNavigation("Coach",{},insets=WindowInsets(0,0,0,0))}}}
        capture("coach");compose.onNodeWithText("Meine Energie verstehen").performClick();compose.onNodeWithContentDescription("Nachricht an Pip").assertTextEquals("Erkläre mir meine Energie.");capture("coach-draft")
    }
    @Test fun profileSettingsCollapseAndEdit() {
        frame {V2ProfileScreen("Noah","Pip","Google Health",{},{},{},{},{},{},connection={Text("Freigabedetails im Test")},ai={AiSettings(AiConfiguration(geminiKey="test-only",routerKey="test-only"),true,null,false) {}},diagnostics={},localData={})};capture("profile")
        compose.onNodeWithText("Gemini API-Key").assertDoesNotExist()
        compose.onNodeWithText("Verbindung verwalten").performClick()
        compose.onNodeWithText("Freigabedetails im Test").assertIsDisplayed()
        compose.onNodeWithText("Verbindungseinstellungen schließen").performScrollTo().performClick()
        compose.onNodeWithText("Freigabedetails im Test").assertDoesNotExist()
        compose.onNodeWithText("Bearbeiten").performScrollTo().performClick()
        compose.onNodeWithText("Groq API-Key").performScrollTo().assertIsDisplayed()
        capture("ai-settings-edit")
        compose.onNodeWithText("Gemini API-Key").performScrollTo().assertIsDisplayed()
    }
    @Test fun onboardingNameAndNextStep() {
        compose.setContent {HealthCoachTheme(darkTheme=false) {Box(Modifier.requiredSize(427.dp,852.dp)) {V2Onboarding("Noah","Pip",null,false,false,null) {_,_,_,_,_->}}}};capture("onboarding")
        compose.onNodeWithText("Weiter").performClick();compose.onNodeWithText("Und wie soll ich heißen?").assertIsDisplayed()
    }
    @Test @Config(qualifiers="w360dp-h800dp-mdpi") fun largeTextKeepsMainNavigationReachable() {
        today(width=360,height=800,fontScale=1.5f);compose.onNodeWithText("Coach",useUnmergedTree=true).assertIsDisplayed();capture("today-large")
        compose.onNodeWithText("Ziele bearbeiten").performScrollTo().assertIsDisplayed()
    }
    @Test fun profileBackReturnsToCoachInsteadOfResettingToToday() {
        compose.mainClock.autoAdvance=true
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("robot_companion",0).edit().putInt("onboarding_version",3).putString("user_name","Noah").putString("name","Pip").commit()
        compose.setContent {HealthCoachTheme(darkTheme=false) {ProductExperience(emptyMap(),emptyList(),false,"Test",false,false,null,{},{},{},{},{},{},emptyMap(),emptyMap(),{_,_->})}}
        compose.onNodeWithText("Coach").performClick();compose.onNodeWithContentDescription("Profil öffnen").performClick();compose.onNodeWithText("Dein Profil").assertIsDisplayed()
        compose.onNodeWithContentDescription("Zurück").performClick();compose.onNodeWithText("DEIN PERSÖNLICHER COACH").assertIsDisplayed()
    }
    @Test fun clayPressMovesImmediatelyAndCancellationDoesNotActivate() {
        var clicks=0
        compose.setContent {HealthCoachTheme(darkTheme=false) {Box(Modifier.requiredSize(427.dp,200.dp).background(MaterialTheme.colorScheme.background).padding(24.dp)) {ClayButton(onClick={clicks++}) {Text("Testknopf")}}}}
        val button=compose.onNodeWithText("Testknopf")
        val top=button.fetchSemanticsNode().boundsInRoot.top
        button.performTouchInput {down(center)}
        compose.mainClock.advanceTimeByFrame()
        assertEquals(0,clicks)
        assertTrue("Clay face must move on pointer down",button.fetchSemanticsNode().boundsInRoot.top>top)
        button.performTouchInput {cancel()}
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(0,clicks)
        button.performClick()
        assertEquals(1,clicks)
    }

    @Test fun compactCoachKeepsRobotAndComposerTogether() {
        lateinit var state: CoachConversationState
        compose.setContent {HealthCoachTheme(darkTheme=false) {val scope=rememberCoroutineScope();state=remember {CoachConversationState().apply {question="Erkläre mir meine Energie."}}
            Box(Modifier.requiredSize(427.dp,410.dp).background(MaterialTheme.colorScheme.background)) {androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onBackground) {CoachChatScreen("Pip","Noah",AiConfiguration(routerKey="test-only"),day,state,scope,Modifier.fillMaxSize(),plan=plan,keyboardVisible=true,configure={})}}
        }}
        compose.onNodeWithText("Ich bin ganz Ohr.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Nachricht an Pip").assertIsDisplayed()
        compose.onNodeWithText("Meine Energie verstehen").assertDoesNotExist()
        capture("coach-compact")
        compose.runOnIdle {state.messages=listOf(CoachMessage("assistant","Dein Plan bleibt in deinem Tempo.",provider="Gemini"))}
        compose.onNodeWithText("Dein Plan bleibt in deinem Tempo.").assertIsDisplayed()
        compose.onNodeWithTag("coach-header-avatar").assertIsDisplayed()
        assertTrue(compose.onNodeWithTag("coach-message-list").fetchSemanticsNode().boundsInRoot.height>=180f)
        compose.onNodeWithContentDescription("Nachricht an Pip").assertIsDisplayed()
        capture("coach-compact-conversation")
    }

    @Test fun conversationUsesHeaderAvatarAndGivesHistoryMostOfTheScreen() {
        compose.mainClock.autoAdvance=true
        compose.setContent {HealthCoachTheme(darkTheme=false) {val scope=rememberCoroutineScope();val state=remember {CoachConversationState().apply {messages=listOf(CoachMessage("assistant","Deine Werte im Blick.",provider="Groq"))}}
            Column(Modifier.requiredSize(427.dp,852.dp).background(MaterialTheme.colorScheme.background)) {
                CoachChatScreen("Pip","Noah",AiConfiguration(groqKey="test-only"),day,state,scope,Modifier.weight(1f),plan=plan,configure={})
                V2BottomNavigation("Coach",{},insets=WindowInsets(0,0,0,0))
            }
        }}
        compose.onNodeWithTag("coach-header-avatar").assertIsDisplayed()
        compose.onNodeWithText("DEIN PERSÖNLICHER COACH").assertDoesNotExist()
        val history=compose.onNodeWithTag("coach-message-list").fetchSemanticsNode().boundsInRoot
        assertTrue("Conversation starts directly below its compact app bar",history.top<=80f)
        assertTrue("Conversation receives most of the usable display",history.height>=500f)
        capture("coach-conversation-header")
    }

    @Test fun imeInsetIsAppliedOnceAndComposerSitsAboveKeyboard() {
        var keyboardHeight by androidx.compose.runtime.mutableIntStateOf(0)
        compose.mainClock.autoAdvance=true
        compose.setContent {HealthCoachTheme(darkTheme=false) {val scope=rememberCoroutineScope();val state=remember {CoachConversationState().apply {question="Ein Entwurf"}}
            Box(Modifier.requiredSize(427.dp,852.dp)) {
                V2InsetScaffold("Coach",{},remember {androidx.compose.material3.SnackbarHostState()},keyboardInsets=WindowInsets(bottom=keyboardHeight),systemInsets=WindowInsets(top=24,bottom=24)) {modifier,visible->
                    Box(modifier) {Box(Modifier.fillMaxSize().semantics {contentDescription="App-Nutzfläche"}) {
                        CoachChatScreen("Pip","Noah",AiConfiguration(routerKey="test-only"),day,state,scope,Modifier.fillMaxSize(),keyboardVisible=visible,configure={})
                    }}
                }
            }
        }}
        compose.onNodeWithText("Trends",useUnmergedTree=true).assertIsDisplayed()
        compose.runOnIdle {keyboardHeight=320}
        compose.onNodeWithText("Trends",useUnmergedTree=true).assertDoesNotExist()
        val bottom=compose.onNodeWithContentDescription("App-Nutzfläche").fetchSemanticsNode().boundsInRoot.bottom
        assertEquals("Only one 320px keyboard inset",532f,bottom,1f)
        val composerBottom=compose.onNodeWithTag("coach-composer").fetchSemanticsNode().boundsInRoot.bottom
        assertTrue("Composer must meet keyboard with only its small bottom gap",bottom-composerBottom in 0f..12f)
        capture("coach-ime-insets")
        compose.runOnIdle {keyboardHeight=0}
        compose.onNodeWithText("Trends",useUnmergedTree=true).assertIsDisplayed()
    }

    @Test fun yesterdayShowsItsOwnCockpitAndKeepsDateWhenOpeningScore() {
        compose.mainClock.autoAdvance=true
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("robot_companion",0).edit().putInt("onboarding_version",3).putString("user_name","Noah").putString("name","Pip").commit()
        val current=LocalDate.now();val past=day.copy(date=current.minusDays(1),scores=scores+(E.BODY_BATTERY_ID to score(E.BODY_BATTERY_ID,54)))
        compose.setContent {HealthCoachTheme(darkTheme=false) {ProductExperience(scores,listOf(past,day.copy(date=current)),false,"Test",true,false,null,{},{},{},{},{},{},emptyMap(),emptyMap(),{_,_->},sourceName="Google Health")}}
        compose.onNodeWithContentDescription("Tag auswählen").performClick()
        compose.onNodeWithTag("detail-sheet").assertExists()
        compose.onNodeWithText("Gestern").performClick()
        compose.onNodeWithText("Dein Rückblick.").assertIsDisplayed()
        compose.onNodeWithText("Dein Akku für heute").assertDoesNotExist()
        compose.onNodeWithText("Dein gespeicherter Tagesakku").assertIsDisplayed()
        compose.onNodeWithText("54").assertIsDisplayed()
        capture("yesterday")
        compose.onNodeWithText("Körperenergie").performClick()
        compose.onNodeWithText("Deine Körperenergie").assertIsDisplayed()
        compose.onNodeWithTag("detail-score").assertTextEquals("54").assertIsDisplayed()
    }
    @Test fun activityDetailsUseExpandableSheetInsteadOfAlertDialog() {
        compose.mainClock.autoAdvance=true
        var open by mutableStateOf(false)
        frame {ClayButton(onClick={open=true}) {Text("Training ansehen")}
            if(open) V2DetailSheet({open=false},title={Text("Radfahren")},text={Text("21 Minuten")},confirmButton={TextButton(onClick={open=false}) {Text("Fertig")}})
        }
        compose.onNodeWithText("Training ansehen").performClick()
        compose.onNodeWithTag("detail-sheet").assertExists()
        compose.onNodeWithText("Radfahren").assertIsDisplayed()
        if(compose.onAllNodesWithText("Details erweitern").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("Details erweitern").performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance=false
        compose.mainClock.advanceTimeBy(2000)
        compose.onNodeWithText("Fertig").assertIsDisplayed()
        val directory=File("build/outputs/native-visual");directory.mkdirs()
        File(directory,"detail-sheet.tree.txt").writeText(compose.onNodeWithTag("detail-sheet").printToString())
        (if(compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty()) compose.onNode(isDialog()) else compose.onNodeWithTag("detail-sheet")).captureToImage().asAndroidBitmap().let {bitmap->File(directory,"detail-sheet.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
        compose.mainClock.autoAdvance=true
        compose.onNodeWithText("Fertig").performClick()
        compose.onNodeWithTag("detail-sheet").assertDoesNotExist()
    }

    @Test fun undoSnackbarIsShortAndCanBeDismissed() {
        compose.mainClock.autoAdvance=true
        lateinit var host: SnackbarHostState
        compose.setContent {HealthCoachTheme(darkTheme=false) {
            host=remember {SnackbarHostState()}
            LaunchedEffect(Unit) {showAppNotice(host,"Ziele gespeichert",true)}
            Box(Modifier.requiredSize(427.dp,852.dp)) {SnackbarHost(host)}
        }}
        compose.onNodeWithText("Ziele gespeichert").assertIsDisplayed()
        compose.runOnIdle {assertEquals(SnackbarDuration.Short,host.currentSnackbarData!!.visuals.duration);assertTrue(host.currentSnackbarData!!.visuals.withDismissAction);host.currentSnackbarData!!.dismiss()}
        compose.onNodeWithText("Ziele gespeichert").assertDoesNotExist()
    }


    @Test fun journalPromptIsProminentUntilRelevantDayIsComplete() {
        var opened: LocalDate?=null
        val journal=WellnessJournal(activeIds=listOf("relaxation"))
        frame {V2TodayScreen("Noah","Pip",scores,allDays,date,"Google Health",true,false,"",plan,{},{},{},{},{},{},{},{},{},{},
            journalProminent=true,journalCard={JournalHomeCard(journal,date,LocalTime.of(20,0),"Pip",true) {opened=it}})}
        compose.onNodeWithTag("journal-home-prompt").assertIsDisplayed()
        capture("journal-home")
        compose.onNodeWithText("Logbuch ausfüllen").performClick();assertEquals(date,opened)
    }
    @Test fun journalNoAnswerCompletesAnHonestCheckInWithoutTraining() {
        var stored by mutableStateOf(WellnessJournal(activeIds=listOf("relaxation")))
        compose.setContent {HealthCoachTheme(darkTheme=false) {Box(Modifier.requiredSize(427.dp,852.dp).background(MaterialTheme.colorScheme.background)) {
            JournalScreen(stored,date,date,"Pip",emptyList(),false,false,null,Modifier.fillMaxSize(),{},{},{}) {next,done->stored=next;done()}
        }}}
        capture("journal-question")
        compose.onNodeWithText("Nein").performClick()
        compose.onNodeWithTag("complete-journal").assertIsDisplayed().performClick()
        assertEquals(false,stored.entries.single().answers.getValue("relaxation").yes)
        assertEquals(1,journalStreak(stored,date))
        capture("journal-completed")
    }
    @Test fun customJournalQuestionCanBeCreatedInTheNativeLibrary() {
        compose.mainClock.autoAdvance=true
        var stored by mutableStateOf(WellnessJournal(activeIds=listOf("relaxation")))
        compose.setContent {HealthCoachTheme(darkTheme=false) {Box(Modifier.requiredSize(427.dp,852.dp).background(MaterialTheme.colorScheme.background)) {
            JournalScreen(stored,date,date,"Pip",emptyList(),false,false,null,Modifier.fillMaxSize(),{},{},{}) {next,done->stored=next;done()}
        }}}
        compose.onNodeWithContentDescription("Fragen auswählen").performClick()
        compose.onNodeWithText("Eigene Frage erstellen").performScrollTo().performClick()
        compose.onNodeWithText("Kurzer Name (optional)").performScrollTo().performTextInput("Meine Abendroutine")
        compose.onNodeWithText("Deine tägliche Frage").performScrollTo().performTextInput("Hast du deine Abendroutine gemacht?")
        compose.onNodeWithText("Eigene Frage hinzufügen").performScrollTo().performClick()
        assertTrue(stored.habits.any {it.custom&&it.title=="Meine Abendroutine"})
        assertEquals(2,stored.activeIds.size)
    }
    @Test fun customJournalDraftIsPersistedAndAppliedByTheMainSaveButton() {
        compose.mainClock.autoAdvance=true
        var stored by mutableStateOf(WellnessJournal(activeIds=listOf("relaxation")))
        compose.setContent {HealthCoachTheme(darkTheme=false) {Box(Modifier.requiredSize(427.dp,852.dp)) {
            JournalScreen(stored,date,date,"Pip",emptyList(),false,false,null,Modifier.fillMaxSize(),{},{},{}) {next,done->
                stored=decodeWellnessJournal(encodeWellnessJournal(prepareJournalSave(stored,next,date).journal));done()
            }
        }}}
        compose.onNodeWithContentDescription("Fragen auswählen").performClick()
        compose.onNodeWithText("Eigene Frage erstellen").performScrollTo().performClick()
        compose.onNodeWithText("Deine tägliche Frage").performScrollTo().performTextInput("Hast du gelesen?")
        compose.onNodeWithText("Fragen übernehmen").performClick()
        val added=stored.habits.single {it.custom}
        assertTrue(added.id in stored.activeIds)
        compose.onNodeWithText("Ja").performClick()
        compose.onNodeWithText("Hast du gelesen?").assertIsDisplayed()
        capture("journal-custom-applied")
    }

    @Test fun coachAtPickerAttachesAndRemovesDatedDataWithoutSending() {
        compose.mainClock.autoAdvance=true
        val state=CoachConversationState().apply {question="Erkläre @"}
        compose.setContent {HealthCoachTheme(darkTheme=false) {val scope=rememberCoroutineScope();Box(Modifier.requiredSize(427.dp,650.dp)) {
            CoachChatScreen("Pip","Noah",AiConfiguration(groqKey="test",shareHealthSummary=true),day,state,scope,Modifier.fillMaxSize(),keyboardVisible=true,appData=CoachDataAccess(allDays,WellnessJournal(),plan,date),configure={})
        }}}
        compose.onNodeWithText("Persönliche Daten anhängen").assertIsDisplayed()
        compose.onNodeWithText("HRV").performClick()
        assertEquals("Erkläre @HRV ",state.question)
        compose.onNodeWithText("Persönliche Daten anhängen").assertDoesNotExist()
        capture("coach-at-attachment")
        compose.onNodeWithText("HRV").performClick()
        assertFalse(state.question.contains("@HRV"))
        assertTrue(state.messages.isEmpty())
    }

    @Test fun robotExpressionsAndSideViewCargoRoadHaveAccessibleStates() {
        frame {Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(RobotMood.CHEERFUL,RobotMood.CAREFUL,RobotMood.RECOVER,RobotMood.SLEEPING,RobotMood.WAKING,RobotMood.THINKING).chunked(3).forEach {row->
                Row {row.forEach {mood->Column(Modifier.weight(1f),horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally) {
                    RobotCompanion("Pip",false,Modifier.size(100.dp),mood=mood);Text(mood.description,fontSize=androidx.compose.ui.unit.TextUnit.Unspecified)
                }}}
            }
            RobotLoadingRoad("Pip","Schlafphasen einordnen",loading=true)
        }}
        compose.onNodeWithContentDescription("Pip, dein kleiner Roboter: schläft. Antippen zum Begrüßen.").assertExists()
        capture("robot-expression-gallery")
    }
    @Test fun completedRoadEndsAtTheRightWithoutClaimingAPercentage() {
        frame {RobotLoadingRoad("Pip","Fertig",loading=false)}
        compose.onNodeWithText("Alles verstaut. Dein gespeicherter Stand ist da.").assertIsDisplayed()
        capture("robot-road-completed")
    }

    @Test fun robotActuallyAnimatesOnOpeningWithoutATap() {
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        Settings.Global.putFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)
        frame {RobotCompanion("Pip",false,Modifier.size(180.dp),mood=RobotMood.CELEBRATE)}
        compose.mainClock.advanceTimeBy(160);compose.waitForIdle()
        val before=requireNotNull(compose.onRoot().captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888,false))
        compose.mainClock.advanceTimeBy(800);compose.waitForIdle()
        val after=compose.onRoot().captureToImage().asAndroidBitmap()
        assertFalse("Opening reaction should change pixels without a tap",before.sameAs(after))
        val out=File("build/outputs/native-visual");out.mkdirs()
        File(out,"robot-celebrate-start.png").outputStream().use {before.compress(Bitmap.CompressFormat.PNG,100,it)}
        File(out,"robot-celebrate-motion.png").outputStream().use {after.compress(Bitmap.CompressFormat.PNG,100,it)}
    }

    @Test fun coachTablesRenderAsCellsInsteadOfVisibleSeparators() {
        frame {CoachMarkdown("| Kennzahl | Wert |\n|---|---|\n| **HRV** | 36 ms |")}
        compose.onNodeWithText("Kennzahl").assertIsDisplayed()
        compose.onNodeWithText("HRV").assertIsDisplayed()
        compose.onNodeWithText("|---|---|").assertDoesNotExist()
        capture("coach-table")
    }

    @Test fun actionableTrainingSheetStartsExpandedWithCompletionButtonReachable() {
        var complete=false
        frame {V2DetailSheet({},title={Text("Heute Radfahren")},text={Text("30 Minuten")},startExpanded=true,confirmButton={ClayButton(onClick={complete=true}) {Text("Als erledigt bestätigen")}})}
        compose.mainClock.autoAdvance=true
        compose.onNodeWithText("Als erledigt bestätigen").assertIsDisplayed().performClick()
        assertTrue(complete)
    }

    @Test fun journalInsightsShowUncertaintyAndAccessibleCoachAction() {
        compose.mainClock.autoAdvance=true
        val habit=journalHabitLibrary.single {it.id=="relaxation"}
        val insight=JournalInsight(habit,JournalOutcome.MORNING_ENERGY,15,15,82.0,74.0,7.5,2.2,12.0,"consistent","Google Health / synthetische Testdaten",listOf("Training am Verhaltenstag"),comparisons=(1L..30L).map {n->JournalComparison(date.minusDays(31-n),n%2==0L,if(n%2==0L) 82.0 else 74.0)})
        compose.setContent {HealthCoachTheme(darkTheme=false) {Box(Modifier.requiredSize(427.dp,852.dp).background(MaterialTheme.colorScheme.background)) {
            JournalScreen(WellnessJournal(activeIds=listOf(habit.id)),date,date,"Pip",listOf(insight),false,false,null,Modifier.fillMaxSize(),{},{},{}) {_,done->done()}
        }}}
        compose.onNodeWithText("Muster").performClick();capture("journal-insights")
        compose.onNodeWithText(habit.title).performClick()
        compose.onNodeWithText("Mit Pip besprechen").assertIsDisplayed()
        compose.mainClock.autoAdvance=false;compose.mainClock.advanceTimeBy(2000)
        val directory=File("build/outputs/native-visual");directory.mkdirs()
        compose.onNode(isDialog()).captureToImage().asAndroidBitmap().let {bitmap->File(directory,"journal-insight-detail.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
        compose.mainClock.autoAdvance=true
    }
    @Test fun missingTodayEnergyKeepsSavedValueAndOpensItsOriginalDay() {
        compose.mainClock.autoAdvance=true
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("robot_companion",0).edit().putInt("onboarding_version",3).putString("user_name","Noah").putString("name","Pip").commit()
        val current=LocalDate.now();val pastDate=current.minusDays(1);val at=pastDate.atTime(14,0).toInstant(ZoneOffset.UTC)
        val saved=score(E.BODY_BATTERY_ID,54).copy(asOf=at)
        val past=day.copy(date=pastDate,calculatedAt=at,scores=day.scores+(E.BODY_BATTERY_ID to saved))
        val fresh=day.copy(date=current,scores=day.scores+(E.BODY_BATTERY_ID to saved.copy(asOf=Instant.now(),score=null,status=E.Status.insufficient_data)))
        val shown=withLastSavedEnergy(fresh.scores,listOf(saved),Instant.now())
        compose.setContent {HealthCoachTheme(darkTheme=false) {ProductExperience(shown,listOf(past,fresh),false,"Test",true,false,null,{},{},{},{},{},{},emptyMap(),emptyMap(),{_,_->},sourceName="Google Health")}}
        compose.onNodeWithText("Körperenergie").performScrollTo().performClick()
        compose.onNodeWithTag("detail-score").assertTextEquals("54")
        compose.onNodeWithText(pastDate.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d. MMMM",java.util.Locale.GERMAN))).assertIsDisplayed()
    }
    @Test fun personalProfileValidatesAndCanRemainOptional() {
        compose.mainClock.autoAdvance=true
        var result: com.wabbo.healthconnectcoach.score.PersonalScoreProfile?=null
        frame {PersonalScoreProfileEditor(com.wabbo.healthconnectcoach.score.PersonalScoreProfile()) {result=it}}
        compose.onNodeWithContentDescription("Geburtsjahr · optional").performTextInput("2020")
        compose.onNodeWithText("Angaben speichern").performScrollTo().performClick()
        compose.onNodeWithText("Bitte ein Geburtsjahr",substring=true).assertExists()
        assertNull(result)
        compose.onNodeWithContentDescription("Geburtsjahr · optional").performScrollTo().performTextClearance()
        compose.onNodeWithText("Angaben speichern").performScrollTo().performClick()
        assertEquals(com.wabbo.healthconnectcoach.score.PersonalScoreProfile(),result)
        compose.onNodeWithContentDescription("Geburtsjahr · optional").performScrollTo().performTextInput("1996")
        compose.onNodeWithText("Angaben speichern").performScrollTo().performClick()
        assertEquals(1996,result?.birthYear)
        compose.onNodeWithContentDescription("Geburtsjahr · optional").performScrollTo()
        capture("personal-score-profile")
    }
    @Test fun onboardingIncludesOptionalPersonalScoreStep() {
        compose.mainClock.autoAdvance=true
        compose.setContent {HealthCoachTheme(darkTheme=false) {Box(Modifier.requiredSize(427.dp,852.dp)) {V2Onboarding("Noah","Pip",null,false,false,null) {_,_,_,_,_->}}}}
        compose.onNodeWithText("Weiter").performClick()
        compose.onNodeWithText("Weiter").performClick()
        compose.onNodeWithText("Was sollte ich über dich wissen?").assertExists()
        compose.onNodeWithText("Weiter").performScrollTo().performClick()
        compose.onNodeWithText("Was passt in deinen Alltag?").assertExists()
    }
    @Test fun personalProfileJsonRoundTripAndLegacyDefault() {
        val p=com.wabbo.healthconnectcoach.score.PersonalScoreProfile(1996,190,true)
        assertEquals(p,decodePersonalScoreProfile(encodePersonalScoreProfile(p)))
        assertEquals(com.wabbo.healthconnectcoach.score.PersonalScoreProfile(),decodePersonalScoreProfile(org.json.JSONObject()))
    }
    @Test fun sleepPlannerShowsWindowAndSavesChosenTime() {
        compose.mainClock.autoAdvance=true
        var saved:SleepPlannerSettings?=null
        frame {SleepPlannerScreen(null,allDays,date,"Google Health",now,ZoneOffset.UTC,null,com.wabbo.healthconnectcoach.score.PersonalScoreProfile(),"Pip",true,false,null,{}, {}, {next,done->saved=next;done()})}
        compose.onNodeWithText("Schlafplan speichern").performScrollTo().performClick()
        assertEquals(420,saved?.wakeMinute)
        compose.onNodeWithText("Dein Vorschaufenster").performScrollTo().assertExists()
        capture("sleep-planner")
    }
    @Test fun visibleCoachMemoryCanCreateEditAndRemoveEntry() {
        compose.mainClock.autoAdvance=true
        var value=CoachMemory()
        frame {var memory by remember {mutableStateOf(value)};CoachMemoryScreen(memory,date,"Pip",true,false,null,{}, {next,done->memory=next;value=next;done()})}
        compose.onNodeWithText("Erinnerung hinzufügen").performScrollTo().performClick()
        compose.onNodeWithText("Was soll dein Coach wissen?").performScrollTo().performTextInput("Ich fahre gerne Fahrrad")
        compose.onNodeWithText("Erinnerung speichern").performScrollTo().performClick()
        assertEquals("Ich fahre gerne Fahrrad",value.entries.single().text)
        compose.onNodeWithText("Bearbeiten").performScrollTo().performClick()
        compose.onNodeWithText("Was soll dein Coach wissen?").performScrollTo().performTextReplacement("Kurz und verständlich")
        compose.onNodeWithText("Erinnerung speichern").performScrollTo().performClick()
        assertEquals("Kurz und verständlich",value.entries.single().text)
        capture("coach-memory")
        compose.onNodeWithText("Entfernen").performScrollTo().performClick()
        assertTrue(value.entries.isEmpty())
    }
    @Test fun vitalCheckShowsFiveSignalsAndPersonalRanges() {
        compose.mainClock.autoAdvance=true
        frame {VitalCheckScreen(allDays,date,"Google Health","Pip",{}, {})}
        compose.onNodeWithText("Herzfrequenzvariabilität").assertExists()
        compose.onAllNodesWithText("In deinem Bereich",substring=true).onFirst().assertExists()
        capture("vital-check")
        compose.onNodeWithText("Hauttemperatur-Abweichung").performScrollTo().assertExists()
    }
}
