package com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.service.limits

import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import fuookami.ospf.kotlin.core.frontend.expression.monomial.*
import fuookami.ospf.kotlin.core.frontend.expression.polynomial.*
import fuookami.ospf.kotlin.core.frontend.inequality.*
import fuookami.ospf.kotlin.core.frontend.model.mechanism.*
import fuookami.ospf.kotlin.framework.model.CGPipeline
import fuookami.ospf.kotlin.framework.model.Extractor
import fuookami.ospf.kotlin.framework.model.ShadowPrice
import fuookami.ospf.kotlin.framework.model.ShadowPriceKey
import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.rule_context.model.*
import com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.model.*

private data class FleetBalanceShadowPriceKey(
    val airport: Airport,
    val aircraftMinorType: AircraftMinorType
) : ShadowPriceKey(FleetBalanceShadowPriceKey::class)

class FleetBalanceLimit(
    private val fleetBalance: FleetBalance,
    private val parameter: Parameter,
    override val name: String = "fleet_balance"
) : CGPipeline<LinearMetaModel, ShadowPriceMap> {
    private val checkPoints: List<FleetBalance.CheckPoint> = fleetBalance.limits.keys.toList()

    override fun invoke(model: LinearMetaModel): Try<Error> {
        val fleet = fleetBalance.fleet
        val l = fleetBalance.l

        for (checkPoint in checkPoints) {
            val limit = fleetBalance.limits[checkPoint]!!
            model.addConstraint(
                (fleet[checkPoint]!! + l[checkPoint]!!) geq limit.amount,
                "${name}_${checkPoint.airport.icao}_${checkPoint.aircraftMinorType.code}"
            )
        }

        val obj = LinearPolynomial()
        for (checkPoint in checkPoints) {
            obj += parameter.fleetBalanceSlack * l[checkPoint]!!
        }
        model.minimize(obj, "fleet balance")

        return Ok(success)
    }

    override fun extractor(): Extractor<ShadowPriceMap> {
        return wrap { map, prevFlightTask: FlightTask?, flightTask: FlightTask?, aircraft: Aircraft? ->
            if (prevFlightTask != null && aircraft != null && flightTask == null) {
                map[FleetBalanceShadowPriceKey(
                    airport = prevFlightTask.arr,
                    aircraftMinorType = aircraft.minorType
                )]?.price ?: Flt64.zero
            } else {
                Flt64.zero
            }
        }
    }

    override fun refresh(map: ShadowPriceMap, model: LinearMetaModel, shadowPrices: List<Flt64>): Try<Error> {
        for ((i, j) in model.indicesOfConstraintGroup(name)!!.withIndex()) {
            val constraint = model.constraints[j]
            if (constraint.name.startsWith(name)) {
                map.put(
                    ShadowPrice(
                        key = FleetBalanceShadowPriceKey(checkPoints[i].airport, checkPoints[i].aircraftMinorType),
                        price = shadowPrices[j]
                    )
                )
            }
        }

        return Ok(success)
    }
}
