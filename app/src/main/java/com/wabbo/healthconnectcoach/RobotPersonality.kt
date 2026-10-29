package com.wabbo.healthconnectcoach

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine as E
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.*

internal enum class RobotMood(val description: String) {
    IDLE("neugierig"), CHEERFUL("fröhlich"), CELEBRATE("freut sich mit dir"), RECOVER("ruht sich aus"),
    SLEEPING("schläft"), WAKING("wacht auf und streckt sich"), CURIOUS("schaut genau hin"), THINKING("denkt nach"),
    PROUD("ist stolz auf deinen Fortschritt"), CAREFUL("erinnert freundlich an Erholung"), WAVE("winkt dir zu"), LISTENING("hört aufmerksam zu"),
    NOD("nickt dir zu"), WINK("zwinkert dir zu"), SHY("freut sich verschmitzt"), SURPRISED("staunt"),
    STRETCH("streckt seine Arme"), DANCE("tanzt vor Freude"), HIGH_FIVE("gibt dir ein High Five"), COMFORT("ist für dich da")
}
internal val LocalRobotMoment=staticCompositionLocalOf<RobotMood?> {null}
internal val LocalRobotWakeAck=staticCompositionLocalOf<()->Unit> { {} }
internal fun robotMoment(hour: Int,lastWake: String?,date: LocalDate): RobotMood?=when {
    hour>=22||hour<6->RobotMood.SLEEPING
    hour<12&&lastWake!=date.toString()->RobotMood.WAKING
    else->null
}
internal fun robotScoreMood(id: String,value: Int?): RobotMood=when {
    value==null->RobotMood.CURIOUS
    id==E.SESSION_LOAD_ID->when {value>100->RobotMood.CAREFUL;value>=80->RobotMood.PROUD;else->RobotMood.CURIOUS}
    id==E.BODY_BATTERY_ID->when {value<=30->RobotMood.RECOVER;value<=60->RobotMood.CAREFUL;value>=80->RobotMood.CHEERFUL;else->RobotMood.IDLE}
    else->when {value<55->RobotMood.RECOVER;value<75->RobotMood.CAREFUL;value>=85->RobotMood.CHEERFUL;else->RobotMood.IDLE}
}
internal data class RobotPose(
    val tilt: Float,val leftArm: Float,val rightArm: Float,val eyes: Float,val look: Float,val bob: Float,
    val lift: Float=0f,val shift: Float=0f,val lean: Float=0f,val squash: Float=1f,val fold: Float=0f,
    val leftHandX: Float=29f,val rightHandX: Float=211f,val headY: Float=0f,val rightEye: Float=1f,
) {
    fun blend(to: RobotPose,fraction: Float): RobotPose {
        val t=fraction.coerceIn(0f,1f)
        fun mix(a:Float,b:Float)=a+(b-a)*t
        return RobotPose(mix(tilt,to.tilt),mix(leftArm,to.leftArm),mix(rightArm,to.rightArm),
            mix(eyes,to.eyes),mix(look,to.look),mix(bob,to.bob),mix(lift,to.lift),mix(shift,to.shift),
            mix(lean,to.lean),mix(squash,to.squash),mix(fold,to.fold),mix(leftHandX,to.leftHandX),
            mix(rightHandX,to.rightHandX),mix(headY,to.headY),mix(rightEye,to.rightEye))
    }
}
internal fun robotSmooth(value: Float): Float=value.coerceIn(0f,1f).let {it*it*(3-2*it)}
internal fun robotGestureMillis(mood: RobotMood)=when(mood) {
    RobotMood.SLEEPING->5600;RobotMood.WAKING->2300;RobotMood.WAVE->1150
    RobotMood.CELEBRATE->1450;RobotMood.CHEERFUL->1500;RobotMood.THINKING->1900
    RobotMood.NOD->850;RobotMood.WINK->1000;RobotMood.SURPRISED->950;RobotMood.HIGH_FIVE->1150
    RobotMood.SHY->1400;RobotMood.DANCE->1800;RobotMood.STRETCH->1800;RobotMood.COMFORT->1900
    RobotMood.RECOVER->3200;RobotMood.CAREFUL->2400;else->1600
}
internal enum class RobotEvent { NONE, INPUT, ANSWERED, CONTENT_CHANGED, SUCCESS, ERROR }
internal fun robotEventMood(event:RobotEvent,base:RobotMood):RobotMood=when {
    base==RobotMood.SLEEPING->RobotMood.WAKING
    event==RobotEvent.ERROR->RobotMood.COMFORT
    event==RobotEvent.INPUT||event==RobotEvent.ANSWERED->RobotMood.NOD
    event==RobotEvent.SUCCESS->RobotMood.HIGH_FIVE
    base==RobotMood.RECOVER||base==RobotMood.CAREFUL->RobotMood.COMFORT
    else->RobotMood.CURIOUS
}
internal fun robotTapMood(base:RobotMood,tap:Int):RobotMood=when(base) {
    RobotMood.SLEEPING->RobotMood.WAKING
    RobotMood.RECOVER,RobotMood.CAREFUL,RobotMood.COMFORT->when((tap-1).mod(4)) {0->RobotMood.COMFORT;1->RobotMood.WINK;2->RobotMood.SHY;else->RobotMood.HIGH_FIVE}
    else->when((tap-1).mod(6)) {0->RobotMood.HIGH_FIVE;1->RobotMood.SHY;2->RobotMood.WINK;3->RobotMood.DANCE;4->RobotMood.SURPRISED;else->RobotMood.WAVE}
}
/** Ambient curiosity, affectionate play and celebration each use their own varied vocabulary. */
internal fun robotAmbientMood(base: RobotMood,beat: Int): RobotMood=when(base) {
    RobotMood.SLEEPING,RobotMood.WAKING,RobotMood.THINKING->base
    RobotMood.RECOVER,RobotMood.CAREFUL->when(beat.mod(5)) {0->RobotMood.COMFORT;1->RobotMood.NOD;2->RobotMood.WINK;3->RobotMood.SHY;else->RobotMood.CURIOUS}
    RobotMood.LISTENING->if(beat%2==0) RobotMood.NOD else RobotMood.CURIOUS
    RobotMood.CELEBRATE,RobotMood.CHEERFUL,RobotMood.PROUD->when(beat.mod(5)) {
        0->RobotMood.HIGH_FIVE;1->RobotMood.DANCE;2->RobotMood.WINK;3->RobotMood.SHY;else->RobotMood.PROUD}
    else->when(beat.mod(7)) {0->RobotMood.WINK;1->RobotMood.CURIOUS;2->RobotMood.STRETCH
        3->RobotMood.SHY;4->RobotMood.SURPRISED;5->RobotMood.WAVE;else->RobotMood.NOD}
}
internal fun robotAmbientDelay(beat:Int)=2300L+beat.mod(4)*400L
/** Finite, closed gestures can replay without a jump at the loop boundary. */
internal fun robotPose(mood: RobotMood,p: Float,blink: Boolean=false): RobotPose {
    val t=p.coerceIn(0f,1f);val pulse=sin(t*PI*6).toFloat();val settle=sin(t*PI).toFloat()
    val lively=sin(t*PI*4).toFloat()*settle;val wink=if(blink) 3f else 25f
    return when(mood) {
        RobotMood.SLEEPING->RobotPose(0f,148f,148f,3f,0f,sin(t*PI*2).toFloat()*.8f,fold=1f)
        RobotMood.WAKING->{val awake=robotSmooth(t/.65f);val stretch=sin(((t-.3f)/.7f).coerceIn(0f,1f)*PI).toFloat()
            RobotPose((1-awake)*12-9*stretch,157-68*stretch,157-74*stretch,3+22*awake,
                3*stretch,-7*stretch,lean=3*stretch,squash=1+.04f*stretch,fold=1-awake)}
        RobotMood.RECOVER->RobotPose(10f+2*settle,155f,145f,if(blink) 3f else 12f,-4f,1f+settle,lean=settle)
        RobotMood.CAREFUL->RobotPose(6f+4*lively,145f,128f-15*settle,wink,-3f-3*settle,-settle,lean=-2*settle)
        RobotMood.CHEERFUL,RobotMood.CELEBRATE->{val hop=abs(lively)
            RobotPose(-4+13*lively,132-47*settle,130-47*settle,if(blink) 3f else 14f,
                3*lively,-hop*15,lift=hop*9,shift=6*lively,lean=7*lively,squash=1-.07f*sin(t*PI*8).toFloat()*settle)}
        RobotMood.PROUD->RobotPose(-5-8*settle,147f,114+18*lively,18f,3*settle,-settle*5,lean=-4*settle,squash=1+.025f*settle)
        RobotMood.THINKING->RobotPose(-7+7*lively,148f,118-9*settle,wink,8*lively,-settle*2,shift=2*lively)
        // Keep the launcher's resting portrait identical to the live model's original curious pose.
        RobotMood.CURIOUS->RobotPose(-9+23*settle,152f,138-18*settle,wink,9*lively,-settle*5,shift=5*settle,lean=-3*settle)
        RobotMood.LISTENING->RobotPose(-4+8*lively,149f,144f,wink,4*settle,-abs(lively)*3,lean=2*lively)
        RobotMood.WAVE->RobotPose(-5+10*lively,151f,105+27*pulse*settle,wink,2*settle,-settle*5,shift=-3*settle,lean=-5*settle)
        RobotMood.IDLE->RobotPose(3-14*settle,157f,147-35*settle,wink,7*lively,-settle*4,shift=4*lively,lean=3*lively)
        RobotMood.NOD->RobotPose(-3+7*abs(lively),151f,145f,wink,2*settle,0f,headY=7*abs(lively))
        RobotMood.WINK->{val close=robotSmooth((t-.12f)/.18f)*(1-robotSmooth((t-.65f)/.2f))
            RobotPose(-4-9*settle,152f,143-20*settle,wink,3*settle,-settle*3,rightEye=1-.9f*close)}
        RobotMood.SHY->RobotPose(6+11*settle,155-48*settle,150-43*settle,if(blink) 3f else 22f,
            -7*settle,settle*3,shift=-3*settle,lean=3*settle,leftHandX=29+43*settle,rightHandX=211-43*settle,headY=3*settle)
        RobotMood.SURPRISED->RobotPose(-5-7*settle,152-27*settle,147-27*settle,if(blink) 3f else 25+4*settle,
            0f,-5*settle,lean=-5*settle,squash=1+.04f*settle,leftHandX=29-8*settle,rightHandX=211+8*settle,headY=-5*settle)
        RobotMood.STRETCH->RobotPose(-3+6*lively,157-84*settle,151-84*settle,if(blink) 3f else 25-12*settle,
            0f,-4*settle,lean=2*lively,squash=1+.035f*settle,leftHandX=29+10*settle,rightHandX=211-10*settle,headY=-4*settle)
        RobotMood.DANCE->RobotPose(-4-13*lively,143-43*settle-15*lively,143-43*settle+15*lively,
            if(blink) 3f else 14f,5*lively,-abs(lively)*7,shift=10*lively,lean=7*lively,squash=1-.05f*lively)
        RobotMood.HIGH_FIVE->RobotPose(-4-8*settle,152f,147-67*settle,wink,4*settle,-settle*4,
            shift=-2*settle,lean=-3*settle,rightHandX=211+7*settle)
        RobotMood.COMFORT->RobotPose(6+5*settle,155-9*settle,149-3*settle,if(blink) 3f else 19f,
            -2*settle,0f,leftHandX=29+73*settle,rightHandX=211-73*settle,headY=2*settle)
    }
}

