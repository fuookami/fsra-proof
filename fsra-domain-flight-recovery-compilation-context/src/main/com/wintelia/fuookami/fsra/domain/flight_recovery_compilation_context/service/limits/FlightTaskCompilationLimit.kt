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
import com.wintelia.fuookami.fsra.infrastructure.ShadowPriceMap
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.model.*

private data class FlightTaskCompilationShadowPriceKey(
    val flightTask: FlightTaskKey
) : ShadowPriceKey(FlightTaskCompilationShadowPriceKey::class)

private typealias CancelCostCalculator = fuookami.ospf.kotlin.utils.functional.Extractor<Flt64, FlightTask>

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
                (compilation[flightTask]!! + y[flightTask]!!) eq Flt64.one,
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
        return { map, args ->
            if (args[1] != null) {
                map[FlightTaskCompilationShadowPriceKey(
                    flightTask = (args[1]!! as FlightTask).key
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
                        key = FlightTaskCompilationShadowPriceKey(flightTasks[i].key),
                        price = shadowPrices[j]
                    )
                )
                ++i
            }

            if (i == flightTasks.size) {
                break
            }
        }

        return Ok(success)
    }
}
