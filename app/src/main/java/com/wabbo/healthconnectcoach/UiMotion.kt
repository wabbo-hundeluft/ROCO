package com.wabbo.healthconnectcoach

import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Infinite product animations stop when the screen is not resumed or system motion is off. */
@Composable internal fun productMotionEnabled(): Boolean {
    val owner=LocalLifecycleOwner.current
    val context=LocalContext.current
    fun allowed()=owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)&&
        Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f
    var enabled by remember(owner) {mutableStateOf(allowed())}
    DisposableEffect(owner) {
        val observer=LifecycleEventObserver {_,_->enabled=allowed()}
        owner.lifecycle.addObserver(observer)
        onDispose {owner.lifecycle.removeObserver(observer)}
    }
    return enabled
}
