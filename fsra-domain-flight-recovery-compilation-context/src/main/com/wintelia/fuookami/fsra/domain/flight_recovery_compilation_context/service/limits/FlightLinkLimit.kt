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
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.rule_context.model.*
import com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.model.Compilation
import com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.model.FlightLink

private data class FlightLinkShadowPriceKey(
    val link: com.wintelia.fuookami.fsra.domain.rule_context.model.FlightLink
) : ShadowPriceKey(FlightLinkShadowPriceKey::class) {
    override fun toString() = "Flight Link ($link)"
}

class FlightLinkLimit(
    private val linkMap: FlightLinkMap,
    private val compilation: Compilation,
    private val link: FlightLink,
    override val name: String = "flight_link"
) : CGPipeline<LinearMetaModel, ShadowPriceMap> {
    override fun invoke(model: LinearMetaModel): Try<Error> {
        val y = compilation.y
        val linkPairs = link.linkPairs

        if (linkPairs.isNotEmpty()) {
            val link = this.link.link
            val k = this.link.k

            for (pair in linkPairs) {
                val lhs = LinearPolynomial()
                lhs += link[pair]!!
                lhs += k[pair]!!
                lhs += y[pair.prevTask]!! / Flt64.two
                lhs += y[pair.succTask]!! / Flt64.two
                model.addConstraint(
                    lhs geq UInt64.one,
                    "${name}_${pair}_${pair.index}"
                )
            }

            val obj = LinearPolynomial()
            for (pair in linkPairs) {
                obj += pair.splitCost * k[pair]!!
            }
            model.minimize(obj, "flight link")
        }

        return Ok(success)
    }

    override fun extractor(): Extractor<ShadowPriceMap> {
        return wrap { map, prevFlightTask: FlightTask?, flightTask: FlightTask?, _: Aircraft? ->
            if (prevFlightTask != null && flightTask != null) {
                linkMap.leftMapper[prevFlightTask.key]?.sumOf {
                    if (it.succTask == flightTask.originTask) {
                        map[FlightLinkShadowPriceKey(it)]?.price?.toDouble() ?: 0.0
                    } else {
                        0.0
                    }
                }?.let { Flt64(it) } ?: Flt64.zero
            } else {
                Flt64.zero
            }
        }
    }

    override fun refresh(map: ShadowPriceMap, model: LinearMetaModel, shadowPrices: List<Flt64>): Try<Error> {
        val linkPairs = link.linkPairs
        for ((i, j) in model.indicesOfConstraintGroup(name)!!.withIndex()) {
            map.put(
                ShadowPrice(
                    key = FlightLinkShadowPriceKey(linkPairs[i]),
                    price = shadowPrices[j]
                )
            )
        }

        return Ok(success)
    }
}
