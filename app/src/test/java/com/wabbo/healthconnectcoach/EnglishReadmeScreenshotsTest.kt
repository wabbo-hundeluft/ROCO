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
@Config(sdk=[36],qualifiers="en-rUS-w427dp-h852dp-mdpi",application=Application::class)
class EnglishReadmeScreenshotsTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var previousZone: TimeZone
    @Before fun prepare() {
        java.util.Locale.setDefault(java.util.Locale.ENGLISH)
        RocoStrings.initialize(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext)
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
    private val walk=ActivityInsight("walk","Brisk walk",walkStart,walkStart.plusSeconds(38*60),38.0,16.0,130.0,143.0,listOf(105,122,132,140,135,137,143,130,125).mapIndexed {i,v->walkStart.plusSeconds(i*285L) to v.toDouble()},false)
    private val day=DayInsight(date,now,false,scores,sleepStart=start,sleepEnd=start.plusSeconds(515*60),sleepBands=sleepBands,
        metrics=mapOf("hrv" to 52.0,"rhr" to 57.0,"hrv_baseline" to 48.0,"rhr_baseline" to 55.0,"hrv_baseline_days" to 26.0,"rhr_baseline_days" to 26.0,"workout_minutes" to 38.0,"sleep_minutes" to 492.0,"sleep_goal" to 480.0,"efficiency" to 95.5,"stage_LIGHT" to 270.0,"stage_DEEP" to 92.0,"stage_REM" to 130.0,"stage_AWAKE" to 23.0),
        sources=mapOf("_connector" to "Google Health","hrv" to "watch","rhr" to "watch","sleep" to "watch"),activities=listOf(walk),
        energyCurve=listOf(460 to 91,540 to 89,630 to 85,690 to 77,720 to 71,780 to 71,840 to 71).map {(minute,value)->date.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(minute*60L) to value})
    private val allDays=(26 downTo 1).map {offset->
        val d=date.minusDays(offset.toLong());val recent=listOf(47.0,49.0,44.0,51.0,48.0,50.0)
        day.copy(date=d,calculatedAt=d.atTime(14,0).toInstant(ZoneOffset.UTC),metrics=day.metrics+mapOf("hrv" to if(offset<=6) recent[6-offset] else listOf(44.0,48.0,52.0)[offset%3],"workout_minutes" to if(d==date.minusDays(5)) 45.0 else if(d==date.minusDays(3)) 29.0 else 0.0))
    }+day
    private val plan=TrainingPlanLogic.create(TrainingGoals(days=setOf(2,4,7),sessionMinutes=20,intensity=1.0),date).let {p->p.copy(sessions=listOf(PlannedSession("tue",date.minusDays(5),"Strength & stability",45,1.5,true),PlannedSession("thu",date.minusDays(3),"Easy cycling",29,1.0,true))+p.sessions.map {it.copy(title="Keep moving")})}
    private fun capture(name: String) {
        compose.waitForIdle()
        compose.mainClock.autoAdvance=false
        compose.mainClock.advanceTimeBy(2000)
        val directory=File("build/outputs/readme-en");directory.mkdirs()
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
        V2TodayScreen("Noah","Pip",scores,allDays,date,"Google Health",true,false,"Fetching watch data",plan,{}, {},onDetail,{},{},{},{},{},{},{})
    }
    @Test fun todayEnglish() { today(); compose.onNodeWithText("Body energy").assertExists(); capture("today") }
    @Test fun sleepEnglish() { frame { V2ScoreDetails(E.SLEEP_ID,scores[E.SLEEP_ID],day,allDays,"Pip",{},{},{},{}) }; compose.onAllNodesWithText("Awake").assertCountEquals(2); capture("sleep") }
    @Test fun trainingEnglish() { frame("Training") {V2TrainingWeek(plan,allDays,date,"Google Health",{},{})}; capture("training") }
    @Test fun trendsEnglish() { frame("Trends") {V2Trends(allDays,"Pip","Google Health",today=date) {_,_->}}; capture("trends") }
    @Test fun coachEnglish() {
        compose.setContent {HealthCoachTheme(darkTheme=false) {val scope=rememberCoroutineScope();Column(Modifier.requiredSize(427.dp,852.dp).background(MaterialTheme.colorScheme.background)) {androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onBackground) {CoachChatScreen("Pip","Noah",AiConfiguration(routerKey="test-only"),day,remember {CoachConversationState()},scope,Modifier.weight(1f),plan=plan,configure={})};V2BottomNavigation("Coach",{},insets=WindowInsets(0,0,0,0))}}}
        capture("coach")
    }
    @Test fun journalEnglish() {
        var stored by mutableStateOf(WellnessJournal(activeIds=listOf("relaxation")))
        compose.setContent {HealthCoachTheme(darkTheme=false) {Box(Modifier.requiredSize(427.dp,852.dp).background(MaterialTheme.colorScheme.background)) {
            JournalScreen(stored,date,date,"Pip",emptyList(),false,false,null,Modifier.fillMaxSize(),{},{},{}) {next,done->stored=next;done()}
        }}}
        compose.onNodeWithText("Yes").assertExists(); compose.onNodeWithText("No").assertExists(); capture("journal-question")
    }
}
