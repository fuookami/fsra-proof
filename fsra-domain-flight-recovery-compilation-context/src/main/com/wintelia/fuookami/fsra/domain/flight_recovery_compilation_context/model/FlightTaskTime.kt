package com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.model

import kotlin.time.*
import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import fuookami.ospf.kotlin.utils.multi_array.*
import fuookami.ospf.kotlin.core.frontend.variable.*
import fuookami.ospf.kotlin.core.frontend.expression.monomial.*
import fuookami.ospf.kotlin.core.frontend.expression.polynomial.*
import fuookami.ospf.kotlin.core.frontend.expression.symbol.*
import fuookami.ospf.kotlin.core.frontend.model.mechanism.*
import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*

class FlightTaskTime(
    private val withRedundancy: Boolean
) {
    lateinit var etd: LinearSymbols1
    lateinit var eta: LinearSymbols1
    lateinit var redundancy: IntVariable1
    lateinit var delay: UIntVariable1

    fun register(flightTasks: List<FlightTask>, model: LinearMetaModel): Try<Error> {
        if (withRedundancy) {
            if (!this::redundancy.isInitialized) {
                redundancy = IntVariable1("time_redundancy", Shape1(flightTasks.size))
                flightTasks.forEach {
                    redundancy[it]!!.name = "${redundancy.name}_${it.name}"
                    if (it.scheduledTime != null && !it.advanceEnabled) {
                        redundancy[it]!!.range.geq(Int64.zero)
                    }
                    if (it.scheduledTime != null && !it.delayEnabled) {
                        redundancy[it]!!.range.leq(Int64.zero)
                    }
                }
            }
            model.addVars(redundancy)

            if (!this::delay.isInitialized) {
                delay = UIntVariable1("delay", Shape1(flightTasks.size))
                flightTasks.forEach { delay[it]!!.name = "${delay.name}_${it.name}" }
            }
            model.addVars(delay)
        }

        if (!this::etd.isInitialized) {
            etd = LinearSymbols1("etd", Shape1(flightTasks.size))
            flightTasks.forEach {
                etd[it] = if (withRedundancy) {
                    LinearSymbol(LinearPolynomial(redundancy[it]!!), "${etd.name}_${it.name}_${it.index}")
                } else {
                    LinearSymbol(LinearPolynomial(), "${etd.name}_${it.name}_${it.index}")
                }
            }
        }
        model.addSymbols(etd)

        if (!this::eta.isInitialized) {
            eta = LinearSymbols1("eta", Shape1(flightTasks.size))
            flightTasks.forEach {
                eta[it] = if (withRedundancy) {
                    LinearSymbol(LinearPolynomial(redundancy[it]!!), "${eta.name}_${it.name}_${it.index}")
                } else {
                    LinearSymbol(LinearPolynomial(), "${eta.name}_${it.name}_${it.index}")
                }
            }
        }
        model.addSymbols(eta)

        return Ok(success)
    }

    fun addColumns(
        iteration: UInt64,
        bunches: List<FlightTaskBunch>,
        flightTasks: List<FlightTask>,
        timeWindow: TimeRange,
        compilation: Compilation
    ): Try<Error> {
        assert(bunches.isNotEmpty())

        val xi = compilation.x[iteration.toInt()]
        for (task in flightTasks) {
            for (bunch in bunches) {
                val actualTask = bunch.get(task) ?: continue
                val time = actualTask.time!!

                val etd = this.etd[task] as LinearSymbol
                etd.flush()
                (etd.polynomial as LinearPolynomial) += Flt64((time.begin - timeWindow.begin).toDouble(DurationUnit.MINUTES)) * xi[bunch]!!

                val eta = this.eta[task] as LinearSymbol
                eta.flush()
                (eta.polynomial as LinearPolynomial) += Flt64((time.end - timeWindow.begin).toDouble(DurationUnit.MINUTES)) * xi[bunch]!!
            }
        }
        for (task in flightTasks) {
            (this.etd[task] as LinearSymbol).cells
            (this.eta[task] as LinearSymbol).cells
        }

        return Ok(success)
    }
}
