package com.wintelia.fuookami.fsra.domain.passenger_context.service

import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import fuookami.ospf.kotlin.core.frontend.model.mechanism.*
import fuookami.ospf.kotlin.framework.model.CGPipeline
import fuookami.ospf.kotlin.framework.model.CGPipelineList
import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.domain.rule_context.model.*
import com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.model.*
import com.wintelia.fuookami.fsra.domain.passenger_context.*
import com.wintelia.fuookami.fsra.domain.passenger_context.service.limits.*

class PipelineListGenerator(
    private val aggregation: Aggregation
) {
    operator fun invoke(flightCapacity: FlightCapacity, parameter: Parameter): Result<CGPipelineList<LinearMetaModel, ShadowPriceMap>, Error> {
        val ret = ArrayList<CGPipeline<LinearMetaModel, ShadowPriceMap>>()

        ret.add(PassengerFlightCapacityLimit(aggregation.flights, aggregation.passengerActualAmount, flightCapacity))
        ret.add(PassengerCancelLimit(aggregation.passengerFlights, aggregation.passengerCancel, parameter))
        ret.add(PassengerClassChangeLimit(aggregation.passengerFlights, aggregation.passengerClassChange, parameter))

        return Ok(ret)
    }
}