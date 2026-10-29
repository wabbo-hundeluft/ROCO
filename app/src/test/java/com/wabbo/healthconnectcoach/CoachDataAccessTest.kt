package com.wabbo.healthconnectcoach

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class CoachDataAccessTest {
    private val today=LocalDate.now()
    private val days=(6L downTo 0L).map {n->val d=today.minusDays(n);DayInsight(d,d.atTime(12,0).toInstant(ZoneOffset.UTC),false,emptyMap(),metrics=mapOf("hrv" to 41.0+n),sources=mapOf("_connector" to "Google Health","hrv" to "watch"))}
    private val data=CoachDataAccess(days,WellnessJournal(),null,today)
    private fun args(section:String="daily",page:Int=0)=JSONObject().put("section",section).put("from_date",today.minusDays(6).toString()).put("to_date",today.toString()).put("page",page).put("habit_id","")
    @Test fun allSavedDaysAreAvailableViaPaginationWithoutInventingMissingDays()=runBlocking {
        val first=readCoachAppData(data,args());val second=readCoachAppData(data,args(page=1));val last=readCoachAppData(data,args(page=2))
        assertEquals(7,first.getInt("totalItems"));assertEquals(1,first.getInt("next_page"));assertEquals(7,first.getJSONArray("items").length()+second.getJSONArray("items").length()+last.getJSONArray("items").length());assertTrue(last.isNull("next_page"))
    }
    @Test fun unknownSectionsAndUnboundedDatesAreRejected()=runBlocking {
        try {readCoachAppData(data,args("credentials"));fail("Unknown section")} catch(_:IllegalStateException) {}
        try {readCoachAppData(data,args().put("to_date",today.plusDays(1).toString()));fail("Future health data")} catch(_:IllegalArgumentException) {}
    }
    @Test fun questionLibraryIncludesCustomAndInactiveQuestions()=runBlocking {
        val custom=JournalHabit("custom","Eigene Frage","Hast du gelesen?","Eigene",custom=true)
        val reply=readCoachAppData(data.copy(journal=data.journal.copy(habits=data.journal.habits+custom)),args("journal_questions"))
        assertTrue(reply.toString().contains("Hast du gelesen?"));assertTrue(reply.toString().contains("late_meal"))
    }
    @Test fun coachReadsLocalDataThenAnswersWithTheToolResultInContext()=runBlocking {
        var requests=0
        val reply=askPlanCoach(AiConfiguration(groqKey="test",shareHealthSummary=true),listOf(CoachMessage("user","Wie war meine HRV?")),days.last(),null,"Pip","Noah",data=data) {_,_,body->
            requests++
            if(requests==1) JSONObject().put("choices",JSONArray().put(JSONObject().put("message",JSONObject().put("tool_calls",JSONArray().put(JSONObject().put("id","read1").put("type","function").put("function",JSONObject().put("name","read_app_data").put("arguments",args().put("from_date",today.toString()).toString())))))))
            else {
                val messages=body.getJSONArray("messages");val tool=messages.getJSONObject(messages.length()-1)
                assertEquals("tool",tool.getString("role"));assertTrue(tool.getString("content").contains("hrv"));assertEquals(41.0,JSONObject(tool.getString("content")).getJSONArray("items").getJSONObject(0).getJSONObject("metrics").getDouble("hrv"),.001)
                JSONObject().put("choices",JSONArray().put(JSONObject().put("message",JSONObject().put("content","Deine gespeicherten Werte sind datiert."))))
            }
        }
        assertEquals(2,requests);assertNull(reply.updatedPlan)
    }
    @Test fun withholdingHealthConsentRemovesReadToolAndRejectsForcedReadCall()=runBlocking {
        try {
            askPlanCoach(AiConfiguration(groqKey="test",shareHealthSummary=false),listOf(CoachMessage("user","Hallo")),days.last(),null,"Pip","Noah",data=data) {_,_,body->
                assertFalse(body.getJSONArray("tools").toString().contains("read_app_data"))
                assertFalse(body.getJSONArray("messages").getJSONObject(0).getString("content").contains("41.0"))
                JSONObject().put("choices",JSONArray().put(JSONObject().put("message",JSONObject().put("tool_calls",JSONArray().put(JSONObject().put("id","read1").put("function",JSONObject().put("name","read_app_data").put("arguments",args().toString())))))))
            }
            fail("Must reject data access")
        } catch(_:IllegalArgumentException) {}
    }
    @Test fun endlessModelReadRequestsAreBounded()=runBlocking {
        var requests=0
        try {
            askPlanCoach(AiConfiguration(groqKey="test",shareHealthSummary=true),listOf(CoachMessage("user","Lies weiter")),days.last(),null,"Pip","Noah",data=data) {_,_,_->
                requests++;JSONObject().put("choices",JSONArray().put(JSONObject().put("message",JSONObject().put("tool_calls",JSONArray().put(JSONObject().put("id","read$requests").put("function",JSONObject().put("name","read_app_data").put("arguments",args().toString())))))))
            };fail("Bounded read loop")
        } catch(_:IllegalStateException) {}
        assertEquals(5,requests)
    }
}
