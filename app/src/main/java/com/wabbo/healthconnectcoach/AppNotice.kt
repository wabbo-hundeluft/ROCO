package com.wabbo.healthconnectcoach

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState

/** Having an Undo action must not implicitly turn a transient notice into an indefinite one. */
internal suspend fun showAppNotice(host: SnackbarHostState,text: String,canUndo: Boolean)=host.showSnackbar(
    message=RocoStrings.translate(text),actionLabel=if(canUndo) RocoStrings.translate("Rückgängig") else null,
    duration=SnackbarDuration.Short,withDismissAction=true)
