package com.wabbo.healthconnectcoach

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlin.math.*

private val robotStops=listOf(.16f,.34f,.54f,.74f)
private const val DRIVE_SECONDS=3.9f
private const val PICK_SECONDS=5.7f
internal const val ROBOT_ROAD_SECONDS=.75f+4*(DRIVE_SECONDS+PICK_SECONDS)+2f
internal enum class RobotPickupStage { DRIVE, LOOK, REACH, GRIP, LIFT, ADMIRE, STOW, RETURN, WAIT, DONE }
internal data class RobotRoadFrame(val position: Float,val item: Int,val stored: Int,val stage: RobotPickupStage,val phase: Float)
internal fun collectedRobotItems(progress: Float)=robotStops.count {progress>=it+.015f}

/** Each object has its own approach, glance, telescopic reach, grip, inspection and cargo-door cycle. */
internal fun robotRoadFrame(seconds: Float): RobotRoadFrame {
    var t=seconds.coerceAtLeast(0f)
    if(t<.75f) return RobotRoadFrame(0f,-1,0,RobotPickupStage.LOOK,t/.75f)
    t-=.75f
    var start=0f
    for((index,stop) in robotStops.withIndex()) {
        if(t<DRIVE_SECONDS) return RobotRoadFrame(start+(stop-start)*robotSmooth(t/DRIVE_SECONDS),-1,index,RobotPickupStage.DRIVE,t/DRIVE_SECONDS)
        t-=DRIVE_SECONDS
        if(t<PICK_SECONDS) {
            val durations=listOf(.6f,1.05f,.4f,.9f,1.1f,1.1f,.55f)
            val stages=listOf(RobotPickupStage.LOOK,RobotPickupStage.REACH,RobotPickupStage.GRIP,RobotPickupStage.LIFT,RobotPickupStage.ADMIRE,RobotPickupStage.STOW,RobotPickupStage.RETURN)
            var phase=t
            for(i in durations.indices) {
                if(phase<durations[i]||i==durations.lastIndex) {
                    val p=(phase/durations[i]).coerceIn(0f,1f)
                    val stored=index+if(stages[i]==RobotPickupStage.RETURN||stages[i]==RobotPickupStage.STOW&&p>=.78f) 1 else 0
                    return RobotRoadFrame(stop,index,stored,stages[i],p)
                }
                phase-=durations[i]
            }
        }
        t-=PICK_SECONDS;start=stop
    }
    if(t<2f) return RobotRoadFrame(start+(.84f-start)*robotSmooth(t/2f),-1,4,RobotPickupStage.DRIVE,t/2f)
    return RobotRoadFrame(.84f,-1,4,RobotPickupStage.WAIT,((t-2f)/4f)%1f)
}
internal fun robotPickupEnd(item: Int)=.75f+(item+1)*(DRIVE_SECONDS+PICK_SECONDS)
internal fun robotHoldsItem(frame: RobotRoadFrame)=frame.item>=0&&when(frame.stage) {
    RobotPickupStage.GRIP->frame.phase>=.45f
    RobotPickupStage.LIFT,RobotPickupStage.ADMIRE->true
    RobotPickupStage.STOW->frame.phase<.78f
    else->false
}

