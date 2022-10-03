package com.wintelia.fuookami.fsra.domain.rule_context.model

import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*

data class StopoverFlightPair(
    val prevTask: FlightTask,
    val nextTask: FlightTask
) {
    val connectionTime = nextTask.scheduledTime!!.begin - prevTask.scheduledTime!!.end

    init {
        assert(prevTask is Flight)
        assert(nextTask is Flight)
    }
}
