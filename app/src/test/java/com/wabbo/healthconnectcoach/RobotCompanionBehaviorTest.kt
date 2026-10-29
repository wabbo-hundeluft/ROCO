package com.wabbo.healthconnectcoach

import android.app.Application
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.wabbo.healthconnectcoach.ui.theme.HealthCoachTheme
import java.util.Locale
import java.io.File
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[36],qualifiers="w427dp-h852dp-mdpi",application=Application::class)
class RobotCompanionBehaviorTest {
    @get:Rule val compose=createComposeRule()
    private val resolver get()=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
    private var originalScale=1f
    @Before fun setup() {
        originalScale=Settings.Global.getFloat(resolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)
        Settings.Global.putFloat(resolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)
        compose.mainClock.autoAdvance=false
    }
    @After fun restore() {Settings.Global.putFloat(resolver,Settings.Global.ANIMATOR_DURATION_SCALE,originalScale)}
    private fun description(mood:RobotMood)="Pip, dein kleiner Roboter: ${mood.description}. Antippen zum Begrüßen."
    @Test fun robotGreetsThenKeepsOfferingSpontaneousGesturesWithoutATap() {
        compose.setContent {RobotCompanion("Pip",false,Modifier.size(180.dp),allowSleep=false)}
        compose.mainClock.advanceTimeBy(300);compose.waitForIdle()
        compose.onNodeWithContentDescription(description(RobotMood.WAVE)).assertExists()
        compose.mainClock.advanceTimeBy(2200);compose.waitForIdle()
        compose.onNodeWithContentDescription(description(RobotMood.IDLE)).assertExists()
        compose.mainClock.advanceTimeBy(1500);compose.waitForIdle()
        compose.onNodeWithContentDescription(description(RobotMood.WINK)).assertExists()
        compose.mainClock.advanceTimeBy(4400);compose.waitForIdle()
        compose.onNodeWithContentDescription(description(RobotMood.CURIOUS)).assertExists()
    }
    @Test fun tappingTheSleepingRobotPeeksOutThenReturnsToSleep() {
        compose.setContent {RobotCompanion("Pip",false,Modifier.size(180.dp),mood=RobotMood.SLEEPING,allowSleep=false)}
        compose.mainClock.advanceTimeBy(1500);compose.waitForIdle()
        compose.onNodeWithContentDescription(description(RobotMood.SLEEPING)).performClick()
        compose.mainClock.advanceTimeBy(300);compose.waitForIdle()
        compose.onNodeWithContentDescription(description(RobotMood.WAKING)).assertExists()
        compose.mainClock.advanceTimeBy(3000);compose.waitForIdle()
        compose.onNodeWithContentDescription(description(RobotMood.SLEEPING)).assertExists()
    }
    @Test fun reducedMotionKeepsTheArtworkStill() {
        Settings.Global.putFloat(resolver,Settings.Global.ANIMATOR_DURATION_SCALE,0f)
        compose.setContent {RobotCompanion("Pip",false,Modifier.size(180.dp),allowSleep=false)}
        compose.mainClock.advanceTimeBy(300);compose.waitForIdle()
        val before=compose.onRoot().captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888,false)
        compose.mainClock.advanceTimeBy(8000);compose.waitForIdle()
        assertTrue(before.sameAs(compose.onRoot().captureToImage().asAndroidBitmap()))
    }
    @Test fun namingFormKeepsTheRobotAndContinueButtonVisibleInACompactWindow() {
        compose.mainClock.autoAdvance=true
        Settings.Global.putFloat(resolver,Settings.Global.ANIMATOR_DURATION_SCALE,0f)
        compose.setContent {
            val context=RocoLocales.withLocale(LocalContext.current,Locale.GERMAN)
            CompositionLocalProvider(LocalContext provides context) {
                HealthCoachTheme(darkTheme=false) {Box(Modifier.requiredSize(360.dp,520.dp)) {
                    V2Onboarding("Noah","Pip",null,false,false,null) {_,_,_,_,_->}
                }}
            }
        }
        compose.mainClock.advanceTimeBy(300);compose.waitForIdle()
        compose.onNodeWithText("Weiter").performClick()
        compose.mainClock.advanceTimeBy(300);compose.waitForIdle()
        File("build/outputs/robot-animation").mkdirs()
        File("build/outputs/robot-animation/onboarding-before-input.tree.txt").writeText(compose.onRoot().printToString())
        File("build/outputs/robot-animation/onboarding-before-input.png").outputStream().use {compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
        compose.onNodeWithContentDescription("Name deines Roboters").performScrollTo().performClick().performTextReplacement("Milo")
        compose.mainClock.advanceTimeBy(300);compose.waitForIdle()
        compose.onNodeWithText("Milo").assertIsDisplayed()
        compose.onNodeWithContentDescription("Milo, dein kleiner Roboter: hört aufmerksam zu. Antippen zum Begrüßen.").assertIsDisplayed()
        compose.onNodeWithText("Weiter").assertIsDisplayed()
        val out=File("build/outputs/robot-animation").apply {mkdirs()}
        File(out,"onboarding-name-compact.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
        }
    }
    @Test fun repeatedTouchesProduceDifferentPlayfulReactions() {
        compose.setContent {RobotCompanion("Pip",false,Modifier.size(180.dp),allowSleep=false)}
        compose.mainClock.advanceTimeBy(300);compose.waitForIdle()
        listOf(RobotMood.HIGH_FIVE,RobotMood.SHY,RobotMood.WINK,RobotMood.DANCE,RobotMood.SURPRISED).forEach {expected->
            compose.onNode(hasClickAction()).performClick()
            compose.mainClock.advanceTimeBy(160);compose.waitForIdle()
            compose.onNodeWithContentDescription(description(expected)).assertExists()
        }
    }
    @Test fun existingContentDoesNotTriggerANewAnswerButAnActualChangeDoes() {
        var token by mutableIntStateOf(0)
        compose.setContent {RobotCompanion("Pip",false,Modifier.size(180.dp),mood=RobotMood.LISTENING,
            reactionKey=token,event=RobotEvent.ANSWERED,allowSleep=false)}
        compose.mainClock.advanceTimeBy(300);compose.waitForIdle()
        compose.onNodeWithContentDescription(description(RobotMood.LISTENING)).assertExists()
        compose.runOnIdle {token++}
        compose.mainClock.advanceTimeBy(100);compose.waitForIdle()
        compose.onNodeWithContentDescription(description(RobotMood.NOD)).assertExists()
        compose.mainClock.advanceTimeBy(1200);compose.waitForIdle()
        compose.onNodeWithContentDescription(description(RobotMood.LISTENING)).assertExists()
    }
}
