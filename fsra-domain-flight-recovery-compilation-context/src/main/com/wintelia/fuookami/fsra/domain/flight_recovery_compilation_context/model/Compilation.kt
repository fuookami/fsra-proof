package com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.model

import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import fuookami.ospf.kotlin.utils.multi_array.*
import fuookami.ospf.kotlin.core.frontend.variable.*
import fuookami.ospf.kotlin.core.frontend.expression.monomial.*
import fuookami.ospf.kotlin.core.frontend.expression.polynomial.*
import fuookami.ospf.kotlin.core.frontend.expression.symbol.*
import fuookami.ospf.kotlin.core.frontend.model.mechanism.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.rule_context.model.*
import fuookami.ospf.kotlin.utils.parallel.ThreadGuard

class Compilation {
    private val _x = ArrayList<BinVariable1>()
    val x: List<BinVariable1> get() = _x
    lateinit var y: BinVariable1
    lateinit var z: BinVariable1

    lateinit var bunchCost: LinearSymbol
    lateinit var flightTaskCompilation: LinearSymbols1
    lateinit var aircraftCompilation: LinearSymbols1

    fun register(flightTasks: List<FlightTask>, aircrafts: List<Aircraft>, lock: Lock, model: LinearMetaModel): Try<Error> {
        if (!this::y.isInitialized) {
            y = BinVariable1("y", Shape1(flightTasks.size))
            flightTasks.forEach {
                y[it]!!.name = "${y.name}_${it.name}_${it.index}"

                if (lock.lockedCancelFlightTasks.contains(it.originTask)) {
                    y[it]!!.range.eq(UInt8.zero)
                }
            }
        }
        model.addVars(y)

        if (!this::z.isInitialized) {
            z = BinVariable1("z", Shape1(aircrafts.size))
            aircrafts.forEach { z[it]!!.name = "${z.name}_${it.regNo}" }
        }
        model.addVars(z)

        if (!this::bunchCost.isInitialized) {
            bunchCost = LinearSymbol(LinearPolynomial(), "bunch_cost")
        }
        model.addSymbol(bunchCost)

        if (!this::flightTaskCompilation.isInitialized) {
            flightTaskCompilation = LinearSymbols1("flight_task_compilation", Shape1(flightTasks.size))
            flightTasks.forEach { flightTaskCompilation[it] = LinearSymbol(LinearPolynomial(y[it]!!), "${flightTaskCompilation.name}_${it.name}_${it.index}") }
        }
        model.addSymbols(flightTaskCompilation)

        if (!this::aircraftCompilation.isInitialized) {
            aircraftCompilation = LinearSymbols1("aircraft_compilation", Shape1(aircrafts.size))
            aircrafts.forEach { aircraftCompilation[it] = LinearSymbol(LinearPolynomial(z[it]!!), "${aircraftCompilation.name}_${it.regNo}_${it.index}") }
        }
        model.addSymbols(aircraftCompilation)

        return Ok(success)
    }

    fun addColumns(
        iteration: UInt64,
        bunches: List<FlightTaskBunch>,
        flightTasks: List<FlightTask>,
        aircrafts: List<Aircraft>,
        model: LinearMetaModel
    ): Result<ThreadGuard, Error> {
        assert(iteration.toInt() == x.size)
        assert(bunches.isNotEmpty())

        val xi = BinVariable1("x_$iteration", Shape1(bunches.size))
        bunches.forEach { xi[it]!!.name = "${xi.name}_${it.index}_${it.aircraft}" }
        model.addVars(xi)
        _x.add(xi)

        bunchCost.flush()
        for (bunch in bunches) {
            (bunchCost.polynomial as LinearPolynomial) += bunch.cost.sum!! * xi[bunch]!!
        }

        for (task in flightTasks) {
            bunches.asSequence()
                .filter { it.contains(task) }
                .forEach {
                    val compilation = this.flightTaskCompilation[task]!! as LinearSymbol
                    compilation.flush()
                    (compilation.polynomial as LinearPolynomial) += xi[it]!!
                }
        }

        for (aircraft in aircrafts) {
            bunches.asSequence()
                .filter { it.aircraft == aircraft }
                .forEach {
                    val compilation = this.aircraftCompilation[aircraft]!! as LinearSymbol
                    compilation.flush()
                    (compilation.polynomial as LinearPolynomial) += xi[it]!!
                }
        }

        return Ok(ThreadGuard(Thread{
            bunchCost.cells

            for (task in flightTasks) {
                (this.flightTaskCompilation[task]!! as LinearSymbol).cells
            }
            for (aircraft in aircrafts) {
                (this.aircraftCompilation[aircraft]!! as LinearSymbol).cells
            }
        }))
    }
}
