package com.wabbo.healthconnectcoach

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlin.math.*

/** Shared, articulated artwork. Sleep retracts the optics and hands BEHIND the chest. */
internal fun DrawScope.drawRobotArtwork(mood: RobotMood,progress: Float,blink: Boolean=false,
    portrait: Boolean=false,poseOverride: RobotPose?=null,sleepFold: Float?=null) {
    val pose=poseOverride ?: robotPose(mood,progress,blink)
    val tuck=if(portrait) 0f else (sleepFold ?: pose.fold).coerceIn(0f,1f)
    val delighted=mood in setOf(RobotMood.CHEERFUL,RobotMood.CELEBRATE,RobotMood.PROUD)
    val chassis=Color(0xFFF1BC65);val edge=Color(0xFFB97D32);val dark=Color(0xFF3D4F53)
    fun rect(color:Color,x:Float,y:Float,w:Float,h:Float,r:Float)=drawRoundRect(color,Offset(x,y),Size(w,h),androidx.compose.ui.geometry.CornerRadius(r))
    fun head() {
        rect(dark,112f,82f+40*tuck,16f,34f*(1-tuck),7f)
        drawContext.canvas.save()
        drawContext.canvas.translate(120f,70f+85*tuck+pose.headY)
        drawContext.canvas.scale(1-.53f*tuck,1-.53f*tuck)
        drawContext.canvas.rotate(pose.tilt*(1-tuck));drawContext.canvas.translate(-120f,-70f)
        drawLine(dark,Offset(120f,35f),Offset(139f,15f),5f,StrokeCap.Round);rect(Color(0xFFE6AC53),131f,6f,18f,15f,7f)
        rect(Color(0xFFBB873D),19f,39f,202f,70f,24f);rect(Color(0xFFFFDA92),19f,33f,202f,68f,24f)
        listOf(30f,131f).forEach {x->
            rect(Color(0xFF354A4C),x,44f,79f,45f,16f);rect(Color(0xFF142E32),x+7,50f,65f,33f,13f)
            val eyeHeight=(pose.eyes*if(x>100) pose.rightEye else 1f).coerceAtLeast(2f)
            rect(Color(0xFFBEE7D7),x+17+pose.look,67-eyeHeight/2,44f,if(mood==RobotMood.CURIOUS&&x>100) eyeHeight*.7f else eyeHeight,11f)
            if(!blink&&!delighted&&mood!=RobotMood.SLEEPING&&eyeHeight>10f) drawCircle(Color.White,5f,Offset(x+27+if(portrait) 0f else pose.look*.5f,58f))
        }
        if(!portrait&&mood==RobotMood.SHY) {
            val blush=sin(progress*PI).toFloat().coerceAtLeast(0f)*.5f
            drawOval(Color(0xFFDF9B84).copy(alpha=blush),Offset(36f,84f),Size(25f,9f))
            drawOval(Color(0xFFDF9B84).copy(alpha=blush),Offset(177f,84f),Size(25f,9f))
        }
        drawContext.canvas.restore()
    }
    fun arms() {
        val lx=pose.leftHandX+(94-pose.leftHandX)*tuck;val rx=pose.rightHandX+(146-pose.rightHandX)*tuck
        val ly=pose.leftArm*(1-tuck)+143*tuck;val ry=pose.rightArm*(1-tuck)+143*tuck
        drawLine(edge,Offset(65f,141f),Offset(lx,ly),13f,StrokeCap.Round)
        drawLine(edge,Offset(175f,141f),Offset(rx,ry),13f,StrokeCap.Round)
        rect(Color(0xFFE8B35F),lx-14,ly-10,27f,22f,9f);rect(Color(0xFFE8B35F),rx-13,ry-10,27f,22f,9f)
    }
    drawContext.canvas.save()
    if(!portrait) {
        // Reserve an overshoot margin for antenna, head turns and the fastest little hops.
        drawContext.canvas.translate(12f,18f);drawContext.canvas.scale(.9f,.9f)
        drawOval(Color(0xFF202820).copy(alpha=.07f),Offset(34f,212f),Size(172f,20f))
        drawContext.canvas.translate(pose.shift,pose.bob-pose.lift)
        drawContext.canvas.translate(120f,190f);drawContext.canvas.rotate(pose.lean)
        drawContext.canvas.scale(1f+(1-pose.squash)*.4f,pose.squash);drawContext.canvas.translate(-120f,-190f)
        rect(dark,34f,172f,51f,48f,17f);rect(dark,155f,172f,51f,48f,17f)
        (0..3).forEach {i->rect(Color(0xFF71817B),42f,181f+i*9,34f,3f,2f);rect(Color(0xFF71817B),164f,181f+i*9,34f,3f,2f)}
    }
    // The head is occluded by the shell as it telescopes down; no floating sleeping face.
    if(tuck>0f) {arms();clipRect(left=0f,top=0f,right=240f,bottom=139f) {head()}}
    rect(edge,62f,112f,116f,80f,23f);rect(chassis,62f,108f,116f,76f,23f);rect(Color(0xFFFFDC94),76f,119f,88f,13f,5f)
    rect(Color(0xFF3E5353),84f,145f,72f,23f,7f)
    rect(Color(0xFFB5E2CF).copy(alpha=1-.65f*tuck),91f,153f,if(delighted) 58f else 43f,7f,3f)
    rect(Color(0xFFB36D47),150f,176f,11f,7f,3f)
    if(tuck==0f) {arms();head()}
    else {
        // A split lid closes only after the optical pod has passed below its rim.
        val shut=robotSmooth((tuck-.55f)/.45f)
        rect(edge,77f,105f,86f,9f,4f)
        rect(Color(0xFFFFD98C),77f,103f,43*shut,9f,4f)
        rect(Color(0xFFFFD98C),163f-43*shut,103f,43*shut,9f,4f)
        if(tuck>.75f) {
            val fade=((tuck-.75f)/.25f).coerceIn(0f,1f)
            repeat(2) {n->val x=163f+n*19;val y=78f-n*23-progress*12
                val z=Path().apply {moveTo(x,y);lineTo(x+11,y);lineTo(x,y+11);lineTo(x+11,y+11)}
                drawPath(z,Color(0xFF7B9CA2).copy(alpha=(.75f-n*.2f)*fade),style=Stroke(3f,cap=StrokeCap.Round))}
        }
    }
    if(!portrait&&mood==RobotMood.HIGH_FIVE) {
        val glow=sin(progress*PI).toFloat().coerceAtLeast(0f)
        repeat(3) {finger->drawLine(Color(0xFFFFDC94),Offset(pose.rightHandX-7+finger*6,pose.rightArm-8),Offset(pose.rightHandX-7+finger*6,pose.rightArm-15-3*glow),3f,StrokeCap.Round)}
        drawLine(Color(0xFFE7BA61).copy(alpha=glow),Offset(pose.rightHandX-13,pose.rightArm-25),Offset(pose.rightHandX-18,pose.rightArm-30),3f,StrokeCap.Round)
        drawLine(Color(0xFFE7BA61).copy(alpha=glow),Offset(pose.rightHandX+8,pose.rightArm-25),Offset(pose.rightHandX+12,pose.rightArm-31),3f,StrokeCap.Round)
    }
    if(!portrait&&mood==RobotMood.COMFORT) {
        val pulse=sin(progress*PI).toFloat().coerceAtLeast(0f)
        val heart=Path().apply {moveTo(120f,153f);cubicTo(98f,139f,108f,125f,120f,136f);cubicTo(132f,125f,142f,139f,120f,153f)}
        drawPath(heart,Color(0xFFD78D76).copy(alpha=pulse*.8f))
    }
    if(!portrait&&mood==RobotMood.SURPRISED) {
        val fade=sin(progress*PI).toFloat().coerceAtLeast(0f)
        drawLine(Color(0xFFE7BA61).copy(alpha=fade),Offset(210f,20f),Offset(215f,9f),3f,StrokeCap.Round)
        drawLine(Color(0xFFE7BA61).copy(alpha=fade),Offset(222f,28f),Offset(234f,24f),3f,StrokeCap.Round)
    }
    if(!portrait&&(delighted||mood==RobotMood.DANCE)) {
        val fade=sin(progress*PI).toFloat().coerceAtLeast(0f)
        repeat(4) {n->val x=15f+n*67;val y=110f-55*fade-(n%2)*30
            drawLine(Color(0xFFE6AB43).copy(alpha=fade),Offset(x-5,y),Offset(x+5,y),3f,StrokeCap.Round)
            drawLine(Color(0xFFE6AB43).copy(alpha=fade),Offset(x,y-5),Offset(x,y+5),3f,StrokeCap.Round)}
    }
    if(mood==RobotMood.RECOVER||mood==RobotMood.CAREFUL) {
        rect(Color(0xFFD8DFBD),90f,148f,35f,16f,4f)
        rect(Color(0xFF7D9969),94f,152f,if(mood==RobotMood.RECOVER) 8f else 18f,8f,2f)
    }
    drawContext.canvas.restore()
}

/** Same head, neck, bevels and upper chest as the live model; no wheels or ground shadow. */
internal fun DrawScope.drawRobotLauncherPortrait() {
    drawContext.canvas.save()
    drawContext.canvas.scale(size.width/108f,size.height/108f)
    drawContext.canvas.translate(18f,27f)
    drawContext.canvas.scale(.30f,.30f)
    clipRect(left=0f,top=0f,right=240f,bottom=172f) {drawRobotArtwork(RobotMood.CURIOUS,0f,portrait=true)}
    drawContext.canvas.restore()
}
