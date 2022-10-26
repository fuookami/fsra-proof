package com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.model

import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import fuookami.ospf.kotlin.utils.multi_array.*
import fuookami.ospf.kotlin.core.frontend.expression.monomial.*
import fuookami.ospf.kotlin.core.frontend.expression.polynomial.*
import fuookami.ospf.kotlin.core.frontend.expression.symbol.*
import fuookami.ospf.kotlin.core.frontend.model.mechanism.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import fuookami.ospf.kotlin.utils.parallel.ThreadGuard

class FlightCapacity(
    val withPassenger: Boolean = false,
    val withCargo: Boolean = false
) {
    lateinit var passenger: Map<PassengerClass, LinearSymbols1>
    lateinit var cargo: LinearSymbols1

    fun register(flightTasks: List<FlightTask>, model: LinearMetaModel): Try<Error> {
        if (withPassenger) {
            if (!this::passenger.isInitialized) {
                passenger = PassengerClass.values().map { cls ->
                    val passengerCapacity = LinearSymbols1("${cls}_capacity", Shape1(flightTasks.size))
                    flightTasks.asSequence()
                        .filter { it.capacity is AircraftCapacity.Passenger }
                        .forEach {
                            passengerCapacity[it]!!.name = "${passengerCapacity.name}_${it.name}"
                        }
                    return@map Pair(cls, passengerCapacity)
                }.toMap()
            }
            flightTasks.asSequence()
                .filter { it.capacity is AircraftCapacity.Passenger }
                .forEach { PassengerClass.values().forEach { cls -> model.addSymbol(passenger[cls]!![it]!!) } }
        }

        if (withCargo) {
            if (!this::cargo.isInitialized) {
                cargo = LinearSymbols1("cargo_capacity", Shape1(flightTasks.size))
                flightTasks.asSequence()
                    .filter { it.capacity is AircraftCapacity.Cargo }
                    .forEach { cargo[it]!!.name = "${cargo.name}_${it.name}" }
            }
            flightTasks.asSequence()
                .filter { it.capacity is AircraftCapacity.Cargo }
                .forEach { model.addSymbol(cargo[it]!!) }
        }

        return Ok(success)
    }

    fun addColumns(
        iteration: UInt64,
        bunches: List<FlightTaskBunch>,
        flightTasks: List<FlightTask>,
        compilation: Compilation
    ): Try<Error> {
        assert(bunches.isNotEmpty())

        val xi = compilation.x[iteration.toInt()]

        if (withPassenger) {
            for (task in flightTasks) {
                for (bunch in bunches) {
                    val actualTask = bunch.get(task) ?: continue

                    when (val aircraftCapacity = actualTask.capacity) {
                        is AircraftCapacity.Passenger -> {
                            PassengerClass.values().forEach { cls ->
                                val capacity = passenger[cls]!![task]!! as LinearSymbol
                                capacity.flush()
                                (capacity.polynomial as LinearPolynomial) += aircraftCapacity[cls] * xi[bunch]!!
                            }
                        }

                        else -> {}
                    }
                }
            }
            for (task in flightTasks) {
                PassengerClass.values().forEach { cls ->
                    (passenger[cls]!![task]!! as LinearSymbol).cells
                }
            }
        }

        if (withCargo) {
            for (task in flightTasks) {
                for (bunch in bunches) {
                    val actualTask = bunch.get(task) ?: continue

                    when (val aircraftCapacity = actualTask.capacity) {
                        is AircraftCapacity.Cargo -> {
                            val capacity = cargo[task]!! as LinearSymbol
                            capacity.flush()
                            (capacity.polynomial as LinearPolynomial) += aircraftCapacity.capacity * xi[bunch]!!
                        }

                        else -> {}
                    }
                }
            }
            for (task in flightTasks) {
                (cargo[task]!! as LinearSymbol).cells
            }
        }

        return Ok(success)
    }
}
