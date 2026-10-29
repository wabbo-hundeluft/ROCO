package com.wabbo.healthconnectcoach

import java.time.DayOfWeek
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Only the presentation is reduced; underlying daily scores and detail pages stay intact. */
internal fun aggregateTrendPoints(points: List<V2PlotPoint>,period: Int,weeklyReset: Boolean=false,zone: ZoneId=ZoneId.systemDefault()): List<V2PlotPoint> {
    if(period<90) return points
    val groups=mutableListOf<MutableList<V2PlotPoint>>()
    points.sortedBy {it.at}.forEach {point->
        val current=groups.lastOrNull()
        val date=point.at.atZone(zone).toLocalDate()
        val previous=current?.lastOrNull()?.at?.atZone(zone)?.toLocalDate()
        if(current==null||current.size>=3||previous?.plusDays(1)!=date||weeklyReset&&date.dayOfWeek==DayOfWeek.MONDAY) groups+=mutableListOf(point)
        else current+=point
    }
    val format=DateTimeFormatter.ofPattern("dd.MM.",Locale.getDefault())
    return groups.map {group->
        val values=group.mapNotNull {it.value?.takeIf {v->v.isFinite()}}
        val start=group.first().at.atZone(zone).toLocalDate();val end=group.last().at.atZone(zone).toLocalDate()
        val middle=start.plusDays((group.size-1)/2L).atStartOfDay(zone).toInstant()
        val label=(if(start==end) start.format(format) else "${start.format(format)}–${end.format(format)}")+" · ${values.size}/${group.size} Tage"
        V2PlotPoint(middle,if(values.isEmpty()) null else values.map {it/values.size}.sum(),label,values.size,group.size,values.size==group.size&&group.all {it.joinable})
    }
}
