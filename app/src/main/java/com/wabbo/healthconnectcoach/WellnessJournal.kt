package com.wabbo.healthconnectcoach

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

internal enum class JournalQuestionKind { BOOLEAN, NUMBER }
internal data class JournalHabit(val id: String,val title: String,val question: String,val category: String,
    val kind: JournalQuestionKind=JournalQuestionKind.BOOLEAN,val unit: String="",val custom: Boolean=false)
internal data class JournalAnswer(val yes: Boolean?=null,val amount: Double?=null,val skipped: Boolean=false) {
    fun validate(habit: JournalHabit): JournalAnswer {
        require(if(skipped) yes==null&&amount==null else if(habit.kind==JournalQuestionKind.NUMBER) amount!=null&&amount.isFinite()&&amount in 0.0..10000.0&&yes==(amount>0) else yes!=null&&amount==null) {"Bitte eine gültige Antwort eingeben oder bewusst überspringen."}
        return this
    }
}
internal data class JournalDay(val date: LocalDate,val answers: Map<String,JournalAnswer>,val completedAt: Instant?=null,val questionIds: Set<String> = emptySet())
internal data class WellnessJournal(val revision: Long=0,val habits: List<JournalHabit> = journalHabitLibrary,
    val activeIds: List<String> = listOf("late_caffeine","late_meal","bedtime_screen","relaxation","high_stress","outdoor_time"),
    val entries: List<JournalDay> = emptyList(),val celebrated: Set<String> = emptySet()) {
    fun validated(): WellnessJournal {
        require(revision>=0&&habits.size<=80&&habits.map {it.id}.distinct().size==habits.size)
        require(habits.all {it.id.matches(Regex("[a-zA-Z0-9_-]{1,64}"))&&it.title.trim().length in 1..60&&it.question.trim().length in 1..180&&it.category.length in 1..40&&it.unit.length<=24})
        require(activeIds.size in 1..10&&activeIds.distinct().size==activeIds.size&&activeIds.all {id->habits.any {it.id==id}}) {"Wähle eine bis zehn Fragen für deinen Check-in."}
        require(entries.size<=730&&entries.map {it.date}.distinct().size==entries.size)
        entries.forEach {entry->
            require(entry.answers.size<=80)
            entry.answers.forEach {(id,answer)->answer.validate(habits.single {it.id==id})}
            if(entry.completedAt!=null) require(entry.questionIds.isNotEmpty()&&entry.questionIds.all {it in entry.answers}&&entry.questionIds.any {entry.answers[it]?.skipped==false})
        }
        return this
    }
}
internal val journalHabitLibrary=listOf(
    JournalHabit("late_caffeine","Koffein am späten Tag","Hast du nach 16 Uhr Koffein getrunken?","Ernährung"),
    JournalHabit("late_meal","Spät gegessen","Hast du innerhalb von zwei Stunden vor dem Schlafen eine große Mahlzeit gegessen?","Ernährung"),
    JournalHabit("bedtime_screen","Bildschirm vor dem Schlafen","Hast du in der letzten Stunde vor dem Schlafen länger auf einen Bildschirm geschaut?","Schlaf"),
    JournalHabit("relaxation","Bewusst entspannt","Hast du dir bewusst Zeit zum Entspannen genommen?","Erholung"),
    JournalHabit("high_stress","Anstrengender Alltag","Hast du dich an diesem Tag stark gestresst gefühlt?","Wohlbefinden"),
    JournalHabit("outdoor_time","Zeit draußen","Warst du bei Tageslicht mindestens 20 Minuten draußen?","Alltag"),
    JournalHabit("alcohol","Alkohol","Hast du Alkohol getrunken?","Ernährung"),
    JournalHabit("caffeine_servings","Koffeinhaltige Getränke","Wie viele koffeinhaltige Getränke hast du getrunken?","Ernährung",JournalQuestionKind.NUMBER,"Getränke"),
    JournalHabit("water_glasses","Wasser getrunken","Wie viele Gläser Wasser hast du getrunken?","Ernährung",JournalQuestionKind.NUMBER,"Gläser"),
    JournalHabit("balanced_meals","Regelmäßige Mahlzeiten","Hast du regelmäßig und in Ruhe gegessen?","Ernährung"),
    JournalHabit("morning_light","Licht am Morgen","Warst du in der ersten Stunde nach dem Aufstehen draußen?","Schlaf"),
    JournalHabit("cool_bedroom","Kühles Schlafzimmer","Hast du in einem für dich angenehm kühlen Zimmer geschlafen?","Schlaf"),
    JournalHabit("dark_bedroom","Dunkles Schlafzimmer","War dein Schlafzimmer während der Nacht gut abgedunkelt?","Schlaf"),
    JournalHabit("sleep_mask","Schlafmaske","Hast du eine Schlafmaske getragen?","Schlaf"),
    JournalHabit("regular_bedtime","Gewohnte Schlafenszeit","Bist du ungefähr zu deiner üblichen Zeit ins Bett gegangen?","Schlaf"),
    JournalHabit("late_training","Spät trainiert","Hast du in den letzten zwei Stunden vor dem Schlafen trainiert?","Training"),
    JournalHabit("mobility","Beweglichkeit","Hast du eine leichte Beweglichkeits- oder Dehneinheit gemacht?","Training"),
    JournalHabit("walking","Spaziergang","Hast du bewusst einen lockeren Spaziergang gemacht?","Training"),
    JournalHabit("meditation","Meditation","Hast du meditiert oder eine Achtsamkeitsübung gemacht?","Erholung"),
    JournalHabit("breathing","Atemübung","Hast du eine ruhige Atemübung gemacht?","Erholung"),
    JournalHabit("sauna","Sauna","Warst du in der Sauna?","Erholung"),
    JournalHabit("nap","Nickerchen","Hast du tagsüber ein Nickerchen gemacht?","Erholung"),
    JournalHabit("feeling_ill","Angeschlagen gefühlt","Hast du dich krank oder angeschlagen gefühlt?","Wohlbefinden"),
    JournalHabit("pain","Schmerzen","Hattest du Schmerzen, die deinen Alltag beeinflusst haben?","Wohlbefinden"),
    JournalHabit("positive_mood","Gute Stimmung","Hast du dich an diesem Tag überwiegend gut gefühlt?","Wohlbefinden"),
    JournalHabit("social_time","Gute Gesellschaft","Hattest du wohltuende Zeit mit anderen Menschen?","Alltag"),
    JournalHabit("travel","Unterwegs gewesen","War dein Alltag durch Reisen deutlich anders als sonst?","Alltag"),
    JournalHabit("late_work","Lange gearbeitet","Hast du bis kurz vor dem Schlafen gearbeitet oder gelernt?","Alltag"),
    JournalHabit("reading","Lesen am Abend","Hast du vor dem Schlafen entspannt gelesen?","Schlaf"),
    JournalHabit("journal_writing","Gedanken aufgeschrieben","Hast du dir Zeit genommen, Gedanken oder Gefühle aufzuschreiben?","Wohlbefinden"),
)
internal fun WellnessJournal.withEntry(date: LocalDate,answers: Map<String,JournalAnswer>,complete: Boolean,today: LocalDate,now: Instant): WellnessJournal {
    require(date in today.minusDays(729)..today) {"Du kannst heutige und vergangene Tage eintragen."}
    val existing=entries.singleOrNull {it.date==date}
    val merged=(existing?.answers.orEmpty()-activeIds.toSet())+answers
    activeIds.forEach {id->merged[id]?.validate(habits.single {it.id==id})}
    if(complete) require(activeIds.all {it in merged}&&activeIds.any {merged[it]?.skipped==false}) {"Beantworte alle Fragen oder überspringe einzelne bewusst. Mindestens eine echte Antwort zählt zum Check-in."}
    val entry=JournalDay(date,merged,if(complete) existing?.completedAt ?: now else null,if(complete) activeIds.toSet() else emptySet())
    return copy(revision=revision+1,entries=(entries.filterNot {it.date==date}+entry).sortedBy {it.date}.takeLast(730)).validated()
}
internal fun journalStreak(journal: WellnessJournal,today: LocalDate): Int {
    val done=journal.entries.filter {it.completedAt!=null&&it.date<=today}.map {it.date}.toSet()
    var date=if(today in done) today else today.minusDays(1);var count=0
    while(date in done) {count++;date=date.minusDays(1)}
    return count
}
internal fun journalPromptDate(journal: WellnessJournal,today: LocalDate,time: LocalTime): LocalDate? {
    if(journal.entries.any {it.date==today&&it.completedAt!=null}) return null
    val date=if(time.hour<18) today.minusDays(1) else today
    return date.takeUnless {d->journal.entries.any {it.date==d&&it.completedAt!=null}}
}
internal class WellnessJournalStore(private val document: SecureLocalDocument) {
    fun read(): WellnessJournal=document.read()?.let(::decodeWellnessJournal) ?: WellnessJournal()
    fun save(journal: WellnessJournal)=document.write(encodeWellnessJournal(journal.validated()))
}
internal fun encodeWellnessJournal(journal: WellnessJournal): String=JSONObject().put("schema",1).put("revision",journal.revision)
    .put("habits",JSONArray().apply {journal.habits.forEach {h->put(JSONObject().put("id",h.id).put("title",h.title).put("question",h.question).put("category",h.category).put("kind",h.kind.name).put("unit",h.unit).put("custom",h.custom))}})
    .put("active",JSONArray(journal.activeIds)).put("celebrated",JSONArray(journal.celebrated.sorted()))
    .put("entries",JSONArray().apply {journal.entries.forEach {d->put(JSONObject().put("date",d.date.toString()).put("completedAt",d.completedAt?.toString()).put("questions",JSONArray(d.questionIds.sorted())).put("answers",JSONObject().apply {
        d.answers.forEach {(id,a)->put(id,JSONObject().put("yes",a.yes).put("amount",a.amount).put("skipped",a.skipped))}
    }))}}).toString()
