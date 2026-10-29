package com.wabbo.healthconnectcoach

import java.time.Instant

/** Merged workout intervals let provider pages discard unrelated all-day pulse samples immediately. */
internal class WorkoutPulseWindows(intervals: List<Pair<Instant, Instant>>) {
    private val windows: List<Pair<Instant, Instant>> = buildList {
        for ((start, end) in intervals.filter { it.first < it.second }.sortedBy { it.first }) {
            val last = lastOrNull()
            if (last != null && start <= last.second) this[lastIndex] = last.first to maxOf(last.second, end)
            else add(start to end)
        }
    }
    fun contains(at: Instant): Boolean {
        var low = 0; var high = windows.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            val (start, end) = windows[mid]
            when { at < start -> high = mid - 1; at >= end -> low = mid + 1; else -> return true }
        }
        return false
    }
}
