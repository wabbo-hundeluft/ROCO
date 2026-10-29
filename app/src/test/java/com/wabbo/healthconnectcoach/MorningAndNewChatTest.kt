package com.wabbo.healthconnectcoach

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.wabbo.healthconnectcoach.ui.theme.HealthCoachTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[36],qualifiers="de-rDE-w360dp-h800dp-mdpi",application=Application::class)
class MorningAndNewChatTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences get()=context.getSharedPreferences("robot_companion",Context.MODE_PRIVATE)
    private var originalScale=1f
    private val morning=LocalDateTime.of(2026,10,29,7,30)
    @Before fun setup() {
        originalScale=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)
        Settings.Global.putFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,0f)
        preferences.edit().remove("last_robot_wake").commit()
        compose.mainClock.autoAdvance=false
    }
    @After fun restore() {
        Settings.Global.putFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,originalScale)
        preferences.edit().remove("last_robot_wake").commit()
    }
    private fun capture(name:String) {
        val out=File("build/outputs/coach-flow").apply {mkdirs()}
        File(out,"$name.png").outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun morningWaitsThroughLoadingThenUnfoldsOnlyOnceForTheVisibleRobot() {
        Settings.Global.putFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)
        var show by mutableStateOf(false)
        var observed:RobotMood?=null
        compose.setContent {Box(Modifier.requiredSize(240.dp).clipToBounds()) {
            RobotCircadianHost(clock={morning}) {
                val value=LocalRobotMoment.current;SideEffect {observed=value}
                RobotCompanion("Pip",false,Modifier.offset(x=if(show) 0.dp else 1000.dp).size(240.dp))
            }
        }}
        compose.mainClock.advanceTimeBy(5000);compose.waitForIdle()
        assertNull("Loading must not consume the morning greeting",preferences.getString("last_robot_wake",null))
        compose.runOnIdle {show=true;Snapshot.sendApplyNotifications()}
        compose.mainClock.advanceTimeBy(300);compose.waitForIdle()
        assertTrue("moment=$observed; wake=${preferences.getString("last_robot_wake",null)}; "+compose.onRoot().printToString(),compose.onAllNodesWithContentDescription("Pip, dein kleiner Roboter: wacht auf und streckt sich. Antippen zum Begrüßen.").fetchSemanticsNodes().isNotEmpty())
        assertEquals(morning.toLocalDate().toString(),preferences.getString("last_robot_wake",null))
        capture("morning-unfolding")
        compose.mainClock.advanceTimeBy(1700);compose.waitForIdle()
        val ready=compose.onRoot().captureToImage().asAndroidBitmap()
        var visibleEyes=0
        for(y in 0 until 110) for(x in 0 until ready.width) {
            val pixel=ready.getPixel(x,y)
            if(android.graphics.Color.green(pixel)-android.graphics.Color.red(pixel)>25&&android.graphics.Color.blue(pixel)-android.graphics.Color.red(pixel)>10) visibleEyes++
        }
        assertTrue("The optical head must actually emerge above the shell",visibleEyes>400)
        capture("morning-ready")
        compose.mainClock.advanceTimeBy(2800);compose.waitForIdle()
        compose.onNodeWithContentDescription("Pip, dein kleiner Roboter: wacht auf und streckt sich. Antippen zum Begrüßen.").assertDoesNotExist()
        compose.runOnIdle {show=false;Snapshot.sendApplyNotifications()};compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle {show=true;Snapshot.sendApplyNotifications()};compose.mainClock.advanceTimeBy(300);compose.waitForIdle()
        compose.onNodeWithContentDescription("Pip, dein kleiner Roboter: wacht auf und streckt sich. Antippen zum Begrüßen.").assertDoesNotExist()
    }
    @Test fun reducedMotionStillAcknowledgesTheMorningWithoutWaitingForAnAnimation() {
        compose.setContent {RobotCircadianHost(clock={morning}) {RobotCompanion("Pip",false,Modifier.size(180.dp))}}
        compose.mainClock.advanceTimeBy(400);compose.waitForIdle()
        assertEquals(morning.toLocalDate().toString(),preferences.getString("last_robot_wake",null))
    }
    @Test fun newChatReturnsToLargeWelcomeAndCanResumeThePreviousConversation() {
        compose.mainClock.autoAdvance=true
        val old=listOf(CoachMessage("user","Hallo"),CoachMessage("assistant","Hallo Noah",provider="Groq"))
        val state=CoachConversationState().apply {messages=old}
        compose.setContent {HealthCoachTheme(darkTheme=false) {
            val scope=rememberCoroutineScope()
            Box(Modifier.requiredSize(360.dp,740.dp)) {CoachChatScreen("Pip","Noah",AiConfiguration(),null,state,scope,Modifier.fillMaxSize(),openMemory={},configure={})}
        }}
        compose.onNodeWithTag("coach-header-avatar").assertIsDisplayed()
        compose.onNodeWithTag("new-coach-chat").performClick()
        compose.onNodeWithTag("coach-header-avatar").assertDoesNotExist()
        val avatar=compose.onNodeWithTag("coach-welcome-avatar").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(avatar.height>=190f)
        assertEquals(old,state.previousMessages);assertTrue(state.messages.isEmpty())
        capture("new-chat-welcome")
        compose.onNodeWithText("Letzten Chat fortsetzen").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Letzten Chat fortsetzen").performClick()
        compose.onNodeWithTag("coach-header-avatar").assertIsDisplayed();assertEquals(old,state.messages)
    }
    @Test fun firstDraftKeepsWelcomeAndOnlyASentMessageSwitchesToTheConversationHeader() {
        compose.mainClock.autoAdvance=true
        val state=CoachConversationState();var keyboard by mutableStateOf(false)
        compose.setContent {HealthCoachTheme(darkTheme=false) {
            val scope=rememberCoroutineScope()
            Box(Modifier.requiredSize(360.dp,if(keyboard) 410.dp else 740.dp)) {CoachChatScreen("Pip","Noah",AiConfiguration(),null,state,scope,Modifier.fillMaxSize(),keyboardVisible=keyboard,configure={})}
        }}
        compose.runOnIdle {keyboard=true;state.question="Ein erster Entwurf"}
        compose.onNodeWithTag("coach-welcome-avatar").assertIsDisplayed()
        compose.onNodeWithTag("coach-header-avatar").assertDoesNotExist()
        compose.onNodeWithTag("coach-composer").assertIsDisplayed()
        compose.runOnIdle {state.messages=listOf(CoachMessage("user",state.question));state.question=""}
        compose.onNodeWithTag("coach-welcome-avatar").assertDoesNotExist()
        val avatar=compose.onNodeWithTag("coach-header-avatar").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(avatar.height<=48f)
        state.busy=true
        compose.onNodeWithTag("new-coach-chat").assertIsNotEnabled()
        capture("conversation-after-first-message")
    }
}
