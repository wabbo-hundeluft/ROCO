package com.wabbo.healthconnectcoach

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class AiProvidersAndCatalogTest {
    private val config=AiConfiguration(groqKey="g",mistralKey="m",routerKey="r",geminiKey="a")
    @Test fun configuredChainStartsWithGroqAndAttemptsEachProviderOnce()=runBlocking {
        val seen=mutableListOf<CoachProvider>()
        val result=coachWithFallback(config) {p->seen+=p;if(p!=CoachProvider.GEMINI) throw IOException("down");PlanCoachReply("Hallo")}
        assertEquals(CoachProvider.entries,seen);assertEquals("Gemini",result.provider);assertTrue(result.usedFallback)
    }
    @Test fun firstSuccessStopsChainAndMissingKeysAreSkipped()=runBlocking {
        val seen=mutableListOf<CoachProvider>()
        val result=coachWithFallback(config.copy(groqKey="",routerKey="")) {seen+=it;PlanCoachReply("Hallo")}
        assertEquals(listOf(CoachProvider.MISTRAL),seen);assertFalse(result.usedFallback);assertEquals("Mistral",result.provider)
    }
    @Test fun cancellationNeverAdvancesToAnotherConfiguredKey()=runBlocking {
        var calls=0
        try {coachWithFallback(config) {calls++;throw CancellationException()};fail("Expected cancellation")} catch(_:CancellationException) {}
        assertEquals(1,calls)
    }
    @Test fun groqAndMistralDoNotReceiveOpenRouterRoutingFields()=runBlocking {
        val seen=mutableListOf<CoachProvider>()
        val r=askPlanCoach(config,listOf(CoachMessage("user","Hallo")),null,null,"Pip","Noah") {p,key,body->
            seen+=p;assertFalse(body.has("provider"));assertEquals(config.keyFor(p),key);assertEquals(config.modelFor(p),body.getString("model"))
            if(p==CoachProvider.GROQ) throw IOException("down")
            JSONObject().put("choices",JSONArray().put(JSONObject().put("message",JSONObject().put("content","Hallo"))))
        }
        assertEquals(listOf(CoachProvider.GROQ,CoachProvider.MISTRAL),seen);assertEquals("Mistral",r.provider)
    }
    @Test fun openRouterListRequiresAllZeroPricesAndTools() {
        fun model(id:String,prompt:String,completion:String,tools:Boolean=true,request:String="0")=JSONObject().put("id",id).put("pricing",JSONObject().put("prompt",prompt).put("completion",completion).put("request",request)).put("supported_parameters",JSONArray(if(tools) listOf("tools") else emptyList<String>()))
        val data=JSONArray().put(model("free","0","0")).put(model("paid","0.01","0")).put(model("perrequest","0","0",request="0.1")).put(model("no-tools","0","0",false)).put(model("unknown","null","0"))
        assertEquals(listOf(AiModelChoice("free","free",true)),parseAiModels(CoachProvider.OPEN_ROUTER,JSONObject().put("data",data)))
    }
    @Test fun availableIsNotLabeledFreeAndNonChatModelsAreExcluded() {
        val data=JSONArray().put(JSONObject().put("id","openai/gpt-oss-120b").put("active",true)).put(JSONObject().put("id","whisper-large-v3")).put(JSONObject().put("id","old-chat").put("active",false))
        val models=parseAiModels(CoachProvider.GROQ,JSONObject().put("data",data))
        assertEquals(1,models.size);assertFalse(models.single().verifiedFree)
        val m=JSONObject().put("id","mistral-small-latest").put("capabilities",JSONObject().put("completion_chat",true).put("function_calling",true))
        assertEquals("mistral-small-latest",parseAiModels(CoachProvider.MISTRAL,JSONObject().put("data",JSONArray().put(m))).single().id)
    }
    @Test fun geminiListUsesTextGenerationAndRemovesResourcePrefix() {
        val data=JSONArray().put(JSONObject().put("name","models/gemini-text").put("supportedGenerationMethods",JSONArray(listOf("generateContent")))).put(JSONObject().put("name","models/gemini-tts").put("supportedGenerationMethods",JSONArray(listOf("generateContent"))))
        val result=parseAiModels(CoachProvider.GEMINI,JSONObject().put("models",data));assertEquals("gemini-text",result.single().id);assertFalse(result.single().verifiedFree)
    }
    @Test fun newProviderInputsNormalizeWithoutChangingExistingDefaults() {
        val c=AiConfiguration(groqKey=" g\n",mistralKey=" m ",groqModel=" openai/gpt-oss-120b ").validated()
        assertEquals("g",c.groqKey);assertEquals("m",c.mistralKey);assertEquals("openai/gpt-oss-120b",c.groqModel)
        assertThrows(IllegalArgumentException::class.java) {c.copy(mistralKey="bad\nkey").validated()}
        val m=CoachMessage("assistant","Hallo",provider="Groq");assertEquals(m,decodeCoachMessages(encodeCoachMessages(listOf(m))).single())
    }
    @Test fun configurationRoundTripPreservesAllProvidersAndMigratesOldSettings() {
        assertEquals(config,decodeAiConfiguration(encodeAiConfiguration(config)))
        val old=decodeAiConfiguration("{\"routerKey\":\"old\",\"share\":true}")
        assertEquals("old",old.routerKey);assertTrue(old.groqKey.isEmpty());assertTrue(old.mistralKey.isEmpty());assertTrue(old.shareHealthSummary)
        val next=old.copy(groqKey="new");assertEquals("old",decodeAiConfiguration(encodeAiConfiguration(next)).routerKey)
    }

}
