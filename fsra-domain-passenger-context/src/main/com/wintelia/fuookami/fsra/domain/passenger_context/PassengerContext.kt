package com.wintelia.fuookami.fsra.domain.passenger_context

import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import fuookami.ospf.kotlin.core.frontend.model.mechanism.*
import com.wintelia.fuookami.fsra.infrastructure.dto.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.rule_context.model.*
import com.wintelia.fuookami.fsra.domain.passenger_context.service.*
import com.wintelia.fuookami.fsra.infrastructure.CostItem

class PassengerContext {
    lateinit var aggregation: Aggregation
    private lateinit var costCalculator: CostCalculator

    fun init(flightTasks: List<FlightTask>, input: Input): Try<Error> {
        val initializer = AggregationInitializer()
        aggregation = when (val ret = initializer(flightTasks, input)) {
            is Ok -> { ret.value }
            is Failed -> { return Failed(ret.error) }
        }
        return Ok(success)
    }

    fun register(model: LinearMetaModel): Try<Error> {
        return Ok(success)
    }

    fun construct(model: LinearMetaModel): Try<Error> {
        return Ok(success)
    }

    fun extractShadowPrice(shadowPriceMap: ShadowPriceMap, model: LinearMetaModel, shadowPrices: List<Flt64>): Try<Error> {
        return Ok(success)
    }

    fun addColumns(iteration: UInt64, bunches: List<FlightTaskBunch>): Try<Error> {
        return Ok(success)
    }

    fun cancelCost(flightTask: FlightTask): CostItem {
        return costCalculator.cancelCost(flightTask)
    }

    fun delayCost(prevFlightTask: FlightTask?, flightTask: FlightTask): CostItem {
        return costCalculator.delayCost(prevFlightTask, flightTask)
    }
}