/** This little journey never claims a completion percentage. Only the real import ends the trip. */
@Composable internal fun RobotLoadingRoad(name: String,step: String,loading: Boolean=true) {
    var visible by remember {mutableStateOf(false)}
    val motion=productMotionEnabled()&&visible;val clock=remember {Animatable(0f)};val arrival=remember {Animatable(0f)}
    LaunchedEffect(loading,motion) {
        if(!motion) {if(!loading) arrival.snapTo(1f);return@LaunchedEffect}
        if(loading) {
            arrival.snapTo(0f)
            if(clock.value<ROBOT_ROAD_SECONDS) clock.animateTo(ROBOT_ROAD_SECONDS,tween(((ROBOT_ROAD_SECONDS-clock.value)*1000).toInt().coerceAtLeast(1),easing=LinearEasing))
            while(true) {clock.snapTo(ROBOT_ROAD_SECONDS);clock.animateTo(ROBOT_ROAD_SECONDS+4f,tween(4000,easing=LinearEasing))}
        } else {
            val current=robotRoadFrame(clock.value)
            // Finish an in-progress pickup before leaving, without holding up the actual data.
            if(current.item>=0&&current.stage!=RobotPickupStage.RETURN) clock.animateTo(robotPickupEnd(current.item),tween(380,easing=LinearEasing))
            arrival.animateTo(1f,tween(750,easing=FastOutSlowInEasing))
        }
    }
    Column(Modifier.fillMaxWidth().semantics {contentDescription=RocoStrings.translate(if(loading) "$name sammelt in Seitenansicht: $step. Die Fahrt zeigt keine Prozentanzeige." else "$name ist angekommen. Aktualisierung beendet; der gespeicherte Stand bleibt verfügbar.")},verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(144.dp).onGloballyPositioned {visible=it.isAttached&&!it.boundsInWindow().isEmpty}) {drawRobotRoad(clock.value,arrival.value)}
        RocoText(if(loading) step else "Alles verstaut. Dein gespeicherter Stand ist da.",style=androidx.compose.material3.MaterialTheme.typography.bodySmall,color=V2Muted)
    }
}

