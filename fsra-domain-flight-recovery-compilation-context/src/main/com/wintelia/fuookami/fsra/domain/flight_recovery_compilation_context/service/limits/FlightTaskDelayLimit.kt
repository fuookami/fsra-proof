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
import com.wintelia.fuookami.fsra.domain.rule_context.model.*
import com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.model.*
import com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.service.*

private data class FlightTaskDelayShadowPriceKey(
    val flightTask: FlightTaskKey
) : ShadowPriceKey(FlightTaskDelayShadowPriceKey::class)

// with redundancy
class FlightTaskDelayLimit(
    private val flightTasks: List<FlightTask>,
    private val flightTaskTime: FlightTaskTime,
    private val timeWindow: TimeRange,
    private val delayCostCalculator: DelayCostCalculator,
    override val name: String = "flight_task_delay"
) : CGPipeline<LinearMetaModel, ShadowPriceMap> {
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
            obj += delayCostCalculator(null, task) * delay[task]!!
        }
        model.minimize(obj, "delay")

        return Ok(success)
    }

    override fun extractor(): Extractor<ShadowPriceMap> {
        return wrap { map, _: FlightTask?, flightTask: FlightTask?, _: Aircraft? ->
            if (flightTask != null) {
                map[FlightTaskDelayShadowPriceKey(flightTask.key)]?.price ?: Flt64.zero
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
                        key = FlightTaskDelayShadowPriceKey(flightTasks[i].key),
                        price = shadowPrices[j]
                    )
                )
            }
        }

        return Ok(success)
    }
}