internal fun decodeWellnessJournal(text: String): WellnessJournal {
    require(text.length<=2_000_000)
    val o=JSONObject(text);require(o.getInt("schema")==1)
    fun strings(a: JSONArray)=(0 until a.length()).map {a.getString(it)}
    val habits=o.getJSONArray("habits");require(habits.length()<=80)
    val entries=o.getJSONArray("entries");require(entries.length()<=730)
    return WellnessJournal(o.getLong("revision"),(0 until habits.length()).map {habits.getJSONObject(it).let {h->JournalHabit(h.getString("id"),h.getString("title"),h.getString("question"),h.getString("category"),JournalQuestionKind.valueOf(h.getString("kind")),h.optString("unit"),h.optBoolean("custom"))}},
        strings(o.getJSONArray("active")),(0 until entries.length()).map {entries.getJSONObject(it).let {d->
            val answers=d.getJSONObject("answers")
            JournalDay(LocalDate.parse(d.getString("date")),answers.keys().asSequence().associateWith {id->answers.getJSONObject(id).let {a->JournalAnswer(if(a.has("yes")&&!a.isNull("yes")) a.getBoolean("yes") else null,if(a.has("amount")&&!a.isNull("amount")) a.getDouble("amount") else null,a.optBoolean("skipped"))}},d.optString("completedAt").takeIf {it.isNotBlank()}?.let(Instant::parse),strings(d.getJSONArray("questions")).toSet())
        }},strings(o.getJSONArray("celebrated")).toSet()).validated()
}