/** Kept separate from the clock so the actual programmed frames can be rendered and inspected. */
internal fun DrawScope.drawRobotRoad(seconds: Float,arrival: Float=0f) {
    val frame=robotRoadFrame(seconds)
    val ground=size.height*.87f;val w=92.dp.toPx();val h=110.dp.toPx()
    val travel=(size.width-w-12.dp.toPx()).coerceAtLeast(0f)
    val position=frame.position+(1-frame.position)*robotSmooth(arrival)
    drawLine(Color(0xFFD7DFCD),Offset(0f,ground),Offset(size.width,ground),3.dp.toPx(),StrokeCap.Round)
    repeat(9) {i->val x=size.width*i/8;drawLine(Color(0xFFE7EADF),Offset(x,ground+7.dp.toPx()),Offset(x+9.dp.toPx(),ground+7.dp.toPx()),2.dp.toPx(),StrokeCap.Round)}
    robotStops.forEachIndexed {i,place->
        if(i>=frame.stored&&!(i==frame.item&&robotHoldsItem(frame)))
            drawCollectible(i,Offset(travel*place+w*1.05f,ground-h/120f*10f),w*.06f,(1-arrival).coerceIn(0f,1f))
    }
    drawSideRobot(Offset(travel*position,ground-h),Size(w,h),if(arrival>.85f) frame.copy(stored=4) else frame,seconds,arrival)
}
private fun DrawScope.drawCollectible(index: Int,at: Offset,r: Float,alpha: Float) {
    val color=listOf(Color(0xFF89AF71),Color(0xFFA8AFE0),Color(0xFFDF9B84),Color(0xFFE7BA61))[index%4].copy(alpha=alpha.coerceIn(0f,1f))
    when(index%4) {
        0->{drawOval(color,Offset(at.x-r,at.y-r),Size(r*1.8f,r));drawLine(color,at+Offset(-r/2,r/2),at+Offset(r/2,-r/2),r/4,StrokeCap.Round)}
        1->{drawCircle(color,r,at);drawCircle(Color(0xFFF8F8F3).copy(alpha=alpha),r*.8f,at+Offset(r*.5f,-r*.3f))}
        2->{val path=Path().apply {moveTo(at.x,at.y+r);cubicTo(at.x-r*2,at.y,at.x-r,at.y-r*1.6f,at.x,at.y-r*.5f);cubicTo(at.x+r,at.y-r*1.6f,at.x+r*2,at.y,at.x,at.y+r)};drawPath(path,color)}
        else->{val path=Path().apply {moveTo(at.x,at.y-r);lineTo(at.x-r*.7f,at.y+r*.2f);lineTo(at.x,at.y+r*.2f);lineTo(at.x-r*.2f,at.y+r);lineTo(at.x+r*.8f,at.y-r*.2f);lineTo(at.x+r*.1f,at.y-r*.2f);close()};drawPath(path,color)}
    }
}
private fun DrawScope.drawSideRobot(origin: Offset,extent: Size,frame: RobotRoadFrame,seconds: Float,arrival: Float) {
    val p=robotSmooth(frame.phase);val stage=frame.stage
    fun mix(a:Offset,b:Offset,t:Float)=a+(b-a)*t
    val rest=Offset(80f,77f);val floor=Offset(101f,106f);val raised=Offset(107f,48f);val admired=Offset(94f,39f);val cargo=Offset(54f,77f)
    val wrist=when(stage) {
        RobotPickupStage.REACH->mix(rest,floor,p)
        RobotPickupStage.GRIP->floor
        RobotPickupStage.LIFT->mix(floor,raised,p)+Offset(sin(p*PI).toFloat()*8,0f)
        RobotPickupStage.ADMIRE->mix(raised,admired,robotSmooth((p/.4f).coerceAtMost(1f)))+Offset(0f,-sin(p*PI).toFloat()*2)
        RobotPickupStage.STOW->mix(admired,cargo,p)
        RobotPickupStage.RETURN->mix(cargo,rest,p)
        else->rest
    }
    val viewer=when(stage) {
        RobotPickupStage.LOOK->sin(frame.phase*PI).toFloat()
        RobotPickupStage.ADMIRE->sin(((p-.3f)/.7f).coerceIn(0f,1f)*PI).toFloat()*.75f
        RobotPickupStage.WAIT->(.4f+.6f*sin(frame.phase*PI).toFloat())
        else->0f
    }.coerceIn(0f,1f).let {max(it,arrival)}
    val moving=stage==RobotPickupStage.DRIVE||arrival>0
    val bob=if(moving) sin(seconds*12)*.6f else 0f
    drawContext.canvas.save();drawContext.canvas.translate(origin.x,origin.y+bob)
    drawContext.canvas.scale(extent.width/100,extent.height/120)
    fun rect(c:Color,x:Float,y:Float,w:Float,h:Float,r:Float=5f)=drawRoundRect(c,Offset(x,y),Size(w,h),CornerRadius(r))
    val edge=Color(0xFFB97D32);val gold=Color(0xFFF1BC65);val dark=Color(0xFF3D4F53);val eye=Color(0xFFBEE7D7)
    rect(dark,13f,94f,69f,23f,10f);rect(Color(0xFF293D42),18f,99f,59f,13f,5f)
    repeat(7) {i->val x=17+(i*9+frame.position*750)%61;drawLine(Color(0xFF81928B),Offset(x,98f),Offset(x,112f),2f,StrokeCap.Round)}
    rect(edge,24f,51f,48f,48f,10f);rect(gold,23f,49f,47f,44f,10f);rect(Color(0xFFFFD88B),28f,54f,33f,8f)
    val door=when(stage) {RobotPickupStage.LIFT->p;RobotPickupStage.ADMIRE,RobotPickupStage.STOW->1f;RobotPickupStage.RETURN->1-p;else->0f}
    rect(dark,43f,69f,25f,21f,3f)
    repeat(frame.stored) {i->drawCircle(listOf(Color(0xFFA7C48B),Color(0xFFB9C2EE),Color(0xFFE8B5A2),Color(0xFFF5D485))[i],2.4f,Offset(48f+i*4.3f,79f))}
    rect(edge,43f,68f-11*door,25f,21f*(1-door)+4,3f);rect(gold,44f,68f-11*door,23f,4f,2f)
    // Two joints plus a visibly sliding metal forearm; fingers close around the real object.
    val shoulder=Offset(66f,63f);val elbow=mix(shoulder,wrist,.48f)+Offset(-6f,10f)
    drawLine(edge,shoulder,elbow,10f,StrokeCap.Round);drawLine(gold,shoulder,elbow,6f,StrokeCap.Round)
    drawLine(dark,elbow,wrist,7f,StrokeCap.Round);drawLine(Color(0xFFD3D8CD),elbow,wrist,4f,StrokeCap.Round)
    drawCircle(edge,6f,shoulder);drawCircle(Color(0xFFFFDC94),3f,shoulder);drawCircle(gold,4.5f,elbow)
    val closed=when(stage) {RobotPickupStage.GRIP->p;RobotPickupStage.LIFT,RobotPickupStage.ADMIRE->1f;RobotPickupStage.STOW->1-robotSmooth((p-.55f)/.25f);else->0f}
    val spread=7f-4.5f*closed
    drawCircle(edge,4f,wrist)
    drawLine(gold,wrist+Offset(0f,-2f),wrist+Offset(6f,-spread),4f,StrokeCap.Round)
    drawLine(gold,wrist+Offset(0f,3f),wrist+Offset(6f,spread+3),4f,StrokeCap.Round)
    drawLine(Color(0xFFFFDA92),wrist+Offset(6f,-spread),wrist+Offset(10f,-spread+3),3f,StrokeCap.Round)
    drawLine(Color(0xFFFFDA92),wrist+Offset(6f,spread+3),wrist+Offset(10f,spread),3f,StrokeCap.Round)
    if(robotHoldsItem(frame)) {
        val shrink=if(stage==RobotPickupStage.STOW) 1-robotSmooth((p-.55f)/.23f) else 1f
        drawCollectible(frame.item,wrist+Offset(4f,4f),6f*shrink,shrink)
    }
    rect(dark,40f,35f,9f,19f,3f)
    val headTilt=when(stage) {RobotPickupStage.REACH,RobotPickupStage.GRIP->11f;RobotPickupStage.LIFT->11-22*p;RobotPickupStage.ADMIRE->-11f-6*sin(p*PI).toFloat();else->-2f-5*viewer}
    drawContext.canvas.save();drawContext.canvas.translate(46f,34f);drawContext.canvas.rotate(headTilt);drawContext.canvas.translate(-46f,-34f)
    rect(edge,29f-8*viewer,15f,46f+17*viewer,28f,10f);rect(Color(0xFFFFDA92),27f-8*viewer,12f,46f+17*viewer,28f,10f)
    val eyeX=58f-22*viewer
    rect(dark,eyeX,17f,24f,18f,6f);rect(Color(0xFF173839),eyeX+6f,18f,18f,16f,5f)
    val blink=(seconds%3.6f) in 3.38f..3.5f
    val gaze=when(stage) {RobotPickupStage.REACH,RobotPickupStage.GRIP->2f;RobotPickupStage.LIFT->2-4*p;RobotPickupStage.ADMIRE->-2f;else->0f}
    rect(eye,eyeX+11f,if(blink) 25f else 21f+gaze,9f,if(blink) 2f else 9f,3f)
    if(!blink) drawCircle(Color.White,1.4f,Offset(eyeX+15f,22f+gaze))
    drawCircle(edge,2f,Offset(33f-7*viewer,29f))
    if(viewer>.05f) {
        val alpha=viewer.coerceIn(0f,1f)
        rect(dark.copy(alpha=alpha),62f,17f,24f,18f,6f);rect(Color(0xFF173839).copy(alpha=alpha),65f,20f,18f,12f,4f)
        rect(eye.copy(alpha=alpha),69f,if(blink) 25f else 22f,10f,if(blink) 2f else 7f,3f)
    }
    drawLine(dark,Offset(39f,12f),Offset(43f,3f),2f,StrokeCap.Round);drawCircle(gold,3f,Offset(44f,3f))
    drawContext.canvas.restore()
    if(stage==RobotPickupStage.ADMIRE) {
        val shine=sin(p*PI).toFloat().coerceAtLeast(0f)
        repeat(3) {i->val at=wrist+Offset(11f+i*7,-11f-(i%2)*9)
            drawLine(Color(0xFFE7BA61).copy(alpha=shine),at-Offset(3f,0f),at+Offset(3f,0f),1.6f,StrokeCap.Round)
            drawLine(Color(0xFFE7BA61).copy(alpha=shine),at-Offset(0f,3f),at+Offset(0f,3f),1.6f,StrokeCap.Round)}
    }
    drawContext.canvas.restore()
}
