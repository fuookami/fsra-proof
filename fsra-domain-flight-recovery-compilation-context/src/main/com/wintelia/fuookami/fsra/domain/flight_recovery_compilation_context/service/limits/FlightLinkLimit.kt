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
import com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.model.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.FlightTask
import fuookami.ospf.kotlin.framework.model.Extractor

data class FlightLinkShadowPriceKey(
    val prevTask: FlightTask,
    val nextTask: FlightTask
) : ShadowPriceKey(FlightLinkShadowPriceKey::class)

class ConnectingFlightLimit(
    private val link: FlightLink,
    private val parameter: Parameter,
    override val name: String = "flight_link"
) : CGPipeline<LinearMetaModel, ShadowPriceMap> {
    override fun invoke(model: LinearMetaModel): Try<Error> {
        val linkPairs = link.linkPairs
        val link = this.link.link
        val k = this.link.k

        if (linkPairs.isNotEmpty()) {
            for (pair in linkPairs) {
                model.addConstraint(
                    (link[pair]!! + k[pair]!!) eq UInt64.one,
                    "${name}_${pair}"
                )
            }

            val obj = LinearPolynomial()
            for (pair in linkPairs) {
                obj += pair.splitCost * k[pair]!!
            }
            model.minimize(obj)
        }

        return Ok(success)
    }

    override fun extractor(): Extractor<ShadowPriceMap> {
        return { map, args ->
            if (args[0] != null && args[1] != null) {
                map[FlightLinkShadowPriceKey(
                    args[0] as FlightTask,
                    args[1] as FlightTask
                )]?.price ?: Flt64.zero
            } else {
                Flt64.zero
            }
        }
    }

    override fun refresh(map: ShadowPriceMap, model: LinearMetaModel, shadowPrices: List<Flt64>): Try<Error> {
        val connectingFlightPairs = link.linkPairs
        for ((i, j) in model.indicesOfConstraintGroup(name)!!.withIndex()) {
            val constraint = model.constraints[j]
            if (constraint.name.startsWith(name)) {
                map.put(
                    ShadowPrice(
                        key = FlightLinkShadowPriceKey(
                            connectingFlightPairs[i].prevTask,
                            connectingFlightPairs[i].nextTask
                        ),
                        price = shadowPrices[j]
                    )
                )
            }
        }

        return Ok(success)
    }
}
