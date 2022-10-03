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
import com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.model.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.FlightTask

class FlowControlShadowPriceKey(
    val checkPoint: Flow.CheckPoint
): ShadowPriceKey(FlowControlShadowPriceKey::class)

class FlowControlLimit(
    private val flow: Flow,
    private val parameter: Parameter,
    override val name: String = "flow_control"
): CGPipeline<LinearMetaModel, ShadowPriceMap> {
    override fun invoke(model: LinearMetaModel): Try<Error> {
        val flow = this.flow.flow
        val m = this.flow.m

        for (checkPoint in this.flow.checkPoints) {
            model.addConstraint(
                (flow[checkPoint]!! - m[checkPoint]!!) leq checkPoint.capacity,
                "${name}_${checkPoint}"
            )
        }

        val obj = LinearPolynomial()
        for (checkPoint in this.flow.checkPoints) {
            obj += parameter.flowControlSlack * m[checkPoint]!!
        }

        return Ok(success)
    }

    override fun extractor(): Extractor<ShadowPriceMap> {
        return { map, args ->
            var ret = Flt64.zero
            for (checkPoint in flow.checkPoints) {
                if (checkPoint(args[0] as FlightTask?, args[1] as FlightTask?)) {
                    ret += map[FlowControlShadowPriceKey(checkPoint)]?.price ?: Flt64.zero
                }
            }
            ret
        }
    }

    override fun refresh(map: ShadowPriceMap, model: LinearMetaModel, shadowPrices: List<Flt64>): Try<Error> {
        var i = 0
        for (j in model.constraints.indices) {
            val constraint = model.constraints[j]
            if (constraint.name.startsWith(name)) {
                map.put(
                    ShadowPrice(
                        key = FlowControlShadowPriceKey(flow.checkPoints[i]),
                        price = shadowPrices[j]
                    )
                )
                ++i
            }

            if (i == flow.checkPoints.size) {
                break
            }
        }

        return Ok(success)
    }
}