package com.wabbo.healthconnectcoach.score

import java.time.*
import java.time.temporal.ChronoUnit
import kotlin.math.*

/** Transparent training-history model. Uses our load units, not TrainingPeaks TSS or a measured fitness test.
 * Slow/fast EWMA time constants follow the PMC concept; target policy is our own, not a medical prescription.
 */
object AdaptiveWeeklyLoad {
    data class Result(val target: Double?, val adaptive: Boolean=false, val completeWeeks: Int=0,
        val chronicWeekly: Double=0.0, val acuteWeekly: Double=0.0, val previousTarget: Double?=null,
        val recoveryHold: Boolean=false, val fatigueHold: Boolean=false, val durationFallback: Boolean=false,
        val pastWeeks: List<Double> = emptyList(), val reason: String?=null)

    fun evaluate(input: ApprovedWellnessScoreEngine.NormalizedInput, initialGoal: Double?,
        load: (ApprovedWellnessScoreEngine.Workout)->ApprovedWellnessScoreEngine.ActivityLoad): Result {
        val seed=initialGoal?.takeIf {it.isFinite()&&it>0}
        fun learning(reason: String)=Result(seed,reason=if(seed==null&&reason=="adaptive_history_missing") "weekly_target_missing" else reason)
        val knownFrom=input.trainingHistoryStart ?: return learning("adaptive_history_missing")
        if(!input.trainingHistoryComplete) return learning("adaptive_history_incomplete")
        val monday=input.asOf.atZone(input.zone).toLocalDate().with(DayOfWeek.MONDAY)
        // Fixed windows relative to Monday prevent today's workouts or the daily read cutoff moving this week's goal.
        val observed=mutableSetOf<LocalDate>()
        input.workouts.filter {it.end<=input.asOf&&(input.boundSources["exercise"]==null||it.source==input.boundSources["exercise"])}.forEach {observed+=it.start.atZone(input.zone).toLocalDate()}
        input.sleeps.filter {it.end<=input.asOf&&(input.boundSources["sleep"]==null||it.source==input.boundSources["sleep"])}.forEach {observed+=it.localWakeDate ?: it.end.atZone(input.zone).toLocalDate()}
        listOf("hrv" to input.hrvBaseline,"rhr" to input.rhrBaseline).forEach { (key,values) ->values.filter {it.value.isFinite()&&it.value>0&&(input.boundSources[key]==null||it.source==input.boundSources[key])}.forEach {observed+=it.date}}
        input.steps.filter {it.end<=input.asOf&&it.count>0&&(input.boundSources["steps"]==null||it.source==input.boundSources["steps"])}.forEach {observed+=it.start.atZone(input.zone).toLocalDate()}
        fun coverage(days: Int): Boolean = (0 until days/7).all {week ->
            val begin=monday.minusDays(days.toLong()).plusDays(week*7L)
            (0L..6).count {begin.plusDays(it) in observed}>=5
        }
        val days=when {
            knownFrom<=monday.minusDays(56).atStartOfDay(input.zone).toInstant()&&coverage(56)->56
            knownFrom<=monday.minusDays(21).atStartOfDay(input.zone).toInstant()&&coverage(21)->21
            else->return learning(if(knownFrom<=monday.minusDays(21).atStartOfDay(input.zone).toInstant()) "adaptive_wear_coverage_missing" else "adaptive_history_too_short")
        }
        val startDate=monday.minusDays(days.toLong())
        val start=startDate.atStartOfDay(input.zone).toInstant();val stop=monday.atStartOfDay(input.zone).toInstant()
        val bound=input.boundSources["exercise"]
        val workouts=input.workouts.filter {it.end>start&&it.start<stop&&it.end>it.start&&it.end<=input.asOf&&(bound==null||it.source==bound)}
            .map {w -> w.copy(start=maxOf(w.start,start),end=minOf(w.end,stop),heartRate=w.heartRate.filter {it.at>=start&&it.at<stop})}
            .distinctBy {listOf(it.start,it.end,it.source)}.sortedBy {it.start}
        if((bound==null&&workouts.map {it.source}.distinct().size>1)||workouts.zipWithNext().any {it.first.end>it.second.start})
            return learning("adaptive_history_ambiguous")
        val daily=DoubleArray(days)
        var fallback=false
        workouts.forEach {w ->
            val dose=load(w);fallback=fallback||dose.fallback
            val seconds=Duration.between(w.start,w.end).toMillis().toDouble()
            var date=w.start.atZone(input.zone).toLocalDate()
            while(date<monday&&date.atStartOfDay(input.zone).toInstant()<w.end) {
                val a=maxOf(w.start,date.atStartOfDay(input.zone).toInstant())
                val b=minOf(w.end,date.plusDays(1).atStartOfDay(input.zone).toInstant())
                if(b>a) daily[ChronoUnit.DAYS.between(startDate,date).toInt()]+=dose.weightedMinutes*Duration.between(a,b).toMillis()/seconds
                date=date.plusDays(1)
            }
        }
        val weeks=(0 until days/7).map {w ->(w*7 until w*7+7).sumOf {daily[it]}}
        fun median(values: List<Double>)=values.sorted().let {v ->if(v.size%2==1) v[v.size/2] else (v[v.size/2-1]+v[v.size/2])/2}
        // A single exceptionally hard week does not define the initial capacity estimate.
        val initial=median(weeks.take(3))
        if(initial<=0) return learning("adaptive_training_baseline_missing").copy(completeWeeks=weeks.size,pastWeeks=weeks.asReversed(),durationFallback=fallback)
        var chronic=initial/7;var acute=chronic;var target=initial;var previous: Double?=null
        var lowRecovery=false;var highFatigue=false
        for(day in 21 until days) {
            chronic+=(daily[day]-chronic)/42.0
            acute+=(daily[day]-acute)/7.0
            if((day+1)%7==0) {
                previous=target
                val boundary=startDate.plusDays((day+1).toLong())
                lowRecovery=suppressedRecovery(input,boundary)
                highFatigue=acute>chronic*1.25
                var candidate=chronic*7
                if(highFatigue) candidate=min(candidate,target)
                if(lowRecovery) candidate=min(candidate,target*.90)
                // Software smoothing limits, not a universal injury-prevention "10% rule".
                target=candidate.coerceIn(target*.90,target*1.10)
            }
        }
        if(days==21) {
            // The standard connector window exposes three complete weeks. Let its most
            // recent week affect the estimate too, rather than returning a static median.
            daily.forEach {dose->chronic+=(dose-chronic)/42.0;acute+=(dose-acute)/7.0}
            previous=target
            lowRecovery=suppressedRecovery(input,monday)
            highFatigue=acute>chronic*1.25
            var candidate=chronic*7
            if(highFatigue) candidate=min(candidate,target)
            if(lowRecovery) candidate=min(candidate,target*.90)
            target=candidate.coerceIn(target*.90,target*1.10)
        }
        return Result(ApprovedWellnessScoreEngine.halfUp(max(target,.01),2),true,weeks.size,chronic*7,acute*7,previous,
            lowRecovery,highFatigue,fallback,weeks.asReversed(),reason=if(weeks.size<6) "adaptive_history_short" else null)
    }

    private fun suppressedRecovery(i: ApprovedWellnessScoreEngine.NormalizedInput,before: LocalDate): Boolean {
        fun values(xs: List<ApprovedWellnessScoreEngine.BaselineDay>,key: String,from: LocalDate,to: LocalDate)=
            xs.filter {it.date in from..to&&it.value.isFinite()&&it.value>0&&(i.boundSources[key]==null||it.source==i.boundSources[key])}
                .groupBy {it.date}.values.map {rows ->rows.map {it.value}.sorted().let {v ->if(v.size%2==1) v[v.size/2] else (v[v.size/2-1]+v[v.size/2])/2}}
        val h=values(i.hrvBaseline,"hrv",before.minusDays(3),before.minusDays(1));val r=values(i.rhrBaseline,"rhr",before.minusDays(3),before.minusDays(1))
        val hb=values(i.hrvBaseline,"hrv",before.minusDays(28),before.minusDays(4));val rb=values(i.rhrBaseline,"rhr",before.minusDays(28),before.minusDays(4))
        return h.size==3&&r.size==3&&hb.size>=7&&rb.size>=7&&h.average()<hb.average()*.90&&r.average()>rb.average()+3
    }
}
