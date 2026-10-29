package com.wabbo.healthconnectcoach

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CompanionOnboarding(initialPerson: String, initialRobot: String, connected: Boolean,
    onComplete: (String, String, Boolean) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var person by rememberSaveable { mutableStateOf(initialPerson) }
    var robot by rememberSaveable { mutableStateOf(initialRobot) }
    Scaffold(containerColor=MaterialTheme.colorScheme.background, contentWindowInsets=WindowInsets.safeDrawing) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).imePadding().padding(horizontal=28.dp,vertical=24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                (0..2).forEach { index -> Box(Modifier.weight(1f).height(6.dp).background(if(index<=step) Color(0xFF171A18) else Color(0xFFE2E6DF),RoundedCornerShape(6.dp))) }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical=24.dp),
                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(24.dp)) {
                RobotCompanion(robot.ifBlank { "Dein Coach" }, false, Modifier.size(210.dp), attentive=if(step==0) person.isNotBlank() else if(step==1) robot!=initialRobot else false, reactionKey=step)
                RocoText(when(step) { 0->"Hallo! Schön, dass du da bist.";1->"Wir sind jetzt ein Team, ${person.trim()}.";else->"Lernen wir deinen Rhythmus kennen." },
                    style=MaterialTheme.typography.headlineLarge,textAlign=TextAlign.Center)
                RocoText(when(step) { 0->"Ich werde dein persönlicher Gesundheitscoach. Wie darf ich dich nennen?";1->"Und wie soll ich heißen? Such dir einen Namen für mich aus.";else->"Mit den Daten deiner Uhr erkläre ich dir Schlaf, Energie und Training. Du entscheidest, was du freigibst." },
                    style=MaterialTheme.typography.bodyLarge,textAlign=TextAlign.Center)
                if(step==0) OutlinedTextField(person,{person=it.take(32)},label={RocoText("Dein Vorname")},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp))
                if(step==1) OutlinedTextField(robot,{robot=it.take(24)},label={RocoText("Name deines Roboters")},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp))
                if(step==2) {
                    listOf("Schlaf verstehen" to "Nächte, Phasen und deinen Rhythmus entdecken.","Energie einteilen" to "Erholung und Bewegung gemeinsam betrachten.","Dranbleiben" to "Deine Trainingswoche mit passenden Pausen planen.").forEach { (title,text) ->
                        Surface(shape=RoundedCornerShape(22.dp),color=Color.White,modifier=Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) { RocoText(title,style=MaterialTheme.typography.titleLarge);RocoText(text,style=MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
            }
            ClayButton(onClick={if(step<2) step++ else onComplete(person.trim(),robot.trim(),!connected)},
                enabled=when(step){0->person.trim().isNotBlank();1->robot.trim().isNotBlank();else->true},modifier=Modifier.fillMaxWidth()) {
                RocoText(when(step){0->"Weiter";1->"Unser Team steht!";else->if(connected) "Auf zu meinem Tag" else "Health Connect verbinden"})
            }
            if(step==2 && !connected) TextButton(onClick={onComplete(person.trim(),robot.trim(),false)},modifier=Modifier.fillMaxWidth()) { RocoText("Später verbinden") }
            else if(step>0) TextButton(onClick={step--},modifier=Modifier.fillMaxWidth()) { RocoText("Zurück") }
        }
    }
}

@Stable
internal class CoachConversationState {
    var question by mutableStateOf("")
    var attachmentIds by mutableStateOf<List<String>>(emptyList())
    var messages by mutableStateOf<List<CoachMessage>>(emptyList())
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var previousMessages by mutableStateOf<List<CoachMessage>>(emptyList())
    var sessionId by mutableIntStateOf(0)
        private set
    fun startNewChat():Boolean {
        if(busy) return false
        if(messages.isNotEmpty()) previousMessages=messages
        messages=emptyList();question="";attachmentIds=emptyList();error=null;sessionId++
        return true
    }
    fun resumePreviousChat():Boolean {
        if(busy||previousMessages.isEmpty()) return false
        val current=messages;messages=previousMessages;previousMessages=current
        question="";attachmentIds=emptyList();error=null;sessionId++
        return true
    }
}

