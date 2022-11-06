package com.wintelia.fuookami.fsra.domain.passenger_context.service.limits

import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import fuookami.ospf.kotlin.core.frontend.inequality.*
import fuookami.ospf.kotlin.core.frontend.model.mechanism.*
import fuookami.ospf.kotlin.framework.model.CGPipeline
import fuookami.ospf.kotlin.framework.model.Extractor
import fuookami.ospf.kotlin.framework.model.ShadowPrice
import fuookami.ospf.kotlin.framework.model.ShadowPriceKey
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.PassengerClass
import com.wintelia.fuookami.fsra.domain.rule_context.model.*
import com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.model.*
import com.wintelia.fuookami.fsra.domain.passenger_context.model.*

private data class PassengerFlightCapacityShadowPriceKey(
    val flight: FlightTask,
    val cls: PassengerClass,
) : ShadowPriceKey(PassengerFlightCapacityShadowPriceKey::class) {
    override fun toString() = "Passenger Flight Capacity ($flight)"
}

class PassengerFlightCapacityLimit(
    private val flights: List<FlightTask>,
    private val passengerActualAmount: PassengerActualAmount,
    private val flightCapacity: FlightCapacity,
    override val name: String = "passenger_flight_capacity"
) : CGPipeline<LinearMetaModel, ShadowPriceMap> {
    override fun invoke(model: LinearMetaModel): Try<Error> {
        val pa = passengerActualAmount.pa
        val capacity = flightCapacity.passenger

        for ((i, flight) in flights.withIndex()) {
            for (cls in PassengerClass.values()) {
                model.addConstraint(
                    pa[i, cls.ordinal]!! leq capacity[flight, cls]!!,
                    "${name}_${flight}"
                )
            }
        }

        return Ok(success)
    }

    override fun extractor(): Extractor<ShadowPriceMap> {
        return wrap { map, _: FlightTask?, flightTask: FlightTask?, _: Aircraft? ->
            if (flightTask != null) {
                Flt64(PassengerClass.values().sumOf { (map[PassengerFlightCapacityShadowPriceKey(flightTask.originTask, it)]?.price ?: Flt64.zero).toDouble() })
            } else {
                Flt64.zero
            }
        }
    }

    override fun refresh(map: ShadowPriceMap, model: LinearMetaModel, shadowPrices: List<Flt64>): Try<Error> {
        var p = 0
        var q = 0
        for ((_, j) in model.indicesOfConstraintGroup(name)!!.withIndex()) {
            map.put(
                ShadowPrice(
                    key = PassengerFlightCapacityShadowPriceKey(flights[p].originTask, PassengerClass.values()[q]),
                    price = shadowPrices[j]
                )
            )
            ++q

            if (q == PassengerClass.values().size) {
                q = 0
                ++p
            }
        }

        return Ok(success)
    }
}
