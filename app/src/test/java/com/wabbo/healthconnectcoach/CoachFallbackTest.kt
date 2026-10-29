package com.wabbo.healthconnectcoach

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class CoachFallbackTest {
    private val config=AiConfiguration(routerKey="router-test",geminiKey="gemini-test")
    private fun answer(text: String)=JSONObject().put("choices",JSONArray().put(JSONObject().put("message",JSONObject().put("content",text))))
    @Test fun successfulRouterNeverCallsBackup()=runBlocking {
        val seen=mutableListOf<CoachProvider>()
        val r=coachWithFallback(config) {seen+=it;PlanCoachReply("Hallo")}
        assertEquals(listOf(CoachProvider.OPEN_ROUTER),seen);assertEquals("OpenRouter",r.provider);assertFalse(r.usedFallback)
    }
    @Test fun errorUsesOneBackupAndKeepsSameToolsWithoutRouterOnlyFields()=runBlocking {
        val seen=mutableListOf<CoachProvider>()
        val r=askPlanCoach(config,listOf(CoachMessage("user","Hilf mir mit meinem Plan")),null,null,"Pip","Noah") {provider,key,request->
            seen+=provider
            assertTrue(request.has("tools"));assertEquals("auto",request.getString("tool_choice"))
            if(provider==CoachProvider.OPEN_ROUTER) {assertEquals("router-test",key);throw IOException("offline")}
            assertEquals("gemini-test",key);assertEquals(config.geminiModel,request.getString("model"));assertFalse(request.has("provider"));assertFalse(request.has("parallel_tool_calls"))
            assertFalse(request.toString().contains("router-test"));answer("Welche Tage passen?")
        }
        assertEquals(listOf(CoachProvider.OPEN_ROUTER,CoachProvider.GEMINI),seen)
        assertEquals("Gemini",r.provider);assertTrue(r.usedFallback);assertNull(r.updatedPlan)
    }
    @Test fun noGeminiKeyDoesNotTryAnotherProvider()=runBlocking {
        val seen=mutableListOf<CoachProvider>();val original=IOException("offline")
        try {coachWithFallback(config.copy(geminiKey="")) {seen+=it;throw original};fail("Expected failure")}
        catch(e:IOException) {assertSame(original,e)}
        assertEquals(listOf(CoachProvider.OPEN_ROUTER),seen)
    }
    @Test fun cancellationDoesNotTriggerBackup()=runBlocking {
        var calls=0
        try {coachWithFallback(config) {calls++;throw CancellationException("cancelled")};fail("Expected cancellation")}
        catch(_:CancellationException) {}
        assertEquals(1,calls)
    }
    @Test fun twoErrorsAreBoundedAndDoNotExposeProviderResponse()=runBlocking {
        var calls=0
        try {coachWithFallback(config) {calls++;throw IOException("secret-response")};fail("Expected failure")}
        catch(e:IllegalStateException) {assertFalse(e.message.orEmpty().contains("secret-response"));assertTrue(e.message.orEmpty().contains("Gemini"))}
        assertEquals(2,calls)
    }
    @Test fun geminiOnlyConfigurationWorksWithoutPretendingRouterFailed()=runBlocking {
        val r=coachWithFallback(config.copy(routerKey="")) {assertEquals(CoachProvider.GEMINI,it);PlanCoachReply("Hallo")}
        assertEquals("Gemini",r.provider);assertFalse(r.usedFallback)
    }
    @Test fun unusableRouterResponseTriggersBackup()=runBlocking {
        var calls=0
        val r=askPlanCoach(config,listOf(CoachMessage("user","Hallo")),null,null,"Pip","Noah") {provider,_,_->
            calls++;answer(if(provider==CoachProvider.OPEN_ROUTER) "" else "Hallo Noah")
        }
        assertEquals(2,calls);assertTrue(r.usedFallback);assertEquals("Hallo Noah",r.text)
    }
    @Test fun providerSurvivesConversationRoundTripAndOldMessagesStillLoad() {
        val m=CoachMessage("assistant","**Hallo**",provider="Gemini")
        assertEquals(listOf(m),decodeCoachMessages(encodeCoachMessages(listOf(m))))
        assertNull(decodeCoachMessages("[{\"role\":\"assistant\",\"text\":\"Alt\"}]").single().provider)
    }
}
