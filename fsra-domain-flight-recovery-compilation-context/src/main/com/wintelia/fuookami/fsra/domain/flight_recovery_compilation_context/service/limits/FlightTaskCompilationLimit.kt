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
import com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.model.*
import com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.service.*

private data class FlightTaskCompilationShadowPriceKey(
    val flightTask: FlightTask
) : ShadowPriceKey(FlightTaskCompilationShadowPriceKey::class) {
    override fun toString() = "Flight Task Compilation (${flightTask})"
}

class FlightTaskCompilationLimit(
    private val flightTasks: List<FlightTask>,
    private val compilation: Compilation,
    private val cancelCostCalculator: CancelCostCalculator,
    override val name: String = "flight_task_compilation"
) : CGPipeline<LinearMetaModel, ShadowPriceMap> {

    override fun invoke(model: LinearMetaModel): Try<Error> {
        val compilation = this.compilation.flightTaskCompilation
        val y = this.compilation.y

        for (flightTask in flightTasks) {
            model.addConstraint(
                compilation[flightTask]!! eq Flt64.one,
                "${name}_${flightTask.name}"
            )
        }

        model.minimize(LinearPolynomial(this.compilation.bunchCost))

        val cancelCostPoly = LinearPolynomial()
        for (flightTask in flightTasks) {
            cancelCostPoly += cancelCostCalculator(flightTask) * y[flightTask]!!
        }
        model.minimize(cancelCostPoly, "flight task cancel")

        return Ok(success)
    }

    override fun extractor(): Extractor<ShadowPriceMap> {
        return wrap { map, _: FlightTask?, flightTask: FlightTask?, _: Aircraft? ->
            if (flightTask != null) {
                map[FlightTaskCompilationShadowPriceKey(flightTask.originTask)]?.price ?: Flt64.zero
            } else {
                Flt64.zero
            }
        }
    }

    override fun refresh(map: ShadowPriceMap, model: LinearMetaModel, shadowPrices: List<Flt64>): Try<Error> {
        for ((i, j) in model.indicesOfConstraintGroup(name)!!.withIndex()) {
            map.put(
                ShadowPrice(
                    key = FlightTaskCompilationShadowPriceKey(flightTasks[i].originTask),
                    price = shadowPrices[j]
                )
            )
        }

        return Ok(success)
    }
}
