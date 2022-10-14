package com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.service

import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.*

typealias AircraftChecker = (Aircraft) -> Boolean

class AggregationInitializer {
    operator fun invoke(): Result<Aggregation, Error> {
        return Ok(Aggregation())
    }
}
