package com.wabbo.healthconnectcoach

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A real solid lower edge, shared by buttons, suggestions and interactive cards. */
@Composable internal fun V2ClaySurface(onClick: ()->Unit,modifier: Modifier=Modifier,enabled: Boolean=true,
    color: Color=Color.White,contentColor: Color=V2Ink,edgeColor: Color=Color(0xFFD7DECE),
    shape: Shape=RoundedCornerShape(23.dp),edgeHeight: Dp=4.dp,border: BorderStroke?=BorderStroke(1.dp,Color(0xFFE6E9DF)),
    onClickLabel: String?=null,content: @Composable ()->Unit) {
    val scope=rememberCoroutineScope();val depth=remember {Animatable(0f)}
    val interactions=remember {MutableInteractionSource()};val haptics=LocalHapticFeedback.current
    val motion=productMotionEnabled();var release by remember {mutableStateOf<Job?>(null)}
    val currentClick by rememberUpdatedState(onClick)
    fun settle(wait: Long=0) {
        release=scope.launch {
            if(motion) {delay(wait);depth.animateTo(0f,spring(dampingRatio=.55f,stiffness=900f))}
            else depth.snapTo(0f)
        }
    }
    LaunchedEffect(enabled) {if(!enabled) {release?.cancel();depth.snapTo(0f)}}
    Box(modifier.padding(bottom=edgeHeight).pointerInput(enabled,edgeHeight,motion) {
        if(enabled) awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
            release?.cancel();haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch {depth.snapTo(edgeHeight.value)}
            val up=waitForUpOrCancellation(pass=PointerEventPass.Initial)
            val elapsed=(up?.uptimeMillis ?: down.uptimeMillis)-down.uptimeMillis
            settle(if(up==null) 0 else (90-elapsed).coerceAtLeast(0))
        }
    }) {
        Box(Modifier.matchParentSize().offset(y=edgeHeight).background(if(enabled) edgeColor else Color(0xFFD9E2CE),shape))
        Surface(color=color,contentColor=contentColor,shape=shape,border=border,
            modifier=Modifier.fillMaxWidth().graphicsLayer {translationY=depth.value.dp.toPx()}
                .clickable(enabled=enabled,role=Role.Button,interactionSource=interactions,indication=null,onClickLabel=onClickLabel?.let(RocoStrings::translate)) {
                    if(depth.value==0f) {
                        release?.cancel();haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        scope.launch {depth.snapTo(edgeHeight.value);settle(90)}
                    }
                    currentClick()
                },content=content)
    }
}

/** The input is a field, not a button: only its send action receives press handling. */
@Composable internal fun V2ComposerShell(content: @Composable ()->Unit) {
    val shape=RoundedCornerShape(26.dp)
    Box(Modifier.fillMaxWidth().padding(bottom=4.dp).testTag("coach-composer")) {
        Box(Modifier.matchParentSize().offset(y=4.dp).background(Color(0xFFD9E4CC),shape))
        Surface(color=Color.White,shape=shape,border=BorderStroke(1.dp,Color(0xFFDBE6CD)),content=content)
    }
}
