package com.wabbo.healthconnectcoach

import com.wabbo.healthconnectcoach.score.PersonalScoreProfile
import org.json.JSONObject

internal class PersonalScoreProfileStore(private val document: SecureLocalDocument) {
    fun read(): PersonalScoreProfile = document.read()?.let { decodePersonalScoreProfile(JSONObject(it)) } ?: PersonalScoreProfile()
    fun save(profile: PersonalScoreProfile) { document.write(encodePersonalScoreProfile(profile.validated()).toString()) }
}
internal fun encodePersonalScoreProfile(p: PersonalScoreProfile) = JSONObject()
    .put("birthYear", p.birthYear ?: JSONObject.NULL)
    .put("measuredMaxHeartRate", p.measuredMaxHeartRate ?: JSONObject.NULL)
    .put("pulseLimited", p.pulseLimited)
internal fun decodePersonalScoreProfile(o: JSONObject) = PersonalScoreProfile(
    if(o.isNull("birthYear")) null else o.getInt("birthYear"),
    if(o.isNull("measuredMaxHeartRate")) null else o.getInt("measuredMaxHeartRate"),
    o.optBoolean("pulseLimited", false))