/** Wake is consumed only when a visible robot actually starts the morning sequence. */
@Composable internal fun RobotCircadianHost(clock:()->LocalDateTime={LocalDateTime.now()},content: @Composable ()->Unit) {
    val context=LocalContext.current;val owner=LocalLifecycleOwner.current
    val now by rememberUpdatedState(clock)
    val prefs=remember {context.getSharedPreferences("robot_companion",Context.MODE_PRIVATE)}
    var active by remember {mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
    var moment by remember {mutableStateOf<RobotMood?>(null)}
    var wakeStarted by remember {mutableStateOf<LocalDate?>(null)}
    DisposableEffect(owner) {val listener=LifecycleEventObserver {_,_->active=owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)};owner.lifecycle.addObserver(listener);onDispose {owner.lifecycle.removeObserver(listener)}}
    LaunchedEffect(active) {
        if(!active) {moment=null;return@LaunchedEffect}
        while(true) {
            val at=now()
            moment=robotMoment(at.hour,prefs.getString("last_robot_wake",null),at.toLocalDate())
            delay(60_000)
        }
    }
    LaunchedEffect(wakeStarted,active) {
        if(active&&wakeStarted!=null) {
            delay(4200)
            if(moment==RobotMood.WAKING) moment=null
        }
    }
    val acknowledge:()->Unit={
        val at=now();val date=at.toLocalDate()
        if(active&&moment==RobotMood.WAKING&&prefs.getString("last_robot_wake",null)!=date.toString()) {
            prefs.edit().putString("last_robot_wake",date.toString()).apply()
            wakeStarted=date
        }
    }
    CompositionLocalProvider(LocalRobotMoment provides moment,LocalRobotWakeAck provides acknowledge,content=content)
}
