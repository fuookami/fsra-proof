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
import com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.model.*

private data class FleetBalanceShadowPriceKey(
    val checkPoint: FleetBalance.CheckPoint
): ShadowPriceKey(FleetBalanceShadowPriceKey::class)

class FleetBalanceLimit(
    private val fleetBalance: FleetBalance,
    private val parameter: Parameter,
    override val name: String = "fleet_balance"
): CGPipeline<LinearMetaModel, ShadowPriceMap> {
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
            obj += parameter.fleetBalance * l[checkPoint]!!
        }
        model.minimize(obj, "fleet balance")

        return Ok(success)
    }

    override fun extractor(): Extractor<ShadowPriceMap> {
        return { map, args ->
            if (args[0] != null && args[2] != null && args[1] == null) {
                map[FleetBalanceShadowPriceKey(FleetBalance.CheckPoint(
                    airport = (args[0]!! as FlightTask).arr,
                    aircraftMinorType = (args[2]!! as Aircraft).minorType
                ))]?.price ?: Flt64.zero
            } else {
                Flt64.zero
            }
        }
    }

    override fun refresh(map: ShadowPriceMap, model: LinearMetaModel, shadowPrices: List<Flt64>): Try<Error> {
        var i = 0
        for (j in model.constraints.indices) {
            val constraint = model.constraints[j]
            if (constraint.name.startsWith(name)) {
                map.put(ShadowPrice(
                    key = FleetBalanceShadowPriceKey(checkPoints[i]),
                    price = shadowPrices[j]
                ))
                ++i
            }

            if (i == checkPoints.size) {
                break
            }
        }

        return Ok(success)
    }
}
