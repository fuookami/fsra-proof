package com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.service.limits

import kotlin.time.*
import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
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

private data class FlightTaskConnectionTimeShadowPriceKey(
    val prevFlightTask: FlightTask,
    val succFlightTask: FlightTask,
    val aircraft: Aircraft
) : ShadowPriceKey(FlightTaskConnectionTimeShadowPriceKey::class) {
    override fun toString() = "Flight Task Connection Time ($prevFlightTask, $succFlightTask, $aircraft)"
}

private data class FlightTaskPair(
    val bunch: FlightTaskBunch,
    val prevFlightTask: FlightTask,
    val succFlightTask: FlightTask
)

// with redundancy
class FlightTaskConnectionTimeLimit(
    private val iteration: UInt64,
    bunches: List<FlightTaskBunch>,
    private val compilation: Compilation,
    private val flightTaskTime: FlightTaskTime,
    private val timeWindow: TimeRange,
    override val name: String = "flight_task_connection_time_${iteration}"
) : CGPipeline<LinearMetaModel, ShadowPriceMap> {
    private val pairs: List<FlightTaskPair>

    init {
        val flightTaskPairs = ArrayList<FlightTaskPair>()
        for (bunch in bunches) {
            assert(bunch.flightTasks.isNotEmpty())
            for (i in 0 until (bunch.size - 1)) {
                flightTaskPairs.add(
                    FlightTaskPair(
                        bunch = bunch,
                        prevFlightTask = bunch.flightTasks[i],
                        succFlightTask = bunch.flightTasks[i + 1]
                    )
                )
            }
        }
        pairs = flightTaskPairs
    }

    override fun invoke(model: LinearMetaModel): Try<Error> {
        model.registerConstraintGroup(name)
        val m = Flt64(timeWindow.duration.toDouble(DurationUnit.MINUTES))

        val xi = compilation.x[iteration.toInt()]
        val redundancy = flightTaskTime.redundancy
        val etd = flightTaskTime.etd
        val eta = flightTaskTime.eta

        for (pair in pairs) {
            val bunch = pair.bunch
            val prevTask = pair.prevFlightTask
            val succTask = pair.succFlightTask
            val aircraft = pair.bunch.aircraft

            val lhs = LinearPolynomial()
            lhs += etd[succTask]!!
            lhs -= eta[prevTask]!!
            lhs += redundancy[succTask]!!

            val rhs = LinearPolynomial()
            rhs += m * (UInt64.one - xi[bunch]!!)
            rhs += Flt64(prevTask.connectionTime(aircraft, succTask).toDouble(DurationUnit.MINUTES))

            model.addConstraint(
                lhs geq rhs,
                "${name}_${aircraft.regNo}_${prevTask.name}_${succTask.name}"
            )
        }

        return Ok(success)
    }

    override fun extractor(): Extractor<ShadowPriceMap> {
        return wrap { map, prevFlightTask: FlightTask?, flightTask: FlightTask?, aircraft: Aircraft? ->
            if (prevFlightTask != null && flightTask != null && aircraft != null) {
                map[FlightTaskConnectionTimeShadowPriceKey(
                    prevFlightTask = prevFlightTask.originTask,
                    succFlightTask = flightTask.originTask,
                    aircraft = aircraft
                )]?.price ?: Flt64.zero
            } else {
                Flt64.zero
            }
        }
    }

    override fun refresh(map: ShadowPriceMap, model: LinearMetaModel, shadowPrices: List<Flt64>): Try<Error> {
        for ((i, j) in model.indicesOfConstraintGroup(name)!!.withIndex()) {
            val key = FlightTaskConnectionTimeShadowPriceKey(
                prevFlightTask = pairs[i].prevFlightTask.originTask,
                succFlightTask = pairs[i].succFlightTask.originTask,
                aircraft = pairs[i].bunch.aircraft
            )
            map.map[key] = ShadowPrice(
                key = key,
                price = (map.map[key]?.price ?: Flt64.zero) + shadowPrices[j]
            )
        }

        return Ok(success)
    }
}
