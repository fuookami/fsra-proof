package com.wintelia.fuookami.fsra.domain.flight_task_context.model

import fuookami.ospf.kotlin.utils.concept.*
import com.wintelia.fuookami.fsra.infrastructure.*

class FlightTaskBunch(
    val aircraft: Aircraft,
    val time: TimeRange
): ManualIndexed() {
}
