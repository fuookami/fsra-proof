package com.wintelia.fuookami.fsra.infrastructure

import kotlin.time.*
import kotlinx.datetime.*

// [b, e)
data class TimeRange(
    val begin: LocalDateTime,
    val end: LocalDateTime
) {
    val empty: Boolean get() = begin == end
    val duration: Duration get() = end.toInstant(TimeZone.currentSystemDefault()) - begin.toInstant(TimeZone.currentSystemDefault())

    fun withIntersection(ano: TimeRange): Boolean {
        return begin <= ano.end && ano.begin < end
    }

    fun contains(time: LocalDateTime): Boolean {
        return begin <= time && time < end;
    }

    fun contains(time: TimeRange): Boolean {
        return begin <= time.begin && time.end <= end;
    }
}
