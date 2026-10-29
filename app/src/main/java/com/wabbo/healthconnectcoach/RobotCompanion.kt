package com.wabbo.healthconnectcoach

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

/** All geometry is rendered by Compose. Animation clocks only run for visible, resumed robots. */
@Composable
internal fun RobotCompanion(name: String, excited: Boolean, modifier: Modifier = Modifier,
    attentive: Boolean = false, reactionKey: Int = 0, mood: RobotMood? = null,
    allowSleep: Boolean = true, event: RobotEvent = RobotEvent.NONE, onTap: () -> Unit = {}) {
    var greeting by remember { mutableStateOf(false) }
    var tapSerial by remember {mutableIntStateOf(0)}
    var blink by remember { mutableStateOf(false) }
    var visible by remember {mutableStateOf(false)}
    val motion=productMotionEnabled()
    val running=motion&&visible
    val haptics = LocalHapticFeedback.current
    val moment=LocalRobotMoment.current?.takeIf {allowSleep||it==RobotMood.WAKING}
    val acknowledgeWake=LocalRobotWakeAck.current
    LaunchedEffect(visible,moment,attentive,excited) {
        if(visible&&moment==RobotMood.WAKING&&!attentive&&!excited) acknowledgeWake()
    }
    val base=if(moment!=null&&!attentive&&!excited) moment else mood ?: when {
        excited->RobotMood.CELEBRATE;attentive->RobotMood.LISTENING;else->RobotMood.IDLE
    }
    var spontaneous by remember {mutableStateOf<RobotMood?>(null)}
    var reaction by remember {mutableStateOf<RobotMood?>(null)}
    var seenKey by remember {mutableIntStateOf(reactionKey)}
    var seenEvent by remember {mutableStateOf(event)}
    val effective=when {greeting->robotTapMood(base,tapSerial)
        reaction!=null->reaction!!;else->spontaneous ?: base}
    val interactions=remember {MutableInteractionSource()}
    val pressed by interactions.collectIsPressedAsState()
    val squish by animateFloatAsState(if(pressed&&motion) 1f else 0f,spring(dampingRatio=.48f,stiffness=950f),label="robot-boop")
    val frame=remember {Animatable(0f)}
    val blend=remember {Animatable(1f)}
    val fold=remember {Animatable(if(base==RobotMood.WAKING) 1f else 0f)}
    var hasShownRobot by remember {mutableStateOf(false)}
    var previousMood by remember {mutableStateOf(effective)}
    var previousPose by remember {mutableStateOf(robotPose(effective,0f))}
    LaunchedEffect(tapSerial) {if(greeting) {delay(if(base==RobotMood.SLEEPING) 2800 else robotGestureMillis(robotTapMood(base,tapSerial)).toLong()+350);greeting=false}}
    LaunchedEffect(running,event,reactionKey) {
        reaction=null
        val changed=seenKey!=reactionKey||seenEvent!=event
        seenKey=reactionKey;seenEvent=event
        if(running&&changed&&event!=RobotEvent.NONE) {
            reaction=robotEventMood(event,base)
            delay(robotGestureMillis(reaction!!).toLong()+180)
            reaction=null
        }
    }
    LaunchedEffect(running,base,greeting,name) {
        spontaneous=null
        if(running&&!greeting&&base !in setOf(RobotMood.SLEEPING,RobotMood.WAKING,RobotMood.THINKING)) {
            var beat=0
            // Welcome on screen entry, then alternate a short gesture with a calm interval.
            if(base==RobotMood.IDLE||base==RobotMood.CURIOUS) {
                spontaneous=RobotMood.WAVE;delay(1350);spontaneous=null
            }
            while(true) {
                delay(robotAmbientDelay(beat))
                spontaneous=robotAmbientMood(base,beat++)
                delay(robotGestureMillis(spontaneous!!).toLong()+250)
                spontaneous=null
            }
        }
    }
    LaunchedEffect(running,effective,reactionKey,tapSerial) {
        previousPose=previousPose.blend(robotPose(previousMood,frame.value,blink),blend.value)
        previousMood=effective
        frame.snapTo(0f);blend.snapTo(if(running) 0f else 1f)
        if(running) {
            launch {blend.animateTo(1f,tween(190,easing=FastOutSlowInEasing))}
            while(true) {
                frame.snapTo(0f)
                frame.animateTo(1f,tween(robotGestureMillis(effective),easing=LinearEasing))
                if(effective==RobotMood.WAKING) break
                if(effective!=RobotMood.SLEEPING&&effective!=RobotMood.THINKING) delay(if(effective in setOf(RobotMood.RECOVER,RobotMood.CAREFUL)) 1300 else 500)
            }
        } else frame.snapTo(if(motion) 0f else 1f)
    }
    LaunchedEffect(running,motion,effective) {
        if(motion&&!visible) return@LaunchedEffect
        val target=if(effective==RobotMood.SLEEPING) 1f else 0f
        if(running) {
            if(!hasShownRobot) {
                fold.snapTo(if(effective==RobotMood.WAKING) 1f else 0f)
                hasShownRobot=true
            }
            fold.animateTo(target,tween(if(target==1f) 1250 else if(effective==RobotMood.WAKING) 1495 else 900,easing=FastOutSlowInEasing))
        } else fold.snapTo(target)
    }
    LaunchedEffect(running) {blink=false;var count=0;while(running) {
        delay(2600L+(count++%3)*850);blink=true;delay(110);blink=false
        if(count%3==0) {delay(180);blink=true;delay(95);blink=false}
    }}
    Canvas(modifier.onGloballyPositioned {coordinates->
        visible=coordinates.isAttached&&!coordinates.boundsInWindow().isEmpty
    }.clickable(interactionSource=interactions,indication=null,onClickLabel=RocoStrings.translate("$name begrüßen")) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        greeting=true;tapSerial++;onTap()
    }.semantics { contentDescription=RocoStrings.translate("$name, dein kleiner Roboter: ${RocoStrings.translate(effective.description)}. Antippen zum Begrüßen.") }) {
        val scale=min(size.width,size.height)/240f
        drawContext.canvas.save()
        drawContext.canvas.translate((size.width-240f*scale)/2,(size.height-240f*scale)/2)
        drawContext.canvas.scale(scale,scale)
        val pose=previousPose.blend(robotPose(effective,frame.value,blink),blend.value)
        drawRobotArtwork(effective,frame.value,blink,poseOverride=pose.copy(squash=pose.squash*(1-.055f*squish),bob=pose.bob+3*squish),sleepFold=fold.value)
        drawContext.canvas.restore()
    }
}