internal data class JournalSaveTransition(val journal: WellnessJournal,val celebrate: Boolean,val newCompletion: Boolean)
internal fun prepareJournalSave(before: WellnessJournal,next: WellnessJournal,today: LocalDate): JournalSaveTransition {
    require(next.revision==before.revision+1) {"Dein Logbuch hat sich inzwischen geändert. Öffne den Tag bitte neu."}
    next.validated()
    val completions=next.entries.filter {it.completedAt!=null&&before.entries.none {old->old.date==it.date&&old.completedAt!=null}}
    val date=completions.lastOrNull {it.date in today.minusDays(1)..today}?.date
    val celebrate=date!=null&&date.toString() !in before.celebrated&&journalStreak(next,today)>journalStreak(before,today)
    val retained=next.entries.map {it.date.toString()}.toSet()
    val marked=(next.celebrated+if(celebrate) setOf(date.toString()) else emptySet()).intersect(retained)
    return JournalSaveTransition(next.copy(celebrated=marked),celebrate,completions.isNotEmpty())
}


/** One transaction for selection and an optional custom draft, shared by both save buttons. */
internal fun prepareJournalLibraryUpdate(document: WellnessJournal,active: List<String>,customOpen: Boolean,
    title: String,question: String,numeric: Boolean,unit: String,id: String="custom_"+java.util.UUID.randomUUID().toString().replace("-","").take(16)): WellnessJournal {
    val hasDraft=customOpen&&listOf(title,question,unit).any {it.isNotBlank()}
    if(!hasDraft) return document.copy(revision=document.revision+1,activeIds=active).validated()
    require(question.isNotBlank()) {"Bitte ergänze deine tägliche Frage."}
    require(!numeric||unit.isNotBlank()) {"Bitte gib eine Einheit für die Mengenfrage an."}
    require(document.habits.size<80) {"Die Fragenbibliothek ist voll."}
    require(active.size<10) {"Entferne zuerst eine der zehn ausgewählten Fragen. Deine eigene Frage bleibt im Formular erhalten."}
    val habit=JournalHabit(id,title.trim().ifBlank {question.trim().take(60)},question.trim(),"Eigene",
        if(numeric) JournalQuestionKind.NUMBER else JournalQuestionKind.BOOLEAN,if(numeric) unit.trim() else "",true)
    return document.copy(revision=document.revision+1,habits=document.habits+habit,activeIds=active+id).validated()
}
