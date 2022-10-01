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

class Compilation {
    private val _x = ArrayList<BinVariable1>()
    val x: List<BinVariable1> get() = _x
    lateinit var y: BinVariable1
    lateinit var z: BinVariable1

    lateinit var bunchCost: LinearSymbol
    lateinit var flightTaskCompilation: LinearSymbols1
    lateinit var aircraftCompilation: LinearSymbols1

    fun register(flightTasks: List<FlightTask>, aircrafts: List<Aircraft>, model: LinearMetaModel): Try<Error> {
        if (!this::y.isInitialized) {
            y = BinVariable1("y", Shape1(flightTasks.size))
            for (task in flightTasks) {
                y[task]!!.name = "${y.name}_${task.name}"
            }
        }
        model.addVars(y)

        if (!this::z.isInitialized) {
            z = BinVariable1("z", Shape1(aircrafts.size))
            for (aircraft in aircrafts) {
                z[aircraft]!!.name = "${z.name}_${aircraft.regNo}"
            }
        }
        model.addVars(z)

        if (!this::bunchCost.isInitialized) {
            bunchCost = LinearSymbol(LinearPolynomial(), "bunchCost")
        }
        model.addSymbol(bunchCost)

        if (!this::flightTaskCompilation.isInitialized) {
            flightTaskCompilation = LinearSymbols1("flight_task_compilation", Shape1(flightTasks.size))
            for (task in flightTasks) {
                flightTaskCompilation[task]!!.name = "${flightTaskCompilation.name}_${task.name}"
            }
        }
        model.addSymbols(flightTaskCompilation)

        if (!this::aircraftCompilation.isInitialized) {
            aircraftCompilation = LinearSymbols1("aircraft_compilation", Shape1(aircrafts.size))
            for (aircraft in aircrafts) {
                aircraftCompilation[aircraft]!!.name = "${aircraftCompilation.name}_${aircraft.regNo}"
            }
        }
        model.addSymbols(aircraftCompilation)

        return Ok(success)
    }

    fun addColumns(iteration: UInt64, bunches: List<FlightTaskBunch>, flightTasks: List<FlightTask>, aircrafts: List<Aircraft>, model: LinearMetaModel): Try<Error> {
        assert(iteration.toInt() == x.size)
        assert(bunches.isNotEmpty())

        val xi = BinVariable1("x_$iteration", Shape1(bunches.size))
        for (bunch in bunches) {
            xi[bunch]!!.name = "${xi.name}_${bunch.index}"
        }
        model.addVars(xi)
        _x.add(xi)

        bunchCost.flush()
        for (bunch in bunches) {
            (bunchCost.polynomial as LinearPolynomial) += bunch.cost.sum!! * xi[bunch]!!
        }

        for (task in flightTasks) {
            for (bunch in bunches) {
                if (bunch.contains(task)) {
                    val compilation = this.flightTaskCompilation[task]!! as LinearSymbol
                    compilation.flush()
                    (compilation.polynomial as LinearPolynomial) += xi[bunch]!!
                }
            }
        }

        for (aircraft in aircrafts) {
            for (bunch in bunches) {
                if (bunch.aircraft == aircraft) {
                    val compilation = this.aircraftCompilation[aircraft]!! as LinearSymbol
                    compilation.flush()
                    (compilation.polynomial as LinearPolynomial) += xi[bunch]!!
                }
            }
        }

        return Ok(success)
    }
}
