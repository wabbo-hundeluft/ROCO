package com.wabbo.healthconnectcoach

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Partly expanded on entry, draggable to the full detail surface. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun V2DetailSheet(onDismissRequest: ()->Unit,title: @Composable ()->Unit,text: @Composable ()->Unit,
    confirmButton: @Composable ()->Unit,dismissButton: @Composable ()->Unit={},startExpanded: Boolean=false) {
    val state=rememberModalBottomSheetState(skipPartiallyExpanded=startExpanded)
    val scope=rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest=onDismissRequest,modifier=Modifier.testTag("detail-sheet"),sheetState=state,
        containerColor=Color(0xFFF8F8F3),contentColor=V2Ink) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {ProvideTextStyle(MaterialTheme.typography.headlineMedium,content=title)}
                IconButton(onClick=onDismissRequest,modifier=Modifier.semantics {contentDescription=RocoStrings.translate("Details schließen")}) {V2Icon("close")}
            }
            if(state.currentValue==SheetValue.PartiallyExpanded) TextButton(onClick={scope.launch {state.expand()}}) {RocoText("Details erweitern")}

            Box(Modifier.weight(1f).fillMaxWidth()) {text()}
            Column(Modifier.fillMaxWidth().padding(bottom=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {dismissButton();confirmButton()}
        }
    }
}
