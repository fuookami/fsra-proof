package com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.service.limits

import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import fuookami.ospf.kotlin.core.frontend.expression.monomial.*
import fuookami.ospf.kotlin.core.frontend.expression.polynomial.*
import fuookami.ospf.kotlin.core.frontend.inequality.*
import fuookami.ospf.kotlin.core.frontend.model.mechanism.*
import fuookami.ospf.kotlin.framework.model.CGPipeline
import fuookami.ospf.kotlin.framework.model.ShadowPrice
import fuookami.ospf.kotlin.framework.model.ShadowPriceKey
import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.domain.rule_context.model.*
import com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.model.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.FlightTask
import fuookami.ospf.kotlin.framework.model.Extractor

data class ConnectingFlightShadowPriceKey(
    val prevTask: FlightTask,
    val nextTask: FlightTask
) : ShadowPriceKey(ConnectingFlightShadowPriceKey::class)

class ConnectingFlightLimit(
    private val connectedFlightPairs: List<ConnectingFlightPair>,
    private val connection: FlightConnection,
    private val parameter: Parameter,
    override val name: String = "connecting_flight"
) : CGPipeline<LinearMetaModel, ShadowPriceMap> {
    override fun invoke(model: LinearMetaModel): Try<Error> {
        val connection = this.connection.connection
        val k = this.connection.k

        if (connectedFlightPairs.isNotEmpty()) {
            for (pair in connectedFlightPairs) {
                model.addConstraint(
                    (connection[pair]!! + k[pair]!!) eq UInt64.one,
                    "${name}_${pair}"
                )
            }

            val obj = LinearPolynomial()
            for (pair in connectedFlightPairs) {
                obj += parameter.connectingFlightSplit * k[pair]!!
            }
            model.minimize(obj)
        }

        return Ok(success)
    }

    override fun extractor(): Extractor<ShadowPriceMap> {
        return { map, args ->
            if (args[0] != null && args[1] != null) {
                map[ConnectingFlightShadowPriceKey(
                    args[0] as FlightTask,
                    args[1] as FlightTask
                )]?.price ?: Flt64.zero
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
                map.put(
                    ShadowPrice(
                        key = ConnectingFlightShadowPriceKey(
                            connectedFlightPairs[i].prevTask,
                            connectedFlightPairs[i].nextTask
                        ),
                        price = shadowPrices[j]
                    )
                )
                ++i
            }

            if (i == connectedFlightPairs.size) {
                break
            }
        }

        return Ok(success)
    }
}