/** Raised edge, spring travel and system-controlled haptic feedback on press. */
@Composable
internal fun ClayButton(onClick:()->Unit, modifier:Modifier=Modifier, enabled:Boolean=true, secondary:Boolean=false, success:Boolean=false, content:@Composable RowScope.()->Unit) {
    val face=if(success) Color(0xFFE1F0D8) else if(secondary) Color(0xFFEDF1E6) else Color(0xFF27392A)
    val edge=if(success) Color(0xFFB3CBA7) else if(secondary) Color(0xFFCBD7BD) else Color(0xFF0D1B10)
    V2ClaySurface(onClick,modifier,enabled,color=if(enabled||success) face else Color(0xFFE7ECDF),
        contentColor=if(success) Color(0xFF234C24) else if(!enabled) Color(0xFFA2AB98) else if(secondary) Color(0xFF60754E) else Color.White,
        edgeColor=edge,shape=RoundedCornerShape(18.dp),edgeHeight=if(secondary) 4.dp else 5.dp,border=null) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight(750))) {
            Row(Modifier.fillMaxWidth().heightIn(min=56.dp).padding(horizontal=18.dp,vertical=14.dp),horizontalArrangement=Arrangement.spacedBy(10.dp,Alignment.CenterHorizontally),verticalAlignment=Alignment.CenterVertically,content=content)
        }
    }
}
