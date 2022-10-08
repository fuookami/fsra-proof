package com.wintelia.fuookami.fsra.infrastructure

import kotlin.time.*

data class RecoveryPlan(
    val id: String,
    val name: String,
    val timeWindow: TimeRange,
    val freezingTime: Duration
) {
    val recoveryTime get() = TimeRange(timeWindow.begin + freezingTime, timeWindow.end)
}
