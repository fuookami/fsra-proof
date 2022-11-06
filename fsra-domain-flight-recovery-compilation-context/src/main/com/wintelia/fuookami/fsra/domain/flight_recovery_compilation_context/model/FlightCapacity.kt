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
    lateinit var passenger: LinearSymbols2
    lateinit var cargo: LinearSymbols1

    fun register(flightTasks: List<FlightTask>, model: LinearMetaModel): Try<Error> {
        if (withPassenger) {
            if (!this::passenger.isInitialized) {
                passenger = LinearSymbols2("passenger_capacity", Shape2(flightTasks.size, PassengerClass.values().size))
                for (task in flightTasks) {
                    if (task.capacity is AircraftCapacity.Passenger) {
                        for (cls in PassengerClass.values()) {
                            passenger[task, cls] = LinearSymbol(LinearPolynomial(), "${passenger.name}_${task.name}_${cls.toShortString()}_${task.index}")
                        }
                    }
                }
            }
            for (task in flightTasks) {
                if (task.capacity is AircraftCapacity.Passenger) {
                    for (cls in PassengerClass.values()) {
                        model.addSymbol(passenger[task, cls]!!)
                    }
                }
            }
        }

        if (withCargo) {
            if (!this::cargo.isInitialized) {
                cargo = LinearSymbols1("cargo_capacity", Shape1(flightTasks.size))
                flightTasks.asSequence()
                    .filter { it.capacity is AircraftCapacity.Cargo }
                    .forEach { cargo[it] = LinearSymbol(LinearPolynomial(), "${cargo.name}_${it.name}_${it.index}") }
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
                                val capacity = passenger[task, cls]!! as LinearSymbol
                                capacity.flush()
                                (capacity.polynomial as LinearPolynomial) += aircraftCapacity[cls] * xi[bunch]!!
                            }
                        }

                        else -> {}
                    }
                }
            }
            for (task in flightTasks) {
                if (task.capacity is AircraftCapacity.Passenger) {
                    PassengerClass.values().forEach { cls ->
                        (passenger[task, cls]!! as LinearSymbol).cells
                    }
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
                if (task.capacity is AircraftCapacity.Cargo) {
                    (cargo[task]!! as LinearSymbol).cells
                }
            }
        }

        return Ok(success)
    }
}
