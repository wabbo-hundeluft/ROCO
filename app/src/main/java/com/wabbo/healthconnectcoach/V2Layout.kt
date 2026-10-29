package com.wabbo.healthconnectcoach

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp

/** Negative top margins from the accepted prototype, without changing touch targets. */
internal fun Modifier.v2TopOverlap(amount: Dp)=layout {measurable,constraints->
    val placeable=measurable.measure(constraints)
    val overlap=amount.roundToPx().coerceAtMost(placeable.height)
    layout(placeable.width,placeable.height-overlap) {placeable.placeRelative(0,-overlap)}
}

internal fun Modifier.v2HorizontalBleed(amount: Dp)=layout {measurable,constraints->
    val bleed=amount.roundToPx()
    val placeable=measurable.measure(constraints.copy(maxWidth=constraints.maxWidth+bleed*2))
    layout((placeable.width-bleed*2).coerceAtLeast(0),placeable.height) {placeable.placeRelative(-bleed,0)}
}
