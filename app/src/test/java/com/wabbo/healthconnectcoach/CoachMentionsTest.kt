package com.wabbo.healthconnectcoach

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class CoachMentionsTest {
    private val date=LocalDate.now();private val at=date.atTime(12,0).toInstant(ZoneOffset.UTC)
    private val data=CoachDataAccess((0L..9L).map {n->DayInsight(date.minusDays(n),at.minusSeconds(n*86400),false,emptyMap(),metrics=mapOf("hrv" to 40.0+n,"rhr" to 60.0),sources=mapOf("_connector" to "Google Health","hrv" to "Pixel","rhr" to "Pixel"))},WellnessJournal(),null,date)
    @Test fun atPickerFindsAliasesAndIgnoresEmailAddresses() {
        assertNull(mentionQuery("name@example.org",16));assertEquals("Rhue",mentionQuery("Wie ist @Rhue",13)?.query)
        assertEquals("energy",matchingMentions("Akku").single().id)
        assertTrue(matchingMentions("").map {it.id}.containsAll(listOf("hrv","rhr","sleep","energy","load","spo2")))
    }
    @Test fun selectingAndRemovingAttachmentNeverLeavesHiddenPersonalData() {
        val text="Bitte @hr erklären";val q=mentionQuery(text,9)!!
        val inserted=insertMention(text,q,coachMentions.first())
        assertTrue(inserted.first.contains("@HRV "))
        assertEquals(listOf("hrv"),selectedMentionIds(inserted.first,listOf("hrv","sleep")))
        assertTrue(selectedMentionIds(inserted.first.replace("@HRV",""),listOf("hrv")).isEmpty())
    }
    @Test fun attachmentContainsDatedHistoryPersonalBaselineAndNoUnselectedMetrics() {
        val packet=JSONObject(coachMentionContext(data,listOf("hrv"),date)).getJSONArray("attachments").getJSONObject(0)
        assertEquals(10,packet.getJSONArray("dailyValues").length());assertEquals("ms",packet.getString("unit"));assertEquals("Pixel",packet.getString("origin"));assertTrue(packet.has("personalBaseline"))
        assertFalse(packet.toString().contains("rhr"))
        assertFalse(JSONObject(coachMentionContext(data,listOf("spo2"),date)).getJSONArray("attachments").getJSONObject(0).getBoolean("available"))
    }
    @Test fun historicalSelectionCannotSeeFutureMeasurements() {
        val packet=JSONObject(coachMentionContext(data,listOf("hrv"),date.minusDays(5))).getJSONArray("attachments").getJSONObject(0)
        assertEquals(date.minusDays(5).toString(),packet.getString("date"));assertEquals(5,packet.getJSONArray("dailyValues").length())
    }
    @Test fun mentionMetadataSurvivesChatStorageAndLegacyMessagesRemainReadable() {
        val m=CoachMessage("user","Erkläre @HRV",mentions=listOf("hrv","spo2"))
        assertEquals(listOf(m),decodeCoachMessages(encodeCoachMessages(listOf(m))))
        assertTrue(decodeCoachMessages("[{\"role\":\"user\",\"text\":\"Hallo\"}]").single().mentions.isEmpty())
    }
    @Test fun everyFallbackReceivesTheSameExplicitAttachment()=runBlocking {
        var calls=0
        val reply=askPlanCoach(AiConfiguration(groqKey="test",mistralKey="test",routerKey="test",geminiKey="test",shareHealthSummary=true),listOf(CoachMessage("user","Erkläre @HRV",mentions=listOf("hrv"))),data.days.first(),null,"Pip","Noah",data=data) {_,_,body->
            calls++;assertTrue(body.getJSONArray("messages").getJSONObject(0).getString("content").contains("EXPLIZITE DATENANHÄNGE"))
            if(calls<4) error("Synthetic offline")
            JSONObject().put("choices",JSONArray().put(JSONObject().put("message",JSONObject().put("content","Deine HRV ist datiert."))))
        }
        assertEquals(4,calls);assertEquals("Gemini",reply.provider)
    }
    @Test fun consentCannotBeBypassedByAMention()=runBlocking {
        try {askPlanCoach(AiConfiguration(groqKey="test",shareHealthSummary=false),listOf(CoachMessage("user","@HRV",mentions=listOf("hrv"))),null,null,"Pip","",data=data) {_,_,_->error("Must not call provider")};fail("No consent")} catch(_:IllegalArgumentException) {}
    }
}
