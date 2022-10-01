package com.wintelia.fuookami.fsra.domain.rule_context.model

import fuookami.ospf.kotlin.utils.math.*
import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*

enum class FlowControlScene {
    Departure {
        override operator fun invoke(prevTask: FlightTask?, task: FlightTask?, airport: Airport, time: TimeRange): Boolean {
            return task != null && task.departedWhen(airport, time)
        }
    },
    Arrival,
    DepartureArrival,
    Stay;

    abstract operator fun invoke(bunch: FlightTaskBunch, airport: Airport, time: TimeRange): UInt64
    abstract operator fun invoke(prevTask: FlightTask?, task: FlightTask?, airport: Airport, time: TimeRange): Boolean
}

class FlowControl {
}
