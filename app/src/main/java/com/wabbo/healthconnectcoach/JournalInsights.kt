package com.wabbo.healthconnectcoach

import com.wabbo.healthconnectcoach.score.ApprovedWellnessScoreEngine as E
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.*
import kotlin.random.Random

internal const val JOURNAL_ANALYSIS_VERSION="journal-association-1"
internal enum class JournalOutcome(val title: String,val unit: String,val lowerIsBetter: Boolean=false) {
    MORNING_ENERGY("Erholung am Morgen","Pkt."), SLEEP("Schlafscore","Pkt."), HRV("HRV","%"), RESTING_PULSE("Ruhepuls","bpm",true), SLEEP_DURATION("Schlafdauer","min")
}
internal data class JournalComparison(val date: LocalDate,val yes: Boolean,val value: Double)
internal data class JournalInsight(val habit: JournalHabit,val outcome: JournalOutcome,val yesCount: Int,val noCount: Int,
    val yesMean: Double?=null,val noMean: Double?=null,val effect: Double?=null,val low: Double?=null,val high: Double?=null,
    val quality: String="learning",val source: String="",val controls: List<String> = emptyList(),val overlapping: List<String> = emptyList(),
    val comparisons: List<JournalComparison> = emptyList(),val stableShare: Double?=null,val threshold: Double?=null) {
    val helpful: Boolean? get()=effect?.let {if(outcome==JournalOutcome.SLEEP_DURATION) null else if(outcome.lowerIsBetter) it<0 else it>0}
}
private data class JournalMetric(val value: Double,val stream: String)
private fun journalMetric(day: DayInsight?,outcome: JournalOutcome,zone: ZoneId): JournalMetric? {
    if(day==null) return null
    fun score(id: String)=day.scores[id]?.takeIf {it.score!=null&&!it.stale&&it.asOf.atZone(zone).toLocalDate()==day.date&&it.algorithmVersion.startsWith(E.MODEL_VERSION+"/")}
    val pair=when(outcome) {
        JournalOutcome.MORNING_ENERGY->score(E.BODY_BATTERY_ID)?.let {s->s.components["START"]?.toDouble()?.let {it to (s.algorithmVersion+"/profile="+s.components["PROFILE_KEY"]+"/"+day.sources["sleep"].orEmpty())}}
        JournalOutcome.SLEEP->score(E.SLEEP_ID)?.let {it.score!!.toDouble() to (it.algorithmVersion+"/profile="+it.components["PROFILE_KEY"]+"/"+day.sources["sleep"].orEmpty())}
        JournalOutcome.HRV->day.metrics["hrv"]?.takeIf {it.isFinite()&&it>0}?.let {ln(it) to day.sources["hrv"].orEmpty()}
        JournalOutcome.RESTING_PULSE->day.metrics["rhr"]?.takeIf {it.isFinite()&&it>0}?.let {it to day.sources["rhr"].orEmpty()}
        JournalOutcome.SLEEP_DURATION->score(E.SLEEP_ID)?.let {day.metrics["sleep_minutes"]?.takeIf {it.isFinite()&&it>0}?.let {it to day.sources["sleep"].orEmpty()}}
    } ?: return null
    if(!pair.first.isFinite()) return null
    return JournalMetric(pair.first,day.sources["_connector"].orEmpty()+"/"+pair.second)
}
private data class JournalRow(val date: LocalDate,val yes: Boolean,val y: Double,val stream: String,val training: Double?,val prior: Double?,val answers: Map<String,JournalAnswer>,val amount: Double?=null)

/** Own exploratory N-of-1 model. Behaviors on D are paired only with outcomes on D+1.
 * Source-separated, ridge-adjusted, with week-block bootstrap uncertainty. No causality claim.
 */
