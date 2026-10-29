package com.wabbo.healthconnectcoach

import java.time.*
import kotlin.math.*
import org.json.JSONObject
import com.wabbo.healthconnectcoach.score.PersonalScoreProfile

internal data class SleepPlannerSettings(val wakeMinute:Int=7*60,val windDownMinutes:Int=30) {
    fun validated():SleepPlannerSettings {require(wakeMinute in 0..1439 && windDownMinutes in 0..90) {"Bitte eine Uhrzeit von 00:00 bis 23:59 und eine Abendroutine von 0 bis 90 Minuten wählen."};return this}
}
internal data class NightPlan(val wake:ZonedDateTime,val bedtime:ZonedDateTime,val windDown:ZonedDateTime,
    val baseMinutes:Int,val debtMinutes:Int,val trainingMinutes:Int,val napCreditMinutes:Int,val sleepMinutes:Int,
    val bedMinutes:Int,val observedNights:Int,val efficiency:Double,val efficiencyAssumed:Boolean,val windowStarted:Boolean,
    val source:String,val dataDate:LocalDate?)
internal fun planTonight(settings:SleepPlannerSettings,days:List<DayInsight>,today:LocalDate,connector:String,zone:ZoneId,
    now:Instant,goals:TrainingGoals?=null,profile:PersonalScoreProfile=PersonalScoreProfile()):NightPlan {
    settings.validated();require(now.atZone(zone).toLocalDate()==today)
    val same=days.filter {it.date<=today && it.sources["_connector"]==connector}.groupBy {it.date}.values.map {it.maxBy {d->d.calculatedAt}}
    val current=same.singleOrNull {it.date==today && it.calculatedAt<=now}
    val origin=current?.sources?.get("sleep") ?: same.filter {it.calculatedAt<=now}.maxByOrNull {it.date}?.sources?.get("sleep")
    val floor=profile.sleepFloor(today).roundToInt()
    val base=max(floor,goals?.sleepMinutes ?: current?.metrics?.get("sleep_goal")?.takeIf {it.isFinite()&&it in 360.0..720.0}?.roundToInt() ?: 480)
    val nights=if(origin.isNullOrBlank()) emptyList() else same.filter {it.date in today.minusDays(2)..today && it.sources["sleep"]==origin && it.calculatedAt<=now &&
        it.metrics["sleep_minutes"]?.let {v->v.isFinite()&&v in 180.0..900.0}==true &&
        it.metrics["time_in_bed"]?.let {v->v.isFinite()&&v>=it.metrics.getValue("sleep_minutes")}==true}
    val debt=min(60.0,nights.sumOf {max(0.0,base-it.metrics.getValue("sleep_minutes"))}*.5).roundToInt()
    val training=current?.activities.orEmpty().filter {it.start.atZone(zone).toLocalDate()==today&&it.end<=now&&it.minutes.isFinite()&&it.minutes>0}
        .distinctBy {it.id to it.start}.sumOf {it.minutes}.let {min(30.0,it/4).roundToInt()}
    val nap=min(45.0,(current?.metrics?.get("nap_minutes")?.takeIf {it.isFinite()&&it>=0} ?: 0.0)*.5).roundToInt()
    val target=(base+debt+training-nap).coerceIn(floor,max(660,base))
    val history=if(origin.isNullOrBlank()) emptyList() else same.filter {it.date<today && it.date>=today.minusDays(28) && it.sources["sleep"]==origin && it.calculatedAt<=now}
    val efficiencies=history.mapNotNull {d->val sleep=d.metrics["sleep_minutes"];val bed=d.metrics["time_in_bed"]
        if(sleep!=null&&bed!=null&&sleep.isFinite()&&bed.isFinite()&&sleep>=180&&bed>=sleep) (sleep/bed).coerceIn(.75,1.0) else null}.sorted()
    val assumed=efficiencies.size<5
    val efficiency=if(assumed) .90 else if(efficiencies.size%2==1) efficiencies[efficiencies.size/2] else (efficiencies[efficiencies.size/2-1]+efficiencies[efficiencies.size/2])/2
    val bedMinutes=min(ceil(target/efficiency/5)*5,target+90.0)
    var wake=today.atTime(settings.wakeMinute/60,settings.wakeMinute%60).atZone(zone)
    if(!wake.toInstant().isAfter(now)) wake=today.plusDays(1).atTime(settings.wakeMinute/60,settings.wakeMinute%60).atZone(zone)
    val bedtime=wake.minusMinutes(bedMinutes.toLong())
    return NightPlan(wake,bedtime,bedtime.minusMinutes(settings.windDownMinutes.toLong()),base,debt,training,nap,target,bedMinutes.toInt(),nights.size,efficiency,assumed,bedtime.toInstant()<now,connector,current?.date)
}
internal class SleepPlannerStore(private val document:SecureLocalDocument) {
    fun read():SleepPlannerSettings?=document.read()?.let {val o=JSONObject(it);SleepPlannerSettings(o.getInt("wakeMinute"),o.getInt("windDownMinutes")).validated()}
    fun save(settings:SleepPlannerSettings) {settings.validated();document.write(JSONObject().put("wakeMinute",settings.wakeMinute).put("windDownMinutes",settings.windDownMinutes).toString())}
}

internal fun nightPlanContext(plan:NightPlan?):String?=plan?.let {
    JSONObject().put("kind","Lokaler Vorschlag für die nächste Nacht, kein gemessener Schlafbedarf und kein gestellter Wecker")
        .put("wake",it.wake.toString()).put("bedtime",it.bedtime.toString()).put("windDown",it.windDown.toString())
        .put("sleepMinutes",it.sleepMinutes).put("baseMinutes",it.baseMinutes).put("debtExtra",it.debtMinutes)
        .put("trainingExtra",it.trainingMinutes).put("napCredit",it.napCreditMinutes).put("efficiencyAssumed",it.efficiencyAssumed)
        .put("observedNights",it.observedNights).put("connector",it.source).put("dataDate",it.dataDate?.toString()).toString()
}
