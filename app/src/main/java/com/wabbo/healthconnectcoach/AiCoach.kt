package com.wabbo.healthconnectcoach

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.coroutineContext

internal data class AiConfiguration(
    val geminiKey: String = "", val geminiModel: String = "gemini-flash-latest",
    val routerKey: String = "", val routerModel: String = "openrouter/free",
    val shareHealthSummary: Boolean = false,
    val autoAnalysis: Boolean = true,
    val groqKey: String="",val groqModel: String="openai/gpt-oss-120b",
    val mistralKey: String="",val mistralModel: String="mistral-small-latest",
)
internal fun AiConfiguration.normalized(): AiConfiguration=copy(geminiKey=geminiKey.trim(),geminiModel=geminiModel.trim(),routerKey=routerKey.trim(),routerModel=routerModel.trim(),groqKey=groqKey.trim(),groqModel=groqModel.trim(),mistralKey=mistralKey.trim(),mistralModel=mistralModel.trim())
internal fun AiConfiguration.validated(): AiConfiguration=normalized().also {c->
    require(listOf(c.geminiKey,c.routerKey,c.groqKey,c.mistralKey).all {key->key.none {it.isWhitespace()||it.isISOControl()}}) {"Ein API-Key enthält Leerzeichen oder Steuerzeichen. Bitte erneut einfügen."}
    require(c.groqKey.isBlank()||c.groqModel.matches(Regex("[A-Za-z0-9._:/-]{1,160}"))) {"Bitte eine gültige Groq-Modell-ID eingeben."}
    require(c.mistralKey.isBlank()||c.mistralModel.matches(Regex("[A-Za-z0-9._:/-]{1,160}"))) {"Bitte eine gültige Mistral-Modell-ID eingeben."}
    require(c.geminiKey.isBlank()||c.geminiModel.matches(Regex("[A-Za-z0-9._-]{1,160}"))) {"Bitte eine gültige Gemini-Modell-ID eingeben."}
    require(c.routerKey.isBlank()||c.routerModel.matches(Regex("[A-Za-z0-9._:/-]{1,160}"))) {"Bitte eine gültige OpenRouter-Modell-ID eingeben."}
}
internal fun decodeAiConfiguration(text: String): AiConfiguration {
    val o=JSONObject(text)
    return AiConfiguration(o.optString("geminiKey"),o.optString("geminiModel","gemini-flash-latest"),o.optString("routerKey"),o.optString("routerModel","openrouter/free"),o.optBoolean("share",false),o.optBoolean("autoAnalysis",true),o.optString("groqKey"),o.optString("groqModel","openai/gpt-oss-120b"),o.optString("mistralKey"),o.optString("mistralModel","mistral-small-latest"))
}
internal fun encodeAiConfiguration(configuration: AiConfiguration): String {
    val c=configuration.validated()
    return JSONObject().put("geminiKey",c.geminiKey).put("geminiModel",c.geminiModel).put("routerKey",c.routerKey).put("routerModel",c.routerModel)
        .put("groqKey",c.groqKey).put("groqModel",c.groqModel).put("mistralKey",c.mistralKey).put("mistralModel",c.mistralModel)
        .put("share",c.shareHealthSummary).put("autoAnalysis",c.autoAnalysis).toString()
}
internal class AiConfigurationStore(private val document: SecureLocalDocument) {
    fun read()=document.read()?.let(::decodeAiConfiguration) ?: AiConfiguration()
    fun save(configuration: AiConfiguration)=document.write(encodeAiConfiguration(configuration))
}
internal data class CoachMessage(val role: String, val text: String, val planChanges: List<String> = emptyList(),val planProposal: TrainingPlan? = null,val provider: String?=null,val mentions: List<String> = emptyList())
internal fun healthSummary(day: DayInsight?): String = if (day == null) "Keine Gesundheitsdaten verfügbar." else buildString {
    append("Datum: ${day.date}; berechnet: ${day.calculatedAt}; historisch rekonstruiert: ${day.reconstructed}.\n")
    day.scores.forEach { (id, s) -> append("$id: ${s.score ?: "fehlt"}, Status ${s.status}, Berechnungszeit ${s.asOf}, Bestandteile ${s.components}, relativer Belastungsindex ${s.rawIndex}, fehlend ${s.coverage.missingReasons}.\n") }
    append("Messwerte (Minuten, Schritte, HRV in ms, Ruhepuls in bpm): ${day.metrics}")
}

