package com.wabbo.healthconnectcoach

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.*

class CoachSystemPromptTest {
    private val day=DayInsight(LocalDate.of(2026,10,25),Instant.parse("2026-10-25T18:00:00Z"),false,emptyMap(),metrics=mapOf("hrv" to 314159.0))
    @Test fun sharedPromptIsSentAsSystemOnEveryPlanProviderIncludingFallback()=runBlocking {
        val seen=mutableListOf<CoachProvider>()
        askPlanCoach(AiConfiguration(groqKey="g",mistralKey="m",routerKey="r",geminiKey="a"),listOf(CoachMessage("user","Hallo")),day,null,"Pip","Noah") {p,_,body->
            seen+=p
            val first=body.getJSONArray("messages").getJSONObject(0)
            assertEquals("system",first.getString("role"));assertTrue(first.getString("content").startsWith(COACH_SYSTEM_PROMPT))
            assertFalse(first.getString("content").contains("314159.0"))
            if(p!=CoachProvider.GEMINI) throw IOException("unavailable")
            JSONObject().put("choices",JSONArray().put(JSONObject().put("message",JSONObject().put("content","Hallo"))))
        }
        assertEquals(CoachProvider.entries,seen)
    }
    @Test fun nativeAndOpenAiTextRequestsBothKeepSystemInstructionsAndConversationRoles() {
        val prompt=coachSystemPrompt("Pip","Noah",day,true,LocalDate.of(2026,10,26))
        val messages=listOf(CoachMessage("user","Frage"),CoachMessage("assistant","Antwort"))
        val gemini=buildCoachTextRequest(prompt,messages,"gemini-text",true)
        assertEquals(coachSystemWithRecap(prompt,messages),gemini.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text"))
        assertEquals("model",gemini.getJSONArray("contents").getJSONObject(1).getString("role"))
        val openAi=buildCoachTextRequest(prompt,messages,"model",false)
        assertEquals(coachSystemWithRecap(prompt,messages),openAi.getJSONArray("messages").getJSONObject(0).getString("content"))
        assertEquals("system",openAi.getJSONArray("messages").getJSONObject(0).getString("role"))
    }
    @Test fun historicalFactsRemainDatedAndHealthConsentExcludesThemEntirely() {
        val shared=coachSystemPrompt("Pip","Noah",day,true,LocalDate.of(2026,10,26))
        assertTrue(shared.contains("314159.0"));assertTrue(shared.contains("Datum: 2026-10-25"));assertTrue(shared.contains("\"heutigesDatum\":\"2026-10-26\""))
        val withheld=coachSystemPrompt("Pip","Noah",day,false)
        assertFalse(withheld.contains("314159.0"));assertFalse(withheld.contains("Datum: 2026-10-25"))
    }
}
