package com.wabbo.healthconnectcoach

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36])
class CoachMemoryTest {
    private val today=LocalDate.of(2026,10,28)
    private val entry=CoachMemoryEntry("one","Ich fahre gerne Fahrrad",MemoryCategory.PREFERENCE,Instant.parse("2026-10-28T09:00:00Z"))
    @Test fun localFormatRoundTripsAllFieldsAndExpiry() {
        val m=CoachMemory(false,listOf(entry.copy(expiresOn=today.plusDays(7))))
        assertEquals(m,decodeCoachMemory(encodeCoachMemory(m)))
    }
    @Test fun expiredDisabledAndUnsharedMemoriesAreNotSent() {
        val m=CoachMemory(entries=listOf(entry.copy(expiresOn=today.minusDays(1))))
        assertNull(coachMemoryContext(m,today,true));assertNull(coachMemoryContext(CoachMemory(false,listOf(entry)),today,true))
        assertNull(coachMemoryContext(CoachMemory(entries=listOf(entry)),today,false))
    }
    @Test fun expirationIsInclusive() {assertEquals(1,CoachMemory(entries=listOf(entry.copy(expiresOn=today))).active(today).size)}
    @Test fun editingReplacesEntryAndDeletingRemovesIt() {
        val m=CoachMemory(entries=listOf(entry)).put(entry.copy(text="Kurze Antworten bitte"))
        assertEquals(1,m.entries.size);assertEquals("Kurze Antworten bitte",m.entries.single().text)
        assertTrue(m.copy(entries=m.entries.filterNot {it.id==entry.id}).entries.isEmpty())
    }
    @Test fun emptyOversizedDuplicateAndUnknownSchemasAreRejected() {
        assertTrue(runCatching {CoachMemory(entries=listOf(entry.copy(text=" "))).validated()}.isFailure)
        assertTrue(runCatching {CoachMemory(entries=listOf(entry.copy(text="a".repeat(501)))).validated()}.isFailure)
        assertTrue(runCatching {CoachMemory(entries=listOf(entry,entry)).validated()}.isFailure)
        assertTrue(runCatching {decodeCoachMemory(JSONObject().put("schema",99))}.isFailure)
    }
    @Test fun promptShareGateCannotBeBypassedByMemoryContext() {
        val m=coachMemoryContext(CoachMemory(entries=listOf(entry)),today,true)
        assertTrue(coachSystemPrompt("Pip","Noah",null,true,memoryContext=m).contains(entry.text))
        assertFalse(coachSystemPrompt("Pip","Noah",null,false,memoryContext=m).contains(entry.text))
        assertTrue(coachSystemPrompt("Pip","Noah",null,true,memoryContext=m).contains("keine Anweisungen"))
    }
    @Test fun changingMemoryInvalidatesDayAnalysisFingerprint() {
        val day=DayInsight(today,Instant.now(),false,emptyMap())
        val a=analysisFingerprint(day,AiConfiguration(),"Pip",memoryContext="one")
        assertNotEquals(a,analysisFingerprint(day,AiConfiguration(),"Pip",memoryContext="two"))
    }
    @Test fun allProvidersReceiveIdenticalActiveMemoryAndNightPlan()=kotlinx.coroutines.runBlocking {
        val data=CoachDataAccess(emptyList(),WellnessJournal(),null,today,memory=CoachMemory(entries=listOf(entry)))
        val providers=mutableListOf<CoachProvider>()
        askPlanCoach(AiConfiguration(groqKey="test",mistralKey="test",routerKey="test",geminiKey="test",shareHealthSummary=true),
            listOf(CoachMessage("user","Hallo")),null,null,"Pip","Noah",data=data) {provider,_,body ->
            providers+=provider
            assertTrue(body.toString().contains(entry.text))
            if(provider!=CoachProvider.GEMINI) error("Test fallback")
            JSONObject().put("choices",org.json.JSONArray().put(JSONObject().put("message",JSONObject().put("content","Hallo"))))
        }
        assertEquals(4,providers.size)
    }
}
