package com.wabbo.healthconnectcoach

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class CoachContinuityTest {
    private val previous=listOf(CoachMessage("user","Ich möchte dienstags Rad fahren."),CoachMessage("assistant","Wie lange möchtest du fahren?",provider="Groq"))
    @Test fun recapContainsTheLastCompletedPairAndNeverThePendingNewQuestion() {
        val recap=JSONObject(coachExchangeRecap(previous+CoachMessage("user","NEUE_FRAGE_35_MINUTEN"))!!)
        assertEquals(previous[0].text,recap.getString("user_excerpt"))
        assertEquals(previous[1].text,recap.getString("coach_excerpt"))
        assertEquals("Groq",recap.getString("previous_provider"))
        assertFalse(recap.toString().contains("NEUE_FRAGE"))
        assertNull(coachExchangeRecap(listOf(CoachMessage("user","Erste Frage"))))
        assertNull(coachExchangeRecap(listOf(CoachMessage("assistant","Begrüßung ohne Nutzerfrage"))))
    }
    @Test fun recapIsBoundedAndKeepsTheConclusionWithoutAnotherAiCall() {
        val text="Anfang. "+"Ausführliche Erklärung. ".repeat(500)+"Bitte ändere nichts am Plan."
        val excerpt=coachRecapExcerpt(text,850)
        assertTrue(excerpt.length<=850);assertTrue(excerpt.startsWith("Anfang."));assertTrue(excerpt.endsWith("Bitte ändere nichts am Plan."))
        assertTrue(excerpt.contains("[…]"))
    }
    @Test fun nativeGeminiAndCompatibleProvidersReceiveTheSameRecap() {
        val messages=previous+CoachMessage("user","35 Minuten.")
        val system="Trusted system instructions."
        val native=buildCoachTextRequest(system,messages,"gemini",true)
        val compatible=buildCoachTextRequest(system,messages,"model",false)
        val a=native.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text")
        val b=compatible.getJSONArray("messages").getJSONObject(0).getString("content")
        assertEquals(a,b);assertTrue(a.contains(coachExchangeRecap(messages)!!));assertTrue(a.startsWith(system))
        assertEquals(3,native.getJSONArray("contents").length())
        assertEquals(4,compatible.getJSONArray("messages").length())
    }
    @Test fun everyFallbackKeepsTheSamePreviousExchange()=runBlocking {
        val recaps=mutableListOf<String>()
        val messages=previous+CoachMessage("user","35 Minuten.")
        askPlanCoach(AiConfiguration(groqKey="g",mistralKey="m",routerKey="r",geminiKey="a"),messages,null,null,"Pip","Noah") {provider,_,body->
            val system=body.getJSONArray("messages").getJSONObject(0).getString("content")
            val recap=coachExchangeRecap(messages)!!
            assertTrue(system.contains(recap));recaps+=recap
            if(provider!=CoachProvider.GEMINI) throw IOException("offline")
            JSONObject().put("choices",JSONArray().put(JSONObject().put("message",JSONObject().put("content","Passt, 35 Minuten."))))
        }
        assertEquals(4,recaps.size);assertEquals(1,recaps.distinct().size)
    }
    @Test fun newChatKeepsOnePreviousThreadButSendsNoOldRecap() {
        val state=CoachConversationState().apply {messages=previous;question="Entwurf";attachmentIds=listOf("hrv");error="Alt"}
        assertTrue(state.startNewChat());assertEquals(previous,state.previousMessages);assertTrue(state.messages.isEmpty())
        assertEquals("",state.question);assertTrue(state.attachmentIds.isEmpty());assertNull(state.error)
        assertNull(coachExchangeRecap(state.messages+CoachMessage("user","Ganz neues Thema")))
        assertTrue(state.startNewChat());assertEquals(previous,state.previousMessages)
        assertTrue(state.resumePreviousChat());assertEquals(previous,state.messages)
        state.busy=true;assertFalse(state.startNewChat());assertEquals(previous,state.messages)
    }
    @Test fun archiveRoundTripAndLegacyMigrationKeepMessagesAndProviders() {
        val active=listOf(CoachMessage("user","Anderes Thema"))
        val snapshot=CoachConversationSnapshot(active,previous)
        assertEquals(snapshot,decodeCoachConversation(encodeCoachConversation(snapshot)))
        assertEquals(CoachConversationSnapshot(previous),decodeCoachConversation(encodeCoachMessages(previous)))
        assertThrows(IllegalArgumentException::class.java) {decodeCoachConversation("{\"version\":99,\"messages\":[]}")}
    }
}
