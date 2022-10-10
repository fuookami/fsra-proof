package com.wintelia.fuookami.fsra.infrastructure

import kotlin.time.*
import kotlinx.datetime.*

// [b, e)
data class TimeRange(
    val begin: Instant = Instant.DISTANT_PAST,
    val end: Instant = Instant.DISTANT_FUTURE
) {
    val empty: Boolean get() = begin == end
    val duration: Duration get() = end - begin

    fun withIntersection(ano: TimeRange): Boolean {
        return begin <= ano.end && ano.begin < end
    }

    fun contains(time: Instant): Boolean {
        return begin <= time && time < end;
    }

    fun contains(time: TimeRange): Boolean {
        return begin <= time.begin && time.end <= end;
    }

    operator fun plus(rhs: Duration): TimeRange {
        return TimeRange(begin + rhs, end + rhs)
    }

    operator fun minus(rhs: Duration): TimeRange {
        return TimeRange(begin - rhs, end - rhs)
    }
}
