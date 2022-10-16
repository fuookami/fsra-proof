package com.wintelia.fuookami.fsra.domain.passenger_context

import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import fuookami.ospf.kotlin.core.frontend.model.mechanism.*
import com.wintelia.fuookami.fsra.infrastructure.dto.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.rule_context.model.*

class PassengerContext {
    fun init(input: Input): Try<Error> {
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
}
