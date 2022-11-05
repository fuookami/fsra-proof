package com.wintelia.fuookami.fsra.domain.rule_context.model

import kotlinx.datetime.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*

// todo: implement it

class RecoveryLock {
    val lockedTime: Instant? get() = null
}

class Lock(
    val recoveryLocks: Map<FlightTaskKey, RecoveryLock> = emptyMap(),
    val lockedCancelFlightTasks: Set<FlightTask> = emptySet()
) {
    fun lockedTime(flightTask: FlightTask): Instant? {
        return recoveryLocks[flightTask.key]?.lockedTime
    }
}
