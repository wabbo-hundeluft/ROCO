package com.wabbo.healthconnectcoach

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.math.BigDecimal
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.coroutineContext

internal data class AiModelChoice(val id: String,val label: String,val verifiedFree: Boolean=false)
internal fun parseAiModels(provider: CoachProvider,json: JSONObject): List<AiModelChoice> {
    val rows=json.optJSONArray(if(provider==CoachProvider.GEMINI) "models" else "data") ?: return emptyList()
    return (0 until rows.length()).mapNotNull {i->
        val o=rows.optJSONObject(i) ?: return@mapNotNull null
        val id=if(provider==CoachProvider.GEMINI) o.optString("name").removePrefix("models/") else o.optString("id")
        if(!id.matches(Regex("[A-Za-z0-9._:/-]{1,160}"))) return@mapNotNull null
        val supported=when(provider) {
            CoachProvider.OPEN_ROUTER -> {
                val price=o.optJSONObject("pricing");val params=o.optJSONArray("supported_parameters")
                price!=null&&listOf("prompt","completion").all {k->runCatching {BigDecimal(price.getString(k)).compareTo(BigDecimal.ZERO)==0}.getOrDefault(false)}&&
                    price.keys().asSequence().all {k->runCatching {BigDecimal(price.getString(k)).compareTo(BigDecimal.ZERO)==0}.getOrDefault(false)}&&
                    params!=null&&(0 until params.length()).any {params.optString(it)=="tools"}
            }
            CoachProvider.MISTRAL -> o.optJSONObject("capabilities")?.let {it.optBoolean("completion_chat")&&it.optBoolean("function_calling")}==true
            CoachProvider.GROQ -> o.optBoolean("active",true)&&listOf("whisper","tts","orpheus","guard","embed").none {id.contains(it,true)}
            CoachProvider.GEMINI -> {
                val methods=o.optJSONArray("supportedGenerationMethods")
                id.startsWith("gemini-")&&listOf("image","tts","audio","live","embedding").none {id.contains(it,true)}&&methods!=null&&
                    (0 until methods.length()).any {methods.optString(it)=="generateContent"}
            }
        }
        if(!supported) null else AiModelChoice(id,o.optString(if(provider==CoachProvider.GEMINI) "displayName" else "name").ifBlank {id},provider==CoachProvider.OPEN_ROUTER)
    }.distinctBy {it.id}.sortedBy {it.id}.take(200)
}

/** Only catalogue metadata is requested, never chat or health data. No credentials in URLs. */
internal suspend fun loadAiModels(provider: CoachProvider,key: String): List<AiModelChoice> = withContext(Dispatchers.IO) {
    require(key.isNotBlank()&&key.none {it.isWhitespace()||it.isISOControl()}) {"Bitte zuerst einen gültigen Key eintragen."}
    val base=when(provider) {
        CoachProvider.GROQ->"https://api.groq.com/openai/v1/models"
        CoachProvider.MISTRAL->"https://api.mistral.ai/v1/models"
        CoachProvider.OPEN_ROUTER->"https://openrouter.ai/api/v1/models"
        CoachProvider.GEMINI->"https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000"
    }
    var next:String?=null;val result=mutableListOf<AiModelChoice>()
    repeat(if(provider==CoachProvider.GEMINI) 3 else 1) {
        coroutineContext.ensureActive()
        val url=base+(next?.let {"&pageToken="+java.net.URLEncoder.encode(it,Charsets.UTF_8.name())} ?: "")
        val c=URL(url).openConnection() as HttpsURLConnection
        val json=try {
            c.instanceFollowRedirects=false;c.connectTimeout=15000;c.readTimeout=20000
            c.setRequestProperty(if(provider==CoachProvider.GEMINI) "x-goog-api-key" else "Authorization",if(provider==CoachProvider.GEMINI) key else "Bearer $key")
            check(c.responseCode in 200..299) {"${provider.label}: Modellliste nicht verfügbar (HTTP ${c.responseCode}). Key und Kontolimits prüfen."}
            val bytes=c.inputStream.use {stream->val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(4096)
                while(true) {coroutineContext.ensureActive();val n=stream.read(buffer);if(n<0) break;out.write(buffer,0,n);check(out.size()<=4*1024*1024) {"Modellliste ist zu groß."}};out.toByteArray()}
            JSONObject(bytes.toString(Charsets.UTF_8))
        } finally {c.disconnect()}
        result+=parseAiModels(provider,json)
        next=json.optString("nextPageToken").takeIf {it.isNotBlank()}
        if(provider!=CoachProvider.GEMINI||next==null) return@withContext result.distinctBy {it.id}.sortedBy {it.id}.take(200)
    }
    result.distinctBy {it.id}.sortedBy {it.id}.take(200)
}
