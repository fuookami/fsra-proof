package com.wintelia.fuookami.fsra.domain.bunch_generation_context

import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.bunch_generation_context.model.*

class Aggregation(
    val graphs: Map<Aircraft, Graph>,
    val reverse: FlightTaskReverse,

    val initialFlightBunches: List<FlightTaskBunch>
)
