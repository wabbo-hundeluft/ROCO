package com.wabbo.healthconnectcoach

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity

/** Consume Scaffold spacing before applying the remaining IME inset, exactly once. */
@Composable internal fun V2InsetScaffold(tab: String,onSelect: (String)->Unit,snackbar: SnackbarHostState,
    keyboardInsets: WindowInsets=WindowInsets.ime,
    systemInsets: WindowInsets=WindowInsets.systemBars.union(WindowInsets.displayCutout),
    navigationVisible: Boolean=true,
    content: @Composable (Modifier,Boolean)->Unit) {
    val keyboardVisible=keyboardInsets.getBottom(LocalDensity.current)>0
    Scaffold(containerColor=MaterialTheme.colorScheme.background,contentWindowInsets=systemInsets,
        snackbarHost={SnackbarHost(snackbar)},bottomBar={if(!keyboardVisible&&navigationVisible) V2BottomNavigation(tab,onSelect)}) {padding->
        content(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).windowInsetsPadding(keyboardInsets),keyboardVisible)
    }
}