/** Fixed HTTPS origins; credentials never appear in URLs, logs or provider error messages. */
internal suspend fun askCoach(config: AiConfiguration, gemini: Boolean, conversation: List<CoachMessage>, day: DayInsight?, coachName: String = "Dein Coach", userName: String = "", provider: CoachProvider=if(gemini) CoachProvider.GEMINI else CoachProvider.OPEN_ROUTER,plan: TrainingPlan?=null,journalContext: String?=null,memoryContext:String?=null,nightContext:String?=null): String = withContext(Dispatchers.IO) {
    val nativeGemini=provider==CoachProvider.GEMINI
    val key=config.keyFor(provider).trim()
    val model=config.modelFor(provider).trim()
    require(key.isNotBlank()) { "Bitte hinterlege zuerst deinen API-Key in den Einstellungen." }
    require(key.none { it.isWhitespace() || it.isISOControl() }) { "Der API-Key enthält ungültige Leerzeichen. Bitte erneut einfügen." }
    require(model.matches(Regex(if (nativeGemini) "[A-Za-z0-9._-]{1,160}" else "[A-Za-z0-9._:/-]{1,160}"))) { "Bitte prüfe die Modell-ID." }
    require(conversation.isNotEmpty()) { "Bitte stelle eine Frage." }
    val system=coachSystemPrompt(coachName,userName,day,config.shareHealthSummary,plan=plan,journalContext=journalContext,memoryContext=memoryContext,nightContext=nightContext)
    val request=buildCoachTextRequest(system,conversation,model,nativeGemini)
    val endpoint = if (nativeGemini) "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent" else provider.endpoint
    val connection = URL(endpoint).openConnection() as HttpsURLConnection
    try {
        coroutineContext.ensureActive()
        connection.requestMethod = "POST"
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 15000
        connection.readTimeout = 45000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty(if (nativeGemini) "x-goog-api-key" else "Authorization", if (nativeGemini) key else "Bearer $key")
        connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        check(status in 200..299) { when (status) {
            401, 403 -> "API-Zugriff abgelehnt. Prüfe Key und Modellberechtigung."
            402 -> "Das gewählte Modell benötigt Guthaben. Wähle ein kostenloses Modell."
            404 -> "Modell nicht verfügbar. Bitte ändere die Modell-ID."
            429 -> "Anfragelimit erreicht. Bitte später erneut versuchen."
            else -> "Der KI-Dienst antwortet mit HTTP $status. Bitte erneut versuchen."
        } }
        val bytes = connection.inputStream.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (output.size() <= 262144) {
                coroutineContext.ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        check(bytes.size <= 262144) { "Die Antwort ist zu groß. Bitte stelle eine kürzere Frage." }
        coroutineContext.ensureActive()
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        val answer = if (nativeGemini) {
            val parts = json.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            if (parts == null) "" else (0 until parts.length()).mapNotNull { index -> parts.optJSONObject(index)?.takeUnless { it.optBoolean("thought", false) }?.optString("text") }.joinToString("\n")
        } else json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
        check(answer.isNotBlank()) { "Der Dienst hat keine Textantwort geliefert. Bitte formuliere deine Frage anders." }
        answer.take(16000)
    } finally { connection.disconnect() }
}

/** Automatic summaries use the same ordered providers, but never request plan tools. */
internal suspend fun askConfiguredTextCoach(config: AiConfiguration,conversation: List<CoachMessage>,day: DayInsight?,robot: String,plan: TrainingPlan?=null,journalContext: String?=null,memoryContext:String?=null,nightContext:String?=null): String {
    val result=coachWithFallback(config.normalized()) {provider->PlanCoachReply(askCoach(config,provider==CoachProvider.GEMINI,conversation,day,robot,provider=provider,plan=plan,journalContext=journalContext,memoryContext=memoryContext,nightContext=nightContext))}
    return (if(result.usedFallback) "_${result.provider} übernimmt diese Analyse._\n\n" else "")+result.text
}


internal fun buildCoachTextRequest(system: String,conversation: List<CoachMessage>,model: String,nativeGemini: Boolean): JSONObject {
    val systemWithRecap=coachSystemWithRecap(system,conversation)
    val messages=conversation.takeLast(16).map {it.copy(text=it.text.take(8000))}
    return if(nativeGemini) JSONObject()
        .put("systemInstruction",JSONObject().put("parts",JSONArray().put(JSONObject().put("text",systemWithRecap))))
        .put("contents",JSONArray().apply {messages.forEach {put(JSONObject().put("role",if(it.role=="assistant") "model" else "user").put("parts",JSONArray().put(JSONObject().put("text",it.text))))}})
        .put("generationConfig",JSONObject().put("maxOutputTokens",1600))
    else JSONObject().put("model",model).put("max_tokens",1600).put("messages",JSONArray().put(JSONObject().put("role","system").put("content",systemWithRecap)).apply {
        messages.forEach {put(JSONObject().put("role",it.role).put("content",it.text))}
    })
}