internal fun journalInsights(journal: WellnessJournal,days: List<DayInsight>,today: LocalDate,
    outcomes: List<JournalOutcome> = JournalOutcome.entries,habitIds: Set<String> = journal.activeIds.toSet(),zone: ZoneId=ZoneId.systemDefault()): List<JournalInsight> {
    val byDay=days.associateBy {it.date}
    val entries=journal.entries.filter {it.completedAt!=null&&it.date in today.minusDays(90)..today.minusDays(1)}
    return journal.habits.filter {it.id in habitIds}.flatMap {habit->outcomes.map {outcome->
        val all=entries.mapNotNull {entry->
            val a=entry.answers[habit.id]?.takeUnless {it.skipped}?.yes ?: return@mapNotNull null
            val next=journalMetric(byDay[entry.date.plusDays(1)],outcome,zone) ?: return@mapNotNull null
            val priorDay=byDay[entry.date]
            val prior=journalMetric(priorDay,outcome,zone)?.takeIf {it.stream==next.stream}?.value
            val training=priorDay?.takeIf {it.sources["_connector"]==byDay[entry.date.plusDays(1)]?.sources?.get("_connector")}?.metrics?.get("workout_minutes")?.takeIf {it.isFinite()&&it>=0}
            JournalRow(entry.date,a,next.value,next.stream,training,prior,entry.answers,entry.answers[habit.id]?.amount)
        }
        // Never pool a provider or measurement-origin change into a behavioral effect.
        val chosen=all.groupBy {it.stream}.values.maxByOrNull {it.size}.orEmpty().sortedBy {it.date}
        val amounts=chosen.mapNotNull {it.amount}.sorted()
        val threshold=if(habit.kind==JournalQuestionKind.NUMBER&&amounts.isNotEmpty()) amounts[(amounts.size-1)/2] else null
        val rows=if(threshold!=null) chosen.map {it.copy(yes=(it.amount ?: 0.0)>threshold)} else chosen
        analyseJournalHabit(habit,outcome,rows,journal.habits).copy(threshold=threshold)
    }}
}
private fun analyseJournalHabit(habit: JournalHabit,outcome: JournalOutcome,rows: List<JournalRow>,library: List<JournalHabit>): JournalInsight {
    val yes=rows.filter {it.yes};val no=rows.filterNot {it.yes}
    fun mean(xs: List<JournalRow>)=xs.takeIf {it.isNotEmpty()}?.map {it.y}?.average()?.let {if(outcome==JournalOutcome.HRV) exp(it) else it}
    val initial=JournalInsight(habit,outcome,yes.size,no.size,mean(yes),mean(no),source=rows.firstOrNull()?.stream.orEmpty(),comparisons=rows.map {JournalComparison(it.date,it.yes,if(outcome==JournalOutcome.HRV) exp(it.y) else it.y)})
    if(yes.size<5||no.size<5) return initial
    val n=rows.size
    val columns=mutableListOf<DoubleArray>();val controlNames=mutableListOf<String>()
    fun add(name: String,values: List<Double?>) {
        val known=values.filterNotNull().filter {it.isFinite()}
        if(known.size<n*.8||known.size<5) return
        val sorted=known.sorted();val median=sorted[sorted.size/2]
        val filled=values.map {it?.takeIf(Double::isFinite) ?: median};val m=filled.average();val sd=sqrt(filled.sumOf {(it-m).pow(2)}/n)
        if(sd<1e-8) return
        columns+=DoubleArray(n) {((filled[it]-m)/sd).coerceIn(-3.0,3.0)};controlNames+=name
    }
    add("Zeittrend",rows.map {it.date.toEpochDay().toDouble()})
    add("Wochenende",rows.map {if(it.date.dayOfWeek in setOf(DayOfWeek.SATURDAY,DayOfWeek.SUNDAY)) 1.0 else 0.0})
    add("Training am Verhaltenstag",rows.map {it.training})
    add("Vorheriger Messwert",rows.map {it.prior})
    val correlated=library.filter {it.id!=habit.id}.mapNotNull {other->
        val known=rows.mapNotNull {r->r.answers[other.id]?.takeUnless {it.skipped}?.yes?.let {r.yes to it}}
        if(known.size<n*.8) return@mapNotNull null
        val x=known.map {if(it.first) 1.0 else 0.0};val z=known.map {if(it.second) 1.0 else 0.0}
        val mx=x.average();val mz=z.average();val denom=sqrt(x.sumOf {(it-mx).pow(2)}*z.sumOf {(it-mz).pow(2)})
        if(denom<1e-8) null else other to x.indices.sumOf {(x[it]-mx)*(z[it]-mz)}/denom
    }.sortedByDescending {abs(it.second)}
    val treatment=rows.map {if(it.yes) 1.0 else 0.0};val meanTreatment=treatment.average()
    val structural=columns.mapIndexedNotNull {i,column->
        val denominator=sqrt(treatment.sumOf {(it-meanTreatment).pow(2)}*column.sumOf {it*it})
        if(denominator>1e-8&&abs(column.indices.sumOf {(treatment[it]-meanTreatment)*column[it]}/denominator)>=.95) controlNames[i] else null
    }
    val overlap=(correlated.filter {abs(it.second)>=.85}.map {it.first.title}+structural).distinct()
    correlated.filter {abs(it.second) in .15..<.85}.take(2).forEach {(other,_)->add(other.title,rows.map {it.answers[other.id]?.takeUnless {it.skipped}?.yes?.let {v->if(v) 1.0 else 0.0}})}
    val ys=rows.map {it.y}.sorted();val low=ys[(n*.025).toInt()];val high=ys[(n*.975).toInt().coerceAtMost(n-1)]
    val response=DoubleArray(n) {rows[it].y.coerceIn(low,high)}
    val design=Array(n) {index->doubleArrayOf(1.0,if(rows[index].yes) 1.0 else 0.0,*columns.map {it[index]}.toDoubleArray())}
    val indexes=rows.indices.toList()
    val beta=journalRidge(design,response,indexes)?.get(1) ?: return initial.copy(quality="uncertain",controls=controlNames,overlapping=overlap)
    fun effect(v: Double)=if(outcome==JournalOutcome.HRV) 100*expm1(v.coerceIn(-3.0,3.0)) else v
    val weeks=rows.indices.groupBy {rows[it].date.with(DayOfWeek.MONDAY)}.values.toList()
    val random=Random(habit.id.hashCode() xor outcome.name.hashCode() xor rows.last().date.toEpochDay().toInt())
    val samples=mutableListOf<Double>()
    if(weeks.size>=3) repeat(96) {
        val pick=(weeks.indices).flatMap {weeks[random.nextInt(weeks.size)]}
        if(pick.count {rows[it].yes}>=2&&pick.count {!rows[it].yes}>=2) journalRidge(design,response,pick)?.get(1)?.takeIf {it.isFinite()}?.let {samples+=effect(it)}
    }
    val estimate=effect(beta)
    val sorted=samples.sorted();val ciLow=sorted.takeIf {it.size>=48}?.get((sorted.size*.025).toInt());val ciHigh=sorted.takeIf {it.size>=48}?.get((sorted.size*.975).toInt().coerceAtMost(sorted.lastIndex))
    val stable=if(samples.isEmpty()) null else samples.count {it*estimate>0}.toDouble()/samples.size
    val halves=listOf(indexes.take(n/2),indexes.drop(n/2))
    val repeated=halves.all {half->half.count {rows[it].yes}>=3&&half.count {!rows[it].yes}>=3&&journalRidge(design,response,half)?.get(1)?.let {it*beta>0}==true}
    // Display thresholds, not clinically validated minimum effects.
    val meaningful=when(outcome) {JournalOutcome.HRV->3.0;JournalOutcome.RESTING_PULSE->1.0;JournalOutcome.SLEEP_DURATION->10.0;else->2.0}
    val quality=when {
        overlap.isNotEmpty()->"overlapping"
        n<20||weeks.size<4||ciLow==null->"early"
        n>=28&&ciLow*ciHigh!!>0&&(stable ?: 0.0)>=.8&&repeated&&abs(estimate)>=meaningful&&rows.first().stream.substringBefore('/').isNotBlank()->"consistent"
        else->"uncertain"
    }
    return initial.copy(effect=estimate,low=ciLow,high=ciHigh,quality=quality,controls=controlNames,overlapping=overlap,stableShare=stable)
}
private fun journalRidge(x: Array<DoubleArray>,y: DoubleArray,rows: List<Int>): DoubleArray? {
    val p=x.firstOrNull()?.size ?: return null
    val a=Array(p) {DoubleArray(p+1)}
    rows.forEach {r->for(i in 0 until p) {a[i][p]+=x[r][i]*y[r];for(j in 0 until p) a[i][j]+=x[r][i]*x[r][j]}}
    for(i in 1 until p) a[i][i]+=if(i==1) .05 else 1.0
    for(i in 0 until p) {
        val pivot=(i until p).maxByOrNull {abs(a[it][i])} ?: return null
        if(abs(a[pivot][i])<1e-10) return null
        val tmp=a[i];a[i]=a[pivot];a[pivot]=tmp
        val scale=a[i][i];for(j in i..p) a[i][j]/=scale
        for(k in 0 until p) if(k!=i) {val factor=a[k][i];for(j in i..p) a[k][j]-=factor*a[i][j]}
    }
    return DoubleArray(p) {a[it][p]}.takeIf {it.all(Double::isFinite)}
}

internal fun journalCoachSummary(insights: List<JournalInsight>): String {
    val chosen=insights.filter {it.effect!=null}.groupBy {it.habit.id}.values.map {rows->
        rows.minWith(compareBy<JournalInsight> {if(it.quality=="consistent") 0 else if(it.quality=="overlapping") 2 else 1}.thenBy {it.outcome.ordinal})
    }.sortedWith(compareBy<JournalInsight> {if(it.quality=="consistent") 0 else 1}.thenByDescending {it.yesCount+it.noCount}).take(6)
    if(chosen.isEmpty()) return "Das lokale Logbuch ist noch in der Lernphase. Es gibt noch keine ausreichend beobachteten persönlichen Zusammenhänge. Erfinde keine Wirkungen."
    return "Lokal berechnete Logbuch-Zusammenhänge (keine Ursachebeweise): "+chosen.joinToString("; ") {
        "${it.habit.title}: ${it.outcome.title}, Unterschied ${it.effect} ${it.outcome.unit}, Spanne ${it.low} bis ${it.high}, Gruppen ${it.yesCount}/${it.noCount}, Einordnung ${it.quality}, gemeinsam auftretend ${it.overlapping}, Datenstrom ${it.source}"
    }
}
