package com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.service.limits

import kotlin.time.*
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

data class FlightTaskDelayShadowPriceKey(
    val flightTask: FlightTaskKey
): ShadowPriceKey(FlightTaskDelayShadowPriceKey::class)

typealias DelayCostCalculator = fuookami.ospf.kotlin.utils.functional.Extractor<Flt64, FlightTask>

// with redundancy
class FlightTaskDelayLimit(
    private val flightTasks: List<FlightTask>,
    private val flightTaskTime: FlightTaskTime,
    private val timeWindow: TimeRange,
    private val delayCostCalculator: DelayCostCalculator,
    override val name: String = "flight_task_delay"
): CGPipeline<LinearMetaModel, ShadowPriceMap> {
    override fun invoke(model: LinearMetaModel): Try<Error> {
        val etd = flightTaskTime.etd
        val delay = flightTaskTime.delay

        for (task in flightTasks) {
            assert((task.scheduledTime != null) xor (task.timeWindow != null))

            val std = if (task.scheduledTime != null) {
                (task.scheduledTime!!.begin - timeWindow.begin).toLong(DurationUnit.MINUTES).toULong()
            } else {
                assert(task.duration != null)
                (task.timeWindow!!.end - task.duration!! - timeWindow.begin).toLong(DurationUnit.MINUTES).toULong()
            }
            model.addConstraint(
                etd[task]!! - delay[task]!! leq UInt64(std),
                "${name}_${task.name}"
            )
        }

        val obj = LinearPolynomial()
        for (task in flightTasks) {
            obj += delayCostCalculator(task) * delay[task]!!
        }
        model.minimize(obj, "delay")

        return Ok(success)
    }

    override fun extractor(): Extractor<ShadowPriceMap> {
        return { map, args ->
            if (args[1] != null) {
                map[FlightTaskDelayShadowPriceKey(
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
                        key = FlightTaskDelayShadowPriceKey(flightTasks[i].key),
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
