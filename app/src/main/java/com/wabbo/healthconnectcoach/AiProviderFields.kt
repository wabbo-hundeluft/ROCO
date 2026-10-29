package com.wabbo.healthconnectcoach

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable internal fun AiProviderFields(provider: CoachProvider,config: AiConfiguration,change: (AiConfiguration)->Unit) {
    val key=config.keyFor(provider);val scope=rememberCoroutineScope()
    var models by remember(key) {mutableStateOf<List<AiModelChoice>>(emptyList())}
    var loading by remember(key) {mutableStateOf(false)};var error by remember(key) {mutableStateOf<String?>(null)}
    var expanded by remember {mutableStateOf(false)}
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        RocoText(provider.label+(if(provider==CoachProvider.GROQ) " · Standard" else " · Ersatz"),style=MaterialTheme.typography.titleLarge)
        OutlinedTextField(key,{change(config.withProvider(provider,key=it))},label={RocoText("${provider.label} API-Key")},visualTransformation=PasswordVisualTransformation(),singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(config.modelFor(provider),{change(config.withProvider(provider,model=it))},label={RocoText("${provider.label}-Modell-ID")},singleLine=true,modifier=Modifier.fillMaxWidth())
        RocoText(provider.modelHelp(),fontSize=12.sp,color=V2Muted)
        RocoText(if(provider==CoachProvider.OPEN_ROUTER) "Auswahl: nur veröffentlichte Nullpreise und Tool-Unterstützung." else "Die API meldet verfügbare Modelle, aber keine verlässliche Gratisquote deines Kontos.",fontSize=11.sp,color=V2Muted)
        ClayButton(onClick={scope.launch {
            loading=true;error=null
            try {models=loadAiModels(provider,key.trim());if(models.isEmpty()) error="Keine passenden Chatmodelle gemeldet. Die Modell-ID bleibt manuell editierbar."}
            catch(e:CancellationException) {throw e}
            catch(_:Exception) {error="Modellliste konnte nicht geladen werden. Key, Verbindung und Kontolimits prüfen; Modell-ID kann manuell eingetragen werden."}
            finally {loading=false}
        }},enabled=key.isNotBlank()&&!loading,secondary=true) {RocoText(if(loading) "Modelle werden geladen …" else "Modelle vom Anbieter laden")}
        if(models.isNotEmpty()) Box {
            OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()) {RocoText("Modell auswählen (${models.size})");V2Icon("expand")}
            DropdownMenu(expanded,{expanded=false},modifier=Modifier.heightIn(max=320.dp)) {
                models.forEach {m->DropdownMenuItem(text={Column {RocoText(m.label);RocoText(m.id+(if(m.verifiedFree) " · kostenlos gelistet" else ""),fontSize=11.sp)}},onClick={change(config.withProvider(provider,model=m.id));expanded=false})}
            }
        }
        error?.let {RocoText(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp)}
        HorizontalDivider()
    }
}
