package com.wabbo.healthconnectcoach

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun V2ProfileScreen(person: String,robot: String,source: String,back: ()->Unit,showSource: ()->Unit,
    editPersonal: ()->Unit,editOnboarding: ()->Unit,editGoals: ()->Unit,refresh: ()->Unit,connection: @Composable ()->Unit,ai: @Composable ()->Unit,
    diagnostics: @Composable ()->Unit,localData: @Composable ()->Unit,editMemory:()->Unit={}) {
    var connectionOpen by rememberSaveable {mutableStateOf(false)}
    var helpOpen by rememberSaveable {mutableStateOf(false)}
    V2PageHeader("Dein Profil",back=back,info=showSource)
    Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(9.dp)) {
        RobotCompanion(robot,false,Modifier.size(120.dp))
        RocoText("$person & $robot",fontSize=23.sp,lineHeight=30.sp,fontWeight=FontWeight(750))
        RocoText("Dein Team für einen guten Rhythmus.",fontSize=13.sp,lineHeight=21.sp,color=Color(0xFF5B6852))
    }
    V2Section("Datenverbindung") {V2Card(gap=0.dp) {
        V2SettingRow("cloud",source,if(source=="Google Health") "Bevorzugte Quelle · Uhrendaten" else "Ersatzquelle · keine Vermischung",showSource)
        HorizontalDivider(color=Color(0xFFEDF2E4))
        V2SettingRow("settings","Verbindung verwalten", "Freigaben, Verlauf und Ersatzquelle",{connectionOpen=!connectionOpen})
    }}
    if(connectionOpen) {connection();TextButton(onClick={connectionOpen=false}) {RocoText("Verbindungseinstellungen schließen")}}
    LanguageSettingsCard()
    ai()
    V2Section("Dein Einstieg") {V2Card(gap=0.dp) {
        V2SettingRow("person","Persönliche Score-Basis","Geburtsjahr, Maximalpuls und Pulsgewichtung",editPersonal)
        HorizontalDivider(color=Color(0xFFEDF2E4))
        V2SettingRow("coach","Coach-Gedächtnis","Erinnerungen ansehen, ändern und befristen",editMemory)
        HorizontalDivider(color=Color(0xFFEDF2E4))
        V2SettingRow("person","Namen und Einstieg bearbeiten","Vorname, Roboter und Orientierung",editOnboarding)
        HorizontalDivider(color=Color(0xFFEDF2E4))
        V2SettingRow("training","Ziele und Trainingstage","Dein Plan, Bewegung und Schlaf",editGoals)
        HorizontalDivider(color=Color(0xFFEDF2E4))
        V2SettingRow("refresh","Daten aktualisieren","$robot begleitet die Verarbeitungsschritte",refresh)
    }}
    V2Section("Hilfe und lokale Daten",if(helpOpen) "Schließen" else "Öffnen",{helpOpen=!helpOpen}) {
        if(helpOpen) {diagnostics();localData()}
    }
    RocoText("ROCO ${BuildConfig.VERSION_NAME} · eigene Wellness-Schätzungen",style=MaterialTheme.typography.bodySmall,color=V2Muted)
}

@Composable internal fun V2SettingRow(icon: String,title: String,subtitle: String,choose: ()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClickLabel=RocoStrings.translate(title),onClick=choose).padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(13.dp)) {
        V2Icon(icon,tint=Color(0xFF809E62))
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            RocoText(title,fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight(750))
            RocoText(subtitle,fontSize=12.sp,lineHeight=19.sp,color=Color(0xFF637551))
        }
        V2Icon("next",size=20.dp,tint=Color(0xFF809E62))
    }
}