@Composable
internal fun CoachChatScreen(robot: String, person: String, config: AiConfiguration, day: DayInsight?,
    state: CoachConversationState, requestScope: CoroutineScope, modifier: Modifier=Modifier,
    plan: TrainingPlan? = null, planReady: Boolean=true,planStatus: String?=null, onPlanUpdate: suspend (TrainingPlan,Long?)->Unit = {_,_->}, onUndo: ()->Unit = {}, canUndo: Boolean = false, keyboardVisible: Boolean=false,appData: CoachDataAccess?=null,openMemory:(()->Unit)?=null,chatReady:Boolean=true,
    configure: ()->Unit) {
    val list=rememberLazyListState()
    val focus=LocalFocusManager.current
    var composer by remember {mutableStateOf(TextFieldValue(state.question,TextRange(state.question.length)))}
    LaunchedEffect(state.question,state.sessionId) {if(composer.text!=state.question||state.question.isEmpty()) composer=TextFieldValue(state.question,TextRange(state.question.length))}
    val query=mentionQuery(composer.text,composer.selection.end)
    val selectedIds=selectedMentionIds(state.question,state.attachmentIds)
    val robotEvent=when {
        !state.error.isNullOrBlank()->RobotEvent.ERROR
        state.busy->RobotEvent.NONE
        state.question.isNotBlank()->RobotEvent.INPUT
        state.messages.lastOrNull()?.role=="assistant"->RobotEvent.ANSWERED
        else->RobotEvent.NONE
    }
    val robotReaction=state.messages.size*1000+state.question.length/8+(state.error?.hashCode() ?: 0)

    val configured=CoachProvider.entries.any {config.keyFor(it).isNotBlank()}
    LaunchedEffect(state.messages.size,state.busy,state.sessionId) { if(state.messages.isNotEmpty()) list.animateScrollToItem(state.messages.lastIndex) }
    BoxWithConstraints(modifier) {
    val compactWelcome=maxHeight<560.dp||keyboardVisible
    val compactPicker=maxHeight<500.dp||keyboardVisible
    val conversationHeader=state.messages.isNotEmpty()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=if(conversationHeader) 10.dp else if(compactWelcome) 12.dp else 24.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(conversationHeader) Box(Modifier.size(48.dp).testTag("coach-header-avatar")) {
                RobotCompanion(robot,state.busy,Modifier.fillMaxSize(),attentive=state.question.isNotBlank(),reactionKey=robotReaction,mood=if(state.busy) RobotMood.THINKING else if(state.question.isNotBlank()) RobotMood.LISTENING else RobotMood.WAVE,allowSleep=false,event=robotEvent)
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(if(conversationHeader) 2.dp else 5.dp)) {
                if(!conversationHeader&&!compactWelcome) V2Eyebrow("DEIN PERSÖNLICHER COACH")
                RocoText(robot,fontSize=if(conversationHeader) 20.sp else 28.sp,lineHeight=if(conversationHeader) 25.sp else 34.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=(-.6).sp,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if(conversationHeader) RocoText(if(state.busy) "Ich denke nach …" else "Ich bin ganz Ohr.",fontSize=12.sp,lineHeight=17.sp,color=V2Muted)
            }
            IconButton(onClick={focus.clearFocus();state.startNewChat()},enabled=!state.busy&&chatReady,
                modifier=Modifier.size(44.dp).testTag("new-coach-chat").semantics {contentDescription=RocoStrings.translate("Neuer Chat")}) {V2Icon("edit",tint=if(state.busy||!chatReady) V2Muted else V2Green)}
            if(openMemory!=null) IconButton(onClick=openMemory,modifier=Modifier.size(44.dp).semantics {contentDescription=RocoStrings.translate("Coach-Gedächtnis öffnen")}) {V2Icon("memory",tint=V2Green)}
            Surface(shape=CircleShape,color=Color(0xFFE3E9DD),modifier=Modifier.size(if(conversationHeader) 40.dp else 46.dp).clickable(onClickLabel=RocoStrings.translate("Profil öffnen"),onClick=configure).semantics {contentDescription=RocoStrings.translate("Profil öffnen")}) {Box(contentAlignment=Alignment.Center) {RocoText(person.take(1).uppercase().ifBlank {"P"},fontSize=16.sp,fontWeight=FontWeight(750))}}
        }
        if(state.messages.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().testTag("coach-welcome").verticalScroll(rememberScrollState()).padding(horizontal=24.dp,vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                key(state.sessionId) {RobotCompanion(robot,state.busy,Modifier.size(if(compactWelcome) 100.dp else 195.dp).testTag("coach-welcome-avatar"),attentive=state.question.isNotBlank(),reactionKey=robotReaction,allowSleep=false,event=robotEvent)}
                Spacer(Modifier.height(if(compactWelcome) 8.dp else 18.dp))
                RocoText(if(conversationHeader) "Frag mich zu deinen Werten oder deinem Plan." else if(compactWelcome) "Ich bin ganz Ohr." else if(person.isBlank()) "Was brauchst du heute?" else "Was brauchst du\nheute, $person?",fontSize=if(compactWelcome) 20.sp else 29.sp,lineHeight=if(compactWelcome) 26.sp else 36.sp,fontWeight=FontWeight(750),letterSpacing=(-1).sp,textAlign=TextAlign.Center)
                if(!compactWelcome) {
                    Spacer(Modifier.height(15.dp))
                    RocoText("Deine Werte verstehen.\nDeinen Plan passend machen.",fontSize=13.sp,lineHeight=24.sp,color=Color(0xFF5B6852),textAlign=TextAlign.Center)
                }
                Spacer(Modifier.height(if(compactWelcome) 12.dp else 33.dp))
                if(!compactWelcome||state.question.isBlank()) Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    V2CoachSuggestion("Meine Energie verstehen","battery",enabled=!state.busy) {state.question=RocoStrings.translate("Erkläre mir meine Energie.")}
                    V2CoachSuggestion(if(plan==null) "Meinen Trainingsplan besprechen" else "Meinen Trainingsplan anpassen","date",enabled=!state.busy) {state.question=RocoStrings.translate(if(plan==null) "Lass uns meinen Trainingsplan gemeinsam festlegen. Frage mich zuerst nach meinen Zielen." else "Hilf mir, meinen Trainingsplan passend anzupassen.")}
                    if(state.previousMessages.isNotEmpty()) TextButton(onClick={focus.clearFocus();state.resumePreviousChat()},enabled=!state.busy&&chatReady,modifier=Modifier.fillMaxWidth()) {V2Icon("undo");RocoText("Letzten Chat fortsetzen")}
                    if(!configured) TextButton(onClick=configure,modifier=Modifier.fillMaxWidth()) {RocoText("Meinen KI-Coach einrichten")}
                }
                Spacer(Modifier.height(24.dp))
            }
        } else {
            LazyColumn(state=list,modifier=Modifier.weight(1f).fillMaxWidth().testTag("coach-message-list"),contentPadding=PaddingValues(horizontal=24.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                items(state.messages) { message ->
                    val fromUser=message.role=="user"
                    Column(Modifier.fillMaxWidth(),horizontalAlignment=if(fromUser) Alignment.End else Alignment.Start) {
                        Surface(color=if(fromUser) Color(0xFFE4EFE3) else Color.White,shape=RoundedCornerShape(24.dp),modifier=Modifier.widthIn(max=520.dp)) {
                            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                RocoText(if(fromUser) "Du" else message.provider?.let {"$robot · $it"} ?: robot,style=MaterialTheme.typography.labelLarge)
                                if(fromUser) {
                                    RocoVerbatimText(message.text,style=MaterialTheme.typography.bodyLarge)
                                    if(message.mentions.isNotEmpty()) RocoText("Angehängt: "+coachMentions.filter {it.id in message.mentions}.joinToString {it.label},fontSize=11.sp,color=V2Muted)
                                } else CoachMarkdown(message.text)
                                message.planProposal?.let {proposal->
                                    V2PlanProposal(proposal,enabled=!state.busy&&planReady,alreadyPlanned=plan!=null,accept={
                                        state.busy=true;state.error=null
                                        requestScope.launch {
                                            try {
                                                check(plan==null) {"Es gibt inzwischen einen bestätigten Plan."}
                                                val accepted=validateInitialPlanProposal(proposal,java.time.LocalDate.now())
                                                onPlanUpdate(accepted,null)
                                                state.messages=state.messages.map {if(it==message) it.copy(planProposal=null,planChanges=listOf("Dein erster Trainingsplan wurde bestätigt und gespeichert.")) else it}
                                            } catch(e:CancellationException) {throw e} catch(e:Exception) {state.error=if(e is IllegalStateException||e is IllegalArgumentException) e.message else "Der Plan konnte nicht gespeichert werden. Dein Vorschlag bleibt erhalten."}
                                            finally {state.busy=false}
                                        }
                                    },discard={state.messages=state.messages.map {if(it==message) it.copy(planProposal=null,text=it.text+"\n\n_Vorschlag verworfen._") else it}})
                                }
                                if(message.planChanges.isNotEmpty()) {
                                    HorizontalDivider()
                                    RocoText("Plan gespeichert",style=MaterialTheme.typography.titleSmall,color=V2Green)
                                    message.planChanges.forEach {RocoText(it,style=MaterialTheme.typography.bodySmall)}
                                    if(canUndo) TextButton(onClick=onUndo) {V2Icon("undo");RocoText("Letzte Planänderung zurücknehmen")}
                                }
                            }
                        }
                    }
                }
            }
        }
        val providerLabel=state.messages.lastOrNull {it.role=="assistant"}?.provider?.let {"Zuletzt $it"} ?: CoachProvider.entries.filter {config.keyFor(it).isNotBlank()}.joinToString(" → ") {it.label}.ifBlank {"Coach noch nicht eingerichtet"}
        Surface(color=Color(0xFFF8F8F3)) {
            Column(Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,top=if(keyboardVisible) 8.dp else 14.dp,bottom=if(keyboardVisible) 6.dp else 14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                planStatus?.let {RocoText(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
                if(!planReady&&planStatus==null) RocoText("Dein gespeicherter Trainingsplan wird geöffnet …",style=MaterialTheme.typography.bodySmall)
                state.error?.let { RocoText(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
                if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if(keyboardVisible) V2ProviderCaption(providerLabel,config.shareHealthSummary)
                if(query!=null&&!state.busy) CoachMentionPicker(query.query,appData,day?.date ?: java.time.LocalDate.now(),config.shareHealthSummary,compact=compactPicker) {item->
                    val inserted=insertMention(composer.text,query,item)
                    composer=TextFieldValue(inserted.first,TextRange(inserted.second));state.question=inserted.first
                    state.attachmentIds=(state.attachmentIds+item.id).distinct()
                }
                if(selectedIds.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    coachMentions.filter {it.id in selectedIds}.forEach {item->InputChip(selected=true,onClick={
                        state.question=state.question.replace("@${item.label}","");state.attachmentIds=state.attachmentIds-item.id
                    },enabled=!state.busy,label={RocoText(item.label,fontSize=11.sp)},trailingIcon={RocoText("×")})}
                }
                V2ComposerShell {
                    Row(Modifier.fillMaxWidth().padding(start=18.dp,end=8.dp,top=7.dp,bottom=7.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) {
                            if(state.question.isBlank()) RocoText("Nachricht an $robot … (@ für Werte)",fontSize=13.sp,lineHeight=20.sp,color=Color(0xFF6A775E))
                            BasicTextField(composer,{if(it.text.length<=4000) {composer=it;state.question=it.text}},enabled=!state.busy,maxLines=4,textStyle=MaterialTheme.typography.bodyMedium.copy(fontSize=13.sp,lineHeight=20.sp,color=V2Ink),cursorBrush=SolidColor(V2Green),modifier=Modifier.fillMaxWidth().heightIn(min=37.dp).semantics {contentDescription=RocoStrings.translate("Nachricht an $robot")})
                        }
                        V2ClaySurface(onClick={
                        if(!configured) configure() else {
                            val request=(state.messages+CoachMessage("user",state.question.trim(),mentions=selectedIds)).takeLast(24)
                            state.busy=true;state.error=null;state.messages=request;state.question="";state.attachmentIds=emptyList()
                            requestScope.launch {
                                try {
                                    val answer=askPlanCoach(config,request,day,plan,robot,person,data=appData)
                                    answer.updatedPlan?.let {next ->onPlanUpdate(next,plan?.revision)}
                                    state.messages=(request+CoachMessage("assistant",(if(answer.usedFallback) "_Der bevorzugte Anbieter konnte nicht antworten. ${answer.provider} übernimmt diese Antwort._\n\n" else "")+answer.text,answer.changes,answer.proposedPlan,answer.provider)).takeLast(24);state.question=""
                                }
                                catch(e:CancellationException){throw e}
                                catch(e:Exception){state.messages=request.dropLast(1);state.question=request.last().text;state.attachmentIds=request.last().mentions;state.error=if(e is IllegalArgumentException || e is IllegalStateException) e.message else "Verbindung fehlgeschlagen. Bitte erneut versuchen."}
                                finally { state.busy=false }
                            }
                        }
                    },enabled=!state.busy && state.question.isNotBlank(),shape=CircleShape,color=if(!state.busy&&state.question.isNotBlank()) Color(0xFF3D6535) else Color(0xFFE4EDDA),edgeColor=Color(0xFF24481F),border=null,modifier=Modifier.width(44.dp).semantics {contentDescription=RocoStrings.translate("Nachricht senden")}) {
                        Box(Modifier.size(44.dp),contentAlignment=Alignment.Center) {V2Icon("up",tint=if(!state.busy&&state.question.isNotBlank()) Color.White else Color(0xFFA9BB93),size=24.dp)}
                    }
                    }
                }
                if(!keyboardVisible) V2ProviderCaption(providerLabel,config.shareHealthSummary)
            }
        }
    }
    }
}

@Composable private fun V2ProviderCaption(provider: String,share: Boolean) {
    RocoText(if(share) "$provider · Datenzugriff freigegeben" else "$provider · Gesundheitsdaten nicht freigegeben",
        modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center,style=MaterialTheme.typography.labelSmall.copy(fontSize=10.sp,lineHeight=15.sp),color=Color(0xFF637551))
}

@Composable private fun V2CoachSuggestion(title: String,icon: String,enabled: Boolean,choose: ()->Unit) {
    V2ClaySurface(onClick=choose,enabled=enabled,color=Color(0xFFF9FCF4),edgeColor=Color(0xFFD6E3C5),shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,Color(0xFFDCE8CE)),modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min=54.dp).padding(horizontal=14.dp,vertical=15.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            V2Icon(icon,tint=Color(0xFF476438),size=20.dp)
            RocoText(title,Modifier.weight(1f),fontSize=12.sp,lineHeight=19.sp,color=Color(0xFF476438))
            V2Icon("forward",tint=Color(0xFF476438),size=18.dp)
        }
    }
}

@Composable internal fun DiagnosticsPanel() {
    val context=LocalContext.current
    val clipboard=LocalClipboardManager.current
    var report by remember { mutableStateOf<String?>(null) }
    val scope=rememberCoroutineScope()
    Surface(color=Color.White,shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            RocoText("Hilfe bei Abstürzen",style=MaterialTheme.typography.titleLarge)
            RocoText("Zeigt technische Fehlerstellen und Androids letzten Beendigungsgrund. Keine Messwerte oder API-Keys.")
            ClayButton(onClick={scope.launch { report=withContext(Dispatchers.IO){readAppDiagnostics(context)} }},secondary=true) { RocoText("Diagnose anzeigen") }
            report?.let { text -> RocoVerbatimText(text,style=MaterialTheme.typography.bodySmall);TextButton(onClick={clipboard.setText(AnnotatedString(text))}) { RocoText("Diagnose kopieren") } }
        }
    }
}
