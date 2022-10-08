package com.wintelia.fuookami.fsra.domain.flight_task_context

import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import com.wintelia.fuookami.fsra.infrastructure.dto.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.service.*

class FlightTaskContext {
    lateinit var aggregation: Aggregation

    fun init(input: Input): Try<Error> {
        val initializer = AggregationInitializer()
        return when (val ret = initializer(input)) {
            is Ok -> {
                aggregation = ret.value
                Ok(success)
            }
            is Failed -> {
                Failed(ret.error)
            }
        }
    }
}
