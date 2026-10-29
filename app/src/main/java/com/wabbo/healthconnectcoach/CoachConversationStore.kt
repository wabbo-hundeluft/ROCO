package com.wabbo.healthconnectcoach

import org.json.JSONArray
import org.json.JSONObject

internal fun decodeCoachMessages(raw: String): List<CoachMessage> {
    val array=JSONArray(raw);require(array.length()<=24)
    return (0 until array.length()).map {array.getJSONObject(it).let {row->
        val role=row.getString("role");require(role in setOf("user","assistant"))
        val changes=row.optJSONArray("changes") ?: JSONArray()
        val mentions=row.optJSONArray("mentions") ?: JSONArray()
        val proposal=row.optJSONObject("proposal")?.let(::decodePlan)
        require(proposal==null||role=="assistant")
        CoachMessage(role,row.getString("text").take(32_000),(0 until changes.length().coerceAtMost(4)).map {changes.getString(it)},proposal,row.optString("provider").takeIf {it in setOf("Groq","Mistral","OpenRouter","Gemini")},(0 until mentions.length().coerceAtMost(coachMentions.size)).map {mentions.getString(it)}.filter {id->coachMentions.any {it.id==id}}.distinct())
    }}
}
internal fun encodeCoachMessages(messages: List<CoachMessage>)=JSONArray().apply {messages.takeLast(24).forEach {m->
    put(JSONObject().put("role",m.role).put("text",m.text.take(32_000)).put("changes",JSONArray(m.planChanges)).put("mentions",JSONArray(m.mentions.filter {id->coachMentions.any {it.id==id}}.distinct()))
        .apply {m.planProposal?.let {put("proposal",encodePlan(it))};m.provider?.takeIf {it in setOf("Groq","Mistral","OpenRouter","Gemini")}?.let {put("provider",it)}})
}}.toString()
internal data class CoachConversationSnapshot(val messages:List<CoachMessage>,val previous:List<CoachMessage> = emptyList())
internal fun decodeCoachConversation(raw:String):CoachConversationSnapshot {
    if(raw.trimStart().startsWith("[")) return CoachConversationSnapshot(decodeCoachMessages(raw))
    val o=JSONObject(raw)
    require(o.getInt("version")==1)
    return CoachConversationSnapshot(decodeCoachMessages(o.getJSONArray("messages").toString()),decodeCoachMessages(o.optJSONArray("previous")?.toString() ?: "[]"))
}
internal fun encodeCoachConversation(snapshot:CoachConversationSnapshot):String=JSONObject().put("version",1)
    .put("messages",JSONArray(encodeCoachMessages(snapshot.messages))).put("previous",JSONArray(encodeCoachMessages(snapshot.previous))).toString()
internal class CoachConversationStore(private val document: SecureLocalDocument) {
    fun readSnapshot():CoachConversationSnapshot=document.read()?.let(::decodeCoachConversation) ?: CoachConversationSnapshot(emptyList())
    fun read(): List<CoachMessage> = readSnapshot().messages
    fun save(messages: List<CoachMessage>)=saveSnapshot(CoachConversationSnapshot(messages))
    fun saveSnapshot(snapshot:CoachConversationSnapshot)=document.write(encodeCoachConversation(snapshot))
}
