package com.wabbo.healthconnectcoach

import java.time.LocalDate
import kotlin.math.*

internal enum class VitalCheckState { WITHIN, LOWER, HIGHER, LEARNING, MISSING }
internal data class VitalCheckItem(val definition:VitalDefinition,val value:Double?,val low:Double?,val high:Double?,val days:Int,val state:VitalCheckState)
internal fun vitalCheck(days:List<DayInsight>,date:LocalDate,connector:String):List<VitalCheckItem> {
    val current=days.filter {it.date==date && it.sources["_connector"]==connector}.maxByOrNull {it.calculatedAt}
    return listOf("hrv","rhr","respiratory_rate","spo2","skin_delta").map {key ->
        val def=vitalDefinitions.first {it.key==key}
        val value=current?.let {finiteVital(it,key)}
        val ref=current?.let {vitalReference(days,def,it)}
        val precision=when(key) {"hrv"->max(3.0,(ref?.median ?: 0.0)*.10);"rhr"->3.0;"respiratory_rate"->1.0;"spo2"->1.0;else->.2}
        val low=ref?.let {min(it.low,it.median-precision).let {v ->if(key=="skin_delta") v else max(0.0,v)}}
        val high=ref?.let {max(it.high,it.median+precision).let {v ->if(key=="spo2") min(100.0,v) else v}}
        val state=when {value==null->VitalCheckState.MISSING;ref==null->VitalCheckState.LEARNING;value<low!!->VitalCheckState.LOWER;value>high!!->VitalCheckState.HIGHER;else->VitalCheckState.WITHIN}
        VitalCheckItem(def,value,low,high,ref?.days ?: 0,state)
    }
}
internal fun vitalCheckLabel(state:VitalCheckState)=when(state) {
    VitalCheckState.WITHIN->"In deinem Bereich";VitalCheckState.LOWER->"Unter deinem Bereich";VitalCheckState.HIGHER->"Über deinem Bereich"
    VitalCheckState.LEARNING->"Deine Basis lernt noch";VitalCheckState.MISSING->"Für diesen Tag fehlt ein Wert"
}
