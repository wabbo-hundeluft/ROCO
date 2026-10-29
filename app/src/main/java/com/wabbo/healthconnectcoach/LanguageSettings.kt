package com.wabbo.healthconnectcoach

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal val rocoLanguageOptions=listOf("" to "Systemsprache","de" to "Deutsch","en" to "English","fr" to "Français","es" to "Español")

@Composable internal fun LanguageSettingsCard() {
    val context=LocalContext.current
    var selected by remember {mutableStateOf(RocoLocales.selectedLanguage(context))}
    var open by rememberSaveable {mutableStateOf(false)}
    V2Section("Sprache") {V2Card(gap=0.dp) {
        V2SettingRow("settings","App-Sprache",rocoLanguageOptions.firstOrNull {it.first==selected}?.second ?: "Systemsprache",{open=true})
    }}
    if(open) V2DetailSheet(onDismissRequest={open=false},startExpanded=true,title={RocoText("App-Sprache")},confirmButton={},text={
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            RocoText("Wähle deine Sprache. Die Oberfläche wird direkt umgestellt.",color=V2Muted)
            rocoLanguageOptions.forEach {(code,name)->
                V2ClaySurface(onClick={
                    selected=code;open=false
                    RocoLocales.selectLanguage(context,code)
                },color=if(code==selected) androidx.compose.ui.graphics.Color(0xFFE1F0D8) else androidx.compose.ui.graphics.Color.White,
                    modifier=Modifier.fillMaxWidth().testTag("language-${code.ifEmpty {"system"}}")) {
                    Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        if(code.isEmpty()) RocoText(name,Modifier.weight(1f),fontWeight=FontWeight.Bold)
                        else RocoVerbatimText(name,Modifier.weight(1f),fontWeight=FontWeight.Bold)
                        if(code==selected) V2Icon("check_circle",tint=V2Green)
                    }
                }
            }
            RocoText("Systemsprache folgt deinem Gerät. Nicht unterstützte Sprachen werden auf Englisch angezeigt.",style=MaterialTheme.typography.bodySmall,color=V2Muted)
        }
    })
}
